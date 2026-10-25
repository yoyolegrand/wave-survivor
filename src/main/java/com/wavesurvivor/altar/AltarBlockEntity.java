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

    // Arène (1.8) : si l'autel appartient à une arène toute faite, de quoi retrouver ses repères (coin d'origine, rotation, niveau du sol)
    private String arenaId = null;
    private int arenaRot, arenaOx, arenaY0, arenaOz;
    private boolean arenaRewarded = false;

    public String arenaId() { return arenaId; }
    public int arenaRot() { return arenaRot; }
    public int arenaOx() { return arenaOx; }
    public int arenaY0() { return arenaY0; }
    public int arenaOz() { return arenaOz; }
    public boolean arenaRewarded() { return arenaRewarded; }

    public void setArena(String id, int rot, int ox, int y0, int oz) {
        arenaId = id;
        arenaRot = rot;
        arenaOx = ox;
        arenaY0 = y0;
        arenaOz = oz;
    }

    public void setArenaRewarded(boolean v) {
        arenaRewarded = v;
        setChanged();
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        if (arenaId != null) {
            tag.putString("ArenaId", arenaId);
            tag.putInt("ArenaRot", arenaRot);
            tag.putInt("ArenaOx", arenaOx);
            tag.putInt("ArenaY0", arenaY0);
            tag.putInt("ArenaOz", arenaOz);
            tag.putBoolean("ArenaRewarded", arenaRewarded);
        }
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        arenaId = tag.contains("ArenaId") ? tag.getString("ArenaId") : null;
        arenaRot = tag.getInt("ArenaRot");
        arenaOx = tag.getInt("ArenaOx");
        arenaY0 = tag.getInt("ArenaY0");
        arenaOz = tag.getInt("ArenaOz");
        arenaRewarded = tag.getBoolean("ArenaRewarded");
    }

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
