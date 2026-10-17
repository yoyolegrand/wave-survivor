package com.wavesurvivor.network;

import com.wavesurvivor.horde.stats.Achievements;
import com.wavesurvivor.horde.stats.PlayerStats;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Statistiques et succès (1.7) :
 *   Request (C→S) : le joueur demande son écran (bouton de l'autel) ;
 *   Data (S→C)    : ses compteurs et la liste de ses succès obtenus ; ouvre l'écran des statistiques.
 */
public final class StatsPackets {

    private StatsPackets() {}

    /** Envoie à ce joueur ses statistiques (commande /ws stats ou bouton). */
    public static void openFor(ServerPlayer p) {
        Map<String, Long> stats = PlayerStats.snapshotOf(p);
        List<String> done = new ArrayList<>();
        for (Achievements.Def d : Achievements.ALL) if (Achievements.isDone(p, d.id())) done.add(d.id());
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Data(stats, done));
    }

    public static class Request {
        public Request() {}
        public void encode(FriendlyByteBuf buf) {}
        public static Request decode(FriendlyByteBuf buf) { return new Request(); }
        public static void handle(Request pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) openFor(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Data {
        public final Map<String, Long> stats;
        public final List<String> done;

        public Data(Map<String, Long> stats, List<String> done) {
            this.stats = stats;
            this.done = done;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(stats.size());
            for (Map.Entry<String, Long> e : stats.entrySet()) {
                buf.writeUtf(e.getKey());
                buf.writeLong(e.getValue());
            }
            buf.writeVarInt(done.size());
            for (String id : done) buf.writeUtf(id);
        }

        public static Data decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            Map<String, Long> stats = new HashMap<>();
            for (int i = 0; i < n; i++) stats.put(buf.readUtf(), buf.readLong());
            int m = buf.readVarInt();
            List<String> done = new ArrayList<>(m);
            for (int i = 0; i < m; i++) done.add(buf.readUtf());
            return new Data(stats, done);
        }

        public static void handle(Data pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.wavesurvivor.client.ClientPacketHandler.openStats(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }
}
