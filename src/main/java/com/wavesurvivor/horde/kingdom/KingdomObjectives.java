package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.config.model.HordeConfigMultiData.CalmObjectives;
import com.wavesurvivor.config.model.HordeConfigMultiData.Reward;
import com.wavesurvivor.entity.GisementEntity;
import com.wavesurvivor.horde.model.ChaosEvent;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * OBJECTIFS DU CALME (mode Kingdom) : à chaque Calme (ou 1 sur N), une mission FACULTATIVE tirée parmi celles activées
 * dans l'éditeur (onglet Calme). Tout est réglable (créatures, PV, durée, récompenses, butin) :
 *  - CONVOI : une caravane part d'une Porte vers le Monolithe, des monstres l'attaquent ; arrivée = ressources.
 *  - CHAMPION DE PORTE : un élite et sa garde sortent d'une Porte ; abattu = Essence + butin (clé de coffre par défaut).
 *  - TRÉSOR ENFOUI : un coffre caché dans le sol, une boussole le désigne ; trouvé = monnaie + butin dans le coffre.
 *  - PURIFICATION : des foyers de corruption ; aucun purifié = Assaut suivant plus fort, tous = plus faible.
 * L'Assaut suivant clôt l'objectif s'il est encore en cours.
 */
public final class KingdomObjectives {

    private KingdomObjectives() {}

    enum Type { CONVOY, CHAMPION, TREASURE, PURIFY }

    private static final Random RNG = new Random();
    private static final String COMPASS_TAG = "ws_treasure_compass";

    private static ServerLevel level;
    private static CalmObjectives cfg;

    /** Objectif tiré pour ce Calme, en attente de son apparition. */
    private static Type pending;
    private static long startAt = -1;

    /** Objectif en cours. */
    private static Type active;
    private static long endAt;
    private static boolean done;

    // Convoi
    private static UUID convoy;
    private static int convoyMaxDist;
    private static boolean convoySecondWave;
    // Champion
    private static UUID champion;
    // Convoi / champion : unités liées (attaquants, garde)
    private static final List<UUID> LINKED = new ArrayList<>();
    // Trésor
    private static BlockPos chest;
    // Purification
    private static final List<UUID> FOYERS = new ArrayList<>();
    private static int foyerTotal, foyerPurified;

    /** Effet de la Purification sur le prochain Assaut (1 = aucun). */
    private static double nextAssaultMult = 1.0;

    // ─── Cycle ───

    /** Début du Calme : tirage d'un objectif (s'il y en a un d'activé et que ce Calme est concerné). */
    static void onCalm(ServerLevel lvl, long now, int cycle, CalmObjectives c) {
        level = lvl;
        cfg = c;
        pending = null;
        if (c == null || !c.enabled) return;
        int every = Math.max(1, c.every);
        if (cycle % every != 0) return;
        List<Type> pool = new ArrayList<>();
        if (c.convoy != null && c.convoy.enabled) pool.add(Type.CONVOY);
        if (c.champion != null && c.champion.enabled) pool.add(Type.CHAMPION);
        if (c.treasure != null && c.treasure.enabled) pool.add(Type.TREASURE);
        if (c.purify != null && c.purify.enabled) pool.add(Type.PURIFY);
        if (pool.isEmpty()) return;
        pending = pool.get(RNG.nextInt(pool.size()));
        startAt = now + Math.max(0, c.delaySeconds) * 20L;
    }

    /** Début de l'Assaut : l'objectif encore en cours est clos ; renvoie le multiplicateur d'unités de cet Assaut. */
    static double onAssault() {
        pending = null;
        if (active != null) finish(false, true);
        double m = nextAssaultMult;
        nextAssaultMult = 1.0;
        return m;
    }

    static void clear() {
        if (active != null && level != null) cleanup();
        clearTrackers();
        active = null;
        pending = null;
        nextAssaultMult = 1.0;
        LINKED.clear();
        FOYERS.clear();
        convoy = null;
        champion = null;
        chest = null;
        level = null;
    }

