package com.wavesurvivor.horde.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * MODE KINGDOM — étape 4a : bloc de DÉFENSE (tour d'archers, tour de mage, sanctuaire de réparation).
 * Posable uniquement dans le claim pendant une partie Kingdom (vérifié par KingdomClaim).
 * Clic droit avec la monnaie de la horde en main → amélioration (niveaux 1 à 3).
 * Le comportement (tirs, aura, soins) est géré par KingdomDefenses.
 */
public class DefenseBlock extends Block implements net.minecraft.world.level.block.EntityBlock {

    public enum Kind { ARCHER, MAGE, SHRINE, BARRACKS, COLLECTOR, WORKSHOP }

    private final Kind kind;

    public DefenseBlock(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    /** Données + rendu 3D de la défense (DefenseBlockEntity / DefenseRenderer). */
    @Nullable
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DefenseBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl) {
            // Fondations pleines et accès à pied obligatoires (pas de tour sur le vide ou sur un pilier)
            String why = KingdomDefenses.foundationProblem(sl, kind, pos, 2);
            if (why != null) {
                sl.removeBlock(pos, false);
                if (placer instanceof ServerPlayer sp) {
                    sp.displayClientMessage(com.wavesurvivor.i18n.WSLang.c(why), true);
                    if (!sp.isCreative()) {
                        ItemStack back = stack.copy();
                        back.setCount(1);
                        if (!sp.getInventory().add(back)) sp.drop(back, false);
                    }
                }
                return;
            }
            KingdomDefenses.register(sl, pos, kind, placer);
        }
    }

    /** Démontage au pic : 3 s avec une pioche, comme le reste de la tour. */
    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return ColliderBlock.pickaxeProgress(player, super.getDestroyProgress(state, player, level, pos));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            // Tour du Glaneur : son stockage tombe au sol (rien n'est perdu)
            if (level.getBlockEntity(pos) instanceof DefenseBlockEntity be) {
                net.minecraft.world.Containers.dropContents(level, pos, be.getStorage());
            }
            KingdomDefenses.unregister(level, pos, kind);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown()) KingdomDefenses.repair(sp, pos);   // Maj + clic droit : réparer (raccourci)
            else KingdomDefenses.sendSheet(sp, pos);                    // clic droit : fiche de la défense
        }
        return InteractionResult.CONSUME;
    }

    /** Fiche › « Ouvrir » : emplacements de l'Atelier de Réparation ou stockage de la Tour du Glaneur. */
    public static void openStorage(ServerPlayer sp, BlockPos pos) {
        Level level = sp.level();
        if (!(level.getBlockState(pos).getBlock() instanceof DefenseBlock db) || !(level.getBlockEntity(pos) instanceof DefenseBlockEntity be)) return;
        if (db.kind == Kind.WORKSHOP) {
            DefenseBlockEntity wbe = be;
            {
                // Atelier de Réparation : clic droit (sans monnaie en main) = ouvrir ses emplacements
                int lvl = Math.max(1, wbe.getDefLevel());
                int n = KingdomWorkshop.slots(lvl);
                String variant = wbe.getVariant() == null ? "" : wbe.getVariant();
                net.minecraftforge.network.NetworkHooks.openScreen(sp, new net.minecraft.world.SimpleMenuProvider(
                        (id, inv, pl) -> new WorkshopMenu(id, inv, wbe.getStorage(), pos, n, lvl, variant),
                        net.minecraft.network.chat.Component.translatable("block.wavesurvivor.kingdom_workshop")), buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeVarInt(n);
                    buf.writeVarInt(lvl);
                    buf.writeUtf(variant, 32);
                });
            }
            return;
        }
        if (db.kind == Kind.COLLECTOR) {
            {
                // Tour du Glaneur : clic droit (sans monnaie en main) = ouvrir son stockage ; le titre rappelle niveau, rayon et amélioration
                int lvl = be.getDefLevel();
                net.minecraft.network.chat.Component title = net.minecraft.network.chat.Component.translatable("block.wavesurvivor.kingdom_collector")
                        .append(net.minecraft.network.chat.Component.literal(com.wavesurvivor.i18n.WSLang.t("kingdom.collector_title", lvl, KingdomDefenses.collectRadius(lvl))));
                int rows = KingdomDefenses.storageRows(lvl);
                sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> rows >= 6
                        ? net.minecraft.world.inventory.ChestMenu.sixRows(id, inv, be.getStorage())
                        : rows == 4
                        ? new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x4, id, inv, be.getStorage(), 4)
                        : net.minecraft.world.inventory.ChestMenu.threeRows(id, inv, be.getStorage()), title));
                sp.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("kingdom.collector_hint"), true);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(com.wavesurvivor.i18n.WSLang.c("kingdom.defense.desc." + kind.name().toLowerCase()));
        tooltip.add(com.wavesurvivor.i18n.WSLang.c("kingdom.defense.hint"));
    }
}
