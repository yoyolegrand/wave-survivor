package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Packet S→C : droits validés → ouvre le menu Custom (onglets Couleur / Particules). */
public class OpenAltarCustomPacket {

    public final BlockPos pos;
    public final String particle;

    public OpenAltarCustomPacket(BlockPos pos, String particle) {
        this.pos = pos;
        this.particle = particle;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(particle);
    }

    public static OpenAltarCustomPacket decode(FriendlyByteBuf buf) {
        return new OpenAltarCustomPacket(buf.readBlockPos(), buf.readUtf());
    }

    public static void handle(OpenAltarCustomPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openAltarCustom(pkt)));
        ctx.get().setPacketHandled(true);
    }
}
