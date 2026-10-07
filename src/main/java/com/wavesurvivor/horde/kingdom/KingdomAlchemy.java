package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ALCHIMISTE : chaque monstre de la horde qu'il abat lui donne 1 RÉACTIF (3 pour une élite), compteur personnel.
 * Pendant le Calme, il les transforme en FIOLES DE JET (onglet Rôles du Monolithe), à lancer quand il veut.
 * Chaque fiole s'AMÉLIORE (niveaux 1 → 3, définitif jusqu'à la fin du royaume) contre des réactifs :
 *  - Givre (4) : Lenteur III + gel ; rayon 4/5/6, durée 6/8/10 s (niv. 3 : + Faiblesse) ;
 *  - Incendiaire (5) : dégâts 4/6/8 + feu 6/8/10 s ; rayon 4/4,5/5 ;
 *  - Soin (6) : +8/12/16 PV et Régénération 5/7/10 s aux joueurs et soldats ; rayon 5/6/7 ;
 *  - Orage (7) : foudre, dégâts 6/8/10 aux monstres + lueur 5 s ; rayon 3/3,5/4 ;
 *  - Bastion (6) : les constructions regagnent 15/25/35 % de leurs PV + Résistance I 6/8/10 s ; rayon 6/7/8.
 * Les fioles sont des potions jetables marquées : à l'impact, leur effet remplace celui de la potion.
 */
public final class KingdomAlchemy {

    private KingdomAlchemy() {}

    public static final String FLASK_TAG = "ws_flask";
    public static final String LEVEL_TAG = "ws_flask_lvl";
    public static final String[] FLASKS = {"frost", "fire", "heal", "storm", "bastion"};
    public static final int[] COST = {4, 5, 6, 7, 6};
    /** Coût pour passer au niveau 2, puis au niveau 3. */
    public static final int[] UPGRADE_COST = {12, 24};
    public static final int MAX_LEVEL = 3;
    private static final int[] COLOR = {0x7FD4FF, 0xFF6A1A, 0xFF5FA8, 0xF5E663, 0xB0B0B8};

    private static final Map<UUID, Integer> REAGENTS = new HashMap<>();
    /** Niveau de chaque fiole, par Alchimiste (1 à 3). */
    private static final Map<UUID, int[]> LEVELS = new HashMap<>();

    public static int reagents(UUID id) {
        return REAGENTS.getOrDefault(id, 0);
    }

    public static int[] levels(UUID id) {
        return LEVELS.computeIfAbsent(id, k -> {
            int[] a = new int[FLASKS.length];
            java.util.Arrays.fill(a, 1);
            return a;
        });
    }

    public static void clear() {
        REAGENTS.clear();
        LEVELS.clear();
    }

    /** Fabrication d'une fiole (codes 70-74 de la boutique) : Alchimiste, pendant le Calme, assez de réactifs. */
    public static void brew(ServerPlayer p, int i) {
        if (i < 0 || i >= FLASKS.length) return;
        if (!KingdomRoles.has(p, KingdomRoles.Role.ALCHEMIST)) {
            p.displayClientMessage(WSLang.c("kingdom.alchemy.only"), true);
            return;
        }
        if (KingdomManager.phase() != KingdomManager.Phase.CALM) {
            p.displayClientMessage(WSLang.c("kingdom.alchemy.calm_only"), true);
            return;
        }
        int have = reagents(p.getUUID());
        if (have < COST[i] && !p.isCreative()) {
            p.displayClientMessage(WSLang.c("kingdom.alchemy.missing", COST[i], have), true);
            return;
        }
        if (!p.isCreative()) REAGENTS.put(p.getUUID(), have - COST[i]);
        ItemStack st = flask(i, levels(p.getUUID())[i]);
        if (!p.getInventory().add(st)) p.drop(st, false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1f, 1f);
        KingdomRoleState.syncTo(p);
    }

