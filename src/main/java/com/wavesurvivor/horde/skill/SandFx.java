package com.wavesurvivor.horde.skill;

import com.wavesurvivor.item.RelicEffects;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

/** Outils communs des compétences du désert : particules de sable et immunité par relique (Voile du Désert). */
public final class SandFx {

    /** Éclats de sable (respectent la vitesse : rafales, tourbillons). */
    public static final BlockParticleOption SAND = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    /** Poussière dorée (tracé d'avertissement des fissures). */
    public static final DustParticleOptions WARN = new DustParticleOptions(new Vector3f(0.9f, 0.55f, 0.15f), 1.6f);

    private SandFx() {}

    /** Objet d'après son id (« wavesurvivor:voile_desert »), ou null si vide / inconnu. */
    public static Item item(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(id.trim()));
            return it == Items.AIR ? null : it;
        } catch (Exception e) {
            return null;
        }
    }

    /** Ce joueur porte-t-il la relique d'immunité demandée ? */
    public static boolean immune(ServerPlayer p, String itemId) {
        Item it = item(itemId);
        return it != null && RelicEffects.hasRelic(p, it);
    }
}
