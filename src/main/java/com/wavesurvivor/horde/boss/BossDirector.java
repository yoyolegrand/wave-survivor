package com.wavesurvivor.horde.boss;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.model.BossWave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * DIRECTEUR DES BOSS — socle de difficulté « Boss 2.0 », appliqué à TOUS les boss (vanilla, moddés, custom).
 *
 *   1. MISE À L'ÉCHELLE : à l'apparition, PV × (1 + hpPerExtraPlayer × (joueurs − 1)).
 *   2. PHASES à 66 % et 33 % des PV : onde de choc, 1,5 s d'invulnérabilité, bouclier, puis boss plus rapide,
 *      plus fort, compétences plus fréquentes (cooldownMultiplier) et compétences « unlockPhase » débloquées.
 *   3. ANTI-EXPLOITATION : un joueur hors d'atteinte (trop loin, perché, inaccessible ou caché) pendant leashSeconds
 *      est attiré vers le boss, ou le boss se téléporte près de lui.
 *   4. BOUCLIER qui renvoie les projectiles : au changement de phase, puis régulièrement à partir de la phase 2.
 *   5. PLAFOND DE DÉGÂTS par coup (% des PV max).
 *   6. AFFIXES aléatoires (1 à 2 par apparition, affichés dans la barre) : Vampirique, Blindé, Invocateur, Rapide,
 *      Réflecteur, Volatile.
 * Réglages par boss : BossWave.difficulty (activés par défaut).
 */
public class BossDirector {

    private static final Random RNG = new Random();

    /** Affixes (tirés au hasard ou choisis dans l'éditeur). */
    public enum Affix {
        VAMPIRIC("vampiric"), ARMORED("armored"), SUMMONER("summoner"), SWIFT("swift"), REFLECTOR("reflector"), VOLATILE("volatile"),
        GLACIAL("glacial"), INFERNAL("infernal"), STORMCALLER("stormcaller"), BLINKING("blinking"),
        REGENERATING("regenerating"), BERSERKER("berserker"), MAGNETIC("magnetic"), TOXIC("toxic");

        public final String id;

        Affix(String id) { this.id = id; }

        public String label() { return com.wavesurvivor.i18n.WSLang.t("boss.affix." + id); }
    }

    private record Mine(Vec3 pos, long armedAt, long expiresAt) {}

    private static final class State {
        final BossWave.BossDifficulty cfg;
        final String name;
        final String minionType;
        int phase = 1;
        long invulnerableUntil = 0;
        long reflectUntil = 0;
        long nextReflect = Long.MAX_VALUE;
        double baseSpeed = -1, baseDamage = -1;
        final Map<UUID, Integer> unreachable = new HashMap<>();
        ServerLevel level;
        // Affixes
        final List<Affix> affixes = new ArrayList<>();
        long armoredUntil = 0, nextArmored = 0, nextSummon = 0, nextAffixReflect = 0, nextMine = 0;
        // Nouveaux affixes : Orageux (frappe annoncée), Téléporteur, Régénérant (dernier coup reçu), Magnétique
        long nextStorm = 0, stormAt = 0, nextBlink = 0, lastHurt = 0, nextMagnet = 0;
        Vec3 stormPos = null;
        Vec3 lastPos = null;
        final List<UUID> summons = new ArrayList<>();
        final List<Mine> mines = new ArrayList<>();

        State(BossWave.BossDifficulty cfg, String name, String minionType) {
            this.cfg = cfg;
            this.name = name;
            this.minionType = minionType;
        }

        boolean has(Affix a) { return affixes.contains(a); }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    // ─── API ───

