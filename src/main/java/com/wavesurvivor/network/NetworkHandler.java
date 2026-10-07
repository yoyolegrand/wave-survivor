package com.wavesurvivor.network;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Point d'entrée réseau du mod.
 *
 * Register des packets bidirectionnels :
 *   - SyncConfigPacket           (S->C) : envoie le ModConfig serveur au client, gzip compressé
 *   - OpenInspectionScreenPacket (S->C) : demande au client d'ouvrir WaveInspectionScreen
 *
 * Appelé une seule fois dans WaveSurvivorMod.commonSetup().
 */
public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(WaveSurvivorMod.MODID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        int id = 0;
        CHANNEL.registerMessage(id++, SyncConfigPacket.class,
                SyncConfigPacket::encode, SyncConfigPacket::decode, SyncConfigPacket::handle);
        CHANNEL.registerMessage(id++, OpenInspectionScreenPacket.class,
                OpenInspectionScreenPacket::encode, OpenInspectionScreenPacket::decode, OpenInspectionScreenPacket::handle);
        CHANNEL.registerMessage(id++, OpenBenedictionScreenPacket.class,
                OpenBenedictionScreenPacket::encode, OpenBenedictionScreenPacket::decode, OpenBenedictionScreenPacket::handle);
        CHANNEL.registerMessage(id++, ChooseBenedictionPacket.class,
                ChooseBenedictionPacket::encode, ChooseBenedictionPacket::decode, ChooseBenedictionPacket::handle);
        CHANNEL.registerMessage(id++, OpenBenedictionListScreenPacket.class,
                OpenBenedictionListScreenPacket::encode, OpenBenedictionListScreenPacket::decode, OpenBenedictionListScreenPacket::handle);
        CHANNEL.registerMessage(id++, OpenAltarConfirmScreenPacket.class,
                OpenAltarConfirmScreenPacket::encode, OpenAltarConfirmScreenPacket::decode, OpenAltarConfirmScreenPacket::handle);
        CHANNEL.registerMessage(id++, TriggerAltarPacket.class,
                TriggerAltarPacket::encode, TriggerAltarPacket::decode, TriggerAltarPacket::handle);
        CHANNEL.registerMessage(id++, OpenChestBuilderPacket.class,
                OpenChestBuilderPacket::encode, OpenChestBuilderPacket::decode, OpenChestBuilderPacket::handle);
        CHANNEL.registerMessage(id++, SaveCustomChestPacket.class,
                SaveCustomChestPacket::encode, SaveCustomChestPacket::decode, SaveCustomChestPacket::handle);
        CHANNEL.registerMessage(id++, DeleteCustomChestPacket.class,
                DeleteCustomChestPacket::encode, DeleteCustomChestPacket::decode, DeleteCustomChestPacket::handle);
        CHANNEL.registerMessage(id++, RenaissanceStatePacket.class,
                RenaissanceStatePacket::encode, RenaissanceStatePacket::decode, RenaissanceStatePacket::handle);
        CHANNEL.registerMessage(id++, RenaissanceSacrificePacket.class,
                RenaissanceSacrificePacket::encode, RenaissanceSacrificePacket::decode, RenaissanceSacrificePacket::handle);
        CHANNEL.registerMessage(id++, RenaissanceBuyPacket.class,
                RenaissanceBuyPacket::encode, RenaissanceBuyPacket::decode, RenaissanceBuyPacket::handle);
        CHANNEL.registerMessage(id++, RenaissanceShopEditPacket.class,
                RenaissanceShopEditPacket::encode, RenaissanceShopEditPacket::decode, RenaissanceShopEditPacket::handle);
        CHANNEL.registerMessage(id++, RelicForgePacket.class,
                RelicForgePacket::encode, RelicForgePacket::decode, RelicForgePacket::handle);
        CHANNEL.registerMessage(id++, RelicEquipSyncPacket.class,
                RelicEquipSyncPacket::encode, RelicEquipSyncPacket::decode, RelicEquipSyncPacket::handle);
        CHANNEL.registerMessage(id++, HeritageSyncPacket.class,
                HeritageSyncPacket::encode, HeritageSyncPacket::decode, HeritageSyncPacket::handle);
        CHANNEL.registerMessage(id++, KingdomMapPackets.Request.class,
                KingdomMapPackets.Request::encode, KingdomMapPackets.Request::decode, KingdomMapPackets.Request::handle);
        CHANNEL.registerMessage(id++, KingdomMapPackets.Data.class,
                KingdomMapPackets.Data::encode, KingdomMapPackets.Data::decode, KingdomMapPackets.Data::handle);
        CHANNEL.registerMessage(id++, KingdomMapPackets.SetCompass.class,
                KingdomMapPackets.SetCompass::encode, KingdomMapPackets.SetCompass::decode, KingdomMapPackets.SetCompass::handle);
        CHANNEL.registerMessage(id++, DefenseSheetPackets.Sheet.class,
                DefenseSheetPackets.Sheet::encode, DefenseSheetPackets.Sheet::decode, DefenseSheetPackets.Sheet::handle);
        CHANNEL.registerMessage(id++, DefenseSheetPackets.Action.class,
                DefenseSheetPackets.Action::encode, DefenseSheetPackets.Action::decode, DefenseSheetPackets.Action::handle);
        CHANNEL.registerMessage(id++, EventFeedPacket.class,
                EventFeedPacket::encode, EventFeedPacket::decode, EventFeedPacket::handle);
        CHANNEL.registerMessage(id++, OpenAltarBindScreenPacket.class,
                OpenAltarBindScreenPacket::encode, OpenAltarBindScreenPacket::decode, OpenAltarBindScreenPacket::handle);
        CHANNEL.registerMessage(id++, AltarBindPacket.class,
                AltarBindPacket::encode, AltarBindPacket::decode, AltarBindPacket::handle);
        CHANNEL.registerMessage(id++, AltarColorPacket.class,
                AltarColorPacket::encode, AltarColorPacket::decode, AltarColorPacket::handle);
        CHANNEL.registerMessage(id++, AltarCustomPackets.Request.class,
                AltarCustomPackets.Request::encode, AltarCustomPackets.Request::decode, AltarCustomPackets.Request::handle);
        CHANNEL.registerMessage(id++, AltarCustomPackets.SetParticle.class,
                AltarCustomPackets.SetParticle::encode, AltarCustomPackets.SetParticle::decode, AltarCustomPackets.SetParticle::handle);
        CHANNEL.registerMessage(id++, OpenAltarCustomPacket.class,
                OpenAltarCustomPacket::encode, OpenAltarCustomPacket::decode, OpenAltarCustomPacket::handle);
        CHANNEL.registerMessage(id++, MonolithShopPackets.State.class,
                MonolithShopPackets.State::encode, MonolithShopPackets.State::decode, MonolithShopPackets.State::handle);
        CHANNEL.registerMessage(id++, MonolithShopPackets.Upgrade.class,
                MonolithShopPackets.Upgrade::encode, MonolithShopPackets.Upgrade::decode, MonolithShopPackets.Upgrade::handle);
        CHANNEL.registerMessage(id++, RouletteLootPacket.class,
                RouletteLootPacket::encode, RouletteLootPacket::decode, RouletteLootPacket::handle);
        CHANNEL.registerMessage(id++, RenaissanceRarityPacket.class,
                RenaissanceRarityPacket::encode, RenaissanceRarityPacket::decode, RenaissanceRarityPacket::handle);
        // Éditeur de Hordes
        CHANNEL.registerMessage(id++, HordeEditorPackets.OpenList.class,
                HordeEditorPackets.OpenList::encode, HordeEditorPackets.OpenList::decode, HordeEditorPackets.OpenList::handle);
        CHANNEL.registerMessage(id++, HordeEditorPackets.Request.class,
                HordeEditorPackets.Request::encode, HordeEditorPackets.Request::decode, HordeEditorPackets.Request::handle);
        CHANNEL.registerMessage(id++, HordeEditorPackets.HordeData.class,
                HordeEditorPackets.HordeData::encode, HordeEditorPackets.HordeData::decode, HordeEditorPackets.HordeData::handle);
        CHANNEL.registerMessage(id++, HordeEditorPackets.Save.class,
                HordeEditorPackets.Save::encode, HordeEditorPackets.Save::decode, HordeEditorPackets.Save::handle);
        CHANNEL.registerMessage(id++, HordeEditorPackets.Delete.class,
                HordeEditorPackets.Delete::encode, HordeEditorPackets.Delete::decode, HordeEditorPackets.Delete::handle);
        CHANNEL.registerMessage(id++, HordeEditorPackets.Result.class,
                HordeEditorPackets.Result::encode, HordeEditorPackets.Result::decode, HordeEditorPackets.Result::handle);
        // Mode Kingdom : Bâton de tracé des chemins (donné depuis l'éditeur)
        CHANNEL.registerMessage(id++, HordeEditorPackets.GiveWand.class,
                HordeEditorPackets.GiveWand::encode, HordeEditorPackets.GiveWand::decode, HordeEditorPackets.GiveWand::handle);
        // « La Chute du Monolithe » : ciel rouge sang côté client
        CHANNEL.registerMessage(id++, MonolithFallPacket.class,
                MonolithFallPacket::encode, MonolithFallPacket::decode, MonolithFallPacket::handle);
        // Éditeur d'entités custom et de compétences
        CHANNEL.registerMessage(id++, EntityEditorPackets.Open.class,
                EntityEditorPackets.Open::encode, EntityEditorPackets.Open::decode, EntityEditorPackets.Open::handle);
        CHANNEL.registerMessage(id++, EntityEditorPackets.Data.class,
                EntityEditorPackets.Data::encode, EntityEditorPackets.Data::decode, EntityEditorPackets.Data::handle);
        CHANNEL.registerMessage(id++, EntityEditorPackets.Save.class,
                EntityEditorPackets.Save::encode, EntityEditorPackets.Save::decode, EntityEditorPackets.Save::handle);
        CHANNEL.registerMessage(id++, EntityEditorPackets.Delete.class,
                EntityEditorPackets.Delete::encode, EntityEditorPackets.Delete::decode, EntityEditorPackets.Delete::handle);
        CHANNEL.registerMessage(id++, EntityEditorPackets.Fork.class,
                EntityEditorPackets.Fork::encode, EntityEditorPackets.Fork::decode, EntityEditorPackets.Fork::handle);
        CHANNEL.registerMessage(id++, EntityEditorPackets.Forked.class,
                EntityEditorPackets.Forked::encode, EntityEditorPackets.Forked::decode, EntityEditorPackets.Forked::handle);
        // Langue du mod
        CHANNEL.registerMessage(id++, LangSyncPacket.class,
                LangSyncPacket::encode, LangSyncPacket::decode, LangSyncPacket::handle);
        // Trésor commun du royaume (mode Kingdom)
        CHANNEL.registerMessage(id++, KingdomTreasuryPacket.class,
                KingdomTreasuryPacket::encode, KingdomTreasuryPacket::decode, KingdomTreasuryPacket::handle);
        // Rôles du royaume (mode Kingdom)
        CHANNEL.registerMessage(id++, KingdomRolesPacket.class,
                KingdomRolesPacket::encode, KingdomRolesPacket::decode, KingdomRolesPacket::handle);
        CHANNEL.registerMessage(id++, RoleTrackerPacket.class,
                RoleTrackerPacket::encode, RoleTrackerPacket::decode, RoleTrackerPacket::handle);
        CHANNEL.registerMessage(id++, RoleStatePacket.class,
                RoleStatePacket::encode, RoleStatePacket::decode, RoleStatePacket::handle);
        // Import / export de hordes (1.4)
        CHANNEL.registerMessage(id++, HordeExchangePackets.Export.class,
                HordeExchangePackets.Export::encode, HordeExchangePackets.Export::decode, HordeExchangePackets.Export::handle);
        CHANNEL.registerMessage(id++, HordeExchangePackets.OpenImport.class,
                HordeExchangePackets.OpenImport::encode, HordeExchangePackets.OpenImport::decode, HordeExchangePackets.OpenImport::handle);
        CHANNEL.registerMessage(id++, HordeExchangePackets.DoImport.class,
                HordeExchangePackets.DoImport::encode, HordeExchangePackets.DoImport::decode, HordeExchangePackets.DoImport::handle);
        // Éditeur : tester une vague
        CHANNEL.registerMessage(id++, HordeTestPackets.Test.class,
                HordeTestPackets.Test::encode, HordeTestPackets.Test::decode, HordeTestPackets.Test::handle);
        CHANNEL.registerMessage(id++, HordeTestPackets.Clear.class,
                HordeTestPackets.Clear::encode, HordeTestPackets.Clear::decode, HordeTestPackets.Clear::handle);
        // Codex (/ws codex)
        CHANNEL.registerMessage(id++, CodexPacket.class,
                CodexPacket::encode, CodexPacket::decode, CodexPacket::handle);
        // Éditeur de hordes à plusieurs (verrous par onglet)
        CHANNEL.registerMessage(id++, HordeCollabPackets.Presence.class,
                HordeCollabPackets.Presence::encode, HordeCollabPackets.Presence::decode, HordeCollabPackets.Presence::handle);
        CHANNEL.registerMessage(id++, HordeCollabPackets.Locks.class,
                HordeCollabPackets.Locks::encode, HordeCollabPackets.Locks::decode, HordeCollabPackets.Locks::handle);
        CHANNEL.registerMessage(id++, HordeCollabPackets.SectionUpdate.class,
                HordeCollabPackets.SectionUpdate::encode, HordeCollabPackets.SectionUpdate::decode, HordeCollabPackets.SectionUpdate::handle);

        WaveSurvivorMod.LOGGER.info("[Network] Channel 'main' enregistré (protocol v{}, {} packets)",
                PROTOCOL_VERSION, id);
    }
}
