package com.wavesurvivor.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * FICHE DE DÉFENSE (Kingdom) : clic droit sur une défense → le serveur envoie son état (Sheet) ; le joueur agit depuis
 * l'écran (Action) : améliorer, spécialiser, réparer, démonter (50 % remboursés), donner un ordre (Baraquement),
 * ouvrir le stockage (Glaneur) ou l'atelier.
 */
public final class DefenseSheetPackets {

    private DefenseSheetPackets() {}

    /** Un coût : ressource (ordinal de KingdomTreasury.Res), quantité, quantité disponible. */
    public record Cost(int res, int count, int have) {}

    /** Une spécialisation proposée : identifiant et coût. */
    public record Variant(String id, List<Cost> cost) {}

    // ─── S→C : état de la défense ───

    public static class Sheet {
        public final BlockPos pos;
        public final int kind, level, townTier, soldiers, soldiersMax;
        public final String variant, order;
        public final float hp, maxHp;
        public final int repairCost, refund;
        public final List<Cost> next;          // niveau 1 → 2
        public final List<Variant> variants;   // niveau 2 → 3

        public Sheet(BlockPos pos, int kind, int level, String variant, float hp, float maxHp, int townTier, int repairCost,
                     int refund, List<Cost> next, List<Variant> variants, String order, int soldiers, int soldiersMax) {
            this.pos = pos; this.kind = kind; this.level = level; this.variant = variant; this.hp = hp; this.maxHp = maxHp;
            this.townTier = townTier; this.repairCost = repairCost; this.refund = refund; this.next = next; this.variants = variants;
            this.order = order; this.soldiers = soldiers; this.soldiersMax = soldiersMax;
        }

        private static void writeCosts(FriendlyByteBuf b, List<Cost> l) {
            b.writeVarInt(l.size());
            for (Cost c : l) { b.writeVarInt(c.res()); b.writeVarInt(c.count()); b.writeVarInt(c.have()); }
        }

        private static List<Cost> readCosts(FriendlyByteBuf b) {
            int n = Math.min(16, b.readVarInt());
            List<Cost> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(new Cost(b.readVarInt(), b.readVarInt(), b.readVarInt()));
            return l;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeVarInt(kind); b.writeVarInt(level); b.writeUtf(variant, 32);
            b.writeFloat(hp); b.writeFloat(maxHp); b.writeVarInt(townTier);
            b.writeVarInt(repairCost); b.writeVarInt(refund);
            writeCosts(b, next);
            b.writeVarInt(variants.size());
            for (Variant v : variants) { b.writeUtf(v.id(), 32); writeCosts(b, v.cost()); }
            b.writeUtf(order, 16); b.writeVarInt(soldiers); b.writeVarInt(soldiersMax);
        }

        public static Sheet decode(FriendlyByteBuf b) {
            BlockPos pos = b.readBlockPos();
            int kind = b.readVarInt(), level = b.readVarInt();
            String variant = b.readUtf(32);
            float hp = b.readFloat(), maxHp = b.readFloat();
            int town = b.readVarInt(), repair = b.readVarInt(), refund = b.readVarInt();
            List<Cost> next = readCosts(b);
            int nv = Math.min(8, b.readVarInt());
            List<Variant> vs = new ArrayList<>();
            for (int i = 0; i < nv; i++) vs.add(new Variant(b.readUtf(32), readCosts(b)));
            String order = b.readUtf(16);
            int soldiers = b.readVarInt(), max = b.readVarInt();
            return new Sheet(pos, kind, level, variant, hp, maxHp, town, repair, refund, next, vs, order, soldiers, max);
        }

        public static void handle(Sheet pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.wavesurvivor.client.DefenseSheetScreen.show(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ─── C→S : action ───

    public static class Action {
        public final BlockPos pos;
        public final String action, arg;

        public Action(BlockPos pos, String action, String arg) {
            this.pos = pos; this.action = action; this.arg = arg == null ? "" : arg;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeUtf(action, 16);
            b.writeUtf(arg, 32);
        }

        public static Action decode(FriendlyByteBuf b) {
            return new Action(b.readBlockPos(), b.readUtf(16), b.readUtf(32));
        }

        public static void handle(Action pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) com.wavesurvivor.horde.kingdom.KingdomDefenses.sheetAction(p, pkt.pos, pkt.action, pkt.arg);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
