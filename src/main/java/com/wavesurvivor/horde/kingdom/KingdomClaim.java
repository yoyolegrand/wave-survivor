package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MODE KINGDOM — étape 3 : LE CLAIM.
 *  - Zone carrée (rayon claimRadius → ex. 32×32) centrée sur le Monolithe, contour dessiné au sol pendant la partie.
 *  - Construction limitée : pendant une partie, impossible de poser ou casser des blocs HORS du claim (créatif exempté).
 *  - Durabilité des murs : chaque bloc POSÉ PAR UN JOUEUR dans le claim a des PV selon sa dureté ; un monstre bloqué
 *    par un mur le frappe (fissures progressives) jusqu'à le casser. Le terrain naturel n'est jamais touché.
 * Actif uniquement pendant une partie Kingdom.
 */
public final class KingdomClaim {

    private static final DustParticleOptions BORDER = new DustParticleOptions(new Vector3f(0.75f, 0.35f, 1f), 1.8f);

    private static ServerLevel level;
    private static BlockPos center;
    private static int radius = 16;
    private static boolean buildOnlyInClaim = true;
    /** Bornes au sol + particules de la bordure (réglage « claimMarkers », jamais si le claim est désactivé). */
    private static boolean showBorder = true;
    private static boolean walls = true;
    private static double hpMult = 1.0;
    /** Blocs posés par les joueurs dans le claim → PV restants / PV max. */
    private static final Map<BlockPos, float[]> PLACED = new HashMap<>();

    /** Carte du royaume : colonnes (x, z) occupées par des remparts posés. */
    public static java.util.Set<Long> rampartColumns(ServerLevel lvl) {
        java.util.Set<Long> out = new java.util.HashSet<>();
        if (lvl == null) return out;
        for (BlockPos p : PLACED.keySet()) {
            if (lvl.isLoaded(p) && lvl.getBlockState(p).getBlock() instanceof RampartBlock) {
                out.add(((long) p.getX() << 32) ^ (p.getZ() & 0xffffffffL));
            }
        }
        return out;
    }

    private KingdomClaim() {}

    static void start(ServerLevel lvl, BlockPos c, HordeConfigMultiData.KingdomSettings cfg) {
        stop();
        level = lvl;
        center = c;
        radius = Math.max(4, cfg.claimRadius);
        buildOnlyInClaim = cfg.buildOnlyInClaim;
        showBorder = cfg.claimMarkers && cfg.buildOnlyInClaim; // claim désactivé : jamais de bornes ni de bordure
        walls = cfg.wallDurability;
        hpMult = Math.max(0.1, cfg.wallHpMultiplier);
        placeMarkers();
        scanRamparts(); // remparts déjà en place (posés avant la partie, monde rechargé) : suivis avec leurs PV
    }

