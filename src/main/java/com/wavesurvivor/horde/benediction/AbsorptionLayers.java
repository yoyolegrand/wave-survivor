package com.wavesurvivor.horde.benediction;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2.0 — COUCHES D'ABSORPTION : cœurs d'absorption donnés par le mod SANS l'effet ABSORPTION vanilla (qui, à sa fin, retire son montant
 * même si ce sont d'autres cœurs qui restent). Chaque couche (Bouclier de Faille, Héritage Survivant 1…) s'AJOUTE à l'absorption actuelle
 * (dont celle de la bénédiction de l'Aube) et on mémorise ce qu'il en reste : les dégâts absorbés sont retirés des couches suivies d'abord,
 * dans l'ordre de {@link #PRIORITY}. Une couche qui expire, ou qui est redonnée (début de vague), ne retire que son propre reste : pas de cumul.
 */
public final class AbsorptionLayers {

    /** Bouclier de Faille (ensemble Faille 4 pièces), sans durée : remplacé au début de chaque vague. */
    public static final String RIFT_SHIELD = "rift_shield";
    /** Héritage Survivant 1 : 4 cœurs pendant 30 s au début de chaque vague. */
    public static final String HERITAGE = "heritage_s1";
    /** Ordre de consommation (le premier part en premier). */
    private static final String[] PRIORITY = {RIFT_SHIELD, HERITAGE};

    private AbsorptionLayers() {}

    private static final class State {
        /** Couche → [reste, tick de fin (-1 = sans fin)]. */
        final Map<String, double[]> layers = new LinkedHashMap<>();
        float lastSeen;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    /** Donne une couche (remplace celle du même nom : son reste est d'abord retiré). {@code ticks} ≤ 0 = sans fin. */
    public static void give(ServerPlayer p, String key, float amount, int ticks) {
        if (p == null || amount <= 0) return;
        remove(p, key);
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        float now = p.getAbsorptionAmount() + amount;
        p.setAbsorptionAmount(now);
        s.layers.put(key, new double[]{amount, ticks > 0 ? p.getServer().getTickCount() + ticks : -1});
        s.lastSeen = now;
    }

    /** Retire ce qui reste d'une couche (jamais plus que l'absorption actuelle). */
    public static void remove(ServerPlayer p, String key) {
        State s = p == null ? null : STATES.get(p.getUUID());
        if (s == null) return;
        sync(p, s);
        double[] l = s.layers.remove(key);
        if (l != null && l[0] > 0) {
            float cur = p.getAbsorptionAmount();
            p.setAbsorptionAmount((float) Math.max(0, cur - Math.min(l[0], cur)));
        }
        s.lastSeen = p.getAbsorptionAmount();
        if (s.layers.isEmpty()) STATES.remove(p.getUUID());
    }

    /** Début de vague, AVANT la bénédiction de l'Aube : les couches de vague précédentes sont retirées (pas de cumul). */
    public static void clearWaveLayers(MinecraftServer srv) {
        if (srv == null) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            remove(p, RIFT_SHIELD);
            remove(p, HERITAGE);
        }
    }

    /** Absorption perdue depuis le dernier passage : retirée des couches suivies, dans l'ordre de priorité. */
    private static void sync(ServerPlayer p, State s) {
        float cur = p.getAbsorptionAmount();
        if (cur < s.lastSeen) {
            double lost = s.lastSeen - cur;
            for (String k : PRIORITY) {
                double[] l = s.layers.get(k);
                if (l == null || lost <= 0) continue;
                double take = Math.min(l[0], lost);
                l[0] -= take;
                lost -= take;
            }
            for (double[] l : s.layers.values()) {   // couches hors de la liste de priorité
                if (lost <= 0) break;
                double take = Math.min(l[0], lost);
                l[0] -= take;
                lost -= take;
            }
        }
        s.lastSeen = cur;
    }

    /** À chaque tick du joueur : suivi des dégâts absorbés, couches épuisées ou expirées. */
    public static void tick(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        sync(p, s);
        long now = p.getServer().getTickCount();
        for (Map.Entry<String, double[]> e : new LinkedHashMap<>(s.layers).entrySet()) {
            if (e.getValue()[0] <= 0) s.layers.remove(e.getKey());
            else if (e.getValue()[1] > 0 && now >= e.getValue()[1]) remove(p, e.getKey());
        }
        if (s.layers.isEmpty()) STATES.remove(p.getUUID());
    }

    public static void forget(UUID id) { STATES.remove(id); }
}
