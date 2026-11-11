package com.wavesurvivor.item;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2.0 — FAMILLE DE RELIQUES « FAILLE » (🌌, 4 pièces) et ses bonus d'ensemble. Ces reliques ne viennent que du Coffre du Titan.
 *   🎲 Dé de la Faille     : relances (et bannissement au niveau III) de bénédiction au début de chaque horde
 *   🔏 Sceau du Titan      : carte Épique / Légendaire garantie dans les premiers choix de bénédictions (une fois par horde)
 *   🏆 Trophée du Titan    : marques de boss (+% de dégâts, 5 max) et PR par boss en fin de horde
 *   💜 Cœur du Titan       : +% de PV max ; après un coup reçu, la prochaine attaque fait +25 % (recharge)
 * Ensemble : 2 = +1 Chance · 3 = +15 % contre les boss et les Titans, aucun recul des ondes de choc · 4 = Bouclier de Faille.
 * Les chiffres ne suivent pas tous le multiplicateur commun (×1 / ×1,5 / ×2 / ×2,5) : ils sont donnés ici niveau par niveau (I à IV).
 */
public class RiftRelics {

    // ─── Chiffres par niveau (I à IV ; le niveau IV vient des Éclats de Faille, via une légendaire) ───

    private static int idx(int lvl) { return Math.max(1, Math.min(4, lvl)) - 1; }

    /** Dé : relances données au début de la horde. */
    public static int rerolls(int lvl) { return lvl <= 0 ? 0 : new int[]{1, 2, 2, 3}[idx(lvl)]; }

    /** Dé : bannissements donnés au début de la horde (niveau III et plus). */
    public static int bans(int lvl) { return lvl >= 3 ? 1 : 0; }

    /**
     * Sceau : rareté garantie dans le {@code menu}-ième choix de bénédictions de la horde (0 = le premier), ou -1.
     * 2 = Épique, 3 = Légendaire. I : 1er Épique · II : 2 premiers Épiques · III : 1er Légendaire · IV : 1er Légendaire et 2e Épique.
     */
    public static int sealRarity(int lvl, int menu) {
        if (lvl <= 0) return -1;
        return switch (Math.min(4, lvl)) {
            case 1 -> menu == 0 ? 2 : -1;
            case 2 -> menu <= 1 ? 2 : -1;
            case 3 -> menu == 0 ? 3 : -1;
            default -> menu == 0 ? 3 : menu == 1 ? 2 : -1;
        };
    }

    /** Trophée : dégâts par marque (0,03 / 0,045 / 0,06 / 0,075). */
    public static double trophyPerMark(int lvl) { return lvl <= 0 ? 0 : 0.03 * RelicEffects.mult(lvl); }

    /** Trophée : PR par boss vaincu, en fin de horde. */
    public static int trophyPr(int lvl) { return lvl <= 0 ? 0 : new int[]{5, 7, 10, 12}[idx(lvl)]; }

    public static final int MAX_MARKS = 5;

    /** Cœur : part de PV max en plus (0,05 / 0,075 / 0,10 / 0,125). */
    public static double heartHp(int lvl) { return lvl <= 0 ? 0 : 0.05 * RelicEffects.mult(lvl); }

    /** Cœur : recharge du coup renforcé, en secondes. */
    public static int heartCooldown(int lvl) { return new int[]{30, 22, 15, 10}[idx(lvl)]; }

    /** Cœur : bonus du coup renforcé (fixe). */
    public static final float HEART_BONUS = 0.25f;

    /** Ensemble 3 pièces : dégâts en plus contre les boss et les Titans. */
    public static final float SET3_BOSS = 0.15f;

    /** Ensemble 4 pièces : Bouclier de Faille (part des PV max). */
    public static final float SHIELD = 0.10f;

    // ─── Textes des infobulles (vrais chiffres du niveau) ───

    /** Nombre affiché : « 4,5 » en français, « 4.5 » en anglais, sans « ,0 ». */
    public static String num(double v) {
        v = Math.round(v * 10) / 10.0;
        String s = v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        return "fr".equals(WSLang.get()) ? s.replace('.', ',') : s;
    }

    /** Lignes d'effet d'une relique de la Faille à ce niveau. */
    public static List<String> effects(String id, int lvl) {
        List<String> out = new ArrayList<>();
        switch (id) {
            case "de_faille" -> {
                out.add(WSLang.t("relic.de_faille.rerolls", rerolls(lvl)));
                if (bans(lvl) > 0) out.add(WSLang.t("relic.de_faille.bans", bans(lvl)));
            }
            case "sceau_titan" -> out.add(WSLang.t("relic.sceau_titan.l" + Math.max(1, Math.min(4, lvl))));
            case "trophee_titan" -> {
                out.add(WSLang.t("relic.trophee_titan.marks", num(trophyPerMark(lvl) * 100), MAX_MARKS));
                out.add(WSLang.t("relic.trophee_titan.pr", trophyPr(lvl)));
            }
            case "coeur_titan" -> {
                out.add(WSLang.t("relic.coeur_titan.hp", num(heartHp(lvl) * 100)));
                out.add(WSLang.t("relic.coeur_titan.strike", Math.round(HEART_BONUS * 100), heartCooldown(lvl)));
            }
            default -> { }
        }
        return out;
    }

