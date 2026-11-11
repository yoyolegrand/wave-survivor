package com.wavesurvivor.craft;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * EFFETS DE L'ÉQUIPEMENT de l'Artisanat sacré et profane : Lame de l'aube, Faux de la souillure (balayage, vol de vie, coup manqué),
 * flèches bénies et souillées, Totem de grâce et Totem de souillure (remplacent la mort quand on les tient en main).
 * L'état (phases du Totem de souillure) n'est pas sauvegardé : il disparaît à la déconnexion et à la mort.
 */
public class CraftEquipEvents {

    public static final String ARROW_TAG = "ws_arrow";
    private static final Set<String> CORRUPTED = Set.of("wavesurvivor:rootling", "wavesurvivor:stone_brute", "wavesurvivor:neantide");
    private static final UUID TAINT_HP = UUID.fromString("0b7c2f4e-3a19-4c6d-8e55-7d1a9f6b2c30");

    /** Balayage de la Faux en cours (évite que les dégâts de balayage ne déclenchent un nouveau balayage). */
    private static boolean sweeping;
    private static final Map<UUID, Long> LAST_HIT = new HashMap<>(), PENDING_MISS = new HashMap<>(), LAST_MINING = new HashMap<>();

    /** Totem de souillure : fin du bonus de dégâts, fin du malus de PV max (temps de jeu) et malus déjà appliqué. */
    private static final class TaintState {
        long bonusEnd, penaltyEnd;
        boolean penaltyApplied;
    }
    private static final Map<UUID, TaintState> TAINT = new HashMap<>();

    /** Lame de l aube : bloc de lumière invisible posé près du porteur la nuit (un seul par joueur, retiré dès que possible). */
    private record Glow(ServerLevel level, net.minecraft.core.BlockPos pos) {}
    private static final Map<UUID, Glow> GLOW = new HashMap<>();
    private static final int GLOW_LEVEL = 10;

    private static void removeGlow(UUID id) {
        Glow g = GLOW.remove(id);
        if (g != null && g.level().getBlockState(g.pos()).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
            g.level().setBlock(g.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void updateGlow(ServerPlayer p) {
        boolean want = holds(p, ModItems.DAWN_BLADE.get()) && p.level().getDayTime() % 24000L >= 13000L && p.isAlive();
        Glow cur = GLOW.get(p.getUUID());
        if (!want) { if (cur != null) removeGlow(p.getUUID()); return; }
        net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
        if (cur != null && cur.level() == p.level() && cur.pos().equals(at)) return;
        net.minecraft.world.level.block.state.BlockState st = p.level().getBlockState(at);
        if (!st.isAir()) return; // pas d air à cet endroit : on garde l ancienne lumière
        if (cur != null) removeGlow(p.getUUID());
        p.level().setBlock(at, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, GLOW_LEVEL), 3);
        GLOW.put(p.getUUID(), new Glow((ServerLevel) p.level(), at));
    }

    private static final java.util.List<net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>>> UNDEAD_TAGS = java.util.List.of(
            entityTag("wavesurvivor", "undead"), entityTag("forge", "undead"), entityTag("c", "undead"), entityTag("minecraft", "undead"));

    private static net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> entityTag(String ns, String path) {
        return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, new ResourceLocation(ns, path));
    }

    /**
     * Mort-vivant ou Corrompu, y compris les monstres d'autres mods : type « mort-vivant » du jeu, tags d'entité
     * (wavesurvivor:undead — modifiable par datapack —, forge:undead, c:undead, minecraft:undead), identifiants Corrompus, ou mot-clé
     * du nom de l'entité (config « equipment.undeadNameHints »).
     */
    public static boolean undeadOrCorrupted(LivingEntity v) {
        if (v.getMobType() == MobType.UNDEAD || v instanceof net.minecraft.world.entity.monster.Zombie || v instanceof net.minecraft.world.entity.monster.AbstractSkeleton) return true;
        for (net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> t : UNDEAD_TAGS) if (v.getType().is(t)) return true;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(v.getType());
        if (id == null) return false;
        if (CORRUPTED.contains(id.toString())) return true;
        for (String hint : CraftConfig.equipStrings("undeadNameHints")) if (!hint.isBlank() && id.getPath().contains(hint)) return true;
        return false;
    }

    /** Les quatre pièces de l'Armure du Séraphin sont portées. */
    public static boolean fullSeraph(ServerPlayer p) {
        for (ItemStack a : p.getInventory().armor) if (!(a.getItem() instanceof com.wavesurvivor.item.SeraphArmorItem)) return false;
        return true;
    }

    private static boolean holds(ServerPlayer p, Item item) { return p.getMainHandItem().is(item); }

    // ─── Dégâts : Lame, Faux, flèches, bonus du Totem de souillure ───

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        DamageSource src = e.getSource();
        LivingEntity victim = e.getEntity();
        float amt = e.getAmount();

        if (src.getEntity() instanceof ServerPlayer ap && src.getDirectEntity() == ap && victim != ap) {
            if (holds(ap, ModItems.DAWN_BLADE.get()) && undeadOrCorrupted(victim)) {
                amt *= 1f + (float) CraftConfig.equipNum("dawn_blade", "undeadBonusPercent", 50) / 100f;
                if (ap.level() instanceof ServerLevel sl) sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, victim.getX(), victim.getY(0.6), victim.getZ(), 6, 0.25, 0.3, 0.25, 0.05);
                ap.displayClientMessage(Component.literal(WSLang.t("craft.item.dawn_bonus", Math.round(amt))), true);
            }
            if (holds(ap, ModItems.TAINT_SCYTHE.get())) {
                ap.heal(amt * (float) CraftConfig.equipNum("taint_scythe", "lifestealPercent", 5) / 100f);
                if (!sweeping) sweep(ap, victim, amt);
            }
        }

