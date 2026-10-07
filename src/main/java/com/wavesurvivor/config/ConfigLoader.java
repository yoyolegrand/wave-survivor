package com.wavesurvivor.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Charge la config du mod.
 *
 * Priorité :
 *   1. config/wavesurvivor/config.json (fichier custom du joueur)
 *   2. Sinon copie la config par défaut embarquée dans le mod et la charge
 *
 * Format attendu : JSON exporté depuis base44 ou l'éditeur Wave Survivor,
 * avec un objet racine contenant un champ "donnees" (les entités par nom).
 */
public class ConfigLoader {

    private static final String CONFIG_DIR_NAME = "wavesurvivor";
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String DEFAULT_RESOURCE = "/defaults/wavesurvivor/config.json";

    private static final Gson GSON = new Gson();

    public static ModConfig loadOrCreateDefault() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIR_NAME);
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);

        try {
            // Créer le dossier config/wavesurvivor/ s'il n'existe pas
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                WaveSurvivorMod.LOGGER.info("[WaveSurvivor] Dossier {} créé.", configDir);
            }

            // Si le fichier config.json n'existe pas, on copie la config par défaut
            if (!Files.exists(configFile)) {
                copyDefaultConfig(configFile);
                WaveSurvivorMod.LOGGER.info("[WaveSurvivor] Config par défaut copiée vers {}", configFile);
            }

            // Charger le fichier
            String content = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
            return parseConfig(content);

        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[WaveSurvivor] Erreur chargement config : {}", e.getMessage(), e);
            WaveSurvivorMod.LOGGER.warn("[WaveSurvivor] Fallback sur config vide.");
            return new ModConfig();
        }
    }

    /**
     * Parse la string JSON en ModConfig.
     * Accepte deux formats :
     *   - Format base44 : { "donnees": { "HordeConfig": [...], ... }, ... }
     *   - Format plat  : { "HordeConfig": [...], ... }
     */
    private static ModConfig parseConfig(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        JsonObject payload;
        if (root.has("donnees") && root.get("donnees").isJsonObject()) {
            payload = root.getAsJsonObject("donnees");
        } else {
            payload = root;
        }

        // Hordes créées/modifiées en jeu (custom_hordes.json) : prioritaires, protégées des exports base44
        CustomHordeStore.mergeInto(payload);
        // Entités custom et compétences créées/modifiées en jeu (custom_entities.json)
        CustomEntityStore.mergeInto(payload);

        return GSON.fromJson(payload, ModConfig.class);
    }

    /**
     * Copie /resources/wavesurvivor-default-config.json vers config/wavesurvivor/config.json.
     * Utilisé au premier lancement si le joueur n'a pas encore de fichier.
     */
    private static void copyDefaultConfig(Path target) throws IOException {
        try (InputStream in = ConfigLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                // Fallback : créer un fichier minimal vide (structure valide)
                String empty = "{\n" +
                        "  \"version\": 1,\n" +
                        "  \"app\": \"wave-survivor\",\n" +
                        "  \"donnees\": {\n" +
                        "    \"HordeConfigMulti\": [],\n" +
                        "    \"CustomEntity\": [],\n" +
                        "    \"CustomSkill\": [],\n" +
                        "    \"CustomItem\": []\n" +
                        "  }\n" +
                        "}\n";
                Files.write(target, empty.getBytes(StandardCharsets.UTF_8));
                WaveSurvivorMod.LOGGER.warn("[WaveSurvivor] Aucune config par défaut embarquée — fichier vide créé.");
                return;
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
