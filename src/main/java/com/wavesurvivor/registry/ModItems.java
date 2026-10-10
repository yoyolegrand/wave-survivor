package com.wavesurvivor.registry;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.roulette.RouletteChestConfig;
import com.wavesurvivor.horde.roulette.RouletteChestManager;
import com.wavesurvivor.horde.roulette.RouletteChestRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Items du mod + onglet créatif dédié "Wave Survivor" :
 * autels, une clé par coffre roulette (NBT prêt), et les coffres roulette eux-mêmes.
 */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WaveSurvivorMod.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WaveSurvivorMod.MODID);

    /** BlockItem pour l'Autel Runique (Monolithe). */
    public static final RegistryObject<Item> ALTAR_RUNIC_ITEM = ITEMS.register("altar_runic",
            () -> new BlockItem(ModBlocks.ALTAR_RUNIC.get(), new Item.Properties()));

    /** BlockItem pour l'Autel de Renaissance. */
    public static final RegistryObject<Item> RENAISSANCE_ALTAR_ITEM = ITEMS.register("renaissance_altar",
            () -> new BlockItem(ModBlocks.RENAISSANCE_ALTAR.get(), new Item.Properties()));

    /** Mode Kingdom : défenses (achetées à la boutique du Monolithe). */
    public static final RegistryObject<Item> KINGDOM_ARCHER_TOWER_ITEM = ITEMS.register("kingdom_archer_tower",
            () -> new BlockItem(ModBlocks.KINGDOM_ARCHER_TOWER.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> KINGDOM_MAGE_TOWER_ITEM = ITEMS.register("kingdom_mage_tower",
            () -> new BlockItem(ModBlocks.KINGDOM_MAGE_TOWER.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> KINGDOM_REPAIR_SHRINE_ITEM = ITEMS.register("kingdom_repair_shrine",
            () -> new BlockItem(ModBlocks.KINGDOM_REPAIR_SHRINE.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> KINGDOM_BARRACKS_ITEM = ITEMS.register("kingdom_barracks",
            () -> new BlockItem(ModBlocks.KINGDOM_BARRACKS.get(), new Item.Properties().stacksTo(16)));
    /** Mode Kingdom (éditeur) : Bâton de tracé des chemins des escouades. Donné par l'éditeur, absent de l'onglet créatif. */
    public static final RegistryObject<Item> PATH_WAND = ITEMS.register("path_wand",
            () -> new com.wavesurvivor.horde.kingdom.PathWandItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> KINGDOM_COLLECTOR_ITEM = ITEMS.register("kingdom_collector",
            () -> new BlockItem(ModBlocks.KINGDOM_COLLECTOR.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> KINGDOM_WORKSHOP_ITEM = ITEMS.register("kingdom_workshop",
            () -> new BlockItem(ModBlocks.KINGDOM_WORKSHOP.get(), new Item.Properties().stacksTo(16)));
    /** Kingdom : Boussole du Royaume (réglée depuis la carte de la Mairie). */
    public static final RegistryObject<Item> RELIQUAIRE_SCELLE = ITEMS.register("reliquaire_scelle",
            () -> new com.wavesurvivor.item.RelicCacheItem(new Item.Properties()));
    /** Kingdom : Boussole du Royaume (réglée depuis la carte de la Mairie). */
    public static final RegistryObject<Item> KINGDOM_COMPASS = ITEMS.register("kingdom_compass",
            () -> new com.wavesurvivor.horde.kingdom.KingdomCompassItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> KINGDOM_RAMPART_ITEM = ITEMS.register("kingdom_rampart",
            () -> new com.wavesurvivor.horde.kingdom.RampartItem(ModBlocks.KINGDOM_RAMPART.get(), new Item.Properties()));
    public static final RegistryObject<Item> KINGDOM_GATE_ITEM = ITEMS.register("kingdom_gate",
            () -> new com.wavesurvivor.horde.kingdom.GateItem(new Item.Properties().stacksTo(8)));
    /** Charge de démolition du Bâtisseur (mode Kingdom). */
    public static final RegistryObject<Item> DEMOLITION_CHARGE = ITEMS.register("demolition_charge",
            () -> new com.wavesurvivor.horde.kingdom.DemolitionChargeItem(new Item.Properties().stacksTo(1)));
    /** Cor de ralliement du Commandant (mode Kingdom). */
    public static final RegistryObject<Item> COMMANDER_HORN = ITEMS.register("commander_horn",
            () -> new com.wavesurvivor.horde.kingdom.CommanderHornItem(new Item.Properties().stacksTo(1)));
    /** Fiole d'alchimiste (Alchimiste, mode Kingdom) : potion jetable empilable par 16. */
    public static final RegistryObject<Item> ALCHEMY_FLASK = ITEMS.register("alchemy_flask",
            () -> new com.wavesurvivor.horde.kingdom.AlchemyFlaskItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> KINGDOM_TRAP_SPIKES_ITEM = ITEMS.register("kingdom_trap_spikes",
            () -> new BlockItem(ModBlocks.KINGDOM_TRAP_SPIKES.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> KINGDOM_TRAP_FIRE_ITEM = ITEMS.register("kingdom_trap_fire",
            () -> new BlockItem(ModBlocks.KINGDOM_TRAP_FIRE.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> KINGDOM_TRAP_FROST_ITEM = ITEMS.register("kingdom_trap_frost",
            () -> new BlockItem(ModBlocks.KINGDOM_TRAP_FROST.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> KINGDOM_TRAP_EXPLOSIVE_ITEM = ITEMS.register("kingdom_trap_explosive",
            () -> new BlockItem(ModBlocks.KINGDOM_TRAP_EXPLOSIVE.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> KINGDOM_TRAP_SNARE_ITEM = ITEMS.register("kingdom_trap_snare",
            () -> new BlockItem(ModBlocks.KINGDOM_TRAP_SNARE.get(), new Item.Properties().stacksTo(16)));

    /** Clé de roulette chest (remplace l'ancien name_tag). Identité = NBT. */
    public static final RegistryObject<Item> ROULETTE_KEY = ITEMS.register("roulette_key",
            () -> new com.wavesurvivor.horde.roulette.RouletteKeyItem(
                    new Item.Properties().stacksTo(64).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    /** Coffre Roulette (Reliquaire runique). Le nom de l'item = le coffre (config). */
    public static final RegistryObject<Item> ROULETTE_CHEST_ITEM = ITEMS.register("roulette_chest",
            () -> new BlockItem(ModBlocks.ROULETTE_CHEST.get(), new Item.Properties()));

    /** Œuf d'apparition du Diablotin. */
    public static final RegistryObject<Item> DIABLOTIN_SPAWN_EGG = ITEMS.register("diablotin_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.DIABLOTIN, 0x7A1208, 0xFFB31A, new Item.Properties()));

    /** Œuf d'apparition de l'Éclat du Néant. */
    public static final RegistryObject<Item> ECLAT_NEANT_SPAWN_EGG = ITEMS.register("eclat_neant_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.ECLAT_NEANT, 0x160A24, 0xB04DFF, new Item.Properties()));

    /** Œuf d'apparition du Golem de Givre (horde des Pics Gelés). */
    public static final RegistryObject<Item> FROST_GOLEM_SPAWN_EGG = ITEMS.register("frost_golem_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.FROST_GOLEM, 0x4F8FC8, 0xE6F8FF, new Item.Properties()));

    /** Œuf d'apparition du Sorcier des Neiges (horde des Pics Gelés). */
    public static final RegistryObject<Item> SNOW_SORCERER_SPAWN_EGG = ITEMS.register("snow_sorcerer_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.SNOW_SORCERER, 0xDDEEFA, 0x1E3A6E, new Item.Properties()));

    /** Œuf d'apparition du Ravageur de givre (base d'Aurvang). */
    public static final RegistryObject<Item> FROST_RAVAGER_SPAWN_EGG = ITEMS.register("frost_ravager_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.FROST_RAVAGER, 0xB0CCE4, 0x3492C4, new Item.Properties()));

    /** Relique : Anneau d'Ancrage du Néant (immunité à la Lévitation, compatible Curios « bague »). */
    public static final RegistryObject<Item> ANNEAU_ANCRAGE = ITEMS.register("anneau_ancrage",
            com.wavesurvivor.item.AnneauAncrageItem::new);

    // ─── Reliques R1 (hordes) ───
    public static final RegistryObject<Item> AMULETTE_BRASIER = ITEMS.register("amulette_brasier",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique des Terres Brûlées", "Collier",
                    "Le feu reconnaît son maître.", "Immunité au feu et à la lave", "Ne brûle jamais"));
    public static final RegistryObject<Item> CHARME_PHYLACTERE = ITEMS.register("charme_phylactere",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique de la Nécropole", "Charme",
                    "Un fragment d'âme que la mort ne peut ronger.", "Immunité au Wither", "Immunité au Poison"));
    public static final RegistryObject<Item> OEIL_VEILLEUR = ITEMS.register("oeil_veilleur",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique du Néant", "Tête",
                    "Rien ne se cache à l'Œil qui ne dort jamais.", "Immunité à la Cécité et aux Ténèbres",
                    "Révèle les monstres à moins de 16 blocs"));
    public static final RegistryObject<Item> CEINTURE_PLOMB = ITEMS.register("ceinture_plomb",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique des Terres Brûlées", "Ceinture",
                    "Ni charge ni vortex ne te déplaceront.", "Aucun recul (coups, charges, puits, attractions)"));

    // ─── Reliques R2 (combat) ───
    public static final RegistryObject<Item> OS_SACRE = ITEMS.register("os_sacre",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique de la Nécropole", "Charme",
                    "Béni contre ce qui refuse de mourir.", "+25 % de dégâts contre les morts-vivants"));
    public static final RegistryObject<Item> ECLAT_BRECHE = ITEMS.register("eclat_breche",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique des Terres Brûlées", "Bague",
                    "Une faille se referme mieux de l'intérieur.", "Dégâts ×2 contre les Brèches",
                    "Chaque Brèche détruite te soigne de 4 PV"));
    public static final RegistryObject<Item> MARQUE_CHASSEUR = ITEMS.register("marque_chasseur",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique du Chasseur", "Bracelet",
                    "Ce que tu touches ne peut plus se cacher.", "Tes coups font briller la cible pendant 5 s"));
    public static final RegistryObject<Item> COEUR_ASSOIFFE = ITEMS.register("coeur_assoiffe",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique du Bastion", "Collier",
                    "Chaque victoire te nourrit.", "Chaque monstre tué te soigne de 1 PV"));

    // ─── Reliques R3 (survie) ───
    public static final RegistryObject<Item> TALISMAN_SOUFFLE = ITEMS.register("talisman_souffle",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique légendaire", "Charme",
                    "Pas encore. Pas cette fois.", "Une fois par vague : un coup fatal te laisse à 1 PV",
                    "+ 3 s d'invulnérabilité (hors horde : toutes les 5 min)"));
    public static final RegistryObject<Item> SCEAU_GARDIEN = ITEMS.register("sceau_gardien",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique du Monolithe", "Ceinture",
                    "Tant que tu veilles, la pierre tient.", "À moins de 8 blocs du Monolithe : il subit −30 % de dégâts"));
    public static final RegistryObject<Item> BANNIERE_RALLIEMENT = ITEMS.register("banniere_ralliement",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique de l'équipe", "Dos",
                    "Ensemble, jusqu'à la dernière vague.", "Joueurs à moins de 8 blocs (toi compris) : +2 armure",
                    "Régénération I toutes les 10 s"));

    // ─── Reliques R4 (économie) ───
    public static final RegistryObject<Item> ANNEAU_PROSPECTEUR = ITEMS.register("anneau_prospecteur",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique du Prospecteur", "Bague",
                    "La roche s'ouvre à qui sait l'écouter.", "Gisements : chaque coup de pioche compte double",
                    "+50 % de butin à la destruction d'un gisement"));
    public static final RegistryObject<Item> CLE_GARDIEN = ITEMS.register("cle_gardien",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique légendaire", "Charme",
                    "Chaque serrure finit par céder.", "Pendant une horde : 10 % de chance qu'un monstre tué",
                    "lâche une clé de Reliquaire"));
    public static final RegistryObject<Item> LANGUE_ARGENT = ITEMS.register("langue_argent",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique du Marchand", "Collier",
                    "Chaque prix est une proposition.", "−25 % sur les prix des marchands de la horde"));

    // ─── Reliques 1.5 (familles : Rempart, Traque, Abîme, Éléments, Fortune) ───
    public static final RegistryObject<Item> PIERRE_FONDATION = ITEMS.register("pierre_fondation",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique du Rempart", "Ceinture",
                    "Ce qui est bâti avec soin ne cède pas.", "Kingdom : constructions et défenses +15 % de PV",
                    "Réparations des défenses −25 %"));
    public static final RegistryObject<Item> CROC_ALPHA = ITEMS.register("croc_alpha",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique de la Traque", "Collier",
                    "Le chef de meute tombe toujours le premier.", "+30 % de dégâts aux élites, champions, gardiens et boss"));
    public static final RegistryObject<Item> PRISME_CATALYSEUR = ITEMS.register("prisme_catalyseur",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique de l'Abîme", "Tête",
                    "La lumière révèle ce que l'abîme cache.", "Dégâts ×2 aux Catalyseurs",
                    "Une colonne de lumière révèle chaque Catalyseur"));
    public static final RegistryObject<Item> FRAGMENT_PORTE = ITEMS.register("fragment_porte",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique de l'Abîme", "Bracelet",
                    "Un éclat arraché à sa propre Porte.", "+25 % de dégâts aux Portes"));
    public static final RegistryObject<Item> CRISTAL_GIVRE = ITEMS.register("cristal_givre",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique des Éléments", "Bague",
                    "Le froid ne mord pas celui qui le porte.", "Immunité à la Lenteur et au gel"));
    public static final RegistryObject<Item> BOURSE_COLPORTEUR = ITEMS.register("bourse_colporteur",
            () -> new com.wavesurvivor.item.RelicItem(net.minecraft.world.item.Rarity.EPIC, "Relique de la Fortune", "Ceinture",
                    "Une pièce de plus, toujours.", "+15 % de monnaie lâchée par tes victimes",
                    "Marchands : chaque offre épuisée se recharge d'un achat"));

    /** Onglet créatif dédié. */
    // ─── Reliques légendaires 1.5 (fusions de la Forge) ───
    private static RegistryObject<Item> legendary(String id, String slot, String quote, String... effects) {
        return ITEMS.register(id, () -> new com.wavesurvivor.item.LegendaryRelicItem("Relique légendaire", slot, quote, effects));
    }

    public static final RegistryObject<Item> CLE_VOUTE = legendary("cle_voute", "Ceinture",
            "La pierre qui tient toutes les autres.", "Effets du Sceau du Gardien et de la Pierre de Fondation (niv. II)",
            "Unique : le Sceau protège le Monolithe où que tu sois");
    public static final RegistryObject<Item> ETENDARD_BASTION = legendary("etendard_bastion", "Dos",
            "Tant qu'il flotte, personne ne recule.", "Effets du Sceau du Gardien et de la Bannière de Ralliement (niv. II)",
            "Unique : aura de la Bannière à 16 blocs, avec Résistance I");
    public static final RegistryObject<Item> TROPHEE_VENEUR = legendary("trophee_veneur", "Collier",
            "La proie désignée n'a plus nulle part où fuir.", "Effets de la Marque du Chasseur et du Croc de l'Alpha (niv. II)",
            "Unique : tes cibles marquées subissent +20 % de toute l'équipe");
    public static final RegistryObject<Item> CALICE_SANG = legendary("calice_sang", "Charme",
            "Chaque victoire remplit la coupe.", "Effets de l'Os Sacré et du Cœur Assoiffé (niv. II)",
            "Unique : +2 PV par kill, le surplus devient de l'Absorption (8 max)");
    public static final RegistryObject<Item> OEIL_FAILLE = legendary("oeil_faille", "Tête",
            "Ce qu'il voit, l'équipe le voit.", "Effets de l'Éclat de Brèche et du Prisme du Catalyseur (niv. II)",
            "Unique : Catalyseurs révélés à toute l'équipe");
    public static final RegistryObject<Item> CLE_PORTES = legendary("cle_portes", "Bracelet",
            "Chaque serrure de l'Abîme a sa clé.", "Effets du Prisme du Catalyseur et du Fragment de Porte (niv. II)",
            "Unique : un Catalyseur détruit arrache 10 % de PV à la Porte la plus proche");
    public static final RegistryObject<Item> EGIDE_FLEAUX = legendary("egide_fleaux", "Collier",
            "Aucun fléau ne franchit ce bouclier.", "Effets de l'Amulette du Brasier et du Charme de Phylactère (niv. II)",
            "Unique : immunité à la Faiblesse et à la Nausée");
    public static final RegistryObject<Item> COEUR_SAISONS = legendary("coeur_saisons", "Charme",
            "Le feu de l'été, la morsure de l'hiver.", "Effets de l'Amulette du Brasier et du Cristal de Givre (niv. II)",
            "Unique : tes coups brûlent et gèlent en alternance");
    public static final RegistryObject<Item> ANCRE_COLOSSE = legendary("ancre_colosse", "Ceinture",
            "Rien ne déplace le colosse.", "Effets de l'Anneau d'Ancrage et de la Ceinture de Plomb (niv. II)",
            "Unique : aucun dégât de chute");
    public static final RegistryObject<Item> REGARD_VEILLEUR = legendary("regard_veilleur", "Tête",
            "Il a vu la mort et l'a regardée en face.", "Effets de l'Œil du Veilleur et du Talisman du Dernier Souffle (niv. II)",
            "Unique : sauvé de la mort → 5 s d'invulnérabilité et monstres révélés à 32 blocs");
    public static final RegistryObject<Item> SCEAU_MARCHAND_ROI = legendary("sceau_marchand_roi", "Bague",
            "Le roi ne paie jamais deux fois.", "Effets de la Langue d'Argent et de la Bourse du Colporteur (niv. II)",
            "Unique : 20 % de chance qu'un achat chez un marchand soit remboursé");
    public static final RegistryObject<Item> TROUSSEAU_PILLEUR = legendary("trousseau_pilleur", "Charme",
            "Une clé pour chaque trésor.", "Effets de la Clé du Gardien et de l'Anneau du Prospecteur (niv. II)",
            "Unique : un gisement détruit lâche une clé de Reliquaire (25 %)");

    /** Onglet créatif dédié. */
    public static final RegistryObject<CreativeModeTab> WAVE_SURVIVOR_TAB = TABS.register("wave_survivor",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wavesurvivor"))
                    .icon(() -> new ItemStack(ALTAR_RUNIC_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(ALTAR_RUNIC_ITEM.get());
                        output.accept(RENAISSANCE_ALTAR_ITEM.get());
                        output.accept(DIABLOTIN_SPAWN_EGG.get());
                        output.accept(FROST_GOLEM_SPAWN_EGG.get());
                        output.accept(SNOW_SORCERER_SPAWN_EGG.get());
                        output.accept(FROST_RAVAGER_SPAWN_EGG.get());
                        output.accept(ECLAT_NEANT_SPAWN_EGG.get());
                        output.accept(ANNEAU_ANCRAGE.get());
                        output.accept(AMULETTE_BRASIER.get());
                        output.accept(CHARME_PHYLACTERE.get());
                        output.accept(OEIL_VEILLEUR.get());
                        output.accept(CEINTURE_PLOMB.get());
                        output.accept(OS_SACRE.get());
                        output.accept(ECLAT_BRECHE.get());
                        output.accept(MARQUE_CHASSEUR.get());
                        output.accept(COEUR_ASSOIFFE.get());
                        output.accept(TALISMAN_SOUFFLE.get());
                        output.accept(SCEAU_GARDIEN.get());
                        output.accept(BANNIERE_RALLIEMENT.get());
                        output.accept(ANNEAU_PROSPECTEUR.get());
                        output.accept(CLE_GARDIEN.get());
                        output.accept(LANGUE_ARGENT.get());
                        output.accept(PIERRE_FONDATION.get());
                        output.accept(CROC_ALPHA.get());
                        output.accept(PRISME_CATALYSEUR.get());
                        output.accept(FRAGMENT_PORTE.get());
                        output.accept(CRISTAL_GIVRE.get());
                        output.accept(BOURSE_COLPORTEUR.get());
                        output.accept(CLE_VOUTE.get());
                        output.accept(ETENDARD_BASTION.get());
                        output.accept(TROPHEE_VENEUR.get());
                        output.accept(CALICE_SANG.get());
                        output.accept(OEIL_FAILLE.get());
                        output.accept(CLE_PORTES.get());
                        output.accept(EGIDE_FLEAUX.get());
                        output.accept(COEUR_SAISONS.get());
                        output.accept(ANCRE_COLOSSE.get());
                        output.accept(REGARD_VEILLEUR.get());
                        output.accept(SCEAU_MARCHAND_ROI.get());
                        output.accept(TROUSSEAU_PILLEUR.get());
                        // Mode Kingdom : défenses, remparts et pièges
                        output.accept(KINGDOM_ARCHER_TOWER_ITEM.get());
                        output.accept(KINGDOM_MAGE_TOWER_ITEM.get());
                        output.accept(KINGDOM_REPAIR_SHRINE_ITEM.get());
                        output.accept(KINGDOM_BARRACKS_ITEM.get());
                        output.accept(KINGDOM_COLLECTOR_ITEM.get());
                        output.accept(KINGDOM_WORKSHOP_ITEM.get());
                        output.accept(KINGDOM_COMPASS.get());
                        output.accept(RELIQUAIRE_SCELLE.get());
                        output.accept(KINGDOM_RAMPART_ITEM.get());
                        output.accept(KINGDOM_GATE_ITEM.get());
                        output.accept(KINGDOM_TRAP_SPIKES_ITEM.get());
                        output.accept(KINGDOM_TRAP_FIRE_ITEM.get());
                        output.accept(KINGDOM_TRAP_FROST_ITEM.get());
                        output.accept(KINGDOM_TRAP_EXPLOSIVE_ITEM.get());
                        output.accept(KINGDOM_TRAP_SNARE_ITEM.get());

                        // Une clé + un coffre par config de roulette chest (triés par nom)
                        List<RouletteChestConfig> configs = new ArrayList<>();
                        try {
                            for (String k : RouletteChestRegistry.allKeys()) {
                                RouletteChestConfig c = RouletteChestRegistry.get(k);
                                if (c != null) configs.add(c);
                            }
                        } catch (Exception ignored) {}
                        configs.sort((a, b) -> String.valueOf(a.chestName()).compareToIgnoreCase(String.valueOf(b.chestName())));

                        Set<String> seen = new HashSet<>(); // l'onglet refuse deux stacks identiques
                        if (configs.isEmpty()) {
                            output.accept(ROULETTE_KEY.get()); // aucune config chargée : clé vierge
                        }
                        for (RouletteChestConfig c : configs) {
                            ItemStack key = RouletteChestManager.createKeyItem(c);
                            if (seen.add("k|" + key.getTag())) output.accept(key);
                        }
                        for (RouletteChestConfig c : configs) {
                            ItemStack chest = RouletteChestManager.createChestItem(c);
                            if (!chest.isEmpty() && seen.add("c|" + chest.getItem() + "|" + chest.getTag())) output.accept(chest);
                        }
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        TABS.register(eventBus);
    }
}
