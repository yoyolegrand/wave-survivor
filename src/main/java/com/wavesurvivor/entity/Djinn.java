package com.wavesurvivor.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

/**
 * DJINN (horde des Sables Ardents) — génie sur la base de l'Évocateur : même comportement (crocs, invocations, sorts),
 * avec son propre modèle (turban, manteau, queue de fumée) et des paillettes autour de lui.
 * Ses invocations sont des ESPRITS DU DJINN (Vex turquoise et or) au lieu de Vex ordinaires.
 */
public class Djinn extends Evoker {

    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.25f), 0.9f);
    private static final DustParticleOptions TURQ = new DustParticleOptions(new Vector3f(0.3f, 0.95f, 0.9f), 0.9f);

    public Djinn(EntityType<? extends Evoker> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl) || tickCount % 4 != 0) return;
        double x = getX(), y = getY() + 0.9, z = getZ();
        sl.sendParticles(random.nextBoolean() ? GOLD : TURQ, x, y, z, 2, 0.5, 0.8, 0.5, 0.0);
        if (tickCount % 20 == 0) sl.sendParticles(ParticleTypes.END_ROD, x, y + 0.6, z, 1, 0.4, 0.4, 0.4, 0.01);
    }

    /** Les Vex invoqués par un Djinn deviennent des Esprits du Djinn (même comportement, autre apparence). */
    public static class Events {
        @SubscribeEvent
        public void onJoin(EntityJoinLevelEvent e) {
            if (!(e.getLevel() instanceof ServerLevel sl) || !(e.getEntity() instanceof Vex vex) || vex instanceof DjinnSpirit) return;
            if (!(vex.getOwner() instanceof Djinn djinn)) return;
            e.setCanceled(true);
            sl.getServer().execute(() -> {
                DjinnSpirit s = com.wavesurvivor.registry.ModEntities.DJINN_SPIRIT.get().create(sl);
                if (s == null) return;
                s.moveTo(vex.getX(), vex.getY(), vex.getZ(), vex.getYRot(), 0f);
                s.finalizeSpawn(sl, sl.getCurrentDifficultyAt(vex.blockPosition()), net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null, null);
                s.setOwner(djinn);
                if (vex.getBoundOrigin() != null) s.setBoundOrigin(vex.getBoundOrigin());
                s.setLimitedLife(20 * (30 + djinn.getRandom().nextInt(90)));
                if (djinn.getTarget() != null) s.setTarget(djinn.getTarget());
                sl.addFreshEntityWithPassengers(s);
                sl.sendParticles(TURQ, s.getX(), s.getY() + 0.4, s.getZ(), 12, 0.25, 0.25, 0.25, 0.0);
            });
        }
    }
}
