package com.wavesurvivor.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * OUTIL (hors jeu) : vérifie que Patch.apply(base, Patch.diff(base, modifié)) redonne bien « modifié » pour toute la base
 * livrée (hordes, entités, compétences) et plusieurs sortes de modifications.
 *   java -cp ... com.wavesurvivor.config.PatchRoundTrip
 */
public class PatchRoundTrip {

    private static int checks = 0, failures = 0;

    public static void main(String[] a) {
        JsonObject p = ConfigLayers.builtin().payload;
        for (String key : new String[]{"HordeConfigMulti", "CustomEntity", "CustomSkill"}) {
            for (JsonElement e : p.getAsJsonArray(key)) {
                JsonObject base = e.getAsJsonObject();
                check(key + " identique", base, base.deepCopy(), true);
                JsonObject m1 = base.deepCopy();
                if (bump(m1)) check(key + " nombre modifié", base, m1, false);
                JsonObject m2 = base.deepCopy();
                if (dropFirstNested(m2)) check(key + " champ retiré", base, m2, false);
                JsonObject m3 = base.deepCopy();
                m3.add("zzz_nouveau", new JsonObject());
                m3.getAsJsonObject("zzz_nouveau").addProperty("a", 1);
                check(key + " champ ajouté", base, m3, false);
                JsonObject m4 = base.deepCopy();
                if (truncateFirstArray(m4)) check(key + " liste raccourcie", base, m4, false);
            }
        }
        System.out.println(checks + " vérifications, " + failures + " échec(s).");
        if (failures > 0) System.exit(1);
    }

    private static void check(String label, JsonObject base, JsonObject mine, boolean expectNoPatch) {
        checks++;
        JsonObject patch = Patch.diff(base, mine);
        if (expectNoPatch) {
            if (patch != null) fail(label, "patch non vide : " + patch);
            return;
        }
        if (patch == null) { fail(label, "aucun patch alors que la copie diffère"); return; }
        JsonObject back = Patch.apply(base, patch);
        if (!Canon.canon(back).equals(Canon.canon(mine))) fail(label, "le patch ne redonne pas la version modifiée");
        // idempotence : appliquer deux fois donne le même résultat
        if (!Canon.canon(Patch.apply(back, patch)).equals(Canon.canon(back))) fail(label, "patch non idempotent");
    }

    private static void fail(String label, String why) {
        failures++;
        System.out.println("ÉCHEC " + label + " : " + why);
    }

    /** Incrémente le premier nombre trouvé dans un objet imbriqué. */
    private static boolean bump(JsonObject o) {
        for (String k : o.keySet()) {
            JsonElement v = o.get(k);
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() && !k.equals("id")) { o.addProperty(k, v.getAsDouble() + 1); return true; }
            if (v.isJsonObject() && bump(v.getAsJsonObject())) return true;
        }
        return false;
    }

    private static boolean dropFirstNested(JsonObject o) {
        for (String k : new java.util.ArrayList<>(o.keySet())) {
            JsonElement v = o.get(k);
            if (v.isJsonObject() && v.getAsJsonObject().size() > 0) {
                String first = v.getAsJsonObject().keySet().iterator().next();
                v.getAsJsonObject().remove(first);
                return true;
            }
        }
        return false;
    }

    private static boolean truncateFirstArray(JsonObject o) {
        for (String k : o.keySet()) {
            JsonElement v = o.get(k);
            if (v.isJsonArray() && v.getAsJsonArray().size() > 0) { v.getAsJsonArray().remove(0); return true; }
            if (v.isJsonObject() && truncateFirstArray(v.getAsJsonObject())) return true;
        }
        return false;
    }
}
