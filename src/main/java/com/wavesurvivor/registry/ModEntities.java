package com.wavesurvivor.registry;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.entity.Diablotin;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Monstres propres au mod. */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, WaveSurvivorMod.MODID);

    /** Diablotin : vex rouge, vol au ras du sol, collisions actives, immunisé au feu. */
    public static final RegistryObject<EntityType<Diablotin>> DIABLOTIN = ENTITIES.register("diablotin",
            () -> EntityType.Builder.<Diablotin>of(Diablotin::new, MobCategory.MONSTER)
                    .sized(0.4f, 0.8f)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .build("diablotin"));

    /** Brèche : faille de portail immobile (mécanique Brèches infernales). */
    public static final RegistryObject<EntityType<com.wavesurvivor.entity.BrecheEntity>> BRECHE = ENTITIES.register("breche",
            () -> EntityType.Builder.<com.wavesurvivor.entity.BrecheEntity>of(com.wavesurvivor.entity.BrecheEntity::new, MobCategory.MISC)
                    .sized(1.2f, 3.0f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("breche"));

    /** Éclat du Néant : vex violet, vol au ras du sol, se téléporte quand il est touché. */
    public static final RegistryObject<EntityType<com.wavesurvivor.entity.EclatNeant>> ECLAT_NEANT = ENTITIES.register("eclat_neant",
            () -> EntityType.Builder.<com.wavesurvivor.entity.EclatNeant>of(com.wavesurvivor.entity.EclatNeant::new, MobCategory.MONSTER)
                    .sized(0.4f, 0.8f)
                    .clientTrackingRange(8)
                    .build("eclat_neant"));

    /** Xâl'Tor : enderman géant (×1,6), boss final du Néant Éternel. */
    public static final RegistryObject<EntityType<com.wavesurvivor.entity.XaltorEntity>> XALTOR = ENTITIES.register("xaltor",
            () -> EntityType.Builder.<com.wavesurvivor.entity.XaltorEntity>of(com.wavesurvivor.entity.XaltorEntity::new, MobCategory.MONSTER)
                    .sized(0.6f * 1.6f, 2.9f * 1.6f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("xaltor"));

    /** Cristal du Néant : cristal de l'End relié à Xâl'Tor (3 coups, sans explosion). */
    public static final RegistryObject<EntityType<com.wavesurvivor.entity.XaltorCrystal>> XALTOR_CRYSTAL = ENTITIES.register("xaltor_crystal",
            () -> EntityType.Builder.<com.wavesurvivor.entity.XaltorCrystal>of(com.wavesurvivor.entity.XaltorCrystal::new, MobCategory.MISC)
                    .sized(2.0f, 2.0f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("xaltor_crystal"));

    /** Totem : pilier sculpté (protection de boss, auras du chaos). */
    public static final RegistryObject<EntityType<com.wavesurvivor.entity.TotemEntity>> TOTEM = ENTITIES.register("totem",
            () -> EntityType.Builder.<com.wavesurvivor.entity.TotemEntity>of(com.wavesurvivor.entity.TotemEntity::new, MobCategory.MISC)
                    .sized(0.8f, 2.6f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("totem"));

    /** Gisement : amas de minerai à miner à la pioche (événement chaos). */
    public static final RegistryObject<EntityType<com.wavesurvivor.entity.GisementEntity>> GISEMENT = ENTITIES.register("gisement",
            () -> EntityType.Builder.<com.wavesurvivor.entity.GisementEntity>of(com.wavesurvivor.entity.GisementEntity::new, MobCategory.MISC)
                    .sized(1.4f, 1.3f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("gisement"));

    /** Mode Kingdom (étape 4b) : soldat allié apparu depuis un Baraquement. */
    public static final RegistryObject<EntityType<com.wavesurvivor.entity.KingdomSoldier>> KINGDOM_SOLDIER = ENTITIES.register("kingdom_soldier",
            () -> EntityType.Builder.<com.wavesurvivor.entity.KingdomSoldier>of(com.wavesurvivor.entity.KingdomSoldier::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(8)
                    .build("kingdom_soldier"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(DIABLOTIN.get(), Diablotin.createAttributes().build());
        event.put(BRECHE.get(), com.wavesurvivor.entity.BrecheEntity.createAttributes().build());
        event.put(ECLAT_NEANT.get(), com.wavesurvivor.entity.EclatNeant.createAttributes().build());
        event.put(XALTOR.get(), com.wavesurvivor.entity.XaltorEntity.createAttributes().build());
        event.put(TOTEM.get(), com.wavesurvivor.entity.TotemEntity.createAttributes().build());
        event.put(GISEMENT.get(), com.wavesurvivor.entity.GisementEntity.createAttributes().build());
        event.put(KINGDOM_SOLDIER.get(), com.wavesurvivor.entity.KingdomSoldier.createAttributes().build());
    }
}
