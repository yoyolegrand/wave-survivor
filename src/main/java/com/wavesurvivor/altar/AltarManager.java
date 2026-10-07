package com.wavesurvivor.altar;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.OpenAltarConfirmScreenPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;

/**
 * Cœur de la logique d'un altar :
 *   - onRightClick : cherche l'AltarEntry, valide qu'il est lié à une horde, ouvre le Screen
 *   - confirmTrigger : après clic "Lancer" dans le Screen, valide terrain + lance la horde
 */
public class AltarManager {

    /** Called from AltarBlock.use() when a player right-clicks an altar. */
    public static void onRightClick(ServerPlayer player, ServerLevel level, BlockPos pos, AltarBlock.AltarType type) {
        String dim = level.dimension().location().toString();
        AltarStore.AltarEntry entry = AltarStore.get(dim, pos);

        // Autel pas encore lié à une horde → écran de liaison (recettes)
        if (entry == null || entry.hordeName == null || entry.hordeName.isBlank()) {
            openBindScreen(player, pos);
            return;
        }

        // Vérifie qu'une horde n'est pas déjà en cours
        if (HordeManager.get().isRunning()) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_une_horde_est_deja_en_cours_imposs")));
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0f, 0.5f);
            return;
        }

        // Résout les variantes de la horde liée (Vanilla / Moddée...) + mods requis détectés
        java.util.List<OpenAltarConfirmScreenPacket.Variant> variants = new java.util.ArrayList<>();
        for (AltarRecipes.Variant v : AltarRecipes.getVariants(entry.hordeName)) {
            HordeConfigMultiData horde = findHordeConfig(v.horde);
            if (horde == null) continue;
            int totalWaves = horde.configData != null ? horde.configData.totalWaves : 0;
            int totalBosses = (horde.configData != null && horde.configData.bossWaves != null)
                    ? horde.configData.bossWaves.size() : 0;
            String specialInfo = "";
            if (horde.configData != null && horde.configData.useSpecialWaves
                    && horde.configData.specialWaves != null && !horde.configData.specialWaves.isEmpty()) {
                specialInfo = horde.configData.specialWaves.size() + com.wavesurvivor.i18n.WSLang.t("srv.variantes_possibles");
            }
            java.util.List<OpenAltarConfirmScreenPacket.Mod> mods = new java.util.ArrayList<>();
            for (HordeModRequirements.Requirement r : HordeModRequirements.of(horde)) {
                mods.add(new OpenAltarConfirmScreenPacket.Mod(r.modId(), r.installed()));
            }
            variants.add(new OpenAltarConfirmScreenPacket.Variant(
                    v.label != null ? v.label : v.horde, horde.hordeName, totalWaves, totalBosses, specialInfo, mods,
                    horde.configData != null ? horde.configData.stars : 0,
                    horde.configData != null && horde.configData.difficulties != null ? horde.configData.difficulties : "",
                    com.wavesurvivor.horde.difficulty.HordeProgress.encode(player, horde),
                    horde.configData != null && horde.configData.isKingdom(),
                    com.wavesurvivor.horde.kingdom.KingdomRoles.encodeAvailable(horde)));
        }
        if (variants.isEmpty()) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_horde_inconnue") + entry.hordeName));
            return;
        }

        // Envoie le packet pour ouvrir le screen
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenAltarConfirmScreenPacket(pos, entry.hordeName, variants));
    }

    /** Called from TriggerAltarPacket when the player clicks "Lancer" in the screen (avec la variante choisie). */
    public static void confirmTrigger(ServerPlayer player, ServerLevel level, BlockPos pos, String variantHorde) {
        confirmTrigger(player, level, pos, variantHorde, "");
    }

    /** @param mutators mutateurs cochés sur l'écran de l'autel (« id1,id2,… »), activés au démarrage de la horde. */
    public static void confirmTrigger(ServerPlayer player, ServerLevel level, BlockPos pos, String variantHorde, String mutators) {
        confirmTrigger(player, level, pos, variantHorde, mutators, "");
    }

    /** @param difficulty niveau choisi sur l'écran de l'autel (« easy », « hard »…) ; refusé par la horde → son niveau par défaut. */
    public static void confirmTrigger(ServerPlayer player, ServerLevel level, BlockPos pos, String variantHorde, String mutators, String difficulty) {
        confirmTrigger(player, level, pos, variantHorde, mutators, difficulty, "");
    }

    /** @param role rôle choisi sur l'écran de l'autel (mode Kingdom ; vide = aucun). */
    public static void confirmTrigger(ServerPlayer player, ServerLevel level, BlockPos pos, String variantHorde, String mutators, String difficulty, String role) {
        String dim = level.dimension().location().toString();
        AltarStore.AltarEntry entry = AltarStore.get(dim, pos);
        if (entry == null || entry.hordeName == null) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cet_autel_n_est_plus_valide")));
            return;
        }

        // Double check : horde pas déjà en cours (le joueur pourrait avoir gardé le screen ouvert)
        if (HordeManager.get().isRunning()) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_trop_tard_une_horde_est_deja_en_co")));
            return;
        }

        // La variante doit appartenir à cet autel (sinon : première variante)
        String chosen = null;
        for (AltarRecipes.Variant v : AltarRecipes.getVariants(entry.hordeName)) {
            if (chosen == null) chosen = v.horde;
            if (variantHorde != null && v.horde.equalsIgnoreCase(variantHorde)) { chosen = v.horde; break; }
        }
        HordeConfigMultiData horde = findHordeConfig(chosen);
        if (horde == null) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_horde_inconnue") + chosen));
            return;
        }

        // Mods requis installés ?
        java.util.List<HordeModRequirements.Requirement> reqs = HordeModRequirements.of(horde);
        if (!HordeModRequirements.allInstalled(reqs)) {
            StringBuilder miss = new StringBuilder();
            for (HordeModRequirements.Requirement r : reqs) if (!r.installed()) miss.append(miss.length() > 0 ? ", " : "").append(r.modId());
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel") + com.wavesurvivor.i18n.WSLang.t(horde.hordeName) + com.wavesurvivor.i18n.WSLang.t("srv.necessite_des_mods_absents") + miss));
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0f, 0.5f);
            return;
        }

        // Validation terrain
        int spawnRadius = horde.configData != null ? horde.configData.spawnRadius : 10;
        AltarTerrainChecker.Result result = AltarTerrainChecker.check(level, pos, spawnRadius);
        if (!result.valid) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_le_terrain_pour_invoquer_la_horde")));
            player.sendSystemMessage(Component.literal(result.errorMessage));
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0f, 0.5f);
            return;
        }

        // Déblocage : toutes les conditions doivent être remplies par le joueur qui lance
        if (!player.isCreative() && !com.wavesurvivor.horde.difficulty.HordeProgress.unlocked(player, horde)) {
            com.wavesurvivor.horde.difficulty.HordeProgress.explain(player, horde);
            return;
        }
        // OK — lance le compte à rebours de 5s + effets, puis start la horde avec override spawn
        com.wavesurvivor.horde.mutator.HordeMutators.setPending(com.wavesurvivor.horde.mutator.Mutator.parse(mutators));
        // Niveau de difficulté : doit être autorisé par la horde, sinon son niveau par défaut
        String allowedCsv = horde.configData != null ? horde.configData.difficulties : "";
        com.wavesurvivor.horde.difficulty.Difficulty diff = com.wavesurvivor.horde.difficulty.Difficulty.byId(difficulty);
        if (diff == null || !com.wavesurvivor.horde.difficulty.Difficulty.allowed(allowedCsv).contains(diff)) {
            diff = com.wavesurvivor.horde.difficulty.Difficulty.defaultFor(allowedCsv);
        }
        com.wavesurvivor.horde.difficulty.HordeDifficulty.setPending(diff);
        // Mode Kingdom : rôle de celui qui lance (appliqué au démarrage du royaume)
        if (horde.configData != null && horde.configData.isKingdom()) {
            com.wavesurvivor.horde.kingdom.KingdomRoles.setPending(player, role);
        }
        launchWithCountdown(player, level, pos, horde);
    }

    /** Compte à rebours 5s avec particules/sons puis lance la horde. */
    private static void launchWithCountdown(ServerPlayer player, ServerLevel level, BlockPos altarPos, HordeConfigMultiData horde) {
        MinecraftServer server = level.getServer();
        final int TOTAL_SECONDS = 5;

        // Broadcast initial
        broadcast(server, Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.le_pacte_est_scelle_la_horde") + com.wavesurvivor.i18n.WSLang.t(horde.hordeName) + com.wavesurvivor.i18n.WSLang.t("srv.arrive")));

        // BossBar countdown (remplace le spam chat)
        AltarCountdownBar.show(server, com.wavesurvivor.i18n.WSLang.t(horde.hordeName));
        AltarCountdownBar.update(TOTAL_SECONDS, TOTAL_SECONDS, com.wavesurvivor.i18n.WSLang.t(horde.hordeName));

        // Son initial fort
        level.playSound(null, altarPos, SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 1.0f, 0.5f);

        // Effet visuel initial : gros burst
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                altarPos.getX() + 0.5, altarPos.getY() + 1.0, altarPos.getZ() + 0.5,
                150, 1.2, 0.5, 1.2, 0.1);

        // Effets par tick pendant les 5s : colonne de flammes qui monte + pulses
        for (int t = 0; t < TOTAL_SECONDS * 20; t++) {
            final int tick = t;
            DelayedActionScheduler.schedule(server, t, () -> {
                // Update BossBar chaque tick pour smoothness
                int secondsRemaining = TOTAL_SECONDS - (tick / 20);
                AltarCountdownBar.update(secondsRemaining, TOTAL_SECONDS, com.wavesurvivor.i18n.WSLang.t(horde.hordeName));

                // Colonne de flammes verticale montante (chaque tick, monte de 0 à 6 blocks)
                double height = ((tick * 6.0) / (TOTAL_SECONDS * 20));
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        altarPos.getX() + 0.5, altarPos.getY() + 1.0 + height, altarPos.getZ() + 0.5,
                        3, 0.1, 0.05, 0.1, 0.01);

                // Cercle tournant serré autour de la base, intensité qui monte
                double intensity = 0.3 + (tick / (double)(TOTAL_SECONDS * 20)) * 0.7;
                double angleOffset = tick * 0.15;
                for (int i = 0; i < 4; i++) {
                    double angle = angleOffset + i * Math.PI / 2;
                    double dx = Math.cos(angle) * 0.8;
                    double dz = Math.sin(angle) * 0.8;
                    level.sendParticles(ParticleTypes.FLAME,
                            altarPos.getX() + 0.5 + dx,
                            altarPos.getY() + 1.2,
                            altarPos.getZ() + 0.5 + dz,
                            1, 0, intensity * 0.1, 0, 0);
                }
            }, "altar countdown tick " + t);
        }

        // À chaque seconde : broadcast + son de plus en plus grave
        for (int i = TOTAL_SECONDS; i >= 1; i--) {
            final int seconds = i;
            int delayTicks = (TOTAL_SECONDS - i) * 20;
            DelayedActionScheduler.schedule(server, delayTicks, () -> {
                // Son grave qui monte en volume à chaque tick
                float pitch = 0.5f + (TOTAL_SECONDS - seconds) * 0.15f;
                level.playSound(null, altarPos, SoundEvents.BELL_BLOCK, SoundSource.MASTER, 2.0f, pitch);
                // Gros burst de SOUL_FIRE_FLAME à chaque seconde
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        altarPos.getX() + 0.5, altarPos.getY() + 1.5, altarPos.getZ() + 0.5,
                        30, 0.5, 0.4, 0.5, 0.05);
            }, "altar tick " + seconds);
        }

        // À t=5s : explosion visuelle finale + lance la horde + hide BossBar
        DelayedActionScheduler.schedule(server, TOTAL_SECONDS * 20, () -> {
            // Cache le countdown BossBar (le HordeHudManager prend le relais)
            AltarCountdownBar.hide();

            // Explosion visuelle finale : sphère de flammes + éclair d'âmes
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    altarPos.getX() + 0.5, altarPos.getY() + 2.0, altarPos.getZ() + 0.5,
                    300, 2.0, 1.5, 2.0, 0.2);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    altarPos.getX() + 0.5, altarPos.getY() + 1.5, altarPos.getZ() + 0.5,
                    3, 0.3, 0.3, 0.3, 0);
            level.playSound(null, altarPos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 1.0f, 1.2f);

            // Lance la horde avec spawn override sur l'altar
            boolean ok = HordeManager.get().startAt(horde, server, altarPos, level.dimension().location().toString());
            if (!ok) {
                player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_impossible_de_demarrer_la_horde_de")));
            }
        }, "altar start horde");
    }

    // ─── Liaison par recette ───

    /** Ouvre l'écran listant les hordes liables avec leurs ingrédients. */
    public static void openBindScreen(ServerPlayer player, BlockPos pos) {
        var cfg = WaveSurvivorMod.getConfig();
        java.util.List<com.wavesurvivor.network.OpenAltarBindScreenPacket.Option> options = new java.util.ArrayList<>();
        if (cfg != null && cfg.hordeConfigMulti != null) {
            for (HordeConfigMultiData h : cfg.hordeConfigMulti) {
                var recipe = AltarRecipes.get(h.hordeName);
                if (recipe == null) continue;
                if (AltarRecipes.isVariantOf(h.hordeName)) continue; // variante : choisie au lancement, pas à la liaison
                java.util.List<com.wavesurvivor.network.OpenAltarBindScreenPacket.Ing> ings = new java.util.ArrayList<>();
                for (AltarRecipes.Ingredient ing : recipe) {
                    var item = ing.resolve();
                    int have = item == null ? 0
                            : (player.isCreative() ? ing.count : AltarRecipes.countInInventory(player, item));
                    ings.add(new com.wavesurvivor.network.OpenAltarBindScreenPacket.Ing(ing.item, ing.count, have));
                }
                int waves = h.configData != null ? h.configData.totalWaves : 0;
                int bosses = (h.configData != null && h.configData.bossWaves != null) ? h.configData.bossWaves.size() : 0;
                boolean kingdom = h.configData != null && h.configData.isKingdom();
                boolean unlocked = player.isCreative() || com.wavesurvivor.horde.difficulty.HordeProgress.unlocked(player, h);
                String lock = com.wavesurvivor.horde.difficulty.HordeProgress.encode(player, h);
                options.add(new com.wavesurvivor.network.OpenAltarBindScreenPacket.Option(
                        h.hordeName, waves, bosses, unlocked && AltarRecipes.hasAll(player, recipe), ings, kingdom, unlocked, lock));
            }
        }
        if (options.isEmpty()) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_aucune_horde_n_a_de_recette_de_lia")));
            return;
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new com.wavesurvivor.network.OpenAltarBindScreenPacket(pos, options));
    }

    /** Clic "Lier" : revalide tout, consomme les ingrédients, lie l'autel, puis ouvre l'écran de lancement. */
    public static void bind(ServerPlayer player, ServerLevel level, BlockPos pos, String hordeName) {
        if (!level.getBlockState(pos).is(com.wavesurvivor.registry.ModBlocks.ALTAR_RUNIC.get())) return;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_trop_loin_de_l_autel")));
            return;
        }
        String dim = level.dimension().location().toString();
        AltarStore.AltarEntry entry = AltarStore.get(dim, pos);
        if (entry != null && entry.hordeName != null && !entry.hordeName.isBlank()) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_cet_autel_est_deja_lie_a") + com.wavesurvivor.i18n.WSLang.t(entry.hordeName)));
            return;
        }
        HordeConfigMultiData horde = findHordeConfig(hordeName);
        var recipe = AltarRecipes.get(hordeName);
        if (horde == null || recipe == null) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_horde_ou_recette_inconnue") + hordeName));
            return;
        }
        if (!AltarRecipes.hasAll(player, recipe)) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_il_te_manque_des_ingredients_pour")));
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0f, 0.5f);
            return;
        }
        // Progression : une horde verrouillée ne peut pas être liée (sauf créatif)
        if (!player.isCreative() && !com.wavesurvivor.horde.difficulty.HordeProgress.unlocked(player, horde)) {
            com.wavesurvivor.horde.difficulty.HordeProgress.explain(player, horde);
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0f, 0.5f);
            return;
        }

        AltarRecipes.consume(player, recipe);
        if (entry == null) {
            entry = new AltarStore.AltarEntry(hordeName, dim, pos.getX(), pos.getY(), pos.getZ(),
                    AltarBlock.AltarType.HORDE_TRIGGER.name());
        } else {
            entry.hordeName = hordeName;
        }
        AltarRecipes.Zone zone = AltarRecipes.getZone(hordeName);
        entry.zoneRadius = zone != null ? zone.radius : 0;
        // Autel ancien sans propriétaire : celui qui le lie en devient propriétaire
        if (entry.ownerUuid == null) {
            entry.ownerUuid = player.getUUID().toString();
            entry.ownerName = player.getGameProfile().getName();
        }
        // Style de la horde (couleur + particules)
        AltarRecipes.Style style = AltarRecipes.getStyle(hordeName);
        if (style != null) applyStyle(level, pos, entry, style.dye(), style.particles);
        AltarStore.register(entry);

        // Zone (claim) : le sol se transforme en onde autour de l'autel
        if (zone != null) AltarZone.transform(level, pos, zone);

        // Effets de liaison
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 0.7f);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
                120, 0.6, 0.6, 0.6, 1.0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                40, 0.4, 0.3, 0.4, 0.02);
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.l_autel_est_desormais_lie_a") + com.wavesurvivor.i18n.WSLang.t(hordeName) + " §4§l☠"));
        WaveSurvivorMod.LOGGER.info("[Altar] {} lie l'autel {} à '{}'", player.getGameProfile().getName(), pos, hordeName);

        // Enchaîne directement sur l'écran de lancement
        onRightClick(player, level, pos, AltarBlock.AltarType.HORDE_TRIGGER);
    }

    // ─── Déliaison (shift + clic, confirmé par un 2e shift + clic dans les 5s) ───

    private static final java.util.Map<java.util.UUID, String> PENDING_UNBIND = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> PENDING_UNBIND_TIME = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long UNBIND_CONFIRM_TICKS = 100;

    public static void onShiftClick(ServerPlayer player, ServerLevel level, BlockPos pos) {
        String dim = level.dimension().location().toString();
        AltarStore.AltarEntry entry = AltarStore.get(dim, pos);
        if (entry == null || entry.hordeName == null || entry.hordeName.isBlank()) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cet_autel_n_est_lie_a_aucune_horde")));
            return;
        }
        if (HordeManager.get().isRunning()) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_impossible_de_delier_pendant_une_h")));
            return;
        }

        String key = AltarStore.posKey(dim, pos);
        long now = level.getGameTime();
        java.util.UUID id = player.getUUID();
        Long t = PENDING_UNBIND_TIME.get(id);
        if (key.equals(PENDING_UNBIND.get(id)) && t != null && now - t <= UNBIND_CONFIRM_TICKS) {
            PENDING_UNBIND.remove(id);
            PENDING_UNBIND_TIME.remove(id);
            String old = entry.hordeName;
            entry.hordeName = null;
            entry.zoneRadius = 0;
            applyStyle(level, pos, entry, net.minecraft.world.item.DyeColor.RED, AltarParticles.AMES.id); // style neutre
            AltarStore.register(entry);
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.8f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    25, 0.3, 0.3, 0.3, 0.02);
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_le_lien_avec") + com.wavesurvivor.i18n.WSLang.t(old) + com.wavesurvivor.i18n.WSLang.t("srv.est_rompu_l_autel_est_vierge")));
        } else {
            PENDING_UNBIND.put(id, key);
            PENDING_UNBIND_TIME.put(id, now);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0f, 0.5f);
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_shift_clic_a_nouveau_5s_pour_delie") + com.wavesurvivor.i18n.WSLang.t(entry.hordeName)
                    + com.wavesurvivor.i18n.WSLang.t("srv.les_ingredients_ne_sont_pas_rendus")));
        }
    }

    /** Applique couleur (état du bloc) + particules (store) — ne sauvegarde pas le store. */
    public static void applyStyle(ServerLevel level, BlockPos pos, AltarStore.AltarEntry entry,
                                  net.minecraft.world.item.DyeColor color, String particles) {
        var state = level.getBlockState(pos);
        if (state.hasProperty(AltarBlock.COLOR) && state.getValue(AltarBlock.COLOR) != color) {
            level.setBlock(pos, state.setValue(AltarBlock.COLOR, color), 3);
        }
        entry.particle = AltarParticles.exists(particles) ? particles.toLowerCase() : AltarParticles.AMES.id;
    }

    // ─── Couleur (panneau Custom) ───

    /** Recolore les runes/tapis de l'autel avec un colorant (1 consommé, sauf créatif). */
    public static void setColor(ServerPlayer player, ServerLevel level, BlockPos pos, net.minecraft.world.item.DyeColor color) {
        var state = level.getBlockState(pos);
        if (!state.is(com.wavesurvivor.registry.ModBlocks.ALTAR_RUNIC.get()) || !state.hasProperty(AltarBlock.COLOR)) return;
        AltarStore.AltarEntry owner = AltarStore.get(level.dimension().location().toString(), pos);
        if (owner == null || !owner.canCustomize(player)) { denyCustom(player, owner); return; }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_trop_loin_de_l_autel")));
            return;
        }
        if (state.getValue(AltarBlock.COLOR) == color) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_l_autel_est_deja_de_cette_couleur")));
            return;
        }
        var dye = net.minecraft.world.item.DyeItem.byColor(color);
        if (!player.isCreative()) {
            net.minecraft.world.item.ItemStack found = null;
            for (var st : player.getInventory().items) if (st.is(dye)) { found = st; break; }
            if (found == null) for (var st : player.getInventory().offhand) if (st.is(dye)) { found = st; break; }
            if (found == null) {
                player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_il_te_faut_un_colorant")
                        + AltarColors.nameFr(color).toLowerCase() + "."));
                level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0f, 0.5f);
                return;
            }
            found.shrink(1);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }

        level.setBlock(pos, state.setValue(AltarBlock.COLOR, color), 3);

        int rgb = AltarColors.rgb(color);
        var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(
                ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), 1.4f);
        level.sendParticles(dust, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 60, 0.45, 0.45, 0.45, 0.0);
        level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.7f);
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.les_runes_de_l_autel_prennent_la_teinte") + AltarColors.nameFr(color) + "§7."));
    }

    // ─── Menu Custom : accès + particules ───

    private static void denyCustom(ServerPlayer player, AltarStore.AltarEntry entry) {
        String who = entry != null && entry.ownerName != null ? " (§f" + entry.ownerName + "§7)" : "";
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_la_personnalisation_est_reservee_a") + who + com.wavesurvivor.i18n.WSLang.t("srv.et_aux_ops")));
    }

    /** Bouton "Custom" : vérifie les droits puis ouvre le menu côté client. */
    public static void openCustom(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(com.wavesurvivor.registry.ModBlocks.ALTAR_RUNIC.get())) return;
        AltarStore.AltarEntry entry = AltarStore.get(level.dimension().location().toString(), pos);
        if (entry == null || !entry.canCustomize(player)) { denyCustom(player, entry); return; }
        String particle = entry.particle != null ? entry.particle : AltarParticles.AMES.id;
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new com.wavesurvivor.network.OpenAltarCustomPacket(pos, particle));
    }

    /** Onglet Particules : changement gratuit, propriétaire / op. */
    public static void setParticle(ServerPlayer player, ServerLevel level, BlockPos pos, String particleId) {
        if (!level.getBlockState(pos).is(com.wavesurvivor.registry.ModBlocks.ALTAR_RUNIC.get())) return;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
        AltarStore.AltarEntry entry = AltarStore.get(level.dimension().location().toString(), pos);
        if (entry == null || !entry.canCustomize(player)) { denyCustom(player, entry); return; }
        if (!AltarParticles.exists(particleId)) return;
        AltarParticles p = AltarParticles.byId(particleId);
        entry.particle = p.id;
        AltarStore.register(entry);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.2f);
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.particules_de_l_autel") + com.wavesurvivor.i18n.WSLang.t(p.nameFr)));
    }

    private static HordeConfigMultiData findHordeConfig(String hordeName) {
        var cfg = WaveSurvivorMod.getConfig();
        if (cfg == null || cfg.hordeConfigMulti == null) return null;
        for (HordeConfigMultiData h : cfg.hordeConfigMulti) {
            if (hordeName.equals(h.hordeName)) return h;
        }
        return null;
    }

    private static void broadcast(MinecraftServer server, Component msg) {
        if (server == null) return;
        for (var p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }
}
