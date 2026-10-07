package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.altar.AltarDefense;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MAIRIE DU ROYAUME (le Monolithe en mode Kingdom) : niveaux 1 → 5, achetés avec des ressources (bois, pierre, fer, or…).
 * Chaque niveau : claim +4 blocs, Monolithe +100 PV max, et la place autour du Monolithe s'embellit (vrais blocs,
 * protégés pendant la partie, terrain d'origine restauré à la fin) :
 *  2 « Place pavée »      : anneau de pavés autour du Monolithe + 4 lampadaires aux diagonales ;
 *  3 « Colonnade »        : 4 colonnes de quartz aux points cardinaux, lanternes suspendues, bordure de deepslate ;
 *                           des glyphes magiques tourbillonnent autour du Monolithe ;
 *  4 « Or et flammes »    : chapiteaux d'or et feux des âmes, incrustations d'or dans les pavés ; paillettes dorées ;
 *  5 « Couronne céleste » : cristaux d'améthyste, barres de l'End, spirale de lumière au-dessus du Monolithe.
 * Débloque aussi la progression des défenses (niveau 2 dès Mairie 2, spécialisations dès Mairie 3).
 */
public final class KingdomTownHall {

    private static int tier = 1;
    private static int baseClaim = 16;
    private static ServerLevel level;
    private static BlockPos center;
    /** Blocs d'origine sous la décoration (restaurés à la fin). */
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();
    /** Niveau du sol de chaque colonne décorée (mémorisé à la première pose). */
    private static final Map<Long, Integer> GROUND = new HashMap<>();
    /** PV max ajoutés au Monolithe par niveau de Mairie. */
    public static final int HP_PER_TIER = 100;
    /** Cristal de la Mairie (entité visuelle au-dessus du Monolithe) et sa hauteur au-dessus du centre. */
    private static java.util.UUID crystalId;
    private static final double CRYSTAL_HEIGHT = 5.0;

    private KingdomTownHall() {}

    public static int tier() { return tier; }

    /** Reprise de partie : remet la Mairie à ce niveau (décor, cristal, claim) sans coût ni PV de Monolithe en plus. */
    static void restoreTier(int t) {
        if (level == null) return;
        int target = Math.max(1, Math.min(KingdomCosts.TOWN_MAX, t));
        while (tier < target) {
            tier++;
            decorate(tier);
        }
        updateCrystal();
        KingdomClaim.setRadius(claimRadius());
    }

    public static int claimRadius() { return baseClaim + (tier - 1) * KingdomCosts.CLAIM_PER_TIER; }

    static void start(ServerLevel l, BlockPos c, int claim) {
        stop();
        level = l;
        center = c;
        tier = 1;
        baseClaim = Math.max(4, claim);
    }

