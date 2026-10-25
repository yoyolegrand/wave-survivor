package com.wavesurvivor.arena;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PLAN D'ARÈNE (1.8, « arènes toutes faites ») : un fichier JSON décrit une arène bloc par bloc.
 *   name, horde        identifiant de l'arène et horde à laquelle elle est liée ;
 *   size {x,y,z}       dimensions ; origine = coin NORD-OUEST au niveau du sol de l'arène (x = 0, y = 0, z = 0) ;
 *   palette            caractère → état de bloc (avec propriétés) ; « . » = air ;
 *   layers[y][z]       une chaîne par ligne, un caractère par bloc de x = 0 à x = size.x - 1 ; layers[0] = le sol ;
 *   markers            repères nommés : { "pos": [x,y,z] } ou liste de positions.
 * Les plans sont lus dans data/wavesurvivor/arenas/&lt;id&gt;.json (dans le mod) ou config/wavesurvivor/arenas/&lt;id&gt;.json (arènes de joueurs, prioritaires).
 * La pose (place) creuse le terrain, comble les fondations, fond l'arène dans le paysage et renvoie les repères en coordonnées du monde.
 */
public final class ArenaBlueprint {

    /** Hauteur dégagée au-dessus du sol de l'arène. */
    public static final int CLEAR_HEIGHT = 12;
    /** Largeur de l'anneau de raccord autour du cercle. */
    public static final int BLEND_WIDTH = 2;

    public final String id, horde, hordeName;
    public final int sx, sy, sz;
    private final Map<Character, BlockState> palette;
    private final String[][] layers;
    private final Map<String, List<int[]>> markers;

    private ArenaBlueprint(String id, String horde, String hordeName, int sx, int sy, int sz, Map<Character, BlockState> palette, String[][] layers, Map<String, List<int[]>> markers) {
        this.id = id;
        this.horde = horde;
        this.hordeName = hordeName;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.palette = palette;
        this.layers = layers;
        this.markers = markers;
    }

    // ─── Chargement ───

    private static final Map<String, ArenaBlueprint> CACHE = new HashMap<>();

