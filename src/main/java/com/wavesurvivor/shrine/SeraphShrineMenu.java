package com.wavesurvivor.shrine;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu du SANCTUAIRE DU SÉRAPHIN (4 onglets : 0 Offrandes · 1 Récolte · 2 Fusion · 3 Forge ; seule l'étape en cours de développement est active).
 * Emplacements : 0 = offrande, 1-27 = inventaire, 28-36 = barre d'objets. Les emplacements d'un autre onglet sont désactivés (isActive).
 * Boutons (clickMenuButton) : 0-3 = changer d'onglet, 10 = Offrir.
 * Données synchronisées : voir SeraphShrineBlockEntity.menuData.
 */
public class SeraphShrineMenu extends AbstractContainerMenu {

    public static final int TAB_OFFER = 0, TAB_HARVEST = 1, TAB_FUSION = 2, TAB_FORGE = 3;
    public static final int BTN_OFFER = 10;
    /** Positions (relatives à la fenêtre 176 × 234) : le panneau commence à y = 24, voir SeraphShrineScreen. */
    public static final int OFFER_X = 25, OFFER_Y = 65, INV_X = 8, INV_Y = 149, HOT_Y = 207;

    private final ContainerData data;
    private final BlockPos pos;
    private final Container offer = new SimpleContainer(1);
    private final Slot offerSlot;
    /** Onglet affiché (client et serveur : décide quels emplacements sont actifs). */
    public int tab = TAB_OFFER;

    /** Côté serveur. */
    public SeraphShrineMenu(int id, Inventory inv, SeraphShrineBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.menuData());
    }

    /** Côté client. */
    public static SeraphShrineMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new SeraphShrineMenu(id, inv, buf.readBlockPos(), new SimpleContainerData(SeraphShrineBlockEntity.DATA_SIZE));
    }

    private SeraphShrineMenu(int id, Inventory inv, BlockPos pos, ContainerData data) {
        super(ModMenus.SERAPH_SHRINE.get(), id);
        this.pos = pos;
        this.data = data;
        offerSlot = addSlot(new Slot(offer, 0, OFFER_X, OFFER_Y) {
            @Override public boolean mayPlace(ItemStack st) { return ShrineConfig.offerValue(st) != 0; }
            @Override public boolean isActive() { return tab == TAB_OFFER; }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, HOT_Y));
        addDataSlots(data);
    }

    public int faith() { return data.get(0); }
    public int lastDelta() { return data.get(1); }
    public int stage() { return data.get(2); }
    public int maxFaith() { return Math.max(1, data.get(3)); }
    public int thrPure() { return data.get(4); }
    public int thrCalm() { return data.get(5); }
    public int thrSoiled() { return data.get(6); }
    public int thrCorrupted() { return data.get(7); }

    /** Valeur (grâce + / souillure −) de l'objet actuellement posé dans l'emplacement d'offrande (calculée côté client avec la même config). */
    public ItemStack offered() { return offerSlot.getItem(); }

    // ─── Boutons ───

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id <= 3) {
            tab = id;
            return true;
        }
        if (id == BTN_OFFER && player instanceof ServerPlayer sp && player.level().getBlockEntity(pos) instanceof SeraphShrineBlockEntity be) {
            offer(sp, be);
            return true;
        }
        return false;
    }

    /** Offre ce qui est dans l'emplacement : la foi bouge d'autant (jamais au-delà des bornes : le reste est rendu). */
    private void offer(ServerPlayer sp, SeraphShrineBlockEntity be) {
        ItemStack st = offer.getItem(0);
        int v = ShrineConfig.offerValue(st);
        if (st.isEmpty() || v == 0) return;
        int max = ShrineConfig.maxFaith(), faith = be.getFaith();
        int room = v > 0 ? max - faith : faith + max;
        int units = Math.min(st.getCount(), (int) Math.ceil(room / (double) Math.abs(v)));
        if (units <= 0) {
            sp.displayClientMessage(Component.literal(WSLang.t(v > 0 ? "shrine.full_pure" : "shrine.full_corrupt")), true);
            return;
        }
        st.shrink(units);
        offer.setChanged();
        be.offerFeedback(v > 0);
        be.addFaith(v * units);
        broadcastChanges();
    }

    // ─── Déplacements rapides ───

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        if (index == 0) {
            if (!moveItemStackTo(s, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (tab == TAB_OFFER && offerSlot.mayPlace(s) && !offerSlot.hasItem()) {
            if (!moveItemStackTo(s, 0, 1, false)) return ItemStack.EMPTY;
        } else if (index < 28) {
            if (!moveItemStackTo(s, 28, 37, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(s, 1, 28, false)) {
            return ItemStack.EMPTY;
        }
        if (s.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    /** À la fermeture, ce qui reste dans l'emplacement d'offrande est rendu au joueur. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, offer);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0
                && player.level().getBlockState(pos).getBlock() instanceof SeraphShrineBlock;
    }
}
