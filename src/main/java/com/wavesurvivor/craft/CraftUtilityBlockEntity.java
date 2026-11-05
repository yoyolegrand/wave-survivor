package com.wavesurvivor.craft;

import com.wavesurvivor.horde.boss.BossManager;
import com.wavesurvivor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Bloc entité des blocs utilitaires de l'Artisanat : sert de repère (registre des lanternes et bougies chargées) et fait
 * tourner la Fontaine sacrée, le Totem d'appât et l'Obélisque profane. Réglages : CraftConfig.blockNum.
 */
public class CraftUtilityBlockEntity extends BlockEntity {

    /** Positions des blocs « zone » chargés, par dimension et par type (lanterne, bougie) : lues par CraftUtilityEvents. */
    private static final Map<ResourceKey<Level>, Map<CraftUtilityBlock.Kind, Set<BlockPos>>> LOADED = new HashMap<>();

    /** Totem d'appât : fin de l'activation (temps de jeu). Totem d'appât et Autel de sang : fin de la recharge. */
    long activeUntil, readyAt;

    /** Poussière rose lumineuse (couleur des runes de l'Obélisque profane). */
    private static final net.minecraft.core.particles.DustParticleOptions PINK_FLAME =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.3f, 0.78f), 1.2f);
    /** Totem d'appât : nombre maximal de monstres attirés par passage, et distance de chaque étape de marche (le pathfinding ne porte pas à 64 blocs). */
    private static final int BAIT_MAX_MOBS = 120;
    private static final double BAIT_STEP = 22.0;

    public CraftUtilityBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRAFT_UTILITY.get(), pos, state);
    }

    /** Copie des positions chargées de ce type dans cette dimension. */
    public static synchronized Set<BlockPos> loaded(Level level, CraftUtilityBlock.Kind kind) {
        Map<CraftUtilityBlock.Kind, Set<BlockPos>> m = LOADED.get(level.dimension());
        if (m == null || !m.containsKey(kind)) return Set.of();
        return new HashSet<>(m.get(kind));
    }

    private CraftUtilityBlock.Kind kind() {
        return getBlockState().getBlock() instanceof CraftUtilityBlock b ? b.kind : null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        CraftUtilityBlock.Kind k = kind();
        if (level != null && !level.isClientSide && k != null) {
            synchronized (CraftUtilityBlockEntity.class) {
                LOADED.computeIfAbsent(level.dimension(), x -> new HashMap<>())
                        .computeIfAbsent(k, x -> new HashSet<>()).add(worldPosition.immutable());
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CraftUtilityBlock.Kind k = kind();
        if (level != null && !level.isClientSide && k != null) {
            synchronized (CraftUtilityBlockEntity.class) {
                Map<CraftUtilityBlock.Kind, Set<BlockPos>> m = LOADED.get(level.dimension());
                if (m != null && m.containsKey(k)) m.get(k).remove(worldPosition);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("activeUntil", activeUntil);
        tag.putLong("readyAt", readyAt);
    }

    /** Zone de rendu : l'obélisque fait 2 blocs de haut et son cristal flotte tout en haut. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new AABB(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), worldPosition.getX() + 1, worldPosition.getY() + 3, worldPosition.getZ() + 1);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        activeUntil = tag.getLong("activeUntil");
        readyAt = tag.getLong("readyAt");
    }

    // ─── Tick serveur ───

    public static void serverTick(Level level, BlockPos pos, BlockState state, CraftUtilityBlockEntity be) {
        if (!(level instanceof ServerLevel sl) || !(state.getBlock() instanceof CraftUtilityBlock b)) return;
        long now = level.getGameTime();
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        switch (b.kind) {
            case FOUNTAIN -> {
                int every = Math.max(1, (int) CraftConfig.blockNum("holy_fountain", "intervalSeconds", 2)) * 20;
                if (now % every != 0 || BossManager.activeBossCount() > 0) return; // pas de soin pendant un combat de boss
                double r = CraftConfig.blockNum("holy_fountain", "radius", 5);
                int amp = (int) CraftConfig.blockNum("holy_fountain", "amplifier", 0);
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(r))) {
                    if (p.distanceToSqr(cx, cy, cz) <= r * r) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, every + 20, amp, true, true, true));
                }
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, cx, pos.getY() + 1.1, cz, 3, 0.3, 0.2, 0.3, 0.0);
            }
            case OBELISK -> {
                if (now % 20 != 0) return;
                double r = CraftConfig.blockNum("profane_obelisk", "radius", 12);
                float dmg = (float) CraftConfig.blockNum("profane_obelisk", "damagePerSecond", 1);
                int hunger = (int) CraftConfig.blockNum("profane_obelisk", "hungerSeconds", 4) * 20;
                AABB box = new AABB(pos).inflate(r);
                for (Mob m : sl.getEntitiesOfClass(Mob.class, box, e -> e instanceof Enemy && e.isAlive())) {
                    if (m.distanceToSqr(cx, cy, cz) <= r * r) m.hurt(sl.damageSources().magic(), dmg);
                }
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, box)) {
                    if (p.distanceToSqr(cx, cy, cz) <= r * r) p.addEffect(new MobEffectInstance(MobEffects.HUNGER, hunger, 0, true, true, true));
                }
                // flammes roses, assorties aux runes et au cristal
                sl.sendParticles(PINK_FLAME, cx, pos.getY() + 1.4, cz, 6, 0.25, 0.5, 0.25, 0.02);
                sl.sendParticles(PINK_FLAME, cx, pos.getY() + 2.1, cz, 3, 0.12, 0.2, 0.12, 0.01);
            }
            case BAIT -> {
                if (now >= be.activeUntil || now % 20 != 0) return;
                // une fois par seconde seulement (now % 20) ; jusqu'à 64 blocs : les monstres lointains avancent par étapes vers le totem
                double r = CraftConfig.blockNum("bait_totem", "radius", 64);
                int n = 0;
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(r), e -> e instanceof Enemy && e.isAlive() && !BossManager.isBoss(e.getUUID()))) {
                    double d2 = m.distanceToSqr(cx, cy, cz);
                    if (d2 > r * r || d2 < 4.0) continue;
                    if (++n > BAIT_MAX_MOBS) break;
                    double d = Math.sqrt(d2), k = Math.min(1.0, BAIT_STEP / d);
                    m.getNavigation().moveTo(m.getX() + (cx - m.getX()) * k, m.getY() + (pos.getY() - m.getY()) * k, m.getZ() + (cz - m.getZ()) * k, 1.15);
                }
                sl.sendParticles(ParticleTypes.SOUL, cx, pos.getY() + 1.4, cz, 6, 0.3, 0.3, 0.3, 0.02);
            }
            default -> { }
        }
    }
}
