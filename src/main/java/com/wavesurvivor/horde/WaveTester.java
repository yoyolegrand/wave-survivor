package com.wavesurvivor.horde;

import com.google.gson.Gson;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.boss.BossManager;
import com.wavesurvivor.horde.model.BossWave;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.spawn.HordeSpawner;
import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * « ▶ TESTER LA VAGUE » de l'éditeur : fait apparaître autour du joueur les monstres (et boss) de la vague N de la
 * horde EN COURS D'ÉDITION, sans lancer la horde (aucune récompense, aucune progression). Impossible pendant une horde.
 * « ✖ Nettoyer » les retire ; marqués comme unités de horde, ils disparaissent aussi au rechargement du monde.
 */
public final class WaveTester {

    private WaveTester() {}

    private static final Gson GSON = new Gson();
    /** Plafond de monstres par test (évite de geler le serveur sur une vague énorme). */
    private static final int MAX_MOBS = 60;
    private static final Set<UUID> TEST = new HashSet<>();
    private static boolean testBosses = false;

    public static void test(ServerPlayer p, String json, int wave) {
        if (HordeManager.get().isRunning()) {
            p.sendSystemMessage(WSLang.c("test.busy"));
            return;
        }
        HordeConfigMultiData h;
        try {
            h = GSON.fromJson(json, HordeConfigMultiData.class);
        } catch (Exception e) {
            p.sendSystemMessage(WSLang.c("test.failed", e.getMessage()));
            return;
        }
        if (h == null || h.configData == null) return;
        ServerLevel lvl = p.serverLevel();
        Set<UUID> before = new HashSet<>();
        for (Map.Entry<UUID, HordeEntity> en : MobRegistry.entries()) before.add(en.getKey());
        int total = 0;
        boolean capped = false;
        if (h.configData.hordeEntities != null) {
            for (HordeEntity t : h.configData.hordeEntities) {
                int n = t.getCountForWave(wave, 1);
                if (n <= 0) continue;
                if (total + n > MAX_MOBS) { n = MAX_MOBS - total; capped = true; }
                if (n <= 0) break;
                total += HordeSpawner.spawnMobs(lvl, t, p.blockPosition(), 10, n, 1);
            }
        }
        int bosses = 0;
        if (h.configData.bossWaves != null) {
            for (BossWave w : h.configData.bossWaves) {
                if (w.waveNumber != wave) continue;
                if (BossManager.spawnBoss(lvl, w, p.blockPosition(), 10, p.getServer())) { bosses++; testBosses = true; }
            }
        }
        for (Map.Entry<UUID, HordeEntity> en : MobRegistry.entries()) {
            if (before.contains(en.getKey())) continue;
            TEST.add(en.getKey());
            Entity e = lvl.getEntity(en.getKey());
            if (e != null) {
                e.getPersistentData().putBoolean("ws_test_mob", true);
                e.getPersistentData().putBoolean("ws_horde_unit", true); // retiré au rechargement du monde
            }
        }
        p.sendSystemMessage(WSLang.c("test.done", total, bosses, wave));
        if (capped) p.sendSystemMessage(WSLang.c("test.capped", MAX_MOBS));
    }

    public static void clear(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        int removed = 0;
        for (UUID id : TEST) {
            for (ServerLevel l : server.getAllLevels()) {
                Entity e = l.getEntity(id);
                if (e != null) {
                    l.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
                    e.discard();
                    removed++;
                    break;
                }
            }
            MobRegistry.remove(id);
        }
        TEST.clear();
        // Boss de test : aucune horde ne tourne, tous les boss actifs viennent du test
        if (testBosses && !HordeManager.get().isRunning()) BossManager.clearAll(server);
        testBosses = false;
        p.sendSystemMessage(WSLang.c("test.cleared", removed));
    }
}
