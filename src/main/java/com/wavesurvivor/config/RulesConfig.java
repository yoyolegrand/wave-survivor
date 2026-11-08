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

    /** Bourse à clés : nombre de cases (arrondi au multiple de 9, 9 à 54). */
    public static int keyPouchSize() { return Math.max(9, Math.min(54, HordeJson.num(cfg(), "keyPouchSize", 27))); }
    /** Bourse à clés : ramassage automatique des clés autorisé. */
    public static boolean keyPouchAutoPickup() { return !cfg().has("keyPouchAutoPickup") || cfg().get("keyPouchAutoPickup").getAsBoolean(); }
    /** Boussole des Vestiges : rayon de recherche (blocs). */
    public static int vestigeRadius() { return cfg().has("vestigeRadius") ? Math.max(100, Math.min(30000, cfg().get("vestigeRadius").getAsInt())) : 2000; }
    /** Boussole des Vestiges : vestiges réclamés partagés par tout le serveur (sinon par joueur). */
    public static boolean vestigeShared() { return cfg().has("vestigeShared") && cfg().get("vestigeShared").getAsBoolean(); }
    /** Boussole des Vestiges : filtre par type (accroupi + clic droit). */
    public static boolean vestigeFilter() { return !cfg().has("vestigeFilter") || cfg().get("vestigeFilter").getAsBoolean(); }
    /** Autel souillé : nombre de Corrompus de la mini-vague (difficulté Normal ; ×0,75 en Facile, ×1,5 en Difficile). */
    public static int altarWaveSize() { return cfg().has("altarWaveSize") ? Math.max(1, Math.min(60, cfg().get("altarWaveSize").getAsInt())) : 8; }
    /** Autel souillé : l'estrade s'ouvre (révèle le coffre) une fois la mini-vague vaincue. */
    public static boolean altarOpenOnVictory() { return !cfg().has("altarOpenOnVictory") || cfg().get("altarOpenOnVictory").getAsBoolean(); }
    /** Autel souillé : chance (en %) qu'un Corrompu du rituel lâche un Éclat de souillure (le Brute de pierre : deux fois plus). */
    public static int altarShardChance() { return cfg().has("altarShardChance") ? Math.max(0, Math.min(100, cfg().get("altarShardChance").getAsInt())) : 30; }
    /** Clé scellée : chance (en %) qu'un boss de horde en lâche une. */
    public static double sealedKeyBossChance() { return cfg().has("sealedKeyBossChance") ? Math.max(0, Math.min(100, cfg().get("sealedKeyBossChance").getAsDouble())) : 7.0; }
    /** Clé scellée : nombre de clés offertes pour une horde terminée sans aucune mort (0 = désactivé). */
    public static int sealedKeyFlawlessCount() { return cfg().has("sealedKeyFlawlessCount") ? Math.max(0, cfg().get("sealedKeyFlawlessCount").getAsInt()) : 1; }
    /** Bourse à clés : ouverture en série (accroupi) autorisée. */
    public static boolean keyPouchBulk() { return !cfg().has("keyPouchBulk") || cfg().get("keyPouchBulk").getAsBoolean(); }
}
