package com.wavesurvivor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.altar.AltarRecipes;
import com.wavesurvivor.horde.roulette.CustomChestData;
import com.wavesurvivor.horde.roulette.CustomChestStore;
import com.wavesurvivor.horde.roulette.RouletteChestSpawnStore;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * IMPORT / EXPORT DE HORDES (classiques et Kingdom).
 *  - EXPORT : un seul fichier .zip dans config/wavesurvivor/export/, léger (compressé) et lisible (JSON) :
 *      manifest.json (nom, type, description, vagues, boss, mods requis, auteur, version, date),
 *      horde.json (config complète), entities.json (entités et compétences custom utilisées),
 *      chests.json (coffres roulette custom utilisés), altar.json (config d'autel de la horde).
 *    Les dépendances sont trouvées en suivant tous les textes de la horde (noms d'entités, de compétences, de coffres).
 *  - IMPORT : les .zip déposés dans config/wavesurvivor/import/ ; /ws import ouvre la liste. Si une horde, une entité,
 *    une compétence ou un coffre du même nom existe déjà : « Remplacer » ou « Garder les deux » (le nouveau reçoit
 *    « (2) », « (3) »… et toutes ses références sont mises à jour). Un élément identique à l'existant est simplement
 *    réutilisé. Rechargement automatique de la config à la fin.
 */
public final class HordeExchange {

    private HordeExchange() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern RES_ID = Pattern.compile("^([a-z0-9_.-]+):[a-z0-9_./-]+$");
    /** Espaces de noms qui ne demandent aucun mod en plus. */
    private static final Set<String> BUILTIN = Set.of("minecraft", "wavesurvivor", "forge", "custom");

    public static final int MODE_NEW = 0, MODE_REPLACE = 1, MODE_KEEP_BOTH = 2;

    public static Path exportDir() { return dir("export"); }

    public static Path importDir() { return dir("import"); }

    private static Path dir(String sub) {
        Path d = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve(sub);
        try { Files.createDirectories(d); } catch (Exception ignored) {}
        return d;
    }

    // ═══ EXPORT ═══

    /** Exporte la horde ENREGISTRÉE {@code name}. @return chemin du fichier créé, ou null en cas d'échec. */
    public static Path export(ServerPlayer p, String name) {
        JsonObject horde = CustomHordeStore.get(name);
        if (horde == null) {
            p.sendSystemMessage(WSLang.c("exchange.export.not_found", name));
            return null;
        }
        // Dépendances : on suit les textes de la horde, puis ceux des entités trouvées (sbires, montures, compétences…)
        Set<String> strings = new LinkedHashSet<>();
        collectStrings(horde, strings);
        Map<String, JsonObject> allEntities = CustomEntityStore.all(CustomEntityStore.Kind.ENTITY);
        Map<String, JsonObject> allSkills = CustomEntityStore.all(CustomEntityStore.Kind.SKILL);
        Map<String, JsonObject> entities = new java.util.LinkedHashMap<>();
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Map.Entry<String, JsonObject> e : allEntities.entrySet()) {
                if (entities.containsKey(e.getKey()) || !strings.contains(e.getKey())) continue;
                entities.put(e.getKey(), e.getValue());
                collectStrings(e.getValue(), strings);
                grew = true;
            }
        }
        JsonArray entArr = new JsonArray(), skillArr = new JsonArray();
        for (JsonObject o : entities.values()) entArr.add(o);
        for (Map.Entry<String, JsonObject> s : allSkills.entrySet()) if (strings.contains(s.getKey())) skillArr.add(s.getValue());
        // Coffres roulette : référencés dans les textes ou posés pour cette horde
        Set<String> chestNames = new LinkedHashSet<>(strings);
        for (RouletteChestSpawnStore.SpawnPoint sp : RouletteChestSpawnStore.getSpawns(name)) chestNames.add(sp.configKey);
        JsonArray chestArr = new JsonArray();
        for (CustomChestData c : CustomChestStore.all()) if (c.name != null && chestNames.contains(c.name)) chestArr.add(GSON.toJsonTree(c));
        String altar = AltarRecipes.toJson(name);

        // Manifeste
        JsonObject entitiesJson = new JsonObject();
        entitiesJson.add("entities", entArr);
        entitiesJson.add("skills", skillArr);
        Set<String> all = new LinkedHashSet<>(strings);
        collectStrings(entitiesJson, all);
        collectStrings(chestArr, all);
        JsonObject cd = horde.has("configData") && horde.get("configData").isJsonObject() ? horde.getAsJsonObject("configData") : new JsonObject();
        JsonObject man = new JsonObject();
        man.addProperty("format", 1);
        man.addProperty("name", str(horde, "hordeName"));
        man.addProperty("type", "kingdom".equalsIgnoreCase(str(cd, "mode")) ? "kingdom" : "classic");
        man.addProperty("description", str(horde, "hordeDescription"));
        man.addProperty("waves", cd.has("totalWaves") ? cd.get("totalWaves").getAsInt() : 0);
        man.addProperty("bosses", cd.has("bossWaves") && cd.get("bossWaves").isJsonArray() ? cd.getAsJsonArray("bossWaves").size() : 0);
        man.addProperty("entities", entArr.size());
        man.addProperty("skills", skillArr.size());
        man.addProperty("chests", chestArr.size());
        JsonArray mods = new JsonArray();
        for (String m : requiredMods(all)) mods.add(m);
        man.add("mods", mods);
        man.addProperty("author", p.getGameProfile().getName());
        man.addProperty("modVersion", ModList.get().getModContainerById(WaveSurvivorMod.MODID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?"));
        man.addProperty("date", LocalDate.now().toString());

        Path out = exportDir().resolve(safe(str(horde, "hordeName")) + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out))) {
            put(zip, "manifest.json", GSON.toJson(man));
            put(zip, "horde.json", GSON.toJson(horde));
            put(zip, "entities.json", GSON.toJson(entitiesJson));
            put(zip, "chests.json", GSON.toJson(chestArr));
            if (altar != null && !altar.isBlank()) put(zip, "altar.json", altar);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Exchange] Export de '{}' échoué : {}", name, e.getMessage());
            p.sendSystemMessage(WSLang.c("exchange.export.failed", e.getMessage()));
            return null;
        }
        p.sendSystemMessage(WSLang.c("exchange.export.done", str(horde, "hordeName"), entArr.size(), skillArr.size(), chestArr.size()));
        p.sendSystemMessage(Component.literal("§7  " + out.toAbsolutePath()));
        return out;
    }

    private static void put(ZipOutputStream zip, String entry, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(entry));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // ═══ LISTE DES IMPORTS ═══

    /** Fiche d'un fichier du dossier import (lue dans son manifeste). */
    public record Entry(String file, String name, String type, String description, int waves, int bosses,
                        int entities, int skills, int chests, List<String> missingMods, boolean conflict, String author) {}

    public static List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(importDir())) {
            for (Path f : files.filter(x -> x.toString().toLowerCase().endsWith(".zip")).sorted().toList()) {
                try (ZipFile z = new ZipFile(f.toFile())) {
                    JsonObject man = readJson(z, "manifest.json");
                    JsonObject horde = readJson(z, "horde.json");
                    if (horde == null) continue;
                    if (man == null) man = new JsonObject();
                    String name = str(horde, "hordeName");
                    List<String> missing = new ArrayList<>();
                    if (man.has("mods")) for (JsonElement m : man.getAsJsonArray("mods")) {
                        if (!ModList.get().isLoaded(m.getAsString())) missing.add(m.getAsString());
                    }
                    out.add(new Entry(f.getFileName().toString(), name, str(man, "type").isEmpty() ? "classic" : str(man, "type"),
                            str(horde, "hordeDescription"), num(man, "waves"), num(man, "bosses"), num(man, "entities"),
                            num(man, "skills"), num(man, "chests"), missing, CustomHordeStore.exists(name), str(man, "author")));
                } catch (Exception e) {
                    WaveSurvivorMod.LOGGER.warn("[Exchange] Fichier d'import illisible {} : {}", f.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Exchange] Lecture du dossier import échouée : {}", e.getMessage());
        }
        return out;
    }

    // ═══ IMPORT ═══

    /** Importe le fichier {@code file} du dossier import. @return nom final de la horde, ou null en cas d'échec. */
    public static String doImport(ServerPlayer p, String file, int mode) {
        Path f = importDir().resolve(file).normalize();
        if (!f.startsWith(importDir()) || !Files.exists(f)) {
            p.sendSystemMessage(WSLang.c("exchange.import.not_found", file));
            return null;
        }
        try (ZipFile z = new ZipFile(f.toFile())) {
            JsonObject horde = readJson(z, "horde.json");
            if (horde == null) throw new IllegalStateException("horde.json manquant");
            JsonObject ents = readJson(z, "entities.json");
            JsonArray chests = readArray(z, "chests.json");
            String altar = readText(z, "altar.json");
            List<JsonObject> entities = objects(ents, "entities"), skills = objects(ents, "skills");
            List<JsonObject> chestList = new ArrayList<>();
            if (chests != null) for (JsonElement e : chests) if (e.isJsonObject()) chestList.add(e.getAsJsonObject());

            // 1) Conflits d'entités / compétences / coffres → renommages éventuels
            Map<String, String> renames = new HashMap<>();
            Set<String> skipEnt = new LinkedHashSet<>(), skipSkill = new LinkedHashSet<>(), skipChest = new LinkedHashSet<>();
            Map<String, JsonObject> curEnt = CustomEntityStore.all(CustomEntityStore.Kind.ENTITY);
            Map<String, JsonObject> curSkill = CustomEntityStore.all(CustomEntityStore.Kind.SKILL);
            Map<String, JsonObject> curChest = new HashMap<>();
            for (CustomChestData c : CustomChestStore.all()) if (c.name != null) curChest.put(c.name, GSON.toJsonTree(c).getAsJsonObject());
            plan(entities, "entityName", curEnt, mode, renames, skipEnt);
            plan(skills, "skillName", curSkill, mode, renames, skipSkill);
            plan(chestList, "name", curChest, mode, renames, skipChest);

            // 2) Nom de la horde
            String name = str(horde, "hordeName");
            String target = name;
            if (CustomHordeStore.exists(name) && mode != MODE_REPLACE) target = freeName(name, CustomHordeStore::exists);
            horde.addProperty("hordeName", target);

            // 3) Références mises à jour, puis enregistrement
            for (JsonObject o : entities) rename(o, renames);
            for (JsonObject o : skills) rename(o, renames);
            for (JsonObject o : chestList) rename(o, renames);
            rename(horde, renames);
            horde.addProperty("hordeName", target);
            int nEnt = 0, nSkill = 0, nChest = 0;
            for (JsonObject o : skills) if (!skipSkill.contains(str(o, "skillName"))) { CustomEntityStore.put(CustomEntityStore.Kind.SKILL, o, null); nSkill++; }
            for (JsonObject o : entities) if (!skipEnt.contains(str(o, "entityName"))) { CustomEntityStore.put(CustomEntityStore.Kind.ENTITY, o, null); nEnt++; }
            for (JsonObject o : chestList) if (!skipChest.contains(str(o, "name"))) {
                CustomChestData d = GSON.fromJson(o, CustomChestData.class);
                if (d != null) { CustomChestStore.addOrReplace(d); nChest++; }
            }
            CustomHordeStore.put(horde, null);
            if (altar != null && !altar.isBlank()) {
                JsonElement ae = JsonParser.parseString(altar);
                rename(ae, renames);
                AltarRecipes.putFromEditor(target, null, GSON.toJson(ae));
            }

            // 4) Rechargement automatique
            String summary = WaveSurvivorMod.reloadConfig();
            com.wavesurvivor.network.PlayerLoginHandler.broadcastToAll(p.getServer());
            p.sendSystemMessage(WSLang.c("exchange.import.done", target, nEnt, nSkill, nChest));
            if (!renames.isEmpty()) p.sendSystemMessage(WSLang.c("exchange.import.renamed", String.join(", ", renames.values())));
            WaveSurvivorMod.LOGGER.info("[Exchange] Import '{}' → '{}' ({}) — {}", file, target, mode, summary);
            return target;
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Exchange] Import de '{}' échoué : {}", file, e.getMessage(), e);
            p.sendSystemMessage(WSLang.c("exchange.import.failed", e.getMessage()));
            return null;
        }
    }

    /**
     * Conflits d'une liste d'objets importés : identique à l'existant → réutilisé (non réécrit) ;
     * différent → remplacé (MODE_REPLACE) ou renommé « Nom (2) » (sinon).
     */
    private static void plan(List<JsonObject> list, String key, Map<String, JsonObject> current, int mode,
                             Map<String, String> renames, Set<String> skip) {
        Set<String> taken = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        taken.addAll(current.keySet());
        for (JsonObject o : list) {
            String n = str(o, key);
            if (n.isEmpty()) continue;
            JsonObject existing = null;
            for (Map.Entry<String, JsonObject> c : current.entrySet()) if (c.getKey().equalsIgnoreCase(n)) existing = c.getValue();
            if (existing == null) { taken.add(n); continue; }
            if (existing.equals(o)) { skip.add(n); continue; }
            if (mode == MODE_REPLACE) continue;
            String nn = freeName(n, taken::contains);
            taken.add(nn);
            renames.put(n, nn);
            o.addProperty(key, nn);
        }
    }

    /** « Nom (2) », « Nom (3) »… premier nom libre. */
    private static String freeName(String base, java.util.function.Predicate<String> exists) {
        for (int i = 2; i < 1000; i++) {
            String n = base + " (" + i + ")";
            if (!exists.test(n)) return n;
        }
        return base + " (" + System.currentTimeMillis() + ")";
    }

    /** Remplace partout les textes exactement égaux à un ancien nom par le nouveau. */
    private static void rename(JsonElement e, Map<String, String> renames) {
        if (renames.isEmpty() || e == null) return;
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            for (String k : new ArrayList<>(o.keySet())) {
                JsonElement v = o.get(k);
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() && renames.containsKey(v.getAsString())) {
                    o.add(k, new JsonPrimitive(renames.get(v.getAsString())));
                } else {
                    rename(v, renames);
                }
            }
        } else if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) {
                JsonElement v = a.get(i);
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() && renames.containsKey(v.getAsString())) {
                    a.set(i, new JsonPrimitive(renames.get(v.getAsString())));
                } else {
                    rename(v, renames);
                }
            }
        }
    }

    // ═══ Outils ═══

    private static void collectStrings(JsonElement e, Set<String> out) {
        if (e == null || e.isJsonNull()) return;
        if (e.isJsonPrimitive()) {
            if (e.getAsJsonPrimitive().isString()) out.add(e.getAsString());
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) collectStrings(x, out);
        } else if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) collectStrings(x.getValue(), out);
        }
    }

    /** Mods nécessaires : espaces de noms des identifiants « mod:objet » rencontrés (hors Minecraft / Wave Survivor). */
    private static List<String> requiredMods(Set<String> strings) {
        Set<String> mods = new TreeSet<>();
        for (String s : strings) {
            var m = RES_ID.matcher(s.trim());
            if (m.matches() && !BUILTIN.contains(m.group(1))) mods.add(m.group(1));
        }
        return new ArrayList<>(mods);
    }

    private static List<JsonObject> objects(JsonObject o, String key) {
        List<JsonObject> out = new ArrayList<>();
        if (o != null && o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) if (e.isJsonObject()) out.add(e.getAsJsonObject());
        }
        return out;
    }

    private static String readText(ZipFile z, String entry) throws Exception {
        ZipEntry e = z.getEntry(entry);
        if (e == null) return null;
        try (InputStream in = z.getInputStream(e)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            in.transferTo(bos);
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    private static JsonObject readJson(ZipFile z, String entry) throws Exception {
        String t = readText(z, entry);
        if (t == null || t.isBlank()) return null;
        JsonElement e = JsonParser.parseString(t);
        return e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray readArray(ZipFile z, String entry) throws Exception {
        String t = readText(z, entry);
        if (t == null || t.isBlank()) return null;
        JsonElement e = JsonParser.parseString(t);
        return e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static int num(JsonObject o, String k) {
        try { return o != null && o.has(k) ? o.get(k).getAsInt() : 0; } catch (Exception e) { return 0; }
    }

    /** Nom de fichier sûr (Discord, Windows) : lettres, chiffres, tirets et soulignés. */
    private static String safe(String name) {
        String s = java.text.Normalizer.normalize(name == null ? "horde" : name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "horde" : s;
    }
}
