package com.wavesurvivor.network;

import com.google.gson.Gson;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.renaissance.RenaissanceConfigData;
import com.wavesurvivor.horde.renaissance.RenaissanceManager;
import com.wavesurvivor.horde.renaissance.RenaissanceShopStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet C→S (op only) : édition de la boutique Renaissance en jeu.
 *   action SAVE   : payload = ShopItem JSON (id vide = nouvelle relique)
 *   action DELETE : payload = id de la relique
 */
public class RenaissanceShopEditPacket {

    public static final int SAVE = 0;
    public static final int DELETE = 1;

    private static final Gson GSON = new Gson();
    private static final int MAX = 65536;

    public final int action;
    public final String payload;

    public RenaissanceShopEditPacket(int action, String payload) {
        this.action = action;
        this.payload = payload != null ? payload : "";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUtf(payload, MAX);
    }

    public static RenaissanceShopEditPacket decode(FriendlyByteBuf buf) {
        return new RenaissanceShopEditPacket(buf.readVarInt(), buf.readUtf(MAX));
    }

    public static void handle(RenaissanceShopEditPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            if (!p.hasPermissions(2)) {
                p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("§c✗ Il faut être op pour éditer la boutique.")));
                return;
            }
            try {
                if (pkt.action == DELETE) {
                    RenaissanceShopStore.remove(pkt.payload);
                    p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("§d[Boutique] §fRelique retirée de la boutique.")));
                } else {
                    RenaissanceConfigData.ShopItem s = GSON.fromJson(pkt.payload, RenaissanceConfigData.ShopItem.class);
                    if (s == null || s.item == null || s.item.isBlank()) {
                        p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("§c✗ Aucun item sélectionné.")));
                        return;
                    }
                    s.count = Math.max(1, Math.min(64, s.count));
                    s.cost = Math.max(0, s.cost);
                    s.maxPerPlayer = Math.max(0, s.maxPerPlayer);
                    if (s.id == null || s.id.isBlank()) {
                        s.id = "custom_" + s.item.replace(':', '_') + "_" + Long.toString(System.currentTimeMillis(), 36);
                    }
                    ItemStack test = RenaissanceConfigData.buildStack(s);
                    if (test.isEmpty()) {
                        p.sendSystemMessage(Component.literal("§c✗ Item invalide : " + s.item));
                        return;
                    }
                    RenaissanceShopStore.put(s);
                    p.sendSystemMessage(Component.literal("§d[Boutique] §a✓ §f" + test.getHoverName().getString()
                            + " §7— " + s.cost + " PR" + (s.maxPerPlayer > 0 ? com.wavesurvivor.i18n.WSLang.t(", max ") + s.maxPerPlayer + com.wavesurvivor.i18n.WSLang.t("/joueur") : com.wavesurvivor.i18n.WSLang.t(", illimité"))));
                }
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[RenaissanceShopEdit] {}", e.getMessage(), e);
                p.sendSystemMessage(Component.literal("§c✗ Erreur : " + e.getMessage()));
            }
            RenaissanceManager.sendState(p, false, RenaissanceManager.TAB_SHOP, false);
        });
        ctx.get().setPacketHandled(true);
    }
}
