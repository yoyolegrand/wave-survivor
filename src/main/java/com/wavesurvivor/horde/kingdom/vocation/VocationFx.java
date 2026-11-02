package com.wavesurvivor.horde.kingdom.vocation;

import com.wavesurvivor.horde.kingdom.KingdomClaim;
import com.wavesurvivor.horde.kingdom.KingdomManager;
import com.wavesurvivor.horde.kingdom.KingdomRoles;
import com.wavesurvivor.horde.kingdom.KingdomTreasury;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/** VOCATIONS — petits effets déclenchés par des talents (Catalyseur fermé, foyer purifié, fin d'Assaut, réparation du Calme…). */
public final class VocationFx {

    private VocationFx() {}

    private static final KingdomRoles.Role ARC = KingdomRoles.Role.ARCANIST;

    /** Un Catalyseur vient d'être fermé : talents de l'Arcaniste (Résonance et Arcanes offensives), centrés sur lui. */
    public static void onCatalystClosed(MinecraftServer server) {
        ServerPlayer a = Voc.holder(ARC);
        if (a == null || !(a.level() instanceof ServerLevel lvl)) return;
        if (Vocations.has(a, ARC, "arcanist.ult.rupture")) KingdomManager.closeNearestRift(a.blockPosition()); // ultime Rupture
        if (Vocations.has(a, ARC, "arcanist.resonance.shield_on_close")) {
            a.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1)); // 2 cœurs d'absorption, 10 s
        }
        boolean knock = Vocations.has(a, ARC, "arcanist.offense.close_knockback");
        boolean bolt = Vocations.has(a, ARC, "arcanist.offense.close_lightning");
        if (!knock && !bolt) return;
        for (LivingEntity m : lvl.getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(knock ? 9 : 14), e -> e instanceof Enemy && e.isAlive())) {
            if (knock && m.distanceToSqr(a) <= 81) {
                Vec3 push = m.position().subtract(a.position()).multiply(1, 0, 1).normalize().scale(1.4);
                m.push(push.x, 0.5, push.z);
                m.hurtMarked = true;
            }
            if (bolt && m.getPersistentData().getBoolean("ws_elite")) {
                var b = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(lvl);
                if (b != null) {
                    b.moveTo(m.position());
                    b.setVisualOnly(true);
                    lvl.addFreshEntity(b);
                }
                m.hurt(lvl.damageSources().lightningBolt(), 16f);
            }
        }
        if (knock) lvl.sendParticles(ParticleTypes.CLOUD, a.getX(), a.getY() + 0.5, a.getZ(), 40, 3, 0.4, 3, 0.1);
    }

    // ── Zones temporaires (fiole incendiaire laissant du feu, zone sacrée d'un foyer purifié) ──
    private record Zone(ServerLevel lvl, net.minecraft.core.BlockPos pos, double r, long until, boolean holy, boolean weak) {}

    private static final java.util.List<Zone> ZONES = new java.util.ArrayList<>();

    public static void addZone(ServerLevel lvl, net.minecraft.core.BlockPos pos, double r, int seconds, boolean holy) {
        ZONES.add(new Zone(lvl, pos.immutable(), r, lvl.getServer().getTickCount() + seconds * 20L, holy, false));
    }

    public static void clear() {
        ZONES.clear();
        TASKS.clear();
        warChest = false;
        motherLode = false;
        lastStandUsed = false;
        captain = null;
        VocationEvents.reset();
    }

    /** Toutes les 0,5 s : le feu au sol brûle les monstres, la zone sacrée les ralentit. */
    public static void tickZones(long tick) {
        if (ZONES.isEmpty()) return;
        ZONES.removeIf(z -> tick >= z.until());
        for (Zone z : ZONES) {
            var c = net.minecraft.world.phys.Vec3.atBottomCenterOf(z.pos());
            z.lvl().sendParticles(z.holy() ? ParticleTypes.END_ROD : ParticleTypes.FLAME, c.x, c.y + 0.3, c.z, 6, z.r() / 2, 0.2, z.r() / 2, 0.01);
            if (tick % 20 != 0) continue;
            for (LivingEntity m : z.lvl().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(z.pos()).inflate(z.r(), 2, z.r()), e -> e instanceof Enemy && e.isAlive())) {
                if (z.weak()) {
                    m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, true, false));
                    m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true, false));
                } else if (z.holy()) m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, true, false));
                else {
                    m.setSecondsOnFire(3);
                    m.hurt(z.lvl().damageSources().onFire(), 2f);
                }
            }
        }
    }

    /** Un foyer vient d'être purifié : ressources (talent Purification) et soin des alliés autour de l'Arcaniste. */
    public static void onFocusPurified(MinecraftServer server, net.minecraft.core.BlockPos at) {
        ServerPlayer a = Voc.holder(ARC);
        if (a == null) return;
        int loot = (int) Vocations.total(a, ARC, "arcanist.purification.purify_loot");
        if (loot > 0) {
            KingdomTreasury.add(KingdomTreasury.Res.WOOD, loot);
            KingdomTreasury.add(KingdomTreasury.Res.STONE, loot);
        }
        if (Vocations.has(a, ARC, "arcanist.purification.purify_zone") && a.level() instanceof ServerLevel zl && at != null) {
            addZone(zl, at, 8, 40, true); // zone sacrée : monstres ralentis 40 s
        }
        if (Vocations.has(a, ARC, "arcanist.purification.purify_heal") && a.level() instanceof ServerLevel lvl) {
            for (LivingEntity m : lvl.getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(14),
                    e -> e.isAlive() && (e instanceof ServerPlayer || e instanceof com.wavesurvivor.entity.KingdomSoldier))) {
                m.heal(8f);
                lvl.sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + 1.2, m.getZ(), 6, 0.4, 0.5, 0.4, 0.02);
            }
        }
    }

    /** Début du Calme (Assaut survécu) : prime de l'Intendant, fiole offerte à l'Alchimiste, caisse de ferraille du Récupérateur. */
    public static void onCalmStart() {
        ServerPlayer alch = Voc.holder(KingdomRoles.Role.ALCHEMIST);
        if (alch != null && Vocations.has(alch, KingdomRoles.Role.ALCHEMIST, "alchemist.distillation.extra_flask")) {
            com.wavesurvivor.horde.kingdom.KingdomAlchemy.giveFree(alch);
        }
        ServerPlayer scav = Voc.holder(KingdomRoles.Role.SCAVENGER);
        if (scav != null && Vocations.has(scav, KingdomRoles.Role.SCAVENGER, "scavenger.recycling.scrap_crate")) {
            com.wavesurvivor.horde.kingdom.KingdomRoleExtras.scrapCrate(scav);
        }
        if (scav != null && Vocations.has(scav, KingdomRoles.Role.SCAVENGER, "scavenger.plunder.raider_chest")) com.wavesurvivor.horde.kingdom.KingdomRoleExtras.raiderChest(scav);
        warChest = scav != null && Vocations.has(scav, KingdomRoles.Role.SCAVENGER, "scavenger.ult.war_chest"); // ultime : doublent les débris du prochain Assaut
        motherLode = Voc.has(KingdomRoles.Role.MINER, "miner.ult.mother_lode"); // ultime : le 1er gisement du Calme sera un filon-mère

        float[] snap = com.wavesurvivor.altar.AltarDefense.snapshot();
        int bonus = (int) Voc.total(KingdomRoles.Role.QUARTERMASTER, "quartermaster.treasury.wave_bonus");
        if (bonus > 0 && snap != null && snap[1] > 0 && snap[0] / snap[1] > 0.75f) {
            KingdomTreasury.reward(KingdomTreasury.Res.MONEY, bonus, "voc.reason.wave_bonus");
        }
    }

    // ── Tâches différées, orbe arcanique, ultimes ──
    private record Task(long at, Runnable run) {}

    private static final java.util.List<Task> TASKS = new java.util.ArrayList<>();
    private static boolean warChest = false, motherLode = false, lastStandUsed = false;
    private static java.util.UUID captain = null;
    private static long captainUntil = 0;

    /** Exécute une action dans {@code ticks} ticks (Réaction en chaîne…). */
    public static void later(MinecraftServer server, int ticks, Runnable r) {
        TASKS.add(new Task(server.getTickCount() + ticks, r));
    }

    /** Ultime Trésor de guerre : les débris de cet Assaut sont doublés. */
    public static boolean warChestActive() { return warChest && KingdomManager.phase() == KingdomManager.Phase.ASSAULT; }

    /** Ultime Filon-mère : consomme le « premier gisement du Calme ». */
    public static boolean takeMotherLode() {
        if (!motherLode) return false;
        motherLode = false;
        return true;
    }

    /** Début d'un Assaut : ultimes de l'Arcaniste (Nexus) et du Commandant (Garde d'élite), Panacée rechargée. */
    public static void onAssaultStart(MinecraftServer server) {
        VocationEvents.onAssaultStart();
        warChest = Voc.has(KingdomRoles.Role.SCAVENGER, "scavenger.ult.war_chest") && warChest; // actif seulement après un Assaut repoussé
        ServerPlayer arc = Voc.holder(ARC);
        if (arc != null && Vocations.has(arc, ARC, "arcanist.ult.nexus") && KingdomManager.center() != null && arc.level() instanceof ServerLevel lvl) {
            addNexus(lvl, KingdomManager.center(), 22, 30);
            arc.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("voc.nexus"));
        }
        ServerPlayer cmd = Voc.holder(KingdomRoles.Role.COMMANDER);
        if (cmd != null && Vocations.has(cmd, KingdomRoles.Role.COMMANDER, "commander.ult.elite_guard")) {
            net.minecraft.world.entity.Mob c = com.wavesurvivor.horde.kingdom.KingdomDefenses.randomSoldier();
            if (c != null) {
                captain = c.getUUID();
                captainUntil = server.getTickCount() + (long) Math.max(5, Vocations.total(cmd, KingdomRoles.Role.COMMANDER, "commander.ult.elite_guard")) * 20L;
                c.setInvulnerable(true);
                c.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, true, false));
                cmd.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("voc.captain"));
            }
        }
    }

    private static void addNexus(ServerLevel lvl, net.minecraft.core.BlockPos pos, double r, int seconds) {
        ZONES.add(new Zone(lvl, pos.immutable(), r, lvl.getServer().getTickCount() + seconds * 20L, false, true));
    }

    /** Orbe arcanique (talent), fin du capitaine invincible, Dernier carré : toutes les 0,5 s. */
    public static void tickAll(MinecraftServer server) {
        long tick = server.getTickCount();
        for (java.util.Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (tick >= t.at()) {
                it.remove();
                try { t.run().run(); } catch (Exception ignored) { }
            }
        }
        tickZones(tick);
        ServerPlayer a = Voc.holder(ARC);
        if (a != null && Vocations.has(a, ARC, "arcanist.offense.arcane_orb") && a.level() instanceof ServerLevel lvl && a.isAlive()) {
            double ang = tick * 0.35;
            for (int i = 0; i < 3; i++) {
                double an = ang + i * Math.PI * 2 / 3;
                lvl.sendParticles(ParticleTypes.END_ROD, a.getX() + Math.cos(an) * 2.2, a.getY() + 1.2, a.getZ() + Math.sin(an) * 2.2, 1, 0, 0, 0, 0);
            }
            for (LivingEntity m : lvl.getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(3), x -> x instanceof Enemy && x.isAlive())) {
                m.hurt(lvl.damageSources().indirectMagic(a, a), 3f);
            }
        }
        if (tick % 20 == 0 && KingdomManager.phase() == KingdomManager.Phase.ASSAULT && !com.wavesurvivor.horde.kingdom.KingdomOmens.activeList().isEmpty()
                && Voc.has(KingdomRoles.Role.AUGUR, "augur.ult.living_omen")) { // ultime Présage vivant : bonus personnel pendant l'Assaut
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!p.isSpectator()) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
            }
        }
        if (captain != null) {
            ServerLevel lvl = KingdomManager.level();
            var c = lvl == null ? null : lvl.getEntity(captain);
            if (c == null || !c.isAlive() || tick >= captainUntil) {
                if (c != null) c.setInvulnerable(false);
                captain = null;
            }
        }
        if (!lastStandUsed && KingdomManager.phase() == KingdomManager.Phase.ASSAULT && Voc.has(KingdomRoles.Role.COMMANDER, "commander.ult.last_stand")) {
            float[] snap = com.wavesurvivor.altar.AltarDefense.snapshot();
            if (snap != null && snap[1] > 0 && snap[0] / snap[1] < 0.25f) {
                lastStandUsed = true;
                com.wavesurvivor.horde.kingdom.KingdomDefenses.reviveSoldiers();
                ServerPlayer cmd = Voc.holder(KingdomRoles.Role.COMMANDER);
                if (cmd != null) cmd.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("voc.last_stand"));
            }
        }
    }

    /** Toutes les 5 s pendant le Calme : le Restaurateur répare légèrement toutes les constructions. */
    public static void tickCalm(long tick) {
        if (tick % 100 != 0 || KingdomManager.phase() != KingdomManager.Phase.CALM || KingdomManager.center() == null) return;
        if (!Voc.has(KingdomRoles.Role.BUILDER, "builder.restorer.auto_repair")) return;
        KingdomClaim.healArea(KingdomManager.center(), 1000, 0.02f);
    }
}
