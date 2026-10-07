package com.wavesurvivor.network;

import com.google.gson.Gson;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.roulette.CustomChestData;
import com.wavesurvivor.horde.roulette.CustomChestStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet C->S : le client envoie un CustomChestData sérialisé en JSON après validation du builder.
 * Le serveur le parse, l'ajoute au CustomChestStore, save le fichier, et confirme au joueur.
 */
public class SaveCustomChestPacket {

    private static final Gson GSON = new Gson();

    public final String jsonPayload;

    public SaveCustomChestPacket(String jsonPayload) {
        this.jsonPayload = jsonPayload != null ? jsonPayload : "";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.jsonPayload, 65536); // limite explicite (JSON peut être long)
    }

    public static SaveCustomChestPacket decode(FriendlyByteBuf buf) {
        return new SaveCustomChestPacket(buf.readUtf(65536));
    }

    public static void handle(SaveCustomChestPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            // Permissions : op only (permission level 2+)
            if (!sender.hasPermissions(2)) {
                sender.displayClientMessage(
                        Component.literal(com.wavesurvivor.i18n.WSLang.t("✗ Il faut être op pour sauvegarder un chest custom."))
                                .withStyle(ChatFormatting.RED), false);
                return;
            }

            try {
                CustomChestData data = GSON.fromJson(pkt.jsonPayload, CustomChestData.class);
                if (data == null || data.name == null || data.name.isBlank()) {
                    sender.displayClientMessage(
                            Component.literal(com.wavesurvivor.i18n.WSLang.t("✗ Données invalides.")).withStyle(ChatFormatting.RED), false);
                    return;
                }
                // Validation du total
                double total = 0;
                if (data.rewards != null) for (CustomChestData.Reward r : data.rewards) total += r.chance;
                if (total > 100.5) {
                    sender.displayClientMessage(
                            Component.literal(com.wavesurvivor.i18n.WSLang.t("✗ Total des chances > 100% (") + total + com.wavesurvivor.i18n.WSLang.t("%). Refusé."))
                                    .withStyle(ChatFormatting.RED), false);
                    return;
                }

                boolean ok = CustomChestStore.addOrReplace(data);
                if (ok) {
                    int nRewards = data.rewards != null ? data.rewards.size() : 0;
                    sender.displayClientMessage(
                            Component.literal(com.wavesurvivor.i18n.WSLang.t("✓ Coffre '") + data.name + com.wavesurvivor.i18n.WSLang.t("' sauvegardé (")
                                    + nRewards + " rewards, " + total + "% total).")
                                    .withStyle(ChatFormatting.GREEN), false);
                    sender.displayClientMessage(
                            Component.literal(com.wavesurvivor.i18n.WSLang.t("  → utilise §e/ws roulettechest list§7 pour voir, §e/ws roulettechest give custom_")
                                    + slugify(data.name) + com.wavesurvivor.i18n.WSLang.t("§7 pour obtenir un coffre."))
                                    .withStyle(ChatFormatting.GRAY), false);
                } else {
                    sender.displayClientMessage(
                            Component.literal(com.wavesurvivor.i18n.WSLang.t("✗ Sauvegarde échouée (voir logs serveur)."))
                                    .withStyle(ChatFormatting.RED), false);
                }
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[SaveCustomChestPacket] Erreur : {}", e.getMessage(), e);
                sender.displayClientMessage(
                        Component.literal("✗ Erreur serveur : " + e.getMessage())
                                .withStyle(ChatFormatting.RED), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static String slugify(String s) {
        if (s == null) return "unnamed";
        String out = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return out.isEmpty() ? "unnamed" : out;
    }
}