    /** Amélioration définitive d'une fiole (codes 75-79) : 12 réactifs pour le niveau 2, 24 pour le niveau 3. */
    public static void upgrade(ServerPlayer p, int i) {
        if (i < 0 || i >= FLASKS.length) return;
        if (!KingdomRoles.has(p, KingdomRoles.Role.ALCHEMIST)) {
            p.displayClientMessage(WSLang.c("kingdom.alchemy.only"), true);
            return;
        }
        int[] lv = levels(p.getUUID());
        if (lv[i] >= MAX_LEVEL) {
            p.displayClientMessage(WSLang.c("kingdom.alchemy.maxed"), true);
            return;
        }
        int cost = UPGRADE_COST[lv[i] - 1];
        int have = reagents(p.getUUID());
        if (have < cost && !p.isCreative()) {
            p.displayClientMessage(WSLang.c("kingdom.alchemy.missing", cost, have), true);
            return;
        }
        if (!p.isCreative()) REAGENTS.put(p.getUUID(), have - cost);
        lv[i]++;
        p.displayClientMessage(WSLang.c("kingdom.alchemy.upgraded", WSLang.t("kingdom.alchemy.flask." + FLASKS[i]), lv[i]), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.2f);
        KingdomRoleState.syncTo(p);
    }

    private static ItemStack flask(int i, int lvl) {
        ItemStack st = new ItemStack(com.wavesurvivor.registry.ModItems.ALCHEMY_FLASK.get());
        var tag = st.getOrCreateTag();
        tag.putString("Potion", "minecraft:water");
        tag.putInt("CustomPotionColor", COLOR[i]);
        tag.putString(FLASK_TAG, FLASKS[i]);
        tag.putInt(LEVEL_TAG, lvl);
        st.setHoverName(Component.literal(WSLang.t("kingdom.alchemy.flask." + FLASKS[i]) + " §7" + WSLang.t("kingdom.alchemy.lvl", lvl)));
        return st;
    }

    /** Monstre visable par une fiole (pas les Portes ni les Catalyseurs). */
    private static boolean foe(LivingEntity le) {
        return le instanceof Enemy && le.isAlive() && !(le instanceof com.wavesurvivor.entity.BrecheEntity);
    }

    private static boolean ally(LivingEntity le) {
        return le.isAlive() && (le instanceof ServerPlayer || le instanceof com.wavesurvivor.entity.KingdomSoldier);
    }

    private static AABB box(double x, double y, double z, double r) {
        return new AABB(x - r, y - 2, z - r, x + r, y + 3, z + r);
    }

    public static class Events {

        /** Réactifs : monstres de la horde abattus par l'Alchimiste. */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void alchemyDeath(LivingDeathEvent e) {
            if (!KingdomRoles.active() || !(e.getSource().getEntity() instanceof ServerPlayer p)
                    || !KingdomRoles.has(p, KingdomRoles.Role.ALCHEMIST)) return;
            LivingEntity dead = e.getEntity();
            boolean elite = dead.getPersistentData().getBoolean("ws_elite");
            if (MobRegistry.get(dead.getUUID()) == null && !elite && !(dead instanceof Enemy)) return;
            REAGENTS.merge(p.getUUID(), elite ? 3 : 1, Integer::sum);
            KingdomRoleState.syncTo(p);
        }

