package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Serveur → client : ouvre (ou ferme, si « close ») l'écran de choix du palier du mode Sans fin. */
public class EndlessTierPacket {

    public final int tier, wave, seconds, cashPercent;
    public final boolean hostDecides, host, close;

    public EndlessTierPacket(int tier, int wave, int seconds, int cashPercent, boolean hostDecides, boolean host, boolean close) {
        this.tier = tier; this.wave = wave; this.seconds = seconds; this.cashPercent = cashPercent;
        this.hostDecides = hostDecides; this.host = host; this.close = close;
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(tier); b.writeVarInt(wave); b.writeVarInt(seconds); b.writeVarInt(cashPercent);
        b.writeBoolean(hostDecides); b.writeBoolean(host); b.writeBoolean(close);
    }

    public static EndlessTierPacket decode(FriendlyByteBuf b) {
        return new EndlessTierPacket(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readBoolean());
    }

    public static void handle(EndlessTierPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.wavesurvivor.client.EndlessTierScreen.receive(pkt)));
        ctx.get().setPacketHandled(true);
    }

    public static void send(ServerPlayer p, EndlessTierPacket pkt) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }
}
