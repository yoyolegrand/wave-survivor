package com.wavesurvivor.entity;

import com.wavesurvivor.horde.chaos.GisementManager;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;

/**
 * GISEMENT — amas de minerai à miner (événement chaos bénéfique).
 * Ne prend de coups que d'un joueur au corps à corps, PIOCHE en main, de niveau ≥ minTier
 * (0 bois/or, 1 pierre, 2 fer, 3 diamant, 4 netherite). Chaque coup = 1 PV (PV max = nombre de coups),
 * avec le son et les particules du minerai ; la pioche s'use. Butin géré par GisementManager.
 */
public class GisementEntity extends Mob {

    private static final EntityDataAccessor<String> BLOCKS = SynchedEntityData.defineId(GisementEntity.class, EntityDataSerializers.STRING);
    /** Taille : 0 petit, 1 moyen, 2 grand. */
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(GisementEntity.class, EntityDataSerializers.INT);
    /** Type : 0 = minerai (pioche), 1 = arbre (hache). */
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(GisementEntity.class, EntityDataSerializers.INT);
    public static final String DEFAULT_ORE = "minecraft:iron_ore,minecraft:stone";
    public static final String DEFAULT_TREE = "minecraft:oak_log|minecraft:oak_leaves";
    public static final float[] SCALES = {1.0f, 1.5f, 2.2f};
    public static final double[] HIT_MULT = {1.0, 1.75, 2.75};
    public static final double[] LOOT_MULT = {1.0, 2.0, 3.5};
    private int minTier = 1;
    private long lastHit = -100;

