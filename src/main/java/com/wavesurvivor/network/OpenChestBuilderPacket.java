package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet S->C : demande au client d'ouvrir le ChestBuilderScreen.
 *
 * Deux modes :
 *   - MODE CRÉATION : jsonData = "" (ou null) → nouveau chest vide avec initialName
 *   - MODE ÉDITION  : jsonData = JSON complet d'un CustomChestData → pré-remplit le builder
 *
 * Envoyé par /ws roulettechest create <nom> (création) ou /ws roulettechest edit <nom> (édition).
 */
public class OpenChestBuilderPacket {

    public final String initialName;
    public final String jsonData;   // "" en mode création, JSON du CustomChestData en mode édition

    public OpenChestBuilderPacket(String initialName) {
        this(initialName, "");
    }

    public OpenChestBuilderPacket(String initialName, String jsonData) {
        this.initialName = initialName != null ? initialName : "";
        this.jsonData = jsonData != null ? jsonData : "";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.initialName);
        buf.writeUtf(this.jsonData, 65536);
    }

    public static OpenChestBuilderPacket decode(FriendlyByteBuf buf) {
        return new OpenChestBuilderPacket(buf.readUtf(), buf.readUtf(65536));
    }

    public static void handle(OpenChestBuilderPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    ClientPacketHandler.openChestBuilder(pkt.initialName, pkt.jsonData));
        });
        ctx.get().setPacketHandled(true);
    }
}
