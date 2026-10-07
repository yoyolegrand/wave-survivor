package com.wavesurvivor.altar;

import com.wavesurvivor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * BlockEntity de l'Autel Runique (moitié basse uniquement) : porte l'état d'animation
 * du livre flottant, calqué sur EnchantmentTableBlockEntity vanilla.
 * Tick CLIENT uniquement (purement visuel, rien à synchroniser).
 */
public class AltarBlockEntity extends BlockEntity {

    /** Rayon de détection du joueur le plus proche (vanilla table d'enchant = 3). */
    private static final double LOOK_RADIUS = 8.0;
    private static final RandomSource RANDOM = RandomSource.create();

    public int time;
    public float flip, oFlip, flipT, flipA;
    public float open, oOpen;
    public float rot, oRot, tRot;

    public AltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALTAR_RUNIC.get(), pos, state);
    }

    /** Copie de EnchantmentTableBlockEntity.bookAnimationTick (rayon élargi, centre au sommet). */
    public static void bookAnimationTick(Level level, BlockPos pos, BlockState state, AltarBlockEntity be) {
        be.oOpen = be.open;
        be.oRot = be.rot;
        Player player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, LOOK_RADIUS, false);
        if (player != null) {
            double dx = player.getX() - (pos.getX() + 0.5);
            double dz = player.getZ() - (pos.getZ() + 0.5);
            be.tRot = (float) Mth.atan2(dz, dx);
            be.open += 0.1F;
            if (be.open < 0.5F || RANDOM.nextInt(40) == 0) {
                float old = be.flipT;
                do {
                    be.flipT += (float) (RANDOM.nextInt(4) - RANDOM.nextInt(4));
                } while (old == be.flipT);
            }
        } else {
            be.tRot += 0.02F;
            be.open -= 0.1F;
        }

        while (be.rot >= (float) Math.PI) be.rot -= (float) (Math.PI * 2);
        while (be.rot < -(float) Math.PI) be.rot += (float) (Math.PI * 2);
        while (be.tRot >= (float) Math.PI) be.tRot -= (float) (Math.PI * 2);
        while (be.tRot < -(float) Math.PI) be.tRot += (float) (Math.PI * 2);

        float d = be.tRot - be.rot;
        while (d >= (float) Math.PI) d -= (float) (Math.PI * 2);
        while (d < -(float) Math.PI) d += (float) (Math.PI * 2);
        be.rot += d * 0.4F;

        be.open = Mth.clamp(be.open, 0.0F, 1.0F);
        be.time++;
        be.oFlip = be.flip;
        float f = Mth.clamp((be.flipT - be.flip) * 0.4F, -0.2F, 0.2F);
        be.flipA += (f - be.flipA) * 0.9F;
        be.flip += be.flipA;
    }

    /** Le livre flotte au-dessus du bloc : élargir la boîte de rendu pour éviter qu'il disparaisse. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition, worldPosition.offset(1, 2, 1));
    }
}