    public GisementEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BLOCKS, DEFAULT_ORE);
        this.entityData.define(SIZE, 0);
        this.entityData.define(KIND, 0);
    }

    public void setTree(boolean tree) {
        this.entityData.set(KIND, tree ? 1 : 0);
        this.refreshDimensions();
    }

    /** Foyer de corruption (objectif de purification du Calme) : se casse avec n'importe quelle arme. */
    public void setFoyer() {
        this.entityData.set(KIND, 2);
        this.refreshDimensions();
    }

    public boolean isFoyer() {
        try { return this.entityData.get(KIND) == 2; } catch (Exception e) { return false; }
    }

    public boolean isTree() {
        try { return this.entityData.get(KIND) == 1; } catch (Exception e) { return false; }
    }

    public void setSize(int size) {
        this.entityData.set(SIZE, Math.max(0, Math.min(2, size)));
        this.refreshDimensions();
    }

    public int getSize() {
        try { return Math.max(0, Math.min(2, this.entityData.get(SIZE))); } catch (Exception e) { return 0; }
    }

    public float scale() { return SCALES[getSize()]; }

    public static String sizeLabel(int size) {
        return com.wavesurvivor.i18n.WSLang.t("gisement.size." + Math.max(0, Math.min(2, size)));
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        // Arbre fantastique : emprise plus large et très haut (tronc + couronne en parasol)
        return isTree() ? super.getDimensions(pose).scale(scale() * 1.6f, scale() * 5.6f) : super.getDimensions(pose).scale(scale());
    }

    /** Arbre : la couronne déborde largement de la boîte de collision → boîte de rendu élargie (pas de disparition en bord d'écran). */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return isTree() ? getBoundingBox().inflate(2.6 * scale(), 0.6 * scale(), 2.6 * scale()) : super.getBoundingBoxForCulling();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SIZE.equals(key) || KIND.equals(key)) this.refreshDimensions();
    }

    public void setup(String blocksCsv, int minTier) {
        this.entityData.set(BLOCKS, blocksCsv == null || blocksCsv.isBlank() ? (isTree() ? DEFAULT_TREE : DEFAULT_ORE) : blocksCsv);
        this.minTier = Math.max(0, minTier);
    }

    public String getBlocksCsv() { return this.entityData.get(BLOCKS); }

    /** Bloc principal (sons / particules). */
    public BlockState mainState() {
        String first = getBlocksCsv().split("[,|]")[0].trim();
        try {
            Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(first));
            return (b == Blocks.AIR ? Blocks.STONE : b).defaultBlockState();
        } catch (Exception e) {
            return Blocks.STONE.defaultBlockState();
        }
    }

    public static String tierName(int t) {
        return com.wavesurvivor.i18n.WSLang.t("tier." + Math.max(0, Math.min(4, t)));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (!(source.getDirectEntity() instanceof Player p) || p.isSpectator()) return false;
        if (isFoyer()) {
            // Foyer : n'importe quelle arme (dégâts du coup, plafonnés), petite pause entre deux coups
            long t = level().getGameTime();
            if (t - lastHit < 6) return false;
            lastHit = t;
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.7 * scale(), getZ(), 20, 0.4, 0.4, 0.4, 0.05);
                sl.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
            }
            this.invulnerableTime = 0;
            float hit = Math.max(2f, Math.min(amount, 14f));
            // Arcaniste : purifie deux fois plus vite
            if (p instanceof net.minecraft.server.level.ServerPlayer sp
                    && com.wavesurvivor.horde.kingdom.KingdomRoles.has(sp, com.wavesurvivor.horde.kingdom.KingdomRoles.Role.ARCANIST)) hit *= 2f;
            return super.hurt(source, hit);
        }
        ItemStack st = p.getMainHandItem();
        boolean tree = isTree();
        if (!st.canPerformAction(tree ? ToolActions.AXE_DIG : ToolActions.PICKAXE_DIG)) {
            p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c(tree ? "gisement.need_axe" : "gisement.need_pickaxe"), true);
            return false;
        }
        int lvl = st.getItem() instanceof TieredItem ti ? ti.getTier().getLevel() : 0;
        if (lvl < minTier) {
            p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c(tree ? "gisement.weak_axe" : "gisement.weak_pickaxe", tierName(minTier)), true);
            return false;
        }
        long now = level().getGameTime();
        if (now - lastHit < 8) return false;
        lastHit = now;

        BlockState state = mainState();
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), getX(), getY() + 0.7 * scale(), getZ(),
                    (int) (18 * scale()), 0.4 * scale(), 0.35 * scale(), 0.4 * scale(), 0.1);
            SoundEvent s = state.getSoundType().getBreakSound();
            sl.playSound(null, blockPosition(), s, SoundSource.BLOCKS, 1.0f, 0.8f + random.nextFloat() * 0.3f);
        }
        if (!p.isCreative()) st.hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        GisementManager.onHit(this, p);
        this.invulnerableTime = 0;
        // Force du coup selon la pioche (bois 1 → netherite 5, pioche moddée = diamant 4) ; Prospecteur ×2
        float dmg = tree ? axePower(st) : pickaxePower(st);
        dmg *= 1f + com.wavesurvivor.item.RelicEffects.scaled(p, com.wavesurvivor.registry.ModItems.ANNEAU_PROSPECTEUR.get(), 1f); // ×2 / ×2,5 / ×3
        return super.hurt(source, dmg);
    }

    /** Puissance d'une pioche : bois/or 1, pierre 2, fer 3, diamant 4, netherite 5 ; toute pioche moddée = 4. */
    public static float pickaxePower(ItemStack st) {
        var it = st.getItem();
        if (it == net.minecraft.world.item.Items.WOODEN_PICKAXE || it == net.minecraft.world.item.Items.GOLDEN_PICKAXE) return 1f;
        if (it == net.minecraft.world.item.Items.STONE_PICKAXE) return 2f;
        if (it == net.minecraft.world.item.Items.IRON_PICKAXE) return 3f;
        if (it == net.minecraft.world.item.Items.DIAMOND_PICKAXE) return 4f;
        if (it == net.minecraft.world.item.Items.NETHERITE_PICKAXE) return 5f;
        return 4f; // pioche d'un autre mod : niveau diamant
    }

    /** Puissance d'une hache : bois/or 1, pierre 2, fer 3, diamant 4, netherite 5 ; toute hache moddée = 4. */
    public static float axePower(ItemStack st) {
        var it = st.getItem();
        if (it == net.minecraft.world.item.Items.WOODEN_AXE || it == net.minecraft.world.item.Items.GOLDEN_AXE) return 1f;
        if (it == net.minecraft.world.item.Items.STONE_AXE) return 2f;
        if (it == net.minecraft.world.item.Items.IRON_AXE) return 3f;
        if (it == net.minecraft.world.item.Items.DIAMOND_AXE) return 4f;
        if (it == net.minecraft.world.item.Items.NETHERITE_AXE) return 5f;
        return 4f; // hache d'un autre mod : niveau diamant
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("GisementBlocks", getBlocksCsv());
        tag.putInt("GisementTier", minTier);
        tag.putInt("GisementSize", getSize());
        tag.putBoolean("GisementTree", isTree());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("GisementTree")) setTree(tag.getBoolean("GisementTree"));
        if (tag.contains("GisementBlocks")) setup(tag.getString("GisementBlocks"), tag.getInt("GisementTier"));
        if (tag.contains("GisementSize")) setSize(tag.getInt("GisementSize"));
    }

    // ─── Immobile ───
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity other) {}
    @Override public void push(double x, double y, double z) {}
    @Override public void knockback(double strength, double x, double z) {}
    @Override public boolean removeWhenFarAway(double dist) { return false; }
    @Override public boolean isAffectedByPotions() { return false; }
    @Override protected SoundEvent getHurtSound(DamageSource src) { return null; }
    @Override protected SoundEvent getDeathSound() { return mainState().getSoundType().getBreakSound(); }
    @Override protected SoundEvent getAmbientSound() { return null; }
}
