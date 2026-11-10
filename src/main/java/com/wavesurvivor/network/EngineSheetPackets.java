package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * FICHE D'ENGIN DE SIÈGE (Faille) : Maj + clic droit sur une catapulte / une baliste → le serveur envoie son état (Sheet) ;
 * le joueur améliore ou choisit une variante depuis l'écran (Action), comme pour les tours du Kingdom.
 */
public final class EngineSheetPackets {

    private EngineSheetPackets() {}

    /** S→C : état de l'engin (identifiant d'entité, type 0 catapulte / 1 baliste, niveau, variante, prix, monnaie disponible). */
    public static class Sheet {
        public final int entity, kind, level, variant, upCost, varCost, have, ammo, hp, maxHp, repairCost;

        public Sheet(int entity, int kind, int level, int variant, int upCost, int varCost, int have, int ammo, int hp, int maxHp, int repairCost) {
            this.entity = entity; this.kind = kind; this.level = level; this.variant = variant;
            this.upCost = upCost; this.varCost = varCost; this.have = have; this.ammo = ammo;
            this.hp = hp; this.maxHp = maxHp; this.repairCost = repairCost;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(entity); b.writeVarInt(kind); b.writeVarInt(level); b.writeVarInt(variant);
            b.writeVarInt(upCost); b.writeVarInt(varCost); b.writeVarInt(have); b.writeVarInt(ammo);
            b.writeVarInt(hp); b.writeVarInt(maxHp); b.writeVarInt(repairCost);
        }

        public static Sheet decode(FriendlyByteBuf b) {
            return new Sheet(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt());
        }

        public static void handle(Sheet pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.wavesurvivor.client.EngineSheetScreen.show(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** C→S : « upgrade » (niveau 2) ou « variant0 » à « variant2 » (niveau 3). */
    public static class Action {
        public final int entity;
        public final String action;

        public Action(int entity, String action) {
            this.entity = entity; this.action = action == null ? "" : action;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(entity);
            b.writeUtf(action, 16);
        }

        public static Action decode(FriendlyByteBuf b) {
            return new Action(b.readVarInt(), b.readUtf(16));
        }

        public static void handle(Action pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) com.wavesurvivor.horde.rift.RiftManager.engineSheetAction(p, pkt.entity, pkt.action);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
