package com.wavesurvivor.config.model;

import com.google.gson.annotations.SerializedName;
import com.wavesurvivor.horde.model.BossWave;
import com.wavesurvivor.horde.model.ChaosEvent;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.model.MultiSpawnEntry;
import com.wavesurvivor.horde.model.PlayerScaling;
import com.wavesurvivor.horde.model.RewardEntry;
import com.wavesurvivor.horde.model.SpawnMessages;
import com.wavesurvivor.horde.model.SpecialWave;
import com.wavesurvivor.horde.model.WavePauseEntry;
import java.util.List;
import java.util.Map;

public class HordeConfigMultiData {

    @SerializedName("hordeName")
    public String hordeName;

    @SerializedName("hordeDescription")
    public String hordeDescription;

    @SerializedName("configData")
    public ConfigDataInner configData;

    @SerializedName("rewards")
    public RewardsBundle rewards;

    @SerializedName("id")
    public String id;

    public static class ConfigDataInner {
        @SerializedName("totalWaves")
        public int totalWaves = 5;

        @SerializedName("delayBetweenWaves")
        public int delayBetweenWaves = 30;

        /** Prix de consolation en cas de défaite (% des bonus de victoire, au prorata des vagues survécues) : -1 = réglage général (rules.json), 0 = aucun. */
        @SerializedName("consolationPercent")
        public int consolationPercent = -1;

        /** Bouton « Prêt » pendant les pauses (hordes classiques, Boss Rush, sans fin, invasion ; pas en Kingdom). */
        @SerializedName("ready")
        public ReadySettings ready = new ReadySettings();

        /** Fin de vague forcée : secondes laissées quand il reste ≤ 10 % de la vague (0 = désactivé). */
        @SerializedName("forceEndSeconds")
        public int forceEndSeconds = 60;

        /** Bonus quand la vague est nettoyée PENDANT le décompte de fin forcée (avant qu'il n'expire). */
        @SerializedName("forceEndBonus")
        public ForceEndBonus forceEndBonus = new ForceEndBonus();

        @SerializedName("spawnType")
        public String spawnType = "single";

        @SerializedName("spawnCoords")
        public Coords spawnCoords;

        @SerializedName("spawnRadius")
        public int spawnRadius = 20;

        /** Spawn progressif : nombre de monstres par lot (0 = toute la vague d'un coup). */
        @SerializedName("spawnBatchSize")
        public int spawnBatchSize = 5;

        /** Spawn progressif : secondes entre deux lots. */
        @SerializedName("spawnBatchInterval")
        public double spawnBatchInterval = 3.0;

        @SerializedName("gameOverRadius")
        public int gameOverRadius = 0;

        /** Mode de jeu : "waves" (vagues classiques, défaut) ou "kingdom" (portails + cycle Assaut / Calme). */
        @SerializedName("mode")
        public String mode = "waves";

        /** Réglages du mode Kingdom (absent = valeurs par défaut). */
        @SerializedName("kingdom")
        public KingdomSettings kingdom;

        public boolean isKingdom() { return "kingdom".equalsIgnoreCase(mode); }

        /** 1.7 — Invasion de village : cette « horde » est une invasion (Mauvais présage / commande), pas une horde d'autel. */
        public boolean isInvasion() { return "invasion".equalsIgnoreCase(mode); }

        /** Réglages de l'invasion (jamais null). */
        public InvasionSettings invasion() { return invasion != null ? invasion : new InvasionSettings(); }

        @SerializedName("invasion")
        public InvasionSettings invasion = new InvasionSettings();

        /** 2.0 — La Faille : cette « horde » ouvre un portail vers une dimension dédiée (nécessite le mod Pehkui). */
        public boolean isRift() { return "rift".equalsIgnoreCase(mode); }

        /** Réglages de la Faille (jamais null). */
        public RiftSettings rift() { return rift != null ? rift : new RiftSettings(); }

        @SerializedName("rift")
        public RiftSettings rift = new RiftSettings();

        public KingdomSettings kingdom() { return kingdom != null ? kingdom : new KingdomSettings(); }

        @SerializedName("playerScaling")
        public PlayerScaling playerScaling;

        @SerializedName("multiSpawns")
        public List<MultiSpawnEntry> multiSpawns;

        @SerializedName("dynamicSpawns")
        public List<Map<String, Object>> dynamicSpawns;

        @SerializedName("spawnConditions")
        public Map<String, Object> spawnConditions;

        @SerializedName("spawnMessages")
        public SpawnMessages spawnMessages;

        @SerializedName("useWavePauses")
        public boolean useWavePauses;

        @SerializedName("wavePauses")
        public List<WavePauseEntry> wavePauses;

        @SerializedName("useMerchants")
        public boolean useMerchants;

        /** Pendant la horde (classique ou Kingdom) : bloque l'apparition naturelle des créatures (allège le serveur). */
        @SerializedName("blockNaturalSpawns")
        public boolean blockNaturalSpawns = false;

        /** Difficulté affichée de la horde, en étoiles (0 = non affichée, 1 à 5). */
        @SerializedName("stars")
        public int stars = 0;

        /** Niveaux de difficulté autorisés au lancement (« easy,normal,hard,nightmare » ; vide = tous). */
        @SerializedName("difficulties")
        public String difficulties = "";

        /** Horde classique : le boss final de la dernière vague attend dans son arène (portail près de l'autel). */
        @SerializedName("raidArena")
        public boolean raidArena = false;

        /** Rayon de l'arène du boss final (horde classique). */
        @SerializedName("raidRadius")
        public int raidRadius = 18;

