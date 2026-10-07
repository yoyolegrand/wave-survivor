package com.wavesurvivor.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * BRÈCHE — faille de portail vers une autre dimension (mécanique Brèches infernales).
 * Immobile, sans IA, sans recul, immunisée au feu. Le rendu (client) : faille de portail animée teintée,
 * anneau de blocs au sol, éclats en orbite ; rétrécit avec ses PV. Style + couleur synchronisés.
 */
public class BrecheEntity extends Mob implements net.minecraft.world.entity.monster.Enemy {

    private static final EntityDataAccessor<String> STYLE = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.INT);
    /** Taille (1 = brèche normale, 3 = grand portail du mode Kingdom). */
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.FLOAT);
    /** Mode Kingdom : « Porte de l'Abîme » (rendu monumental) au lieu d'une brèche classique. */
    private static final EntityDataAccessor<Boolean> GREAT = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.BOOLEAN);
    /** Mode Kingdom : Catalyseur (cristal à détruire pendant le Calme pour « débrécher » une Porte). */
    private static final EntityDataAccessor<Boolean> CATALYST = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.BOOLEAN);
    /** Mode Kingdom : thème visuel de la Porte (abyss, fire, bone, end, ocean, arcane). */
    private static final EntityDataAccessor<String> THEME = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.STRING);
    /** Mode Kingdom : Porte construite en blocs réels (le rendu ne dessine plus que la faille et ses effets). */
    private static final EntityDataAccessor<Boolean> BUILT = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.BOOLEAN);
    /** Mode Kingdom : faille au sol des brèches du Calme (visuel seul : fissure, fosse lumineuse, yeux). */
    private static final EntityDataAccessor<Boolean> GROUND = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.BOOLEAN);
    /** Faille au sol : paires d'yeux restantes (= unités encore à sortir). */
    private static final EntityDataAccessor<Integer> EYES = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.INT);
    /** Faille au sol : ouverture de 0 (fermée) à 1 (grande ouverte). */
    private static final EntityDataAccessor<Float> OPEN = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.FLOAT);
    /** Mode Kingdom : cristal de la Mairie au-dessus du Monolithe (visuel seul ; sa taille suit le niveau, stocké dans EYES). */
    private static final EntityDataAccessor<Boolean> CRYSTAL = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.BOOLEAN);
    /** Mode Kingdom : Porte en forme d'« Antre » (amas rocheux, ovale tourbillonnant, vrilles) au lieu de l'arche. */
    private static final EntityDataAccessor<Boolean> DEN = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.BOOLEAN);
    /** Antre : couleur du fond de l'ovale ("auto" ou une des 16 couleurs de teinture). */
    private static final EntityDataAccessor<String> DEN_BG = SynchedEntityData.defineId(BrecheEntity.class, EntityDataSerializers.STRING);
    /** Mode Kingdom : plancher de PV (la Porte débréchée ne peut pas descendre plus bas). Côté serveur. */
    private float hpFloor = 0f;
    /** Mode Kingdom : portail invulnérable (pendant l'Assaut). Côté serveur uniquement. */
    private boolean shielded = false;

    public BrecheEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(STYLE, BreachStyle.INFERNALE.id);
        this.entityData.define(COLOR, -1);
        this.entityData.define(SCALE, 1.0f);
        this.entityData.define(GREAT, false);
        this.entityData.define(CATALYST, false);
        this.entityData.define(THEME, "abyss");
        this.entityData.define(BUILT, false);
        this.entityData.define(GROUND, false);
        this.entityData.define(EYES, 0);
        this.entityData.define(OPEN, 0f);
        this.entityData.define(CRYSTAL, false);
        this.entityData.define(DEN, false);
        this.entityData.define(DEN_BG, "auto");
    }

    public void setDenBackground(String c) { this.entityData.set(DEN_BG, c == null || c.isBlank() ? "auto" : c); }

    public String getDenBackground() { return this.entityData.get(DEN_BG); }

    public void setDen(boolean d) { this.entityData.set(DEN, d); }

    public boolean isDen() { return this.entityData.get(DEN); }

    public void setCrystal(boolean c) { this.entityData.set(CRYSTAL, c); }

    public boolean isCrystal() { return this.entityData.get(CRYSTAL); }

    public void setGroundRift(boolean g) { this.entityData.set(GROUND, g); }

    public boolean isGroundRift() { return this.entityData.get(GROUND); }

    public void setRiftEyes(int n) { this.entityData.set(EYES, Math.max(0, n)); }

    public int getRiftEyes() { return this.entityData.get(EYES); }

    public void setRiftOpen(float f) {
        if (Math.abs(this.entityData.get(OPEN) - f) > 0.01f) this.entityData.set(OPEN, f);
    }

    public float getRiftOpen() { return this.entityData.get(OPEN); }

    /** Faille au sol : ni cliquable (les brèches ne sont déjà jamais poussées, voir isPushable plus bas). */
    @Override
    public boolean isPickable() { return !isGroundRift() && !isCrystal() && super.isPickable(); }

    public void setBuilt(boolean b) { this.entityData.set(BUILT, b); }

    public boolean isBuilt() { return this.entityData.get(BUILT); }

    public void setCatalyst(boolean c) { this.entityData.set(CATALYST, c); }

    public boolean isCatalyst() { return this.entityData.get(CATALYST); }

    public void setTheme(String th) { this.entityData.set(THEME, th == null ? "abyss" : th); }

    public String getTheme() { return this.entityData.get(THEME); }

    public void setHpFloor(float f) { this.hpFloor = Math.max(0f, f); }

    public void setGreat(boolean g) { this.entityData.set(GREAT, g); }

    public boolean isGreat() { return this.entityData.get(GREAT); }

    /** Porte de l'Abîme : le décor dépasse largement la hitbox → zone d'affichage élargie (sinon elle clignote en bord d'écran). */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        if (isCatalyst()) return super.getBoundingBoxForCulling().inflate(1.5, 16.0, 1.5); // rayon de lumière
        if (isGroundRift()) return super.getBoundingBoxForCulling().inflate(4.5, 1.5, 4.5); // veines au sol
        if (isCrystal()) return super.getBoundingBoxForCulling().inflate(3.0, 4.0, 3.0);    // cristal + éclats en orbite
        return isGreat() ? super.getBoundingBoxForCulling().inflate(8.0, 12.0, 8.0) : super.getBoundingBoxForCulling();
    }

    public void setRiftScale(float s) {
        this.entityData.set(SCALE, Math.max(0.5f, Math.min(6f, s)));
        this.refreshDimensions();
    }

    public float getRiftScale() {
        return this.entityData.get(SCALE);
    }

    public void setShielded(boolean s) { this.shielded = s; }

    public boolean isShielded() { return shielded; }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SCALE.equals(key)) this.refreshDimensions();
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return super.getDimensions(pose).scale(getRiftScale());
    }

    /** @param color RGB, ou -1 pour la couleur du style. */
    public void setStyle(String style, int color) {
        this.entityData.set(STYLE, BreachStyle.byId(style).id);
        this.entityData.set(COLOR, color);
    }

    public BreachStyle getStyle() {
        return BreachStyle.byId(this.entityData.get(STYLE));
    }

    /** Couleur effective de la faille. */
    public int getRiftColor() {
        int c = this.entityData.get(COLOR);
        return c >= 0 ? c : getStyle().color;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("BreachStyle", this.entityData.get(STYLE));
        tag.putInt("BreachColor", this.entityData.get(COLOR));
        tag.putFloat("BreachScale", getRiftScale());
        tag.putBoolean("BreachGreat", isGreat());
        tag.putBoolean("BreachCatalyst", isCatalyst());
        tag.putString("BreachTheme", getTheme());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("BreachStyle")) this.entityData.set(STYLE, tag.getString("BreachStyle"));
        if (tag.contains("BreachColor")) this.entityData.set(COLOR, tag.getInt("BreachColor"));
        if (tag.contains("BreachScale")) setRiftScale(tag.getFloat("BreachScale"));
        if (tag.contains("BreachGreat")) setGreat(tag.getBoolean("BreachGreat"));
        if (tag.contains("BreachCatalyst")) setCatalyst(tag.getBoolean("BreachCatalyst"));
        if (tag.contains("BreachTheme")) setTheme(tag.getString("BreachTheme"));
    }

    // ─── Immobile, inébranlable ───

    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity other) {}
    @Override public void push(double x, double y, double z) {}
    @Override public void knockback(double strength, double x, double z) {}
    @Override public boolean removeWhenFarAway(double dist) { return false; }
    @Override public boolean isAffectedByPotions() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) || source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)) return false;
        if (isGroundRift() || isCrystal()) return false; // faille au sol / cristal : purement visuels
        if (shielded && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // Mode Kingdom : bouclier pendant l'Assaut (le portail ne peut être attaqué que pendant le Calme)
            if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("kingdom.portal_shielded"), true);
            }
            this.level().playSound(null, this.blockPosition(), SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.HOSTILE, 1f, 0.6f);
            return false;
        }
        // Porte débréchée : elle ne peut pas descendre sous son plancher de PV
        if (hpFloor > 0f && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount = Math.min(amount, this.getHealth() - hpFloor);
            if (amount <= 0f) {
                if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("kingdom.portal_shielded"), true);
                }
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    // ─── Sons ───

    @Override protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.AMETHYST_BLOCK_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.BEACON_DEACTIVATE; }
    @Override protected SoundEvent getAmbientSound() { return null; }
}
