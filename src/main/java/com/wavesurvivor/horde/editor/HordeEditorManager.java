package com.wavesurvivor.horde.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.CustomHordeStore;
import com.wavesurvivor.config.ModConfig;
import com.wavesurvivor.config.model.CustomEntityData;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.network.HordeEditorPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Côté serveur de l'Éditeur de Hordes (ops uniquement). */
public final class HordeEditorManager {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private HordeEditorManager() {}

    private static boolean allowed(ServerPlayer p) {
        if (p.hasPermissions(2)) return true;
        p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.editeur_reserve_aux_operateurs")));
        return false;
    }

    /** /ws editor : ouvre la liste des hordes. */
    public static void openList(ServerPlayer p) {
        if (!allowed(p)) return;
        List<HordeEditorPackets.Entry> entries = new ArrayList<>();
        for (String name : CustomHordeStore.names()) {
            JsonObject h = CustomHordeStore.get(name);
            JsonObject cd = h != null && h.has("configData") && h.get("configData").isJsonObject() ? h.getAsJsonObject("configData") : new JsonObject();
            entries.add(new HordeEditorPackets.Entry(name, CustomHordeStore.sourceOf(name).name(),
                    intOf(cd, "totalWaves"), sizeOf(cd, "hordeEntities"), sizeOf(cd, "specialWaves"), sizeOf(cd, "bossWaves")));
        }
        List<String> ce = new ArrayList<>();
        ModConfig cfg = WaveSurvivorMod.getConfig();
        if (cfg != null && cfg.customEntity != null) {
            for (CustomEntityData e : cfg.customEntity) if (e.entityName != null) ce.add(e.entityName);
        }
        ce.sort(String.CASE_INSENSITIVE_ORDER);
        List<String> chests = new ArrayList<>();
        try {
            for (String k : com.wavesurvivor.horde.roulette.RouletteChestRegistry.allKeys()) {
                var c = com.wavesurvivor.horde.roulette.RouletteChestRegistry.get(k);
                if (c != null) chests.add(k + "|" + c.chestName());
            }
        } catch (Exception ignored) {}
        chests.sort(String.CASE_INSENSITIVE_ORDER);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HordeEditorPackets.OpenList(entries, ce, chests,
                EntityEditorManager.skillCatalogJson())); // + catalogue des compétences (onglet Mobs)
    }

    public static void sendHorde(ServerPlayer p, String name, int purpose) {
        if (!allowed(p)) return;
        JsonObject h = CustomHordeStore.get(name);
        if (h == null) {
            result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.horde_introuvable") + name, null);
            return;
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new HordeEditorPackets.HordeData(name, purpose, GSON.toJson(h),
                        com.wavesurvivor.altar.AltarRecipes.toJson(name)));
    }

    public static void save(ServerPlayer p, String oldName, String json, String altarJson) {
        save(p, oldName, json, altarJson, HordeSections.ALL);
    }

    /** @param mask onglets à enregistrer (HordeSections.ALL = toute la horde). À plusieurs : fusion onglet par onglet. */
    public static void save(ServerPlayer p, String oldName, String json, String altarJson, int mask) {
        if (!allowed(p)) return;
        if (HordeManager.get().isRunning()) {
            result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.impossible_d_enregistrer_pendant_une_hor"), null);
            return;
        }
        JsonObject h;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) throw new IllegalArgumentException(com.wavesurvivor.i18n.WSLang.t("pas un objet"));
            h = el.getAsJsonObject();
        } catch (Exception e) {
            result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.json_invalide") + e.getMessage(), null);
            return;
        }
        String name = h.has("hordeName") ? h.get("hordeName").getAsString().trim() : "";
        if (name.isBlank()) { result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.la_horde_doit_avoir_un_nom"), null); return; }
        if (!h.has("configData") || !h.get("configData").isJsonObject()) {
            result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.la_horde_n_a_pas_de_configdata"), null);
            return;
        }
        // ── Édition à plusieurs : seuls les onglets demandés (et non occupés par un autre) sont fusionnés ──
        int effective = HordeSections.ALL;
        int skipped = 0;
        boolean partial = mask != HordeSections.ALL && oldName != null && !oldName.isBlank() && CustomHordeStore.exists(oldName);
        if (partial) {
            effective = HordeEditSessions.allowedMask(p, oldName, mask);
            skipped = mask & ~effective;
            boolean wantsRename = !oldName.equalsIgnoreCase(name);
            if (wantsRename && HordeSections.has(effective, HordeSections.T_GENERAL) && HordeEditSessions.othersEditing(p, oldName)) {
                result(p, false, com.wavesurvivor.i18n.WSLang.t("collab.no_rename"), null);
                return;
            }
            JsonObject stored = CustomHordeStore.get(oldName);
            if (stored != null) {
                JsonObject base = stored.deepCopy();
                HordeSections.merge(base, h, effective);
                h = base;
                name = h.has("hordeName") ? h.get("hordeName").getAsString().trim() : oldName;
            }
            if (!HordeSections.has(effective, HordeSections.T_ALTAR)) altarJson = "";
        }
        h.addProperty("hordeName", name);
        boolean renamed = oldName == null || oldName.isBlank() || !oldName.equalsIgnoreCase(name);
        if (renamed && CustomHordeStore.exists(name)) {
            result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.une_horde") + name + com.wavesurvivor.i18n.WSLang.t("srv.existe_deja"), null);
            return;
        }
        // Sanity : tableaux attendus présents
        JsonObject cd = h.getAsJsonObject("configData");
        for (String arr : new String[]{"hordeEntities", "specialWaves", "bossWaves"}) {
            if (!cd.has(arr) || !cd.get(arr).isJsonArray()) cd.add(arr, new JsonArray());
        }
        if (!h.has("id")) h.addProperty("id", java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24));

        CustomHordeStore.put(h, oldName);
        // Config d'autel (altar_recipes.json) : suit le renommage, remplacée si éditée
        com.wavesurvivor.altar.AltarRecipes.putFromEditor(name, oldName, altarJson);
        String summary = WaveSurvivorMod.reloadConfig();
        WaveSurvivorMod.LOGGER.info("[Éditeur] {} a enregistré la horde « {} » ({})", p.getGameProfile().getName(), name, summary);
        // À plusieurs : les sessions suivent un renommage, les autres éditeurs reçoivent les onglets enregistrés
        if (oldName != null && !oldName.isBlank()) HordeEditSessions.rename(oldName, name);
        HordeEditSessions.broadcastUpdate(p, name, effective, GSON.toJson(h), com.wavesurvivor.altar.AltarRecipes.toJson(name));
        String msg = com.wavesurvivor.i18n.WSLang.t("srv.horde") + name + com.wavesurvivor.i18n.WSLang.t("srv.enregistree");
        if (skipped != 0) msg += " " + com.wavesurvivor.i18n.WSLang.t("collab.skipped");
        result(p, true, msg, name);
    }

    public static void delete(ServerPlayer p, String name) {
        if (!allowed(p)) return;
        if (HordeManager.get().isRunning()) {
            result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.impossible_de_supprimer_pendant_une_hord"), null);
            return;
        }
        if (!CustomHordeStore.exists(name)) { result(p, false, com.wavesurvivor.i18n.WSLang.t("srv.horde_introuvable") + name, null); return; }
        CustomHordeStore.delete(name);
        com.wavesurvivor.altar.AltarRecipes.removeFromEditor(name);
        WaveSurvivorMod.reloadConfig();
        result(p, true, com.wavesurvivor.i18n.WSLang.t("srv.horde") + name + com.wavesurvivor.i18n.WSLang.t("srv.supprimee"), null);
        openList(p);
    }

    private static void result(ServerPlayer p, boolean ok, String msg, String savedName) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HordeEditorPackets.Result(ok, msg, savedName));
    }

    private static int intOf(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsInt() : 0; } catch (Exception e) { return 0; }
    }

    private static int sizeOf(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k).size() : 0;
    }
}