        /** Déblocage : hordes à avoir terminées avant de pouvoir lancer celle-ci (toutes requises). */
        @SerializedName("unlockRequires")
        public java.util.List<UnlockReq> unlockRequires = new java.util.ArrayList<>();

        /** Une condition : terminer la horde {@code horde}, au moins au niveau {@code difficulty} (vide = n'importe lequel). */
        public static class UnlockReq {
            public String horde = "";
            public String difficulty = "";
        }

        @SerializedName("merchantDebug")
        public boolean merchantDebug;

        @SerializedName("merchants")
        public List<MerchantData> merchants;

        @SerializedName("hordeEntities")
        public List<HordeEntity> hordeEntities;

        /**
         * 2.0 — La Faille : monstres, vagues spéciales et boss propres à chaque phase (vides ou absents = ceux de la Horde).
         * Siège = moteur du Kingdom (Assauts), Contre-offensive = gardiens des catalyseurs et renforts, Titan = vagues du pont.
         */
        @SerializedName("siegeEntities") public List<HordeEntity> siegeEntities;
        @SerializedName("counterEntities") public List<HordeEntity> counterEntities;
        @SerializedName("titanEntities") public List<HordeEntity> titanEntities;
        @SerializedName("siegeSpecialWaves") public List<SpecialWave> siegeSpecialWaves;
        @SerializedName("counterSpecialWaves") public List<SpecialWave> counterSpecialWaves;
        @SerializedName("titanSpecialWaves") public List<SpecialWave> titanSpecialWaves;
        @SerializedName("siegeBossWaves") public List<BossWave> siegeBossWaves;
        @SerializedName("counterBossWaves") public List<BossWave> counterBossWaves;
        @SerializedName("titanBossWaves") public List<BossWave> titanBossWaves;

        @SerializedName("useSpecialWaves")
        public boolean useSpecialWaves;

        @SerializedName("specialWaveChance")
        public int specialWaveChance;

        @SerializedName("specialWaves")
        public List<SpecialWave> specialWaves;

        @SerializedName("useBossWaves")
        public boolean useBossWaves;

        /** 1.6 — Boss Rush : réglages du mode (vies, pause, boss retenus, Gantelet, récompenses…). */
        @SerializedName("bossRush")
        public BossRushSettings bossRush = new BossRushSettings();

        /** 1.7 — Mode sans fin : la horde ne s'arrête plus après sa dernière vague (réglages par horde, éditeur › Sans fin). */
        @SerializedName("endless")
        public EndlessSettings endless = new EndlessSettings();

        @SerializedName("bossWaves")
        public List<BossWave> bossWaves;

        @SerializedName("chaosEnabled")
        public boolean chaosEnabled;

        @SerializedName("chaosMinInterval")
        public int chaosMinInterval = 60;

        @SerializedName("chaosMaxInterval")
        public int chaosMaxInterval = 120;

        @SerializedName("chaosEvents")
        public List<ChaosEvent> chaosEvents;
    }

    public static class Coords {
        public int x;
        public int y;
        public int z;

        @SerializedName("dimension")
        public String dimension = "minecraft:overworld";
    }

    public static class RewardsBundle {
        public boolean enabled;
        public List<RewardEntry> top1;
        public List<RewardEntry> top2;
        public List<RewardEntry> top3;
        public List<RewardEntry> bossKiller;
    }

    /** Bonus « vague nettoyée à temps » : trésor commun en Kingdom, émeraudes à l'autel en horde classique. */
    public static class ForceEndBonus {
        @SerializedName("money") public int money = 5;
        @SerializedName("wood") public int wood = 5;
        @SerializedName("stone") public int stone = 5;
        @SerializedName("iron") public int iron = 2;
        @SerializedName("essence") public int essence = 1;
        @SerializedName("emeralds") public int emeralds = 5;
    }

    /** Objectifs du Calme (Kingdom) : une mission facultative tirée au hasard parmi celles activées. */
    public static class CalmObjectives {
        @SerializedName("enabled") public boolean enabled = true;
        /** 1 = à chaque Calme, 2 = un Calme sur deux… */
        @SerializedName("every") public int every = 1;
        /** Secondes après le début du Calme avant l'apparition de l'objectif. */
        @SerializedName("delaySeconds") public int delaySeconds = 15;
        @SerializedName("convoy") public Convoy convoy = new Convoy();
        @SerializedName("champion") public Champion champion = new Champion();
        @SerializedName("treasure") public Treasure treasure = new Treasure();
        @SerializedName("purify") public Purify purify = new Purify();
    }

    /** Récompense en ressources (trésor commun). */
    public static class Reward {
        @SerializedName("money") public int money = 0;
        @SerializedName("wood") public int wood = 0;
        @SerializedName("stone") public int stone = 0;
        @SerializedName("iron") public int iron = 0;
        @SerializedName("essence") public int essence = 0;
        public Reward() {}
        public Reward(int money, int wood, int stone, int iron, int essence) {
            this.money = money; this.wood = wood; this.stone = stone; this.iron = iron; this.essence = essence;
        }
    }

    /** Convoi : une caravane part d'une Porte vers le Monolithe, des monstres l'attaquent ; arrivée = récompense. */
    public static class Convoy {
        @SerializedName("enabled") public boolean enabled = true;
        @SerializedName("entityType") public String entityType = "minecraft:wandering_trader";
        @SerializedName("health") public double health = 80;
        /** Vitesse de marche (attribut de vitesse : 0,2 = pas tranquille, 0,3 = joueur qui marche). */
        @SerializedName("speed") public double speed = 0.2;
        @SerializedName("attackers") public int attackers = 6;
        @SerializedName("seconds") public int seconds = 150;
        @SerializedName("reward") public Reward reward = new Reward(15, 10, 10, 5, 1);
    }

