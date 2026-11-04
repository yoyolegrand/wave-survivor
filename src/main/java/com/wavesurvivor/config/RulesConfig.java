package com.wavesurvivor.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.editor.HordeJson;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RÈGLES GÉNÉRALES DE JEU (1.9) : valeurs par défaut valables pour toutes les hordes, que chaque horde peut surcharger dans l'éditeur.
 * Base livrée avec le mod : defaults/wavesurvivor/rules.json ; couche du joueur : config/wavesurvivor/rules.json (seulement les champs à changer).
 * Rechargé par /ws reload.
 */
public final class RulesConfig {

    private static volatile JsonObject root = null;

    private RulesConfig() {}

    public static synchronized void load() {
        JsonObject r = new JsonObject();
        try (InputStream in = RulesConfig.class.getResourceAsStream("/defaults/wavesurvivor/rules.json")) {
            if (in != null) r = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Règles] Lecture de la base impossible : {}", e.getMessage());
        }
        Path f = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("rules.json");
        if (Files.exists(f)) {
            try {
                r = Patch.apply(r, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[Règles] {} illisible : {}", f.getFileName(), e.getMessage());
            }
        }
        root = r;
    }

    private static JsonObject cfg() {
        if (root == null) load();
        return root;
    }

    /** Prix de consolation par défaut (% des bonus de victoire, au prorata des vagues survécues) ; 0 = désactivé. */
    public static int consolationPercent() { return Math.max(0, Math.min(100, HordeJson.num(cfg(), "consolationPercent", 30))); }
}
