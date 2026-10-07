package com.wavesurvivor.network;

import com.wavesurvivor.horde.renaissance.RenaissanceConfigData;
import com.wavesurvivor.horde.renaissance.RenaissanceManager;
import com.wavesurvivor.horde.renaissance.RenaissanceRarityStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S : un op règle la rareté de sacrifice d'un item (écran Configuration de l'autel de Renaissance).
 * value : nom de rareté, "__none__" (exclu) ou "" (retour au comportement par défaut).
 */
public class RenaissanceRarityPacket {

    public final String itemId;
    public final String value;

    public RenaissanceRarityPacket(String itemId, String value) {
        this.itemId = itemId;
        this.value = value != null ? value : "";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(itemId);
        buf.writeUtf(value);
    }

    public static RenaissanceRarityPacket decode(FriendlyByteBuf buf) {
        return new RenaissanceRarityPacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(RenaissanceRarityPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            if (!p.hasPermissions(2)) {
                p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("§c[Renaissance] Réservé aux opérateurs.")));
                return;
            }
            RenaissanceConfigData cfg = RenaissanceConfigData.current();
            String v = pkt.value;
            if (!v.isEmpty() && !RenaissanceRarityStore.EXCLUDED.equals(v) && cfg.findRarity(v) == null) return;
            RenaissanceRarityStore.set(pkt.itemId, v);

            String label = v.isEmpty() ? com.wavesurvivor.i18n.WSLang.t("§7Défaut") : RenaissanceRarityStore.EXCLUDED.equals(v) ? com.wavesurvivor.i18n.WSLang.t("§8Exclu")
                    : cfg.findRarity(v).chatColor() + cfg.findRarity(v).name;
            p.displayClientMessage(Component.literal("§d[Renaissance] §f" + pkt.itemId + " §7→ " + label), true);
            // Config à jour côté client (écran ouvert rafraîchi, pas de réouverture)
            RenaissanceManager.sendState(p, false, RenaissanceManager.TAB_SACRIFICE, false);
        });
        ctx.get().setPacketHandled(true);
    }
}
