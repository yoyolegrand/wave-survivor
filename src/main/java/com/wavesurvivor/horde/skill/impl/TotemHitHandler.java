package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Fix vanilla / mods combat : ArmorStand.hurt() ignore beaucoup de damage sources,
 * surtout celles introduites par Better Combat, TravelOptics, etc.
 * On intercepte donc LivingAttackEvent pour les armor_stands tagués totem
 * et on applique le dégât manuellement.
 *
 * Register via MinecraftForge.EVENT_BUS.register(new TotemHitHandler())
 * dans le constructeur de WaveSurvivorMod.
 */
public class TotemHitHandler {

    /** Tag NBT ajouté à chaque armor_stand créé par SkillTotemProtection. */
    public static final String TOTEM_TAG = "wave_survivor_totem";

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target instanceof ArmorStand stand)) return;
        if (!stand.getTags().contains(TOTEM_TAG)) return;

        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        // Autoriser tout hit de joueur (mêlée ou projectile)
        if (!(attacker instanceof Player player)) return;

        float amount = event.getAmount();
        if (amount <= 0) return;

        // Bypass la logique vanilla qui rejette l'attaque
        event.setCanceled(true);

        float oldHealth = stand.getHealth();
        float newHealth = oldHealth - amount;

        if (newHealth <= 0) {
            stand.setHealth(0);
            // Play death sound
            stand.level().playSound(null, stand.blockPosition(),
                    SoundEvents.ARMOR_STAND_BREAK, SoundSource.NEUTRAL, 1.0f, 1.0f);
            stand.remove(Entity.RemovalReason.KILLED);
            WaveSurvivorMod.LOGGER.info("[TotemHit] '{}' a détruit un totem ({} dmg)",
                    player.getName().getString(), amount);
        } else {
            stand.setHealth(newHealth);
            // Animation de hit
            stand.hurtTime = 10;
            stand.hurtDuration = 10;
            // Hit sound
            stand.level().playSound(null, stand.blockPosition(),
                    SoundEvents.ARMOR_STAND_HIT, SoundSource.NEUTRAL, 0.8f, 1.0f);
            WaveSurvivorMod.LOGGER.debug("[TotemHit] '{}' → totem {}HP → {}HP",
                    player.getName().getString(), oldHealth, newHealth);
        }
    }
}
