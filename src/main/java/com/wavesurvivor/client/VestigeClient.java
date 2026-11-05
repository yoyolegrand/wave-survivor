package com.wavesurvivor.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Cible de la Boussole des Vestiges côté client (reçue du serveur) : l'aiguille la suit, ou tourne au hasard s'il n'y en a pas. */
@OnlyIn(Dist.CLIENT)
public final class VestigeClient {

    private VestigeClient() {}

    /** Cible actuelle, ou null. */
    public static GlobalPos target = null;

    public static void set(boolean has, ResourceLocation dim, BlockPos pos) {
        target = has ? GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), pos) : null;
    }
}
