package com.wavesurvivor.horde.frost;

import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * TEMPÊTE (1.6) — moteur partagé de « tempête » : une zone où les joueurs exposés subissent des effets, des dégâts
 * par seconde, un gel visuel, du vent et/ou une attraction. Les ABRIS (blocs de chaleur proches : feu de camp, feu,
 * lave… réglables) protègent entièrement. Les monstres de la horde peuvent recevoir leurs propres effets.
 * Utilisée par l'événement de chaos « tempete » (zone fixe, avec phase d'avertissement) et par la compétence de boss
 * « blizzard_storm » (zone qui suit le boss). Réutilisable pour d'autres thèmes (sable, cendres…) : tout est réglable
 * (particules, effets, abris).
 */
public class StormManager {

    /** Abris par défaut : sources de chaleur. */
    public static final List<String> DEFAULT_SHELTER = List.of("minecraft:campfire", "minecraft:soul_campfire",
            "minecraft:fire", "minecraft:soul_fire", "minecraft:lava");

    /** Effet de statut de la tempête (sur les joueurs exposés ou sur les monstres de la horde). */
    public record Fx(MobEffect effect, int amplifier, boolean onPlayers) {}

    /** Réglages d'une tempête. */
    public static final class Params {
        public double radius = 12;
        public int warningTicks = 100;
        public int durationTicks = 600;
        public double damagePerSecond = 1.0;
        public double wind = 0.0;
        public double pull = 0.0;
        public double shelterRadius = 4;
        public boolean freeze = true;
        public boolean announce = true;
        public String particle = "minecraft:snowflake";
        public List<String> shelter = DEFAULT_SHELTER;
        public List<Fx> effects = new ArrayList<>();
    }

    private static final class Storm {
        ServerLevel level;
        Vec3 center;
        UUID follow;
        Params p;
        long startAt, endAt;
        boolean begun;
        Vec3 windDir = Vec3.ZERO;
        ParticleOptions particle;
        Set<Block> shelterBlocks = new HashSet<>();
        final Set<UUID> exposed = new HashSet<>();
    }

    private static final List<Storm> STORMS = new ArrayList<>();
    private static final Random RNG = new Random();

