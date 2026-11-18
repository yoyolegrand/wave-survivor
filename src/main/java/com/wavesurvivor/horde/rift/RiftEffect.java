package com.wavesurvivor.horde.rift;

import com.wavesurvivor.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * 2.1 — EFFET DE FAILLE (debuff cumulable posé par les ennemis de la Faille).
 *  - Chaque coup d'un ennemi de la Faille sur un joueur a {@value #CHANCE_PERCENT} % de chance de le poser ou de l'empiler :
 *    +1 niveau (jusqu'à {@value #MAX_LEVEL}) et +5 s de durée (jusqu'à {@value #MAX_SECONDS} s).
 *  - Soins reçus réduits de 10 % par niveau.
 *  - « Ennemi de la Faille » : les créatures de la Faille (Géodon, Ancreur, Bolide…) partout, et tout monstre hostile
 *    dans la dimension de la Faille pendant une partie de Faille. Les tirs comptent (c'est le tireur qui est regardé).
 * Enregistré dans WaveSurvivorMod.
 */
public class RiftEffect {

    public static final int CHANCE_PERCENT = 5;
    public static final int MAX_LEVEL = 5;
    public static final int STACK_SECONDS = 5, MAX_SECONDS = 30;
    public static final double HEAL_CUT_PER_LEVEL = 0.10;
    private static final Random RNG = new Random();

    /** L'attaquant est-il un ennemi de la Faille ? */
    public static boolean isRiftEnemy(Entity e) {
        if (e instanceof com.wavesurvivor.entity.FailleMonster) return true;
        return e instanceof Enemy && e instanceof LivingEntity && RiftManager.isActive() && e.level().dimension() == RiftManager.RIFT_DIM;
    }

    /** Pose l'effet ou ajoute un niveau et 5 s. */
    public static void stack(LivingEntity target) {
        // 2.1 — Armure de la Faille : Grâce (4 pièces bénies) = immunité ; Ancre = niveau maximal réduit selon le palier
        int max = MAX_LEVEL;
        if (target instanceof net.minecraft.world.entity.player.Player pl) {
            if (com.wavesurvivor.item.RiftArmor.immune(pl)) return;
            max = com.wavesurvivor.item.RiftArmor.effectCap(pl, MAX_LEVEL);
        }
        MobEffectInstance cur = target.getEffect(ModEffects.RIFT_EFFECT.get());
        int amp = cur == null ? 0 : Math.min(max - 1, cur.getAmplifier() + 1);
        int dur = Math.min(MAX_SECONDS * 20, (cur == null ? 0 : cur.getDuration()) + STACK_SECONDS * 20);
        if (cur != null) target.removeEffect(ModEffects.RIFT_EFFECT.get());   // sinon un niveau plus haut ne remplace pas la durée proprement
        target.addEffect(new MobEffectInstance(ModEffects.RIFT_EFFECT.get(), dur, amp, false, true, true));
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return;
        if (e.getAmount() <= 0 || !isRiftEnemy(e.getSource().getEntity())) return;
        if (RNG.nextInt(100) < CHANCE_PERCENT) stack(p);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHeal(LivingHealEvent e) {
        MobEffectInstance cur = e.getEntity().getEffect(ModEffects.RIFT_EFFECT.get());
        if (cur == null) return;
        double keep = Math.max(0, 1 - HEAL_CUT_PER_LEVEL * (cur.getAmplifier() + 1));
        e.setAmount((float) (e.getAmount() * keep));
    }
}
