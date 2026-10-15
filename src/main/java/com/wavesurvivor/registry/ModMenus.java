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

    /** Brasier des Offrandes (Kingdom) : grille d'offrandes, jauge et boutons Sacrifier / spécialisation. */
    public static final RegistryObject<MenuType<com.wavesurvivor.horde.kingdom.OfferingBrazierMenu>> OFFERING_BRAZIER =
            MENUS.register("offering_brazier", () -> IForgeMenuType.create(com.wavesurvivor.horde.kingdom.OfferingBrazierMenu::fromNetwork));

    /** Fiche d'un rempart (Kingdom) : PV, coût de l'amélioration, bouton Renforcer. */
    public static final RegistryObject<MenuType<com.wavesurvivor.horde.kingdom.RampartMenu>> RAMPART =
            MENUS.register("kingdom_rampart", () -> IForgeMenuType.create(com.wavesurvivor.horde.kingdom.RampartMenu::fromNetwork));

    /** Stockage d'un Glaneur (Kingdom) : filtre de tri et onglets vers les Glaneurs proches. */
    public static final RegistryObject<MenuType<com.wavesurvivor.horde.kingdom.GleanerMenu>> GLEANER =
            MENUS.register("kingdom_gleaner", () -> IForgeMenuType.create(com.wavesurvivor.horde.kingdom.GleanerMenu::fromNetwork));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
