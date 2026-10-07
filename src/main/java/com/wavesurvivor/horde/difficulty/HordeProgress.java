package com.wavesurvivor.horde.difficulty;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * PROGRESSION DES JOUEURS (sauvegardée avec le monde) : pour chaque joueur, les hordes terminées et le meilleur niveau
 * de difficulté atteint. Sert au déblocage des hordes (« unlockRequires » : terminer la horde X, au moins au niveau Y).
 */
public class HordeProgress extends SavedData {

    private static final String NAME = "wavesurvivor_progress";
    /** joueur → (nom de horde en minuscules → meilleur niveau : ordinal de Difficulty). */
    private final Map<UUID, Map<String, Integer>> done = new HashMap<>();

    public static HordeProgress get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HordeProgress::load, HordeProgress::new, NAME);
    }

    private static String key(String horde) { return horde == null ? "" : horde.trim().toLowerCase(Locale.ROOT); }

    // ─── Enregistrement ───

    /** Victoire : la horde est créditée à chaque joueur (en gardant le meilleur niveau atteint). */
    public void record(UUID player, String horde, Difficulty d) {
        Map<String, Integer> m = done.computeIfAbsent(player, k -> new HashMap<>());
        int lvl = (d == null ? Difficulty.NORMAL : d).ordinal();
        m.merge(key(horde), lvl, Math::max);
        setDirty();
    }

    /** Meilleur niveau atteint sur cette horde (ordinal de Difficulty), ou -1 si jamais terminée. */
    public int best(UUID player, String horde) {
        Map<String, Integer> m = done.get(player);
        return m == null ? -1 : m.getOrDefault(key(horde), -1);
    }

    public Map<String, Integer> of(UUID player) { return done.getOrDefault(player, Map.of()); }

    public void reset(UUID player) {
        if (done.remove(player) != null) setDirty();
    }

    /** Fin de horde victorieuse : tous les joueurs connectés sont crédités. */
    public static void recordWin(MinecraftServer server, String horde) {
        if (server == null || horde == null) return;
        Difficulty d = HordeDifficulty.active();
        HordeProgress p = get(server);
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
            p.record(pl.getUUID(), horde, d);
            com.wavesurvivor.horde.renaissance.Heritage.markWin(pl); // 1.5 : la Renaissance devient possible
        }
        WaveSurvivorMod.LOGGER.info("[Progression] « {} » terminée ({}) par {} joueur(s).", horde,
                d == null ? "normal" : d.id, server.getPlayerList().getPlayerCount());
    }

    // ─── Déblocage ───

    /** Une ligne de condition : remplie ou non + texte (déjà traduit). */
    public record Line(boolean ok, String text) {}

    public static List<Line> status(ServerPlayer player, HordeConfigMultiData horde) {
        List<Line> out = new ArrayList<>();
        if (horde == null) return out;
        // Variante sans conditions propres (« … Moddée ») : elle hérite de celles de sa horde principale
        HordeConfigMultiData src = horde;
        boolean own = horde.configData != null && horde.configData.unlockRequires != null && !horde.configData.unlockRequires.isEmpty();
        String parent = com.wavesurvivor.altar.AltarRecipes.parentOf(horde.hordeName);
        if (!own && parent != null) {
            var cfg = WaveSurvivorMod.getConfig();
            if (cfg != null && cfg.hordeConfigMulti != null) {
                for (HordeConfigMultiData h : cfg.hordeConfigMulti) if (parent.equalsIgnoreCase(h.hordeName)) src = h;
            }
        }
        if (src.configData == null || src.configData.unlockRequires == null) return out;
        HordeProgress p = get(player.server);
        for (HordeConfigMultiData.ConfigDataInner.UnlockReq r : src.configData.unlockRequires) {
            if (r == null || r.horde == null || r.horde.isBlank()) continue;
            Difficulty min = Difficulty.byId(r.difficulty);
            // Terminer n'importe quelle variante de la horde demandée compte (Vanilla ou Moddée)
            int best = -1;
            for (String fam : com.wavesurvivor.altar.AltarRecipes.familyOf(r.horde)) best = Math.max(best, p.best(player.getUUID(), fam));
            boolean ok = best >= 0 && (min == null || best >= min.ordinal());
            String txt = min == null ? WSLang.t("unlock.req", WSLang.t(r.horde))
                    : WSLang.t("unlock.req_diff", WSLang.t(r.horde), min.color + WSLang.t("difficulty." + min.id));
            out.add(new Line(ok, txt));
        }
        return out;
    }

    public static boolean unlocked(ServerPlayer player, HordeConfigMultiData horde) {
        for (Line l : status(player, horde)) if (!l.ok()) return false;
        return true;
    }

    /** Lignes encodées pour l'écran de l'autel : « 1|texte » (remplie) ou « 0|texte », séparées par des retours à la ligne. */
    public static String encode(ServerPlayer player, HordeConfigMultiData horde) {
        StringBuilder sb = new StringBuilder();
        for (Line l : status(player, horde)) sb.append(sb.length() > 0 ? "\n" : "").append(l.ok() ? "1|" : "0|").append(l.text());
        return sb.toString();
    }

    /** Refus de lancement : liste des conditions manquantes dans le chat. */
    public static void explain(ServerPlayer player, HordeConfigMultiData horde) {
        player.sendSystemMessage(Component.literal(WSLang.t("unlock.locked", WSLang.t(horde.hordeName))));
        for (Line l : status(player, horde)) player.sendSystemMessage(Component.literal((l.ok() ? " \u00a7a\u2714 " : " \u00a7c\u2716 ") + "\u00a77" + l.text()));
    }

    // ─── Sauvegarde ───

    private static HordeProgress load(CompoundTag tag) {
        HordeProgress p = new HordeProgress();
        CompoundTag players = tag.getCompound("players");
        for (String id : players.getAllKeys()) {
            try {
                CompoundTag h = players.getCompound(id);
                Map<String, Integer> m = new HashMap<>();
                for (String k : h.getAllKeys()) m.put(k, h.getInt(k));
                p.done.put(UUID.fromString(id), m);
            } catch (IllegalArgumentException ignored) {}
        }
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Map<String, Integer>> e : done.entrySet()) {
            CompoundTag h = new CompoundTag();
            for (Map.Entry<String, Integer> x : e.getValue().entrySet()) h.putInt(x.getKey(), x.getValue());
            players.put(e.getKey().toString(), h);
        }
        tag.put("players", players);
        return tag;
    }
}
