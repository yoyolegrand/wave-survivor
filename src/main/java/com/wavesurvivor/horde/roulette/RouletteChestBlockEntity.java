package com.wavesurvivor.horde.roulette;

import com.wavesurvivor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity du Coffre Roulette. Conteneur de 0 case (jamais ouvert) : il n'existe que pour porter
 * le NOM du coffre (CustomName), qui identifie sa config (RouletteChestRegistry.getByChestName).
 * Le nom est synchronisé au client → la teinte des runes / de la gemme suit le type de coffre.
 */
public class RouletteChestBlockEntity extends BaseContainerBlockEntity {

    public RouletteChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROULETTE_CHEST.get(), pos, state);
    }

    /** Couleur des runes / de la gemme (RGB), -1 = déduite du nom côté client. */
    private int color = -1;

    public int getColor() { return color; }

    // ─── Animation d'ouverture (1.9) : seulement envoyée au client, jamais sauvegardée ───
    private long animStart = -1;
    private int animGive = 0;
    private int animColor = 0xFFFFFF;
    private ItemStack animStack = ItemStack.EMPTY;

    /** Lance l'animation : instant de départ (temps du monde), instant de remise du gain (ticks), couleur de rareté et objet qui s'élève dans le faisceau. */
    public void startAnim(long start, int give, int color, ItemStack shown) {
        animStart = start;
        animGive = give;
        animColor = color;
        animStack = shown == null ? ItemStack.EMPTY : shown;
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void clearAnim() {
        if (animStart < 0) return;
        animStart = -1;
        animStack = ItemStack.EMPTY;
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public long animStart() { return animStart; }
    public int animGive() { return animGive; }
    public int animColor() { return animColor; }
    public ItemStack animStack() { return animStack; }

    /** Angle du couvercle affiché (client) et dernier instant de mise à jour : le couvercle suit sa cible à vitesse constante. */
    private float lidAngle = 0f;
    private long lidNanos = 0L;

    /** Déplace le couvercle vers {@code target} (degrés) à {@code speed} degrés par seconde et renvoie son angle actuel. */
    public float animateLid(float target, float speed) {
        long now = System.nanoTime();
        float dt = lidNanos == 0 ? 0 : Math.min(0.1f, (now - lidNanos) / 1.0e9f);
        lidNanos = now;
        float step = speed * dt;
        if (lidAngle < target) lidAngle = Math.min(target, lidAngle + step);
        else if (lidAngle > target) lidAngle = Math.max(target, lidAngle - step);
        return lidAngle;
    }

    /** Style résolu du coffre (JSON de {@link RouletteStyle}) : synchronisé au client, qui s'en sert pour le rendu. Vide = style par défaut. */
    private String style = "";
    private RouletteStyle parsed = null;

    public void setStyle(String json) {
        if (json == null) json = "";
        if (json.equals(style)) return;
        style = json;
        parsed = null;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Style à afficher : celui reçu du serveur, sinon le style par défaut à la couleur d'accent (anciens coffres). */
    public RouletteStyle style() {
        if (parsed == null) { int accent = color >= 0 ? color : getCustomName() != null ? RouletteKeyItem.colorForName(getCustomName().getString()) : 0x3DDC84;
            parsed = style.isEmpty() ? RouletteStyle.defaults(accent) : RouletteStyle.fromJson(style);
        }
        return parsed;
    }

    /** 2.0 : un coffre agrandi (Coffre du Titan) et son faisceau de révélation restent visibles quand le bloc sort de l'écran. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        double s = style().scale;
        if (s <= 1.0) return super.getRenderBoundingBox();
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(s, 0, s).expandTowards(0, 8 * s, 0);
    }

    /** Appelé par le serveur (config du coffre) ; synchronisé au client seulement si elle change. */
    public void setColor(int rgb) {
        if (rgb == color) return;
        color = rgb;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("RouletteColor", color);
        if (!style.isEmpty()) tag.putString("RouletteStyle", style);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        color = tag.contains("RouletteColor") ? tag.getInt("RouletteColor") : -1;
        style = tag.getString("RouletteStyle");
        parsed = null;
        animStart = tag.contains("RouletteAnimStart") ? tag.getLong("RouletteAnimStart") : -1;
        animGive = tag.getInt("RouletteAnimGive");
        animColor = tag.contains("RouletteAnimColor") ? tag.getInt("RouletteAnimColor") : 0xFFFFFF;
        animStack = tag.contains("RouletteAnimStack") ? ItemStack.of(tag.getCompound("RouletteAnimStack")) : ItemStack.EMPTY;
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ─── Synchro client (teinte) ───

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = saveWithoutMetadata();
        if (animStart >= 0) {
            t.putLong("RouletteAnimStart", animStart);
            t.putInt("RouletteAnimGive", animGive);
            t.putInt("RouletteAnimColor", animColor);
            if (!animStack.isEmpty()) t.put("RouletteAnimStack", animStack.save(new CompoundTag()));
        }
        return t;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        rerender();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        rerender();
    }

    private void rerender() {
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        }
    }

    // ─── Conteneur vide (0 case) ───

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.wavesurvivor.roulette_chest");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return null; // jamais ouvert : le clic droit est géré par RouletteChestEventHandler
    }

    @Override public int getContainerSize() { return 0; }
    @Override public boolean isEmpty() { return true; }
    @Override public ItemStack getItem(int slot) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }
    @Override public void setItem(int slot, ItemStack stack) {}
    @Override public boolean stillValid(Player player) { return true; }
    @Override public void clearContent() {}
}
