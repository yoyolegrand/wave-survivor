package com.wavesurvivor.network;

import com.wavesurvivor.horde.renaissance.RenaissanceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet C→S : achat dans la boutique Renaissance.
 * shopId = id d'une relique, ou RenaissanceConfigData.SKILL_POINT_ID pour les points de compétence.
 */
public class RenaissanceBuyPacket {

    public final String shopId;
    public final int quantity;

    public RenaissanceBuyPacket(String shopId, int quantity) {
        this.shopId = shopId;
        this.quantity = quantity;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(shopId);
        buf.writeVarInt(quantity);
    }

    public static RenaissanceBuyPacket decode(FriendlyByteBuf buf) {
        return new RenaissanceBuyPacket(buf.readUtf(), buf.readVarInt());
    }

    public static void handle(RenaissanceBuyPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) RenaissanceManager.buy(sender, pkt.shopId, pkt.quantity);
        });
        ctx.get().setPacketHandled(true);
    }
}
