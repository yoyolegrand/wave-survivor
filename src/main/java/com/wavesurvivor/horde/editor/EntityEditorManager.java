package com.wavesurvivor.horde.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.CustomEntityStore;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.network.EntityEditorPackets;
import com.wavesurvivor.network.HordeEditorPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Côté serveur de l'Éditeur d'entités custom et de compétences (ops). */
public final class EntityEditorManager {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    /** Type de compétence → préfixes des paramètres qui le concernent (champs de CustomSkillData). */
    private static final Map<String, List<String>> TYPE_PREFIXES = new LinkedHashMap<>();
    static {
        TYPE_PREFIXES.put("gatling", List.of("gatling"));
        TYPE_PREFIXES.put("mortar", List.of("mortar"));
        TYPE_PREFIXES.put("dash_charge", List.of("dash"));
        TYPE_PREFIXES.put("uppercut", List.of("uppercut"));
        TYPE_PREFIXES.put("chill", List.of("chill"));
        TYPE_PREFIXES.put("venom", List.of("venom"));
        TYPE_PREFIXES.put("web_trap", List.of("webTrap"));
        TYPE_PREFIXES.put("potion_rain", List.of("potionRain"));
        TYPE_PREFIXES.put("projectile_potion", List.of("projectile", "potionEffect", "potionDuration", "potionAmplifier",
                "potionColor", "potionCooldown", "invisibleProjectile", "trailParticle"));
        TYPE_PREFIXES.put("minions", List.of("minion"));
        TYPE_PREFIXES.put("illusions", List.of("illusions"));
        TYPE_PREFIXES.put("spectral_vanish", List.of("vanish"));
        TYPE_PREFIXES.put("shadow_trap", List.of("shadowTrap"));
        TYPE_PREFIXES.put("void_scream", List.of("voidScream"));
        TYPE_PREFIXES.put("solar", List.of("solar"));
        TYPE_PREFIXES.put("supernova", List.of("supernova"));
        TYPE_PREFIXES.put("force_field", List.of("forceField"));
        TYPE_PREFIXES.put("totem_protection", List.of("totem", "protectionRadius"));
        TYPE_PREFIXES.put("execution", List.of("execution"));
        TYPE_PREFIXES.put("life_drain", List.of("lifeDrain"));
        TYPE_PREFIXES.put("death_mark", List.of("deathMark"));
        TYPE_PREFIXES.put("resurrection", List.of("resurrection"));
        TYPE_PREFIXES.put("burial", List.of("burial"));
        TYPE_PREFIXES.put("eruption", List.of("eruption"));
        TYPE_PREFIXES.put("wither_curse", List.of("witherCurse"));
        TYPE_PREFIXES.put("burning_touch", List.of("burningTouch"));
        TYPE_PREFIXES.put("blink", List.of("blink"));
        TYPE_PREFIXES.put("gravity_well", List.of("gravityWell"));
        // Iron's Spells : toujours déclaré ici, mais proposé dans l'éditeur seulement si le mod est installé
        // (vérifié au moment d'envoyer le schéma, quand la liste des mods est garantie complète)
        TYPE_PREFIXES.put("irons_spell", List.of("ironsSpell"));
        // Boss 2.0 : mécaniques signatures
        TYPE_PREFIXES.put("grave_grip", List.of("graveGrip"));
        TYPE_PREFIXES.put("war_banner", List.of("warBanner"));
        TYPE_PREFIXES.put("homing_orbs", List.of("homingOrbs"));
        TYPE_PREFIXES.put("doom_mark", List.of("doomMark"));
        TYPE_PREFIXES.put("telegraph", List.of("telegraph"));
    }

    private EntityEditorManager() {}

    private static boolean allowed(ServerPlayer p) {
        if (p.hasPermissions(2)) return true;
        p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.editeur_reserve_aux_operateurs")));
        return false;
    }

