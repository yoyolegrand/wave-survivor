package com.wavesurvivor.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wavesurvivor.horde.editor.HordeJson;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VÉRIFICATION D'UNE HORDE dans l'éditeur (bouton « ✔ Vérifier » et à chaque enregistrement).
 *  - BLOQUANT : la horde planterait ou resterait bloquée (aucun monstre, monstre / boss introuvable, Kingdom sans Porte).
 *  - AVERTISSEMENT : suspect mais jouable (mod absent, identifiant inconnu, boss au-delà des vagues, monstre qui
 *    n'apparaît jamais, vague spéciale vide, marchand sans échange, fin de vague forcée désactivée…).
 * Chaque problème indique l'onglet de l'éditeur où le corriger.
 */
@OnlyIn(Dist.CLIENT)
public final class HordeValidator {

    private HordeValidator() {}

    public record Issue(boolean blocking, int tab, String text) {}

    /** Onglets de l'éditeur (mêmes indices que HordeEditorScreen). */
    static final int T_GENERAL = 0, T_MOBS = 1, T_SPECIAL = 2, T_BOSS = 3, T_MERCH = 4, T_CHAOS = 5;

    private static final Pattern RES_ID = Pattern.compile("^([a-z0-9_.-]+):([a-z0-9_./-]+)$");

    public static List<Issue> check(JsonObject horde, List<String> customEntities) {
        List<Issue> out = new ArrayList<>();
        JsonObject cd = HordeJson.obj(horde, "configData");
        boolean kingdom = "kingdom".equalsIgnoreCase(HordeJson.str(cd, "mode", ""));
        int waves = HordeJson.num(cd, "totalWaves", 0);
        String W = kingdom ? "A" : "V";

        if (HordeJson.str(horde, "hordeName", "").isBlank()) out.add(new Issue(true, T_GENERAL, WSLang.t("check.no_name")));

        // ─── Monstres ───
        JsonArray mobs = HordeJson.arr(cd, "hordeEntities");
        if (mobs.isEmpty()) out.add(new Issue(true, T_MOBS, WSLang.t("check.no_mobs")));
        for (JsonElement e : mobs) {
            if (!e.isJsonObject()) continue;
            JsonObject m = e.getAsJsonObject();
            String name = HordeJson.displayName(m).replaceAll("§.", "");
            checkEntity(HordeJson.str(m, "entity_type", ""), customEntities, T_MOBS, name, out);
            if (!kingdom && HordeJson.num(m, "base_count", 0) <= 0 && HordeJson.num(m, "count_increment", 0) <= 0) {
                out.add(new Issue(false, T_MOBS, WSLang.t("check.never_spawns", name)));
            }
            int min = HordeJson.num(m, "min_wave", 0);
            if (waves > 0 && min > waves) out.add(new Issue(false, T_MOBS, WSLang.t("check.min_wave_too_high", name, W + min, waves)));
        }

        // ─── Vagues spéciales ───
        for (JsonElement e : HordeJson.arr(cd, "specialWaves")) {
            if (!e.isJsonObject()) continue;
            JsonObject s = e.getAsJsonObject();
            String sn = HordeJson.str(s, "name", "?").replaceAll("§.", "").trim();
            JsonArray ents = HordeJson.arr(s, "entities");
            if (ents.isEmpty()) out.add(new Issue(false, T_SPECIAL, WSLang.t("check.special_empty", sn)));
            for (JsonElement x : ents) {
                if (x.isJsonObject()) checkEntity(HordeJson.str(x.getAsJsonObject(), "entity_type", ""), customEntities, T_SPECIAL,
                        sn + " › " + HordeJson.displayName(x.getAsJsonObject()).replaceAll("§.", ""), out);
            }
        }

        // ─── Boss ───
        JsonArray bosses = HordeJson.arr(cd, "bossWaves");
        for (JsonElement e : bosses) {
            if (!e.isJsonObject()) continue;
            JsonObject b = e.getAsJsonObject();
            String bn = HordeJson.str(b, "bossName", "Boss").replaceAll("§.", "");
            int wn = HordeJson.num(b, "waveNumber", 0);
            if (HordeJson.bool(b, "useCustomEntity", false)) {
                String ce = HordeJson.str(b, "customEntityName", "");
                if (ce.isBlank() || !customEntities.contains(ce)) out.add(new Issue(true, T_BOSS, WSLang.t("check.boss_custom_missing", bn, ce)));
            } else {
                checkEntity(HordeJson.str(b, "entityType", ""), customEntities, T_BOSS, bn, out);
            }
            if (!kingdom && waves > 0 && wn > waves) out.add(new Issue(false, T_BOSS, WSLang.t("check.boss_after_end", bn, W + wn, waves)));
            if (wn <= 0) out.add(new Issue(false, T_BOSS, WSLang.t("check.boss_no_wave", bn)));
        }

        // ─── Marchands ───
        for (JsonElement e : HordeJson.arr(cd, "merchants")) {
            if (!e.isJsonObject()) continue;
            JsonObject mm = e.getAsJsonObject();
            if (HordeJson.arr(mm, "trades").isEmpty()) {
                out.add(new Issue(false, T_MERCH, WSLang.t("check.merchant_empty", HordeJson.str(mm, "name", "?"))));
            }
        }

        // ─── Kingdom ───
        if (kingdom) {
            JsonObject k = HordeJson.obj(cd, "kingdom");
            boolean fixed = "fixed".equalsIgnoreCase(HordeJson.str(k, "gatePlacement", "auto"));
            if (fixed && HordeJson.arr(k, "gatePoints").isEmpty()) out.add(new Issue(true, T_GENERAL, WSLang.t("check.kingdom_no_points")));
            if (!fixed && HordeJson.num(k, "gateCount", 4) < 1) out.add(new Issue(true, T_GENERAL, WSLang.t("check.kingdom_no_gate")));
            if (HordeJson.bool(k, "finalBoss", true) && bosses.isEmpty()) out.add(new Issue(false, T_BOSS, WSLang.t("check.kingdom_no_final")));
        }

        // ─── Réglages ───
        if (HordeJson.num(cd, "forceEndSeconds", 60) <= 0) out.add(new Issue(false, T_GENERAL, WSLang.t("check.force_end_off")));

        // ─── Identifiants « mod:objet » de toute la horde : mods absents, objets inconnus ───
        Set<String> ids = new LinkedHashSet<>();
        collect(horde, ids);
        Set<String> missingMods = new TreeSet<>();
        List<String> unknown = new ArrayList<>();
        for (String id : ids) {
            Matcher mt = RES_ID.matcher(id);
            if (!mt.matches()) continue;
            String ns = mt.group(1), path = mt.group(2);
            if (path.contains("/") || "custom".equals(ns)) continue; // tables de butin, chemins : pas des objets
            if (!ModList.get().isLoaded(ns) && !"minecraft".equals(ns)) { missingMods.add(ns); continue; }
            if (!known(new ResourceLocation(ns, path))) unknown.add(id);
        }
        for (String m : missingMods) out.add(new Issue(false, T_GENERAL, WSLang.t("check.mod_missing", m)));
        for (int i = 0; i < Math.min(5, unknown.size()); i++) out.add(new Issue(false, T_GENERAL, WSLang.t("check.unknown_id", unknown.get(i))));
        if (unknown.size() > 5) out.add(new Issue(false, T_GENERAL, WSLang.t("check.unknown_more", unknown.size() - 5)));

        // Bloquants en premier
        out.sort((a, b) -> Boolean.compare(b.blocking(), a.blocking()));
        return out;
    }

    /** Un monstre doit être une entité custom connue ou un type d'entité enregistré. */
    private static void checkEntity(String type, List<String> customEntities, int tab, String who, List<Issue> out) {
        if (type == null || type.isBlank()) {
            out.add(new Issue(true, tab, WSLang.t("check.entity_empty", who)));
            return;
        }
        if (customEntities.contains(type)) return;
        Matcher mt = RES_ID.matcher(type);
        if (!mt.matches()) {
            out.add(new Issue(true, tab, WSLang.t("check.entity_unknown", who, type)));
            return;
        }
        if (!"minecraft".equals(mt.group(1)) && !ModList.get().isLoaded(mt.group(1))) return; // signalé comme mod absent
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(type))) {
            out.add(new Issue(true, tab, WSLang.t("check.entity_unknown", who, type)));
        }
    }

    /** Identifiant connu : objet, bloc, entité, effet, particule, son, enchantement, biome ou dimension. */
    private static boolean known(ResourceLocation rl) {
        if (ForgeRegistries.ITEMS.containsKey(rl) || ForgeRegistries.BLOCKS.containsKey(rl) || ForgeRegistries.ENTITY_TYPES.containsKey(rl)
                || ForgeRegistries.MOB_EFFECTS.containsKey(rl) || ForgeRegistries.PARTICLE_TYPES.containsKey(rl)
                || ForgeRegistries.SOUND_EVENTS.containsKey(rl) || ForgeRegistries.ENCHANTMENTS.containsKey(rl)
                || ForgeRegistries.ATTRIBUTES.containsKey(rl) || ForgeRegistries.POTIONS.containsKey(rl)) return true;
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            var ra = mc.level.registryAccess();
            if (ra.registry(Registries.BIOME).map(r -> r.containsKey(rl)).orElse(false)) return true;
            if (ra.registry(Registries.DIMENSION_TYPE).map(r -> r.containsKey(rl)).orElse(false)) return true;
        }
        if (mc.getConnection() != null) {
            for (var lv : mc.getConnection().levels()) if (lv.location().equals(rl)) return true;
        }
        return false;
    }

    private static void collect(JsonElement e, Set<String> out) {
        if (e == null || e.isJsonNull()) return;
        if (e.isJsonPrimitive()) {
            if (e.getAsJsonPrimitive().isString()) out.add(e.getAsString().trim());
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) collect(x, out);
        } else if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) collect(x.getValue(), out);
        }
    }
}
