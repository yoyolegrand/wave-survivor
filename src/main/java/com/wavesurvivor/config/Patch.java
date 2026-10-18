package com.wavesurvivor.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.Set;

/**
 * CORRECTIFS CHAMP PAR CHAMP (1.7, couches de configuration) : ce que le joueur a changé dans un élément de base
 * (horde, entité, compétence, autel) est stocké comme un « patch » (uniquement les champs modifiés), appliqué par-dessus la
 * version ACTUELLE de la base. Une mise à jour du mod corrige donc tous les champs que le joueur n'a pas touchés.
 *
 *  • objets : fusionnés champ par champ (récursif) ;
 *  • listes (monstres, vagues, boss, compétences…) et valeurs simples : remplacées en bloc ;
 *  • JsonNull dans un patch = champ supprimé.
 * Les clés « id » et « _base » du niveau supérieur sont ignorées.
 * Uniquement Gson (utilisable hors du jeu).
 */
public final class Patch {

    private static final Set<String> IGNORED_TOP = Set.of("id", "_base");

    private Patch() {}

    /** Patch qui transforme base en mine ; null si les deux sont équivalents. */
    public static JsonObject diff(JsonObject base, JsonObject mine) {
        JsonObject p = diff(base, mine, true);
        return p.size() == 0 ? null : p;
    }

    private static JsonObject diff(JsonObject base, JsonObject mine, boolean top) {
        JsonObject p = new JsonObject();
        for (String k : mine.keySet()) {
            if (top && IGNORED_TOP.contains(k)) continue;
            JsonElement m = mine.get(k);
            if (m.isJsonNull() && (!base.has(k) || base.get(k).isJsonNull())) continue; // null = absent
            if (!base.has(k) || base.get(k).isJsonNull()) { p.add(k, m.deepCopy()); continue; }
            JsonElement b = base.get(k);
            if (b.isJsonObject() && m.isJsonObject()) {
                JsonObject sub = diff(b.getAsJsonObject(), m.getAsJsonObject(), false);
                if (sub.size() > 0) p.add(k, sub);
            } else if (!Canon.canon(b).equals(Canon.canon(m))) {
                p.add(k, m.deepCopy());
            }
        }
        for (String k : base.keySet()) {
            if (top && IGNORED_TOP.contains(k)) continue;
            if (!mine.has(k) && !base.get(k).isJsonNull()) p.add(k, JsonNull.INSTANCE);
            else if (mine.has(k) && mine.get(k).isJsonNull() && !base.get(k).isJsonNull()) p.add(k, JsonNull.INSTANCE);
        }
        return p;
    }

    /** Version de base + patch. */
    public static JsonObject apply(JsonObject base, JsonObject patch) {
        JsonObject out = base.deepCopy();
        if (patch != null) merge(out, patch);
        return out;
    }

    private static void merge(JsonObject target, JsonObject patch) {
        for (String k : patch.keySet()) {
            JsonElement v = patch.get(k);
            if (v == null || v.isJsonNull()) { target.remove(k); continue; }
            if (v.isJsonObject() && target.has(k) && target.get(k).isJsonObject()) {
                merge(target.getAsJsonObject(k), v.getAsJsonObject());
            } else {
                target.add(k, v.deepCopy());
            }
        }
    }

    /** Nombre de champs de premier niveau d'un objet modifiés (pour l'affichage), récursif sur les objets. */
    public static int count(JsonObject patch) {
        int n = 0;
        for (String k : patch.keySet()) {
            JsonElement v = patch.get(k);
            n += v != null && v.isJsonObject() ? Math.max(1, count(v.getAsJsonObject())) : 1;
        }
        return n;
    }

    /** Distance entre deux éléments : nombre de champs différents (sert à retrouver la version livrée la plus proche). */
    public static int distance(JsonObject a, JsonObject b) {
        return count(diffAll(a, b));
    }

    private static JsonObject diffAll(JsonObject a, JsonObject b) {
        return diff(a, b, true);
    }
}
