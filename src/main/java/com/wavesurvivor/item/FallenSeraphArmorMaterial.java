package com.wavesurvivor.item;

import com.wavesurvivor.registry.ModItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.util.Lazy;

/**
 * 2.0 — Matériau de l'Armure du Séraphin déchu : mêmes valeurs que le Séraphin (durabilité ×42, protection 3 / 8 / 6 / 3, robustesse 3,
 * recul 0,1, enchantabilité 18), son d'équipement de netherite, réparation avec un Fragment de souillure.
 */
public enum FallenSeraphArmorMaterial implements ArmorMaterial {
    FALLEN_SERAPH;

    private static final Lazy<Ingredient> REPAIR = Lazy.of(() -> Ingredient.of(ModItems.TAINT_FRAGMENT.get()));

    @Override public int getDurabilityForType(ArmorItem.Type type) { return SeraphArmorMaterial.SERAPH.getDurabilityForType(type); }
    @Override public int getDefenseForType(ArmorItem.Type type) { return SeraphArmorMaterial.SERAPH.getDefenseForType(type); }
    @Override public int getEnchantmentValue() { return SeraphArmorMaterial.SERAPH.getEnchantmentValue(); }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_NETHERITE; }
    @Override public Ingredient getRepairIngredient() { return REPAIR.get(); }
    @Override public String getName() { return "wavesurvivor:fallen_seraph"; }
    @Override public float getToughness() { return SeraphArmorMaterial.SERAPH.getToughness(); }
    @Override public float getKnockbackResistance() { return SeraphArmorMaterial.SERAPH.getKnockbackResistance(); }
}
