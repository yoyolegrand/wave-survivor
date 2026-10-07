package com.wavesurvivor.horde.mutator;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * EFFETS DES MUTATEURS (actifs seulement pendant une horde qui en a) :
 *  - Frénésie / Blindés / Brutes / Avortons : vitesse, PV, dégâts des unités à leur apparition ;
 *  - Nuée : ×1,5 unités (voir HordeEntity.getCountForWave et le budget des Assauts Kingdom) ;
 *  - Instables : petite explosion à la mort (aucun dégât au terrain) ; Venimeux : chaque coup empoisonne ;
 *  - Fragiles : pas de régénération naturelle ; Affamés : effet Faim permanent ;
 *  - Une seule vie : un joueur mort revient en spectateur ; si tout le monde est tombé, la horde est perdue ;
 *  - Enragés / Escorte : voir BossDirector ; Nuit éternelle : nuit figée (Kingdom : voir KingdomManager) ;
 *  - Sol instable : une Anomalie sous les pieds d'un joueur toutes les 30 s ; Monolithe fragile : PV ÷ 2 ;
 *  - Bonus : butin et récompenses de fin multipliés (scaleQty).
 * Toutes les règles de jeu modifiées sont restaurées à la fin.
 */
public class MutatorEffects {

    private static final Random RNG = new Random();
    /** Joueurs tombés avec « Une seule vie » (passés en spectateur, remis en Survie à la fin). */
    private static final Set<UUID> FALLEN = new HashSet<>();
    private static Boolean prevRegen = null, prevDaylight = null;

    // ─── Début / fin de horde ───

    static void onBegin(MinecraftServer server, boolean kingdom) {
        FALLEN.clear();
        if (server == null) return;
        GameRules rules = server.getGameRules();
        if (HordeMutators.on(Mutator.FRAGILE)) {
            prevRegen = rules.getBoolean(GameRules.RULE_NATURAL_REGENERATION);
            rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        }
        if (HordeMutators.on(Mutator.ETERNAL_NIGHT) && !kingdom) { // le Kingdom gère son propre jour / nuit
            prevDaylight = rules.getBoolean(GameRules.RULE_DAYLIGHT);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            ServerLevel ow = server.overworld();
            long t = ow.getDayTime();
            ow.setDayTime(t + Math.floorMod(18000 - Math.floorMod(t, 24000L), 24000L));
        }
        if (HordeMutators.on(Mutator.GLASS_MONOLITH)) com.wavesurvivor.altar.AltarDefense.scaleMaxHp(0.5);
    }

