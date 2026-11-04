package com.wavesurvivor.carnet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ÉTAT DU CARNET PAR JOUEUR (sauvegardé avec le monde) : carnet déjà remis, compteurs au moment de la remise
 * (seule la progression faite ensuite compte : pas de récompenses rétroactives) et objectifs validés.
 */
public class CarnetData extends SavedData {

    private static final String NAME = "wavesurvivor_carnet";

    public static class Entry {
        public boolean given = false;
        public final Map<String, Long> base = new HashMap<>();
        public final Set<String> done = new HashSet<>();
    }

    private final Map<UUID, Entry> data = new HashMap<>();

    public static CarnetData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CarnetData::load, CarnetData::new, NAME);
    }

    /** Entrée du joueur, ou null s'il n'a jamais reçu de carnet. */
    public Entry find(UUID id) { return data.get(id); }

    public Entry getOrCreate(UUID id) { return data.computeIfAbsent(id, k -> new Entry()); }

    static CarnetData load(CompoundTag tag) {
        CarnetData d = new CarnetData();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag pc = players.getCompound(i);
            Entry e = new Entry();
            e.given = pc.getBoolean("given");
            CompoundTag base = pc.getCompound("base");
            for (String k : base.getAllKeys()) e.base.put(k, base.getLong(k));
            for (Tag t : pc.getList("done", Tag.TAG_STRING)) e.done.add(t.getAsString());
            d.data.put(pc.getUUID("id"), e);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Entry> en : data.entrySet()) {
            CompoundTag pc = new CompoundTag();
            pc.putUUID("id", en.getKey());
            pc.putBoolean("given", en.getValue().given);
            CompoundTag base = new CompoundTag();
            for (Map.Entry<String, Long> b : en.getValue().base.entrySet()) base.putLong(b.getKey(), b.getValue());
            pc.put("base", base);
            ListTag done = new ListTag();
            for (String s : en.getValue().done) done.add(StringTag.valueOf(s));
            pc.put("done", done);
            players.add(pc);
        }
        tag.put("players", players);
        return tag;
    }
}