    static void tick(long now) {
        if (level == null) return;
        if (pending != null && now >= startAt) {
            Type t = pending;
            pending = null;
            try {
                start(t, now);
            } catch (Exception e) {
                com.wavesurvivor.WaveSurvivorMod.LOGGER.warn("[Kingdom] Objectif {} impossible : {}", t, e.getMessage());
                active = null;
            }
        }
        if (active == null || done) return;
        if (now >= endAt) {
            finish(false, false);
            return;
        }
        if (now % 10 == 0) {
            switch (active) {
                case CONVOY -> tickConvoy(now);
                case CHAMPION -> tickChampion();
                case TREASURE -> tickTreasure(now);
                case PURIFY -> tickPurify();
            }
            if (active != null && !done) updateTrackers();
        }
        if (active != null && !done && now % 20 == 0) hud(now);
    }

    // ─── Démarrage ───

    private static void start(Type t, long now) {
        BlockPos center = KingdomManager.objCenter();
        List<BlockPos> gates = KingdomManager.objGates();
        if (center == null) return;
        active = t;
        done = false;
        LINKED.clear();
        switch (t) {
            case CONVOY -> {
                // Point de départ libre : un point au hasard entre le royaume et les Portes
                int claim = KingdomManager.objClaimRadius();
                double a = RNG.nextDouble() * Math.PI * 2;
                int d = claim + 26 + RNG.nextInt(16);
                BlockPos from = ground(new BlockPos(center.getX() + (int) Math.round(Math.cos(a) * d), center.getY(),
                        center.getZ() + (int) Math.round(Math.sin(a) * d)));
                Mob m = create(cfg.convoy.entityType, "minecraft:wandering_trader", from);
                if (m == null) { active = null; return; }
                m.setCustomName(Component.literal(WSLang.t("obj.convoy.name")));
                m.setCustomNameVisible(true);
                setHealth(m, cfg.convoy.health);
                setAttr(m, Attributes.FOLLOW_RANGE, 96);
                setAttr(m, Attributes.MOVEMENT_SPEED, Math.max(0.05, Math.min(0.6, cfg.convoy.speed)));
                stripGoals(m); // plus de fuite ni d'errance : il ne fait que marcher vers le Monolithe
                m.setPersistenceRequired();
                m.setGlowingTag(true);
                if (m instanceof net.minecraft.world.entity.npc.WanderingTrader wt) wt.setDespawnDelay(cfg.convoy.seconds * 20 + 400);
                level.addFreshEntity(m);
                convoy = m.getUUID();
                convoyMaxDist = (int) Math.max(1, Math.sqrt(from.distSqr(center)));
                convoySecondWave = false;
                spawnLinked(from, Math.max(0, cfg.convoy.attackers) / 2 + Math.max(0, cfg.convoy.attackers) % 2, m);
                endAt = now + Math.max(20, cfg.convoy.seconds) * 20L;
                broadcast(WSLang.c("obj.start.convoy", dir8(from.getX(), from.getZ()), Math.max(20, cfg.convoy.seconds)));
            }
            case CHAMPION -> {
                if (gates.isEmpty()) { active = null; return; }
                BlockPos g = gates.get(RNG.nextInt(gates.size()));
                Vec3 dir = Vec3.atCenterOf(center).subtract(Vec3.atCenterOf(g)).normalize();
                BlockPos at = ground(BlockPos.containing(Vec3.atCenterOf(g).add(dir.scale(6))));
                Mob m = create(cfg.champion.entityType, "minecraft:vindicator", at);
                if (m == null) { active = null; return; }
                String name = cfg.champion.name == null || cfg.champion.name.isBlank() ? WSLang.t("obj.champion.name") : cfg.champion.name;
                m.setCustomName(Component.literal(name));
                m.setCustomNameVisible(true);
                setHealth(m, cfg.champion.health);
                setAttr(m, Attributes.ATTACK_DAMAGE, cfg.champion.damage);
                setAttr(m, Attributes.KNOCKBACK_RESISTANCE, 0.6);
                m.getPersistentData().putBoolean("ws_obj_champion", true);
                m.setPersistenceRequired();
                m.setGlowingTag(true);
                level.addFreshEntity(m);
                champion = m.getUUID();
                spawnLinked(at, Math.max(0, cfg.champion.guards), null);
                endAt = now + Math.max(20, cfg.champion.seconds) * 20L;
                broadcast(WSLang.c("obj.start.champion", direction(g), Math.max(20, cfg.champion.seconds)));
            }
            case TREASURE -> {
                treasureFound = false;
                int min = Math.max(KingdomManager.objClaimRadius() + 4, cfg.treasure.minDistance);
                int max = Math.max(min + 4, cfg.treasure.maxDistance);
                double a = RNG.nextDouble() * Math.PI * 2;
                int d = min + RNG.nextInt(max - min + 1);
                int x = center.getX() + (int) Math.round(Math.cos(a) * d), z = center.getZ() + (int) Math.round(Math.sin(a) * d);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1; // enfoui sous la surface
                chest = new BlockPos(x, y, z);
                chestOriginal = level.getBlockState(chest);
                level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
                if (level.getBlockEntity(chest) instanceof ChestBlockEntity be) {
                    int slot = 0;
                    for (ItemStack st : rollDrops(cfg.treasure.drops, defaultTreasureDrops())) {
                        if (slot >= be.getContainerSize()) break;
                        be.setItem(slot, st);
                        slot += 1 + RNG.nextInt(3);
                    }
                }
                endAt = now + Math.max(20, cfg.treasure.seconds) * 20L;
                broadcast(WSLang.c("obj.start.treasure", dir8(x, z), Math.max(20, cfg.treasure.seconds)));
            }
            case PURIFY -> {
                int n = Math.max(1, Math.min(8, cfg.purify.count));
                FOYERS.clear();
                foyerTotal = n;
                foyerPurified = 0;
                int claim = KingdomManager.objClaimRadius();
                double base = RNG.nextDouble() * Math.PI * 2;
                for (int i = 0; i < n; i++) {
                    double a = base + i * Math.PI * 2 / n + (RNG.nextDouble() - 0.5) * 0.6;
                    int d = claim + 10 + RNG.nextInt(22);
                    int x = center.getX() + (int) Math.round(Math.cos(a) * d), z = center.getZ() + (int) Math.round(Math.sin(a) * d);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    GisementEntity g = com.wavesurvivor.registry.ModEntities.GISEMENT.get().create(level);
                    if (g == null) continue;
                    g.setFoyer();
                    g.setup("minecraft:crying_obsidian,minecraft:obsidian,minecraft:purple_stained_glass", 0);
                    g.setSize(1);
                    g.moveTo(x + 0.5, y, z + 0.5, RNG.nextFloat() * 360f, 0);
                    setHealth(g, Math.max(4, cfg.purify.health));
                    g.setGlowingTag(true);
                    if (level.addFreshEntity(g)) FOYERS.add(g.getUUID());
                }
                foyerTotal = FOYERS.size();
                if (foyerTotal == 0) { active = null; return; }
                endAt = now + Math.max(20, cfg.purify.seconds) * 20L;
                broadcast(WSLang.c("obj.start.purify", foyerTotal, Math.max(20, cfg.purify.seconds)));
            }
        }
        for (ServerPlayer p : level.players()) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1.5f, 1.2f);
        }
    }

    // ─── Suivi ───

    private static void tickConvoy(long now) {
        Entity e = level.getEntity(convoy);
        if (!(e instanceof Mob m) || !m.isAlive()) {
            finish(false, false);
            return;
        }
        BlockPos center = KingdomManager.objCenter();
        double dx = center.getX() + 0.5 - m.getX(), dz = center.getZ() + 0.5 - m.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= 5.0) { // validé à 5 blocs du Monolithe
            finish(true, false);
            return;
        }
        // Avance par étapes de 12 blocs vers le Monolithe (trajets courts = chemins fiables), à sa vitesse réglée
        double step = Math.min(12, Math.max(0, dist - 2));
        if (m.getNavigation().isDone() || now % 40 == 0) {
            m.getNavigation().moveTo(m.getX() + dx / dist * step, m.getY(), m.getZ() + dz / dist * step, 1.0);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() + 0.4, m.getZ(), 1, 0.2, 0.2, 0.2, 0);
        // Deuxième vague d'attaquants à mi-chemin
        if (!convoySecondWave && dist < convoyMaxDist * 0.55) {
            convoySecondWave = true;
            spawnLinked(m.blockPosition(), Math.max(0, cfg.convoy.attackers) / 2, m);
        }
        // Les attaquants visent le convoi
        for (UUID id : LINKED) {
            if (level.getEntity(id) instanceof Mob a && a.isAlive() && a.getTarget() != m
                    && (a.getTarget() == null || !(a.getTarget() instanceof ServerPlayer) || a.distanceToSqr(m) < 100)) {
                a.setTarget(m);
            }
        }
    }

    private static void tickChampion() {
        Entity e = level.getEntity(champion);
        if (e instanceof LivingEntity le && le.isDeadOrDying()) {
            Vec3 at = le.position();
            for (ItemStack st : rollDrops(cfg.champion.drops, defaultChampionDrops())) {
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y + 0.5, at.z, st));
            }
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y + 1, at.z, 40, 0.6, 1, 0.6, 0.3);
            finish(true, false);
            return;
        }
        if (!(e instanceof Mob m) || !m.isAlive()) {
            finish(false, false);
            return;
        }
        level.sendParticles(ParticleTypes.FLAME, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
    }

    private static void tickTreasure(long now) {
        if (chest == null || !level.getBlockState(chest).is(Blocks.CHEST)) {
            treasureFound = true; // coffre déjà déterré et cassé
            finish(true, false);
            return;
        }
        if (now % 40 == 0) level.sendParticles(ParticleTypes.GLOW, chest.getX() + 0.5, chest.getY() + 1.4, chest.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            double dx = p.getX() - (chest.getX() + 0.5), dz = p.getZ() - (chest.getZ() + 0.5);
            if (dx * dx + dz * dz <= 2.5 * 2.5 && Math.abs(p.getY() - chest.getY()) < 4) {
                treasureFound = true; // le coffre s'ouvre : son contenu jaillit au sol
                finish(true, false);
                return;
            }
        }
    }

    private static void tickPurify() {
        int alive = 0;
        for (UUID id : FOYERS) {
            Entity e = level.getEntity(id);
            if (e instanceof GisementEntity g && g.isAlive()) {
                alive++;
                g.setCustomName(Component.literal(WSLang.t("obj.purify.foyer") + " §7" + Math.round(g.getHealth()) + "/" + Math.round(g.getMaxHealth())));
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, g.getX(), g.getY() + 1.2, g.getZ(), 2, 0.3, 0.6, 0.3, 0.01);
                if (level.random.nextInt(4) == 0) level.sendParticles(ParticleTypes.PORTAL, g.getX(), g.getY() + 2.5, g.getZ(), 6, 0.2, 2.0, 0.2, 0.1);
            }
        }
        int purified = foyerTotal - alive;
        if (purified != foyerPurified) {
            foyerPurified = purified;
            broadcast(WSLang.c("obj.purify.progress", foyerPurified, foyerTotal));
        }
        if (alive == 0) finish(true, false);
    }

    private static void hud(long now) {
        long left = Math.max(0, (endAt - now) / 20);
        Component bar = switch (active) {
            case CONVOY -> {
                Entity e = level.getEntity(convoy);
                int hp = e instanceof LivingEntity le ? Math.round(le.getHealth()) : 0;
                int dist = 0;
                if (e != null && KingdomManager.objCenter() != null) {
                    BlockPos c = KingdomManager.objCenter();
                    dist = (int) Math.max(0, Math.sqrt(Math.pow(e.getX() - c.getX(), 2) + Math.pow(e.getZ() - c.getZ(), 2)) - 5);
                }
                yield WSLang.c("obj.hud.convoy", hp, dist, left);
            }
            case CHAMPION -> WSLang.c("obj.hud.champion", left);
            case TREASURE -> WSLang.c("obj.hud.treasure", left);
            case PURIFY -> WSLang.c("obj.hud.purify", foyerPurified, foyerTotal, left);
        };
        for (ServerPlayer p : level.players()) p.displayClientMessage(bar, true);
    }

    // ─── Fin ───

    /**
     * @param success objectif réussi
     * @param byAssault l'Assaut commence (temps écoulé de fait)
     */
    private static void finish(boolean success, boolean byAssault) {
        Type t = active;
        if (t == null) return;
        done = true;
        switch (t) {
            case CONVOY -> {
                if (success) {
                    pay(cfg.convoy.reward);
                    broadcast(WSLang.c("obj.convoy.done", rewardText(cfg.convoy.reward)));
                } else broadcast(WSLang.c("obj.convoy.failed"));
            }
            case CHAMPION -> {
                if (success) {
                    pay(cfg.champion.reward);
                    broadcast(WSLang.c("obj.champion.done", rewardText(cfg.champion.reward)));
                } else broadcast(WSLang.c("obj.champion.failed"));
            }
            case TREASURE -> {
                if (success) {
                    pay(cfg.treasure.reward);
                    broadcast(WSLang.c("obj.treasure.done", rewardText(cfg.treasure.reward)));
                } else broadcast(WSLang.c("obj.treasure.failed"));
            }
            case PURIFY -> {
                if (foyerPurified >= foyerTotal) {
                    nextAssaultMult = Math.max(0.1, 1.0 - Math.max(0, cfg.purify.successPercent) / 100.0);
                    broadcast(WSLang.c("obj.purify.all", Math.round(cfg.purify.successPercent)));
                } else if (foyerPurified == 0) {
                    // Arcaniste dans l'équipe : il voit les foyers… mais les laisser vivre coûte deux fois plus cher
                    double pct = Math.max(0, cfg.purify.failPercent) * (KingdomRoles.present(KingdomRoles.Role.ARCANIST) ? 2 : 1);
                    nextAssaultMult = 1.0 + pct / 100.0;
                    broadcast(WSLang.c(KingdomRoles.present(KingdomRoles.Role.ARCANIST) ? "obj.purify.none_arcanist" : "obj.purify.none", Math.round(pct)));
                } else {
                    nextAssaultMult = 1.0;
                    broadcast(WSLang.c("obj.purify.some", foyerPurified, foyerTotal));
                }
            }
        }
        if (level != null) {
            for (ServerPlayer p : level.players()) {
                level.playSound(null, p.blockPosition(), success ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_BASS.value(),
                        SoundSource.MASTER, 0.8f, success ? 1.3f : 0.6f);
            }
        }
        cleanup();
        active = null;
    }

    /** Retire ce qui reste de l'objectif (convoi, champion, unités liées, foyers, coffre non trouvé, boussoles). */
    private static void cleanup() {
        if (level == null) return;
        for (UUID id : LINKED) discard(id);
        LINKED.clear();
        if (convoy != null) { discard(convoy); convoy = null; }
        if (champion != null) {
            Entity e = level.getEntity(champion);
            if (e != null && e.isAlive()) discard(champion);
            champion = null;
        }
        for (UUID id : FOYERS) discard(id);
        FOYERS.clear();
        if (chest != null) {
            boolean wasChest = level.getBlockState(chest).is(Blocks.CHEST);
            if (wasChest) {
                // Trouvé : son contenu jaillit au sol ; sinon il disparaît avec le coffre
                if (!treasureFound && level.getBlockEntity(chest) instanceof ChestBlockEntity be) be.clearContent();
                level.destroyBlock(chest, false);
                if (treasureFound) level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5, 40, 0.5, 0.8, 0.5, 0.3);
            }
            // Le sol est régénéré : le bloc d'origine reprend sa place (pas de trou)
            if (chestOriginal != null && (wasChest || level.getBlockState(chest).isAir())) {
                level.setBlock(chest, chestOriginal, 3);
            }
            chest = null;
            chestOriginal = null;
        }
        clearTrackers();
    }

    /** Trésor : vrai dès qu'un joueur l'a atteint (son contenu est alors conservé). */
    private static boolean treasureFound;
    /** Bloc remplacé par le coffre, remis en place à la fin. */
    private static net.minecraft.world.level.block.state.BlockState chestOriginal;

    // ─── 2e flèche du HUD (trésor pour tous, foyers pour l'Arcaniste) ───

    private static final java.util.Set<UUID> OBJ_TRACKED = new java.util.HashSet<>();

    private static void sendTracker(ServerPlayer p, String kind, List<double[]> pts) {
        if (com.wavesurvivor.network.NetworkHandler.CHANNEL == null) return;
        double[] flat = new double[pts.size() * 3];
        for (int i = 0; i < pts.size(); i++) {
            flat[i * 3] = pts.get(i)[0];
            flat[i * 3 + 1] = pts.get(i)[1];
            flat[i * 3 + 2] = pts.get(i)[2];
        }
        com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.wavesurvivor.network.RoleTrackerPacket("obj:" + kind, flat));
    }

    private static void updateTrackers() {
        java.util.Set<UUID> now = new java.util.HashSet<>();
        if (active == Type.TREASURE && chest != null) {
            List<double[]> pts = List.<double[]>of(new double[]{chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5});
            for (ServerPlayer p : level.players()) {
                sendTracker(p, "treasure", pts);
                now.add(p.getUUID());
            }
        } else if (active == Type.PURIFY) {
            List<double[]> pts = new ArrayList<>();
            List<Entity> alive = new ArrayList<>();
            for (UUID id : FOYERS) {
                if (level.getEntity(id) instanceof GisementEntity g && g.isAlive()) {
                    pts.add(new double[]{g.getX(), g.getY(), g.getZ()});
                    alive.add(g);
                }
            }
            for (ServerPlayer p : level.players()) {
                if (!KingdomRoles.has(p, KingdomRoles.Role.ARCANIST) || pts.isEmpty()) continue;
                sendTracker(p, "foyer", pts);
                now.add(p.getUUID());
                for (Entity f : alive) { // colonne rose visible de loin, pour lui seul
                    for (int i = 0; i < 16; i++) {
                        level.sendParticles(p, FOYER_DUST, true, f.getX(), f.getY() + 2 + i * 1.5, f.getZ(), 1, 0.05, 0.2, 0.05, 0);
                    }
                }
            }
        }
        for (UUID id : OBJ_TRACKED) {
            if (now.contains(id)) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) sendTracker(p, "", List.of());
        }
        OBJ_TRACKED.clear();
        OBJ_TRACKED.addAll(now);
    }

    private static void clearTrackers() {
        if (level == null) return;
        for (UUID id : OBJ_TRACKED) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) sendTracker(p, "", List.of());
        }
        OBJ_TRACKED.clear();
    }

    private static final net.minecraft.core.particles.DustParticleOptions FOYER_DUST =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.3f, 0.65f), 2.0f);

    /** Direction approximative (8 secteurs : nord, nord-est…) d'un point vu depuis le Monolithe. */
    static String dir8(double x, double z) {
        BlockPos c = KingdomManager.objCenter();
        if (c == null) return "?";
        double ang = Math.toDegrees(Math.atan2(x - c.getX(), -(z - c.getZ()))); // 0 = nord, 90 = est
        String[] keys = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return WSLang.t("kingdom.dir8." + keys[Math.floorMod(Math.round(ang / 45.0), 8)]);
    }

    // ─── Outils ───

    private static void spawnLinked(BlockPos at, int n, Mob target) {
        for (int i = 0; i < n; i++) {
            LivingEntity u = KingdomManager.spawnObjectiveUnit(at, 5);
            if (u == null) continue;
            LINKED.add(u.getUUID());
            if (target != null && u instanceof Mob m) m.setTarget(target);
        }
    }

    private static Mob create(String id, String fallback, BlockPos at) {
        EntityType<?> type = EntityType.byString(id == null || id.isBlank() ? fallback : id.trim())
                .orElse(EntityType.byString(fallback).orElse(null));
        if (type == null) return null;
        Entity e = type.create(level);
        if (!(e instanceof Mob m)) {
            if (e != null) e.discard();
            return null;
        }
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, RNG.nextFloat() * 360f, 0);
        try {
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        } catch (Exception ignored) {}
        return m;
    }

    /**
     * Retire les comportements propres de la créature (fuite, errance, commerce…) : seule la marche donnée par
     * l'objectif reste, plus la nage pour ne pas se noyer.
     */
    private static void stripGoals(Mob m) {
        try {
            net.minecraft.world.entity.ai.goal.GoalSelector gs = net.minecraftforge.fml.util.ObfuscationReflectionHelper
                    .getPrivateValue(Mob.class, m, "f_21345_"); // goalSelector
            if (gs == null) return;
            gs.removeAllGoals(g -> true);
            gs.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(m));
        } catch (Exception e) {
            com.wavesurvivor.WaveSurvivorMod.LOGGER.debug("[Kingdom] Convoi : IA d'origine conservée ({})", e.getMessage());
        }
    }

    private static void setHealth(LivingEntity e, double hp) {
        setAttr(e, Attributes.MAX_HEALTH, Math.max(1, hp));
        e.setHealth((float) Math.max(1, hp));
    }

    private static void setAttr(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute a, double v) {
        AttributeInstance ai = e.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    private static BlockPos ground(BlockPos p) {
        return new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
    }

    private static void discard(UUID id) {
        Entity e = level.getEntity(id);
        if (e != null) {
            level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
            e.discard();
        }
    }

    private static ItemStack compass(BlockPos target) {
        ItemStack c = new ItemStack(Items.COMPASS);
        CompoundTag tag = c.getOrCreateTag();
        CompoundTag pos = new CompoundTag();
        pos.putInt("X", target.getX());
        pos.putInt("Y", target.getY());
        pos.putInt("Z", target.getZ());
        tag.put("LodestonePos", pos);
        tag.putString("LodestoneDimension", level.dimension().location().toString());
        tag.putBoolean("LodestoneTracked", false);
        tag.putBoolean(COMPASS_TAG, true);
        c.setHoverName(Component.literal(WSLang.t("obj.treasure.compass")));
        return c;
    }

    private static List<ChaosEvent.Drop> defaultChampionDrops() {
        List<ChaosEvent.Drop> l = new ArrayList<>();
        l.add(drop("wavesurvivor:roulette_key", 1, 1, 100));
        return l;
    }

    private static List<ChaosEvent.Drop> defaultTreasureDrops() {
        List<ChaosEvent.Drop> l = new ArrayList<>();
        l.add(drop("minecraft:golden_apple", 1, 2, 100));
        l.add(drop("minecraft:diamond", 1, 2, 60));
        l.add(drop("minecraft:experience_bottle", 3, 6, 100));
        return l;
    }

    private static ChaosEvent.Drop drop(String item, int min, int max, double chance) {
        ChaosEvent.Drop d = new ChaosEvent.Drop();
        d.item = item;
        d.minQty = min;
        d.maxQty = max;
        d.chance = chance;
        return d;
    }

    /** Butin tiré (liste de l'éditeur, ou butin par défaut si la liste n'a jamais été réglée). */
    private static List<ItemStack> rollDrops(List<ChaosEvent.Drop> drops, List<ChaosEvent.Drop> fallback) {
        List<ChaosEvent.Drop> src = drops != null ? drops : fallback;
        List<ItemStack> out = new ArrayList<>();
        for (ChaosEvent.Drop d : src) {
            if (d == null || d.item == null || d.item.isBlank()) continue;
            if (RNG.nextDouble() * 100 >= d.chance) continue;
            int n = d.minQty + (d.maxQty > d.minQty ? RNG.nextInt(d.maxQty - d.minQty + 1) : 0);
            if (n <= 0) continue;
            ItemStack st = com.wavesurvivor.horde.loot.LootItems.resolve(d.item, n);
            if (!st.isEmpty()) out.add(st);
        }
        return out;
    }

    private static void pay(Reward r) {
        if (r == null || !KingdomTreasury.active()) return;
        if (r.money > 0) KingdomTreasury.add(KingdomTreasury.Res.MONEY, r.money);
        if (r.wood > 0) KingdomTreasury.add(KingdomTreasury.Res.WOOD, r.wood);
        if (r.stone > 0) KingdomTreasury.add(KingdomTreasury.Res.STONE, r.stone);
        if (r.iron > 0) KingdomTreasury.add(KingdomTreasury.Res.IRON, r.iron);
        if (r.essence > 0) KingdomTreasury.add(KingdomTreasury.Res.ESSENCE, r.essence);
    }

    private static String rewardText(Reward r) {
        if (r == null) return "";
        List<String> parts = new ArrayList<>();
        if (r.money > 0) parts.add("§e" + r.money + " ◆");
        if (r.wood > 0) parts.add("§6" + r.wood + " " + WSLang.t("obj.res.wood"));
        if (r.stone > 0) parts.add("§7" + r.stone + " " + WSLang.t("obj.res.stone"));
        if (r.iron > 0) parts.add("§f" + r.iron + " " + WSLang.t("obj.res.iron"));
        if (r.essence > 0) parts.add("§d" + r.essence + " " + WSLang.t("obj.res.essence"));
        return String.join("§7, ", parts);
    }

    /** Direction (nord, est…) d'une position vue depuis le Monolithe. */
    private static String direction(BlockPos p) {
        BlockPos c = KingdomManager.objCenter();
        double dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ();
        String key = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
        return WSLang.t("kingdom.dir." + key);
    }

    /** Annonces des objectifs du Calme : dans le fil d'événements (plus dans le chat). */
    private static void broadcast(Component c) {
        if (level == null) return;
        com.wavesurvivor.network.EventFeedPacket.toAll(level.getServer(), c, "minecraft:compass", 0xFF5FD8E0, true);
    }
}
