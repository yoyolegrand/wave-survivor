package com.wavesurvivor.network;

import com.wavesurvivor.carnet.CarnetConfig;
import com.wavesurvivor.carnet.CarnetObjectives;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Serveur → client : ouvre le Carnet du Survivant avec l'état des objectifs du joueur. */
public class CarnetPacket {

    /** Un objectif tel que l'écran l'affiche. */
    public record Obj(String id, long progress, long threshold, boolean done, int pr, List<String> items, List<Integer> counts) {}

    public final List<Obj> objectives;

    public CarnetPacket(List<Obj> objectives) { this.objectives = objectives; }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(objectives.size());
        for (Obj o : objectives) {
            b.writeUtf(o.id());
            b.writeVarLong(o.progress());
            b.writeVarLong(o.threshold());
            b.writeBoolean(o.done());
            b.writeVarInt(o.pr());
            b.writeVarInt(o.items().size());
            for (int i = 0; i < o.items().size(); i++) {
                b.writeUtf(o.items().get(i));
                b.writeVarInt(o.counts().get(i));
            }
        }
    }

    public static CarnetPacket decode(FriendlyByteBuf b) {
        int n = b.readVarInt();
        List<Obj> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String id = b.readUtf();
            long prog = b.readVarLong(), thr = b.readVarLong();
            boolean done = b.readBoolean();
            int pr = b.readVarInt(), k = b.readVarInt();
            List<String> items = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (int j = 0; j < k; j++) { items.add(b.readUtf()); counts.add(b.readVarInt()); }
            out.add(new Obj(id, prog, thr, done, pr, items, counts));
        }
        return new CarnetPacket(out);
    }

    public static void handle(CarnetPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.wavesurvivor.client.CarnetScreen.open(pkt.objectives)));
        ctx.get().setPacketHandled(true);
    }

    public static void openFor(ServerPlayer p) {
        List<Obj> list = new ArrayList<>();
        for (CarnetObjectives.State s : CarnetObjectives.states(p)) {
            CarnetConfig.Objective o = s.objective();
            List<String> items = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (CarnetConfig.Reward r : o.items()) { items.add(r.item()); counts.add(r.count()); }
            list.add(new Obj(o.id(), s.progress(), o.threshold(), s.done(), o.pr(), items, counts));
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new CarnetPacket(list));
    }
}
