package com.wavesurvivor.horde.renaissance;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * HÉRITAGE ET RENAISSANCE (1.5).
 *  - Arbre d'Héritage : 3 branches (Survivant, Marchand, Seigneur) de 5 nœuds, à débloquer dans l'ordre, payés en PR
 *    (5 / 10 / 15 / 25 / 40). Les stats permanentes restent comme 4e branche « Corps ».
 *  - Renaissance (prestige) : après avoir investi assez de PR (60 / 120 / 200 / 300 / 450) et gagné au moins une horde
 *    depuis la dernière renaissance → l'Héritage et les stats repartent à zéro (reliques et PR non dépensés conservés),
 *    rang +1 (5 max) : +1 emplacement Relique, +5 % de PR gagnés, titre et aura.
 * Données : dans les données persistantes du joueur (même endroit que les PR, conservées à la mort).
 */
public class Heritage {

    public enum Branch { SURVIVOR, MERCHANT, LORD }

    public static final int MAX_NODE = 5;
    public static final int[] NODE_COST = {5, 10, 15, 25, 40};
    public static final int MAX_RANK = 5;
    public static final int[] RANK_THRESHOLD = {60, 120, 200, 300, 450};

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        CompoundTag persisted = root.getCompound("PlayerPersisted");
        CompoundTag d = persisted.getCompound("ws_heritage");
        persisted.put("ws_heritage", d);
        root.put("PlayerPersisted", persisted);
        return d;
    }

    // ─── Lecture ───

    /** Niveau débloqué dans une branche (0 à 5). Côté client : valeurs synchronisées. */
    public static int level(Player p, Branch b) {
        if (p == null) return 0;
        if (p.level().isClientSide) return CLIENT_NODES[b.ordinal()];
        return data(p).getInt("n_" + b.name());
    }

    /** Le nœud n (1 à 5) de cette branche est-il débloqué ? (utilisé par les effets, lot C) */
    public static boolean has(Player p, Branch b, int node) {
        return level(p, b) >= node;
    }

    public static int rank(Player p) {
        if (p == null) return 0;
        if (p.level().isClientSide) return CLIENT_RANK;
        return data(p).getInt("rank");
    }

    public static int invested(Player p) {
        return data(p).getInt("invested");
    }

    public static boolean wonSince(Player p) {
        return data(p).getBoolean("won");
    }

    /** Multiplicateur de PR gagnés : +5 % par rang (+10 % avec le nœud Marchand 4). */
    public static double prMultiplier(Player p) {
        return 1.0 + 0.05 * rank(p) + (has(p, Branch.MERCHANT, 4) ? 0.15 : 0.0);
    }

    public static boolean canRebirth(Player p) {
        int r = rank(p);
        return r < MAX_RANK && invested(p) >= RANK_THRESHOLD[r] && wonSince(p);
    }

    /** Clé de traduction du titre du rang (vide au rang 0). */
    public static String titleKey(int rank) {
        return rank <= 0 ? "" : "heritage.title." + Math.min(MAX_RANK, rank);
    }

    // ─── Écriture ───

    /** PR investis depuis la dernière renaissance (Héritage + stats permanentes). */
    public static void addInvested(Player p, int pr) {
        CompoundTag d = data(p);
        d.putInt("invested", d.getInt("invested") + Math.max(0, pr));
    }

    /** Victoire de horde : la Renaissance devient possible (si le seuil est atteint). */
    public static void markWin(ServerPlayer p) {
        data(p).putBoolean("won", true);
        sync(p);
    }

    /** Achète le prochain nœud d'une branche. */
    public static void buy(ServerPlayer p, Branch b) {
        int lvl = level(p, b);
        if (lvl >= MAX_NODE) return;
        int cost = NODE_COST[lvl];
        int pts = RenaissanceStore.getPoints(p);
        if (pts < cost) {
            p.sendSystemMessage(Component.literal(WSLang.t("srv.pas_assez_de_pr") + cost + WSLang.t("srv.requis_tu_en_as") + pts + "."));
            return;
        }
        RenaissanceStore.addPoints(p, -cost);
        boolean lordBefore = b == Branch.LORD && lvl + 1 == 4 && anyone(p.server, Branch.LORD, 4);
        data(p).putInt("n_" + b.name(), lvl + 1);
        addInvested(p, cost);
        // Seigneur 4 acheté pendant une partie : le Monolithe gagne ses +10 % de PV tout de suite
        if (b == Branch.LORD && lvl + 1 == 4 && !lordBefore && com.wavesurvivor.altar.AltarDefense.isActive()) {
            float[] snap = com.wavesurvivor.altar.AltarDefense.snapshot();
            if (snap != null) {
                com.wavesurvivor.altar.AltarDefense.addMaxHp(snap[1] * 0.20f);
                for (ServerPlayer q : p.server.getPlayerList().getPlayers()) q.sendSystemMessage(Component.literal(WSLang.t("heritage.monolith_bonus")));
            }
        }
        p.sendSystemMessage(Component.literal(WSLang.t("heritage.bought", WSLang.t("heritage." + b.name().toLowerCase() + "." + (lvl + 1)),
                cost, RenaissanceStore.getPoints(p))));
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
        applyPassives(p);
        sync(p);
    }

    /** Renaissance : Héritage et stats remis à zéro, rang +1. */
    public static void rebirth(ServerPlayer p) {
        if (!canRebirth(p)) {
            p.sendSystemMessage(Component.literal(WSLang.t("heritage.cannot", RANK_THRESHOLD[Math.min(MAX_RANK - 1, rank(p))])));
            return;
        }
        CompoundTag d = data(p);
        for (Branch b : Branch.values()) d.remove("n_" + b.name());
        d.putInt("invested", 0);
        d.putBoolean("won", false);
        int r = d.getInt("rank") + 1;
        d.putInt("rank", r);
        // Stats permanentes : remises à zéro
        RenaissanceStats.resetAll(p);
        RenaissanceStore.clearStats(p);
        applyPassives(p);
        applyRelicSlots(p);
        p.refreshDisplayName();
        p.refreshTabListName();
        // Mise en scène
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 120, 0.8, 1.2, 0.8, 0.1);
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 120, 0.6, 1, 0.6, 0.6);
            sl.playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.2f, 0.8f);
            sl.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        }
        Component msg = Component.literal(WSLang.t("heritage.reborn", p.getGameProfile().getName(), WSLang.t(titleKey(r)), r));
        for (ServerPlayer q : p.server.getPlayerList().getPlayers()) q.sendSystemMessage(msg);
        WaveSurvivorMod.LOGGER.info("[Héritage] {} renaît au rang {}", p.getGameProfile().getName(), r);
        sync(p);
    }

    // ─── Effets permanents simples (Survivant 1 et 4) ───

    private static final UUID HP_ID = UUID.fromString("4f5b6a21-1c2d-4e3f-9a8b-7c6d5e4f3a21");
    private static final UUID ARMOR_ID = UUID.fromString("4f5b6a21-1c2d-4e3f-9a8b-7c6d5e4f3a22");
    private static final UUID TOUGH_ID = UUID.fromString("4f5b6a21-1c2d-4e3f-9a8b-7c6d5e4f3a23");

    /** Survivant 1 : +2 PV max ; Survivant 4 : +1 armure et +1 robustesse. */
    public static void applyPassives(ServerPlayer p) {
        mod(p, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, HP_ID, has(p, Branch.SURVIVOR, 1) ? 6.0 : 0);
        mod(p, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, ARMOR_ID, has(p, Branch.SURVIVOR, 4) ? 2.0 : 0);
        mod(p, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS, TOUGH_ID, has(p, Branch.SURVIVOR, 4) ? 2.0 : 0);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private static void mod(ServerPlayer p, net.minecraft.world.entity.ai.attributes.Attribute a, UUID id, double v) {
        var inst = p.getAttribute(a);
        if (inst == null) return;
        if (inst.getModifier(id) != null) inst.removeModifier(id);
        if (v != 0) inst.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, "ws_heritage", v,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }

    // ─── Emplacements Relique (Curios) : +1 par rang, 4 max ───

    private static final UUID SLOT_ID = UUID.fromString("4f5b6a21-1c2d-4e3f-9a8b-7c6d5e4f3a30");

    public static void applyRelicSlots(ServerPlayer p) {
        if (!com.wavesurvivor.item.RelicEffects.curiosAvailable()) return;
        int extra = Math.min(3, rank(p));
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object lazy = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class).invoke(null, p);
            if (!(lazy instanceof net.minecraftforge.common.util.LazyOptional<?> lo) || lo.resolve().isEmpty()) return;
            Object h = lo.resolve().get();
            Method remove = h.getClass().getMethod("removeSlotModifier", String.class, UUID.class);
            remove.invoke(h, "relic", SLOT_ID);
            if (extra > 0) {
                Method add = h.getClass().getMethod("addPermanentSlotModifier", String.class, UUID.class, String.class, double.class,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.class);
                add.invoke(h, "relic", SLOT_ID, "ws_renaissance_rank", (double) extra,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
            }
        } catch (Exception ex) {
            WaveSurvivorMod.LOGGER.warn("[Héritage] Emplacements Relique Curios non ajustés : {}", ex.getMessage());
        }
    }

    // ─── Synchronisation client (onglet Héritage, emplacements sans Curios) ───

    public static int CLIENT_RANK = 0;
    public static int[] CLIENT_NODES = new int[3];
    public static int CLIENT_INVESTED = 0;
    public static boolean CLIENT_WON = false;

    public static void sync(ServerPlayer p) {
        if (com.wavesurvivor.network.NetworkHandler.CHANNEL == null) return;
        int[] n = new int[3];
        for (Branch b : Branch.values()) n[b.ordinal()] = level(p, b);
        com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.wavesurvivor.network.HeritageSyncPacket(rank(p), n, invested(p), wonSince(p)));
    }

    // ─── Événements ───

    /** Un joueur connecté a-t-il ce nœud ? (effets d'équipe : Seigneur) */
    public static boolean anyone(net.minecraft.server.MinecraftServer s, Branch b, int node) {
        if (s == null) return false;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (!p.isSpectator() && has(p, b, node)) return true;
        return false;
    }

    /** Survivant 3 : réapparition plus rapide (multijoueur Kingdom). */
    public static int respawnSeconds(ServerPlayer p, int base) {
        return has(p, Branch.SURVIVOR, 3) ? Math.max(2, base - 5) : base;
    }

    /** Survivant 3 : 5 s d'invulnérabilité en revenant au combat (Kingdom). */
    public static void onRevive(ServerPlayer p) {
        if (has(p, Branch.SURVIVOR, 3)) {
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 4, false, true));
        }
    }

    /** Survivant 5 : coups fatals déjà évités pendant cette horde (2 maximum). */
    private static final java.util.Map<UUID, Integer> LAST_STAND = new java.util.HashMap<>();

    /** Début de horde : Marchand 1 (+10 émeraudes), Marchand 3 (+1 clé de coffre), Survivant 5 rechargé. */
    public static void onHordeStart(net.minecraft.server.MinecraftServer s) {
        LAST_STAND.clear();
        if (s == null) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (has(p, Branch.MERCHANT, 1)) give(p, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 20));
            if (has(p, Branch.MERCHANT, 3)) {
                for (int k = 0; k < 2; k++) {
                    var key = com.wavesurvivor.item.RelicEffects.randomKeyStack();
                    if (!key.isEmpty()) give(p, key);
                }
            }
        }
    }

    /** Début de vague / d'Assaut : Survivant 2 (Régénération I 10 s). */
    public static void onWaveStart(net.minecraft.server.MinecraftServer s) {
        if (s == null) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (has(p, Branch.SURVIVOR, 2)) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 1, false, true));
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 0, false, true));
            }
        }
    }

    private static void give(ServerPlayer p, net.minecraft.world.item.ItemStack st) {
        if (!p.getInventory().add(st)) p.drop(st, false);
    }

    /** Survivant 5 : une fois par horde, un coup fatal laisse à 1 PV (avant le Talisman du Dernier Souffle). */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public void onLethal(net.minecraftforge.event.entity.living.LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.isCreative()) return;
        if (e.getAmount() < p.getHealth() || !com.wavesurvivor.horde.HordeManager.get().isRunning()) return;
        if (!has(p, Branch.SURVIVOR, 5) || LAST_STAND.getOrDefault(p.getUUID(), 0) >= 2) return;
        LAST_STAND.merge(p.getUUID(), 1, Integer::sum);
        e.setAmount(Math.max(0f, p.getHealth() - 1f));
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 60, 4, false, true));
        p.displayClientMessage(Component.literal(WSLang.t("heritage.last_stand")), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    /** Marchand 2 : −10 % chez les marchands pendant une horde. */
    @SubscribeEvent
    public void onMerchant(net.minecraftforge.event.entity.player.PlayerContainerEvent.Open e) {
        if (!(e.getContainer() instanceof net.minecraft.world.inventory.MerchantMenu menu)) return;
        if (!(e.getEntity() instanceof ServerPlayer p) || !has(p, Branch.MERCHANT, 2)) return;
        if (!com.wavesurvivor.horde.HordeManager.get().isRunning()) return;
        for (net.minecraft.world.item.trading.MerchantOffer o : menu.getOffers()) {
            int cut = (int) Math.floor(o.getBaseCostA().getCount() * 0.15);
            if (cut > 0) o.addToSpecialPriceDiff(-cut);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        applyPassives(p);
        applyRelicSlots(p);
        sync(p);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { applyPassives(p); sync(p); }
    }

    /** Titre devant le pseudo (chat). */
    @SubscribeEvent
    public void onName(PlayerEvent.NameFormat e) {
        if (e.getEntity().level().isClientSide) return;
        int r = rank(e.getEntity());
        if (r > 0) e.setDisplayname(Component.literal(rankColor(r) + "[" + WSLang.t(titleKey(r)) + "] §r").append(e.getDisplayname()));
    }

    /** Titre dans la liste des joueurs (Tab). */
    @SubscribeEvent
    public void onTabName(PlayerEvent.TabListNameFormat e) {
        if (e.getEntity().level().isClientSide) return;
        int r = rank(e.getEntity());
        if (r > 0) e.setDisplayName(Component.literal(rankColor(r) + "[" + WSLang.t(titleKey(r)) + "] §r").append(e.getEntity().getName()));
    }

    private static String rankColor(int r) {
        return switch (Math.min(MAX_RANK, r)) { case 1 -> "§e"; case 2 -> "§6"; case 3 -> "§c"; case 4 -> "§d"; default -> "§b"; };
    }

    /** Aura : particules qui grandissent avec le rang. */
    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        int r = rank(p);
        if (r <= 0 || p.isSpectator() || p.isInvisible() || !(p.level() instanceof ServerLevel sl)) return;
        var type = r >= 5 ? ParticleTypes.END_ROD : r >= 3 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        sl.sendParticles(type, p.getX(), p.getY() + 0.1, p.getZ(), r, 0.35, 0.05, 0.35, 0.01);
    }
}
