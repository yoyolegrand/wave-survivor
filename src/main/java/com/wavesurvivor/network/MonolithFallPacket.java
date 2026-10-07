package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S→C : « La Chute du Monolithe » commence → ciel rouge sang pendant {@code ticks} ticks (voir client.MonolithFallFog). */
public class MonolithFallPacket {

    public final int ticks;

    public MonolithFallPacket(int ticks) { this.ticks = ticks; }

    public void encode(FriendlyByteBuf buf) { buf.writeVarInt(ticks); }

    public static MonolithFallPacket decode(FriendlyByteBuf buf) { return new MonolithFallPacket(buf.readVarInt()); }

    public static void handle(MonolithFallPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.wavesurvivor.client.MonolithFallFog.start(pkt.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
