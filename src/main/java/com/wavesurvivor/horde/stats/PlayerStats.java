package com.wavesurvivor.horde.stats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * STATISTIQUES DES JOUEURS (1.7, sauvegardées avec le monde) : compteurs permanents par joueur (hordes gagnées, vagues
 * terminées, monstres et boss tués, morts, temps de jeu, meilleure vague du sans fin, Défis du jour…).
 * Chaque changement vérifie les succès (Achievements). Les clés sont des textes libres : les compteurs
 * « won_horde:<nom> » comptent les hordes différentes gagnées (clé dérivée « distinct_hordes »).
 */
public class PlayerStats extends SavedData {

    private static final String NAME = "wavesurvivor_stats";
    public static final String WON_PREFIX = "won_horde:";

    private final Map<UUID, Map<String, Long>> data = new HashMap<>();

    public static PlayerStats get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PlayerStats::load, PlayerStats::new, NAME);
    }

    // ─── API ───

    /** Ajoute {@code n} au compteur du joueur. */
    public static void add(ServerPlayer p, String key, long n) {
        if (p == null || p.getServer() == null || n == 0) return;
        PlayerStats s = get(p.getServer());
        Map<String, Long> m = s.data.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        m.merge(key, n, Long::sum);
        s.setDirty();
        Achievements.check(p, s.snapshot(p.getUUID()));
    }

    /** Garde le maximum entre la valeur actuelle et {@code v} (meilleure vague, meilleure série…). */
    public static void max(ServerPlayer p, String key, long v) {
        if (p == null || p.getServer() == null) return;
        PlayerStats s = get(p.getServer());
        Map<String, Long> m = s.data.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        if (m.getOrDefault(key, 0L) >= v) return;
        m.put(key, v);
        s.setDirty();
        Achievements.check(p, s.snapshot(p.getUUID()));
    }

    /** Ajoute {@code n} à tous les joueurs connectés qui ne sont pas spectateurs. */
    public static void addForAll(MinecraftServer srv, String key, long n) {
        if (srv == null) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) if (!p.isSpectator()) add(p, key, n);
    }

    /** Compteurs d'un joueur, avec la clé dérivée « distinct_hordes ». */
    public Map<String, Long> snapshot(UUID id) {
        Map<String, Long> out = new HashMap<>(data.getOrDefault(id, Map.of()));
        long distinct = 0;
        for (String k : out.keySet()) if (k.startsWith(WON_PREFIX)) distinct++;
        out.put("distinct_hordes", distinct);
        return out;
    }

    public static Map<String, Long> snapshotOf(ServerPlayer p) {
        if (p == null || p.getServer() == null) return new HashMap<>();
        return get(p.getServer()).snapshot(p.getUUID());
    }

    // ─── Sauvegarde NBT ───

    private static PlayerStats load(CompoundTag t) {
        PlayerStats s = new PlayerStats();
        ListTag players = t.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag pc = players.getCompound(i);
            UUID id;
            try { id = UUID.fromString(pc.getString("id")); } catch (IllegalArgumentException e) { continue; }
            Map<String, Long> m = new HashMap<>();
            ListTag vals = pc.getList("values", Tag.TAG_COMPOUND);
            for (int j = 0; j < vals.size(); j++) {
                CompoundTag vc = vals.getCompound(j);
                m.put(vc.getString("k"), vc.getLong("v"));
            }
            s.data.put(id, m);
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Map<String, Long>> pe : data.entrySet()) {
            CompoundTag pc = new CompoundTag();
            pc.putString("id", pe.getKey().toString());
            ListTag vals = new ListTag();
            for (Map.Entry<String, Long> ve : pe.getValue().entrySet()) {
                CompoundTag vc = new CompoundTag();
                vc.putString("k", ve.getKey());
                vc.putLong("v", ve.getValue());
                vals.add(vc);
            }
            pc.put("values", vals);
            players.add(pc);
        }
        t.put("players", players);
        return t;
    }
}
