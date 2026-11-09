package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C→S : le pilote de la baliste céleste maintient le clic droit : un tir (la cadence est gérée par le serveur). */
public class EngineFirePacket {

    public EngineFirePacket() {}

    public void encode(FriendlyByteBuf b) {}

    public static EngineFirePacket decode(FriendlyByteBuf b) { return new EngineFirePacket(); }

    public static void handle(EngineFirePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) com.wavesurvivor.horde.rift.RiftManager.engineFire(p);
        });
        ctx.get().setPacketHandled(true);
    }
}
