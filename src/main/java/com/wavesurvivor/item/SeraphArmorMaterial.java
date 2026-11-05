package com.wavesurvivor.item;

import com.wavesurvivor.registry.ModItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.util.Lazy;

/**
 * Matériau de l'Armure du Séraphin (valeurs de départ, à équilibrer) : durabilité ×42 (netherite : ×37), protection 3 / 8 / 6 / 3, robustesse 3,
 * résistance au recul 0,1 (comme la netherite), enchantabilité 18, réparation avec un Fragment de grâce.
 */
public enum SeraphArmorMaterial implements ArmorMaterial {
    SERAPH;

    /** Durabilité de base par pièce (casque, plastron, jambières, bottes), multipliée par 42. */
    private static final int[] BASE_DURABILITY = {11, 16, 15, 13};
    private static final int[] DEFENSE = {3, 8, 6, 3};
    private static final Lazy<Ingredient> REPAIR = Lazy.of(() -> Ingredient.of(ModItems.GRACE_FRAGMENT.get()));

    @Override public int getDurabilityForType(ArmorItem.Type type) { return BASE_DURABILITY[type.ordinal()] * 42; }
    @Override public int getDefenseForType(ArmorItem.Type type) { return DEFENSE[type.ordinal()]; }
    @Override public int getEnchantmentValue() { return 18; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_GOLD; }
    @Override public Ingredient getRepairIngredient() { return REPAIR.get(); }
    @Override public String getName() { return "wavesurvivor:seraph"; }
    @Override public float getToughness() { return 3.0f; }
    @Override public float getKnockbackResistance() { return 0.1f; }
}
