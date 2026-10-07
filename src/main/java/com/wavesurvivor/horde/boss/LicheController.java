package com.wavesurvivor.horde.boss;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.altar.AltarDefense;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MÉCANIQUES DE LICHE (boss CustomEntity avec bossConfig.licheMechanics = true) — Morvhal.
 *
 *   Phase 1 (100 → 60%) : compétences normales (drain de vie, volée d'os, marque funèbre) + invocations.
 *   Phase 2 (≤ 60%)     : BOUCLIER d'invulnérabilité + 3 PHYLACTÈRES immobiles autour de l'arène,
 *                         reliés à la Liche par un rayon d'âmes. Les détruire tous brise le bouclier.
 *   Phase 3 (≤ 30%)     : NOVA FUNÈBRE (onde qui projette et blesse les joueurs proches), puis la Liche
 *                         SIPHONNE le monolithe toutes les 2 s (Défense du Monolithe) et s'en soigne.
 */
public class LicheController {

    private static final float PHASE2_AT = 0.60f;
    private static final float PHASE3_AT = 0.30f;
    private static final int PHYLACTERY_COUNT = 3;
    private static final double PHYLACTERY_HEALTH = 40;
    private static final double PHYLACTERY_RING = 7;
    private static final float SIPHON_AMOUNT = 6f;
    private static final int SIPHON_INTERVAL = 40;
    private static final String NO_REVIVE_TAG = "ws_revenant";

    private static class State {
        final UUID liche;
        final BlockPos arena;
        int phase = 1;
        boolean shield = false;
        final List<UUID> phylacteries = new ArrayList<>();
        long lastSiphon = 0;
        State(UUID liche, BlockPos arena) { this.liche = liche; this.arena = arena; }
    }

    private static final Map<UUID, State> LICHES = new ConcurrentHashMap<>();

    public static void register(LivingEntity liche, BlockPos spawnCenter) {
        // L'arène = le monolithe s'il est défendu, sinon le centre de spawn du boss
        BlockPos altar = AltarDefense.activePos();
        LICHES.put(liche.getUUID(), new State(liche.getUUID(), altar != null ? altar : spawnCenter));
        WaveSurvivorMod.LOGGER.info("[Liche] Mécaniques activées pour {}", liche.getName().getString());
    }

    public static boolean isShielded(UUID id) {
        State s = LICHES.get(id);
        return s != null && s.shield;
    }

    // ─── Tick ───

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || LICHES.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % 5 != 0) return;

        for (State s : LICHES.values()) {
            LivingEntity liche = find(server, s.liche);
            if (liche == null || !liche.isAlive()) {
                cleanup(server, s);
                LICHES.remove(s.liche);
                continue;
            }
            ServerLevel level = (ServerLevel) liche.level();
            float pct = liche.getHealth() / liche.getMaxHealth();

            // Phase 2 : bouclier + Phylactères
            if (s.phase == 1 && pct <= PHASE2_AT) enterPhase2(level, liche, s);

            if (s.shield) {
                tickShield(level, liche, s, now);
            } else if (s.phase == 2 && pct <= PHASE3_AT) {
                enterPhase3(level, liche, s);
            }

            // Phase 3 : siphon du monolithe
            if (s.phase == 3 && now - s.lastSiphon >= SIPHON_INTERVAL) {
                s.lastSiphon = now;
                siphon(level, liche);
            }
        }
    }

    private static void enterPhase2(ServerLevel level, LivingEntity liche, State s) {
        s.phase = 2;
        s.shield = true;
        for (int i = 0; i < PHYLACTERY_COUNT; i++) {
            double a = Math.PI * 2 * i / PHYLACTERY_COUNT + Math.PI / 6;
            int x = s.arena.getX() + (int) Math.round(Math.cos(a) * PHYLACTERY_RING);
            int z = s.arena.getZ() + (int) Math.round(Math.sin(a) * PHYLACTERY_RING);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Skeleton p = EntityType.SKELETON.create(level);
            if (p == null) continue;
            p.moveTo(x + 0.5, y, z + 0.5, 0, 0);
            p.setNoAi(true);
            p.setPersistenceRequired();
            p.setCustomName(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.phylactere")));
            p.setCustomNameVisible(true);
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SOUL_LANTERN)); // visuel + pas de brûlure au soleil
            p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            for (EquipmentSlot slot : EquipmentSlot.values()) p.setDropChance(slot, 0f);
            AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(PHYLACTERY_HEALTH); p.setHealth((float) PHYLACTERY_HEALTH); }
            AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(1.0);
            p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0, false, false));
            p.getPersistentData().putBoolean(NO_REVIVE_TAG, true); // la Résurrection ne le relève pas
            if (level.addFreshEntity(p)) {
                s.phylacteries.add(p.getUUID());
                level.sendParticles(ParticleTypes.SCULK_SOUL, x + 0.5, y + 1, z + 0.5, 30, 0.4, 0.8, 0.4, 0.03);
            }
        }
        level.playSound(null, liche.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2f, 0.5f);
        broadcast(level, com.wavesurvivor.i18n.WSLang.t("srv.morvhal_s_entoure_d_un_bouclier_d_ames_d")
                + s.phylacteries.size() + com.wavesurvivor.i18n.WSLang.t("srv.phylacteres_pour_le_briser"));
    }

    private static void tickShield(ServerLevel level, LivingEntity liche, State s, long now) {
        // Phylactères encore en vie
        s.phylacteries.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        if (s.phylacteries.isEmpty()) {
            s.shield = false;
            level.playSound(null, liche.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2f, 0.5f);
            level.sendParticles(ParticleTypes.SOUL, liche.getX(), liche.getY() + 1, liche.getZ(), 60, 0.8, 1, 0.8, 0.1);
            broadcast(level, com.wavesurvivor.i18n.WSLang.t("srv.le_bouclier_de_morvhal_vole_en_eclats_fr"));
            return;
        }
        // Halo du bouclier
        for (int i = 0; i < 6; i++) {
            double a = (now * 0.15) + i * Math.PI / 3;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, liche.getX() + Math.cos(a) * 1.1,
                    liche.getY() + 1 + Math.sin(now * 0.1 + i) * 0.6, liche.getZ() + Math.sin(a) * 1.1, 1, 0, 0, 0, 0);
        }
        // Rayons Phylactère → Liche (toutes les 10 ticks)
        if (now % 10 == 0) {
            for (UUID id : s.phylacteries) {
                Entity p = level.getEntity(id);
                if (p != null) beam(level, p.position().add(0, 1.6, 0), liche.position().add(0, 1.2, 0));
            }
        }
    }

    private static void enterPhase3(ServerLevel level, LivingEntity liche, State s) {
        s.phase = 3;
        // NOVA FUNÈBRE : projette et blesse les joueurs dans un rayon de 8 blocs
        level.sendParticles(ParticleTypes.SONIC_BOOM, liche.getX(), liche.getY() + 1, liche.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, liche.getX(), liche.getY() + 1, liche.getZ(), 120, 3, 1, 3, 0.1);
        level.playSound(null, liche.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2f, 0.6f);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, liche.getBoundingBox().inflate(8),
                pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
            Vec3 push = p.position().subtract(liche.position()).normalize().scale(1.6);
            p.hurt(level.damageSources().indirectMagic(liche, liche), 6f);
            p.setDeltaMovement(push.x, 0.7, push.z);
            p.hurtMarked = true;
        }
        broadcast(level, com.wavesurvivor.i18n.WSLang.t("srv.nova_funebre_morvhal_puise_desormais_dan"));
    }

    private static void siphon(ServerLevel level, LivingEntity liche) {
        BlockPos altar = AltarDefense.activePos();
        if (altar == null) return;
        float taken = AltarDefense.siphon(level, SIPHON_AMOUNT);
        if (taken <= 0) return;
        liche.heal(taken);
        beam(level, Vec3.atCenterOf(altar).add(0, 0.6, 0), liche.position().add(0, 1.2, 0));
        level.playSound(null, altar, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.2f, 0.7f);
    }

    // ─── Bouclier : annule les dégâts ───

    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        State s = LICHES.get(event.getEntity().getUUID());
        if (s == null || !s.shield) return;
        event.setCanceled(true);
        LivingEntity liche = event.getEntity();
        if (liche.level() instanceof ServerLevel level) {
            level.playSound(null, liche.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1f, 0.6f);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, liche.getX(), liche.getY() + 1, liche.getZ(), 8, 0.4, 0.5, 0.4, 0.1);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer p) {
            p.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.le_bouclier_d_ames_est_actif_detruisez_l")), true);
        }
    }

    // ─── Utils ───

    private static void beam(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int steps = (int) Math.max(4, d.length() * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 pt = from.add(d.scale((double) i / steps));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
    }

    private static void broadcast(ServerLevel level, String msg) {
        Component c = Component.literal(msg);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) p.sendSystemMessage(c);
    }

    private static LivingEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e instanceof LivingEntity le) return le;
        }
        return null;
    }

    private static void cleanup(MinecraftServer server, State s) {
        for (UUID id : s.phylacteries) {
            for (ServerLevel lvl : server.getAllLevels()) {
                Entity e = lvl.getEntity(id);
                if (e != null) { e.discard(); break; }
            }
        }
        s.phylacteries.clear();
    }

    /** Fin de horde : retire les Phylactères restants et oublie les Liches. */
    public static void clearAll(MinecraftServer server) {
        if (server != null) for (State s : LICHES.values()) cleanup(server, s);
        LICHES.clear();
    }
}
