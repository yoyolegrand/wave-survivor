package com.wavesurvivor.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S→C : cible de la Boussole des Vestiges (dimension + position), ou aucune. */
public class VestigeTargetPacket {

    public final boolean has;
    public final ResourceLocation dim;
    public final BlockPos pos;

    public VestigeTargetPacket(boolean has, ResourceLocation dim, BlockPos pos) {
        this.has = has;
        this.dim = dim;
        this.pos = pos;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(has);
        buf.writeResourceLocation(dim);
        buf.writeBlockPos(pos);
    }

    public static VestigeTargetPacket decode(FriendlyByteBuf buf) {
        return new VestigeTargetPacket(buf.readBoolean(), buf.readResourceLocation(), buf.readBlockPos());
    }

    public static void handle(VestigeTargetPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.wavesurvivor.client.VestigeClient.set(pkt.has, pkt.dim, pkt.pos)));
        ctx.get().setPacketHandled(true);
    }
}
