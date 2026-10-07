package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.entity.BrecheEntity;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.spawn.HordeSpawner;
import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.horde.spawn.SpawnZone;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * MODE KINGDOM — étape 1.
 *  - 4 grands portails aux points cardinaux (N / E / S / O), sur l'anneau ringMin–ringMax autour du Monolithe.
 *  - Cycle ASSAUT (chaque portail libère son budget d'unités en flux continu, plafonné à aliveCap unités vivantes)
 *    → quand tout est mort : CALME (≈ calmPercent % de production + petites brèches) → nouvel ASSAUT plus fort.
 *  - Portails invulnérables pendant l'Assaut (option), attaquables pendant le Calme.
 *  - Victoire : les 4 portails détruits. Défaite : le Monolithe tombe (géré par AltarDefense).
 *  - Limite de sécurité : la partie s'arrête après maxCycles.
 * Les unités viennent de la liste « Mobs » de la horde (le cycle remplace le numéro de vague).
 */
public final class KingdomManager {

    public enum Phase { ASSAULT, CALM }

    private static final class Portal {
        final UUID id;
        final BlockPos pos;
        final String dir;
        int budget;
        int spawned;
        boolean destroyed;
        /** Numéro de la Porte (1, 2, 3… dans l'ordre de création) : sert à rattacher des unités à une Porte. */
        int num;
        /** Ticks d'affilée où l'entité de la Porte est introuvable alors que sa zone est chargée. */
        int missing;
        /** Gardiens de la Porte (tant qu'un vit, elle est invincible). */
        final List<UUID> guardians = new ArrayList<>();
        /** Gardiens de l'Assaut en cours pas encore récompensés (Essence dès qu'ils sont tous tombés). */
        boolean guardReward;
        /** Débréchée par un Catalyseur : attaquable jusqu'au plancher de PV {@code floor}. */
        boolean breached;
        float floor;
        /** Dernière Porte : « Porte du Roi » (boss final). */
        boolean isFinal;
        /** Style « blocks » : structure en vrais blocs autour de la faille (null en style « visual »). */
        GateStructure structure;
        /** Corruption du sol autour de la Porte (null si non réglée). */
        GateCorruption corruption;

        Portal(UUID id, BlockPos pos, String dir) {
            this.id = id;
            this.pos = pos;
            this.dir = dir;
        }
    }

    private static final Random RNG = new Random();
    private static final String[] DIRS = {"north", "east", "south", "west"};
    private static final int[][] VEC = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private static boolean active = false;
    private static Phase phase = Phase.ASSAULT;
    private static int cycle = 0;
    private static long phaseEndTick, nextSpawnTick, nextSmallBreachTick;
    /** Présage « Lune de sang » : prochaine brèche ouverte pendant l'Assaut. */
    private static long nextBloodBreachTick = Long.MAX_VALUE;
    private static ServerLevel level;
    private static BlockPos center;
    private static HordeConfigMultiData horde;
    private static HordeConfigMultiData.KingdomSettings cfg;
    private static int players = 1;
    private static ServerBossEvent bar;
    private static final List<Portal> PORTALS = new ArrayList<>();
    /** Blocs des Portes en style « blocks » (protégés : impossibles à casser). */
    private static final java.util.Set<BlockPos> STRUCT = new java.util.HashSet<>();

    /** Vrai si cette unité suit en ce moment un couloir / chemin tracé (l'IA du Profanateur la laisse alors marcher). */
    public static boolean marching(UUID id) {
        return active && KingdomMarch.isMarching(id);
    }

    /** Numéro de l'Assaut en cours (bilan de défaite). */
    public static int cycle() { return cycle; }

    /** « La Chute du Monolithe » : les Portes encore debout s'illuminent et poussent un grondement de victoire. */
    public static void defeatRoar() {
        if (level == null) return;
        for (Portal p : PORTALS) {
            if (p.destroyed) continue;
            if (level.getEntity(p.id) instanceof BrecheEntity b) b.setGlowingTag(true);
            double x = p.pos.getX() + 0.5, y = p.pos.getY() + 2, z = p.pos.getZ() + 0.5;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 80, 1.5, 2, 1.5, 0.08);
            level.sendParticles(ParticleTypes.END_ROD, x, y + 2, z, 40, 0.5, 3, 0.5, 0.05);
            level.playSound(null, p.pos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4f, 0.7f);
        }
    }

    /** Portes réellement mortes (événement de mort) : seule façon de déclarer une Porte détruite si elle est hors de vue. */
    private static final java.util.Set<UUID> KILLED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void portalKilled(UUID id) { KILLED.add(id); }

    /** Charge et garde chargée la zone (3×3 tronçons) autour d'un point, AVANT d'y poser une Porte. */
    private static void forceArea(BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            long key = net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz);
            if (!FORCED.contains(key) && !level.getForcedChunks().contains(key) && level.setChunkForced(cx + dx, cz + dz, true)) FORCED.add(key);
            level.getChunk(cx + dx, cz + dz); // chargement immédiat (terrain généré avant de choisir l'emplacement)
        }
    }

    // ─── Zones des Portes gardées chargées pendant la partie (Portes et unités actives même loin des joueurs) ───

    private static final java.util.Set<Long> FORCED = new java.util.HashSet<>();

    private static void forceGateChunks() {
        for (Portal p : PORTALS) {
            int cx = p.pos.getX() >> 4, cz = p.pos.getZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                long key = net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz);
                if (FORCED.contains(key) || level.getForcedChunks().contains(key)) continue; // déjà forcée (par nous ou autre)
                if (level.setChunkForced(cx + dx, cz + dz, true)) FORCED.add(key);
            }
        }
    }

    private static void releaseChunks() {
        if (level != null) {
            for (long key : FORCED) level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key), false);
        }
        FORCED.clear();
    }

    /** Vrai si ce bloc fait partie d'une Porte construite (protégé contre la casse). */
    public static boolean isGateBlock(BlockPos p) {
        return STRUCT.contains(p);
    }

    /** Blocs protégés ajoutés par d'autres modules (décoration de la Mairie). */
    static void addStruct(BlockPos p) { STRUCT.add(p.immutable()); }

    static void removeStruct(java.util.Collection<BlockPos> c) { STRUCT.removeAll(c); }

    // ─── Jour / nuit : Calme = jour, Assaut = nuit (transition rapide d'environ 3 s) ───

    private static Boolean prevDaylight = null;
    /** keepInventory activé par le royaume (null = déjà actif avant la partie : on n'y touche pas). */
    private static Boolean prevKeepInv = null;
    private static long targetTime = 6000;
    /** Début du Calme en cours (pour l'aube progressive). */
    private static long calmStartTick;
    /** Durée du crépuscule (fin du Calme) et de l'aube (début du Calme) : 15 s. */
    private static final int TWILIGHT = 300;

    /**
     * Jour / nuit progressifs : la nuit tombe sur les 15 DERNIÈRES secondes du Calme (nuit complète pile au début de
     * l'Assaut), le jour se lève sur les 15 PREMIÈRES secondes du Calme ; nuit pendant l'Assaut, jour pendant le Calme.
     * Mouvement adouci (accélère puis ralentit), toujours vers l'avant.
     */
    private static void tweenTime(long now) {
        long tod;
        if (phase == Phase.ASSAULT || com.wavesurvivor.horde.mutator.HordeMutators.on(com.wavesurvivor.horde.mutator.Mutator.ETERNAL_NIGHT)) {
            tod = 18000; // nuit (toujours, avec le mutateur « Nuit éternelle »)
        } else {
            long len = Math.max(2, phaseEndTick - calmStartTick);
            long dusk = Math.min(TWILIGHT, len / 2), dawn = Math.min(TWILIGHT, len / 2);
            if (now >= phaseEndTick - dusk) {                 // crépuscule : 6000 (midi) → 18000 (minuit)
                double f = Math.min(1, (now - (phaseEndTick - dusk)) / (double) dusk);
                tod = keyframes(f, DUSK_KEYS);
            } else if (now < calmStartTick + dawn) {          // aube : 18000 (minuit) → 30000 (midi du lendemain)
                double f = Math.min(1, (now - calmStartTick) / (double) dawn);
                tod = keyframes(f, DAWN_KEYS);
            } else {
                tod = 6000;
            }
        }
        long t = level.getDayTime();
        long dist = Math.floorMod(Math.floorMod(tod, 24000L) - Math.floorMod(t, 24000L), 24000L);
        if (dist != 0) level.setDayTime(t + dist);
    }

    private static double smooth(double f) { return f * f * (3 - 2 * f); }

    /**
     * Courbes du crépuscule et de l'aube : la partie VISIBLE (coucher / lever du soleil) occupe l'essentiel des 15 s,
     * l'après-midi et la nuit noire passent plus vite. Paires {part du temps, heure du jour}.
     */
    private static final double[][] DUSK_KEYS = {{0, 6000}, {0.25, 11000}, {0.8, 14000}, {1, 18000}};
    private static final double[][] DAWN_KEYS = {{0, 18000}, {0.2, 22000}, {0.75, 24600}, {1, 30000}};

    private static long keyframes(double f, double[][] k) {
        for (int i = 1; i < k.length; i++) {
            if (f <= k[i][0]) {
                double t = (f - k[i - 1][0]) / Math.max(1e-6, k[i][0] - k[i - 1][0]);
                return Math.round(k[i - 1][1] + (k[i][1] - k[i - 1][1]) * t);
            }
        }
        return Math.round(k[k.length - 1][1]);
    }

    private static void tweenTimeOld() {
        long t = level.getDayTime();
        long dist = Math.floorMod(targetTime - Math.floorMod(t, 24000L), 24000L);
        if (dist == 0) return;
        level.setDayTime(t + Math.min(dist, 200));
    }

    /**
     * Centre d'un événement du chaos en mode Kingdom : sur l'anneau ENTRE le claim et les Portes,
     * dans une direction diagonale (les Portes sont aux points cardinaux), posé sur le terrain.
     */
    public static BlockPos chaosCenter() {
        if (level == null || center == null || cfg == null) return center != null ? center : BlockPos.ZERO;
        int claim = Math.max(4, cfg.claimRadius);
        int ring = Math.max(claim + 12, cfg.ringMin);
        double d = (claim + ring) / 2.0 + (RNG.nextDouble() - 0.5) * Math.max(2, (ring - claim) * 0.3);
        double a = Math.toRadians(45 + 90 * RNG.nextInt(4) + (RNG.nextDouble() - 0.5) * 50);
        int x = center.getX() + (int) Math.round(Math.cos(a) * d);
        int z = center.getZ() + (int) Math.round(Math.sin(a) * d);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private KingdomManager() {}

    public static boolean isActive() { return active; }

    /** Monde de la partie Kingdom en cours (null hors partie). */
    public static ServerLevel level() { return active ? level : null; }

    /** Position du Monolithe (centre du royaume). */
    public static BlockPos center() { return center; }

    /** Phase en cours (Assaut / Calme). */
    public static Phase phase() { return phase; }

    // ─── Charge de démolition (Bâtisseur) ───

    /** Erreurs de cible : aucune Porte proche, Porte pas ébréchée, gardiens encore vivants. */
    public static final int NO_GATE = -1, NOT_BREACHED = -2, GUARDED = -3;

    /**
     * Porte visée par une charge posée en {@code at} : la plus proche à moins de {@code r} blocs.
     * @return son indice si elle est ébréchée et sans gardien, sinon un code d'erreur (NO_GATE, NOT_BREACHED, GUARDED).
     */
    public static int demolitionTarget(BlockPos at, double r) {
        if (!active || level == null) return NO_GATE;
        int best = -1;
        double bd = r * r;
        for (int i = 0; i < PORTALS.size(); i++) {
            Portal p = PORTALS.get(i);
            if (p.destroyed) continue;
            double d = p.pos.distSqr(at);
            if (d <= bd) { bd = d; best = i; }
        }
        if (best < 0) return NO_GATE;
        Portal p = PORTALS.get(best);
        if (guardiansAlive(p)) return GUARDED;
        if (!p.breached || (p.isFinal && finalBossSpawned)) return NOT_BREACHED;
        return best;
    }

    /** Explosion de la charge : la Porte tombe d'un coup jusqu'au plancher de sa brèche (jamais en dessous). */
    public static boolean detonate(int gate) {
        if (!active || level == null || gate < 0 || gate >= PORTALS.size()) return false;
        Portal p = PORTALS.get(gate);
        if (p.destroyed || !p.breached || guardiansAlive(p) || !(level.getEntity(p.id) instanceof BrecheEntity b)) return false;
        float floor = p.floor;
        if (p.isFinal && !finalBossSpawned) floor = Math.max(floor, b.getMaxHealth() * KING_LOW);
        if (b.getHealth() <= floor + 0.5f) return false;
        b.setHealth(Math.max(1f, floor));
        return true;
    }

    public static BlockPos gatePos(int gate) {
        return gate >= 0 && gate < PORTALS.size() ? PORTALS.get(gate).pos : null;
    }

    // ─── Augure ───

    /** Effectifs de l'Assaut selon le présage (Marée basse, Butin abondant, Porte endormie, Frénésie). */
    private static void applyOmen(KingdomOmens.Omen om) {
        if (om == null) return;
        double mult = switch (om) {
            case TIDE -> 0.75;
            case BOUNTY -> 1.2;
            case SLUMBER -> 1.3;
            case FRENZY -> 1.4;
            case DROWSY -> 1.3;
            case OFFERING -> 0.65;
            case IRON_RAIN, ROYAL_HUNT, FAIR_WINDS, BLOOD_MOON -> 1.0;
        };
        Portal sleeping = null;
        if (om == KingdomOmens.Omen.SLUMBER) {
            List<Portal> cand = new ArrayList<>();
            for (Portal p : PORTALS) if (!p.destroyed && !p.isFinal) cand.add(p);
            if (cand.size() > 1) sleeping = cand.get(RNG.nextInt(cand.size()));
        }
        for (Portal p : PORTALS) {
            if (p.destroyed) continue;
            p.budget = p == sleeping ? 0 : (int) Math.max(1, Math.round(p.budget * mult));
        }
        if (sleeping != null) broadcast(WSLang.c("kingdom.omen.slumber.gate", WSLang.t("kingdom.dir." + sleeping.dir)));
    }

    // ─── Lancement ───

    public static void start(MinecraftServer server, HordeConfigMultiData h, ServerLevel lvl, BlockPos c) {
        stop(server);
        active = true;
        horde = h;
        level = lvl;
        center = c;
        cfg = h.configData.kingdom();
        cycle = 0;
        KingdomReport.stop();
        pendingSpecial = null;
        specialRolled = false;
        CATS.clear();
        CAT_GATES.clear();
        ESCORT.clear();
        finalMarked = false;
        forcedFinal = false;
        finalBossSpawned = false;
        kingLow = false;
        BOSS_DONE.clear();
        bossDefeated = false;
        players = Math.max(1, server.getPlayerList().getPlayerCount());
        KingdomTreasury.start(server);
        // Héritage — Seigneur 1 : trésor de départ +20 ◆, +10 Bois et Pierre
        if (com.wavesurvivor.horde.renaissance.Heritage.anyone(server, com.wavesurvivor.horde.renaissance.Heritage.Branch.LORD, 1)) {
            KingdomTreasury.add(KingdomTreasury.Res.MONEY, 40);
            KingdomTreasury.add(KingdomTreasury.Res.WOOD, 20);
            KingdomTreasury.add(KingdomTreasury.Res.STONE, 20);
            KingdomTreasury.add(KingdomTreasury.Res.IRON, 5);
        }
        KingdomRoles.start(server, h);

        bar = new ServerBossEvent(Component.literal(""), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) bar.addPlayer(p);

        int dist = Math.max(16, (Math.max(cfg.ringMin, 8) + Math.max(cfg.ringMax, cfg.ringMin)) / 2);
        // Emplacements : coordonnées fixes (maps custom) ou 1 à 8 Portes réparties sur l'anneau en partant du nord
        boolean fixedGates = "fixed".equalsIgnoreCase(cfg.gatePlacement) && cfg.gatePoints != null && !cfg.gatePoints.isEmpty();
        List<BlockPos> targets = new ArrayList<>();
        List<String> faces = new ArrayList<>();
        if (fixedGates) {
            for (var gp : cfg.gatePoints) if (gp != null && targets.size() < 8) {
                targets.add(new BlockPos(gp.x, gp.y, gp.z));
                faces.add(gp.facing == null ? "" : gp.facing);
            }
        } else {
            int n = Math.max(1, Math.min(8, cfg.gateCount));
            for (int i = 0; i < n; i++) {
                double a = -Math.PI / 2 + i * Math.PI * 2 / n; // -90° = nord
                targets.add(c.offset((int) Math.round(Math.cos(a) * dist), 0, (int) Math.round(Math.sin(a) * dist)));
            }
        }
        for (int i = 0; i < targets.size(); i++) {
            BrecheEntity b = com.wavesurvivor.registry.ModEntities.BRECHE.get().create(lvl);
            if (b == null) continue;
            BlockPos aim = targets.get(i);
            forceArea(aim); // zone chargée AVANT de choisir l'emplacement et de bâtir (sinon Porte mal placée ou « perdue »)
            BlockPos at = fixedGates ? aim : SpawnZone.pick(lvl, aim, 4, b.getType());
            // Façade tournée vers le Monolithe (axe dominant), nom selon la vraie direction
            int vx = c.getX() - at.getX(), vz = c.getZ() - at.getZ();
            int fx = Math.abs(vx) >= Math.abs(vz) ? Integer.signum(vx) : 0, fz = fx == 0 ? Integer.signum(vz) : 0;
            if (fx == 0 && fz == 0) fz = 1;
            // Orientation : réglage propre à la Porte (mode fixe), sinon réglage général (face / dos / côté gauche / droit)
            String face = i < faces.size() && !faces.get(i).isBlank() ? faces.get(i) : cfg.gateFacing;
            switch (face == null ? "monolith" : face) {
                case "away" -> { fx = -fx; fz = -fz; }
                case "left" -> { int t0 = fx; fx = fz; fz = -t0; }
                case "right" -> { int t0 = fx; fx = -fz; fz = t0; }
                default -> { }
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-fx, fz));
            String dir = directionOf(Math.atan2(at.getZ() - c.getZ(), at.getX() - c.getX()));
            b.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
            b.setYBodyRot(yaw);
            b.setYHeadRot(yaw);
            b.setGreat(true);
            b.setTheme(resolveTheme(cfg.portalTheme, h.hordeName));
            b.setStyle(cfg.portalStyle, -1);
            b.setRiftScale((float) cfg.portalScale);
            AttributeInstance hp = b.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(Math.max(10, cfg.portalHealth));
            b.setHealth(b.getMaxHealth());
            // Nom numéroté : « Portail 1, 2, 3… » en mode fixe, « Portail du Nord #1 » en mode auto
            b.setCustomName(Component.literal(fixedGates ? WSLang.t("kingdom.portal_name.num", i + 1)
                    : WSLang.t("kingdom.portal_name." + dir) + " \u00a77#" + (i + 1)));
            b.setCustomNameVisible(true);
            b.getPersistentData().putBoolean("ws_kingdom_portal", true);
            boolean den = "den".equalsIgnoreCase(cfg.gateShape);
            b.setDen(den);
            b.setDenBackground(cfg.denBackground);
            // Style « blocks » : la Porte est construite en vrais blocs, la faille se pose sur sa plateforme
            GateStructure gs = null;
            if (!"visual".equalsIgnoreCase(cfg.gateStyle)) {
                String gateTheme = resolveTheme(cfg.portalTheme, h.hordeName);
                gs = den ? GateStructure.buildDen(lvl, at, fx, fz, gateTheme) : GateStructure.build(lvl, at, fx, fz, gateTheme);
                b.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, yaw, 0f);
                b.setBuilt(true);
            }
            if (lvl.addFreshEntity(b)) {
                Portal portal = new Portal(b.getUUID(), at, dir);
                portal.num = i + 1;
                portal.structure = gs;
                if (gs != null) STRUCT.addAll(gs.original.keySet());
                PORTALS.add(portal);
                // Corruption du sol autour de la Porte (se propage par anneaux)
                if (GateCorruption.usable(cfg.corruption)) portal.corruption = new GateCorruption(lvl, at, cfg.corruption);
                lvl.sendParticles(ParticleTypes.REVERSE_PORTAL, at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5, 120, 1.5, 2, 1.5, 0.1);
            } else if (gs != null) {
                gs.restore(); // faille refusée : on ne laisse pas une structure orpheline
            }
        }
        broadcast(WSLang.c("kingdom.start", WSLang.t(h.hordeName), PORTALS.size()));
        forceGateChunks();
        // Gardiens de chaque Porte
        for (Portal p : PORTALS) spawnGuardians(p);
        // Étape 2 : couloirs de marche (calculés une seule fois)
        List<BlockPos> portalPos = new ArrayList<>();
        for (Portal p : PORTALS) portalPos.add(p.pos);
        // Chemins tracés au Bâton de tracé (prioritaires sur les couloirs automatiques)
        List<List<BlockPos>> customPaths = new ArrayList<>();
        if (cfg.paths != null) for (var pd : cfg.paths) {
            if (pd == null || pd.points == null || pd.points.isEmpty()) continue;
            List<BlockPos> l = new ArrayList<>();
            for (var gp : pd.points) if (gp != null) l.add(new BlockPos(gp.x, gp.y, gp.z));
            if (!l.isEmpty()) customPaths.add(l);
        }
        KingdomMarch.init(lvl, c, portalPos, customPaths, cfg.squadSize, cfg.engageRadius);
        // Étape 3 : zone claim du royaume
        KingdomClaim.start(lvl, c, cfg);
        // Étape 5 : unités de siège
        KingdomSiege.start(lvl, c, cfg);
        // Mairie (le Monolithe) : niveau 1, claim de base
        KingdomTownHall.start(lvl, c, cfg.claimRadius);
        // Cycle jour / nuit : Calme = jour, Assaut = nuit (le cycle naturel est suspendu pendant la partie)
        var daylight = lvl.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT);
        prevDaylight = daylight.get();
        daylight.set(false, server);
        // Mort pendant le royaume : on garde son équipement (keepInventory activé seulement s'il ne l'était pas déjà)
        var keepInv = lvl.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        if (!keepInv.get()) {
            prevKeepInv = false;
            keepInv.set(true, server);
            Component kmsg = WSLang.c("kingdom.keep_inventory");
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) sp.sendSystemMessage(kmsg);
        } else {
            prevKeepInv = null;
        }
        targetTime = 6000;
        WaveSurvivorMod.LOGGER.info("[Kingdom] '{}' : {} portails à {} blocs du Monolithe {}", h.hordeName, PORTALS.size(), dist, c.toShortString());

        // Premier Assaut dans 10 secondes
        phase = Phase.CALM;
        phaseEndTick = server.getTickCount() + 200;
        calmStartTick = server.getTickCount() - TWILIGHT; // pas d'aube au lancement : on part en plein jour
        nextSmallBreachTick = Long.MAX_VALUE;
        for (Portal p : PORTALS) { p.budget = 0; p.spawned = 0; }
    }

    // ─── Phases ───

    /** Reprise en cours : le Calme relancé ne compte ni PR ni bilan. */
    private static boolean resuming = false;

    /** Sauvegarde de partie : uniquement pendant un Calme (point de reprise propre). */
    public static boolean isCalmPhase() {
        return active && phase == Phase.CALM && cycle > 0;
    }

    /** SAUVEGARDE DE PARTIE : tout l'état du royaume nécessaire pour reprendre au début de ce Calme. */
    public static net.minecraft.nbt.CompoundTag saveState() {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putInt("cycle", cycle);
        t.putIntArray("treasury", KingdomTreasury.amounts());
        t.putInt("town", KingdomTownHall.tier());
        net.minecraft.nbt.ListTag gates = new net.minecraft.nbt.ListTag();
        for (Portal p : PORTALS) {
            net.minecraft.nbt.CompoundTag g = new net.minecraft.nbt.CompoundTag();
            g.putInt("num", p.num);
            g.putBoolean("destroyed", p.destroyed);
            g.putBoolean("final", p.isFinal);
            float ratio = 1f;
            if (level != null && level.getEntity(p.id) instanceof net.minecraft.world.entity.LivingEntity le && le.getMaxHealth() > 0) {
                ratio = Math.max(0.05f, le.getHealth() / le.getMaxHealth());
            }
            g.putFloat("hp", ratio);
            gates.add(g);
        }
        t.put("gates", gates);
        t.putIntArray("bossDone", BOSS_DONE.stream().mapToInt(Integer::intValue).toArray());
        t.putBoolean("finalMarked", finalMarked);
        t.putBoolean("bossDefeated", bossDefeated);
        t.putBoolean("kingLow", kingLow);
        t.put("roles", KingdomRoles.saveRoles());
        t.put("defs", KingdomDefenses.saveState());
        return t;
    }

    /**
     * REPRISE DE PARTIE : la partie vient d'être relancée (Portes neuves, Monolithe) ; on réimpose l'état sauvegardé
     * (trésor, Mairie, Portes, boss, rôles, défenses) puis on ouvre un vrai Calme après l'Assaut {@code cycle}.
     */
    public static void restoreState(net.minecraft.nbt.CompoundTag t, net.minecraft.server.MinecraftServer server) {
        if (!active || level == null || t == null || t.isEmpty()) return;
        KingdomTreasury.restoreAmounts(t.getIntArray("treasury"));
        KingdomTownHall.restoreTier(t.getInt("town"));
        net.minecraft.nbt.ListTag gates = t.getList("gates", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < gates.size(); i++) {
            net.minecraft.nbt.CompoundTag g = gates.getCompound(i);
            for (Portal p : PORTALS) {
                if (p.num != g.getInt("num")) continue;
                Entity e = level.getEntity(p.id);
                if (g.getBoolean("destroyed")) {
                    p.destroyed = true;
                    for (UUID gid : p.guardians) { Entity ge = level.getEntity(gid); if (ge != null) ge.discard(); }
                    p.guardians.clear();
                    if (e != null) e.discard();
                } else if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                    le.setHealth(Math.max(1f, le.getMaxHealth() * Math.min(1f, g.getFloat("hp"))));
                }
                p.isFinal = g.getBoolean("final");
            }
        }
        for (int b : t.getIntArray("bossDone")) BOSS_DONE.add(b);
        finalMarked = t.getBoolean("finalMarked");
        bossDefeated = t.getBoolean("bossDefeated");
        kingLow = t.getBoolean("kingLow");
        finalBossSpawned = false; // un boss final vivant au moment de l'arrêt réapparaîtra à son Assaut
        KingdomRoles.restoreRoles(server, t.getCompound("roles"));
        KingdomDefenses.restoreState(level, t.getCompound("defs"));
        cycle = Math.max(0, t.getInt("cycle"));
        resuming = true;
        try {
            startCalm(server.getTickCount());
        } finally {
            resuming = false;
        }
    }

    private static int budgetFor(int c) {
        // (mutateur « Nuée » : ×1,5 unités par Assaut)
        return (int) Math.max(1, Math.round(cfg.assaultBudget * (1 + Math.max(0, cfg.assaultGrowth) * (c - 1))
                * com.wavesurvivor.horde.mutator.MutatorEffects.countMultiplier()
                * com.wavesurvivor.horde.difficulty.HordeDifficulty.countMultiplier()));
    }

    private static void startAssault(long now) {
        cycle++;
        targetTime = 18000; // l'Assaut tombe à la nuit
        // Boss final en attente / vivant / dans son arène : la limite de sécurité ne coupe pas le dernier combat
        boolean finalPending = cfg.finalBoss && hasFinalBoss() && !bossDefeated;
        if (cfg.maxCycles > 0 && cycle > cfg.maxCycles && !finalPending) {
            broadcast(WSLang.c("kingdom.safety_end", cfg.maxCycles));
            HordeManager.get().endKingdom(false);
            return;
        }
        // Assaut du boss final atteint sans Porte du Roi (aucune Porte détruite) : le Roi choisit la Porte la plus abîmée
        if (cfg.finalBoss && hasFinalBoss() && !finalMarked && cycle >= finalBossWave()) markFinalForced();
        phase = Phase.ASSAULT;
        KingdomReport.start(cycle); // bilan de fin d'Assaut : compteurs remis à zéro
        KingdomRoles.sync(level.getServer());
        int budget = budgetFor(cycle);
        for (Portal p : PORTALS) { p.budget = p.destroyed ? 0 : budget; p.spawned = 0; }
        // Augure : le présage choisi pendant le Calme modifie les effectifs de cet Assaut
        applyOmen(KingdomOmens.startAssault(level.getServer()));
        // Objectif du Calme précédent : clos ; Purification → Assaut plus fort (aucun foyer) ou plus faible (tous)
        double objMult = KingdomObjectives.onAssault();
        // Héritage — Seigneur 5 : 2 premiers Assauts −15 % de monstres
        if (cycle <= 2 && com.wavesurvivor.horde.renaissance.Heritage.anyone(level.getServer(), com.wavesurvivor.horde.renaissance.Heritage.Branch.LORD, 5)) objMult *= 0.85;
        com.wavesurvivor.horde.renaissance.Heritage.onWaveStart(level.getServer()); // Survivant 2
        if (objMult != 1.0) {
            for (Portal p : PORTALS) if (p.budget > 0) p.budget = (int) Math.max(1, Math.round(p.budget * objMult));
        }
        // Le Catalyseur du Calme précédent disparaît ; les Gardiens se reforment (sauf présage « Gardiens assoupis ») ;
        // la Porte du Roi libère son boss
        clearCatalyst();
        if (!KingdomOmens.is(KingdomOmens.Omen.DROWSY)) {
            for (Portal p : PORTALS) if (!p.destroyed) spawnGuardians(p);
        }
        // Porte du Roi : son boss surgit à l'Assaut de sa vague de boss (~10), ou dès l'Assaut qui suit sa chute à 35 % PV
        if (finalMarked && !finalBossSpawned && (cycle >= finalBossWave() || kingLow)) spawnFinalBoss();
        // Fin du Calme : marchands et coffres repartent ; tirage d'un éventuel assaut spécial ; boss d'assaut
        HordeManager.get().kingdomCalmEnd(level.getServer());
        pickSpecial();
        spawnAssaultBosses();
        nextSpawnTick = now + 20;
        nextBloodBreachTick = now + 15 * 20L; // Lune de sang : première brèche 15 s après le début de l'Assaut
        com.wavesurvivor.horde.WaveCleanup.reset();
        com.wavesurvivor.network.EventFeedPacket.toAll(level.getServer(), WSLang.c("kingdom.assault", cycle),
                "minecraft:iron_sword", com.wavesurvivor.network.EventFeedPacket.DANGER, true);
        for (ServerPlayer p : level.players()) {
            level.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 1f);
        }
    }

    private static void startCalm(long now) {
        phase = Phase.CALM;
        if (!resuming) {
            KingdomReport.finish(level); // bilan de l'Assaut qui vient de se terminer
            com.wavesurvivor.horde.renaissance.RenaissanceRewards.onWaveCleared(); // PR : Assaut survécu
        }
        // Gisements / arbres garantis (réglage « Garantis par Calme » de l'éditeur)
        HordeManager.get().spawnGuaranteedResources(level.getServer());
        KingdomRoles.sync(level.getServer());
        // Intendant : intérêts de fin de vague (+5 % du trésor, 100 maximum) — pas au Calme de reprise
        if (!resuming && KingdomRoles.present(KingdomRoles.Role.QUARTERMASTER)) {
            int gain = Math.min(100, (int) Math.floor(KingdomTreasury.get(KingdomTreasury.Res.MONEY) * 0.05));
            if (gain > 0) KingdomTreasury.reward(KingdomTreasury.Res.MONEY, gain, "kingdom.role.quartermaster.interest");
        }
        // Chasseur de primes : bilan du contrat de la vague, puis nouveau contrat
        KingdomRoleExtras.onCalm(level.getServer(), cycle);
        // Augure : fin du présage de l'Assaut, nouveaux présages proposés
        KingdomOmens.onCalm(level.getServer());
        phaseEndTick = now + Math.max(10, cfg.calmSeconds) * 20L;
        // Arène du Roi : l'Assaut final est repoussé → la Porte du Roi s'ouvre sur l'arène du boss
        if (raidAwait && !RaidManager.isOpen()) {
            raidAwait = false;
            Portal fin = null;
            for (Portal p : PORTALS) if (!p.destroyed && p.isFinal) fin = p;
            if (fin != null) RaidManager.open(level, fin.pos, center, raidBoss, cfg.raidRadius);
            else raidVictory();
        }
        calmStartTick = now; // l'aube commence
        targetTime = 6000; // le Calme se lève avec le jour
        int calmBudget = (int) Math.max(1, Math.round(budgetFor(cycle) * Math.max(0, cfg.calmPercent) / 100.0));
        for (Portal p : PORTALS) { p.budget = p.destroyed ? 0 : calmBudget; p.spawned = 0; }
        // Objectif du Calme : un Catalyseur apparaît près d'une Porte
        spawnCatalyst();
        // Objectif facultatif du Calme (convoi, champion, trésor, purification), tiré au hasard
        KingdomObjectives.onCalm(level, now, cycle, cfg.calmObjectives);
        // Calme : Bénédictions, marchands et coffres roulette (comme la pause entre deux vagues)
        special = null;
        specialUnits.clear();
        HordeManager.get().kingdomCalmStart(level.getServer(), cycle);
        // Éclaireur : ce que sera le prochain Assaut (Portes, effectifs, assaut spécial tiré dès maintenant, boss)
        scoutReport();
        nextSpawnTick = now + 40;
        nextSmallBreachTick = cfg.smallBreachSeconds > 0 ? now + cfg.smallBreachSeconds * 20L : Long.MAX_VALUE;
        com.wavesurvivor.network.EventFeedPacket.toAll(level.getServer(), WSLang.c("kingdom.calm", cycle, Math.max(10, cfg.calmSeconds)),
                "minecraft:feather", com.wavesurvivor.network.EventFeedPacket.RESOURCE, false);
        for (ServerPlayer p : level.players()) {
            level.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.MASTER, 2f, 0.8f);
        }
    }

    // ─── Tick ───

    public static void tick(MinecraftServer server) {
        if (!active || level == null) return;
        long now = server.getTickCount();
        tweenTime(now);
        KingdomTownHall.tick(now);   // ambiance de la Mairie (selon son niveau)
        RaidManager.tick(now);       // Arène du Roi (Porte ouverte, entrées, boss, victoire)
        if (!active) return;
        KingdomObjectives.tick(now); // objectif du Calme en cours
        int remaining = 0;
        for (Portal p : PORTALS) {
            if (p.destroyed) continue;
            Entity e = level.getEntity(p.id);
            // Détruite seulement sur une VRAIE mort (événement de mort, ou entité trouvée morte) : jamais parce qu'elle est hors de vue
            boolean gone = (e != null && !e.isAlive()) || KILLED.contains(p.id);
            if (gone) {
                p.destroyed = true;
                p.budget = p.spawned;
                for (UUID gid : p.guardians) { Entity g = level.getEntity(gid); if (g != null) g.discard(); }
                p.guardians.clear();
                if (p.structure != null) {
                    STRUCT.removeAll(p.structure.original.keySet());
                    p.structure.collapse();
                    p.structure = null;
                }
                if (p.corruption != null) { // Porte détruite : son sol est purifié
                    p.corruption.restore();
                    p.corruption = null;
                }
                broadcast(WSLang.c("kingdom.portal_destroyed", WSLang.t("kingdom.dir." + p.dir)));
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.pos.getX() + 0.5, p.pos.getY() + 2, p.pos.getZ() + 0.5, 3, 1, 1, 1, 0);
                for (ServerPlayer pl : level.players()) level.playSound(null, pl.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1f, 1.2f);
            } else {
                remaining++;
            }
        }
        if (remaining == 0 && !PORTALS.isEmpty()) {
            broadcast(WSLang.c("kingdom.victory"));
            HordeManager.get().endKingdom(true);
            return;
        }
        // Dernière Porte : elle devient la Porte du Roi
        if (remaining == 1 && cfg.finalBoss && !finalMarked) markFinal();
        // Gardiens, Catalyseur, vulnérabilité des Portes, boss final
        if (now % 10 == 0) {
            checkCatalyst();
            updateGates();
            guardianLinks();
            if (finalBossSpawned && !bossDefeated && !cfg.raidArena) {
                com.wavesurvivor.horde.boss.BossManager.updateBars(server);
                if (com.wavesurvivor.horde.boss.BossManager.activeBossCount() == 0) {
                    bossDefeated = true;
                    broadcast(WSLang.c("kingdom.final_boss_down"));
                    // Porte du Roi choisie alors que d'autres Portes tenaient encore : le Roi est tombé, le royaume a gagné
                    if (forcedFinal) {
                        HordeManager.get().endKingdom(true);
                        return;
                    }
                }
            } else if (com.wavesurvivor.horde.boss.BossManager.activeBossCount() > 0) {
                com.wavesurvivor.horde.boss.BossManager.updateBars(server); // boss d'assaut
            }
        }
        if (now % 40 == 0) leashGuardians();

        for (Portal p : PORTALS) if (!p.destroyed && p.corruption != null) p.corruption.tick(); // propagation de la corruption
        tickRifts(now);                                                                           // failles des brèches du Calme
        if (now % 20 == 0) MobRegistry.pruneDead(server);
        int alive = MobRegistry.size();

        if (phase == Phase.ASSAULT) {
            HordeManager.get().kingdomChaosTick(server); // événements du chaos pendant les Assauts
            if (now >= nextSpawnTick) {
                nextSpawnTick = now + 10; // 2 vagues de libération par seconde
                for (Portal p : PORTALS) {
                    if (p.destroyed || p.spawned >= p.budget) continue;
                    if (alive >= Math.max(1, cfg.aliveCap)) break;
                    alive += spawnUnits(p, 1);
                    p.spawned++;
                }
            }
            boolean spent = true;
            for (Portal p : PORTALS) if (!p.destroyed && p.spawned < p.budget) { spent = false; break; }
            // Présage « Lune de sang » : des brèches s'ouvrent aussi pendant l'Assaut (tant qu'il reste des unités à venir)
            if (!spent && KingdomOmens.is(KingdomOmens.Omen.BLOOD_MOON) && now >= nextBloodBreachTick) {
                nextBloodBreachTick = now + Math.max(20, cfg.smallBreachSeconds > 0 ? cfg.smallBreachSeconds : 40) * 20L;
                smallBreach(now);
            }
            // Fin de vague forcée : ≤ 10 % de l'Assaut en vie → compte à rebours, puis les survivantes disparaissent
            if (com.wavesurvivor.horde.WaveCleanup.tick(level.getServer(), now, spent, horde.configData.forceEndSeconds)) alive = 0;
            if (spent && alive == 0) startCalm(now);
        } else {
            // Calme : seuls les événements de ressources (gisements, arbres) peuvent survenir
            HordeManager.get().kingdomChaosTick(server, true);
            if (now >= nextSpawnTick && cycle > 0) {
                int calmBudget = 1;
                for (Portal p : PORTALS) calmBudget = Math.max(calmBudget, p.budget);
                nextSpawnTick = now + Math.max(20, Math.max(10, cfg.calmSeconds) * 20L / calmBudget);
                for (Portal p : PORTALS) {
                    if (p.destroyed || p.spawned >= p.budget || alive >= cfg.aliveCap) continue;
                    alive += spawnUnits(p, 1);
                    p.spawned++;
                }
            }
            if (now >= nextSmallBreachTick) {
                nextSmallBreachTick = now + Math.max(5, cfg.smallBreachSeconds) * 20L;
                smallBreach(now);
            }
            // Porte du Roi ouverte sur l'arène : le Calme ne finit pas (plus aucun Assaut tant que le raid dure)
            if (RaidManager.isOpen()) phaseEndTick = Math.max(phaseEndTick, now + 200);
            if (now >= phaseEndTick) startAssault(now);
            if (!active) return;
        }

        if (now % 10 == 0) KingdomMarch.tick();
        KingdomClaim.tick(now);
        KingdomDefenses.tick(now);
        KingdomSiege.tick(now, cycle);
        if (now % 20 == 0) retarget();
        if (now % 10 == 0) updateBar(now, alive);
        if (now % 10 == 0) shieldParticles();
    }

    // ─── Gardiens, Catalyseur, Porte du Roi ───

    /** Catalyseurs du Calme en cours (2 possibles avec l'Arcaniste) et leur Porte. */
    private static final List<UUID> CATS = new ArrayList<>();
    private static final List<Integer> CAT_GATES = new ArrayList<>();

    /** Catalyseurs vivants (effets de rôle : surbrillance, flèche de l'Arcaniste). */
    public static List<Entity> catalysts() {
        List<Entity> out = new ArrayList<>();
        if (!active || level == null) return out;
        for (UUID id : CATS) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) out.add(e);
        }
        return out;
    }
    private static final List<UUID> ESCORT = new ArrayList<>();
    private static boolean finalMarked, finalBossSpawned, bossDefeated;
    /** Porte du Roi désignée d'office (d'autres Portes tenaient encore) : vaincre le boss final suffit à gagner. */
    private static boolean forcedFinal;

    /** La horde a un boss final configuré. */
    private static boolean hasFinalBoss() {
        return horde != null && horde.configData != null && horde.configData.bossWaves != null && !horde.configData.bossWaves.isEmpty();
    }

    /** Le Roi choisit sa Porte : la plus abîmée des Portes encore debout devient la Porte du Roi. */
    private static void markFinalForced() {
        Portal pick = null;
        float best = Float.MAX_VALUE;
        for (Portal p : PORTALS) {
            if (p.destroyed) continue;
            float pct = level.getEntity(p.id) instanceof BrecheEntity b ? b.getHealth() / Math.max(1f, b.getMaxHealth()) : 1f;
            if (pct < best) { best = pct; pick = p; }
        }
        if (pick == null) return;
        finalMarked = true;
        forcedFinal = true;
        pick.isFinal = true;
        pick.breached = false;
        if (level.getEntity(pick.id) instanceof BrecheEntity b) b.setCustomName(Component.literal(WSLang.t("kingdom.king_gate_name")));
        broadcast(WSLang.c("kingdom.king_forced", WSLang.t("kingdom.dir." + pick.dir)));
    }

    /**
     * Test (op) : saute à l'étape finale — toutes les unités retirées, Porte du Roi désignée, puis arène ouverte
     * (si activée) ou boss final sorti de la Porte du Roi.
     */
    public static int debugFinal(net.minecraft.commands.CommandSourceStack src) {
        if (!active || level == null) { src.sendFailure(WSLang.c("kingdom.debug_not_active")); return 0; }
        if (!hasFinalBoss()) { src.sendFailure(WSLang.c("kingdom.debug_no_boss")); return 0; }
        for (var en : new ArrayList<>(MobRegistry.entries())) {
            Entity e = level.getEntity(en.getKey());
            if (e != null && !com.wavesurvivor.horde.boss.BossManager.isBoss(e.getUUID())) e.discard();
            MobRegistry.remove(en.getKey());
        }
        if (!finalMarked) markFinalForced();
        cycle = Math.max(cycle, finalBossWave());
        for (Portal p : PORTALS) { p.budget = 0; p.spawned = 0; }
        long now = level.getServer().getTickCount();
        if (!finalBossSpawned) spawnFinalBoss();
        if (cfg.raidArena && raidAwait) startCalm(now); // arène : la Porte du Roi s'ouvre tout de suite
        else phase = Phase.ASSAULT;                    // sinon : le boss final est sorti de la Porte du Roi
        src.sendSuccess(() -> WSLang.c("kingdom.debug_final"), true);
        return 1;
    }
    /** Arène du Roi : boss final en attente dans son arène (la Porte s'ouvrira au prochain Calme). */
    private static boolean raidAwait;
    private static com.wavesurvivor.horde.model.BossWave raidBoss;

    /** Boss de l'arène vaincu : victoire du royaume (les joueurs sont déjà rentrés au Monolithe). */
    static void raidVictory() {
        bossDefeated = true;
        HordeManager.get().endKingdom(true);
    }
    /** La Porte du Roi est tombée à 35 % PV : son boss surgira au prochain Assaut. */
    private static boolean kingLow;
    /** Vagues de boss déjà libérées (pour ne pas faire apparaître deux fois un mini-boss). */
    private static final java.util.Set<Integer> BOSS_DONE = new java.util.HashSet<>();
    /** Seuil de PV de la Porte du Roi qui appelle son boss. */
    private static final float KING_LOW = 0.35f;

    /** Vague du boss final (la plus haute vague de boss) ; 10 si aucune n'est configurée. */
    private static int finalBossWave() {
        var waves = horde.configData.bossWaves;
        int n = 0;
        if (waves != null) for (var w : waves) n = Math.max(n, w.waveNumber);
        return n > 0 ? n : 10;
    }

    /** Thème des Portes : celui de la config, ou déduit du nom de la horde (« auto »). */
    private static String resolveTheme(String wanted, String hordeName) {
        if (wanted != null && !wanted.isBlank() && !"auto".equalsIgnoreCase(wanted)) return wanted.toLowerCase();
        String n = hordeName == null ? "" : hordeName.toLowerCase();
        if (n.contains("brûl") || n.contains("brul") || n.contains("scorch") || n.contains("nether")) return "fire";
        if (n.contains("nécrop") || n.contains("necrop")) return "bone";
        if (n.contains("profond") || n.contains("depth")) return "ocean";
        if (n.contains("néant") || n.contains("neant") || n.contains("void")) return "end";
        if (n.contains("arcan") || n.contains("conclave")) return "arcane";
        return "abyss";
    }

    /** Monstre le plus robuste de la horde (base des Gardiens) — ou celui coché « ⛨ Gardien » dans l'éditeur. */
    private static HordeEntity strongestTemplate() {
        HordeEntity best = null;
        for (HordeEntity t : horde.configData.hordeEntities) if (t.kingdomGuardian && (best == null || t.maxHealth > best.maxHealth)) best = t;
        if (best != null) return best;
        for (HordeEntity t : horde.configData.hordeEntities) if (best == null || t.maxHealth > best.maxHealth) best = t;
        return best;
    }

    // ─── Assauts spéciaux et boss d'assaut ───

    private static com.wavesurvivor.horde.model.SpecialWave special;
    /** Éclaireur : assaut spécial tiré au début du Calme pour le prochain Assaut. */
    private static com.wavesurvivor.horde.model.SpecialWave pendingSpecial;
    private static boolean specialRolled = false;
    /** Dernier rapport de l'Éclaireur (réaffichable). */
    private static final List<Component> SCOUT = new ArrayList<>();

    /** Rapport de l'Éclaireur sur le prochain Assaut, envoyé à tous au début du Calme. */
    private static void scoutReport() {
        SCOUT.clear();
        int next = cycle + 1;
        List<String> dirs = new ArrayList<>();
        for (Portal p : PORTALS) if (!p.destroyed) dirs.add(WSLang.t("kingdom.dir." + p.dir));
        if (dirs.isEmpty()) return;
        int per = budgetFor(next);
        SCOUT.add(WSLang.c("kingdom.scout.head", next));
        SCOUT.add(WSLang.c("kingdom.scout.gates", per * dirs.size(), dirs.size(), String.join(", ", dirs), per));
        // Assaut spécial : tiré maintenant, appliqué tel quel à l'Assaut
        pendingSpecial = HordeManager.get().kingdomPickSpecial();
        specialRolled = true;
        if (pendingSpecial != null && pendingSpecial.entities != null && !pendingSpecial.entities.isEmpty()) {
            SCOUT.add(WSLang.c("kingdom.scout.special", WSLang.t(pendingSpecial.name).trim()));
        }
        // Boss d'assaut prévus à cet Assaut (hors boss final)
        var waves = horde.configData.bossWaves;
        if (waves != null && horde.configData.useBossWaves) {
            com.wavesurvivor.horde.model.BossWave finalWave = null;
            for (var w : waves) if (finalWave == null || w.waveNumber > finalWave.waveNumber) finalWave = w;
            for (var w : waves) {
                if (w.waveNumber != next || BOSS_DONE.contains(w.waveNumber) || (cfg.finalBoss && w == finalWave)) continue;
                SCOUT.add(WSLang.c("kingdom.scout.boss", WSLang.t(w.bossName)));
            }
        }
        // Boss final
        if (cfg.finalBoss && hasFinalBoss() && !bossDefeated && !finalBossSpawned && next >= finalBossWave()) {
            SCOUT.add(WSLang.c("kingdom.scout.final"));
        }
        SCOUT.add(WSLang.c("kingdom.scout.note"));
        for (ServerPlayer p : level.players()) for (Component c : SCOUT) p.sendSystemMessage(c);
    }

    /** Réaffiche le dernier rapport de l'Éclaireur à un joueur (bouton de la Mairie). */
    public static void scoutRepeat(ServerPlayer p) {
        if (SCOUT.isEmpty() || phase != Phase.CALM) {
            p.displayClientMessage(WSLang.c("kingdom.scout.none"), true);
            return;
        }
        for (Component c : SCOUT) p.sendSystemMessage(c);
    }
    private static final List<HordeEntity> specialUnits = new ArrayList<>();

    /** Tirage d'un assaut spécial : ses unités remplacent le mélange habituel pendant tout l'Assaut. */
    private static void pickSpecial() {
        // Assaut spécial déjà tiré au début du Calme (annoncé par l'Éclaireur) : c'est celui-là
        if (specialRolled) {
            special = pendingSpecial;
            specialRolled = false;
            pendingSpecial = null;
        } else {
            special = HordeManager.get().kingdomPickSpecial();
        }
        specialUnits.clear();
        if (special == null || special.entities == null) { special = null; return; }
        for (var swe : special.entities) specialUnits.add(swe.toHordeEntity());
        if (specialUnits.isEmpty()) { special = null; return; }
        broadcast(WSLang.c("kingdom.special_assault", WSLang.t(special.name).trim()));
    }

    /** Boss d'assaut : ceux réglés sur cet assaut sortent d'une Porte au hasard (le boss final reste réservé à la Porte du Roi). */
    private static void spawnAssaultBosses() {
        var waves = horde.configData.bossWaves;
        if (waves == null || waves.isEmpty() || !horde.configData.useBossWaves) return;
        com.wavesurvivor.horde.model.BossWave finalWave = null;
        for (var w : waves) if (finalWave == null || w.waveNumber > finalWave.waveNumber) finalWave = w;
        List<Portal> open = new ArrayList<>();
        for (Portal p : PORTALS) if (!p.destroyed) open.add(p);
        if (open.isEmpty()) return;
        for (var w : waves) {
            if (w.waveNumber != cycle || BOSS_DONE.contains(w.waveNumber)) continue;
            if (cfg.finalBoss && w == finalWave) continue;
            Portal from = open.get(RNG.nextInt(open.size()));
            // Porte choisie dans l'éditeur (si elle est encore debout), sinon au hasard
            if (w.gate > 0) for (Portal p : open) if (p.num == w.gate) { from = p; break; }
            if (com.wavesurvivor.horde.boss.BossManager.spawnBoss(level, w, from.pos, 6, level.getServer())) {
                BOSS_DONE.add(w.waveNumber);
                broadcast(WSLang.c("kingdom.assault_boss", WSLang.t(w.bossName), WSLang.t("kingdom.dir." + from.dir)));
                // Présage « Chasse royale » : un second boss surgit d'une autre Porte
                if (KingdomOmens.is(KingdomOmens.Omen.ROYAL_HUNT)) {
                    Portal from2 = open.get(RNG.nextInt(open.size()));
                    if (com.wavesurvivor.horde.boss.BossManager.spawnBoss(level, w, from2.pos, 6, level.getServer())) {
                        broadcast(WSLang.c("kingdom.assault_boss", WSLang.t(w.bossName), WSLang.t("kingdom.dir." + from2.dir)));
                    }
                }
            }
        }
    }

    /** Fait apparaître 1 unité de la horde SANS la compter dans l'Assaut (Gardiens, escorte du Catalyseur). */
    private static LivingEntity spawnDetached(HordeEntity t, BlockPos pos, int radius) {
        if (t == null) return null;
        java.util.Set<UUID> before = new java.util.HashSet<>();
        for (Map.Entry<UUID, HordeEntity> en : MobRegistry.entries()) before.add(en.getKey());
        HordeSpawner.spawnMobs(level, t, pos, radius, 1, players);
        LivingEntity found = null;
        for (Map.Entry<UUID, HordeEntity> en : new ArrayList<>(MobRegistry.entries())) {
            if (before.contains(en.getKey())) continue;
            // Toutes les créatures apparues (monture, cavalier…) quittent la horde : aucune ne compte comme unité d'Assaut
            MobRegistry.remove(en.getKey());
            if (found == null && level.getEntity(en.getKey()) instanceof LivingEntity le) found = le;
        }
        if (found != null) return found;
        Entity e = HordeSpawner.spawnSummonFromTemplate(level, t, pos, players);
        return e instanceof LivingEntity le ? le : null;
    }

    /** Complète les Gardiens d'une Porte (×PV, aura lumineuse, nom, restent près de leur Porte). */
    private static void spawnGuardians(Portal p) {
        p.guardians.removeIf(id -> { Entity e = level.getEntity(id); return e == null || !e.isAlive(); });
        HordeEntity t = strongestTemplate();
        for (int i = p.guardians.size(); i < Math.max(0, cfg.guardiansPerGate); i++) {
            LivingEntity g = spawnDetached(t, p.pos, 6);
            if (g == null) break;
            AttributeInstance hp = g.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(hp.getBaseValue() * Math.max(1, cfg.guardianHpMult));
            g.setHealth(g.getMaxHealth());
            g.setCustomName(Component.literal(WSLang.t("kingdom.guardian_name", WSLang.t("kingdom.dir." + p.dir))));
            g.setCustomNameVisible(true);
            g.setGlowingTag(true);
            g.getPersistentData().putBoolean("ws_kingdom_guardian", true);
            if (g instanceof net.minecraft.world.entity.PathfinderMob pm) pm.restrictTo(p.pos, 8);
            if (g instanceof Mob m) m.setPersistenceRequired();
            p.guardians.add(g.getUUID());
            p.guardReward = true;
        }
    }

    private static boolean guardiansAlive(Portal p) {
        for (UUID id : p.guardians) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) return true;
        }
        return false;
    }

    /** Les Gardiens ne s'éloignent pas de leur Porte (rappel à 12 blocs, téléportation à 24). */
    private static void leashGuardians() {
        for (Portal p : PORTALS) {
            if (p.destroyed) continue;
            for (UUID id : p.guardians) {
                if (!(level.getEntity(id) instanceof Mob m) || !m.isAlive()) continue;
                double d = m.position().distanceTo(Vec3.atBottomCenterOf(p.pos));
                if (d > 24) m.teleportTo(p.pos.getX() + 0.5, p.pos.getY(), p.pos.getZ() + 0.5);
                else if (d > 12) {
                    m.setTarget(null);
                    m.getNavigation().moveTo(p.pos.getX() + 0.5, p.pos.getY(), p.pos.getZ() + 0.5, 1.1);
                }
            }
        }
    }

    /** Lien visible (flammes des âmes) entre chaque Gardien et sa Porte. */
    private static void guardianLinks() {
        for (Portal p : PORTALS) {
            if (p.destroyed) continue;
            Vec3 gate = Vec3.atBottomCenterOf(p.pos).add(0, 2.0 * cfg.portalScale, 0);
            for (UUID id : p.guardians) {
                Entity g = level.getEntity(id);
                if (g == null || !g.isAlive()) continue;
                Vec3 a = g.position().add(0, g.getBbHeight() * 0.6, 0);
                for (int i = 1; i < 10; i++) {
                    Vec3 q = a.lerp(gate, i / 10.0);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    /**
     * Vulnérabilité des Portes : attaquable seulement si ses Gardiens sont morts ET qu'elle est débréchée
     * (jusqu'à son plancher de PV). Porte du Roi : attaquable sans limite une fois le boss final vaincu.
     */
    private static void updateGates() {
        for (Portal p : PORTALS) {
            if (p.destroyed || !(level.getEntity(p.id) instanceof BrecheEntity b)) continue;
            if (p.structure != null && b.getHealth() < b.getMaxHealth() * 0.5f) p.structure.crack();
            boolean guarded = guardiansAlive(p);
            if (!guarded && p.guardReward) {
                p.guardReward = false;
                KingdomTreasury.reward(KingdomTreasury.Res.ESSENCE, 5, "kingdom.essence.guardians");
            }
            boolean vulnerable;
            float floor = 0f;
            if (p.isFinal && !finalBossSpawned) {
                // Avant son boss : la Porte du Roi se brèche comme les autres, mais ne descend pas sous 35 % (son boss arrive alors)
                floor = Math.max(p.floor, b.getMaxHealth() * KING_LOW);
                vulnerable = !guarded && p.breached && !kingLow;
                if (!kingLow && b.getHealth() <= b.getMaxHealth() * KING_LOW + 0.5f) {
                    kingLow = true;
                    p.breached = false;
                    broadcast(WSLang.c("kingdom.king_low"));
                    KingdomTreasury.reward(KingdomTreasury.Res.ESSENCE, 5, "kingdom.essence.breach");
                } else if (p.breached && b.getHealth() <= floor + 0.5f) {
                    p.breached = false; // morceau de brèche épuisé avant les 35 % : elle se referme, comme les autres Portes
                    broadcast(WSLang.c("kingdom.gate_resealed", WSLang.t("kingdom.dir." + p.dir)));
                    KingdomTreasury.reward(KingdomTreasury.Res.ESSENCE, 5, "kingdom.essence.breach");
                }
            } else if (p.isFinal) {
                vulnerable = !guarded && bossDefeated;
            } else {
                vulnerable = !guarded && p.breached;
                floor = p.floor;
            }
            b.setShielded(!vulnerable);
            b.setHpFloor(vulnerable ? floor : 0f);
            if (p.breached && !p.isFinal && b.getHealth() <= p.floor + 0.5f) {
                p.breached = false;
                broadcast(WSLang.c("kingdom.gate_resealed", WSLang.t("kingdom.dir." + p.dir)));
                KingdomTreasury.reward(KingdomTreasury.Res.ESSENCE, 5, "kingdom.essence.breach");
            }
        }
    }

    private static void clearCatalyst() {
        for (UUID id : CATS) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        CATS.clear();
        CAT_GATES.clear();
        for (UUID id : ESCORT) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        ESCORT.clear();
    }

    /** Objectif du Calme : un Catalyseur apparaît entre une Porte (au hasard) et le Monolithe, avec son escorte.
     *  Arcaniste présent : 20 % de chance d'un second Catalyseur sur une autre Porte. */
    private static void spawnCatalyst() {
        clearCatalyst();
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < PORTALS.size(); i++) {
            Portal c0 = PORTALS.get(i);
            // La Porte du Roi peut aussi recevoir le Catalyseur tant que son boss n'est pas appelé
            if (!c0.destroyed && (!c0.isFinal || (!finalBossSpawned && !kingLow))) candidates.add(i);
        }
        if (candidates.isEmpty()) return;
        int gi = candidates.get(RNG.nextInt(candidates.size()));
        if (!spawnCatalystAt(gi)) return;
        candidates.remove(Integer.valueOf(gi));
        if (!candidates.isEmpty() && KingdomRoles.present(KingdomRoles.Role.ARCANIST) && RNG.nextDouble() < 0.20) {
            int g2 = candidates.get(RNG.nextInt(candidates.size()));
            if (spawnCatalystAt(g2)) broadcast(WSLang.c("kingdom.role.arcanist.second"));
        }
    }

    private static boolean spawnCatalystAt(int gi) {
        Portal p = PORTALS.get(gi);
        BlockPos aim = new BlockPos((int) Math.round(p.pos.getX() + (center.getX() - p.pos.getX()) * 0.3), p.pos.getY(),
                (int) Math.round(p.pos.getZ() + (center.getZ() - p.pos.getZ()) * 0.3));
        BrecheEntity c = com.wavesurvivor.registry.ModEntities.BRECHE.get().create(level);
        if (c == null) return false;
        BlockPos at = SpawnZone.pick(level, aim, 4, c.getType());
        c.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        c.setCatalyst(true);
        c.setTheme(resolveTheme(cfg.portalTheme, horde.hordeName));
        AttributeInstance hp = c.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(Math.max(10, cfg.catalystHealth));
        c.setHealth(c.getMaxHealth());
        c.setCustomName(Component.literal(WSLang.t("kingdom.catalyst_name")));
        c.setCustomNameVisible(true);
        if (!level.addFreshEntity(c)) return false;
        CATS.add(c.getUUID());
        CAT_GATES.add(gi);
        for (int i = 0; i < Math.max(0, cfg.catalystEscort); i++) {
            LivingEntity g = spawnDetached(pickTemplate(p.num), at, 4);
            if (g == null) break;
            if (g instanceof net.minecraft.world.entity.PathfinderMob pm) pm.restrictTo(at, 6);
            g.getPersistentData().putBoolean("ws_kingdom_escort", true);
            ESCORT.add(g.getUUID());
        }
        com.wavesurvivor.network.EventFeedPacket.toAll(level.getServer(), WSLang.c("kingdom.catalyst_appeared", KingdomObjectives.dir8(at.getX(), at.getZ()),
                (int) Math.round(Math.sqrt(at.distSqr(center)))), "minecraft:end_crystal", com.wavesurvivor.network.EventFeedPacket.ARCANE, true);
        level.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3f, 0.7f);
        return true;
    }

    /** Catalyseur détruit → sa Porte est débréchée (attaquable jusqu'à -breachPercent % de ses PV max). */
    private static void checkCatalyst() {
        for (int i = CATS.size() - 1; i >= 0; i--) {
            Entity e = level.getEntity(CATS.get(i));
            if (e != null && e.isAlive()) continue;
            int gate = CAT_GATES.get(i);
            CATS.remove(i);
            CAT_GATES.remove(i);
            breachGate(gate);
        }
    }

    private static void breachGate(int gate) {
        Portal p = gate >= 0 && gate < PORTALS.size() ? PORTALS.get(gate) : null;
        if (p == null || p.destroyed || !(level.getEntity(p.id) instanceof BrecheEntity b)) return;
        p.breached = true;
        p.floor = (float) Math.max(0, b.getHealth() - b.getMaxHealth() * Math.max(1, cfg.breachPercent) / 100.0);
        com.wavesurvivor.network.EventFeedPacket.toAll(level.getServer(), WSLang.c("kingdom.gate_breached", WSLang.t("kingdom.dir." + p.dir), (int) Math.round(cfg.breachPercent)),
                "minecraft:crying_obsidian", com.wavesurvivor.network.EventFeedPacket.ARCANE, true);
        KingdomTreasury.reward(KingdomTreasury.Res.ESSENCE, 3, "kingdom.essence.catalyst");
        level.playSound(null, p.pos, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3f, 0.5f);
    }

    /** Il ne reste qu'une Porte : elle devient la Porte du Roi. */
    private static void markFinal() {
        finalMarked = true;
        for (Portal p : PORTALS) {
            if (p.destroyed) continue;
            p.isFinal = true;
            p.breached = false;
            if (level.getEntity(p.id) instanceof BrecheEntity b) {
                b.setCustomName(Component.literal(WSLang.t("kingdom.king_gate_name")));
            }
            broadcast(WSLang.c("kingdom.king_gate"));
            broadcast(WSLang.c("kingdom.king_gate_rule", finalBossWave(), Math.round(KING_LOW * 100)));
        }
    }

    /** Au prochain Assaut, le boss final de la horde sort de la Porte du Roi. */
    private static void spawnFinalBoss() {
        finalBossSpawned = true;
        Portal fin = null;
        for (Portal p : PORTALS) if (!p.destroyed && p.isFinal) fin = p;
        var waves = horde.configData.bossWaves;
        com.wavesurvivor.horde.model.BossWave last = null;
        if (waves != null) for (var w : waves) if (last == null || w.waveNumber > last.waveNumber) last = w;
        // Arène du Roi : le boss ne sort pas, il attend dans son arène ; la Porte s'ouvrira une fois cet Assaut repoussé
        if (cfg.raidArena && fin != null && last != null) {
            raidBoss = last;
            raidAwait = true;
            BOSS_DONE.add(last.waveNumber);
            broadcast(WSLang.c("raid.await", WSLang.t(last.bossName)));
            for (ServerPlayer pl : level.players()) level.playSound(null, pl.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1f, 0.5f);
            for (var w : waves) {
                if (w == last || w.waveNumber == cycle || BOSS_DONE.contains(w.waveNumber)) continue;
                if (com.wavesurvivor.horde.boss.BossManager.spawnBoss(level, w, fin.pos, 8, level.getServer())) {
                    BOSS_DONE.add(w.waveNumber);
                    broadcast(WSLang.c("kingdom.assault_boss", WSLang.t(w.bossName), WSLang.t("kingdom.dir." + fin.dir)));
                }
            }
            return;
        }
        boolean ok = fin != null && last != null
                && com.wavesurvivor.horde.boss.BossManager.spawnBoss(level, last, fin.pos, 6, level.getServer());
        if (!ok) {
            bossDefeated = true; // pas de boss configuré : la Porte est directement attaquable une fois ses Gardiens tombés
            return;
        }
        broadcast(WSLang.c("kingdom.final_boss", WSLang.t(last.bossName)));
        for (ServerPlayer pl : level.players()) level.playSound(null, pl.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1f, 0.8f);
        BOSS_DONE.add(last.waveNumber);
        // Les mini-boss pas encore apparus l'accompagnent (ceux de cet Assaut sortiront juste après, normalement)
        for (var w : waves) {
            if (w == last || w.waveNumber == cycle || BOSS_DONE.contains(w.waveNumber)) continue;
            if (com.wavesurvivor.horde.boss.BossManager.spawnBoss(level, w, fin.pos, 8, level.getServer())) {
                BOSS_DONE.add(w.waveNumber);
                broadcast(WSLang.c("kingdom.assault_boss", WSLang.t(w.bossName), WSLang.t("kingdom.dir." + fin.dir)));
            }
        }
    }

    // ─── Unités ───

    /** Fait apparaître {@code n} unités de la horde autour de {@code pos}. @return unités réellement apparues. */
    private static int spawnUnits(Portal p, int n) {
        // Les unités de siège font partie de la liste Mobs (rôle « siege_role ») : elles sortent comme les autres.
        // Seules les unités rattachées à cette Porte (ou à toutes) peuvent en sortir.
        HordeEntity t = pickTemplate(p.num);
        // Contrepartie du Récupérateur / présage « Pluie de fer » : 25 % des groupes sont des unités de siège
        if (special == null && (KingdomRoles.present(KingdomRoles.Role.SCAVENGER) || KingdomOmens.is(KingdomOmens.Omen.IRON_RAIN))
                && RNG.nextDouble() < 0.25) {
            HordeEntity s = pickSiegeTemplate(p.num);
            if (s != null) t = s;
        }
        if (t == null) return 0;
        return HordeSpawner.spawnMobs(level, t, p.pos, 5, n, players);
    }

    /** Unité tirable à cette vague : pas un Gardien, dans sa fenêtre de vagues, quantité de base ou augmentation non nulle. */
    private static boolean activeAt(HordeEntity u, int wave) {
        if (u == null || u.kingdomGuardian) return false;
        if (u.minWave > 0 && wave < u.minWave) return false;
        if (u.maxWave > 0 && wave > u.maxWave) return false;
        return u.baseCount > 0 || u.countIncrement > 0;
    }

    /** Unité de siège (rôle de siège renseigné) autorisée à cette Porte ; null si la horde n'en a pas. */
    private static HordeEntity pickSiegeTemplate(int gate) {
        var list = horde.configData.hordeEntities;
        if (list == null) return null;
        int wave = Math.max(1, Math.min(cycle, Math.max(1, horde.configData.totalWaves)));
        List<HordeEntity> ok = new ArrayList<>();
        for (HordeEntity u : list) {
            if (u != null && u.siegeRole != null && !u.siegeRole.isEmpty() && u.allowedAtGate(gate) && activeAt(u, wave)) ok.add(u);
        }
        return ok.isEmpty() ? null : ok.get(RNG.nextInt(ok.size()));
    }

    /** La horde en cours contient-elle des unités de siège ? (contrats du Chasseur de primes) */
    public static boolean hasSiegeUnits() {
        if (!active || horde == null || horde.configData == null || horde.configData.hordeEntities == null) return false;
        for (HordeEntity u : horde.configData.hordeEntities) {
            if (u != null && u.siegeRole != null && !u.siegeRole.isEmpty()) return true;
        }
        return false;
    }

    /** L'Assaut suivant (cycle + 1) fera-t-il apparaître un boss d'assaut ? (présage « Chasse royale ») */
    public static boolean nextAssaultHasBoss() {
        if (!active || horde == null || horde.configData == null) return false;
        var waves = horde.configData.bossWaves;
        if (waves == null || waves.isEmpty() || !horde.configData.useBossWaves) return false;
        com.wavesurvivor.horde.model.BossWave finalWave = null;
        for (var w : waves) if (finalWave == null || w.waveNumber > finalWave.waveNumber) finalWave = w;
        int next = cycle + 1;
        for (var w : waves) {
            if (w.waveNumber != next || BOSS_DONE.contains(w.waveNumber)) continue;
            if (cfg.finalBoss && w == finalWave) continue;
            return true;
        }
        return false;
    }

    private static int spawnUnitsUnused(BlockPos pos, int n) {
        // Les unités de siège font partie de la liste Mobs (rôle « siege_role ») : elles sortent comme les autres
        HordeEntity t = pickTemplate();
        if (t == null) return 0;
        return HordeSpawner.spawnMobs(level, t, pos, 5, n, players);
    }

    /** Monstre tiré dans la liste de la horde, pondéré par sa quantité au « cycle » courant (cycle = vague). */
    private static HordeEntity pickTemplate() { return pickTemplate(0); }

    // ─── Accès pour les objectifs du Calme (KingdomObjectives) ───

    static BlockPos objCenter() { return center; }

    static int objClaimRadius() { return cfg == null ? 16 : Math.max(4, cfg.claimRadius); }

    /** Positions des Portes encore debout. */
    static List<BlockPos> objGates() {
        List<BlockPos> out = new ArrayList<>();
        for (Portal p : PORTALS) if (!p.destroyed) out.add(p.pos);
        return out;
    }

    /** Une unité de la horde, hors vague (non comptée dans l'Assaut — garde, attaquants d'un convoi…). */
    static LivingEntity spawnObjectiveUnit(BlockPos pos, int radius) {
        HordeEntity t = pickTemplate();
        return t == null ? null : spawnDetached(t, pos, radius);
    }

    /** Tirage pondéré d'une unité autorisée à la Porte n° {@code gate} (0 = n'importe laquelle) ; null si aucune. */
    private static HordeEntity pickTemplate(int gate) {
        // Assaut spécial : ses unités remplacent le mélange habituel
        if (special != null && !specialUnits.isEmpty()) {
            int tw = 0;
            for (HordeEntity u : specialUnits) if (u.allowedAtGate(gate)) tw += Math.max(1, u.getCountForWave(1, players));
            if (tw <= 0) return null; // aucune unité de cet assaut spécial pour cette Porte
            int r = RNG.nextInt(tw);
            for (HordeEntity u : specialUnits) {
                if (!u.allowedAtGate(gate)) continue;
                r -= Math.max(1, u.getCountForWave(1, players));
                if (r < 0) return u;
            }
            return null;
        }
        var list = horde.configData.hordeEntities;
        if (list == null || list.isEmpty()) return null;
        int wave = Math.max(1, Math.min(cycle, Math.max(1, horde.configData.totalWaves)));
        int total = 0;
        int[] w = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            // Unités « ⛨ Gardien » : réservées aux Gardiens des Portes, jamais tirées dans un Assaut
            w[i] = list.get(i).allowedAtGate(gate) && !list.get(i).kingdomGuardian ? Math.max(0, list.get(i).getCountForWave(wave, players)) : 0;
            total += w[i];
        }
        if (total <= 0) {
            // Repli : seulement des unités présentes à cette vague (fenêtre min/max) et qui ne sont pas des Gardiens
            List<HordeEntity> ok = new ArrayList<>();
            for (HordeEntity u : list) if (u.allowedAtGate(gate) && activeAt(u, wave)) ok.add(u);
            return ok.isEmpty() ? null : ok.get(RNG.nextInt(ok.size()));
        }
        int r = RNG.nextInt(total);
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) return list.get(i);
        }
        return null;
    }

    private static HordeEntity pickTemplateUnfilteredUnused() {
        // Assaut spécial : ses unités remplacent le mélange habituel
        if (special != null && !specialUnits.isEmpty()) {
            int tw = 0;
            for (HordeEntity u : specialUnits) tw += Math.max(1, u.getCountForWave(1, players));
            int r = RNG.nextInt(Math.max(1, tw));
            for (HordeEntity u : specialUnits) {
                r -= Math.max(1, u.getCountForWave(1, players));
                if (r < 0) return u;
            }
            return specialUnits.get(specialUnits.size() - 1);
        }
        var list = horde.configData.hordeEntities;
        if (list == null || list.isEmpty()) return null;
        int wave = Math.max(1, Math.min(cycle, Math.max(1, horde.configData.totalWaves)));
        int total = 0;
        int[] w = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            w[i] = Math.max(0, list.get(i).getCountForWave(wave, players));
            total += w[i];
        }
        if (total <= 0) return list.get(RNG.nextInt(list.size()));
        int r = RNG.nextInt(total);
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) return list.get(i);
        }
        return list.get(list.size() - 1);
    }

    /** Les unités visent le joueur le plus proche (portée étendue) ; sans joueur, elles marchent vers le Monolithe. */
    private static void retarget() {
        TargetingConditions tc = TargetingConditions.forCombat().range(160);
        for (Map.Entry<UUID, HordeEntity> en : MobRegistry.entries()) {
            Entity e = level.getEntity(en.getKey());
            if (!(e instanceof Mob m) || !m.isAlive()) continue;
            if (KingdomMarch.isMarching(en.getKey())) continue; // encore en marche : le couloir décide
            // Étape 5 : un sapeur qui a un mur à viser ne se laisse pas distraire par les joueurs
            if ("sapper".equals(KingdomSiege.roleOf(m)) && KingdomClaim.nearestPlaced(m.blockPosition(), KingdomClaim.radius() + 10) != null) continue;
            AttributeInstance fr = m.getAttribute(Attributes.FOLLOW_RANGE);
            if (fr != null && fr.getBaseValue() < 96) fr.setBaseValue(96);
            if (com.wavesurvivor.altar.AltarDefense.isProfaner(en.getKey())) continue; // les Profanateurs gardent le Monolithe pour cible
            // Un soldat du royaume proche (10 blocs) devient la cible : les soldats encaissent vraiment
            com.wavesurvivor.entity.KingdomSoldier ns = null;
            double nd = 100;
            for (var sol : level.getEntitiesOfClass(com.wavesurvivor.entity.KingdomSoldier.class, m.getBoundingBox().inflate(10))) {
                double dd = sol.distanceToSqr(m);
                if (dd < nd) { nd = dd; ns = sol; }
            }
            if (ns != null && !(m.getTarget() instanceof com.wavesurvivor.entity.KingdomSoldier)) {
                m.setTarget(ns);
                continue;
            }
            if (m.getTarget() != null && m.getTarget().isAlive()) continue;
            Player nearest = level.getNearestPlayer(tc, m);
            if (nearest != null) m.setTarget(nearest);
            else m.getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.0);
        }
    }

    /** Petite brèche pendant le Calme : un point au hasard sur l'anneau libère quelques unités. */
    // ─── Brèches du Calme : une faille s'ouvre dans le sol, puis les unités en sortent une à une ───

    private static final class Rift {
        final UUID id;
        final BlockPos pos;
        final long start;
        int left;
        long nextAt;
        long closeAt;
        /** Fin du maintien grand ouvert après la dernière sortie (3 s). */
        long holdUntil;

        Rift(UUID id, BlockPos pos, long start, int left) {
            this.id = id;
            this.pos = pos;
            this.start = start;
            this.left = left;
            this.nextAt = start + 40; // 2 s d'ouverture avant la première sortie
        }
    }

    private static final List<Rift> RIFTS = new ArrayList<>();

    private static void smallBreach(long now) {
        double a = RNG.nextDouble() * Math.PI * 2;
        double d = cfg.ringMin + RNG.nextDouble() * Math.max(1, cfg.ringMax - cfg.ringMin);
        BlockPos aim = center.offset((int) Math.round(Math.cos(a) * d), 0, (int) Math.round(Math.sin(a) * d));
        // Portes à coordonnées fixes (map custom) : la faille s'ouvre entre une Porte au hasard et le Monolithe
        if ("fixed".equalsIgnoreCase(cfg.gatePlacement)) {
            List<Portal> alive = new ArrayList<>();
            for (Portal p : PORTALS) if (!p.destroyed) alive.add(p);
            if (!alive.isEmpty()) {
                Portal p = alive.get(RNG.nextInt(alive.size()));
                double vx = center.getX() - p.pos.getX(), vz = center.getZ() - p.pos.getZ(), len = Math.max(1, Math.sqrt(vx * vx + vz * vz));
                double step = Math.min(len * 0.6, 8 + RNG.nextDouble() * 6);
                aim = p.pos.offset((int) Math.round(vx / len * step + RNG.nextGaussian() * 3), 0, (int) Math.round(vz / len * step + RNG.nextGaussian() * 3));
                a = Math.atan2(aim.getZ() - center.getZ(), aim.getX() - center.getX());
            }
        }
        BlockPos at = SpawnZone.pick(level, aim, 4, null);
        int n = Math.max(1, cfg.smallBreachUnits);
        // Contrepartie de l'Arcaniste : brèches du Calme +50 % d'unités
        if (KingdomRoles.present(KingdomRoles.Role.ARCANIST)) n = (int) Math.ceil(n * 1.5);
        BrecheEntity r = com.wavesurvivor.registry.ModEntities.BRECHE.get().create(level);
        if (r == null) return;
        r.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        r.setGroundRift(true);
        r.setRiftEyes(n);
        r.setRiftOpen(0f);
        r.setTheme(resolveTheme(cfg.portalTheme, horde.hordeName));
        r.setSilent(true);
        if (!level.addFreshEntity(r)) return;
        RIFTS.add(new Rift(r.getUUID(), at, now, n));
        level.playSound(null, at, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.6f, 0.8f);
        com.wavesurvivor.network.EventFeedPacket.toAll(level.getServer(), WSLang.c("kingdom.small_breach", WSLang.t("kingdom.dir." + directionOf(a)), n),
                "minecraft:ender_eye", com.wavesurvivor.network.EventFeedPacket.ARCANE, false);
    }

    /** Unité d'une brèche du Calme : tirée dans la liste « Brèches » (pondérée par sa quantité), sinon mélange de la horde. */
    private static HordeEntity pickCalmTemplate() {
        var list = cfg.calmBreachUnits;
        if (list == null || list.isEmpty()) return pickTemplate();
        int tw = 0;
        for (var u : list) tw += Math.max(1, u.count);
        int r = RNG.nextInt(Math.max(1, tw));
        for (var u : list) {
            r -= Math.max(1, u.count);
            if (r < 0) return u.toHordeEntity();
        }
        return list.get(list.size() - 1).toHordeEntity();
    }

    /** Ouverture (2 s) → sortie des unités (une toutes les 0,4 s, une paire d'yeux s'éteint) → fermeture (2 s). */
    private static void tickRifts(long now) {
        if (RIFTS.isEmpty()) return;
        java.util.Iterator<Rift> it = RIFTS.iterator();
        while (it.hasNext()) {
            Rift rf = it.next();
            if (!(level.getEntity(rf.id) instanceof BrecheEntity b)) { it.remove(); continue; }
            long age = now - rf.start;
            if (rf.closeAt > 0) {
                b.setRiftOpen(Math.max(0f, (rf.closeAt - now) / 40f));
                if (now >= rf.closeAt) { b.discard(); it.remove(); KingdomRoleExtras.riftClosed(level, rf.pos); }
                continue;
            }
            b.setRiftOpen(Math.min(1f, age / 40f));
            if (age < 40 && age % 4 == 0) {
                // Le sol se fend : débris du bloc sous la faille
                net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(rf.pos.below());
                if (!ground.isAir()) level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground),
                        rf.pos.getX() + 0.5, rf.pos.getY() + 0.1, rf.pos.getZ() + 0.5, 12, 1.2, 0.05, 1.2, 0.1);
            }
            if (rf.left > 0 && now >= rf.nextAt) {
                HordeEntity t = pickCalmTemplate();
                if (t != null) HordeSpawner.spawnMobs(level, t, rf.pos, 1, 1, players);
                rf.left--;
                b.setRiftEyes(rf.left);
                rf.nextAt = now + 8;
                level.sendParticles(ParticleTypes.DRAGON_BREATH, rf.pos.getX() + 0.5, rf.pos.getY() + 0.4, rf.pos.getZ() + 0.5, 25, 0.4, 0.6, 0.4, 0.03);
                level.playSound(null, rf.pos, SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.8f, 0.6f);
            }
            if (rf.left <= 0) {
                // Toutes les unités sont sorties : la faille reste grande ouverte 3 s, puis se referme en 2 s
                if (rf.holdUntil == 0) rf.holdUntil = now + 60;
                else if (now >= rf.holdUntil) rf.closeAt = now + 40;
            }
            // Spores et braises qui s'élèvent de la faille (ambiance « monde à l'envers »)
            if (age % 3 == 0) {
                level.sendParticles(ParticleTypes.WHITE_ASH, rf.pos.getX() + 0.5, rf.pos.getY() + 0.3, rf.pos.getZ() + 0.5, 6, 1.6, 0.4, 1.6, 0.01);
                level.sendParticles(ParticleTypes.SMALL_FLAME, rf.pos.getX() + 0.5, rf.pos.getY() + 0.15, rf.pos.getZ() + 0.5, 2, 0.8, 0.05, 0.8, 0.01);
            }
        }
    }


    private static String directionOf(double angle) {
        // angle 0 = est (cos), sens horaire vu du dessus (z vers le sud) ; 8 secteurs de 45°
        double deg = (Math.toDegrees(angle) + 360 + 22.5) % 360;
        String[] d8 = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
        return d8[(int) (deg / 45) % 8];
    }

    // ─── Boucliers / affichage ───

    private static void setShields(boolean on) {
        for (Portal p : PORTALS) {
            if (p.destroyed) continue;
            if (level.getEntity(p.id) instanceof BrecheEntity b) b.setShielded(on);
        }
    }

    private static void shieldParticles() {
        for (Portal p : PORTALS) {
            if (p.destroyed) continue;
            if (!(level.getEntity(p.id) instanceof BrecheEntity b) || !b.isShielded()) continue; // seulement les Portes protégées
            double r = 1.6 * cfg.portalScale;
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10 + level.getGameTime() * 0.1;
                level.sendParticles(ParticleTypes.END_ROD, p.pos.getX() + 0.5 + Math.cos(a) * r, p.pos.getY() + 1.5 * cfg.portalScale,
                        p.pos.getZ() + 0.5 + Math.sin(a) * r, 1, 0, 0.1, 0, 0);
            }
        }
    }

    private static void updateBar(long now, int alive) {
        if (bar == null) return;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) bar.addPlayer(p);
        int portalsLeft = 0;
        for (Portal p : PORTALS) if (!p.destroyed) portalsLeft++;
        if (phase == Phase.ASSAULT) {
            int total = 0, left = alive;
            for (Portal p : PORTALS) { total += p.budget; left += Math.max(0, p.budget - p.spawned); }
            bar.setName(Component.literal(WSLang.t("kingdom.bar_assault", cycle, left, portalsLeft)));
            bar.setColor(BossEvent.BossBarColor.RED);
            bar.setProgress(total > 0 ? Math.max(0f, Math.min(1f, left / (float) (total + alive))) : 0f);
        } else {
            long secs = Math.max(0, (phaseEndTick - now) / 20);
            String time = String.format("%d:%02d", secs / 60, secs % 60);
            bar.setName(Component.literal(cycle == 0
                    ? WSLang.t("kingdom.bar_prepare", time)
                    : WSLang.t("kingdom.bar_calm", cycle, time, portalsLeft)));
            bar.setColor(BossEvent.BossBarColor.GREEN);
            long full = cycle == 0 ? 200 : Math.max(10, cfg.calmSeconds) * 20L;
            bar.setProgress(Math.max(0f, Math.min(1f, (phaseEndTick - now) / (float) full)));
        }
    }

    private static void broadcast(Component msg) {
        if (level == null) return;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) p.sendSystemMessage(msg);
    }

    // ─── Commandes ───

    /** /ws skip : Assaut → Calme (unités en vie retirées) ou Calme → Assaut. */
    public static boolean skipPhase(MinecraftServer server) {
        if (!active) return false;
        long now = server.getTickCount();
        if (phase == Phase.ASSAULT) {
            for (Map.Entry<UUID, HordeEntity> en : new ArrayList<>(MobRegistry.entries())) {
                Entity e = level.getEntity(en.getKey());
                if (e != null) e.discard();
            }
            MobRegistry.clear();
            for (Portal p : PORTALS) p.spawned = p.budget;
            com.wavesurvivor.network.EventFeedPacket.toAll(level.getServer(), WSLang.c("kingdom.skip_to_calm"),
                    "minecraft:feather", com.wavesurvivor.network.EventFeedPacket.KINGDOM, false);
            startCalm(now);
        } else {
            com.wavesurvivor.network.EventFeedPacket.toAll(level.getServer(), WSLang.c("kingdom.skip_to_assault"),
                    "minecraft:iron_sword", com.wavesurvivor.network.EventFeedPacket.KINGDOM, false);
            startAssault(now);
        }
        return true;
    }

    /** Arrêt complet (fin de partie, /ws stop, défaite) : portails retirés, barre masquée. */
    public static void stop(MinecraftServer server) {
        RaidManager.stop(); // joueurs de l'arène renvoyés chez eux avant de tout restaurer
        raidAwait = false;
        raidBoss = null;
        if (level != null) {
            for (Portal p : PORTALS) {
                Entity e = level.getEntity(p.id);
                if (e != null) e.discard();
                for (UUID gid : p.guardians) { Entity g = level.getEntity(gid); if (g != null) g.discard(); }
                if (p.structure != null) p.structure.restore(); // terrain d'origine restauré
                if (p.corruption != null) p.corruption.restore(); // sol purifié
            }
            clearCatalyst();
            for (Rift rf : RIFTS) { Entity re = level.getEntity(rf.id); if (re != null) re.discard(); }
        }
        RIFTS.clear();
        releaseChunks();
        KILLED.clear();
        KingdomTownHall.stop();
        if (level != null && prevDaylight != null) {
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(prevDaylight, server);
        }
        prevDaylight = null;
        if (prevKeepInv != null) {
            server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(prevKeepInv, server);
        }
        prevKeepInv = null;
        STRUCT.clear();
        PORTALS.clear();
        special = null;
        specialUnits.clear();
        KingdomMarch.clear();
        KingdomClaim.stop();
        KingdomDefenses.clear();
        KingdomSiege.clear();
        KingdomObjectives.clear();
        KingdomTreasury.stop(server);
        KingdomRoles.stop(server);
        KingdomLives.stop(server);
        KingdomDemolition.clear(server);
        KingdomRoleExtras.clear();
        KingdomOmens.clear();
        KingdomAlchemy.clear();
        if (bar != null) { bar.removeAllPlayers(); bar = null; }
        active = false;
        level = null;
        horde = null;
    }
}
