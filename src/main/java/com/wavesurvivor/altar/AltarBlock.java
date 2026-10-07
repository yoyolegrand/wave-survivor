package com.wavesurvivor.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Block "Autel Runique" — Monolithe de guerre (1 bloc) avec livre flottant.
 *
 *   Clic droit       : autel vierge → écran de liaison (recettes) ; autel lié → écran de lancement
 *   Shift + clic     : délier l'autel (main vide, confirmation par un 2e shift+clic)
 *
 * L'enum AltarType est prévu pour les futurs types d'altars.
 */
public class AltarBlock extends Block implements EntityBlock {

    public enum AltarType {
        HORDE_TRIGGER,   // Déclenche une horde à cet emplacement
        HORDE_TARGET     // (futur) Objectif à protéger pendant la horde
    }

    private final AltarType altarType;

    /** Couleur des runes / du tapis (colorant). Rouge par défaut. */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.world.item.DyeColor> COLOR =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("color", net.minecraft.world.item.DyeColor.class);

    public AltarBlock(Properties properties, AltarType type) {
        super(properties);
        this.altarType = type;
        this.registerDefaultState(this.stateDefinition.any().setValue(COLOR, net.minecraft.world.item.DyeColor.RED));
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
    }

    public AltarType getAltarType() {
        return altarType;
    }

    /** Conservé pour compatibilité (l'autel ne fait plus qu'un bloc). */
    public static BlockPos basePos(BlockState state, BlockPos pos) {
        return pos;
    }

    // ─── BlockEntity (livre flottant, animation client) ───

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AltarBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide()) return null;
        if (type != com.wavesurvivor.registry.ModBlockEntities.ALTAR_RUNIC.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<AltarBlockEntity>) AltarBlockEntity::bookAnimationTick;
    }

    // ─── Interaction ───

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;

        // Défense du Monolithe : réparation avec l'item configuré (poudre d'os par défaut)
        if (!sp.isShiftKeyDown() && AltarDefense.tryRepair(sp, sl, pos, sp.getItemInHand(hand))) {
            return InteractionResult.CONSUME;
        }
        // Défense en cours : clic droit = boutique d'améliorations (tout le monde, op ou non)
        if (!sp.isShiftKeyDown() && AltarDefense.tryOpenShop(sp, sl, pos)) {
            return InteractionResult.CONSUME;
        }

        if (sp.isShiftKeyDown()) {
            AltarManager.onShiftClick(sp, sl, pos);
        } else {
            AltarManager.onRightClick(sp, sl, pos, this.altarType);
        }
        return InteractionResult.CONSUME;
    }
}
