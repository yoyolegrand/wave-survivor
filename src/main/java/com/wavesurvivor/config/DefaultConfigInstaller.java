package com.wavesurvivor.config;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * CONFIGURATION D'ORIGINE DU MOD (premier lancement).
 *
 * Le jar embarque la configuration complète du mod (hordes, entités, compétences, autels, coffres, Renaissance)
 * dans /defaults/wavesurvivor/. Au démarrage, chaque fichier ABSENT de config/wavesurvivor/ est copié depuis le jar.
 * Un fichier déjà présent n'est JAMAIS écrasé : un joueur qui met le mod à jour garde ses modifications.
 *
 * Pour revenir à la configuration d'origine : supprimer le(s) fichier(s) concerné(s) puis relancer le jeu.
 */
public final class DefaultConfigInstaller {

    private DefaultConfigInstaller() {}

    /** Fichiers de contenu livrés avec le mod (les fichiers propres à un monde — autels posés, positions — n'en font pas partie). */
    private static final String[] FILES = {
            "config.json",
            "custom_hordes.json",
            "custom_entities.json",
            "altar_recipes.json",
            "custom_roulette_chests.json",
            "renaissance_shop_custom.json",
            "renaissance_rarities.json"
    };

    private static final String RESOURCE_DIR = "/defaults/wavesurvivor/";

