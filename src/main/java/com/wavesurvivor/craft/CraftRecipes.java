package com.wavesurvivor.craft;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Fabrications de l'onglet Artisanat du Sanctuaire : lecture des ingrédients dans l'inventaire du joueur (client et serveur). */
public final class CraftRecipes {

    private CraftRecipes() {}

    public static boolean matches(ItemStack st, String spec) {
        if (st.isEmpty()) return false;
        if (spec.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(spec.substring(1));
            return id != null && st.is(TagKey.create(Registries.ITEM, id));
        }
        ResourceLocation id = ResourceLocation.tryParse(spec);
        return id != null && id.equals(ForgeRegistries.ITEMS.getKey(st.getItem()));
    }

    public static int count(Player p, String spec) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (matches(st, spec)) n += st.getCount();
        return n;
    }

    public static boolean canAfford(Player p, CraftConfig.Craft c) {
        for (CraftConfig.Cost k : c.cost()) if (count(p, k.item()) < k.count()) return false;
        return !c.cost().isEmpty();
    }

    /** Objet servant d'icône et de nom pour un ingrédient (pour un tag : le premier objet du tag). */
    public static ItemStack icon(String spec) {
        if (spec.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(spec.substring(1));
            if (id != null) {
                var tag = ForgeRegistries.ITEMS.tags().getTag(TagKey.create(Registries.ITEM, id));
                return tag.stream().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
            }
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(spec);
        Item it = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return it == null || id == null || !ForgeRegistries.ITEMS.containsKey(id) ? ItemStack.EMPTY : new ItemStack(it);
    }

    public static ItemStack result(CraftConfig.Craft c) {
        ItemStack st = icon(c.result());
        if (!st.isEmpty()) st.setCount(c.count());
        return st;
    }

    /** Consomme les ingrédients et donne le résultat (au sol si l'inventaire est plein). Renvoie false si le joueur ne peut pas payer. */
    public static boolean craft(ServerPlayer sp, CraftConfig.Craft c) {
        ItemStack out = result(c);
        if (out.isEmpty() || !canAfford(sp, c)) return false;
        for (CraftConfig.Cost k : c.cost()) {
            int left = k.count();
            for (ItemStack st : sp.getInventory().items) {
                if (left <= 0) break;
                if (!matches(st, k.item())) continue;
                int take = Math.min(left, st.getCount());
                st.shrink(take);
                left -= take;
            }
        }
        if (!sp.getInventory().add(out)) sp.drop(out, false);
        return true;
    }
}