    /** Champion de Porte : un élite et sa garde sortent d'une Porte ; tué = Essence + butin personnalisable. */
    public static class Champion {
        @SerializedName("enabled") public boolean enabled = true;
        @SerializedName("entityType") public String entityType = "minecraft:vindicator";
        @SerializedName("name") public String name = "";
        @SerializedName("health") public double health = 200;
        @SerializedName("damage") public double damage = 10;
        @SerializedName("guards") public int guards = 4;
        @SerializedName("seconds") public int seconds = 150;
        @SerializedName("reward") public Reward reward = new Reward(0, 0, 0, 0, 3);
        @SerializedName("drops") public java.util.List<com.wavesurvivor.horde.model.ChaosEvent.Drop> drops;
    }

    /** Trésor enfoui : un coffre caché dans le monde, une boussole le désigne ; trouvé = monnaie + butin. */
    public static class Treasure {
        @SerializedName("enabled") public boolean enabled = true;
        @SerializedName("minDistance") public int minDistance = 28;
        @SerializedName("maxDistance") public int maxDistance = 55;
        @SerializedName("seconds") public int seconds = 150;
        @SerializedName("reward") public Reward reward = new Reward(20, 0, 0, 0, 1);
        @SerializedName("drops") public java.util.List<com.wavesurvivor.horde.model.ChaosEvent.Drop> drops;
    }

    /**
     * Bouton « Prêt » (1.9) : pendant une pause entre deux vagues, les joueurs peuvent se déclarer prêts ; quand tous le sont, la vague suivante démarre
     * et ils reçoivent un bonus d'enchaînement. Type : « coins » (émeraudes), « pr » (points de Renaissance) ou un effet (strength, speed, resistance,
     * regeneration, haste). « amount » = quantité (ou durée en secondes pour un effet), « level » = niveau de l'effet.
     */
    public static class ReadySettings {
        @SerializedName("allow") public boolean allow = true;
        @SerializedName("bonusType") public String bonusType = "coins";
        @SerializedName("amount") public int amount = 3;
        @SerializedName("level") public int level = 1;
        /** Bonus proportionnel au temps restant de la pause : enchaîner tout de suite = bonus maximal. */
        @SerializedName("proportional") public boolean proportional = true;
    }

    /** Boss Rush (1.6) : la horde jouée avec ses seuls boss, à la suite. Réglages par horde (éditeur › Boss Rush). */
    public static class BossRushSettings {
        @SerializedName("enabled") public boolean enabled = true;
        /** Vies partagées par l'équipe (chaque mort d'un joueur en consomme une). */
        @SerializedName("lives") public int lives = 3;
        /** Pause entre deux boss, en secondes. */
        @SerializedName("pauseSeconds") public int pauseSeconds = 20;
        /** Les joueurs en vie récupèrent leur santé entre deux boss. */
        @SerializedName("healBetween") public boolean healBetween = true;
        /** Bénédictions proposées entre deux boss. */
        @SerializedName("blessings") public boolean blessings = true;
        /** Marchands et coffres roulette (ravitaillement) pendant les pauses. */
        @SerializedName("merchants") public boolean merchants = true;
        @SerializedName("supplyChests") public boolean supplyChests = true;
        /** Mode Gantelet autorisé, et hausse cumulée de PV / dégâts à chaque boss (en %). */
        @SerializedName("gauntlet") public boolean gauntlet = true;
        @SerializedName("gauntletPercent") public double gauntletPercent = 15;
        /** Débloqué seulement après avoir terminé la horde une fois. */
        @SerializedName("requiresCompletion") public boolean requiresCompletion = true;
        /** PR de Renaissance par boss vaincu, et bonus de victoire (pas de bonus de première victoire). */
        @SerializedName("prPerBoss") public int prPerBoss = 3;
        @SerializedName("prVictory") public int prVictory = 10;
        /** Récompenses du classement de fin de horde (top 3 + tueur du boss) aussi en Boss Rush. */
        @SerializedName("leaderboardRewards") public boolean leaderboardRewards = false;
        /** Nombre de temps gardés au classement. */
        @SerializedName("topSize") public int topSize = 10;
        /** Limite de temps en minutes (0 = aucune) : au-delà, c'est la défaite. */
        @SerializedName("maxMinutes") public int maxMinutes = 0;
        /** Boss retenus : numéros de vague séparés par des virgules (vide = tous les boss de la horde). */
        @SerializedName("waves") public String waves = "";
    }

