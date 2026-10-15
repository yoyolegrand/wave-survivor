package com.wavesurvivor.horde.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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
 * Menu du BRASIER DES OFFRANDES : grille de 27 cases + inventaire du joueur.
 * Boutons (clickMenuButton) : 0 = Sacrifier, 1-3 = choix de la spécialisation (niveau 3).
 * Données synchronisées : 0 total · 1 jauge · 2 taille de jauge · 3 points du Calme · 4 plafond · 5 spécialisation.
 */
public class OfferingBrazierMenu extends AbstractContainerMenu {

    public static final int GRID_Y = 82, INV_Y = 154;
    private final Container storage;
    private final ContainerData data;
    private final BlockPos pos;

    /** Côté serveur. */
    public OfferingBrazierMenu(int id, Inventory inv, Container storage, ContainerData data, BlockPos pos) {
        super(com.wavesurvivor.registry.ModMenus.OFFERING_BRAZIER.get(), id);
        this.storage = storage;
        this.data = data;
        this.pos = pos;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(storage, c + r * 9, 8 + c * 18, GRID_Y + r * 18));
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    /** Côté client. */
    public static OfferingBrazierMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new OfferingBrazierMenu(id, inv, new SimpleContainer(OfferingBrazierBlockEntity.SLOTS), new SimpleContainerData(6), buf.readBlockPos());
    }

    public int total() { return data.get(0); }
    public int gauge() { return data.get(1); }
    public int perRoll() { return Math.max(1, data.get(2)); }
    public int calmUsed() { return data.get(3); }
    public int calmCap() { return data.get(4); }
    public int variantIndex() { return data.get(5); }
    public int level() { return OfferingBrazierBlockEntity.levelOf(total()); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || !(player.level().getBlockEntity(pos) instanceof OfferingBrazierBlockEntity be)) return false;
        if (id == 0) be.sacrifice(sp);
        else be.chooseVariant(sp, id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        int grid = OfferingBrazierBlockEntity.SLOTS;
        if (index < grid) {
            if (!moveItemStackTo(s, grid, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(s, 0, grid, false)) {
            return ItemStack.EMPTY;
        }
        if (s.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0) <= 64.0
                && player.level().getBlockState(pos).getBlock() instanceof OfferingBrazierBlock;
    }
}
