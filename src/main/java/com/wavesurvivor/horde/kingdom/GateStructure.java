package com.wavesurvivor.horde.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PORTE DE L'ABÎME EN BLOCS RÉELS (style « blocks » du mode Kingdom).
 * Construit autour de la faille une vraie structure solide (≈ 11 de large × 13 de haut × 5 de profondeur) :
 * plateforme, 2 piliers veinés de lumière, chapiteaux + pics, ARCHE OUVRAGÉE (bandeau, frise lumineuse,
 * clé de voûte, gradins, couronne : crâne / tête de dragon / conduit…), chaînes + lanternes, feux au pied.
 * La faille (BrecheEntity) reste au centre de l'ouverture : c'est elle qu'on attaque.
 * Les blocs d'origine sont mémorisés : effondrement / fin de partie → le terrain est restauré à l'identique.
 * Le style « visual » (ancienne Porte entièrement dessinée) reste disponible dans l'éditeur.
 */
final class GateStructure {

    /** Matériaux d'un thème. */
    private record Palette(BlockState platform, BlockState cracked, BlockState pillar, BlockState vein, BlockState capital,
                           BlockState arch, BlockState spike, BlockState fire, BlockState lantern,
                           BlockState keystone, BlockState crown) {}

    private static Palette palette(String theme) {
        BlockState soulFire = Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
        BlockState fire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
        BlockState soulLantern = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        BlockState drip = Blocks.POINTED_DRIPSTONE.defaultBlockState();
        return switch (theme == null ? "abyss" : theme) {
            case "fire" -> new Palette(Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.CRACKED_NETHER_BRICKS.defaultBlockState(),
                    Blocks.BLACKSTONE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState(),
                    Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.BASALT.defaultBlockState(), fire, lantern,
                    Blocks.CHISELED_NETHER_BRICKS.defaultBlockState(), Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
            case "bone" -> new Palette(Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(),
                    Blocks.BONE_BLOCK.defaultBlockState(), Blocks.SCULK.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                    Blocks.DEEPSLATE_BRICKS.defaultBlockState(), drip, soulFire, soulLantern,
                    Blocks.CHISELED_DEEPSLATE.defaultBlockState(), Blocks.SKELETON_SKULL.defaultBlockState());
            case "end" -> new Palette(Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.END_STONE.defaultBlockState(),
                    Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(),
                    Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.END_ROD.defaultBlockState(), Blocks.END_ROD.defaultBlockState(), soulLantern,
                    Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.DRAGON_HEAD.defaultBlockState());
            case "ocean" -> new Palette(Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState(),
                    Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState(),
                    Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), soulLantern,
                    Blocks.SEA_LANTERN.defaultBlockState(), Blocks.CONDUIT.defaultBlockState());
            case "arcane" -> new Palette(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
                    Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(),
                    Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.AMETHYST_CLUSTER.defaultBlockState(), soulFire, soulLantern,
                    Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.AMETHYST_CLUSTER.defaultBlockState());
            default -> new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                    Blocks.OBSIDIAN.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
                    Blocks.BLACKSTONE.defaultBlockState(), drip, soulFire, soulLantern,
                    Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
        };
    }

    /** Blocs d'origine (pour restaurer le terrain), dans l'ordre de pose. */
    final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
    private final List<BlockPos> crackable = new ArrayList<>();
    private final BlockState crackedState;
    private boolean cracked;
    private final ServerLevel level;
    private final BlockPos origin;
    private final int fx, fz, sx, sz;

    private GateStructure(ServerLevel level, BlockPos origin, int fx, int fz, BlockState crackedState) {
        this.level = level;
        this.origin = origin;
        this.fx = fx;
        this.fz = fz;
        this.sx = -fz;   // axe latéral (perpendiculaire à la façade)
        this.sz = fx;
        this.crackedState = crackedState;
    }

    /** Position monde : lx = latéral, y = hauteur, lz = profondeur (négatif = côté Monolithe). */
    private BlockPos at(int lx, int y, int lz) {
        return origin.offset(sx * lx - fx * lz, y, sz * lx - fz * lz);
    }

