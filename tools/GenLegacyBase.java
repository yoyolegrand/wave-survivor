import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wavesurvivor.altar.AltarRecipes;
import com.wavesurvivor.config.BuiltinBuilder;
import com.wavesurvivor.config.Canon;
import com.wavesurvivor.config.ConfigLayers;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OUTIL (hors jeu) : génère defaults/wavesurvivor/legacy_base.json, toutes les versions des éléments de base livrés
 * par les versions précédentes. La migration vers les couches de configuration (ConfigMigration) s'en sert pour reconnaître
 * un élément que le joueur n'a PAS modifié (il suivra alors le mod) et ne conserver que ses propres modifications.
 *
 * À relancer quand une version est publiée (ajouter son tag) : les joueurs qui migrent depuis cette version sont reconnus.
 *
 * Utilisation : voir docs/CONFIG_LAYERS.md
 *   java -cp <classes du mod + Forge + gson> GenLegacyBase <racine du dépôt> <fichier de sortie> <tag>... WORKTREE
 */
public class GenLegacyBase {

    private static final String DEFAULTS = "src/main/resources/defaults/wavesurvivor/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // kind → nom → versions livrées distinctes de l'élément (forme canonique → élément)
    private static final Map<String, Map<String, Map<String, JsonElement>>> ENTRIES = new LinkedHashMap<>();
    // kind → noms présents dans chaque version publiée (intersection)
    private static final Map<String, Set<String>> ALWAYS = new LinkedHashMap<>();

    public static void main(String[] a) throws Exception {
        String repo = a[0];
        Path out = Path.of(a[1]);
        boolean first = true;
        for (int i = 2; i < a.length; i++) {
            String v = a[i];
            boolean worktree = v.equals("WORKTREE");
            BuiltinBuilder.Source src = worktree ? rel -> readDisk(repo, rel) : rel -> readGit(repo, v, rel);
            List<String> additions = worktree ? ConfigLayers.ADDITIONS : additionsOf(repo, v);
            BuiltinBuilder.Result r = BuiltinBuilder.build(src, additions);
            Map<String, Set<String>> names = new LinkedHashMap<>();

            collect(names, "hordes", array(r.payload, "HordeConfigMulti"), "hordeName", false);
            collect(names, "entities", array(r.payload, "CustomEntity"), "entityName", false);
            collect(names, "skills", array(r.payload, "CustomSkill"), "skillName", false);

            JsonObject raw = BuiltinBuilder.parse(src.read("config.json"));
            if (raw != null) {
                JsonObject p = raw.has("donnees") && raw.get("donnees").isJsonObject() ? raw.getAsJsonObject("donnees") : raw;
                collect(names, "rawHordes", array(p, "HordeConfigMulti"), "hordeName", false);
                collect(names, "rawEntities", array(p, "CustomEntity"), "entityName", false);
                collect(names, "rawSkills", array(p, "CustomSkill"), "skillName", false);
            }
            for (Map.Entry<String, JsonElement> e : r.altar.entrySet()) {
                add("altar", e.getKey(), AltarRecipes.normalizeEntry(e.getValue()));
                names.computeIfAbsent("altar", k -> new LinkedHashSet<>()).add(e.getKey().trim().toLowerCase());
            }
            collect(names, "chests", r.chests, "name", false);
            collect(names, "shop", r.shopItems, "id", false);
            for (Map.Entry<String, JsonElement> e : r.rarities.entrySet()) {
                add("rarities", e.getKey(), e.getValue());
                names.computeIfAbsent("rarities", k -> new LinkedHashSet<>()).add(e.getKey().trim().toLowerCase());
            }

            if (!worktree) {
                for (Map.Entry<String, Set<String>> e : names.entrySet()) {
                    if (first) ALWAYS.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
                    else if (ALWAYS.containsKey(e.getKey())) ALWAYS.get(e.getKey()).retainAll(e.getValue());
                }
                for (String k : new ArrayList<>(ALWAYS.keySet())) if (!names.containsKey(k)) ALWAYS.remove(k);
                first = false;
            }
            System.out.println(v + " : " + count("hordes") + " hordes, " + count("entities") + " entités, " + count("skills") + " compétences (cumul)");
        }

        JsonObject root = new JsonObject();
        JsonArray vs = new JsonArray();
        for (int i = 2; i < a.length; i++) vs.add(a[i]);
        root.add("versions", vs);
        JsonObject entries = new JsonObject();
        for (Map.Entry<String, Map<String, Map<String, JsonElement>>> k : ENTRIES.entrySet()) {
            JsonObject o = new JsonObject();
            for (Map.Entry<String, Map<String, JsonElement>> n : k.getValue().entrySet()) {
                JsonArray vs2 = new JsonArray();
                for (JsonElement v2 : n.getValue().values()) vs2.add(v2);
                o.add(n.getKey(), vs2);
            }
            entries.add(k.getKey(), o);
        }
        root.add("entries", entries);
        JsonObject always = new JsonObject();
        for (Map.Entry<String, Set<String>> e : ALWAYS.entrySet()) {
            JsonArray arr = new JsonArray();
            for (String n : e.getValue()) arr.add(n);
            always.add(e.getKey(), arr);
        }
        root.add("always", always);
        Files.writeString(out, GSON.toJson(root), StandardCharsets.UTF_8);
        System.out.println("écrit : " + out);
    }

