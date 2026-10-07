package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.altar.AltarDefense;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MODE KINGDOM — étape 4a : comportement des DÉFENSES.
 *  - TOUR D'ARCHERS : 1 flèche/s sur le monstre de horde le plus proche (24 blocs) ; niv.2 flèches enflammées ; niv.3 tir double.
 *  - TOUR DE MAGE : toutes les 2 s, Lenteur II sur les monstres (10 blocs) ; niv.2 rayon 14 ; niv.3 + Faiblesse.
 *  - SANCTUAIRE : soigne le Monolithe de 2 PV/s ; niv.2 4 PV/s ; niv.3 + Régénération aux joueurs proches (8 blocs).
 * Achat : boutique du Monolithe (monnaie de la horde). Amélioration : clic droit sur la défense avec la monnaie.
 * Les défenses ne visent QUE les monstres de la horde.
 */
public final class KingdomDefenses {

    private static final class Def {
        final BlockPos pos;
        final DefenseBlock.Kind kind;
        int level = 1;
        /** Spécialisation choisie au niveau 3 (« » tant qu'il n'y en a pas). */
        String variant = "";
        /** Baraquement : soldats en vie, prochaine apparition, remplissage initial terminé. */
        final List<UUID> soldiers = new ArrayList<>();
        /** Blocs de collision invisibles (corps physique de la défense). */
        final List<BlockPos> colliders = new ArrayList<>();
        long nextSpawn = 0;
        boolean filled = false;
        /** Fondations ou accès perdus : tick du début de l'instabilité (−1 = stable). */
        long unstableAt = -1;
        /** Baraquement : ordre des soldats (« hold » tenir la position, « monolith », « patrol », « follow » Commandant). */
        String order = "hold";
        /** Ordre « Suivez-moi » : le Commandant à escorter. */
        UUID follow = null;
        /** Ressources dépensées pour cette défense (achat + améliorations), pour le remboursement au démontage. */
        final java.util.EnumMap<KingdomTreasury.Res, Integer> spent = new java.util.EnumMap<>(KingdomTreasury.Res.class);

        Def(BlockPos pos, DefenseBlock.Kind kind) {
            this.pos = pos;
            this.kind = kind;
        }
    }

    /** Prix d'achat (ARCHER, MAGE, SHRINE, BARRACKS) et prix pour passer au niveau 2 puis 3. */
    public static final int[] BUY = {32, 40, 48, 56, 36, 44};
    private static final int[] UPGRADE = {0, 48, 64};

    private static final Map<BlockPos, Def> DEFS = new HashMap<>();
    /** « Tout réparer » : tick du 1er clic (devis annoncé), par joueur. */
    private static final Map<UUID, Long> REPAIR_CONFIRM = new HashMap<>();

    // ─── Fondations et accès : pas de tour sur le vide, sur un pilier ou hors d'atteinte ───

