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

    /** Un foyer vient d'être purifié : ressources (talent Purification) et soin des alliés autour de l'Arcaniste. */
    public static void onFocusPurified(MinecraftServer server) {
        ServerPlayer a = Voc.holder(ARC);
        if (a == null) return;
        int loot = (int) Vocations.total(a, ARC, "arcanist.purification.purify_loot");
        if (loot > 0) {
            KingdomTreasury.add(KingdomTreasury.Res.WOOD, loot);
            KingdomTreasury.add(KingdomTreasury.Res.STONE, loot);
        }
        if (Vocations.has(a, ARC, "arcanist.purification.purify_heal") && a.level() instanceof ServerLevel lvl) {
            for (LivingEntity m : lvl.getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(14),
                    e -> e.isAlive() && (e instanceof ServerPlayer || e instanceof com.wavesurvivor.entity.KingdomSoldier))) {
                m.heal(8f);
                lvl.sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + 1.2, m.getZ(), 6, 0.4, 0.5, 0.4, 0.02);
            }
        }
    }

    /** Début du Calme (Assaut survécu) : prime de l'Intendant si le Monolithe est en bonne santé. */
    public static void onCalmStart() {
        float[] snap = com.wavesurvivor.altar.AltarDefense.snapshot();
        int bonus = (int) Voc.total(KingdomRoles.Role.QUARTERMASTER, "quartermaster.treasury.wave_bonus");
        if (bonus > 0 && snap != null && snap[1] > 0 && snap[0] / snap[1] > 0.75f) {
            KingdomTreasury.reward(KingdomTreasury.Res.MONEY, bonus, "voc.reason.wave_bonus");
        }
    }

    /** Toutes les 5 s pendant le Calme : le Restaurateur répare légèrement toutes les constructions. */
    public static void tickCalm(long tick) {
        if (tick % 100 != 0 || KingdomManager.phase() != KingdomManager.Phase.CALM || KingdomManager.center() == null) return;
        if (!Voc.has(KingdomRoles.Role.BUILDER, "builder.restorer.auto_repair")) return;
        KingdomClaim.healArea(KingdomManager.center(), 1000, 0.02f);
    }
}
