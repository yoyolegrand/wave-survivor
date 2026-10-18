package com.wavesurvivor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * MIGRATION VERS LES COUCHES DE CONFIGURATION (1.7), une seule fois par dossier de config.
 *
 * Jusqu'en 1.6, le mod COPIAIT sa configuration d'origine dans config/wavesurvivor/ au premier lancement : le fichier du
 * joueur mélangeait donc l'ancienne base et ses propres ajouts. Ici on les sépare :
 *   1. le dossier est copié tel quel dans config/wavesurvivor_backup_avant_couches (rien ne peut se perdre) ;
 *   2. pour chaque élément on retrouve, parmi les versions LIVRÉES (legacy_base.json, générée depuis les tags précédents, +
 *      la base actuelle), celle dont il est le plus proche : ce que le joueur a changé = la différence avec elle ;
 *   3. identique à une version livrée → retiré (il suivra le mod) ; modifié → seule la différence est gardée (patch champ par
 *      champ, appliqué sur la base ACTUELLE : les champs qu'il n'a pas touchés profitent des corrections) ;
 *      créé par le joueur → gardé entier.
 * En cas de doute, le contenu du joueur est conservé : on ne supprime jamais ce qu'il a fait.
 */
public final class ConfigMigration {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String MARKER = "config_layers.txt";
    private static final String[] FILES = {"config.json", "custom_hordes.json", "custom_entities.json", "altar_recipes.json",
            "custom_roulette_chests.json", "renaissance_shop_custom.json", "renaissance_rarities.json"};

    private JsonObject legacy = new JsonObject();
    private final StringBuilder report = new StringBuilder();

    private ConfigMigration() {}

