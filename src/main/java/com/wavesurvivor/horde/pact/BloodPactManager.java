package com.wavesurvivor.horde.pact;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Random;

/**
 * Cœur du système Pacte de Sang.
 * - tryActivate() : point d'entrée depuis l'event handler (check cooldown, select, animation)
 * - selectEffect() : random weighted OU return all (sealed)
 * - startAnimation() : rolling display + son pitch qui monte, puis applyEffect à la fin
 * - applyEffect() : applique les MobEffects (single ou pack ou tous les effets pour sealed)
 * - createHeadItem() : construit le player_head avec skullOwner NBT + Lore Custom Head ID
 */
public class BloodPactManager {

    private static final String NBT_COOLDOWN_KEY = "BloodPactCooldown";
    private static final Random RNG = new Random();

    /**
     * Tentative d'activation d'un pacte. Renvoie true si le clic a été géré (à cancel dans l'event).
     */
    public static boolean tryActivate(ServerPlayer player, ItemStack itemInHand, BloodPactConfig pact) {
        MinecraftServer server = player.server;
        long currentTick = server.getTickCount();
        int cooldownExpire = player.getPersistentData().getInt(NBT_COOLDOWN_KEY);

        // Check cooldown
        if (currentTick < cooldownExpire) {
            int remainingSeconds = (int) Math.ceil((cooldownExpire - currentTick) / 20.0);
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.pacte_votre_sang_est_deja_lie_par_un_pac")));
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.attendez_encore") + remainingSeconds + com.wavesurvivor.i18n.WSLang.t("srv.s_avant_d_en_sceller_un_nouveau")));
            playSound((ServerLevel) player.level(), player, "minecraft:block.chest.locked", 1.0f, 0.5f);
            return true;
        }

        // Select effect maintenant (pour connaître max duration + set cooldown avant animation)
        Object selectedEffect = selectEffect(pact);
        int maxDuration = getMaxDuration(selectedEffect, pact);
        int animationTicks = pact.showAnimation() ? pact.animationDurationSeconds() * 20 : 0;

        // Set cooldown = expire à currentTick + animation + max duration
        player.getPersistentData().putInt(NBT_COOLDOWN_KEY, (int) (currentTick + maxDuration + animationTicks));

        // Lance l'animation (avec l'effet pré-sélectionné)
        startAnimation(player, pact, selectedEffect);

        WaveSurvivorMod.LOGGER.info("[BloodPact] '{}' active pacte '{}' (cooldown {}s)",
                player.getName().getString(), pact.pactName(), (maxDuration + animationTicks) / 20);
        return true;
    }

    /**
     * Sélectionne un effet :
     *  - SEALED : renvoie List<BloodPactEffect> (tous les effets)
     *  - RANDOM : renvoie un seul BloodPactEffect (weighted par chance)
     */
    @SuppressWarnings("unchecked")
    private static Object selectEffect(BloodPactConfig pact) {
        if (pact.pactType() == BloodPactConfig.PactType.SEALED) {
            return pact.effects();
        }
        // RANDOM
        double roll = RNG.nextDouble();
        double acc = 0;
        for (BloodPactEffect e : pact.effects()) {
            acc += e.chance();
            if (roll <= acc) return e;
        }
        return pact.effects().get(RNG.nextInt(pact.effects().size()));
    }

    private static int getMaxDuration(Object effect, BloodPactConfig pact) {
        if (effect instanceof List<?> list) {
            int max = 0;
            for (Object o : list) {
                if (o instanceof BloodPactEffect e) {
                    int d = e.maxDuration();
                    if (d > max) max = d;
                }
            }
            return max;
        }
        if (effect instanceof BloodPactEffect e) return e.maxDuration();
        return 0;
    }

    // ================================================================
    // ANIMATION
    // ================================================================

