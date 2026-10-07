package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** S→C : ouvre le Codex (commande /ws codex). */
public class CodexPacket {

    public CodexPacket() {}

    public void encode(FriendlyByteBuf buf) {}

    public static CodexPacket decode(FriendlyByteBuf buf) { return new CodexPacket(); }

    public static void handle(CodexPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.wavesurvivor.client.CodexScreen.open(null)));
        ctx.get().setPacketHandled(true);
    }

    public static void openFor(ServerPlayer p) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new CodexPacket());
    }
}
