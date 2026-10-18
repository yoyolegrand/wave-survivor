package com.wavesurvivor.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OUTIL (hors jeu) : compare la config EFFECTIVE d'avant (ancien système : copie de la base + modifications du joueur) et
 * d'après (couches : base du jar + couche migrée du joueur), pour un dossier copié puis migré (voir MigrationCheck).
 * Les différences attendues sont celles que la mise à jour apporte à la base (champs que le joueur n'avait pas touchés).
 *   java -cp ... com.wavesurvivor.config.LayerCheck <dossier migré> <dossier de sauvegarde avant migration>
 */
public class LayerCheck {

    public static void main(String[] a) throws Exception {
        Path migrated = Path.of(a[0]), backup = Path.of(a[1]);
        JsonObject oldHordes = read(backup.resolve("custom_hordes.json")), oldEnts = read(backup.resolve("custom_entities.json"));
        JsonObject cfg = read(backup.resolve("config.json"));
        JsonObject oldPayload = cfg.has("donnees") ? cfg.getAsJsonObject("donnees") : cfg;
        // Ancien système : config.json recouvert par les fichiers du joueur
        BuiltinBuilder.overlay(BuiltinBuilder.arr(oldPayload, "HordeConfigMulti", true), BuiltinBuilder.arr(oldHordes, "hordes", false), BuiltinBuilder.arr(oldHordes, "deleted", false), "hordeName");
        BuiltinBuilder.overlay(BuiltinBuilder.arr(oldPayload, "CustomEntity", true), BuiltinBuilder.arr(oldEnts, "entities", false), BuiltinBuilder.arr(oldEnts, "deletedEntities", false), "entityName");
        BuiltinBuilder.overlay(BuiltinBuilder.arr(oldPayload, "CustomSkill", true), BuiltinBuilder.arr(oldEnts, "skills", false), BuiltinBuilder.arr(oldEnts, "deletedSkills", false), "skillName");

        // Nouveau système : base du jar + couche migrée
        JsonObject newPayload = ConfigLayers.payloadCopy();
        JsonObject nh = read(migrated.resolve("custom_hordes.json")), ne = read(migrated.resolve("custom_entities.json"));
        layer(BuiltinBuilder.arr(newPayload, "HordeConfigMulti", true), nh, "hordes", "patches", "deleted", "hordeName");
        layer(BuiltinBuilder.arr(newPayload, "CustomEntity", true), ne, "entities", "entityPatches", "deletedEntities", "entityName");
        layer(BuiltinBuilder.arr(newPayload, "CustomSkill", true), ne, "skills", "skillPatches", "deletedSkills", "skillName");

        compare("hordes", BuiltinBuilder.arr(oldPayload, "HordeConfigMulti", false), BuiltinBuilder.arr(newPayload, "HordeConfigMulti", false), "hordeName");
        compare("entités", BuiltinBuilder.arr(oldPayload, "CustomEntity", false), BuiltinBuilder.arr(newPayload, "CustomEntity", false), "entityName");
        compare("compétences", BuiltinBuilder.arr(oldPayload, "CustomSkill", false), BuiltinBuilder.arr(newPayload, "CustomSkill", false), "skillName");
    }

    /** Même logique que CustomHordeStore / CustomEntityStore.mergeInto. */
    private static void layer(JsonArray base, JsonObject file, String fullKey, String patchKey, String delKey, String nameKey) {
        Map<String, JsonObject> full = new LinkedHashMap<>(), patches = new LinkedHashMap<>();
        for (JsonElement e : BuiltinBuilder.arr(file, fullKey, false)) full.put(BuiltinBuilder.key(BuiltinBuilder.nameOf(e, nameKey)), e.getAsJsonObject());
        if (file.has(patchKey)) for (String k : file.getAsJsonObject(patchKey).keySet()) patches.put(BuiltinBuilder.key(k), file.getAsJsonObject(patchKey).getAsJsonObject(k));
        java.util.Set<String> del = new java.util.HashSet<>();
        for (JsonElement e : BuiltinBuilder.arr(file, delKey, false)) del.add(BuiltinBuilder.key(e.getAsString()));
        java.util.List<JsonElement> out = new java.util.ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (JsonElement e : base) {
            String k = BuiltinBuilder.key(BuiltinBuilder.nameOf(e, nameKey));
            if (del.contains(k) && !full.containsKey(k)) continue;
            if (full.containsKey(k)) out.add(full.get(k));
            else if (patches.containsKey(k)) out.add(Patch.apply(e.getAsJsonObject(), patches.get(k)));
            else out.add(e);
            used.add(k);
        }
        for (Map.Entry<String, JsonObject> c : full.entrySet()) if (!used.contains(c.getKey())) out.add(c.getValue());
        while (base.size() > 0) base.remove(0);
        for (JsonElement e : out) base.add(e);
    }

    private static void compare(String label, JsonArray oldList, JsonArray newList, String nameKey) {
        int same = 0, diff = 0, onlyOld = 0, onlyNew = 0;
        for (JsonElement o : oldList) {
            String n = BuiltinBuilder.nameOf(o, nameKey);
            JsonObject nw = BuiltinBuilder.find(newList, nameKey, n);
            if (nw == null) { onlyOld++; System.out.println("  [" + label + "] absent de la nouvelle config : " + n); continue; }
            JsonObject p = Patch.diff(o.getAsJsonObject(), nw);
            if (p == null) { same++; continue; }
            diff++;
            String s = p.toString();
            System.out.println("  [" + label + "] " + n + " : " + Patch.count(p) + " champ(s) différent(s) (mise à jour de la base) " + (s.length() > 160 ? s.substring(0, 160) + "…" : s));
        }
        for (JsonElement e : newList) if (BuiltinBuilder.find(oldList, nameKey, BuiltinBuilder.nameOf(e, nameKey)) == null) onlyNew++;
        System.out.println(label + " : " + same + " identiques, " + diff + " mis à jour par la base, " + onlyOld + " perdus, " + onlyNew + " nouveaux (contenu livré depuis).");
    }

    private static JsonObject read(Path p) throws Exception {
        if (!Files.exists(p)) return new JsonObject();
        JsonObject o = BuiltinBuilder.parse(Files.readString(p));
        return o == null ? new JsonObject() : o;
    }
}
