package com.wavesurvivor.registry;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Menus (conteneurs) personnalisés du mod. */
public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, WaveSurvivorMod.MODID);

    /** Atelier de Réparation (Kingdom) : emplacements réservés aux objets à durabilité, propriétaire par objet. */
    public static final RegistryObject<MenuType<com.wavesurvivor.horde.kingdom.WorkshopMenu>> WORKSHOP =
            MENUS.register("kingdom_workshop", () -> IForgeMenuType.create(com.wavesurvivor.horde.kingdom.WorkshopMenu::fromNetwork));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
