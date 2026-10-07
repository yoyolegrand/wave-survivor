package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.altar.AltarDefense;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.KingdomTreasuryPacket;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;

/**
 * TRÉSOR COMMUN DU ROYAUME (mode Kingdom uniquement) : Monnaie, Pierre, Bois, Fer, Essence de brèche.
 * Remplace les émeraudes et les blocs physiques pour tous les achats et améliorations du royaume.
 *  - Monnaie : chaque émeraude ramassée est créditée et disparaît (le Glaneur la crédite aussi) ;
 *  - Pierre / Bois / Fer : chaque bloc NATUREL cassé crédite 1 (le bloc tombe quand même) — les blocs posés par un
 *    joueur (ou créés par un fluide) ne rapportent rien, même posés avant la partie (voir {@link PlacedBlocks}) ;
 *  - Fer : aussi à la destruction d'un gisement ;
 *  - Essence : catalyseur détruit, gardiens tués, brèche épuisée, boss tué, coups de pioche sur un gisement.
 * Valeurs synchronisées avec tous les clients (panneau à droite de l'écran, boutique du Monolithe).
 */
public final class KingdomTreasury {

    private KingdomTreasury() {}

    public enum Res {
        MONEY("money"), STONE("stone"), WOOD("logs"), IRON("iron"), ESSENCE("essence");
        public final String key;
        Res(String key) { this.key = key; }
    }

    public static final int N = Res.values().length;

    private static final int[] AMT = new int[N];
    private static boolean on = false;
    private static boolean dirty = false;

    /** Copie côté client (remplie par le paquet de synchronisation). */
    public static final int[] CLIENT = new int[N];
    public static boolean clientOn = false;

    // ─── État ───

    public static boolean active() {
        return on && KingdomManager.isActive();
    }

    /** Début de partie Kingdom : trésor à zéro. */
    public static void start(MinecraftServer server) {
        Arrays.fill(AMT, 0);
        on = true;
        sync(server);
    }

    /** Fin de partie : trésor vidé, panneau masqué. */
    public static void stop(MinecraftServer server) {
        boolean was = on;
        on = false;
        Arrays.fill(AMT, 0);
        if (was) sync(server);
    }

    /** Reprise de partie : remet le trésor exactement à ces montants. */
    static void restoreAmounts(int[] v) {
        if (v == null) return;
        for (int i = 0; i < Math.min(v.length, AMT.length); i++) AMT[i] = Math.max(0, v[i]);
        dirty = true;
    }

    /** Sauvegarde de partie : copie des montants. */
    static int[] amounts() {
        return AMT.clone();
    }

    public static int get(Res r) {
        return AMT[r.ordinal()];
    }

    public static void add(Res r, int n) {
        if (!active() || n <= 0) return;
        AMT[r.ordinal()] += n;
        KingdomReport.onGain(r, n); // bilan de fin d'Assaut
        dirty = true;
    }

    /** Gain annoncé à toute l'équipe (Essence…) : « +5 Essence de brèche (Gardiens vaincus) ». */
    public static void reward(Res r, int n, String reasonKey) {
        if (!active() || n <= 0) return;
        // Présage « Lune de sang » : l'Essence gagnée pendant l'Assaut est doublée
        if (r == Res.ESSENCE && KingdomOmens.is(KingdomOmens.Omen.BLOOD_MOON)
                && KingdomManager.phase() == KingdomManager.Phase.ASSAULT) n *= 2;
        add(r, n);
        ServerLevel lvl = KingdomManager.level();
        if (lvl == null) return;
        Component msg = WSLang.c("kingdom.treasury.reward", n, WSLang.t("kingdom.res." + r.key), WSLang.t(reasonKey));
        for (ServerPlayer p : lvl.getServer().getPlayerList().getPlayers()) p.sendSystemMessage(msg);
    }

    public static boolean has(Res r, int n) {
        return AMT[r.ordinal()] >= n;
    }

    /** Retire sans vérifier (appeler après has / canAfford). */
    public static void spend(Res r, int n) {
        AMT[r.ordinal()] = Math.max(0, AMT[r.ordinal()] - Math.max(0, n));
        dirty = true;
    }

    /** Retrait de monnaie en émeraudes physiques (pour commercer avec les marchands). */
    public static void withdraw(ServerPlayer p, int n) {
        if (!active()) return;
        int take = Math.min(n, get(Res.MONEY));
        if (take <= 0) {
            p.displayClientMessage(WSLang.c("kingdom.treasury.empty"), true);
            return;
        }
        spend(Res.MONEY, take);
        ItemStack st = new ItemStack(AltarDefense.currencyItem(), take);
        if (!p.getInventory().add(st)) p.drop(st, false);
        p.displayClientMessage(WSLang.c("kingdom.treasury.withdrawn", take), true);
        sync(p.getServer());
    }

