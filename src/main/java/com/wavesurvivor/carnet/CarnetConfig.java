package com.wavesurvivor.carnet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.Patch;
import com.wavesurvivor.horde.editor.HordeJson;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RÉGLAGES DU CARNET DU SURVIVANT : liste des objectifs (compteur suivi, seuil, récompense).
 * Base livrée avec le mod : defaults/wavesurvivor/carnet.json ; couche du joueur : config/wavesurvivor/carnet.json
 * (les listes sont remplacées en entier : pour changer un objectif, recopier toute la liste « objectives »). Rechargé par /ws reload.
 */
public final class CarnetConfig {

    /** Un objet donné en récompense. */
    public record Reward(String item, int count) {}

    /** Un objectif : « stat » = compteur de PlayerStats, « threshold » = progression à accomplir APRÈS la remise du carnet. */
    public record Objective(String id, String stat, long threshold, int pr, List<Reward> items) {}

    private static volatile JsonObject root = null;

    private CarnetConfig() {}

    private static JsonObject base() {
        try (InputStream in = CarnetConfig.class.getResourceAsStream("/defaults/wavesurvivor/carnet.json")) {
            if (in != null) return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Carnet] Lecture de la base impossible : {}", e.getMessage());
        }
        return new JsonObject();
    }

    public static synchronized void load() {
        JsonObject r = base();
        Path f = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("carnet.json");
        if (Files.exists(f)) {
            try {
                r = Patch.apply(r, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[Carnet] {} illisible : {}", f.getFileName(), e.getMessage());
            }
        }
        root = r;
    }

    private static JsonObject cfg() {
        if (root == null) load();
        return root;
    }

    /** Le carnet est-il remis automatiquement à la première connexion ? */
    public static boolean autoGive() { return HordeJson.bool(cfg(), "autoGive", true); }

    public static List<Objective> objectives() {
        List<Objective> out = new ArrayList<>();
        JsonObject c = cfg();
        if (!c.has("objectives") || !c.get("objectives").isJsonArray()) return out;
        for (JsonElement e : c.getAsJsonArray("objectives")) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String id = HordeJson.str(o, "id", ""), stat = HordeJson.str(o, "stat", "");
            if (id.isEmpty() || stat.isEmpty()) continue;
            List<Reward> items = new ArrayList<>();
            if (o.has("items") && o.get("items").isJsonArray()) {
                for (JsonElement ie : o.getAsJsonArray("items")) {
                    if (!ie.isJsonObject()) continue;
                    String it = HordeJson.str(ie.getAsJsonObject(), "item", "");
                    if (!it.isEmpty()) items.add(new Reward(it, Math.max(1, HordeJson.num(ie.getAsJsonObject(), "count", 1))));
                }
            }
            out.add(new Objective(id, stat, Math.max(1, (long) HordeJson.num(o, "threshold", 1)), Math.max(0, HordeJson.num(o, "pr", 0)), items));
        }
        return out;
    }
}
