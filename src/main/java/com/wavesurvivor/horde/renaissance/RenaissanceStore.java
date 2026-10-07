package com.wavesurvivor.horde.renaissance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistance Renaissance dans le NBT joueur, sous "PlayerPersisted"
 * (sous-tag que Forge recopie au respawn → les PR survivent à la mort).
 *
 *   PlayerPersisted.ws_renaissance.points          : int
 *   PlayerPersisted.ws_renaissance.totalEarned     : int (stat)
 *   PlayerPersisted.ws_renaissance.purchases.<id>  : int (nb achats par relique)
 */
public class RenaissanceStore {

    private static final String PERSISTED = "PlayerPersisted";
    private static final String KEY = "ws_renaissance";

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        CompoundTag persisted = root.getCompound(PERSISTED);
        CompoundTag d = persisted.getCompound(KEY);
        persisted.put(KEY, d);
        root.put(PERSISTED, persisted);
        return d;
    }

    public static int getPoints(Player p) {
        return data(p).getInt("points");
    }

    public static void setPoints(Player p, int value) {
        data(p).putInt("points", Math.max(0, value));
    }

    public static void addPoints(Player p, int delta) {
        CompoundTag d = data(p);
        d.putInt("points", Math.max(0, d.getInt("points") + delta));
        if (delta > 0) d.putInt("totalEarned", d.getInt("totalEarned") + delta);
    }

    public static int getTotalEarned(Player p) {
        return data(p).getInt("totalEarned");
    }

    public static int getPurchases(Player p, String shopId) {
        return data(p).getCompound("purchases").getInt(shopId);
    }

    public static void addPurchases(Player p, String shopId, int n) {
        CompoundTag d = data(p);
        CompoundTag pur = d.getCompound("purchases");
        pur.putInt(shopId, pur.getInt(shopId) + n);
        d.put("purchases", pur);
    }

    public static Map<String, Integer> getAllPurchases(Player p) {
        Map<String, Integer> out = new HashMap<>();
        CompoundTag pur = data(p).getCompound("purchases");
        for (String k : pur.getAllKeys()) out.put(k, pur.getInt(k));
        return out;
    }

    // ─── Stats permanentes (niveaux) ───

    public static int getStatLevel(Player p, String statId) {
        return data(p).getCompound("stats").getInt(statId);
    }

    public static void setStatLevel(Player p, String statId, int level) {
        CompoundTag d = data(p);
        CompoundTag stats = d.getCompound("stats");
        if (level > 0) stats.putInt(statId, level);
        else stats.remove(statId);
        d.put("stats", stats);
    }

    public static Map<String, Integer> getAllStatLevels(Player p) {
        Map<String, Integer> out = new HashMap<>();
        CompoundTag stats = data(p).getCompound("stats");
        for (String k : stats.getAllKeys()) out.put(k, stats.getInt(k));
        return out;
    }

    public static void clearStats(Player p) {
        data(p).remove("stats");
    }

    /** Remet à zéro les compteurs d'achats de la boutique (limites par joueur). */
    public static void clearPurchases(Player p) {
        data(p).remove("purchases");
    }
}
