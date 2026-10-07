package com.wavesurvivor.client;

import com.wavesurvivor.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Dispatch client-only pour les packets S->C.
 *
 * Isolé du package network pour ne PAS charger Minecraft.getInstance() ni Screen
 * côté serveur dédié (crash sinon). Les handlers du package network appellent ces
 * méthodes via DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...).
 */
@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void handleSyncConfig(ModConfig config) {
        ClientConfigCache.set(config);
    }

    public static void openInspectionScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new WaveInspectionScreen());
        }
    }

    public static void openBenedictionScreen(java.util.List<String> ids, int currentWave) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new BenedictionSelectionScreen(ids, currentWave));
        }
    }

    public static void openBenedictionListScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new BenedictionListScreen());
        }
    }

    public static void openAltarConfirmScreen(com.wavesurvivor.network.OpenAltarConfirmScreenPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new AltarConfirmScreen(pkt));
        }
    }

    // ─── Éditeur de Hordes ───

    public static void openHordeEditorList(com.wavesurvivor.network.HordeEditorPackets.OpenList pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.screen instanceof HordeEditorScreen) return; // ne pas interrompre une édition
        String keep = mc.screen instanceof HordeEditorListScreen old ? old.status() : "";
        HordeEditorListScreen fresh = new HordeEditorListScreen(pkt);
        fresh.setStatus(keep);
        mc.setScreen(fresh);
    }

    public static void handleHordeData(com.wavesurvivor.network.HordeEditorPackets.HordeData pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !(mc.screen instanceof HordeEditorListScreen list)) return;
        if (pkt.purpose == com.wavesurvivor.network.HordeEditorPackets.PURPOSE_COPY) {
            list.onCopied(pkt.name, pkt.json, pkt.altarJson);
            return;
        }
        com.google.gson.JsonObject o = com.wavesurvivor.horde.editor.HordeJson.parseObject(pkt.json);
        if (o == null) { list.setStatus(com.wavesurvivor.i18n.WSLang.t("§cJSON de horde illisible.")); return; }
        mc.setScreen(new HordeEditorScreen(list, o, pkt.name, list.customEntities, pkt.altarJson));
    }

    public static void handleHordeEditorResult(com.wavesurvivor.network.HordeEditorPackets.Result pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.screen instanceof HordeEditorScreen ed) ed.onResult(pkt);
        else if (mc.screen instanceof CustomEntityEditorScreen ee) ee.onResult(pkt);
        else if (mc.screen instanceof HordeEditorListScreen list) list.setStatus((pkt.ok ? "§a✔ " : "§c✘ ") + pkt.message);
    }

    public static void handleForked(com.wavesurvivor.network.EntityEditorPackets.Forked pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof HordeEditorScreen ed) ed.onForked(pkt.source, pkt.newName);
    }

    public static void openEntityEditor(com.wavesurvivor.network.EntityEditorPackets.Data pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        com.google.gson.JsonObject data = com.wavesurvivor.horde.editor.HordeJson.parseObject(pkt.json);
        if (data == null) return;
        if (mc.screen instanceof CustomEntityEditorScreen ee) ee.refresh(data);
        else mc.setScreen(new CustomEntityEditorScreen(mc.screen, data));
    }

    public static void openRouletteLoot(com.wavesurvivor.network.RouletteLootPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.setScreen(new RouletteLootScreen(pkt));
    }

    public static void handleMonolithShop(com.wavesurvivor.network.MonolithShopPackets.State pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.screen instanceof MonolithShopScreen ms) ms.applyState(pkt);
        else if (pkt.open) mc.setScreen(new MonolithShopScreen(pkt));
    }

    public static void openAltarCustom(com.wavesurvivor.network.OpenAltarCustomPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.setScreen(new AltarColorScreen(mc.screen, pkt.pos, pkt.particle));
    }

    public static void openAltarBindScreen(com.wavesurvivor.network.OpenAltarBindScreenPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.setScreen(new AltarBindScreen(pkt));
    }

    public static void handleRenaissanceState(com.wavesurvivor.network.RenaissanceStatePacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.screen instanceof RenaissanceScreen rs) {
            rs.applyState(pkt);
        } else if (mc.screen instanceof RenaissanceRarityScreen rr) {
            rr.applyState(pkt); // écran Configuration (op) : config à jour + parent synchronisé
        } else if (pkt.open) {
            mc.setScreen(new RenaissanceScreen(pkt));
        }
    }

    public static void openChestBuilder(String initialName, String jsonData) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        // Mode édition si jsonData non vide, sinon création
        if (jsonData != null && !jsonData.isBlank()) {
            try {
                com.wavesurvivor.horde.roulette.CustomChestData data =
                        new com.google.gson.Gson().fromJson(jsonData, com.wavesurvivor.horde.roulette.CustomChestData.class);
                if (data != null && data.name != null) {
                    mc.setScreen(new ChestBuilderScreen(data));
                    return;
                }
            } catch (Exception e) {
                com.wavesurvivor.WaveSurvivorMod.LOGGER.error("[Client] Parse JSON chest échoué : {}", e.getMessage());
            }
        }
        mc.setScreen(new ChestBuilderScreen(initialName));
    }
}
