package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.config.model.TotemConfigData;
import com.wavesurvivor.entity.TotemEntity;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.spawn.CustomSkillRegistry;
import com.wavesurvivor.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * totem_protection : le boss pose des TOTEMS (entité Totem, rôle « protection », dorés) autour de lui,
 * reliés par un rayon de particules. Tant qu'un totem tient : boss INVULNÉRABLE.
 * Réglages : totemCount, totemPositions (optionnel), totemRadius, totemHealth,
 *            totemRespawnSeconds (0 = jamais ; sinon réapparaissent autour de la position ACTUELLE du boss).
 * Apparence : TotemConfig (totemId) → tête au sommet.
 */
public class SkillTotemProtection extends BossSkill {

    private final List<UUID> placedTotems = new ArrayList<>();
    private boolean placed = false;
    private long allDeadSince = -1;

    public SkillTotemProtection(CustomSkillData config) {
        super(config);
    }

    @Override public long getCooldownTicks() { return 20; }
    @Override public boolean isOneShot() { return false; }
    @Override public boolean requiresTarget() { return false; }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        long now = level.getGameTime();
        if (!placed) {
            placed = true;
            placeTotems(boss, level, false);
        }

        int alive = aliveTotems(level, boss);
        if (alive > 0) {
            allDeadSince = -1;
            boss.setInvulnerable(true);
            boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, false));
            level.sendParticles(ParticleTypes.WAX_ON, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(),
                    4, 0.4, 0.5, 0.4, 0.01);
        } else {
            if (boss.isInvulnerable()) {
                boss.setInvulnerable(false);
                boss.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                broadcast(level, com.wavesurvivor.i18n.WSLang.c("totem.protect.fallen", bossName(boss))
                        .copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
            if (config.totemRespawnSeconds > 0) {
                if (allDeadSince < 0) allDeadSince = now;
                else if (now - allDeadSince >= config.totemRespawnSeconds * 20L) {
                    allDeadSince = -1;
                    placeTotems(boss, level, true);
                }
            }
        }
    }

    private void placeTotems(LivingEntity boss, ServerLevel level, boolean respawn) {
        TotemConfigData cfg = CustomSkillRegistry.getTotem(config.totemId);
        double hp = config.totemHealth > 0 ? config.totemHealth : Math.max(20, cfg != null ? cfg.baseHP : 20);
        String head = cfg != null && cfg.headItem != null && !cfg.headItem.isBlank() ? cfg.headItem : "minecraft:player_head";
        List<CustomSkillData.TotemPosition> positions = config.totemPositions;
        int count = config.totemCount > 0 ? config.totemCount : (positions != null ? positions.size() : 3);
        BlockPos base = boss.blockPosition();
        int ok = 0;
        for (int i = 0; i < count; i++) {
            int dx, dz;
            if (positions != null && i < positions.size() && !respawn) {
                dx = positions.get(i).x;
                dz = positions.get(i).z;
            } else {
                double a = 2 * Math.PI * i / count;
                dx = (int) Math.round(Math.cos(a) * config.totemRadius);
                dz = (int) Math.round(Math.sin(a) * config.totemRadius);
            }
            int x = base.getX() + dx, z = base.getZ() + dz;
            // Sol à la hauteur du boss (pas « le bloc le plus haut » : sinon totem sur un toit, un pilier ou hors de l'arène)
            int y = com.wavesurvivor.horde.spawn.SpawnZone.groundNear(level, x, z, base.getY()).getY();
            try {
                TotemEntity t = ModEntities.TOTEM.get().create(level);
                if (t == null) continue;
                t.moveTo(x + 0.5, y, z + 0.5, (float) Math.toDegrees(Math.atan2(-dx, dz)), 0);
                t.setup("protection", head, -1);
                AttributeInstance mh = t.getAttribute(Attributes.MAX_HEALTH);
                if (mh != null) mh.setBaseValue(hp);
                t.setHealth((float) hp);
                t.setCustomNameVisible(true);
                t.getPersistentData().putBoolean("ws_revenant", true);
                t.addTag(TotemHitHandler.TOTEM_TAG);
                if (level.addFreshEntity(t)) {
                    placedTotems.add(t.getUUID());
                    level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x + 0.5, y + 1.2, z + 0.5, 25, 0.3, 0.8, 0.3, 0.3);
                    ok++;
                }
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[totem_protection] Totem #{} : {}", i, e.getMessage());
            }
        }
        if (ok > 0) {
            level.playSound(null, base, SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.HOSTILE, 1.2f, 0.7f);
            broadcast(level, com.wavesurvivor.i18n.WSLang.c(respawn ? "totem.protect.raise" : "totem.protect.surround", bossName(boss), ok)
                    .copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    /** Totems vivants ; rayon de particules vers le boss + nom avec PV. */
    private int aliveTotems(ServerLevel level, LivingEntity boss) {
        int alive = 0;
        var it = placedTotems.iterator();
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.24f), 1.0f);
        while (it.hasNext()) {
            Entity e = level.getEntity(it.next());
            if (!(e instanceof TotemEntity t) || !t.isAlive()) { it.remove(); continue; }
            alive++;
            t.setCustomName(Component.literal(com.wavesurvivor.i18n.WSLang.t("totem.protect.name")).withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(Math.round(t.getHealth()) + "/" + Math.round(t.getMaxHealth())).withStyle(ChatFormatting.RED)));
            Vec3 from = t.position().add(0, 2.3, 0), to = boss.position().add(0, boss.getBbHeight() * 0.6, 0);
            Vec3 d = to.subtract(from);
            int steps = (int) Math.max(4, d.length() * 1.5);
            for (int i = 0; i <= steps; i++) {
                Vec3 p = from.add(d.scale((double) i / steps));
                level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        return alive;
    }

    private static String bossName(LivingEntity boss) {
        return boss.getCustomName() != null ? boss.getCustomName().getString() : com.wavesurvivor.i18n.WSLang.t("common.boss");
    }

    private static void broadcast(ServerLevel level, Component c) {
        for (var p : level.getServer().getPlayerList().getPlayers()) p.sendSystemMessage(c);
    }
}