    /**
     * La Faille (2.0) : réglages d'une horde de mode « rift ». Le portail s'ouvre près de l'autel (animation d'ouverture la première fois),
     * les joueurs qui le franchissent arrivent sur une île flottante dans une dimension dédiée.
     */
    public static class RiftSettings {
        /** Multiplicateur de durée de l'animation d'ouverture (1 = 6,5 s ; 0,5 = deux fois plus rapide). */
        @SerializedName("openingSpeed") public double openingSpeed = 1.0;
        /** Intensité des secousses d'écran de l'animation (0 = aucune). */
        @SerializedName("shakeIntensity") public double shakeIntensity = 1.0;
        /** Titre « La Faille est ouverte » à la fin de l'animation. */
        @SerializedName("showTitle") public boolean showTitle = true;
        /** Rayon de l'île de départ (phase Horde). */
        @SerializedName("islandRadius") public int islandRadius = 16;
        /** (Inutilisé depuis le jalon 2 : le nombre de vagues de la Horde est celui de la horde, onglet Général.) */
        @SerializedName("hordeWaves") public int hordeWaves = 5;
        /** Délai (s) entre l'entrée du premier joueur dans la Faille et le début de la Horde. */
        @SerializedName("startDelaySeconds") public int startDelaySeconds = 20;
        /** Temps (s) passé en spectateur après une mort avant de réapparaître sur l'île. */
        @SerializedName("reviveSeconds") public int reviveSeconds = 15;
        /** Pause de fin (s) après la victoire ou la défaite, avant la fermeture de la Faille (4 min par défaut). */
        @SerializedName("endPauseSeconds") public int endPauseSeconds = 240;
        /** Phase 2 — Le Siège (île agrandie, forteresse, assauts). Désactivée : la Horde mène directement à la suite. */
        @SerializedName("siegeEnabled") public boolean siegeEnabled = true;
        /** Nombre d'assauts du Siège avant la Contre-offensive. */
        @SerializedName("siegeAssaults") public int siegeAssaults = 4;
        /** Rayon final de l'île (agrandie par anneaux au début du Siège). */
        @SerializedName("siegeRadius") public int siegeRadius = 32;
        /** Pause (s) entre deux phases. */
        @SerializedName("interludeSeconds") public int interludeSeconds = 10;
        /** Ressources minimales qui apparaissent à chaque calme du Siège. */
        /** Siège (moteur du Kingdom) : unités par assaut au premier assaut, hausse par assaut (en %) et durée du calme (s). */
        @SerializedName("siegeBudget") public int siegeBudget = 40;
        @SerializedName("siegeGrowthPercent") public int siegeGrowthPercent = 25;
        @SerializedName("siegeCalmSeconds") public int siegeCalmSeconds = 180;   // 3 min de Calme (1 min + 2 min)
        /** Coffres roulette posés autour du Monolithe pendant les pauses de la Horde et les Calmes du Siège (0 = aucun). */
        @SerializedName("riftChests") public int riftChests = 6;
        @SerializedName("resourceTrees") public int resourceTrees = 4;
        @SerializedName("resourceOres") public int resourceOres = 3;
        /** Phase 3 — Contre-offensive : nombre de catalyseurs, PV de chacun, PV du cœur de la forteresse, renforts toutes les X s. */
        @SerializedName("catalysts") public int catalysts = 6;
        @SerializedName("catalystHp") public int catalystHp = 400;
        /** Forteresse : identifiant du plan (« citadelle »…) ou « random » (tirage au sort à l'ouverture de la Faille). */
        @SerializedName("fortress") public String fortress = "citadelle";
        /** Catalyseurs : régénération (% des PV par seconde) si aucun joueur à moins de N blocs ; gardiens par chaîne brisée (min, max) et bonus de force par phase (%). */
        @SerializedName("catalystRegenPercent") public double catalystRegenPercent = 2.0;
        @SerializedName("catalystRegenRadius") public int catalystRegenRadius = 8;
        @SerializedName("catalystGuardMin") public int catalystGuardMin = 3;
        @SerializedName("catalystGuardMax") public int catalystGuardMax = 5;
        @SerializedName("catalystGuardBoostPercent") public int catalystGuardBoostPercent = 35;
        @SerializedName("coreHp") public int coreHp = 500;
        @SerializedName("reinforceSeconds") public int reinforceSeconds = 20;
        /** Phase 4 — Titan : entité, taille (Pehkui), PV, avatars (PV, % de PV max retirés à leur mort), plafond de dégâts par coup (% des PV max). */
        @SerializedName("titanEnabled") public boolean titanEnabled = true;
        /** 2.0 — Titan par défaut : le Dévoreur (~10 blocs × 5 ≈ 50 blocs de haut). Toute autre entité reste possible. */
        @SerializedName("titanEntity") public String titanEntity = "wavesurvivor:devoreur";
        @SerializedName("titanScale") public double titanScale = 5.0;
        @SerializedName("titanHp") public int titanHp = 3000;
        @SerializedName("titanAvatarPercent") public int titanAvatarPercent = 8;
        @SerializedName("titanAvatarHp") public int titanAvatarHp = 250;
        @SerializedName("titanDamageCapPercent") public int titanDamageCapPercent = 6;
        /** Fenêtre de vulnérabilité (après une grosse attaque) : durée (s) et total de dégâts maximal par fenêtre (% des PV max). */
        /** Marge (blocs) entre le bord de l'île et le bord du Titan : hors de portée du corps à corps, atteignable à distance. */
        @SerializedName("titanGap") public int titanGap = 12;
        /** Poing du Titan contre le Monolithe : dégâts bruts infligés à l'intégrité du Monolithe par coup (0 = désactivé). */
        @SerializedName("titanMonolithDamage") public int titanMonolithDamage = 60;
        /** Pluie de météores : blocs (« id=poids, … »), taille, rayon, dégâts, nombre (base + par joueur), annonce et chute (ticks), traînée, cratère. */
        @SerializedName("meteorBlocks") public String meteorBlocks = "magma_block=3, blackstone=3, obsidian=2, crying_obsidian=1";
        @SerializedName("meteorSize") public double meteorSize = 3.0;
        @SerializedName("meteorRadius") public double meteorRadius = 6.0;
        @SerializedName("meteorDamage") public int meteorDamage = 12;
        @SerializedName("meteorBase") public int meteorBase = 3;
        @SerializedName("meteorPerPlayer") public int meteorPerPlayer = 1;
        @SerializedName("meteorDelay") public int meteorDelay = 50;
        @SerializedName("meteorFall") public int meteorFall = 24;
        @SerializedName("meteorTrail") public String meteorTrail = "flame";
        @SerializedName("meteorCrater") public boolean meteorCrater = true;
        /** Engins de siège : rechargement de la catapulte (ticks) niveaux 1 / 2 ; baliste : intervalle entre deux tirs et rechargement (ticks) niveaux 1 / 2. */
        /** Tour-catapulte : munitions illimitées (sinon 1 Pierre du trésor du royaume par tir). */
        @SerializedName("catapultTowerUnlimited") public boolean catapultTowerUnlimited = true;
        @SerializedName("catapultReload1") public int catapultReload1 = 600;
        @SerializedName("catapultReload2") public int catapultReload2 = 480;
        @SerializedName("ballistaInterval1") public int ballistaInterval1 = 10;
        @SerializedName("ballistaInterval2") public int ballistaInterval2 = 8;
        @SerializedName("ballistaReload1") public int ballistaReload1 = 200;
        @SerializedName("ballistaReload2") public int ballistaReload2 = 160;
        /** Charge du Titan sur l'île : activée, délai entre deux charges (s), stun (s), corps à corps (s), dégâts de l'impact, empiétement sur l'île (blocs),
         *  dégâts d'un coup au corps à corps, plafond de dégâts pendant le stun (% des PV max). */
        @SerializedName("titanCharge") public boolean titanCharge = true;
        @SerializedName("titanChargeInterval") public int titanChargeInterval = 60;
        @SerializedName("titanChargeStun") public int titanChargeStun = 6;
        @SerializedName("titanChargeMelee") public int titanChargeMelee = 10;
        @SerializedName("titanChargeDamage") public int titanChargeDamage = 24;
        @SerializedName("titanChargeReach") public int titanChargeReach = 8;
        @SerializedName("titanMeleeDamage") public int titanMeleeDamage = 18;
        @SerializedName("titanChargeCapPercent") public int titanChargeCapPercent = 20;
        /** Points faibles : nombre de cristaux, PV de chacun, % des PV max du Titan retirés par cristal détruit, stun prolongé (s) si détruit pendant la charge. */
        @SerializedName("titanCrystals") public int titanCrystals = 4;
        @SerializedName("titanCrystalHp") public int titanCrystalHp = 120;
        @SerializedName("titanCrystalPercent") public int titanCrystalPercent = 5;
        @SerializedName("titanCrystalStunBonus") public int titanCrystalStunBonus = 2;
        /** Vagues entre les phases : unités par vague à la phase 2 (doublées à chaque phase), intervalle (s), plafond d'ennemis vivants. */
        @SerializedName("titanWaveBase") public int titanWaveBase = 5;
        @SerializedName("titanWaveInterval") public int titanWaveInterval = 10;
        @SerializedName("titanWaveCap") public int titanWaveCap = 60;
        /** Attaque contre les bâtiments : dégâts et rayon (aussi appliqués par la charge aux bâtiments pris dedans). */
        /** Ruines des bâtiments du Kingdom : % des ressources investies à payer pour les réparer. */
        @SerializedName("ruinRepairPercent") public int ruinRepairPercent = 50;
        /** Émeraudes lâchées par chaque monstre tué dans la Faille (monnaie du Kingdom : achats et améliorations). */
        /** Vagues spéciales / boss par phase (la Horde garde « useSpecialWaves » et « useBossWaves » de l'onglet) : activés + chance (%) par groupe. */
        @SerializedName("siegeSpecialOn") public boolean siegeSpecialOn = false;
        @SerializedName("siegeSpecialChance") public int siegeSpecialChance = 35;
        @SerializedName("siegeBossOn") public boolean siegeBossOn = false;
        @SerializedName("counterSpecialOn") public boolean counterSpecialOn = false;
        @SerializedName("counterSpecialChance") public int counterSpecialChance = 35;
        @SerializedName("counterBossOn") public boolean counterBossOn = false;
        @SerializedName("titanSpecialOn") public boolean titanSpecialOn = false;
        @SerializedName("titanSpecialChance") public int titanSpecialChance = 35;
        @SerializedName("titanBossOn") public boolean titanBossOn = false;
        @SerializedName("emeraldMin") public int emeraldMin = 1;
        @SerializedName("emeraldMax") public int emeraldMax = 2;
        @SerializedName("titanBuildDamage") public int titanBuildDamage = 300;
        /** Compétences du Titan : chaque charge brise un secteur du bord de l'île (profondeur en blocs), coups pour abattre le rocher, dégâts du piétinement. */
        @SerializedName("titanIslandBreak") public boolean titanIslandBreak = true;
        /** Récompenses de fin : PR bonus si le Titan est tué, Reliquaires scellés pour chaque participant, bonus du podium (top 1 / 2 / 3, aux éliminations). */
        @SerializedName("titanPrBonus") public int titanPrBonus = 30;
        @SerializedName("rewardReliquaries") public int rewardReliquaries = 1;
        @SerializedName("topPr1") public int topPr1 = 30;
        @SerializedName("topPr2") public int topPr2 = 20;
        @SerializedName("topPr3") public int topPr3 = 10;
        @SerializedName("topReliq1") public int topReliq1 = 2;
        @SerializedName("topReliq2") public int topReliq2 = 1;
        @SerializedName("topReliq3") public int topReliq3 = 1;
        /** Éclats de Faille (Titan vaincu) : par participant, puis en plus pour le podium. */
        @SerializedName("shardsPerPlayer") public int shardsPerPlayer = 2;
        @SerializedName("topShards1") public int topShards1 = 2;
        @SerializedName("topShards2") public int topShards2 = 1;
        @SerializedName("topShards3") public int topShards3 = 1;
        /** Accès : avoir déjà gagné une horde classique, un Kingdom, une invasion de village (chaque condition activable). */
        @SerializedName("requireHordeWin") public boolean requireHordeWin = true;
        @SerializedName("requireKingdomWin") public boolean requireKingdomWin = true;
        @SerializedName("requireInvasionWin") public boolean requireInvasionWin = false;
        /** Siège, Contre-offensive, Titan : tous les monstres abîment les bâtiments et engins qu'ils frôlent. */
        @SerializedName("monstersAttackBuildings") public boolean monstersAttackBuildings = true;
        @SerializedName("titanBreakDepth") public int titanBreakDepth = 7;
        @SerializedName("titanRockHits") public int titanRockHits = 3;
        @SerializedName("titanStompDamage") public int titanStompDamage = 10;
        @SerializedName("titanBuildRadius") public int titanBuildRadius = 6;
        /** Cinématique d'apparition : activée, passable (maintenir S'accroupir 1 s), durée (s), nom et sous-titre affichés, sons. */
        @SerializedName("titanCine") public boolean titanCine = true;
        @SerializedName("titanCineSkippable") public boolean titanCineSkippable = true;
        @SerializedName("titanCineSeconds") public int titanCineSeconds = 12;
        @SerializedName("titanCineName") public String titanCineName = "";   // vide = texte traduit
        @SerializedName("titanCineSub") public String titanCineSub = "";
        @SerializedName("titanCineSounds") public boolean titanCineSounds = true;
        /** Cinématique de construction de la forteresse : activée, passable, durée (s), blocs posés par tick (minimum), titre (vide = nom de la forteresse). */
        @SerializedName("fortressCine") public boolean fortressCine = true;
        @SerializedName("fortressCineSkippable") public boolean fortressCineSkippable = true;
        @SerializedName("fortressCineSeconds") public int fortressCineSeconds = 20;
        @SerializedName("fortressCineSpeed") public int fortressCineSpeed = 150;
        @SerializedName("fortressCineTitle") public String fortressCineTitle = "";
        @SerializedName("titanWindowSeconds") public int titanWindowSeconds = 8;
        @SerializedName("titanWindowCapPercent") public int titanWindowCapPercent = 8;
        /** Engins de siège (catapultes, balistes) posés sur l'île au début du Siège. */
        @SerializedName("engines") public boolean engines = true;
    }

