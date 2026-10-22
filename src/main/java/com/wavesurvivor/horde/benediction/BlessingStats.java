package com.wavesurvivor.horde.benediction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * STATISTIQUES DES BÉNÉDICTIONS (1.8, sauvegardées avec le monde) : pour chaque bénédiction, combien de fois elle a été proposée, choisie, et
 * dans combien de parties terminées elle était dans le sac du joueur (et combien de ces parties ont été gagnées). Les catégories bannies sont
 * comptées à part. Sert à équilibrer : /ws benediction stats, et la fiche du catalogue.
 * Cases d'un tableau : 0 proposée, 1 choisie, 2 bannie (catégorie), 3 parties, 4 victoires.
 */
public class BlessingStats extends SavedData {

    public static final int OFFERED = 0, CHOSEN = 1, BANNED = 2, RUNS = 3, WINS = 4;
    private static final String NAME = "wavesurvivor_blessing_stats";
    private static final String CAT = "cat:";

    private final Map<String, int[]> data = new HashMap<>();

    public static BlessingStats get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BlessingStats::load, BlessingStats::new, NAME);
    }

    private int[] row(String key) { return data.computeIfAbsent(key, k -> new int[5]); }

    private void add(String key, int idx) {
        row(key)[idx]++;
        setDirty();
    }

    // ─── Enregistrement (appelé par le serveur) ───

    public static void offered(MinecraftServer s, List<String> ids) {
        if (s == null) return;
        BlessingStats st = get(s);
        for (String id : ids) st.add(id, OFFERED);
    }

    public static void chosen(MinecraftServer s, String id) {
        if (s != null) get(s).add(id, CHOSEN);
    }

    public static void banned(MinecraftServer s, String category) {
        if (s != null) get(s).add(CAT + category, BANNED);
    }

    /** Fin de partie : chaque bénédiction que portait un joueur compte une partie (et une victoire si c'est gagné). */
    public static void recordRun(MinecraftServer s, boolean win) {
        if (s == null) return;
        BlessingStats st = get(s);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            for (String id : RogueUpgradeManager.levels(p).keySet()) {
                st.add(id, RUNS);
                if (win) st.add(id, WINS);
            }
        }
    }

    public void reset() {
        data.clear();
        setDirty();
    }

    // ─── Lecture ───

    /** Copie des statistiques (bénédiction → tableau). Les catégories bannies sont préfixées « cat: ». */
    public Map<String, int[]> snapshot() {
        Map<String, int[]> m = new HashMap<>();
        for (Map.Entry<String, int[]> e : data.entrySet()) m.put(e.getKey(), e.getValue().clone());
        return m;
    }

    public static boolean isCategoryKey(String key) { return key.startsWith(CAT); }

    public static String categoryOf(String key) { return key.substring(CAT.length()); }

    // ─── Sauvegarde ───

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag t = new CompoundTag();
        for (Map.Entry<String, int[]> e : data.entrySet()) t.putIntArray(e.getKey(), e.getValue());
        tag.put("stats", t);
        return tag;
    }

    private static BlessingStats load(CompoundTag tag) {
        BlessingStats st = new BlessingStats();
        CompoundTag t = tag.getCompound("stats");
        for (String k : t.getAllKeys()) {
            int[] a = t.getIntArray(k);
            int[] r = new int[5];
            System.arraycopy(a, 0, r, 0, Math.min(a.length, 5));
            st.data.put(k, r);
        }
        return st;
    }
}
