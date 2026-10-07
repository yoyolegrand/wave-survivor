package com.wavesurvivor.network;

import com.wavesurvivor.horde.WaveTester;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Éditeur → serveur : « ▶ Tester » une vague (version en cours d'édition) et « ✖ Nettoyer ». Réservé aux OP. */
public final class HordeTestPackets {

    private HordeTestPackets() {}

    public static class Test {
        public final String json;
        public final int wave;
        public Test(String json, int wave) { this.json = json; this.wave = wave; }
        public void encode(FriendlyByteBuf buf) { HordeEditorPackets.writeJson(buf, json); buf.writeVarInt(wave); }
        public static Test decode(FriendlyByteBuf buf) { return new Test(HordeEditorPackets.readJson(buf), buf.readVarInt()); }
        public static void handle(Test pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null && p.hasPermissions(2)) WaveTester.test(p, pkt.json, pkt.wave);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Clear {
        public Clear() {}
        public void encode(FriendlyByteBuf buf) {}
        public static Clear decode(FriendlyByteBuf buf) { return new Clear(); }
        public static void handle(Clear pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null && p.hasPermissions(2)) WaveTester.clear(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
