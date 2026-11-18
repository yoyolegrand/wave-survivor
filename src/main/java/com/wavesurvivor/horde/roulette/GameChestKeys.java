package com.wavesurvivor.horde.roulette;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.altar.AltarLayout;
import com.wavesurvivor.altar.AltarRecipes;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.loot.LootItems;
import com.wavesurvivor.horde.rift.RiftManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2.1 — Clés lâchées par les monstres COHÉRENTES avec les coffres roulette de la partie.
 * Pendant une partie (horde, Kingdom, Faille), une clé de butin « roulettechest:key_X » dont le coffre X n'apparaît jamais aux pauses
 * (ex. les zombies de la Faille qui lâchaient des clés de Reliquaires) devient la clé d'un coffre qui, lui, est posé aux pauses.
 *  - Coffres de référence : ceux réellement posés depuis le début de la partie ; avant la première pause, ceux prévus
 *    (Faille : les N premiers coffres auto ; horde : emplacements enregistrés + emplacements et coffres auto de la zone d'autel).
 *  - Hors partie, ou si aucun coffre n'est connu : aucune correction.
 */
public final class GameChestKeys {

    private GameChestKeys() {}

    private static final String KEY_PREFIX = "roulettechest:key_";
    private static final Set<String> PLACED = ConcurrentHashMap.newKeySet();
    private static final Random RNG = new Random();

    /** Un coffre roulette vient d'être posé pour la partie (RouletteChestSpawner). */
    public static void placed(String configKey) {
        if (configKey != null && !configKey.isBlank()) PLACED.add(configKey);
    }

    /** Fin de partie (HordeManager.reset). */
    public static void clear() {
        PLACED.clear();
    }

    private static boolean inGame() {
        HordeManager hm = HordeManager.get();
        return RiftManager.isActive() || (hm != null && hm.isRunning() && hm.getActiveHorde() != null);
    }

    /** Coffres de la partie (clés de config). Vide = inconnu. */
    static List<String> allowed() {
        if (!inGame()) return List.of();
        if (!PLACED.isEmpty()) return new ArrayList<>(PLACED);
        Set<String> out = new LinkedHashSet<>();
        if (RiftManager.isActive()) {
            AltarRecipes.Zone z = new AltarRecipes.Zone();
            z.maxChests = Math.max(0, RiftManager.settingsPublic().riftChests);
            for (RouletteChestConfig c : AltarLayout.autoChestConfigs(z)) out.add(c.configKey());
            return new ArrayList<>(out);
        }
        String horde = HordeManager.get().getActiveHorde().hordeName;
        var spawns = RouletteChestSpawnStore.getSpawns(horde);
        if (spawns != null) for (RouletteChestSpawnStore.SpawnPoint sp : spawns) if (RouletteChestRegistry.get(sp.configKey) != null) out.add(sp.configKey);
        AltarRecipes.Zone zone = AltarRecipes.peekZone(horde);
        if (zone != null && zone.slots != null)
            for (AltarRecipes.Slot s : zone.slots)
                if ("chest".equals(s.type) && s.configKey != null && RouletteChestRegistry.get(s.configKey) != null) out.add(s.configKey);
        if (zone == null || zone.autoLayout) for (RouletteChestConfig c : AltarLayout.autoChestConfigs(zone)) out.add(c.configKey());
        return new ArrayList<>(out);
    }

    /** Identifiant de butin éventuellement corrigé : une clé d'un coffre absent de la partie → clé d'un coffre de la partie, au hasard. */
    public static String remap(String itemId) {
        if (itemId == null || !itemId.trim().startsWith(KEY_PREFIX)) return itemId;
        List<String> ok = allowed();
        if (ok.isEmpty()) return itemId;
        RouletteChestConfig cfg = LootItems.findChestConfig(itemId.trim().substring(KEY_PREFIX.length()));
        if (cfg != null && ok.contains(cfg.configKey())) return itemId;
        String pick = ok.get(RNG.nextInt(ok.size()));
        WaveSurvivorMod.LOGGER.debug("[Loot] Clé '{}' remplacée par '{}' (coffre absent de la partie)", itemId, pick);
        return KEY_PREFIX + pick;
    }
}
