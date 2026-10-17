package com.wavesurvivor.horde.spawn;

import com.wavesurvivor.horde.boss.BossManager;
import com.wavesurvivor.horde.model.HordeEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * GARDE DES UNITÉS DE HORDE (1.7) :
 *  • Aucune transformation : un zombie de horde qui tombe à l'eau ne devient pas un noyé, un husk reste un husk, un squelette
 *    ne devient pas un Vagabond, piglins et hoglins ne se zombifient pas… (le jeu remplace l'entité : l'ancienne sortait du
 *    registre sans mourir, la nouvelle n'était plus une unité, et la vague ne se terminait jamais). La conversion est annulée.
 *  • Filet de sécurité : si une conversion passe quand même, l'inscription et les statistiques passent à la nouvelle entité.
 *  • Les sorcières de horde (et boss sorcières) ne se soignent pas : potion de soin et régénération sont annulées.
 * Enregistré dans WaveSurvivorMod ; couvre aussi le Kingdom, les invasions de village et les invocations rattachées.
 */
public class HordeUnitGuard {

    /** Marques persistantes des entités qui appartiennent à une partie (unités, invocations, boss, Kingdom, invasions). */
    private static final String[] TAGS = {"ws_horde_unit", "ws_invasion", "ws_kingdom_guardian", "ws_kingdom_escort",
            "ws_revenant", "ws_illusion", "ws_elite", "ws_mutated", "ws_obj_champion"};

    /** Cette entité fait-elle partie d'une partie en cours (unité inscrite, boss, invocation, unité du Kingdom ou d'une invasion) ? */
    public static boolean isHordeEntity(Entity e) {
        if (e == null || e.level().isClientSide) return false;
        if (MobRegistry.get(e.getUUID()) != null || BossManager.isBoss(e.getUUID())) return true;
        var tag = e.getPersistentData();
        for (String t : TAGS) if (tag.getBoolean(t)) return true;
        return false;
    }

    // ─── Transformations ───

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onConvertPre(LivingConversionEvent.Pre event) {
        if (isHordeEntity(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onConvertPost(LivingConversionEvent.Post event) {
        LivingEntity old = event.getEntity();
        LivingEntity now = event.getOutcome();
        if (old == null || now == null || now.level().isClientSide) return;
        HordeEntity template = MobRegistry.get(old.getUUID());
        if (template == null) return; // boss / invocation sans modèle : rien à transférer
        // La nouvelle entité reprend les statistiques de l'unité, puis son inscription (difficulté, mutateurs, compétences)
        set(now, Attributes.MAX_HEALTH, template.maxHealth);
        now.setHealth(now.getMaxHealth());
        set(now, Attributes.ATTACK_DAMAGE, template.attackDamage);
        set(now, Attributes.MOVEMENT_SPEED, template.movementSpeed);
        set(now, Attributes.KNOCKBACK_RESISTANCE, template.knockbackResistance);
        MobRegistry.remove(old.getUUID());
        MobRegistry.register(now, template);
    }

    private static void set(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    // ─── Sorcières : aucun soin ───

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof Witch w && isHordeEntity(w)) event.setCanceled(true);
    }
}