    private static void startAnimation(ServerPlayer player, BloodPactConfig pact, Object finalEffect) {
        MinecraftServer server = player.server;

        // Consomme la tête AVANT l'animation (comme le JS)
        if (pact.consumeHead()) {
            player.getMainHandItem().shrink(1);
        }

        // Son activation
        playSound((ServerLevel) player.level(), player, pact.sounds().activation(), 1.0f, 1.0f);

        // Titre selon type
        String title = pact.pactType() == BloodPactConfig.PactType.SEALED
                ? com.wavesurvivor.i18n.WSLang.t("srv.pacte_scelle")
                : com.wavesurvivor.i18n.WSLang.t("srv.pacte_de_sang");
        player.sendSystemMessage(Component.literal(title));

        if (!pact.showAnimation()) {
            applyEffect(player, pact, finalEffect);
            return;
        }

        // Roulette : steps tous les 10 ticks
        int steps = (pact.animationDurationSeconds() * 20) / 10;
        for (int i = 0; i < steps; i++) {
            final int step = i;
            DelayedActionScheduler.schedule(server, 10 + i * 10, () -> {
                if (!player.isAlive()) return;
                // Random preview effect
                BloodPactEffect preview = pact.effects().get(RNG.nextInt(pact.effects().size()));
                String displayText = preview.type() == BloodPactEffect.Type.PACK
                        ? com.wavesurvivor.i18n.WSLang.t(preview.packName()) : com.wavesurvivor.i18n.WSLang.t(preview.displayName());
                String prefix = preview.isPositive() ? "§a" : "§c";
                player.sendSystemMessage(Component.literal(prefix + "⟲ " + displayText));

                // Son pitch qui monte 1.0 → 1.6
                float pitch = 1.0f + (step / (float) Math.max(steps, 1)) * 0.6f;
                playSound((ServerLevel) player.level(), player, pact.sounds().rolling(), 0.8f, pitch);
            }, "blood pact anim " + i);
        }

        // Apply à la fin
        DelayedActionScheduler.schedule(server, 10 + steps * 10, () -> {
            if (!player.isAlive()) return;
            applyEffect(player, pact, finalEffect);
        }, "blood pact apply");
    }

    // ================================================================
    // APPLY EFFECT
    // ================================================================