    // ─── Synchronisation ───

    public static void sync(MinecraftServer server) {
        dirty = false;
        if (server == null || NetworkHandler.CHANNEL == null) return;
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new KingdomTreasuryPacket(on, AMT.clone()));
    }

    public static void syncTo(ServerPlayer p) {
        if (NetworkHandler.CHANNEL == null) return;
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new KingdomTreasuryPacket(on, AMT.clone()));
    }

    // ─── Classement des blocs ───

    /** Ressource rapportée par un bloc cassé (null = rien). */
    public static Res classify(BlockState st) {
        if (st.is(BlockTags.LOGS)) return Res.WOOD;
        if (st.is(BlockTags.IRON_ORES)) return Res.IRON;
        if (st.is(Tags.Blocks.STONE) || st.is(Tags.Blocks.COBBLESTONE)
                || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.BASE_STONE_NETHER)) return Res.STONE;
        return null;
    }

    // ─── Événements ───

    public static class Events {

        /** Synchronisation régulière (au plus 4 fois par seconde, seulement si quelque chose a changé). */
        @SubscribeEvent
        public void treasuryTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !dirty) return;
            if (e.getServer().getTickCount() % 5 == 0) sync(e.getServer());
        }

        @SubscribeEvent
        public void treasuryLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer sp) syncTo(sp);
        }

        /** Émeraude ramassée pendant une partie Kingdom → créditée au trésor. */
        @SubscribeEvent
        public void treasuryPickup(EntityItemPickupEvent e) {
            if (!active() || !(e.getEntity() instanceof ServerPlayer p)) return;
            ItemStack st = e.getItem().getItem();
            // Récupérateur : Bois / Fer / Pierre réservés, crédités au trésor
            if (st.hasTag() && st.getTag().contains(KingdomRoleExtras.SCAV_TAG)) {
                String tag = st.getTag().getString(KingdomRoleExtras.SCAV_TAG);
                Res r = "iron".equals(tag) ? Res.IRON : "stone".equals(tag) ? Res.STONE : Res.WOOD;
                add(r, st.getCount());
                e.getItem().discard();
                e.setCanceled(true);
                p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3f, 1.2f);
                return;
            }
            if (!AltarDefense.isCurrency(st)) return;
            add(Res.MONEY, st.getCount());
            e.getItem().discard();
            e.setCanceled(true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3f, 1.6f);
        }

        /** Bloc posé (avant ou pendant la partie) : retenu, il ne rapportera rien une fois recassé. */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void treasuryPlace(BlockEvent.EntityPlaceEvent e) {
            if (!(e.getLevel() instanceof ServerLevel lvl)) return;
            if (classify(e.getPlacedBlock()) != null) PlacedBlocks.get(lvl).add(e.getPos());
        }

        /** Pierre / cobblestone créée par un fluide (générateur) : comptée comme posée. */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void treasuryFluid(BlockEvent.FluidPlaceBlockEvent e) {
            if (!(e.getLevel() instanceof ServerLevel lvl)) return;
            if (classify(e.getNewState()) != null) PlacedBlocks.get(lvl).add(e.getPos());
        }

        /** Bloc cassé : crédit s'il est naturel. */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void treasuryBreak(BlockEvent.BreakEvent e) {
            if (!(e.getLevel() instanceof ServerLevel lvl)) return;
            BlockPos pos = e.getPos();
            boolean placed = PlacedBlocks.get(lvl).remove(pos);
            if (placed || !active() || lvl != KingdomManager.level()) return;
            if (e.getPlayer() == null || e.getPlayer().isCreative() || e.getPlayer().isSpectator()) return;
            Res r = classify(e.getState());
            if (r != null) add(r, 1);
        }

        /** Boss d'assaut vaincu : +3 Essence (priorité haute : avant que le boss soit retiré du registre). */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void treasuryBossDeath(LivingDeathEvent e) {
            if (!active() || e.getEntity().level().isClientSide) return;
            if (com.wavesurvivor.horde.boss.BossManager.isBoss(e.getEntity().getUUID())) {
                // Présage « Chasse royale » : +6 au lieu de +3
                reward(Res.ESSENCE, KingdomOmens.is(KingdomOmens.Omen.ROYAL_HUNT) ? 6 : 3, "kingdom.essence.boss");
            }
        }
    }
}
