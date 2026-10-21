package com.wavesurvivor.horde.benediction;

import com.google.gson.JsonObject;
import com.wavesurvivor.horde.editor.HordeJson;
import com.wavesurvivor.horde.roulette.RouletteReward;
import com.wavesurvivor.horde.spawn.HordeUnitGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * LA CHANCE HORS BÉNÉDICTIONS (1.8) : l'attribut Luck du joueur (bénédictions de Chance, potion, équipement) améliore aussi
 *   • les récompenses du Brasier d'offrandes du Kingdom (les meilleures sortent plus souvent) ;
 *   • les coffres roulette (les lots de haute rareté pèsent davantage) ;
 *   • le butin des monstres de horde : chance qu'un monstre tué lâche un lot supplémentaire (au plus un par monstre).
 * Même formule que le tirage des bénédictions (rendements décroissants, plafond) ; chaque usage a son interrupteur et son décalage
 * dans blessings.json › settings › luck (uses, roulette.shift, brazier.shift, extraLoot.maxChance).
 */
public class BlessingLuck {

    private static final Random RNG = new Random();

    private static JsonObject luck() {
        JsonObject s = BlessingCatalog.settings();
        return s.has("luck") && s.get("luck").isJsonObject() ? s.getAsJsonObject("luck") : new JsonObject();
    }

    private static JsonObject child(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    /** Cet usage de la Chance est-il actif ? (réglage « uses », vrai par défaut) */
    public static boolean on(String use) {
        return HordeJson.bool(child(luck(), "uses"), use, true);
    }

    /** Bonus de Chance du joueur : 0 à maxBonus. */
    public static double bonus(ServerPlayer p) {
        JsonObject l = luck();
        return BlessingDraw.luckBonus(p, HordeJson.dbl(l, "scale", 12), HordeJson.dbl(l, "maxBonus", 1.0));
    }

    // ─── Coffres roulette ───

    /** Recalcule les poids des lots selon la Chance du joueur (les raretés hautes gagnent du poids, les basses en perdent). */
    public static List<RouletteReward> adjustRoulette(List<RouletteReward> rewards, ServerPlayer p) {
        if (p == null || !on("roulette")) return rewards;
        double b = bonus(p);
        if (b <= 0.001) return rewards;
        JsonObject shift = child(child(luck(), "roulette"), "shift");
        double[] def = {-0.3, -0.1, 0.2, 0.6, 1.2};
        List<RouletteReward> out = new ArrayList<>();
        for (RouletteReward r : rewards) {
            double k = HordeJson.dbl(shift, r.rarity().name(), def[r.rarity().ordinal()]);
            out.add(new RouletteReward(r.item(), r.minQty(), r.maxQty(), r.chance() * Math.max(0.05, 1 + b * k), r.rarity(), r.displayName()));
        }
        return out;
    }

    // ─── Brasier d'offrandes ───

    /** Facteur de poids d'une récompense du Brasier (« reliquary », « blessing », « pr »…). */
    public static double brazierFactor(ServerPlayer p, String reward) {
        if (p == null || !on("brazier")) return 1;
        double b = bonus(p);
        if (b <= 0.001) return 1;
        JsonObject shift = child(child(luck(), "brazier"), "shift");
        double k = HordeJson.dbl(shift, reward, 0);
        return Math.max(0.05, 1 + b * k);
    }

    // ─── Butin supplémentaire des monstres ───

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrops(LivingDropsEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer killer) || e.getDrops().isEmpty()) return;
        if (!HordeUnitGuard.isHordeEntity(e.getEntity()) || !on("extraLoot")) return;
        double chance = bonus(killer) * HordeJson.dbl(child(luck(), "extraLoot"), "maxChance", 0.5);
        if (chance <= 0 || RNG.nextDouble() >= chance) return;
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity d : e.getDrops()) {
            extra.add(new ItemEntity(e.getEntity().level(), d.getX(), d.getY(), d.getZ(), d.getItem().copy()));
        }
        e.getDrops().addAll(extra);
        if (e.getEntity().level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, e.getEntity().getX(), e.getEntity().getY() + 0.8,
                    e.getEntity().getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }
    }
}
