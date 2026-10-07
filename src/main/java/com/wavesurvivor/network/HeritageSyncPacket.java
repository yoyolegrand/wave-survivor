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

    public HeritageSyncPacket(int rank, int[] nodes, int invested, boolean won) {
        this.rank = rank;
        this.nodes = nodes;
        this.invested = invested;
        this.won = won;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(rank);
        for (int i = 0; i < 3; i++) buf.writeVarInt(nodes[i]);
        buf.writeVarInt(invested);
        buf.writeBoolean(won);
    }

    public static HeritageSyncPacket decode(FriendlyByteBuf buf) {
        int r = buf.readVarInt();
        int[] n = {buf.readVarInt(), buf.readVarInt(), buf.readVarInt()};
        return new HeritageSyncPacket(r, n, buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(HeritageSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Heritage.CLIENT_RANK = pkt.rank;
            Heritage.CLIENT_NODES = pkt.nodes;
            Heritage.CLIENT_INVESTED = pkt.invested;
            Heritage.CLIENT_WON = pkt.won;
        });
        ctx.get().setPacketHandled(true);
    }
}
