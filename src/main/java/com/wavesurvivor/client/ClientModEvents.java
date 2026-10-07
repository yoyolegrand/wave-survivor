package com.wavesurvivor.client;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.altar.AltarBlock;
import com.wavesurvivor.altar.AltarColors;
import com.wavesurvivor.registry.ModBlockEntities;
import com.wavesurvivor.registry.ModBlocks;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Enregistrements CLIENT sur le mod event bus (renderers de BlockEntity, teintes de blocs/items...).
 */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    /** Écrans des menus personnalisés (Atelier de Réparation). */
    @SubscribeEvent
    public static void onClientSetupMenus(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.gui.screens.MenuScreens.register(com.wavesurvivor.registry.ModMenus.WORKSHOP.get(), WorkshopScreen::new);
            // Boussole du Royaume : aiguille native des boussoles liées, vers la cible choisie sur la carte (sinon elle tourne)
            net.minecraft.client.renderer.item.ItemProperties.register(com.wavesurvivor.registry.ModItems.KINGDOM_COMPASS.get(),
                    new net.minecraft.resources.ResourceLocation("angle"),
                    new net.minecraft.client.renderer.item.CompassItemPropertyFunction(
                            (level, stack, entity) -> com.wavesurvivor.horde.kingdom.KingdomCompassItem.target(stack)));
        });
    }

    /** Fil d'événements (notifications à gauche de l'écran). */
    @SubscribeEvent
    public static void onFeedOverlay(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("ws_event_feed", EventFeedOverlay.INSTANCE);
    }

    /** Boussole du Royaume : cadran vanilla teinté en or. */
    @SubscribeEvent
    public static void onItemColorsCompass(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? 0xFFD54A : -1, com.wavesurvivor.registry.ModItems.KINGDOM_COMPASS.get());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ALTAR_RUNIC.get(), AltarBookRenderer::new);
        // Mode Kingdom : structures 3D des défenses (tours, sanctuaire, baraquement)
        event.registerBlockEntityRenderer(ModBlockEntities.DEFENSE.get(), DefenseRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.DIABLOTIN.get(), DiablotinRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.BRECHE.get(), BrecheRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.ECLAT_NEANT.get(),
                ctx -> new DiablotinRenderer(ctx, 0.72f, 0.36f, 1.0f)); // violet du Néant
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.XALTOR.get(), XaltorRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.TOTEM.get(), TotemRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.GISEMENT.get(), GisementRenderer::new);
        // Soldat du royaume : humanoïde propre au mod (corps humain + armure + arme)
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.KINGDOM_SOLDIER.get(), KingdomSoldierRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.XALTOR_CRYSTAL.get(),
                net.minecraft.client.renderer.entity.EndCrystalRenderer::new);
    }

    /** Autel : le calque tintindex 0 (runes + tapis) prend la couleur du colorant de l'état du bloc. */
    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) return -1;
            return AltarColors.rgb(state.hasProperty(AltarBlock.COLOR) ? state.getValue(AltarBlock.COLOR) : DyeColor.RED);
        }, ModBlocks.ALTAR_RUNIC.get());

        // Coffre Roulette : runes + gemme (tintindex 0) = couleur du type de coffre (nom synchronisé)
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) return -1;
            if (level != null && pos != null
                    && level.getBlockEntity(pos) instanceof com.wavesurvivor.horde.roulette.RouletteChestBlockEntity be) {
                if (be.getColor() >= 0) return be.getColor();
                if (be.getCustomName() != null)
                    return com.wavesurvivor.horde.roulette.RouletteKeyItem.colorForName(be.getCustomName().getString());
            }
            return com.wavesurvivor.horde.roulette.RouletteKeyItem.colorForName("");
        }, ModBlocks.ROULETTE_CHEST.get());
    }

    /** Item de l'autel : toujours rouge (couleur par défaut à la pose). */
    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? AltarColors.rgb(DyeColor.RED) : -1,
                ModItems.ALTAR_RUNIC_ITEM.get());
        // Clé de roulette : corps (calque 0) = matériau · gemme (calque 1) = couleur du type de clé
        event.register((stack, tintIndex) -> tintIndex == 0
                        ? com.wavesurvivor.horde.roulette.RouletteKeyItem.bodyColor(stack)
                        : tintIndex == 1 ? com.wavesurvivor.horde.roulette.RouletteKeyItem.gemColor(stack) : -1,
                ModItems.ROULETTE_KEY.get());
        // Coffre Roulette en item : couleur d'après son nom
        event.register((stack, tintIndex) -> {
                    if (tintIndex != 0) return -1;
                    var tag = stack.getTag();
                    if (tag != null && tag.contains("rouletteColor")) return tag.getInt("rouletteColor");
                    return com.wavesurvivor.horde.roulette.RouletteKeyItem.colorForName(
                            stack.hasCustomHoverName() ? stack.getHoverName().getString() : "");
                },
                ModItems.ROULETTE_CHEST_ITEM.get());
    }
}
