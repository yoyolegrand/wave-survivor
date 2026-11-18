package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 2.1 — Tuto du Kingdom : HUD des joueurs accompagnés (serveur → client) et option / remise à zéro (client → serveur). */
public final class KingdomTutoPackets {

    private KingdomTutoPackets() {}

    /** Ligne de la liste « À faire » : texte, cochée ?, cible facultative (flèche de direction). */
    public record Line(String text, boolean done, boolean hasTarget, double x, double z) {}

    /** Serveur → client. */
    public static final class Hud {
        public static final int OFF = 0, BANNER = 1, TODO = 2, CYCLE = 3;

        public final int kind;
        /** BANNER : Assaut ? et durée du Calme en secondes (−1 = sans chrono). */
        public final boolean assault;
        public final int seconds;
        /** TODO : ligne d'en-tête (vide = aucune), lignes, rappel du bas. */
        public final String header, footer;
        public final List<Line> lines;

        private Hud(int kind, boolean assault, int seconds, String header, List<Line> lines, String footer) {
            this.kind = kind;
            this.assault = assault;
            this.seconds = seconds;
            this.header = header == null ? "" : header;
            this.lines = lines == null ? List.of() : lines;
            this.footer = footer == null ? "" : footer;
        }

        public static Hud off() { return new Hud(OFF, false, 0, null, null, null); }
        public static Hud cycleScreen() { return new Hud(CYCLE, false, 0, null, null, null); }
        public static Hud banner(boolean assault, int seconds) { return new Hud(BANNER, assault, seconds, null, null, null); }
        public static Hud todo(String header, List<Line> lines, String footer) { return new Hud(TODO, false, 0, header, lines, footer); }

        public void encode(FriendlyByteBuf b) {
            b.writeByte(kind);
            b.writeBoolean(assault);
            b.writeInt(seconds);
            b.writeUtf(header, 512);
            b.writeUtf(footer, 512);
            b.writeVarInt(lines.size());
            for (Line l : lines) {
                b.writeUtf(l.text(), 512);
                b.writeBoolean(l.done());
                b.writeBoolean(l.hasTarget());
                b.writeDouble(l.x());
                b.writeDouble(l.z());
            }
        }

        public static Hud decode(FriendlyByteBuf b) {
            int kind = b.readByte();
            boolean assault = b.readBoolean();
            int seconds = b.readInt();
            String header = b.readUtf(512), footer = b.readUtf(512);
            int n = Math.min(16, b.readVarInt());
            List<Line> lines = new ArrayList<>();
            for (int i = 0; i < n; i++) lines.add(new Line(b.readUtf(512), b.readBoolean(), b.readBoolean(), b.readDouble(), b.readDouble()));
            return new Hud(kind, assault, seconds, header, lines, footer);
        }

        public static void handle(Hud pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.wavesurvivor.client.KingdomTutoClient.receive(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client → serveur : « optout » / « optin » (option client), « reset » (Codex › Revoir le tuto). */
    public static final class Action {
        public final String action;

        public Action(String action) { this.action = action == null ? "" : action; }

        public void encode(FriendlyByteBuf b) { b.writeUtf(action, 32); }

        public static Action decode(FriendlyByteBuf b) { return new Action(b.readUtf(32)); }

        public static void handle(Action pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p == null) return;
                switch (pkt.action) {
                    case "optout" -> com.wavesurvivor.horde.kingdom.KingdomTuto.setOptOut(p, true);
                    case "optin" -> com.wavesurvivor.horde.kingdom.KingdomTuto.setOptOut(p, false);
                    case "reset" -> {
                        com.wavesurvivor.horde.kingdom.KingdomTuto.reset(p, "");
                        p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("kingdom.tuto.reset_self"), true);
                    }
                    default -> { }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
