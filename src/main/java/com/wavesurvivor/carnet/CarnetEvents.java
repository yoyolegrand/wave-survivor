package com.wavesurvivor.carnet;

import com.wavesurvivor.horde.stats.PlayerStats;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.item.RelicEffects;
import com.wavesurvivor.item.RelicEquip;
import com.wavesurvivor.network.CarnetPacket;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * CARNET DU SURVIVANT : remise à la première connexion (avec les compteurs de départ), détection d'une relique équipée
 * (aucun événement ne l'annonce : vérifié toutes les 5 secondes tant que l'objectif n'est pas fait) et ouverture par commande.
 */
public class CarnetEvents {

    /** Compteur « relique équipée » (valeur 1 dès qu'une relique est portée une fois). */
    public static final String RELIC_STAT = "relic_equipped";

    /** Remet un carnet (déjà reçu ou non) : à la connexion, ou par /ws carnet quand le joueur n'en a plus. */
    public static boolean hasCarnet(ServerPlayer p) {
        return p.getInventory().hasAnyMatching(s -> s.is(ModItems.SURVIVOR_NOTEBOOK.get()));
    }

    public static void giveCarnet(ServerPlayer p) {
        ItemStack st = new ItemStack(ModItems.SURVIVOR_NOTEBOOK.get());
        if (!p.getInventory().add(st)) p.drop(st, false);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.getServer() == null || !CarnetConfig.autoGive()) return;
        CarnetData data = CarnetData.get(p.getServer());
        CarnetData.Entry entry = data.getOrCreate(p.getUUID());
        if (entry.given) return;
        entry.given = true;
        entry.base.putAll(PlayerStats.snapshotOf(p)); // pas de récompense pour ce qui a déjà été accompli
        data.setDirty();
        giveCarnet(p);
        p.sendSystemMessage(Component.literal(WSLang.t("carnet.welcome")));
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.getServer() == null) return;
        if (p.tickCount % 100 != 0 || p.isSpectator()) return;
        CarnetData.Entry entry = CarnetData.get(p.getServer()).find(p.getUUID());
        if (entry == null || !entry.given || entry.done.contains("relic")) return;
        if (anyRelicEquipped(p)) PlayerStats.max(p, RELIC_STAT, 1);
    }

    /** Une relique est-elle équipée (emplacement Curios « Relique », ou autel de Renaissance sans Curios) ? */
    private static boolean anyRelicEquipped(ServerPlayer p) {
        if (!RelicEffects.curiosAvailable()) return !RelicEquip.list(p).isEmpty();
        for (Item it : ForgeRegistries.ITEMS) {
            if (it instanceof com.wavesurvivor.item.RelicItem || it instanceof com.wavesurvivor.item.LegendaryRelicItem) {
                if (RelicEffects.hasRelic(p, it)) return true;
            }
        }
        return false;
    }

    public static void open(ServerPlayer p) {
        if (!hasCarnet(p)) giveCarnet(p);
        CarnetPacket.openFor(p);
    }
}
