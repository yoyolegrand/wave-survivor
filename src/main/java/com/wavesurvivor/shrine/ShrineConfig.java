package com.wavesurvivor.shrine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.Patch;
import com.wavesurvivor.horde.editor.HordeJson;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RÉGLAGES DU SANCTUAIRE DU SÉRAPHIN : jauge de foi, paliers des étapes, offrandes, événements à proximité.
 * Base livrée avec le mod : defaults/wavesurvivor/shrine_offerings.json ; couche du joueur : config/wavesurvivor/shrine_offerings.json
 * (seulement les champs à changer, voir le commentaire du fichier de base). Rechargé par /ws reload.
 */
public final class ShrineConfig {

    private record Offering(ResourceLocation id, boolean tag, int value) {}

    private static volatile JsonObject root = null;
    private static volatile List<Offering> offerings = List.of();

    private ShrineConfig() {}

    private static JsonObject base() {
        try (InputStream in = ShrineConfig.class.getResourceAsStream("/defaults/wavesurvivor/shrine_offerings.json")) {
            if (in != null) return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Sanctuaire] Lecture de la base impossible : {}", e.getMessage());
        }
        return new JsonObject();
    }

    public static synchronized void load() {
        JsonObject r = base();
        Path f = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("shrine_offerings.json");
        if (Files.exists(f)) {
            try {
                r = Patch.apply(r, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[Sanctuaire] {} illisible : {}", f.getFileName(), e.getMessage());
            }
        }
        List<Offering> list = new ArrayList<>();
        if (r.has("offerings") && r.get("offerings").isJsonArray()) {
            for (JsonElement e : r.getAsJsonArray("offerings")) {
                if (!e.isJsonObject()) continue;
                String item = HordeJson.str(e.getAsJsonObject(), "item", "").trim();
                int value = HordeJson.num(e.getAsJsonObject(), "value", 0);
                if (item.isEmpty() || value == 0) continue;
                boolean tag = item.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(tag ? item.substring(1) : item);
                if (id != null) list.add(new Offering(id, tag, value));
            }
        }
        offerings = list;
        root = r;
        WaveSurvivorMod.LOGGER.info("[Sanctuaire] {} offrandes chargées.", list.size());
    }

    private static JsonObject cfg() {
        if (root == null) load();
        return root;
    }

    private static JsonObject sub(String k) {
        JsonObject c = cfg();
        return c.has(k) && c.get(k).isJsonObject() ? c.getAsJsonObject(k) : new JsonObject();
    }

    // ─── Jauge ───

    public static int maxFaith() { return Math.max(10, HordeJson.num(cfg(), "maxFaith", 600)); }

    /** Seuils des étapes : 0 = Pure (foi ≥), 1 = Apaisée (≥), 2 = Souillée (foi ≤ ce seuil), 3 = Corrompue (foi ≤ ce seuil). */
    public static int pure() { return HordeJson.num(sub("stages"), "pure", 400); }
    public static int calm() { return HordeJson.num(sub("stages"), "calm", 100); }
    public static int soiled() { return HordeJson.num(sub("stages"), "soiled", -100); }
    public static int corrupted() { return HordeJson.num(sub("stages"), "corrupted", -400); }

    /** Étape (0 pure … 4 corrompue) correspondant à une valeur de foi. */
    public static int stageOf(int faith) {
        if (faith >= pure()) return 0;
        if (faith >= calm()) return 1;
        if (faith > soiled()) return 2;
        if (faith > corrupted()) return 3;
        return 4;
    }

    /** Foi au milieu d'une étape (migration des sanctuaires qui n'avaient qu'une étape). */
    public static int defaultFaithOf(int stage) {
        return switch (stage) {
            case 0 -> (pure() + maxFaith()) / 2;
            case 1 -> (calm() + pure()) / 2;
            case 3 -> (corrupted() + soiled()) / 2;
            case 4 -> (-maxFaith() + corrupted()) / 2;
            default -> 0;
        };
    }

    // ─── Événements à proximité ───

    public static int eventRadius() { return Math.max(1, HordeJson.num(sub("events"), "radius", 16)); }
    public static int hordeKill() { return HordeJson.num(sub("events"), "hordeMonsterKill", 1); }
    public static int animalKill() { return HordeJson.num(sub("events"), "animalKill", -3); }
    public static int villagerKill() { return HordeJson.num(sub("events"), "villagerKill", -50); }

    // ─── Production de fragments ───

    private static JsonObject prod() { return sub("production"); }

    /** Durée d'un cycle de production, en ticks, pour cette étape (0 = aucune production). */
    public static int intervalTicks(int stage) {
        JsonObject p = prod();
        if (!p.has("intervalSeconds") || !p.get("intervalSeconds").isJsonArray()) return 0;
        var a = p.getAsJsonArray("intervalSeconds");
        if (stage < 0 || stage >= a.size()) return 0;
        return Math.max(0, a.get(stage).getAsInt()) * 20;
    }

    public static int stockCap() { return Math.max(1, HordeJson.num(prod(), "stockCap", 64)); }

    /** Production avancée par un monstre de horde tué à proximité, en ticks. */
    public static int killBonusTicks() { return Math.max(0, HordeJson.num(prod(), "killBonusSeconds", 10)) * 20; }

    public static int fusionCost() { return Math.max(2, HordeJson.num(prod(), "fusionCost", 9)); }

    /** Chance qu'un boss lâche un Cœur (de grâce ou de souillure) à sa mort. */
    public static double bossHeartChance() { return Math.max(0, Math.min(1, HordeJson.dbl(prod(), "bossHeartChance", 0.05))); }

    // ─── Offrandes ───

    /** Valeur d'UNE unité de cet objet (positive : grâce, négative : souillure, 0 : refusé). */
    public static int offerValue(ItemStack st) {
        if (st.isEmpty()) return 0;
        if (root == null) load();
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(st.getItem());
        for (Offering o : offerings) {
            if (o.tag()) {
                if (st.is(TagKey.create(Registries.ITEM, o.id()))) return o.value();
            } else if (o.id().equals(key)) return o.value();
        }
        return 0;
    }
}
