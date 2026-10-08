package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * CARTE DU ROYAUME (onglet Town Hall › Carte). Le client la demande toutes les 2 s tant qu'elle est affichée ;
 * le serveur répond avec les marqueurs (positions relatives au Monolithe).
 */
public final class KingdomMapPackets {

    private KingdomMapPackets() {}

    /** Types de marqueurs. */
    public static final byte MONOLITH = 0, DEFENSE = 1, RAMPART = 2, TRAP = 3, GATE = 4, CATALYST = 5,
            DEPOSIT = 6, TREE = 7, PLAYER = 8, SELF = 9, FOYER = 10;

    /** Un marqueur : type, position relative au Monolithe, information en plus (type de défense…). */
    public record Marker(byte type, short dx, short dz, byte extra) {}

    // ─── C→S : demande ───

    public static class Request {
        public void encode(FriendlyByteBuf buf) {}

        public static Request decode(FriendlyByteBuf buf) { return new Request(); }

        public static void handle(Request pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p == null) return;
                Data d = com.wavesurvivor.horde.kingdom.KingdomMap.build(p);
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), d);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ─── S→C : données ───

    public static class Data {
        public final int claim;
        public final List<Marker> markers;

        public Data(int claim, List<Marker> markers) {
            this.claim = claim;
            this.markers = markers;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(claim);
            buf.writeVarInt(markers.size());
            for (Marker m : markers) {
                buf.writeByte(m.type());
                buf.writeShort(m.dx());
                buf.writeShort(m.dz());
                buf.writeByte(m.extra());
            }
        }

        public static Data decode(FriendlyByteBuf buf) {
            int claim = buf.readVarInt();
            int n = Math.min(4000, buf.readVarInt());
            List<Marker> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new Marker(buf.readByte(), buf.readShort(), buf.readShort(), buf.readByte()));
            return new Data(claim, l);
        }

        public static void handle(Data pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                CLIENT_CLAIM = pkt.claim;
                CLIENT = pkt.markers;
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Côté client : dernière carte reçue. */
    public static int CLIENT_CLAIM = -1;
    public static List<Marker> CLIENT = new ArrayList<>();

    // ─── C→S : régler la Boussole du Royaume sur un repère de la carte (clear = effacer la cible) ───

    public static class SetCompass {
        public final Marker target;
        public final boolean clear;

        public SetCompass(Marker target, boolean clear) {
            this.target = target;
            this.clear = clear;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(clear);
            buf.writeByte(target.type());
            buf.writeShort(target.dx());
            buf.writeShort(target.dz());
            buf.writeByte(target.extra());
        }

        public static SetCompass decode(FriendlyByteBuf buf) {
            boolean clear = buf.readBoolean();
            return new SetCompass(new Marker(buf.readByte(), buf.readShort(), buf.readShort(), buf.readByte()), clear);
        }

        public static void handle(SetCompass pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) com.wavesurvivor.horde.kingdom.KingdomMap.setCompass(p, pkt.target, pkt.clear);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
