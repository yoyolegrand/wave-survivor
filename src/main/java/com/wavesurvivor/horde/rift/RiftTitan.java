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
        /** Grosse attaque : le Titan est à découvert juste après (fenêtre de vulnérabilité). */
        boolean window;
    }

    private static LivingEntity titan;
    private static UUID titanId;
    private static ServerBossEvent bar;
    private static final List<Pending> pending = new ArrayList<>();
    private static final Set<UUID> avatars = new HashSet<>();
    private static int phase;
    /** Charge sur l'île : 0 repos, 1 annonce, 2 élan, 3 stun, 4 corps à corps, 5 retour. */
    private static int charge;
    /** Points faibles (cristaux), leur décalage par rapport au Titan {dx, dy}, prochaine vague, dégâts subis pendant la charge, stun en plus (ticks). */
    /** Cinématique d'apparition : date de début, échelle de durée (1 = 12 s), activée, joueurs qui l'ont passée, étapes déjà jouées. */
    private static long cineStart;
    private static double cineF = 1.0;
    private static boolean cineOn, cineRise, cineNamed, cineRoar, cineAbyss, cineGrowl;
    private static final Set<UUID> cineSkipped = new HashSet<>();
    private static final List<UUID> crystals = new ArrayList<>();
    private static final List<float[]> crystalOff = new ArrayList<>();
    private static long nextWaveAt;
    private static float chargeDealt;
    private static int stunBonus;
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
    /** Charges déjà faites (choix du secteur visé), secteurs de l'île déjà effondrés, secteur et x visés par la charge en cours. */
    private static int chargeCount, chargeSector;
    private static final Set<Integer> brokenSectors = new HashSet<>();
    private static double chargeX;

    private RiftTitan() {}

    static boolean active() { return titan != null && !dead; }

    // ─── Boîtes de contact : un Titan géant n'est pas retrouvable par Minecraft loin de son centre (voir TitanHitboxEntity) ───

    private static final List<UUID> hitboxes = new ArrayList<>();

    /** Pose des boîtes de contact de 8 × 8 × 8 sur la face avant (côté île) du Titan, jusqu'à ~48 blocs de haut. */
    private static void spawnHitboxes(ServerLevel l) {
        clearHitboxes(l);
        double hw = halfWidth();
        int cols = (int) Math.max(1, Math.min(12, Math.ceil(2 * hw / 8)));
        double height = titan.getBbHeight();
        int rows = (int) Math.max(1, Math.min(6, Math.ceil(Math.min(height, 48) / 8)));
        for (int i = 0; i < cols * rows; i++) {
            com.wavesurvivor.entity.TitanHitboxEntity h = com.wavesurvivor.registry.ModEntities.TITAN_HITBOX.get().create(l);
            if (h == null) continue;
            h.moveTo(titan.getX(), titan.getY(), titan.getZ(), 0f, 0f);
            l.addFreshEntity(h);
            hitboxes.add(h.getUUID());
        }
        placeHitboxes(l);
    }

    /** Replace les boîtes de contact sur la face avant du Titan (à chaque déplacement du Titan). */
    private static void placeHitboxes(ServerLevel l) {
        if (titan == null || hitboxes.isEmpty()) return;
        double hw = halfWidth();
        int cols = (int) Math.max(1, Math.min(12, Math.ceil(2 * hw / 8)));
        int rows = Math.max(1, hitboxes.size() / cols);
        double front = titan.getZ() - hw;   // bord nord du Titan (côté île)
        int k = 0;
        for (UUID id : hitboxes) {
            Entity h = l.getEntity(id);
            int col = k % cols, row = k / cols;
            k++;
            if (h == null) continue;
            double x = titan.getX() - hw + 4 + col * Math.min(8.0, (2 * hw - 8) / Math.max(1, cols - 1));
            if (cols == 1) x = titan.getX();
            h.setPos(x, titan.getY() + row * 8.0, front + 4);
        }
        placeCrystals(l);
    }

    private static void clearCrystals(ServerLevel l) {
        for (UUID id : new ArrayList<>(crystals)) {
            Entity e = l.getEntity(id);
            if (e != null) e.discard();
        }
        crystals.clear();
        crystalOff.clear();
    }

    private static void clearHitboxes(ServerLevel l) {
        clearCrystals(l);
        for (UUID id : new ArrayList<>(hitboxes)) {
            Entity h = l.getEntity(id);
            if (h != null) h.discard();
        }
        hitboxes.clear();
    }

    /** Un coup reçu par une boîte de contact : porté au Titan (chaque coup compte : pas de délai d'invulnérabilité entre deux coups). */
    static boolean hurtTitan(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (titan == null || dead || !titan.isAlive() || emerging) return false;
        titan.invulnerableTime = 0;
        return titan.hurt(source, amount);
    }

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
        // Invisible sous l'abîme jusqu'à son surgissement (la cinématique le révèle)
        mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100000, 0, false, false));
        l.addFreshEntity(mob);
        titan = mob;
        titanId = mob.getUUID();
        emerging = true;
        cineOn = settings.titanCine;
        cineF = (cineOn ? Math.max(4, Math.min(40, settings.titanCineSeconds)) : 6) / 12.0;
        cineStart = l.getServer().getTickCount();
        emergeUntil = cineStart + (long) (240 * cineF);
        cineRise = cineNamed = cineRoar = cineAbyss = cineGrowl = false;
        cineSkipped.clear();
        charge = 0;
        chargeDue = false;
        stunning = false;
        strikes.clear();
        chargeDealt = 0f;
        stunBonus = 0;
        chargeCount = 0;
        brokenSectors.clear();
        nextWaveAt = Long.MAX_VALUE;
        nextChargeAt = cineStart + (long) (240 * cineF) + Math.max(10, settings.titanChargeInterval) * 20L;
        bar = new ServerBossEvent(Component.literal("§5§lTitan de la Faille"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setProgress(1f);   // la barre n'apparaît qu'à l'étape du nom
        if (cineOn) {   // joueurs figés et invulnérables, caméra et bandes noires côté client
            int dur = (int) (240 * cineF);
            for (ServerPlayer p : l.players()) {
                if (p.isSpectator()) continue;
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur + 20, 9, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, dur + 20, 128, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur + 20, 4, false, false));
                com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                        new com.wavesurvivor.network.TitanCinePackets.Cine(true, dur, baseX, baseY + titanHeight() * 0.55, baseZ, settings.titanCineSkippable));
            }
        }
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
            cineTick(l, now);
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
            if (phase >= 1) {
                spawnCrystals(l);                                  // les points faibles se reforment à chaque palier
                nextWaveAt = now + 60;                             // et l'invasion continue reprend, deux fois plus forte à chaque phase
                phaseCinematic(l);
            }
        }
        // Attaques annoncées en cours
        for (Pending p : new ArrayList<>(pending)) {
            if (now >= p.fireAt) {
                pending.remove(p);
                titan.setGlowingTag(false);
                p.fire.run();
                if (p.window) openWindow(l, now); // grosse attaque : le Titan est à découvert
            } else if (now % 4 == 0) {
                p.draw.accept(now);
                if (now % 10 == 0) for (ServerPlayer pl : alive(l)) pl.displayClientMessage(Component.literal("§c⚠ " + p.name), true);
            }
        }
        // Choix de la prochaine attaque
        waveTick(l, now);
        rockTick(l, now);
        phaseFx(l, now);
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
        RiftManager.feed(l, msg);   // fil d'événements (étiquettes en haut à gauche), pas le chat
    }

    /**
     * Répartition par phase (PV) : phase 1 = météores + piétinement ; phase 2 = + rocher arraché, météores traqueurs ;
     * phase 3 = + regard du Titan, pluie de lave ; phase 4 = + rugissement, météore géant. Chaque palier déclenche aussi une charge
     * qui brise un nouveau secteur de l'île.
     */
    private static void chooseSkill(ServerLevel l, long now, List<ServerPlayer> alive) {
        List<Integer> pool = new ArrayList<>();
        pool.add(0); pool.add(0); pool.add(1);                                 // météores, piétinement
        if (phase >= 1) { pool.add(7); pool.add(7); pool.add(8); }             // rocher arraché, météores traqueurs
        if (phase >= 2) { pool.add(2); pool.add(9); }                          // regard du Titan, pluie de lave
        if (phase >= 3) { pool.add(3); pool.add(10); pool.add(2); }            // rugissement, météore géant
        if (phase >= 1 && !defensePositions().isEmpty()) { pool.add(6); if (phase >= 2) pool.add(6); } // attaque d'un bâtiment
        if (rs.titanMonolithDamage > 0 && AltarDefenseHook.alive()) { if (phase >= 1) pool.add(5); if (phase >= 3) pool.add(5); } // poing contre le Monolithe
        if (RNG.nextInt(6) == 0 && avatars.size() < 4) pool.add(4);
        switch (pool.get(RNG.nextInt(pool.size()))) {
            case 0 -> meteors(l, now, alive);
            case 1 -> stomp(l, now);
            case 2 -> beam(l, now);
            case 3 -> roar(l, now);
            case 5 -> monolithFist(l, now);
            case 6 -> buildingStrike(l, now);
            case 7 -> { if (ROCKS.isEmpty()) rockThrow(l, now); else meteors(l, now, alive); }
            case 8 -> trackingMeteors(l, now, alive);
            case 9 -> lavaRain(l, now, alive);
            case 10 -> giantMeteor(l, now, alive);
            default -> summonAvatars(l);
        }
    }

    /** Test (commande /ws rift skill) : lance une attaque tout de suite. */
    static boolean debugSkill(ServerLevel l, String name) {
        if (!active() || emerging || titan == null) return false;
        long now = l.getServer().getTickCount();
        List<ServerPlayer> al = alive(l);
        if (al.isEmpty()) return false;
        switch (name) {
            case "meteors" -> meteors(l, now, al);
            case "tracking" -> trackingMeteors(l, now, al);
            case "lava" -> lavaRain(l, now, al);
            case "giant" -> giantMeteor(l, now, al);
            case "stomp" -> stomp(l, now);
            case "rock" -> rockThrow(l, now);
            case "beam" -> beam(l, now);
            case "roar" -> roar(l, now);
            case "charge" -> { if (charge != 0) return false; startCharge(l, now); }
            default -> { return false; }
        }
        return true;
    }

    static final String[] DEBUG_SKILLS = {"meteors", "tracking", "lava", "giant", "stomp", "rock", "beam", "roar", "charge"};

    private static int groundY(ServerLevel l, int x, int z) {
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static void ring(ServerLevel l, double cx, double y, double cz, double r, DustParticleOptions c, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            l.sendParticles(c, cx + Math.cos(a) * r, y + 0.15, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private static Pending schedule(ServerLevel l, long now, int delay, String name, LongConsumer draw, Runnable fire) {
        Pending p = new Pending();
        p.fireAt = now + delay;
        p.name = name;
        p.draw = draw;
        p.fire = fire;
        pending.add(p);
        titan.setGlowingTag(true);
        l.playSound(null, RiftManager.CENTER, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3f, 0.6f);
        return p;
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
        /** Rayon de la zone, dégâts, cratère (rayon, durée en ticks), dégâts aux bâtiments ; joueur suivi jusqu'à {@code lockAt}. */
        double radius = 6, damage = 12;
        int craterR, craterTicks = 200;
        float buildDamage;
        UUID follow;
        long lockAt;
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

    /** Identifiants des blocs d'affichage de météore vivants (le nettoyage au chargement du monde supprime les autres). */
    private static final Set<UUID> METEOR_IDS = new HashSet<>();
    /** Journal de débogage : -Dwavesurvivor.debugMeteors=true */
    private static final boolean DEBUG_METEORS = Boolean.getBoolean("wavesurvivor.debugMeteors");

    static boolean isMeteorPart(UUID id) { return METEOR_IDS.contains(id); }

    private static void dropMeteorPart(Display.BlockDisplay d, String why) {
        if (d == null) return;
        METEOR_IDS.remove(d.getUUID());
        if (!d.isRemoved()) d.discard();
        if (DEBUG_METEORS) WaveSurvivorMod.LOGGER.info("[Météore] bloc retiré ({})", why);
    }

    private static Display.BlockDisplay spawnPart(ServerLevel l, Meteor m, BlockState st, CompoundTag transformation) {
        Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(l);
        if (d == null) return null;
        // L'entité naît à son point de départ, dans le ciel (côté Titan) ; la translation l'amène ensuite jusqu'au point d'impact
        d.moveTo(m.tx + m.off.x, m.ty + m.off.y, m.tz + m.off.z, 0f, 0f);
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        tag.put("block_state", NbtUtils.writeBlockState(st));
        tag.put("transformation", transformation);
        CompoundTag light = new CompoundTag();
        light.putInt("sky", 15);
        light.putInt("block", 15);
        tag.put("brightness", light);
        tag.putFloat("view_range", 6.0f);
        tag.putFloat("width", 0f);    // 0 : jamais écarté par le culling
        tag.putFloat("height", 0f);
        d.load(tag);
        d.addTag("ws_meteor");        // étiquette propre (IceFx supprime les blocs d'affichage « ws_fx » qu'il ne connaît pas)
        METEOR_IDS.add(d.getUUID());  // avant l'ajout au monde : le nettoyage à l'entrée ne doit pas le refuser
        if (!l.addFreshEntity(d)) {
            METEOR_IDS.remove(d.getUUID());
            WaveSurvivorMod.LOGGER.warn("[Météore] un bloc d'affichage n'a pas pu être ajouté au monde.");
            return null;
        }
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
        int craterR = rs.meteorCrater ? (int) Math.max(1, Math.min(3, Math.round(radius / 3))) : 0;
        for (double[] s : spots) {
            newMeteor(s[0], s[1], s[2], (float) Math.max(0.3, rs.meteorSize / 3.0), radius, rs.meteorDamage, craterR, 200, impact, fall, 1f, rs.meteorBlocks)
                    .buildDamage = Math.max(1, rs.titanBuildDamage / 3f);
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

    /** Prépare un météore (bloc d'affichage en amas) : point d'impact, taille, zone, dégâts, cratère, date d'impact et durée de chute. */
    private static Meteor newMeteor(double x, double y, double z, float k, double radius, double dmg, int craterR, int craterTicks,
                                    long impactAt, int fall, float offMul, String blocks) {
        Meteor m = new Meteor();
        m.tx = x; m.ty = y; m.tz = z;
        m.k = k;
        m.radius = radius;
        m.damage = dmg;
        m.craterR = craterR;
        m.craterTicks = craterTicks;
        double dx = baseX - x, dz = baseZ - z, len = Math.max(1, Math.hypot(dx, dz));
        m.off = new Vector3f((float) (dx / len * 28 * offMul), 40f * offMul, (float) (dz / len * 28 * offMul));
        m.impactAt = impactAt;
        m.spawnAt = impactAt - fall;
        m.endRot = new Quaternionf().rotateAxis((float) Math.toRadians(150), 0.6f, 0.8f, 0.3f);
        for (float[] p : CLUSTER) { m.layout.add(p); m.states.add(pickBlock(blocks)); }
        METEORS.add(m);
        return m;
    }

    /** Météores traqueurs : la zone suit un joueur, puis se fige 1 s avant l'impact (on l'esquive au dernier moment). */
    private static void trackingMeteors(ServerLevel l, long now, List<ServerPlayer> alive) {
        List<ServerPlayer> pool = new ArrayList<>(alive);
        java.util.Collections.shuffle(pool, RNG);
        int n = Math.min(pool.size(), 1 + phase / 2);
        final int delay = 70, fall = 20;
        List<Meteor> mine = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ServerPlayer t = pool.get(i);
            int gy = groundY(l, t.getBlockX(), t.getBlockZ());
            if (gy <= l.getMinBuildHeight() + 1) continue;
            long imp = now + delay + i * 12L;
            Meteor m = newMeteor(t.getX(), gy, t.getZ(), (float) Math.max(0.3, rs.meteorSize / 3.0) * 0.85f, Math.max(1.5, rs.meteorRadius) * 0.8,
                    rs.meteorDamage * 1.2, 1, 160, imp, fall, 1f, rs.meteorBlocks);
            m.follow = t.getUUID();
            m.buildDamage = Math.max(1, rs.titanBuildDamage / 3f);
            m.lockAt = imp - fall;
            mine.add(m);
        }
        if (mine.isEmpty()) return;
        schedule(l, now, delay + (mine.size() - 1) * 12, com.wavesurvivor.i18n.WSLang.t("rift.titan.meteor_track"), t -> {
            for (Meteor m : mine) {
                if (t >= m.impactAt) continue;
                boolean locked = t >= m.lockAt;
                ring(l, m.tx, m.ty, m.tz, m.radius, locked ? RED : VIOLET, (int) (m.radius * 7));
                if (locked) ring(l, m.tx, m.ty, m.tz, m.radius * 0.5, RED, 14);
            }
        }, () -> { });
    }

    /** Pluie de lave : une nuée de petits météores en fusion sur toute l'île, chacun laisse une flaque de magma brûlante. */
    private static void lavaRain(ServerLevel l, long now, List<ServerPlayer> alive) {
        int n = 8 + 2 * alive.size();
        int sr = Math.max(RiftManager.radius(), siegeRadius) - 2;
        BlockPos c = RiftManager.CENTER;
        List<Meteor> mine = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double x, z;
            if (i % 2 == 0) {   // la moitié autour des joueurs, le reste partout sur l'île
                ServerPlayer t = alive.get(RNG.nextInt(alive.size()));
                x = t.getX() + (RNG.nextDouble() - 0.5) * 8;
                z = t.getZ() + (RNG.nextDouble() - 0.5) * 8;
            } else {
                double a = RNG.nextDouble() * Math.PI * 2, d = Math.sqrt(RNG.nextDouble()) * sr;
                x = c.getX() + 0.5 + Math.cos(a) * d;
                z = c.getZ() + 0.5 + Math.sin(a) * d;
            }
            int gy = groundY(l, (int) Math.floor(x), (int) Math.floor(z));
            if (gy <= l.getMinBuildHeight() + 1) continue;
            long imp = now + 50 + RNG.nextInt(50);
            Meteor lm = newMeteor(x, gy, z, 0.45f, 2.0, Math.max(1, rs.meteorDamage * 0.5), 1, 240, imp, 16, 0.8f, "magma_block=4, blackstone=1");
            lm.buildDamage = Math.max(1, rs.titanBuildDamage / 6f);
            mine.add(lm);
        }
        if (mine.isEmpty()) return;
        long last = now;
        for (Meteor m : mine) last = Math.max(last, m.impactAt);
        schedule(l, now, (int) (last - now), com.wavesurvivor.i18n.WSLang.t("rift.titan.lava_rain"), t -> {
            for (Meteor m : mine) if (t < m.impactAt) ring(l, m.tx, m.ty, m.tz, m.radius, RED, 14);
        }, () -> { });
    }

    /** Météore géant : vise le groupe de joueurs le plus serré, longue annonce, énorme impact qui écrase aussi les bâtiments. */
    private static void giantMeteor(ServerLevel l, long now, List<ServerPlayer> alive) {
        ServerPlayer best = alive.get(0);
        int bestN = -1;
        for (ServerPlayer p : alive) {
            int k = 0;
            for (ServerPlayer q : alive) if (q.distanceToSqr(p) < 100) k++;
            if (k > bestN) { bestN = k; best = p; }
        }
        final double x = best.getX(), z = best.getZ();
        final int gy = groundY(l, best.getBlockX(), best.getBlockZ());
        if (gy <= l.getMinBuildHeight() + 1) return;
        final double radius = 9;
        final int delay = 110;
        Meteor m = newMeteor(x, gy, z, 2.3f, radius, Math.max(10, rs.meteorDamage * 2.5), 4, 400, now + delay, 45, 1.8f, rs.meteorBlocks);
        m.buildDamage = Math.max(1, rs.titanBuildDamage);
        final long start = now;
        schedule(l, now, delay, com.wavesurvivor.i18n.WSLang.t("rift.titan.meteor_giant"), t -> {
            double f = Math.max(0.05, Math.min(1.0, (t - start) / (double) delay));
            ring(l, x, gy, z, radius, RED, 70);
            for (double r = 1.5; r <= radius * f; r += 1.5) ring(l, x, gy, z, r, RED, Math.max(8, (int) (r * 5)));
            if (t % 20 == 0) l.playSound(null, BlockPos.containing(x, gy, z), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 4f, 0.5f);
        }, () -> { });
    }

    /** Animation des météores (chaque tick) : apparition, chute en diagonale en tournant, traînée, impact. */
    private static void tickMeteors(ServerLevel l, long now) {
        for (Meteor m : new ArrayList<>(METEORS)) {
            // Météore traqueur : la zone suit son joueur jusqu'au verrouillage
            if (m.follow != null && !m.spawned && now < m.lockAt) {
                if (l.getEntity(m.follow) instanceof ServerPlayer fp && fp.isAlive() && !fp.isSpectator()) {
                    int gy = groundY(l, fp.getBlockX(), fp.getBlockZ());
                    if (gy > l.getMinBuildHeight() + 1) { m.tx = fp.getX(); m.ty = gy; m.tz = fp.getZ(); }
                }
            }
            if (!m.spawned && now >= m.spawnAt) {
                m.spawned = true;
                m.animAt = now + 2;   // 2 ticks : le client doit avoir reçu l'entité avant la nouvelle transformation
                Quaternionf id = new Quaternionf();
                for (int i = 0; i < m.layout.size(); i++) {
                    float[] p = m.layout.get(i);
                    Vector3f c = partCenter(p, m.k, id, new Vector3f(0, 0, 0));   // relatif à l'entité, déjà dans le ciel
                    m.parts.add(spawnPart(l, m, m.states.get(i), meteorTransform(c, id, p[3] * m.k)));
                }
                l.playSound(null, BlockPos.containing(m.tx, m.ty, m.tz), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 4f, 0.5f);
            }
            if (m.spawned && !m.animated && now >= m.animAt) {
                m.animated = true;
                int dur = (int) Math.max(1, m.impactAt - now);
                Vector3f end = new Vector3f(-m.off.x, -m.off.y + 1.6f * m.k, -m.off.z);   // le point d'impact, relatif à l'entité
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
        if (DEBUG_METEORS && !METEORS.isEmpty() && now % 5 == 0) WaveSurvivorMod.LOGGER.info("[Météore] {} météore(s) en vol, {} bloc(s) d'affichage vivant(s).", METEORS.size(), METEOR_IDS.size());
        if (!FX.isEmpty()) {
            for (Fx f : new ArrayList<>(FX)) {
                if (now < f.until) continue;
                FX.remove(f);
                dropMeteorPart(f.d, "effet");
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
        double x = m.tx, y = m.ty, z = m.tz, radius = Math.max(1.5, m.radius), hit = radius * (6.4 / 6.0);
        BlockPos at = BlockPos.containing(x, y, z);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, radius > 7 ? 5 : 2, 0.6 * m.k, 0.4, 0.6 * m.k, 0);
        l.sendParticles(ParticleTypes.LAVA, x, y + 1, z, (int) (20 * Math.max(0.5, m.k)), radius * 0.4, 0.3, radius * 0.4, 0);
        for (BlockState st : m.states) {
            l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, st), x, y + 1.2, z, 6, 1.2 * m.k, 0.6, 1.2 * m.k, 0.35);
        }
        l.playSound(null, at, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, radius > 7 ? 9f : 5f, radius > 7 ? 0.4f : 0.6f);
        for (ServerPlayer p : l.players()) {
            double d = Math.hypot(p.getX() - x, p.getZ() - z);
            if (d <= radius * 5) ShakePacket.send(p, radius > 7 ? 50 : 24, (float) Math.max(0.3, (radius > 7 ? 2.0 : 1.2) - d / (radius * 5)));
        }
        for (ServerPlayer p : alive(l)) {
            if (Math.hypot(p.getX() - x, p.getZ() - z) <= hit && Math.abs(p.getY() - y) < 4) p.hurt(l.damageSources().explosion(null, titan), (float) Math.max(1, m.damage));
        }
        for (Display.BlockDisplay d : m.parts) dropMeteorPart(d, "impact");
        if (m.buildDamage > 0) hitBuildings(l, at, (int) Math.ceil(radius), m.buildDamage);
        if (m.craterR > 0) crater(l, at, m.craterR, now, m.craterTicks);
    }

    /** Petit cratère temporaire (magma et pierre noire fumants) : jamais sur les blocs posés par les joueurs ni sur les blocs à entité. */
    private static void crater(ServerLevel l, BlockPos at, int r, long now, int ticks) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz > r * r + 1 || RNG.nextInt(3) == 0) continue;
            scorch(l, new BlockPos(at.getX() + dx, groundY(l, at.getX() + dx, at.getZ() + dz) - 1, at.getZ() + dz), now, ticks, RNG.nextBoolean());
        }
    }

    /** Un bloc du sol devient du magma (ou de la pierre noire) quelques secondes, puis redevient lui-même. */
    private static void scorch(ServerLevel l, BlockPos p, long now, int ticks, boolean magma) {
        BlockState cur = l.getBlockState(p);
        if (cur.isAir() || !cur.getFluidState().isEmpty() || cur.is(Blocks.MAGMA_BLOCK) || cur.is(Blocks.BLACKSTONE)
                || com.wavesurvivor.horde.kingdom.KingdomClaim.isPlaced(p)) return;
        if (cur.getBlock() instanceof net.minecraft.world.level.block.EntityBlock) return;
        for (Restore r : RESTORES) if (r.pos.equals(p)) return;
        RESTORES.add(new Restore(p, cur, now + ticks));
        l.setBlock(p, (magma ? Blocks.MAGMA_BLOCK : Blocks.BLACKSTONE).defaultBlockState(), 3);
        l.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
    }

    // ─── Blocs d'affichage d'effet (blocs qui bondissent, rocher arraché) ───

    private static final class Fx {
        final Display.BlockDisplay d;
        final long until;
        Fx(Display.BlockDisplay d, long until) { this.d = d; this.until = until; }
    }

    private static final List<Fx> FX = new ArrayList<>();

    /** Bloc d'affichage d'effet à {@code (x, y, z)}, retiré au tick {@code until} (étiqueté comme les météores : le nettoyage d'IceFx l'épargne). */
    private static Display.BlockDisplay fxBlock(ServerLevel l, double x, double y, double z, BlockState st, CompoundTag tr, long until) {
        Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(l);
        if (d == null) return null;
        d.moveTo(x, y, z, 0f, 0f);
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        tag.put("block_state", NbtUtils.writeBlockState(st));
        tag.put("transformation", tr);
        tag.putFloat("view_range", 6.0f);
        tag.putFloat("width", 0f);
        tag.putFloat("height", 0f);
        d.load(tag);
        d.addTag("ws_meteor");
        METEOR_IDS.add(d.getUUID());
        if (!l.addFreshEntity(d)) {
            METEOR_IDS.remove(d.getUUID());
            return null;
        }
        FX.add(new Fx(d, until));
        return d;
    }

    /** Nouvelle transformation d'un bloc d'affichage, atteinte en {@code dur} ticks (interpolation côté client). */
    private static void fxMove(Display.BlockDisplay d, CompoundTag tr, int dur) {
        if (d == null || d.isRemoved()) return;
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        tag.put("transformation", tr);
        tag.putInt("interpolation_duration", Math.max(1, dur));
        tag.putInt("start_interpolation", 0);
        d.load(tag);
    }

    /** Un bloc du sol bondit puis retombe (onde du piétinement). */
    private static void popBlock(ServerLevel l, BlockPos bp, BlockState st, long now) {
        Quaternionf id = new Quaternionf();
        CompoundTag flat = meteorTransform(new Vector3f(0.5f, 0.5f, 0.5f), id, 1.01f);
        Display.BlockDisplay d = fxBlock(l, bp.getX(), bp.getY(), bp.getZ(), st, flat, now + 14);
        if (d == null) return;
        Vector3f axis = new Vector3f(RNG.nextFloat() - 0.5f, 0.001f, RNG.nextFloat() - 0.5f).normalize();
        Quaternionf tilt = new Quaternionf().rotateAxis((float) Math.toRadians(8 + RNG.nextInt(14)), axis.x, axis.y, axis.z);
        RiftManager.later(l.getServer(), 2, () -> fxMove(d, meteorTransform(new Vector3f(0.5f, 1.3f, 0.5f), tilt, 1.01f), 3));
        RiftManager.later(l.getServer(), 6, () -> fxMove(d, flat, 4));
    }

    /** Retire les météores en vol et rend le terrain (fin du Titan ou de la Faille). */
    private static void clearMeteors(ServerLevel l) {
        for (Meteor m : METEORS) for (Display.BlockDisplay d : m.parts) dropMeteorPart(d, "nettoyage");
        METEORS.clear();
        for (Fx f : FX) dropMeteorPart(f.d, "nettoyage");
        FX.clear();
        ROCKS.clear();
        METEOR_IDS.clear();
        for (Restore r : RESTORES) l.setBlock(r.pos, r.state, 3);
        RESTORES.clear();
    }

    private static final DustParticleOptions ORANGE = new DustParticleOptions(new Vector3f(1.0f, 0.55f, 0.1f), 1.5f);

    /** Point du bord sud de l'île (côté Titan) à l'abscisse {@code x}. */
    private static double southEdgeZ(double x) {
        BlockPos c = RiftManager.CENTER;
        int sr = Math.max(RiftManager.radius(), siegeRadius);
        double dx = x - c.getX() - 0.5;
        return c.getZ() + 0.5 + Math.sqrt(Math.max(0, (double) sr * sr - dx * dx));
    }

    /**
     * Piétinement sismique : le Titan frappe le bord de l'île. Des fissures lumineuses rampent vers le nord (3 s), puis deux ondes de choc
     * traversent l'île : on saute par-dessus. Touché : dégâts, étourdi, projeté en l'air ; les bâtiments sur le passage sont abîmés.
     */
    private static void stomp(ServerLevel l, long now) {
        BlockPos c = RiftManager.CENTER;
        int sr = Math.max(RiftManager.radius(), siegeRadius);
        final double ox = Math.max(c.getX() + 0.5 - sr * 0.6, Math.min(c.getX() + 0.5 + sr * 0.6, titan.getX()));
        final double oz = southEdgeZ(ox);
        final int gy = c.getY() + 1;
        final double reach = sr * 2 + 4;
        final long start = now;
        final double[] angs = new double[7];
        for (int i = 0; i < angs.length; i++) angs[i] = Math.PI + (i + 0.5) * Math.PI / angs.length + (RNG.nextDouble() - 0.5) * 0.25;
        Pending pd = schedule(l, now, 60, com.wavesurvivor.i18n.WSLang.t("rift.titan.stomp"), t -> {
            double f = Math.min(1, (t - start) / 50.0);
            for (double a : angs) {
                for (double d = 1; d <= reach * 0.6 * f; d += 1.5) {
                    double px = ox + Math.cos(a) * d + (RNG.nextDouble() - 0.5) * 0.6, pz = oz + Math.sin(a) * d + (RNG.nextDouble() - 0.5) * 0.6;
                    l.sendParticles(ORANGE, px, groundY(l, (int) Math.floor(px), (int) Math.floor(pz)) + 0.1, pz, 1, 0, 0, 0, 0);
                }
            }
            if (t % 20 == 0) l.playSound(null, BlockPos.containing(ox, gy, oz), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 4f, 0.4f);
        }, () -> {
            l.playSound(null, BlockPos.containing(ox, gy, oz), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 8f, 0.4f);
            l.playSound(null, BlockPos.containing(ox, gy, oz), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 5f, 0.4f);
            l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ox, gy, oz, 3, 3, 0.5, 2, 0);
            for (ServerPlayer p : l.players()) ShakePacket.send(p, 50, 1.6f);
            stompRing(l, ox, gy, oz, reach, 0);
            stompRing(l, ox, gy, oz, reach, 18);
        });
        pd.window = true;
    }

    /** Une onde de choc du piétinement : un demi-cercle qui grandit vers le nord à 0,9 bloc par tick. */
    private static void stompRing(ServerLevel l, double ox, int gy, double oz, double reach, int delay) {
        final Set<UUID> hit = new HashSet<>();
        final Set<BlockPos> hitDef = new HashSet<>();
        final List<BlockPos> defs = defensePositions();
        final int dur = (int) Math.ceil(reach / 0.9);
        final BlockPos c = RiftManager.CENTER;
        final int sr = Math.max(RiftManager.radius(), siegeRadius) + 1;
        for (int k = 0; k <= dur; k++) {
            final int kk = k;
            RiftManager.later(l.getServer(), delay + k, () -> {
                if (!active()) return;
                double r = kk * 0.9;
                long nowT = l.getServer().getTickCount();
                int pts = (int) Math.max(8, r * 3);
                for (int i = 0; i < pts; i++) {
                    double a = Math.PI + Math.PI * (i + 0.5) / pts;
                    double px = ox + Math.cos(a) * r, pz = oz + Math.sin(a) * r;
                    if (Math.hypot(px - c.getX() - 0.5, pz - c.getZ() - 0.5) > sr) continue;
                    int top = groundY(l, (int) Math.floor(px), (int) Math.floor(pz));
                    l.sendParticles(ParticleTypes.POOF, px, top + 0.2, pz, 1, 0.1, 0.05, 0.1, 0.02);
                    if (kk % 2 == 0 && i % 4 == 0) {   // les blocs du sol bondissent au passage de l'onde
                        BlockPos bp = new BlockPos((int) Math.floor(px), top - 1, (int) Math.floor(pz));
                        BlockState st = l.getBlockState(bp);
                        if (!st.isAir() && st.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) popBlock(l, bp, st, nowT);
                    }
                }
                for (ServerPlayer p : alive(l)) {
                    if (hit.contains(p.getUUID())) continue;
                    double d = Math.hypot(p.getX() - ox, p.getZ() - oz);
                    if (Math.abs(d - r) > 1.3 || p.getZ() > oz + 1 || Math.abs(p.getY() - gy) > 4) continue;
                    hit.add(p.getUUID());
                    if (!p.onGround()) continue;   // en l'air : on a sauté par-dessus
                    p.hurt(l.damageSources().explosion(null, titan), Math.max(1, rs.titanStompDamage));
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 4, false, true));
                    p.setDeltaMovement(p.getDeltaMovement().add(0, 0.55, 0));
                    p.hurtMarked = true;
                }
                for (BlockPos dp : defs) {
                    if (hitDef.contains(dp) || Math.abs(Math.hypot(dp.getX() + 0.5 - ox, dp.getZ() + 0.5 - oz) - r) > 1.2) continue;
                    hitDef.add(dp);
                    hitBuildings(l, dp, 1, Math.max(1, rs.titanBuildDamage / 3f));
                }
                if (kk % 6 == 0) l.playSound(null, BlockPos.containing(ox, gy, oz - r), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 3f, 0.5f);
            });
        }
    }

    // ─── Rocher arraché à l'abîme (on peut l'abattre en vol) ───

    private static final class Rock {
        Vec3 from, hand, to, cur;
        long riseAt, throwAt, hitAt, nextSeg;
        int hp, beamHits;
        boolean animStarted, thrown, monolith;
        BlockPos target;
        float k = 1.3f;
        Vector3f axis;
        Pending pend;
        final List<Display.BlockDisplay> parts = new ArrayList<>();
        final List<float[]> layout = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
    }

    private static final List<Rock> ROCKS = new ArrayList<>();

    /**
     * Le Titan arrache un rocher flottant à l'abîme (jamais un morceau de l'île), le soulève, puis le lance en cloche sur le Monolithe, une tour
     * ou un joueur. On peut l'abattre en vol : boulets de catapulte, carreaux et rayons de baliste, flèches.
     */
    private static void rockThrow(ServerLevel l, long now) {
        List<BlockPos> defs = defensePositions();
        List<ServerPlayer> al = alive(l);
        BlockPos tgt;
        boolean mono = false;
        int roll = RNG.nextInt(3);
        if (AltarDefenseHook.alive() && (roll == 0 || (defs.isEmpty() && roll == 1))) { tgt = RiftManager.MONOLITH; mono = true; }
        else if (!defs.isEmpty() && roll != 2) tgt = defs.get(RNG.nextInt(defs.size()));
        else {
            if (al.isEmpty()) return;
            ServerPlayer p = al.get(RNG.nextInt(al.size()));
            tgt = new BlockPos(p.getBlockX(), groundY(l, p.getBlockX(), p.getBlockZ()), p.getBlockZ());
        }
        Rock r = new Rock();
        double side = RNG.nextBoolean() ? 1 : -1, hw = halfWidth();
        r.from = new Vec3(titan.getX() + side * hw * 0.9, baseY - 30, titan.getZ() - hw * 0.4);
        r.hand = new Vec3(titan.getX() + side * hw * 0.7, baseY + titanHeight() * 0.7, titan.getZ() - hw * 0.6);
        r.to = Vec3.atBottomCenterOf(tgt).add(0, 1.5, 0);
        r.cur = r.from;
        r.riseAt = now;
        r.throwAt = now + 60;
        r.hitAt = r.throwAt + 70;
        r.hp = Math.max(1, rs.titanRockHits);
        r.target = tgt;
        r.monolith = mono;
        r.axis = new Vector3f(RNG.nextFloat() - 0.5f, RNG.nextFloat() - 0.5f, RNG.nextFloat() - 0.5f).add(0.001f, 0.001f, 0.001f).normalize();
        Quaternionf id = new Quaternionf();
        for (float[] p : CLUSTER) {
            BlockState st = pickBlock("blackstone=3, obsidian=2, crying_obsidian=1, magma_block=1");
            r.layout.add(p);
            r.states.add(st);
            r.parts.add(fxBlock(l, r.from.x, r.from.y, r.from.z, st, meteorTransform(partCenter(p, r.k, id, new Vector3f()), id, p[3] * r.k), r.hitAt + 40));
        }
        ROCKS.add(r);
        announce(l, "§6" + com.wavesurvivor.i18n.WSLang.t("rift.titan.rock_ripped"));
        l.playSound(null, BlockPos.containing(r.hand), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 6f, 0.4f);
        final BlockPos ft = tgt;
        r.pend = schedule(l, now, (int) (r.hitAt - now), com.wavesurvivor.i18n.WSLang.t("rift.titan.rock_incoming"), t -> {
            if (ROCKS.contains(r) && t >= r.throwAt - 20) ring(l, ft.getX() + 0.5, ft.getY() + 0.2, ft.getZ() + 0.5, 5, RED, 30);
        }, () -> { });
    }

    private static Vec3 rockPos(Rock r, long t) {
        if (t <= r.throwAt) {
            double f = Math.max(0, Math.min(1, (t - r.riseAt) / (double) Math.max(1, r.throwAt - r.riseAt)));
            f = f * f * (3 - 2 * f);
            return r.from.lerp(r.hand, f);
        }
        double f = Math.max(0, Math.min(1, (t - r.throwAt) / (double) Math.max(1, r.hitAt - r.throwAt)));
        return r.hand.lerp(r.to, f).add(0, Math.sin(Math.PI * f) * 22, 0);
    }

    /** Amène le rocher à la position monde {@code at} (rotation du tick {@code t}) en {@code dur} ticks. */
    private static void moveRock(Rock r, Vec3 at, long t, int dur) {
        Quaternionf rot = new Quaternionf().rotateAxis((float) ((t - r.riseAt) * 0.09), r.axis.x, r.axis.y, r.axis.z);
        Vector3f base = new Vector3f((float) (at.x - r.from.x), (float) (at.y - r.from.y), (float) (at.z - r.from.z));
        for (int i = 0; i < r.parts.size(); i++) {
            float[] p = r.layout.get(i);
            fxMove(r.parts.get(i), meteorTransform(partCenter(p, r.k, rot, base), rot, p[3] * r.k), dur);
        }
    }

    private static void dropRock(Rock r) {
        ROCKS.remove(r);
        for (Display.BlockDisplay d : r.parts) {
            FX.removeIf(f -> f.d == d);
            dropMeteorPart(d, "rocher");
        }
        if (r.pend != null) pending.remove(r.pend);
    }

    /** Un rayon de baliste passe près d'un rocher en vol : il compte comme deux coups. */
    static void rockBeam(Vec3 from, Vec3 to) {
        for (Rock r : ROCKS) {
            if (!r.thrown || r.cur == null) continue;
            Vec3 d = to.subtract(from);
            double tt = d.lengthSqr() < 1e-6 ? 0 : Math.max(0, Math.min(1, r.cur.subtract(from).dot(d) / d.lengthSqr()));
            if (r.cur.distanceTo(from.add(d.scale(tt))) <= 3.0 * r.k) r.beamHits += 2;
        }
    }

    private static void rockTick(ServerLevel l, long now) {
        if (ROCKS.isEmpty()) return;
        for (Rock r : new ArrayList<>(ROCKS)) {
            if (!r.animStarted && now >= r.riseAt + 2) {   // 2 ticks : le client a reçu les blocs avant leur premier mouvement
                r.animStarted = true;
                moveRock(r, r.hand, r.throwAt, (int) Math.max(1, r.throwAt - now));
            }
            Vec3 pos = rockPos(r, now);
            r.cur = pos;
            if (now < r.throwAt) {   // le rocher monte : des débris en tombent
                if (now % 3 == 0) l.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, pos.x, pos.y, pos.z, 4, 1.5, 1, 1.5, 0);
                if (now % 4 == 0) l.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y - 1.5, pos.z, 3, 1.2, 0.5, 1.2, 0.01);
                continue;
            }
            if (!r.thrown) {
                r.thrown = true;
                r.nextSeg = now;
                l.playSound(null, BlockPos.containing(pos), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 6f, 0.5f);
                l.playSound(null, BlockPos.containing(r.to), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 5f, 0.5f);
            }
            if (now >= r.nextSeg && now < r.hitAt) {   // trajectoire en cloche, découpée en segments de 5 ticks
                long nt = Math.min(r.hitAt, now + 5);
                moveRock(r, rockPos(r, nt), nt, (int) (nt - now));
                r.nextSeg = nt;
            }
            l.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 3, 0.8, 0.8, 0.8, 0.01);
            l.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 2, 0.8, 0.8, 0.8, 0.01);
            // Interception : flèches, boulets de catapulte, rayons de baliste
            int dmg = 0;
            for (net.minecraft.world.entity.projectile.AbstractArrow a : l.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,
                    new net.minecraft.world.phys.AABB(pos, pos).inflate(2.6 * r.k))) {
                a.discard();
                dmg++;
            }
            dmg += 3 * RiftEngines.interceptBoulders(l, pos, 3.2 * r.k);
            dmg += r.beamHits;
            r.beamHits = 0;
            if (dmg > 0) {
                r.hp -= dmg;
                l.playSound(null, BlockPos.containing(pos), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3f, 0.5f);
                l.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 2, 1, 1, 1, 0);
                if (r.hp <= 0) { shatterRock(l, r, pos); continue; }
            }
            if (now >= r.hitAt) rockImpact(l, r, now);
        }
    }

    private static void shatterRock(ServerLevel l, Rock r, Vec3 pos) {
        dropRock(r);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 2, 1, 1, 1, 0);
        for (BlockState st : r.states) {
            l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, st), pos.x, pos.y, pos.z, 10, 2, 2, 2, 0.4);
        }
        l.playSound(null, BlockPos.containing(pos), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 6f, 0.8f);
        announce(l, "§a§l" + com.wavesurvivor.i18n.WSLang.t("rift.titan.rock_shattered"));
    }

    private static void rockImpact(ServerLevel l, Rock r, long now) {
        Vec3 p = r.to;
        dropRock(r);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 3, 1, 0.5, 1, 0);
        for (BlockState st : r.states) {
            l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, st), p.x, p.y, p.z, 8, 2, 1, 2, 0.4);
        }
        l.playSound(null, BlockPos.containing(p), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 8f, 0.5f);
        for (ServerPlayer pl : l.players()) {
            double d = pl.position().distanceTo(p);
            if (d < 40) ShakePacket.send(pl, 35, (float) Math.max(0.4, 1.6 - d / 40));
            if (!pl.isSpectator() && pl.isAlive() && d <= 5.5) {
                pl.hurt(l.damageSources().explosion(null, titan), 14f);
                double dx = pl.getX() - p.x, dz = pl.getZ() - p.z, len = Math.max(0.5, Math.hypot(dx, dz));
                pl.setDeltaMovement(dx / len, 0.6, dz / len);
                pl.hurtMarked = true;
            }
        }
        hitBuildings(l, r.target, 4, Math.max(1, rs.titanBuildDamage * 1.5f));
        if (r.monolith) com.wavesurvivor.altar.AltarDefense.externalHit(l, rs.titanMonolithDamage);
        crater(l, r.target, 2, now, 300);
        announce(l, "§c" + com.wavesurvivor.i18n.WSLang.t("rift.titan.rock_impact"));
    }

    /**
     * Regard du Titan : ses yeux s'allument, une ligne rouge marque la rangée visée, puis un faisceau balaie l'île d'un bord à l'autre.
     * On s'abrite derrière un bloc. Le sol brûle (magma), les bâtiments sur le passage sont abîmés, les joueurs touchés prennent feu.
     */
    private static void beam(ServerLevel l, long now) {
        BlockPos c = RiftManager.CENTER;
        final double ex = titan.getX(), ey = baseY + titanHeight() * 0.85, ez = titan.getZ() - halfWidth() * 0.5;
        final double span = Math.max(RiftManager.radius(), siegeRadius) * 0.95;
        final double zLine = c.getZ() + 0.5 + (RNG.nextDouble() - 0.6) * span;
        final boolean flip = RNG.nextBoolean();
        schedule(l, now, 60, com.wavesurvivor.i18n.WSLang.t("rift.titan.gaze"), t -> {
            double xx = c.getX() + 0.5 + (flip ? span : -span);
            drawLine(l, ex, ey, ez, xx, c.getY() + 1.2, zLine, RED, 2.5);
            l.sendParticles(ParticleTypes.END_ROD, ex, ey, ez, 8, 2, 1, 2, 0.02);
            for (double x = -span; x <= span; x += 3) l.sendParticles(RED, c.getX() + 0.5 + x, c.getY() + 1.1, zLine, 1, 0, 0, 0, 0);
        }, () -> {
            final int dur = 90;
            l.playSound(null, c, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 6f, 0.5f);
            for (int k = 0; k < dur; k++) {
                final int kk = k;
                RiftManager.later(l.getServer(), k, () -> {
                    if (!active()) return;
                    long nowT = l.getServer().getTickCount();
                    double f = kk / (double) dur;
                    double px = c.getX() + 0.5 + (flip ? span : -span) * (1 - 2 * f), pz = zLine;
                    double py = groundY(l, (int) Math.floor(px), (int) Math.floor(pz)) + 0.2;
                    if (kk % 2 == 0) {
                        drawLine(l, ex, ey, ez, px, py, pz, VIOLET, 1.8);
                        l.sendParticles(ParticleTypes.FLAME, px, py, pz, 6, 0.6, 0.2, 0.6, 0.02);
                        l.sendParticles(ParticleTypes.LAVA, px, py, pz, 1, 0.4, 0.1, 0.4, 0);
                    }
                    if (kk % 3 == 0) scorch(l, BlockPos.containing(px, py - 1, pz), nowT, 160, true);
                    if (kk % 10 == 0) {
                        hitBuildings(l, BlockPos.containing(px, py, pz), 2, Math.max(1, rs.titanBuildDamage / 4f));
                        l.playSound(null, BlockPos.containing(px, py, pz), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 4f, 0.5f);
                    }
                    if (kk % 5 == 0) {
                        Vec3 from = new Vec3(ex, ey, ez), to = new Vec3(px, py, pz);
                        for (ServerPlayer p : alive(l)) {
                            Vec3 pp = p.position().add(0, 1, 0);
                            Vec3 d = to.subtract(from);
                            double tt = Math.max(0, Math.min(1, pp.subtract(from).dot(d) / d.lengthSqr()));
                            if (pp.distanceTo(from.add(d.scale(tt))) > 1.9) continue;
                            var hit = l.clip(new ClipContext(from, p.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, titan));
                            if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceTo(p.getEyePosition()) > 1.5) continue; // à couvert
                            p.hurt(l.damageSources().magic(), 9f);
                            p.setSecondsOnFire(4);
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

    /**
     * Rugissement : une onde violette traverse l'île et repousse loin du Titan (s'accroupir réduit la poussée, un mur la bloque), affaiblit,
     * enraye les engins de siège 6 s et fait surgir des renforts sur le pont. Le Titan est ensuite à découvert.
     */
    private static void roar(ServerLevel l, long now) {
        final double mx = titan.getX(), my = baseY + titanHeight() * 0.8, mz = titan.getZ() - halfWidth() * 0.6;
        Pending pd = schedule(l, now, 50, com.wavesurvivor.i18n.WSLang.t("rift.titan.roar"), t -> {
            l.sendParticles(ParticleTypes.SONIC_BOOM, mx, my, mz, 1, 0, 0, 0, 0);
            l.sendParticles(ParticleTypes.LARGE_SMOKE, mx, my, mz, 10, 3, 2, 3, 0.05);
        }, () -> {
            l.playSound(null, RiftManager.CENTER, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 10f, 0.4f);
            l.playSound(null, RiftManager.CENTER, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 8f, 0.5f);
            final int gy = RiftManager.CENTER.getY() + 1;
            for (int k = 0; k < 30; k++) {
                final int kk = k;
                RiftManager.later(l.getServer(), k, () -> {
                    double r = 6 + kk * 2.4;
                    int pts = (int) Math.max(12, r * 1.5);
                    for (int i = 0; i < pts; i++) {
                        double a = Math.PI + Math.PI * (i + 0.5) / pts;
                        l.sendParticles(VIOLET, mx + Math.cos(a) * r, gy + 0.6, mz + Math.sin(a) * r, 1, 0, 0.3, 0, 0);
                    }
                });
            }
            Vec3 mouth = new Vec3(mx, my, mz);
            for (ServerPlayer p : alive(l)) {
                ShakePacket.send(p, 50, 1.4f);
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
                var hit = l.clip(new ClipContext(mouth, p.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, titan));
                if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceTo(p.getEyePosition()) > 1.5) continue;   // à l'abri
                double dx = p.getX() - mx, dz = p.getZ() - mz, len = Math.max(0.1, Math.hypot(dx, dz));
                double force = p.isShiftKeyDown() ? 0.35 : 1.7;
                p.setDeltaMovement(p.getDeltaMovement().add(dx / len * force, p.isShiftKeyDown() ? 0.1 : 0.5, dz / len * force));
                p.hurtMarked = true;
            }
            RiftEngines.jam(l, 120);
            announce(l, "§5" + com.wavesurvivor.i18n.WSLang.t("rift.titan.jammed"));
            RiftManager.titanWave(l, Math.max(2, rs.titanWaveBase));
        });
        pd.window = true;
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
        if (charge != 0) {
            // Pendant toute la charge : au plus 20 % des PV max perdus (×1,5 pendant le stun, ×0,5 sinon)
            float room = Math.max(0f, (float) (maxHp * rs.titanChargeCapPercent / 100.0) - chargeDealt);
            float out = Math.min(amount * (stunning ? 1.5f : 0.5f), room);
            chargeDealt += out;
            return out;
        }
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
                if (now % 8 == 0) crackTelegraph(l);
                if (now >= chargeAt) { charge = 2; chargeAt = now + 20; }
            }
            case 2 -> {   // élan : le Titan avance vers l'île
                double f = 1.0 - (chargeAt - now) / 20.0;
                f = Math.max(0, Math.min(1, f));
                double s = f * f * (3 - 2 * f);
                titan.setPos(baseX + (chargeX - baseX) * s, baseY, baseZ + (chargeTargetZ - baseZ) * s);
                placeHitboxes(l);
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
                titan.setPos(chargeX + (baseX - chargeX) * s, baseY, chargeTargetZ + (baseZ - chargeTargetZ) * s);
                placeHitboxes(l);
                if (now >= chargeAt) {
                    titan.setPos(baseX, baseY, baseZ);
                    placeHitboxes(l);
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
        // Secteur visé : le centre du bord sud, puis le sud-ouest, puis le sud-est (ensuite au hasard, sans rien rebriser)
        chargeSector = chargeCount < SECTOR_OFF.length ? chargeCount : RNG.nextInt(SECTOR_OFF.length);
        chargeCount++;
        double off = SECTOR_OFF[chargeSector] * sr;
        chargeX = c.getX() + 0.5 + off;
        double edge = Math.sqrt(Math.max(0, (double) sr * sr - off * off));
        // Le bord nord du Titan arrive à « reach » blocs à l'intérieur de l'île
        chargeTargetZ = c.getZ() + 0.5 + edge - reach + halfWidth();
        double hw = Math.min(halfWidth(), 14);   // zone d'impact réduite : seul le bord visé de l'île est touché
        zone = new double[]{chargeX - hw, chargeX + hw, c.getZ() + 0.5 + edge - reach - 2, c.getZ() + 0.5 + edge + 4};
        chargeDealt = 0f;
        stunBonus = 0;
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
        // Les bâtiments pris dans la zone sont écrasés
        hitRect(l, zone[0], zone[1], zone[2], zone[3], Math.max(1, rs.titanBuildDamage));
        breakSector(l, now);
        charge = 3;
        chargeAt = now + Math.max(2, rs.titanChargeStun) * 20L + stunBonus;
        stunBonus = 0;
        stunning = true;
        windowUntil = chargeAt;
        windowDealt = 0f;
        titan.setGlowingTag(true);
        if (bar != null) bar.setColor(BossEvent.BossBarColor.RED);
        announce(l, "§c§l⚔ Le Titan est à terre ! Frappez-le maintenant (" + rs.titanChargeStun + " s) !");
    }

    // ─── L'île se brise : chaque charge arrache un secteur du bord sud (une seule fois par secteur) ───

    private static final double[] SECTOR_OFF = {0.0, -0.42, 0.42};

    /** Pendant l'annonce de la charge : des fissures rampent sur le bord qui va s'effondrer. */
    private static void crackTelegraph(ServerLevel l) {
        if (!rs.titanIslandBreak || brokenSectors.contains(chargeSector) || zone == null) return;
        BlockPos c = RiftManager.CENTER;
        int sr = siegeRadius, depth = Math.max(2, rs.titanBreakDepth);
        for (int i = 0; i < 16; i++) {
            double x = zone[0] + RNG.nextDouble() * (zone[1] - zone[0]);
            double dx = x - c.getX() - 0.5;
            double z = c.getZ() + 0.5 + Math.sqrt(Math.max(0, (double) sr * sr - dx * dx)) - RNG.nextDouble() * depth;
            int gy = groundY(l, (int) Math.floor(x), (int) Math.floor(z));
            if (gy <= l.getMinBuildHeight() + 1) continue;
            l.sendParticles(ORANGE, x, gy + 0.05, z, 2, 0.4, 0, 0.4, 0);
            BlockState st = l.getBlockState(new BlockPos((int) Math.floor(x), gy - 1, (int) Math.floor(z)));
            if (!st.isAir()) l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, st), x, gy + 0.1, z, 3, 0.3, 0.1, 0.3, 0.05);
        }
        l.playSound(null, BlockPos.containing(chargeX, c.getY(), zone[3]), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 3f, 0.5f);
    }

    /** La charge arrache le bord percuté : un arc du cercle s'effondre dans l'abîme, du bord vers l'intérieur (le centre est épargné). */
    private static void breakSector(ServerLevel l, long now) {
        if (!rs.titanIslandBreak || !brokenSectors.add(chargeSector)) return;
        BlockPos c = RiftManager.CENTER;
        int sr = siegeRadius, depth = Math.max(2, Math.min(sr / 2, rs.titanBreakDepth));
        int x0 = (int) Math.floor(zone[0]), x1 = (int) Math.ceil(zone[1]);
        List<int[]> cols = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = c.getZ(); z <= c.getZ() + sr + 3; z++) {
                double d = Math.hypot(x - c.getX(), z - c.getZ());
                if (d < sr - depth || d > sr + 3 || d < 12) continue;
                cols.add(new int[]{x, z, (int) Math.round((sr + 3 - d) / (depth + 3.0) * 40)});
            }
        }
        if (cols.isEmpty()) return;
        announce(l, "§6§l" + com.wavesurvivor.i18n.WSLang.t("rift.titan.island_break"));
        l.playSound(null, BlockPos.containing(chargeX, c.getY(), zone[3]), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 8f, 0.3f);
        final int[] falling = {0};
        for (int[] col : cols) RiftManager.later(l.getServer(), 4 + col[2] + RNG.nextInt(4), () -> collapseColumn(l, col[0], col[1], falling));
        // Les bâtiments posés sur ce bord s'écroulent avec lui
        hitRect(l, zone[0], zone[1], c.getZ() + sr - depth, c.getZ() + sr + 3, Math.max(1, rs.titanBuildDamage * 4f));
    }

    /** Une colonne de l'île tombe : les blocs du dessus chutent (blocs qui tombent), le reste disparaît dans l'abîme. */
    private static void collapseColumn(ServerLevel l, int x, int z, int[] falling) {
        BlockPos c = RiftManager.CENTER;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int seen = 0;
        for (int y = c.getY() + 14; y >= c.getY() - 45; y--) {
            m.set(x, y, z);
            BlockState st = l.getBlockState(m);
            if (st.isAir() || st.getBlock() instanceof net.minecraft.world.level.block.EntityBlock) continue;
            if (st.getDestroySpeed(l, m) < 0) continue;   // incassable (bedrock, barrière)
            if (seen < 2 && falling[0] < 220 && RNG.nextInt(2) == 0) {
                net.minecraft.world.entity.item.FallingBlockEntity fb = net.minecraft.world.entity.item.FallingBlockEntity.fall(l, m.immutable(), st);
                fb.disableDrop();
                fb.dropItem = false;
                falling[0]++;
            } else {
                l.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
            }
            seen++;
        }
        if (RNG.nextInt(3) == 0) l.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, c.getY() + 1, z + 0.5, 2, 0.3, 0.3, 0.3, 0.02);
        if (RNG.nextInt(8) == 0) l.playSound(null, new BlockPos(x, c.getY(), z), SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 2f, 0.5f);
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

    // ─── Cinématique d'apparition (~12 s) : silence, l'abîme s'éveille, le Titan surgit, rugissement, son nom, fin ───

    private static double titanHeight() {
        EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(rs.titanEntity));
        return (t != null ? t.getHeight() : 2.7) * rs.titanScale;
    }

    private static void sound(ServerLevel l, net.minecraft.sounds.SoundEvent ev, float vol, float pitch) {
        if (rs.titanCineSounds) l.playSound(null, BlockPos.containing(baseX, RiftManager.CENTER.getY(), baseZ), ev, SoundSource.HOSTILE, vol, pitch);
    }

    private static void shakeAll(ServerLevel l, int ticks, float amp) {
        for (ServerPlayer p : l.players()) ShakePacket.send(p, ticks, amp);
    }

    /** Aucune animation de modèle : seulement position, lumière, sons et particules (marche avec n'importe quelle entité Titan). */
    private static void cineTick(ServerLevel l, long now) {
        long t = now - cineStart;
        double f = cineF;
        long tAbyss = (long) (40 * f), tRise = (long) (80 * f), tRoar = (long) (160 * f), tName = (long) (200 * f), tEnd = (long) (240 * f);
        double hw = halfWidth(), height = titanHeight();
        BlockPos c = RiftManager.CENTER;
        // 2. L'abîme s'éveille : lueur violette sous l'horizon, secousses qui montent
        if (t >= tAbyss && t < tRise) {
            double p = (t - tAbyss) / (double) Math.max(1, tRise - tAbyss);
            if (!cineAbyss) {
                cineAbyss = true;
                sound(l, SoundEvents.WARDEN_EMERGE, 8f, 0.4f);
            }
            if (t == tAbyss + (tRise - tAbyss) / 2) sound(l, SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value(), 6f, 0.6f);
            if (t % 2 == 0) {
                for (int i = 0; i < 6 + (int) (14 * p); i++) {
                    double x = baseX + (RNG.nextDouble() - 0.5) * (hw * 2 + 30), z = baseZ + (RNG.nextDouble() - 0.5) * (hw * 2 + 30);
                    l.sendParticles(VIOLET, x, c.getY() - 3 + RNG.nextDouble() * 6, z, 1, 0, 0.2, 0, 0.02);
                }
            }
            if (t % 8 == 0) shakeAll(l, 10, (float) (0.3 + 0.7 * p));
        }
        // 3. Le Titan surgit : il monte de très bas jusqu'à sa place, ralenti en fin de course
        if (t >= tRise && t < tRoar) {
            double p = (t - tRise) / (double) Math.max(1, tRoar - tRise);
            if (!cineRise) {
                cineRise = true;
                titan.removeEffect(MobEffects.INVISIBILITY);
                titan.setGlowingTag(true);   // tête et yeux mis en valeur (lueur universelle)
                sound(l, SoundEvents.IRON_GOLEM_HURT, 10f, 0.3f);
            }
            if (!cineGrowl && p > 0.5) { cineGrowl = true; sound(l, SoundEvents.ENDER_DRAGON_GROWL, 10f, 0.4f); }
            double e = 1 - Math.pow(1 - p, 3);
            titan.setPos(baseX, (baseY - 90) + 90 * e, baseZ);
            if (t % 3 == 0) {   // débris qui tombent de ses épaules, étincelles à la tête
                double sy = titan.getY() + height * 0.8;
                for (int i = 0; i < 8; i++) {
                    double x = baseX + (RNG.nextDouble() - 0.5) * hw * 1.6;
                    l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()),
                            x, sy, baseZ - hw * 0.5, 3, 1.0, 0.3, 1.0, 0.05);
                }
                l.sendParticles(ParticleTypes.END_ROD, baseX, titan.getY() + height * 0.92, baseZ - hw * 0.4, 6, hw * 0.15, 0.5, 0.3, 0.02);
            }
            if (t % 5 == 0) shakeAll(l, 8, 1.1f);
        }
        if (t >= tRoar && cineRise && titan.getY() < baseY) titan.setPos(baseX, baseY, baseZ);
        // 4. Le rugissement : onde de choc violette qui traverse l'île, secousse maximale
        if (t >= tRoar && t < tName) {
            if (!cineRoar) {
                cineRoar = true;
                sound(l, SoundEvents.RAVAGER_ROAR, 12f, 0.5f);
                sound(l, SoundEvents.WITHER_SPAWN, 10f, 0.5f);
                shakeAll(l, (int) (tName - tRoar), 1.8f);
            }
            if (t % 2 == 0) {
                double reach = distance(rs, siegeRadius) + siegeRadius + 20, r = (t - tRoar) / (double) Math.max(1, tName - tRoar) * reach;
                for (int i = 0; i < 140; i++) {
                    double a = i * Math.PI * 2 / 140;
                    l.sendParticles(VIOLET, baseX + Math.cos(a) * r, c.getY() + 1.2, baseZ + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
        }
        // 5. Le nom : titre et sous-titre, puis la barre de vie
        if (t >= tName && !cineNamed) {
            cineNamed = true;
            for (ServerPlayer p : l.players()) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, Math.max(20, (int) ((tEnd - tName) - 20)), 10));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7" + rs.titanCineSub)));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§5§l" + rs.titanCineName)));
                if (bar != null) bar.addPlayer(p);
            }
        }
        // 6. Fin : bandes retirées, caméra rendue, contrôle rendu ; le Titan attend 5 s avant sa première attaque
        if (t >= tEnd) {
            emerging = false;
            titan.setInvulnerable(false);
            titan.setGlowingTag(false);
            titan.setPos(baseX, baseY, baseZ);
            for (ServerPlayer p : l.players()) {
                p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                p.removeEffect(MobEffects.JUMP);
                p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                if (cineOn && !cineSkipped.contains(p.getUUID())) {
                    com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                            new com.wavesurvivor.network.TitanCinePackets.Cine(false, 0, 0, 0, 0, false));
                }
            }
            spawnHitboxes(l);
            spawnCrystals(l);
            nextSkillAt = now + 100;
            announce(l, "§5§l⚔ Le Titan vous a vus !");
            summonAvatars(l);
        }
    }

    /** Un joueur passe la cinématique : il reprend le contrôle (le combat commence à la fin prévue pour tout le monde). */
    static void cineSkip(ServerPlayer p) {
        if (!emerging || !cineOn || !cineSkipped.add(p.getUUID())) return;
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        p.removeEffect(MobEffects.JUMP);
        p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.wavesurvivor.network.TitanCinePackets.Cine(false, 0, 0, 0, 0, false));
    }

    // ─── Points faibles (cristaux sur le corps du Titan) ───

    /** (Re)pose les cristaux manquants sur la face avant du Titan, bas du corps : atteignables aux engins, aux arcs et en mêlée. */
    private static void spawnCrystals(ServerLevel l) {
        if (titan == null) return;
        int n = Math.max(0, Math.min(8, rs.titanCrystals));
        double hw = halfWidth();
        // retire ceux qui n'existent plus
        for (int i = crystals.size() - 1; i >= 0; i--) {
            Entity e = l.getEntity(crystals.get(i));
            if (e == null || e.isRemoved()) { crystals.remove(i); crystalOff.remove(i); }
        }
        if (n == 0) return;
        for (int i = 0; i < n; i++) {
            float dx = (float) ((i + 0.5) / n * 2 * hw * 0.8 - hw * 0.8), dy = 5f + (i % 2) * 7f;
            boolean have = false;
            for (float[] o : crystalOff) if (Math.abs(o[0] - dx) < 0.01f) { have = true; break; }
            if (have) continue;
            UUID id = RiftFortress.spawnTitanCrystal(l, titan.getX() + dx, titan.getY() + dy, titan.getZ() - hw - 1.5, Math.max(10, rs.titanCrystalHp), "§dPoint faible");
            crystals.add(id);
            crystalOff.add(new float[]{dx, dy});
        }
        if (now0(l) % 1 == 0) announce(l, "§d§l✦ Des points faibles apparaissent sur le Titan ! Détruisez-les (−" + rs.titanCrystalPercent + " % de ses PV chacun).");
    }

    private static long now0(ServerLevel l) { return l.getServer().getTickCount(); }

    private static void placeCrystals(ServerLevel l) {
        if (titan == null) return;
        double hw = halfWidth();
        for (int i = 0; i < crystals.size(); i++) {
            Entity e = l.getEntity(crystals.get(i));
            if (e == null) continue;
            float[] o = crystalOff.get(i);
            e.setPos(titan.getX() + o[0], titan.getY() + o[1], titan.getZ() - hw - 1.5);
        }
    }

    /** Un point faible est détruit : −5 % des PV max du Titan, et le stun est prolongé de 2 s si la charge est en cours. */
    static void crystalDown(ServerLevel l) {
        if (titan == null || dead || !titan.isAlive()) return;
        float cut = (float) (maxHp * Math.max(0, rs.titanCrystalPercent) / 100.0);
        titan.setHealth(Math.max(1f, titan.getHealth() - cut));
        chargeDealt += charge != 0 ? cut : 0f;
        l.playSound(null, titan.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 6f, 0.5f);
        for (ServerPlayer p : l.players()) ShakePacket.send(p, 20, 0.6f);
        long bonus = Math.max(0, rs.titanCrystalStunBonus) * 20L;
        if (charge == 3) {            // stun en cours : prolongé
            chargeAt += bonus;
            windowUntil += bonus;
            announce(l, "§d§l✦ Point faible détruit : −" + rs.titanCrystalPercent + " % de ses PV et stun prolongé de " + rs.titanCrystalStunBonus + " s !");
        } else if (charge != 0 && charge < 3) {   // détruit pendant l'annonce ou l'élan : le stun suivant sera plus long
            stunBonus += bonus;
            announce(l, "§d§l✦ Point faible détruit : −" + rs.titanCrystalPercent + " % de ses PV (stun plus long à l'impact) !");
        } else {
            announce(l, "§d§l✦ Point faible détruit : −" + rs.titanCrystalPercent + " % de ses PV !");
        }
    }

    // ─── Invasion continue entre les phases (portail sur le pont) ───

    private static void waveTick(ServerLevel l, long now) {
        if (phase < 1 || now < nextWaveAt || titan == null) return;
        nextWaveAt = now + Math.max(3, rs.titanWaveInterval) * 20L;
        // 5 unités par vague à la phase 2, doublées à chaque phase suivante
        int count = Math.max(1, rs.titanWaveBase) * (1 << (Math.min(3, phase) - 1));
        int near = l.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                new net.minecraft.world.phys.AABB(RiftManager.CENTER).inflate(200, 80, 200)).size();
        if (near >= Math.max(5, rs.titanWaveCap)) return;
        RiftManager.titanWave(l, count);
    }

    // ─── Attaque d'un bâtiment ───

    private static List<BlockPos> defensePositions() {
        List<BlockPos> out = new ArrayList<>();
        com.wavesurvivor.horde.kingdom.KingdomDefenses.forEachDefense((p, k) -> out.add(p));
        if (titan != null && titan.level() instanceof ServerLevel l) out.addAll(RiftEngines.positions(l));   // les engins de siège aussi
        return out;
    }

    private static boolean warnedNoBuildings;

    /** Dégâts aux bâtiments du royaume (murs, tours, remparts) ET aux engins de siège dans un rayon. */
    private static int hitBuildings(ServerLevel l, BlockPos at, int r, float dmg) {
        RiftEngines.damageNear(l, Vec3.atCenterOf(at), r + 1.5, dmg);
        if (!warnedNoBuildings && com.wavesurvivor.horde.kingdom.KingdomClaim.trackedCount() == 0) {
            warnedNoBuildings = true;
            WaveSurvivorMod.LOGGER.info("[Faille] Titan : aucun bâtiment du royaume n'a de PV suivis (claim inactif ou vide).");
        }
        return com.wavesurvivor.horde.kingdom.KingdomClaim.explosionDamage(at, r, dmg);
    }

    private static int hitRect(ServerLevel l, double x0, double x1, double z0, double z1, float dmg) {
        RiftEngines.damageRect(l, x0, x1, z0, z1, dmg);
        return com.wavesurvivor.horde.kingdom.KingdomClaim.damageRect(x0, x1, z0, z1, dmg);
    }

    /** Le Titan vise un bâtiment : annoncé (cercle rouge), puis tout ce qui est dans la zone est endommagé. */
    private static void buildingStrike(ServerLevel l, long now) {
        List<BlockPos> list = defensePositions();
        if (list.isEmpty()) return;
        BlockPos t = list.get(RNG.nextInt(list.size()));
        final double r = Math.max(2, rs.titanBuildRadius);
        schedule(l, now, 60, "Le Titan écrase un bâtiment !", x -> ring(l, t.getX() + 0.5, t.getY() + 0.2, t.getZ() + 0.5, r, RED, 28), () -> {
            l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, t.getX() + 0.5, t.getY() + 1, t.getZ() + 0.5, 2, 0.8, 0.5, 0.8, 0);
            l.playSound(null, t, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 6f, 0.5f);
            hitBuildings(l, t, (int) Math.ceil(r), Math.max(1, rs.titanBuildDamage));
            for (ServerPlayer p : alive(l)) {
                if (Math.hypot(p.getX() - t.getX() - 0.5, p.getZ() - t.getZ() - 0.5) <= r) p.hurt(l.damageSources().explosion(null, titan), 10f);
                ShakePacket.send(p, 25, 1.0f);
            }
        });
    }

    // ─── Ambiance des phases (visuel) ───

    private static void phaseCinematic(ServerLevel l) {
        String[] names = {"", "§e§lPHASE 2", "§6§lPHASE 3", "§c§lPHASE 4"};
        String[] subs = {"", "§7L'invasion reprend…", "§7L'île commence à se fissurer…", "§7Le Titan est enragé !"};
        for (ServerPlayer p : l.players()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subs[Math.min(3, phase)])));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(names[Math.min(3, phase)])));
            ShakePacket.send(p, 60, 1.4f);
        }
        l.playSound(null, RiftManager.CENTER, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 6f, 0.5f);
    }

    /** Ambiance continue : braises (phase 2), éclairs visuels (phase 3), fissures qui rampent sur l'île (phase 4). */
    private static void phaseFx(ServerLevel l, long now) {
        if (phase < 1 || titan == null) return;
        BlockPos c = RiftManager.CENTER;
        int sr = Math.max(16, siegeRadius);
        if (now % 10 == 0) {
            for (int i = 0; i < 6; i++) {
                double a = RNG.nextDouble() * Math.PI * 2, d = RNG.nextDouble() * sr;
                l.sendParticles(phase >= 3 ? ParticleTypes.LAVA : ParticleTypes.FLAME, c.getX() + 0.5 + Math.cos(a) * d, c.getY() + 1.5 + RNG.nextDouble() * 4, c.getZ() + 0.5 + Math.sin(a) * d, 1, 0.2, 0.5, 0.2, 0.02);
            }
        }
        if (phase >= 2 && now % 90 == 0) {   // éclair visuel loin des joueurs
            net.minecraft.world.entity.LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
            if (bolt != null) {
                double a = RNG.nextDouble() * Math.PI * 2, d = sr + 8 + RNG.nextDouble() * 20;
                bolt.moveTo(c.getX() + 0.5 + Math.cos(a) * d, c.getY(), c.getZ() + 0.5 + Math.sin(a) * d);
                bolt.setVisualOnly(true);
                l.addFreshEntity(bolt);
            }
        }
        if (phase >= 3 && now % 20 == 0) {   // fissures : des lignes de particules rayonnent depuis le bord sud de l'île vers le Monolithe
            for (int k = 0; k < 4; k++) {
                double a = Math.PI / 2 + (RNG.nextDouble() - 0.5) * 1.2;
                for (double d = sr; d > sr * 0.35; d -= 1.4) {
                    l.sendParticles(ParticleTypes.CRIT, c.getX() + 0.5 + Math.cos(a) * d + (RNG.nextDouble() - 0.5), c.getY() + 1.05, c.getZ() + 0.5 + Math.sin(a) * d, 1, 0, 0, 0, 0);
                }
            }
            if (now % 100 == 0) l.playSound(null, c, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 3f, 0.4f);
        }
    }

    private static void onTitanDeath(ServerLevel l) {
        charge = 0;
        stunning = false;
        strikes.clear();
        dead = true;
        pending.clear();
        clearMeteors(l);
        clearHitboxes(l);
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
        clearHitboxes(l);
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
