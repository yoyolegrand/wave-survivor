package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 2.0 — Cinématique d'apparition du Titan : le serveur la lance (Start) ou l'arrête (Stop) pour un joueur ; le client gère bandes noires,
 * caméra, brouillard et musique. Le joueur peut la passer en maintenant S'accroupir 1 s (Skip).
 */
public final class TitanCinePackets {

    private TitanCinePackets() {}

    /** S→C : début ({@code start}) ou fin de la cinématique ; durée en ticks, point regardé, passable ou non. */
    public static class Cine {
        public final boolean start, skippable;
        public final int ticks;
        public final double x, y, z;

        public Cine(boolean start, int ticks, double x, double y, double z, boolean skippable) {
            this.start = start; this.ticks = ticks; this.x = x; this.y = y; this.z = z; this.skippable = skippable;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeBoolean(start); b.writeVarInt(ticks);
            b.writeDouble(x); b.writeDouble(y); b.writeDouble(z);
            b.writeBoolean(skippable);
        }

        public static Cine decode(FriendlyByteBuf b) {
            return new Cine(b.readBoolean(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean());
        }

        public static void handle(Cine pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.wavesurvivor.client.TitanCineClient.onPacket(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** C→S : le joueur passe la cinématique (le combat commence quand même à la fin prévue, pour tous). */
    public static class Skip {
        public Skip() {}

        public void encode(FriendlyByteBuf b) {}

        public static Skip decode(FriendlyByteBuf b) { return new Skip(); }

        public static void handle(Skip pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) com.wavesurvivor.horde.rift.RiftManager.titanCineSkip(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
