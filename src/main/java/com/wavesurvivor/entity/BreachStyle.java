package com.wavesurvivor.entity;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Styles de Brèche : couleur de la faille, anneau de base (2 blocs alternés), éclats en orbite.
 * Réglable par horde (altar_recipes.json → breaches.style / breaches.color).
 */
public enum BreachStyle {
    INFERNALE("infernale", 0xFF5A1E, Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK, Blocks.CRYING_OBSIDIAN),
    AMES("ames", 0x3FD6FF, Blocks.SOUL_SOIL, Blocks.SOUL_SAND, Blocks.BONE_BLOCK),
    END("end", 0xB04DFF, Blocks.END_STONE, Blocks.PURPUR_BLOCK, Blocks.PURPUR_BLOCK),
    ABYSSES("abysses", 0x2FE0C8, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN);

    public final String id;
    public final int color;
    public final Block baseA, baseB, shard;

    BreachStyle(String id, int color, Block baseA, Block baseB, Block shard) {
        this.id = id;
        this.color = color;
        this.baseA = baseA;
        this.baseB = baseB;
        this.shard = shard;
    }

    public static BreachStyle byId(String id) {
        if (id != null) for (BreachStyle s : values()) if (s.id.equalsIgnoreCase(id)) return s;
        return INFERNALE;
    }
}