    private void put(int lx, int y, int lz, BlockState st) {
        BlockPos p = at(lx, y, lz);
        original.putIfAbsent(p, level.getBlockState(p));
        level.setBlock(p, st, 2);
    }

    /** Rotation 0-15 d'un crâne pour qu'il regarde dans la direction (fx, fz). */
    private static int skullRotation(int fx, int fz) {
        if (fz > 0) return 8;   // regarde vers le sud
        if (fz < 0) return 0;   // vers le nord
        if (fx > 0) return 4;   // vers l'est
        return 12;              // vers l'ouest
    }

    /** Variantes « travaillées » d'un matériau du terrain : taillé, fissuré, sculpté, poli. */
    private record Dressed(BlockState cut, BlockState cracked, BlockState chiseled, BlockState polished) {}

    private static Dressed dressed(BlockState raw) {
        net.minecraft.world.level.block.Block b = raw.getBlock();
        if (b == Blocks.STONE || b == Blocks.COBBLESTONE || b == Blocks.SMOOTH_STONE || b == Blocks.GRAVEL)
            return new Dressed(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
                    Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Blocks.SMOOTH_STONE.defaultBlockState());
        if (b == Blocks.MOSSY_COBBLESTONE || b == Blocks.MOSS_BLOCK)
            return new Dressed(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
                    Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState());
        if (b == Blocks.DEEPSLATE || b == Blocks.COBBLED_DEEPSLATE || b == Blocks.TUFF)
            return new Dressed(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
                    Blocks.CHISELED_DEEPSLATE.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        if (b == Blocks.SAND || b == Blocks.SANDSTONE)
            return new Dressed(Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
                    Blocks.CHISELED_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        if (b == Blocks.RED_SAND || b == Blocks.RED_SANDSTONE)
            return new Dressed(Blocks.CUT_RED_SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(),
                    Blocks.CHISELED_RED_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState());
        if (b == Blocks.GRANITE) return new Dressed(Blocks.POLISHED_GRANITE.defaultBlockState(), raw, Blocks.POLISHED_GRANITE.defaultBlockState(), Blocks.POLISHED_GRANITE.defaultBlockState());
        if (b == Blocks.DIORITE) return new Dressed(Blocks.POLISHED_DIORITE.defaultBlockState(), raw, Blocks.POLISHED_DIORITE.defaultBlockState(), Blocks.POLISHED_DIORITE.defaultBlockState());
        if (b == Blocks.ANDESITE) return new Dressed(Blocks.POLISHED_ANDESITE.defaultBlockState(), raw, Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState());
        if (b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.COARSE_DIRT || b == Blocks.PODZOL || b == Blocks.ROOTED_DIRT
                || b == Blocks.MUD || b == Blocks.MYCELIUM || b == Blocks.CLAY || b == Blocks.DIRT_PATH)
            return new Dressed(Blocks.MUD_BRICKS.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState(),
                    Blocks.MUD_BRICKS.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState());
        if (b == Blocks.SNOW_BLOCK || b == Blocks.POWDER_SNOW || b == Blocks.ICE || b == Blocks.PACKED_ICE)
            return new Dressed(Blocks.PACKED_ICE.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(),
                    Blocks.BLUE_ICE.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState());
        if (b == Blocks.NETHERRACK)
            return new Dressed(Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.CRACKED_NETHER_BRICKS.defaultBlockState(),
                    Blocks.CHISELED_NETHER_BRICKS.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState());
        if (b == Blocks.BLACKSTONE || b == Blocks.BASALT)
            return new Dressed(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                    Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        if (b == Blocks.END_STONE)
            return new Dressed(Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.END_STONE.defaultBlockState(),
                    Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState());
        return new Dressed(raw, raw, raw, raw); // matériau inconnu : utilisé tel quel
    }

    /** Bloc le plus fréquent parmi ceux relevés. */
    private static BlockState mostCommon(java.util.Map<BlockState, Integer> counts, BlockState fallback) {
        BlockState best = fallback;
        int n = 0;
        for (var e : counts.entrySet()) if (e.getValue() > n) { n = e.getValue(); best = e.getKey(); }
        return best;
    }

    /**
     * Thème « Terrain » : la Porte se bâtit avec les matériaux du terrain alentour (zone de 13×13). Le bloc le plus courant
     * en surface donne la plateforme, le plus courant en profondeur (2 à 5 blocs sous la surface) les piliers et l'arche,
     * chacun dans sa version travaillée. Les lueurs et la faille gardent les couleurs de l'abîme.
     */
    private static Palette terrainPalette(ServerLevel level, BlockPos origin) {
        java.util.Map<BlockState, Integer> surf = new java.util.HashMap<>(), deep = new java.util.HashMap<>();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            int x = origin.getX() + dx, z = origin.getZ() + dz;
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            for (int d = 0; d <= 5; d++) {
                BlockPos p = new BlockPos(x, top - d, z);
                BlockState s = level.getBlockState(p);
                if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty() || !s.isCollisionShapeFullBlock(level, p)) continue;
                if (d <= 1) surf.merge(s.getBlock().defaultBlockState(), d == 0 ? 2 : 1, Integer::sum);
                else deep.merge(s.getBlock().defaultBlockState(), 1, Integer::sum);
            }
        }
        BlockState surface = mostCommon(surf, Blocks.STONE.defaultBlockState());
        Dressed top = dressed(surface);
        Dressed low = dressed(mostCommon(deep, surface));
        BlockState soulFire = Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
        BlockState soulLantern = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        return new Palette(top.cut(), top.cracked(), low.cut(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), low.polished(),
                low.cut(), Blocks.POINTED_DRIPSTONE.defaultBlockState(), soulFire, soulLantern,
                low.chiseled(), Blocks.SKELETON_SKULL.defaultBlockState());
    }

    /**
     * Construit la Porte. {@code (fx, fz)} = direction de la façade (vers le Monolithe).
     * La faille doit ensuite être placée sur la plateforme (origin + 1).
     */
    static GateStructure build(ServerLevel level, BlockPos origin, int fx, int fz, String theme) {
        Palette p = "terrain".equals(theme) ? terrainPalette(level, origin) : palette(theme);
        GateStructure g = new GateStructure(level, origin, fx, fz, p.cracked());

        // Ouverture dégagée (la faille vit dedans)
        for (int lx = -2; lx <= 2; lx++) for (int y = 1; y <= 7; y++) for (int lz = -1; lz <= 1; lz++) {
            g.put(lx, y, lz, Blocks.AIR.defaultBlockState());
        }
        // Plateforme + marche avant
        for (int lx = -5; lx <= 5; lx++) for (int lz = -2; lz <= 2; lz++) {
            g.put(lx, 0, lz, p.platform());
            g.crackable.add(g.at(lx, 0, lz));
        }
        for (int lx = -4; lx <= 4; lx++) {
            g.put(lx, 0, -3, p.platform());
            g.crackable.add(g.at(lx, 0, -3));
        }
        // Piliers colossaux (3 × 3), veine lumineuse en façade, chapiteaux, pics
        for (int side : new int[]{-1, 1}) {
            for (int dx = 3; dx <= 5; dx++) for (int y = 1; y <= 9; y++) for (int lz = -1; lz <= 1; lz++) {
                g.put(side * dx, y, lz, p.pillar());
            }
            for (int y = 2; y <= 8; y++) g.put(side * 4, y, -1, p.vein());
            for (int dx = 3; dx <= 5; dx++) for (int lz = -1; lz <= 1; lz++) {
                g.put(side * dx, 10, lz, p.capital());
                g.crackable.add(g.at(side * dx, 10, lz));
            }
            g.put(side * 4, 11, 0, p.spike());
            g.put(side * 4, 1, -2, p.fire());
        }

        // ─── Arche ouvragée au-dessus de l'ouverture ───
        // Bandeau sombre (5 × 3) juste au-dessus de la faille : les chaînes y sont accrochées
        for (int lx = -2; lx <= 2; lx++) for (int lz = -1; lz <= 1; lz++) {
            g.put(lx, 8, lz, p.capital());
            g.crackable.add(g.at(lx, 8, lz));
        }
        // Frise lumineuse en façade (même matériau que les veines) + clé de voûte au centre
        for (int lx = -2; lx <= 2; lx++) {
            g.put(lx, 9, 0, p.arch());
            g.put(lx, 9, -1, lx == 0 ? p.keystone() : p.vein());
        }
        // Gradins, flanqués de 2 pics
        for (int lx = -1; lx <= 1; lx++) g.put(lx, 10, 0, p.arch());
        g.put(-2, 10, 0, p.spike());
        g.put(2, 10, 0, p.spike());
        g.put(0, 11, 0, p.keystone());
        // Couronne (crâne / tête de dragon tournés vers le Monolithe, conduit, améthyste…)
        BlockState crown = p.crown();
        if (crown.hasProperty(BlockStateProperties.ROTATION_16)) {
            crown = crown.setValue(BlockStateProperties.ROTATION_16, skullRotation(fx, fz));
        }
        g.put(0, 12, 0, crown);

        // Chaînes + lanternes accrochées au bandeau, devant la faille
        for (int lx : new int[]{-1, 1}) {
            g.put(lx, 7, -1, Blocks.CHAIN.defaultBlockState());
            g.put(lx, 6, -1, p.lantern());
        }
        level.playSound(null, origin, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.5f, 0.6f);
        return g;
    }

    // ─── Forme « Antre » : amas rocheux, ouverture ovale, liseré et vrilles lumineuses ───

    /** Bruit déterministe 0..1 (même Porte = même rocher). */
    private static double noise(long seed, int a, int b, int c) {
        long h = seed ^ (a * 73856093L) ^ (b * 19349663L) ^ (c * 83492791L);
        h = (h ^ (h >>> 13)) * 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private static double sq(double v) { return v * v; }

    private static long key(int lx, int y, int lz) { return ((long) (lx + 64) << 20) | ((long) (y + 64) << 10) | (lz + 64); }

    /**
     * ANTRE : un dôme de roche penché (~11 × 11) dont la face, tournée vers le Monolithe, est percée d'une ouverture ovale
     * (la faille vit dedans) cerclée de veines lumineuses ; un 2ᵉ rocher plus petit à côté ; des vrilles lumineuses
     * grimpent à la surface des deux. La faille dessinée (ovale tourbillonnant) se pose à origin + 1.
     */
    static GateStructure buildDen(ServerLevel level, BlockPos origin, int fx, int fz, String theme) {
        Palette p = "terrain".equals(theme) ? terrainPalette(level, origin) : palette(theme);
        GateStructure g = new GateStructure(level, origin, fx, fz, p.cracked());
        long seed = origin.asLong();
        java.util.Map<Long, int[]> cells = new java.util.LinkedHashMap<>();
        // Dôme principal (penché sur le côté, bord irrégulier), ouverture ovale creusée dans la face
        for (int lx = -7; lx <= 7; lx++) for (int y = 0; y <= 11; y++) for (int lz = -1; lz <= 7; lz++) {
            double e = sq((lx - y * 0.08) / 5.6) + sq(y / 10.5) + sq((lz - 2.6) / 4.0);
            if (e > 1 + (noise(seed, lx, y, lz) - 0.5) * 0.4) continue;
            if (lz <= 1 && y >= 1 && sq(lx / 2.3) + sq((y - 4) / 3.6) < 1) continue;
            cells.put(key(lx, y, lz), new int[]{lx, y, lz});
        }
        // Rocher annexe, sur le côté
        for (int lx = 7; lx <= 11; lx++) for (int y = 0; y <= 6; y++) for (int lz = -1; lz <= 3; lz++) {
            double e = sq((lx - 9) / 1.8) + sq(y / 6.2) + sq((lz - 1) / 1.8);
            if (e < 1 + (noise(seed, lx, y, lz) - 0.5) * 0.4) cells.put(key(lx, y, lz), new int[]{lx, y, lz});
        }
        // Ouverture dégagée (terrain d'origine retiré) pour la faille
        for (int lx = -2; lx <= 2; lx++) for (int y = 1; y <= 7; y++) for (int lz = -2; lz <= 1; lz++) {
            if (sq(lx / 2.3) + sq((y - 4) / 3.6) < 1) g.put(lx, y, lz, Blocks.AIR.defaultBlockState());
        }
        // Pose de la roche (mélange de 3 matériaux) ; liseré lumineux autour de l'ovale sur la face
        java.util.List<int[]> surface = new ArrayList<>();
        int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] c : cells.values()) {
            double n2 = noise(seed, c[0], c[1], c[2] + 99);
            BlockState rock = n2 < 0.55 ? p.arch() : n2 < 0.85 ? p.pillar() : p.cracked();
            double ring = sq(c[0] / 2.3) + sq((c[1] - 4) / 3.6);
            boolean rim = c[2] <= 0 && c[0] >= -4 && c[0] <= 4 && ring >= 1 && ring < 1.55;
            g.put(c[0], c[1], c[2], rim ? p.vein() : rock);
            boolean surf = false;
            for (int[] d : nb) if (c[1] + d[1] >= 0 && !cells.containsKey(key(c[0] + d[0], c[1] + d[1], c[2] + d[2]))) { surf = true; break; }
            if (surf && !rim) {
                surface.add(c);
                if (n2 < 0.4) g.crackable.add(g.at(c[0], c[1], c[2]));
            }
        }
        // Vrilles lumineuses : 4 sur le dôme, 2 sur le rocher annexe, qui grimpent à la surface
        java.util.Random rnd = new java.util.Random(seed);
        for (int v = 0; v < 6 && !surface.isEmpty(); v++) {
            boolean annex = v >= 4;
            int[] cur = null;
            for (int tries = 0; tries < 40 && cur == null; tries++) {
                int[] c = surface.get(rnd.nextInt(surface.size()));
                if (c[1] <= 1 && (c[0] >= 7) == annex) cur = c;
            }
            if (cur == null) continue;
            for (int step = 0; step < 14 && cur != null; step++) {
                g.put(cur[0], cur[1], cur[2], p.vein());
                int[] next = null;
                for (int tries = 0; tries < 12 && next == null; tries++) {
                    int[] d = nb[rnd.nextInt(6)];
                    int[] c = cells.get(key(cur[0] + d[0], cur[1] + d[1] + (rnd.nextBoolean() ? 1 : 0), cur[2] + d[2]));
                    if (c != null && c[1] >= cur[1] && surface.contains(c)) next = c;
                }
                cur = next;
            }
        }
        level.playSound(null, origin, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.5f, 0.5f);
        return g;
    }

    /** Sous 50 % de PV : les blocs « fissurables » passent à leur variante craquelée (une seule fois). */
    void crack() {
        if (cracked) return;
        cracked = true;
        for (BlockPos q : crackable) level.setBlock(q, crackedState, 2);
    }

    /** Remet le terrain d'origine, à l'identique. */
    void restore() {
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(original.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) level.setBlock(entries.get(i).getKey(), entries.get(i).getValue(), 2);
    }

    /** Effondrement (Porte détruite) : nuées de débris puis terrain restauré. */
    void collapse() {
        int n = 0;
        for (BlockPos q : original.keySet()) {
            if ((n++ & 3) != 0) continue;
            BlockState st = level.getBlockState(q);
            if (!st.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.1);
        }
        level.playSound(null, origin, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3f, 0.6f);
        restore();
    }
}
