package com.wavesurvivor.horde.difficulty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * DIFFICULTÉ DE LA HORDE EN COURS (côté serveur) : choisie sur l'écran de l'autel (« en attente » pendant le compte à
 * rebours), active du démarrage à la fin de la horde. Multiplicateurs lus dans config/wavesurvivor/difficulty.json
 * (créé avec les valeurs par défaut s'il manque) : PV / dégâts / nombre des monstres, PV / dégâts des boss, récompenses.
 */
public final class HordeDifficulty {

    /** Multiplicateurs d'un niveau (valeurs du fichier de config, ou par défaut). */
    public static final class Mult {
        public double monsterHealth, monsterDamage, monsterCount, bossHealth, bossDamage, rewards;

        Mult(Difficulty d) {
            monsterHealth = d.monsterHealth;
            monsterDamage = d.monsterDamage;
            monsterCount = d.monsterCount;
            bossHealth = d.bossHealth;
            bossDamage = d.bossDamage;
            rewards = d.rewards;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Difficulty pending = null;
    private static Difficulty active = null;
    private static Mult mult = null;

    private HordeDifficulty() {}

    // ─── Cycle de vie ───

    public static synchronized void setPending(Difficulty d) { pending = d; }

    /** Démarrage de la horde : le niveau choisi devient actif (Normal par défaut) et il est annoncé. */
    public static synchronized void begin(MinecraftServer server) {
        active = pending != null ? pending : Difficulty.NORMAL;
        pending = null;
        mult = load().get(active);
        if (server != null && active != Difficulty.NORMAL) {
            String name = active.color + "\u00a7l" + WSLang.t("difficulty." + active.id);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(Component.literal(WSLang.t("difficulty.announce", name)));
        }
        WaveSurvivorMod.LOGGER.info("[Difficulté] {} (monstres PV \u00d7{}, nombre \u00d7{} \u00b7 boss PV \u00d7{} \u00b7 r\u00e9compenses \u00d7{})",
                active.id, mult.monsterHealth, mult.monsterCount, mult.bossHealth, mult.rewards);
    }

    /** Fin de horde. */
    public static synchronized void clear() {
        active = null;
        mult = null;
    }

    public static synchronized Difficulty active() { return active; }

    private static synchronized Mult m() { return mult; }

    // ─── Effets ───

    public static double countMultiplier() { Mult x = m(); return x == null ? 1.0 : x.monsterCount; }

    public static double rewardMultiplier() { Mult x = m(); return x == null ? 1.0 : x.rewards; }

    /** Unités de horde (à leur enregistrement, après leurs statistiques) : PV et dégâts. */
    public static void applyToUnit(Entity e) {
        Mult x = m();
        if (x == null || !(e instanceof LivingEntity le)) return;
        var tag = le.getPersistentData();
        if (tag.getBoolean("ws_diff") || tag.getBoolean("ws_diff_boss")) return; // déjà traité (unité ou boss)
        tag.putBoolean("ws_diff", true);
        scale(le, Attributes.MAX_HEALTH, x.monsterHealth);
        scale(le, Attributes.ATTACK_DAMAGE, x.monsterDamage);
        if (x.monsterHealth != 1) le.setHealth(le.getMaxHealth());
    }

    /** Boss : PV et dégâts de boss (en tenant compte du multiplicateur d'unité déjà appliqué s'il a été enregistré comme unité). */
    public static void applyToBoss(LivingEntity boss) {
        Mult x = m();
        double g = com.wavesurvivor.horde.bossrush.BossRush.gauntletMultiplier(); // 1.6 — Gantelet : chaque boss plus fort que le précédent
        if (x == null && g == 1.0) return;
        var tag = boss.getPersistentData();
        if (tag.getBoolean("ws_diff_boss")) return;
        tag.putBoolean("ws_diff_boss", true);
        boolean asUnit = tag.getBoolean("ws_diff");
        double bh = x == null ? 1.0 : x.bossHealth, bd = x == null ? 1.0 : x.bossDamage;
        double mh = x == null ? 1.0 : x.monsterHealth, md = x == null ? 1.0 : x.monsterDamage;
        double hp = (asUnit ? bh / Math.max(0.01, mh) : bh) * g;
        double dmg = (asUnit ? bd / Math.max(0.01, md) : bd) * g;
        scale(boss, Attributes.MAX_HEALTH, hp);
        scale(boss, Attributes.ATTACK_DAMAGE, dmg);
        boss.setHealth(boss.getMaxHealth());
    }

    private static void scale(LivingEntity le, Attribute a, double f) {
        if (f == 1) return;
        AttributeInstance i = le.getAttribute(a);
        if (i != null) i.setBaseValue(Math.max(0.5, i.getBaseValue() * f));
    }

    // ─── Fichier de config ───

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("difficulty.json"); }

    /** Lit difficulty.json (le crée avec les valeurs par défaut s'il manque) ; valeur manquante = valeur par défaut. */
    public static Map<Difficulty, Mult> load() {
        Map<Difficulty, Mult> out = new EnumMap<>(Difficulty.class);
        for (Difficulty d : Difficulty.values()) out.put(d, new Mult(d));
        Path f = file();
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                JsonObject root = new JsonObject();
                root.addProperty("_comment", "Wave Survivor - multiplicateurs par niveau de difficulte (1.0 = inchange).");
                for (Difficulty d : Difficulty.values()) root.add(d.id, GSON.toJsonTree(out.get(d)));
                Files.writeString(f, GSON.toJson(root), StandardCharsets.UTF_8);
                return out;
            }
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Difficulty d : Difficulty.values()) {
                if (!root.has(d.id) || !root.get(d.id).isJsonObject()) continue;
                JsonObject o = root.getAsJsonObject(d.id);
                Mult m = out.get(d);
                m.monsterHealth = num(o, "monsterHealth", m.monsterHealth);
                m.monsterDamage = num(o, "monsterDamage", m.monsterDamage);
                m.monsterCount = num(o, "monsterCount", m.monsterCount);
                m.bossHealth = num(o, "bossHealth", m.bossHealth);
                m.bossDamage = num(o, "bossDamage", m.bossDamage);
                m.rewards = num(o, "rewards", m.rewards);
            }
        } catch (Exception ex) {
            WaveSurvivorMod.LOGGER.warn("[Difficulté] Lecture de {} impossible, valeurs par défaut : {}", f, ex.getMessage());
        }
        return out;
    }

    private static double num(JsonObject o, String k, double def) {
        JsonElement e = o.get(k);
        try { return e != null && e.isJsonPrimitive() ? Math.max(0.05, e.getAsDouble()) : def; } catch (Exception ex) { return def; }
    }
}
