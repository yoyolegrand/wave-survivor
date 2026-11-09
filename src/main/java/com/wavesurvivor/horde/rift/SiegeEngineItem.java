package com.wavesurvivor.horde.rift;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** 2.0 — Engin de siège acheté à la boutique du Monolithe (catapulte ou baliste céleste) : clic droit sur le sol de l'île pour le poser. */
public class SiegeEngineItem extends Item {
    private final boolean ballista;

    public SiegeEngineItem(Properties props, boolean ballista) {
        super(props);
        this.ballista = ballista;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        int r = RiftManager.placeEngine(level, at, ballista);
        if (ctx.getPlayer() != null) {
            String key = switch (r) {
                case 0 -> "rift.engine.placed";
                case 1 -> "rift.engine.no_room";
                case 2 -> "rift.engine.too_far";
                default -> "rift.engine.only_rift";
            };
            ctx.getPlayer().displayClientMessage(Component.literal(WSLang.t(key)), true);
        }
        if (r != 0) return InteractionResult.FAIL;
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
