package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet S→C : ouvre l'AltarConfirmScreen.
 * La horde liée peut avoir plusieurs VARIANTES (ex : Vanilla / Moddée) : chacune porte ses infos
 * (vagues, boss, spéciales) et la liste des mods requis (détectés), installés ou non.
 */
public class OpenAltarConfirmScreenPacket {

    public record Mod(String id, boolean installed) {}

    public record Variant(String label, String hordeName, int totalWaves, int totalBosses,
                          String specialInfo, List<Mod> mods, int stars, String difficulties, String lock,
                          boolean kingdom, String roles, String rush) {
        public boolean launchable() {
            for (Mod m : mods) if (!m.installed()) return false;
            return !locked();
        }

        /** Vrai si au moins une condition de déblocage n'est pas remplie (lignes « 0|… »). */
        public boolean locked() {
            if (lock == null || lock.isEmpty()) return false;
            for (String l : lock.split("\n")) if (l.startsWith("0|")) return true;
            return false;
        }
    }

    public final BlockPos altarPos;
    public final String familyName;       // horde liée à l'autel
    public final List<Variant> variants;

    public OpenAltarConfirmScreenPacket(BlockPos pos, String familyName, List<Variant> variants) {
        this.altarPos = pos;
        this.familyName = familyName;
        this.variants = variants;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(altarPos);
        buf.writeUtf(familyName);
        buf.writeVarInt(variants.size());
        for (Variant v : variants) {
            buf.writeUtf(v.label());
            buf.writeUtf(v.hordeName());
            buf.writeVarInt(v.totalWaves());
            buf.writeVarInt(v.totalBosses());
            buf.writeUtf(v.specialInfo() != null ? v.specialInfo() : "");
            buf.writeVarInt(v.mods().size());
            for (Mod m : v.mods()) { buf.writeUtf(m.id()); buf.writeBoolean(m.installed()); }
            buf.writeVarInt(v.stars());
            buf.writeUtf(v.difficulties() != null ? v.difficulties() : "");
            buf.writeUtf(v.lock() != null ? v.lock() : "", 8192);
            buf.writeBoolean(v.kingdom());
            buf.writeUtf(v.roles() != null ? v.roles() : "");
            buf.writeUtf(v.rush() != null ? v.rush() : "");
        }
    }

    public static OpenAltarConfirmScreenPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String family = buf.readUtf();
        int n = buf.readVarInt();
        List<Variant> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String label = buf.readUtf(), horde = buf.readUtf();
            int waves = buf.readVarInt(), bosses = buf.readVarInt();
            String info = buf.readUtf();
            int m = buf.readVarInt();
            List<Mod> mods = new ArrayList<>(m);
            for (int j = 0; j < m; j++) mods.add(new Mod(buf.readUtf(), buf.readBoolean()));
            int stars = buf.readVarInt();
            String diffs = buf.readUtf();
            String lock = buf.readUtf(8192);
            boolean kingdom = buf.readBoolean();
            String roles = buf.readUtf();
            String rush = buf.readUtf();
            list.add(new Variant(label, horde, waves, bosses, info, mods, stars, diffs, lock, kingdom, roles, rush));
        }
        return new OpenAltarConfirmScreenPacket(pos, family, list);
    }

    public static void handle(OpenAltarConfirmScreenPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientPacketHandler.openAltarConfirmScreen(pkt));
        });
        ctx.get().setPacketHandled(true);
    }
}