    /** À appeler juste après l'apparition d'un boss. */
    public static void register(LivingEntity boss, BossWave wave) {
        if (boss == null || wave == null || !(boss.level() instanceof ServerLevel level)) return;
        // Niveau de difficulté de la horde (avant tout le reste : s'applique même sans difficulté adaptative)
        com.wavesurvivor.horde.difficulty.HordeDifficulty.applyToBoss(boss);
        // Taille du boss (mod Pehkui, optionnel) : prioritaire sur la taille d'unité
        boss.getPersistentData().putBoolean("ws_boss_scaled", true);
        com.wavesurvivor.compat.PehkuiCompat.setScale(boss, wave.scale > 0 ? wave.scale : 1.0);
        BossWave.BossDifficulty cfg = wave.difficulty();
        if (!cfg.enabled) return;
        String minion = wave.minionConfig != null && wave.minionConfig.minionType != null && !wave.minionConfig.minionType.isBlank()
                ? wave.minionConfig.minionType : "minecraft:vex";
        State s = new State(cfg, boss.getName().getString(), minion);
        s.level = level;
        long now = level.getServer().getTickCount();

        // Mise à l'échelle selon les joueurs présents autour du boss
        int players = 0;
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && p.distanceToSqr(boss) < 64 * 64) players++;
        }
        double mult = 1 + Math.max(0, cfg.hpPerExtraPlayer) * Math.max(0, players - 1);
        AttributeInstance hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && mult > 1.0001) {
            hp.setBaseValue(hp.getBaseValue() * mult);
            boss.setHealth(boss.getMaxHealth());
        }

        // Affixes aléatoires
        if (cfg.affixes) rollAffixes(s);
        AttributeInstance spd = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) {
            if (s.has(Affix.SWIFT)) spd.setBaseValue(spd.getBaseValue() * 1.3);
            s.baseSpeed = spd.getBaseValue();
        }
        AttributeInstance dmg = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) s.baseDamage = dmg.getBaseValue();
        s.nextArmored = now + 200;
        s.nextSummon = now + 200;
        s.nextAffixReflect = now + 300;
        s.nextMine = now + 60;

        ACTIVE.put(boss.getUUID(), s);
        // Mutateur « Enragés » : +50 % de PV et directement en phase 3 (mécaniques de fin de combat dès le début)
        if (com.wavesurvivor.horde.mutator.HordeMutators.on(com.wavesurvivor.horde.mutator.Mutator.ENRAGED)) {
            AttributeInstance h2 = boss.getAttribute(Attributes.MAX_HEALTH);
            if (h2 != null) {
                h2.setBaseValue(h2.getBaseValue() * 1.5);
                boss.setHealth(boss.getMaxHealth());
            }
            if (cfg.phases) enterPhase(s, boss, 3, now);
        }
        // Mutateur « Escorte » : 3 unités parmi les plus robustes de la horde accompagnent le boss
        if (com.wavesurvivor.horde.mutator.HordeMutators.on(com.wavesurvivor.horde.mutator.Mutator.ESCORT)) spawnEscort(level, boss);
        WaveSurvivorMod.LOGGER.info("[BossDirector] '{}' : {} joueur(s) → PV ×{} ({} PV), affixes {}", s.name, players,
                String.format("%.2f", mult), Math.round(boss.getMaxHealth()), s.affixes);
    }

    private static void rollAffixes(State s) {
        List<Affix> pool = new ArrayList<>();
        for (Affix a : Affix.values()) {
            if (s.cfg.allowedAffixes == null || s.cfg.allowedAffixes.isEmpty() || s.cfg.allowedAffixes.contains(a.id)) pool.add(a);
        }
        // Mode « fixe » : exactement les affixes choisis dans l'éditeur, sans tirage
        if ("fixed".equalsIgnoreCase(s.cfg.affixMode) && s.cfg.allowedAffixes != null && !s.cfg.allowedAffixes.isEmpty()) {
            s.affixes.addAll(pool);
            return;
        }
        Collections.shuffle(pool, RNG);
        int min = Math.max(0, s.cfg.affixMin), max = Math.max(min, s.cfg.affixMax);
        int n = Math.min(pool.size(), min + (max > min ? RNG.nextInt(max - min + 1) : 0));
        for (int i = 0; i < n; i++) s.affixes.add(pool.get(i));
    }

    /** Texte des affixes pour la barre du boss (« » si aucun). */
    public static String affixSuffix(UUID uuid) {
        State s = ACTIVE.get(uuid);
        if (s == null || s.affixes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" §8[");
        for (int i = 0; i < s.affixes.size(); i++) {
            if (i > 0) sb.append("§8 · ");
            sb.append(s.affixes.get(i).label());
        }
        return sb.append("§8]").toString();
    }

    /** Mutateur « Escorte » : 3 unités de la horde (la plus robuste hors unités de siège) apparaissent autour du boss. */
    private static void spawnEscort(ServerLevel level, LivingEntity boss) {
        var h = com.wavesurvivor.horde.HordeManager.get().getActiveHorde();
        if (h == null || h.configData == null || h.configData.hordeEntities == null) return;
        com.wavesurvivor.horde.model.HordeEntity best = null;
        for (var t : h.configData.hordeEntities) {
            if (t == null || (t.siegeRole != null && !t.siegeRole.isBlank())) continue;
            if (best == null || t.maxHealth > best.maxHealth) best = t;
        }
        if (best == null) return;
        int n = com.wavesurvivor.horde.spawn.HordeSpawner.spawnMobs(level, best, boss.blockPosition(), 4, 3, Math.max(1, level.players().size()));
        WaveSurvivorMod.LOGGER.info("[BossDirector] Escorte : {} × {} autour de '{}'", n, best.entityType, boss.getName().getString());
    }

    public static void unregister(UUID uuid) {
        State s = ACTIVE.remove(uuid);
        if (s != null) discardSummons(s);
    }

    public static void clearAll() {
        for (State s : ACTIVE.values()) discardSummons(s);
        ACTIVE.clear();
    }

    private static void discardSummons(State s) {
        if (s.level == null) return;
        for (UUID id : s.summons) {
            Entity e = s.level.getEntity(id);
            if (e != null) e.discard();
        }
        s.summons.clear();
        s.mines.clear();
    }

    /** Phase actuelle du boss (1 si inconnu). */
    public static int phase(UUID uuid) {
        State s = ACTIVE.get(uuid);
        return s != null ? s.phase : 1;
    }

    /** Multiplicateur appliqué aux temps de recharge des compétences du boss (1 = normal). */
    public static double cooldownMultiplier(UUID uuid) {
        State s = ACTIVE.get(uuid);
        if (s == null) return 1;
        double m = s.cfg.phases ? Math.pow(Math.max(0.2, Math.min(1, s.cfg.phaseCooldownMult)), s.phase - 1) : 1;
        if (s.has(Affix.SWIFT)) m *= 0.85;
        return m;
    }

    // ─── Tick ───

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, State>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> e = it.next();
            State s = e.getValue();
            LivingEntity boss = find(server, e.getKey(), s);
            if (boss == null || !boss.isAlive()) { discardSummons(s); it.remove(); continue; }

            // Phases
            if (s.cfg.phases) {
                float pct = boss.getHealth() / Math.max(1f, boss.getMaxHealth());
                int target = pct <= 0.33f ? 3 : pct <= 0.66f ? 2 : 1;
                if (target > s.phase) enterPhase(s, boss, target, now);
            }

            // Bouclier réflecteur (dès la phase 2)
            if (s.cfg.reflectShield && now >= s.nextReflect && s.phase >= 2) {
                s.reflectUntil = Math.max(s.reflectUntil, now + Math.max(1, s.cfg.reflectDurationSeconds) * 20L);
                s.nextReflect = now + Math.max(5, s.cfg.reflectEverySeconds) * 20L;
                announce(s, boss, "boss.shield", s.name);
                s.level.playSound(null, boss.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5f, 1.4f);
            }
            if (now < s.reflectUntil && now % 4 == 0) shieldParticles(s.level, boss);

            tickAffixes(s, boss, now);

            // Anti-exploitation (1 fois par seconde)
            if (s.cfg.antiCheese && now % 20 == 0) antiCheese(s, boss);
        }
    }

    private static void tickAffixes(State s, LivingEntity boss, long now) {
        ServerLevel lvl = s.level;
        // 🛡 Blindé : 5 s de réduction des dégâts toutes les 12 s
        if (s.has(Affix.ARMORED)) {
            if (now >= s.nextArmored) {
                s.armoredUntil = now + 100;
                s.nextArmored = now + 240;
                lvl.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.HOSTILE, 1.5f, 0.7f);
            }
            if (now < s.armoredUntil && now % 5 == 0) {
                lvl.sendParticles(ParticleTypes.CRIT, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(),
                        8, boss.getBbWidth() * 0.6, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.6, 0.05);
            }
        }
        // 👥 Invocateur : 2 sbires toutes les 15 s (6 vivants au maximum)
        if (s.has(Affix.SUMMONER) && now >= s.nextSummon) {
            s.nextSummon = now + 300;
            s.summons.removeIf(id -> { Entity m = lvl.getEntity(id); return m == null || !m.isAlive(); });
            int room = Math.max(0, 6 - s.summons.size());
            for (int i = 0; i < Math.min(2, room); i++) summon(s, boss);
        }
        // 🪞 Réflecteur : bouclier dès la phase 1, toutes les 15 s pendant 4 s
        if (s.has(Affix.REFLECTOR) && now >= s.nextAffixReflect) {
            s.nextAffixReflect = now + 300;
            s.reflectUntil = Math.max(s.reflectUntil, now + 80);
            lvl.playSound(null, boss.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.2f, 1.6f);
        }
        // 💣 Volatile : sème des mines en se déplaçant
        if (s.has(Affix.VOLATILE)) {
            Vec3 pos = boss.position();
            if (now >= s.nextMine) {
                s.nextMine = now + 60;
                if (s.lastPos != null && s.lastPos.distanceToSqr(pos) > 1.0 && s.mines.size() < 8) {
                    s.mines.add(new Mine(pos, now + 20, now + 300));
                    // Pose d'une mine : « clic » mécanique bien audible
                    s.level.playSound(null, BlockPos.containing(pos), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.HOSTILE, 1.2f, 0.6f);
                }
                s.lastPos = pos;
            }
            if (now % 5 == 0) tickMines(s, boss, now);
        }
        tickNewAffixes(s, boss, now);
    }

    /** Joueurs vivants (hors créatif / spectateur) à moins de {@code r} blocs du boss. */
    private static List<ServerPlayer> playersNear(ServerLevel lvl, LivingEntity boss, double r) {
        return lvl.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(r),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(boss) <= r * r);
    }

    /** Les 8 nouveaux affixes (aucun ne touche au terrain : le feu ne va que sur les joueurs). */
    private static void tickNewAffixes(State s, LivingEntity boss, long now) {
        ServerLevel lvl = s.level;
        double bx = boss.getX(), by = boss.getY(), bz = boss.getZ();
        // ❄ Glacial : aura de givre (Lenteur à 4 blocs)
        if (s.has(Affix.GLACIAL) && now % 20 == 0) {
            lvl.sendParticles(ParticleTypes.SNOWFLAKE, bx, by + 1, bz, 20, 2.0, 0.6, 2.0, 0.01);
            for (ServerPlayer p : playersNear(lvl, boss, 4)) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, true));
            }
        }
        // 🔥 Infernal : aura de flammes, ne brûle jamais lui-même
        if (s.has(Affix.INFERNAL)) {
            if (boss.isOnFire()) boss.clearFire();
            if (now % 10 == 0) lvl.sendParticles(ParticleTypes.FLAME, bx, by + boss.getBbHeight() * 0.5, bz, 6,
                    boss.getBbWidth() * 0.6, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.6, 0.02);
        }
        // ⚡ Orageux : toutes les 20 s, un cercle annonce la foudre sur un joueur (1,5 s pour s'écarter)
        if (s.has(Affix.STORMCALLER)) {
            if (s.stormPos == null && now >= s.nextStorm) {
                List<ServerPlayer> near = playersNear(lvl, boss, 24);
                if (!near.isEmpty()) {
                    s.stormPos = near.get(RNG.nextInt(near.size())).position();
                    s.stormAt = now + 30;
                    lvl.playSound(null, BlockPos.containing(s.stormPos), SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 0.6f, 1.6f);
                }
                s.nextStorm = now + 400;
            }
            if (s.stormPos != null) {
                Vec3 c = s.stormPos;
                if (now % 4 == 0) for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16;
                    lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x + Math.cos(a) * 2, c.y + 0.1, c.z + Math.sin(a) * 2, 1, 0, 0, 0, 0);
                }
                if (now >= s.stormAt) {
                    var bolt = EntityType.LIGHTNING_BOLT.create(lvl);
                    if (bolt != null) {
                        bolt.moveTo(c.x, c.y, c.z);
                        bolt.setVisualOnly(true); // visuel seul : ni feu, ni dégâts au terrain
                        lvl.addFreshEntity(bolt);
                    }
                    for (ServerPlayer p : lvl.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(c, c).inflate(2.2),
                            p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
                        p.hurt(lvl.damageSources().lightningBolt(), 7f);
                    }
                    s.stormPos = null;
                }
            }
        }
        // 🌀 Téléporteur : toutes les 15 s, réapparaît dans le dos d'un joueur
        if (s.has(Affix.BLINKING) && now >= s.nextBlink) {
            s.nextBlink = now + 300;
            LivingEntity tgt = boss instanceof Mob m && m.getTarget() instanceof ServerPlayer tp ? tp : null;
            if (tgt == null) {
                List<ServerPlayer> near = playersNear(lvl, boss, 24);
                if (!near.isEmpty()) tgt = near.get(RNG.nextInt(near.size()));
            }
            if (tgt != null) {
                Vec3 back = tgt.position().subtract(tgt.getLookAngle().multiply(2, 0, 2));
                lvl.sendParticles(ParticleTypes.PORTAL, bx, by + 1, bz, 40, 0.4, 0.8, 0.4, 0.3);
                if (boss.randomTeleport(back.x, tgt.getY(), back.z, true)) {
                    lvl.playSound(null, boss.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.4f, 0.8f);
                    lvl.sendParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1, boss.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
                }
            }
        }
        // 💚 Régénérant : +1 % des PV max par seconde s'il n'a pas été touché depuis 5 s
        if (s.has(Affix.REGENERATING) && now % 20 == 0 && now - s.lastHurt > 100 && boss.getHealth() < boss.getMaxHealth()) {
            boss.heal(boss.getMaxHealth() * 0.01f);
            lvl.sendParticles(ParticleTypes.HAPPY_VILLAGER, bx, by + boss.getBbHeight() + 0.3, bz, 4, 0.4, 0.2, 0.4, 0);
        }
        // 🩸 Berserker : sous 30 % de PV, +50 % de dégâts (Force) et de vitesse, aura rouge
        if (s.has(Affix.BERSERKER) && boss.getHealth() < boss.getMaxHealth() * 0.3f) {
            if (now % 40 == 0) {
                boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 60, 1, false, false));
                boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 60, 1, false, false));
            }
            if (now % 6 == 0) lvl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.85f, 0.05f, 0.05f), 1.4f),
                    bx, by + boss.getBbHeight() * 0.5, bz, 6, boss.getBbWidth() * 0.6, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.6, 0);
        }
        // 🧲 Magnétique : toutes les 12 s, attire les joueurs à 12 blocs
        if (s.has(Affix.MAGNETIC) && now >= s.nextMagnet) {
            s.nextMagnet = now + 240;
            List<ServerPlayer> near = playersNear(lvl, boss, 12);
            if (!near.isEmpty()) {
                lvl.playSound(null, boss.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.5f, 0.6f);
                for (ServerPlayer p : near) {
                    Vec3 dir = boss.position().subtract(p.position()).multiply(1, 0, 1);
                    if (dir.lengthSqr() < 0.01) continue;
                    dir = dir.normalize().scale(1.1);
                    p.setDeltaMovement(dir.x, 0.35, dir.z);
                    p.hurtMarked = true;
                    lvl.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 15, 0.3, 0.5, 0.3, 0.5);
                }
            }
        }
    }

    private static void summon(State s, LivingEntity boss) {
        EntityType<?> type;
        try {
            type = BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(s.minionType)).orElse(EntityType.VEX);
        } catch (Exception ex) {
            type = EntityType.VEX;
        }
        BlockPos at = com.wavesurvivor.horde.spawn.SpawnZone.pick(s.level, boss.blockPosition(), 4, type);
        Entity ent = type.create(s.level);
        if (!(ent instanceof Mob mob)) return;
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, RNG.nextFloat() * 360f, 0);
        mob.finalizeSpawn(s.level, s.level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null, null);
        mob.getPersistentData().putBoolean("ws_revenant", true); // jamais relevé par la Résurrection
        if (boss instanceof Mob bm && bm.getTarget() != null) mob.setTarget(bm.getTarget());
        if (s.level.addFreshEntity(mob)) {
            s.summons.add(mob.getUUID());
            s.level.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
        }
    }

    private static final net.minecraft.core.particles.DustParticleOptions MINE_RED =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0.1f, 0.05f), 1.3f);
    private static final net.minecraft.core.particles.DustParticleOptions MINE_ORANGE =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0.55f, 0.05f), 1.0f);

    /**
     * Mines « Volatile » : cercle orange en pointillés pendant l'armement, puis cercle rouge qui pulse avec une lueur
     * centrale ; quand un joueur approche (5 blocs), le cercle clignote de plus en plus vite et un tic-tac accélère.
     */
    private static void tickMines(State s, LivingEntity boss, long now) {
        Iterator<Mine> it = s.mines.iterator();
        while (it.hasNext()) {
            Mine m = it.next();
            if (now >= m.expiresAt()) { it.remove(); continue; }
            boolean armed = now >= m.armedAt();
            double y = m.pos().y + 0.12;

            // Joueur le plus proche (pour le clignotement et le tic-tac)
            double nearest = Double.MAX_VALUE;
            for (ServerPlayer p : s.level.getEntitiesOfClass(ServerPlayer.class,
                    new net.minecraft.world.phys.AABB(m.pos(), m.pos()).inflate(5, 3, 5))) {
                if (!p.isCreative() && !p.isSpectator()) nearest = Math.min(nearest, p.position().distanceTo(m.pos()));
            }

            if (!armed) {
                // Armement : cercle orange en pointillés
                for (int i = 0; i < 8; i += 2) {
                    double a = Math.PI * 2 * i / 8;
                    s.level.sendParticles(MINE_ORANGE, m.pos().x + Math.cos(a) * 0.8, y, m.pos().z + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
                }
                continue;
            }

            // Armée : cercle rouge (clignote plus vite quand un joueur approche)
            int period = nearest < 5 ? Math.max(1, (int) Math.round(nearest)) : 4; // en pas de 5 ticks
            boolean on = ((now / 5) % period) == 0 || nearest >= 5;
            if (on) {
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12;
                    s.level.sendParticles(MINE_RED, m.pos().x + Math.cos(a) * 0.8, y, m.pos().z + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
                }
                s.level.sendParticles(MINE_RED, m.pos().x, y + 0.05, m.pos().z, 2, 0.05, 0.02, 0.05, 0);
                s.level.sendParticles(ParticleTypes.LAVA, m.pos().x, y, m.pos().z, 1, 0.05, 0, 0.05, 0);
            }
            // Tic-tac qui accélère et monte dans les aigus
            if (nearest < 5 && ((now / 5) % period) == 0) {
                float pitch = (float) (2.0 - nearest * 0.25);
                s.level.playSound(null, BlockPos.containing(m.pos()), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.HOSTILE, 0.9f, pitch);
            }

            for (ServerPlayer p : s.level.getEntitiesOfClass(ServerPlayer.class,
                    new net.minecraft.world.phys.AABB(m.pos(), m.pos()).inflate(1.6, 1.5, 1.6))) {
                if (p.isCreative() || p.isSpectator()) continue;
                s.level.explode(boss, m.pos().x, m.pos().y, m.pos().z, 2.2f, Level.ExplosionInteraction.NONE);
                it.remove();
                break;
            }
        }
    }

    private static LivingEntity find(MinecraftServer server, UUID id, State s) {
        if (s.level != null && s.level.getEntity(id) instanceof LivingEntity le) return le;
        for (ServerLevel lvl : server.getAllLevels()) {
            if (lvl.getEntity(id) instanceof LivingEntity le) { s.level = lvl; return le; }
        }
        return null;
    }

    private static void enterPhase(State s, LivingEntity boss, int phase, long now) {
        s.phase = phase;
        s.invulnerableUntil = now + 30; // 1,5 s
        if (s.cfg.reflectShield) {
            s.reflectUntil = Math.max(s.reflectUntil, now + 60);
            s.nextReflect = now + Math.max(5, s.cfg.reflectEverySeconds) * 20L;
        }
        // Boss plus rapide et plus fort
        AttributeInstance spd = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null && s.baseSpeed > 0) spd.setBaseValue(s.baseSpeed * (1 + s.cfg.phaseSpeedBonus * (phase - 1)));
        AttributeInstance dmg = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && s.baseDamage > 0) dmg.setBaseValue(s.baseDamage * (1 + s.cfg.phaseDamageBonus * (phase - 1)));

        // Onde de choc
        ServerLevel lvl = s.level;
        lvl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1, boss.getZ(), 1, 0, 0, 0, 0);
        lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), boss.getY() + 0.5, boss.getZ(), 80, 3, 0.3, 3, 0.15);
        lvl.playSound(null, boss.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0f, 0.8f);
        for (ServerPlayer p : lvl.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(7))) {
            if (p.isCreative() || p.isSpectator()) continue;
            Vec3 push = p.position().subtract(boss.position()).multiply(1, 0, 1);
            push = push.lengthSqr() < 0.01 ? new Vec3(RNG.nextDouble() - 0.5, 0, RNG.nextDouble() - 0.5) : push;
            push = push.normalize().scale(1.4);
            p.setDeltaMovement(p.getDeltaMovement().add(push.x, 0.55, push.z));
            p.hurtMarked = true;
        }
        announce(s, boss, "boss.phase", s.name, phase);
        WaveSurvivorMod.LOGGER.info("[BossDirector] '{}' passe en phase {}", s.name, phase);
    }

    /** Joueur hors d'atteinte trop longtemps → attiré vers le boss, ou le boss se téléporte près de lui. */
    private static void antiCheese(State s, LivingEntity boss) {
        int leash = Math.max(6, s.cfg.leashDistance);
        for (ServerPlayer p : s.level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(48))) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) { s.unreachable.remove(p.getUUID()); continue; }
            double dist = Math.sqrt(p.distanceToSqr(boss));
            boolean perched = p.getY() - boss.getY() > 4.5 && dist < 14;
            boolean far = dist > leash;
            boolean hidden = !boss.hasLineOfSight(p);
            boolean noPath = false;
            if (boss instanceof Mob mob && dist > 3 && dist < 32 && attr(boss, Attributes.MOVEMENT_SPEED) > 0.01) {
                Path path = mob.getNavigation().createPath(p, 1);
                noPath = path == null || !path.canReach();
            }
            if (!(far || perched || hidden || noPath)) { s.unreachable.remove(p.getUUID()); continue; }
            int sec = s.unreachable.merge(p.getUUID(), 1, Integer::sum);
            if (sec < Math.max(2, s.cfg.leashSeconds)) continue;
            s.unreachable.remove(p.getUUID());

            boolean immobile = attr(boss, Attributes.MOVEMENT_SPEED) <= 0.01;
            if (immobile || RNG.nextBoolean()) {
                // Attraction vers le boss
                Vec3 pull = boss.position().subtract(p.position());
                Vec3 dir = pull.normalize().scale(Math.min(2.2, 0.9 + dist * 0.06));
                p.setDeltaMovement(dir.x, Math.max(0.45, dir.y + 0.4), dir.z);
                p.hurtMarked = true;
                s.level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.2);
                s.level.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 0.6f);
                p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("boss.pulled", s.name), true);
            } else {
                // Téléportation du boss près du joueur (position sûre)
                BlockPos to = com.wavesurvivor.horde.spawn.SpawnZone.pick(s.level, p.blockPosition(), 3, boss.getType());
                s.level.sendParticles(ParticleTypes.PORTAL, boss.getX(), boss.getY() + 1, boss.getZ(), 60, 0.5, 1, 0.5, 0.5);
                boss.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
                s.level.sendParticles(ParticleTypes.PORTAL, boss.getX(), boss.getY() + 1, boss.getZ(), 60, 0.5, 1, 0.5, 0.5);
                s.level.playSound(null, boss.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.5f);
                if (boss instanceof Mob mob) mob.setTarget(p);
                p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("boss.teleported", s.name), true);
            }
        }
    }

    private static double attr(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute a) {
        AttributeInstance i = e.getAttribute(a);
        return i != null ? i.getValue() : 0;
    }

    private static void shieldParticles(ServerLevel lvl, LivingEntity boss) {
        double r = Math.max(1.2, boss.getBbWidth() * 0.9);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + lvl.getGameTime() * 0.15;
            lvl.sendParticles(ParticleTypes.END_ROD, boss.getX() + Math.cos(a) * r, boss.getY() + boss.getBbHeight() * 0.5,
                    boss.getZ() + Math.sin(a) * r, 1, 0, 0.2, 0, 0);
        }
    }

    private static void announce(State s, LivingEntity boss, String key, Object... args) {
        Component msg = com.wavesurvivor.i18n.WSLang.c(key, args);
        for (ServerPlayer p : s.level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(64))) {
            p.displayClientMessage(msg, true);
        }
    }

    // ─── Événements de dégâts ───

    /** Invulnérabilité pendant le changement de phase + bouclier qui renvoie les projectiles. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent event) {
        State s = ACTIVE.get(event.getEntity().getUUID());
        if (s == null || s.level == null) return;
        long now = s.level.getServer().getTickCount();
        if (now < s.invulnerableUntil) { event.setCanceled(true); return; }
        Entity direct = event.getSource().getDirectEntity();
        if (now < s.reflectUntil && direct instanceof Projectile proj) {
            event.setCanceled(true);
            // Le projectile repart vers le tireur
            proj.setDeltaMovement(proj.getDeltaMovement().scale(-0.9));
            proj.setOwner(event.getEntity());
            if (proj instanceof AbstractArrow arrow) arrow.setBaseDamage(arrow.getBaseDamage() * 0.75);
            s.level.playSound(null, event.getEntity().blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1f, 1.2f);
        }
    }

    /** ☠ Toxique : à sa mort, le boss laisse un nuage de poison persistant (10 s). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBossDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        State s = ACTIVE.get(event.getEntity().getUUID());
        if (s == null || !s.has(Affix.TOXIC) || !(event.getEntity().level() instanceof ServerLevel lvl)) return;
        LivingEntity b = event.getEntity();
        net.minecraft.world.entity.AreaEffectCloud cloud = new net.minecraft.world.entity.AreaEffectCloud(lvl, b.getX(), b.getY(), b.getZ());
        cloud.setRadius(4f);
        cloud.setDuration(200);
        cloud.setRadiusPerTick(-0.01f);
        cloud.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100, 1));
        lvl.addFreshEntity(cloud);
        lvl.playSound(null, b.blockPosition(), SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 1.5f, 0.5f);
    }

    /** Plafond de dégâts, affixe Blindé (dégâts reçus) et affixe Vampirique (dégâts infligés). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent event) {
        // 🩸 Vampirique : le boss se soigne de 50 % des dégâts qu'il inflige
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity atk) {
            State a = ACTIVE.get(atk.getUUID());
            if (a != null && a.has(Affix.VAMPIRIC) && atk.isAlive()) {
                atk.heal(event.getAmount() * 0.5f);
                if (a.level != null) a.level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, atk.getX(), atk.getY() + atk.getBbHeight(), atk.getZ(), 4, 0.3, 0.2, 0.3, 0.05);
            }
            // Coups du boss : ❄ Glacial (Lenteur), 🔥 Infernal (feu), ☠ Toxique (Poison)
            if (a != null && event.getEntity() instanceof ServerPlayer victim) {
                if (a.has(Affix.GLACIAL)) victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                if (a.has(Affix.INFERNAL)) victim.setSecondsOnFire(4);
                if (a.has(Affix.TOXIC)) victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 80, 0));
            }
        }
        State s = ACTIVE.get(event.getEntity().getUUID());
        if (s == null) return;
        // 🔥 Infernal : immunisé contre le feu
        if (s.has(Affix.INFERNAL) && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
            return;
        }
        if (s.level != null) s.lastHurt = s.level.getServer().getTickCount(); // 💚 Régénérant : pression des joueurs
        float amount = event.getAmount();
        // 🛡 Blindé : −60 % de dégâts pendant sa fenêtre
        if (s.has(Affix.ARMORED) && s.level != null && s.level.getServer().getTickCount() < s.armoredUntil) amount *= 0.4f;
        // Plafond de dégâts par coup
        if (s.cfg.damageCapPercent > 0) {
            float cap = (float) (event.getEntity().getMaxHealth() * s.cfg.damageCapPercent / 100.0);
            amount = Math.min(amount, cap);
        }
        event.setAmount(amount);
    }
}
