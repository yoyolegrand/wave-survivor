package com.wavesurvivor.shrine;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModItems;
import com.wavesurvivor.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Menu du SANCTUAIRE DU SÉRAPHIN (4 onglets : 0 Offrandes · 1 Récolte · 2 Fusion · 3 Forge).
 * Emplacements : 0 = offrande · 1 = fusion (entrée) · 2 = fusion (sortie, prise seulement) · 3 = forge (équipement) · 4 = forge (fragment) · 5-31 = inventaire · 32-40 = barre d'objets.
 * Les emplacements d'un autre onglet sont désactivés (isActive).
 * Boutons (clickMenuButton) : 0-3 = changer d'onglet · 10 = Offrir · 11 = Récolter · 12 = Fusionner · 13 = Insérer · 14 = Retirer.
 * Données synchronisées : voir SeraphShrineBlockEntity.menuData.
 */
public class SeraphShrineMenu extends AbstractContainerMenu {

    public static final int TAB_OFFER = 0, TAB_HARVEST = 1, TAB_FUSION = 2, TAB_FORGE = 3;
    public static final int BTN_OFFER = 10, BTN_HARVEST = 11, BTN_FUSE = 12, BTN_INSERT = 13, BTN_REMOVE = 14;
    /** Positions (relatives à la fenêtre 176 × 234) : le panneau commence à y = 24, voir SeraphShrineScreen. */
    public static final int OFFER_X = 25, OFFER_Y = 65, FUSE_IN_X = 37, FUSE_OUT_X = 109, FUSE_Y = 65, FORGE_X = 15, EQUIP_Y = 53, FRAG_Y = 85;
    public static final int INV_X = 8, INV_Y = 149, HOT_Y = 207;
    private static final int FIRST_INV = 5, HOT_START = 32, END = 41;

