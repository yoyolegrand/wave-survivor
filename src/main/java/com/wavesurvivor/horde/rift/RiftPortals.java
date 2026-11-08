package com.wavesurvivor.horde.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * 2.0 — Mémoire des portails de Faille déjà « invoqués » (un par autel) : le rocher reste en place d'une partie à l'autre,
 * l'animation d'ouverture complète n'est jouée que la première fois. Stockée dans le monde principal.
 */
public final class RiftPortals extends SavedData {

    /** Un portail invoqué : dimension, autel d'origine, pied du rocher et direction de la façade (vers l'autel). */
    public record Rec(String dim, BlockPos altar, BlockPos origin, int fx, int fz) {}

    private final List<Rec> list = new ArrayList<>();

    public static RiftPortals get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RiftPortals::load, RiftPortals::new, "wavesurvivor_rift_portals");
    }

    public static Rec find(MinecraftServer server, ServerLevel level, BlockPos altar) {
        String dim = level.dimension().location().toString();
        for (Rec r : get(server).list) if (r.dim.equals(dim) && r.altar.equals(altar)) return r;
        return null;
    }

    public static void add(MinecraftServer server, Rec rec) {
        RiftPortals d = get(server);
        d.list.add(rec);
        d.setDirty();
    }

    public static List<Rec> all(MinecraftServer server) {
        return new ArrayList<>(get(server).list);
    }

    private static RiftPortals load(CompoundTag tag) {
        RiftPortals d = new RiftPortals();
        ListTag l = tag.getList("portals", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            d.list.add(new Rec(c.getString("dim"), BlockPos.of(c.getLong("altar")), BlockPos.of(c.getLong("origin")), c.getInt("fx"), c.getInt("fz")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Rec r : list) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", r.dim);
            c.putLong("altar", r.altar.asLong());
            c.putLong("origin", r.origin.asLong());
            c.putInt("fx", r.fx);
            c.putInt("fz", r.fz);
            l.add(c);
        }
        tag.put("portals", l);
        return tag;
    }
}
