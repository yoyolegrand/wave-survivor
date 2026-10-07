package com.wavesurvivor.network;

import com.wavesurvivor.altar.AltarDefense;
import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Boutique d'améliorations du monolithe (pendant une horde en Défense du Monolithe).
 *   State   (S→C) : PV, niveaux, coûts, réglages → ouvre ou rafraîchit l'écran
 *   Upgrade (C→S) : achat d'un niveau (0 = Renfort, 1 = Régénération, 2 = Blindage)
 */
public final class MonolithShopPackets {

    private MonolithShopPackets() {}

    public static class State {
        public final boolean open;
        public final BlockPos pos;
        public final int hp, maxHp;
        public final int[] levels;
        public final int[] costs;
        public final int maxLevel;
        public final String currency;
        public final int hpPerLevel;
        public final double regenPerLevel;
        public final int armorPerLevel;
        /** Mode Kingdom : niveau de la Mairie (0 = pas de royaume) et rayon actuel du claim. */
        public int townTier = 0, claimRadius = 0;

        public State(boolean open, BlockPos pos, int hp, int maxHp, int[] levels, int[] costs, int maxLevel,
                     String currency, int hpPerLevel, double regenPerLevel, int armorPerLevel) {
            this.open = open; this.pos = pos; this.hp = hp; this.maxHp = maxHp;
            this.levels = levels; this.costs = costs; this.maxLevel = maxLevel; this.currency = currency;
            this.hpPerLevel = hpPerLevel; this.regenPerLevel = regenPerLevel; this.armorPerLevel = armorPerLevel;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(open);
            buf.writeBlockPos(pos);
            buf.writeVarInt(hp);
            buf.writeVarInt(maxHp);
            for (int i = 0; i < 3; i++) { buf.writeVarInt(levels[i]); buf.writeVarInt(costs[i]); }
            buf.writeVarInt(maxLevel);
            buf.writeUtf(currency);
            buf.writeVarInt(hpPerLevel);
            buf.writeDouble(regenPerLevel);
            buf.writeVarInt(armorPerLevel);
            buf.writeVarInt(townTier);
            buf.writeVarInt(claimRadius);
        }

        public static State decode(FriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            BlockPos pos = buf.readBlockPos();
            int hp = buf.readVarInt(), maxHp = buf.readVarInt();
            int[] lv = new int[3], co = new int[3];
            for (int i = 0; i < 3; i++) { lv[i] = buf.readVarInt(); co[i] = buf.readVarInt(); }
            int maxLevel = buf.readVarInt();
            String cur = buf.readUtf();
            int hpl = buf.readVarInt();
            double rpl = buf.readDouble();
            int apl = buf.readVarInt();
            State st = new State(open, pos, hp, maxHp, lv, co, maxLevel, cur, hpl, rpl, apl);
            st.townTier = buf.readVarInt();
            st.claimRadius = buf.readVarInt();
            return st;
        }

        public static void handle(State pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleMonolithShop(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Upgrade {
        public final int type;
        public Upgrade(int type) { this.type = type; }
        public void encode(FriendlyByteBuf buf) { buf.writeVarInt(type); }
        public static Upgrade decode(FriendlyByteBuf buf) { return new Upgrade(buf.readVarInt()); }
        public static void handle(Upgrade pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) AltarDefense.upgrade(p, pkt.type);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
