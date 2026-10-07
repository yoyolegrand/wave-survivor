package com.wavesurvivor.network;

import com.wavesurvivor.altar.AltarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet C→S : le joueur a cliqué "Lier" sur une horde dans l'écran de liaison.
 * Le serveur revalide tout (distance, autel vierge, recette, ingrédients) puis consomme.
 */
public class AltarBindPacket {

    public final BlockPos pos;
    public final String hordeName;

    public AltarBindPacket(BlockPos pos, String hordeName) {
        this.pos = pos;
        this.hordeName = hordeName;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(hordeName);
    }

    public static AltarBindPacket decode(FriendlyByteBuf buf) {
        return new AltarBindPacket(buf.readBlockPos(), buf.readUtf());
    }

    public static void handle(AltarBindPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (!(sender.level() instanceof ServerLevel level)) return;
            AltarManager.bind(sender, level, pkt.pos, pkt.hordeName);
        });
        ctx.get().setPacketHandled(true);
    }
}
