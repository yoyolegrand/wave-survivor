package com.wavesurvivor.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Œufs d'apparition → identifiant d'entité (éditeurs de hordes / d'entités).
 * Fonctionne avec tous les œufs au format standard : vanilla (SpawnEggItem) et moddés (ForgeSpawnEggItem, qui en hérite),
 * y compris un œuf dont les données NBT désignent une autre entité (EntityTag).
 */
public final class SpawnEggs {

    private SpawnEggs() {}

    /** Identifiant de l'entité invoquée par cet œuf, ou null si l'objet n'est pas un œuf reconnu. */
    public static String entityId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof SpawnEggItem egg)) return null;
        try {
            EntityType<?> type = egg.getType(stack.getTag());
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return key != null ? key.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
