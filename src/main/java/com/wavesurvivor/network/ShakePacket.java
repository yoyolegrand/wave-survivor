package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** S→C : secousse de caméra (séisme) pendant {@code ticks} ticks, d'une certaine intensité (1 = normale). */
public class ShakePacket {

    public final int ticks;
    public final float intensity;

    public ShakePacket(int ticks, float intensity) {
        this.ticks = ticks;
        this.intensity = intensity;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(ticks);
        buf.writeFloat(intensity);
    }

    public static ShakePacket decode(FriendlyByteBuf buf) {
        return new ShakePacket(buf.readVarInt(), buf.readFloat());
    }

    public static void handle(ShakePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.wavesurvivor.client.CameraShake.start(pkt.ticks, pkt.intensity)));
        ctx.get().setPacketHandled(true);
    }

    public static void send(ServerPlayer p, int ticks, float intensity) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ShakePacket(ticks, intensity));
    }
}
