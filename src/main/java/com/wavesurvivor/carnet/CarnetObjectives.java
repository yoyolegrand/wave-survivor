package com.wavesurvivor.carnet;

import com.wavesurvivor.horde.renaissance.RenaissanceStore;
import com.wavesurvivor.horde.stats.PlayerStats;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.EventFeedPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OBJECTIFS DU CARNET : chaque objectif suit un compteur de {@link PlayerStats} (le même que les succès) ; il est validé quand le compteur
 * a progressé d'au moins « threshold » depuis la remise du carnet. Validé = récompense + annonce dans le fil d'événements.
 * Appelé par PlayerStats à chaque changement de compteur.
 */
public final class CarnetObjectives {

    /** État d'un objectif pour l'écran : progression déjà plafonnée au seuil. */
    public record State(CarnetConfig.Objective objective, long progress, boolean done) {}

    private CarnetObjectives() {}

    /** Progression d'un compteur depuis la remise du carnet. */
    private static long progress(CarnetData.Entry e, CarnetConfig.Objective o, Map<String, Long> stats) {
        return Math.max(0, stats.getOrDefault(o.stat(), 0L) - e.base.getOrDefault(o.stat(), 0L));
    }

    /** Valide les objectifs atteints (sans effet pour un joueur sans carnet). */
    public static void check(ServerPlayer p, Map<String, Long> stats) {
        if (p == null || p.getServer() == null) return;
        CarnetData data = CarnetData.get(p.getServer());
        CarnetData.Entry e = data.find(p.getUUID());
        if (e == null || !e.given) return;
        for (CarnetConfig.Objective o : CarnetConfig.objectives()) {
            if (e.done.contains(o.id()) || progress(e, o, stats) < o.threshold()) continue;
            e.done.add(o.id());
            data.setDirty();
            reward(p, o);
        }
    }

    private static void reward(ServerPlayer p, CarnetConfig.Objective o) {
        if (o.pr() > 0) RenaissanceStore.addPoints(p, o.pr());
        for (CarnetConfig.Reward r : o.items()) {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(r.item()));
            if (it == null || it == net.minecraft.world.item.Items.AIR) continue;
            ItemStack st = new ItemStack(it, r.count());
            if (!p.getInventory().add(st)) p.drop(st, false);
        }
        EventFeedPacket.toPlayer(p, Component.literal(WSLang.t("carnet.done", WSLang.t("carnet.obj." + o.id()))),
                "minecraft:writable_book", EventFeedPacket.RESOURCE, true);
    }

    /** États de tous les objectifs pour ce joueur (écran du carnet). */
    public static List<State> states(ServerPlayer p) {
        List<State> out = new ArrayList<>();
        CarnetData.Entry e = CarnetData.get(p.getServer()).find(p.getUUID());
        Map<String, Long> stats = PlayerStats.snapshotOf(p);
        for (CarnetConfig.Objective o : CarnetConfig.objectives()) {
            boolean done = e != null && e.done.contains(o.id());
            long prog = e == null ? 0 : done ? o.threshold() : Math.min(o.threshold(), progress(e, o, stats));
            out.add(new State(o, prog, done));
        }
        return out;
    }
}
