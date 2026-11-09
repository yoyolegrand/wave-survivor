package com.wavesurvivor.horde.rift;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.compat.PehkuiCompat;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.network.ShakePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongConsumer;

/**
 * 2.0 (jalon 5) — LE TITAN DE LA FAILLE. Une entité quelconque (réglage) agrandie par Pehkui, immobile hors de l'arène, au-delà de la forteresse.
 * Il attaque de loin avec des compétences annoncées (zone au sol qui se remplit, texte, lueur) : pluie de météores, onde de choc, rayon,
 * rugissement, invocation d'avatars. Tuer un avatar retire un pourcentage de ses PV max. 4 phases (75 / 50 / 25 % de PV).
 * Aucune compétence ne dépend d'une animation du modèle : tout est dessiné par des particules et des sons.
 */
final class RiftTitan {

    private static final Random RNG = new Random();
    private static final DustParticleOptions RED = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.15f, 0.2f), 1.4f);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new org.joml.Vector3f(0.65f, 0.2f, 0.95f), 1.4f);
    private static final int[] GAP = {200, 170, 140, 110};

    /** Une attaque annoncée : dessinée tous les 4 ticks jusqu'à {@code fireAt}, puis déclenchée. */
    private static final class Pending {
        long fireAt;
        String name;
        LongConsumer draw;
        Runnable fire;
    }

    private static LivingEntity titan;
    private static UUID titanId;
    private static ServerBossEvent bar;
    private static final List<Pending> pending = new ArrayList<>();
    private static final Set<UUID> avatars = new HashSet<>();
    private static int phase;
    private static long nextSkillAt, emergeUntil, windowUntil;
    private static float windowDealt;
    private static boolean dead, emerging;
    private static double baseX, baseY, baseZ;
    private static float maxHp;
    private static HordeConfigMultiData.RiftSettings rs;
    private static int siegeRadius;

    private RiftTitan() {}

    static boolean active() { return titan != null && !dead; }

    static boolean isDead() { return dead; }

    static boolean isTitan(Entity e) { return titanId != null && e.getUUID().equals(titanId); }

    static float maxHp() { return maxHp; }

    // ─── Apparition ───

    /** Distance (vers le sud) entre le centre de l'île et le Titan : l'île + une marge (réglable, 12 par défaut) + la demi-largeur du Titan agrandi. */
    static double distance(HordeConfigMultiData.RiftSettings settings, int sr) {
        EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(settings.titanEntity));
        double w = t != null ? t.getWidth() : 1.4;
        return Math.max(30, sr + Math.max(4, settings.titanGap) + w * settings.titanScale * 0.5);
    }

    static boolean spawn(ServerLevel l, HordeConfigMultiData.RiftSettings settings, int players, int sr) {
        rs = settings;
        siegeRadius = sr;
        dead = false;
        phase = 0;
        pending.clear();
        avatars.clear();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(settings.titanEntity));
        Entity e = type.create(l);
        if (!(e instanceof Mob)) e = EntityType.IRON_GOLEM.create(l);
        Mob mob = (Mob) e;
        baseX = RiftManager.CENTER.getX() + 0.5;
        baseZ = RiftManager.CENTER.getZ() + distance(settings, sr) + 0.5;   // au SUD de l'île : à portée des engins de siège
        baseY = RiftManager.CENTER.getY() - 8;
        maxHp = (float) (settings.titanHp * (1.0 + 0.6 * Math.max(0, players - 1)));
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setPersistenceRequired();
        mob.setInvulnerable(true);   // le temps de l'éveil
        if (mob.getAttribute(Attributes.MAX_HEALTH) != null) mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHp);
        if (mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        mob.setHealth(maxHp);
        mob.setCustomName(Component.literal("§5§lTitan de la Faille"));
        mob.setCustomNameVisible(false);
        mob.getPersistentData().putBoolean("ws_rift_titan", true);
        PehkuiCompat.setScale(mob, settings.titanScale);
        mob.moveTo(baseX, baseY - 90, baseZ, 180f, 0f);   // regarde vers le nord (l'île)
        mob.setYHeadRot(180f);
        mob.yBodyRot = 180f;
        l.addFreshEntity(mob);
        titan = mob;
        titanId = mob.getUUID();
        emerging = true;
        emergeUntil = l.getServer().getTickCount() + 220;
        bar = new ServerBossEvent(Component.literal("§5§lTitan de la Faille"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setProgress(1f);
        for (ServerPlayer p : l.players()) bar.addPlayer(p);
        for (ServerPlayer p : l.players()) {
            ShakePacket.send(p, 220, 1.3f);
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7Quelque chose monte de l'abîme…")));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§5§lLE TITAN S'ÉVEILLE")));
        }
        l.playSound(null, RiftManager.CENTER, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 8f, 0.4f);
        WaveSurvivorMod.LOGGER.info("[Faille] Titan {} (x{}, {} PV) en {}", settings.titanEntity, settings.titanScale, maxHp, mob.blockPosition());
        return true;
    }

    // ─── Boucle ───

    /** À appeler chaque tick tant que le Titan existe. */
    static void tick(ServerLevel l, long now) {
        if (titan == null) return;
        if (titan.isRemoved() || !titan.isAlive()) {
            if (!dead) onTitanDeath(l);
            return;
        }
        if (emerging) {
            double y = Math.min(baseY, titan.getY() + 0.45);
            titan.setPos(baseX, y, baseZ);
            if (now % 20 == 0) {
                l.sendParticles(ParticleTypes.LARGE_SMOKE, baseX, y + 2, baseZ, 30, 8, 3, 8, 0.05);
                l.playSound(null, BlockPos.containing(baseX, RiftManager.CENTER.getY(), baseZ), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 6f, 0.5f);
            }
            if (now >= emergeUntil || y >= baseY) {
                emerging = false;
                titan.setInvulnerable(false);
                titan.setPos(baseX, baseY, baseZ);
                nextSkillAt = now + 100;
                announce(l, "§5§l⚔ Le Titan vous a vus !");
                summonAvatars(l);
            }
            return;
        }
        if (windowUntil > 0 && now >= windowUntil) {
            windowUntil = 0;
            titan.setGlowingTag(false);
            if (bar != null) bar.setColor(BossEvent.BossBarColor.PURPLE);
            announce(l, "§7La brèche dans sa garde se referme.");
        }
        // Barre de vie
        if (now % 10 == 0 && bar != null) {
            bar.setProgress(Math.max(0f, Math.min(1f, titan.getHealth() / maxHp)));
            for (ServerPlayer p : l.players()) bar.addPlayer(p);
        }
        // Phases (75 / 50 / 25 %)
        float frac = titan.getHealth() / maxHp;
        int ph = frac > 0.75f ? 0 : frac > 0.5f ? 1 : frac > 0.25f ? 2 : 3;
        if (ph != phase) {
            phase = ph;
            announce(l, "§5§l⚔ Le Titan entre en fureur ! (phase " + (phase + 1) + "/4)");
            for (ServerPlayer p : l.players()) ShakePacket.send(p, 60, 1.5f);
            l.playSound(null, RiftManager.CENTER, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 8f, 0.5f);
            summonAvatars(l);
            nextSkillAt = now + 80;
        }
        // Attaques annoncées en cours
        for (Pending p : new ArrayList<>(pending)) {
            if (now >= p.fireAt) {
                pending.remove(p);
                titan.setGlowingTag(false);
                p.fire.run();
                if (p.name.startsWith("Onde") || p.name.startsWith("Rugissement")) openWindow(l, now); // grosse attaque : le Titan est à découvert
            } else if (now % 4 == 0) {
                p.draw.accept(now);
                if (now % 10 == 0) for (ServerPlayer pl : alive(l)) pl.displayClientMessage(Component.literal("§c⚠ " + p.name), true);
            }
        }
        // Choix de la prochaine attaque
        if (now >= nextSkillAt && pending.size() < 2) {
            nextSkillAt = now + GAP[phase] + RNG.nextInt(40);
            List<ServerPlayer> alive = alive(l);
            if (!alive.isEmpty()) chooseSkill(l, now, alive);
        }
    }

    private static List<ServerPlayer> alive(ServerLevel l) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : l.players()) if (!p.isSpectator() && p.isAlive()) out.add(p);
        return out;
    }

    private static void announce(ServerLevel l, String msg) {
        for (ServerPlayer p : l.players()) p.sendSystemMessage(Component.literal(msg));
    }

    private static void chooseSkill(ServerLevel l, long now, List<ServerPlayer> alive) {
        List<Integer> pool = new ArrayList<>();
        pool.add(0); pool.add(0);                    // météores
        if (phase >= 1) { pool.add(1); pool.add(1); } // onde de choc
        if (phase >= 2) { pool.add(2); }             // rayon
        if (phase >= 3) { pool.add(3); pool.add(2); } // rugissement
        if (RNG.nextInt(6) == 0 && avatars.size() < 4) pool.add(4);
        switch (pool.get(RNG.nextInt(pool.size()))) {
            case 0 -> meteors(l, now, alive);
            case 1 -> shockwave(l, now);
            case 2 -> beam(l, now);
            case 3 -> roar(l, now);
            default -> summonAvatars(l);
        }
    }

    private static int groundY(ServerLevel l, int x, int z) {
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static void ring(ServerLevel l, double cx, double y, double cz, double r, DustParticleOptions c, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            l.sendParticles(c, cx + Math.cos(a) * r, y + 0.15, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private static void schedule(ServerLevel l, long now, int delay, String name, LongConsumer draw, Runnable fire) {
        Pending p = new Pending();
        p.fireAt = now + delay;
        p.name = name;
        p.draw = draw;
        p.fire = fire;
        pending.add(p);
        titan.setGlowingTag(true);
        l.playSound(null, RiftManager.CENTER, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3f, 0.6f);
    }

    // ─── Compétences ───

    /** Pluie de météores : cercles rouges sous les joueurs, puis impacts. */
    private static void meteors(ServerLevel l, long now, List<ServerPlayer> alive) {
        int n = 3 + alive.size();
        List<double[]> spots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ServerPlayer t = alive.get(RNG.nextInt(alive.size()));
            double x = t.getX() + (RNG.nextDouble() - 0.5) * 5, z = t.getZ() + (RNG.nextDouble() - 0.5) * 5;
            int gy = groundY(l, (int) Math.floor(x), (int) Math.floor(z));
            if (gy <= l.getMinBuildHeight() + 1) continue; // au-dessus du vide
            spots.add(new double[]{x, gy, z});
        }
        if (spots.isEmpty()) return;
        schedule(l, now, 50, "Pluie de météores !", t -> {
            for (double[] s : spots) ring(l, s[0], s[1], s[2], 3.0, RED, 20);
        }, () -> {
            for (double[] s : spots) {
                for (int h = 0; h < 30; h += 3) l.sendParticles(ParticleTypes.FLAME, s[0], s[1] + h, s[2], 3, 0.3, 0.2, 0.3, 0.02);
                l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, s[0], s[1] + 1, s[2], 1, 0, 0, 0, 0);
                l.playSound(null, BlockPos.containing(s[0], s[1], s[2]), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4f, 0.7f);
                for (ServerPlayer p : alive(l)) {
                    if (Math.hypot(p.getX() - s[0], p.getZ() - s[2]) <= 3.2 && Math.abs(p.getY() - s[1]) < 4) p.hurt(l.damageSources().explosion(null, titan), 12f);
                }
            }
        });
    }

    /** Onde de choc sur un quart de l'arène : on la fuit ou on saute. */
    private static void shockwave(ServerLevel l, long now) {
        int q = RNG.nextInt(4);
        BlockPos c = RiftManager.CENTER;
        double reach = Math.max(RiftManager.radius(), siegeRadius) + 4;
        schedule(l, now, 60, "Onde de choc ! Quittez la zone ou sautez !", t -> {
            for (double r = 2; r <= reach; r += 3) {
                for (int i = 0; i < 6; i++) {
                    double a = (q + (i + 0.5) / 6.0) * Math.PI / 2;
                    l.sendParticles(RED, c.getX() + 0.5 + Math.cos(a) * r, c.getY() + 1.1, c.getZ() + 0.5 + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
        }, () -> {
            l.playSound(null, c, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 6f, 0.5f);
            for (double r = 1; r <= reach; r += 1.5) {
                for (int i = 0; i < 10; i++) {
                    double a = (q + (i + 0.5) / 10.0) * Math.PI / 2;
                    l.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5 + Math.cos(a) * r, c.getY() + 1.2, c.getZ() + 0.5 + Math.sin(a) * r, 1, 0.1, 0.2, 0.1, 0.05);
                }
            }
            for (ServerPlayer p : alive(l)) {
                double dx = p.getX() - c.getX() - 0.5, dz = p.getZ() - c.getZ() - 0.5;
                double ang = (Math.atan2(dz, dx) + Math.PI * 2) % (Math.PI * 2);
                if ((int) Math.floor(ang / (Math.PI / 2)) != q) continue;
                if (Math.hypot(dx, dz) > reach) continue;
                if (!p.onGround()) continue; // en l'air : on a sauté par-dessus
                p.hurt(l.damageSources().magic(), 14f);
                p.setDeltaMovement(p.getDeltaMovement().add(0, 0.7, 0));
                p.hurtMarked = true;
            }
        });
    }

    /** Rayon qui balaie l'arène : on s'abrite derrière un mur. */
    private static void beam(ServerLevel l, long now) {
        BlockPos c = RiftManager.CENTER;
        double ex = baseX, ey = titan.getEyeY(), ez = baseZ;
        double span = Math.max(RiftManager.radius(), siegeRadius) * 0.9;
        boolean flip = RNG.nextBoolean();
        schedule(l, now, 50, "Rayon du Titan ! Mettez-vous à couvert !", t -> {
            double zz = (flip ? -span : span);
            drawLine(l, ex, ey, ez, c.getX() + 0.5, c.getY() + 1.5, c.getZ() + 0.5 + zz, RED, 2.5);
        }, () -> {
            final int dur = 80;
            for (int k = 0; k < dur; k++) {
                final int kk = k;
                RiftManager.later(l.getServer(), k, () -> {
                    if (!active()) return;
                    double f = kk / (double) dur;
                    double zz = (flip ? -span : span) * (1 - 2 * f);
                    double px = c.getX() + 0.5, py = c.getY() + 1.5, pz = c.getZ() + 0.5 + zz;
                    if (kk % 2 == 0) drawLine(l, ex, ey, ez, px, py, pz, VIOLET, 3.0);
                    if (kk % 5 == 0) {
                        l.playSound(null, BlockPos.containing(px, py, pz), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 4f, 0.5f);
                        Vec3 from = new Vec3(ex, ey, ez), to = new Vec3(px, py, pz);
                        for (ServerPlayer p : alive(l)) {
                            Vec3 pp = p.position().add(0, 1, 0);
                            Vec3 d = to.subtract(from);
                            double tt = Math.max(0, Math.min(1, pp.subtract(from).dot(d) / d.lengthSqr()));
                            if (pp.distanceTo(from.add(d.scale(tt))) > 1.9) continue;
                            var hit = l.clip(new ClipContext(from, p.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, titan));
                            if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceTo(p.getEyePosition()) > 1.5) continue; // à couvert
                            p.hurt(l.damageSources().magic(), 9f);
                        }
                    }
                });
            }
        });
    }

    private static void drawLine(ServerLevel l, double x0, double y0, double z0, double x1, double y1, double z1, DustParticleOptions c, double step) {
        double len = Math.sqrt(sq(x1 - x0) + sq(y1 - y0) + sq(z1 - z0));
        int n = (int) Math.max(2, len / step);
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            l.sendParticles(c, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, z0 + (z1 - z0) * f, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private static double sq(double v) { return v * v; }

    /** Rugissement : tout le monde est affaibli. */
    private static void roar(ServerLevel l, long now) {
        schedule(l, now, 40, "Rugissement du Titan !", t -> l.sendParticles(ParticleTypes.SONIC_BOOM, baseX - 15, baseY + 30, baseZ, 1, 0, 0, 0, 0), () -> {
            l.playSound(null, RiftManager.CENTER, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 10f, 0.4f);
            for (ServerPlayer p : alive(l)) {
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
                p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 0));
                ShakePacket.send(p, 50, 1.4f);
            }
        });
    }

    /** Invoque des avatars : mini-boss dont la mort retire un pourcentage des PV max du Titan. */
    private static void summonAvatars(ServerLevel l) {
        List<ServerPlayer> alive = alive(l);
        int n = Math.min(4, 1 + alive.size() / 2 + phase / 2);
        double hp = rs.titanAvatarHp * (1.0 + 0.35 * Math.max(0, alive.size() - 1));
        for (int i = 0; i < n; i++) {
            Entity e = EntityType.WITHER_SKELETON.create(l);
            if (!(e instanceof Mob m)) continue;
            double x = RiftManager.CENTER.getX() + (RNG.nextDouble() - 0.5) * 14, z = RiftManager.CENTER.getZ() + siegeRadius - 4 + RNG.nextInt(3);
            m.moveTo(x, RiftManager.CENTER.getY() + 1, z, 0f, 0f);
            if (m.getAttribute(Attributes.MAX_HEALTH) != null) m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
            if (m.getAttribute(Attributes.ATTACK_DAMAGE) != null) m.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(9);
            m.setHealth((float) hp);
            m.setCustomName(Component.literal("§5§lAvatar du Titan"));
            m.setCustomNameVisible(true);
            m.setGlowingTag(true);
            m.setPersistenceRequired();
            m.getPersistentData().putBoolean("ws_rift_avatar", true);
            l.addFreshEntity(m);
            avatars.add(m.getUUID());
            l.sendParticles(ParticleTypes.REVERSE_PORTAL, x, RiftManager.CENTER.getY() + 2, z, 30, 0.5, 1, 0.5, 0.2);
        }
        if (n > 0) announce(l, "§5Le Titan invoque " + n + " avatar" + (n > 1 ? "s" : "") + " : les abattre l'affaiblit !");
    }

    // ─── Dégâts et mort ───

    /** Plafond de dégâts par coup (dégâts au Titan plafonnés à un pourcentage de ses PV max). */
    static float capDamage(float amount, long now) {
        if (windowUntil > 0 && now < windowUntil) {
            // Fenêtre de vulnérabilité : dégâts ×1,5, mais total plafonné pour la fenêtre entière
            float room = Math.max(0f, (float) (maxHp * rs.titanWindowCapPercent / 100.0) - windowDealt);
            float out = Math.min(amount * 1.5f, room);
            windowDealt += out;
            return out;
        }
        // Hors fenêtre : garde du Titan, dégâts réduits de moitié et plafonnés par coup
        float cap = (float) (maxHp * rs.titanDamageCapPercent / 100.0);
        return Math.min(amount * 0.5f, Math.max(1f, cap));
    }

    /** Ouvre une fenêtre de vulnérabilité (le Titan brille, sa barre devient rouge). */
    private static void openWindow(ServerLevel l, long now) {
        if (titan == null || rs.titanWindowSeconds <= 0) return;
        windowUntil = now + rs.titanWindowSeconds * 20L;
        windowDealt = 0f;
        titan.setGlowingTag(true);
        if (bar != null) bar.setColor(BossEvent.BossBarColor.RED);
        announce(l, "§c§l⚔ Le Titan est à découvert ! Frappez maintenant (" + rs.titanWindowSeconds + " s) !");
    }

    static void onAvatarDeath(ServerLevel l, Entity avatar) {
        if (!avatars.remove(avatar.getUUID()) || titan == null || !titan.isAlive()) return;
        float cut = (float) (maxHp * rs.titanAvatarPercent / 100.0);
        titan.setHealth(Math.max(1f, titan.getHealth() - cut));
        l.sendParticles(ParticleTypes.SONIC_BOOM, avatar.getX(), avatar.getY() + 1, avatar.getZ(), 1, 0, 0, 0, 0);
        l.playSound(null, avatar.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 3f, 1.2f);
        announce(l, "§aUn avatar est tombé : le Titan perd " + rs.titanAvatarPercent + " % de ses PV max !");
    }

    private static void onTitanDeath(ServerLevel l) {
        dead = true;
        pending.clear();
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        for (ServerPlayer p : l.players()) ShakePacket.send(p, 120, 1.6f);
        l.playSound(null, RiftManager.CENTER, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 10f, 0.4f);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, baseX, baseY + 25, baseZ, 6, 8, 12, 8, 0);
        for (UUID id : new ArrayList<>(avatars)) {
            Entity e = l.getEntity(id);
            if (e != null) e.discard();
        }
        avatars.clear();
    }

    /** Retire le Titan, sa barre et ses avatars (fin de la Faille). */
    static void clear(ServerLevel l) {
        if (titan != null && !titan.isRemoved()) titan.discard();
        for (UUID id : new ArrayList<>(avatars)) {
            Entity e = l.getEntity(id);
            if (e != null) e.discard();
        }
        avatars.clear();
        pending.clear();
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        titan = null;
        titanId = null;
        dead = false;
        emerging = false;
        windowUntil = 0;
        windowDealt = 0f;
    }
}
