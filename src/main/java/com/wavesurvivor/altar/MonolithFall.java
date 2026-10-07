package com.wavesurvivor.altar;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * « LA CHUTE DU MONOLITHE » : séquence de défaite d'environ 8 s quand le Monolithe est détruit.
 *   0 → 2 s : le temps se fige (monstres figés, tournés vers le Monolithe), cœur qui bat, cloche ×3, ciel rouge sang.
 *   2 → 4 s : la fissure (fissures violettes, éclats qui tournoient, colonne de lumière).
 *   4 → 5 s : l'implosion (tout est aspiré vers le Monolithe), puis une demi-seconde de silence.
 *   5 s     : l'éclatement (explosion de lumière sans dégâts, onde de choc, titre « LE MONOLITHE EST TOMBÉ »).
 *   5 → 8 s : le triomphe de la horde (rugissements, monstres dissipés en fumée), puis arrêt propre + bilan.
 * Kingdom : le cristal de la Mairie éclate, les Portes grondent, le décor de la Mairie s'effondre.
 * La horde est mise en pause pendant la séquence (voir HordeTickHandler) ; les joueurs sont protégés.
 */
public final class MonolithFall {

    public static final int DURATION = 160; // 8 s
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.04f), 1.6f);
    private static final DustParticleOptions CRACK = new DustParticleOptions(new Vector3f(0.75f, 0.25f, 1f), 1.2f);

    private static final class Seq {
        ServerLevel level;
        BlockPos pos;
        String dim, hordeName;
        boolean kingdom;
        long start;
        int wave;
        long startedAt;
        Map<UUID, Integer> kills;
        final List<UUID> mobs = new ArrayList<>();
        int removed;
    }

    private static Seq SEQ;
    /** Dernier autel tombé (pour « ↻ Réessayer »). */
    private static String lastDim;
    private static BlockPos lastPos;

    /** Public : instance enregistrée sur le bus d'événements (WaveSurvivorMod) pour le tick de la séquence. */
    public MonolithFall() {}

    /** Vrai pendant la séquence : la horde est en pause. */
    public static boolean active() { return SEQ != null; }

    /** Lance la séquence (appelé à la place de l'arrêt immédiat de la horde). */
    public static void start(ServerLevel level, BlockPos pos, String dim, String hordeName) {
        if (SEQ != null) return;
        HordeManager hm = HordeManager.get();
        Seq s = new Seq();
        s.level = level;
        s.pos = pos;
        s.dim = dim;
        s.hordeName = hordeName;
        s.start = level.getServer().getTickCount();
        s.kingdom = hm.getActiveHorde() != null && hm.getActiveHorde().configData != null && hm.getActiveHorde().configData.isKingdom();
        s.wave = s.kingdom ? com.wavesurvivor.horde.kingdom.KingdomManager.cycle() : hm.getCurrentWave();
        s.startedAt = hm.getStartedAtTick();
        s.kills = com.wavesurvivor.horde.rewards.KillTracker.snapshotKills();
        lastDim = dim;
        lastPos = pos;

        // Unités de la horde (registre + entités marquées) : figées et tournées vers le Monolithe
        AABB box = new AABB(pos).inflate(160);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box)) {
            if (m instanceof com.wavesurvivor.entity.BrecheEntity) continue;
            var tag = m.getPersistentData();
            boolean horde = MobRegistry.get(m.getUUID()) != null || tag.getBoolean("ws_horde_unit") || tag.getBoolean("ws_revenant")
                    || tag.getBoolean("ws_kingdom_guardian") || tag.getBoolean("ws_kingdom_escort");
            if (!horde) continue;
            m.setTarget(null);
            m.getNavigation().stop();
            m.setNoAi(true);
            float yaw = (float) Math.toDegrees(Math.atan2(-(pos.getX() + 0.5 - m.getX()), pos.getZ() + 0.5 - m.getZ()));
            m.setYRot(yaw);
            m.setYHeadRot(yaw);
            m.yBodyRot = yaw;
            s.mobs.add(m.getUUID());
        }
        java.util.Collections.shuffle(s.mobs);

        // Joueurs : protégés pendant la cinématique + ciel rouge sang (côté client)
        for (ServerPlayer p : players(s)) {
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION + 20, 4, false, false));
            com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                    new com.wavesurvivor.network.MonolithFallPacket(DURATION));
        }
        SEQ = s;
        WaveSurvivorMod.LOGGER.info("[MonolithFall] Chute du Monolithe @ {} ({} unités figées)", pos, s.mobs.size());
    }

    private static List<ServerPlayer> players(Seq s) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : s.level.players()) if (p.distanceToSqr(Vec3.atCenterOf(s.pos)) < 128 * 128) out.add(p);
        return out;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SEQ == null) return;
        Seq s = SEQ;
        int t = (int) (event.getServer().getTickCount() - s.start);
        try {
            tick(s, t);
        } catch (Exception ex) {
            WaveSurvivorMod.LOGGER.error("[MonolithFall] Erreur pendant la séquence", ex);
            t = DURATION;
        }
        if (t >= DURATION) finish(event.getServer(), s);
    }

    private static void tick(Seq s, int t) {
        ServerLevel lv = s.level;
        double x = s.pos.getX() + 0.5, y = s.pos.getY() + 1.0, z = s.pos.getZ() + 0.5;
        List<ServerPlayer> ps = players(s);

        // ── 1. Le temps se fige (0 → 2 s) ──
        if (t == 0 || t == 15 || t == 30) lv.playSound(null, s.pos, SoundEvents.BELL_BLOCK, SoundSource.MASTER, 3f, 0.5f);
        if (t < 90 && t % 12 == 0) for (ServerPlayer p : ps) p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.MASTER, 1.2f, 0.9f);
        if (t < 100 && t % 2 == 0) for (ServerPlayer p : ps) {
            lv.sendParticles(p, BLOOD, true, p.getX(), p.getY() + 3, p.getZ(), 10, 6, 2, 6, 0);
        }

        // ── 2. La fissure (2 → 4 s) ──
        if (t >= 40 && t < 80) {
            double h = (t - 40) / 40.0 * 4.0;
            for (int i = 0; i < 4; i++) {
                double a = lv.random.nextDouble() * Math.PI * 2;
                lv.sendParticles(CRACK, x + Math.cos(a) * 0.55, y - 0.5 + lv.random.nextDouble() * h, z + Math.sin(a) * 0.55, 1, 0, 0, 0, 0);
            }
            // éclats qui tournoient
            for (int i = 0; i < 3; i++) {
                double a = t * 0.25 + i * 2.1, r = 1.4 + 0.4 * Math.sin(t * 0.3 + i);
                lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
                        x + Math.cos(a) * r, y + 0.5 + Math.sin(t * 0.2 + i), z + Math.sin(a) * r, 2, 0.05, 0.05, 0.05, 0);
            }
            // colonne de lumière
            for (int k = 0; k < 6; k++) lv.sendParticles(ParticleTypes.END_ROD, x, y + lv.random.nextDouble() * (t - 38), z, 1, 0.08, 0, 0.08, 0.01);
            if (t % 8 == 0) lv.playSound(null, s.pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.MASTER, 2f, 0.5f + (t - 40) / 80f);
            if (t == 40) lv.playSound(null, s.pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 3f, 0.5f);
            if (t == 70) lv.playSound(null, s.pos, SoundEvents.GLASS_BREAK, SoundSource.MASTER, 3f, 0.4f);
        }

        // ── 3. L'implosion (4 → 4,5 s), puis silence jusqu'à 5 s ──
        if (t >= 80 && t < 90) {
            for (int i = 0; i < 40; i++) {
                double a = lv.random.nextDouble() * Math.PI * 2, b = lv.random.nextDouble() * Math.PI - Math.PI / 2, r = 8 + lv.random.nextDouble() * 6;
                double px = Math.cos(a) * Math.cos(b) * r, py = Math.sin(b) * r * 0.6, pz = Math.sin(a) * Math.cos(b) * r;
                lv.sendParticles(ParticleTypes.REVERSE_PORTAL, x + px, y + py, z + pz, 0, -px, -py, -pz, 0.12);
            }
            for (ServerPlayer p : ps) {
                if (p.isCreative() || p.isSpectator() || p.distanceToSqr(x, y, z) > 24 * 24) continue;
                Vec3 pull = new Vec3(x - p.getX(), 0, z - p.getZ()).normalize().scale(0.18);
                p.setDeltaMovement(p.getDeltaMovement().add(pull));
                p.hurtMarked = true;
            }
            if (t == 80) {
                lv.playSound(null, s.pos, SoundEvents.PORTAL_TRIGGER, SoundSource.MASTER, 2.5f, 1.6f);
                if (s.kingdom) com.wavesurvivor.horde.kingdom.KingdomTownHall.shatterCrystal();
            }
        }

        // ── 4. L'éclatement (5 s) ──
        if (t == 100) {
            lv.sendParticles(ParticleTypes.FLASH, x, y + 1, z, 3, 0, 0, 0, 0);
            lv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 4, 1.5, 1, 1.5, 0);
            lv.sendParticles(ParticleTypes.END_ROD, x, y + 1, z, 200, 0.5, 0.5, 0.5, 0.6);
            lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 120, 6, 0.3, 6, 0.08);
            lv.playSound(null, s.pos, SoundEvents.GENERIC_EXPLODE, SoundSource.MASTER, 6f, 0.5f);
            lv.playSound(null, s.pos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.MASTER, 4f, 0.7f);
            lv.playSound(null, s.pos, SoundEvents.WITHER_DEATH, SoundSource.MASTER, 3f, 0.7f);
            for (ServerPlayer p : ps) {
                if (!p.isCreative() && !p.isSpectator() && p.distanceToSqr(x, y, z) < 20 * 20) {
                    Vec3 push = new Vec3(p.getX() - x, 0, p.getZ() - z);
                    push = push.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : push.normalize();
                    p.setDeltaMovement(push.x * 1.6, 0.6, push.z * 1.6);
                    p.hurtMarked = true;
                }
                p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 70, 25));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(WSLang.t("fall.subtitle", WSLang.t(s.hordeName)))));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(WSLang.t("fall.title"))));
            }
            if (s.kingdom) {
                com.wavesurvivor.horde.kingdom.KingdomManager.defeatRoar();
                com.wavesurvivor.horde.kingdom.KingdomTownHall.crumble();
            }
        }

        // ── 5. Le triomphe de la horde (5 → 8 s) : rugissements, puis dissipation en fumée ──
        if (t > 100 && t < DURATION && !s.mobs.isEmpty()) {
            if (t == 104) lv.playSound(null, s.pos, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4f, 0.6f);
            int target = (int) Math.ceil(s.mobs.size() * (t - 100) / (double) (DURATION - 110));
            while (s.removed < Math.min(target, s.mobs.size())) {
                Entity e = lv.getEntity(s.mobs.get(s.removed++));
                if (e == null || !e.isAlive()) continue;
                lv.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                lv.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 3, 0.2, 0.3, 0.2, 0.03);
                if (lv.random.nextInt(4) == 0 && e instanceof Mob m) m.playAmbientSound();
                MobRegistry.remove(e.getUUID());
                e.discard();
            }
        }
    }

    private static void finish(MinecraftServer server, Seq s) {
        SEQ = null;
        long dur = s.startedAt > 0 ? Math.max(0, server.getTickCount() - s.startedAt) / 20 : 0;
        try {
            HordeManager.get().stopSilently(); // terrain restauré, unités restantes retirées
        } catch (Exception ex) {
            WaveSurvivorMod.LOGGER.error("[MonolithFall] Échec de l'arrêt de la horde", ex);
        }
        // Bilan
        int total = 0;
        UUID best = null;
        int bestK = 0;
        if (s.kills != null) for (Map.Entry<UUID, Integer> e : s.kills.entrySet()) {
            total += e.getValue();
            if (e.getValue() > bestK) { bestK = e.getValue(); best = e.getKey(); }
        }
        String bestName = null;
        if (best != null) {
            ServerPlayer bp = server.getPlayerList().getPlayer(best);
            bestName = bp != null ? bp.getGameProfile().getName() : null;
        }
        String time = String.format("%d:%02d", dur / 60, dur % 60);
        MutableComponent retry = Component.literal(WSLang.t("fall.retry")).withStyle(st -> st
                .withColor(ChatFormatting.GOLD).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wavesurvivor retry"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(WSLang.t("fall.retry_hover")))));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(WSLang.t("fall.sum_head")));
            p.sendSystemMessage(Component.literal(WSLang.t("fall.sum_horde", WSLang.t(s.hordeName))));
            p.sendSystemMessage(Component.literal(WSLang.t(s.kingdom ? "fall.sum_assault" : "fall.sum_wave", s.wave)));
            p.sendSystemMessage(Component.literal(WSLang.t("fall.sum_time", time)));
            p.sendSystemMessage(Component.literal(WSLang.t("fall.sum_kills", total)));
            if (bestName != null) p.sendSystemMessage(Component.literal(WSLang.t("fall.sum_best", bestName, bestK)));
            p.sendSystemMessage(Component.literal(WSLang.t("fall.sum_profaned")));
            p.sendSystemMessage(Component.literal("   ").append(retry));
        }
        WaveSurvivorMod.LOGGER.info("[MonolithFall] Fin de la séquence : horde « {} » vaincue (vague {}, {} kills, {})", s.hordeName, s.wave, total, time);
    }

    /** Fermeture du serveur pendant la séquence : on l'abandonne (la horde est arrêtée proprement ailleurs). */
    public static void cancel() { SEQ = null; }

    /** « ↻ Réessayer » : rouvre l'écran de reconsécration du dernier autel tombé. */
    public static int retry(net.minecraft.commands.CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) return 0;
        if (lastPos == null || HordeManager.get().isRunning()) {
            p.displayClientMessage(Component.literal(WSLang.t("fall.retry_none")), true);
            return 0;
        }
        if (!p.level().dimension().location().toString().equals(lastDim)) {
            p.displayClientMessage(Component.literal(WSLang.t("fall.retry_dim")), true);
            return 0;
        }
        AltarManager.openBindScreen(p, lastPos);
        return 1;
    }
}
