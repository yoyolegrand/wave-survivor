package com.wavesurvivor.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * FORME CANONIQUE D'UN JSON (1.7, couches de configuration) : clés triées, nombres normalisés (2 = 2.0), sans espaces.
 * Sert à comparer deux éléments de config sans être trompé par la mise en forme, et à calculer leur empreinte.
 * Un champ à null équivaut à un champ absent. Les clés « id » et « _base » du niveau supérieur sont ignorées (identifiant ajouté par l'éditeur, marqueur de version).
 * Uniquement Gson : utilisable hors du jeu (outil de génération des empreintes).
 */
public final class Canon {

    /** Clés du niveau supérieur qui ne comptent pas dans la comparaison. */
    private static final Set<String> IGNORED_TOP = Set.of("id", "_base");

    private Canon() {}

    public static String canon(JsonElement e) {
        StringBuilder sb = new StringBuilder();
        write(sb, e, true);
        return sb.toString();
    }

    /** Empreinte courte (20 caractères hexadécimaux de SHA-256) de la forme canonique. */
    public static String hash(JsonElement e) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(canon(e).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static boolean same(JsonElement a, JsonElement b) {
        return a != null && b != null && canon(a).equals(canon(b));
    }

    private static void write(StringBuilder sb, JsonElement e, boolean top) {
        if (e == null || e.isJsonNull()) { sb.append("null"); return; }
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isNumber()) {
                BigDecimal bd;
                try { bd = new BigDecimal(p.getAsString()); } catch (Exception ex) { sb.append(p.getAsString()); return; }
                sb.append(bd.signum() == 0 ? "0" : bd.stripTrailingZeros().toPlainString());
            } else if (p.isBoolean()) {
                sb.append(p.getAsBoolean());
            } else {
                sb.append(new JsonPrimitive(p.getAsString()).toString());
            }
            return;
        }
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            sb.append('[');
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) sb.append(',');
                write(sb, a.get(i), false);
            }
            sb.append(']');
            return;
        }
        JsonObject o = e.getAsJsonObject();
        List<String> keys = new ArrayList<>(o.keySet());
        Collections.sort(keys);
        sb.append('{');
        boolean first = true;
        for (String k : keys) {
            if (top && IGNORED_TOP.contains(k)) continue;
            if (o.get(k).isJsonNull()) continue; // champ à null = champ absent (même effet une fois lu par Gson)
            if (!first) sb.append(',');
            first = false;
            sb.append(new JsonPrimitive(k).toString()).append(':');
            write(sb, o.get(k), false);
        }
        sb.append('}');
    }
}