    /**
     * Invasion de village (1.7) : réglages d'une horde de mode « invasion ». Déclenchée par le Mauvais présage (roulette)
     * ou la commande /ws invasion, dans un vrai village : le nombre de vagues dépend de la difficulté du monde et du
     * niveau de présage, comme le raid vanilla.
     */
    public static class InvasionSettings {
        @SerializedName("enabled") public boolean enabled = true;
        /** Poids dans la roulette du Mauvais présage (0 = jamais tirée au sort : lançable seulement par commande). */
        @SerializedName("weight") public int weight = 50;
        /** « vanilla » : composition des vagues du raid vanilla ; « horde » : monstres, vagues spéciales et boss de cette horde. */
        @SerializedName("composition") public String composition = "horde";
        /** Composition « vanilla » : multiplicateurs de PV et de dégâts des envahisseurs vanilla (1 = valeurs du jeu). */
        @SerializedName("vanillaHealthMult") public double vanillaHealthMult = 1.0;
        @SerializedName("vanillaDamageMult") public double vanillaDamageMult = 1.0;
        /** Préparation avant la première vague, et pause entre deux vagues (secondes). */
        @SerializedName("prepSeconds") public int prepSeconds = 30;
        @SerializedName("waveDelaySeconds") public int waveDelaySeconds = 20;
        /** Une vague de plus à partir de ce niveau de Mauvais présage (vanilla : 2 ; 0 = jamais). */
        @SerializedName("bonusWaveOmen") public int bonusWaveOmen = 2;
        /** Rayon (blocs) autour de la cloche où l'on compte les villageois ; distance d'apparition des envahisseurs. */
        @SerializedName("villagerRadius") public int villagerRadius = 72;
        @SerializedName("spawnDistance") public int spawnDistance = 56;
        /** Défaite : % de villageois perdus à partir duquel l'invasion est perdue (100 = tous). */
        @SerializedName("lossPercent") public int lossPercent = 50;
        /** Les envahisseurs ouvrent les portes / les brisent (le village est un peu abîmé, jamais le terrain). */
        @SerializedName("openDoors") public boolean openDoors = true;
        @SerializedName("breakDoors") public boolean breakDoors = true;
        /** Une vague non terminée au bout de ce temps (secondes) laisse place à la suivante (0 = jamais). */
        @SerializedName("waveTimeoutSeconds") public int waveTimeoutSeconds = 360;
        /** Récompenses de victoire : « Héros du village » (durée en secondes, niveau = présage − 1), émeraudes ×présage,
         *  clés de coffre mystère, points de Renaissance, et butin supplémentaire (« objet*min-max@chance, … »). */
        @SerializedName("heroSeconds") public int heroSeconds = 2400;
        @SerializedName("rewardEmeralds") public int rewardEmeralds = 6;
        @SerializedName("rewardKeys") public int rewardKeys = 1;
        @SerializedName("rewardPr") public int rewardPr = 3;
        @SerializedName("lootCsv") public String lootCsv = "";
    }

