package com.wavesurvivor.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * ÉQUIPEMENT DE RELIQUES SANS CURIOS (1.5) : seules les reliques ÉQUIPÉES agissent. Sans Curios, elles s'équipent dans
 * l'onglet « Équipement » de l'autel de Renaissance : 3 emplacements (+1 par rang de Renaissance, 6 max).
 * Stockage : données persistantes du joueur (recopiées à la mort), synchronisées au client pour les infobulles.
 */
public class RelicEquip {

    private static final String TAG = "ws_relic_equip";
    public static final int BASE_SLOTS = 3;
    public static final int MAX_SLOTS = 6;

    /** Côté client : reliques équipées du joueur local (reçues du serveur). */
    public static List<ItemStack> CLIENT = new ArrayList<>();

    /** Nombre d'emplacements du joueur (le rang de Renaissance s'y ajoutera à l'étape 5). */
    public static int slots(Player p) {
        return Math.min(MAX_SLOTS, BASE_SLOTS + rank(p));
    }

    /** Rang de Renaissance (étape 5). */
    public static int rank(Player p) {
        return com.wavesurvivor.horde.renaissance.Heritage.rank(p);
    }

    public static List<ItemStack> list(Player p) {
        if (p == null) return List.of();
        if (p.level().isClientSide) return CLIENT;
        List<ItemStack> out = new ArrayList<>();
        ListTag l = p.getPersistentData().getList(TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            ItemStack st = ItemStack.of(l.getCompound(i));
            if (!st.isEmpty()) out.add(st);
        }
        return out;
    }

    private static void save(Player p, List<ItemStack> stacks) {
        ListTag l = new ListTag();
        for (ItemStack st : stacks) l.add(st.save(new CompoundTag()));
        p.getPersistentData().put(TAG, l);
    }

    public static boolean has(Player p, Item item) {
        for (ItemStack st : list(p)) if (st.is(item)) return true;
        return false;
    }

    /** Équipe la relique de cette case de l'inventaire principal. */
    public static void equip(ServerPlayer p, int invSlot) {
        if (invSlot < 0 || invSlot >= p.getInventory().items.size()) return;
        ItemStack st = p.getInventory().items.get(invSlot);
        if (st.isEmpty() || !(st.getItem() instanceof RelicItem)) return;
        List<ItemStack> eq = new ArrayList<>(list(p));
        if (eq.size() >= slots(p)) {
            p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("equip.full", slots(p))));
            return;
        }
        for (ItemStack e : eq) {
            if (e.is(st.getItem())) {
                p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("equip.duplicate")));
                return;
            }
        }
        eq.add(st.split(1));
        save(p, eq);
        p.getInventory().setChanged();
        sync(p);
    }

    /** Retire la relique équipée n° {@code idx} (rendue dans l'inventaire). */
    public static void unequip(ServerPlayer p, int idx) {
        List<ItemStack> eq = new ArrayList<>(list(p));
        if (idx < 0 || idx >= eq.size()) return;
        ItemStack st = eq.remove(idx);
        save(p, eq);
        if (!p.getInventory().add(st)) p.drop(st, false);
        sync(p);
    }

    /** Remplace la relique équipée (amélioration à la Forge d'une relique équipée). */
    public static void replace(ServerPlayer p, int idx, ItemStack st) {
        List<ItemStack> eq = new ArrayList<>(list(p));
        if (idx < 0 || idx >= eq.size()) return;
        eq.set(idx, st);
        save(p, eq);
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        if (com.wavesurvivor.network.NetworkHandler.CHANNEL == null) return;
        com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.wavesurvivor.network.RelicEquipSyncPacket(list(p)));
    }

    // ─── Événements : conservé à la mort, synchronisé à la connexion ───

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData();
        if (old.contains(TAG)) e.getEntity().getPersistentData().put(TAG, old.getList(TAG, Tag.TAG_COMPOUND).copy());
        if (old.contains("ws_renaissance_rank")) e.getEntity().getPersistentData().putInt("ws_renaissance_rank", old.getInt("ws_renaissance_rank"));
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sync(sp);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sync(sp);
    }
}
