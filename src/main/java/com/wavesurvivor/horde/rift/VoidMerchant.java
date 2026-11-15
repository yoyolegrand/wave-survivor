package com.wavesurvivor.horde.rift;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.kingdom.KingdomManager;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.EventFeedPacket;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.VoidMerchantPackets;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 2.1 — LE MARCHAND DU NÉANT (fiche docs/faille/v2_1_ilots/FICHE_2_1_0.md § 3 ; sauvegarde § A ; après le Siège § C).
 * <ul>
 *   <li>Arrive sur son îlot au 2e Calme du Siège (réglable) ; clic droit = boutique payée en éclats d'améthyste.</li>
 *   <li>Stock renouvelé à chaque Calme ; consommables limités par joueur et par Calme (4 de chaque par défaut, 0 = illimité).</li>
 *   <li>Pendant les Assauts : bouclier, boutique fermée. Ouvert pendant la Contre-offensive, fermé pendant le combat du Titan.</li>
 * </ul>
 * Stock d'un article (réglage « merchantStock ») : 0 = illimité (limite par joueur), n &gt; 0 = n par Calme (partagé), −n = n par partie.
 */
public final class VoidMerchant {

    private VoidMerchant() {}

    public static final int N = 12;
    static final String TAG = "ws_void_merchant";
    private static final int[] DEF_PRICE = {3, 4, 5, 5, 4, 3, 6, 4, 24, 30, 12, 120};
    private static final int[] DEF_STOCK = {0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 1, -1};

    private static boolean arrived;
    private static int calms;                 // Calmes du Siège écoulés
    private static BlockPos spot;             // pied du marchand, derrière son comptoir
    private static float yaw;
    private static UUID entity;
    private static final int[] stock = new int[N];      // restant ce Calme (−1 = illimité)
    private static final int[] runBought = new int[N];  // achats de la partie (articles « par partie »)
    private static final Map<UUID, int[]> personal = new HashMap<>();

    private static HordeConfigMultiData.RiftSettings rs() { return RiftManager.settingsPublic(); }

    static int price(int i) {
        List<Integer> l = rs().merchantPrices;
        int v = l != null && i < l.size() && l.get(i) != null ? l.get(i) : DEF_PRICE[i];
        return Math.max(0, Math.min(999, v));
    }

    static int stockSetting(int i) {
        List<Integer> l = rs().merchantStock;
        return l != null && i < l.size() && l.get(i) != null ? l.get(i) : DEF_STOCK[i];
    }

    /** L'objet vendu (Clé : celle du Coffre du Titan ; Poussière de Faille par 4). */
    static ItemStack stack(int i) {
        return switch (i) {
            case 0 -> new ItemStack(ModItems.FIOLE_NEANT.get());
            case 1 -> new ItemStack(Items.GOLDEN_APPLE);
            case 2 -> new ItemStack(ModItems.ELIXIR_RESISTANCE.get());
            case 3 -> new ItemStack(ModItems.ELIXIR_FORCE.get());
            case 4 -> new ItemStack(ModItems.PIERRE_RAPPEL.get());
            case 5 -> new ItemStack(ModItems.KIT_PONT.get());
            case 6 -> new ItemStack(ModItems.KIT_ENGIN.get());
            case 7 -> new ItemStack(ModItems.LANTERNE_VIGIE.get());
            case 8 -> TitanChest.key();
            case 9 -> new ItemStack(ModItems.POUSSIERE_FAILLE.get(), 4);
            case 10 -> new ItemStack(ModItems.RELANCE_BENEDICTION.get());
            default -> new ItemStack(ModItems.RIFT_SHARD.get());
        };
    }

    // ─── Cycle ───

    /** L'îlot est posé : place réservée au marchand (il n'arrive qu'au Calme prévu). */
    static void place(BlockPos at, float facing) {
        spot = at;
        yaw = facing;
    }

    /** Début d'un Calme du Siège : arrivée au Calme prévu, puis stock renouvelé. */
    static void onSiegeCalm(ServerLevel l) {
        if (spot == null) return;
        calms++;
        if (!arrived && calms >= Math.max(1, rs().merchantCalm)) {
            arrived = true;
            spawn(l, true);
            EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.merchant.arrive")), "minecraft:amethyst_cluster", EventFeedPacket.RESOURCE, true);
        }
        if (arrived) restock();
    }

    private static void restock() {
        for (int i = 0; i < N; i++) {
            int s = stockSetting(i);
            stock[i] = s > 0 ? s : -1;
        }
        personal.clear();
    }

