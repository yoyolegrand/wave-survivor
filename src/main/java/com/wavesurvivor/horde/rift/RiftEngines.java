package com.wavesurvivor.horde.rift;

import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.entity.SiegeEngineEntity;
import com.wavesurvivor.registry.ModEntities;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 2.0 — ENGINS DE SIÈGE de la Faille : catapultes (2 × 6, 3 de haut) et balistes célestes (3 × 3, 3 de haut), achetées à la boutique du Monolithe
 * et posées par les joueurs. Ce sont des ENTITÉS ({@link SiegeEngineEntity}) rendues par un modèle JSON (niveaux 1 et 2) ; elles regardent vers le SUD.
 * Pilotage : clic droit sur l'engin.
 * <ul>
 *   <li>Catapulte : 1 pierre taillée par boulet, rechargement 30 s (24 s au niveau 2), tir en cloche dans la direction du regard
 *       (accroupi : tir court). Dégâts de zone 80 au centre → 30 % au bord (rayon 4), ×1,5 contre le Titan, les avatars et les structures.</li>
 *   <li>Baliste céleste : chargeur de 16 flèches prises dans l'inventaire, un tir toutes les 0,5 s (0,4 s au niveau 2), rechargement 10 s (8 s),
 *       6 de dégâts par flèche ; elle suit le regard du dernier tireur.</li>
 * </ul>
 * Cadences réglables dans l'éditeur (page Titan). Amélioration : clic droit avec un lingot d'or (niveau 2, puis variante de niveau 3).
 */
final class RiftEngines {

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
    /** Joueur → engin dont il choisit la variante (boutons du chat). */
    private static final Map<UUID, UUID> choosing = new HashMap<>();
    private static final List<Boulder> BOULDERS = new ArrayList<>();
    private static final Map<UUID, Long> msgGate = new HashMap<>();

    private RiftEngines() {}

    static void clear() {
        BOULDERS.clear();
        msgGate.clear();
        choosing.clear();
    }

    private static String nameOf(SiegeEngineEntity e) {
        boolean b = e.isBallista();
        String base = b ? "Baliste céleste" : "Catapulte";
        int lv = e.engineLevel();
        if (lv == 2) return base + (b ? " consacrée" : " renforcée");
        if (lv >= 3 && e.variant >= 1) return base + " " + VARIANTS[b ? 1 : 0][e.variant - 1];
        return base;
    }

    // ─── Cadences (réglables) ───

    private static HordeConfigMultiData.RiftSettings rs() { return RiftManager.settingsPublic(); }

    /** Rechargement de la catapulte (ticks) : 30 s niveau 1, 24 s niveau 2 et variantes (le Trébuchet est plus lent). */
    private static int catapultReload(SiegeEngineEntity e) {
        if (e.engineLevel() >= 3 && e.variant == 3) return 750;
        return Math.max(20, e.engineLevel() >= 2 ? rs().catapultReload2 : rs().catapultReload1);
    }

    private static int ballistaInterval(SiegeEngineEntity e) {
        if (e.engineLevel() >= 3 && e.variant == 2) return 4;   // Solaire : rayon à haute cadence
        return Math.max(1, e.engineLevel() >= 2 ? rs().ballistaInterval2 : rs().ballistaInterval1);
    }

    private static int ballistaReload(SiegeEngineEntity e) {
        return Math.max(20, e.engineLevel() >= 2 ? rs().ballistaReload2 : rs().ballistaReload1);
    }

    // ─── Pose ───

