package com.wavesurvivor.horde.kingdom;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * TRI DES GLANEURS : 6 catégories d'objets. Chaque Glaneur a un filtre (cases cochées) ; 0 case = il garde tout.
 * Règle : un objet va au Glaneur filtré qui accepte sa catégorie ; les Glaneurs sans filtre ne prennent que ce
 * qu'aucun Glaneur filtré à portée n'accepte.
 */
public final class GleanerSort {

    public enum Category { RESOURCES, EQUIPMENT, KEYS, FOOD, BLOCKS, OTHER }

    public static final int ALL = (1 << Category.values().length) - 1;

    private static final TagKey<Item> INGOTS = tag("ingots"), RAW = tag("raw_materials"), GEMS = tag("gems"),
            ORES = tag("ores"), NUGGETS = tag("nuggets"), DUSTS = tag("dusts");

    private GleanerSort() {}

    private static TagKey<Item> tag(String path) {
        return ItemTags.create(new ResourceLocation("forge", path));
    }

    public static Category of(ItemStack s) {
        Item it = s.getItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
        String path = id == null ? "" : id.getPath();
        // Clés (coffres mystère, reliquaires de boss…)
        if (id != null && ("roulettechest".equals(id.getNamespace()) || path.contains("key"))) return Category.KEYS;
        // Équipement : armes, armures, outils, boucliers, arcs
        if (it instanceof ArmorItem || it instanceof TieredItem || it instanceof BowItem || it instanceof CrossbowItem
                || it instanceof ShieldItem || it instanceof TridentItem || s.isDamageableItem()) return Category.EQUIPMENT;
        if (s.isEdible()) return Category.FOOD;
        // Ressources : lingots, minerais bruts, gemmes, pépites, poudres, charbon, bois, os, ficelle…
        if (s.is(INGOTS) || s.is(RAW) || s.is(GEMS) || s.is(ORES) || s.is(NUGGETS) || s.is(DUSTS) || s.is(ItemTags.LOGS)
                || s.is(ItemTags.COALS) || s.is(Items.REDSTONE) || s.is(Items.LAPIS_LAZULI) || s.is(Items.BONE)
                || s.is(Items.STRING) || s.is(Items.GUNPOWDER) || s.is(Items.LEATHER) || s.is(Items.FEATHER)
                || s.is(Items.ENDER_PEARL) || s.is(Items.BLAZE_ROD) || s.is(Items.SLIME_BALL)) return Category.RESOURCES;
        if (it instanceof BlockItem) return Category.BLOCKS;
        return Category.OTHER;
    }

    public static boolean accepts(int filter, ItemStack s) {
        return filter == 0 || (filter & (1 << of(s).ordinal())) != 0;
    }
}
