package com.wavesurvivor.network;

import com.wavesurvivor.horde.roulette.CustomChestStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet C->S : demande la suppression d'un chest custom par son nom.
 * Envoyé depuis le bouton "Supprimer" du ChestBuilderScreen (mode édition)
 * ou depuis la commande /ws roulettechest delete <name>.
 */
public class DeleteCustomChestPacket {

    public final String chestName;

    public DeleteCustomChestPacket(String chestName) {
        this.chestName = chestName != null ? chestName : "";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.chestName);
    }

    public static DeleteCustomChestPacket decode(FriendlyByteBuf buf) {
        return new DeleteCustomChestPacket(buf.readUtf());
    }

    public static void handle(DeleteCustomChestPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            if (!sender.hasPermissions(2)) {
                sender.displayClientMessage(
                        Component.literal(com.wavesurvivor.i18n.WSLang.t("✗ Il faut être op pour supprimer un chest custom."))
                                .withStyle(ChatFormatting.RED), false);
                return;
            }

            if (pkt.chestName == null || pkt.chestName.isBlank()) {
                sender.displayClientMessage(
                        Component.literal("✗ Nom vide.").withStyle(ChatFormatting.RED), false);
                return;
            }

            boolean removed = CustomChestStore.delete(pkt.chestName);
            if (removed) {
                sender.displayClientMessage(
                        Component.literal(com.wavesurvivor.i18n.WSLang.t("✓ Coffre '") + pkt.chestName + com.wavesurvivor.i18n.WSLang.t("' supprimé."))
                                .withStyle(ChatFormatting.GREEN), false);
            } else {
                sender.displayClientMessage(
                        Component.literal(com.wavesurvivor.i18n.WSLang.t("✗ Coffre '") + pkt.chestName + com.wavesurvivor.i18n.WSLang.t("' introuvable."))
                                .withStyle(ChatFormatting.YELLOW), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
