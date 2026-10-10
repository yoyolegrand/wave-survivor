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
