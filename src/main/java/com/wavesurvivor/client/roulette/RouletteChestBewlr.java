package com.wavesurvivor.client.roulette;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wavesurvivor.horde.roulette.RouletteKeyItem;
import com.wavesurvivor.horde.roulette.RouletteStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Rendu du coffre roulette en objet (inventaire, main, sol) : le même rendu que le bloc, avec le style porté par l'objet (étiquette « rouletteStyle »)
 * ou, pour un ancien objet, la couleur d'accent de son étiquette « rouletteColor » (sinon déduite de son nom).
 */
@OnlyIn(Dist.CLIENT)
public class RouletteChestBewlr extends BlockEntityWithoutLevelRenderer {

    private static RouletteChestBewlr instance;
    private final Map<String, RouletteStyle> cache = new HashMap<>();

    private RouletteChestBewlr() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static RouletteChestBewlr get() {
        if (instance == null) instance = new RouletteChestBewlr();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        RouletteChestRenderer.renderChest(styleOf(stack), ps, buf, light, overlay, 0f, Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime());
    }

    private RouletteStyle styleOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("rouletteStyle")) {
            String json = tag.getString("rouletteStyle");
            if (cache.size() > 64) cache.clear();
            return cache.computeIfAbsent(json, RouletteStyle::fromJson);
        }
        int accent = tag != null && tag.contains("rouletteColor") ? tag.getInt("rouletteColor")
                : RouletteKeyItem.colorForName(stack.hasCustomHoverName() ? stack.getHoverName().getString() : "");
        return cache.computeIfAbsent("#" + accent, k -> RouletteStyle.defaults(accent));
    }
}
