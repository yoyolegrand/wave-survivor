package com.wavesurvivor.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * 2.0 — L'ARCHONTE DU NÉANT, Titan : spectre en robe qui flotte, auréole et rochers en orbite (modèle et rendu faits par
 * Claude chat : ArchonteModel, ArchonteRenderer). Tout le comportement commun des Titans animés est dans {@link RiftTitanEntity}.
 */
public class Archonte extends RiftTitanEntity {

    public Archonte(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }
}
