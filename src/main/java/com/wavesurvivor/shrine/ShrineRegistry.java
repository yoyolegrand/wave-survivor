package com.wavesurvivor.shrine;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Statues du Séraphin actuellement chargées (côté serveur) : sert à trouver celles qui sont proches d'un événement. */
public final class ShrineRegistry {

    private static final Set<SeraphShrineBlockEntity> LOADED = ConcurrentHashMap.newKeySet();

    private ShrineRegistry() {}

    public static void add(SeraphShrineBlockEntity be) { LOADED.add(be); }

    public static void remove(SeraphShrineBlockEntity be) { LOADED.remove(be); }

    public static void clear() { LOADED.clear(); }

    /** Statues du même monde à moins de {@code radius} blocs de ce point. */
    public static List<SeraphShrineBlockEntity> nearby(ServerLevel level, Vec3 pos, double radius) {
        List<SeraphShrineBlockEntity> out = new ArrayList<>();
        double r2 = radius * radius;
        for (SeraphShrineBlockEntity be : LOADED) {
            if (be.getLevel() != level || be.isRemoved()) continue;
            if (be.getBlockPos().getCenter().distanceToSqr(pos) <= r2) out.add(be);
        }
        return out;
    }
}
