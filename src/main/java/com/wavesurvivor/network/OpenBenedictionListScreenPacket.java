package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet S->C : demande au client d'ouvrir le BenedictionListScreen (catalogue de toutes les bénédictions).
 * Payload vide — le client utilise son propre RogueUpgradeRegistry.
 */
public class OpenBenedictionListScreenPacket {

    /** Multiplicateur de difficulté des bonus (1.7), pour afficher les valeurs réelles. */
    private final float mult;

    /** Statistiques des bénédictions du monde (1.8) : identifiant → [proposée, choisie, bannie, parties, victoires]. */
    private final java.util.Map<String, int[]> stats;

    public OpenBenedictionListScreenPacket(float mult, java.util.Map<String, int[]> stats) { this.mult = mult; this.stats = stats; }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(mult);
        buf.writeVarInt(stats.size());
        for (java.util.Map.Entry<String, int[]> e : stats.entrySet()) {
            buf.writeUtf(e.getKey(), 160);
            for (int i = 0; i < 5; i++) buf.writeVarInt(e.getValue()[i]);
        }
    }

    public static OpenBenedictionListScreenPacket decode(FriendlyByteBuf buf) {
        float mult = buf.readFloat();
        int n = buf.readVarInt();
        java.util.Map<String, int[]> m = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf(160);
            int[] a = new int[5];
            for (int j = 0; j < 5; j++) a[j] = buf.readVarInt();
            m.put(k, a);
        }
        return new OpenBenedictionListScreenPacket(mult, m);
    }

    public static void handle(OpenBenedictionListScreenPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientPacketHandler.openBenedictionListScreen(pkt.mult, pkt.stats));
        });
        ctx.get().setPacketHandled(true);
    }
}
