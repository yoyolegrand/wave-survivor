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
            net.minecraft.client.gui.screens.MenuScreens.register(com.wavesurvivor.registry.ModMenus.SERAPH_SHRINE.get(), SeraphShrineScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(com.wavesurvivor.registry.ModMenus.OFFERING_BRAZIER.get(), OfferingBrazierScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(com.wavesurvivor.registry.ModMenus.RAMPART.get(), RampartScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(com.wavesurvivor.registry.ModMenus.GLEANER.get(), GleanerScreen::new);
            // Clé de roulette : forme (tête × panneton) choisie par propriété d'objet → 32 modèles
            net.minecraft.client.renderer.item.ItemProperties.register(com.wavesurvivor.registry.ModItems.ROULETTE_KEY.get(),
                    new net.minecraft.resources.ResourceLocation("wavesurvivor", "key_shape"),
                    (stack, level, entity, seed) -> com.wavesurvivor.horde.roulette.RouletteKeyItem.shapeIndex(stack));
            // Boussole des Vestiges : même aiguille que la boussole vanilla (image 00 = cible droit devant), cible reçue du serveur
            net.minecraft.client.renderer.item.ItemProperties.register(com.wavesurvivor.registry.ModItems.VESTIGE_COMPASS.get(),
                    new net.minecraft.resources.ResourceLocation("angle"),
                    new net.minecraft.client.renderer.item.CompassItemPropertyFunction((level, stack, entity) -> VestigeClient.target));
            // Boussole du Royaume : aiguille native des boussoles liées, vers la cible choisie sur la carte (sinon elle tourne)
            net.minecraft.client.renderer.item.ItemProperties.register(com.wavesurvivor.registry.ModItems.KINGDOM_COMPASS.get(),
                    new net.minecraft.resources.ResourceLocation("angle"),
                    new net.minecraft.client.renderer.item.CompassItemPropertyFunction(
                            (level, stack, entity) -> com.wavesurvivor.horde.kingdom.KingdomCompassItem.target(stack)));
        });
    }

    /** Flamme dorée : même rendu que la petite flamme vanilla, avec notre texture. */
    @SubscribeEvent
    public static void onParticleProviders(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.wavesurvivor.registry.ModParticles.GOLD_FLAME.get(), net.minecraft.client.particle.FlameParticle.SmallFlameProvider::new);
    }

    /** Coffre roulette : le modèle et les médaillons composés sont reconstruits après un rechargement des ressources (F3 + T, pack de textures). */
    @SubscribeEvent
    public static void onReloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> {
            com.wavesurvivor.client.roulette.RouletteChestModel.reset();
            com.wavesurvivor.client.roulette.MedallionTextures.clear();
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
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(com.wavesurvivor.client.armor.SeraphArmorModel.LAYER, com.wavesurvivor.client.armor.SeraphArmorModel::createBodyLayer); // Armure du Séraphin
        event.registerLayerDefinition(DjinnModel.LAYER, DjinnModel::createBodyLayer); // Djinn : turban et queue de fumée
        event.registerLayerDefinition(ScarabModel.LAYER, ScarabModel::createBodyLayer); // Scarabée (modèle sur mesure)
        event.registerLayerDefinition(ScorpionModel.LAYER, ScorpionModel::createBodyLayer); // Scorpion (modèle sur mesure)
        event.registerLayerDefinition(JackalModel.LAYER, JackalModel::createBodyLayer); // Chacal maudit (modèle sur mesure)
        event.registerLayerDefinition(SandstoneGolemModel.LAYER, SandstoneGolemModel::createBodyLayer); // Colosse de grès (modèle sur mesure)
        event.registerLayerDefinition(RoyalGuardModel.LAYER, RoyalGuardModel::createBodyLayer); // Garde royal momifié (modèle sur mesure)
        event.registerLayerDefinition(RootlingModel.LAYER, RootlingModel::createBodyLayer); // Rootling (Corrompus, modèle sur mesure)
        event.registerLayerDefinition(StoneBruteModel.MODEL_LAYER, StoneBruteModel::createBodyLayer); // Stone Brute (Corrompus, haute résolution)
        event.registerLayerDefinition(NeantideModel.MODEL_LAYER, NeantideModel::createBodyLayer); // Néantide (haute résolution)
        event.registerLayerDefinition(DevoreurModel.MODEL_LAYER, DevoreurModel::createBodyLayer); // 2.0 : le Dévoreur (Titan de la Faille)
        event.registerLayerDefinition(ColosseModel.MODEL_LAYER, ColosseModel::createBodyLayer);   // 2.0 : le Colosse de la Faille
        event.registerLayerDefinition(ArchonteModel.MODEL_LAYER, ArchonteModel::createBodyLayer); // 2.0 : l'Archonte du Néant
        event.registerLayerDefinition(SeraphShrineModel.MODEL_LAYER, SeraphShrineModel::createBodyLayer); // Sanctuaire du Séraphin (bloc, HD)
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ALTAR_RUNIC.get(), AltarBookRenderer::new);
        // Mode Kingdom : structures 3D des défenses (tours, sanctuaire, baraquement)
        event.registerBlockEntityRenderer(ModBlockEntities.DEFENSE.get(), DefenseRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SERAPH_SHRINE.get(), SeraphShrineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRAFT_UTILITY.get(), ObeliskCrystalRenderer::new); // cristal tournant de l'Obélisque profane
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.DIABLOTIN.get(), DiablotinRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.SIEGE_ENGINE.get(), SiegeEngineRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.TITAN_HITBOX.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.BRECHE.get(), BrecheRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.ECLAT_NEANT.get(),
                ctx -> new DiablotinRenderer(ctx, 0.72f, 0.36f, 1.0f)); // violet du Néant
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.XALTOR.get(), XaltorRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.TOTEM.get(), TotemRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.GISEMENT.get(), GisementRenderer::new);
        // Soldat du royaume : humanoïde propre au mod (corps humain + armure + arme)
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.KINGDOM_SOLDIER.get(), KingdomSoldierRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.FROST_GOLEM.get(), FrostGolemRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.SNOW_SORCERER.get(), SnowSorcererRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.FROST_RAVAGER.get(), FrostRavagerRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.DJINN.get(), DjinnRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.DJINN_SPIRIT.get(), DjinnSpiritRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.DJINN_FANGS.get(), DjinnFangsRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.SCARAB.get(), ScarabRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.SCORPION.get(), ScorpionRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.JACKAL.get(), JackalRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.SANDSTONE_GOLEM.get(), SandstoneGolemRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.ROYAL_GUARD.get(), RoyalGuardRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.ROOTLING.get(), RootlingRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.STONE_BRUTE.get(), StoneBruteRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.NEANTIDE.get(), NeantideRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.DEVOREUR.get(), DevoreurRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.COLOSSE.get(), ColosseRenderer::new);
        event.registerEntityRenderer(com.wavesurvivor.registry.ModEntities.ARCHONTE.get(), ArchonteRenderer::new);
        event.registerBlockEntityRenderer(com.wavesurvivor.registry.ModBlockEntities.ROULETTE_CHEST.get(), com.wavesurvivor.client.roulette.RouletteChestRenderer::new);
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
        // Calques : 0 contour (non teinté) · 1 tige, 2 panneton, 3 tête = matière · 4 gemme = couleur des gemmes du coffre
        event.register((stack, tintIndex) -> tintIndex >= 1 && tintIndex <= 3
                        ? com.wavesurvivor.horde.roulette.RouletteKeyItem.bodyColor(stack)
                        : tintIndex == 4 ? com.wavesurvivor.horde.roulette.RouletteKeyItem.gemColor(stack) : -1,
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
