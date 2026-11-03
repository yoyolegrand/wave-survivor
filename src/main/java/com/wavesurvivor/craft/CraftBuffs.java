package com.wavesurvivor.craft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ÉTAT DES NOURRITURES SPÉCIALES (Artisanat sacré et profane). GARDE-FOU : un seul effet de nourriture spéciale à la fois,
 * en manger une 2e remplace la 1re (ses effets de potion, son bonus de dégâts, sa perte de PV max et son blocage de
 * régénération sont retirés). L'état n'est pas sauvegardé : il disparaît à la déconnexion et à la mort.
 */
public final class CraftBuffs {

    private static final UUID HP_MODIFIER = UUID.fromString("5d1c6f0a-7b0e-4d1f-9a61-0c8f3b7a2e41");

    /** Effet de potion en attente (délai avant application). */
    private static final class Pending {
        final MobEffectInstance effect; final long at;
        Pending(MobEffectInstance e, long at) { this.effect = e; this.at = at; }
    }

    private static final class Active {
        String food;
        long end;                       // fin du dernier effet ou buff (temps de jeu)
        long buffEnd;                   // fin du bonus de dégâts / malus de PV / blocage de régénération
        int damagePct;
        boolean noRegen, hpModified;
        final List<MobEffect> applied = new ArrayList<>();
        final List<Pending> pending = new ArrayList<>();
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    /** Aliment spécial actuellement actif pour ce joueur (null si aucun). */
    public static String activeFood(Player p) {
        Active a = ACTIVE.get(p.getUUID());
        return a == null ? null : a.food;
    }

    /** Applique les effets d'un aliment spécial et remplace ceux de l'aliment précédent. */
    public static void apply(ServerPlayer p, String foodId, CraftConfig.Food f) {
        clear(p);
        long now = p.level().getGameTime();
        Active a = new Active();
        a.food = foodId;
        a.end = now;
        // Soin de l'effet négatif (Pomme sanctifiée)
        for (int i = 0; i < f.cleanse(); i++) {
            for (MobEffectInstance inst : new ArrayList<>(p.getActiveEffects())) {
                if (inst.getEffect().getCategory() == MobEffectCategory.HARMFUL) { p.removeEffect(inst.getEffect()); break; }
            }
        }
        for (CraftConfig.FoodEffect fe : f.effects()) {
            MobEffect eff = BuiltInRegistries.MOB_EFFECT.get(fe.effect());
            if (eff == null) continue;
            MobEffectInstance inst = new MobEffectInstance(eff, fe.seconds() * 20, fe.amplifier(), false, true, true);
            long at = now + fe.delay() * 20L;
            if (fe.delay() <= 0) {
                p.addEffect(inst);
                a.applied.add(eff);
            } else {
                a.pending.add(new Pending(inst, at));
            }
            a.end = Math.max(a.end, at + fe.seconds() * 20L);
        }
        if (f.hasBuff()) {
            a.buffEnd = now + f.buffSeconds() * 20L;
            a.end = Math.max(a.end, a.buffEnd);
            a.damagePct = f.damageBonusPercent();
            a.noRegen = f.noRegen();
            if (f.maxHealthLoss() > 0) {
                AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) {
                    hp.removeModifier(HP_MODIFIER);
                    hp.addTransientModifier(new AttributeModifier(HP_MODIFIER, "wavesurvivor_craft_food", -f.maxHealthLoss(), AttributeModifier.Operation.ADDITION));
                    a.hpModified = true;
                    if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
                }
            }
        }
        ACTIVE.put(p.getUUID(), a);
    }

    /** Retire tout ce que l'aliment spécial actif avait donné (remplacement, fin, mort, déconnexion). */
    public static void clear(ServerPlayer p) {
        Active a = ACTIVE.remove(p.getUUID());
        if (a == null) return;
        for (MobEffect e : a.applied) p.removeEffect(e);
        if (a.hpModified) {
            AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.removeModifier(HP_MODIFIER);
        }
    }

    // ─── Événements ───

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        Active a = ACTIVE.get(p.getUUID());
        if (a == null) return;
        long now = p.level().getGameTime();
        if (!a.pending.isEmpty()) {
            a.pending.removeIf(pe -> {
                if (now < pe.at) return false;
                p.addEffect(pe.effect);
                a.applied.add(pe.effect.getEffect());
                return true;
            });
        }
        if (a.buffEnd > 0 && now >= a.buffEnd) {
            a.damagePct = 0;
            a.noRegen = false;
            a.buffEnd = 0;
            if (a.hpModified) {
                AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) hp.removeModifier(HP_MODIFIER);
                a.hpModified = false;
            }
        }
        if (now >= a.end && a.pending.isEmpty()) ACTIVE.remove(p.getUUID());
    }

    /** Bonus de dégâts du Vin noir et du Cœur cru. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer p) {
            Active a = ACTIVE.get(p.getUUID());
            if (a != null && a.damagePct != 0) e.setAmount(e.getAmount() * (1f + a.damagePct / 100f));
        }
    }

    /** Cœur cru : aucune régénération NATURELLE (celle de la jauge de faim) ; les soins de potions, festin, etc. restent permis. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHeal(LivingHealEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Active a = ACTIVE.get(p.getUUID());
        if (a == null || !a.noRegen) return;
        boolean natural = StackWalker.getInstance().walk(s -> s.anyMatch(fr -> fr.getClassName().endsWith(".FoodData")));
        if (natural) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) clear(p);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) clear(p);
    }

    /** Arrêt du serveur / nouvelle partie : plus aucun état. */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        ACTIVE.remove(e.getEntity().getUUID());
    }
}
