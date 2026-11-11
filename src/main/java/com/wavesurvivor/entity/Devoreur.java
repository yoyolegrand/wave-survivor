package com.wavesurvivor.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * 2.0 — LE DÉVOREUR, Titan de la première Faille : serpent colossal (modèle et rendu faits par Claude chat : DevoreurModel,
 * DevoreurRenderer). Tout le comportement commun des Titans animés est dans {@link RiftTitanEntity}.
 */
public class Devoreur extends RiftTitanEntity {

    public Devoreur(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }
}
