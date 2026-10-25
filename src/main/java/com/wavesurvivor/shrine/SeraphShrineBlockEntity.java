package com.wavesurvivor.shrine;

import com.wavesurvivor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Sanctuaire du Séraphin : garde l'étape de corruption (0 = pure … 4 = corrompue), synchronisée aux joueurs
 * pour que le rendu (texture, ailes, halo) suive l'état.
 */
public class SeraphShrineBlockEntity extends BlockEntity {

    public static final int MAX_STAGE = 4;
    private int stage = 2;   // neutre au départ

    public SeraphShrineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERAPH_SHRINE.get(), pos, state);
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int s) {
        int clamped = Math.max(0, Math.min(MAX_STAGE, s));
        if (clamped == stage) return;
        stage = clamped;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Stage", stage);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stage = Math.max(0, Math.min(MAX_STAGE, tag.getInt("Stage")));
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("Stage", stage);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Le modèle déborde largement du bloc (ailes, halo) : zone de rendu agrandie pour qu'il ne disparaisse pas. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(4.0, 0.0, 4.0).expandTowards(0, 6.0, 0);
    }
}
