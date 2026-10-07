package com.wavesurvivor.network;

import com.wavesurvivor.horde.kingdom.KingdomRoles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S→C : cibles suivies par le rôle du joueur (catalyseurs de l'Arcaniste, gisements du Mineur) pour la flèche du HUD. */
public class RoleTrackerPacket {

    /** « catalyst », « deposit » ou vide (aucune flèche). */
    public final String kind;
    /** x, y, z à la suite. */
    public final double[] points;

    public RoleTrackerPacket(String kind, double[] points) {
        this.kind = kind;
        this.points = points;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind);
        buf.writeVarInt(points.length);
        for (double d : points) buf.writeDouble(d);
    }

    public static RoleTrackerPacket decode(FriendlyByteBuf buf) {
        String k = buf.readUtf();
        int n = buf.readVarInt();
        double[] p = new double[n];
        for (int i = 0; i < n; i++) p[i] = buf.readDouble();
        return new RoleTrackerPacket(k, p);
    }

    public static void handle(RoleTrackerPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // « obj:… » = 2e flèche (objectif du Calme : trésor, foyers), indépendante de celle du rôle
            if (pkt.kind.startsWith("obj:")) {
                KingdomRoles.clientObjKind = pkt.points.length == 0 ? "" : pkt.kind.substring(4);
                KingdomRoles.clientObjTrack = pkt.points;
                return;
            }
            KingdomRoles.clientTrackKind = pkt.kind;
            KingdomRoles.clientTrack = pkt.points;
        });
        ctx.get().setPacketHandled(true);
    }
}