    private static int count(String kind) { return ENTRIES.getOrDefault(kind, Map.of()).size(); }

    private static void collect(Map<String, Set<String>> names, String kind, JsonArray list, String nameKey, boolean unused) {
        for (JsonElement e : list) {
            String n = BuiltinBuilder.nameOf(e, nameKey);
            if (n.isBlank()) continue;
            add(kind, n, e);
            names.computeIfAbsent(kind, k -> new LinkedHashSet<>()).add(n.trim().toLowerCase());
        }
    }

    private static void add(String kind, String name, JsonElement value) {
        // les versions brutes du config.json rejoignent les candidats de leur famille
        String fam = kind.startsWith("raw") ? Character.toLowerCase(kind.charAt(3)) + kind.substring(4) : kind;
        ENTRIES.computeIfAbsent(fam, k -> new LinkedHashMap<>())
                .computeIfAbsent(name.trim().toLowerCase(), k -> new LinkedHashMap<>())
                .putIfAbsent(Canon.canon(value), value.deepCopy());
    }

    private static JsonArray array(JsonObject o, String k) { return BuiltinBuilder.arr(o, k, false); }

    private static String readDisk(String repo, String rel) {
        try {
            Path p = Path.of(repo, DEFAULTS + rel);
            return Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
        } catch (Exception e) { return null; }
    }

    private static String readGit(String repo, String tag, String rel) {
        byte[] b = git(repo, "show", tag + ":" + DEFAULTS + rel);
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    private static List<String> additionsOf(String repo, String tag) {
        byte[] b = git(repo, "ls-tree", "--name-only", tag, DEFAULTS + "additions/");
        List<String> out = new ArrayList<>();
        if (b == null) return out;
        for (String l : new String(b, StandardCharsets.UTF_8).split("\n")) {
            l = l.trim();
            if (l.endsWith(".json")) out.add("additions/" + l.substring(l.lastIndexOf('/') + 1));
        }
        // même ordre que le jar actuel quand c'est possible
        out.sort((x, y) -> {
            int ix = ConfigLayers.ADDITIONS.indexOf(x), iy = ConfigLayers.ADDITIONS.indexOf(y);
            return Integer.compare(ix < 0 ? 999 : ix, iy < 0 ? 999 : iy);
        });
        return out;
    }

    private static byte[] git(String repo, String... args) {
        try {
            List<String> cmd = new ArrayList<>(List.of("git", "-C", repo));
            cmd.addAll(List.of(args));
            Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            p.getInputStream().transferTo(bo);
            return p.waitFor() == 0 ? bo.toByteArray() : null;
        } catch (Exception e) { return null; }
    }
}
