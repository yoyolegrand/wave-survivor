package com.wavesurvivor.shrine;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Sanctuaire du Séraphin : garde la FOI (jauge de -maxFaith à +maxFaith, négatif = corrompu) et en déduit l'étape (0 pure … 4 corrompue),
 * synchronisée aux joueurs pour que le rendu (texture, ailes, halo) suive l'état. Une statue = un état partagé par tous les joueurs.
 * Les seuils et les valeurs viennent de ShrineConfig (shrine_offerings.json).
 */
public class SeraphShrineBlockEntity extends BlockEntity implements MenuProvider {

    public static final int MAX_STAGE = 4;
    /** Nombre de valeurs synchronisées avec le menu (voir menuData). */
    public static final int DATA_SIZE = 14;

    private int faith = 0;
    private int stage = 2;       // neutre au départ
    /** Dernière variation de la jauge (offrande ou événement), affichée dans l'écran ; non sauvegardée. */
    private int lastDelta = 0;
    /** Éclats produits et gardés dans la statue (récoltés par le joueur). */
    private int graceStock = 0, taintStock = 0;
    /** Avancement du cycle de production (ticks) et sens de production du dernier tick (-1 aucun, 0 grâce, 1 souillure). */
    private int progress = 0, direction = -1;

    public SeraphShrineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERAPH_SHRINE.get(), pos, state);
    }

    // ─── Registre des statues chargées (pour les événements à proximité) ───

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) ShrineRegistry.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ShrineRegistry.remove(this);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level instanceof ServerLevel) ShrineRegistry.add(this);
    }

    // ─── Foi et étape ───

    public int getStage() { return stage; }

    public int getFaith() { return faith; }

    public int getLastDelta() { return lastDelta; }

    /** Change l'étape seulement (aperçu / commandes) : cale la foi au milieu de cette étape. */
    public void setStage(int s) {
        int clamped = Math.max(0, Math.min(MAX_STAGE, s));
        if (clamped == stage) return;
        setFaith(ShrineConfig.defaultFaithOf(clamped));
    }

    /** Fixe la foi (bornée), recalcule l'étape ; renvoie vrai si l'étape a changé. */
    public boolean setFaith(int f) {
        int max = ShrineConfig.maxFaith();
        faith = Math.max(-max, Math.min(max, f));
        int ns = ShrineConfig.stageOf(faith);
        boolean changed = ns != stage;
        stage = ns;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return changed;
    }

    /**
     * Ajoute (ou retire) de la foi : offrande, événement à proximité… Annonce le changement d'étape aux joueurs proches.
     * Renvoie vrai si l'étape a changé.
     */
    public boolean addFaith(int delta) {
        if (delta == 0) return false;
        int before = stage;
        boolean changed = setFaith(faith + delta);
        lastDelta = delta;
        if (changed && level instanceof ServerLevel sl) announceStage(sl, before);
        return changed;
    }

    private void announceStage(ServerLevel sl, int before) {
        boolean purer = stage < before;
        double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY() + 3.0, cz = worldPosition.getZ() + 0.5;
        if (purer) {
            sl.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.88f, 0.45f), 1.4f), cx, cy, cz, 40, 1.2, 1.4, 1.2, 0);
            sl.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 15, 1.0, 1.5, 1.0, 0.02);
            sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 1.4f);
        } else {
            sl.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.27f, 0.78f), 1.4f), cx, cy, cz, 40, 1.2, 1.4, 1.2, 0);
            sl.sendParticles(ParticleTypes.SMOKE, cx, cy, cz, 20, 1.0, 1.5, 1.0, 0.02);
            sl.playSound(null, worldPosition, SoundEvents.WITHER_AMBIENT, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
        Component msg = Component.literal(WSLang.t("shrine.becomes", WSLang.t("shrine.stage." + stage)));
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(cx, cy, cz) <= 40.0 * 40.0) p.displayClientMessage(msg, true);
        }
    }

    /** Effets d'une offrande déposée (son et particules légers, sans changement d'étape). */
    public void offerFeedback(boolean grace) {
        if (!(level instanceof ServerLevel sl)) return;
        double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY() + 2.0, cz = worldPosition.getZ() + 0.5;
        if (grace) {
            sl.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.88f, 0.45f), 1.0f), cx, cy, cz, 12, 0.5, 0.8, 0.5, 0);
            sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8f, 1.3f);
        } else {
            sl.sendParticles(new DustParticleOptions(new Vector3f(0.7f, 0.2f, 0.85f), 1.0f), cx, cy, cz, 12, 0.5, 0.8, 0.5, 0);
            sl.playSound(null, worldPosition, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 0.8f, 0.8f);
        }
    }

    // ─── Production et récolte de fragments ───

    /** Sens de production selon l'étape : 0 = grâce (étapes 0-1), 1 = souillure (étapes 3-4), -1 = rien (neutre). */
    private int directionOf(int st) { return st <= 1 ? 0 : st >= 3 ? 1 : -1; }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, SeraphShrineBlockEntity be) {
        be.tickProduction();
    }

    private void tickProduction() {
        int dir = directionOf(stage), iv = ShrineConfig.intervalTicks(stage);
        if (dir != direction) { direction = dir; progress = 0; }
        if (dir < 0 || iv <= 0) { progress = 0; return; }
        int cap = ShrineConfig.stockCap();
        if (stock(dir) >= cap) { progress = Math.min(progress, iv); return; } // plein : on attend la récolte
        if (++progress >= iv) produce(dir, iv);
    }

    private int stock(int dir) { return dir == 0 ? graceStock : taintStock; }

    private void produce(int dir, int iv) {
        progress = Math.max(0, progress - iv);
        if (dir == 0) graceStock = Math.min(ShrineConfig.stockCap(), graceStock + 1);
        else taintStock = Math.min(ShrineConfig.stockCap(), taintStock + 1);
        setChanged();
        if (level instanceof ServerLevel sl) {
            double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY() + 1.6, cz = worldPosition.getZ() + 0.5;
            sl.sendParticles(dir == 0 ? ParticleTypes.END_ROD : ParticleTypes.SOUL, cx, cy, cz, 6, 0.4, 0.5, 0.4, 0.01);
        }
    }

    /** Avance la production (monstre de horde tué à proximité), dans le sens de l'étape. */
    public void advance(int ticks) {
        int dir = directionOf(stage), iv = ShrineConfig.intervalTicks(stage);
        if (dir < 0 || iv <= 0 || ticks <= 0 || stock(dir) >= ShrineConfig.stockCap()) return;
        if (dir != direction) { direction = dir; progress = 0; }
        progress += ticks;
        while (progress >= iv && stock(dir) < ShrineConfig.stockCap()) produce(dir, iv);
        progress = Math.min(progress, iv);
    }

    public int getGraceStock() { return graceStock; }

    public int getTaintStock() { return taintStock; }

    /** Donne au joueur tous les Éclats stockés (ce qui ne rentre pas dans l'inventaire tombe à ses pieds). */
    public void harvest(ServerPlayer p) {
        if (graceStock <= 0 && taintStock <= 0) {
            p.displayClientMessage(Component.literal(WSLang.t("shrine.harvest_empty")), true);
            return;
        }
        give(p, new net.minecraft.world.item.ItemStack(com.wavesurvivor.registry.ModItems.GRACE_SHARD.get()), graceStock);
        give(p, new net.minecraft.world.item.ItemStack(com.wavesurvivor.registry.ModItems.TAINT_SHARD.get()), taintStock);
        p.displayClientMessage(Component.literal(WSLang.t("shrine.harvested", graceStock, taintStock)), true);
        graceStock = 0;
        taintStock = 0;
        setChanged();
        p.level().playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.6f);
    }

    private static void give(ServerPlayer p, net.minecraft.world.item.ItemStack proto, int count) {
        while (count > 0) {
            net.minecraft.world.item.ItemStack st = proto.copy();
            st.setCount(Math.min(count, st.getMaxStackSize()));
            count -= st.getCount();
            if (!p.getInventory().add(st)) p.drop(st, false);
        }
    }

    // ─── Menu ───

    /** Valeurs envoyées au menu : 0 foi · 1 dernière variation · 2 étape · 3 maximum · 4-7 seuils (pure, apaisée, souillée, corrompue) · 8-9 stocks (grâce, souillure) · 10-11 production (secondes) · 12 plafond · 13 coût de fusion. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> faith;
                    case 1 -> lastDelta;
                    case 2 -> stage;
                    case 3 -> ShrineConfig.maxFaith();
                    case 4 -> ShrineConfig.pure();
                    case 5 -> ShrineConfig.calm();
                    case 6 -> ShrineConfig.soiled();
                    case 7 -> ShrineConfig.corrupted();
                    case 8 -> graceStock;
                    case 9 -> taintStock;
                    case 10 -> progress / 20;
                    case 11 -> ShrineConfig.intervalTicks(stage) / 20;
                    case 12 -> ShrineConfig.stockCap();
                    case 13 -> ShrineConfig.fusionCost();
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() { return DATA_SIZE; }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(WSLang.t("shrine.title"));
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SeraphShrineMenu(id, inv, this);
    }

    // ─── Sauvegarde / synchronisation ───

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Stage", stage);
        tag.putInt("Faith", faith);
        tag.putInt("GraceStock", graceStock);
        tag.putInt("TaintStock", taintStock);
        tag.putInt("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stage = Math.max(0, Math.min(MAX_STAGE, tag.getInt("Stage")));
        // Anciennes statues (étape seule) : la foi est calée au milieu de l'étape
        faith = tag.contains("Faith") ? tag.getInt("Faith") : ShrineConfig.defaultFaithOf(stage);
        graceStock = Math.max(0, tag.getInt("GraceStock"));
        taintStock = Math.max(0, tag.getInt("TaintStock"));
        progress = Math.max(0, tag.getInt("Progress"));
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