    /** Lance une tempête. {@code follow} : entité suivie (boss) ou null pour une zone fixe. */
    public static void start(ServerLevel level, Vec3 center, UUID follow, Params p) {
        if (level == null || center == null || p == null) return;
        Storm s = new Storm();
        s.level = level;
        s.center = center;
        s.follow = follow;
        s.p = p;
        long now = level.getServer().getTickCount();
        s.startAt = now + Math.max(0, p.warningTicks);
        s.endAt = s.startAt + Math.max(20, p.durationTicks);
        double a = RNG.nextDouble() * Math.PI * 2;
        s.windDir = new Vec3(Math.cos(a), 0, Math.sin(a));
        s.particle = resolveParticle(p.particle);
        for (String id : (p.shelter == null || p.shelter.isEmpty() ? DEFAULT_SHELTER : p.shelter)) {
            try {
                Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.trim()));
                if (b != Blocks.AIR) s.shelterBlocks.add(b);
            } catch (Exception ignored) {}
        }
        STORMS.add(s);
    }

    public static boolean active() { return !STORMS.isEmpty(); }

    /** Fin de horde : plus aucune tempête. */
    public static void clearAll() { STORMS.clear(); }

    private static ParticleOptions resolveParticle(String id) {
        try {
            var t = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(id == null || id.isBlank() ? "minecraft:snowflake" : id.trim()));
            if (t instanceof ParticleOptions po) return po;
        } catch (Exception ignored) {}
        return ParticleTypes.SNOWFLAKE;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || STORMS.isEmpty()) return;
        MinecraftServer srv = e.getServer();
        long now = srv.getTickCount();
        Iterator<Storm> it = STORMS.iterator();
        while (it.hasNext()) {
            Storm s = it.next();
            if (!tick(s, now)) it.remove();
        }
    }

    private static boolean tick(Storm s, long now) {
        ServerLevel level = s.level;
        if (s.follow != null) {
            Entity f = level.getEntity(s.follow);
            if (f == null || !f.isAlive()) { finish(s); return false; }
            s.center = f.position();
        }
        double r = Math.max(2, s.p.radius);
        double r2 = r * r;

        // ─── Avertissement ───
        if (now < s.startAt) {
            if ((s.startAt - now) % 20 == 0 && s.p.announce) {
                int sec = (int) ((s.startAt - now) / 20);
                for (ServerPlayer p : level.players()) {
                    if (inZone(p, s, r2)) p.displayClientMessage(Component.literal(WSLang.t("storm.warning", sec)), true);
                }
            }
            return true;
        }
        if (!s.begun) {
            s.begun = true;
            if (s.p.announce) {
                for (ServerPlayer p : level.players()) {
                    if (inZone(p, s, r2)) p.sendSystemMessage(Component.literal(WSLang.t("storm.start")));
                }
            }
        }
        if (now >= s.endAt) { finish(s); return false; }

        // ─── Chaque tick : particules, vent, attraction ───
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive() || !inZone(p, s, r2)) continue;
            // Tempête violente : épais rideau de particules + rafales horizontales qui filent dans le sens du vent
            level.sendParticles(p, s.particle, false, p.getX(), p.getY() + 1.5, p.getZ(), 40, 9.0, 4.0, 9.0, 0.08);
            level.sendParticles(p, ParticleTypes.WHITE_ASH, false, p.getX(), p.getY() + 1.5, p.getZ(), 25, 8.0, 3.5, 8.0, 0.1);
            for (int i = 0; i < 14; i++) {
                double ox = p.getX() + (RNG.nextDouble() - 0.5) * 18 - s.windDir.x * 8;
                double oz = p.getZ() + (RNG.nextDouble() - 0.5) * 18 - s.windDir.z * 8;
                double oy = p.getY() + RNG.nextDouble() * 4;
                level.sendParticles(p, s.particle, false, ox, oy, oz, 0, s.windDir.x, -0.05, s.windDir.z, 1.6);
            }
            if (now % 10 == 0) level.sendParticles(p, ParticleTypes.CLOUD, false, p.getX(), p.getY() + 1.0, p.getZ(), 6, 7.0, 1.5, 7.0, 0.15);
            if (!s.exposed.contains(p.getUUID()) || p.isCreative() || com.wavesurvivor.item.RelicEffects.immovable(p)) continue;
            // Gel vanilla (comme la neige poudreuse) : +3/tick compense le -2/tick que Minecraft retire hors neige,
            // donc la jauge monte vraiment ; une fois pleine, le jeu inflige lui-même ses dégâts de gel.
            if (s.p.freeze) p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 40, p.getTicksFrozen() + 3));
            double mx = 0, mz = 0;
            if (s.p.wind > 0) { mx += s.windDir.x * s.p.wind; mz += s.windDir.z * s.p.wind; }
            if (s.p.pull > 0) {
                Vec3 to = new Vec3(s.center.x - p.getX(), 0, s.center.z - p.getZ());
                if (to.length() > 0.8) { to = to.normalize().scale(s.p.pull); mx += to.x; mz += to.z; }
            }
            if (mx != 0 || mz != 0) {
                p.setDeltaMovement(p.getDeltaMovement().add(mx, 0, mz));
                p.hurtMarked = true;
            }
        }

        // ─── Chaque seconde : abri ? effets, gel, dégâts ───
        if (now % 20 != 0) return true;
        int sr = (int) Math.ceil(Math.max(0, s.p.shelterRadius));
        s.exposed.clear();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive() || p.isCreative() || !inZone(p, s, r2)) continue;
            if (sr > 0 && sheltered(level, p.blockPosition(), sr, s.shelterBlocks)) {
                p.displayClientMessage(Component.literal(WSLang.t("storm.sheltered")), true);
                continue;
            }
            s.exposed.add(p.getUUID());
            p.displayClientMessage(Component.literal(WSLang.t("storm.exposed")), true);
            for (Fx fx : s.p.effects) {
                if (fx.onPlayers()) p.addEffect(new MobEffectInstance(fx.effect(), 50, Math.max(0, fx.amplifier()), false, true));
            }
            if (s.p.damagePerSecond > 0) p.hurt(level.damageSources().freeze(), (float) s.p.damagePerSecond);
        }
        boolean monsterFx = false;
        for (Fx fx : s.p.effects) if (!fx.onPlayers()) { monsterFx = true; break; }
        if (monsterFx) {
            AABB box = new AABB(s.center, s.center).inflate(r, 64, r);
            for (Mob m : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive() && MobRegistry.get(m.getUUID()) != null)) {
                for (Fx fx : s.p.effects) {
                    if (!fx.onPlayers()) m.addEffect(new MobEffectInstance(fx.effect(), 50, Math.max(0, fx.amplifier()), false, false));
                }
            }
        }
        return true;
    }

    private static boolean inZone(ServerPlayer p, Storm s, double r2) {
        double dx = p.getX() - s.center.x, dz = p.getZ() - s.center.z;
        return dx * dx + dz * dz <= r2;
    }

    /** Une source de chaleur (bloc d'abri allumé) à portée du joueur ? */
    private static boolean sheltered(ServerLevel level, BlockPos pos, int r, Set<Block> blocks) {
        if (blocks.isEmpty()) return false;
        for (BlockPos bp : BlockPos.betweenClosed(pos.offset(-r, -2, -r), pos.offset(r, 2, r))) {
            BlockState st = level.getBlockState(bp);
            if (!blocks.contains(st.getBlock())) continue;
            if (st.hasProperty(BlockStateProperties.LIT) && !st.getValue(BlockStateProperties.LIT)) continue; // feu de camp éteint
            return true;
        }
        return false;
    }

    private static void finish(Storm s) {
        if (s.p.announce && s.begun) {
            for (ServerPlayer p : s.level.players()) p.sendSystemMessage(Component.literal(WSLang.t("storm.end")));
        }
    }
}
