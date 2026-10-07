package com.wavesurvivor.horde.kingdom;

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
 * Données d'une DÉFENSE posée (mode Kingdom) : son niveau, synchronisé au client
 * pour que le rendu 3D (DefenseRenderer) change avec les améliorations.
 */
public class DefenseBlockEntity extends BlockEntity {

    private int defLevel = 1;
    /** Spécialisation choisie au niveau 3 (« » sinon) — affichée par le rendu 3D. */
    private String variant = "";

    public String getVariant() { return variant; }

    public void setVariant(String v) {
        this.variant = v == null ? "" : v;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    /** Tour du Glaneur : stockage de 54 cases (double coffre) ; seules 3 / 4 / 6 rangées servent selon le niveau. */
    private final net.minecraft.world.SimpleContainer storage = new net.minecraft.world.SimpleContainer(54) {
        @Override
        public void setChanged() {
            super.setChanged();
            DefenseBlockEntity.this.setChanged();
        }
    };
    /** PV actuels / max (synchronisés pour la barre au-dessus de la défense ; 0 = pas encore suivis). */
    private float hp = 0f, maxHp = 0f;

    public DefenseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEFENSE.get(), pos, state);
    }

    public DefenseBlock.Kind kind() {
        return getBlockState().getBlock() instanceof DefenseBlock d ? d.kind() : DefenseBlock.Kind.ARCHER;
    }

    public int getDefLevel() { return defLevel; }

    public void setDefLevel(int lvl) {
        this.defLevel = Math.max(1, Math.min(3, lvl));
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public float getHp() { return hp; }

    public float getMaxHp() { return maxHp; }

    /** Met à jour les PV affichés (envoi au client seulement si la valeur change d'au moins 1). */
    public void setHp(float hp, float max) {
        boolean changed = Math.abs(this.hp - hp) >= 1f || Math.abs(this.maxHp - max) >= 1f;
        this.hp = hp;
        this.maxHp = max;
        if (!changed) return;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public net.minecraft.world.SimpleContainer getStorage() { return storage; }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("DefLevel")) defLevel = tag.getInt("DefLevel");
        variant = tag.getString("DefVariant");
        hp = tag.getFloat("DefHp");
        maxHp = tag.getFloat("DefMaxHp");
        if (tag.contains("Storage")) storage.fromTag(tag.getList("Storage", net.minecraft.nbt.Tag.TAG_COMPOUND));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("DefLevel", defLevel);
        tag.putString("DefVariant", variant);
        tag.putFloat("DefHp", hp);
        tag.putFloat("DefMaxHp", maxHp);
        tag.put("Storage", storage.createTag());
    }

    /** Le client n'a pas besoin du contenu du stockage : paquet de mise à jour allégé. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DefLevel", defLevel);
        tag.putString("DefVariant", variant);
        tag.putFloat("DefHp", hp);
        tag.putFloat("DefMaxHp", maxHp);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** La structure dépasse largement du bloc : zone de rendu élargie (sinon elle disparaît en regardant vers le haut). */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5).expandTowards(0, 5.5, 0);
    }
}