    /** Mode sans fin (1.7) : après la dernière vague, la horde continue, de plus en plus forte, jusqu'à la défaite. */
    public static class EndlessSettings {
        @SerializedName("enabled") public boolean enabled = true;
        /** Vies partagées par l'équipe (chaque mort d'un joueur en consomme une ; la chute du Monolithe met aussi fin à la partie). */
        @SerializedName("lives") public int lives = 5;
        /** Par vague au-delà de la dernière : monstres en plus (%), PV en plus (%), dégâts en plus (%). */
        @SerializedName("countPercent") public double countPercent = 8;
        @SerializedName("healthPercent") public double healthPercent = 6;
        @SerializedName("damagePercent") public double damagePercent = 4;
        /** Plafond du nombre de monstres (multiple de la dernière vague normale), pour garder le serveur fluide. */
        @SerializedName("maxCountMultiplier") public double maxCountMultiplier = 4.0;
        /** Un boss de la horde (tour à tour) revient toutes les N vagues au-delà de la dernière (0 = aucun). */
        @SerializedName("bossEvery") public int bossEvery = 5;
        /** Chaque retour de boss : PV et dégâts en plus (%, cumulés). */
        @SerializedName("bossPowerPercent") public double bossPowerPercent = 15;
        /** Débloqué seulement après avoir terminé la horde une fois. */
        @SerializedName("requiresCompletion") public boolean requiresCompletion = true;
        /** Nombre de scores gardés au classement. */
        @SerializedName("topSize") public int topSize = 10;
        /** Paliers (1.9) : tous les N vagues (à partir de la dernière vague normale), coffre de palier puis choix Continuer / Encaisser (0 = aucun palier). */
        @SerializedName("tierEvery") public int tierEvery = 10;
        /** Part des gains (PR) versée quand l'équipe encaisse à un palier ; continuer jusqu'à la défaite rapporte 100 %. */
        @SerializedName("cashPercent") public int cashPercent = 90;
        /** Durée du choix (secondes) pendant la pause du palier. */
        @SerializedName("voteSeconds") public int voteSeconds = 20;
        /** Faux : l'équipe encaisse à la majorité (les joueurs qui ne votent pas comptent comme « Continuer ») ; vrai : seul l'hôte décide. */
        @SerializedName("hostDecides") public boolean hostDecides = false;
        /** Contenu du coffre de palier : « count » au premier palier, « perTier » de plus à chaque palier suivant ; « pr » = points de Renaissance. */
        @SerializedName("tierChest") public java.util.List<TierItem> tierChest = defaultTierChest();

