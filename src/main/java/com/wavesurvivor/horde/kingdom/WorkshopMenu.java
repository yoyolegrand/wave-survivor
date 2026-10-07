package com.wavesurvivor.horde.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu de l'ATELIER DE RÉPARATION : 2 / 4 / 6 emplacements selon le niveau (cases 0 à 5 du stockage de la défense).
 *  - n'accepte que les objets à durabilité (armures, armes, outils, arcs, boucliers…), un par case ;
 *  - l'objet est marqué au nom de celui qui le dépose : lui seul peut le reprendre (créatif exempté) ;
 *  - le marquage est retiré quand l'objet ressort.
 */
public class WorkshopMenu extends AbstractContainerMenu {

    public static final String OWNER = "ws_workshop_owner";
    public static final String OWNER_NAME = "ws_workshop_owner_name";
    public static final int MAX = 6;

    private final Container storage;
    private final BlockPos pos;
    private final int nSlots;
    private final Player player;
    public final int defLevel;
    public final String variant;

    /** Côté client (ouverture réseau) : un conteneur local, synchronisé par le menu. */
    public static WorkshopMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int slots = buf.readVarInt();
        int lvl = buf.readVarInt();
        String variant = buf.readUtf(32);
        return new WorkshopMenu(id, inv, new SimpleContainer(54), pos, slots, lvl, variant);
    }

    public WorkshopMenu(int id, Inventory inv, Container storage, BlockPos pos, int slots, int defLevel, String variant) {
        super(com.wavesurvivor.registry.ModMenus.WORKSHOP.get(), id);
        this.storage = storage;
        this.pos = pos;
        this.nSlots = Math.max(1, Math.min(MAX, slots));
        this.player = inv.player;
        this.defLevel = defLevel;
        this.variant = variant == null ? "" : variant;
        // Emplacements de l'atelier, centrés sur une ligne
        int x0 = 88 - this.nSlots * 9;
        for (int i = 0; i < this.nSlots; i++) addSlot(new RepairSlot(storage, i, x0 + i * 18, 36));
        // Inventaire du joueur
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 142));
    }

    public int workshopSlots() { return nSlots; }

    // ─── Propriétaire ───

    static void markOwner(ItemStack st, Player p) {
        if (st.isEmpty() || p == null) return;
        CompoundTag t = st.getOrCreateTag();
        if (!t.hasUUID(OWNER)) {
            t.putUUID(OWNER, p.getUUID());
            t.putString(OWNER_NAME, p.getGameProfile().getName());
        }
    }

    static void clearOwner(ItemStack st) {
        if (st.isEmpty() || !st.hasTag()) return;
        CompoundTag t = st.getTag();
        t.remove(OWNER);
        t.remove(OWNER_NAME);
        if (t.isEmpty()) st.setTag(null);
    }

    static boolean canTake(ItemStack st, Player p) {
        if (st.isEmpty() || p == null || p.getAbilities().instabuild) return true;
        return !st.hasTag() || !st.getTag().hasUUID(OWNER) || st.getTag().getUUID(OWNER).equals(p.getUUID());
    }

    /** Case de l'atelier : objets à durabilité uniquement, un seul, réservés à leur propriétaire. */
    private class RepairSlot extends Slot {
        RepairSlot(Container c, int idx, int x, int y) { super(c, idx, x, y); }

        @Override public boolean mayPlace(ItemStack st) { return st.isDamageableItem(); }

        @Override public int getMaxStackSize() { return 1; }

        @Override public boolean mayPickup(Player p) { return canTake(getItem(), p); }

        @Override
        public void set(ItemStack st) {
            if (!player.level().isClientSide) markOwner(st, player);
            super.set(st);
        }

        @Override
        public void onTake(Player p, ItemStack st) {
            clearOwner(st);
            super.onTake(p, st);
        }
    }

    // ─── Maj + clic ───

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot s = this.slots.get(index);
        if (s == null || !s.hasItem()) return ItemStack.EMPTY;
        ItemStack st = s.getItem();
        ItemStack copy = st.copy();
        if (index < nSlots) {
            // Atelier -> inventaire (seulement son propre objet)
            if (!canTake(st, p)) return ItemStack.EMPTY;
            ItemStack out = st.copy();
            clearOwner(out);
            if (!moveItemStackTo(out, nSlots, this.slots.size(), true)) return ItemStack.EMPTY;
            s.set(ItemStack.EMPTY);
            return copy;
        }
        // Inventaire -> atelier : un objet à durabilité dans la première case libre
        if (!st.isDamageableItem()) return ItemStack.EMPTY;
        for (int i = 0; i < nSlots; i++) {
            Slot w = this.slots.get(i);
            if (!w.hasItem()) {
                w.set(st.split(1));
                s.setChanged();
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        if (p.level().isClientSide) return true;
        return p.level().getBlockState(pos).getBlock() instanceof DefenseBlock
                && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
