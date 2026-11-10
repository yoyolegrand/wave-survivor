package com.wavesurvivor.network;

import com.wavesurvivor.horde.renaissance.Heritage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S→C : état d'Héritage du joueur (rang, nœuds des 3 branches, PR investis, victoire depuis la dernière renaissance). */
public class HeritageSyncPacket {

    public final int rank;
    public final int[] nodes;
    public final int invested;
    public final boolean won;
    /** 2.0 — Héritage de la Faille : {Titans, meilleure difficulté, podiums, Marques, nœuds}. */
    public final int[] rift;

    public HeritageSyncPacket(int rank, int[] nodes, int invested, boolean won, int[] rift) {
        this.rank = rank;
        this.nodes = nodes;
        this.invested = invested;
        this.won = won;
        this.rift = rift;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(rank);
        for (int i = 0; i < 3; i++) buf.writeVarInt(nodes[i]);
        buf.writeVarInt(invested);
        buf.writeBoolean(won);
        for (int i = 0; i < 5; i++) buf.writeVarInt(rift[i]);
    }

    public static HeritageSyncPacket decode(FriendlyByteBuf buf) {
        int r = buf.readVarInt();
        int[] n = {buf.readVarInt(), buf.readVarInt(), buf.readVarInt()};
        int inv = buf.readVarInt();
        boolean won = buf.readBoolean();
        int[] rift = new int[5];
        for (int i = 0; i < 5; i++) rift[i] = buf.readVarInt();
        return new HeritageSyncPacket(r, n, inv, won, rift);
    }

    public static void handle(HeritageSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Heritage.CLIENT_RANK = pkt.rank;
            com.wavesurvivor.horde.renaissance.RiftLegacy.CLIENT = pkt.rift;
            Heritage.CLIENT_NODES = pkt.nodes;
            Heritage.CLIENT_INVESTED = pkt.invested;
            Heritage.CLIENT_WON = pkt.won;
        });
        ctx.get().setPacketHandled(true);
    }
}
