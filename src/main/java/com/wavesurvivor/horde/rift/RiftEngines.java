package com.wavesurvivor.horde.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 2.0 (jalon 6) — ENGINS DE SIÈGE de la Faille : 2 catapultes (2 × 6, 3 de haut) et 2 balistes célestes (3 × 3, 3 de haut), posées sur l'île
 * au début du Siège, qui restent pour la Contre-offensive et le Titan. Pilotage : clic droit sur le pupitre de l'engin.
 * <ul>
 *   <li>Catapulte : 1 pierre taillée (cobblestone) par boulet, rechargement 30 s, tir en cloche dans la direction du regard
 *       (accroupi : tir court). Dégâts de zone 80 au centre → 30 % au bord (rayon 4), ×1,5 contre le Titan, les avatars et les structures.</li>
 *   <li>Baliste céleste : chargeur de 16 flèches prises dans l'inventaire, un tir toutes les 0,5 s (clics répétés), rechargement 10 s,
 *       6 de dégâts par flèche.</li>
 * </ul>
 * Version provisoire : modèles en blocs vanilla, un seul niveau (les améliorations et variantes viendront ensuite).
 */
final class RiftEngines {

    private static final class Engine {
        final BlockPos lectern;
        final boolean ballista;
        final Vec3 muzzle;
        long readyAt, nextShot;
        int ammo;
        boolean reloading;
        /** Niveau (1 base, 2 renforcée / consacrée, 3 variante choisie) et variante (0 = aucune, 1 à 3). */
        int level = 1, variant = 0;

        Engine(BlockPos lectern, boolean ballista, Vec3 muzzle) {
            this.lectern = lectern;
            this.ballista = ballista;
            this.muzzle = muzzle;
        }
    }

    private static final class Boulder {
        Vec3 pos, vel;
        UUID owner;
        float mul = 1f;
        boolean fire, frag;
    }

    /** Prix de l'amélioration au niveau 2, puis de la variante de niveau 3 (catapulte, baliste). */
    private static final int[] UP2 = {60, 50}, UP3 = {100, 80};
    private static final String[][] VARIANTS = {
            {"Infernale", "À fragmentation", "Trébuchet de siège"}, {"Séraphique", "Solaire", "Du Jugement"}};
    private static final Map<UUID, Engine> choosing = new HashMap<>();

    private static String nameOf(Engine e) {
        String base = e.ballista ? "Baliste céleste" : "Catapulte";
        if (e.level == 2) return base + (e.ballista ? " consacrée" : " renforcée");
        if (e.level >= 3 && e.variant >= 1) return base + " " + VARIANTS[e.ballista ? 1 : 0][e.variant - 1];
        return base;
    }

    private static final List<Engine> ENGINES = new ArrayList<>();
    private static final List<Boulder> BOULDERS = new ArrayList<>();
    private static final Map<UUID, Long> msgGate = new HashMap<>();

    private RiftEngines() {}

    static void clear() {
        ENGINES.clear();
        BOULDERS.clear();
        msgGate.clear();
        choosing.clear();
    }

    // ─── Construction (tous les engins regardent vers le SUD, là où surgit le Titan) ───

    /**
     * Pose un engin acheté à la boutique (tourné vers le sud). {@code at} = bloc libre au-dessus du sol visé.
     * @return 0 posé · 1 pas de place / sol instable · 2 hors de l'île ou dimension incorrecte
     */
    static int place(ServerLevel l, BlockPos at, boolean ballista) {
        if (l.dimension() != RiftManager.RIFT_DIM) return 2;
        int x0 = at.getX() + (ballista ? 2 : 1), z0 = at.getZ(), y = at.getY();
        int xMin = x0 - 3, xMax = ballista ? x0 - 1 : x0, zMax = z0 + (ballista ? 2 : 5);
        int rMax = Math.max(4, RiftManager.islandRadiusNow() - 2);
        long r2 = (long) rMax * rMax;
        for (int x = xMin; x <= xMax; x++) for (int z = z0; z <= zMax; z++) {
            long dx = x - RiftManager.CENTER.getX(), dz = z - RiftManager.CENTER.getZ();
            if (dx * dx + dz * dz > r2) return 2;
            if (l.getBlockState(new BlockPos(x, y - 1, z)).isAir()) return 1;
            for (int h = 0; h <= 3; h++) {
                BlockState s = l.getBlockState(new BlockPos(x, y + h, z));
                if (!s.isAir() && !s.canBeReplaced()) return 1;
            }
        }
        if (ballista) ballistaAt(l, x0, y, z0); else catapultAt(l, x0, y, z0);
        return 0;
    }