    /** Schéma : { type: [ {name, kind, def}, ... ] } — kind = int | double | bool | string | list. */
    /**
     * Catalogue des compétences pour le Horde Editor (onglet Mobs) : { skills : [définitions], schema : {type → paramètres} }.
     * Permet d'ajouter des compétences à une unité et de régler leurs paramètres propres.
     */
    public static String skillCatalogJson() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (JsonObject s : CustomEntityStore.all(CustomEntityStore.Kind.SKILL).values()) arr.add(s);
        root.add("skills", arr);
        root.add("schema", schema());
        return GSON.toJson(root);
    }

    private static JsonObject schema() {
        JsonObject out = new JsonObject();
        CustomSkillData defaults = new CustomSkillData();
        for (Map.Entry<String, List<String>> t : TYPE_PREFIXES.entrySet()) {
            if (t.getKey().equals("irons_spell")
                    && !net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) continue; // Iron's absent
            JsonArray fields = new JsonArray();
            for (Field f : CustomSkillData.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                SerializedName sn = f.getAnnotation(SerializedName.class);
                String name = sn != null ? sn.value() : f.getName();
                boolean match = false;
                for (String p : t.getValue()) if (name.startsWith(p)) { match = true; break; }
                if (!match) continue;
                if (t.getKey().equals("projectile_potion") && name.startsWith("potionRain")) continue;
                String kind;
                Class<?> c = f.getType();
                if (c == int.class || c == Integer.class || c == long.class) kind = "int";
                else if (c == double.class || c == Double.class || c == float.class || c == Float.class) kind = "double";
                else if (c == boolean.class || c == Boolean.class) kind = "bool";
                else if (c == String.class) kind = "string";
                else if (List.class.isAssignableFrom(c)) kind = "list";
                else continue;
                JsonObject fd = new JsonObject();
                fd.addProperty("name", name);
                fd.addProperty("kind", kind);
                try {
                    f.setAccessible(true);
                    Object v = f.get(defaults);
                    fd.add("def", v == null ? (kind.equals("list") ? new JsonArray() : new com.google.gson.JsonPrimitive(""))
                            : GSON.toJsonTree(v));
                } catch (Exception e) {
                    fd.addProperty("def", "");
                }
                fields.add(fd);
            }
            // Réglage commun à toutes les compétences (Boss 2.0) : phase de déblocage
            JsonObject up = new JsonObject();
            up.addProperty("name", "unlockPhase");
            up.addProperty("kind", "int");
            up.addProperty("def", 1);
            fields.add(up);
            out.add(t.getKey(), fields);
        }
        return out;
    }

    public static void open(ServerPlayer p) { open(p, ""); }

    public static void open(ServerPlayer p, String focus) {
        if (!allowed(p)) return;
        JsonObject root = new JsonObject();
        root.addProperty("focus", focus != null ? focus : "");
        for (CustomEntityStore.Kind k : CustomEntityStore.Kind.values()) {
            JsonArray arr = new JsonArray();
            JsonObject src = new JsonObject();
            for (Map.Entry<String, JsonObject> e : CustomEntityStore.all(k).entrySet()) {
                arr.add(e.getValue());
                src.addProperty(e.getKey(), CustomEntityStore.sourceOf(k, e.getKey()));
            }
            root.add(k == CustomEntityStore.Kind.ENTITY ? "entities" : "skills", arr);
            root.add(k == CustomEntityStore.Kind.ENTITY ? "entitySources" : "skillSources", src);
        }
        root.add("schema", schema());
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new EntityEditorPackets.Data(GSON.toJson(root)));
    }

    public static void save(ServerPlayer p, boolean skill, String oldName, String json) {
        if (!allowed(p)) return;
        if (HordeManager.get().isRunning()) { result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.impossible_d_enregistrer_pendant_une_hor_3c2c")); return; }
        CustomEntityStore.Kind k = skill ? CustomEntityStore.Kind.SKILL : CustomEntityStore.Kind.ENTITY;
        JsonObject o;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) throw new IllegalArgumentException(com.wavesurvivor.i18n.WSLang.t("pas un objet"));
            o = el.getAsJsonObject();
        } catch (Exception e) {
            result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.json_invalide") + e.getMessage());
            return;
        }
        String name = o.has(k.nameKey()) ? o.get(k.nameKey()).getAsString().trim() : "";
        if (name.isBlank() || name.contains(":")) {
            result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.nom_interne_invalide_vide_ou_contient"));
            return;
        }
        o.addProperty(k.nameKey(), name);
        boolean renamed = oldName == null || oldName.isBlank() || !oldName.equalsIgnoreCase(name);
        if (renamed && CustomEntityStore.exists(k, name)) { result(p, false, "« " + name + com.wavesurvivor.i18n.WSLang.t("srv.existe_deja")); return; }
        if (!o.has("id")) o.addProperty("id", java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        if (skill && (!o.has("skillType") || !TYPE_PREFIXES.containsKey(o.get("skillType").getAsString()))) {
            result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.type_de_competence_inconnu"));
            return;
        }
        CustomEntityStore.put(k, o, oldName);
        WaveSurvivorMod.reloadConfig();
        result(p, true, (skill ? com.wavesurvivor.i18n.WSLang.t("srv.competence") : com.wavesurvivor.i18n.WSLang.t("srv.entite")) + " « " + name + com.wavesurvivor.i18n.WSLang.t("srv.enregistree"));
        open(p, name);
    }

    /**
     * Copie dédiée d'une entité custom pour une horde : même fiche complète (compétences, stats, loot...),
     * nouveau nom « source_horde ». L'original n'est pas modifié.
     */
    public static void fork(ServerPlayer p, String source, String hordeName) {
        if (!allowed(p)) return;
        if (HordeManager.get().isRunning()) { result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.impossible_pendant_une_horde_en_cours")); return; }
        JsonObject src = null;
        for (Map.Entry<String, JsonObject> e : CustomEntityStore.all(CustomEntityStore.Kind.ENTITY).entrySet()) {
            if (e.getKey().equalsIgnoreCase(source)) { src = e.getValue(); break; }
        }
        if (src == null) { result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.entite_introuvable") + source); return; }
        String base = source + "_" + slug(hordeName);
        String name = base;
        int i = 2;
        while (CustomEntityStore.exists(CustomEntityStore.Kind.ENTITY, name)) name = base + "_" + i++;
        JsonObject copy = src.deepCopy();
        copy.addProperty("entityName", name);
        copy.addProperty("id", java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        CustomEntityStore.put(CustomEntityStore.Kind.ENTITY, copy, "");
        WaveSurvivorMod.reloadConfig();
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new EntityEditorPackets.Forked(source, name));
        WaveSurvivorMod.LOGGER.info("[Éditeur] {} : copie dédiée « {} » → « {} »", p.getGameProfile().getName(), source, name);
    }

    private static String slug(String s) {
        String n = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return n.isEmpty() ? "horde" : n;
    }

    public static void delete(ServerPlayer p, boolean skill, String name) {
        if (!allowed(p)) return;
        if (HordeManager.get().isRunning()) { result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.impossible_de_supprimer_pendant_une_hord")); return; }
        CustomEntityStore.Kind k = skill ? CustomEntityStore.Kind.SKILL : CustomEntityStore.Kind.ENTITY;
        if (!CustomEntityStore.exists(k, name)) { result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.introuvable") + name); return; }
        CustomEntityStore.delete(k, name);
        WaveSurvivorMod.reloadConfig();
        result(p, true, "« " + name + com.wavesurvivor.i18n.WSLang.t("srv.supprime_e"));
        open(p);
    }

    private static void result(ServerPlayer p, boolean ok, String msg) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HordeEditorPackets.Result(ok, msg, ""));
    }
}
