package com.wavesurvivor.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;

/**
 * CRISTAL DU NÉANT (phase 2 de Xâl'Tor) : cristal de l'End relié au boss par un rayon.
 * Il faut le frapper 3 fois ; il se BRISE sans exploser (pas de dégâts au terrain ni d'explosion géante).
 */
public class XaltorCrystal extends EndCrystal {

    private int hitsLeft = 3;
    private long lastHit = -100;

    public XaltorCrystal(EntityType<? extends EndCrystal> type, Level level) {
        super(type, level);
        this.setShowBottom(false);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source) || source.getEntity() == null) return false; // seuls les coups « portés » comptent
        if (!(this.level() instanceof ServerLevel sl)) return true;
        long now = sl.getGameTime();
        if (now - lastHit < 8) return false; // anti-spam
        lastHit = now;
        hitsLeft--;
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 25, 0.4, 0.4, 0.4, 0.2);
        sl.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.2f, 0.7f);
        if (hitsLeft <= 0) {
            sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 2, 0.3, 0.3, 0.3, 0);
            sl.sendParticles(ParticleTypes.DRAGON_BREATH, getX(), getY() + 1, getZ(), 40, 0.6, 0.6, 0.6, 0.05);
            sl.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 0.5f);
            this.discard();
        }
        return true;
    }
}