    private final ContainerData data;
    private final BlockPos pos;
    private final Container offer = new SimpleContainer(1);
    private final Container fuseIn = new SimpleContainer(1);
    private final Container fuseOut = new SimpleContainer(1);
    private final Container forgeEquip = new SimpleContainer(1);
    private final Container forgeFrag = new SimpleContainer(1);
    private final Slot offerSlot, fuseInSlot, fuseOutSlot, equipSlot, fragSlot;
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
        fuseInSlot = addSlot(new Slot(fuseIn, 0, FUSE_IN_X, FUSE_Y) {
            @Override public boolean mayPlace(ItemStack st) { return nextLevel(st.getItem()) != null; }
            @Override public boolean isActive() { return tab == TAB_FUSION; }
        });
        fuseOutSlot = addSlot(new Slot(fuseOut, 0, FUSE_OUT_X, FUSE_Y) {
            @Override public boolean mayPlace(ItemStack st) { return false; }
            @Override public boolean isActive() { return tab == TAB_FUSION; }
        });
        equipSlot = addSlot(new Slot(forgeEquip, 0, FORGE_X, EQUIP_Y) {
            @Override public boolean mayPlace(ItemStack st) { return !ShrineGear.typesOf(st).isEmpty(); }
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean isActive() { return tab == TAB_FORGE; }
        });
        fragSlot = addSlot(new Slot(forgeFrag, 0, FORGE_X, FRAG_Y) {
            @Override public boolean mayPlace(ItemStack st) { return ShrineGear.campOf(st.getItem()) != null; }
            @Override public boolean isActive() { return tab == TAB_FORGE; }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, HOT_Y));
        addDataSlots(data);
    }

    // ─── Données synchronisées ───

    public int faith() { return data.get(0); }
    public int lastDelta() { return data.get(1); }
    public int stage() { return data.get(2); }
    public int maxFaith() { return Math.max(1, data.get(3)); }
    public int thrPure() { return data.get(4); }
    public int thrCalm() { return data.get(5); }
    public int thrSoiled() { return data.get(6); }
    public int thrCorrupted() { return data.get(7); }
    public int graceStock() { return data.get(8); }
    public int taintStock() { return data.get(9); }
    /** Progression de la production en cours, en secondes, et durée d'un cycle (0 = pas de production). */
    public int progressSeconds() { return data.get(10); }
    public int intervalSeconds() { return data.get(11); }
    public int stockCap() { return Math.max(1, data.get(12)); }
    public int fusionCost() { return Math.max(1, data.get(13)); }

    public ItemStack offered() { return offerSlot.getItem(); }
    public ItemStack fusionInput() { return fuseInSlot.getItem(); }
    public ItemStack fusionOutput() { return fuseOutSlot.getItem(); }
    public ItemStack forgeEquipment() { return equipSlot.getItem(); }
    public ItemStack forgeFragment() { return fragSlot.getItem(); }

    /** Niveau supérieur d'un fragment (Éclat → Fragment → Cœur), ou null (Cœur, autre objet). */
    public static Item nextLevel(Item it) {
        if (it == ModItems.GRACE_SHARD.get()) return ModItems.GRACE_FRAGMENT.get();
        if (it == ModItems.GRACE_FRAGMENT.get()) return ModItems.GRACE_HEART.get();
        if (it == ModItems.TAINT_SHARD.get()) return ModItems.TAINT_FRAGMENT.get();
        if (it == ModItems.TAINT_FRAGMENT.get()) return ModItems.TAINT_HEART.get();
        return null;
    }

    // ─── Boutons ───

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id <= 3) {
            tab = id;
            return true;
        }
        if (!(player instanceof ServerPlayer sp) || !(player.level().getBlockEntity(pos) instanceof SeraphShrineBlockEntity be)) return false;
        switch (id) {
            case BTN_OFFER -> offer(sp, be);
            case BTN_HARVEST -> be.harvest(sp);
            case BTN_FUSE -> fuse(sp);
            case BTN_INSERT -> insertUpgrade(sp);
            case BTN_REMOVE -> removeUpgrade(sp);
            default -> { return false; }
        }
        return true;
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

    /** Fusion : par lots de {@code fusionCost} objets du même niveau → 1 objet du niveau supérieur (autant de lots que possible). */
    private void fuse(ServerPlayer sp) {
        ItemStack in = fuseIn.getItem(0);
        Item next = nextLevel(in.getItem());
        if (in.isEmpty() || next == null) return;
        int cost = ShrineConfig.fusionCost();
        int n = in.getCount() / cost;
        if (n <= 0) {
            sp.displayClientMessage(Component.literal(WSLang.t("shrine.fuse_need", cost)), true);
            return;
        }
        ItemStack out = fuseOut.getItem(0);
        if (!out.isEmpty() && out.getItem() != next) {
            sp.displayClientMessage(Component.literal(WSLang.t("shrine.fuse_busy")), true);
            return;
        }
        int room = new ItemStack(next).getMaxStackSize() - out.getCount();
        n = Math.min(n, room);
        if (n <= 0) {
            sp.displayClientMessage(Component.literal(WSLang.t("shrine.fuse_busy")), true);
            return;
        }
        in.shrink(n * cost);
        if (out.isEmpty()) fuseOut.setItem(0, new ItemStack(next, n)); else out.grow(n);
        fuseIn.setChanged();
        fuseOut.setChanged();
        sp.level().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.0f);
        broadcastChanges();
    }

    // ─── Forge ───

    private void say(ServerPlayer sp, String key, Object... args) {
        sp.displayClientMessage(Component.literal(WSLang.t(key, args)), true);
    }

    /** Insère une amélioration tirée au sort dans l'équipement (coût croissant en fragments, un seul alignement par objet). */
    private void insertUpgrade(ServerPlayer sp) {
        ItemStack eq = forgeEquip.getItem(0), fr = forgeFrag.getItem(0);
        if (eq.isEmpty()) { say(sp, "shrine.forge_no_item"); return; }
        if (fr.isEmpty()) { say(sp, "shrine.forge_no_frag"); return; }
        java.util.Set<String> types = ShrineGear.typesOf(eq);
        String camp = ShrineGear.campOf(fr.getItem());
        int tier = ShrineGear.tierOf(fr.getItem());
        if (types.isEmpty() || camp == null || tier <= 0) return;
        String align = ShrineGear.alignOf(eq);
        if (align != null && !align.equals(camp)) { say(sp, "shrine.forge_align", WSLang.t("shrine.align." + align)); return; }
        java.util.List<ShrineGear.Applied> cur = ShrineGear.upgradesOf(eq);
        if (cur.size() >= ShrineUpgrades.maxSlots()) { say(sp, "shrine.forge_full"); return; }
        int cost = ShrineUpgrades.slotCost(cur.size());
        if (fr.getCount() < cost) { say(sp, "shrine.forge_need", cost); return; }
        java.util.List<String> owned = new java.util.ArrayList<>();
        for (ShrineGear.Applied a : cur) owned.add(a.id());
        ShrineUpgrades.Def d = ShrineGear.draw(types, camp, owned, sp.getRandom());
        if (d == null) { say(sp, "shrine.forge_none"); return; }
        fr.shrink(cost);
        ShrineGear.add(eq, camp, d.id(), tier);
        forgeEquip.setChanged();
        forgeFrag.setChanged();
        sp.level().playSound(null, pos, camp.equals(ShrineGear.CONSECRATED) ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS,
                camp.equals(ShrineGear.CONSECRATED) ? 1.2f : 0.35f, camp.equals(ShrineGear.CONSECRATED) ? 1.0f : 1.6f);
        say(sp, "shrine.forge_done", WSLang.t("shrine.up." + d.id()), ShrineGear.roman(tier));
        ShrineGear.refresh(sp);
        broadcastChanges();
    }

    /** Retire la dernière amélioration (coûte des fragments du même camp, ne les rend pas) : on peut ensuite en tirer une nouvelle. */
    private void removeUpgrade(ServerPlayer sp) {
        ItemStack eq = forgeEquip.getItem(0), fr = forgeFrag.getItem(0);
        if (eq.isEmpty() || ShrineGear.upgradesOf(eq).isEmpty()) { say(sp, "shrine.forge_nothing"); return; }
        String align = ShrineGear.alignOf(eq);
        int cost = ShrineUpgrades.removeCost();
        if (fr.isEmpty() || align == null || !align.equals(ShrineGear.campOf(fr.getItem())) || fr.getCount() < cost) {
            say(sp, "shrine.forge_remove_need", cost, WSLang.t("shrine.align." + align));
            return;
        }
        fr.shrink(cost);
        ShrineGear.removeLast(eq);
        forgeEquip.setChanged();
        forgeFrag.setChanged();
        sp.level().playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.8f, 1.0f);
        say(sp, "shrine.forge_removed");
        ShrineGear.refresh(sp);
        broadcastChanges();
    }

    // ─── Déplacements rapides ───

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        if (index < FIRST_INV) {
            if (!moveItemStackTo(s, FIRST_INV, END, true)) return ItemStack.EMPTY;
        } else if (tab == TAB_OFFER && offerSlot.mayPlace(s) && !offerSlot.hasItem()) {
            if (!moveItemStackTo(s, 0, 1, false)) return ItemStack.EMPTY;
        } else if (tab == TAB_FUSION && fuseInSlot.mayPlace(s)) {
            if (!moveItemStackTo(s, 1, 2, false)) return ItemStack.EMPTY;
        } else if (tab == TAB_FORGE && equipSlot.mayPlace(s) && !equipSlot.hasItem()) {
            if (!moveItemStackTo(s, 3, 4, false)) return ItemStack.EMPTY;
        } else if (tab == TAB_FORGE && fragSlot.mayPlace(s)) {
            if (!moveItemStackTo(s, 4, 5, false)) return ItemStack.EMPTY;
        } else if (index < HOT_START) {
            if (!moveItemStackTo(s, HOT_START, END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(s, FIRST_INV, HOT_START, false)) {
            return ItemStack.EMPTY;
        }
        if (s.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    /** À la fermeture, ce qui reste dans les emplacements du Sanctuaire est rendu au joueur. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            clearContainer(player, offer);
            clearContainer(player, fuseIn);
            clearContainer(player, fuseOut);
            clearContainer(player, forgeEquip);
            clearContainer(player, forgeFrag);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0
                && player.level().getBlockState(pos).getBlock() instanceof SeraphShrineBlock;
    }
}