        /** Impact d'une fiole : effet de l'Alchimiste (selon son niveau) à la place de la potion d'eau. */
        @SubscribeEvent
        public void alchemyImpact(ProjectileImpactEvent e) {
            if (!(e.getProjectile() instanceof ThrownPotion tp) || !(tp.level() instanceof ServerLevel lvl)) return;
            ItemStack st = tp.getItem();
            if (!st.hasTag() || !st.getTag().contains(FLASK_TAG)) return;
            String kind = st.getTag().getString(FLASK_TAG);
            int lv = Math.max(1, Math.min(MAX_LEVEL, st.getTag().getInt(LEVEL_TAG)));
            int k = lv - 1;
            e.setCanceled(true);
            double x = tp.getX(), y = tp.getY(), z = tp.getZ();
            BlockPos at = tp.blockPosition();
            tp.discard();
            int idx = java.util.Arrays.asList(FLASKS).indexOf(kind);
            lvl.levelEvent(2002, at, idx >= 0 ? COLOR[idx] : 0xFFFFFF); // éclats de verre colorés
            switch (kind) {
                case "frost" -> {
                    double r = 4 + k;
                    int t = (6 + 2 * k) * 20;
                    for (LivingEntity m : lvl.getEntitiesOfClass(LivingEntity.class, box(x, y, z, r), KingdomAlchemy::foe)) {
                        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, 2));
                        if (lv >= 3) m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, 0));
                        m.setTicksFrozen(Math.max(m.getTicksFrozen(), t + 40));
                    }
                    lvl.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 0.5, z, 40 + 20 * k, r / 2, 0.6, r / 2, 0.02);
                    lvl.playSound(null, at, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, 0.7f);
                }
                case "fire" -> {
                    double r = 4 + 0.5 * k;
                    float dmg = 4 + 2 * k;
                    for (LivingEntity m : lvl.getEntitiesOfClass(LivingEntity.class, box(x, y, z, r), KingdomAlchemy::foe)) {
                        m.hurt(lvl.damageSources().onFire(), dmg);
                        m.setSecondsOnFire(6 + 2 * k);
                    }
                    lvl.sendParticles(ParticleTypes.FLAME, x, y + 0.5, z, 40 + 20 * k, r / 2, 0.6, r / 2, 0.04);
                    lvl.playSound(null, at, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1f, 0.8f);
                }
                case "heal" -> {
                    double r = 5 + k;
                    for (LivingEntity m : lvl.getEntitiesOfClass(LivingEntity.class, box(x, y, z, r), KingdomAlchemy::ally)) {
                        m.heal(8 + 4 * k);
                        m.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (5 + (k == 2 ? 5 : 2 * k)) * 20, 0));
                    }
                    lvl.sendParticles(ParticleTypes.HEART, x, y + 0.8, z, 15 + 5 * k, r / 2, 0.6, r / 2, 0.02);
                    lvl.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 1.2f);
                }
                case "storm" -> {
                    double r = 3 + 0.5 * k;
                    float dmg = 6 + 2 * k;
                    var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(lvl);
                    if (bolt != null) {
                        bolt.moveTo(x, y, z);
                        bolt.setVisualOnly(true); // l'éclair est décoratif : les dégâts sont gérés ici (pas de feu)
                        lvl.addFreshEntity(bolt);
                    }
                    for (LivingEntity m : lvl.getEntitiesOfClass(LivingEntity.class, box(x, y, z, r), KingdomAlchemy::foe)) {
                        m.hurt(lvl.damageSources().lightningBolt(), dmg);
                        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                    }
                    lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.8, z, 40 + 20 * k, r / 2, 0.8, r / 2, 0.1);
                }
                case "bastion" -> {
                    double r = 6 + k;
                    int n = KingdomClaim.healArea(at, r, 0.15f + 0.10f * k);
                    for (LivingEntity m : lvl.getEntitiesOfClass(LivingEntity.class, box(x, y, z, r), KingdomAlchemy::ally)) {
                        m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, (6 + 2 * k) * 20, 0));
                    }
                    lvl.sendParticles(ParticleTypes.WAX_ON, x, y + 0.8, z, 40 + 20 * k, r / 2, 0.8, r / 2, 0.05);
                    lvl.playSound(null, at, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 1.4f);
                    if (tp.getOwner() instanceof ServerPlayer owner) {
                        owner.displayClientMessage(WSLang.c("kingdom.alchemy.bastion_done", n), true);
                    }
                }
                default -> { }
            }
        }
    }
}