        if (src.getDirectEntity() instanceof AbstractArrow arrow && !arrow.level().isClientSide) {
            String kind = arrow.getPersistentData().getString(ARROW_TAG);
            if (kind.equals("blessed") && undeadOrCorrupted(victim)) {
                victim.setSecondsOnFire((int) CraftConfig.equipNum("blessed_arrow", "fireSeconds", 5));
                var mv = arrow.getDeltaMovement();
                victim.knockback(CraftConfig.equipNum("blessed_arrow", "knockback", 1.5), -mv.x, -mv.z);
            } else if (kind.equals("tainted")) {
                victim.addEffect(new MobEffectInstance(MobEffects.WITHER, (int) CraftConfig.equipNum("tainted_arrow", "witherSeconds", 4) * 20, 0));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) CraftConfig.equipNum("tainted_arrow", "slownessSeconds", 4) * 20, 0));
            }
        }

        // Bénédiction du Séraphin (les 4 pièces) : moins de dégâts des morts-vivants et des Corrompus
        if (victim instanceof ServerPlayer vp && src.getEntity() instanceof LivingEntity att && undeadOrCorrupted(att) && fullSeraph(vp)) {
            amt *= 1f - (float) CraftConfig.equipNum("seraph_set", "undeadReductionPercent", 15) / 100f;
        }

        if (src.getEntity() instanceof ServerPlayer tp) {
            TaintState st = TAINT.get(tp.getUUID());
            if (st != null && tp.level().getGameTime() < st.bonusEnd) amt *= 1f + (float) CraftConfig.equipNum("taint_totem", "damageBonusPercent", 50) / 100f;
        }
        e.setAmount(amt);
    }

    /** Lame de l'aube : tuer un mort-vivant (ou un Corrompu) au corps à corps donne Force I pendant quelques secondes. */
    @SubscribeEvent
    public void onUndeadKill(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getSource().getDirectEntity() != p || e.getEntity() == p) return;
        if (!holds(p, ModItems.DAWN_BLADE.get()) || !undeadOrCorrupted(e.getEntity())) return;
        int secs = (int) CraftConfig.equipNum("dawn_blade", "killStrengthSeconds", 5);
        if (secs <= 0) return;
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, secs * 20, 0));
        if (p.level() instanceof ServerLevel sl) sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY(1.0), p.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
    }

    /** Balayage large de la Faux : les ennemis proches de la victime reçoivent une part des dégâts. */
    private static void sweep(ServerPlayer p, LivingEntity victim, float amt) {
        double r = CraftConfig.equipNum("taint_scythe", "sweepRadius", 3);
        float part = amt * (float) CraftConfig.equipNum("taint_scythe", "sweepPercent", 60) / 100f;
        sweeping = true;
        try {
            for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(r),
                    x -> x != victim && x != p && x instanceof Enemy && x.isAlive())) {
                t.hurt(p.damageSources().playerAttack(p), part);
            }
        } finally {
            sweeping = false;
        }
        if (p.level() instanceof ServerLevel sl) sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY(0.5), victim.getZ(), 1, 0, 0, 0, 0);
    }

    // ─── Faux : coup manqué ───

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) LAST_HIT.put(p.getUUID(), p.level().getGameTime());
    }

    /** Un bloc cassé (récolte, feuilles, herbe…) n'est pas un coup manqué : on note le tick pour ignorer le balancement qui l'accompagne. */
    @SubscribeEvent
    public void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p) LAST_MINING.put(p.getUUID(), p.level().getGameTime());
    }

    /**
     * Appelé quand le joueur balance la Faux : si aucune entité n'est touchée (et qu'aucun bloc n'est cassé), le coup est compté comme manqué au tick suivant.
     * Un balancement déjà en cours (bouton maintenu sur un bloc) ne compte pas une 2e fois.
     */
    static void onScytheSwing(ServerPlayer p) {
        if (p.swinging) return;
        PENDING_MISS.put(p.getUUID(), p.level().getGameTime());
    }

    private static final UUID SERAPH_HP = UUID.fromString("7c1a5f20-5a0e-4d51-9a11-0e5a1f0000a1");

    /** Ensemble complet du Séraphin : +2 cœurs de PV max (config « seraph_set.bonusHearts »), retirés dès qu'une pièce manque. */
    private static void updateSeraphHearts(ServerPlayer p) {
        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        boolean full = fullSeraph(p);
        double bonus = CraftConfig.equipNum("seraph_set", "bonusHearts", 2) * 2.0;
        AttributeModifier cur = hp.getModifier(SERAPH_HP);
        if (full && bonus > 0) {
            if (cur != null && cur.getAmount() == bonus) return;
            if (cur != null) hp.removeModifier(SERAPH_HP);
            hp.addTransientModifier(new AttributeModifier(SERAPH_HP, "wavesurvivor_seraph_hearts", bonus, AttributeModifier.Operation.ADDITION));
        } else if (cur != null) {
            hp.removeModifier(SERAPH_HP);
            if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        }
    }

    // ─── Tick : coup manqué et phases du Totem de souillure ───

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        long now = p.level().getGameTime();
        if (now % 4 == 0) updateGlow(p);
        if (now % 10 == 0) updateSeraphHearts(p);
        if (now % 8 == 0 && p.getMainHandItem().is(ModItems.DAWN_BLADE.get()) && p.level().getDayTime() % 24000L >= 13000L && p.level() instanceof ServerLevel nl) {
            nl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY(1.0), p.getZ(), 2, 0.4, 0.5, 0.4, 0.01); // la Lame de l'aube brille la nuit
        }
        Long swing = PENDING_MISS.get(p.getUUID());
        if (swing != null && now > swing) {
            PENDING_MISS.remove(p.getUUID());
            Long hit = LAST_HIT.get(p.getUUID());
            Long mine = LAST_MINING.get(p.getUUID());
            boolean touched = (hit != null && Math.abs(hit - swing) <= 1) || (mine != null && Math.abs(mine - swing) <= 1);
            float cost = (float) CraftConfig.equipNum("taint_scythe", "missCost", 1);
            if (!touched && holds(p, ModItems.TAINT_SCYTHE.get()) && p.getHealth() > cost + 1) {
                p.hurt(p.damageSources().magic(), cost);
                p.displayClientMessage(Component.literal(WSLang.t("craft.item.scythe_miss")), true);
            }
        }
        TaintState st = TAINT.get(p.getUUID());
        if (st != null) {
            if (!st.penaltyApplied && now >= st.bonusEnd) {
                st.penaltyApplied = true;
                double cap = CraftConfig.equipNum("taint_totem", "maxHealth", 8);
                AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) {
                    hp.removeModifier(TAINT_HP);
                    double cut = hp.getValue() - cap;
                    if (cut > 0) hp.addTransientModifier(new AttributeModifier(TAINT_HP, "wavesurvivor_taint_totem", -cut, AttributeModifier.Operation.ADDITION));
                    if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
                }
            }
            if (now >= st.penaltyEnd) clearTaint(p);
        }
    }

    private static void clearTaint(ServerPlayer p) {
        TAINT.remove(p.getUUID());
        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.removeModifier(TAINT_HP);
    }

    // ─── Totems : remplacent la mort ───

    /** Dégâts mortels : un Totem de grâce ou de souillure tenu en main annule les dégâts et ramène à la vie. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.isCanceled()) return;
        if (e.getAmount() < p.getHealth() || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack st = p.getItemInHand(h);
            boolean grace = st.is(ModItems.GRACE_TOTEM.get()), taint = st.is(ModItems.TAINT_TOTEM.get());
            if (!grace && !taint) continue;
            st.shrink(1);
            e.setCanceled(true);
            p.removeAllEffects();
            if (grace) reviveGrace(p); else reviveTaint(p);
            p.level().broadcastEntityEvent(p, (byte) 35);
            return;
        }
    }

    private static void reviveGrace(ServerPlayer p) {
        p.setHealth(1.0f);
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        com.wavesurvivor.horde.benediction.AbsorptionLayers.give(p, com.wavesurvivor.horde.benediction.AbsorptionLayers.GRACE_TOTEM, 8f, 100);   // 4 cœurs, 5 s (couche d'absorption, plus l'effet vanilla)
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        double r = CraftConfig.equipNum("grace_totem", "allyRadius", 6);
        float heal = (float) CraftConfig.equipNum("grace_totem", "allyHeal", 8);
        for (ServerPlayer ally : p.level().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(r), x -> x != p && x.isAlive())) {
            ally.heal(heal);
            ((ServerLevel) p.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, ally.getX(), ally.getY(1.6), ally.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
        }
    }

    private static void reviveTaint(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        long now = p.level().getGameTime();
        TaintState st = new TaintState();
        st.bonusEnd = now + (long) CraftConfig.equipNum("taint_totem", "bonusSeconds", 30) * 20;
        st.penaltyEnd = st.bonusEnd + (long) CraftConfig.equipNum("taint_totem", "penaltySeconds", 120) * 20;
        TAINT.put(p.getUUID(), st);
        p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4f, 1.6f);
    }

    // ─── Nettoyage ───

    /** Arrêt du serveur : plus aucun bloc de lumière ne doit rester dans le monde. */
    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent e) {
        for (UUID id : new java.util.ArrayList<>(GLOW.keySet())) removeGlow(id);
    }

    @SubscribeEvent
    public void onDeathGlow(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) removeGlow(p.getUUID());
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        TAINT.remove(e.getEntity().getUUID());
        AttributeInstance hp = e.getEntity().getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.removeModifier(TAINT_HP);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            removeGlow(p.getUUID());
            clearTaint(p);
            PENDING_MISS.remove(p.getUUID());
            LAST_HIT.remove(p.getUUID());
            LAST_MINING.remove(p.getUUID());
        }
    }
}
