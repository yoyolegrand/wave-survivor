package com.wavesurvivor.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.Map;

/**
 * CONSTRUCTION DE LA COUCHE « BASE » (1.7) : ce que le mod livre, reconstruit à chaque démarrage depuis le jar.
 *
 * Ordre : config.json (base) → custom_hordes.json / custom_entities.json livrés (hordes et entités éditées par l'auteur)
 * → fichiers d'ajouts des mises à jour (hordes, entités, compétences, autels, déblocages, lots de coffres).
 * Le fichier du joueur (config/wavesurvivor/…) vient ENSUITE et ne contient que ses ajouts et ses modifications.
 *
 * Uniquement Gson : l'outil tools/GenLegacyHashes s'en sert aussi pour reconstruire la base des versions précédentes
 * (lues dans git) et en tirer les empreintes utilisées par la migration.
 */
public final class BuiltinBuilder {

    /** Lecture d'un fichier de défauts, chemin relatif à defaults/wavesurvivor/ ; null si absent. */
    public interface Source { String read(String relPath); }

    /** La base reconstruite. */
    public static final class Result {
        /** Clés base44 : HordeConfigMulti, CustomEntity, CustomSkill, RenaissanceConfig… */
        public JsonObject payload = new JsonObject();
        /** Définitions d'autel : nom de horde → définition. */
        public JsonObject altar = new JsonObject();
        /** Coffres roulette livrés (clé « name »). */
        public JsonArray chests = new JsonArray();
        /** Boutique Renaissance livrée : reliques (clé « id ») et identifiants masqués. */
        public JsonArray shopItems = new JsonArray();
        public JsonArray shopDisabled = new JsonArray();
        /** Raretés de sacrifice livrées : objet → rareté. */
        public JsonObject rarities = new JsonObject();
        /** Catégories de bénédictions livrées (1.8) : chacune avec ses niveaux de rareté. */
        public JsonArray blessings = new JsonArray();
        /** Réglages du tirage des bénédictions livrés (poids des raretés, Chance, relances, bannissements). */
        public JsonObject blessingSettings = new JsonObject();
    }

    private BuiltinBuilder() {}

    public static Result build(Source src, List<String> additions) {
        Result r = new Result();

        JsonObject cfg = parse(src.read("config.json"));
        if (cfg != null) r.payload = cfg.has("donnees") && cfg.get("donnees").isJsonObject() ? cfg.getAsJsonObject("donnees").deepCopy() : cfg.deepCopy();

        JsonObject hordes = parse(src.read("custom_hordes.json"));
        if (hordes != null) overlay(arr(r.payload, "HordeConfigMulti", true), arr(hordes, "hordes", false), arr(hordes, "deleted", false), "hordeName");

        JsonObject ents = parse(src.read("custom_entities.json"));
        if (ents != null) {
            overlay(arr(r.payload, "CustomEntity", true), arr(ents, "entities", false), arr(ents, "deletedEntities", false), "entityName");
            overlay(arr(r.payload, "CustomSkill", true), arr(ents, "skills", false), arr(ents, "deletedSkills", false), "skillName");
        }

        JsonObject altar = parse(src.read("altar_recipes.json"));
        if (altar != null) r.altar = altar.deepCopy();

        JsonObject chests = parse(src.read("custom_roulette_chests.json"));
        if (chests != null) r.chests = arr(chests, "chests", false).deepCopy();

        JsonObject shop = parse(src.read("renaissance_shop_custom.json"));
        if (shop != null) {
            r.shopItems = arr(shop, "items", false).deepCopy();
            r.shopDisabled = arr(shop, "disabled", false).deepCopy();
        }

        JsonObject rar = parse(src.read("renaissance_rarities.json"));
        if (rar != null) r.rarities = rar.deepCopy();

        JsonObject bless = parse(src.read("blessings.json"));
        if (bless != null) {
            r.blessings = arr(bless, "categories", false).deepCopy();
            if (bless.has("settings") && bless.get("settings").isJsonObject()) r.blessingSettings = bless.getAsJsonObject("settings").deepCopy();
        }

        for (String a : additions) {
            JsonObject add = parse(src.read(a));
            if (add != null) applyAddition(r, add);
        }
        return r;
    }

    // ─── Ajouts des mises à jour ───

