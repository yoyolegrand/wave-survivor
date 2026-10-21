package com.wavesurvivor.horde.benediction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.ConfigLayers;
import com.wavesurvivor.network.BlessingCatalogPacket;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CATALOGUE DES BÉNÉDICTIONS (1.8) : les bénédictions ne sont plus écrites dans le code mais décrites par des DONNÉES, en CATÉGORIES
 * (« Bénédiction de Vie »…) qui contiennent chacune leurs niveaux de rareté (COMMON, RARE, EPIC, LEGENDARY), chaque niveau pouvant être
 * absent ou désactivé (ex. Longévité : Épique et Légendaire seulement).
 *
 * Couches de configuration, comme pour les hordes :
 *   • base livrée avec le mod : defaults/wavesurvivor/blessings.json, relue à chaque lancement ;
 *   • couche du joueur : config/wavesurvivor/custom_blessings.json (CustomBlessingStore).
 * Le serveur envoie le catalogue effectif aux clients (BlessingCatalogPacket) : à la connexion et à chaque rechargement.
 *
 * Identifiant d'une bénédiction : BENEDICTION_DE_&lt;catégorie&gt;_&lt;rareté&gt; (identique aux versions précédentes : les parties en cours,
 * qui retiennent les niveaux par identifiant, ne sont pas touchées).
 */
public final class BlessingCatalog {

    public static final String[] RARITIES = {"COMMON", "RARE", "EPIC", "LEGENDARY"};
    private static final String[] RARITY_LABELS_FR = {"Commun", "Rare", "Épique", "Légendaire"};
    private static final ChatFormatting[] RARITY_COLORS = {ChatFormatting.WHITE, ChatFormatting.BLUE, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW};

    /** Catégories effectives (base + joueur) : ce qui est envoyé aux clients et affiché par l'éditeur. */
    private static volatile JsonArray effective = new JsonArray();
    private static volatile boolean loaded = false;
    /** Les bénédictions (avec leurs attributs) sont construites au premier accès : au démarrage du mod, les registres ne sont pas encore remplis. */
    private static volatile boolean built = false;

    private BlessingCatalog() {}

    // ─── Chargement ───

    /** Construit le catalogue : base du mod + couche du joueur. À appeler au démarrage et à chaque rechargement. */
    public static synchronized void load() {
        JsonArray base = ConfigLayers.builtin().blessings.deepCopy();
        JsonArray merged = CustomBlessingStore.mergeInto(base);
        install(merged);
        WaveSurvivorMod.LOGGER.info("[Bénédictions] {} catégories chargées.", merged.size());
    }

    /** Côté client : catalogue reçu du serveur. */
    public static synchronized void loadFromJson(String json) {
        try {
            JsonElement el = JsonParser.parseString(json);
            if (el.isJsonArray()) install(el.getAsJsonArray());
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[Bénédictions] Catalogue reçu illisible : {}", e.getMessage());
        }
    }

    /** Charge le catalogue local s'il ne l'est pas encore (premier accès, avant toute synchronisation). */
    public static void ensureLoaded() {
        if (!loaded) load();
    }

    private static void install(JsonArray cats) {
        effective = cats;
        loaded = true;
        built = false; // reconstruit au prochain accès
    }

    public static boolean isBuilt() { return built; }

    /** Construit les bénédictions depuis les catégories effectives (les attributs doivent exister : registres prêts). */
    static synchronized void buildNow() {
        if (ForgeRegistries.ATTRIBUTES.getKeys().isEmpty()) return; // trop tôt : on réessaiera
        Map<String, RogueUpgrade> map = build(effective);
        RogueUpgradeRegistry.replaceAll(map);
        built = true;
        WaveSurvivorMod.LOGGER.info("[Bénédictions] {} bénédictions construites.", map.size());
    }

    public static String toJson() {
        ensureLoaded();
        return effective.toString();
    }

    /** Copie des catégories effectives (pour l'éditeur). */
    public static JsonArray categories() {
        ensureLoaded();
        return effective.deepCopy();
    }

    // ─── Synchronisation vers les clients ───

    public static void syncTo(ServerPlayer p) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new BlessingCatalogPacket(toJson()));
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new BlessingCatalogPacket(toJson()));
    }

    // ─── Construction des bénédictions ───

    private static Map<String, RogueUpgrade> build(JsonArray cats) {
        Map<String, RogueUpgrade> out = new LinkedHashMap<>();
        for (JsonElement ce : cats) {
            if (!ce.isJsonObject()) continue;
            JsonObject cat = ce.getAsJsonObject();
            String cid = str(cat, "id");
            if (cid.isBlank() || !cat.has("tiers") || !cat.get("tiers").isJsonObject()) continue;
            JsonObject tiers = cat.getAsJsonObject("tiers");
            for (int i = 0; i < RARITIES.length; i++) {
                if (!tiers.has(RARITIES[i]) || !tiers.get(RARITIES[i]).isJsonObject()) continue;
                JsonObject t = tiers.getAsJsonObject(RARITIES[i]);
                if (t.has("enabled") && t.get("enabled").isJsonPrimitive() && !t.get("enabled").getAsBoolean()) continue;
                RogueUpgrade u = tier(cat, cid, t, i);
                if (u != null) out.put(u.id(), u);
            }
        }
        return out;
    }

    /** Identifiant d'une bénédiction : BENEDICTION_DE_&lt;catégorie&gt;_&lt;rareté&gt;. */
    public static String idOf(String categoryId, String rarity) {
        return "BENEDICTION_DE_" + categoryId + "_" + rarity;
    }

    private static RogueUpgrade tier(JsonObject cat, String cid, JsonObject t, int rarityIndex) {
        List<RogueUpgrade.AttributeMod> mods = new ArrayList<>();
        if (t.has("modifiers") && t.get("modifiers").isJsonArray()) {
            for (JsonElement me : t.getAsJsonArray("modifiers")) {
                if (!me.isJsonObject()) continue;
                JsonObject m = me.getAsJsonObject();
                Attribute attr = attribute(str(m, "attribute"));
                if (attr == null) {
                    WaveSurvivorMod.LOGGER.warn("[Bénédictions] Attribut inconnu « {} » ({}) : ignoré (mod absent ?).", str(m, "attribute"), cid);
                    continue;
                }
                double amount = m.has("amount") && m.get("amount").isJsonPrimitive() ? m.get("amount").getAsDouble() : 0;
                mods.add(new RogueUpgrade.AttributeMod(attr, amount, operation(str(m, "operation"))));
            }
        }
        int heal = t.has("heal") && t.get("heal").isJsonPrimitive() ? t.get("heal").getAsInt() : 0;
        if (mods.isEmpty() && heal <= 0) return null;
        String name = t.has("name") && !str(t, "name").isBlank() ? str(t, "name")
                : str(cat, "name") + " (" + RARITY_LABELS_FR[rarityIndex] + ")";
        String desc = t.has("description") && !str(t, "description").isBlank() ? str(t, "description") : str(cat, "description");
        return new RogueUpgrade(idOf(cid, RARITIES[rarityIndex]), name, desc, str(cat, "icon"), RARITY_COLORS[rarityIndex], mods, heal);
    }

    public static Attribute attribute(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(id.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    public static Operation operation(String s) {
        return switch (s == null ? "" : s.toLowerCase()) {
            case "multiply_base" -> Operation.MULTIPLY_BASE;
            case "multiply_total" -> Operation.MULTIPLY_TOTAL;
            default -> Operation.ADDITION;
        };
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }
}