        private static java.util.List<TierItem> defaultTierChest() {
            java.util.List<TierItem> l = new java.util.ArrayList<>();
            l.add(new TierItem("minecraft:emerald", 10, 5));
            l.add(new TierItem("minecraft:golden_apple", 1, 1));
            l.add(new TierItem("minecraft:experience_bottle", 8, 4));
            l.add(new TierItem("pr", 5, 3));
            return l;
        }
    }

    /** Un objet du coffre de palier. */
    public static class TierItem {
        @SerializedName("item") public String item = "minecraft:emerald";
        @SerializedName("count") public int count = 1;
        @SerializedName("perTier") public int perTier = 0;
        public TierItem() {}
        public TierItem(String item, int count, int perTier) { this.item = item; this.count = count; this.perTier = perTier; }
    }

    /** Purification : des foyers de corruption à casser ; aucun = Assaut suivant plus fort, tous = plus faible. */
    public static class Purify {
        @SerializedName("enabled") public boolean enabled = true;
        @SerializedName("count") public int count = 3;
        @SerializedName("health") public double health = 40;
        @SerializedName("seconds") public int seconds = 120;
        /** % d'unités en plus à l'Assaut suivant si aucun foyer n'est purifié. */
        @SerializedName("failPercent") public double failPercent = 15;
        /** % d'unités en moins à l'Assaut suivant si tous les foyers sont purifiés. */
        @SerializedName("successPercent") public double successPercent = 10;
    }

