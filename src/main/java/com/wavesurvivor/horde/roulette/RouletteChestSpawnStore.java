package com.wavesurvivor.horde.roulette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistance des points de spawn de roulette chests.
 *
 * Stocke Map<HordeName, List<SpawnPoint>> dans un fichier JSON séparé du config principal.
 * Load au démarrage, save à chaque modification via commande.
 */
public class RouletteChestSpawnStore {

    /** Un point de spawn : à quelle position poser quel type de coffre. */
    public static class SpawnPoint {
        public String configKey;    // clé de la config dans RouletteChestRegistry
        public int x;               // position ABSOLUE (peu importe si spawn config est offset)
        public int y;
        public int z;
        public String dimension;    // "minecraft:overworld" ou autre

        public SpawnPoint() {}
        public SpawnPoint(String configKey, int x, int y, int z, String dimension) {
            this.configKey = configKey;
            this.x = x; this.y = y; this.z = z;
            this.dimension = dimension != null ? dimension : "minecraft:overworld";
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor/roulette_spawns.json");

    /** Map < hordeName -> liste des SpawnPoints > */
    private static Map<String, List<SpawnPoint>> DATA = new LinkedHashMap<>();

    /** Charge le fichier au démarrage. Si absent, initialise vide. */
    public static void load() {
        try {
            if (!Files.exists(FILE)) {
                DATA = new LinkedHashMap<>();
                save(); // Crée le fichier vide
                return;
            }
            String json = Files.readString(FILE);
            Type type = new TypeToken<LinkedHashMap<String, List<SpawnPoint>>>(){}.getType();
            Map<String, List<SpawnPoint>> loaded = GSON.fromJson(json, type);
            DATA = loaded != null ? loaded : new LinkedHashMap<>();
            int total = 0;
            for (List<SpawnPoint> l : DATA.values()) total += l.size();
            WaveSurvivorMod.LOGGER.info("[RouletteSpawnStore] Chargé {} horde(s), {} spawn(s) total", DATA.size(), total);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[RouletteSpawnStore] Erreur load : {}", e.getMessage());
            DATA = new LinkedHashMap<>();
        }
    }

    /** Sauvegarde immédiate. */
    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(DATA));
        } catch (IOException e) {
            WaveSurvivorMod.LOGGER.error("[RouletteSpawnStore] Erreur save : {}", e.getMessage());
        }
    }

    /** Récupère la liste (non-null, potentiellement vide) pour une horde. */
    public static List<SpawnPoint> getSpawns(String hordeName) {
        return DATA.getOrDefault(hordeName, new ArrayList<>());
    }

    /** Ajoute un spawn point et sauvegarde. */
    public static void addSpawn(String hordeName, SpawnPoint sp) {
        DATA.computeIfAbsent(hordeName, k -> new ArrayList<>()).add(sp);
        save();
    }

    /** Retire tous les spawns pour une horde. */
    public static int clearSpawns(String hordeName) {
        List<SpawnPoint> removed = DATA.remove(hordeName);
        save();
        return removed != null ? removed.size() : 0;
    }
}