    @SuppressWarnings("unchecked")
    private static void applyEffect(ServerPlayer player, BloodPactConfig pact, Object effectOrList) {
        boolean[] hp = {false, false}; // [hasPositive, hasNegative]

        // SEALED : liste de tous les effets
        if (pact.pactType() == BloodPactConfig.PactType.SEALED && effectOrList instanceof List<?> list) {
            player.sendSystemMessage(Component.literal("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.pacte_scelle_active")));
            for (Object o : list) {
                if (o instanceof BloodPactEffect e) {
                    applyOneEffect(player, e, hp);
                }
            }
            player.sendSystemMessage(Component.literal("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        }
        // RANDOM : un seul effet (peut être pack ou single)
        else if (effectOrList instanceof BloodPactEffect e) {
            applyOneEffect(player, e, hp);
        }

        // Son final selon mix positif/négatif
        MinecraftServer server = player.server;
        ServerLevel level = (ServerLevel) player.level();
        if (hp[0] && hp[1]) {
            playSound(level, player, pact.sounds().positive(), 0.5f, 1.0f);
            DelayedActionScheduler.schedule(server, 5, () -> {
                playSound(level, player, pact.sounds().negative(), 0.5f, 0.9f);
            }, "blood pact mixed sound");
        } else if (hp[0]) {
            playSound(level, player, pact.sounds().positive(), 1.0f, 1.2f);
        } else if (hp[1]) {
            playSound(level, player, pact.sounds().negative(), 1.0f, 0.8f);
        }

        // Particules autour du joueur
        spawnParticles(level, player, pact.particles());
    }

    /** Applique un BloodPactEffect (single ou pack) et update hp[]. */
    private static void applyOneEffect(ServerPlayer player, BloodPactEffect effect, boolean[] hp) {
        if (effect.type() == BloodPactEffect.Type.PACK) {
            player.sendSystemMessage(Component.literal("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            player.sendSystemMessage(Component.literal("§d§l[" + com.wavesurvivor.i18n.WSLang.t(effect.packName()) + "]"));
            for (BloodPactSubEffect sub : effect.packEffects()) {
                applyMobEffect(player, sub.effectId(), sub.duration(), sub.amplifier());
                String prefix = sub.isPositive() ? "§a✓" : "§c✗";
                String name = sub.effectId().replace("minecraft:", "");
                player.sendSystemMessage(Component.literal(prefix + " §f" + name
                        + com.wavesurvivor.i18n.WSLang.t("srv.niv_944c") + (sub.amplifier() + 1) + ", " + (sub.duration() / 20) + "s)"));
                if (sub.isPositive()) hp[0] = true; else hp[1] = true;
            }
            player.sendSystemMessage(Component.literal("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        } else {
            // SINGLE
            applyMobEffect(player, effect.effectId(), effect.duration(), effect.amplifier());
            String prefix = effect.isPositive() ? com.wavesurvivor.i18n.WSLang.t("srv.benediction") : com.wavesurvivor.i18n.WSLang.t("srv.malediction");
            player.sendSystemMessage(Component.literal("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            player.sendSystemMessage(Component.literal(prefix + " §f" + com.wavesurvivor.i18n.WSLang.t(effect.displayName())));
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.niveau_d45e") + (effect.amplifier() + 1)
                    + com.wavesurvivor.i18n.WSLang.t("srv.duree") + (effect.duration() / 20) + "s"));
            player.sendSystemMessage(Component.literal("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            if (effect.isPositive()) hp[0] = true; else hp[1] = true;
        }
    }

    private static void applyMobEffect(ServerPlayer player, String effectId, int duration, int amplifier) {
        try {
            MobEffect mob = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(effectId));
            if (mob == null) {
                WaveSurvivorMod.LOGGER.warn("[BloodPact] MobEffect inconnu : {}", effectId);
                return;
            }
            player.addEffect(new MobEffectInstance(mob, duration, amplifier, false, true));
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[BloodPact] Erreur addEffect '{}' : {}", effectId, e.getMessage());
        }
    }

    // ================================================================
    // Reset cooldown (respawn + commande)
    // ================================================================

    public static void resetCooldown(ServerPlayer player) {
        player.getPersistentData().putInt(NBT_COOLDOWN_KEY, 0);
    }

    // ================================================================
    // Give head item (commande /ws pacte give)
    // ================================================================

    /**
     * Construit un ItemStack player_head configuré pour ce pacte :
     *   - SkullOwner NBT (skin Base64)
     *   - CustomName = displayName
     *   - Lore contenant le headCustomId (utilisé pour matcher au right-click)
     */
    public static ItemStack createHeadItem(BloodPactConfig pact) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        try {
            // Parse le string SkullOwner et l'applique à l'item
            String wrappedNbt = "{" + pact.skullOwnerNbt() + "}";
            CompoundTag parsed = TagParser.parseTag(wrappedNbt);
            CompoundTag itemTag = head.getOrCreateTag();
            if (parsed.contains("SkullOwner")) {
                itemTag.put("SkullOwner", parsed.get("SkullOwner"));
            }

            // Display : Name + Lore avec headCustomId
            CompoundTag display = new CompoundTag();
            display.putString("Name", "{\"text\":\"" + pact.displayName().replace("\"", "\\\"") + "\",\"italic\":false}");
            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf("{\"text\":\"" + pact.headCustomId() + "\",\"color\":\"gray\",\"italic\":false}"));
            display.put("Lore", lore);
            itemTag.put("display", display);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[BloodPact] Erreur createHeadItem : {}", e.getMessage());
        }
        return head;
    }

    // ================================================================
    // Helpers
    // ================================================================

    private static void playSound(ServerLevel level, ServerPlayer player, String soundId, float vol, float pitch) {
        if (soundId == null || soundId.isBlank()) return;
        try {
            SoundEvent snd = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
            if (snd != null) {
                level.playSound(null, player.blockPosition(), snd, SoundSource.PLAYERS, vol, pitch);
            }
        } catch (Exception ignore) {}
    }

    private static void spawnParticles(ServerLevel level, ServerPlayer player, String particleId) {
        try {
            String id = particleId != null && particleId.contains(":") ? particleId : "minecraft:soul_fire_flame";
            Object p = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(id));
            if (p instanceof ParticleOptions po) {
                level.sendParticles(po,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        40, 0.5, 0.5, 0.5, 0.1);
            }
        } catch (Exception ignore) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.5, 0.5, 0.1);
        }
    }
}