    /** Boutique ouverte : Calme du Siège ou Contre-offensive. */
    static boolean open() {
        if (!arrived) return false;
        RiftManager.Phase ph = RiftManager.phasePublic();
        if (ph == RiftManager.Phase.SIEGE) return !KingdomManager.isActive() || KingdomManager.phase() == KingdomManager.Phase.CALM;
        return ph == RiftManager.Phase.COUNTER;
    }

    private static void spawn(ServerLevel l, boolean fx) {
        if (spot == null) return;
        if (entity != null && l.getEntity(entity) instanceof WanderingTrader t && t.isAlive()) return;
        WanderingTrader t = EntityType.WANDERING_TRADER.create(l);
        if (t == null) return;
        t.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0f);
        t.setYHeadRot(yaw);
        t.setYBodyRot(yaw);
        t.setNoAi(true);
        t.setInvulnerable(true);
        t.setPersistenceRequired();
        t.setDespawnDelay(0);
        t.setCustomName(Component.literal("§d" + WSLang.t("rift.merchant.name")));
        t.setCustomNameVisible(true);
        t.addTag(TAG);
        if (!l.addFreshEntity(t)) return;
        entity = t.getUUID();
        if (fx) {
            l.sendParticles(ParticleTypes.REVERSE_PORTAL, t.getX(), t.getY() + 1, t.getZ(), 80, 0.5, 1, 0.5, 0.2);
            l.playSound(null, spot, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 3f, 0.7f);
        }
    }

    /** Toutes les secondes : le marchand est toujours là ; bouclier visible quand la boutique est fermée. */
    static void tick(ServerLevel l, long now) {
        if (!arrived || spot == null || now % 20 != 0) return;
        spawn(l, false);
        if (!open()) {
            for (int k = 0; k < 24; k++) {
                double a = Math.random() * Math.PI * 2, b = Math.acos(2 * Math.random() - 1);
                l.sendParticles(ParticleTypes.ENCHANT, spot.getX() + 0.5 + 1.8 * Math.sin(b) * Math.cos(a), spot.getY() + 1 + 1.8 * Math.cos(b),
                        spot.getZ() + 0.5 + 1.8 * Math.sin(b) * Math.sin(a), 1, 0, 0, 0, 0);
            }
        }
    }

    // ─── Boutique ───

    private static int amethyst(ServerPlayer p) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (st.is(Items.AMETHYST_SHARD)) n += st.getCount();
        return n;
    }

    private static void pay(ServerPlayer p, int n) {
        for (ItemStack st : p.getInventory().items) {
            if (n <= 0) break;
            if (!st.is(Items.AMETHYST_SHARD)) continue;
            int take = Math.min(n, st.getCount());
            st.shrink(take);
            n -= take;
        }
    }

    /** Restant pour ce joueur (−1 = illimité). */
    static int left(ServerPlayer p, int i) {
        int s = stockSetting(i);
        if (s < 0) return Math.max(0, -s - runBought[i]);
        if (s > 0) return Math.max(0, stock[i]);
        int lim = Math.max(0, rs().merchantLimit);
        if (lim == 0) return -1;
        int[] mine = personal.get(p.getUUID());
        return Math.max(0, lim - (mine == null ? 0 : mine[i]));
    }

    static void openFor(ServerPlayer p) {
        List<VoidMerchantPackets.Entry> list = new ArrayList<>();
        for (int i = 0; i < N; i++) list.add(new VoidMerchantPackets.Entry(stack(i), price(i), left(p, i)));
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new VoidMerchantPackets.Open(list, amethyst(p)));
    }

    /** Achat (paquet du client). */
    public static void buy(ServerPlayer p, int i) {
        if (i < 0 || i >= N || !RiftManager.isActive() || p.level().dimension() != RiftManager.RIFT_DIM) return;
        if (!open()) { p.displayClientMessage(Component.literal(WSLang.t("rift.merchant.closed")), true); return; }
        if (spot == null || p.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 12 * 12) return;
        int left = left(p, i);
        if (left == 0) { p.displayClientMessage(Component.literal(WSLang.t("rift.merchant.sold_out")), true); openFor(p); return; }
        int cost = price(i);
        if (amethyst(p) < cost) { p.displayClientMessage(Component.literal(WSLang.t("rift.merchant.need", cost)), true); return; }
        ItemStack st = stack(i);
        if (st.isEmpty()) return;
        pay(p, cost);
        int s = stockSetting(i);
        if (s < 0) runBought[i]++;
        else if (s > 0) stock[i] = Math.max(0, stock[i] - 1);
        else personal.computeIfAbsent(p.getUUID(), k -> new int[N])[i]++;
        if (!p.getInventory().add(st)) p.drop(st, false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
        openFor(p);
    }

    // ─── Sauvegarde (§ A) ───

    static CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("arrived", arrived);
        t.putInt("calms", calms);
        t.putIntArray("stock", stock.clone());
        t.putIntArray("run", runBought.clone());
        CompoundTag per = new CompoundTag();
        personal.forEach((id, v) -> per.putIntArray(id.toString(), v.clone()));
        t.put("personal", per);
        return t;
    }

    static void restore(ServerLevel l, CompoundTag t) {
        arrived = t.getBoolean("arrived");
        calms = t.getInt("calms");
        int[] s = t.getIntArray("stock"), r = t.getIntArray("run");
        for (int i = 0; i < N; i++) {
            stock[i] = i < s.length ? s[i] : -1;
            runBought[i] = i < r.length ? r[i] : 0;
        }
        personal.clear();
        CompoundTag per = t.getCompound("personal");
        for (String k : per.getAllKeys()) {
            try {
                int[] v = per.getIntArray(k), w = new int[N];
                System.arraycopy(v, 0, w, 0, Math.min(N, v.length));
                personal.put(UUID.fromString(k), w);
            } catch (IllegalArgumentException ignored) {}
        }
        if (arrived) spawn(l, false);
    }

    static void clear(ServerLevel l) {
        if (l != null && entity != null) {
            Entity e = l.getEntity(entity);
            if (e != null) e.discard();
        }
        arrived = false;
        calms = 0;
        spot = null;
        entity = null;
        java.util.Arrays.fill(stock, -1);
        java.util.Arrays.fill(runBought, 0);
        personal.clear();
    }

    static String status() {
        if (spot == null) return "";
        return arrived ? (open() ? " §a☾ " : " §c☾ ") + WSLang.t(open() ? "rift.merchant.st_open" : "rift.merchant.st_closed")
                : " §8☾ " + WSLang.t("rift.merchant.st_waiting", Math.max(1, rs().merchantCalm), calms);
    }

    /** Test : fait arriver le marchand tout de suite. */
    static boolean debugArrive(ServerLevel l) {
        if (spot == null || arrived) return false;
        arrived = true;
        spawn(l, true);
        restock();
        return true;
    }

    // ─── Événements ───

    @Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        private Events() {}

        /** Clic droit sur le marchand : sa boutique (jamais l'écran d'échange vanilla). */
        @SubscribeEvent
        public static void onInteract(PlayerInteractEvent.EntityInteract e) {
            if (!e.getTarget().getTags().contains(TAG)) return;
            handle(e.getEntity(), e.getHand());
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }

        @SubscribeEvent
        public static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific e) {
            if (!e.getTarget().getTags().contains(TAG)) return;
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }

        private static void handle(net.minecraft.world.entity.player.Player pl, InteractionHand hand) {
            if (!(pl instanceof ServerPlayer p) || hand != InteractionHand.MAIN_HAND) return;
            if (!open()) {
                boolean titan = RiftManager.phasePublic() == RiftManager.Phase.TITAN;
                p.displayClientMessage(Component.literal(WSLang.t(titan ? "rift.merchant.closed_titan" : "rift.merchant.closed")), true);
                return;
            }
            openFor(p);
        }

        /** Kit d'engin : clic droit sur un engin de siège → +50 % de ses PV. */
        @SubscribeEvent
        public static void onEngineKit(PlayerInteractEvent.EntityInteract e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof com.wavesurvivor.entity.SiegeEngineEntity eng)) return;
            ItemStack st = p.getItemInHand(e.getHand());
            if (!st.is(ModItems.KIT_ENGIN.get())) return;
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            if (eng.maxHp() <= 0 || eng.hp() >= eng.maxHp()) { p.displayClientMessage(Component.literal(WSLang.t("item.kit_engin.full")), true); return; }
            eng.setHpRatio(Math.min(1f, eng.hp() / eng.maxHp() + 0.5f));
            if (!p.isCreative()) st.shrink(1);
            p.level().playSound(null, eng.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8f, 1.3f);
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, eng.getX(), eng.getY() + 1.5, eng.getZ(), 15, 0.8, 0.8, 0.8, 0.05);
            p.displayClientMessage(Component.literal(WSLang.t("item.kit_engin.used")), true);
        }
    }
}
