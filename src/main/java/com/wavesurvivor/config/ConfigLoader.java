package com.wavesurvivor.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.*;

/**
 * Charge la config du mod, en deux couches (1.7) :
 *   1. BASE : livrée dans le jar, relue à chaque démarrage (ConfigLayers) ;
 *   2. JOUEUR : config/wavesurvivor/custom_hordes.json, custom_entities.json… (ses ajouts, ses surcharges, ses suppressions),
 *      appliquée par-dessus (CustomHordeStore, CustomEntityStore).
 * Le fichier config/wavesurvivor/config.json des anciennes versions n'est plus lu : la migration (ConfigMigration) en a
 * extrait ce que le joueur avait modifié.
 */
public class ConfigLoader {

    private static final String CONFIG_DIR_NAME = "wavesurvivor";

    private static final Gson GSON = new Gson();

    public static ModConfig loadOrCreateDefault() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIR_NAME);

        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                WaveSurvivorMod.LOGGER.info("[WaveSurvivor] Dossier {} créé.", configDir);
            }
            if (Files.exists(configDir.resolve("config.json"))) {
                WaveSurvivorMod.LOGGER.warn("[WaveSurvivor] config.json est ignoré depuis la 1.7 (la base vient du mod). Mettez vos ajouts dans l'éditeur en jeu.");
            }
            return parseConfig();
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[WaveSurvivor] Erreur chargement config : {}", e.getMessage(), e);
            WaveSurvivorMod.LOGGER.warn("[WaveSurvivor] Fallback sur config vide.");
            return new ModConfig();
        }
    }

    /** Base du jar + couche du joueur → ModConfig. */
    private static ModConfig parseConfig() {
        JsonObject payload = ConfigLayers.payloadCopy();

        // Hordes créées/modifiées en jeu (custom_hordes.json) : prioritaires sur la base
        CustomHordeStore.mergeInto(payload);
        // Entités custom et compétences créées/modifiées en jeu (custom_entities.json)
        CustomEntityStore.mergeInto(payload);

        return GSON.fromJson(payload, ModConfig.class);
    }
}
