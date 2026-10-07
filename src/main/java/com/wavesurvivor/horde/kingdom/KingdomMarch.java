package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.spawn.MobRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MODE KINGDOM — étape 2 : LA MARCHE.
 *  - COULOIRS : un chemin de points de passage (tous les 8 blocs, à hauteur du terrain) par portail vers le Monolithe,
 *    calculé une seule fois au lancement.
 *  - ESCOUADES : les unités sont regroupées par squadSize ; seul le CHEF suit le couloir, les autres suivent le chef
 *    (×squadSize moins de calculs de trajet). Chef mort → le suivant prend le relais.
 *  - DÉCOINÇAGE : immobile 3 s → petit saut vers l'avant ; toujours bloqué → téléportation de 3 blocs le long du couloir.
 *  - IA ALLÉGÉE : pas de ramassage d'objets, trajet rafraîchi toutes les 0,5 s seulement.
 *  - APPROCHE FINALE : à engageRadius blocs du Monolithe ou d'un joueur, l'unité quitte l'escouade
 *    et repasse sur son IA de combat normale (gérée par KingdomManager.retarget).
 * Les unités sont prises en charge automatiquement dès qu'elles apparaissent dans MobRegistry
 * (portails comme petites brèches : couloir le plus proche).
 */
final class KingdomMarch {

    private static final class Squad {
        final int lane;
        UUID leader;
        int wp;
        /** Moment (temps du monde) où le point actuel est devenu l'objectif : point injoignable sauté après 30 s. */
        long wpSince;
        final List<UUID> members = new ArrayList<>();

        Squad(int lane) { this.lane = lane; }
    }

    /** Couloir d'origine de chaque unité : après un combat, elle reprend SON chemin, jamais un autre. */
    private static final Map<UUID, Integer> HOME_LANE = new HashMap<>();

    private static final class UnitState {
        Squad squad;
        Vec3 lastPos;
        int stillTicks;
        int unstuckTries;
    }

    private static final List<List<BlockPos>> LANES = new ArrayList<>();
    private static final Map<UUID, UnitState> UNITS = new HashMap<>();
    private static final Set<UUID> RELEASED = new HashSet<>();
    private static final Map<Integer, Squad> OPEN_SQUAD = new HashMap<>();
    private static ServerLevel level;
    private static BlockPos center;
    private static int squadSize = 8;
    private static int engage = 16;

    private KingdomMarch() {}

    static void init(ServerLevel lvl, BlockPos c, List<BlockPos> portals, int size, int engageRadius) {
        init(lvl, c, portals, List.of(), size, engageRadius);
    }

    /**
     * @param custom chemins tracés au Bâton de tracé (points dans l'ordre) : chacun devient un couloir qui finit au Monolithe.
     *               Une Porte sans chemin qui démarre près d'elle (32 blocs) garde son couloir automatique.
     */
    static void init(ServerLevel lvl, BlockPos c, List<BlockPos> portals, List<List<BlockPos>> custom, int size, int engageRadius) {
        clear();
        level = lvl;
        center = c;
        squadSize = Math.max(1, size);
        engage = Math.max(4, engageRadius);
        for (List<BlockPos> path : custom) {
            if (path == null || path.isEmpty()) continue;
            List<BlockPos> lane = new ArrayList<>(path);
            lane.add(c);
            CUSTOM.add(LANES.size());
            LANES.add(lane);
        }
        for (BlockPos p : portals) {
            boolean covered = false;
            for (List<BlockPos> path : custom) if (path != null && !path.isEmpty() && path.get(0).distSqr(p) < 32 * 32) covered = true;
            if (!covered) LANES.add(buildLane(p, c));
        }
    }

    /** Index des couloirs issus d'un chemin tracé (suivis dans l'ordre, même s'ils font des détours). */
    private static final Set<Integer> CUSTOM = new HashSet<>();

