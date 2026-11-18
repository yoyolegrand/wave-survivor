package com.wavesurvivor.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/** Fichiers d'échange de hordes : aller-retour des 4 modes, refus des fichiers piégés, compatibilité format 1, garde-fous. */
public class HordeArchiveTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    // ─── hordes livrées avec le mod ───

    private static JsonObject resource(String path) throws Exception {
        try (InputStream in = HordeArchiveTest.class.getResourceAsStream("/defaults/wavesurvivor/" + path)) {
            assertNotNull("ressource introuvable : " + path, in);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** Première horde du fichier ayant ce mode (+ ses entités et compétences s'il y en a). */
    private static JsonObject[] shipped(String file, String mode) throws Exception {
        JsonObject d = resource(file);
        for (JsonElement h : d.getAsJsonArray("hordes")) {
            if (HordeArchive.modeOf(h.getAsJsonObject()).equals(mode)) {
                JsonObject ents = new JsonObject();
                ents.add("entities", d.has("entities") ? d.get("entities") : new JsonArray());
                ents.add("skills", d.has("skills") ? d.get("skills") : new JsonArray());
                return new JsonObject[]{h.getAsJsonObject(), ents};
            }
        }
        fail("aucune horde " + mode + " dans " + file);
        return null;
    }

    private Path export(JsonObject horde, JsonObject ents) throws Exception {
        List<String> mods = HordeArchive.requiredMods(Set.of(), HordeArchive.modeOf(horde));
        JsonObject man = HordeArchive.manifest(horde, ents.getAsJsonArray("entities").size(), ents.getAsJsonArray("skills").size(),
                0, mods, "tester", "2.1.0", "2026-11-15");
        Path f = tmp.newFile().toPath();
        try (OutputStream out = Files.newOutputStream(f)) {
            HordeArchive.write(out, man, horde, ents, new JsonArray(), "{\"ingredients\":[]}");
        }
        return f;
    }

    private void roundTrip(String file, String mode) throws Exception {
        JsonObject[] h = shipped(file, mode);
        HordeArchive.Contents c = HordeArchive.read(export(h[0], h[1]));
        assertEquals(HordeArchive.FORMAT, c.format());
        assertEquals(mode, c.type());
        assertEquals(mode, c.manifest().get("type").getAsString());
        // les valeurs null sont omises à l'écriture (comme l'export d'avant) : sans effet au chargement
        assertEquals(noNulls(h[0]), c.horde());
        assertEquals(noNulls(h[1]), c.entities());
        assertEquals(h[0].get("hordeName").getAsString(), c.manifest().get("name").getAsString());
        assertNotNull(c.altar());
        assertEquals(HordeArchive.RIFT.equals(mode), c.mods().contains(HordeArchive.PEHKUI));
        assertTrue("une horde livrée ne doit déclencher aucun garde-fou : " + HordeArchive.warnings(c.horde(), c.entities()),
                HordeArchive.warnings(c.horde(), c.entities()).isEmpty());
    }

    private static JsonElement noNulls(JsonElement e) {
        if (e.isJsonObject()) {
            JsonObject o = new JsonObject();
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet())
                if (!x.getValue().isJsonNull()) o.add(x.getKey(), noNulls(x.getValue()));
            return o;
        }
        if (e.isJsonArray()) {
            JsonArray a = new JsonArray();
            for (JsonElement x : e.getAsJsonArray()) a.add(noNulls(x));
            return a;
        }
        return e;
    }

    @Test public void roundTripClassic() throws Exception { roundTrip("custom_hordes.json", HordeArchive.CLASSIC); }
    @Test public void roundTripKingdom() throws Exception { roundTrip("additions/kingdom_pillagers.json", HordeArchive.KINGDOM); }
    @Test public void roundTripInvasion() throws Exception { roundTrip("additions/invasions.json", HordeArchive.INVASION); }
    @Test public void roundTripRift() throws Exception { roundTrip("additions/rift.json", HordeArchive.RIFT); }

    @Test public void riftManifestListsPehkui() throws Exception {
        JsonObject[] h = shipped("additions/rift.json", HordeArchive.RIFT);
        assertTrue(HordeArchive.requiredMods(Set.of(), HordeArchive.RIFT).contains(HordeArchive.PEHKUI));
        assertFalse(HordeArchive.requiredMods(Set.of(), HordeArchive.CLASSIC).contains(HordeArchive.PEHKUI));
        HordeArchive.Contents c = HordeArchive.read(export(h[0], h[1]));
        assertTrue(c.manifest().getAsJsonArray("mods").toString().contains("pehkui"));
    }

    @Test public void shippedHordesStayUnderLimits() throws Exception {
        for (String f : List.of("custom_hordes.json", "additions/frozen_peaks.json", "additions/invasions.json",
                "additions/kingdom_pillagers.json", "additions/kingdom_sables.json", "additions/rift.json", "additions/sables_ardents.json")) {
            JsonObject d = resource(f);
            JsonObject ents = new JsonObject();
            ents.add("entities", d.has("entities") ? d.get("entities") : new JsonArray());
            for (JsonElement h : d.getAsJsonArray("hordes"))
                assertTrue(f, HordeArchive.warnings(h.getAsJsonObject(), ents).isEmpty());
        }
    }

    // ─── fichiers refusés ───

    /** Zip construit à la main : entrées dans l'ordre donné. */
    private Path zip(Map<String, byte[]> entries) throws Exception {
        Path f = tmp.newFile().toPath();
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(f))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return f;
    }

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private static Map<String, byte[]> valid() throws Exception {
        JsonObject horde = shipped("custom_hordes.json", HordeArchive.CLASSIC)[0];
        JsonObject man = HordeArchive.manifest(horde, 0, 0, 0, List.of(), "t", "2.1.0", "");
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("manifest.json", b(man.toString()));
        m.put("horde.json", b(horde.toString()));
        return m;
    }

    private static String rejectKey(Path f) {
        try {
            HordeArchive.read(f);
            fail("le fichier aurait dû être refusé");
            return null;
        } catch (HordeArchive.Rejected r) {
            return r.msg.key();
        }
    }

    @Test public void validMinimalZipAccepted() throws Exception {
        assertEquals(HordeArchive.CLASSIC, HordeArchive.read(zip(valid())).type());
    }

    @Test public void rejectsExtraExe() throws Exception {
        Map<String, byte[]> m = valid();
        m.put("setup.exe", new byte[]{0x4D, 0x5A});
        Path f = zip(m);
        try {
            HordeArchive.read(f);
            fail();
        } catch (HordeArchive.Rejected r) {
            assertEquals("exchange.reject.unexpected", r.msg.key());
            assertEquals(List.of("setup.exe"), r.msg.args());
        }
    }

    @Test public void rejectsEntryTooLarge() throws Exception {
        Map<String, byte[]> m = valid();
        byte[] big = new byte[(int) HordeArchive.MAX_ENTRY_BYTES + 1];
        java.util.Arrays.fill(big, (byte) ' ');   // très compressible : le zip reste minuscule
        m.put("entities.json", big);
        Path f = zip(m);
        assertTrue(Files.size(f) < HordeArchive.MAX_ZIP_BYTES);
        assertEquals("exchange.reject.entry_too_large", rejectKey(f));
    }

    @Test public void rejectsZipTooLarge() throws Exception {
        Map<String, byte[]> m = valid();
        byte[] noise = new byte[(int) HordeArchive.MAX_ZIP_BYTES + 1024];
        new Random(1).nextBytes(noise);   // incompressible
        m.put("altar.json", noise);
        assertEquals("exchange.reject.zip_too_large", rejectKey(zip(m)));
    }

    @Test public void rejectsMissingHorde() throws Exception {
        Map<String, byte[]> m = valid();
        m.remove("horde.json");
        assertEquals("exchange.reject.missing", rejectKey(zip(m)));
    }

    @Test public void rejectsMissingManifest() throws Exception {
        Map<String, byte[]> m = valid();
        m.remove("manifest.json");
        assertEquals("exchange.reject.missing", rejectKey(zip(m)));
    }

    @Test public void rejectsTypeMismatch() throws Exception {
        JsonObject horde = shipped("additions/rift.json", HordeArchive.RIFT)[0];
        JsonObject man = HordeArchive.manifest(horde, 0, 0, 0, List.of(), "t", "2.1.0", "");
        man.addProperty("type", "classic");
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("manifest.json", b(man.toString()));
        m.put("horde.json", b(horde.toString()));
        assertEquals("exchange.reject.type_mismatch", rejectKey(zip(m)));
    }

    @Test public void rejectsNameMismatch() throws Exception {
        Map<String, byte[]> m = valid();
        JsonObject man = JsonParser.parseString(new String(m.get("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        man.addProperty("name", "Autre nom");
        m.put("manifest.json", b(man.toString()));
        assertEquals("exchange.reject.name_mismatch", rejectKey(zip(m)));
    }

    @Test public void rejectsPathTraversal() throws Exception {
        Map<String, byte[]> m = valid();
        byte[] h = m.remove("horde.json");
        m.put("../horde.json", h);
        assertEquals("exchange.reject.unexpected", rejectKey(zip(m)));
    }

    @Test public void rejectsFolders() throws Exception {
        Map<String, byte[]> m = valid();
        m.put("data/entities.json", b("{}"));
        assertEquals("exchange.reject.unexpected", rejectKey(zip(m)));
        Map<String, byte[]> m2 = valid();
        m2.put("chests.json/", new byte[0]);
        assertEquals("exchange.reject.unexpected", rejectKey(zip(m2)));
        Map<String, byte[]> m3 = valid();
        m3.put("..\\altar.json", b("{}"));
        assertEquals("exchange.reject.unexpected", rejectKey(zip(m3)));
    }

    @Test public void rejectsInvalidJson() throws Exception {
        Map<String, byte[]> m = valid();
        m.put("entities.json", b("{\"entities\": [ oops"));
        assertEquals("exchange.reject.bad_json", rejectKey(zip(m)));
        Map<String, byte[]> m2 = valid();
        m2.put("chests.json", b("{}"));   // doit être une liste
        assertEquals("exchange.reject.bad_json", rejectKey(zip(m2)));
    }

    @Test public void rejectsNotAZip() throws Exception {
        Path f = tmp.newFile().toPath();
        Files.write(f, b("ceci n'est pas un zip"));
        assertEquals("exchange.reject.not_zip", rejectKey(f));
    }

    // ─── compatibilité ───

    @Test public void format1StillAccepted() throws Exception {
        // ancien export (≤ 2.0) d'une Faille : format 1, type écrit « classic », pas de Pehkui dans les mods
        JsonObject horde = shipped("additions/rift.json", HordeArchive.RIFT)[0];
        JsonObject man = new JsonObject();
        man.addProperty("format", 1);
        man.addProperty("name", horde.get("hordeName").getAsString());
        man.addProperty("type", "classic");
        man.add("mods", new JsonArray());
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("manifest.json", b(man.toString()));
        m.put("horde.json", b(horde.toString()));
        m.put("entities.json", b("{\"entities\":[],\"skills\":[]}"));
        m.put("chests.json", b("[]"));
        HordeArchive.Contents c = HordeArchive.read(zip(m));
        assertEquals(1, c.format());
        assertEquals(HordeArchive.RIFT, c.type());
        assertTrue(c.mods().contains(HordeArchive.PEHKUI));
    }

    // ─── garde-fous ───

    @Test public void extremeValuesWarn() throws Exception {
        JsonObject horde = shipped("additions/rift.json", HordeArchive.RIFT)[0].deepCopy();
        JsonObject cd = horde.getAsJsonObject("configData");
        cd.addProperty("totalWaves", 200);
        JsonObject e = cd.getAsJsonArray("hordeEntities").get(0).getAsJsonObject();
        e.addProperty("base_count", 1000);
        e.addProperty("max_health", 50000);
        e.addProperty("attack_damage", 500);
        cd.getAsJsonObject("rift").addProperty("titanScale", 40);
        cd.getAsJsonObject("rift").addProperty("titanHp", 100000);
        List<HordeArchive.Msg> w = HordeArchive.warnings(horde, new JsonObject());
        List<String> keys = w.stream().map(HordeArchive.Msg::key).toList();
        assertEquals(List.of("exchange.warn.waves", "exchange.warn.monsters", "exchange.warn.hp", "exchange.warn.damage",
                "exchange.warn.titan_hp", "exchange.warn.titan_scale"), keys);
        // un garde-fou n'empêche pas l'import
        JsonObject man = HordeArchive.manifest(horde, 0, 0, 0, List.of(), "t", "2.1.0", "");
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("manifest.json", b(man.toString()));
        m.put("horde.json", b(horde.toString()));
        assertEquals(HordeArchive.RIFT, HordeArchive.read(zip(m)).type());
    }
}