    /** Copie les fichiers manquants. À appeler avant tout chargement de configuration. */
    public static void installMissing() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor");
        int copied = 0;
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[WaveSurvivor] Impossible de créer {} : {}", dir, e.getMessage());
            return;
        }
        for (String f : FILES) {
            Path target = dir.resolve(f);
            if (Files.exists(target)) continue;
            try (InputStream in = DefaultConfigInstaller.class.getResourceAsStream(RESOURCE_DIR + f)) {
                if (in == null) {
                    WaveSurvivorMod.LOGGER.warn("[WaveSurvivor] Config d'origine absente du jar : {}", f);
                    continue;
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[WaveSurvivor] Copie de la config d'origine {} impossible : {}", f, e.getMessage());
            }
        }
        if (copied > 0) {
            WaveSurvivorMod.LOGGER.info("[WaveSurvivor] Configuration d'origine installée ({} fichier(s)) dans {}", copied, dir);
        }
        mergeAdditions(dir);
    }

    // ─── Contenu ajouté par les mises à jour ───

    /**
     * Contenu ajouté par une mise à jour (hordes, entités, compétences, recettes d'autel). Les fichiers de config
     * existants n'étant jamais écrasés, ce contenu y est FUSIONNÉ : ajouté s'il manque et n'a pas été supprimé par le
     * joueur. Chaque fichier d'ajouts n'est appliqué qu'UNE fois (noté dans additions_applied.txt) : un joueur qui
     * supprime ensuite la horde ne la voit pas revenir.
     */
    private static final String[] ADDITIONS = {
            "additions/kingdom_pillagers.json",  // 1.2.1 : « Le Siège des Pillards » (horde Kingdom prête à jouer)
            "additions/progression_1_4.json",    // 1.4.0 : conditions de déblocage (progression des hordes)
            "additions/relics_1_5.json",         // 1.5.0 : nouvelles reliques dans les Reliquaires
            "additions/frozen_peaks.json"        // 1.6.0 : « Les Pics Gelés » (horde Vanilla : monstres, élites, vagues spéciales, 3 boss)
    };

    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static void mergeAdditions(Path dir) {
        Path marker = dir.resolve("additions_applied.txt");
        java.util.Set<String> done = new java.util.LinkedHashSet<>();
        try {
            if (Files.exists(marker)) for (String l : Files.readAllLines(marker, java.nio.charset.StandardCharsets.UTF_8)) {
                if (!l.isBlank()) done.add(l.trim());
            }
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[WaveSurvivor] Lecture de {} impossible : {}", marker, e.getMessage());
        }
        boolean changed = false;
        for (String a : ADDITIONS) {
            if (done.contains(a)) continue;
            com.google.gson.JsonObject add;
            try (InputStream in = DefaultConfigInstaller.class.getResourceAsStream(RESOURCE_DIR + a)) {
                if (in == null) { WaveSurvivorMod.LOGGER.warn("[WaveSurvivor] Ajouts absents du jar : {}", a); continue; }
                add = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[WaveSurvivor] Lecture des ajouts {} impossible : {}", a, e.getMessage());
                continue;
            }
            try {
                int n = 0;
                n += mergeArray(dir.resolve("custom_hordes.json"), "hordes", "hordeName", "deleted", arr(add, "hordes"));
                n += mergeArray(dir.resolve("custom_entities.json"), "entities", "entityName", "deletedEntities", arr(add, "entities"));
                n += mergeArray(dir.resolve("custom_entities.json"), "skills", "skillName", "deletedSkills", arr(add, "skills"));
                n += mergeObject(dir.resolve("altar_recipes.json"), add.has("altarRecipes") ? add.getAsJsonObject("altarRecipes") : null);
                n += mergeUnlocks(dir.resolve("custom_hordes.json"), add.has("unlocks") ? add.getAsJsonObject("unlocks") : null);
                n += mergeChestRewards(dir.resolve("custom_roulette_chests.json"), add.has("chestRewards") ? add.getAsJsonObject("chestRewards") : null);
                done.add(a);
                changed = true;
                WaveSurvivorMod.LOGGER.info("[WaveSurvivor] Contenu de mise à jour fusionné ({}) : {} élément(s) ajouté(s).", a, n);
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[WaveSurvivor] Fusion des ajouts {} impossible : {}", a, e.getMessage());
            }
        }
        if (changed) {
            try {
                Files.write(marker, done, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.warn("[WaveSurvivor] Écriture de {} impossible : {}", marker, e.getMessage());
            }
        }
    }

    private static com.google.gson.JsonArray arr(com.google.gson.JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new com.google.gson.JsonArray();
    }

    private static com.google.gson.JsonObject readRoot(Path file) throws Exception {
        if (!Files.exists(file)) return new com.google.gson.JsonObject();
        com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(Files.readString(file, java.nio.charset.StandardCharsets.UTF_8));
        if (!el.isJsonObject()) throw new IllegalStateException(file.getFileName() + " n'est pas un objet JSON");
        return el.getAsJsonObject();
    }

    /** Ajoute chaque élément absent (par nom, sans casse) et non supprimé par le joueur. @return nombre d'ajouts. */
    private static int mergeArray(Path file, String arrayKey, String nameKey, String deletedKey, com.google.gson.JsonArray items) throws Exception {
        if (items.size() == 0) return 0;
        com.google.gson.JsonObject root = readRoot(file);
        com.google.gson.JsonArray list = arr(root, arrayKey);
        java.util.Set<String> have = new java.util.HashSet<>(), deleted = new java.util.HashSet<>();
        for (com.google.gson.JsonElement e : list) {
            if (e.isJsonObject() && e.getAsJsonObject().has(nameKey)) have.add(e.getAsJsonObject().get(nameKey).getAsString().trim().toLowerCase());
        }
        for (com.google.gson.JsonElement e : arr(root, deletedKey)) {
            if (e.isJsonPrimitive()) deleted.add(e.getAsString().trim().toLowerCase());
        }
        int n = 0;
        for (com.google.gson.JsonElement e : items) {
            if (!e.isJsonObject() || !e.getAsJsonObject().has(nameKey)) continue;
            String k = e.getAsJsonObject().get(nameKey).getAsString().trim().toLowerCase();
            if (have.contains(k) || deleted.contains(k)) continue;
            list.add(e.deepCopy());
            have.add(k);
            n++;
        }
        if (n > 0) {
            root.add(arrayKey, list);
            Files.writeString(file, GSON.toJson(root), java.nio.charset.StandardCharsets.UTF_8);
        }
        return n;
    }

    /**
     * Conditions de déblocage (progression) : posées sur les hordes nommées qui n'en ont AUCUNE — les réglages du
     * joueur et ses hordes perso ne sont jamais modifiés. @return nombre de hordes mises à jour.
     */
    private static int mergeUnlocks(Path file, com.google.gson.JsonObject unlocks) throws Exception {
        if (unlocks == null || unlocks.size() == 0 || !Files.exists(file)) return 0;
        com.google.gson.JsonObject root = readRoot(file);
        int n = 0;
        for (com.google.gson.JsonElement e : arr(root, "hordes")) {
            if (!e.isJsonObject() || !e.getAsJsonObject().has("hordeName")) continue;
            com.google.gson.JsonObject h = e.getAsJsonObject();
            String name = h.get("hordeName").getAsString().trim();
            com.google.gson.JsonElement reqs = null;
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> u : unlocks.entrySet()) {
                if (u.getKey().equalsIgnoreCase(name)) reqs = u.getValue();
            }
            if (reqs == null || !reqs.isJsonArray()) continue;
            if (!h.has("configData") || !h.get("configData").isJsonObject()) continue;
            com.google.gson.JsonObject cd = h.getAsJsonObject("configData");
            if (cd.has("unlockRequires") && cd.get("unlockRequires").isJsonArray() && cd.getAsJsonArray("unlockRequires").size() > 0) continue;
            cd.add("unlockRequires", reqs.deepCopy());
            n++;
        }
        if (n > 0) Files.writeString(file, GSON.toJson(root), java.nio.charset.StandardCharsets.UTF_8);
        return n;
    }

    /**
     * Lots ajoutés aux coffres roulette existants (par nom de coffre) : un lot n'est ajouté que si le coffre ne contient
     * pas déjà cet objet ; coffre absent ou supprimé = ignoré. @return nombre de lots ajoutés.
     */
    private static int mergeChestRewards(Path file, com.google.gson.JsonObject byChest) throws Exception {
        if (byChest == null || byChest.size() == 0 || !Files.exists(file)) return 0;
        com.google.gson.JsonObject root = readRoot(file);
        int n = 0;
        for (com.google.gson.JsonElement e : arr(root, "chests")) {
            if (!e.isJsonObject() || !e.getAsJsonObject().has("name")) continue;
            com.google.gson.JsonObject chest = e.getAsJsonObject();
            String name = chest.get("name").getAsString().trim();
            com.google.gson.JsonElement adds = null;
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> c : byChest.entrySet()) {
                if (c.getKey().equalsIgnoreCase(name)) adds = c.getValue();
            }
            if (adds == null || !adds.isJsonArray()) continue;
            com.google.gson.JsonArray rewards = arr(chest, "rewards");
            java.util.Set<String> have = new java.util.HashSet<>();
            for (com.google.gson.JsonElement r : rewards) {
                if (r.isJsonObject() && r.getAsJsonObject().has("item")) have.add(r.getAsJsonObject().get("item").getAsString().toLowerCase());
            }
            for (com.google.gson.JsonElement r : adds.getAsJsonArray()) {
                if (!r.isJsonObject() || !r.getAsJsonObject().has("item")) continue;
                String item = r.getAsJsonObject().get("item").getAsString().toLowerCase();
                if (!have.add(item)) continue;
                rewards.add(r.deepCopy());
                n++;
            }
            chest.add("rewards", rewards);
        }
        if (n > 0) Files.writeString(file, GSON.toJson(root), java.nio.charset.StandardCharsets.UTF_8);
        return n;
    }

    /** Recettes d'autel (objet indexé par nom de horde) : ajoute les entrées absentes. @return nombre d'ajouts. */
    private static int mergeObject(Path file, com.google.gson.JsonObject entries) throws Exception {
        if (entries == null || entries.size() == 0) return 0;
        com.google.gson.JsonObject root = readRoot(file);
        int n = 0;
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : entries.entrySet()) {
            boolean exists = false;
            for (String k : root.keySet()) if (k.equalsIgnoreCase(e.getKey())) { exists = true; break; }
            if (exists) continue;
            root.add(e.getKey(), e.getValue().deepCopy());
            n++;
        }
        if (n > 0) Files.writeString(file, GSON.toJson(root), java.nio.charset.StandardCharsets.UTF_8);
        return n;
    }
}
