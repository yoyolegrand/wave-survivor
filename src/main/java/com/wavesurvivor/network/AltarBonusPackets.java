package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 2.1 — Bonus de l'île-autel : écran à 3 cartes (serveur → client, avec les valeurs) et choix (client → serveur). */
public final class AltarBonusPackets {

    private AltarBonusPackets() {}

    public static class Open {
        public final int rampart, arsenal, prosperity;

        public Open(int rampart, int arsenal, int prosperity) { this.rampart = rampart; this.arsenal = arsenal; this.prosperity = prosperity; }

        public void encode(FriendlyByteBuf b) { b.writeVarInt(rampart); b.writeVarInt(arsenal); b.writeVarInt(prosperity); }

        public static Open decode(FriendlyByteBuf b) { return new Open(b.readVarInt(), b.readVarInt(), b.readVarInt()); }

        public static void handle(Open pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.wavesurvivor.client.AltarBonusScreen.open(pkt.rampart, pkt.arsenal, pkt.prosperity)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Choose {
        public final int index;

        public Choose(int index) { this.index = index; }

        public void encode(FriendlyByteBuf b) { b.writeVarInt(index); }

        public static Choose decode(FriendlyByteBuf b) { return new Choose(b.readVarInt()); }

        public static void handle(Choose pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) com.wavesurvivor.horde.rift.RiftIslets.chooseBonus(p, pkt.index);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
