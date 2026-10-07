package com.wavesurvivor.network;

import com.wavesurvivor.horde.editor.HordeEditSessions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Éditeur de hordes à plusieurs : présence par onglet, verrous, mises à jour des onglets enregistrés par les autres. */
public final class HordeCollabPackets {

    private HordeCollabPackets() {}

    // ─── C→S : « je suis sur cet onglet de cette horde » (open = false : j'ai fermé l'éditeur) ───

    public static class Presence {
        public final String horde;
        public final int tab;
        public final boolean open;
        public Presence(String horde, int tab, boolean open) { this.horde = horde != null ? horde : ""; this.tab = tab; this.open = open; }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(horde); buf.writeVarInt(tab); buf.writeBoolean(open); }
        public static Presence decode(FriendlyByteBuf buf) { return new Presence(buf.readUtf(), buf.readVarInt(), buf.readBoolean()); }
        public static void handle(Presence pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null && p.hasPermissions(2)) HordeEditSessions.presence(p, pkt.horde, pkt.tab, pkt.open);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ─── S→C : verrous des autres éditeurs de la même horde ───

    public record Lock(int tab, String player) {}

    public static class Locks {
        public final String horde;
        public final int editors;
        public final boolean denied;
        public final List<Lock> locks;
        public Locks(String horde, int editors, boolean denied, List<Lock> locks) {
            this.horde = horde; this.editors = editors; this.denied = denied; this.locks = locks;
        }
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(horde); buf.writeVarInt(editors); buf.writeBoolean(denied);
            buf.writeVarInt(locks.size());
            for (Lock l : locks) { buf.writeVarInt(l.tab()); buf.writeUtf(l.player()); }
        }
        public static Locks decode(FriendlyByteBuf buf) {
            String h = buf.readUtf(); int ed = buf.readVarInt(); boolean den = buf.readBoolean();
            int n = buf.readVarInt();
            List<Lock> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new Lock(buf.readVarInt(), buf.readUtf()));
            return new Locks(h, ed, den, l);
        }
        public static void handle(Locks pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.wavesurvivor.client.HordeEditorScreen.onLocks(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ─── S→C : un autre joueur a enregistré des onglets de la horde ───

    public static class SectionUpdate {
        public final String horde;
        public final String by;
        public final int mask;
        public final String json;
        public final String altarJson;
        public SectionUpdate(String horde, String by, int mask, String json, String altarJson) {
            this.horde = horde; this.by = by; this.mask = mask; this.json = json; this.altarJson = altarJson != null ? altarJson : "";
        }
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(horde); buf.writeUtf(by); buf.writeInt(mask);
            HordeEditorPackets.writeJson(buf, json); HordeEditorPackets.writeJson(buf, altarJson);
        }
        public static SectionUpdate decode(FriendlyByteBuf buf) {
            return new SectionUpdate(buf.readUtf(), buf.readUtf(), buf.readInt(), HordeEditorPackets.readJson(buf), HordeEditorPackets.readJson(buf));
        }
        public static void handle(SectionUpdate pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.wavesurvivor.client.HordeEditorScreen.onSectionUpdate(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }
}
