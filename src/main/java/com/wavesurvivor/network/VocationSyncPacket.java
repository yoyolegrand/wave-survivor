package com.wavesurvivor.network;

import com.wavesurvivor.client.VocationsClient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S→C : Maîtrise et talents du joueur (+ configuration des arbres si elle est jointe). */
public class VocationSyncPacket {

    private static final int MAX = 1_000_000;

    public final String config;
    public final CompoundTag data;

    public VocationSyncPacket(String config, CompoundTag data) {
        this.config = config;
        this.data = data;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(config, MAX);
        buf.writeNbt(data);
    }

    public static VocationSyncPacket decode(FriendlyByteBuf buf) {
        return new VocationSyncPacket(buf.readUtf(MAX), buf.readNbt());
    }

    public static void handle(VocationSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VocationsClient.apply(pkt.config, pkt.data)));
        ctx.get().setPacketHandled(true);
    }
}
