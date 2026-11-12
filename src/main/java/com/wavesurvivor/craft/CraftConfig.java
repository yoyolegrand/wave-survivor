package com.wavesurvivor.craft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.Patch;
import com.wavesurvivor.horde.editor.HordeJson;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RÉGLAGES DE L'ARTISANAT SACRÉ ET PROFANE (nourriture, plus tard blocs utilitaires et équipement).
 * Base livrée avec le mod : defaults/wavesurvivor/crafting_fragments.json ; couche du joueur : config/wavesurvivor/crafting_fragments.json
 * (seulement les champs à changer). Rechargé par /ws reload. Les recettes sont des recettes vanilla (datapack).
 */
public final class CraftConfig {

    /** Effet de potion appliqué par un aliment : delay = secondes avant l'application. */
    public record FoodEffect(ResourceLocation effect, int amplifier, int seconds, int delay) {}

    /** Réglages d'un aliment spécial. */
    public record Food(int nutrition, float saturation, int cleanse, List<FoodEffect> effects,
                       int damageBonusPercent, int maxHealthLoss, boolean noRegen, int buffSeconds) {
        public boolean hasBuff() { return damageBonusPercent != 0 || maxHealthLoss > 0 || noRegen; }
    }

    private static volatile JsonObject root = null;

    private CraftConfig() {}

    private static JsonObject base() {
        try (InputStream in = CraftConfig.class.getResourceAsStream("/defaults/wavesurvivor/crafting_fragments.json")) {
            if (in != null) return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Artisanat] Lecture de la base impossible : {}", e.getMessage());
        }
        return new JsonObject();
    }

    public static synchronized void load() {
        JsonObject r = base();
        Path f = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("crafting_fragments.json");
        if (Files.exists(f)) {
            try {
                r = Patch.apply(r, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[Artisanat] {} illisible : {}", f.getFileName(), e.getMessage());
            }
        }
        root = r;
        WaveSurvivorMod.LOGGER.info("[Artisanat] Réglages chargés.");
    }

    private static JsonObject cfg() {
        if (root == null) load();
        return root;
    }

    private static JsonObject sub(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    /** Réglages de l'aliment « id » (ex. « blessed_bread »), ou valeurs neutres si absent. */
    public static Food food(String id) {
        JsonObject o = sub(sub(cfg(), "food"), id);
        List<FoodEffect> list = effectsOf(o);
        return new Food(Math.max(0, HordeJson.num(o, "nutrition", 4)), (float) Math.max(0, HordeJson.dbl(o, "saturation", 0.6)),
                Math.max(0, HordeJson.num(o, "cleanse", 0)), list,
                HordeJson.num(o, "damageBonusPercent", 0), Math.max(0, HordeJson.num(o, "maxHealthLoss", 0)),
                HordeJson.bool(o, "noRegen", false), Math.max(1, HordeJson.num(o, "buffSeconds", 60)));
    }

    /** Liste « effects » d'un objet JSON (effet, amplificateur, secondes, délai). */
    private static List<FoodEffect> effectsOf(JsonObject o) {
        List<FoodEffect> list = new ArrayList<>();
        if (o.has("effects") && o.get("effects").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("effects")) {
                if (!e.isJsonObject()) continue;
                JsonObject eo = e.getAsJsonObject();
                ResourceLocation rl = ResourceLocation.tryParse(HordeJson.str(eo, "effect", ""));
                if (rl == null) continue;
                list.add(new FoodEffect(rl, Math.max(0, HordeJson.num(eo, "amplifier", 0)),
                        Math.max(1, HordeJson.num(eo, "seconds", 10)), Math.max(0, HordeJson.num(eo, "delay", 0))));
            }
        }
        return list;
    }

    // ─── Festin de grâce ───

    /** Effets de potion donnés par chaque part du Festin de grâce. */
    public static List<FoodEffect> feastEffects() { return effectsOf(sub(cfg(), "feast")); }

    public static int feastNutrition() { return Math.max(0, HordeJson.num(sub(cfg(), "feast"), "nutrition", 8)); }

    public static float feastSaturation() { return (float) Math.max(0, HordeJson.dbl(sub(cfg(), "feast"), "saturation", 0.8)); }

    public static float feastHeal() { return (float) Math.max(0, HordeJson.dbl(sub(cfg(), "feast"), "heal", 8)); }

    // ─── Blocs utilitaires ───

    /** Réglage numérique d'un bloc utilitaire (« blocks.<bloc>.<clé> »). */
    public static double blockNum(String block, String key, double def) {
        return HordeJson.dbl(sub(sub(cfg(), "blocks"), block), key, def);
    }

    /** Objet JSON complet d'un bloc utilitaire (« blocks.<bloc> »), vide si absent : pour les listes (ex. vague du Totem d'appât). */
    public static JsonObject block(String block) {
        return sub(sub(cfg(), "blocks"), block);
    }

    /** Réglage numérique d'un équipement (« equipment.<objet>.<clé> »). */
    public static double equipNum(String item, String key, double def) {
        return HordeJson.dbl(sub(sub(cfg(), "equipment"), item), key, def);
    }

    /** Liste de textes d'« equipment.<clé> » (ex. mots-clés de noms de morts-vivants). */
    public static List<String> equipStrings(String key) {
        List<String> out = new ArrayList<>();
        JsonObject e = sub(cfg(), "equipment");
        if (e.has(key) && e.get(key).isJsonArray()) for (JsonElement x : e.getAsJsonArray(key)) if (x.isJsonPrimitive()) out.add(x.getAsString().toLowerCase(java.util.Locale.ROOT));
        return out;
    }

    // ─── Fabrications de l'onglet Artisanat du Sanctuaire ───

    /** Un ingrédient : objet « mod:id » ou tag « #mod:tag ». */
    public record Cost(String item, int count) {}

    /** Une fabrication : align = « consecrated » ou « profaned ». */
    public record Craft(String id, String result, int count, String align, List<Cost> cost) {}

    public static List<Craft> crafts() {
        List<Craft> out = new ArrayList<>();
        JsonObject c = cfg();
        if (!c.has("shrineCrafts") || !c.get("shrineCrafts").isJsonArray()) return out;
        for (JsonElement e : c.getAsJsonArray("shrineCrafts")) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String id = HordeJson.str(o, "id", ""), result = HordeJson.str(o, "result", "");
            if (id.isEmpty() || result.isEmpty()) continue;
            List<Cost> cost = new ArrayList<>();
            if (o.has("cost") && o.get("cost").isJsonArray()) {
                for (JsonElement ce : o.getAsJsonArray("cost")) {
                    if (!ce.isJsonObject()) continue;
                    String it = HordeJson.str(ce.getAsJsonObject(), "item", "");
                    if (!it.isEmpty()) cost.add(new Cost(it, Math.max(1, HordeJson.num(ce.getAsJsonObject(), "count", 1))));
                }
            }
            out.add(new Craft(id, result, Math.max(1, HordeJson.num(o, "count", 1)), HordeJson.str(o, "align", "consecrated"), cost));
        }
        return out;
    }
}
