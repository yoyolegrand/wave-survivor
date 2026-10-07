package com.wavesurvivor.horde.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class BossWave {

    @SerializedName("waveNumber")
    public int waveNumber = 1;

    /** Mode Kingdom : Porte d'où sort ce boss d'Assaut (0 = au hasard ; si elle est tombée, une autre Porte ouverte). */
    @SerializedName("gate")
    public int gate = 0;

    /** Taille du boss (1 = normale, 3 = géant) — nécessite le mod Pehkui, ignorée sinon. */
    @SerializedName("scale")
    public double scale = 1.0;

    @SerializedName("bossName")
    public String bossName = "Boss";

    @SerializedName("entityType")
    public String entityType = "minecraft:wither_skeleton";

    @SerializedName("useCustomEntity")
    public boolean useCustomEntity;

    @SerializedName("customEntityName")
    public String customEntityName;

    @SerializedName("maxHealth")
    public double maxHealth = 100;

    @SerializedName("attackDamage")
    public double attackDamage = 10;

    @SerializedName("movementSpeed")
    public double movementSpeed = 0.3;

    @SerializedName("followRange")
    public double followRange = 50;

    @SerializedName("knockbackResistance")
    public double knockbackResistance = 0.5;

    @SerializedName("equipment")
    public Map<String, EquipmentEntry> equipment;

    @SerializedName("lootTable")
    public List<LootEntry> lootTable;

    @SerializedName("useMinionSummon")
    public boolean useMinionSummon;

    @SerializedName("minionConfig")
    public BossMinionConfig minionConfig;

    /** Difficulté « Boss 2.0 » (phases, mise à l'échelle, anti-exploitation…). Absente = réglages par défaut (activés). */
    @SerializedName("difficulty")
    public BossDifficulty difficulty;

    /** Réglages effectifs (jamais null). */
    public BossDifficulty difficulty() {
        return difficulty != null ? difficulty : new BossDifficulty();
    }

    /** Socle de difficulté commun à tous les boss. */
    public static class BossDifficulty {
        /** Interrupteur général. */
        @SerializedName("enabled") public boolean enabled = true;

        // ── Mise à l'échelle ──
        /** PV supplémentaires par joueur au-delà du premier (0.4 = +40 %). */
        @SerializedName("hpPerExtraPlayer") public double hpPerExtraPlayer = 0.4;

        // ── Phases (66 % et 33 % des PV) ──
        @SerializedName("phases") public boolean phases = true;
        /** Vitesse gagnée à chaque phase (0.15 = +15 %). */
        @SerializedName("phaseSpeedBonus") public double phaseSpeedBonus = 0.15;
        /** Dégâts gagnés à chaque phase. */
        @SerializedName("phaseDamageBonus") public double phaseDamageBonus = 0.15;
        /** Multiplicateur des temps de recharge à chaque phase (0.8 = 20 % plus rapide). */
        @SerializedName("phaseCooldownMult") public double phaseCooldownMult = 0.8;

        // ── Anti-exploitation ──
        @SerializedName("antiCheese") public boolean antiCheese = true;
        /** Au-delà de cette distance (blocs), un joueur est considéré comme hors d'atteinte. */
        @SerializedName("leashDistance") public int leashDistance = 20;
        /** Secondes hors d'atteinte avant que le boss réagisse (attraction ou téléportation). */
        @SerializedName("leashSeconds") public int leashSeconds = 5;

        // ── Bouclier qui renvoie les projectiles ──
        @SerializedName("reflectShield") public boolean reflectShield = true;
        /** À partir de la phase 2 : bouclier toutes les N secondes… */
        @SerializedName("reflectEverySeconds") public int reflectEverySeconds = 30;
        /** …pendant N secondes. */
        @SerializedName("reflectDurationSeconds") public int reflectDurationSeconds = 5;

        // ── Plafond de dégâts ──
        /** Dégâts maximum par coup, en % des PV max du boss (0 = désactivé). */
        @SerializedName("damageCapPercent") public double damageCapPercent = 8;

        // ── Affixes aléatoires ──
        @SerializedName("affixes") public boolean affixes = true;
        /** Nombre d'affixes tirés à chaque apparition (entre min et max). */
        @SerializedName("affixMin") public int affixMin = 1;
        @SerializedName("affixMax") public int affixMax = 2;
        /** Affixes autorisés (vide = tous) : vampiric, armored, summoner, swift, reflector, volatile, glacial, infernal,
         *  stormcaller, blinking, regenerating, berserker, magnetic, toxic. */
        @SerializedName("allowedAffixes") public java.util.List<String> allowedAffixes;
        /** « random » : tirage de affixMin à affixMax parmi les affixes autorisés ; « fixed » : TOUS les affixes choisis, sans tirage. */
        @SerializedName("affixMode") public String affixMode = "random";
    }
}
