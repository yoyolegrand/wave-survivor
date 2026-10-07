package com.wavesurvivor.horde.editor;

import com.wavesurvivor.network.HordeCollabPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * ÉDITION À PLUSIEURS d'une même horde (côté serveur).
 *  - Chaque joueur dans l'éditeur occupe UN onglet de SA horde ; deux joueurs ne peuvent pas être sur le même onglet
 *    (sauf Aperçu, en lecture seule). Le premier arrivé le garde jusqu'à ce qu'il en sorte.
 *  - Un enregistrement à plusieurs ne porte que sur les onglets modifiés non occupés par un autre (HordeSections).
 *  - Le verrou disparaît à la fermeture de l'éditeur, à la déconnexion, ou après 5 min sans signe de vie (plantage).
 */
public final class HordeEditSessions {

    private HordeEditSessions() {}

    private static final long TIMEOUT_MS = 5 * 60 * 1000L;

    private static final class Sess {
        final UUID player;
        final String name;
        int tab;
        long last;
        Sess(UUID player, String name, int tab) { this.player = player; this.name = name; this.tab = tab; this.last = System.currentTimeMillis(); }
    }

    /** Horde (nom en minuscules) → éditeurs (ordre d'arrivée). */
    private static final Map<String, Map<UUID, Sess>> BY_HORDE = new HashMap<>();

    private static String key(String horde) { return horde == null ? "" : horde.trim().toLowerCase(Locale.ROOT); }

    // ─── Présence ───

    public static void presence(ServerPlayer p, String horde, int tab, boolean open) {
        String k = key(horde);
        UUID id = p.getUUID();
        // Un joueur n'édite qu'une horde à la fois : retiré des autres
        for (Map.Entry<String, Map<UUID, Sess>> en : new ArrayList<>(BY_HORDE.entrySet())) {
            if (en.getKey().equals(k) && open) continue;
            if (en.getValue().remove(id) != null) {
                if (en.getValue().isEmpty()) BY_HORDE.remove(en.getKey());
                broadcast(en.getKey(), null);
            }
        }
        if (!open || k.isEmpty()) return;
        Map<UUID, Sess> m = BY_HORDE.computeIfAbsent(k, x -> new LinkedHashMap<>());
        Sess s = m.get(id);
        boolean denied = false;
        if (tab != HordeSections.T_PREVIEW && holder(k, tab, id) != null) {
            tab = HordeSections.T_PREVIEW; // onglet déjà occupé : renvoyé sur l'Aperçu
            denied = true;
        }
        if (s != null && s.tab == tab && !denied) { s.last = System.currentTimeMillis(); return; } // simple signe de vie
        if (s == null) { s = new Sess(id, p.getGameProfile().getName(), tab); m.put(id, s); }
        s.tab = tab;
        s.last = System.currentTimeMillis();
        broadcast(k, denied ? id : null);
    }

    /** Joueur (autre que {@code except}) occupant cet onglet de la horde, ou null. */
    private static Sess holder(String k, int tab, UUID except) {
        Map<UUID, Sess> m = BY_HORDE.get(k);
        if (m == null) return null;
        for (Sess s : m.values()) if (!s.player.equals(except) && s.tab == tab) return s;
        return null;
    }

    /** Vrai si d'autres joueurs éditent la même horde. */
    public static boolean othersEditing(ServerPlayer p, String horde) {
        Map<UUID, Sess> m = BY_HORDE.get(key(horde));
        if (m == null) return false;
        for (UUID u : m.keySet()) if (!u.equals(p.getUUID())) return true;
        return false;
    }

    /** Masque réduit aux onglets qui ne sont pas occupés par un autre joueur. */
    public static int allowedMask(ServerPlayer p, String horde, int mask) {
        String k = key(horde);
        int out = mask;
        for (int t = 0; t < HordeSections.TAB_COUNT; t++) {
            if (HordeSections.has(out, t) && holder(k, t, p.getUUID()) != null) out &= ~HordeSections.bit(t);
        }
        return out;
    }

    /** Horde renommée (enregistrement en solo) : les sessions suivent. */
    public static void rename(String oldName, String newName) {
        String o = key(oldName), n = key(newName);
        if (o.isEmpty() || o.equals(n)) return;
        Map<UUID, Sess> m = BY_HORDE.remove(o);
        if (m != null) BY_HORDE.put(n, m);
    }

    // ─── Diffusion ───

    private static MinecraftServer server() { return ServerLifecycleHooks.getCurrentServer(); }

    private static void broadcast(String k, UUID deniedFor) {
        MinecraftServer srv = server();
        Map<UUID, Sess> m = BY_HORDE.get(k);
        if (srv == null || m == null) return;
        for (Sess s : m.values()) {
            ServerPlayer pl = srv.getPlayerList().getPlayer(s.player);
            if (pl == null) continue;
            List<HordeCollabPackets.Lock> locks = new ArrayList<>();
            for (Sess o : m.values()) {
                if (o.player.equals(s.player) || o.tab == HordeSections.T_PREVIEW) continue;
                locks.add(new HordeCollabPackets.Lock(o.tab, o.name));
            }
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pl),
                    new HordeCollabPackets.Locks(k, m.size(), s.player.equals(deniedFor), locks));
        }
    }

    /** Après un enregistrement : les autres éditeurs de la horde reçoivent les onglets enregistrés. */
    public static void broadcastUpdate(ServerPlayer saver, String horde, int mask, String json, String altarJson) {
        MinecraftServer srv = server();
        Map<UUID, Sess> m = BY_HORDE.get(key(horde));
        if (srv == null || m == null) return;
        for (Sess s : m.values()) {
            if (s.player.equals(saver.getUUID())) continue;
            ServerPlayer pl = srv.getPlayerList().getPlayer(s.player);
            if (pl == null) continue;
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pl),
                    new HordeCollabPackets.SectionUpdate(horde, saver.getGameProfile().getName(), mask, json, altarJson));
        }
    }

    private static void drop(UUID id) {
        for (Map.Entry<String, Map<UUID, Sess>> en : new ArrayList<>(BY_HORDE.entrySet())) {
            if (en.getValue().remove(id) != null) {
                if (en.getValue().isEmpty()) BY_HORDE.remove(en.getKey());
                else broadcast(en.getKey(), null);
            }
        }
    }

    private static void expire() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Map<UUID, Sess>> en : new ArrayList<>(BY_HORDE.entrySet())) {
            boolean changed = false;
            for (Iterator<Sess> it = en.getValue().values().iterator(); it.hasNext(); ) {
                if (now - it.next().last > TIMEOUT_MS) { it.remove(); changed = true; }
            }
            if (en.getValue().isEmpty()) BY_HORDE.remove(en.getKey());
            else if (changed) broadcast(en.getKey(), null);
        }
    }

    // ─── Événements (noms uniques : piège de l'event bus Forge) ───

    public static class Events {
        private int ticks = 0;

        @SubscribeEvent
        public void hordeCollabTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || BY_HORDE.isEmpty()) return;
            if (++ticks % 100 == 0) expire();
        }

        @SubscribeEvent
        public void hordeCollabLogout(PlayerEvent.PlayerLoggedOutEvent e) {
            if (!BY_HORDE.isEmpty()) drop(e.getEntity().getUUID());
        }
    }
}
