package com.wavesurvivor.horde.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Événement de chaos déclenché aléatoirement pendant une horde.
 * Types supportés : "merchant" (spawn villageois avec trades), "spawn" (spawn mob).
 */
public class ChaosEvent {

    @SerializedName("type")
    public String type = "spawn";

    @SerializedName("message")
    public String message;

    @SerializedName("sound")
    public String sound;

    /** Pour type "spawn" : entityType à spawn. */
    @SerializedName("entityType")
    public String entityType;

    @SerializedName("isCustomEntity")
    public boolean isCustomEntity;

    /** Pour type "spawn" : quantité (défaut 1 si absent). */
    @SerializedName("count")
    public int count = 1;

    /** Pour type "merchant" : trades du marchand. */
    @SerializedName("trades")
    public List<ChaosTrade> trades;

    /** Pour type "merchant" : durée de vie en secondes (défaut 60). */
    @SerializedName("lifetime")
    public int lifetime = 60;

    /** Damage bonus (non utilisé Phase 1e). */
    @SerializedName("damage")
    public int damage;

    // ─── Type "totem" (T2) ───

    /** Apparence : nom d'une TotemConfig (tête au sommet). */
    @SerializedName("totemName")
    public String totemName;

    /** Aura : fureur, soin, malediction, invocation. */
    @SerializedName("aura")
    public String aura = "fureur";

    /** Rayon de l'aura (blocs). */
    @SerializedName("radius")
    public double radius = 8;

    /** PV de chaque totem. */
    @SerializedName("totemHealth")
    public double totemHealth = 40;

    /** Récompense quand un totem est détruit. */
    @SerializedName("rewardItem")
    public String rewardItem = "minecraft:emerald";

    @SerializedName("rewardCount")
    public int rewardCount = 3;

    /** Tête au sommet du totem (item), prioritaire sur totemName. */
    @SerializedName("headItem")
    public String headItem;

    /** Effets de l'aura (vide = effets par défaut de l'aura). */
    @SerializedName("effects")
    public List<AuraEffect> effects;

    /** Soin (aura « soin ») : PV rendus par seconde aux monstres. */
    @SerializedName("healPerSecond")
    public double healPerSecond = 2;

    /** Invocation : monstres par vague d'invocation, intervalle (s), maximum vivants. */
    @SerializedName("summonCount")
    public int summonCount = 2;
    @SerializedName("summonInterval")
    public int summonInterval = 10;
    @SerializedName("summonMax")
    public int summonMax = 6;

    public static class AuraEffect {
        /** Ex : minecraft:blindness */
        @SerializedName("effect") public String effect;
        /** Niveau 1 = I, 2 = II... */
        @SerializedName("level") public int level = 1;
        /** joueurs | monstres */
        @SerializedName("target") public String target = "joueurs";

        public AuraEffect() {}
        public AuraEffect(String effect, int level, String target) { this.effect = effect; this.level = level; this.target = target; }
    }

    // ─── Type "gisement" (dépôt de minerai à miner) ───

    /** Blocs visuels de l'amas (ex : minerai + pierre). */
    @SerializedName("blocks")
    public List<String> blocks;

    /** Arbre : blocs du tronc et du feuillage (plusieurs possibles, alternés). Vides = repli sur {@code blocks}. */
    @SerializedName("trunkBlocks")
    public List<String> trunkBlocks;

    @SerializedName("leafBlocks")
    public List<String> leafBlocks;

    /** Arbre : fruits lumineux dans la couronne (absent = shroomlight, liste vide = aucun fruit). */
    @SerializedName("fruitBlocks")
    public List<String> fruitBlocks;

    /** Nombre de coups de pioche pour le détruire. */
    @SerializedName("hits")
    public int hits = 8;

    /** Pioche minimum : 0 bois, 1 pierre, 2 fer, 3 diamant, 4 netherite. */
    @SerializedName("minTier")
    public int minTier = 1;

    /** Butin à la destruction. */
    @SerializedName("drops")
    public List<Drop> drops;

    /** Fragment possible à chaque coup (ancien format, un seul objet). */
    @SerializedName("hitDropItem")
    public String hitDropItem;

    /** Butin possible à chaque coup de pioche (liste complète). */
    @SerializedName("hitDrops")
    public List<Drop> hitDrops;

    @SerializedName("hitDropChance")
    public double hitDropChance = 25;

    @SerializedName("distanceMin")
    public int distanceMin = 10;

    @SerializedName("distanceMax")
    public int distanceMax = 25;

    /**
     * Gisement / Arbre : nombre d'apparitions GARANTIES à chaque Calme (Kingdom) ou à chaque pause entre deux vagues
     * (horde classique), en plus du tirage au hasard. 0 = uniquement au hasard.
     */
    @SerializedName("guaranteed")
    public int guaranteed = 0;

    /** Taille : petit, moyen, grand, aleatoire (60 / 30 / 10 %). */
    @SerializedName("size")
    public String size = "petit";

    public static class Drop {
        @SerializedName("item") public String item;
        @SerializedName("minQty") public int minQty = 1;
        @SerializedName("maxQty") public int maxQty = 1;
        @SerializedName("chance") public double chance = 100;
    }

    public static class ChaosTrade {
        @SerializedName("input1")
        public String input1;
        @SerializedName("input1Count")
        public int input1Count = 1;

        @SerializedName("input2")
        public String input2;
        @SerializedName("input2Count")
        public int input2Count = 0;

        @SerializedName("output")
        public String output;
        @SerializedName("outputCount")
        public int outputCount = 1;

        @SerializedName("maxUses")
        public int maxUses = 5;
    }
}
