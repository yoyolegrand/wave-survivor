package com.wavesurvivor.horde.chaos;

import com.wavesurvivor.config.model.CustomEntityData;
import com.wavesurvivor.horde.kingdom.KingdomClaim;
import com.wavesurvivor.horde.kingdom.KingdomManager;
import com.wavesurvivor.horde.model.ChaosEvent;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.IceFx;
import com.wavesurvivor.horde.skill.SandFx;
import com.wavesurvivor.horde.spawn.CustomEntityRegistry;
import com.wavesurvivor.horde.spawn.CustomEntitySpawner;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ÉVÉNEMENTS DU DÉSERT (1.9, Kingdom des Sables) : sables mouvants, soleil de plomb, nuée de scarabées, mirage, crue de l'oasis.
 * (La tempête de sable est la tempête ordinaire, réglée « sable ».) Tous sont réglables dans l'éditeur du chaos :
 * nombre, rayon, durée (desertDuration) et intensité (desertIntensity).
 * Trois objets agissent dessus : la Gourde d'eau fraîche (immunité au soleil), la Fiole de mirage (annule le prochain événement
 * défavorable du désert) et la Tablette de reconnaissance (voir {@link KingdomManager#previewNextAssault()}).
 */
public final class DesertEvents {

    private static final Random RNG = new Random();
    public static final String MIRAGE_TAG = "ws_mirage";
    public static final String GOURDE_KEY = "ws_gourde_until";

    public DesertEvents() {}

    /** Types d'événements du désert gérés ici. */
    public static boolean isDesertType(String type) {
        if (type == null) return false;
        return switch (type.toLowerCase()) { case "sables_mouvants", "soleil", "nuee", "mirage", "crue" -> true; default -> false; };
    }

    /** Événement défavorable (la Fiole de mirage peut l'annuler) : tout sauf la crue ; la tempête seulement si elle est « sable ». */
    public static boolean unfavorable(ChaosEvent ev) {
        if (ev == null || ev.type == null) return false;
        String t = ev.type.toLowerCase();
        if ("tempete".equals(t)) return "sand".equalsIgnoreCase(ev.stormLook);
        return isDesertType(t) && !"crue".equals(t);
    }

    // ─── État ───

    /** Fiole de mirage utilisée : le prochain événement défavorable du désert est annulé. */
    private static volatile boolean shield = false;
    private static long sunUntil = -1;
    /** Soleil de plomb : faim ajoutée toutes les 10 ticks (× intensité) et secondes d'exposition continue avant la nausée. */
    private static final double SUN_EXHAUSTION = 0.22;
    private static final int SUN_NAUSEA_AFTER = 15;
    private static final Map<UUID, Long> SUN_EXPOSED = new java.util.HashMap<>();
    private static double sunIntensity = 1.0;
    private static final Map<UUID, Long> MIRAGES = new ConcurrentHashMap<>();

    public static void armShield() { shield = true; }
    public static boolean shieldArmed() { return shield; }

    /** Annule l'événement s'il est défavorable et que la Fiole est armée. @return vrai si annulé. */
    public static boolean consumeShield(ChaosEvent ev, MinecraftServer srv) {
        if (!shield || !unfavorable(ev)) return false;
        shield = false;
        com.wavesurvivor.network.EventFeedPacket.toAll(srv, WSLang.c("desert.shielded"), "minecraft:potion", com.wavesurvivor.network.EventFeedPacket.CHAOS, false);
        return true;
    }

    /** Remise à zéro (fin ou arrêt de la partie). */
    public static void clearAll(MinecraftServer srv) {
        shield = false;
        sunUntil = -1;
        SUN_EXPOSED.clear();
        if (srv != null) for (UUID id : new ArrayList<>(MIRAGES.keySet())) discard(srv, id, false);
        MIRAGES.clear();
    }

    // ─── Application ───

    public static void apply(ChaosEvent ev, ServerLevel level, BlockPos center, MinecraftServer srv) {
        switch (ev.type.toLowerCase()) {
            case "sables_mouvants" -> quicksand(ev, level, center, srv);
            case "soleil" -> sun(ev, level, srv);
            case "nuee" -> swarm(ev, level, center, srv);
            case "mirage" -> mirage(ev, level, center, srv);
            case "crue" -> flood(ev, level, srv);
            default -> { }
        }
    }

    private static int duration(ChaosEvent ev, int dflt) { return Math.max(5, ev.desertDuration > 0 ? ev.desertDuration : dflt); }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    // ── Sables mouvants : disques de sable près des Portes ; joueurs et monstres y sont ralentis et ne sautent plus ──

    private static void quicksand(ChaosEvent ev, ServerLevel level, BlockPos center, MinecraftServer srv) {
        int n = Math.max(1, Math.min(8, ev.count));
        double r = Math.max(2, Math.min(12, ev.radius));
        int ticks = duration(ev, 25) * 20;
        int slow = Math.max(0, (int) Math.round(2 * Math.max(0.25, ev.desertIntensity)));
        List<BlockPos> spots = new ArrayList<>();
        BlockPos mono = KingdomManager.isActive() ? KingdomManager.center() : null;
        for (int i = 0; i < n; i++) {
            BlockPos gate = KingdomManager.isActive() ? KingdomManager.gatePos(RNG.nextInt(Math.max(1, KingdomManager.gateCount()))) : null;
            double x, z;
            if (gate != null && mono != null) {
                // Entre la Porte et le royaume : la zone que traversent les troupes
                double t = 0.12 + RNG.nextDouble() * 0.3;
                x = gate.getX() + (mono.getX() - gate.getX()) * t + (RNG.nextDouble() - 0.5) * 14;
                z = gate.getZ() + (mono.getZ() - gate.getZ()) * t + (RNG.nextDouble() - 0.5) * 14;
            } else {
                double a = RNG.nextDouble() * Math.PI * 2, d = 6 + RNG.nextDouble() * 20;
                x = center.getX() + Math.cos(a) * d;
                z = center.getZ() + Math.sin(a) * d;
            }
            spots.add(surface(level, (int) Math.round(x), (int) Math.round(z)));
        }
        for (BlockPos p : spots) {
            double y = p.getY();
            Display.BlockDisplay disc = IceFx.spawn(level, p.getX() + 0.5, y + 0.01, p.getZ() + 0.5, Blocks.SAND.defaultBlockState(),
                    new Quaternionf(), (float) (2 * r), 0.04f, (float) (2 * r), 0f);
            quicksandStep(level, srv, p.getX() + 0.5, y, p.getZ() + 0.5, r, disc, 0, ticks, slow);
        }
    }

    private static void quicksandStep(ServerLevel level, MinecraftServer srv, double cx, double cy, double cz, double r, Display.BlockDisplay disc, int elapsed, int total, int slow) {
        DelayedActionScheduler.schedule(srv, 5, () -> {
            int t = elapsed + 5;
            if (t >= total || !com.wavesurvivor.horde.HordeManager.get().isRunning()) { IceFx.remove(disc); return; }
            level.sendParticles(SandFx.SAND, cx, cy + 0.2, cz, 10, r * 0.5, 0.1, r * 0.5, 0.05);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(cx - r, cy - 2.5, cz - r, cx + r, cy + 3, cz + r))) {
                if (!e.isAlive() || (!(e instanceof Enemy) && !(e instanceof ServerPlayer))) continue;
                if (e instanceof ServerPlayer p && (p.isSpectator() || p.isCreative() || SandFx.immune(p, "wavesurvivor:voile_desert"))) continue;
                double dx = cx - e.getX(), dz = cz - e.getZ();
                if (dx * dx + dz * dz > r * r) continue;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, slow, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.JUMP, 12, 128, false, false)); // enlisé : plus de saut
            }
            quicksandStep(level, srv, cx, cy, cz, r, disc, t, total, slow);
        }, "desert quicksand");
    }

    // ── Soleil de plomb : de jour, hors de l'ombre, la faim baisse plus vite (la Gourde protège) ──

    private static void sun(ChaosEvent ev, ServerLevel level, MinecraftServer srv) {
        sunUntil = srv.getTickCount() + (long) duration(ev, 90) * 20L;
        sunIntensity = Math.max(0.2, ev.desertIntensity);
    }

    /** Gourde d'eau fraîche : immunité au soleil de plomb pour {@code seconds} secondes. */
    public static void drink(ServerPlayer p, int seconds) {
        p.getPersistentData().putLong(GOURDE_KEY, p.server.getTickCount() + seconds * 20L);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.wavesurvivor.registry.ModEffects.DESERT_FRESHNESS.get(), seconds * 20, 0, false, true, true));
    }

    private static boolean protectedFromSun(ServerPlayer p, long now) {
        CompoundTag d = p.getPersistentData();
        return p.hasEffect(com.wavesurvivor.registry.ModEffects.DESERT_FRESHNESS.get()) || d.contains(GOURDE_KEY) && d.getLong(GOURDE_KEY) > now;
    }

    // ── Nuée de scarabées : surgit du sable près d'un coffre du royaume (sinon du Monolithe) ──

    private static void swarm(ChaosEvent ev, ServerLevel level, BlockPos center, MinecraftServer srv) {
        BlockPos at = pickChest(level);
        if (at == null) at = KingdomManager.isActive() && KingdomManager.center() != null ? KingdomManager.center() : center;
        String name = ev.entityType != null && !ev.entityType.isBlank() ? ev.entityType : "scarabee";
        CustomEntityData ce = CustomEntityRegistry.get(name);
        if (ce == null) return;
        int n = Math.max(1, Math.min(30, ev.count));
        CustomEntitySpawner.spawnMobs(level, ce, at, 4, n, null);
        level.sendParticles(SandFx.SAND, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 60, 1.5, 0.5, 1.5, 0.15);
        // Les scarabées foncent sur le joueur le plus proche
        for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(at).inflate(8))) {
            if (m.getTarget() != null || !(m instanceof Enemy)) continue;
            ServerPlayer best = null;
            double bd = Double.MAX_VALUE;
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.isCreative()) continue;
                double d = p.distanceToSqr(m);
                if (d < bd) { bd = d; best = p; }
            }
            if (best != null) m.setTarget(best);
        }
    }

    /** Un coffre ou tonneau du royaume (hors coffres roulette), tiré au hasard ; null s'il n'y en a pas. */
    private static BlockPos pickChest(ServerLevel level) {
        if (!KingdomManager.isActive() || KingdomManager.center() == null) return null;
        BlockPos c = KingdomManager.center();
        int r = KingdomClaim.radius();
        List<BlockPos> found = new ArrayList<>();
        for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) {
            for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity)) continue;
                    if (be instanceof com.wavesurvivor.horde.roulette.RouletteChestBlockEntity) continue;
                    BlockPos p = be.getBlockPos();
                    if (p.distSqr(c) <= (double) r * r) found.add(p);
                }
            }
        }
        return found.isEmpty() ? null : found.get(RNG.nextInt(found.size()));
    }

    // ── Mirage : de fausses troupes au loin, inoffensives, qui se dissipent à l'approche ──

    private static void mirage(ChaosEvent ev, ServerLevel level, BlockPos center, MinecraftServer srv) {
        BlockPos mono = KingdomManager.isActive() && KingdomManager.center() != null ? KingdomManager.center() : center;
        int n = Math.max(1, Math.min(40, ev.count));
        long until = srv.getTickCount() + (long) duration(ev, 45) * 20L;
        int claim = KingdomManager.isActive() ? KingdomClaim.radius() : 16;
        int made = 0;
        // Quelques groupes serrés, chacun dans une direction
        int groups = Math.max(1, Math.min(4, n / 3));
        for (int g = 0; g < groups; g++) {
            double a = RNG.nextDouble() * Math.PI * 2, d = claim + 22 + RNG.nextDouble() * 22;
            double gx = mono.getX() + Math.cos(a) * d, gz = mono.getZ() + Math.sin(a) * d;
            int per = n / groups + (g < n % groups ? 1 : 0);
            for (int i = 0; i < per; i++) {
                BlockPos p = surface(level, (int) Math.round(gx + (RNG.nextDouble() - 0.5) * 8), (int) Math.round(gz + (RNG.nextDouble() - 0.5) * 8));
                var h = EntityType.HUSK.create(level);
                if (h == null) continue;
                h.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, RNG.nextFloat() * 360f, 0);
                h.setNoAi(true);
                h.setInvulnerable(true);
                h.setSilent(true);
                h.setPersistenceRequired();
                h.addTag(MIRAGE_TAG);
                h.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100000, 0, false, false));
                if (level.addFreshEntity(h)) {
                    MIRAGES.put(h.getUUID(), until);
                    made++;
                }
            }
        }
        if (made == 0) return;
    }

    private static void discard(MinecraftServer srv, UUID id, boolean poof) {
        for (ServerLevel l : srv.getAllLevels()) {
            var e = l.getEntity(id);
            if (e == null) continue;
            if (poof) l.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1, e.getZ(), 14, 0.3, 0.6, 0.3, 0.03);
            e.discard();
            return;
        }
    }

    // ── Crue de l'oasis (favorable) : arbres de plus, régénération, marchands -10 % ──

    private static void flood(ChaosEvent ev, ServerLevel level, MinecraftServer srv) {
        int pct = (int) Math.round(10 * Math.max(0.2, ev.desertIntensity));
        // Arbres : on rejoue le premier événement « arbre » de la horde (deux fois, à quelques secondes d'écart)
        ChaosEvent tree = null;
        for (ChaosEvent e : com.wavesurvivor.horde.HordeManager.get().activeChaosEvents()) if (e != null && "arbre".equalsIgnoreCase(e.type)) { tree = e; break; }
        if (tree != null) {
            final ChaosEvent te = tree;
            for (int i = 0; i < Math.max(1, Math.min(4, ev.count)); i++) {
                DelayedActionScheduler.schedule(srv, 40 + i * 100, () -> {
                    BlockPos c = KingdomManager.isActive() ? KingdomManager.center() : null;
                    if (c != null) ChaosApplier.apply(te, level, c, srv);
                }, "desert flood tree");
            }
        }
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration(ev, 60) * 20, 0, false, true));
        }
        int n = com.wavesurvivor.horde.merchant.MerchantSpawner.discount(srv, pct);
        if (n > 0) com.wavesurvivor.network.EventFeedPacket.toAll(srv, WSLang.c("desert.flood_discount", pct), "minecraft:emerald", com.wavesurvivor.network.EventFeedPacket.RESOURCE, false);
    }

    // ─── Tick (toutes les 10 ticks) ───

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer srv = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (srv == null || srv.getTickCount() % 10 != 0) return;
        long now = srv.getTickCount();

        if (sunUntil >= 0) {
            boolean assault = KingdomManager.isActive() && KingdomManager.phase() == KingdomManager.Phase.ASSAULT;
            if (now >= sunUntil || assault) { sunUntil = -1; SUN_EXPOSED.clear(); }
            else {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    // À l'ombre, protégé ou hors du jour : l'exposition continue repart de zéro
                    if (p.isSpectator() || p.isCreative() || !p.level().isDay() || protectedFromSun(p, now)
                            || !p.level().canSeeSky(p.blockPosition().above())) {
                        SUN_EXPOSED.remove(p.getUUID());
                        continue;
                    }
                    p.causeFoodExhaustion((float) (SUN_EXHAUSTION * sunIntensity)); // la faim descend vite (≈ 1 point toutes les 9 s)
                    if (now % 200 == 0) p.displayClientMessage(WSLang.c("desert.sun_burns"), true);
                    // Icône du HUD + voile orangé côté client : l'effet est rafraîchi tant que le joueur est exposé, il s'éteint tout seul à l'ombre
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.wavesurvivor.registry.ModEffects.SCORCHING_SUN.get(), 40, 0, false, false, true));
                    // Exposition continue trop longue : nausée (rafraîchie tant qu'on reste dehors)
                    long since = SUN_EXPOSED.computeIfAbsent(p.getUUID(), u -> now);
                    if (now - since >= SUN_NAUSEA_AFTER * 20L) {
                        if (!p.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION)) p.displayClientMessage(WSLang.c("desert.sun_nausea"), true);
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 160, 0, false, false, true));
                    }
                }
            }
        }

        if (!MIRAGES.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> it = MIRAGES.entrySet().iterator();
            while (it.hasNext()) {
                var en = it.next();
                boolean vanish = now >= en.getValue();
                if (!vanish) {
                    for (ServerLevel l : srv.getAllLevels()) {
                        var m = l.getEntity(en.getKey());
                        if (m == null) continue;
                        for (ServerPlayer p : l.players()) if (!p.isSpectator() && p.distanceToSqr(m) < 16 * 16) { vanish = true; break; }
                        break;
                    }
                }
                if (vanish) { discard(srv, en.getKey(), true); it.remove(); }
            }
        }
    }

    /** Mirages oubliés (sauvegarde puis redémarrage) : retirés dès qu'ils réapparaissent. */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        if (e.getEntity().getTags().contains(MIRAGE_TAG) && !MIRAGES.containsKey(e.getEntity().getUUID())) e.setCanceled(true);
    }
}