    /** Remparts déjà présents autour du Monolithe au lancement : chacun redevient un bâtiment (PV, cible du siège). */
    private static void scanRamparts() {
        if (level == null || center == null) return;
        int r = radius + 8;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int found = 0;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -12; dy <= 24; dy++) {
            q.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
            BlockState st = level.getBlockState(q);
            if (st.getBlock() instanceof RampartBlock && st.getValue(RampartBlock.PART) == RampartBlock.Part.BASE) {
                trackRampartAt(q.immutable());
                found++;
            }
        }
        if (found > 0) com.wavesurvivor.WaveSurvivorMod.LOGGER.info("[Kingdom] {} segment(s) de rempart déjà en place suivi(s).", found);
    }

    /**
     * Suit un segment de rempart existant (n'importe lequel de ses 3 blocs) s'il ne l'est pas encore : PV de base,
     * + amélioration s'il est déjà renforcé. Sans effet hors partie Kingdom.
     */
    static void trackRampartAt(BlockPos anyPart) {
        if (level == null) return;
        BlockState st = level.getBlockState(anyPart);
        if (!(st.getBlock() instanceof RampartBlock)) return;
        BlockPos base = RampartBlock.baseOf(anyPart, st);
        java.util.List<BlockPos> parts = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BlockPos q = base.above(i);
            if (PLACED.containsKey(q)) return; // déjà suivi
            if (level.getBlockState(q).getBlock() instanceof RampartBlock) parts.add(q);
        }
        if (parts.isEmpty()) return;
        trackGroup(parts, RampartBlock.HP + (st.getValue(RampartBlock.REINFORCED) ? RampartBlock.UPGRADE_HP : 0f));
    }

    static void stop() {
        if (level != null) {
            for (BlockPos p : PLACED.keySet()) level.destroyBlockProgress(p.hashCode(), p, -1);
            clearMarkers();
        }
        PLACED.clear();
        builderBonus = false;
        foundationLevel = 0;
        level = null;
    }

    public static boolean isActive() { return level != null; }

    /** Demi-côté de la zone (les soldats du royaume restent dedans). */
    public static int radius() { return radius; }

    /** Mairie : agrandit (ou réduit) le claim en cours de partie ; les bornes et la bordure suivent. */
    static void setRadius(int r) {
        clearMarkers();
        radius = Math.max(4, r);
        placeMarkers();
    }

    // ─── Bornes de la bordure (vrais blocs, protégés, retirés à la fin) ───

    private static final Map<BlockPos, BlockState> MARKERS = new java.util.LinkedHashMap<>();

    /**
     * Bornes AU RAS DU SOL (aucun obstacle) : un bloc d'améthyste incrusté tous les 2 blocs, de l'obsidienne pleureuse
     * (lumineuse) tous les 8 blocs et aux 4 coins.
     */
    private static void placeMarkers() {
        if (level == null || center == null || !showBorder) return;
        int cx = center.getX(), cz = center.getZ(), r = radius;
        for (int i = -r + 2; i <= r - 2; i += 2) {
            boolean bright = Math.floorMod(i, 8) == 0;
            marker(cx + i, cz - r, bright);
            marker(cx + i, cz + r, bright);
            marker(cx - r, cz + i, bright);
            marker(cx + r, cz + i, bright);
        }
        for (int[] k : new int[][]{{-r, -r}, {r, -r}, {-r, r}, {r, r}}) marker(cx + k[0], cz + k[1], true);
    }

    /** Remplace le bloc de SOL (affleurant) par une borne ; jamais une construction, une défense, une Porte ni l'eau. */
    private static void marker(int x, int z, boolean bright) {
        BlockPos g = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        BlockState cur = level.getBlockState(g);
        if (cur.isAir() || !cur.getFluidState().isEmpty() || cur.hasBlockEntity() || PLACED.containsKey(g)
                || KingdomManager.isGateBlock(g) || KingdomDefenses.parentOf(g) != null || cur.getDestroySpeed(level, g) < 0) return;
        MARKERS.putIfAbsent(g.immutable(), cur);
        level.setBlock(g, (bright ? net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN
                : net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK).defaultBlockState(), 3);
        KingdomManager.addStruct(g);
    }

    /** Anciennes bornes en hauteur (murets + torches), remplacées par les bornes au ras du sol. */
    private static void markerTallUnused(int x, int z, boolean corner) {
        BlockPos b = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        if (corner) {
            if (!setMarker(b, net.minecraft.world.level.block.Blocks.CHISELED_STONE_BRICKS.defaultBlockState())) return;
            setMarker(b.above(), net.minecraft.world.level.block.Blocks.STONE_BRICK_WALL.defaultBlockState());
            setMarker(b.above(2), net.minecraft.world.level.block.Blocks.STONE_BRICK_WALL.defaultBlockState());
            setMarker(b.above(3), net.minecraft.world.level.block.Blocks.SOUL_LANTERN.defaultBlockState());
        } else {
            if (!setMarker(b, net.minecraft.world.level.block.Blocks.STONE_BRICK_WALL.defaultBlockState())) return;
            setMarker(b.above(), net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState());
        }
    }

    /** Pose un bloc de borne si la place est libre (jamais sur une construction). @return vrai si posé. */
    private static boolean setMarker(BlockPos p, BlockState st) {
        BlockState cur = level.getBlockState(p);
        if (!cur.isAir() && !cur.canBeReplaced()) return false;
        MARKERS.putIfAbsent(p.immutable(), cur);
        level.setBlock(p, st, 3);
        KingdomManager.addStruct(p);
        return true;
    }

    private static void clearMarkers() {
        if (level != null) {
            java.util.List<Map.Entry<BlockPos, BlockState>> list = new java.util.ArrayList<>(MARKERS.entrySet());
            for (int i = list.size() - 1; i >= 0; i--) level.setBlock(list.get(i).getKey(), list.get(i).getValue(), 3);
        }
        KingdomManager.removeStruct(MARKERS.keySet());
        MARKERS.clear();
    }

    /** Étape 5 : vrai si ce bloc a été posé par un joueur dans le claim (mur). */
    static boolean isPlaced(BlockPos p) {
        return PLACED.containsKey(p);
    }

    /** Étape 5 : mur de joueur le plus proche (null si aucun à moins de maxDist blocs). */
    static BlockPos nearestPlaced(BlockPos from, int maxDist) {
        BlockPos best = null;
        double bestD = (double) maxDist * maxDist;
        for (BlockPos p : PLACED.keySet()) {
            double d = p.distSqr(from);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    /** Étape 5 (sapeur) : détruit les blocs posés par les joueurs dans un rayon donné. @return nombre de blocs cassés. */
    static int blastPlaced(BlockPos at, int r) {
        if (level == null) return 0;
        int n = 0;
        for (BlockPos p : new java.util.ArrayList<>(PLACED.keySet())) {
            if (p.distSqr(at) > r * r + 1) continue;
            level.destroyBlockProgress(p.hashCode(), p, -1);
            level.destroyBlock(p, true);
            PLACED.remove(p);
            n++;
        }
        return n;
    }

    /**
     * Sapeur : inflige {@code dmg} dégâts aux murs et défenses dans un rayon (chaque réserve de PV n'est frappée qu'une fois,
     * une défense et son corps invisible partagent la leur). Ce qui tombe à 0 PV est détruit. @return blocs détruits.
     */
    static int blastDamage(BlockPos at, int r, float dmg) {
        if (level == null) return 0;
        int broken = 0;
        java.util.Set<float[]> hitPools = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (BlockPos p : new java.util.ArrayList<>(PLACED.keySet())) {
            if (p.distSqr(at) > r * r + 1) continue;
            float[] hp = PLACED.get(p);
            if (hp == null || !hitPools.add(hp)) continue;
            hp[0] -= dmg;
            KingdomReport.onStructureDamage(dmg);
            if (hp[0] <= 0) {
                KingdomReport.onStructureLost();
                for (Map.Entry<BlockPos, float[]> en : new java.util.ArrayList<>(PLACED.entrySet())) {
                    if (en.getValue() != hp) continue;
                    BlockPos q = en.getKey();
                    level.destroyBlockProgress(q.hashCode(), q, -1);
                    level.destroyBlock(q, true);
                    PLACED.remove(q);
                    broken++;
                }
            } else {
                int stage = (int) Math.max(0, Math.min(9, (1 - hp[0] / hp[1]) * 10));
                level.destroyBlockProgress(p.hashCode(), p, stage);
                syncDefense(p);
            }
        }
        return broken;
    }

    /** Explosion d'une unité de horde : dégâts aux murs et défenses alentour (le terrain naturel n'est jamais touché). */
    public static int explosionDamage(BlockPos at, int r, float dmg) {
        return blastDamage(at, r, dmg);
    }

    /**
     * Coup d'une unité « attaque les bâtiments » : frappe le mur / la défense le plus proche à portée (sauteurs, volants…
     * qui ne percutent jamais un mur de face). @return vrai si un bâtiment a été touché.
     */
    public static boolean hitNear(net.minecraft.world.entity.Mob m, double reach, float dmg) {
        if (level == null || m.level() != level || PLACED.isEmpty()) return false;
        BlockPos best = null;
        double bestD = (reach + 0.5) * (reach + 0.5);
        Vec3 c = m.position().add(0, m.getBbHeight() * 0.5, 0);
        BlockPos mp = m.blockPosition();
        int r = (int) Math.ceil(reach) + 1;
        for (BlockPos p : BlockPos.betweenClosed(mp.offset(-r, -2, -r), mp.offset(r, (int) Math.ceil(m.getBbHeight()) + 1, r))) {
            if (!PLACED.containsKey(p)) continue;
            double d = c.distanceToSqr(Vec3.atCenterOf(p));
            if (d <= bestD) { bestD = d; best = p.immutable(); }
        }
        if (best == null) return false;
        float[] hp = PLACED.get(best);
        BlockState st = level.getBlockState(best);
        if (hp == null || st.isAir()) { PLACED.remove(best); return false; }
        float dealt = Math.max(1f, dmg) * (KingdomDefenses.bulwarkNear(best) ? 0.8f : 1f); // Sanctuaire Rempart : −20 %
        hp[0] -= dealt;
        KingdomReport.onStructureDamage(dealt);
        m.swing(InteractionHand.MAIN_HAND);
        m.getLookControl().setLookAt(best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5);
        level.playSound(null, best, st.getSoundType().getHitSound(), SoundSource.BLOCKS, 1f, 0.8f);
        if (hp[0] <= 0) {
            KingdomReport.onStructureLost();
            for (Map.Entry<BlockPos, float[]> en : new java.util.ArrayList<>(PLACED.entrySet())) {
                if (en.getValue() != hp) continue;
                BlockPos q = en.getKey();
                level.destroyBlockProgress(q.hashCode(), q, -1);
                level.destroyBlock(q, true);
                PLACED.remove(q);
            }
        } else {
            level.destroyBlockProgress(best.hashCode(), best, (int) Math.max(0, Math.min(9, (1 - hp[0] / hp[1]) * 10)));
            syncDefense(best);
        }
        return true;
    }

    /** Montée de niveau d'une défense : +{@code v} PV max (et soignée d'autant), multiplicateur de PV des murs compris. */
    static void addDefenseMaxHp(BlockPos p, float v) {
        float[] hp = PLACED.get(p);
        if (hp == null) return;
        float add = (float) (v * hpMult * bonusMult());
        hp[1] += add;
        hp[0] = Math.min(hp[1], hp[0] + add);
        syncDefense(p);
    }

    public static boolean inside(BlockPos p) {
        return center != null && Math.abs(p.getX() - center.getX()) <= radius && Math.abs(p.getZ() - center.getZ()) <= radius;
    }

    /**
     * Construction autorisée à cette position ? Même règle pour TOUT ce qui se pose (tours, pièges, remparts, portes) :
     * « Claim obligatoire : NON » → partout ; sinon uniquement dans le claim.
     */
    public static boolean buildAllowed(BlockPos p) {
        return !buildOnlyInClaim || inside(p);
    }

    static void track(BlockPos pos, BlockState st) {
        // « Murs cassables » ne concerne que les murs ordinaires : les bâtiments du royaume (tours, remparts, caserne…)
        // sont TOUJOURS suivis (PV, cibles des Sapeurs / Béliers), même quand l'option est désactivée
        boolean defensive = st.getBlock() instanceof DefenseBlock || st.is(com.wavesurvivor.registry.ModBlocks.KINGDOM_RAMPART.get());
        if (!walls && !defensive) return;
        if (st.getBlock() instanceof TrapBlock) return; // pièges : au sol, jamais « frappés » comme un mur
        if (!level.getBlockState(pos).is(st.getBlock())) return; // défense rendue (pas de place) : rien à suivre
        float hardness = st.getDestroySpeed(level, pos);
        if (hardness < 0) return; // incassable (bedrock…)
        float hp = st.getBlock() instanceof DefenseBlock db
                ? (float) (DEFENSE_HP[db.kind().ordinal()] * hpMult)          // défenses : PV dédiés
                : st.is(com.wavesurvivor.registry.ModBlocks.KINGDOM_RAMPART.get())
                ? (float) (300 * hpMult)                                     // bloc de rempart
                : (float) (Math.min(400, 10 + hardness * 15) * hpMult);
        hp *= (float) bonusMult();
        float[] shared = {hp, hp};
        PLACED.put(pos.immutable(), shared);
        // Corps physique d'une défense : ses blocs de collision partagent ses PV (les frapper l'abîme)
        for (BlockPos c : KingdomDefenses.collidersOf(pos)) PLACED.put(c, shared);
        syncDefense(pos);
    }

    /** Segment de rempart (3 blocs) : une seule réserve de PV partagée — un bâtiment pour les Sapeurs / Béliers. */
    static void trackRampart(java.util.List<BlockPos> parts) {
        trackGroup(parts, RampartBlock.HP);
    }

    /** Règles du claim pour une construction (rempart) : hors zone interdit si « construction dans le claim » est actif. */
    static boolean canBuild(ServerPlayer p, BlockPos pos) {
        if (level == null || p.level() != level) return true; // hors partie Kingdom : construction libre
        if (!inside(pos) && buildOnlyInClaim && !p.isCreative()) {
            p.displayClientMessage(WSLang.c("kingdom.claim_outside"), true);
            return false;
        }
        return true;
    }

    /** Porte de rempart : ses 9 parties partagent la MÊME réserve de PV (frapper n'importe laquelle l'entame). */
    static void trackGroup(java.util.List<BlockPos> parts, float baseHp) {
        if (level == null) return;
        float hp = (float) (baseHp * hpMult * bonusMult());
        float[] shared = {hp, hp};
        for (BlockPos q : parts) PLACED.put(q.immutable(), shared);
    }

    // ─── Bâtisseur : +10 % de PV sur toutes les constructions tant qu'il est là ───

    public static final double BUILDER_HP = 1.10;
    /** Pierre de Fondation (relique) : +15 % de PV sur les constructions tant qu'un porteur est connecté. */
    public static final double FOUNDATION_HP = 1.15;
    private static boolean builderBonus = false;
    /** Niveau de la meilleure Pierre de Fondation portée (0 = aucune). */
    private static int foundationLevel = 0;

    /** Multiplicateur de PV courant des constructions (Bâtisseur × Pierre de Fondation +15 / +22 / +30 %). */
    private static double bonusMult() {
        double f = foundationLevel <= 0 ? 1.0 : 1.0 + (FOUNDATION_HP - 1.0) * com.wavesurvivor.item.RelicEffects.mult(foundationLevel);
        return (builderBonus ? BUILDER_HP : 1.0) * f;
    }

    public static void setFoundationLevel(int lvl) {
        if (level == null) { foundationLevel = 0; return; }
        if (lvl == foundationLevel) return;
        double before = bonusMult();
        foundationLevel = lvl;
        rescale(bonusMult() / before);
    }

    /** Applique un changement de multiplicateur à toutes les constructions suivies (PV max et PV actuels). */
    private static void rescale(double ratio) {
        float f = (float) ratio;
        java.util.Set<float[]> done = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Map.Entry<BlockPos, float[]> en : PLACED.entrySet()) {
            float[] hp = en.getValue();
            if (!done.add(hp)) continue;
            hp[1] *= f;
            hp[0] = Math.min(hp[1], hp[0] * f);
            syncDefense(en.getKey());
        }
    }

    /**
     * Fiole de bastion (Alchimiste) : toutes les constructions suivies à moins de {@code r} blocs regagnent
     * {@code fraction} de leurs PV max (une seule fois par construction, même si elle compte plusieurs blocs).
     * @return nombre de constructions soignées.
     */
    public static int healArea(BlockPos c, double r, float fraction) {
        if (level == null) return 0;
        double r2 = r * r;
        java.util.Set<float[]> done = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        int n = 0;
        for (Map.Entry<BlockPos, float[]> en : PLACED.entrySet()) {
            if (en.getKey().distSqr(c) > r2) continue;
            float[] hp = en.getValue();
            if (!done.add(hp) || hp[0] >= hp[1]) continue;
            hp[0] = Math.min(hp[1], hp[0] + hp[1] * fraction);
            syncDefense(en.getKey());
            n++;
        }
        return n;
    }

    /** Active / retire le bonus du Bâtisseur sur toutes les constructions suivies (PV max et actuels). */
    public static void setBuilderBonus(boolean on) {
        if (on == builderBonus || level == null) {
            if (level == null) builderBonus = false;
            return;
        }
        double before = bonusMult();
        builderBonus = on;
        rescale(bonusMult() / before);
    }

    static void untrack(BlockPos p) {
        if (PLACED.remove(p) != null && level != null) level.destroyBlockProgress(p.hashCode(), p, -1);
    }

    /** PV des défenses (ARCHER, MAGE, SHRINE, BARRACKS, COLLECTOR, WORKSHOP) avant multiplicateur ; +100 par niveau gagné. */
    private static final float[] DEFENSE_HP = {350f, 350f, 300f, 450f, 350f, 250f};

    /** Envoie les PV d'une défense à son bloc (barre affichée au-dessus côté client). */
    private static void syncDefense(BlockPos p) {
        BlockPos parent = KingdomDefenses.parentOf(p); // coup porté au corps invisible → barre de la défense
        if (parent != null) p = parent;
        float[] hp = PLACED.get(p);
        if (hp != null && level != null && level.getBlockEntity(p) instanceof DefenseBlockEntity be) be.setHp(hp[0], hp[1]);
    }

    /** PV {actuels, max} d'un bloc suivi (mur ou défense), ou null. */
    static float[] hpOf(BlockPos p) {
        return PLACED.get(p);
    }

    /** Sanctuaire « Rempart » : répare tous les murs abîmés dans un rayon (une réserve de PV partagée n'est soignée qu'une fois). */
    static void healArea(BlockPos c, int r, float amount) {
        if (level == null || PLACED.isEmpty()) return;
        java.util.Set<float[]> done = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Map.Entry<BlockPos, float[]> en : new java.util.ArrayList<>(PLACED.entrySet())) {
            float[] hp = en.getValue();
            if (hp[0] >= hp[1] - 0.5f || en.getKey().distSqr(c) > (double) r * r || !done.add(hp)) continue;
            heal(en.getKey(), amount);
        }
    }

    /** Répare un bloc suivi de {@code amount} PV (fissures et barre mises à jour). */
    static void heal(BlockPos p, float amount) {
        float[] hp = PLACED.get(p);
        if (hp == null || level == null) return;
        hp[0] = Math.min(hp[1], hp[0] + amount);
        int stage = (int) Math.max(0, Math.min(9, (1 - hp[0] / hp[1]) * 10));
        level.destroyBlockProgress(p.hashCode(), p, hp[0] >= hp[1] - 0.5f ? -1 : stage);
        syncDefense(p);
    }

    // ─── Tick (appelé par KingdomManager) ───

    static void tick(long now) {
        if (level == null) return;
        if (walls && !PLACED.isEmpty() && now % 20 == 0) siege();
        if (showBorder && now % 5 == 0) drawBorder(now);
        if (!PLACED.isEmpty() && now % 10 == 0) lookInfo();
    }

    /** Un joueur qui regarde un bloc suivi (mur, rempart, porte, défense) à 6 blocs voit ses PV en bas de l'écran. */
    private static void lookInfo() {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            var hr = p.pick(6.0, 0f, false);
            if (hr.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !(hr instanceof net.minecraft.world.phys.BlockHitResult bh)) continue;
            float[] hp = PLACED.get(bh.getBlockPos());
            if (hp == null) continue;
            BlockPos named = KingdomDefenses.parentOf(bh.getBlockPos()) != null ? KingdomDefenses.parentOf(bh.getBlockPos()) : bh.getBlockPos();
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("§7🧱 ")
                    .append(level.getBlockState(named).getBlock().getName())
                    .append(net.minecraft.network.chat.Component.literal(" §8— §f" + Math.round(hp[0]) + "§7/" + Math.round(hp[1]) + " "
                            + WSLang.t("kingdom.hp_unit"))), true);
        }
    }

    /** Les monstres près du claim frappent le mur posé par un joueur qui leur barre la route. */
    private static void siege() {
        // Tous les monstres hostiles près du royaume (boss, Gardiens et escortes compris)
        net.minecraft.world.phys.AABB zone = new net.minecraft.world.phys.AABB(center).inflate(radius + 4, 24, radius + 4);
        for (Mob m : level.getEntitiesOfClass(Mob.class, zone, TrapBlock::isHostile)) {
            if ("ram".equals(m.getPersistentData().getString(KingdomSiege.ROLE))) continue; // Béliers : gérés par leur charge (KingdomSiege.rams)
            Vec3 goal = m.getTarget() != null ? m.getTarget().position() : Vec3.atBottomCenterOf(center);
            Vec3 dir = goal.subtract(m.position()).multiply(1, 0, 1);
            if (dir.lengthSqr() < 0.01) continue;
            dir = dir.normalize();
            BlockPos front = BlockPos.containing(m.getX() + dir.x * (m.getBbWidth() * 0.5 + 0.8), m.getY() + 0.5, m.getZ() + dir.z * (m.getBbWidth() * 0.5 + 0.8));
            BlockPos hit = PLACED.containsKey(front) ? front : PLACED.containsKey(front.above()) ? front.above() : null;
            if (hit == null) continue;
            float[] hp = PLACED.get(hit);
            BlockState st = level.getBlockState(hit);
            if (st.isAir()) { PLACED.remove(hit); continue; }
            double atk = m.getAttribute(Attributes.ATTACK_DAMAGE) != null ? m.getAttributeValue(Attributes.ATTACK_DAMAGE) : 2;
            boolean ram = "ram".equals(m.getPersistentData().getString(KingdomSiege.ROLE));
            if (ram) atk *= 5; // Étape 5 : le bélier défonce les murs
            hp[0] -= (float) Math.max(1, atk);
            KingdomReport.onStructureDamage((float) Math.max(1, atk));
            m.swing(InteractionHand.MAIN_HAND);
            m.getLookControl().setLookAt(hit.getX() + 0.5, hit.getY() + 0.5, hit.getZ() + 0.5);
            level.playSound(null, hit, ram ? net.minecraft.sounds.SoundEvents.ANVIL_LAND : st.getSoundType().getHitSound(), SoundSource.BLOCKS, ram ? 0.6f : 1f, 0.8f);
            if (hp[0] <= 0) {
                KingdomReport.onStructureLost();
                level.destroyBlockProgress(hit.hashCode(), hit, -1);
                level.destroyBlock(hit, true);
                PLACED.remove(hit);
            } else {
                int stage = (int) Math.max(0, Math.min(9, (1 - hp[0] / hp[1]) * 10));
                level.destroyBlockProgress(hit.hashCode(), hit, stage);
                syncDefense(hit);
            }
        }
    }

    /** Contour du claim au sol (particules violettes, un point tous les 2 blocs). */
    /**
     * Bordure animée : un point par bloc sur 2 hauteurs (sol + hauteur des yeux), effet « guirlande » qui défile
     * (un point sur trois à chaque passage), et colonnes de lumière aux 4 coins.
     */
    private static void drawBorder(long now) {
        int cx = center.getX(), cz = center.getZ(), r = radius;
        int phase = (int) ((now / 5) % 3);
        for (int i = -r; i <= r; i++) {
            if (Math.floorMod(i + phase, 3) != 0) continue;
            dot(cx + i, cz - r);
            dot(cx + i, cz + r);
            dot(cx - r, cz + i);
            dot(cx + r, cz + i);
        }
        if ((now / 5) % 2 == 0) {
            for (int[] k : new int[][]{{-r, -r}, {r, -r}, {-r, r}, {r, r}}) {
                int x = cx + k[0], z = cz + k[1];
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, x + 0.5, y + 1.5, z + 0.5, 4, 0.05, 1.2, 0.05, 0.02);
            }
        }
    }

    private static final DustParticleOptions BORDER_HIGH = new DustParticleOptions(new Vector3f(0.95f, 0.75f, 1f), 1.0f);

    private static void drawBorderOld() {
        int cx = center.getX(), cz = center.getZ();
        for (int i = -radius; i <= radius; i += 2) {
            dot(cx + i, cz - radius);
            dot(cx + i, cz + radius);
            dot(cx - radius, cz + i);
            dot(cx + radius, cz + i);
        }
    }

    private static void dot(int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        level.sendParticles(BORDER, x + 0.5, y + 0.2, z + 0.5, 1, 0, 0.05, 0, 0);
        level.sendParticles(BORDER_HIGH, x + 0.5, y + 1.3, z + 0.5, 1, 0, 0.05, 0, 0);
    }

    // ─── Événements (enregistrés au démarrage du mod) ───

    public static class Events {

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onPlace(BlockEvent.EntityPlaceEvent e) {
            // Défenses : posables uniquement dans le claim, pendant une partie Kingdom
            if ((e.getPlacedBlock().getBlock() instanceof DefenseBlock || e.getPlacedBlock().getBlock() instanceof TrapBlock)
                    && e.getEntity() instanceof ServerPlayer dp) {
                // Claim désactivé : défenses et pièges posables partout (toujours seulement pendant une partie Kingdom)
                if (level == null || e.getLevel() != level || (buildOnlyInClaim && !inside(e.getPos()))) {
                    e.setCanceled(true);
                    dp.displayClientMessage(WSLang.c(level == null ? "kingdom.defense_only_kingdom" : "kingdom.claim_outside"), true);
                    return;
                }
                // (Emprise et espace libre : gérés par les blocs de collision des défenses, voir KingdomDefenses.register)
            }
            if (level == null || e.getLevel() != level || !(e.getEntity() instanceof ServerPlayer p)) return;
            BlockPos pos = e.getPos();
            if (!inside(pos)) {
                if (buildOnlyInClaim && !p.isCreative()) {
                    e.setCanceled(true);
                    p.displayClientMessage(WSLang.c("kingdom.claim_outside"), true);
                } else if (e.getPlacedBlock().getBlock() instanceof DefenseBlock) {
                    track(pos, e.getPlacedBlock()); // claim désactivé : une défense hors zone a quand même ses PV
                }
                return;
            }
            track(pos, e.getPlacedBlock());
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onBreak(BlockEvent.BreakEvent e) {
            // Les Portes construites en blocs sont incassables (on attaque la faille, pas la pierre)
            if (KingdomManager.isGateBlock(e.getPos())) {
                e.setCanceled(true);
                return;
            }
            if (level == null || e.getLevel() != level) return;
            BlockPos pos = e.getPos();
            if (!inside(pos) && buildOnlyInClaim && e.getPlayer() != null && !e.getPlayer().isCreative()) {
                e.setCanceled(true);
                e.getPlayer().displayClientMessage(WSLang.c("kingdom.claim_outside"), true);
                return;
            }
            if (PLACED.remove(pos) != null) level.destroyBlockProgress(pos.hashCode(), pos, -1);
        }
    }
}
