package com.wavesurvivor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * FORMAT DU FICHIER D'ÉCHANGE DE HORDE (.zip) — écriture, lecture sécurisée, garde-fous.
 * Classe « pure » (Gson + java.util.zip, aucune classe Minecraft) : testée en JUnit (src/test).
 *
 * Contenu : manifest.json et horde.json (obligatoires), entities.json, chests.json, altar.json (facultatifs), à la racine.
 * Lecture : liste blanche vérifiée AVANT toute lecture (5 entrées max, noms exacts, pas de dossier), .zip ≤ 5 Mo,
 * chaque entrée ≤ 2 Mo décompressés (mesurés pendant la lecture, la taille annoncée par le zip n'est pas crue),
 * JSON strict, manifeste cohérent avec horde.json (type et nom). Le moindre problème refuse tout le fichier.
 *
 * Format 1 (≤ 2.0) : type du manifeste limité à classic/kingdom → le type est déduit de horde.json.
 * Format 2 (2.1)   : type réel (classic / kingdom / invasion / rift), Pehkui listé pour la Faille.
 */
public final class HordeArchive {

    private HordeArchive() {}

    public static final int FORMAT = 2;
    public static final long MAX_ZIP_BYTES = 5L * 1024 * 1024;
    public static final long MAX_ENTRY_BYTES = 2L * 1024 * 1024;
    public static final String MANIFEST = "manifest.json", HORDE = "horde.json", ENTITIES = "entities.json",
            CHESTS = "chests.json", ALTAR = "altar.json";
    /** Seules entrées acceptées, dans l'ordre d'écriture. */
    public static final List<String> ALLOWED = List.of(MANIFEST, HORDE, ENTITIES, CHESTS, ALTAR);
    public static final List<String> REQUIRED = List.of(MANIFEST, HORDE);

    public static final String CLASSIC = "classic", KINGDOM = "kingdom", INVASION = "invasion", RIFT = "rift";
    public static final List<String> TYPES = List.of(CLASSIC, KINGDOM, INVASION, RIFT);

    /**
     * Seuils des garde-fous (avertissement jaune à l'import, jamais bloquant) : environ 3× le maximum des hordes livrées
     * avec le mod (relevé en 2.1 sur defaults/wavesurvivor/custom_hordes.json + additions/*.json) :
     * 10 vagues, 175 monstres à la dernière vague, 1000 PV et 24 dégâts (boss / entités custom), Titan 3000 PV et taille ×5.
     */
    public static final class Limits {
        private Limits() {}
        public static final int WAVES = 30;
        public static final int MONSTERS_PER_WAVE = 525;
        public static final double ENTITY_HP = 3000;
        public static final double ENTITY_DAMAGE = 72;
        public static final double TITAN_HP = 9000;
        public static final double TITAN_SCALE = 15;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final TypeAdapter<JsonElement> JSON = new Gson().getAdapter(JsonElement.class);
    private static final Pattern RES_ID = Pattern.compile("^([a-z0-9_.-]+):[a-z0-9_./-]+$");
    private static final Pattern MOD_ID = Pattern.compile("^[a-z0-9_.-]{1,64}$");
    /** Espaces de noms qui ne demandent aucun mod en plus. */
    private static final Set<String> BUILTIN = Set.of("minecraft", "wavesurvivor", "forge", "custom");
    /** La Faille redimensionne le Titan et l'île avec Pehkui. */
    public static final String PEHKUI = "pehkui";

    /** Message à traduire côté client ou serveur (clé WSLang + arguments). */
    public record Msg(String key, List<String> args) {
        public static Msg of(String key, Object... args) {
            List<String> a = new ArrayList<>();
            for (Object o : args) a.add(String.valueOf(o));
            return new Msg(key, a);
        }
    }

    /** Fichier refusé : la raison est une clé WSLang (exchange.reject.*). */
    public static final class Rejected extends Exception {
        public final Msg msg;
        Rejected(String key, Object... args) {
            super(key);
            this.msg = Msg.of(key, args);
        }
    }

    /** Contenu validé d'un fichier d'échange. {@code type} = type réel (lu dans horde.json). */
    public record Contents(int format, String type, JsonObject manifest, JsonObject horde, JsonObject entities,
                           JsonArray chests, String altar, List<String> mods) {}

    // ═══ Type et dépendances ═══

    /** Type réel d'une horde, d'après configData.mode (absent = classique). */
    public static String modeOf(JsonObject horde) {
        JsonObject cd = obj(horde, "configData");
        String m = str(cd, "mode").toLowerCase(Locale.ROOT);
        return TYPES.contains(m) ? m : CLASSIC;
    }

    /** Mods requis : espaces de noms « mod:objet » rencontrés (hors Minecraft / Wave Survivor), + Pehkui pour la Faille. */
    public static List<String> requiredMods(Set<String> strings, String type) {
        Set<String> mods = new TreeSet<>();
        for (String s : strings) {
            var m = RES_ID.matcher(s.trim());
            if (m.matches() && !BUILTIN.contains(m.group(1))) mods.add(m.group(1));
        }
        if (RIFT.equals(type)) mods.add(PEHKUI);
        return new ArrayList<>(mods);
    }

    // ═══ Écriture ═══

    /** Manifeste (format 2). */
    public static JsonObject manifest(JsonObject horde, int entities, int skills, int chests, List<String> mods,
                                      String author, String modVersion, String date) {
        JsonObject cd = obj(horde, "configData");
        JsonObject man = new JsonObject();
        man.addProperty("format", FORMAT);
        man.addProperty("name", str(horde, "hordeName"));
        man.addProperty("type", modeOf(horde));
        man.addProperty("description", str(horde, "hordeDescription"));
        man.addProperty("waves", num(cd, "totalWaves"));
        man.addProperty("bosses", cd.has("bossWaves") && cd.get("bossWaves").isJsonArray() ? cd.getAsJsonArray("bossWaves").size() : 0);
        man.addProperty("entities", entities);
        man.addProperty("skills", skills);
        man.addProperty("chests", chests);
        JsonArray m = new JsonArray();
        for (String s : mods) m.add(s);
        man.add("mods", m);
        man.addProperty("author", author == null ? "" : author);
        man.addProperty("modVersion", modVersion == null ? "?" : modVersion);
        man.addProperty("date", date == null ? "" : date);
        return man;
    }

    /** Écrit le .zip (altar facultatif). */
    public static void write(OutputStream out, JsonObject manifest, JsonObject horde, JsonObject entities,
                             JsonArray chests, String altar) throws java.io.IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, MANIFEST, GSON.toJson(manifest));
            put(zip, HORDE, GSON.toJson(horde));
            put(zip, ENTITIES, GSON.toJson(entities == null ? new JsonObject() : entities));
            put(zip, CHESTS, GSON.toJson(chests == null ? new JsonArray() : chests));
            if (altar != null && !altar.isBlank()) put(zip, ALTAR, altar);
        }
    }

    private static void put(ZipOutputStream zip, String entry, String content) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(entry));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // ═══ Lecture sécurisée ═══

    /** Lit et valide un fichier d'échange. Aucun effet de bord : rien n'est enregistré ici. */
    public static Contents read(Path file) throws Rejected {
        long size;
        try { size = Files.size(file); } catch (Exception e) { throw new Rejected("exchange.reject.unreadable"); }
        if (size > MAX_ZIP_BYTES) throw new Rejected("exchange.reject.zip_too_large", mb(size), mb(MAX_ZIP_BYTES));

        Map<String, String> texts = new HashMap<>();
        try (ZipFile z = new ZipFile(file.toFile())) {
            // 1) liste blanche, avant toute lecture
            List<ZipEntry> entries = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                entries.add(e);
                if (entries.size() > ALLOWED.size()) throw new Rejected("exchange.reject.too_many", ALLOWED.size());
            }
            Set<String> seen = new TreeSet<>();
            for (ZipEntry e : entries) {
                String n = e.getName();
                if (e.isDirectory() || n.contains("/") || n.contains("\\") || n.contains("..") || !ALLOWED.contains(n))
                    throw new Rejected("exchange.reject.unexpected", printable(n));
                if (!seen.add(n)) throw new Rejected("exchange.reject.duplicate", n);
            }
            for (String r : REQUIRED) if (!seen.contains(r)) throw new Rejected("exchange.reject.missing", r);
            // 2) lecture bornée
            for (ZipEntry e : entries) {
                try (InputStream in = z.getInputStream(e)) {
                    texts.put(e.getName(), readBounded(in, e.getName()));
                }
            }
        } catch (Rejected r) {
            throw r;
        } catch (Exception e) {
            throw new Rejected("exchange.reject.not_zip");
        }

        // 3) JSON strict et types attendus
        JsonObject man = asObject(parse(texts.get(MANIFEST), MANIFEST), MANIFEST);
        JsonObject horde = asObject(parse(texts.get(HORDE), HORDE), HORDE);
        JsonObject ents = texts.containsKey(ENTITIES) ? asObject(parse(texts.get(ENTITIES), ENTITIES), ENTITIES) : new JsonObject();
        JsonArray chests = new JsonArray();
        if (texts.containsKey(CHESTS)) {
            JsonElement c = parse(texts.get(CHESTS), CHESTS);
            if (!c.isJsonArray()) throw new Rejected("exchange.reject.bad_json", CHESTS);
            chests = c.getAsJsonArray();
        }
        String altar = null;
        if (texts.containsKey(ALTAR)) {
            parse(texts.get(ALTAR), ALTAR);
            altar = texts.get(ALTAR);
        }
        String name = str(horde, "hordeName");
        if (name.isBlank()) throw new Rejected("exchange.reject.bad_json", HORDE);

        // 4) cohérence du manifeste
        int format = man.has("format") ? num(man, "format") : 1;
        if (format > FORMAT) throw new Rejected("exchange.reject.newer_format", format);
        String type = modeOf(horde);
        if (format >= 2) {
            String mt = str(man, "type").toLowerCase(Locale.ROOT);
            if (!mt.equals(type)) throw new Rejected("exchange.reject.type_mismatch", mt.isEmpty() ? "?" : mt, type);
        }
        if ((format >= 2 || man.has("name")) && !str(man, "name").equals(name))
            throw new Rejected("exchange.reject.name_mismatch", printable(str(man, "name")), printable(name));

        // 5) mods requis (identifiants propres seulement) ; Pehkui toujours exigé par la Faille, même en format 1
        Set<String> mods = new TreeSet<>();
        if (man.has("mods") && man.get("mods").isJsonArray())
            for (JsonElement m : man.getAsJsonArray("mods"))
                if (m.isJsonPrimitive() && MOD_ID.matcher(m.getAsString()).matches()) mods.add(m.getAsString());
        if (RIFT.equals(type)) mods.add(PEHKUI);
        return new Contents(format, type, man, horde, ents, chests, altar, new ArrayList<>(mods));
    }

    /** Lit au plus MAX_ENTRY_BYTES ; au-delà, refus (la taille annoncée dans le zip n'est pas crue). */
    private static String readBounded(InputStream in, String name) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            total += r;
            if (total > MAX_ENTRY_BYTES) throw new Rejected("exchange.reject.entry_too_large", name, mb(MAX_ENTRY_BYTES));
            bos.write(buf, 0, r);
        }
        return bos.toString(StandardCharsets.UTF_8);
    }

    /** JSON strict : syntaxe standard, document entièrement consommé. */
    private static JsonElement parse(String text, String name) throws Rejected {
        try {
            JsonReader r = new JsonReader(new StringReader(text == null ? "" : text));
            r.setLenient(false);
            JsonElement e = JSON.read(r);
            if (r.peek() != JsonToken.END_DOCUMENT || e == null || e.isJsonNull()) throw new IllegalStateException();
            return e;
        } catch (Exception | StackOverflowError e) {
            throw new Rejected("exchange.reject.bad_json", name);
        }
    }

    private static JsonObject asObject(JsonElement e, String name) throws Rejected {
        if (!e.isJsonObject()) throw new Rejected("exchange.reject.bad_json", name);
        return e.getAsJsonObject();
    }

    // ═══ Garde-fous ═══

    /** Valeurs extrêmes (au-delà de {@link Limits}) : avertissements, l'import reste possible. */
    public static List<Msg> warnings(JsonObject horde, JsonObject entities) {
        List<Msg> out = new ArrayList<>();
        JsonObject cd = obj(horde, "configData");
        int waves = num(cd, "totalWaves");
        if (waves > Limits.WAVES) out.add(Msg.of("exchange.warn.waves", waves, Limits.WAVES));
        long monsters = 0;
        if (cd.has("hordeEntities") && cd.get("hordeEntities").isJsonArray()) {
            for (JsonElement e : cd.getAsJsonArray("hordeEntities")) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                monsters += (long) num(o, "base_count") + (long) num(o, "count_increment") * Math.max(0, waves - 1);
            }
        }
        if (monsters > Limits.MONSTERS_PER_WAVE) out.add(Msg.of("exchange.warn.monsters", monsters, Limits.MONSTERS_PER_WAVE));
        double[] hpDmg = new double[2];
        maxStats(cd, hpDmg);
        maxStats(entities, hpDmg);
        if (hpDmg[0] > Limits.ENTITY_HP) out.add(Msg.of("exchange.warn.hp", fmt(hpDmg[0]), fmt(Limits.ENTITY_HP)));
        if (hpDmg[1] > Limits.ENTITY_DAMAGE) out.add(Msg.of("exchange.warn.damage", fmt(hpDmg[1]), fmt(Limits.ENTITY_DAMAGE)));
        JsonObject rift = obj(cd, "rift");
        double tHp = dbl(rift, "titanHp"), tScale = dbl(rift, "titanScale");
        if (tHp > Limits.TITAN_HP) out.add(Msg.of("exchange.warn.titan_hp", fmt(tHp), fmt(Limits.TITAN_HP)));
        if (tScale > Limits.TITAN_SCALE) out.add(Msg.of("exchange.warn.titan_scale", fmt(tScale), fmt(Limits.TITAN_SCALE)));
        return out;
    }

    /** PV et dégâts les plus hauts trouvés n'importe où (monstres de vague, boss, sbires, entités custom). */
    private static void maxStats(JsonElement e, double[] out) {
        if (e == null) return;
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
                String k = x.getKey();
                JsonElement v = x.getValue();
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                    double d = v.getAsDouble();
                    if (k.equals("max_health") || k.equals("maxHealth")) out[0] = Math.max(out[0], d);
                    else if (k.equals("attack_damage") || k.equals("attackDamage")) out[1] = Math.max(out[1], d);
                } else {
                    maxStats(v, out);
                }
            }
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) maxStats(x, out);
        }
    }

    // ═══ Outils ═══

    private static JsonObject obj(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    static String str(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    static int num(JsonObject o, String k) {
        try { return o != null && o.has(k) ? o.get(k).getAsInt() : 0; } catch (Exception e) { return 0; }
    }

    private static double dbl(JsonObject o, String k) {
        try { return o != null && o.has(k) ? o.get(k).getAsDouble() : 0; } catch (Exception e) { return 0; }
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0)).replace(".0", "");
    }

    /** Nom affichable : sans caractères de contrôle ni codes couleur, 60 caractères max. */
    static String printable(String s) {
        String t = s == null ? "" : s.replaceAll("[\\p{Cntrl}§]", "?");
        return t.length() > 60 ? t.substring(0, 59) + "…" : t;
    }
}