    /** À appeler avant tout chargement de configuration. */
    public static void runIfNeeded(Path dir) {
        try {
            Files.createDirectories(dir);
            Path marker = dir.resolve(MARKER);
            if (Files.exists(marker)) return;
            boolean any = false;
            for (String f : FILES) if (Files.exists(dir.resolve(f))) any = true;
            if (any) {
                ConfigMigration m = new ConfigMigration();
                m.migrate(dir);
                Files.writeString(marker, "Couches de configuration (1.7) — migration faite.\n" + m.report, StandardCharsets.UTF_8);
                WaveSurvivorMod.LOGGER.info("[Config] Migration vers les couches terminée :\n{}", m.report);
            } else {
                Files.writeString(marker, "Couches de configuration (1.7) — nouvelle installation, rien à migrer.\n", StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Config] Migration vers les couches impossible : {}", e.getMessage(), e);
        }
    }

    // ─── Déroulement ───

    private void migrate(Path dir) throws Exception {
        backup(dir);
        JsonObject parsed = BuiltinBuilder.parse(ConfigLayers.readResource("legacy_base.json"));
        if (parsed != null) legacy = parsed;

        JsonObject hordes = readOrEmpty(dir.resolve("custom_hordes.json"));
        JsonObject ents = readOrEmpty(dir.resolve("custom_entities.json"));

        Layer h = new Layer("hordes", "hordeName");
        Layer e = new Layer("entities", "entityName");
        Layer s = new Layer("skills", "skillName");
        h.addAll(BuiltinBuilder.arr(hordes, "hordes", false));
        e.addAll(BuiltinBuilder.arr(ents, "entities", false));
        s.addAll(BuiltinBuilder.arr(ents, "skills", false));

        // Ancien config.json : ce que le joueur y avait modifié (et qu'aucun autre fichier ne masque) passe dans sa couche
        Path cfgFile = dir.resolve("config.json");
        if (Files.exists(cfgFile)) {
            JsonObject root = readOrEmpty(cfgFile);
            JsonObject payload = root.has("donnees") && root.get("donnees").isJsonObject() ? root.getAsJsonObject("donnees") : root;
            int n = h.addUnshadowed(BuiltinBuilder.arr(payload, "HordeConfigMulti", false), BuiltinBuilder.arr(hordes, "deleted", false))
                    + e.addUnshadowed(BuiltinBuilder.arr(payload, "CustomEntity", false), BuiltinBuilder.arr(ents, "deletedEntities", false))
                    + s.addUnshadowed(BuiltinBuilder.arr(payload, "CustomSkill", false), BuiltinBuilder.arr(ents, "deletedSkills", false));
            report.append("config.json : ").append(n).append(" élément(s) modifié(s) récupéré(s), le fichier n'est plus utilisé.\n");
            // Autres sections (bénédictions, pacte de sang, objets…) : elles viennent désormais du mod ; on signale celles qui diffèrent
            List<String> differing = new ArrayList<>();
            for (Map.Entry<String, JsonElement> sec : payload.entrySet()) {
                String k = sec.getKey();
                if (k.equals("HordeConfigMulti") || k.equals("CustomEntity") || k.equals("CustomSkill")) continue;
                JsonElement cur = ConfigLayers.builtin().payload.get(k);
                if (cur != null && !Canon.canon(cur).equals(Canon.canon(sec.getValue()))) differing.add(k);
            }
            if (!differing.isEmpty()) {
                report.append("config.json : sections différentes de celles du mod (ancienne version ou modification à la main) : ")
                        .append(String.join(", ", differing)).append(" — elles ne sont plus lues, voir la copie de sécurité.\n");
            }
            Files.delete(cfgFile);
        }

        if (Files.exists(dir.resolve("custom_hordes.json")) || !h.full.isEmpty() || !h.patches.entrySet().isEmpty()) {
            hordes.add("hordes", h.full);
            hordes.add("patches", h.patches);
            write(dir.resolve("custom_hordes.json"), hordes);
        }
        if (Files.exists(dir.resolve("custom_entities.json")) || !e.full.isEmpty() || !s.full.isEmpty()
                || !e.patches.entrySet().isEmpty() || !s.patches.entrySet().isEmpty()) {
            ents.add("entities", e.full);
            ents.add("skills", s.full);
            ents.add("entityPatches", e.patches);
            ents.add("skillPatches", s.patches);
            write(dir.resolve("custom_entities.json"), ents);
        }
        report.append(h.summary("hordes")).append(e.summary("entités")).append(s.summary("compétences"));

        migrateAltar(dir.resolve("altar_recipes.json"));
        migrateChests(dir.resolve("custom_roulette_chests.json"));
        migrateShop(dir.resolve("renaissance_shop_custom.json"));
        migrateRarities(dir.resolve("renaissance_rarities.json"));
    }

    private static void backup(Path dir) throws Exception {
        Path bk = dir.resolveSibling(dir.getFileName() + "_backup_avant_couches");
        int i = 2;
        while (Files.exists(bk)) bk = dir.resolveSibling(dir.getFileName() + "_backup_avant_couches_" + i++);
        Files.createDirectories(bk);
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isRegularFile(p)) Files.copy(p, bk.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        WaveSurvivorMod.LOGGER.info("[Config] Copie de sécurité de votre config : {}", bk);
    }

    // ─── Versions livrées ───

    /** Versions livrées d'un élément (toutes les versions précédentes + la base actuelle). */
    private List<JsonObject> candidates(String kind, String name, JsonObject current) {
        List<JsonObject> out = new ArrayList<>();
        if (legacy.has("entries") && legacy.getAsJsonObject("entries").has(kind)) {
            JsonObject byName = legacy.getAsJsonObject("entries").getAsJsonObject(kind);
            for (Map.Entry<String, JsonElement> e : byName.entrySet()) {
                if (!e.getKey().equalsIgnoreCase(name.trim())) continue;
                for (JsonElement v : e.getValue().getAsJsonArray()) if (v.isJsonObject()) out.add(v.getAsJsonObject());
            }
        }
        if (current != null) out.add(current);
        return out;
    }

    /** La version livrée la plus proche de l'élément du joueur (celle qu'il a reçue au départ), ou null. */
    private static JsonObject closest(List<JsonObject> cands, JsonObject mine) {
        JsonObject best = null;
        int bd = Integer.MAX_VALUE;
        for (JsonObject c : cands) {
            int d = Patch.distance(c, mine);
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }

    /** Noms présents dans TOUTES les versions livrées : s'ils manquent au fichier du joueur, c'est qu'il les a supprimés. */
    private Set<String> alwaysNames(String kind) {
        Set<String> out = new HashSet<>();
        if (legacy.has("always") && legacy.get("always").isJsonObject() && legacy.getAsJsonObject("always").has(kind)) {
            for (JsonElement e : legacy.getAsJsonObject("always").getAsJsonArray(kind)) out.add(e.getAsString().trim().toLowerCase());
        }
        return out;
    }

    // ─── Hordes / entités / compétences : couche à patchs ───

    private final class Layer {
        final String kind, nameKey;
        final JsonArray full = new JsonArray();
        final JsonObject patches = new JsonObject();
        int identical, patched, created;

        Layer(String kind, String nameKey) { this.kind = kind; this.nameKey = nameKey; }

        JsonObject current(String name) {
            return switch (kind) {
                case "hordes" -> ConfigLayers.builtinHorde(name);
                case "entities" -> ConfigLayers.builtinEntity(CustomEntityStore.Kind.ENTITY, name);
                default -> ConfigLayers.builtinEntity(CustomEntityStore.Kind.SKILL, name);
            };
        }

        void addAll(JsonArray list) { for (JsonElement el : list) if (el.isJsonObject()) add(el.getAsJsonObject()); }

        /** Éléments d'un ancien config.json qu'aucune entrée du joueur ne masque ni ne supprime. */
        int addUnshadowed(JsonArray list, JsonArray deleted) {
            Set<String> mine = new HashSet<>();
            for (JsonElement el : full) mine.add(BuiltinBuilder.key(BuiltinBuilder.nameOf(el, nameKey)));
            for (String k : patches.keySet()) mine.add(BuiltinBuilder.key(k));
            for (JsonElement el : deleted) if (el.isJsonPrimitive()) mine.add(BuiltinBuilder.key(el.getAsString()));
            int before = patched + created;
            for (JsonElement el : list) {
                if (!el.isJsonObject()) continue;
                String k = BuiltinBuilder.key(BuiltinBuilder.nameOf(el, nameKey));
                if (k.isEmpty() || mine.contains(k)) continue;
                add(el.getAsJsonObject());
            }
            return patched + created - before;
        }

        void add(JsonObject mine) {
            String name = BuiltinBuilder.nameOf(mine, nameKey);
            if (name.isBlank()) { full.add(mine); created++; return; }
            JsonObject cur = current(name);
            List<JsonObject> cands = candidates(kind, name, cur);
            if (cands.isEmpty() || cur == null) { full.add(mine); created++; return; } // créé par le joueur (ou base retirée du mod)
            JsonObject origin = closest(cands, mine);
            JsonObject p = Patch.diff(origin, mine);
            if (p == null) { identical++; return; }
            patches.add(name.trim().toLowerCase(), p);
            patched++;
        }

        String summary(String label) {
            return label + " : " + identical + " identique(s) à la base (suivent le mod), " + patched
                    + " modifiée(s) (seuls vos champs changés sont gardés), " + created + " créée(s) par vous.\n";
        }
    }

    // ─── Autels ───

    private void migrateAltar(Path file) throws Exception {
        if (!Files.exists(file)) return;
        JsonObject root = readOrEmpty(file);
        JsonObject out = new JsonObject();
        BuiltinBuilder.Result base = ConfigLayers.builtin();
        int identical = 0, patched = 0, created = 0;
        Set<String> present = new HashSet<>();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            present.add(e.getKey().trim().toLowerCase());
            JsonObject norm = com.wavesurvivor.altar.AltarRecipes.normalizeEntry(e.getValue());
            JsonObject cur = null;
            for (Map.Entry<String, JsonElement> b : base.altar.entrySet()) {
                if (b.getKey().equalsIgnoreCase(e.getKey())) cur = com.wavesurvivor.altar.AltarRecipes.normalizeEntry(b.getValue());
            }
            List<JsonObject> cands = candidates("altar", e.getKey(), cur);
            if (cands.isEmpty() || cur == null) { out.add(e.getKey(), e.getValue()); created++; continue; }
            JsonObject p = Patch.diff(closest(cands, norm), norm);
            if (p == null) { identical++; continue; }
            JsonObject wrap = new JsonObject();
            wrap.add("__patch", p);
            out.add(e.getKey(), wrap);
            patched++;
        }
        // Autel de base absent du fichier = supprimé par le joueur
        JsonArray del = new JsonArray();
        for (String n : alwaysNames("altar")) if (!present.contains(n)) del.add(n);
        if (del.size() > 0) out.add("__deleted", del);
        write(file, out);
        report.append("autels : ").append(identical).append(" identique(s) à la base, ").append(patched).append(" modifié(s), ")
                .append(created).append(" créé(s) par vous, ").append(del.size()).append(" supprimé(s) par vous.\n");
    }

    // ─── Coffres roulette, boutique Renaissance, raretés : entrées entières ───

    /** Vrai si l'élément du joueur est identique à une version livrée. */
    private boolean sameAsDelivered(String kind, String name, JsonElement mine, JsonElement current) {
        String c = Canon.canon(mine);
        if (current != null && Canon.canon(current).equals(c)) return true;
        if (legacy.has("entries") && legacy.getAsJsonObject("entries").has(kind)) {
            for (Map.Entry<String, JsonElement> e : legacy.getAsJsonObject("entries").getAsJsonObject(kind).entrySet()) {
                if (!e.getKey().equalsIgnoreCase(name.trim())) continue;
                for (JsonElement v : e.getValue().getAsJsonArray()) if (Canon.canon(v).equals(c)) return true;
            }
        }
        return false;
    }

    private void migrateChests(Path file) throws Exception {
        if (!Files.exists(file)) return;
        JsonObject root = readOrEmpty(file);
        JsonArray kept = new JsonArray();
        Set<String> present = new HashSet<>();
        int dropped = 0;
        for (JsonElement e : BuiltinBuilder.arr(root, "chests", false)) {
            if (!e.isJsonObject()) continue;
            String name = BuiltinBuilder.nameOf(e, "name");
            present.add(BuiltinBuilder.key(name));
            if (sameAsDelivered("chests", name, e, BuiltinBuilder.find(ConfigLayers.builtin().chests, "name", name))) { dropped++; continue; }
            kept.add(e);
        }
        JsonArray del = new JsonArray();
        for (String n : alwaysNames("chests")) if (!present.contains(n)) del.add(n);
        JsonObject out = new JsonObject();
        out.add("chests", kept);
        out.add("deleted", del);
        write(file, out);
        report.append("coffres : ").append(dropped).append(" identique(s) à la base, ").append(kept.size()).append(" conservé(s), ")
                .append(del.size()).append(" supprimé(s) par vous.\n");
    }

    private void migrateShop(Path file) throws Exception {
        if (!Files.exists(file)) return;
        JsonObject root = readOrEmpty(file);
        JsonArray kept = new JsonArray();
        int dropped = 0;
        for (JsonElement e : BuiltinBuilder.arr(root, "items", false)) {
            if (!e.isJsonObject()) continue;
            String id = BuiltinBuilder.nameOf(e, "id");
            if (sameAsDelivered("shop", id, e, BuiltinBuilder.find(ConfigLayers.builtin().shopItems, "id", id))) { dropped++; continue; }
            kept.add(e);
        }
        // Masquées : la base en masque déjà certaines ; on ne garde que la différence
        Set<String> baseDisabled = new HashSet<>();
        for (JsonElement e : ConfigLayers.builtin().shopDisabled) baseDisabled.add(e.getAsString());
        Set<String> userDisabled = new HashSet<>();
        for (JsonElement e : BuiltinBuilder.arr(root, "disabled", false)) userDisabled.add(e.getAsString());
        JsonArray disabled = new JsonArray(), enabled = new JsonArray();
        for (String d : userDisabled) if (!baseDisabled.contains(d)) disabled.add(d);
        for (String d : baseDisabled) if (!userDisabled.contains(d)) enabled.add(d);
        JsonObject out = new JsonObject();
        out.add("items", kept);
        out.add("disabled", disabled);
        out.add("enabled", enabled);
        write(file, out);
        report.append("boutique Renaissance : ").append(dropped).append(" identique(s) à la base, ").append(kept.size()).append(" conservé(s).\n");
    }

    private void migrateRarities(Path file) throws Exception {
        if (!Files.exists(file)) return;
        JsonObject root = readOrEmpty(file);
        JsonObject out = new JsonObject();
        int dropped = 0;
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            if (sameAsDelivered("rarities", e.getKey(), e.getValue(), ConfigLayers.builtin().rarities.get(e.getKey()))) { dropped++; continue; }
            out.add(e.getKey(), e.getValue());
        }
        // Une rareté de base que le joueur a retirée : marqueur « __reset__ »
        for (String k : ConfigLayers.builtin().rarities.keySet()) if (!root.has(k)) out.addProperty(k, "__reset__");
        write(file, out);
        report.append("raretés : ").append(dropped).append(" identique(s) à la base, ").append(out.size()).append(" conservée(s).\n");
    }

    // ─── Fichiers ───

    private static JsonObject readOrEmpty(Path f) {
        try {
            if (!Files.exists(f)) return new JsonObject();
            JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (Exception ex) {
            WaveSurvivorMod.LOGGER.warn("[Config] Lecture de {} impossible : {}", f.getFileName(), ex.getMessage());
            return new JsonObject();
        }
    }

    private static void write(Path f, JsonObject o) throws Exception {
        Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
    }
}