    private static void put(ServerLevel l, int x, int y, int z, BlockState st) {
        l.setBlock(new BlockPos(x, y, z), st, 2);
    }

    /** Pose un bloc du modèle (dessiné vers +X) tourné vers le sud : bloc monde = (x0 - dz - 1, z0 + dx). */
    private static void putR(ServerLevel l, int x0, int y, int z0, int dx, int dz, BlockState st) {
        put(l, x0 - dz - 1, y, z0 + dx, st);
    }

    /** Catapulte : châssis 6 × 2 en bois sombre, roues de fer, montants, grande poutre, cuillère (chaudron), contrepoids, treuil (pupitre). */
    private static void catapultAt(ServerLevel l, int x0, int y, int z0) {
        BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
        for (int dx = 0; dx < 6; dx++) for (int dz = 0; dz < 2; dz++) putR(l, x0, y, z0, dx, dz, planks);
        for (int dx : new int[]{1, 4}) {
            putR(l, x0, y, z0, dx, -1, Blocks.IRON_BARS.defaultBlockState());
            putR(l, x0, y, z0, dx, 2, Blocks.IRON_BARS.defaultBlockState());
        }
        for (int dz = 0; dz < 2; dz++) {
            putR(l, x0, y + 1, z0, 3, dz, fence);
            putR(l, x0, y + 2, z0, 3, dz, fence);
        }
        BlockState beam = Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        for (int dx = 0; dx <= 4; dx++) putR(l, x0, y + 3, z0, dx, 0, beam);
        putR(l, x0, y + 2, z0, 5, 0, Blocks.CAULDRON.defaultBlockState());
        putR(l, x0, y + 2, z0, 0, 0, Blocks.COBBLESTONE.defaultBlockState());
        putR(l, x0, y + 2, z0, 0, 1, Blocks.COBBLESTONE.defaultBlockState());
        BlockPos lec = new BlockPos(x0 - 1, y + 1, z0);
        put(l, lec.getX(), lec.getY(), lec.getZ(), Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH));
        ENGINES.add(new Engine(lec, false, new Vec3(x0 - 1.0, y + 3.5, z0 + 5.5)));
    }

    /** Baliste céleste : piédestal 3 × 3 de marbre (quartz) à coins dorés, fût, arc en « ailes » dorées, carreau lumineux. */
    private static void ballistaAt(ServerLevel l, int x0, int y, int z0) {
        for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) {
            boolean corner = (dx != 1) && (dz != 1);
            putR(l, x0, y, z0, dx, dz, corner ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.QUARTZ_BLOCK.defaultBlockState());
        }
        putR(l, x0, y + 1, z0, 1, 1, Blocks.QUARTZ_PILLAR.defaultBlockState());
        putR(l, x0, y + 2, z0, 1, 1, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        for (int dz : new int[]{0, 2}) {
            putR(l, x0, y + 2, z0, 2, dz, Blocks.GOLD_BLOCK.defaultBlockState());
            putR(l, x0, y + 2, z0, 1, dz, Blocks.WHITE_STAINED_GLASS.defaultBlockState());
            putR(l, x0, y + 3, z0, 2, dz, Blocks.AMETHYST_BLOCK.defaultBlockState());
        }
        putR(l, x0, y + 2, z0, 2, 1, Blocks.END_ROD.defaultBlockState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.SOUTH));
        BlockPos lec = new BlockPos(x0 - 2, y + 1, z0);
        put(l, lec.getX(), lec.getY(), lec.getZ(), Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH));
        ENGINES.add(new Engine(lec, true, new Vec3(x0 - 1.5, y + 2.6, z0 + 3.2)));
    }

    // ─── Pilotage ───

    /** Clic droit sur un pupitre d'engin. @return vrai si le bloc est un engin (le clic est alors consommé). */
    static boolean onUse(ServerPlayer p, BlockPos pos, long now) {
        for (Engine e : ENGINES) {
            if (!e.lectern.equals(pos)) continue;
            if (!(p.level() instanceof ServerLevel l)) return true;
            if (p.getMainHandItem().is(Items.GOLD_INGOT)) { upgrade(p, e, now); return true; } // lingot d'or en main : amélioration
            if (e.ballista) useBallista(p, l, e, now); else useCatapult(p, l, e, now);
            return true;
        }
        return false;
    }

    /** Clic droit avec un lingot d'or : niveau 2 (payant), puis choix d'une variante au niveau 3. */
    private static void upgrade(ServerPlayer p, Engine e, long now) {
        int k = e.ballista ? 1 : 0;
        if (e.level == 1) {
            if (!com.wavesurvivor.altar.AltarDefense.trySpend(p, UP2[k])) return;
            e.level = 2;
            if (p.level() instanceof ServerLevel l) l.playSound(null, e.lectern, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 1.2f);
            say(p, "§a" + nameOf(e) + " ! Clic droit avec un lingot d'or pour choisir sa variante.", now);
        } else if (e.level == 2) {
            choosing.put(p.getUUID(), e);
            net.minecraft.network.chat.MutableComponent line = Component.literal("§6Variante (" + UP3[k] + " ◆) : ");
            for (int i = 0; i < 3; i++) {
                final String cmd = "/wavesurvivor riftengine " + i;
                line.append(Component.literal("§e§l[" + VARIANTS[k][i] + "]  ").withStyle(st -> st.withClickEvent(
                        new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, cmd))));
            }
            p.sendSystemMessage(line);
        } else {
            say(p, "§7" + nameOf(e) + " : niveau maximal.", now);
        }
    }

    /** Choix de la variante (clic dans le chat) : définitif pour cet engin. */
    static void chooseVariant(ServerPlayer p, int i) {
        Engine e = choosing.get(p.getUUID());
        if (e == null || e.level != 2 || i < 0 || i > 2 || p.distanceToSqr(e.lectern.getX() + 0.5, e.lectern.getY() + 0.5, e.lectern.getZ() + 0.5) > 400) return;
        if (!com.wavesurvivor.altar.AltarDefense.trySpend(p, UP3[e.ballista ? 1 : 0])) return;
        e.level = 3;
        e.variant = i + 1;
        choosing.remove(p.getUUID());
        if (p.level() instanceof ServerLevel l) l.playSound(null, e.lectern, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1f);
        p.displayClientMessage(Component.literal("§a" + nameOf(e) + " !"), true);
    }

    private static void say(ServerPlayer p, String msg, long now) {
        Long last = msgGate.get(p.getUUID());
        if (last != null && now - last < 10) return;
        msgGate.put(p.getUUID(), now);
        p.displayClientMessage(Component.literal(msg), true);
    }

    private static int take(ServerPlayer p, net.minecraft.world.item.Item item, int max) {
        int got = 0;
        for (int i = 0; i < p.getInventory().getContainerSize() && got < max; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) {
                int n = Math.min(max - got, s.getCount());
                s.shrink(n);
                got += n;
            }
        }
        return got;
    }

    private static void useCatapult(ServerPlayer p, ServerLevel l, Engine e, long now) {
        if (now < e.readyAt) {
            say(p, "§7" + nameOf(e) + " en rechargement : §f" + ((e.readyAt - now + 19) / 20) + " s", now);
            return;
        }
        if (!p.isCreative() && take(p, Items.COBBLESTONE, 1) < 1) {
            say(p, "§cIl faut de la pierre taillée (cobblestone) pour charger la catapulte.", now);
            return;
        }
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, Math.max(look.y, 0.25), look.z).normalize();
        Boulder b = new Boulder();
        b.pos = e.muzzle;
        double speed = (p.isShiftKeyDown() ? 1.6 : 2.5) * (e.level >= 2 ? 1.1 : 1.0) * (e.level >= 3 && e.variant == 3 ? 1.25 : 1.0);
        b.vel = dir.scale(speed);
        b.owner = p.getUUID();
        b.mul = (e.level >= 2 ? 1.25f : 1f) * (e.level >= 3 && e.variant == 3 ? 1.6f : 1f);
        b.fire = e.level >= 3 && e.variant == 1;
        b.frag = e.level >= 3 && e.variant == 2;
        BOULDERS.add(b);
        e.readyAt = now + (e.level >= 3 && e.variant == 3 ? 750 : e.level >= 2 ? 450 : 600);
        l.playSound(null, BlockPos.containing(e.muzzle), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 3f, 0.6f);
        l.playSound(null, BlockPos.containing(e.muzzle), SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 3f, 0.5f);
        say(p, "§6Catapulte : tir ! Rechargement 30 s", now);
    }

    private static void useBallista(ServerPlayer p, ServerLevel l, Engine e, long now) {
        if (e.reloading) {
            if (now < e.readyAt) {
                say(p, "§7Baliste en rechargement : §f" + ((e.readyAt - now + 19) / 20) + " s", now);
                return;
            }
            e.reloading = false;
        }
        if (e.ammo <= 0) {
            int n = p.isCreative() ? 16 : take(p, Items.ARROW, 16);
            if (n <= 0) {
                say(p, "§cIl faut des flèches pour charger la baliste.", now);
                return;
            }
            e.ammo = n;
            e.reloading = true;
            e.readyAt = now + 200;
            l.playSound(null, e.lectern, SoundEvents.CROSSBOW_LOADING_END, SoundSource.BLOCKS, 2f, 0.7f);
            say(p, "§bBaliste : chargement de " + n + " flèches (10 s)…", now);
            return;
        }
        if (now < e.nextShot) return;
        boolean solar = e.level >= 3 && e.variant == 2, judge = e.level >= 3 && e.variant == 3, seraph = e.level >= 3 && e.variant == 1;
        e.nextShot = now + (solar ? 4 : e.level >= 2 ? 8 : 10);
        Vec3 look = p.getLookAngle();
        if (solar || judge) {
            beam(p, l, e, look, solar ? 5f : 4f, judge);   // rayon de lumière (Solaire) ou rayon qui marque (Jugement)
        } else {
            Arrow a = new Arrow(EntityType.ARROW, l);
            a.setOwner(p);
            a.setPos(e.muzzle.x, e.muzzle.y, e.muzzle.z);
            a.setBaseDamage(e.level >= 2 ? 1.9 : 1.5);   // 6 dégâts à pleine vitesse (7,6 consacrée)
            if (seraph) a.setPierceLevel((byte) 4);       // carreau perforant
            a.pickup = AbstractArrow.Pickup.DISALLOWED;
            a.shoot(look.x, look.y, look.z, 4.0f, 0.0f);
            l.addFreshEntity(a);
        }
        e.ammo--;
        l.playSound(null, BlockPos.containing(e.muzzle), SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 2.5f, 0.6f);
        l.sendParticles(ParticleTypes.END_ROD, e.muzzle.x, e.muzzle.y, e.muzzle.z, 3, 0.1, 0.1, 0.1, 0.02);
        if (e.ammo <= 0) {
            e.reloading = true;
            e.readyAt = now + 200;
            say(p, "§bBaliste vide : rechargement (clic pour recharger)", now);
        } else {
            p.displayClientMessage(Component.literal("§b" + nameOf(e) + " §f" + e.ammo + " / 16"), true);
        }
    }

    /** Tir instantané (rayon) : touche tout ce qui est sur la ligne ; {@code mark} : la cible est marquée (+25 % de dégâts subis, 8 s). */
    private static void beam(ServerPlayer p, ServerLevel l, Engine e, Vec3 dir, float dmg, boolean mark) {
        Vec3 from = e.muzzle, to = from.add(dir.scale(70));
        net.minecraft.world.phys.BlockHitResult bh = l.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (bh.getType() != net.minecraft.world.phys.HitResult.Type.MISS) to = bh.getLocation();
        double len = from.distanceTo(to);
        for (double d = 0; d < len; d += 1.0) {
            Vec3 q = from.add(dir.scale(d));
            l.sendParticles(mark ? ParticleTypes.GLOW : ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        final Vec3 end = to;
        for (Entity t : l.getEntitiesOfClass(Entity.class, new AABB(from, end).inflate(2.0))) {
            if (t instanceof Player) continue;
            boolean target = RiftFortress.isTarget(t);
            if (!(t instanceof LivingEntity) && !target) continue;
            if (t.getBoundingBox().inflate(0.3).clip(from, end).isEmpty()) continue;
            if (target) RiftFortress.damageTarget(t, dmg);
            else {
                ((LivingEntity) t).hurt(l.damageSources().playerAttack(p), dmg);
                if (mark) t.getPersistentData().putLong("ws_rift_mark", l.getGameTime() + 160);
            }
        }
    }

    /** Dégâts supplémentaires sur une cible marquée par la baliste du Jugement. */
    static float markedDamage(LivingEntity e, float amount) {
        long until = e.getPersistentData().getLong("ws_rift_mark");
        return until > e.level().getGameTime() ? amount * 1.25f : amount;
    }

    // ─── Boulets de catapulte (trajectoire simulée) ───

    static void tick(ServerLevel l) {
        if (BOULDERS.isEmpty()) return;
        for (Boulder b : new ArrayList<>(BOULDERS)) {
            boolean boom = false;
            for (int s = 0; s < 4 && !boom; s++) {
                b.pos = b.pos.add(b.vel.scale(0.25));
                b.vel = b.vel.add(0, -0.0125, 0);
                BlockPos bp = BlockPos.containing(b.pos);
                if (!l.getBlockState(bp).isAir() && l.getBlockState(bp).getFluidState().isEmpty()) boom = true;
                else if (!l.getEntitiesOfClass(Entity.class, new AABB(b.pos, b.pos).inflate(1.3),
                        t -> (t instanceof LivingEntity && !(t instanceof Player)) || RiftFortress.isTarget(t)).isEmpty()) boom = true;
                if (b.pos.y < RiftManager.CENTER.getY() - 120) { BOULDERS.remove(b); boom = false; break; }
            }
            if (!BOULDERS.contains(b)) continue;
            l.sendParticles(ParticleTypes.LARGE_SMOKE, b.pos.x, b.pos.y, b.pos.z, 2, 0.1, 0.1, 0.1, 0.0);
            l.sendParticles(ParticleTypes.FLAME, b.pos.x, b.pos.y, b.pos.z, 1, 0.05, 0.05, 0.05, 0.0);
            if (boom) {
                BOULDERS.remove(b);
                explode(l, b);
            }
        }
    }

    private static void explode(ServerLevel l, Boulder b) {
        Entity owner = l.getServer().getPlayerList().getPlayer(b.owner);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, b.pos.x, b.pos.y, b.pos.z, 1, 0, 0, 0, 0);
        l.playSound(null, BlockPos.containing(b.pos), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4f, 0.8f);
        blast(l, b.pos, 4.0, b.mul, owner, b.fire);
        if (b.frag) {   // fragmentation : 5 éclats autour de l'impact
            for (int i = 0; i < 5; i++) {
                double a = i * Math.PI * 2 / 5 + Math.random();
                Vec3 q = b.pos.add(Math.cos(a) * 3.5, 0.5, Math.sin(a) * 3.5);
                l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                blast(l, q, 2.2, b.mul * 0.45f, owner, false);
            }
        }
    }

    private static void blast(ServerLevel l, Vec3 center, double r, float mul, Entity owner, boolean fire) {
        for (Entity t : l.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(r))) {
            if (t instanceof Player) continue;
            boolean target = RiftFortress.isTarget(t);
            if (!(t instanceof LivingEntity) && !target) continue;
            double d = t.getBoundingBox().getCenter().distanceTo(center);
            if (d > r + t.getBbWidth()) continue;
            float dmg = (float) (80 * mul * (1 - 0.7 * Math.min(1, d / r)));
            if (fire && t instanceof LivingEntity) t.setSecondsOnFire(6);
            boolean big = target || RiftTitan.isTitan(t) || t.getPersistentData().getBoolean("ws_rift_avatar");
            if (big) dmg *= 1.5f;
            if (target) RiftFortress.damageTarget(t, dmg);
            else ((LivingEntity) t).hurt(l.damageSources().explosion(null, owner), dmg);
        }
    }
}