    static void clear() {
        LANES.clear();
        CUSTOM.clear();
        IDLE.clear();
        PROGRESS.clear();
        REL_POS.clear();
        REL_STILL.clear();
        NO_ENGAGE.clear();
        HOME_LANE.clear();
        UNITS.clear();
        RELEASED.clear();
        OPEN_SQUAD.clear();
        level = null;
    }

    /** Vrai si l'unité est encore en marche (KingdomManager ne doit pas lui imposer de cible). */
    static boolean isMarching(UUID id) {
        return UNITS.containsKey(id);
    }

    /** Anti-blocage des unités relâchées : dernière position, temps immobile, et « ne plus se laisser détourner » jusqu'à. */
    private static final Map<UUID, Vec3> REL_POS = new HashMap<>();
    private static final Map<UUID, Integer> REL_STILL = new HashMap<>();
    private static final Map<UUID, Long> NO_ENGAGE = new HashMap<>();

    /** Vrai pendant 30 s après un abandon : l'unité suit son chemin sans se laisser détourner (joueur, bâtiment). */
    static boolean noEngage(UUID id) {
        Long t = NO_ENGAGE.get(id);
        if (t == null || level == null) return false;
        if (level.getGameTime() >= t) { NO_ENGAGE.remove(id); return false; }
        return true;
    }

    /** Unité de siège qui attaque un bâtiment : elle quitte son chemin (et y reviendra ensuite, là où elle en était). */
    static void release(UUID id) {
        if (noEngage(id)) return; // vient d'abandonner un combat impossible : reste sur son chemin
        UnitState st = UNITS.get(id);
        if (st == null) return;
        drop(id, st);
        RELEASED.add(id);
    }

    /** Couloir : points tous les 8 blocs du portail vers le Monolithe, posés sur le terrain. */
    private static List<BlockPos> buildLane(BlockPos from, BlockPos to) {
        List<BlockPos> lane = new ArrayList<>();
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        int steps = Math.max(1, (int) (Math.sqrt(dx * dx + dz * dz) / 8));
        for (int i = 1; i <= steps; i++) {
            int x = (int) Math.round(from.getX() + dx * i / steps);
            int z = (int) Math.round(from.getZ() + dz * i / steps);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            lane.add(new BlockPos(x, y, z));
        }
        return lane;
    }

