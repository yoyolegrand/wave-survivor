package com.wavesurvivor.network;

import com.wavesurvivor.horde.renaissance.RenaissanceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S : action de la Forge de l'autel de Renaissance.
 *   « upgrade » + relique : monte la meilleure copie d'un niveau (consomme un doublon + PR) ;
 *   « fuse »    + id de fusion : fusionne deux reliques en une légendaire (lot 3).
 * Le serveur revalide tout (inventaire, niveaux, PR).
 */
public class RelicForgePacket {

    public final String action;
    public final String id;

    public RelicForgePacket(String action, String id) {
        this.action = action;
        this.id = id;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action, 32);
        buf.writeUtf(id, 256);
    }

    public static RelicForgePacket decode(FriendlyByteBuf buf) {
        return new RelicForgePacket(buf.readUtf(32), buf.readUtf(256));
    }

    public static void handle(RelicForgePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            switch (pkt.action) {
                case "upgrade" -> RenaissanceManager.forgeUpgrade(p, pkt.id);
                case "fuse" -> RenaissanceManager.forgeFuse(p, pkt.id);
                // Sans Curios : équiper (id = case de l'inventaire) / retirer (id = rang dans l'équipement)
                case "equip" -> { try { com.wavesurvivor.item.RelicEquip.equip(p, Integer.parseInt(pkt.id)); } catch (NumberFormatException ignored) {} }
                case "unequip" -> { try { com.wavesurvivor.item.RelicEquip.unequip(p, Integer.parseInt(pkt.id)); } catch (NumberFormatException ignored) {} }
                // Héritage (1.5) : acheter le prochain nœud d'une branche (id = SURVIVOR / MERCHANT / LORD), renâitre
                case "heritage" -> {
                    try { com.wavesurvivor.horde.renaissance.Heritage.buy(p, com.wavesurvivor.horde.renaissance.Heritage.Branch.valueOf(pkt.id)); }
                    catch (IllegalArgumentException ignored) {}
                    RenaissanceManager.sendState(p, false, 3, false);
                }
                case "rebirth" -> {
                    com.wavesurvivor.horde.renaissance.Heritage.rebirth(p);
                    RenaissanceManager.sendState(p, false, 3, false);
                }
                default -> { }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
