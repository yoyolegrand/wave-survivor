package com.wavesurvivor.horde.rift;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 2.0 — Île de la Faille d'après les plans définitifs (data/wavesurvivor/rift/island_r16 / r32 / r48.json) : l'île COMPLÈTE de ce rayon
 * (dessus et cône du dessous). Repère : « center_surface » = bloc de surface au centre, aligné sur {@link RiftManager#CENTER} (le Monolithe se pose au-dessus).
 * Au Siège, l'île grandit par anneaux (du centre vers le bord) en posant les blocs du plan du rayon final.
 */
final class RiftIsland {

    private static final class Plan {
        int cx, cy, cz;
        String[][] layers;
        Map<Character, BlockState> pal = new HashMap<>();
    }

    private static final Map<Integer, Plan> CACHE = new HashMap<>();
    /** Plan dont l'intérieur (cône du dessous) est déjà posé : évite de repasser sur le centre à chaque anneau. */
    private static int innerBucket = -1;

    private RiftIsland() {}

    /** Plan à utiliser pour un rayon donné : le plus petit des plans (16, 32, 48) qui le contient. */
    private static int bucket(int radius) {
        return radius <= 16 ? 16 : radius <= 32 ? 32 : 48;
    }

    private static Plan plan(int b) {
        Plan cached = CACHE.get(b);
        if (cached != null) return cached;
        Plan p = new Plan();
        try (InputStream in = RiftIsland.class.getResourceAsStream("/data/wavesurvivor/rift/island_r" + b + ".json")) {
            if (in == null) throw new IllegalStateException("plan d'île introuvable : r" + b);
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : o.getAsJsonObject("palette").entrySet()) {
                if (e.getKey().length() != 1) continue;
                BlockState st = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), e.getValue().getAsString(), false).blockState();
                // Veines de la Faille : plaquées contre le sol
                if (st.getBlock() instanceof MultifaceBlock) st = st.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true);
                p.pal.put(e.getKey().charAt(0), st);
            }
            JsonArray c = o.getAsJsonObject("markers").getAsJsonArray("center_surface");
            p.cx = c.get(0).getAsInt();
            p.cy = c.get(1).getAsInt();
            p.cz = c.get(2).getAsInt();
            JsonArray ly = o.getAsJsonArray("layers");
            p.layers = new String[ly.size()][];
            for (int y = 0; y < ly.size(); y++) {
                JsonArray rows = ly.get(y).getAsJsonArray();
                p.layers[y] = new String[rows.size()];
                for (int z = 0; z < rows.size(); z++) p.layers[y][z] = rows.get(z).getAsString();
            }
        } catch (Exception ex) {
            WaveSurvivorMod.LOGGER.error("[Faille] Plan d'île r{} illisible : {}", b, ex.getMessage());
            return null;
        }
        CACHE.put(b, p);
        return p;
    }

    /** Pose l'île de départ (rayon {@code radius}). @return faux si le plan est illisible (l'appelant retombe sur l'île provisoire). */
    static boolean place(ServerLevel l, int radius) {
        Plan p = plan(bucket(radius));
        if (p == null) return false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos c = RiftManager.CENTER;
        for (int y = 0; y < p.layers.length; y++) {
            String[] rows = p.layers[y];
            for (int z = 0; z < rows.length; z++) {
                String row = rows[z];
                for (int x = 0; x < row.length(); x++) {
                    BlockState st = p.pal.get(row.charAt(x));
                    if (st == null || st.isAir()) continue;
                    if (Math.hypot(x - p.cx, z - p.cz) > radius + 0.5) continue;
                    l.setBlock(m.set(c.getX() + x - p.cx, c.getY() + y - p.cy, c.getZ() + z - p.cz), st, 2);
                }
            }
        }
        innerBucket = bucket(radius);
        return true;
    }

    /**
     * Un anneau d'agrandissement : les blocs du plan du rayon final dont la distance au centre est dans ]from - 2 ; to] (l'ancien rebord est
     * remplacé ; l'air du plan n'efface rien) ; au premier anneau avec un nouveau plan, le cône du dessous du centre est complété.
     * @return faux si le plan est illisible
     */
    static boolean ring(ServerLevel l, int from, int to, int finalRadius) {
        Plan p = plan(bucket(Math.max(finalRadius, to)));
        if (p == null) return false;
        boolean inner = innerBucket != bucket(Math.max(finalRadius, to));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos c = RiftManager.CENTER;
        for (int y = 0; y < p.layers.length; y++) {
            String[] rows = p.layers[y];
            for (int z = 0; z < rows.length; z++) {
                String row = rows[z];
                for (int x = 0; x < row.length(); x++) {
                    double d = Math.hypot(x - p.cx, z - p.cz);
                    if (d > to + 0.5) continue;
                    BlockState st = p.pal.get(row.charAt(x));
                    boolean air0 = st == null || st.isAir();
                    m.set(c.getX() + x - p.cx, c.getY() + y - p.cy, c.getZ() + z - p.cz);
                    if (d > from - 2) {
                        if (!air0) l.setBlock(m, st, 2);   // (l'air du plan n'efface rien : le pont et les constructions restent)
                    } else if (inner && !air0 && l.getBlockState(m).isAir()) {
                        l.setBlock(m, st, 2);
                    }
                }
            }
        }
        innerBucket = bucket(Math.max(finalRadius, to));
        return true;
    }
}
