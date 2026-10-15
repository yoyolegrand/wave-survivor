package com.wavesurvivor.horde.kingdom;

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

import java.util.ArrayList;
import java.util.List;

/**
 * STOCKAGE D'UN GLANEUR (remplace le coffre vanilla) : rangées selon le niveau, filtre de tri, onglets vers les
 * Glaneurs proches (24 blocs). Boutons : 0-5 = cocher / décocher une catégorie ; 100+i = ouvrir l'onglet i.
 * Données synchronisées : 0 = filtre.
 */
public class GleanerMenu extends AbstractContainerMenu {

    public static final int TAB_RANGE = 24;
    public static final int GRID_Y = 38;
    private final BlockPos pos;
    private final int rows, defLevel;
    private final List<BlockPos> tabs;
    private final List<Integer> tabFilters;
    private final ContainerData data;

    public GleanerMenu(int id, Inventory inv, Container storage, BlockPos pos, int rows, int defLevel,
                       List<BlockPos> tabs, List<Integer> tabFilters, ContainerData data) {
        super(com.wavesurvivor.registry.ModMenus.GLEANER.get(), id);
        this.pos = pos;
        this.rows = rows;
        this.defLevel = defLevel;
        this.tabs = tabs;
        this.tabFilters = tabFilters;
        this.data = data;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(storage, c + r * 9, 8 + c * 18, GRID_Y + r * 18));
        int invY = invY();
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, invY + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, invY + 58));
        addDataSlots(data);
    }

    public int invY() { return GRID_Y + rows * 18 + 14; }
    public int rows() { return rows; }
    public int defLevel() { return defLevel; }
    public BlockPos pos() { return pos; }
    public List<BlockPos> tabs() { return tabs; }
    public int tabFilter(int i) { return i < tabFilters.size() ? tabFilters.get(i) : 0; }
    public int filter() { return data.get(0); }

    /** Ouvre le stockage du Glaneur à cette position (avec ses onglets). */
    public static void open(ServerPlayer sp, BlockPos pos) {
        if (!(sp.level().getBlockEntity(pos) instanceof DefenseBlockEntity be)) return;
        int lvl = Math.max(1, be.getDefLevel());
        int rows = KingdomDefenses.storageRows(lvl);
        List<BlockPos> tabs = KingdomDefenses.nearbyCollectors(pos, TAB_RANGE);
        List<Integer> filters = new ArrayList<>();
        for (BlockPos t : tabs) filters.add(sp.level().getBlockEntity(t) instanceof DefenseBlockEntity tb ? tb.getFilter() : 0);
        ContainerData d = new ContainerData() {
            @Override
            public int get(int i) { return i == 0 ? be.getFilter() : 0; }
            @Override
            public void set(int i, int v) {}
            @Override
            public int getCount() { return 1; }
        };
        Component title = Component.translatable("block.wavesurvivor.kingdom_collector")
                .append(Component.literal(com.wavesurvivor.i18n.WSLang.t("kingdom.collector_title", lvl, KingdomDefenses.collectRadius(lvl))));
        net.minecraftforge.network.NetworkHooks.openScreen(sp, new net.minecraft.world.SimpleMenuProvider(
                (id, inv, pl) -> new GleanerMenu(id, inv, be.getStorage(), pos, rows, lvl, tabs, filters, d), title), buf -> {
            buf.writeBlockPos(pos);
            buf.writeVarInt(rows);
            buf.writeVarInt(lvl);
            buf.writeVarInt(tabs.size());
            for (int i = 0; i < tabs.size(); i++) {
                buf.writeBlockPos(tabs.get(i));
                buf.writeVarInt(filters.get(i));
            }
        });
    }

    /** Côté client. */
    public static GleanerMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int rows = buf.readVarInt(), lvl = buf.readVarInt(), n = buf.readVarInt();
        List<BlockPos> tabs = new ArrayList<>();
        List<Integer> filters = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tabs.add(buf.readBlockPos());
            filters.add(buf.readVarInt());
        }
        return new GleanerMenu(id, inv, new SimpleContainer(rows * 9), pos, rows, lvl, tabs, filters, new SimpleContainerData(1));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || !(sp.level().getBlockEntity(pos) instanceof DefenseBlockEntity be)) return false;
        if (id >= 0 && id < GleanerSort.Category.values().length) {
            be.setFilter(be.getFilter() ^ (1 << id));
            return true;
        }
        if (id >= 100 && id - 100 < tabs.size()) {
            BlockPos target = tabs.get(id - 100);
            if (!target.equals(pos) && sp.level().getBlockEntity(target) instanceof DefenseBlockEntity) {
                sp.server.execute(() -> open(sp, target)); // après le traitement du clic : ouvre l'autre Glaneur
            }
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        int grid = rows * 9;
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
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 48 * 48
                && player.level().getBlockState(pos).getBlock() instanceof DefenseBlock;
    }
}
