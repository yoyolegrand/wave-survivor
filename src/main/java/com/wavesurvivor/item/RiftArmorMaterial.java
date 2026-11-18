package com.wavesurvivor.item;

import com.wavesurvivor.registry.ModItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.util.Lazy;

/**
 * 2.1 — Matériau de l'Armure de la Faille : durabilité de la netherite dès le palier I (×37), enchantabilité 15,
 * son de la netherite, réparation avec la Poussière de Faille. Protection / robustesse / recul = palier I ; les paliers
 * suivants et les branches sont calculés depuis le NBT (RiftArmorItem.getAttributeModifiers).
 */
public enum RiftArmorMaterial implements ArmorMaterial {
    RIFT;

    private static final int[] BASE_DURABILITY = {11, 16, 15, 13};   // vanilla (casque, plastron, jambières, bottes)
    private static final int[] DEFENSE = {2, 6, 5, 2};
    private static final Lazy<Ingredient> REPAIR = Lazy.of(() -> Ingredient.of(ModItems.POUSSIERE_FAILLE.get()));

    @Override public int getDurabilityForType(ArmorItem.Type type) { return BASE_DURABILITY[type.ordinal()] * 37; }
    @Override public int getDefenseForType(ArmorItem.Type type) { return DEFENSE[type.ordinal()]; }
    @Override public int getEnchantmentValue() { return 15; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_NETHERITE; }
    @Override public Ingredient getRepairIngredient() { return REPAIR.get(); }
    @Override public String getName() { return "wavesurvivor:rift"; }
    @Override public float getToughness() { return 0f; }
    @Override public float getKnockbackResistance() { return 0f; }
}
