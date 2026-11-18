package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.ready.ReadyManager;
import com.wavesurvivor.horde.stats.PlayerStats;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.EventFeedPacket;
import com.wavesurvivor.network.KingdomTutoPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 2.1 — OPTION « TUTO » DU KINGDOM (docs/TUTO_KINGDOM.md). Case « Tuto » cochée dans l'éditeur : un joueur qui lance ce Kingdom pour
 * la première fois (clé PlayerStats {@code kingdom_tuto_done:<horde>} absente) est accompagné pendant toute la partie :
 * écran du cycle au lancement, bandeau + teinte à chaque phase, liste « À faire » à droite (calculée ici, envoyée toutes les 2 s),
 * conseils dans le bilan d'Assaut, astuces au premier contact (clé {@code tip:<id>}, une fois par joueur pour tous les Kingdoms)
 * et premier Calme sans chrono (l'Assaut part quand tout le monde est Prêt).
 * Les aides ne vont qu'aux joueurs concernés ; l'option client {@code kingdomTutorial: false} les coupe (signalée à la connexion).
 */
public final class KingdomTuto {

    public static final String DONE = "kingdom_tuto_done:", TIP = "tip:";
    /** Clé de la pause « Prêt » du Kingdom (moteur commun ReadyManager). */
    public static final Object READY_KEY = new Object();
    /** Astuces au premier contact et page du Codex associée. */
    public static final Map<String, String> TIPS = new LinkedHashMap<>();
    static {
        TIPS.put("first_calm", "kingdom");
        TIPS.put("first_assault", "kingdom");
        TIPS.put("gate_shield", "objectives");
        TIPS.put("catalyst", "objectives");
        TIPS.put("small_breach", "kingdom");
        TIPS.put("building_damaged", "defenses");
        TIPS.put("treasury", "treasury");
        TIPS.put("gate_destroyed", "objectives");
        TIPS.put("death", "kingdom");
        TIPS.put("omen", "omens");
    }

    private static boolean on;
    private static String horde = "";
    private static boolean gentle;
    private static HordeConfigMultiData.ReadySettings readyCfg;
    /** Joueurs ayant coupé le tuto dans leurs options (client_options.json). */
    private static final Set<UUID> OPT_OUT = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Astuces déjà diffusées pendant cette partie (évite de reparcourir les joueurs à chaque gain du trésor…). */
    private static final Set<String> TIPPED = new HashSet<>();
    /** Liste « À faire » : lignes vues pendant la phase en cours (id → texte), pour les cocher quand elles sont faites. */
    private static final Map<UUID, LinkedHashMap<String, String>> SEEN = new HashMap<>();

    // Bilan : ce qui a manqué pendant l'Assaut
    private static final Map<Integer, Integer> REACHED = new HashMap<>();   // n° de Porte (ordre de gates()) → unités arrivées au Monolithe
    private static final Set<UUID> REACHED_IDS = new HashSet<>();
    private static float monoStart = -1;
    private static boolean catalystMissed;

    private KingdomTuto() {}

    // ─── Joueurs concernés ───

    public static boolean active() { return on; }

    /** Le joueur est-il accompagné dans cette partie ? */
    public static boolean inTuto(ServerPlayer p) {
        if (!on || p == null || OPT_OUT.contains(p.getUUID())) return false;
        return PlayerStats.snapshotOf(p).getOrDefault(DONE + horde, 0L) <= 0;
    }

    private static List<ServerPlayer> players() {
        ServerLevel l = KingdomManager.level();
        List<ServerPlayer> out = new ArrayList<>();
        if (l != null) for (ServerPlayer p : l.players()) if (!p.isSpectator()) out.add(p);
        return out;
    }

    private static List<ServerPlayer> tutoPlayers() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : players()) if (inTuto(p)) out.add(p);
        return out;
    }

    /** Option client : le joueur coupe (ou réactive) complètement l'accompagnement. */
    public static void setOptOut(ServerPlayer p, boolean out) {
        if (out) OPT_OUT.add(p.getUUID());
        else OPT_OUT.remove(p.getUUID());
        if (out) send(p, KingdomTutoPackets.Hud.off());
    }

    // ─── Début / fin de partie ───

    /**
     * Lancement du Kingdom (premier Calme, cycle 0). @return la fin du premier Calme : sans chrono (au plus tutorialGentleMaxSeconds)
     * si le démarrage en douceur s'applique, sinon {@code defaultEnd}.
     */
    public static long onStart(MinecraftServer server, HordeConfigMultiData h, long now, long defaultEnd) {
        clear();
        HordeConfigMultiData.KingdomSettings k = h.configData.kingdom();
        on = k != null && k.tutorial && !com.wavesurvivor.horde.bossrush.BossRush.active()
                && !com.wavesurvivor.horde.rift.RiftManager.kingdomSiege();
        if (!on) return defaultEnd;
        horde = h.hordeName == null ? "" : h.hordeName;
        readyCfg = h.configData.ready;
        List<ServerPlayer> news = tutoPlayers();
        if (news.isEmpty()) { clear(); return defaultEnd; }   // personne de nouveau : rien ne change (pas de « Prêt » ajouté)
        gentle = k.tutorialGentleStart;
        long end = gentle ? now + Math.max(60, k.tutorialGentleMaxSeconds) * 20L : defaultEnd;
        for (ServerPlayer p : news) {
            send(p, KingdomTutoPackets.Hud.cycleScreen());
            send(p, KingdomTutoPackets.Hud.banner(false, gentle ? -1 : (int) ((end - now) / 20)));
            p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.MASTER, 1f, 0.6f);   // cloche grave (le Calme de lancement n'en a pas)
            tip(p, "first_calm");
        }
        if (gentle) beginReady(now, end, true);
        WaveSurvivorMod.LOGGER.info("[Kingdom] Tuto actif : {} joueur(s) accompagné(s){}", news.size(), gentle ? ", premier Calme sans chrono" : "");
        return end;
    }

    /** Fin de partie (victoire, défaite, arrêt) : la clé « déjà fait » est posée si au moins un Assaut a eu lieu. */
    public static void onEnd(MinecraftServer server, int cycle) {
        ReadyManager.end(READY_KEY);
        if (!on) { clear(); return; }
        for (ServerPlayer p : players()) {
            send(p, KingdomTutoPackets.Hud.off());
            if (cycle >= 1 && PlayerStats.snapshotOf(p).getOrDefault(DONE + horde, 0L) <= 0) PlayerStats.add(p, DONE + horde, 1);
        }
        clear();
    }

    private static void clear() {
        on = false;
        gentle = false;
        horde = "";
        readyCfg = null;
        TIPPED.clear();
        SEEN.clear();
        REACHED.clear();
        REACHED_IDS.clear();
        monoStart = -1;
        catalystMissed = false;
    }

    // ─── Phases ───

    /** Début d'un Calme (cycle ≥ 1). */
    public static void onCalm(long now, long phaseEnd, int seconds) {
        if (!on) return;
        SEEN.clear();
        for (ServerPlayer p : tutoPlayers()) {
            send(p, KingdomTutoPackets.Hud.banner(false, seconds));
            tip(p, "first_calm");
        }
        beginReady(now, phaseEnd, false);
    }

    /** Début d'un Assaut : le Calme du chrono doux est fini ; on note ce qu'il faudra commenter au bilan. */
    public static void onAssault(int catalystsLeft) {
        ReadyManager.end(READY_KEY);
        if (!on) return;
        gentle = false;
        SEEN.clear();
        REACHED.clear();
        REACHED_IDS.clear();
        catalystMissed = catalystsLeft > 0;
        float[] m = com.wavesurvivor.altar.AltarDefense.snapshot();
        monoStart = m == null ? -1 : m[0];
        for (ServerPlayer p : tutoPlayers()) {
            send(p, KingdomTutoPackets.Hud.banner(true, 0));
            tip(p, "first_assault");
        }
    }

    /** Pause « Prêt » du Calme (parties avec tuto) : le premier Calme doux s'arrête au Prêt de tous, sans bonus. */
    private static void beginReady(long now, long end, boolean gentleCalm) {
        HordeConfigMultiData.ReadySettings s = readyCfg;
        if (gentleCalm) {
            s = new HordeConfigMultiData.ReadySettings();
            s.allow = true;
            s.amount = 0;
        }
        if (s == null || !s.allow) return;
        ReadyManager.begin(READY_KEY, new ReadyManager.Pause() {
            @Override public List<ServerPlayer> players() { return KingdomTuto.players(); }
            @Override public void skip() { KingdomManager.skipCalm(); }
        }, s, now, end);
    }

    public static boolean gentle() { return on && gentle; }

    // ─── Tick : liste « À faire » et unités arrivées au Monolithe ───

    public static void tick(MinecraftServer server, long now) {
        if (!on) return;
        ServerLevel l = KingdomManager.level();
        if (l == null) return;
        if (KingdomManager.phase() == KingdomManager.Phase.ASSAULT && now % 10 == 0) trackReached(l);
        if (now % 40 != 0) return;
        for (ServerPlayer p : tutoPlayers()) send(p, todo(p, l, now));
    }

    /** Unités de la horde arrivées près du Monolithe : comptées par Porte (la Porte dans leur direction). */
    private static void trackReached(ServerLevel l) {
        BlockPos c = KingdomManager.center();
        if (c == null) return;
        List<KingdomManager.GateView> gates = KingdomManager.gates();
        if (gates.isEmpty()) return;
        for (var en : com.wavesurvivor.horde.spawn.MobRegistry.entries()) {
            UUID id = en.getKey();
            if (REACHED_IDS.contains(id)) continue;
            Entity e = l.getEntity(id);
            if (e == null || !e.isAlive() || e.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) > 64) continue;
            REACHED_IDS.add(id);
            double a = Math.atan2(e.getZ() - c.getZ(), e.getX() - c.getX());
            int best = 0;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < gates.size(); i++) {
                BlockPos g = gates.get(i).pos();
                double d = Math.abs(net.minecraft.util.Mth.wrapDegrees(Math.toDegrees(Math.atan2(g.getZ() - c.getZ(), g.getX() - c.getX()) - a)));
                if (d < bd) { bd = d; best = i; }
            }
            REACHED.merge(best, 1, Integer::sum);
        }
    }

    private static KingdomTutoPackets.Hud todo(ServerPlayer p, ServerLevel l, long now) {
        List<KingdomTutoPackets.Line> lines = new ArrayList<>();
        BlockPos c = KingdomManager.center();
        List<KingdomManager.GateView> gates = KingdomManager.gates();
        int gatesTotal = gates.size(), gatesLeft = 0;
        for (var g : gates) if (!g.destroyed()) gatesLeft++;
        String header = null;
        if (KingdomManager.phase() == KingdomManager.Phase.ASSAULT) {
            float[] m = com.wavesurvivor.altar.AltarDefense.snapshot();
            if (m != null) lines.add(line(WSLang.t("kingdom.tuto.todo.monolith", Math.round(m[0]), Math.round(m[1])), null, false));
            lines.add(line(WSLang.t("kingdom.tuto.todo.gates", gatesLeft, gatesTotal), null, false));
            lines.add(line(WSLang.t("kingdom.tuto.todo.units", com.wavesurvivor.horde.spawn.MobRegistry.size()), null, false));
            Entity threat = nearestThreat(l, c);
            if (threat != null) lines.add(line(WSLang.t("kingdom.tuto.todo.threat",
                    KingdomObjectives.dir8(threat.getX(), threat.getZ())), threat.blockPosition(), false));
        } else {
            if (gentle) header = WSLang.t("kingdom.tuto.todo.gentle");
            Map<String, KingdomTutoPackets.Line> cur = new LinkedHashMap<>();
            // Événement temporaire du Calme (convoi, champion, trésor, foyers) : en tête, il a un chrono
            KingdomObjectives.TutoView ov = KingdomObjectives.tutoView(p, now);
            if (ov != null) {
                String t = switch (ov.type()) {
                    case CONVOY -> WSLang.t("kingdom.tuto.todo.obj.convoy", ov.a(), ov.secs());
                    case CHAMPION -> WSLang.t("kingdom.tuto.todo.obj.champion", ov.secs());
                    case TREASURE -> WSLang.t("kingdom.tuto.todo.obj.treasure", ov.secs());
                    case PURIFY -> WSLang.t("kingdom.tuto.todo.obj.purify", ov.a(), ov.b(), ov.secs());
                };
                cur.put("obj_" + ov.type().name().toLowerCase(), line(t, ov.target(), false));
            }
            // Ouvrir une Porte, dans l'ordre : 1. fermer le catalyseur 2. tuer ses Gardiens 3. frapper la Porte
            Entity cat = nearest(p, KingdomManager.catalysts());
            if (cat != null) cur.put("catalyst", line(WSLang.t("kingdom.tuto.todo.catalyst"), cat.blockPosition(), false));
            for (var g : gates) {
                if (g.destroyed() || !g.breached()) continue;
                String dir = WSLang.t("kingdom.dir." + g.dir());
                if (g.guards() > 0 && g.guardPos() != null)
                    cur.put("guards_" + g.dir(), line(WSLang.t("kingdom.tuto.todo.guards", dir, g.guards()), g.guardPos(), false));
                else if (!g.shielded())
                    cur.put("gate_" + g.dir(), line(WSLang.t("kingdom.tuto.todo.gate", dir), g.pos(), false));
            }
            int[] rq = KingdomDefenses.repairQuote(p);
            if (rq[0] > 0) cur.put("repair", line(WSLang.t("kingdom.tuto.todo.repair", rq[0], rq[1]), null, false));
            int money = KingdomTreasury.get(KingdomTreasury.Res.MONEY);
            if (money >= KingdomDefenses.cheapestBuy()) cur.put("spend", line(WSLang.t("kingdom.tuto.todo.spend", money), null, false));
            Entity dep = nearest(p, new ArrayList<>(com.wavesurvivor.horde.chaos.GisementManager.deposits(l.getServer())));
            if (dep != null) cur.put("deposit", line(WSLang.t("kingdom.tuto.todo.deposit"), dep.blockPosition(), false));
            if (KingdomRoles.has(p, KingdomRoles.Role.AUGUR) && !KingdomOmens.options().isEmpty() && KingdomOmens.chosen() == null)
                cur.put("omen", line(WSLang.t("kingdom.tuto.todo.omen"), null, false));
            ReadyManager.Status st = ReadyManager.status(p, now);
            // « Prêt » : cochée quand le joueur l'a fait, et toujours en dernier
            if (st != null && !st.ready()) cur.put("ready", line(WSLang.t("kingdom.tuto.todo.ready", st.n(), st.total()), null, false));
            // Lignes déjà vues pendant ce Calme et qui ne s'appliquent plus : faites (cochées)
            LinkedHashMap<String, String> seen = SEEN.computeIfAbsent(p.getUUID(), k -> new LinkedHashMap<>());
            for (var en : cur.entrySet()) seen.put(en.getKey(), en.getValue().text());
            List<KingdomTutoPackets.Line> done = new ArrayList<>();
            for (var en : seen.entrySet()) if (!cur.containsKey(en.getKey())) done.add(line(en.getValue(), null, true));
            KingdomTutoPackets.Line ready = cur.remove("ready");
            if (st != null && st.ready() && !seen.containsKey("ready")) done.add(line(WSLang.t("kingdom.tuto.todo.ready_done"), null, true));
            for (KingdomTutoPackets.Line ln : cur.values()) if (lines.size() < 4) lines.add(ln);
            for (KingdomTutoPackets.Line ln : done) if (lines.size() < 4) lines.add(ln);
            if (ready != null) lines.add(ready);
        }
        return KingdomTutoPackets.Hud.todo(header, lines, WSLang.t("kingdom.tuto.todo.goal", gatesTotal));
    }

    private static KingdomTutoPackets.Line line(String text, BlockPos target, boolean done) {
        return new KingdomTutoPackets.Line(text, done, target != null, target == null ? 0 : target.getX() + 0.5, target == null ? 0 : target.getZ() + 0.5);
    }

    private static Entity nearest(ServerPlayer p, List<? extends Entity> list) {
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : list) {
            if (e == null || !e.isAlive() || e.level() != p.level()) continue;
            double d = e.distanceToSqr(p);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    /** Monstre de la horde le plus proche du Monolithe. */
    private static Entity nearestThreat(ServerLevel l, BlockPos c) {
        if (c == null) return null;
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (var en : com.wavesurvivor.horde.spawn.MobRegistry.entries()) {
            Entity e = l.getEntity(en.getKey());
            if (e == null || !e.isAlive()) continue;
            double d = e.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    // ─── Conseils du bilan d'Assaut ───

    /**
     * 1 ou 2 conseils (le plus pertinent d'abord), envoyés seulement aux joueurs en tuto.
     * @param structLost bâtiments détruits pendant l'Assaut ; playerShare part des éliminations faites par les joueurs (0..1)
     */
    static void advice(int structLost, double playerShare, int totalKills) {
        if (!on) return;
        List<Component> out = new ArrayList<>();
        List<KingdomManager.GateView> gates = KingdomManager.gates();
        int bestGate = -1, bestN = 0;
        for (var en : REACHED.entrySet()) if (en.getValue() > bestN) { bestN = en.getValue(); bestGate = en.getKey(); }
        if (bestGate >= 0 && bestGate < gates.size() && bestN >= 3)
            out.add(WSLang.c("kingdom.advice.gate", WSLang.t("kingdom.dir." + gates.get(bestGate).dir())));
        float[] m = com.wavesurvivor.altar.AltarDefense.snapshot();
        if (m != null && monoStart > 0 && (monoStart - m[0]) > m[1] * 0.30f) out.add(WSLang.c("kingdom.advice.monolith"));
        if (structLost > 0) out.add(WSLang.c("kingdom.advice.buildings", structLost));
        if (totalKills >= 10 && playerShare > 0.70) out.add(WSLang.c("kingdom.advice.defenses"));
        int money = KingdomTreasury.get(KingdomTreasury.Res.MONEY);
        if (money >= 100) out.add(WSLang.c("kingdom.advice.money", money));
        if (catalystMissed) out.add(WSLang.c("kingdom.advice.catalyst"));
        if (out.isEmpty()) return;
        List<Component> top = out.subList(0, Math.min(2, out.size()));
        for (ServerPlayer p : tutoPlayers()) {
            p.sendSystemMessage(WSLang.c("kingdom.advice.head"));
            for (Component c : top) p.sendSystemMessage(c);
        }
    }

    // ─── Astuces au premier contact ───

    /** Astuce pour un joueur (une seule fois par joueur, tous Kingdoms confondus). */
    public static void tip(ServerPlayer p, String id) {
        if (!on || p == null || !TIPS.containsKey(id) || !inTuto(p)) return;
        if (PlayerStats.snapshotOf(p).getOrDefault(TIP + id, 0L) > 0) return;
        PlayerStats.add(p, TIP + id, 1);
        Component text = Component.literal("§e💡 " + WSLang.t("kingdom.tip." + id) + "\n§8" + WSLang.t("kingdom.tip.codex", WSLang.t("codex." + TIPS.get(id))));
        EventFeedPacket.toPlayer(p, text, "minecraft:writable_book", EventFeedPacket.BLESSING, true);
        p.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.MASTER, 1f, 1f);
    }

    /** Astuce pour tous les joueurs en tuto (un seul passage par partie). */
    public static void tipAll(String id) {
        if (!on || !TIPPED.add(id)) return;
        for (ServerPlayer p : tutoPlayers()) tip(p, id);
    }

    // ─── Remise à zéro (/ws tuto reset, bouton du Codex) ───

    /** Efface la clé « déjà fait » (d'un Kingdom, ou de tous si horde est vide) et les astuces vues. @return clés effacées. */
    public static int reset(ServerPlayer p, String hordeName) {
        if (p == null || p.getServer() == null) return 0;
        int n = PlayerStats.removeKeys(p, k -> (hordeName == null || hordeName.isEmpty() ? k.startsWith(DONE) : k.equals(DONE + hordeName)) || k.startsWith(TIP));
        if (on && inTuto(p)) send(p, KingdomTutoPackets.Hud.cycleScreen());
        return n;
    }

    private static void send(ServerPlayer p, KingdomTutoPackets.Hud pkt) {
        if (p == null || NetworkHandler.CHANNEL == null) return;
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }

    // ─── Événements ───

    @Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID)
    public static final class Events {
        @SubscribeEvent
        public static void onDeath(LivingDeathEvent e) {
            if (on && e.getEntity() instanceof ServerPlayer p && p.level() == KingdomManager.level()) tip(p, "death");
        }

        @SubscribeEvent
        public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
            OPT_OUT.remove(e.getEntity().getUUID());
        }
    }
}
