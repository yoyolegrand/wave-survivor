package com.wavesurvivor.altar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistance des autels posés dans le monde.
 * Stocke Map<PosKey, AltarEntry> dans config/wavesurvivor/altars.json
 * PosKey = "dimension|x|y|z" (ex "minecraft:overworld|100|64|-200")
 */
public class AltarStore {

    /** Entrée d'un autel enregistré : lien vers une horde + type. */
    public static class AltarEntry {
        public String hordeName;          // nom de la horde liée (peut être null si non lié)
        public String dimension;          // "minecraft:overworld"
        public int x, y, z;
        public String altarType;          // "HORDE_TRIGGER" (futur : autres types)
        public int zoneRadius;            // rayon de la zone (claim) autour de l'autel, 0 = aucune
        public String ownerUuid;          // joueur qui a posé l'autel (null = autel ancien → prochain qui le lie)
        public String ownerName;          // pour l'affichage
        public String particle;           // préréglage de particules (AltarParticles), null = âmes

        /** Propriétaire ou op (niveau 2). */
        public boolean canCustomize(net.minecraft.server.level.ServerPlayer p) {
            return p.hasPermissions(2) || (ownerUuid != null && ownerUuid.equals(p.getUUID().toString()));
        }

        public AltarEntry() {}
        public AltarEntry(String hordeName, String dim, int x, int y, int z, String type) {
            this.hordeName = hordeName;
            this.dimension = dim;
            this.x = x; this.y = y; this.z = z;
            this.altarType = type;
        }

        public BlockPos toBlockPos() { return new BlockPos(x, y, z); }

        /** Vrai si la position (x,z) est dans le disque de la zone de l'autel. */
        public boolean isInZone(BlockPos p) {
            if (zoneRadius <= 0) return false;
            double dx = p.getX() - x, dz = p.getZ() - z;
            return dx * dx + dz * dz <= (zoneRadius + 0.5) * (zoneRadius + 0.5);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor/altars.json");

    /** Map < posKey -> AltarEntry >. ConcurrentHashMap pour safety. */
    private static Map<String, AltarEntry> DATA = new ConcurrentHashMap<>();

    public static String posKey(String dimension, BlockPos pos) {
        return dimension + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) {
                DATA = new ConcurrentHashMap<>();
                save();
                return;
            }
            String json = Files.readString(FILE);
            Type type = new TypeToken<LinkedHashMap<String, AltarEntry>>(){}.getType();
            Map<String, AltarEntry> loaded = GSON.fromJson(json, type);
            DATA = loaded != null ? new ConcurrentHashMap<>(loaded) : new ConcurrentHashMap<>();
            WaveSurvivorMod.LOGGER.info("[AltarStore] Chargé {} autel(s)", DATA.size());
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[AltarStore] Erreur load : {}", e.getMessage());
            DATA = new ConcurrentHashMap<>();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(DATA));
        } catch (IOException e) {
            WaveSurvivorMod.LOGGER.error("[AltarStore] Erreur save : {}", e.getMessage());
        }
    }

    public static AltarEntry get(String dimension, BlockPos pos) {
        return DATA.get(posKey(dimension, pos));
    }

    public static void register(AltarEntry entry) {
        DATA.put(posKey(entry.dimension, entry.toBlockPos()), entry);
        save();
    }

    public static void unregister(String dimension, BlockPos pos) {
        if (DATA.remove(posKey(dimension, pos)) != null) save();
    }

    public static List<AltarEntry> all() {
        return new ArrayList<>(DATA.values());
    }

    public static int count() { return DATA.size(); }
}
