package com.wavesurvivor.horde;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.benediction.RogueUpgradeManager;
import com.wavesurvivor.horde.boss.BossManager;
import com.wavesurvivor.horde.chaos.ChaosApplier;
import com.wavesurvivor.horde.chaos.ChaosTracker;
import com.wavesurvivor.horde.hud.HordeHudManager;
import com.wavesurvivor.horde.merchant.MerchantSpawner;
import com.wavesurvivor.horde.roulette.RouletteChestSpawner;
import com.wavesurvivor.horde.model.BossWave;
import com.wavesurvivor.horde.model.ChaosEvent;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.model.MultiSpawnEntry;
import com.wavesurvivor.horde.model.SpawnMessages;
import com.wavesurvivor.horde.model.SpecialWave;
import com.wavesurvivor.horde.model.WavePauseEntry;
import com.wavesurvivor.horde.rewards.KillTracker;
import com.wavesurvivor.horde.rewards.RewardsDistributor;
import com.wavesurvivor.horde.spawn.HordeSpawner;
import com.wavesurvivor.horde.spawn.MobRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class HordeManager {

    private static final long WAVE_CLEAR_GRACE_TICKS = 100L;
    private static final long WAVE_MAX_DURATION_TICKS = 12000L;

    public enum State { IDLE, RUNNING, WAITING_NEXT_WAVE, PAUSED, FINISHED }

    private static final HordeManager INSTANCE = new HordeManager();
    public static HordeManager get() { return INSTANCE; }
    private static final Random RNG = new Random();

    private State state = State.IDLE;
    private HordeConfigMultiData activeHorde;
    private int currentWave = 0;
    private long startedAtTick = -1L;
    private long nextActionTick = -1L;
    private long waveStartedAtTick = -1L;
    private long nextChaosTick = -1L;
    /** Durée totale en secondes du délai courant (WAITING_NEXT_WAVE ou PAUSED), pour le HUD countdown. */
    private int currentDelaySeconds = 0;
    /** Position override pour le spawn (utilisé par les altars). Si non-null, remplace cfg.spawnCoords. */
    private BlockPos overrideSpawnPos = null;
    /** Dimension override pour le spawn (utilisé par les altars). */
    private String overrideSpawnDim = null;
    private MinecraftServer server;
    /** Vague spéciale imposée pour la prochaine vague (commande /ws special). */
    private String forcedSpecialWave = null;

    /** @return le nom exact de la vague si elle existe dans la horde en cours, sinon null. */
    public String forceSpecialWave(String name) {
        if (activeHorde == null || activeHorde.configData == null || activeHorde.configData.specialWaves == null) return null;
        for (SpecialWave sw : activeHorde.configData.specialWaves) {
            if (sw.name != null && sw.name.equalsIgnoreCase(name.trim())) {
                forcedSpecialWave = sw.name;
                return sw.name;
            }
        }
        return null;
    }

    private HordeManager() {}

    /**
     * Lance une horde à une position custom (celle d'un altar).
     * Le spawn center + dimension configurés dans la horde sont ignorés le temps de cette instance.
     */
    public boolean startAt(HordeConfigMultiData horde, MinecraftServer srv, BlockPos overridePos, String overrideDim) {
        this.overrideSpawnPos = overridePos;
        this.overrideSpawnDim = overrideDim;
        boolean ok = start(horde, srv);
        if (!ok) {
            // Nettoie l'override si le start a échoué
            this.overrideSpawnPos = null;
            this.overrideSpawnDim = null;
        } else {
            // Niveau de difficulté choisi sur l'écran de l'autel (Normal par défaut)
            com.wavesurvivor.horde.difficulty.HordeDifficulty.begin(srv);
            // Nouvelle horde (ou reprise) : l'ancienne sauvegarde de partie est remplacée (ré-enregistrée d'ici 30 s)
            HordeSession.discard(srv);
            // Mutateurs cochés sur l'écran de l'autel : activés pour cette horde (« Une seule vie » exclue en Kingdom)
            com.wavesurvivor.horde.mutator.HordeMutators.begin(srv, horde.configData != null && horde.configData.isKingdom());
            // 1.6 — Défi du jour : la horde lancée correspond-elle au défi (difficulté + 3 mutateurs) ?
            com.wavesurvivor.horde.daily.DailyServer.onHordeStart(srv, horde.configData != null && horde.configData.isKingdom());
            // 1.5 — Héritage : émeraudes et clé de départ (Marchand), coup fatal évité rechargé (Survivant)
            com.wavesurvivor.horde.renaissance.Heritage.onHordeStart(srv);
            // 1.5 — PR gagnés en jouant : compteurs de la partie
            com.wavesurvivor.horde.renaissance.RenaissanceRewards.reset(horde.configData != null && horde.configData.isKingdom());
            // Spawn naturel bloqué : on chasse aussi les monstres sauvages déjà présents (avant l'arrivée de la horde)
            if (horde.configData != null && horde.configData.blockNaturalSpawns) HordeSafety.purgeHostiles(srv);
        }
        return ok;
    }

    /** Renvoie le centre de spawn effectif (override si présent, sinon cfg.spawnCoords, sinon position du premier joueur, sinon 0,64,0). */
    private BlockPos getSpawnCenter(HordeConfigMultiData.ConfigDataInner cfg) {
        if (overrideSpawnPos != null) return overrideSpawnPos;
        if (cfg.spawnCoords != null) return new BlockPos(cfg.spawnCoords.x, cfg.spawnCoords.y, cfg.spawnCoords.z);
        // Pas de coordonnées fixes (config d'origine du mod) : la horde se lance là où se trouve le joueur
        if (server != null && !server.getPlayerList().getPlayers().isEmpty()) {
            if (fallbackCenter == null) fallbackCenter = server.getPlayerList().getPlayers().get(0).blockPosition();
            return fallbackCenter;
        }
        return new BlockPos(0, 64, 0);
    }

    /** Centre mémorisé pour toute la horde quand elle a été lancée sans coordonnées fixes ni autel. */
    private BlockPos fallbackCenter = null;

    public boolean start(HordeConfigMultiData horde, MinecraftServer srv) {
        if (state == State.RUNNING || state == State.WAITING_NEXT_WAVE || state == State.PAUSED) {
            WaveSurvivorMod.LOGGER.warn("[HordeManager] Horde déjà en cours.");
            return false;
        }
        this.activeHorde = horde;
        this.server = srv;
        this.currentWave = 0;
        this.startedAtTick = srv.getTickCount();
        this.nextActionTick = srv.getTickCount() + 40;
        this.state = State.WAITING_NEXT_WAVE;
        scheduleNextChaos(srv);

        KillTracker.startTracking();
        MobRegistry.clear();
        int totalWaves = horde.configData != null ? horde.configData.totalWaves : 0;
        HordeHudManager.show(srv, com.wavesurvivor.i18n.WSLang.t(horde.hordeName), totalWaves);
        // Défense du Monolithe (seulement si lancée depuis un autel et activée pour cette horde)
        com.wavesurvivor.altar.AltarDefense.onHordeStart(srv, horde.hordeName, overrideSpawnDim, overrideSpawnPos);

        HordeConfigMultiData.ConfigDataInner cfg = horde.configData;
        if (cfg != null) {
            int nEnt = cfg.hordeEntities != null ? cfg.hordeEntities.size() : 0;
            int nBoss = cfg.bossWaves != null ? cfg.bossWaves.size() : 0;
            String coords = cfg.spawnCoords != null
                    ? "(" + cfg.spawnCoords.x + "," + cfg.spawnCoords.y + "," + cfg.spawnCoords.z + com.wavesurvivor.i18n.WSLang.t("srv.dim") + cfg.spawnCoords.dimension
                    : com.wavesurvivor.i18n.WSLang.t("srv.aucune");
            WaveSurvivorMod.LOGGER.info("[HordeManager] start '{}' : {} vagues, {} entités template, {} boss, radius={}, coords={}",
                    horde.hordeName, totalWaves, nEnt, nBoss, cfg.spawnRadius, coords);
            // Zone de spawn sûre : scan du cercle de spawn dès le lancement (blocs bloquants / dangereux exclus)
            com.wavesurvivor.horde.spawn.SpawnZone.clear();
            try {
                ServerLevel zl = resolveLevel(cfg);
                if (zl != null) com.wavesurvivor.horde.spawn.SpawnZone.prepare(zl, getSpawnCenter(cfg), cfg.spawnRadius);
            } catch (Exception ex) {
                WaveSurvivorMod.LOGGER.warn("[HordeManager] Scan de la zone de spawn impossible : {}", ex.getMessage());
            }
        }

        WaveSurvivorMod.LOGGER.info("[HordeManager] Horde '{}' démarrée ({} vagues).", horde.hordeName, totalWaves);
        broadcast(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.horde_dcd7") + com.wavesurvivor.i18n.WSLang.t(horde.hordeName) + com.wavesurvivor.i18n.WSLang.t("srv.commence_dans_2_secondes")).withStyle(ChatFormatting.GOLD));

        // Mode Kingdom : portails cardinaux + cycle Assaut / Calme (remplace les vagues)
        if (cfg != null && cfg.isKingdom()) {
            ServerLevel kl = resolveLevel(cfg);
            if (kl != null) {
                HordeHudManager.hide();
                this.state = State.RUNNING;
                com.wavesurvivor.horde.kingdom.KingdomManager.start(srv, horde, kl, getSpawnCenter(cfg));
                return true;
            }
        }

        // Les marchands sont spawnés ENTRE les vagues (voir bloc "fin de vague" dans tick),
        // pas au start de la horde.

        return true;
    }

    /** Arrêt sans le message « Horde arrêtée » (fin de « La Chute du Monolithe », qui affiche son propre bilan). */
    private boolean quietStop = false;

    /** Horde classique : boss final retenu dans son arène (portail ouvert une fois la dernière vague nettoyée). */
    private BossWave raidBoss;

    /** Dernière vague nettoyée : portail ouvert à 5 blocs de l'autel vers l'arène du boss final. */
    private void openClassicRaid(HordeConfigMultiData.ConfigDataInner cfg, MinecraftServer srv) {
        ServerLevel level = resolveLevel(cfg);
        BlockPos home = getSpawnCenter(cfg);
        BossWave b = raidBoss;
        raidBoss = null;
        if (level == null || home == null || b == null) {
            finishHorde(srv);
            return;
        }
        BlockPos g = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, home.offset(5, 0, 0));
        com.wavesurvivor.horde.kingdom.RaidManager.open(level, g, home, b, cfg.raidRadius, () -> finishHorde(srv), "raid.open_classic");
    }

    /**
     * Reprise d'une partie sauvegardée (juste après startAt) : la vague {@code wave} démarre dans 5 s ;
     * la durée de jeu déjà écoulée est conservée (bilan, statistiques).
     */
    public void resumeAt(int wave, long elapsedTicks) {
        if (server == null) return;
        long now = server.getTickCount();
        this.currentWave = Math.max(0, wave - 1);
        this.state = State.WAITING_NEXT_WAVE;
        this.nextActionTick = now + 100;
        this.currentDelaySeconds = 5;
        if (elapsedTicks > 0) this.startedAtTick = now - elapsedTicks;
    }

    public boolean stopSilently() {
        quietStop = true;
        try { return stop(); } finally { quietStop = false; }
    }

    public boolean stop() {
        if (state == State.IDLE || state == State.FINISHED) return false;
        // Défaite réelle (Monolithe tombé, équipe vaincue) : PR des vagues acquises ; un arrêt manuel ne rapporte rien
        if (com.wavesurvivor.horde.renaissance.RenaissanceRewards.consumeDefeat() && activeHorde != null) {
            com.wavesurvivor.horde.renaissance.RenaissanceRewards.payout(server, activeHorde, false);
        }
        String name = activeHorde != null ? activeHorde.hordeName : "?";
        WaveSurvivorMod.LOGGER.info("[HordeManager] Horde '{}' arrêtée manuellement (vague {}).", name, currentWave);
        if (!quietStop) broadcast(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.horde_d97e") + com.wavesurvivor.i18n.WSLang.t(name) + com.wavesurvivor.i18n.WSLang.t("srv.arretee")).withStyle(ChatFormatting.RED));
        killAllActiveMobs();
        com.wavesurvivor.altar.AltarDefense.onHordeEnd(server, false);
        com.wavesurvivor.horde.skill.NecroTracker.clearAll();
        com.wavesurvivor.horde.boss.LicheController.clearAll(server);
        com.wavesurvivor.horde.breach.BreachManager.clearAll(server);
        com.wavesurvivor.horde.kingdom.KingdomManager.stop(server);
        com.wavesurvivor.horde.boss.CavalierController.clearAll(server);
        com.wavesurvivor.horde.anomaly.AnomalyManager.clearAll();
        com.wavesurvivor.horde.boss.XaltorController.clearAll(server);
        com.wavesurvivor.horde.chaos.TotemAuraManager.clearAll(server);
        com.wavesurvivor.horde.skill.MagicBolts.clearAll();
        BossManager.clearAll(server);
        ChaosTracker.clearAll(server);
        KillTracker.stopTracking();
        HordeHudManager.hide();
        MobRegistry.clear();
        RogueUpgradeManager.clearAllPlayers(server);
        MerchantSpawner.despawnAll(server);
        RouletteChestSpawner.despawnAll(server);
        reset();
        return true;
    }

    public boolean skip() {
        if (state == State.IDLE || state == State.FINISHED || activeHorde == null || server == null) return false;
        // Mode Kingdom : passe directement à la phase suivante (Assaut ↔ Calme)
        if (com.wavesurvivor.horde.kingdom.KingdomManager.isActive()) return com.wavesurvivor.horde.kingdom.KingdomManager.skipPhase(server);
        long now = server.getTickCount();

        if (state == State.RUNNING) {
            int killed = killAllActiveMobs();
            BossManager.clearAll(server);
            broadcast(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.vague_12a0") + currentWave + com.wavesurvivor.i18n.WSLang.t("srv.passee") + killed + com.wavesurvivor.i18n.WSLang.t("srv.mob_s_elimine_s"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));

            HordeConfigMultiData.ConfigDataInner cfg = activeHorde.configData;

            // Dernière vague skip → terminer la horde immédiatement
            if (cfg != null && currentWave >= cfg.totalWaves) {
                finishHorde(server);
                return true;
            }

            WavePauseEntry pause = (cfg != null) ? findWavePause(cfg, currentWave) : null;
            state = (pause != null) ? State.PAUSED : State.WAITING_NEXT_WAVE;
            nextActionTick = now + 20;
        } else {
            broadcast(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.delai_passe_vague_suivante_immediatement"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            nextActionTick = now;
        }
        return true;
    }

    public String forceChaos(int index) {
        if (state == State.IDLE || state == State.FINISHED || activeHorde == null || activeHorde.configData == null) {
            return com.wavesurvivor.i18n.WSLang.t("srv.aucune_horde_active_986e");
        }
        HordeConfigMultiData.ConfigDataInner cfg = activeHorde.configData;
        if (cfg.chaosEvents == null || cfg.chaosEvents.isEmpty()) {
            return com.wavesurvivor.i18n.WSLang.t("srv.cette_horde_n_a_aucun_chaos_event_config");
        }

        ChaosEvent picked;
        if (index < 0) {
            picked = cfg.chaosEvents.get(RNG.nextInt(cfg.chaosEvents.size()));
        } else if (index < cfg.chaosEvents.size()) {
            picked = cfg.chaosEvents.get(index);
        } else {
            return com.wavesurvivor.i18n.WSLang.t("srv.index") + index + com.wavesurvivor.i18n.WSLang.t("srv.hors_limites_0_a") + (cfg.chaosEvents.size() - 1) + ")";
        }

        ServerLevel level = resolveLevel(cfg);
        if (level == null) return com.wavesurvivor.i18n.WSLang.t("srv.impossible_de_resoudre_le_level");
        BlockPos center = new BlockPos(
                cfg.spawnCoords != null ? cfg.spawnCoords.x : 0,
                cfg.spawnCoords != null ? cfg.spawnCoords.y : 64,
                cfg.spawnCoords != null ? cfg.spawnCoords.z : 0);
        ChaosApplier.apply(picked, level, center, server);
        WaveSurvivorMod.LOGGER.info("[Chaos] Force '{}' (type={}) via /ws chaos", picked.entityType, picked.type);
        return null;
    }

    public List<ChaosEvent> listChaosEvents() {
        if (activeHorde == null || activeHorde.configData == null) return null;
        return activeHorde.configData.chaosEvents;
    }

    private int killAllActiveMobs() {
        if (server == null) return 0;
        int count = 0;
        for (Map.Entry<UUID, HordeEntity> e : MobRegistry.entries()) {
            Entity ent = findEntityByUuid(server, e.getKey());
            if (ent != null && ent.isAlive()) {
                ent.discard();
                count++;
            }
        }
        MobRegistry.clear();
        return count;
    }

    private static Entity findEntityByUuid(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }

    /** Termine la horde immédiatement (broadcast + rewards + cleanup + reset). */
    /**
     * Mode Kingdom — début du Calme : Bénédictions pour chaque joueur, puis marchands + coffres roulette
     * (même logique que la pause entre deux vagues classiques).
     */
    /** Noms des marchands de la horde, dans l'ordre (disposition : un emplacement par marchand). */
    private static java.util.List<String> merchantNames(HordeConfigMultiData.ConfigDataInner cfg) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (cfg.useMerchants && cfg.merchants != null) for (var m : cfg.merchants) out.add(m.name == null ? "" : m.name);
        return out;
    }

    public void kingdomCalmStart(MinecraftServer srv, int cycle) {
        if (activeHorde == null || activeHorde.configData == null) return;
        HordeConfigMultiData.ConfigDataInner cfg = activeHorde.configData;
        RogueUpgradeManager.openMenuForAll(srv, cycle);
        ServerLevel merchLvl = resolveLevel(cfg);
        com.wavesurvivor.altar.AltarStore.AltarEntry altarEntry = (overrideSpawnPos != null && overrideSpawnDim != null)
                ? com.wavesurvivor.altar.AltarStore.get(overrideSpawnDim, overrideSpawnPos) : null;
        if (merchLvl != null && altarEntry != null) {
            // Kingdom : la disposition couvre tout le claim (au moins le rayon de la zone de l'autel)
            int radius = Math.max(altarEntry.zoneRadius, Math.max(4, cfg.kingdom().claimRadius));
            var zone = com.wavesurvivor.altar.AltarRecipes.peekZone(activeHorde.hordeName);
            var layout = com.wavesurvivor.altar.AltarLayout.compute(merchLvl, overrideSpawnPos, radius, zone, merchantNames(cfg));
            MerchantSpawner.spawnAll(activeHorde, merchLvl, layout.merchants(), overrideSpawnPos);
            RouletteChestSpawner.spawnAtSpots(merchLvl, layout.chests());
        } else {
            if (merchLvl != null) MerchantSpawner.spawnAll(activeHorde, merchLvl);
            RouletteChestSpawner.spawnAll(activeHorde.hordeName, srv);
        }
    }

    /** Mode Kingdom — fin du Calme : marchands et coffres repartent. */
    public void kingdomCalmEnd(MinecraftServer srv) {
        MerchantSpawner.despawnAll(srv);
        RouletteChestSpawner.despawnAll(srv);
    }

    /** Mode Kingdom — événements du chaos pendant les Assauts (même réglage que les hordes classiques). */
    public void kingdomChaosTick(MinecraftServer srv) { kingdomChaosTick(srv, false); }

    /** @param resourceOnly Calme : seuls les événements de ressources (gisement, arbre) sont tirés. */
    public void kingdomChaosTick(MinecraftServer srv, boolean resourceOnly) {
        if (activeHorde == null || activeHorde.configData == null) return;
        HordeConfigMultiData.ConfigDataInner cfg = activeHorde.configData;
        if (!cfg.chaosEnabled || cfg.chaosEvents == null || cfg.chaosEvents.isEmpty()) return;
        if (nextChaosTick < 0) scheduleNextChaos(srv);
        if (srv.getTickCount() >= nextChaosTick) {
            triggerRandomChaos(cfg, srv, resourceOnly);
            scheduleNextChaos(srv);
        }
    }

    /** Événement de ressources (bénéfique) : gisement de minerai ou arbre. */
    public static boolean isResourceEvent(ChaosEvent ev) {
        return ev != null && ("gisement".equalsIgnoreCase(ev.type) || "arbre".equalsIgnoreCase(ev.type));
    }

    /** Mode Kingdom — tirage d'un « assaut spécial » (même chance et mêmes poids que les vagues spéciales). */
    public SpecialWave kingdomPickSpecial() {
        return activeHorde == null || activeHorde.configData == null ? null : pickSpecialWave(activeHorde.configData);
    }

    /**
     * Fin d'une partie Kingdom.
     * @param victory vrai : les 4 portails sont détruits → unités restantes retirées, récompenses et fin normale.
     *                faux : limite de sécurité atteinte → arrêt sans récompense.
     */
    public void endKingdom(boolean victory) {
        if (server == null) return;
        if (victory) {
            killAllActiveMobs();
            finishHorde(server);
        } else {
            stop();
        }
    }

    private void finishHorde(MinecraftServer srv) {
        String name = activeHorde != null ? activeHorde.hordeName : "?";
        WaveSurvivorMod.LOGGER.info("[HordeManager] Horde '{}' TERMINÉE.", name);
        broadcast(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.horde_59a3") + com.wavesurvivor.i18n.WSLang.t(name) + com.wavesurvivor.i18n.WSLang.t("srv.terminee")).withStyle(ChatFormatting.GREEN));
        if (activeHorde != null) RewardsDistributor.distribute(activeHorde, srv);
        // PR gagnés en jouant (avant l'enregistrement de la victoire : on sait si c'est la première)
        if (activeHorde != null) com.wavesurvivor.horde.renaissance.RenaissanceRewards.payout(srv, activeHorde, true);
        // Progression : horde terminée (au niveau de difficulté en cours) pour les joueurs connectés
        if (activeHorde != null) com.wavesurvivor.horde.difficulty.HordeProgress.recordWin(srv, activeHorde.hordeName);
        com.wavesurvivor.altar.AltarDefense.onHordeEnd(srv, true);
        com.wavesurvivor.horde.skill.NecroTracker.clearAll();
        com.wavesurvivor.horde.boss.LicheController.clearAll(srv);
        com.wavesurvivor.horde.breach.BreachManager.clearAll(srv);
        com.wavesurvivor.horde.kingdom.KingdomManager.stop(srv);
        com.wavesurvivor.horde.boss.CavalierController.clearAll(srv);
        com.wavesurvivor.horde.anomaly.AnomalyManager.clearAll();
        com.wavesurvivor.horde.boss.XaltorController.clearAll(srv);
        com.wavesurvivor.horde.chaos.TotemAuraManager.clearAll(srv);
        com.wavesurvivor.horde.skill.MagicBolts.clearAll();
        BossManager.clearAll(srv);
        ChaosTracker.clearAll(srv);
        HordeHudManager.hide();
        MobRegistry.clear();
        KillTracker.stopTracking();
        RogueUpgradeManager.clearAllPlayers(srv);
        MerchantSpawner.despawnAll(srv);
        RouletteChestSpawner.despawnAll(srv);
        state = State.FINISHED;
        reset();
    }

    public void tick(MinecraftServer srv) {
        if (state != State.RUNNING && state != State.WAITING_NEXT_WAVE && state != State.PAUSED) return;
        if (activeHorde == null || activeHorde.configData == null) return;

        // Mode Kingdom : tout le déroulement est géré par le KingdomManager
        if (com.wavesurvivor.horde.kingdom.KingdomManager.isActive()) {
            com.wavesurvivor.horde.kingdom.KingdomManager.tick(srv);
            return;
        }

        long now = srv.getTickCount();
        HordeConfigMultiData.ConfigDataInner cfg = activeHorde.configData;

        // Sauvegarde de partie toutes les 30 s (reprise possible même après un plantage)
        if (now % 600 == 0) HordeSession.capture(srv);

        // Arène du boss final ouverte : la horde est en pause, seul le raid tourne
        if (com.wavesurvivor.horde.kingdom.RaidManager.isOpen()) {
            com.wavesurvivor.horde.kingdom.RaidManager.tickFromHorde(now);
            com.wavesurvivor.horde.boss.BossManager.updateBars(srv);
            return;
        }

        // Dispatch HUD selon state
        if (state == State.RUNNING) {
            HordeHudManager.update(currentWave, cfg.totalWaves);
        } else if (state == State.WAITING_NEXT_WAVE || state == State.PAUSED) {
            int secondsRemaining = (int) Math.max(0, (nextActionTick - now) / 20);
            HordeHudManager.updateCountdown(currentWave, cfg.totalWaves,
                    secondsRemaining, currentDelaySeconds, state == State.PAUSED);
        }

        if (now % 20 == 0 && cfg.gameOverRadius > 0 && cfg.gameOverRadius < 1000) {
            checkGameOverRadius(cfg, srv);
            if (state == State.IDLE) return;
        }

        if (cfg.chaosEnabled && cfg.chaosEvents != null && !cfg.chaosEvents.isEmpty()
                && now >= nextChaosTick && state == State.RUNNING) {
            triggerRandomChaos(cfg, srv);
            scheduleNextChaos(srv);
        }

        if (state == State.RUNNING) {
            tickPendingSpawns(now);
            // Fin de vague forcée : ≤ 10 % de la vague en vie → compte à rebours, puis les survivantes disparaissent
            boolean forced = WaveCleanup.tick(srv, now, pendingSpawns.isEmpty(), cfg.forceEndSeconds);
            boolean cleared = forced || isWaveClear(now);
            boolean timeout = (now - waveStartedAtTick) > WAVE_MAX_DURATION_TICKS;
            if (cleared || timeout) {
                if (timeout) {
                    WaveSurvivorMod.LOGGER.warn("[HordeManager] Vague {} timeout, passe au suivant.", currentWave);
                }
                // Brèches encore ouvertes : se referment en renforçant la horde ; renforts retirés
                com.wavesurvivor.horde.breach.BreachManager.onWaveEnd(srv);
                if (cleared) com.wavesurvivor.horde.renaissance.RenaissanceRewards.onWaveCleared(); // PR : vague terminée
                // Horde classique : gisements / arbres garantis pendant la pause entre deux vagues
                if (cleared && !com.wavesurvivor.horde.kingdom.KingdomManager.isActive()) spawnGuaranteedResources(srv);
                // Dernière vague clear → terminer immédiatement (pas de délai)
                if (currentWave >= cfg.totalWaves) {
                    broadcast(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.vague_5e08") + currentWave + com.wavesurvivor.i18n.WSLang.t("srv.nettoyee")).withStyle(ChatFormatting.GREEN));
                    // Arène du boss final : un portail s'ouvre près de l'autel au lieu de terminer la horde
                    if (raidBoss != null) {
                        openClassicRaid(cfg, srv);
                        return;
                    }
                    finishHorde(srv);
                    return;
                }
                WavePauseEntry pause = findWavePause(cfg, currentWave);
                state = (pause != null) ? State.PAUSED : State.WAITING_NEXT_WAVE;
                long delaySec = (pause != null) ? pause.pauseDuration : cfg.delayBetweenWaves;
                nextActionTick = now + Math.max(20, delaySec * 20L);
                currentDelaySeconds = (int) delaySec;
                broadcast(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.vague_5e08") + currentWave + com.wavesurvivor.i18n.WSLang.t("srv.nettoyee")).withStyle(ChatFormatting.GREEN));
                if (pause != null) {
                    broadcast(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.pause_plus_longue") + pause.pauseDuration + "s").withStyle(ChatFormatting.AQUA));
                }
                // Bénédictions : proposer un don à chaque joueur pour cette vague terminée
                RogueUpgradeManager.openMenuForAll(srv, currentWave);

                // Marchands + roulette chests : spawn pendant la pause entre vagues
                ServerLevel merchLvl = resolveLevel(cfg);
                com.wavesurvivor.altar.AltarStore.AltarEntry altarEntry = (overrideSpawnPos != null && overrideSpawnDim != null)
                        ? com.wavesurvivor.altar.AltarStore.get(overrideSpawnDim, overrideSpawnPos) : null;
                if (merchLvl != null && altarEntry != null && altarEntry.zoneRadius > 0) {
                    // Horde lancée depuis un autel avec zone : disposition auto/manuelle dans la zone
                    var zone = com.wavesurvivor.altar.AltarRecipes.peekZone(activeHorde.hordeName);
                    var layout = com.wavesurvivor.altar.AltarLayout.compute(merchLvl, overrideSpawnPos,
                            altarEntry.zoneRadius, zone, merchantNames(cfg));
                    MerchantSpawner.spawnAll(activeHorde, merchLvl, layout.merchants(), overrideSpawnPos);
                    RouletteChestSpawner.spawnAtSpots(merchLvl, layout.chests());
                } else {
                    if (merchLvl != null) {
                        MerchantSpawner.spawnAll(activeHorde, merchLvl);
                    }
                    // Roulette chests : spawn pendant la pause entre vagues (même cycle que marchands)
                    RouletteChestSpawner.spawnAll(activeHorde.hordeName, srv);
                }
            }
            return;
        }

        if (now < nextActionTick) return;

        if (state == State.WAITING_NEXT_WAVE || state == State.PAUSED) {
            // Marchands : despawn (avec poof + son) au moment où la vague suivante va démarrer
            MerchantSpawner.despawnAll(srv);
            // Roulette chests : despawn (même cycle)
            RouletteChestSpawner.despawnAll(srv);

            currentWave++;

            if (currentWave > cfg.totalWaves) {
                finishHorde(srv);
                return;
            }

            spawnWave(currentWave, cfg);
            com.wavesurvivor.horde.renaissance.Heritage.onWaveStart(srv); // Héritage : Survivant 2
            SpecialWave chosenSpecial = pickSpecialWave(cfg);
            if (chosenSpecial != null) {
                spawnSpecialWave(currentWave, cfg, chosenSpecial);
                broadcastWaveMessage(cfg, currentWave, chosenSpecial.name, true);
            } else {
                broadcastWaveMessage(cfg, currentWave, null, false);
            }
            spawnBossesForWave(currentWave, cfg);

            // Brèches infernales (horde avec breaches.enabled) : s'ouvrent toutes les N vagues
            {
                ServerLevel bl = resolveLevel(cfg);
                BlockPos bc = overrideSpawnPos != null ? overrideSpawnPos : getSpawnCenter(cfg);
                var ae = (overrideSpawnPos != null && overrideSpawnDim != null)
                        ? com.wavesurvivor.altar.AltarStore.get(overrideSpawnDim, overrideSpawnPos) : null;
                com.wavesurvivor.horde.breach.BreachManager.onWaveStart(bl, activeHorde.hordeName, currentWave, bc,
                        ae != null ? ae.zoneRadius : 0);
            }

            state = State.RUNNING;
            waveStartedAtTick = now;
            WaveCleanup.reset();
        }
    }

    private boolean isWaveClear(long now) {
        if (waveStartedAtTick < 0) return false;
        if (!pendingSpawns.isEmpty()) return false; // des monstres attendent encore d'apparaître
        if ((now - waveStartedAtTick) < WAVE_CLEAR_GRACE_TICKS) return false;
        return MobRegistry.size() + BossManager.activeBossCount() == 0;
    }

    private void checkGameOverRadius(HordeConfigMultiData.ConfigDataInner cfg, MinecraftServer srv) {
        BlockPos center = getSpawnCenter(cfg);
        int rSq = cfg.gameOverRadius * cfg.gameOverRadius;

        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            double dx = p.getX() - center.getX();
            double dz = p.getZ() - center.getZ();
            double dSq = dx * dx + dz * dz;
            if (dSq > rSq) {
                broadcast(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.game_over") + p.getName().getString()
                        + com.wavesurvivor.i18n.WSLang.t("srv.est_trop_loin_de_la_zone_rayon") + cfg.gameOverRadius + ").").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                stop();
                return;
            }
        }
    }

    private void scheduleNextChaos(MinecraftServer srv) {
        if (activeHorde == null || activeHorde.configData == null) return;
        HordeConfigMultiData.ConfigDataInner cfg = activeHorde.configData;
        if (!cfg.chaosEnabled) { nextChaosTick = -1; return; }
        int min = Math.max(10, cfg.chaosMinInterval);
        int max = Math.max(min + 1, cfg.chaosMaxInterval);
        int sec = min + RNG.nextInt(max - min + 1);
        nextChaosTick = srv.getTickCount() + sec * 20L;
        WaveSurvivorMod.LOGGER.info("[Chaos] Prochain event dans {}s", sec);
    }

    private void triggerRandomChaos(HordeConfigMultiData.ConfigDataInner cfg, MinecraftServer srv) {
        triggerRandomChaos(cfg, srv, false);
    }

    /**
     * Gisements et arbres GARANTIS (réglage « Garantis par Calme » de l'éditeur) : à chaque Calme (Kingdom) ou pause
     * entre deux vagues (classique), chaque événement concerné apparaît N fois, un à la fois toutes les 6 s.
     */
    public void spawnGuaranteedResources(MinecraftServer srv) {
        if (srv == null || activeHorde == null || activeHorde.configData == null || activeHorde.configData.chaosEvents == null) return;
        int k = 0;
        for (ChaosEvent ev : activeHorde.configData.chaosEvents) {
            if (ev == null || !isResourceEvent(ev) || ev.guaranteed <= 0) continue;
            for (int i = 0; i < Math.min(5, ev.guaranteed); i++) {
                final ChaosEvent e = ev;
                int delay = 100 + (k++) * 120;
                com.wavesurvivor.horde.skill.DelayedActionScheduler.schedule(srv, delay, () -> {
                    if (!isRunning() || activeHorde == null || activeHorde.configData == null) return;
                    ServerLevel lvl = resolveLevel(activeHorde.configData);
                    if (lvl == null) return;
                    BlockPos c = com.wavesurvivor.horde.kingdom.KingdomManager.isActive()
                            ? com.wavesurvivor.horde.kingdom.KingdomManager.center() : getSpawnCenter(activeHorde.configData);
                    if (c != null) ChaosApplier.apply(e, lvl, c, srv);
                }, "guaranteed resource");
            }
        }
    }

    private void triggerRandomChaos(HordeConfigMultiData.ConfigDataInner cfg, MinecraftServer srv, boolean resourceOnly) {
        List<ChaosEvent> events = cfg.chaosEvents;
        if (events == null || events.isEmpty()) return;
        if (resourceOnly) {
            List<ChaosEvent> res = new java.util.ArrayList<>();
            for (ChaosEvent ev : events) if (isResourceEvent(ev)) res.add(ev);
            if (res.isEmpty()) return;
            events = res;
        }
        ChaosEvent picked = events.get(RNG.nextInt(events.size()));
        ServerLevel level = resolveLevel(cfg);
        if (level == null) return;
        // Mode Kingdom : le chaos tombe entre le claim et les Portes (jamais dans le royaume ni sur une Porte)
        BlockPos center = com.wavesurvivor.horde.kingdom.KingdomManager.isActive()
                ? com.wavesurvivor.horde.kingdom.KingdomManager.chaosCenter()
                : getSpawnCenter(cfg);
        ChaosApplier.apply(picked, level, center, srv);
        // Exploitant présent (Kingdom) : 50 % de chance qu'un gisement ou un arbre de plus accompagne un autre événement
        if (com.wavesurvivor.horde.kingdom.KingdomRoles.present(com.wavesurvivor.horde.kingdom.KingdomRoles.Role.MINER)
                && !isResourceEvent(picked) && RNG.nextDouble() < 0.5) {
            List<ChaosEvent> deposits = new java.util.ArrayList<>();
            for (ChaosEvent ev : cfg.chaosEvents) if (isResourceEvent(ev)) deposits.add(ev);
            if (!deposits.isEmpty()) {
                ChaosApplier.apply(deposits.get(RNG.nextInt(deposits.size())), level,
                        com.wavesurvivor.horde.kingdom.KingdomManager.chaosCenter(), srv);
            }
        }
    }

    private SpecialWave pickSpecialWave(HordeConfigMultiData.ConfigDataInner cfg) {
        // Vague forcée par /ws special <nom> (tests) : prioritaire sur le tirage
        if (forcedSpecialWave != null && cfg.specialWaves != null) {
            String want = forcedSpecialWave;
            forcedSpecialWave = null;
            for (SpecialWave sw : cfg.specialWaves) if (want.equalsIgnoreCase(sw.name)) return sw;
        }
        if (!cfg.useSpecialWaves || cfg.specialWaves == null || cfg.specialWaves.isEmpty()) return null;
        if (cfg.specialWaveChance > 0 && RNG.nextInt(100) >= cfg.specialWaveChance) return null;
        int totalWeight = 0;
        // chance = poids relatif ; 0 = vague désactivée (jamais tirée)
        for (SpecialWave sw : cfg.specialWaves) totalWeight += Math.max(0, sw.chance);
        if (totalWeight <= 0) return null;
        int r = RNG.nextInt(totalWeight);
        int cum = 0;
        for (SpecialWave sw : cfg.specialWaves) {
            if (sw.chance <= 0) continue;
            cum += sw.chance;
            if (r < cum) return sw;
        }
        return null;
    }

    private void spawnSpecialWave(int wave, HordeConfigMultiData.ConfigDataInner cfg, SpecialWave sw) {
        if (sw.entities == null || sw.entities.isEmpty()) return;
        ServerLevel level = resolveLevel(cfg);
        if (level == null) return;
        int playerCount = Math.max(1, server.getPlayerList().getPlayerCount());
        int radius = Math.max(1, cfg.spawnRadius);
        BlockPos center = getSpawnCenter(cfg);

        int total = 0;
        for (SpecialWave.SpecialWaveEntity swe : sw.entities) {
            HordeEntity h = swe.toHordeEntity();
            int count = h.getCountForWave(wave, playerCount);
            queueSpawn(level, h, center, radius, count, playerCount);
            int done = Math.max(0, count);
            total += done;
        }
        WaveSurvivorMod.LOGGER.info("[SpecialWave '{}' - Wave {}] {} mobs en plus des normaux.", sw.name, wave, total);
    }

    private void broadcastWaveMessage(HordeConfigMultiData.ConfigDataInner cfg, int wave, String specialName, boolean isSpecial) {
        SpawnMessages sm = cfg.spawnMessages;
        String template;
        String soundId = null;
        if (sm != null) {
            template = isSpecial ? sm.specialWave : sm.normalWave;
            if (sm.useSound) {
                soundId = isSpecial ? sm.specialSound : sm.normalSound;
            }
        } else {
            template = com.wavesurvivor.i18n.WSLang.t("srv.vague_wave_total");
        }
        if (template == null || template.isBlank()) {
            template = com.wavesurvivor.i18n.WSLang.t("srv.vague_wave_total");
        }
        template = com.wavesurvivor.i18n.WSLang.t(template); // message de la config, traduit
        String rendered = template
                .replace("{wave}", String.valueOf(wave))
                .replace("{total}", String.valueOf(cfg.totalWaves))
                .replace("{name}", specialName != null ? com.wavesurvivor.i18n.WSLang.t(specialName) : "");
        broadcast(Component.literal(rendered).withStyle(isSpecial ? ChatFormatting.DARK_PURPLE : ChatFormatting.YELLOW));

        if (soundId != null && !soundId.isBlank()) {
            playGlobalSound(soundId);
        }
    }

    private void playGlobalSound(String soundId) {
        try {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
            if (sound == null || server == null) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.level().playSound(null, p.blockPosition(), sound, SoundSource.MASTER, 1.0f, 1.0f);
            }
        } catch (Exception ignore) {}
    }

    private WavePauseEntry findWavePause(HordeConfigMultiData.ConfigDataInner cfg, int wave) {
        if (!cfg.useWavePauses || cfg.wavePauses == null) return null;
        for (WavePauseEntry p : cfg.wavePauses) {
            if (p.interval > 0 && wave % p.interval == 0) return p;
        }
        return null;
    }

    private void spawnWave(int wave, HordeConfigMultiData.ConfigDataInner cfg) {
        if (cfg.hordeEntities == null || cfg.hordeEntities.isEmpty()) {
            WaveSurvivorMod.LOGGER.warn("[Wave {}] hordeEntities vide.", wave);
            return;
        }
        ServerLevel level = resolveLevel(cfg);
        if (level == null) return;

        int playerCount = Math.max(1, server.getPlayerList().getPlayerCount());
        int radius = Math.max(1, cfg.spawnRadius);

        boolean useMulti = "multi".equalsIgnoreCase(cfg.spawnType) && cfg.multiSpawns != null && !cfg.multiSpawns.isEmpty();
        List<MultiSpawnEntry> points = useMulti ? cfg.multiSpawns : null;

        int totalSpawned = 0;
        // Héritage — Seigneur 5 : 2 premières vagues −15 % de monstres
        boolean lordFirst = wave <= 2 && com.wavesurvivor.horde.renaissance.Heritage.anyone(server, com.wavesurvivor.horde.renaissance.Heritage.Branch.LORD, 5);
        for (HordeEntity template : cfg.hordeEntities) {
            int totalCount = template.getCountForWave(wave, playerCount);
            if (lordFirst && totalCount > 1) totalCount = Math.max(1, (int) Math.round(totalCount * 0.85));
            if (totalCount <= 0) continue;

            if (useMulti) {
                int distributed = 0;
                for (int i = 0; i < points.size(); i++) {
                    MultiSpawnEntry pt = points.get(i);
                    int share = (i == points.size() - 1)
                            ? totalCount - distributed
                            : (int) Math.round(totalCount * pt.percentage / 100.0);
                    if (share <= 0) continue;
                    distributed += share;
                    BlockPos c = new BlockPos(pt.x, pt.y, pt.z);
                    int done = share;
                    queueSpawn(level, template, c, radius, share, playerCount);
                    totalSpawned += done;
                }
            } else {
                BlockPos c = getSpawnCenter(cfg);
                int done = totalCount;
                queueSpawn(level, template, c, radius, totalCount, playerCount);
                totalSpawned += done;
            }
        }
        WaveSurvivorMod.LOGGER.info("[Wave {}] Normale : {} mob(s), {} joueur(s).", wave, totalSpawned, playerCount);
    }

    private void spawnBossesForWave(int wave, HordeConfigMultiData.ConfigDataInner cfg) {
        if (!cfg.useBossWaves || cfg.bossWaves == null || cfg.bossWaves.isEmpty()) return;
        ServerLevel level = resolveLevel(cfg);
        if (level == null) return;

        int radius = Math.max(1, cfg.spawnRadius);
        BlockPos center = getSpawnCenter(cfg);

        for (BossWave boss : cfg.bossWaves) {
            if (boss.waveNumber != wave) continue;
            // Arène du boss final : le boss de la dernière vague ne sort pas, il attend dans son arène
            if (cfg.raidArena && wave >= cfg.totalWaves && raidBoss == null) {
                raidBoss = boss;
                broadcast(Component.literal(com.wavesurvivor.i18n.WSLang.t("raid.await_classic", com.wavesurvivor.i18n.WSLang.t(boss.bossName))));
                continue;
            }
            boolean ok = BossManager.spawnBoss(level, boss, center, radius, server);
            if (ok) {
                broadcast(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.boss_b2e9") + com.wavesurvivor.i18n.WSLang.t(boss.bossName) + com.wavesurvivor.i18n.WSLang.t("srv.apparait")).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            }
        }
    }

    private ServerLevel resolveLevel(HordeConfigMultiData.ConfigDataInner cfg) {
        if (server == null) return null;
        String dim = overrideSpawnDim != null ? overrideSpawnDim
                : (cfg.spawnCoords != null && cfg.spawnCoords.dimension != null
                        ? cfg.spawnCoords.dimension : "minecraft:overworld");
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim));
            ServerLevel lvl = server.getLevel(key);
            if (lvl != null) return lvl;
        } catch (Exception ignore) {}
        return server.overworld();
    }

    private void broadcast(Component msg) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    public void reset() {
        raidBoss = null;
        // Fin de horde (victoire, défaite, arrêt, nouvelle horde) : la sauvegarde de partie est effacée
        // (sauf pendant la fermeture du serveur, qui vient justement de l'enregistrer)
        HordeSession.discard(server);
        com.wavesurvivor.horde.mutator.HordeMutators.clear();
        com.wavesurvivor.horde.difficulty.HordeDifficulty.clear();
        com.wavesurvivor.horde.daily.DailyServer.clear();
        this.state = State.IDLE;
        this.activeHorde = null;
        this.currentWave = 0;
        this.startedAtTick = -1L;
        this.nextActionTick = -1L;
        this.waveStartedAtTick = -1L;
        this.nextChaosTick = -1L;
        this.overrideSpawnPos = null;
        this.overrideSpawnDim = null;
        this.forcedSpecialWave = null;
        this.pendingSpawns.clear();
        this.fallbackCenter = null;
        com.wavesurvivor.horde.spawn.SpawnZone.clear(); // la zone sera rescannée au prochain lancement
        this.nextBatchTick = 0L;
    }

    // ─── Spawn progressif (par lots) ───
    // Les monstres d'une vague sont mis en file d'attente puis libérés par lots de spawnBatchSize
    // toutes les spawnBatchInterval secondes. Chaque lot pioche un monstre de chaque type à tour de rôle
    // (vagues mélangées). spawnBatchSize = 0 → tout apparaît d'un coup (ancien comportement).

    private static final class PendingSpawn {
        final ServerLevel level;
        final HordeEntity template;
        final BlockPos center;
        final int radius;
        final int players;
        int left;

        PendingSpawn(ServerLevel level, HordeEntity template, BlockPos center, int radius, int left, int players) {
            this.level = level;
            this.template = template;
            this.center = center;
            this.radius = radius;
            this.left = left;
            this.players = players;
        }
    }

    private final java.util.List<PendingSpawn> pendingSpawns = new java.util.ArrayList<>();
    private long nextBatchTick = 0L;

    private void queueSpawn(ServerLevel level, HordeEntity template, BlockPos center, int radius, int count, int players) {
        if (count <= 0) return;
        int batch = activeHorde != null && activeHorde.configData != null ? activeHorde.configData.spawnBatchSize : 0;
        if (batch <= 0) {
            HordeSpawner.spawnMobs(level, template, center, radius, count, players);
            return;
        }
        pendingSpawns.add(new PendingSpawn(level, template, center, radius, count, players));
    }

    private void tickPendingSpawns(long now) {
        if (pendingSpawns.isEmpty() || now < nextBatchTick) return;
        HordeConfigMultiData.ConfigDataInner cfg = activeHorde != null ? activeHorde.configData : null;
        int budget = Math.max(1, cfg != null ? cfg.spawnBatchSize : 5);
        double interval = cfg != null ? cfg.spawnBatchInterval : 3.0;
        int idx = 0;
        while (budget > 0 && !pendingSpawns.isEmpty()) {
            if (idx >= pendingSpawns.size()) idx = 0;
            PendingSpawn p = pendingSpawns.get(idx);
            HordeSpawner.spawnMobs(p.level, p.template, p.center, p.radius, 1, p.players);
            p.left--;
            budget--;
            if (p.left <= 0) pendingSpawns.remove(idx);
            else idx++;
        }
        nextBatchTick = now + Math.max(1L, Math.round(Math.max(0.05, interval) * 20));
    }

    public State getState() { return state; }
    public HordeConfigMultiData getActiveHorde() { return activeHorde; }
    public int getCurrentWave() { return currentWave; }
    public long getStartedAtTick() { return startedAtTick; }
    public boolean isRunning() { return state == State.RUNNING || state == State.WAITING_NEXT_WAVE || state == State.PAUSED; }

    /** Vrai si la horde en cours bloque l'apparition naturelle des créatures (réglage « blockNaturalSpawns »). */
    public boolean blocksNaturalSpawns() {
        return isRunning() && activeHorde != null && activeHorde.configData != null && activeHorde.configData.blockNaturalSpawns;
    }
    public MinecraftServer getServer() { return server; }
}