    private static int nearestLane(Vec3 pos) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        double[] dist = new double[LANES.size()];
        for (int i = 0; i < LANES.size(); i++) {
            List<BlockPos> lane = LANES.get(i);
            dist[i] = Double.MAX_VALUE;
            for (BlockPos p : lane) {
                double d = pos.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                if (d < dist[i]) dist[i] = d;
            }
            if (dist[i] < bestD) { bestD = dist[i]; best = i; }
        }
        // Embranchements : plusieurs chemins tracés partent du même endroit → tirage au sort parmi eux
        if (!CUSTOM.isEmpty()) {
            List<Integer> close = new ArrayList<>();
            double lim = Math.sqrt(bestD) + 12;
            for (int i = 0; i < dist.length; i++) if (CUSTOM.contains(i) && Math.sqrt(dist[i]) <= lim) close.add(i);
            if (close.size() > 1) return close.get(level.random.nextInt(close.size()));
        }
        return best;
    }

    /** Premier point du couloir qui rapproche réellement du Monolithe (évite de faire demi-tour). */
    private static int startWaypoint(int lane, Vec3 pos) { return startWaypoint(lane, pos, 0); }

    /** Point de départ sur le couloir, jamais avant {@code minIdx} (progression déjà acquise par l'unité). */
    private static int startWaypoint(int lane, Vec3 pos, int minIdx) {
        List<BlockPos> l = LANES.get(lane);
        int from = Math.max(0, Math.min(minIdx, l.size() - 1));
        // Chemin tracé : on part du point le plus proche (à partir de la progression acquise), puis on suit l'ordre
        if (CUSTOM.contains(lane)) {
            int best = from;
            double bestD = Double.MAX_VALUE;
            for (int i = from; i < l.size(); i++) {
                double d = pos.distanceToSqr(l.get(i).getX() + 0.5, l.get(i).getY(), l.get(i).getZ() + 0.5);
                if (d < bestD) { bestD = d; best = i; }
            }
            // Déjà dépassé ce point (plus proche du suivant que lui) : on vise le suivant, jamais de demi-tour
            if (best + 1 < l.size()) {
                BlockPos a = l.get(best), b = l.get(best + 1);
                if (pos.distanceToSqr(b.getX() + 0.5, b.getY(), b.getZ() + 0.5) < a.distSqr(b)) best++;
            }
            return best;
        }
        double myDist = pos.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        for (int i = from; i < l.size(); i++) {
            BlockPos p = l.get(i);
            if (p.distSqr(center) < myDist) return i;
        }
        return l.size() - 1;
    }

    /** Appelé toutes les 10 ticks par KingdomManager. */
    static void tick() {
        if (level == null || LANES.isEmpty()) return;
        TargetingConditions near = TargetingConditions.forCombat().range(engage);

        // 1) Prise en charge des nouvelles unités
        for (Map.Entry<UUID, HordeEntity> en : MobRegistry.entries()) {
            UUID id = en.getKey();
            if (UNITS.containsKey(id) || RELEASED.contains(id)) continue;
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob m) || !m.isAlive()) continue;
            // Les Profanateurs suivent eux aussi les couloirs / chemins tracés ; leur IA reprend la main au Monolithe
            int lane = nearestLane(m.position());
            Squad s = OPEN_SQUAD.get(lane);
            Entity lead = s == null || s.leader == null ? null : level.getEntity(s.leader);
            // Escouade ouverte seulement si son chef est encore proche : sinon l'unité courrait après un chef parti trop loin
            // devant (échec de navigation → bloquée près de sa Porte)
            if (s == null || s.members.size() >= squadSize || lead == null || lead.distanceToSqr(m) > 16 * 16) {
                s = new Squad(lane);
                s.leader = id;
                s.wp = startWaypoint(lane, m.position());
                OPEN_SQUAD.put(lane, s);
            }
            HOME_LANE.put(id, lane);
            s.members.add(id);
            UnitState st = new UnitState();
            st.squad = s;
            st.lastPos = m.position();
            UNITS.put(id, st);
            m.setCanPickUpLoot(false);
            m.setTarget(null);
        }

        // 2) Marche
        Vec3 c = new Vec3(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        for (Map.Entry<UUID, UnitState> en : new ArrayList<>(UNITS.entrySet())) {
            UUID id = en.getKey();
            UnitState st = en.getValue();
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob m) || !m.isAlive()) { drop(id, st); continue; }

            // Approche finale : proche du Monolithe, d'un joueur ou d'un soldat du royaume → IA de combat normale.
            // Un Profanateur ne vise que le Monolithe : il ne quitte son chemin qu'en arrivant au Monolithe.
            boolean profaner = com.wavesurvivor.altar.AltarDefense.isProfaner(id);
            boolean calm = noEngage(id); // vient d'abandonner un combat impossible : il ne se laisse plus détourner un moment
            if (m.position().distanceTo(c) < engage || (!profaner && !calm && (level.getNearestPlayer(near, m) != null
                    || !level.getEntitiesOfClass(com.wavesurvivor.entity.KingdomSoldier.class, m.getBoundingBox().inflate(engage)).isEmpty()))) {
                drop(id, st);
                RELEASED.add(id);
                continue;
            }
            // En marche : une cible lointaine (IA vanilla des araignées, poissons d'argent…) ne le détourne pas de son chemin
            if (m.getTarget() != null && (calm || m.distanceTo(m.getTarget()) > engage)) m.setTarget(null);

            Squad s = st.squad;
            if (s.leader == null || level.getEntity(s.leader) == null) promote(s);
            if (s.leader == null) s.leader = id; // dernier survivant de l'escouade
            List<BlockPos> lane = LANES.get(s.lane);
            BlockPos wp = lane.get(Math.min(s.wp, lane.size() - 1));
            if (id.equals(s.leader)) {
                long gt = level.getGameTime();
                if (s.wpSince == 0) s.wpSince = gt;
                // Point atteint à 3 blocs près à l'horizontale (la hauteur ne compte pas : point sur une dalle, un bloc…) ;
                // point injoignable : sauté après 30 s d'efforts
                double hx = m.getX() - (wp.getX() + 0.5), hz = m.getZ() - (wp.getZ() + 0.5);
                boolean reached = hx * hx + hz * hz < 9 && Math.abs(m.getY() - wp.getY()) < 4;
                if ((reached || gt - s.wpSince > 600) && s.wp < lane.size() - 1) {
                    s.wp++;
                    s.wpSince = gt;
                    wp = lane.get(s.wp);
                }
                m.getNavigation().moveTo(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5, 1.0);
            } else {
                Entity leader = level.getEntity(s.leader);
                // Trop loin de son chef (parti devant, coincé ailleurs…) : il quitte l'escouade et marche seul vers la suite
                if (leader == null || m.distanceToSqr(leader) > 24 * 24) {
                    PROGRESS.merge(id, s.wp, Math::max);
                    s.members.remove(id);
                    UNITS.remove(id);
                    enlist(id, m);
                    continue;
                }
                if (m.distanceTo(leader) > 3) m.getNavigation().moveTo(leader, 1.1);
            }

            // Décoinçage
            if (m.position().distanceToSqr(st.lastPos) < 0.25) st.stillTicks += 10;
            else { st.stillTicks = 0; st.unstuckTries = 0; }
            st.lastPos = m.position();
            if (st.stillTicks >= 60) {
                st.stillTicks = 0;
                Vec3 dir = new Vec3(wp.getX() + 0.5 - m.getX(), 0, wp.getZ() + 0.5 - m.getZ());
                dir = dir.lengthSqr() < 0.01 ? c.subtract(m.position()).multiply(1, 0, 1) : dir;
                dir = dir.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : dir.normalize();
                if (++st.unstuckTries <= 1) {
                    m.setDeltaMovement(dir.x * 0.45, 0.55, dir.z * 0.45);
                } else {
                    int tx = (int) Math.floor(m.getX() + dir.x * 3), tz = (int) Math.floor(m.getZ() + dir.z * 3);
                    int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);
                    level.sendParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 1, m.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
                    m.teleportTo(tx + 0.5, ty, tz + 0.5);
                    level.playSound(null, m.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.6f, 1.4f);
                    st.unstuckTries = 0;
                }
            }
        }

        // Ménage des unités relâchées qui ne sont plus dans la horde
        if (RELEASED.size() > 400) {
            Set<UUID> live = new HashSet<>();
            for (Map.Entry<UUID, HordeEntity> en : MobRegistry.entries()) live.add(en.getKey());
            RELEASED.retainAll(live);
        }

        // 3) Retour au chemin : une unité relâchée pour combattre qui n'a plus rien à combattre depuis 3 s
        //    (ni joueur ni soldat à portée, pas de cible proche, pas au Monolithe) reprend sa marche au point le plus proche
        for (UUID id : new ArrayList<>(RELEASED)) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob m) || !m.isAlive()) { IDLE.remove(id); continue; }
            if (com.wavesurvivor.altar.AltarDefense.isProfaner(id)) continue;
            net.minecraft.world.entity.LivingEntity tgt = m.getTarget();
            // Anti-blocage : immobile depuis 8 s sans être au contact de sa cible (joueur inaccessible, bâtiment hors
            // d'atteinte…) → il abandonne, reprend son chemin et ne s'en laisse plus détourner pendant 30 s
            Vec3 lp = REL_POS.put(id, m.position());
            if (lp != null && lp.distanceToSqr(m.position()) < 1.0) REL_STILL.merge(id, 10, Integer::sum); else REL_STILL.put(id, 0);
            BlockPos bt = KingdomSiege.siegeTarget(m);
            boolean inContact = (tgt != null && tgt.isAlive() && m.distanceTo(tgt) < 3.0)
                    || (bt != null && m.position().distanceToSqr(Vec3.atCenterOf(bt)) < 3.5 * 3.5);
            if (!inContact && REL_STILL.getOrDefault(id, 0) >= 160 && m.position().distanceTo(c) >= engage + 4) {
                NO_ENGAGE.put(id, level.getGameTime() + 600);
                REL_STILL.remove(id);
                REL_POS.remove(id);
                IDLE.remove(id);
                RELEASED.remove(id);
                m.setTarget(null);
                m.getNavigation().stop();
                enlist(id, m);
                continue;
            }
            boolean busy = m.position().distanceTo(c) < engage + 4
                    || (tgt != null && tgt.isAlive() && m.distanceTo(tgt) < engage * 2)
                    || level.getNearestPlayer(near, m) != null
                    || KingdomSiege.siegeTarget(m) != null // Sapeur / Bélier encore occupé à un bâtiment
                    || !level.getEntitiesOfClass(com.wavesurvivor.entity.KingdomSoldier.class, m.getBoundingBox().inflate(engage)).isEmpty();
            if (busy) { IDLE.remove(id); continue; }
            if (IDLE.merge(id, 10, Integer::sum) < 60) continue;
            IDLE.remove(id);
            RELEASED.remove(id);
            m.setTarget(null);
            enlist(id, m);
        }
    }

    /** Temps (ticks) depuis lequel une unité relâchée n'a plus rien à combattre. */
    private static final Map<UUID, Integer> IDLE = new HashMap<>();

    /** Remet une unité en marche, seule (sa propre escouade), au point du couloir le plus pertinent : elle ne fait jamais demi-tour. */
    private static void enlist(UUID id, Mob m) {
        int lane = HOME_LANE.getOrDefault(id, nearestLane(m.position())); // son propre chemin, jamais un autre
        if (lane < 0 || lane >= LANES.size()) lane = nearestLane(m.position());
        Squad s = new Squad(lane);
        s.leader = id;
        s.wp = startWaypoint(lane, m.position(), PROGRESS.getOrDefault(id, 0)); // jamais en deçà de sa progression
        s.members.add(id);
        UnitState st = new UnitState();
        st.squad = s;
        st.lastPos = m.position();
        UNITS.put(id, st);
    }

    private static void drop(UUID id, UnitState st) {
        UNITS.remove(id);
        Squad s = st.squad;
        PROGRESS.merge(id, s.wp, Math::max); // mémorise où en était son escouade sur le chemin
        s.members.remove(id);
        if (id.equals(s.leader)) promote(s);
    }

    /** Progression de chaque unité sur son chemin (index de point déjà atteint par son escouade) : jamais de demi-tour. */
    private static final Map<UUID, Integer> PROGRESS = new HashMap<>();

    /** Le premier membre encore en vie devient chef ; il reprend la marche au même point du couloir. */
    private static void promote(Squad s) {
        // Nouveau chef : le membre le plus avancé (le plus proche du point visé), pour que l'escouade ne fasse pas demi-tour
        s.leader = null;
        List<BlockPos> lane = LANES.get(s.lane);
        BlockPos wp = lane.get(Math.min(s.wp, lane.size() - 1));
        double best = Double.MAX_VALUE;
        for (UUID m : s.members) {
            Entity e = level.getEntity(m);
            if (e == null || !e.isAlive()) continue;
            double d = e.distanceToSqr(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5);
            if (d < best) { best = d; s.leader = m; }
        }
        if (s.leader == null && OPEN_SQUAD.get(s.lane) == s) OPEN_SQUAD.remove(s.lane);
    }
}
