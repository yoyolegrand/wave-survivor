package com.wavesurvivor.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * 2.0 — LE COLOSSE DE LA FAILLE, Titan : géant d'obsidienne en facettes (modèle et rendu faits par Claude chat : ColosseModel,
 * ColosseRenderer). Tout le comportement commun des Titans animés est dans {@link RiftTitanEntity}.
 */
public class Colosse extends RiftTitanEntity {

    public Colosse(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }
}