    /** Plan d'une arène (null s'il n'existe pas ou s'il est illisible : voir le journal). */
    public static synchronized ArenaBlueprint load(String id) {
        if (CACHE.containsKey(id)) return CACHE.get(id);
        ArenaBlueprint bp = null;
        try {
            Path f = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("arenas").resolve(id + ".json");
            JsonObject o = null;
            if (Files.exists(f)) {
                o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            } else {
                try (InputStream in = ArenaBlueprint.class.getResourceAsStream("/data/wavesurvivor/arenas/" + id + ".json")) {
                    if (in != null) o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
            if (o != null) bp = parse(id, o);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Arènes] Plan « {} » illisible : {}", id, e.getMessage());
        }
        CACHE.put(id, bp);
        return bp;
    }

    /** Oublie les plans en mémoire (rechargement). */
    public static synchronized void clearCache() { CACHE.clear(); }

    private static ArenaBlueprint parse(String id, JsonObject o) throws Exception {
        JsonObject size = o.getAsJsonObject("size");
        int sx = size.get("x").getAsInt(), sy = size.get("y").getAsInt(), sz = size.get("z").getAsInt();
        Map<Character, BlockState> palette = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("palette").entrySet()) {
            if (e.getKey().length() != 1) continue;
            BlockState st = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), e.getValue().getAsString(), false).blockState();
            palette.put(e.getKey().charAt(0), st);
        }
        JsonArray ly = o.getAsJsonArray("layers");
        String[][] layers = new String[ly.size()][];
        for (int y = 0; y < ly.size(); y++) {
            JsonArray rows = ly.get(y).getAsJsonArray();
            layers[y] = new String[rows.size()];
            for (int z = 0; z < rows.size(); z++) layers[y][z] = rows.get(z).getAsString();
        }
        Map<String, List<int[]>> markers = new LinkedHashMap<>();
        if (o.has("markers")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("markers").entrySet()) {
            JsonElement v = e.getValue();
            List<int[]> list = new ArrayList<>();
            if (v.isJsonObject() && v.getAsJsonObject().has("pos")) list.add(pos(v.getAsJsonObject().getAsJsonArray("pos")));
            else if (v.isJsonArray() && v.getAsJsonArray().size() > 0 && v.getAsJsonArray().get(0).isJsonArray())
                for (JsonElement p : v.getAsJsonArray()) list.add(pos(p.getAsJsonArray()));
            else continue; // notes et autres textes
            markers.put(e.getKey(), list);
        }
        String horde = o.has("horde") ? o.get("horde").getAsString() : "";
        String hordeName = o.has("hordeName") ? o.get("hordeName").getAsString() : "";
        return new ArenaBlueprint(id, horde, hordeName, sx, Math.max(sy, layers.length), sz, palette, layers, markers);
    }

    private static int[] pos(JsonArray a) { return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()}; }

    // ─── Rotation ───

    /** Position (x, z) du plan d'origine → position dans le plan tourné. */
    private int[] rot(int x, int z, Rotation r) {
        return switch (r) {
            case CLOCKWISE_90 -> new int[]{sz - 1 - z, x};
            case CLOCKWISE_180 -> new int[]{sx - 1 - x, sz - 1 - z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, sx - 1 - x};
            default -> new int[]{x, z};
        };
    }

    /** Largeur (x) et profondeur (z) du plan tourné. */
    private int[] dims(Rotation r) {
        return r == Rotation.CLOCKWISE_90 || r == Rotation.COUNTERCLOCKWISE_90 ? new int[]{sz, sx} : new int[]{sx, sz};
    }

    // ─── Terrain ───

    private static boolean solidGround(LevelReader level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        return !st.isAir() && st.getFluidState().isEmpty() && !st.is(BlockTags.LEAVES) && !st.is(BlockTags.LOGS) && !st.canBeReplaced();
    }

    /** Altitude du dernier bloc de sol (arbres, herbes et eau ignorés) à cet endroit. {@code wg} : relief d'origine (génération du monde). */
    public static int groundY(LevelReader level, int x, int z, boolean wg) {
        int y = level.getHeight(wg ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
        while (m.getY() > level.getMinBuildHeight() && !solidGround(level, m)) m.move(0, -1, 0);
        return m.getY();
    }

    // ─── Pose ───

    /** Résultat d'une pose : où l'arène est, et ses repères en coordonnées du monde. */
    public record Placement(ArenaBlueprint plan, Rotation rotation, BlockPos origin, int width, int depth, int groundY, Map<String, List<BlockPos>> markers) {
        public BlockPos marker(String name) {
            List<BlockPos> l = markers.get(name);
            return l == null || l.isEmpty() ? null : l.get(0);
        }

        public List<BlockPos> markerList(String name) { return markers.getOrDefault(name, List.of()); }

        public BlockPos center() { return origin.offset(width / 2, 0, depth / 2); }
    }

    /** Largeur et profondeur de l'arène une fois tournée. */
    public int[] size(Rotation rot) { return dims(rot); }

    /** Rayon du cercle de l'arène. */
    public double radius() { return Math.max(sx, sz) / 2.0; }

    /**
     * Pose l'arène centrée sur (center.x, center.z) (commande de test) : niveau du sol = moyenne des altitudes du terrain sur le cercle,
     * puis {@link #build}. Renvoie l'emplacement et les repères en coordonnées du monde.
     */
    public Placement place(ServerLevel level, BlockPos center, Rotation rot) {
        int[] d = dims(rot);
        int ox = center.getX() - d[0] / 2, oz = center.getZ() - d[1] / 2;
        double cx = (d[0] - 1) / 2.0, cz = (d[1] - 1) / 2.0, radius = radius();
        long sum = 0;
        int count = 0;
        for (int x = 0; x < d[0]; x++) for (int z = 0; z < d[1]; z++) {
            if (Math.hypot(x - cx, z - cz) <= radius) { sum += groundY(level, ox + x, oz + z, false); count++; }
        }
        int y0 = (int) Math.round(sum / (double) Math.max(1, count));
        build(level, ox, oz, y0, rot, null, false);
        return new Placement(this, rot, new BlockPos(ox, y0, oz), d[0], d[1], y0, worldMarkers(ox, oz, y0, rot));
    }

    /** Repères du plan en coordonnées du monde pour une arène dont le coin nord-ouest (après rotation) est (ox, y0, oz). */
    public Map<String, List<BlockPos>> worldMarkers(int ox, int oz, int y0, Rotation rot) {
        Map<String, List<BlockPos>> world = new LinkedHashMap<>();
        for (Map.Entry<String, List<int[]>> e : markers.entrySet()) {
            List<BlockPos> l = new ArrayList<>();
            for (int[] p : e.getValue()) {
                int[] r = rot(p[0], p[2], rot);
                l.add(new BlockPos(ox + r[0], y0 + p[1], oz + r[1]));
            }
            world.put(e.getKey(), l);
        }
        return world;
    }

    /** Positions (monde) de tous les blocs du plan désignés par ce caractère de la palette (rotation comprise). */
    public List<BlockPos> cellsOf(char c, int ox, int oz, int y0, Rotation rot) {
        List<BlockPos> out = new ArrayList<>();
        for (int y = 0; y < layers.length; y++) for (int z = 0; z < layers[y].length; z++) {
            String row = layers[y][z];
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) != c) continue;
                int[] r = rot(x, z, rot);
                out.add(new BlockPos(ox + r[0], y0 + y, oz + r[1]));
            }
        }
        return out;
    }

    private static boolean in(BoundingBox clip, int x, int z) {
        return clip == null || (x >= clip.minX() && x <= clip.maxX() && z >= clip.minZ() && z <= clip.maxZ());
    }

    /**
     * Construit l'arène : dégage le cercle jusqu'à 12 blocs, comble les fondations jusqu'au terrain solide, pose le plan (rotation comprise),
     * puis fond un anneau de 2 blocs de podzol / terre stérile / mousse dans le paysage.
     * {@code clip} : si non nul, seules les colonnes de cette zone sont touchées (génération du monde, chunk par chunk) ; {@code wg} : relief d'origine.
     * Le niveau du sol {@code y0} est fixé une fois pour toutes par l'appelant (il doit être le même pour tous les chunks).
     */
    public void build(WorldGenLevel level, int ox, int oz, int y0, Rotation rot, BoundingBox clip, boolean wg) {
        int[] d = dims(rot);
        int w = d[0], dep = d[1];
        double cx = (w - 1) / 2.0, cz = (dep - 1) / 2.0, radius = radius();
        RandomSource rnd = level.getRandom();
        BlockState air = Blocks.AIR.defaultBlockState();

        // 1. Dégagement du cercle  2. Fondations : sous le sol de l'arène, on comble jusqu'au terrain solide
        for (int x = 0; x < w; x++) for (int z = 0; z < dep; z++) {
            if (Math.hypot(x - cx, z - cz) > radius || !in(clip, ox + x, oz + z)) continue;
            for (int y = 0; y <= CLEAR_HEIGHT; y++) set(level, new BlockPos(ox + x, y0 + y, oz + z), air);
            fill(level, ox + x, y0 - 1, oz + z, y0 - 48);
        }
        // 3. Le plan (l'air du plan est déjà posé par le dégagement)
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < layers[y].length; z++) {
                String row = layers[y][z];
                for (int x = 0; x < row.length(); x++) {
                    BlockState st = palette.get(row.charAt(x));
                    if (st == null || st.isAir()) continue;
                    int[] r = rot(x, z, rot);
                    if (!in(clip, ox + r[0], oz + r[1])) continue;
                    set(level, new BlockPos(ox + r[0], y0 + y, oz + r[1]), st.rotate(rot));
                }
            }
        }
        // 4. Raccord : anneau autour du cercle, qui rejoint peu à peu le terrain naturel
        int rr = (int) Math.ceil(radius + BLEND_WIDTH);
        BlockState[] cover = {Blocks.PODZOL.defaultBlockState(), Blocks.PODZOL.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState()};
        for (int x = (int) -(rr - cx); x <= w - 1 + (rr - cx); x++) for (int z = (int) -(rr - cz); z <= dep - 1 + (rr - cz); z++) {
            double dist = Math.hypot(x - cx, z - cz);
            if (dist <= radius || dist > radius + BLEND_WIDTH || !in(clip, ox + x, oz + z)) continue;
            int wx = ox + x, wz = oz + z;
            int h = groundY(level, wx, wz, wg);
            double frac = (dist - radius) / BLEND_WIDTH;                    // 0 contre l'arène, 1 au bord du raccord
            int ty = (int) Math.round(y0 + (h - y0) * frac);
            for (int y = ty + 1; y <= Math.max(h, ty) + 1 + CLEAR_HEIGHT / 2; y++) set(level, new BlockPos(wx, y, wz), air);
            fill(level, wx, ty - 1, wz, ty - 48);
            set(level, new BlockPos(wx, ty, wz), cover[rnd.nextInt(cover.length)]);
        }
    }

    private static void set(WorldGenLevel level, BlockPos p, BlockState st) {
        if (level.getBlockState(p) != st) level.setBlock(p, st, 2);
    }

    /** Comble la colonne vers le bas depuis y jusqu'au premier bloc de sol solide (au plus jusqu'à minY) : terre stérile puis abîmes concassés. */
    private static void fill(WorldGenLevel level, int x, int y, int z, int minY) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
        int depth = 0;
        while (m.getY() >= Math.max(minY, level.getMinBuildHeight()) && !solidGround(level, m)) {
            level.setBlock(m, (depth++ < 2 ? Blocks.COARSE_DIRT : Blocks.COBBLED_DEEPSLATE).defaultBlockState(), 2);
            m.move(0, -1, 0);
        }
    }
}
