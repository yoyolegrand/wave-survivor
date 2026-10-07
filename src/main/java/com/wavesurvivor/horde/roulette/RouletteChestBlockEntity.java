package com.wavesurvivor.horde.roulette;

import com.wavesurvivor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity du Coffre Roulette. Conteneur de 0 case (jamais ouvert) : il n'existe que pour porter
 * le NOM du coffre (CustomName), qui identifie sa config (RouletteChestRegistry.getByChestName).
 * Le nom est synchronisé au client → la teinte des runes / de la gemme suit le type de coffre.
 */
public class RouletteChestBlockEntity extends BaseContainerBlockEntity {

    public RouletteChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROULETTE_CHEST.get(), pos, state);
    }

    /** Couleur des runes / de la gemme (RGB), -1 = déduite du nom côté client. */
    private int color = -1;

    public int getColor() { return color; }

    /** Appelé par le serveur (config du coffre) ; synchronisé au client seulement si elle change. */
    public void setColor(int rgb) {
        if (rgb == color) return;
        color = rgb;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("RouletteColor", color);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        color = tag.contains("RouletteColor") ? tag.getInt("RouletteColor") : -1;
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ─── Synchro client (teinte) ───

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        rerender();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        rerender();
    }

    private void rerender() {
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        }
    }

    // ─── Conteneur vide (0 case) ───

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.wavesurvivor.roulette_chest");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return null; // jamais ouvert : le clic droit est géré par RouletteChestEventHandler
    }

    @Override public int getContainerSize() { return 0; }
    @Override public boolean isEmpty() { return true; }
    @Override public ItemStack getItem(int slot) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }
    @Override public void setItem(int slot, ItemStack stack) {}
    @Override public boolean stillValid(Player player) { return true; }
    @Override public void clearContent() {}
}
