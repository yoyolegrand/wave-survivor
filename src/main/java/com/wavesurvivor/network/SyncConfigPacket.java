package com.wavesurvivor.network;

import com.google.gson.Gson;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.client.ClientPacketHandler;
import com.wavesurvivor.config.ModConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Packet S->C : envoie la config complète du serveur au client.
 *
 * Le payload est sérialisé en JSON via Gson, puis gzip-compressé pour tenir dans un packet
 * (la config peut faire plusieurs centaines de Ko en JSON, ~10-30 Ko compressée).
 *
 * Envoyé automatiquement :
 *   - à chaque login (PlayerLoginHandler.onPlayerLoggedIn)
 *   - à chaque /ws reload (broadcast via PlayerLoginHandler.broadcastToAll)
 *
 * Reçu côté client : le ModConfig est stocké dans ClientConfigCache pour affichage par
 * WaveInspectionScreen.
 */
public class SyncConfigPacket {

    private static final Gson GSON = new Gson();

    public final byte[] compressedJson;

    public SyncConfigPacket(byte[] compressed) {
        this.compressedJson = compressed;
    }

    /** Construit un packet à partir du ModConfig serveur. */
    public static SyncConfigPacket fromConfig(ModConfig config) {
        String json = GSON.toJson(config);
        byte[] compressed = gzip(json.getBytes(StandardCharsets.UTF_8));
        return new SyncConfigPacket(compressed);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByteArray(compressedJson);
    }

    public static SyncConfigPacket decode(FriendlyByteBuf buf) {
        return new SyncConfigPacket(buf.readByteArray());
    }

    public static void handle(SyncConfigPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            try {
                String json = new String(gunzip(pkt.compressedJson), StandardCharsets.UTF_8);
                ModConfig config = GSON.fromJson(json, ModConfig.class);
                // Isolation client-only : ClientPacketHandler n'existe que côté client.
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncConfig(config));
                WaveSurvivorMod.LOGGER.info("[Network] Config sync reçue ({} octets compressés -> {} octets JSON)",
                        pkt.compressedJson.length, json.length());
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[Network] Erreur décodage SyncConfigPacket : {}", e.getMessage(), e);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static byte[] gzip(byte[] data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gz = new GZIPOutputStream(baos)) {
            gz.write(data);
            gz.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Network] gzip fail : {}", e.getMessage(), e);
            return data;
        }
    }

    private static byte[] gunzip(byte[] compressed) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             GZIPInputStream gz = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gz.read(buf)) > 0) baos.write(buf, 0, n);
            return baos.toByteArray();
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Network] gunzip fail : {}", e.getMessage(), e);
            return compressed;
        }
    }
}
