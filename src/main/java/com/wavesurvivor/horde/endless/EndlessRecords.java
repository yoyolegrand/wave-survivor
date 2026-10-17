package com.wavesurvivor.horde.endless;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CLASSEMENT DU MODE SANS FIN (sauvegardé avec le monde) : meilleurs scores par horde.
 * Score = nombre de vagues terminées ; à égalité, la partie la plus rapide passe devant.
 * La taille du classement se règle par horde dans l'éditeur (topSize).
 */
public class EndlessRecords extends SavedData {

    private static final String NAME = "wavesurvivor_endless";

    /** Un score : joueurs présents (noms séparés par des virgules), vagues terminées, durée en ticks. */
    public record Entry(String players, int waves, long ticks) {}

    private final Map<String, List<Entry>> data = new LinkedHashMap<>();

    public static EndlessRecords get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(EndlessRecords::load, EndlessRecords::new, NAME);
    }

    /** Ajoute un score ; renvoie son rang (1 = record) ou -1 s'il n'entre pas dans le classement. */
    public int submit(String hordeName, String players, int waves, long ticks, int topSize) {
        String key = hordeName == null ? "?" : hordeName.trim();
        List<Entry> list = data.computeIfAbsent(key, k -> new ArrayList<>());
        Entry e = new Entry(players, waves, ticks);
        list.add(e);
        list.sort((a, b) -> a.waves() != b.waves() ? Integer.compare(b.waves(), a.waves()) : Long.compare(a.ticks(), b.ticks()));
        int rank = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i) == e) rank = i + 1;
        while (list.size() > Math.max(1, topSize)) list.remove(list.size() - 1);
        if (rank > list.size()) rank = -1;
        setDirty();
        return rank;
    }

    public List<Entry> top(String hordeName) {
        return data.getOrDefault(hordeName == null ? "?" : hordeName.trim(), List.of());
    }

    public Set<String> keys() {
        return data.keySet();
    }

    // ─── Sauvegarde NBT ───

    private static EndlessRecords load(CompoundTag t) {
        EndlessRecords r = new EndlessRecords();
        ListTag all = t.getList("records", Tag.TAG_COMPOUND);
        for (int i = 0; i < all.size(); i++) {
            CompoundTag c = all.getCompound(i);
            List<Entry> list = new ArrayList<>();
            ListTag es = c.getList("entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < es.size(); j++) {
                CompoundTag e = es.getCompound(j);
                list.add(new Entry(e.getString("players"), e.getInt("waves"), e.getLong("ticks")));
            }
            r.data.put(c.getString("key"), list);
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag all = new ListTag();
        for (Map.Entry<String, List<Entry>> me : data.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("key", me.getKey());
            ListTag es = new ListTag();
            for (Entry e : me.getValue()) {
                CompoundTag ec = new CompoundTag();
                ec.putString("players", e.players());
                ec.putInt("waves", e.waves());
                ec.putLong("ticks", e.ticks());
                es.add(ec);
            }
            c.put("entries", es);
            all.add(c);
        }
        t.put("records", all);
        return t;
    }
}
