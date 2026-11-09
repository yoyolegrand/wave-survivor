package com.wavesurvivor.horde.rift;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.compat.PehkuiCompat;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.network.ShakePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
    /** Charge sur l'île : 0 repos, 1 annonce, 2 élan, 3 stun, 4 corps à corps, 5 retour. */
    private static int charge;
    private static long chargeAt, nextChargeAt, nextMeleeAt;
    private static boolean chargeDue, stunning;
    private static double chargeTargetZ;
    private static double[] zone;   // {xMin, xMax, zMin, zMax} de la zone d'impact (monde)
    private static final List<double[]> strikes = new ArrayList<>();   // coups au corps à corps annoncés : {x, z, tir à (tick)}
    private static long nextSkillAt, emergeUntil, windowUntil;
    private static float windowDealt;
    private static boolean dead, emerging;
    private static double baseX, baseY, baseZ;
    private static float maxHp;
    private static HordeConfigMultiData.RiftSettings rs;
    private static int siegeRadius;

    private RiftTitan() {}

    static boolean active() { return titan != null && !dead; }

    /** L'entité du Titan (cible de la tour-catapulte), ou null. */
    static LivingEntity entity() { return titan != null && !dead && titan.isAlive() ? titan : null; }

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
        // Évite le gros lag : un Titan géant testait sans cesse des milliers de blocs autour de lui (« dans un mur ? »), ce qui chargeait
        // de force des dizaines de chunks vides à chaque tick. Sans collisions, ce test est sauté.
        mob.noPhysics = true;
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
        charge = 0;
        chargeDue = false;
        stunning = false;
        strikes.clear();
        nextChargeAt = l.getServer().getTickCount() + 220 + Math.max(10, settings.titanChargeInterval) * 20L;
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
        double real = PehkuiCompat.getScale(mob);
        WaveSurvivorMod.LOGGER.info("[Faille] Titan {} (demandé x{}, appliqué x{}, {} PV) en {}", settings.titanEntity, settings.titanScale, real, maxHp, mob.blockPosition());
        if (real > 0 && real < settings.titanScale * 0.9) {
            WaveSurvivorMod.LOGGER.warn("[Faille] Pehkui limite la taille du Titan à x{} (demandé x{}) : vérifier la configuration de Pehkui.", real, settings.titanScale);
        }
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
        tickMeteors(l, now);
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
            if (rs.titanCharge && phase >= 1) chargeDue = true;   // chaque palier de PV (75 / 50 / 25 %) déclenche une charge
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
        chargeTick(l, now);
        if (now >= nextSkillAt && pending.size() < 2 && charge == 0) {
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
        if (rs.titanMonolithDamage > 0 && AltarDefenseHook.alive()) { if (phase >= 1) pool.add(5); if (phase >= 3) pool.add(5); } // poing contre le Monolithe
        if (RNG.nextInt(6) == 0 && avatars.size() < 4) pool.add(4);
        switch (pool.get(RNG.nextInt(pool.size()))) {
            case 0 -> meteors(l, now, alive);
            case 1 -> shockwave(l, now);
            case 2 -> beam(l, now);
            case 3 -> roar(l, now);
            case 5 -> monolithFist(l, now);
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
    /** Petit relais : le Monolithe est-il encore défendu (session de défense active) ? */
    private static final class AltarDefenseHook {
        static boolean alive() { return com.wavesurvivor.altar.AltarDefense.isActive(); }
    }

    /** Le Titan abat son poing sur le Monolithe : annoncé (cercle rouge sur le Monolithe), puis coup porté à son intégrité. */
    private static void monolithFist(ServerLevel l, long now) {
        BlockPos m = RiftManager.MONOLITH;
        schedule(l, now, 70, "Le Titan vise le Monolithe !", t -> ring(l, m.getX() + 0.5, m.getY() + 0.2, m.getZ() + 0.5, 3.5, RED, 24), () -> {
            l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, m.getX() + 0.5, m.getY() + 1, m.getZ() + 0.5, 2, 0.6, 0.6, 0.6, 0);
            l.playSound(null, m, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 6f, 0.5f);
            com.wavesurvivor.altar.AltarDefense.externalHit(l, rs.titanMonolithDamage);
            for (ServerPlayer p : alive(l)) {
                if (Math.hypot(p.getX() - m.getX() - 0.5, p.getZ() - m.getZ() - 0.5) <= 3.5) p.hurt(l.damageSources().explosion(null, titan), 10f);
                ShakePacket.send(p, 30, 1.0f);
            }
            announce(l, "§c§l⚠ Le Monolithe est frappé ! Réparez-le au clic droit.");
        });
    }

    // ─── Pluie de météores : zone d'annonce qui se remplit, météore en blocs d'affichage qui tombe en diagonale, impact, cratère ───

    private static final class Meteor {
        double tx, ty, tz;
        Vector3f off;
        long spawnAt, impactAt, animAt;
        boolean spawned, animated;
        float k;
        Quaternionf endRot;
        final List<Display.BlockDisplay> parts = new ArrayList<>();
        final List<float[]> layout = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
    }

    private static final class Restore {
        final BlockPos pos;
        final BlockState state;
        final long at;
        Restore(BlockPos pos, BlockState state, long at) { this.pos = pos; this.state = state; this.at = at; }
    }

    private static final List<Meteor> METEORS = new ArrayList<>();
    private static final List<Restore> RESTORES = new ArrayList<>();
    /** Amas arrondi d'environ 3 × 3 × 3 : un cœur, six faces et quatre excroissances (x, y, z, taille, en blocs pour une taille de 3). */
    private static final float[][] CLUSTER = {
            {0, 0, 0, 1.7f}, {1, 0, 0, 1.2f}, {-1, 0, 0, 1.2f}, {0, 1, 0, 1.2f}, {0, -1, 0, 1.2f}, {0, 0, 1, 1.2f}, {0, 0, -1, 1.2f},
            {0.8f, 0.8f, 0.8f, 0.9f}, {-0.8f, 0.8f, -0.8f, 0.9f}, {0.8f, -0.8f, -0.8f, 0.9f}, {-0.8f, -0.8f, 0.8f, 0.9f}};

    /** « magma_block=3, blackstone=3, … » → liste de blocs tirés selon leur poids. */
    private static BlockState pickBlock(String spec) {
        List<BlockState> pool = new ArrayList<>();
        if (spec != null) for (String part : spec.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            int w = 1;
            int eq = s.lastIndexOf('=');
            if (eq > 0) {
                try { w = Math.max(1, Math.min(50, Integer.parseInt(s.substring(eq + 1).trim()))); } catch (NumberFormatException ignored) { }
                s = s.substring(0, eq).trim();
            }
            ResourceLocation id = ResourceLocation.tryParse(s.contains(":") ? s : "minecraft:" + s);
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) continue;
            BlockState st = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
            for (int i = 0; i < w; i++) pool.add(st);
        }
        if (pool.isEmpty()) return Blocks.MAGMA_BLOCK.defaultBlockState();
        return pool.get(RNG.nextInt(pool.size()));
    }

    private static ListTag floats(float... v) {
        ListTag t = new ListTag();
        for (float f : v) t.add(FloatTag.valueOf(f));
        return t;
    }

    /** Transformation d'un bloc d'affichage dont le CENTRE est à {@code c} (relatif à l'entité), tourné de {@code rot}, de côté {@code s}. */
    private static CompoundTag meteorTransform(Vector3f c, Quaternionf rot, float s) {
        Vector3f corner = rot.transform(new Vector3f(-s / 2f, -s / 2f, -s / 2f));
        CompoundTag tag = new CompoundTag();
        tag.put("translation", floats(c.x + corner.x, c.y + corner.y, c.z + corner.z));
        tag.put("left_rotation", floats(rot.x, rot.y, rot.z, rot.w));
        tag.put("scale", floats(s, s, s));
        tag.put("right_rotation", floats(0, 0, 0, 1));
        return tag;
    }

    /** Positionne le centre d'un morceau : décalage du morceau dans l'amas (tourné avec l'amas) + position de l'amas. */
    private static Vector3f partCenter(float[] p, float k, Quaternionf rot, Vector3f base) {
        Vector3f o = rot.transform(new Vector3f(p[0] * k, p[1] * k, p[2] * k));
        return o.add(base);
    }

    private static Display.BlockDisplay spawnPart(ServerLevel l, Meteor m, BlockState st, CompoundTag transformation) {
        Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(l);
        if (d == null) return null;
        d.moveTo(m.tx, m.ty, m.tz, 0f, 0f);
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        tag.put("block_state", NbtUtils.writeBlockState(st));
        tag.put("transformation", transformation);
        CompoundTag light = new CompoundTag();
        light.putInt("sky", 15);
        light.putInt("block", 15);
        tag.put("brightness", light);
        tag.putFloat("view_range", 3.0f);
        d.load(tag);
        d.addTag(com.wavesurvivor.horde.skill.IceFx.TAG);   // nettoyage automatique si le monde se recharge
        l.addFreshEntity(d);
        return d;
    }

    private static net.minecraft.core.particles.ParticleOptions trail(String kind, boolean smoke) {
        if (smoke) return ParticleTypes.LARGE_SMOKE;
        return "soul".equalsIgnoreCase(kind) ? ParticleTypes.SOUL_FIRE_FLAME : "smoke".equalsIgnoreCase(kind) ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.FLAME;
    }

    private static void meteors(ServerLevel l, long now, List<ServerPlayer> alive) {
        final int n = Math.max(1, rs.meteorBase + Math.max(0, rs.meteorPerPlayer) * alive.size());
        final double radius = Math.max(1.5, rs.meteorRadius);
        final int delay = Math.max(10, rs.meteorDelay), fall = Math.max(8, Math.min(delay - 2, rs.meteorFall));
        List<double[]> spots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ServerPlayer t = alive.get(RNG.nextInt(alive.size()));
            double x = t.getX() + (RNG.nextDouble() - 0.5) * 5, z = t.getZ() + (RNG.nextDouble() - 0.5) * 5;
            int gy = groundY(l, (int) Math.floor(x), (int) Math.floor(z));
            if (gy <= l.getMinBuildHeight() + 1) continue; // au-dessus du vide
            spots.add(new double[]{x, gy, z});
        }
        if (spots.isEmpty()) return;
        final long start = now, impact = now + delay;
        // Un météore par zone : visible dans le ciel côté Titan, ~1,2 s avant l'impact
        for (double[] s : spots) {
            Meteor m = new Meteor();
            m.tx = s[0]; m.ty = s[1]; m.tz = s[2];
            m.k = (float) Math.max(0.3, rs.meteorSize / 3.0);
            double dx = baseX - s[0], dz = baseZ - s[2], len = Math.max(1, Math.hypot(dx, dz));
            m.off = new Vector3f((float) (dx / len * 28), 40f, (float) (dz / len * 28));
            m.impactAt = impact;
            m.spawnAt = impact - fall;
            m.endRot = new Quaternionf().rotateAxis((float) Math.toRadians(150), 0.6f, 0.8f, 0.3f);
            for (float[] p : CLUSTER) { m.layout.add(p); m.states.add(pickBlock(rs.meteorBlocks)); }
            METEORS.add(m);
        }
        schedule(l, now, delay, "Pluie de météores !", t -> {
            // La zone se remplit du centre vers le bord, en plus du cercle
            double f = Math.max(0.05, Math.min(1.0, (t - start) / (double) delay));
            for (double[] s : spots) {
                ring(l, s[0], s[1], s[2], radius, RED, (int) (radius * 7));
                for (double r = 1; r <= radius * f; r += 1.0) ring(l, s[0], s[1], s[2], r, RED, Math.max(6, (int) (r * 4)));
            }
        }, () -> { });
    }

    /** Animation des météores (chaque tick) : apparition, chute en diagonale en tournant, traînée, impact. */
    private static void tickMeteors(ServerLevel l, long now) {
        for (Meteor m : new ArrayList<>(METEORS)) {
            if (!m.spawned && now >= m.spawnAt) {
                m.spawned = true;
                m.animAt = now + 1;
                Quaternionf id = new Quaternionf();
                for (int i = 0; i < m.layout.size(); i++) {
                    float[] p = m.layout.get(i);
                    Vector3f c = partCenter(p, m.k, id, new Vector3f(m.off));
                    m.parts.add(spawnPart(l, m, m.states.get(i), meteorTransform(c, id, p[3] * m.k)));
                }
                l.playSound(null, BlockPos.containing(m.tx, m.ty, m.tz), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 4f, 0.5f);
            }
            if (m.spawned && !m.animated && now >= m.animAt) {
                m.animated = true;
                int dur = (int) Math.max(1, m.impactAt - now);
                Vector3f end = new Vector3f(0, 1.6f * m.k, 0);
                for (int i = 0; i < m.layout.size() && i < m.parts.size(); i++) {
                    Display.BlockDisplay d = m.parts.get(i);
                    if (d == null || d.isRemoved()) continue;
                    float[] p = m.layout.get(i);
                    CompoundTag tag = d.saveWithoutId(new CompoundTag());
                    tag.put("transformation", meteorTransform(partCenter(p, m.k, m.endRot, end), m.endRot, p[3] * m.k));
                    tag.putInt("interpolation_duration", dur);
                    tag.putInt("start_interpolation", 0);
                    d.load(tag);
                }
            }
            if (m.spawned && now < m.impactAt) {
                double f = Math.max(0, Math.min(1, (now - m.spawnAt) / (double) Math.max(1, m.impactAt - m.spawnAt)));
                double x = m.tx + m.off.x * (1 - f), y = m.ty + 1.6 * m.k + m.off.y * (1 - f), z = m.tz + m.off.z * (1 - f);
                l.sendParticles(trail(rs.meteorTrail, false), x, y, z, 6, 0.5 * m.k, 0.5 * m.k, 0.5 * m.k, 0.02);
                l.sendParticles(trail(rs.meteorTrail, true), x, y, z, 3, 0.4 * m.k, 0.4 * m.k, 0.4 * m.k, 0.01);
            }
            if (now >= m.impactAt) {
                METEORS.remove(m);
                impact(l, m, now);
            }
        }
        if (!RESTORES.isEmpty()) {
            for (Restore r : new ArrayList<>(RESTORES)) {
                if (now < r.at) continue;
                RESTORES.remove(r);
                l.setBlock(r.pos, r.state, 3);
            }
        }
    }

    private static void impact(ServerLevel l, Meteor m, long now) {
        double x = m.tx, y = m.ty, z = m.tz, radius = Math.max(1.5, rs.meteorRadius), hit = radius * (6.4 / 6.0);
        BlockPos at = BlockPos.containing(x, y, z);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 2, 0.6, 0.4, 0.6, 0);
        l.sendParticles(ParticleTypes.LAVA, x, y + 1, z, 20, radius * 0.4, 0.3, radius * 0.4, 0);
        for (BlockState st : m.states) {
            l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, st), x, y + 1.2, z, 6, 1.2 * m.k, 0.6, 1.2 * m.k, 0.35);
        }
        l.playSound(null, at, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 5f, 0.6f);
        for (ServerPlayer p : l.players()) {
            double d = Math.hypot(p.getX() - x, p.getZ() - z);
            if (d <= radius * 5) ShakePacket.send(p, 24, (float) Math.max(0.3, 1.2 - d / (radius * 5)));
        }
        for (ServerPlayer p : alive(l)) {
            if (Math.hypot(p.getX() - x, p.getZ() - z) <= hit && Math.abs(p.getY() - y) < 4) p.hurt(l.damageSources().explosion(null, titan), Math.max(1, rs.meteorDamage));
        }
        for (Display.BlockDisplay d : m.parts) if (d != null && !d.isRemoved()) d.discard();
        // Petit cratère temporaire (magma et pierre noire fumants) : jamais sur les blocs posés par les joueurs
        if (rs.meteorCrater) {
            int r = (int) Math.max(1, Math.min(3, Math.round(radius / 3)));
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1 || RNG.nextInt(3) == 0) continue;
                BlockPos p = new BlockPos(at.getX() + dx, groundY(l, at.getX() + dx, at.getZ() + dz) - 1, at.getZ() + dz);
                BlockState cur = l.getBlockState(p);
                if (cur.isAir() || !cur.getFluidState().isEmpty() || cur.is(Blocks.MAGMA_BLOCK) || com.wavesurvivor.horde.kingdom.KingdomClaim.isPlaced(p)) continue;
                if (cur.getBlock() instanceof net.minecraft.world.level.block.EntityBlock) continue;
                RESTORES.add(new Restore(p, cur, now + 200));
                l.setBlock(p, (RNG.nextBoolean() ? Blocks.MAGMA_BLOCK : Blocks.BLACKSTONE).defaultBlockState(), 3);
                l.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
            }
        }
    }

    /** Retire les météores en vol et rend le terrain (fin du Titan ou de la Faille). */
    private static void clearMeteors(ServerLevel l) {
        for (Meteor m : METEORS) for (Display.BlockDisplay d : m.parts) if (d != null && !d.isRemoved()) d.discard();
        METEORS.clear();
        for (Restore r : RESTORES) l.setBlock(r.pos, r.state, 3);
        RESTORES.clear();
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
            float room = Math.max(0f, (float) (maxHp * (stunning ? rs.titanChargeCapPercent : rs.titanWindowCapPercent) / 100.0) - windowDealt);
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

    // ─── La charge : le Titan fonce sur l'île, tremblement, stun, corps à corps, puis retourne à sa place ───

    private static double halfWidth() {
        EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(rs.titanEntity));
        return (t != null ? t.getWidth() : 1.4) * rs.titanScale * 0.5;
    }

    private static void chargeTick(ServerLevel l, long now) {
        if (charge == 0) {
            if (!rs.titanCharge || titan == null || !pending.isEmpty()) return;
            if (chargeDue || (phase >= 2 && now >= nextChargeAt)) startCharge(l, now);
            return;
        }
        BlockPos c = RiftManager.CENTER;
        switch (charge) {
            case 1 -> {   // annonce : la zone d'impact se dessine sur le bord sud de l'île, puis se remplit
                if (now % 4 == 0) {
                    double f = Math.min(1.0, 1.0 - (chargeAt - now) / 60.0);
                    for (double x = zone[0]; x <= zone[1]; x += 3) {
                        l.sendParticles(RED, x, c.getY() + 1.1, zone[2], 1, 0, 0, 0, 0);
                        l.sendParticles(RED, x, c.getY() + 1.1, zone[3], 1, 0, 0, 0, 0);
                    }
                    for (double z = zone[2]; z <= zone[2] + (zone[3] - zone[2]) * f; z += 2) {
                        for (double x = zone[0]; x <= zone[1]; x += 4) l.sendParticles(RED, x, c.getY() + 1.1, z, 1, 0.3, 0, 0.3, 0);
                    }
                    if (now % 20 == 0) for (ServerPlayer p : alive(l)) p.displayClientMessage(Component.literal("§c⚠ Le Titan va charger l'île ! Quittez la zone rouge !"), true);
                }
                if (now >= chargeAt) { charge = 2; chargeAt = now + 20; }
            }
            case 2 -> {   // élan : le Titan avance vers l'île
                double f = 1.0 - (chargeAt - now) / 20.0;
                f = Math.max(0, Math.min(1, f));
                double s = f * f * (3 - 2 * f);
                titan.setPos(baseX, baseY, baseZ + (chargeTargetZ - baseZ) * s);
                if (now % 2 == 0) for (ServerPlayer p : l.players()) ShakePacket.send(p, 6, 0.8f);
                if (now >= chargeAt) impact(l, now);
            }
            case 3 -> {   // stun : à terre, vulnérable au corps à corps
                if (now >= chargeAt) {
                    stunning = false;
                    windowUntil = 0;
                    titan.setGlowingTag(false);
                    if (bar != null) bar.setColor(BossEvent.BossBarColor.PURPLE);
                    announce(l, "§5§l⚔ Le Titan se réveille !");
                    for (ServerPlayer p : l.players()) ShakePacket.send(p, 40, 1.2f);
                    charge = 4;
                    chargeAt = now + Math.max(2, rs.titanChargeMelee) * 20L;
                    nextMeleeAt = now + 30;
                }
            }
            case 4 -> {   // corps à corps : coups annoncés sur la moitié sud de l'île
                meleeTick(l, now);
                if (now >= chargeAt) {
                    strikes.clear();
                    charge = 5;
                    chargeAt = now + 60;
                    announce(l, "§7Le Titan recule…");
                }
            }
            default -> {   // retour à sa place
                double f = 1.0 - (chargeAt - now) / 60.0;
                f = Math.max(0, Math.min(1, f));
                double s = f * f * (3 - 2 * f);
                titan.setPos(baseX, baseY, chargeTargetZ + (baseZ - chargeTargetZ) * s);
                if (now >= chargeAt) {
                    titan.setPos(baseX, baseY, baseZ);
                    charge = 0;
                    nextChargeAt = now + Math.max(10, rs.titanChargeInterval) * 20L;
                    nextSkillAt = now + 60;
                }
            }
        }
    }

    private static void startCharge(ServerLevel l, long now) {
        chargeDue = false;
        pending.clear();
        strikes.clear();
        BlockPos c = RiftManager.CENTER;
        int sr = siegeRadius;
        double reach = Math.max(4, rs.titanChargeReach);
        // Le bord nord du Titan arrive à « reach » blocs à l'intérieur de l'île
        chargeTargetZ = c.getZ() + 0.5 + sr - reach + halfWidth();
        double hw = Math.min(halfWidth(), sr + 4);
        zone = new double[]{c.getX() + 0.5 - hw, c.getX() + 0.5 + hw, c.getZ() + 0.5 + sr - reach - 4, c.getZ() + 0.5 + sr + 6};
        charge = 1;
        chargeAt = now + 60;
        titan.setGlowingTag(true);
        announce(l, "§c§l⚠ Le Titan prend son élan !");
        l.playSound(null, c, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 8f, 0.4f);
        for (ServerPlayer p : l.players()) ShakePacket.send(p, 40, 0.8f);
    }

    /** L'impact : tremblement, dégâts et recul des joueurs trop proches de la zone, puis stun. */
    private static void impact(ServerLevel l, long now) {
        BlockPos c = RiftManager.CENTER;
        double zc = (zone[2] + zone[3]) / 2, xc = c.getX() + 0.5;
        l.playSound(null, c, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 10f, 0.4f);
        l.playSound(null, c, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 6f, 0.5f);
        for (double x = zone[0]; x <= zone[1]; x += 5) {
            l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, c.getY() + 1, zone[2] + 3, 1, 0, 0, 0, 0);
            l.sendParticles(ParticleTypes.CLOUD, x, c.getY() + 1.2, zone[2] + 3, 12, 2, 0.3, 2, 0.1);
        }
        for (ServerPlayer p : l.players()) {
            ShakePacket.send(p, 90, 1.8f);
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.getX() >= zone[0] && p.getX() <= zone[1] && p.getZ() >= zone[2] && p.getZ() <= zone[3]) {
                p.hurt(l.damageSources().explosion(null, titan), Math.max(1, rs.titanChargeDamage));
                p.setDeltaMovement(0, 0.9, -1.7);   // repoussé vers le Monolithe
                p.hurtMarked = true;
            }
        }
        charge = 3;
        chargeAt = now + Math.max(2, rs.titanChargeStun) * 20L;
        stunning = true;
        windowUntil = chargeAt;
        windowDealt = 0f;
        titan.setGlowingTag(true);
        if (bar != null) bar.setColor(BossEvent.BossBarColor.RED);
        announce(l, "§c§l⚔ Le Titan est à terre ! Frappez-le maintenant (" + rs.titanChargeStun + " s) !");
    }

    private static void meleeTick(ServerLevel l, long now) {
        BlockPos c = RiftManager.CENTER;
        // Annonce d'un nouveau coup toutes les 2,5 s : sur un joueur de la zone sud de l'île
        if (now >= nextMeleeAt) {
            nextMeleeAt = now + 50;
            List<ServerPlayer> al = alive(l);
            if (!al.isEmpty()) {
                ServerPlayer t = al.get(RNG.nextInt(al.size()));
                strikes.add(new double[]{t.getX(), t.getZ(), now + 25});
            }
        }
        for (double[] s : new ArrayList<>(strikes)) {
            if (now % 3 == 0) ring(l, s[0], c.getY() + 1, s[1], 5.0, RED, 24);
            if (now >= s[2]) {
                strikes.remove(s);
                l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, s[0], c.getY() + 1, s[1], 1, 0, 0, 0, 0);
                l.playSound(null, BlockPos.containing(s[0], c.getY(), s[1]), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 5f, 0.6f);
                for (ServerPlayer p : l.players()) {
                    double d = Math.hypot(p.getX() - s[0], p.getZ() - s[1]);
                    if (d < 40) ShakePacket.send(p, 20, 1.0f);
                    if (!p.isSpectator() && p.isAlive() && d <= 5.2 && Math.abs(p.getY() - c.getY()) < 5) {
                        p.hurt(l.damageSources().explosion(null, titan), Math.max(1, rs.titanMeleeDamage));
                        double dx = p.getX() - s[0], dz = p.getZ() - s[1], len = Math.max(0.5, Math.hypot(dx, dz));
                        p.setDeltaMovement(dx / len * 1.0, 0.6, dz / len * 1.0);
                        p.hurtMarked = true;
                    }
                }
            }
        }
    }

    private static void onTitanDeath(ServerLevel l) {
        charge = 0;
        stunning = false;
        strikes.clear();
        dead = true;
        pending.clear();
        clearMeteors(l);
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
        clearMeteors(l);
        charge = 0;
        chargeDue = false;
        stunning = false;
        strikes.clear();
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
