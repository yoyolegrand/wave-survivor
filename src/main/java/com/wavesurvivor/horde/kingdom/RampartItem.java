package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Pose un segment de rempart complet (3 blocs de haut) d'un seul clic ; il compte comme UN bâtiment (PV partagés). */
public class RampartItem extends BlockItem {

    public RampartItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        BlockState clickedState = level.getBlockState(clicked);
        BlockPos base;
        if (clickedState.getBlock() instanceof RampartBlock) {
            // Contre un rempart : le nouveau segment se pose à côté, À LA MÊME HAUTEUR (aligné sur sa base) ; jamais empilé
            if (ctx.getClickedFace().getAxis().isVertical()) {
                if (!level.isClientSide && ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(WSLang.c("kingdom.rampart.no_stack"), true);
                return InteractionResult.FAIL;
            }
            base = RampartBlock.baseOf(clicked, clickedState).relative(ctx.getClickedFace());
        } else {
            base = clickedState.canBeReplaced() ? clicked : clicked.relative(ctx.getClickedFace());
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        // Les 3 emplacements doivent être libres (air, herbe haute…) et dans le monde
        for (int i = 0; i < 3; i++) {
            BlockPos q = base.above(i);
            if (!level.isInWorldBounds(q) || !level.getBlockState(q).canBeReplaced()) {
                if (player != null) player.displayClientMessage(WSLang.c("kingdom.rampart.no_room"), true);
                return InteractionResult.FAIL;
            }
        }
        // Pas de créature (ni joueur) prise dans la muraille
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(base).expandTowards(0, 2, 0)).isEmpty()) {
            if (player != null) player.displayClientMessage(WSLang.c("kingdom.rampart.no_room"), true);
            return InteractionResult.FAIL;
        }
        // Règles du claim (construction réservée à la zone, si activé)
        if (player instanceof ServerPlayer sp && !KingdomClaim.canBuild(sp, base)) return InteractionResult.FAIL;

        Block b = getBlock();
        // Orientation : la muraille court de gauche à droite face au joueur ; collée à un rempart existant, elle s'aligne
        // sur lui pour former une muraille continue
        net.minecraft.core.Direction.Axis axis = ctx.getHorizontalDirection().getAxis() == net.minecraft.core.Direction.Axis.Z
                ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z;
        boolean alongX = level.getBlockState(base.east()).is(b) || level.getBlockState(base.west()).is(b);
        boolean alongZ = level.getBlockState(base.north()).is(b) || level.getBlockState(base.south()).is(b);
        if (alongX && !alongZ) axis = net.minecraft.core.Direction.Axis.X;
        else if (alongZ && !alongX) axis = net.minecraft.core.Direction.Axis.Z;
        BlockState st = b.defaultBlockState().setValue(RampartBlock.AXIS, axis);
        level.setBlock(base, st.setValue(RampartBlock.PART, RampartBlock.Part.BASE), Block.UPDATE_ALL);
        level.setBlock(base.above(), st.setValue(RampartBlock.PART, RampartBlock.Part.MIDDLE), Block.UPDATE_ALL);
        level.setBlock(base.above(2), st.setValue(RampartBlock.PART, RampartBlock.Part.TOP), Block.UPDATE_ALL);
        KingdomClaim.trackRampart(List.of(base, base.above(), base.above(2)));
        level.playSound(null, base, SoundEvents.DEEPSLATE_BRICKS_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
        if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