    /**
     * Problème de terrain pour une défense posée en {@code pos}, ou null si tout va bien :
     *  - « instable » : un bloc sous l'emprise n'est pas solide (vide, liquide, feuillage…) ;
     *  - « inaccessible » : moins de {@code minAccess} cases autour de l'emprise où un monstre peut se tenir
     *    (sol solide au même niveau à ±1 bloc, 2 blocs d'air au-dessus).
     */
    public static String foundationProblem(ServerLevel lvl, DefenseBlock.Kind kind, BlockPos pos, int minAccess) {
        java.util.Set<BlockPos> foot = new java.util.HashSet<>();
        foot.add(pos);
        for (BlockPos c : colliderPositions(kind, pos)) if (c.getY() == pos.getY()) foot.add(c);
        for (BlockPos f : foot) {
            BlockPos b = f.below();
            var s = lvl.getBlockState(b);
            if (s.getCollisionShape(lvl, b).isEmpty() || !s.getFluidState().isEmpty() || s.is(net.minecraft.tags.BlockTags.LEAVES)
                    || s.getBlock() instanceof DefenseBlock || s.getBlock() instanceof ColliderBlock) {
                return "kingdom.tower.unstable";
            }
        }
        java.util.Set<Long> access = new java.util.HashSet<>();
        for (BlockPos f : foot) {
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos n = f.relative(dir);
                if (foot.contains(n)) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    if (standable(lvl, n.above(dy))) { access.add(BlockPos.asLong(n.getX(), 0, n.getZ())); break; }
                }
            }
        }
        return access.size() < minAccess ? "kingdom.tower.unreachable" : null;
    }

    /** Un monstre peut-il se tenir debout ici (sol solide, 2 blocs libres) ? */
    private static boolean standable(ServerLevel lvl, BlockPos s) {
        BlockPos b = s.below();
        var bs = lvl.getBlockState(b);
        return !bs.getCollisionShape(lvl, b).isEmpty() && bs.getFluidState().isEmpty()
                && lvl.getBlockState(s).getCollisionShape(lvl, s).isEmpty() && lvl.getFluidState(s).isEmpty()
                && lvl.getBlockState(s.above()).getCollisionShape(lvl, s.above()).isEmpty();
    }

    /**
     * Surveillance (toutes les 2 s, chaque seconde une fois instable) : fondations creusées ou plus aucun accès →
     * la tour tremble 5 s (poussière, craquements, avertissement), puis s'effondre si rien n'est réparé.
     * @return vrai si la défense s'est effondrée
     */
    private static boolean checkStability(Def d, long now) {
        String why = foundationProblem(level, d.kind, d.pos, 1);
        if (why == null) {
            d.unstableAt = -1;
            return false;
        }
        double cx = d.pos.getX() + 0.5, cz = d.pos.getZ() + 0.5;
        if (d.unstableAt < 0) {
            d.unstableAt = now;
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(d.pos).inflate(32))) {
                p.displayClientMessage(WSLang.c("kingdom.tower.collapsing"), true);
            }
        }
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.FALLING_DUST,
                net.minecraft.world.level.block.Blocks.GRAVEL.defaultBlockState()), cx, d.pos.getY() + 3, cz, 20, 1.0, 2.0, 1.0, 0);
        level.playSound(null, d.pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.9f, 0.5f);
        if (now - d.unstableAt < 100) return false;
        // Effondrement : la défense tombe comme détruite par un monstre
        level.sendParticles(ParticleTypes.EXPLOSION, cx, d.pos.getY() + 1, cz, 3, 0.8, 0.8, 0.8, 0);
        level.playSound(null, d.pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.8f, 0.7f);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(d.pos).inflate(32))) {
            p.displayClientMessage(WSLang.c("kingdom.tower.collapsed"), true);
        }
        level.destroyBlock(d.pos, false);
        return true;
    }

    /** Sanctuaire « Rempart » à 16 blocs ou moins : les défenses et murs proches subissent −20 % de dégâts. */
    public static boolean bulwarkNear(BlockPos p) {
        for (Def d : DEFS.values()) {
            if (d.kind == DefenseBlock.Kind.SHRINE && "bulwark".equals(d.variant) && d.pos.distSqr(p) <= 16 * 16) return true;
        }
        return false;
    }

    /** Carte du royaume : chaque défense posée (position, type). */
    public static void forEachDefense(java.util.function.BiConsumer<BlockPos, DefenseBlock.Kind> out) {
        for (Def d : DEFS.values()) out.accept(d.pos, d.kind);
    }

    /** Carte du royaume : positions des pièges posés. */
    public static java.util.List<BlockPos> trapPositions() {
        return new java.util.ArrayList<>(TRAPS);
    }
    private static ServerLevel level;

    private KingdomDefenses() {}

    static void register(ServerLevel l, BlockPos pos, DefenseBlock.Kind kind, net.minecraft.world.entity.LivingEntity placer) {
        if (!KingdomManager.isActive()) return;
        level = l;
        // Corps physique : il faut de la place (au-dessus de la tour, ou 3×3×3 pour le baraquement)
        List<BlockPos> cols = colliderPositions(kind, pos);
        for (BlockPos c : cols) {
            net.minecraft.world.level.block.state.BlockState s = l.getBlockState(c);
            if (!s.isAir() && !s.canBeReplaced()) {
                if (placer instanceof ServerPlayer sp) {
                    sp.displayClientMessage(WSLang.c(kind == DefenseBlock.Kind.BARRACKS ? "kingdom.barracks_space" : "kingdom.defense_space"), true);
                }
                l.destroyBlock(pos, true); // pas de place : la défense est rendue
                return;
            }
        }
        Def d = new Def(pos.immutable(), kind);
        DEFS.put(d.pos, d);
        for (BlockPos c : cols) {
            l.setBlock(c, com.wavesurvivor.registry.ModBlocks.KINGDOM_COLLIDER.get().defaultBlockState(), 3);
            COLLIDERS.put(c.immutable(), d.pos);
            d.colliders.add(c.immutable());
        }
        // Registre sauvegardé : retirés au prochain démarrage si la partie est interrompue
        KingdomLeftovers.track(l, d.pos);
        KingdomLeftovers.track(l, d.colliders);
        l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 25, 0.4, 0.5, 0.4, 0.2);
        l.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6f, 1.3f);
    }

    // ─── Corps physique (blocs de collision invisibles) ───

    /** Bloc de collision → défense propriétaire. */
    private static final Map<BlockPos, BlockPos> COLLIDERS = new HashMap<>();

    /** Volume solide de chaque défense (en plus du socle). */
    private static List<BlockPos> colliderPositions(DefenseBlock.Kind k, BlockPos p) {
        List<BlockPos> out = new ArrayList<>();
        switch (k) {
            case ARCHER -> { // tour 2×2 (empreinte +x/+z), 8 blocs de haut
                for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) for (int dy = 0; dy <= 7; dy++) {
                    if (dx != 0 || dz != 0 || dy != 0) out.add(p.offset(dx, dy, dz));
                }
            }
            case MAGE -> { for (int y = 1; y <= 3; y++) out.add(p.above(y)); }
            case SHRINE, COLLECTOR -> { for (int y = 1; y <= 2; y++) out.add(p.above(y)); }
            case WORKSHOP -> out.add(p.east()); // atelier : 2 blocs de long (vers l'est), 1 de haut
            case BARRACKS -> { // bâtiment 6×4 (x de -1 à +4, z de -1 à +2), 5 blocs de haut
                for (int dx = -1; dx <= 4; dx++) for (int dz = -1; dz <= 2; dz++) for (int dy = 0; dy <= 4; dy++) {
                    if (dx != 0 || dz != 0 || dy != 0) out.add(p.offset(dx, dy, dz));
                }
            }
        }
        return out;
    }

    /** Défense propriétaire d'un bloc de collision (null sinon). */
    public static BlockPos parentOf(BlockPos pos) {
        return COLLIDERS.get(pos);
    }

    /** Blocs de collision d'une défense (vide si aucun). */
    static List<BlockPos> collidersOf(BlockPos parent) {
        Def d = DEFS.get(parent);
        return d == null ? List.of() : d.colliders;
    }

    /** Un bloc de collision a disparu (détruit par un monstre, un sapeur…) : toute la défense tombe. */
    static void colliderBroken(BlockPos pos) {
        BlockPos parent = COLLIDERS.remove(pos);
        if (parent == null || level == null) return;
        if (level.getBlockState(parent).getBlock() instanceof DefenseBlock) level.destroyBlock(parent, true);
    }

    /** Vrai si une défense active est enregistrée à cette position. */
    static boolean isDefense(BlockPos pos) { return DEFS.containsKey(pos); }

    /** Vrai si cette position appartient à une porte de rempart active. */
    static boolean isGatePart(BlockPos pos) {
        for (Gate g : GATES) if (g.parts.contains(pos)) return true;
        return false;
    }

    /** Vrai si un piège actif est enregistré à cette position. */
    static boolean isTrap(BlockPos pos) { return TRAPS.contains(pos); }

    /**
     * Socle de défense retiré. Défense ORPHELINE (partie interrompue, plus rien en mémoire) : ses blocs invisibles
     * sont retrouvés par leur forme (toujours la même autour du socle) et retirés aussi.
     */
    static void unregister(net.minecraft.world.level.Level l, BlockPos pos, DefenseBlock.Kind kind) {
        if (!DEFS.containsKey(pos) && l != null) {
            for (BlockPos c : colliderPositions(kind, pos)) {
                if (COLLIDERS.containsKey(c)) continue; // appartient à une autre défense active
                if (l.getBlockState(c).getBlock() instanceof ColliderBlock) l.removeBlock(c, false);
            }
        }
        unregister(pos);
    }

    static void unregister(BlockPos pos) {
        Def d = DEFS.remove(pos);
        if (d != null) {
            discardSoldiers(d);
            for (BlockPos c : d.colliders) {
                COLLIDERS.remove(c);   // retiré avant : pas de réaction en chaîne
                KingdomClaim.untrack(c);
                if (level != null && level.getBlockState(c).getBlock() instanceof ColliderBlock) level.removeBlock(c, false);
            }
        }
        KingdomClaim.untrack(pos);
    }

    /**
     * SAUVEGARDE DE PARTIE (Kingdom) : photographie des défenses (position, type, niveau, spécialisation, ordre,
     * part de PV), des pièges (position, bloc) et du nombre de portes de rempart.
     */
    static net.minecraft.nbt.CompoundTag saveState() {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag defs = new net.minecraft.nbt.ListTag();
        for (Def d : DEFS.values()) {
            net.minecraft.nbt.CompoundTag e = new net.minecraft.nbt.CompoundTag();
            e.putLong("pos", d.pos.asLong());
            e.putString("kind", d.kind.name());
            e.putInt("level", d.level);
            e.putString("variant", d.variant);
            e.putString("order", d.order);
            float[] hp = KingdomClaim.hpOf(d.pos);
            e.putFloat("hp", hp == null || hp[1] <= 0 ? 1f : Math.max(0.05f, hp[0] / hp[1]));
            defs.add(e);
        }
        t.put("defenses", defs);
        net.minecraft.nbt.ListTag traps = new net.minecraft.nbt.ListTag();
        if (level != null) {
            for (BlockPos p : TRAPS) {
                var st = level.getBlockState(p);
                if (!(st.getBlock() instanceof TrapBlock)) continue;
                net.minecraft.nbt.CompoundTag e = new net.minecraft.nbt.CompoundTag();
                e.putLong("pos", p.asLong());
                e.putString("block", net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(st.getBlock()).toString());
                traps.add(e);
            }
        }
        t.put("traps", traps);
        t.putInt("gates", GATES.size());
        return t;
    }

    /**
     * REPRISE DE PARTIE (Kingdom) : repose les défenses (bloc, corps, niveau, spécialisation, ordre, PV) et les pièges.
     * Les portes de rempart ne sont pas reconstruites : leur prix est rendu au trésor.
     */
    static void restoreState(ServerLevel l, net.minecraft.nbt.CompoundTag t) {
        level = l;
        net.minecraft.nbt.ListTag defs = t.getList("defenses", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < defs.size(); i++) {
            net.minecraft.nbt.CompoundTag e = defs.getCompound(i);
            BlockPos pos = BlockPos.of(e.getLong("pos"));
            DefenseBlock.Kind kind;
            try { kind = DefenseBlock.Kind.valueOf(e.getString("kind")); } catch (IllegalArgumentException ex) { continue; }
            net.minecraft.world.level.block.Block block = switch (kind) {
                case ARCHER -> com.wavesurvivor.registry.ModBlocks.KINGDOM_ARCHER_TOWER.get();
                case MAGE -> com.wavesurvivor.registry.ModBlocks.KINGDOM_MAGE_TOWER.get();
                case SHRINE -> com.wavesurvivor.registry.ModBlocks.KINGDOM_REPAIR_SHRINE.get();
                case BARRACKS -> com.wavesurvivor.registry.ModBlocks.KINGDOM_BARRACKS.get();
                case COLLECTOR -> com.wavesurvivor.registry.ModBlocks.KINGDOM_COLLECTOR.get();
                case WORKSHOP -> com.wavesurvivor.registry.ModBlocks.KINGDOM_WORKSHOP.get();
            };
            // Bloc déjà occupé par autre chose : on n'écrase pas (défense perdue, signalée dans le journal)
            var cur = l.getBlockState(pos);
            if (!cur.isAir() && !cur.canBeReplaced() && !(cur.getBlock() instanceof DefenseBlock)) {
                com.wavesurvivor.WaveSurvivorMod.LOGGER.warn("[Reprise] Défense {} non reposée en {} (emplacement occupé).", kind, pos);
                continue;
            }
            l.setBlock(pos, block.defaultBlockState(), 3);
            register(l, pos, kind, null);
            Def d = DEFS.get(pos);
            if (d == null) continue;
            int lvl = Math.max(1, Math.min(3, e.getInt("level")));
            KingdomClaim.track(pos, l.getBlockState(pos));
            if (lvl > 1) applyLevel(d, lvl, lvl >= 3 ? e.getString("variant") : "");
            d.order = e.getString("order").isEmpty() ? "hold" : e.getString("order");
            float[] hp = KingdomClaim.hpOf(pos);
            if (hp != null) hp[0] = Math.max(1f, hp[1] * Math.min(1f, e.getFloat("hp")));
        }
        net.minecraft.nbt.ListTag traps = t.getList("traps", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < traps.size(); i++) {
            net.minecraft.nbt.CompoundTag e = traps.getCompound(i);
            BlockPos pos = BlockPos.of(e.getLong("pos"));
            var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(e.getString("block")));
            if (!(block instanceof TrapBlock) || !l.getBlockState(pos).canBeReplaced()) continue;
            l.setBlock(pos, block.defaultBlockState(), 3);
            registerTrap(l, pos);
        }
        int gates = t.getInt("gates");
        if (gates > 0 && KingdomTreasury.active()) KingdomTreasury.add(KingdomTreasury.Res.MONEY, gates * GATE_COST);
    }

    static void clear() {
        // Fin de partie : soldats renvoyés et défenses retirées du monde (rien ne reste après la horde)
        if (level != null) {
            for (Def d : new ArrayList<>(DEFS.values())) {
                discardSoldiers(d);
                if (level.getBlockState(d.pos).getBlock() instanceof DefenseBlock) {
                    level.sendParticles(ParticleTypes.POOF, d.pos.getX() + 0.5, d.pos.getY() + 0.5, d.pos.getZ() + 0.5, 15, 0.4, 0.4, 0.4, 0.02);
                    level.removeBlock(d.pos, false);
                }
            }
            // Portes de rempart et pièges non utilisés : retirés aussi
            for (Gate g : new ArrayList<>(GATES)) {
                for (BlockPos q : g.parts) if (level.getBlockState(q).getBlock() instanceof GatePartBlock) level.removeBlock(q, false);
            }
            for (BlockPos t : new ArrayList<>(TRAPS)) {
                if (level.getBlockState(t).getBlock() instanceof TrapBlock) level.removeBlock(t, false);
            }
        }
        KingdomLeftovers.forgetAll(level);
        GATES.clear();
        TRAPS.clear();
        COLLIDERS.clear();
        DEFS.clear();
        level = null;
    }

    private static void discardSoldiers(Def d) {
        if (level == null) return;
        for (UUID id : d.soldiers) {
            Entity e = level.getEntity(id);
            if (e != null) {
                level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1, e.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                e.discard();
            }
        }
        d.soldiers.clear();
    }

    // ─── Place au sol du baraquement (bâtiment 3×3×3) ───

    /** Le baraquement exige un espace 3×3 (sur 3 de haut) dégagé autour de lui. */
    static boolean barracksAreaClear(ServerLevel l, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 2; dy++) {
            if (dx == 0 && dz == 0 && dy == 0) continue; // le socle lui-même
            net.minecraft.world.level.block.state.BlockState s = l.getBlockState(pos.offset(dx, dy, dz));
            if (!s.isAir() && !s.canBeReplaced()) return false;
        }
        return true;
    }

    /** Vrai si cette position est dans l'emprise (3×3×3) d'un baraquement déjà posé. */
    static boolean insideBarracks(BlockPos pos) {
        for (Def d : DEFS.values()) {
            if (d.kind != DefenseBlock.Kind.BARRACKS || d.pos.equals(pos)) continue;
            int dx = pos.getX() - d.pos.getX(), dy = pos.getY() - d.pos.getY(), dz = pos.getZ() - d.pos.getZ();
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && dy >= 0 && dy <= 2) return true;
        }
        return false;
    }

    private static ItemStack itemFor(DefenseBlock.Kind k) {
        return switch (k) {
            case ARCHER -> new ItemStack(ModItems.KINGDOM_ARCHER_TOWER_ITEM.get());
            case MAGE -> new ItemStack(ModItems.KINGDOM_MAGE_TOWER_ITEM.get());
            case SHRINE -> new ItemStack(ModItems.KINGDOM_REPAIR_SHRINE_ITEM.get());
            case BARRACKS -> new ItemStack(ModItems.KINGDOM_BARRACKS_ITEM.get());
            case COLLECTOR -> new ItemStack(ModItems.KINGDOM_COLLECTOR_ITEM.get());
            case WORKSHOP -> new ItemStack(ModItems.KINGDOM_WORKSHOP_ITEM.get());
        };
    }

    // ─── Achat (boutique du Monolithe) ───

    public static void buy(ServerPlayer p, int type) {
        if (!KingdomManager.isActive()) {
            p.displayClientMessage(WSLang.c("kingdom.defense_only_kingdom"), true);
            return;
        }
        int cost;
        ItemStack item;
        if (type >= 10 && type <= 15) {                       // défenses (15 = Atelier de Réparation)
            cost = BUY[type - 10];
            item = itemFor(DefenseBlock.Kind.values()[type - 10]);
        } else if (type == 20) {                              // blocs de rempart
            cost = RAMPART_COST;
            item = new ItemStack(ModItems.KINGDOM_RAMPART_ITEM.get(), RAMPART_COUNT);
        } else if (type == 21) {                              // porte de rempart
            cost = GATE_COST;
            item = new ItemStack(ModItems.KINGDOM_GATE_ITEM.get());
        } else if (type >= 30 && type <= 34) {                // pièges
            cost = TRAP_COST[type - 30];
            item = new ItemStack(trapItem(type - 30));
        } else if (type == 22) {                              // charge de démolition (Bâtisseur, 1 par vague)
            if (!KingdomRoles.has(p, KingdomRoles.Role.BUILDER)) {
                p.displayClientMessage(WSLang.c("kingdom.charge.builder_only"), true);
                return;
            }
            if (KingdomDemolition.boughtThisWave()) {
                p.displayClientMessage(WSLang.c("kingdom.charge.one_per_wave"), true);
                return;
            }
            cost = CHARGE_COST;
            item = new ItemStack(ModItems.DEMOLITION_CHARGE.get());
        } else if (type == 23) {                              // Boussole du Royaume (réglable depuis la carte)
            cost = COMPASS_COST;
            item = new ItemStack(ModItems.KINGDOM_COMPASS.get());
        } else {
            return;
        }
        // Bâtisseur présent : défenses, remparts, portes et pièges -20 %
        if (type != 22 && type != 23) cost = KingdomRoles.builderPrice(cost, KingdomRoles.present(KingdomRoles.Role.BUILDER));
        // Héritage — Seigneur 2 : défenses et remparts −15 %
        if (type != 22 && type != 23 && com.wavesurvivor.horde.renaissance.Heritage.anyone(p.server, com.wavesurvivor.horde.renaissance.Heritage.Branch.LORD, 2)) {
            cost = Math.max(1, (int) Math.round(cost * 0.85));
        }
        if (!AltarDefense.trySpend(p, cost)) return;
        if (type == 22) KingdomDemolition.markBought();
        String name = item.getHoverName().getString();
        if (!p.getInventory().add(item)) p.drop(item, false);
        p.displayClientMessage(WSLang.c("kingdom.defense_bought", name), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1f, 1f);
    }

    /** Prix : 16 blocs de rempart, une porte de rempart, les 5 pièges (PICS, FEU, GIVRE, EXPLOSIF, COLLET). */
    /** Rempart : 8 segments (3 blocs de haut chacun) pour 10 — peu cher, on en pose beaucoup. */
    public static final int RAMPART_COST = 10, RAMPART_COUNT = 8, GATE_COST = 30;
    /** Charge de démolition du Bâtisseur. */
    public static final int CHARGE_COST = 20;
    /** Boussole du Royaume : prix fixe. */
    public static final int COMPASS_COST = 64;
    public static final int[] TRAP_COST = {10, 12, 14, 16, 12};
    private static final float GATE_HP = 500f;

    private static net.minecraft.world.item.Item trapItem(int i) {
        return switch (i) {
            case 0 -> ModItems.KINGDOM_TRAP_SPIKES_ITEM.get();
            case 1 -> ModItems.KINGDOM_TRAP_FIRE_ITEM.get();
            case 2 -> ModItems.KINGDOM_TRAP_FROST_ITEM.get();
            case 3 -> ModItems.KINGDOM_TRAP_EXPLOSIVE_ITEM.get();
            default -> ModItems.KINGDOM_TRAP_SNARE_ITEM.get();
        };
    }

    // ─── Portes de rempart ───

    private static final class Gate {
        final List<BlockPos> parts;
        final BlockPos base;
        boolean open;

        Gate(BlockPos base, List<BlockPos> parts) {
            this.base = base;
            this.parts = parts;
        }
    }

    private static final List<Gate> GATES = new ArrayList<>();
    private static final java.util.Set<BlockPos> TRAPS = new java.util.HashSet<>();

    static void registerGate(ServerLevel l, BlockPos base, List<BlockPos> parts) {
        level = l;
        List<BlockPos> copy = new ArrayList<>();
        for (BlockPos q : parts) copy.add(q.immutable());
        GATES.add(new Gate(base.immutable(), copy));
        KingdomClaim.trackGroup(copy, GATE_HP);
        KingdomLeftovers.track(l, copy);
    }

    /** Une partie de porte a disparu (cassée, détruite) : toute la porte s'effondre. */
    static void gateBroken(BlockPos pos) {
        Gate g = null;
        for (Gate x : GATES) if (x.parts.contains(pos)) { g = x; break; }
        if (g == null || level == null) return;
        GATES.remove(g);
        for (BlockPos q : g.parts) {
            KingdomClaim.untrack(q);
            if (level.getBlockState(q).getBlock() instanceof GatePartBlock) level.removeBlock(q, false);
        }
        level.sendParticles(ParticleTypes.POOF, g.base.getX() + 0.5, g.base.getY() + 1.5, g.base.getZ() + 0.5, 30, 1, 1, 1, 0.02);
        level.playSound(null, g.base, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.2f, 0.8f);
    }

    /** Fermeture de sécurité : dès qu'un monstre approche (4 blocs) d'une porte ouverte, elle se referme. */
    private static void tickGates() {
        for (Gate g : new ArrayList<>(GATES)) {
            if (g.open && foeNear(g, 4)) setGate(g, false);
        }
    }

    private static boolean foeNear(Gate g, double r) {
        Vec3 c = Vec3.atCenterOf(g.base.above());
        return !level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(r, 2, r), TrapBlock::isHostile).isEmpty();
    }

    /** Ouvre / ferme : seule la colonne centrale s'ouvre (passage de 1 × 3), les montants latéraux restent en place. */
    private static void setGate(Gate g, boolean open) {
        g.open = open;
        for (BlockPos q : g.parts) {
            if (q.getX() != g.base.getX() || q.getZ() != g.base.getZ()) continue; // montants : toujours fermés
            var st = level.getBlockState(q);
            if (st.getBlock() instanceof GatePartBlock) level.setBlock(q, st.setValue(GatePartBlock.OPEN, open), 3);
        }
        level.playSound(null, g.base, open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 1.2f, 0.7f);
        level.sendParticles(ParticleTypes.CLOUD, g.base.getX() + 0.5, g.base.getY() + 1.5, g.base.getZ() + 0.5, 8, 0.2, 0.8, 0.2, 0.01);
    }

    /** Clic droit sur la porte : ouvrir / fermer (ouverture refusée si un monstre est à moins de 5 blocs). */
    static void toggleGate(ServerPlayer p, BlockPos pos) {
        Gate g = null;
        for (Gate x : GATES) if (x.parts.contains(pos)) { g = x; break; }
        if (g == null || level == null) return;
        if (!g.open && foeNear(g, 5)) {
            p.displayClientMessage(WSLang.c("kingdom.gate_blocked"), true);
            return;
        }
        setGate(g, !g.open);
        p.displayClientMessage(WSLang.c(g.open ? "kingdom.gate_opened" : "kingdom.gate_closed"), true);
    }

    // ─── Pièges ───

    static void registerTrap(ServerLevel l, BlockPos pos) {
        if (!KingdomManager.isActive()) return;
        level = l;
        TRAPS.add(pos.immutable());
        KingdomLeftovers.track(l, pos.immutable());
    }

    static void unregisterTrap(BlockPos pos) {
        TRAPS.remove(pos);
    }

    // ─── Réparation (Maj + clic droit) ───

    /** Coût d'une réparation (25 % des PV max). */
    public static final int REPAIR_COST = 4;

    static void repair(ServerPlayer p, BlockPos pos) {
        float[] hp = KingdomClaim.hpOf(pos);
        if (hp == null || !DEFS.containsKey(pos)) {
            p.displayClientMessage(WSLang.c("kingdom.defense_inactive"), true);
            return;
        }
        if (hp[0] >= hp[1] - 0.5f) {
            p.displayClientMessage(WSLang.c("kingdom.defense_full"), true);
            return;
        }
        // Contrepartie du Bâtisseur : réparations +25 %
        int repairCost = KingdomRoles.present(KingdomRoles.Role.BUILDER) ? (int) Math.ceil(REPAIR_COST * 1.25) : REPAIR_COST;
        // Pierre de Fondation (relique) : réparations −25 % (−37 / −50 % aux niveaux II / III)
        int fondation = com.wavesurvivor.item.RelicEffects.bestLevel(p.server, com.wavesurvivor.registry.ModItems.PIERRE_FONDATION.get());
        if (fondation > 0) {
            repairCost = Math.max(1, (int) Math.round(repairCost * (1.0 - 0.25 * com.wavesurvivor.item.RelicEffects.mult(fondation))));
        }
        if (!AltarDefense.trySpend(p, repairCost)) return;
        KingdomClaim.heal(pos, hp[1] * 0.25f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 15, 0.5, 0.6, 0.5, 0);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
        p.displayClientMessage(WSLang.c("kingdom.defense_repaired", Math.round(hp[0]), Math.round(hp[1])), true);
    }

    // ─── Amélioration (clic droit) ───

    // ─── Fiche de défense (clic droit) ───

    /**
     * Mairie › « Tout réparer » : répare toutes les défenses abîmées par tranches de 25 % (même prix qu'une réparation
     * à la main), les plus endommagées d'abord ; s'arrête si le trésor ne suffit plus.
     */
    public static void repairAll(ServerPlayer p) {
        if (level == null) return;
        List<Def> hurt = new ArrayList<>();
        for (Def d : DEFS.values()) {
            float[] hp = KingdomClaim.hpOf(d.pos);
            if (hp != null && hp[0] < hp[1] - 0.5f) hurt.add(d);
        }
        if (hurt.isEmpty()) {
            p.displayClientMessage(WSLang.c("kingdom.repair_all_none"), true);
            return;
        }
        // 1er clic : annonce du coût total ; 2e clic dans les 10 s : réparation
        long now = level.getGameTime();
        Long asked = REPAIR_CONFIRM.get(p.getUUID());
        if (asked == null || now - asked > 200) {
            int chunks = 0;
            for (Def d : hurt) {
                float[] hp = KingdomClaim.hpOf(d.pos);
                chunks += Math.min(4, (int) Math.ceil((hp[1] - hp[0]) / Math.max(1f, hp[1] * 0.25f)));
            }
            REPAIR_CONFIRM.put(p.getUUID(), now);
            p.sendSystemMessage(WSLang.c("kingdom.repair_all_quote", hurt.size(), chunks * repairCostFor(p)));
            return;
        }
        REPAIR_CONFIRM.remove(p.getUUID());
        hurt.sort((a, b) -> {
            float[] ha = KingdomClaim.hpOf(a.pos), hb = KingdomClaim.hpOf(b.pos);
            return Float.compare(ha[0] / Math.max(1f, ha[1]), hb[0] / Math.max(1f, hb[1]));
        });
        int unit = repairCostFor(p), spent = 0, fixed = 0;
        boolean broke = false;
        for (Def d : hurt) {
            float[] hp = KingdomClaim.hpOf(d.pos);
            boolean touched = false;
            int chunks = 0;
            while (hp != null && hp[0] < hp[1] - 0.5f && chunks++ < 4) {
                if (!AltarDefense.trySpend(p, unit)) { broke = true; break; }
                spent += unit;
                KingdomClaim.heal(d.pos, hp[1] * 0.25f);
                touched = true;
                hp = KingdomClaim.hpOf(d.pos);
            }
            if (touched) {
                fixed++;
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, d.pos.getX() + 0.5, d.pos.getY() + 1.2, d.pos.getZ() + 0.5, 15, 0.5, 0.6, 0.5, 0);
            }
            if (broke) break;
        }
        if (fixed > 0) level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 1.3f);
        p.sendSystemMessage(WSLang.c(broke ? "kingdom.repair_all_partial" : "kingdom.repair_all_done", fixed, hurt.size(), spent));
    }

    /** Coût d'une réparation de 25 % pour ce joueur (Bâtisseur, Pierre de Fondation). */
    static int repairCostFor(ServerPlayer p) {
        int c = KingdomRoles.present(KingdomRoles.Role.BUILDER) ? (int) Math.ceil(REPAIR_COST * 1.25) : REPAIR_COST;
        int f = com.wavesurvivor.item.RelicEffects.bestLevel(p.server, com.wavesurvivor.registry.ModItems.PIERRE_FONDATION.get());
        if (f > 0) c = Math.max(1, (int) Math.round(c * (1.0 - 0.25 * com.wavesurvivor.item.RelicEffects.mult(f))));
        return c;
    }

    /** Remboursement au démontage : 50 % de l'achat et des améliorations, par ressource. */
    private static java.util.EnumMap<KingdomTreasury.Res, Integer> refundOf(Def d) {
        java.util.EnumMap<KingdomTreasury.Res, Integer> r = new java.util.EnumMap<>(KingdomTreasury.Res.class);
        r.merge(KingdomTreasury.Res.MONEY, BUY[Math.min(BUY.length - 1, d.kind.ordinal())], Integer::sum);
        if (d.level >= 2) for (KingdomCosts.Cost c : KingdomCosts.upgradeToLevel2(d.kind)) r.merge(c.res(), c.count(), Integer::sum);
        if (d.level >= 3 && !d.variant.isEmpty()) for (KingdomCosts.Cost c : KingdomCosts.variantCost(d.kind, d.variant)) r.merge(c.res(), c.count(), Integer::sum);
        r.replaceAll((k, v) -> v / 2);
        return r;
    }

    private static List<com.wavesurvivor.network.DefenseSheetPackets.Cost> sheetCosts(ServerPlayer p, List<KingdomCosts.Cost> l) {
        net.minecraft.world.item.Item cur = AltarDefense.currencyItem();
        List<com.wavesurvivor.network.DefenseSheetPackets.Cost> out = new ArrayList<>();
        for (KingdomCosts.Cost c : l) out.add(new com.wavesurvivor.network.DefenseSheetPackets.Cost(c.res().ordinal(), c.count(), KingdomCosts.have(p, c, cur)));
        return out;
    }

    /** Ouvre (ou rafraîchit) la fiche de la défense pour ce joueur. */
    public static void sendSheet(ServerPlayer p, BlockPos pos) {
        Def d = DEFS.get(pos);
        if (d == null || level == null) {
            p.displayClientMessage(WSLang.c("kingdom.defense_inactive"), true);
            return;
        }
        float[] hp = KingdomClaim.hpOf(pos);
        List<com.wavesurvivor.network.DefenseSheetPackets.Cost> next = d.level == 1 ? sheetCosts(p, KingdomCosts.upgradeToLevel2(d.kind)) : List.of();
        List<com.wavesurvivor.network.DefenseSheetPackets.Variant> vs = new ArrayList<>();
        if (d.level == 2) {
            for (String v : KingdomCosts.variants(d.kind)) {
                vs.add(new com.wavesurvivor.network.DefenseSheetPackets.Variant(v, sheetCosts(p, KingdomCosts.variantCost(d.kind, v))));
            }
        }
        int refund = refundOf(d).values().stream().mapToInt(Integer::intValue).sum();
        int max = 2 + d.level + ("paladins".equals(d.variant) ? 1 : 0) + (KingdomRoles.present(KingdomRoles.Role.COMMANDER) ? 1 : 0);
        com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.wavesurvivor.network.DefenseSheetPackets.Sheet(pos, d.kind.ordinal(), d.level, d.variant,
                        hp == null ? 0 : hp[0], hp == null ? 0 : hp[1], KingdomTownHall.tier(), repairCostFor(p), refund,
                        next, vs, d.order, d.soldiers.size(), d.kind == DefenseBlock.Kind.BARRACKS ? max : 0));
    }

    /** Action choisie dans la fiche (revalidée côté serveur), puis fiche rafraîchie. */
    public static void sheetAction(ServerPlayer p, BlockPos pos, String action, String arg) {
        Def d = DEFS.get(pos);
        if (d == null || level == null || p.level() != level || p.distanceToSqr(Vec3.atCenterOf(pos)) > 12 * 12) return;
        switch (action) {
            case "upgrade" -> upgrade(p, pos);
            case "variant" -> chooseVariant(p, pos, arg);
            case "repair" -> repair(p, pos);
            case "order" -> {
                if (d.kind == DefenseBlock.Kind.BARRACKS && "follow".equals(arg)) {
                    // Ordre réservé au Commandant : les soldats l'escortent
                    if (!KingdomRoles.has(p, KingdomRoles.Role.COMMANDER)) {
                        p.displayClientMessage(WSLang.c("kingdom.order_commander_only"), true);
                    } else {
                        d.order = "follow";
                        d.follow = p.getUUID();
                        p.displayClientMessage(WSLang.c("kingdom.order_set", WSLang.t("kingdom.order.follow")), true);
                    }
                } else if (d.kind == DefenseBlock.Kind.BARRACKS && ("hold".equals(arg) || "monolith".equals(arg) || "patrol".equals(arg))) {
                    d.order = arg;
                    d.follow = null;
                    p.displayClientMessage(WSLang.c("kingdom.order_set", WSLang.t("kingdom.order." + arg)), true);
                }
            }
            case "open" -> { DefenseBlock.openStorage(p, pos); return; }
            case "demolish" -> {
                var refund = refundOf(d);
                if (KingdomTreasury.active()) refund.forEach((res, n) -> { if (n > 0) KingdomTreasury.add(res, n); });
                int total = refund.values().stream().mapToInt(Integer::intValue).sum();
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.8, 0.8, 0.8, 0.02);
                level.destroyBlock(pos, false);
                p.displayClientMessage(WSLang.c("kingdom.demolished", total), true);
                p.closeContainer();
                return;
            }
            default -> { return; }
        }
        sendSheet(p, pos);
    }

    // ─── Amélioration (clic droit) ───

    static void upgrade(ServerPlayer p, BlockPos pos) {
        Def d = DEFS.get(pos);
        if (d == null) {
            p.displayClientMessage(WSLang.c("kingdom.defense_inactive"), true);
            return;
        }
        if (d.level >= 3) {
            p.displayClientMessage(WSLang.c("kingdom.defense_max"), true);
            return;
        }
        net.minecraft.world.item.Item cur = AltarDefense.currencyItem();
        if (d.level == 1) {
            // Niveau 2 : ressources cohérentes avec la défense ; Mairie niveau 2 requise
            if (KingdomTownHall.tier() < 2) {
                p.displayClientMessage(WSLang.c("kingdom.need_town", 2), true);
                return;
            }
            List<KingdomCosts.Cost> cost = KingdomCosts.upgradeToLevel2(d.kind);
            if (!KingdomCosts.canAfford(p, cost, cur)) {
                p.sendSystemMessage(WSLang.c("kingdom.need").copy().append(KingdomCosts.missing(p, cost, cur)));
                return;
            }
            KingdomCosts.take(p, cost, cur);
            applyLevel(d, 2, "");
            p.displayClientMessage(WSLang.c("kingdom.defense_upgraded", d.level), true);
            return;
        }
        // Niveau 3 : choix d'une spécialisation (Mairie niveau 3 requise) — depuis la fiche de la défense
        if (KingdomTownHall.tier() < 3) {
            p.displayClientMessage(WSLang.c("kingdom.need_town", 3), true);
            return;
        }
        sendSheet(p, pos);
    }

    /** Propose les spécialisations dans le chat : boutons cliquables (survol = effet + coût). */
    private static void offerVariants(ServerPlayer p, Def d, net.minecraft.world.item.Item cur) {
        p.sendSystemMessage(WSLang.c("kingdom.variant_choose"));
        net.minecraft.network.chat.MutableComponent line = net.minecraft.network.chat.Component.literal("  ");
        for (String v : KingdomCosts.variants(d.kind)) {
            net.minecraft.network.chat.MutableComponent hover = WSLang.c("kingdom.variant.desc." + v).copy()
                    .append(net.minecraft.network.chat.Component.literal("\n\n")).append(WSLang.c("kingdom.cost"));
            for (KingdomCosts.Cost c : KingdomCosts.variantCost(d.kind, v)) {
                int have = KingdomCosts.have(p, c, cur);
                hover.append(net.minecraft.network.chat.Component.literal("\n " + (have >= c.count() ? "§a" : "§c") + c.count() + "× §f"))
                        .append(c.isCurrency() ? new ItemStack(cur).getHoverName() : c.label());
            }
            String cmd = "/wavesurvivor kvariant " + d.pos.getX() + " " + d.pos.getY() + " " + d.pos.getZ() + " " + v;
            line.append(net.minecraft.network.chat.Component.literal("§e§l[" + WSLang.t("kingdom.variant." + v) + "]").withStyle(st -> st
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, cmd))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, hover))));
            line.append(net.minecraft.network.chat.Component.literal("  "));
        }
        p.sendSystemMessage(line);
    }

    /** Clic sur une spécialisation (commande /wavesurvivor kvariant x y z v). */
    public static void chooseVariant(ServerPlayer p, BlockPos pos, String v) {
        Def d = DEFS.get(pos);
        if (d == null || level == null || p.distanceToSqr(Vec3.atCenterOf(pos)) > 12 * 12) {
            p.displayClientMessage(WSLang.c("kingdom.defense_inactive"), true);
            return;
        }
        if (d.level != 2 || !java.util.Arrays.asList(KingdomCosts.variants(d.kind)).contains(v)) return;
        if (KingdomTownHall.tier() < 3) {
            p.displayClientMessage(WSLang.c("kingdom.need_town", 3), true);
            return;
        }
        net.minecraft.world.item.Item cur = AltarDefense.currencyItem();
        List<KingdomCosts.Cost> cost = KingdomCosts.variantCost(d.kind, v);
        if (!KingdomCosts.canAfford(p, cost, cur)) {
            p.sendSystemMessage(WSLang.c("kingdom.need").copy().append(KingdomCosts.missing(p, cost, cur)));
            return;
        }
        KingdomCosts.take(p, cost, cur);
        applyLevel(d, 3, v);
        com.wavesurvivor.network.EventFeedPacket.toPlayer(p, net.minecraft.network.chat.Component.literal(WSLang.t("kingdom.variant_done", WSLang.t("kingdom.variant." + v)).replaceAll("§.", "")),
                "minecraft:nether_star", com.wavesurvivor.network.EventFeedPacket.KINGDOM, false);
    }

    /** Passe une défense au niveau donné (et sa spécialisation) : rendu, soldats, effets visuels. */
    private static void applyLevel(Def d, int lvl, String variant) {
        BlockPos pos = d.pos;
        int gained = lvl - d.level;
        d.level = lvl;
        d.variant = variant == null ? "" : variant;
        // +100 PV max par niveau gagné (et soignée d'autant)
        if (gained > 0) KingdomClaim.addDefenseMaxHp(pos, 100f * gained);
        if (level.getBlockEntity(pos) instanceof DefenseBlockEntity be) {
            be.setDefLevel(d.level);   // rendu 3D mis à jour
            be.setVariant(d.variant);
        }
        // Baraquement : les soldats déjà présents passent aussitôt aux stats du nouveau niveau (et sont soignés)
        if (d.kind == DefenseBlock.Kind.BARRACKS) {
            for (UUID id : d.soldiers) {
                if (!(level.getEntity(id) instanceof com.wavesurvivor.entity.KingdomSoldier s)) continue;
                applySoldierStats(s, d);
                s.setHealth(s.getMaxHealth());
            }
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.2);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    /** Stats d'un soldat selon le niveau et la spécialisation du baraquement. */
    private static void applySoldierStats(com.wavesurvivor.entity.KingdomSoldier s, Def d) {
        double hp = 16 + 8 * d.level, atk = 4 + d.level, armor = 0, speed = 0.32;
        switch (d.variant) {
            case "guards" -> { hp *= 2.0; armor = 8; }
            case "berserkers" -> { atk *= 1.5; speed = 0.38; }
            default -> { }
        }
        var a = s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (a != null) a.setBaseValue(hp);
        a = s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (a != null) a.setBaseValue(atk);
        a = s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (a != null) a.setBaseValue(armor);
        a = s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (a != null) a.setBaseValue(speed);
    }

    private static void upgradeLegacyUnused(ServerPlayer p, BlockPos pos) {
        Def d = DEFS.get(pos);
        if (d == null) return;
        int cost = UPGRADE[d.level];
        if (!AltarDefense.trySpend(p, cost)) return;
        d.level++;
        if (level.getBlockEntity(pos) instanceof DefenseBlockEntity be) be.setDefLevel(d.level); // rendu 3D mis à jour
        // Baraquement : les soldats déjà présents passent aussitôt aux stats du nouveau niveau (et sont soignés)
        if (d.kind == DefenseBlock.Kind.BARRACKS) {
            for (UUID id : d.soldiers) {
                if (!(level.getEntity(id) instanceof com.wavesurvivor.entity.KingdomSoldier s)) continue;
                var maxHp = s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (maxHp != null) maxHp.setBaseValue(16 + 8 * d.level);
                var atk = s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (atk != null) atk.setBaseValue(4 + d.level);
                s.setHealth(s.getMaxHealth());
            }
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.2);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 1.2f);
        p.displayClientMessage(WSLang.c("kingdom.defense_upgraded", d.level), true);
    }

    // ─── Tick (appelé par KingdomManager) ───

    static void tick(long now) {
        if (level == null) return;
        if (now % 5 == 0 && !GATES.isEmpty()) tickGates();
        if (DEFS.isEmpty()) return;
        boolean second = now % 20 == 0, twoSeconds = now % 40 == 0;
        // Archers de Feu : une 2e salve à chaque demi-seconde (2 salves de 3 flèches par seconde)
        if (now % 20 == 10) {
            for (Def d : new ArrayList<>(DEFS.values())) {
                if (d.kind == DefenseBlock.Kind.ARCHER && "fire".equals(d.variant)
                        && level.getBlockState(d.pos).getBlock() instanceof DefenseBlock) archer(d);
            }
        }
        if (!second) return;
        for (Def d : new ArrayList<>(DEFS.values())) {
            if (!(level.getBlockState(d.pos).getBlock() instanceof DefenseBlock)) { unregister(d.pos); continue; }
            switch (d.kind) {
                case ARCHER -> archer(d);
                case MAGE -> { if (twoSeconds) mage(d); }
                case SHRINE -> shrine(d);
                case BARRACKS -> barracks(d, now);
                case COLLECTOR -> collector(d);
                case WORKSHOP -> KingdomWorkshop.tick(level, d.pos, d.level, d.variant, now);
            }
            // Fondations creusées ou accès supprimé : la tour tremble 5 s puis s'effondre
            if (twoSeconds || d.unstableAt >= 0) {
                if (checkStability(d, now)) continue;
            }
            if (twoSeconds) {
                // Petite lueur : une particule par niveau
                level.sendParticles(ParticleTypes.END_ROD, d.pos.getX() + 0.5, d.pos.getY() + 1.3, d.pos.getZ() + 0.5, d.level, 0.25, 0.2, 0.25, 0.01);
            }
        }
    }

    /**
     * Monstres hostiles vivants à moins de {@code r} blocs, du plus proche au plus lointain.
     * Inclut les boss, les Gardiens et l'escorte du Catalyseur (qui ne comptent pas dans la vague).
     */
    /**
     * Cibles d'une tour : monstres à portée et VISIBLES depuis son sommet (ligne de tir dégagée, ni mur ni rempart),
     * classés par menace (score le plus bas d'abord) :
     *   Porte-étendard −40 · autres unités de siège (bélier, sapeur, grimpeur) −30 · au pied de la tour −25
     *   · élite / boss −10 · presque mort (< 30 % PV) −8 · vise un joueur −5 · puis la distance au Monolithe.
     */
    private static List<Mob> towerTargets(BlockPos pos, double cx, double top, double cz, double r, int n) {
        BlockPos mono = KingdomManager.center();
        Vec3 c = Vec3.atCenterOf(pos);
        List<Mob> visible = new ArrayList<>();
        for (Mob m : hordeMobsNear(pos, r)) {
            // Point de tir : au sommet, un bloc vers la cible (les créneaux)
            double hx = m.getX() - cx, hz = m.getZ() - cz, hl = Math.max(0.001, Math.sqrt(hx * hx + hz * hz));
            Vec3 from = new Vec3(cx + hx / hl * 1.2, top, cz + hz / hl * 1.2);
            if (clearShot(from, m.getEyePosition(), m) || clearShot(from, m.position().add(0, m.getBbHeight() * 0.5, 0), m)) visible.add(m);
        }
        visible.sort((a, b) -> Double.compare(threat(a, c, mono), threat(b, c, mono)));
        return visible.size() > n ? new ArrayList<>(visible.subList(0, n)) : visible;
    }

    /** Ligne de tir dégagée (aucun bloc solide entre les deux points). */
    private static boolean clearShot(Vec3 from, Vec3 to, net.minecraft.world.entity.Entity ref) {
        var hit = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, ref));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** Score de menace d'un monstre pour une tour (plus bas = plus prioritaire). */
    private static double threat(Mob m, Vec3 tower, BlockPos mono) {
        double s = mono != null ? Math.sqrt(m.distanceToSqr(Vec3.atCenterOf(mono))) : Math.sqrt(m.distanceToSqr(tower));
        String role = KingdomSiege.roleOf(m);
        if ("banner".equals(role)) s -= 40;
        else if (role != null && !role.isEmpty()) s -= 30;
        double dx = m.getX() - tower.x, dz = m.getZ() - tower.z;
        if (dx * dx + dz * dz <= 16) s -= 25;                                  // au pied de la tour : elle se défend
        if (com.wavesurvivor.item.RelicEffects.isEliteTarget(m)) s -= 10;
        if (m.getHealth() < m.getMaxHealth() * 0.3f) s -= 8;                    // l'achever
        if (m.getTarget() instanceof net.minecraft.world.entity.player.Player) s -= 5;
        return s;
    }

    private static List<Mob> hordeMobsNear(BlockPos pos, double r) {
        Vec3 c = Vec3.atCenterOf(pos);
        List<Mob> out = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(r, 8, r),
                m -> TrapBlock.isHostile(m) && m.distanceToSqr(c) <= r * r));
        out.sort((a, b) -> Double.compare(a.distanceToSqr(c), b.distanceToSqr(c)));
        return out;
    }

    private static void archer(Def d) {
        double cx = d.pos.getX() + 1.0, cz = d.pos.getZ() + 1.0; // centre de la tour 2×2
        double top = d.pos.getY() + 8.3;                            // sommet de la tour (8 blocs)
        int shots = "fire".equals(d.variant) ? 3 : d.level >= 3 ? 2 : 1;
        // Cibles VISIBLES depuis les créneaux, classées par menace (réévalué à chaque tir : jamais bloquée derrière un mur)
        List<Mob> targets = towerTargets(d.pos, cx, top, cz, 24, shots);
        if (targets.isEmpty()) return;
        int volley = "fire".equals(d.variant) ? shots : targets.size(); // Feu : 3 flèches, réparties sur les cibles visibles
        for (int i = 0; i < volley; i++) {
            Mob t = targets.get(i % targets.size());
            // Tir depuis la plateforme (au-dessus du corps invisible), décalé d'un bloc vers la cible : la flèche part des créneaux
            double hx = t.getX() - cx, hz = t.getZ() - cz, hl = Math.max(0.001, Math.sqrt(hx * hx + hz * hz));
            double sx = cx + hx / hl * 1.0, sy = top, sz = cz + hz / hl * 1.0;
            Arrow arrow = new Arrow(level, sx, sy, sz);
            double dx = t.getX() - sx, dy = t.getY(0.5) - sy, dz = t.getZ() - sz;
            arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.12, dz, 2.0f, 2f);
            arrow.setBaseDamage(4.0);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            if (d.level >= 2) arrow.setSecondsOnFire(100);
            arrow.getPersistentData().putBoolean("ws_tower_arrow", true);
            // Spécialisations : Feu (dégâts + brûlure longue) ; Givre / Explosif gérés à l'impact (ArrowEvents)
            if ("fire".equals(d.variant)) { arrow.setBaseDamage(4.0); arrow.setSecondsOnFire(100); }
            if (!d.variant.isEmpty()) arrow.getPersistentData().putString("ws_tower_variant", d.variant);
            level.addFreshEntity(arrow);
        }
        level.playSound(null, d.pos, SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 0.7f, 1.1f);
    }

    private static void mage(Def d) {
        double r = (d.level >= 2 ? 14 : 10) + ("glacier".equals(d.variant) ? 2 : 0);
        List<Mob> near = hordeMobsNear(d.pos, r);
        long gt = level.getGameTime();
        boolean nova = "glacier".equals(d.variant) && gt % 80 < 40;      // Glacier : nova de givre toutes les 4 s
        for (Mob m : near) {
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, "glacier".equals(d.variant) ? 3 : 1, false, true));
            if (d.level >= 3) m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, "curse".equals(d.variant) ? 1 : 0, false, true));
            if ("glacier".equals(d.variant)) m.setTicksFrozen(Math.max(m.getTicksFrozen(), 150));
            if (nova) m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 9, false, true)); // immobilisé 1 s
            if ("curse".equals(d.variant)) {
                m.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 2, false, true));
                m.getPersistentData().putLong("ws_cursed_until", gt + 60);   // +20 % de dégâts subis
            }
        }
        if (nova && !near.isEmpty()) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, d.pos.getX() + 0.5, d.pos.getY() + 1.5, d.pos.getZ() + 0.5, 80, r / 2, 0.5, r / 2, 0.05);
            level.playSound(null, d.pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8f, 0.6f);
        }
        // Tempête : 3 éclairs (visuels) sur des monstres au hasard, 10 dégâts magiques, chacun rebondit (5) sur un voisin
        if ("storm".equals(d.variant) && !near.isEmpty()) {
            for (int i = 0; i < Math.min(3, near.size()); i++) {
                Mob m = near.get(level.random.nextInt(near.size()));
                net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(m.getX(), m.getY(), m.getZ());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                m.hurt(level.damageSources().magic(), 10f);
                Mob chain = null;
                double bd = 25;
                for (Mob o : near) {
                    if (o == m || !o.isAlive()) continue;
                    double dd = o.distanceToSqr(m);
                    if (dd < bd) { bd = dd; chain = o; }
                }
                if (chain != null) {
                    chain.hurt(level.damageSources().magic(), 5f);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, chain.getX(), chain.getY() + 1, chain.getZ(), 10, 0.3, 0.5, 0.3, 0.1);
                }
            }
        }
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            level.sendParticles("glacier".equals(d.variant) ? ParticleTypes.SNOWFLAKE : ParticleTypes.WITCH,
                    d.pos.getX() + 0.5 + Math.cos(a) * r, d.pos.getY() + 0.3, d.pos.getZ() + 0.5 + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        level.playSound(null, d.pos, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.BLOCKS, 0.4f, 1.4f);
    }

    /**
     * Baraquement : maintient 3 / 4 / 5 soldats (selon le niveau). Remplissage initial rapide (1 par seconde),
     * puis remplacement des morts toutes les 30 / 25 / 20 s. Stats : 30/40/50 PV, 5/6/7 dégâts.
     */
    private static void barracks(Def d, long now) {
        d.soldiers.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        // Retour à la base : sans cible et sorti du royaume → il rentre (téléporté s'il est beaucoup trop loin)
        // Commandant : tant que sa bannière de ralliement est plantée, les soldats sans cible s'y rendent
        BlockPos rally = KingdomRoleExtras.rallyPoint();
        int home = Math.max(8, KingdomClaim.radius()) + 4;
        for (UUID id : d.soldiers) {
            if (!(level.getEntity(id) instanceof Mob s) || s.getTarget() != null) continue;
            // Commandant — « Suivez-moi » : les soldats l'escortent partout (prioritaire sur la bannière et les autres ordres)
            if ("follow".equals(d.order) && d.follow != null) {
                ServerPlayer lead = level.getServer().getPlayerList().getPlayer(d.follow);
                if (lead != null && lead.isAlive() && !lead.isSpectator() && lead.level() == level) {
                    double dl = Math.sqrt(s.distanceToSqr(lead));
                    if (dl > 48) s.teleportTo(lead.getX() + level.random.nextInt(5) - 2, lead.getY(), lead.getZ() + level.random.nextInt(5) - 2);
                    else if (dl > 4) s.getNavigation().moveTo(lead, 1.3);
                    continue;
                }
            }
            if (rally != null) {
                // Pas de chemin direct au-delà de la portée de suivi du soldat : il avance par étapes de 12 blocs
                Vec3 tgt = Vec3.atBottomCenterOf(rally);
                double dr = Math.sqrt(s.distanceToSqr(tgt));
                if (dr > 64) {
                    s.teleportTo(tgt.x, tgt.y + 0.5, tgt.z);
                } else if (dr > 4) {
                    Vec3 dir = tgt.subtract(s.position()).normalize();
                    Vec3 wp = dr > 14 ? s.position().add(dir.scale(12)) : tgt;
                    BlockPos wpp = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            BlockPos.containing(wp));
                    s.getNavigation().moveTo(wpp.getX() + 0.5, wpp.getY(), wpp.getZ() + 0.5, 1.3);
                }
                continue;
            }
            double dist = Math.sqrt(s.distanceToSqr(Vec3.atCenterOf(d.pos)));
            // Ordres du Baraquement (fiche de la défense) : Défendre le Monolithe / Patrouille / Tenir la position
            BlockPos mono = KingdomManager.center();
            if ("monolith".equals(d.order) && mono != null) {
                double dm = Math.sqrt(s.distanceToSqr(Vec3.atCenterOf(mono)));
                if (dm > 64) s.teleportTo(mono.getX() + 0.5, mono.getY() + 1, mono.getZ() + 0.5);
                else if (dm > 6) s.getNavigation().moveTo(mono.getX() + 0.5 + level.random.nextInt(7) - 3, mono.getY(),
                        mono.getZ() + 0.5 + level.random.nextInt(7) - 3, 1.1);
                continue;
            }
            if ("patrol".equals(d.order) && mono != null) {
                if (s.getNavigation().isDone() && level.random.nextInt(3) == 0) {
                    int r = Math.max(6, KingdomClaim.radius() - 2);
                    double a = level.random.nextDouble() * Math.PI * 2;
                    int px = mono.getX() + (int) Math.round(Math.cos(a) * r), pz = mono.getZ() + (int) Math.round(Math.sin(a) * r);
                    int py = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
                    s.getNavigation().moveTo(px + 0.5, py, pz + 0.5, 1.0);
                }
                continue;
            }
            if (dist > home + 32) s.teleportTo(d.pos.getX() + 0.5, d.pos.getY() + 1, d.pos.getZ() + 0.5);
            else if (dist > home) s.getNavigation().moveTo(d.pos.getX() + 0.5, d.pos.getY(), d.pos.getZ() + 0.5, 1.0);
        }
        int max = 2 + d.level;
        if ("paladins".equals(d.variant)) max++;                          // Paladins : un soldat de plus
        // Commandant : +1 soldat par baraquement
        if (KingdomRoles.present(KingdomRoles.Role.COMMANDER)) max++;
        // Paladins : Régénération II, et soignent les joueurs proches (1 PV / 2 s)
        if ("paladins".equals(d.variant)) {
            boolean heal = level.getGameTime() % 40 < 20;
            for (UUID id : d.soldiers) {
                if (!(level.getEntity(id) instanceof Mob s)) continue;
                s.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, true));
                if (heal) {
                    for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, s.getBoundingBox().inflate(6))) {
                        if (p.getHealth() < p.getMaxHealth()) {
                            p.heal(1f);
                            level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2.0, p.getZ(), 1, 0.2, 0.1, 0.2, 0);
                        }
                    }
                }
            }
        }
        // Gardes : provocation — les monstres à 6 blocs d'un garde le prennent pour cible
        if ("guards".equals(d.variant)) {
            for (UUID id : d.soldiers) {
                if (!(level.getEntity(id) instanceof Mob s) || !s.isAlive()) continue;
                for (Mob m : level.getEntitiesOfClass(Mob.class, s.getBoundingBox().inflate(6), TrapBlock::isHostile)) {
                    if (m.getTarget() != s) m.setTarget(s);
                }
            }
        }
        // Berserkers : sous 50 % de PV, ils entrent en rage (+50 % de dégâts, +25 % de vitesse, particules de colère)
        if ("berserkers".equals(d.variant)) {
            for (UUID id : d.soldiers) {
                if (level.getEntity(id) instanceof Mob s && s.isAlive()) berserkRage(s);
            }
        }
        // Commandant — Cri de guerre : soldats à 12 blocs d'un Commandant +20 % de dégâts et de vitesse
        for (UUID id : d.soldiers) {
            if (level.getEntity(id) instanceof Mob s && s.isAlive()) warCry(s);
        }
        if (d.soldiers.size() >= max) { d.filled = true; return; }
        if (now < d.nextSpawn) return;

        com.wavesurvivor.entity.KingdomSoldier s = com.wavesurvivor.registry.ModEntities.KINGDOM_SOLDIER.get().create(level);
        if (s == null) return;
        BlockPos at = com.wavesurvivor.horde.spawn.SpawnZone.pick(level, d.pos, 6, s.getType()); // autour du bâtiment 6×4
        s.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        double hp = 16 + 8 * d.level; // 24 / 32 / 40 PV (avant spécialisation)
        applySoldierStats(s, d);
        s.setHealth(s.getMaxHealth());
        s.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        s.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(net.minecraft.world.item.Items.SHIELD));
        s.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        s.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) s.setDropChance(slot, 0f);
        s.setCustomName(net.minecraft.network.chat.Component.literal(WSLang.t("kingdom.soldier_name")));
        s.setCustomNameVisible(true);
        s.getPersistentData().putBoolean("ws_kingdom_soldier", true);
        if (level.addFreshEntity(s)) {
            d.soldiers.add(s.getUUID());
            level.sendParticles(ParticleTypes.CLOUD, s.getX(), s.getY() + 1, s.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            level.playSound(null, d.pos, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.BLOCKS, 1f, 1f);
        }
        d.nextSpawn = now + (d.filled ? (35 - 5L * d.level) * 20L : 20L);
    }

    /**
     * Les flèches des tours TRAVERSENT les soldats du royaume et les joueurs (l'impact est ignoré,
     * la flèche continue sa course vers sa vraie cible). Enregistré au démarrage du mod.
     */
    public static class ArrowEvents {

        /** Cible gelée (Givre) : +50 % de dégâts subis ; maudite (Malédiction) : +20 %. */
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onVulnerable(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
            if (level == null || e.getEntity().level().isClientSide) return;
            var pd = e.getEntity().getPersistentData();
            long gt = level.getGameTime();
            if (pd.getLong("ws_frozen_until") > gt) e.setAmount(e.getAmount() * 1.5f);
            if (pd.getLong("ws_cursed_until") > gt) e.setAmount(e.getAmount() * 1.2f);
        }
        /**
         * Les coups des MONSTRES sur les soldats du royaume doivent passer : ce dernier écouteur (priorité la plus basse,
         * voit aussi les événements annulés) rétablit le coup si un autre écouteur — d'un autre mod par exemple — l'a annulé.
         * (Les coups des joueurs et les flèches des tours restent bloqués par KingdomSoldier.hurt.)
         */
        private static boolean hostileHitOnSoldier(net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.damagesource.DamageSource src) {
            return victim instanceof com.wavesurvivor.entity.KingdomSoldier
                    && src.getEntity() instanceof Mob atk && TrapBlock.isHostile(atk);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
        public void onSoldierAttacked(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
            if (e.isCanceled() && hostileHitOnSoldier(e.getEntity(), e.getSource())) e.setCanceled(false);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
        public void onSoldierHurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
            if (e.isCanceled() && hostileHitOnSoldier(e.getEntity(), e.getSource())) e.setCanceled(false);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
        public void onSoldierDamaged(net.minecraftforge.event.entity.living.LivingDamageEvent e) {
            if (e.isCanceled() && hostileHitOnSoldier(e.getEntity(), e.getSource())) e.setCanceled(false);
        }

        /**
         * Maj + clic droit sur une défense = réparation. Intercepté ici car Minecraft ne déclenche PAS l'action
         * du bloc quand on est accroupi avec un objet en main (les émeraudes).
         */
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onRightClick(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
            if (e.getLevel().isClientSide || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
            if (!(e.getEntity() instanceof ServerPlayer sp) || !sp.isShiftKeyDown()) return;
            BlockPos target = e.getPos();
            var clicked = e.getLevel().getBlockState(target).getBlock();
            if (clicked instanceof ColliderBlock) {
                target = parentOf(target);          // clic sur le corps invisible → la défense
                if (target == null) return;
            } else if (!(clicked instanceof DefenseBlock)) {
                return;
            }
            repair(sp, target);
            e.setCanceled(true);
            e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onImpact(net.minecraftforge.event.entity.ProjectileImpactEvent e) {
            if (!e.getProjectile().getPersistentData().getBoolean("ws_tower_arrow")) return;
            if (e.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                    && (hit.getEntity() instanceof com.wavesurvivor.entity.KingdomSoldier
                        || hit.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
                e.setCanceled(true);
                return;
            }
            // Spécialisations des flèches de tour : Givre (ralentit + gèle) et Explosif (souffle sur les monstres seulement)
            String variant = e.getProjectile().getPersistentData().getString("ws_tower_variant");
            if (variant.isEmpty() || level == null) return;
            Vec3 at = e.getRayTraceResult().getLocation();
            if ("frost".equals(variant) && e.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult fh
                    && fh.getEntity() instanceof Mob fm && TrapBlock.isHostile(fm)) {
                fm.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true));
                fm.setTicksFrozen(Math.max(fm.getTicksFrozen(), 140));
                level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.02);
                // Marques de givre : au 3e coup en moins de 4 s, la cible gèle (immobile 2 s, +50 % de dégâts subis)
                var pd = fm.getPersistentData();
                long gt = level.getGameTime();
                int marks = gt - pd.getLong("ws_frost_t") <= 80 ? pd.getInt("ws_frost_marks") + 1 : 1;
                pd.putLong("ws_frost_t", gt);
                if (marks >= 3) {
                    marks = 0;
                    pd.putLong("ws_frozen_until", gt + 40);
                    fm.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 9, false, true));
                    fm.setTicksFrozen(Math.max(fm.getTicksFrozen(), 300));
                    level.sendParticles(ParticleTypes.ITEM_SNOWBALL, fm.getX(), fm.getY() + 1, fm.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
                    level.playSound(null, fm.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.7f, 1.6f);
                    for (Mob o : level.getEntitiesOfClass(Mob.class, fm.getBoundingBox().inflate(3), TrapBlock::isHostile)) {
                        if (o != fm) o.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true));
                    }
                }
                pd.putInt("ws_frost_marks", marks);
            }
            if ("explosive".equals(variant) && !e.getProjectile().getPersistentData().getBoolean("ws_exploded")) {
                e.getProjectile().getPersistentData().putBoolean("ws_exploded", true);
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(2.5), TrapBlock::isHostile)) {
                    m.hurt(level.damageSources().explosion(null, null), 6f);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.6f, 1.4f);
            }
        }
    }

    /** Tour du Glaneur : rayon de ramassage selon le niveau. */
    private static final int[] COLLECT_RADIUS = {16, 32, 48};

    /** Rayon de ramassage du Glaneur pour un niveau (1 à 3). */
    public static int collectRadius(int lvl) {
        return COLLECT_RADIUS[Math.min(2, Math.max(0, lvl - 1))];
    }

    /** Rangées de stockage du Glaneur : 3 (coffre) → 4 → 6 (double coffre). */
    public static int storageRows(int lvl) {
        return lvl >= 3 ? 6 : lvl == 2 ? 4 : 3;
    }

    /** Range un objet dans les {@code cap} premières cases (empile d'abord, puis cases vides). @return le reste. */
    private static ItemStack insert(net.minecraft.world.SimpleContainer c, ItemStack st, int cap) {
        ItemStack rest = st.copy();
        for (int i = 0; i < cap && !rest.isEmpty(); i++) {
            ItemStack slot = c.getItem(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, rest)) continue;
            int move = Math.min(rest.getCount(), Math.min(c.getMaxStackSize(), slot.getMaxStackSize()) - slot.getCount());
            if (move > 0) {
                slot.grow(move);
                rest.shrink(move);
            }
        }
        for (int i = 0; i < cap && !rest.isEmpty(); i++) {
            if (c.getItem(i).isEmpty()) {
                c.setItem(i, rest.copy());
                rest = ItemStack.EMPTY;
            }
        }
        c.setChanged();
        return rest;
    }

    /** Ramasse les objets AU SOL dans le rayon et les range dans le stockage (27 cases). */
    /** Cri de guerre du Commandant : bonus (transitoires) tant qu'un Commandant est à 12 blocs du soldat. */
    private static final UUID CRY_DMG = UUID.fromString("3b9e6c1a-2f4d-4a8e-9c7b-5d1e2f3a4b03");
    private static final UUID CRY_SPD = UUID.fromString("3b9e6c1a-2f4d-4a8e-9c7b-5d1e2f3a4b04");

    private static void warCry(Mob s) {
        boolean near = false;
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, s.getBoundingBox().inflate(12))) {
            if (!p.isSpectator() && KingdomRoles.has(p, KingdomRoles.Role.COMMANDER)) { near = true; break; }
        }
        var atk = s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        var spd = s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (atk != null) {
            boolean has = atk.getModifier(CRY_DMG) != null;
            if (near && !has) atk.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(CRY_DMG, "ws_war_cry", 0.2,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
            else if (!near && has) atk.removeModifier(CRY_DMG);
        }
        if (spd != null) {
            boolean has = spd.getModifier(CRY_SPD) != null;
            if (near && !has) spd.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(CRY_SPD, "ws_war_cry_speed", 0.2,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
            else if (!near && has) spd.removeModifier(CRY_SPD);
        }
        if (near && level.getGameTime() % 60 < 20) {
            level.sendParticles(ParticleTypes.NOTE, s.getX(), s.getY() + s.getBbHeight() + 0.4, s.getZ(), 1, 0.2, 0.1, 0.2, 0.5);
        }
    }

    /** Rage du Berserker : bonus (transitoires) appliqués sous 50 % de PV, retirés au-dessus. */
    private static final UUID RAGE_DMG = UUID.fromString("3b9e6c1a-2f4d-4a8e-9c7b-5d1e2f3a4b01");
    private static final UUID RAGE_SPD = UUID.fromString("3b9e6c1a-2f4d-4a8e-9c7b-5d1e2f3a4b02");

    private static void berserkRage(Mob s) {
        boolean rage = s.getHealth() < s.getMaxHealth() * 0.5f;
        var atk = s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        var spd = s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (atk != null) {
            boolean has = atk.getModifier(RAGE_DMG) != null;
            if (rage && !has) atk.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RAGE_DMG, "ws_berserk_rage", 0.5,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
            else if (!rage && has) atk.removeModifier(RAGE_DMG);
        }
        if (spd != null) {
            boolean has = spd.getModifier(RAGE_SPD) != null;
            if (rage && !has) spd.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RAGE_SPD, "ws_berserk_rage_speed", 0.25,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
            else if (!rage && has) spd.removeModifier(RAGE_SPD);
        }
        if (rage) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, s.getX(), s.getY() + s.getBbHeight() + 0.2, s.getZ(), 2, 0.3, 0.2, 0.3, 0);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, s.getX(), s.getY() + 1, s.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
        }
    }

    private static void collector(Def d) {
        if (!(level.getBlockEntity(d.pos) instanceof DefenseBlockEntity be)) return;
        int cap = storageRows(d.level) * 9;
        // Trésorerie : 2 pièces de monnaie toutes les 20 s
        if ("treasury".equals(d.variant) && level.getGameTime() % 400 < 20) {
            // Partie Kingdom : les pièces vont directement dans le trésor commun
            if (KingdomTreasury.active()) KingdomTreasury.add(KingdomTreasury.Res.MONEY, 2);
            else insert(be.getStorage(), new ItemStack(AltarDefense.currencyItem(), 2), cap);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, d.pos.getX() + 0.5, d.pos.getY() + 2.5, d.pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
        }
        double r = "magnet".equals(d.variant) ? 96 : COLLECT_RADIUS[Math.min(2, Math.max(0, d.level - 1))];
        // Aimant : aspire aussi l'expérience, rendue au joueur le plus proche de la tour
        if ("magnet".equals(d.variant)) {
            for (net.minecraft.world.entity.ExperienceOrb orb : level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
                    new AABB(d.pos).inflate(r, 16, r), o -> o.isAlive())) {
                net.minecraft.world.entity.player.Player near = level.getNearestPlayer(d.pos.getX() + 0.5, d.pos.getY(), d.pos.getZ() + 0.5, 128, false);
                if (near == null) break;
                near.giveExperiencePoints(orb.getValue());
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, orb.getX(), orb.getY() + 0.2, orb.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
                orb.discard();
            }
        }
        AABB box = new AABB(d.pos).inflate(r, Math.min(r, 12), r);
        int taken = 0;
        for (net.minecraft.world.entity.item.ItemEntity ie : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
                it -> it.isAlive() && !it.hasPickUpDelay())) {
            ItemStack before = ie.getItem();
            // Monnaie ramassée par le Glaneur : créditée au trésor commun
            if (KingdomTreasury.active() && AltarDefense.isCurrency(before)) {
                KingdomTreasury.add(KingdomTreasury.Res.MONEY, before.getCount());
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, ie.getX(), ie.getY() + 0.3, ie.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
                ie.discard();
                if (++taken >= 16) break;
                continue;
            }
            // Fonderie : ce qui se fond (minerais, nourriture…) est rangé déjà cuit
            ItemStack toStore = "smelter".equals(d.variant) ? smelted(before) : before;
            // Fonderie : 50 % de chance de doubler ce qui a été fondu
            if ("smelter".equals(d.variant) && toStore != before && !ItemStack.isSameItem(toStore, before) && level.random.nextBoolean()) {
                toStore = toStore.copy();
                toStore.setCount(Math.min(toStore.getMaxStackSize(), toStore.getCount() * 2));
            }
            ItemStack rest = insert(be.getStorage(), toStore, cap);
            if (rest.getCount() == toStore.getCount()) continue; // stockage plein pour cet objet
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, ie.getX(), ie.getY() + 0.3, ie.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
            if (rest.isEmpty()) ie.discard();
            else ie.setItem(rest);
            if (++taken >= 16) break;
        }
        if (taken > 0) level.playSound(null, d.pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 0.8f);
    }

    /** Fonderie du Glaneur : résultat de cuisson au four (ou l'objet tel quel s'il ne se cuit pas). */
    private static ItemStack smelted(ItemStack st) {
        var rec = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                new net.minecraft.world.SimpleContainer(st.copyWithCount(1)), level);
        if (rec.isEmpty()) return st;
        ItemStack out = rec.get().getResultItem(level.registryAccess()).copy();
        out.setCount(Math.min(out.getMaxStackSize(), out.getCount() * st.getCount()));
        return out;
    }

    private static void shrine(Def d) {
        AltarDefense.healMonolith(d.level >= 2 ? 4f : 2f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, d.pos.getX() + 0.5, d.pos.getY() + 1.2, d.pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
        // Répare aussi les défenses proches (16 blocs) : +1 / +2 / +3 PV par seconde selon le niveau
        Vec3 c = Vec3.atCenterOf(d.pos);
        for (Def o : DEFS.values()) {
            if (o == d || o.pos.distSqr(d.pos) > 16 * 16) continue;
            float[] hp = KingdomClaim.hpOf(o.pos);
            if (hp == null || hp[0] >= hp[1] - 0.5f) continue;
            KingdomClaim.heal(o.pos, "bulwark".equals(d.variant) ? d.level * 4 : d.level);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, o.pos.getX() + 0.5, o.pos.getY() + 1.4, o.pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0);
        }
        // Régénération pour les soldats du royaume proches (niveau II au niv. 3)
        for (com.wavesurvivor.entity.KingdomSoldier s : level.getEntitiesOfClass(com.wavesurvivor.entity.KingdomSoldier.class,
                new AABB(d.pos).inflate(16), sol -> sol.distanceToSqr(c) <= 16 * 16)) {
            s.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, "life".equals(d.variant) ? 2 : d.level >= 3 ? 1 : 0, false, true));
            if ("blessing".equals(d.variant)) {
                s.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, false, true));
                s.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, true));
            }
        }
        // Spécialisations du sanctuaire
        if ("life".equals(d.variant)) {
            boolean shield = level.getGameTime() % 200 < 20;                    // toutes les 10 s : 4 cœurs d'Absorption
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(d.pos).inflate(12))) {
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, true));
                if (shield) p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1, false, true));
            }
        }
        if ("bulwark".equals(d.variant)) KingdomClaim.healArea(d.pos, 12, 4f);   // répare aussi les murs
        if ("blessing".equals(d.variant)) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(d.pos).inflate(20))) {
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, false, true));
            }
        }
        if (d.level >= 3) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(d.pos).inflate(8))) {
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, true));
            }
        }
    }
}
