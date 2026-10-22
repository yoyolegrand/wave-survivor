package com.wavesurvivor.horde.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.benediction.BlessingCatalog;
import com.wavesurvivor.horde.benediction.CustomBlessingStore;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.BlessingEditorPackets;
import com.wavesurvivor.network.HordeEditorPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.regex.Pattern;

/**
 * Côté serveur de l'ÉDITEUR DES BÉNÉDICTIONS (1.8, opérateurs) : liste les catégories (base du mod + couche du joueur), enregistre, supprime et
 * rétablit. Chaque modification recharge le catalogue et le renvoie à tous les joueurs. Pas de modification pendant une horde (les joueurs
 * retiennent leurs bénédictions par identifiant).
 */
public final class BlessingEditorManager {

    private static final Pattern ID = Pattern.compile("[A-Z0-9_]{2,40}");

    private BlessingEditorManager() {}

    private static boolean allowed(ServerPlayer p) {
        if (p.hasPermissions(2)) return true;
        p.sendSystemMessage(Component.literal(WSLang.t("srv.editeur_reserve_aux_operateurs")));
        return false;
    }

    private static boolean idle(ServerPlayer p) {
        if (!HordeManager.get().isRunning()) return true;
        result(p, false, WSLang.t("blessed.err.running"));
        return false;
    }

    /** Catégories effectives + origine de chacune. */
    private static String payload() {
        JsonObject root = new JsonObject();
        JsonArray cats = BlessingCatalog.categories();
        JsonObject sources = new JsonObject();
        for (JsonElement e : cats) {
            if (!e.isJsonObject()) continue;
            String id = HordeJson.str(e.getAsJsonObject(), "id", "");
            String s = CustomBlessingStore.sourceOf(id);
            if (s.equals("OVERRIDE")) s += ":" + CustomBlessingStore.modifiedFields(id);
            sources.addProperty(id, s);
        }
        root.add("categories", cats);
        root.add("sources", sources);
        root.add("settings", BlessingCatalog.settings().deepCopy());
        JsonArray packs = new JsonArray();
        for (String n : CustomBlessingStore.listPacks()) packs.add(n);
        root.add("packs", packs);
        return root.toString();
    }