    /** « La Chute du Monolithe » : le cristal de la Mairie éclate en morceaux. */
    public static void shatterCrystal() {
        if (level == null || crystalId == null) return;
        net.minecraft.world.entity.Entity c = level.getEntity(crystalId);
        if (c == null) return;
        double x = c.getX(), y = c.getY() + 0.8, z = c.getZ();
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.EMERALD_BLOCK.defaultBlockState()), x, y, z, 80, 0.8, 0.8, 0.8, 0.3);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK.defaultBlockState()), x, y, z, 50, 0.8, 0.8, 0.8, 0.3);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, x, y, z, 40, 0.3, 0.3, 0.3, 0.25);
        level.playSound(null, c.blockPosition(), net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.MASTER, 3f, 0.6f);
        level.playSound(null, c.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK, net.minecraft.sounds.SoundSource.MASTER, 3f, 0.5f);
        c.discard();
        crystalId = null;
    }

    /** « La Chute du Monolithe » : le décor de la Mairie s'effondre en poussière (le terrain d'origine réapparaît). */
    public static void crumble() {
        if (level == null || ORIGINAL.isEmpty()) return;
        List<Map.Entry<BlockPos, BlockState>> list = new ArrayList<>(ORIGINAL.entrySet());
        int n = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            BlockPos p = list.get(i).getKey();
            BlockState now = level.getBlockState(p);
            if (now.equals(list.get(i).getValue())) continue;
            if (!now.isAir()) {
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, now),
                        p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
                if (n++ % 12 == 0) level.levelEvent(2001, p, net.minecraft.world.level.block.Block.getId(now)); // bruit de casse (dosé)
            }
            level.setBlock(p, list.get(i).getValue(), 2);
        }
        KingdomManager.removeStruct(ORIGINAL.keySet());
        ORIGINAL.clear();
    }

    static void stop() {
        if (level != null) {
            if (crystalId != null && level.getEntity(crystalId) != null) level.getEntity(crystalId).discard();
            List<Map.Entry<BlockPos, BlockState>> list = new ArrayList<>(ORIGINAL.entrySet());
            for (int i = list.size() - 1; i >= 0; i--) level.setBlock(list.get(i).getKey(), list.get(i).getValue(), 2);
        }
        KingdomManager.removeStruct(ORIGINAL.keySet());
        ORIGINAL.clear();
        GROUND.clear();
        crystalId = null;
        level = null;
        tier = 1;
    }

    /** Achat du niveau suivant (boutique du Monolithe, onglet Mairie). */
    public static void upgrade(ServerPlayer p) {
        if (level == null || !KingdomManager.isActive()) {
            p.displayClientMessage(WSLang.c("kingdom.defense_only_kingdom"), true);
            return;
        }
        if (tier >= KingdomCosts.TOWN_MAX) {
            p.displayClientMessage(WSLang.c("kingdom.town_max"), true);
            return;
        }
        List<KingdomCosts.Cost> cost = KingdomCosts.townHall(tier + 1);
        net.minecraft.world.item.Item cur = AltarDefense.currencyItem();
        if (!KingdomCosts.canAfford(p, cost, cur)) {
            p.displayClientMessage(WSLang.c("kingdom.need").copy().append(KingdomCosts.missing(p, cost, cur)), false);
            return;
        }
        KingdomCosts.take(p, cost, cur);
        tier++;
        decorate(tier);
        updateCrystal();
        KingdomClaim.setRadius(claimRadius());
        AltarDefense.addMaxHp(HP_PER_TIER);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, center.getX() + 0.5, center.getY() + 2, center.getZ() + 0.5, 120, 2, 2, 2, 0.4);
        level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, 60, 3, 0.2, 3, 0.05);
        level.playSound(null, center, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1f, 1f);
        level.playSound(null, center, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 0.8f);
        for (ServerPlayer pl : level.players()) {
            pl.sendSystemMessage(WSLang.c("kingdom.town_upgraded", tier, claimRadius(), HP_PER_TIER));
        }
    }

    /** Crée le cristal au premier niveau gagné, puis le fait grandir (sa taille suit le niveau de la Mairie). */
    private static void updateCrystal() {
        com.wavesurvivor.entity.BrecheEntity b = crystalId != null && level.getEntity(crystalId) instanceof com.wavesurvivor.entity.BrecheEntity x ? x : null;
        if (b == null) {
            b = com.wavesurvivor.registry.ModEntities.BRECHE.get().create(level);
            if (b == null) return;
            b.moveTo(center.getX() + 0.5, center.getY() + CRYSTAL_HEIGHT, center.getZ() + 0.5, 0f, 0f);
            b.setCrystal(true);
            b.setNoGravity(true);
            b.setSilent(true);
            b.setPersistenceRequired();
            if (!level.addFreshEntity(b)) return;
            crystalId = b.getUUID();
        }
        b.setRiftEyes(tier);
        level.sendParticles(ParticleTypes.END_ROD, b.getX(), b.getY() + 1, b.getZ(), 40, 0.8, 1.2, 0.8, 0.08);
    }

    // ─── Ambiance (particules selon le niveau) ───

    static void tick(long now) {
        if (level == null || tier < 2 || now % 5 != 0) return;
        // Niv. 2+ : étincelles autour du cristal
        if (now % 10 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + CRYSTAL_HEIGHT + 0.5, center.getZ() + 0.5,
                    1 + tier / 2, 0.6 + 0.2 * tier, 0.8 + 0.2 * tier, 0.6 + 0.2 * tier, 0.01);
        }
        if (tier < 3) return;
        double cx = center.getX() + 0.5, cy = center.getY() + 0.5, cz = center.getZ() + 0.5;
        // Niv. 3+ : glyphes magiques qui tourbillonnent autour du Monolithe
        double a = (now % 360) * 0.12;
        for (int i = 0; i < 3; i++) {
            double b = a + i * Math.PI * 2 / 3;
            level.sendParticles(ParticleTypes.ENCHANT, cx + Math.cos(b) * 2.2, cy + 1.2, cz + Math.sin(b) * 2.2, 2, 0.1, 0.4, 0.1, 0.3);
        }
        // Niv. 4+ : paillettes dorées dans l'air
        if (tier >= 4) level.sendParticles(ParticleTypes.WAX_ON, cx, cy + 2, cz, 3, 2.5, 1.5, 2.5, 0.02);
        // Niv. 5 : spirale de lumière qui s'élève au-dessus du Monolithe
        if (tier >= 5) {
            for (int i = 0; i < 2; i++) {
                double b = a * 2 + i * Math.PI;
                double h = (now % 40) / 40.0 * 8;
                level.sendParticles(ParticleTypes.END_ROD, cx + Math.cos(b + h) * 1.2, cy + 2 + h, cz + Math.sin(b + h) * 1.2, 1, 0, 0, 0, 0);
            }
        }
    }

    // ─── Décoration (vrais blocs, cumulés niveau après niveau) ───

    /**
     * Pose un bloc de décor à (dx, dy, dz) au-dessus du sol mémorisé de la colonne (dy = -1 : le sol lui-même).
     * {@code force} : remplace aussi le terrain naturel (pavés), mais jamais une construction de joueur,
     * un bloc à contenu, une Porte ni le Monolithe.
     */
    private static void put(int dx, int dy, int dz, BlockState st, boolean force) {
        int x = center.getX() + dx, z = center.getZ() + dz;
        int ground = GROUND.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL),
                k -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
        BlockPos p = new BlockPos(x, ground + dy, z);
        BlockState cur = level.getBlockState(p);
        boolean free = cur.isAir() || cur.canBeReplaced() || ORIGINAL.containsKey(p);
        if (!free) {
            if (!force || cur.hasBlockEntity() || KingdomClaim.isPlaced(p) || KingdomManager.isGateBlock(p)
                    || KingdomDefenses.parentOf(p) != null || cur.getDestroySpeed(level, p) < 0) return;
        }
        ORIGINAL.putIfAbsent(p, cur);
        level.setBlock(p, st, 3);
        KingdomManager.addStruct(p);
    }

    private static void put(int dx, int dy, int dz, BlockState st) { put(dx, dy, dz, st, false); }

    private static final int[][] DIAG = {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}};
    private static final int[][] CARD = {{5, 0}, {-5, 0}, {0, 5}, {0, -5}};

    /** Décor propre au niveau atteint. */
    private static void decorate(int t) {
        switch (t) {
            case 2 -> {
                // Place pavée : anneau de 2,5 à 4,5 blocs, pavés mélangés (taillés / moussus / fissurés)
                for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d < 2.5 || d > 4.5) continue;
                    int h = Math.floorMod(dx * 7 + dz * 13, 10);
                    BlockState pave = h < 2 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                            : h < 3 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                    put(dx, -1, dz, pave, true);
                }
                // 4 lampadaires aux diagonales
                for (int[] d : DIAG) {
                    put(d[0], 0, d[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
                    put(d[0], 1, d[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
                    put(d[0], 2, d[1], Blocks.LANTERN.defaultBlockState());
                }
            }
            case 3 -> {
                // Bordure de deepslate autour de la place
                for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d >= 4.5 && d < 5.5 && Math.abs(dx) != 5 && Math.abs(dz) != 5) put(dx, -1, dz, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), true);
                }
                // 4 colonnes de quartz aux points cardinaux, lanterne suspendue
                for (int[] d : CARD) {
                    put(d[0], -1, d[1], Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), true);
                    put(d[0], 0, d[1], Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                    for (int y = 1; y <= 3; y++) put(d[0], y, d[1], Blocks.QUARTZ_PILLAR.defaultBlockState());
                    put(d[0], 4, d[1], Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
                    int sx = Integer.signum(-d[0]), sz = Integer.signum(-d[1]);   // côté tourné vers le Monolithe
                    put(d[0] + sx, 3, d[1] + sz, Blocks.CHAIN.defaultBlockState());
                    put(d[0] + sx, 2, d[1] + sz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                }
            }
            case 4 -> {
                // Chapiteaux d'or + feux des âmes sur les colonnes, lanternes des âmes sur les lampadaires
                for (int[] d : CARD) {
                    put(d[0], 5, d[1], Blocks.GOLD_BLOCK.defaultBlockState());
                    put(d[0], 6, d[1], Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
                }
                for (int[] d : DIAG) put(d[0], 2, d[1], Blocks.SOUL_LANTERN.defaultBlockState());
                // Incrustations d'or dans les pavés (8 points sur l'anneau intérieur)
                int[][] inlay = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}};
                for (int[] d : inlay) put(d[0], -1, d[1], Blocks.GILDED_BLACKSTONE.defaultBlockState(), true);
            }
            case 5 -> {
                // Couronne céleste : cristaux d'améthyste sur les lampadaires, barres de l'End au-dessus des colonnes
                for (int[] d : DIAG) put(d[0], 3, d[1], Blocks.AMETHYST_CLUSTER.defaultBlockState());
                for (int[] d : CARD) {
                    put(d[0], 7, d[1], Blocks.END_ROD.defaultBlockState());
                }
            }
            default -> { }
        }
    }
}
