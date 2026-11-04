package com.wavesurvivor.network;

import com.wavesurvivor.horde.endless.EndlessTiers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → serveur : choix du joueur au palier du mode Sans fin (vrai = encaisser et s'arrêter, faux = continuer). */
public class EndlessVotePacket {

    public final boolean cash;

    public EndlessVotePacket(boolean cash) { this.cash = cash; }

    public void encode(FriendlyByteBuf b) { b.writeBoolean(cash); }

    public static EndlessVotePacket decode(FriendlyByteBuf b) { return new EndlessVotePacket(b.readBoolean()); }

    public static void handle(EndlessVotePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && p.getServer() != null) EndlessTiers.vote(p, pkt.cash);
        });
        ctx.get().setPacketHandled(true);
    }
}