    static void onEnd(MinecraftServer server) {
        if (server != null) {
            GameRules rules = server.getGameRules();
            if (prevRegen != null) rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(prevRegen, server);
            if (prevDaylight != null) rules.getRule(GameRules.RULE_DAYLIGHT).set(prevDaylight, server);
            for (UUID id : FALLEN) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null && p.isSpectator()) p.setGameMode(GameType.SURVIVAL);
            }
        }
        prevRegen = null;
        prevDaylight = null;
        FALLEN.clear();
    }

    // ─── Unités ───

    /** Appelé à l'enregistrement de chaque unité de horde (après ses statistiques). */
    public static void applyToUnit(Entity e) {
        if (!(e instanceof LivingEntity le) || !HordeMutators.any()) return;
        var tag = le.getPersistentData();
        if (tag.getBoolean("ws_mutated")) return;
        tag.putBoolean("ws_mutated", true);
        double hp = 1, spd = 1, dmg = 1;
        if (HordeMutators.on(Mutator.FRENZY)) spd *= 1.3;
        if (HordeMutators.on(Mutator.ARMORED)) hp *= 1.5;
        if (HordeMutators.on(Mutator.BRUTES)) { hp *= 1.5; dmg *= 1.5; spd *= 0.8; }
        if (HordeMutators.on(Mutator.RUNTS)) { hp *= 0.5; spd *= 1.4; }
        scale(le, Attributes.MAX_HEALTH, hp);
        scale(le, Attributes.MOVEMENT_SPEED, spd);
        scale(le, Attributes.ATTACK_DAMAGE, dmg);
        if (hp != 1) le.setHealth(le.getMaxHealth());
    }

    private static void scale(LivingEntity le, Attribute a, double f) {
        if (f == 1) return;
        AttributeInstance i = le.getAttribute(a);
        if (i != null) i.setBaseValue(i.getBaseValue() * f);
    }

    /** Nuée : multiplicateur du nombre d'unités. */
    public static double countMultiplier() { return HordeMutators.on(Mutator.SWARM) ? 1.5 : 1.0; }

    /**
     * Récompenses : quantité × (bonus des mutateurs × niveau de difficulté), arrondi au hasard (ex. 3 × 1,45 → 4 ou 5 ;
     * 3 × 0,75 → 2 ou 3).
     */
    public static int scaleQty(int q) {
        double m = HordeMutators.multiplier() * com.wavesurvivor.horde.difficulty.HordeDifficulty.rewardMultiplier();
        if (Math.abs(m - 1.0) < 0.0001 || q <= 0) return q;
        double v = q * m;
        int base = (int) Math.floor(v);
        return base + (RNG.nextDouble() < v - base ? 1 : 0);
    }

    // ─── Événements ───

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!HordeMutators.any() || !(event.getEntity().level() instanceof ServerLevel level)) return;
        LivingEntity dead = event.getEntity();
        // Instables : explosion à la mort (sans casser de blocs)
        if (HordeMutators.on(Mutator.VOLATILE) && MobRegistry.get(dead.getUUID()) != null) {
            level.explode(null, dead.getX(), dead.getY() + 0.5, dead.getZ(), 1.6f, Level.ExplosionInteraction.NONE);
        }
        // Une seule vie : le joueur reviendra en spectateur
        if (HordeMutators.on(Mutator.ONE_LIFE) && dead instanceof ServerPlayer p && HordeManager.get().isRunning()) {
            FALLEN.add(p.getUUID());
            for (ServerPlayer o : level.getServer().getPlayerList().getPlayers()) {
                o.sendSystemMessage(Component.literal(WSLang.t("mutator.one_life_fallen", p.getGameProfile().getName())));
            }
        }
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!HordeMutators.on(Mutator.VENOMOUS) || !(event.getEntity() instanceof ServerPlayer p)) return;
        Entity src = event.getSource().getEntity();
        if (src != null && MobRegistry.get(src.getUUID()) != null) p.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && FALLEN.contains(p.getUUID()) && HordeManager.get().isRunning()) {
            p.setGameMode(GameType.SPECTATOR);
            p.displayClientMessage(Component.literal(WSLang.t("mutator.one_life_spectator")), false);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !HordeMutators.any() || !HordeManager.get().isRunning()) return;
        if (com.wavesurvivor.altar.MonolithFall.active()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        // Affamés : effet Faim entretenu
        if (HordeMutators.on(Mutator.HUNGRY) && now % 200 == 0) {
            for (ServerPlayer p : players) if (!p.isSpectator() && !p.isCreative()) p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 260, 0, true, false));
        }
        // Sol instable : une Anomalie sous les pieds d'un joueur toutes les 30 s
        if (HordeMutators.on(Mutator.UNSTABLE) && now % 600 == 0) {
            List<ServerPlayer> ok = players.stream().filter(p -> !p.isSpectator() && !p.isCreative()).toList();
            if (!ok.isEmpty()) {
                ServerPlayer p = ok.get(RNG.nextInt(ok.size()));
                try {
                    com.wavesurvivor.horde.anomaly.AnomalyManager.spawnAt(p.serverLevel(), p.position(), new com.wavesurvivor.altar.AltarRecipes.Anomalies());
                } catch (Exception ex) {
                    WaveSurvivorMod.LOGGER.warn("[Mutateurs] Anomalie impossible : {}", ex.getMessage());
                }
            }
        }
        // Une seule vie : tout le monde est tombé → horde perdue
        if (HordeMutators.on(Mutator.ONE_LIFE) && !FALLEN.isEmpty() && now % 40 == 0) {
            boolean anyAlive = players.stream().anyMatch(p -> !FALLEN.contains(p.getUUID()) && !p.isSpectator());
            if (!anyAlive) {
                for (ServerPlayer p : players) p.sendSystemMessage(Component.literal(WSLang.t("mutator.one_life_wiped")));
                HordeManager.get().stop();
            }
        }
    }
}
