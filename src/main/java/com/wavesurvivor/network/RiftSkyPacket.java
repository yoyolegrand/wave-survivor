package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Serveur → client (2.0) : phase du ciel de la Faille (horde, siege, contre, titan, victoire).
 * {@code instant} = sans fondu (arrivée dans la dimension) ; {@code yaw} = lacet de la Faille dans le ciel (0 = sud, côté du Titan).
 */
public class RiftSkyPacket {

    public final String phase;
    public final boolean instant;
    public final float yaw;

    public RiftSkyPacket(String phase, boolean instant, float yaw) {
        this.phase = phase;
        this.instant = instant;
        this.yaw = yaw;
    }

    public void encode(FriendlyByteBuf b) {
        b.writeUtf(phase);
        b.writeBoolean(instant);
        b.writeFloat(yaw);
    }

    public static RiftSkyPacket decode(FriendlyByteBuf b) {
        return new RiftSkyPacket(b.readUtf(), b.readBoolean(), b.readFloat());
    }

    public static void handle(RiftSkyPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.wavesurvivor.client.RiftSkyEffects.receive(pkt.phase, pkt.instant, pkt.yaw)));
        ctx.get().setPacketHandled(true);
    }
}
