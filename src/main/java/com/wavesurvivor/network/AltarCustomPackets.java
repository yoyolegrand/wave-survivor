package com.wavesurvivor.network;

import com.wavesurvivor.altar.AltarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packets du menu Custom de l'autel :
 *   AltarCustomPackets.Request (C→S) : clic sur "Custom" → le serveur vérifie propriétaire/op puis envoie OpenAltarCustomPacket
 *   AltarCustomPackets.SetParticle (C→S) : choix d'un préréglage de particules
 */
public final class AltarCustomPackets {

    private AltarCustomPackets() {}

    public static class Request {
        public final BlockPos pos;
        public Request(BlockPos pos) { this.pos = pos; }
        public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); }
        public static Request decode(FriendlyByteBuf buf) { return new Request(buf.readBlockPos()); }
        public static void handle(Request pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null && p.level() instanceof ServerLevel lvl) AltarManager.openCustom(p, lvl, pkt.pos);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SetParticle {
        public final BlockPos pos;
        public final String particle;
        public SetParticle(BlockPos pos, String particle) { this.pos = pos; this.particle = particle; }
        public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeUtf(particle); }
        public static SetParticle decode(FriendlyByteBuf buf) { return new SetParticle(buf.readBlockPos(), buf.readUtf()); }
        public static void handle(SetParticle pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null && p.level() instanceof ServerLevel lvl) AltarManager.setParticle(p, lvl, pkt.pos, pkt.particle);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
