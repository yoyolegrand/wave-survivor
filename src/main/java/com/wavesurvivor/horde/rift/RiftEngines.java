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

        Engine(BlockPos lectern, boolean ballista, Vec3 muzzle) {
            this.lectern = lectern;
            this.ballista = ballista;
            this.muzzle = muzzle;
        }
    }

    private static final class Boulder {
        Vec3 pos, vel;
        UUID owner;
    }

    private static final List<Engine> ENGINES = new ArrayList<>();
    private static final List<Boulder> BOULDERS = new ArrayList<>();
    private static final Map<UUID, Long> msgGate = new HashMap<>();

    private RiftEngines() {}

    static void clear() {
        ENGINES.clear();
        BOULDERS.clear();
        msgGate.clear();
    }

    // ─── Construction (tous les engins regardent vers +X, la forteresse) ───

    static void build(ServerLevel l) {
        clear();
        int y = RiftManager.CENTER.getY() + 1;
        catapult(l, -9, y, -10);
        catapult(l, -9, y, 8);
        ballista(l, -12, y, -4);
        ballista(l, -12, y, 2);
    }

    private static void put(ServerLevel l, int x, int y, int z, BlockState st) {
        l.setBlock(new BlockPos(x, y, z), st, 2);
    }

    /** Catapulte : châssis 6 × 2 en bois sombre, roues de fer, montants, grande poutre, cuillère (chaudron), contrepoids, treuil (pupitre). */
    private static void catapult(ServerLevel l, int bx, int y, int bz) {
        BlockPos o = RiftManager.CENTER;
        int x0 = o.getX() + bx, z0 = o.getZ() + bz;
        BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
        for (int dx = 0; dx < 6; dx++) for (int dz = 0; dz < 2; dz++) put(l, x0 + dx, y, z0 + dz, planks);
        for (int dx : new int[]{1, 4}) {
            put(l, x0 + dx, y, z0 - 1, Blocks.IRON_BARS.defaultBlockState());
            put(l, x0 + dx, y, z0 + 2, Blocks.IRON_BARS.defaultBlockState());
        }
        for (int dz = 0; dz < 2; dz++) {
            put(l, x0 + 3, y + 1, z0 + dz, fence);
            put(l, x0 + 3, y + 2, z0 + dz, fence);
        }
        BlockState beam = Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int dx = 0; dx <= 4; dx++) put(l, x0 + dx, y + 3, z0, beam);
        put(l, x0 + 5, y + 2, z0, Blocks.CAULDRON.defaultBlockState());
        put(l, x0, y + 2, z0, Blocks.COBBLESTONE.defaultBlockState());
        put(l, x0, y + 2, z0 + 1, Blocks.COBBLESTONE.defaultBlockState());
        BlockPos lec = new BlockPos(x0, y + 1, z0);
        put(l, lec.getX(), lec.getY(), lec.getZ(), Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.EAST));
        ENGINES.add(new Engine(lec, false, new Vec3(x0 + 5.5, y + 3.5, z0 + 1.0)));
    }

    /** Baliste céleste : piédestal 3 × 3 de marbre (quartz) à coins dorés, fût, arc en « ailes » dorées, carreau lumineux. */
    private static void ballista(ServerLevel l, int bx, int y, int bz) {
        BlockPos o = RiftManager.CENTER;
        int x0 = o.getX() + bx, z0 = o.getZ() + bz;
        for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) {
            boolean corner = (dx != 1) && (dz != 1);
            put(l, x0 + dx, y, z0 + dz, corner ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.QUARTZ_BLOCK.defaultBlockState());
        }
        put(l, x0 + 1, y + 1, z0 + 1, Blocks.QUARTZ_PILLAR.defaultBlockState());
        put(l, x0 + 1, y + 2, z0 + 1, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        for (int dz : new int[]{0, 2}) {
            put(l, x0 + 2, y + 2, z0 + dz, Blocks.GOLD_BLOCK.defaultBlockState());
            put(l, x0 + 1, y + 2, z0 + dz, Blocks.WHITE_STAINED_GLASS.defaultBlockState());
            put(l, x0 + 2, y + 3, z0 + dz, Blocks.AMETHYST_BLOCK.defaultBlockState());
        }
        put(l, x0 + 2, y + 2, z0 + 1, Blocks.END_ROD.defaultBlockState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.EAST));
        BlockPos lec = new BlockPos(x0, y + 1, z0 + 1);
        put(l, lec.getX(), lec.getY(), lec.getZ(), Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.EAST));
        ENGINES.add(new Engine(lec, true, new Vec3(x0 + 3.2, y + 2.6, z0 + 1.5)));
    }

    // ─── Pilotage ───

    /** Clic droit sur un pupitre d'engin. @return vrai si le bloc est un engin (le clic est alors consommé). */
    static boolean onUse(ServerPlayer p, BlockPos pos, long now) {
        for (Engine e : ENGINES) {
            if (!e.lectern.equals(pos)) continue;
            if (!(p.level() instanceof ServerLevel l)) return true;
            if (e.ballista) useBallista(p, l, e, now); else useCatapult(p, l, e, now);
            return true;
        }
        return false;
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
            say(p, "§7Catapulte en rechargement : §f" + ((e.readyAt - now + 19) / 20) + " s", now);
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
        b.vel = dir.scale(p.isShiftKeyDown() ? 1.6 : 2.5);
        b.owner = p.getUUID();
        BOULDERS.add(b);
        e.readyAt = now + 600;
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
        e.nextShot = now + 10;
        Arrow a = new Arrow(EntityType.ARROW, l);
        a.setOwner(p);
        a.setPos(e.muzzle.x, e.muzzle.y, e.muzzle.z);
        a.setBaseDamage(1.5);                         // 6 dégâts à pleine vitesse
        a.pickup = AbstractArrow.Pickup.DISALLOWED;
        Vec3 look = p.getLookAngle();
        a.shoot(look.x, look.y, look.z, 4.0f, 0.0f);
        l.addFreshEntity(a);
        e.ammo--;
        l.playSound(null, BlockPos.containing(e.muzzle), SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 2.5f, 0.6f);
        l.sendParticles(ParticleTypes.END_ROD, e.muzzle.x, e.muzzle.y, e.muzzle.z, 3, 0.1, 0.1, 0.1, 0.02);
        if (e.ammo <= 0) {
            e.reloading = true;
            e.readyAt = now + 200;
            say(p, "§bBaliste vide : rechargement (clic pour recharger)", now);
        } else {
            p.displayClientMessage(Component.literal("§bBaliste §f" + e.ammo + " / 16"), true);
        }
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
        double r = 4.0;
        for (Entity t : l.getEntitiesOfClass(Entity.class, new AABB(b.pos, b.pos).inflate(r))) {
            if (t instanceof Player) continue;
            boolean target = RiftFortress.isTarget(t);
            if (!(t instanceof LivingEntity) && !target) continue;
            double d = t.getBoundingBox().getCenter().distanceTo(b.pos);
            if (d > r + t.getBbWidth()) continue;
            float dmg = (float) (80 * (1 - 0.7 * Math.min(1, d / r)));
            boolean big = target || RiftTitan.isTitan(t) || t.getPersistentData().getBoolean("ws_rift_avatar");
            if (big) dmg *= 1.5f;
            if (target) RiftFortress.damageTarget(t, dmg);
            else ((LivingEntity) t).hurt(l.damageSources().explosion(null, owner), dmg);
        }
    }
}
