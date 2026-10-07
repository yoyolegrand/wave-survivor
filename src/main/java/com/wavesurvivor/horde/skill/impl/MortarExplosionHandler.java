package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Empêche les fireballs du mortier de casser des blocs.
 * Les fireballs sont tagués "wave_survivor_mortar" au spawn dans SkillMortar.
 * On intercepte l'ExplosionEvent.Detonate et on clear la liste des blocs affectés —
 * les dégâts aux entités restent (ils sont dans getAffectedEntities, pas touché).
 */
public class MortarExplosionHandler {

    public static final String MORTAR_TAG = "wave_survivor_mortar";

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        // getDirectSourceEntity() = l'entité source directe de l'explosion (le fireball lui-même)
        Object direct = event.getExplosion().getDirectSourceEntity();
        boolean isMortar = false;
        if (direct instanceof LargeFireball fb && fb.getTags().contains(MORTAR_TAG)) isMortar = true;
        if (direct instanceof SmallFireball sfb && sfb.getTags().contains(MORTAR_TAG)) isMortar = true;

        if (isMortar) {
            int blockCount = event.getAffectedBlocks().size();
            event.getAffectedBlocks().clear();
            WaveSurvivorMod.LOGGER.debug("[Mortar] Explosion cleared {} block(s) (no griefing)", blockCount);
        }
    }
}
