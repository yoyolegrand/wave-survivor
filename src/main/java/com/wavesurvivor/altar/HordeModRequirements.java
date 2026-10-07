package com.wavesurvivor.altar;

import com.google.gson.Gson;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.ModConfig;
import com.wavesurvivor.config.model.CustomEntityData;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mods requis par une horde, détectés automatiquement : tous les identifiants "namespace:chemin"
 * présents dans la horde (mobs, équipement, loot, marchands, chaos, boss), dans ses entités custom
 * et dans leurs compétences, dont le namespace n'est ni minecraft, ni wavesurvivor, ni roulettechest.
 */
public final class HordeModRequirements {

    private static final Gson GSON = new Gson();
    private static final Pattern ID = Pattern.compile("\"([a-z0-9_.\\-]+):[a-z0-9_./\\-]+\"");
    private static final Pattern REF = Pattern.compile("\"(?:entity_type|customEntityName|minionType|entityType)\"\\s*:\\s*\"([^\":]+)\"");
    private static final Set<String> BUILTIN = Set.of("minecraft", "wavesurvivor", "roulettechest", "forge");

    private HordeModRequirements() {}

    public record Requirement(String modId, boolean installed) {}

    public static List<Requirement> of(HordeConfigMultiData horde) {
        Set<String> ns = new TreeSet<>();
        if (horde == null) return List.of();
        ModConfig cfg = WaveSurvivorMod.getConfig();
        String hordeJson = GSON.toJson(horde);
        collect(hordeJson, ns);

        // Entités custom référencées (+ leurs compétences)
        Matcher m = REF.matcher(hordeJson);
        Set<String> seen = new TreeSet<>();
        while (m.find()) {
            String name = m.group(1);
            if (!seen.add(name) || cfg == null) continue;
            CustomEntityData ce = cfg.findCustomEntity(name);
            if (ce == null) continue;
            collect(GSON.toJson(ce), ns);
            if (ce.skills != null && cfg.customSkill != null) {
                for (String s : ce.skills) {
                    for (CustomSkillData sk : cfg.customSkill) {
                        if (sk.skillName != null && sk.skillName.equalsIgnoreCase(s)) collect(skillJson(sk), ns);
                    }
                }
            }
        }

        List<Requirement> out = new ArrayList<>();
        for (String id : ns) out.add(new Requirement(id, ModList.get().isLoaded(id)));
        return out;
    }

    public static boolean allInstalled(List<Requirement> reqs) {
        for (Requirement r : reqs) if (!r.installed()) return false;
        return true;
    }

    /**
     * JSON d'une compétence pour la détection, SANS les champs des autres types de compétences qui ont une valeur
     * par défaut « mod:id » (ex : ironsSpellId = irons_spellbooks:fireball existe dans TOUTES les compétences) :
     * sinon chaque horde ayant une compétence semblerait exiger Iron's Spells.
     */
    private static String skillJson(CustomSkillData sk) {
        com.google.gson.JsonObject o = GSON.toJsonTree(sk).getAsJsonObject();
        if (!"irons_spell".equals(sk.skillType)) {
            for (String k : new java.util.ArrayList<>(o.keySet())) if (k.startsWith("ironsSpell")) o.remove(k);
        }
        return o.toString();
    }

    private static void collect(String json, Set<String> out) {
        Matcher m = ID.matcher(json);
        while (m.find()) {
            String n = m.group(1);
            if (!BUILTIN.contains(n)) out.add(n);
        }
    }
}