    public static void open(ServerPlayer p) {
        if (!allowed(p)) return;
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new BlessingEditorPackets.Data(payload()));
    }

    public static void save(ServerPlayer p, String oldId, String json) {
        if (!allowed(p) || !idle(p)) return;
        JsonObject cat;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) throw new IllegalArgumentException("pas un objet");
            cat = el.getAsJsonObject();
        } catch (Exception e) {
            result(p, false, WSLang.t("srv.json_invalide") + e.getMessage());
            return;
        }
        String id = HordeJson.str(cat, "id", "").trim().toUpperCase();
        if (!ID.matcher(id).matches()) { result(p, false, WSLang.t("blessed.err.id")); return; }
        cat.addProperty("id", id);
        if (HordeJson.str(cat, "name", "").isBlank()) { result(p, false, WSLang.t("blessed.err.name")); return; }
        // L'identifiant d'une catégorie existante ne change jamais (les joueurs retiennent leurs niveaux par identifiant)
        if (oldId != null && !oldId.isBlank() && !oldId.equalsIgnoreCase(id)) { result(p, false, WSLang.t("blessed.err.rename")); return; }
        boolean existed = oldId != null && !oldId.isBlank();
        if (!existed && categoryExists(id)) { result(p, false, WSLang.t("blessed.err.exists", id)); return; }

        // Validation des niveaux : raretés connues, attributs enregistrés
        JsonObject tiers = HordeJson.obj(cat, "tiers");
        for (String k : new java.util.ArrayList<>(tiers.keySet())) {
            boolean known = false;
            for (String r : BlessingCatalog.RARITIES) if (r.equals(k)) known = true;
            if (!known) tiers.remove(k);
        }
        int usable = 0;
        for (String r : BlessingCatalog.RARITIES) {
            if (!tiers.has(r) || !tiers.get(r).isJsonObject()) continue;
            JsonObject t = tiers.getAsJsonObject(r);
            for (JsonElement me : HordeJson.arr(t, "modifiers")) {
                if (!me.isJsonObject()) continue;
                String attr = HordeJson.str(me.getAsJsonObject(), "attribute", "");
                if (BlessingCatalog.attribute(attr) == null) { result(p, false, WSLang.t("blessed.err.attribute", attr)); return; }
            }
            if (HordeJson.bool(t, "enabled", true)) usable++;
        }
        if (usable == 0) { result(p, false, WSLang.t("blessed.err.notier")); return; }

        CustomBlessingStore.put(cat);
        reloadAndSync(p, WSLang.t("blessed.saved", HordeJson.str(cat, "name", id)));
    }

    /** Réglages du tirage (poids, Chance, relances…) : json vide = valeurs du mod. */
    public static void saveSettings(ServerPlayer p, String json) {
        if (!allowed(p)) return;
        if (json == null || json.isBlank()) {
            CustomBlessingStore.putSettings(null);
            reloadAndSync(p, WSLang.t("blessset.reset_done"));
            return;
        }
        JsonObject s = HordeJson.parseObject(json);
        if (s == null) { result(p, false, WSLang.t("srv.json_invalide")); return; }
        CustomBlessingStore.putSettings(s);
        reloadAndSync(p, WSLang.t("blessset.saved"));
    }

    /** Export / import d'un pack de bénédictions (dossier config/wavesurvivor/blessing_packs). */
    public static void pack(ServerPlayer p, byte action, String name) {
        if (!allowed(p) || !idle(p)) return;
        if (!CustomBlessingStore.validPackName(name)) { result(p, false, WSLang.t("blesspack.err_name")); return; }
        if (action == BlessingEditorPackets.PackAction.EXPORT) {
            try {
                CustomBlessingStore.exportPack(name);
                result(p, true, WSLang.t("blesspack.exported", name));
            } catch (Exception e) {
                result(p, false, WSLang.t("blesspack.err_io", e.getMessage()));
            }
            return;
        }
        CustomBlessingStore.ImportResult r = CustomBlessingStore.importPack(name, action == BlessingEditorPackets.PackAction.IMPORT_OVERWRITE);
        if (r.error() != null) { result(p, false, WSLang.t("blesspack.err_import", name, r.error())); return; }
        String msg = WSLang.t("blesspack.imported", name, r.added(), r.replaced());
        if (!r.skipped().isEmpty()) msg += WSLang.t("blesspack.skipped", r.skipped().size(), String.join(", ", r.skipped()));
        reloadAndSync(p, msg);
    }

    public static void delete(ServerPlayer p, String id) {
        if (!allowed(p) || !idle(p)) return;
        CustomBlessingStore.delete(id);
        reloadAndSync(p, WSLang.t("blessed.deleted", id));
    }

    public static void revert(ServerPlayer p, String id) {
        if (!allowed(p) || !idle(p)) return;
        if (id == null || id.isBlank()) {
            for (String d : CustomBlessingStore.deletedBaseIds()) CustomBlessingStore.revert(d);
            reloadAndSync(p, WSLang.t("blessed.restored"));
        } else {
            CustomBlessingStore.revert(id);
            reloadAndSync(p, WSLang.t("cfg.reverted", id));
        }
    }

    private static boolean categoryExists(String id) {
        for (JsonElement e : BlessingCatalog.categories()) {
            if (e.isJsonObject() && HordeJson.str(e.getAsJsonObject(), "id", "").equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    private static void reloadAndSync(ServerPlayer p, String message) {
        BlessingCatalog.load();
        BlessingCatalog.syncAll(p.getServer());
        WaveSurvivorMod.LOGGER.info("[Bénédictions] {} : {}", p.getGameProfile().getName(), message);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new BlessingEditorPackets.Data(payload()));
        result(p, true, message);
    }

    private static void result(ServerPlayer p, boolean ok, String msg) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HordeEditorPackets.Result(ok, msg, ""));
    }
}