    // ─── Niveaux portés ───

    public static int dice(Player p) { return RelicEffects.level(p, ModItems.DE_FAILLE.get()); }
    public static int seal(Player p) { return RelicEffects.level(p, ModItems.SCEAU_TITAN.get()); }
    public static int trophy(Player p) { return RelicEffects.level(p, ModItems.TROPHEE_TITAN.get()); }
    public static int heart(Player p) { return RelicEffects.level(p, ModItems.COEUR_TITAN.get()); }

    /** Relances données par le Dé au début de la horde. */
    public static int bonusRerolls(Player p) { return rerolls(dice(p)); }

    public static int bonusBans(Player p) { return bans(dice(p)); }

    /** Boss du mod, ou Titan de la Faille. */
    public static boolean isBossOrTitan(Entity e) {
        return e != null && (com.wavesurvivor.horde.boss.BossManager.isBoss(e.getUUID()) || e.getPersistentData().getBoolean("ws_rift_titan"));
    }

    /** Aucun recul des ondes de choc (compétences de boss et du Titan) : Ceinture de Plomb, ensemble Faille 3 pièces, Couronne du Titan. */
    public static boolean shockImmune(LivingEntity e) {
        return RelicEffects.immovable(e) || (e instanceof Player p && RelicSets.has(p, RelicSets.Family.RIFT, 3));
    }

    // ─── 🏆 Trophée du Titan : marques de la horde en cours ───

    /** Boss et Titans vaincus pendant la partie en cours (marques, 5 max pour les dégâts). */
    private static int marks = 0;
    /** Boss déjà comptés (le Titan peut être signalé deux fois : mort normale, puis fin de combat). */
    private static final Set<UUID> COUNTED = new HashSet<>();

    /** Nouvelle partie : plus aucune marque. */
    public static void resetRun() {
        marks = 0;
        COUNTED.clear();
    }

    public static int marks() { return marks; }

