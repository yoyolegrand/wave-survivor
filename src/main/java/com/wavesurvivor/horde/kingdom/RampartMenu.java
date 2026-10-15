package com.wavesurvivor.horde.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * FICHE D'UN REMPART (clic droit) : PV, état (renforcé ou non), coût de l'amélioration et solde du trésor.
 * Bouton (clickMenuButton) : 0 = ▲ Renforcer. Aucun emplacement d'objet.
 * Données synchronisées : 0 PV · 1 PV max · 2 renforcé (0/1) · 3 coût · 4 monnaie du trésor · 5 créatif (0/1).
 */
public class RampartMenu extends AbstractContainerMenu {

    private final BlockPos base;
    private final ContainerData data;

    public RampartMenu(int id, Inventory inv, BlockPos base, ContainerData data) {
        super(com.wavesurvivor.registry.ModMenus.RAMPART.get(), id);
        this.base = base;
        this.data = data;
        addDataSlots(data);
    }

    /** Côté client. */
    public static RampartMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new RampartMenu(id, inv, buf.readBlockPos(), new SimpleContainerData(6));
    }

    /** Valeurs lues en direct côté serveur (rafraîchies tant que la fiche est ouverte). */
    static ContainerData serverData(ServerPlayer sp, BlockPos base) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                float[] hp = KingdomClaim.hpOf(base);
                BlockState st = sp.level().getBlockState(base);
                return switch (i) {
                    case 0 -> hp == null ? 0 : Math.round(hp[0]);
                    case 1 -> hp == null ? 0 : Math.round(hp[1]);
                    case 2 -> st.getBlock() instanceof RampartBlock && st.getValue(RampartBlock.REINFORCED) ? 1 : 0;
                    case 3 -> RampartBlock.UPGRADE_COST;
                    case 4 -> KingdomTreasury.active() ? KingdomTreasury.get(KingdomTreasury.Res.MONEY) : 0;
                    case 5 -> sp.isCreative() ? 1 : 0;
                    default -> 0;
                };
            }
            @Override
            public void set(int i, int v) {}
            @Override
            public int getCount() { return 6; }
        };
    }

    public int hp() { return data.get(0); }
    public int maxHp() { return data.get(1); }
    public boolean reinforced() { return data.get(2) == 1; }
    public int cost() { return data.get(3); }
    public int money() { return data.get(4); }
    public boolean creative() { return data.get(5) == 1; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && player instanceof ServerPlayer sp) RampartBlock.reinforce(sp, base);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5) <= 100.0
                && player.level().getBlockState(base).getBlock() instanceof RampartBlock;
    }
}