    /**
     * Pose un engin acheté à la boutique (tourné vers le sud). {@code at} = bloc libre au-dessus du sol visé.
     * @return 0 posé · 1 pas de place / sol instable · 2 hors de l'île ou dimension incorrecte
     */
    static int place(ServerLevel l, BlockPos at, boolean ballista) {
        if (l.dimension() != RiftManager.RIFT_DIM) return 2;
        double[] f = SiegeEngineEntity.footprint(ballista ? SiegeEngineEntity.BALLISTA : SiegeEngineEntity.CATAPULT);
        int w = (int) f[0], d = (int) f[2], y = at.getY();
        int ox = at.getX() - (ballista ? 1 : 0), oz = at.getZ() - (ballista ? 1 : 2);   // coin nord-ouest de l'emprise
        int rMax = Math.max(4, RiftManager.islandRadiusNow() - 2);
        long r2 = (long) rMax * rMax;
        for (int x = ox; x < ox + w; x++) for (int z = oz; z < oz + d; z++) {
            long dx = x - RiftManager.CENTER.getX(), dz = z - RiftManager.CENTER.getZ();
            if (dx * dx + dz * dz > r2) return 2;
            if (l.getBlockState(new BlockPos(x, y - 1, z)).isAir()) return 1;
            for (int h = 0; h < 3; h++) {
                BlockState s = l.getBlockState(new BlockPos(x, y + h, z));
                if (!s.isAir() && !s.canBeReplaced()) return 1;
            }
        }
        AABB box = new AABB(ox, y, oz, ox + w, y + 3, oz + d);
        if (!l.getEntitiesOfClass(SiegeEngineEntity.class, box).isEmpty()) return 1;   // pas sur un autre engin
        SiegeEngineEntity e = ModEntities.SIEGE_ENGINE.get().create(l);
        if (e == null) return 1;
        e.moveTo(ox + w / 2.0, y, oz + d / 2.0, 0f, 0f);
        e.setKind(ballista ? SiegeEngineEntity.BALLISTA : SiegeEngineEntity.CATAPULT);
        e.setEngineLevel(1);
        e.setAmmo(ballista ? 0 : 1);
        e.setReloadTicks(ballista ? ballistaReload(e) : catapultReload(e));
        e.setAim(0f, -18f);
        e.readyAt = 0;
        l.addFreshEntity(e);
        l.playSound(null, e.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.2f, 0.7f);
        l.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 20, w / 2.0, 0.3, d / 2.0, 0.02);
        return 0;
    }

    // ─── Pilotage ───

    /** Clic droit sur un engin. */
    static void onUse(ServerPlayer p, SiegeEngineEntity e) {
        if (!(p.level() instanceof ServerLevel l)) return;
        long now = l.getGameTime();
        if (p.getMainHandItem().is(Items.GOLD_INGOT)) { upgrade(p, e, now); return; } // lingot d'or en main : amélioration
        if (e.isBallista()) useBallista(p, l, e, now); else useCatapult(p, l, e, now);
    }

    /** Clic droit avec un lingot d'or : niveau 2 (payant), puis choix d'une variante au niveau 3. */
    private static void upgrade(ServerPlayer p, SiegeEngineEntity e, long now) {
        int k = e.isBallista() ? 1 : 0;
        if (e.engineLevel() == 1) {
            if (!com.wavesurvivor.altar.AltarDefense.trySpend(p, UP2[k])) return;
            e.setEngineLevel(2);
            e.setReloadTicks(e.isBallista() ? ballistaReload(e) : catapultReload(e));
            if (p.level() instanceof ServerLevel l) l.playSound(null, e.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 1.2f);
            say(p, "§a" + nameOf(e) + " ! Clic droit avec un lingot d'or pour choisir sa variante.", now);
        } else if (e.engineLevel() == 2) {
            choosing.put(p.getUUID(), e.getUUID());
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
        UUID id = choosing.get(p.getUUID());
        if (id == null || i < 0 || i > 2 || !(p.level() instanceof ServerLevel l) || !(l.getEntity(id) instanceof SiegeEngineEntity e)) return;
        if (e.engineLevel() != 2 || p.distanceToSqr(e) > 400) return;
        if (!com.wavesurvivor.altar.AltarDefense.trySpend(p, UP3[e.isBallista() ? 1 : 0])) return;
        e.setEngineLevel(3);
        e.variant = i + 1;
        e.setReloadTicks(e.isBallista() ? ballistaReload(e) : catapultReload(e));
        choosing.remove(p.getUUID());
        l.playSound(null, e.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1f);
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

    /** Bouche de la catapulte : le bout de la cuillère (modèle : centre du boulet à 1 ; 2,2 ; 5,25 depuis le coin nord-ouest). */
    private static Vec3 catapultMuzzle(SiegeEngineEntity e) {
        return new Vec3(e.getX(), e.getY() + 2.2, e.getZ() + 2.25);
    }

    private static void useCatapult(ServerPlayer p, ServerLevel l, SiegeEngineEntity e, long now) {
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
        boolean tre = e.engineLevel() >= 3 && e.variant == 3;
        Boulder b = new Boulder();
        b.pos = catapultMuzzle(e);
        double speed = (p.isShiftKeyDown() ? 1.6 : 2.5) * (e.engineLevel() >= 2 ? 1.1 : 1.0) * (tre ? 1.25 : 1.0);
        b.vel = dir.scale(speed);
        b.owner = p.getUUID();
        b.mul = (e.engineLevel() >= 2 ? 1.25f : 1f) * (tre ? 1.6f : 1f);
        b.fire = e.engineLevel() >= 3 && e.variant == 1;
        b.frag = e.engineLevel() >= 3 && e.variant == 2;
        BOULDERS.add(b);
        int reload = catapultReload(e);
        e.readyAt = now + reload;
        e.markFired(now);
        e.setReloadTicks(reload);
        e.setAmmo(0);
        l.playSound(null, BlockPos.containing(b.pos), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 3f, 0.6f);
        l.playSound(null, BlockPos.containing(b.pos), SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 3f, 0.5f);
        say(p, "§6" + nameOf(e) + " : tir ! Rechargement " + (reload / 20) + " s", now);
    }

    private static void useBallista(ServerPlayer p, ServerLevel l, SiegeEngineEntity e, long now) {
        // Elle suit le regard du dernier tireur (visée horizontale et verticale)
        e.setAim(p.getYRot(), Math.max(-60f, Math.min(40f, p.getXRot())));
        if (e.reloading) {
            if (now < e.readyAt) {
                say(p, "§7Baliste en rechargement : §f" + ((e.readyAt - now + 19) / 20) + " s", now);
                return;
            }
            e.reloading = false;
        }
        if (e.ammo() <= 0) {
            int n = p.isCreative() ? 16 : take(p, Items.ARROW, 16);
            if (n <= 0) {
                say(p, "§cIl faut des flèches pour charger la baliste.", now);
                return;
            }
            int reload = ballistaReload(e);
            e.setAmmo(n);
            e.reloading = true;
            e.readyAt = now + reload;
            e.markReload(now, reload);
            l.playSound(null, e.blockPosition(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.BLOCKS, 2f, 0.7f);
            say(p, "§bBaliste : chargement de " + n + " flèches (" + (reload / 20) + " s)…", now);
            return;
        }
        if (now < e.nextShot) return;
        boolean solar = e.engineLevel() >= 3 && e.variant == 2, judge = e.engineLevel() >= 3 && e.variant == 3, seraph = e.engineLevel() >= 3 && e.variant == 1;
        e.nextShot = now + ballistaInterval(e);
        Vec3 look = p.getLookAngle();
        Vec3 muzzle = new Vec3(e.getX(), e.getY() + 1.47, e.getZ()).add(look.scale(1.5));
        if (solar || judge) {
            beam(p, l, muzzle, look, solar ? 5f : 4f, judge);   // rayon de lumière (Solaire) ou rayon qui marque (Jugement)
        } else {
            Arrow a = new Arrow(EntityType.ARROW, l);
            a.setOwner(p);
            a.setPos(muzzle.x, muzzle.y, muzzle.z);
            a.setBaseDamage(e.engineLevel() >= 2 ? 1.9 : 1.5);   // 6 dégâts à pleine vitesse (7,6 consacrée)
            if (seraph) a.setPierceLevel((byte) 4);                 // carreau perforant
            a.pickup = AbstractArrow.Pickup.DISALLOWED;
            a.shoot(look.x, look.y, look.z, 4.0f, 0.0f);
            l.addFreshEntity(a);
        }
        e.setAmmo(e.ammo() - 1);
        e.markFired(now);
        l.playSound(null, BlockPos.containing(muzzle), SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 2.5f, 0.6f);
        l.sendParticles(ParticleTypes.END_ROD, muzzle.x, muzzle.y, muzzle.z, 3, 0.1, 0.1, 0.1, 0.02);
        if (e.ammo() <= 0) {
            say(p, "§bBaliste vide : clic pour recharger", now);
        } else {
            p.displayClientMessage(Component.literal("§b" + nameOf(e) + " §f" + e.ammo() + " / 16"), true);
        }
    }

    /** Tir instantané (rayon) : touche tout ce qui est sur la ligne ; {@code mark} : la cible est marquée (+25 % de dégâts subis, 8 s). */
    private static void beam(ServerPlayer p, ServerLevel l, Vec3 from, Vec3 dir, float dmg, boolean mark) {
        Vec3 to = from.add(dir.scale(70));
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
            if (t instanceof Player || t instanceof SiegeEngineEntity) continue;
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
            if (t instanceof Player || t instanceof SiegeEngineEntity) continue;
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
