package com.wavesurvivor.network;

import com.wavesurvivor.altar.AltarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet C→S : le joueur a choisi une couleur dans le panneau "Custom" de l'autel.
 * Le serveur vérifie la distance + la possession du colorant, en consomme 1, puis recolore.
 */
public class AltarColorPacket {

    public final BlockPos pos;
    public final DyeColor color;

    public AltarColorPacket(BlockPos pos, DyeColor color) {
        this.pos = pos;
        this.color = color;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(color.getId());
    }

    public static AltarColorPacket decode(FriendlyByteBuf buf) {
        return new AltarColorPacket(buf.readBlockPos(), DyeColor.byId(buf.readVarInt()));
    }

    public static void handle(AltarColorPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (!(sender.level() instanceof ServerLevel level)) return;
            AltarManager.setColor(sender, level, pkt.pos, pkt.color);
        });
        ctx.get().setPacketHandled(true);
    }
}