    private static void applyAddition(Result r, JsonObject add) {
        addIfAbsent(arr(r.payload, "HordeConfigMulti", true), arr(add, "hordes", false), "hordeName");
        addIfAbsent(arr(r.payload, "CustomEntity", true), arr(add, "entities", false), "entityName");
        addIfAbsent(arr(r.payload, "CustomSkill", true), arr(add, "skills", false), "skillName");

        if (add.has("altarRecipes") && add.get("altarRecipes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : add.getAsJsonObject("altarRecipes").entrySet()) {
                boolean exists = false;
                for (String k : r.altar.keySet()) if (k.equalsIgnoreCase(e.getKey())) { exists = true; break; }
                if (!exists) r.altar.add(e.getKey(), e.getValue().deepCopy());
            }
        }

        // Conditions de déblocage : posées sur les hordes nommées qui n'en ont aucune
        if (add.has("unlocks") && add.get("unlocks").isJsonObject()) {
            JsonObject unlocks = add.getAsJsonObject("unlocks");
            for (JsonElement e : arr(r.payload, "HordeConfigMulti", true)) {
                if (!e.isJsonObject() || !e.getAsJsonObject().has("hordeName")) continue;
                JsonObject h = e.getAsJsonObject();
                String name = h.get("hordeName").getAsString().trim();
                JsonElement reqs = null;
                for (Map.Entry<String, JsonElement> u : unlocks.entrySet()) if (u.getKey().equalsIgnoreCase(name)) reqs = u.getValue();
                if (reqs == null || !reqs.isJsonArray()) continue;
                if (!h.has("configData") || !h.get("configData").isJsonObject()) continue;
                JsonObject cd = h.getAsJsonObject("configData");
                if (cd.has("unlockRequires") && cd.get("unlockRequires").isJsonArray() && cd.getAsJsonArray("unlockRequires").size() > 0) continue;
                cd.add("unlockRequires", reqs.deepCopy());
            }
        }

        // Lots ajoutés aux coffres existants (par nom de coffre) : seulement les objets absents du coffre
        if (add.has("chestRewards") && add.get("chestRewards").isJsonObject()) {
            JsonObject byChest = add.getAsJsonObject("chestRewards");
            for (JsonElement e : r.chests) {
                if (!e.isJsonObject() || !e.getAsJsonObject().has("name")) continue;
                JsonObject chest = e.getAsJsonObject();
                String name = chest.get("name").getAsString().trim();
                JsonElement adds = null;
                for (Map.Entry<String, JsonElement> c : byChest.entrySet()) if (c.getKey().equalsIgnoreCase(name)) adds = c.getValue();
                if (adds == null || !adds.isJsonArray()) continue;
                JsonArray rewards = arr(chest, "rewards", true);
                java.util.Set<String> have = new java.util.HashSet<>();
                for (JsonElement x : rewards) if (x.isJsonObject() && x.getAsJsonObject().has("item")) have.add(x.getAsJsonObject().get("item").getAsString().toLowerCase());
                for (JsonElement x : adds.getAsJsonArray()) {
                    if (!x.isJsonObject() || !x.getAsJsonObject().has("item")) continue;
                    if (have.add(x.getAsJsonObject().get("item").getAsString().toLowerCase())) rewards.add(x.deepCopy());
                }
            }
        }
    }

    // ─── Outils ───

    public static String key(String n) { return n == null ? "" : n.trim().toLowerCase(); }

    public static String nameOf(JsonElement e, String nameKey) {
        if (e == null || !e.isJsonObject()) return "";
        JsonObject o = e.getAsJsonObject();
        return o.has(nameKey) && o.get(nameKey).isJsonPrimitive() ? o.get(nameKey).getAsString() : "";
    }

    /** Élément nommé d'un tableau (sans casse), ou null. */
    public static JsonObject find(JsonArray list, String nameKey, String name) {
        if (list == null) return null;
        String k = key(name);
        for (JsonElement e : list) if (e.isJsonObject() && key(nameOf(e, nameKey)).equals(k)) return e.getAsJsonObject();
        return null;
    }

    public static JsonObject parse(String txt) {
        if (txt == null || txt.isBlank()) return null;
        JsonElement e = JsonParser.parseString(txt);
        return e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /** Tableau d'un objet ; créé (et attaché) s'il manque quand create = vrai. */
    public static JsonArray arr(JsonObject o, String k, boolean create) {
        if (o.has(k) && o.get(k).isJsonArray()) return o.getAsJsonArray(k);
        JsonArray a = new JsonArray();
        if (create) o.add(k, a);
        return a;
    }

    /** Remplace (sur place) les éléments de même nom par ceux de custom, ajoute les autres, retire les supprimés non redéfinis. */
    static void overlay(JsonArray base, JsonArray custom, JsonArray deleted, String nameKey) {
        java.util.Map<String, JsonElement> byName = new java.util.LinkedHashMap<>();
        for (JsonElement e : custom) if (e.isJsonObject()) byName.put(key(nameOf(e, nameKey)), e);
        java.util.Set<String> del = new java.util.HashSet<>();
        for (JsonElement e : deleted) if (e.isJsonPrimitive()) del.add(key(e.getAsString()));
        java.util.List<JsonElement> out = new java.util.ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (JsonElement e : base) {
            String k = key(nameOf(e, nameKey));
            if (k.isEmpty()) { out.add(e); continue; }
            if (del.contains(k) && !byName.containsKey(k)) continue;
            out.add(byName.containsKey(k) ? byName.get(k).deepCopy() : e);
            used.add(k);
        }
        for (java.util.Map.Entry<String, JsonElement> c : byName.entrySet()) if (!used.contains(c.getKey())) out.add(c.getValue().deepCopy());
        while (base.size() > 0) base.remove(0);
        for (JsonElement e : out) base.add(e);
    }

    /** Ajoute chaque élément dont le nom est absent (sans casse). */
    static void addIfAbsent(JsonArray base, JsonArray items, String nameKey) {
        java.util.Set<String> have = new java.util.HashSet<>();
        for (JsonElement e : base) have.add(key(nameOf(e, nameKey)));
        for (JsonElement e : items) {
            String k = key(nameOf(e, nameKey));
            if (k.isEmpty() || !have.add(k)) continue;
            base.add(e.deepCopy());
        }
    }
}