    /** Un boss ou un Titan vient de tomber : une marque de plus, annoncée aux porteurs du Trophée. */
    public static void onBossDefeated(MinecraftServer server, UUID id) {
        if (server == null || id == null || !COUNTED.add(id)) return;
        marks++;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (trophy(p) > 0) p.displayClientMessage(Component.literal(WSLang.t("relic.trophee_titan.mark", Math.min(MAX_MARKS, marks), MAX_MARKS)), true);
        }
    }

    /** PR du Trophée en fin de partie : PR par boss vaincu (0 sans Trophée). */
    public static int trophyPrFor(Player p, int bosses) {
        int l = trophy(p);
        return l <= 0 || bosses <= 0 ? 0 : bosses * trophyPr(l);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDeath(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !isBossOrTitan(v)) return;
        onBossDefeated(v.level().getServer(), v.getUUID());
    }

    // ─── 💜 Cœur du Titan : coup renforcé ───

    /** Joueur → [coup renforcé prêt (1/0), tick où la recharge se termine]. */
    private static final Map<UUID, long[]> HEART = new ConcurrentHashMap<>();

    // ─── Dégâts ───

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide) return;
        long now = target.level().getGameTime();
        // Coup reçu par un porteur du Cœur : sa prochaine attaque est renforcée (si la recharge est finie)
        if (target instanceof ServerPlayer hurt && e.getSource().getEntity() instanceof LivingEntity && e.getSource().getEntity() != hurt) {
            int hl = heart(hurt);
            if (hl > 0) {
                long[] h = HEART.computeIfAbsent(hurt.getUUID(), k -> new long[2]);
                if (h[0] == 0 && now >= h[1]) {
                    h[0] = 1;
                    hurt.displayClientMessage(Component.literal(WSLang.t("relic.coeur_titan.ready")), true);
                }
            }
        }
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || target == p) return;
        float mult = 1f;
        boolean boss = isBossOrTitan(target);
        // 🏆 Trophée : +% de dégâts par marque (5 max)
        int tl = trophy(p);
        if (tl > 0 && marks > 0) mult *= 1f + (float) (trophyPerMark(tl) * Math.min(MAX_MARKS, marks));
        // 🌌 Ensemble 3 pièces : +15 % contre les boss et les Titans
        if (boss && RelicSets.has(p, RelicSets.Family.RIFT, 3)) mult *= 1f + SET3_BOSS;
        // 💜 Cœur : coup renforcé consommé
        long[] h = HEART.get(p.getUUID());
        if (h != null && h[0] == 1) {
            int hl = heart(p);
            h[0] = 0;
            if (hl > 0) {
                mult *= 1f + HEART_BONUS;
                h[1] = now + heartCooldown(hl) * 20L;
                if (p.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
                    sl.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);
                }
            }
        }
        if (mult != 1f) e.setAmount(e.getAmount() * mult);
    }

    // ─── Bonus permanents (PV du Cœur, Chance de l'ensemble) et suivi du Bouclier ───

    private static final UUID HEART_HP_ID = UUID.fromString("5e2a7c31-0b4d-4f6e-9a18-3c7d2e8f1a01");
    private static final UUID SET_LUCK_ID = UUID.fromString("5e2a7c31-0b4d-4f6e-9a18-3c7d2e8f1a02");

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        trackShield(p);
        if (p.tickCount % 20 != 0) return;
        // 💜 Cœur : +% de PV max (le modificateur est remplacé quand le niveau change)
        double hp = heartHp(heart(p));
        AttributeInstance mh = p.getAttribute(Attributes.MAX_HEALTH);
        if (mh != null) {
            AttributeModifier cur = mh.getModifier(HEART_HP_ID);
            if (cur != null && (hp <= 0 || Math.abs(cur.getAmount() - hp) > 1e-6)) mh.removeModifier(HEART_HP_ID);
            if (hp > 0 && mh.getModifier(HEART_HP_ID) == null) mh.addTransientModifier(new AttributeModifier(HEART_HP_ID, "ws_titan_heart", hp, AttributeModifier.Operation.MULTIPLY_TOTAL));
            if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        }
        // 🌌 Ensemble 2 pièces : +1 Chance
        AttributeInstance luck = p.getAttribute(Attributes.LUCK);
        if (luck != null) {
            boolean on = RelicSets.has(p, RelicSets.Family.RIFT, 2);
            boolean has = luck.getModifier(SET_LUCK_ID) != null;
            if (on && !has) luck.addTransientModifier(new AttributeModifier(SET_LUCK_ID, "ws_set_rift_luck", 1.0, AttributeModifier.Operation.ADDITION));
            else if (!on && has) luck.removeModifier(SET_LUCK_ID);
        }
    }

    // ─── 🌌 Ensemble 4 pièces : Bouclier de Faille ───
    // S'AJOUTE aux cœurs d'absorption de la bénédiction de l'Aube (jamais par l'effet ABSORPTION vanilla, qui remplace la valeur et la retire à
    // sa fin). On mémorise ce qu'il en reste : il est consommé en premier ; au début de la vague suivante, le reste est retiré avant d'en redonner.

    /** Joueur → [reste du bouclier, absorption vue au dernier tick]. */
    private static final Map<UUID, float[]> SHIELDS = new ConcurrentHashMap<>();

    private static void trackShield(ServerPlayer p) {
        float[] s = SHIELDS.get(p.getUUID());
        if (s == null) return;
        float cur = p.getAbsorptionAmount();
        if (cur < s[1]) s[0] = Math.max(0f, s[0] - (s[1] - cur));   // absorption perdue : le bouclier part en premier
        s[1] = cur;
        if (s[0] <= 0f) SHIELDS.remove(p.getUUID());
    }

    /** Début de vague, AVANT la bénédiction de l'Aube : on retire ce qui reste du bouclier précédent (pas de cumul). */
    public static void shieldBeforeWave(MinecraftServer srv) {
        if (srv == null) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            float[] s = SHIELDS.remove(p.getUUID());
            if (s == null) continue;
            float cur = p.getAbsorptionAmount();
            p.setAbsorptionAmount(Math.max(0f, cur - Math.min(s[0], cur)));
        }
    }

    /** Début de vague, APRÈS la bénédiction de l'Aube : nouveau bouclier de 10 % des PV max, ajouté à l'absorption actuelle. */
    public static void shieldAfterWave(MinecraftServer srv) {
        if (srv == null) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || !RelicSets.has(p, RelicSets.Family.RIFT, 4)) continue;
            float add = p.getMaxHealth() * SHIELD;
            if (add <= 0f) continue;
            float now = p.getAbsorptionAmount() + add;
            p.setAbsorptionAmount(now);
            SHIELDS.put(p.getUUID(), new float[]{add, now});
            p.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            p.displayClientMessage(Component.literal(WSLang.t("set.rift.shield_up", num(add / 2.0))), true);
        }
    }

    /** Début de vague (hordes, Assauts, Contre-offensive et Titan de la Faille) : bouclier seul, quand la bénédiction de l'Aube n'est pas appelée. */
    public static void onWaveStart(MinecraftServer srv) {
        shieldBeforeWave(srv);
        shieldAfterWave(srv);
    }

    // ─── Jauge de la Faille (Coffre du Titan) : par joueur, sauvegardée ───

    private static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        CompoundTag persisted = root.getCompound("PlayerPersisted");
        root.put("PlayerPersisted", persisted);
        return persisted;
    }

    /** Bonus de chance Légendaire dans le Coffre du Titan, en points de pourcentage. */
    public static int gauge(Player p) { return p == null ? 0 : persisted(p).getInt("ws_rift_gauge"); }

    public static void setGauge(Player p, int v) { if (p != null) persisted(p).putInt("ws_rift_gauge", Math.max(0, v)); }

    @SubscribeEvent
    public void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        HEART.remove(e.getEntity().getUUID());
        SHIELDS.remove(e.getEntity().getUUID());
    }
}
