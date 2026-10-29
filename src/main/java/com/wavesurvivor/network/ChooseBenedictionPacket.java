package com.wavesurvivor.network;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.benediction.RogueUpgrade;
import com.wavesurvivor.horde.benediction.RogueUpgradeManager;
import com.wavesurvivor.horde.benediction.RogueUpgradeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet C->S : le joueur a cliqué sur une bénédiction dans le screen.
 * Le serveur valide (horde en cours, pas déjà choisi cette vague, id valide),
 * applique l'upgrade puis marque la vague comme utilisée.
 */
public class ChooseBenedictionPacket {

    private final String id;

    public ChooseBenedictionPacket(String id) {
        this.id = id;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id, 128);
    }

    public static ChooseBenedictionPacket decode(FriendlyByteBuf buf) {
        return new ChooseBenedictionPacket(buf.readUtf(128));
    }

    public static void handle(ChooseBenedictionPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            RogueUpgrade upg = RogueUpgradeRegistry.get(pkt.id);
            if (upg == null) {
                player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.benediction_inconnue") + pkt.id)
                        .withStyle(ChatFormatting.RED));
                WaveSurvivorMod.LOGGER.warn("[ChooseBenediction] '{}' a envoyé un id inconnu : {}",
                        player.getName().getString(), pkt.id);
                return;
            }
            if (!HordeManager.get().isRunning()) {
                player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucune_horde_en_cours")).withStyle(ChatFormatting.RED));
                return;
            }
            int currentWave = HordeManager.get().getCurrentWave();
            if (currentWave > 0 && RogueUpgradeManager.getLastChosenWave(player) == currentWave) {
                player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.vous_avez_deja_choisi_un_don_pour_cette"))
                        .withStyle(ChatFormatting.YELLOW));
                return;
            }

            if (!RogueUpgradeManager.canChoose(player, pkt.id)) {
                WaveSurvivorMod.LOGGER.warn("[ChooseBenediction] '{}' a choisi une bénédiction qui ne lui était pas proposée : {}",
                        player.getName().getString(), pkt.id);
                return;
            }
            RogueUpgradeManager.addUpgrade(player, pkt.id);
            RogueUpgradeManager.onChosen(player);
            RogueUpgradeManager.setLastChosenWave(player, currentWave);
            com.wavesurvivor.horde.benediction.BenedictionProtection.release(player); // 1.7 : fin de la protection (multijoueur)
            com.wavesurvivor.network.EventFeedPacket.toPlayer(player, Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.vous_avez_choisi") + upg.name())
                    .withStyle(upg.color(), ChatFormatting.BOLD), "minecraft:totem_of_undying", com.wavesurvivor.network.EventFeedPacket.BLESSING, false);
        });
        context.setPacketHandled(true);
    }
}
