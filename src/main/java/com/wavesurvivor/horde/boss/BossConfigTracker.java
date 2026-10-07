package com.wavesurvivor.horde.boss;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomEntityData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registre des boss actifs qui ont un bossConfig (playerAura, selfEffects, summon).
 * Alimenté par BossManager.spawnBoss et vidé par BossManager.onBossDeath/updateBars/clearAll.
 * Tick par BossConfigTicker.
 */
public class BossConfigTracker {

    private static final Map<UUID, BossState> ACTIVE = new HashMap<>();

    public static class BossState {
        public final CustomEntityData ce;
        public final BossConfigParsed cfg;
        public final long spawnTick;

        // Timestamps du dernier trigger (ticks serveur)
        public long playerAuraLastTick = -1;
        public long selfEffectsLastTick = -1;
        public long summonLastTick = -1;

        // Flags pour triggeredOnce (healthThreshold)
        public boolean selfEffectsTriggered = false;
        public boolean summonTriggered = false;

        // UUID des adds encore vivants (spawn via summon)
        public final List<UUID> activeSummons = new ArrayList<>();

        BossState(CustomEntityData ce, BossConfigParsed cfg, long spawnTick) {
            this.ce = ce;
            this.cfg = cfg;
            this.spawnTick = spawnTick;
        }
    }

    /**
     * Enregistre un boss pour tick des mécaniques bossConfig.
     * No-op si ce.bossConfig vide ou toutes les mécaniques désactivées.
     */
    public static void register(UUID bossUuid, CustomEntityData ce, long currentTick) {
        if (ce == null || ce.bossConfig == null || ce.bossConfig.isEmpty()) return;
        BossConfigParsed cfg = BossConfigParsed.from(ce.bossConfig);
        if (cfg == null || cfg.isEmpty()) {
            WaveSurvivorMod.LOGGER.info("[BossConfig] '{}' : bossConfig vide/désactivé, skip register.",
                    ce.entityName);
            return;
        }
        ACTIVE.put(bossUuid, new BossState(ce, cfg, currentTick));
        WaveSurvivorMod.LOGGER.info("[BossConfig] '{}' enregistré (aura={} selfFx={} summon={})",
                ce.entityName, cfg.hasPlayerAura(), cfg.hasSelfEffects(), cfg.hasSummon());
    }

    public static void unregister(UUID uuid) {
        BossState removed = ACTIVE.remove(uuid);
        if (removed != null) {
            WaveSurvivorMod.LOGGER.info("[BossConfig] '{}' unregister (uuid={}).", removed.ce.entityName, uuid);
        }
    }

    public static void clearAll() {
        int n = ACTIVE.size();
        ACTIVE.clear();
        if (n > 0) WaveSurvivorMod.LOGGER.info("[BossConfig] clearAll : {} boss purgés.", n);
    }

    public static Map<UUID, BossState> all() {
        return ACTIVE;
    }

    public static int size() {
        return ACTIVE.size();
    }
}