    /** Réglages du mode Kingdom (étape 1 : portails cardinaux, cycle Assaut / Calme, flux plafonné). */
    public static class KingdomSettings {
        /** Objectifs du Calme (convoi, champion, trésor, purification). */
        @SerializedName("calmObjectives") public CalmObjectives calmObjectives = new CalmObjectives();
        /** Anneau d'apparition des grands portails, en blocs autour du Monolithe. */
        @SerializedName("ringMin") public int ringMin = 64;
        @SerializedName("ringMax") public int ringMax = 72;
        /** PV d'un grand portail. */
        @SerializedName("portalHealth") public double portalHealth = 800;
        /** Taille visuelle des grands portails (1 = brèche normale). */
        @SerializedName("portalScale") public double portalScale = 3.0;
        /** Unités par portail lors du premier Assaut. */
        @SerializedName("assaultBudget") public int assaultBudget = 100;
        /** Hausse du budget à chaque cycle (0.2 = +20 %). */
        @SerializedName("assaultGrowth") public double assaultGrowth = 0.2;
        /** Unités vivantes en même temps (tous portails confondus). */
        @SerializedName("aliveCap") public int aliveCap = 80;
        /** Production pendant le Calme, en % du budget d'Assaut. */
        @SerializedName("calmPercent") public double calmPercent = 5;
        /** Durée du Calme (secondes). */
        @SerializedName("calmSeconds") public int calmSeconds = 120;
        /** Petite brèche pendant le Calme toutes les N secondes (0 = aucune). */
        @SerializedName("smallBreachSeconds") public int smallBreachSeconds = 30;
        /** Unités libérées par une petite brèche. */
        @SerializedName("smallBreachUnits") public int smallBreachUnits = 4;
        /** Limite de sécurité : la partie s'arrête après ce nombre de cycles. */
        @SerializedName("maxCycles") public int maxCycles = 10;
        /** Portails invulnérables pendant l'Assaut (attaquables seulement pendant le Calme). */
        @SerializedName("shieldDuringAssault") public boolean shieldDuringAssault = true;
        /** Style visuel des portails (infernale, ames, end, abysses). */
        @SerializedName("portalStyle") public String portalStyle = "infernale";
        /** Marche (étape 2) : unités par escouade (seul le chef calcule le trajet). */
        @SerializedName("squadSize") public int squadSize = 8;
        /** Marche : distance (blocs) au Monolithe ou à un joueur à laquelle une unité quitte la marche pour combattre. */
        @SerializedName("engageRadius") public int engageRadius = 16;
        /** Claim (étape 3) : demi-côté de la zone carrée autour du Monolithe (16 → 32×32). */
        @SerializedName("claimRadius") public int claimRadius = 16;
        /** Claim : construction / destruction interdite hors de la zone pendant la partie. */
        @SerializedName("buildOnlyInClaim") public boolean buildOnlyInClaim = true;
        /** Claim : bornes au sol + particules de la bordure (jamais affichées si le claim est désactivé). */
        @SerializedName("claimMarkers") public boolean claimMarkers = true;
        /** Claim : forme de la zone, « square » (carré, par défaut) ou « circle » (cercle de même surface). */
        @SerializedName("claimShape") public String claimShape = "square";
        /** Arène du Roi : le boss final attend dans une dimension dédiée, accessible par la Porte du Roi ouverte. */
        @SerializedName("raidArena") public boolean raidArena = false;
        /** Rayon de l'arène du Roi (en blocs). */
        @SerializedName("raidRadius") public int raidRadius = 18;
        /** Claim : les blocs posés par les joueurs ont des PV et les monstres peuvent les casser. */
        @SerializedName("wallDurability") public boolean wallDurability = true;
        /** Claim : multiplicateur des PV des murs. */
        @SerializedName("wallHpMultiplier") public double wallHpMultiplier = 1.0;
        /** Siège (étape 5) : certains monstres reçoivent un rôle de siège à leur sortie des portails. */
        @SerializedName("siegeUnits") public boolean siegeUnits = true;
        /** Siège : % de sapeurs, de béliers et de grimpeurs parmi les unités. */
        @SerializedName("sapperPercent") public double sapperPercent = 6;
        @SerializedName("ramPercent") public double ramPercent = 4;
        @SerializedName("climberPercent") public double climberPercent = 6;
        /** Siège : premier Assaut où les rôles apparaissent (laisse le temps de construire). */
        @SerializedName("siegeFromCycle") public int siegeFromCycle = 2;
        /** Gardiens : nombre par Porte (tant qu'un Gardien vit, sa Porte est invincible). */
        @SerializedName("guardiansPerGate") public int guardiansPerGate = 2;
        /** Gardiens : multiplicateur de PV (sur le monstre le plus costaud de la horde). */
        @SerializedName("guardianHpMult") public double guardianHpMult = 3.0;
        /** Catalyseur (objectif du Calme) : PV et taille de l'escorte. */
        @SerializedName("catalystHealth") public double catalystHealth = 150;
        @SerializedName("catalystEscort") public int catalystEscort = 4;
        /** Catalyseur : % des PV max de la Porte qu'on peut lui retirer après chaque débréchage. */
        @SerializedName("breachPercent") public double breachPercent = 35;
        /** Boss final : la dernière Porte libère le boss final de la horde. */
        @SerializedName("finalBoss") public boolean finalBoss = true;
        /** Thème visuel des Portes : auto, abyss, fire, bone, end, ocean, arcane. */
        @SerializedName("portalTheme") public String portalTheme = "auto";
        /** Style des Portes : "blocks" (structure en vrais blocs, solide) ou "visual" (ancienne Porte entièrement dessinée). */
        @SerializedName("gateStyle") public String gateStyle = "blocks";
        /** Corruption du sol autour des Portes (même format que la zone d'un autel : rayon, sol pondéré, dessin). */
        @SerializedName("corruption") public com.wavesurvivor.altar.AltarRecipes.Zone corruption;
        /** Unités des brèches du Calme (format d'une vague spéciale ; « count » = poids). Vide = mélange normal de la horde. */
        @SerializedName("calmBreachUnits") public java.util.List<com.wavesurvivor.horde.model.SpecialWave.SpecialWaveEntity> calmBreachUnits = new java.util.ArrayList<>();
        /** Placement des Portes : "auto" (anneau autour du Monolithe) ou "fixed" (coordonnées manuelles, maps custom). */
        @SerializedName("gatePlacement") public String gatePlacement = "auto";
        /** Forme des Portes : "arch" (arche monumentale) ou "den" (antre rocheux avec ovale tourbillonnant et vrilles). */
        @SerializedName("gateShape") public String gateShape = "arch";
        /** Orientation des Portes : "monolith" (face au Monolithe), "away" (dos), "left" / "right" (de côté). */
        @SerializedName("gateFacing") public String gateFacing = "monolith";
        /** Antre : couleur du fond de l'ovale ("auto" = selon le thème, sinon une des 16 couleurs : black, red, purple…). */
        @SerializedName("denBackground") public String denBackground = "auto";
        /** Mode auto : nombre de Portes (1 à 8), réparties régulièrement en partant du nord. */
        @SerializedName("gateCount") public int gateCount = 4;
        /** Mode fixe : coordonnées exactes de chaque Porte (8 au maximum). */
        @SerializedName("gatePoints") public java.util.List<GatePoint> gatePoints = new java.util.ArrayList<>();
        /** Chemins tracés au « Bâton de tracé » : les escouades suivent ces points jusqu'au Monolithe (8 au maximum). */
        @SerializedName("paths") public java.util.List<PathDef> paths = new java.util.ArrayList<>();

        /** Un chemin : nom + points dans l'ordre (le Monolithe est toujours l'arrivée). */
        public static class PathDef {
            public String name = "Chemin";
            public java.util.List<GatePoint> points = new java.util.ArrayList<>();
        }

        /** Coordonnées d'une Porte (mode fixe). */
        public static class GatePoint {
            public int x, y, z;
            /** Orientation propre à cette Porte (« » = réglage général). */
            public String facing = "";
        }
    }
}
