package com.wavesurvivor.shrine;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * EFFETS DES ÉQUIPEMENTS AMÉLIORÉS (Forge du Sanctuaire) : tout part des totaux de ShrineGear.totals (équipement porté, plafond de +30 %).
 * Les bonus d'attributs sont posés par ShrineGear.refresh ; ici les effets qui demandent un événement :
 * dégâts infligés (Châtiment, Bourreau, Marque de la Bête, vol de vie, Venin), dégâts reçus (Frénésie, Plume, Épines, Pacte de survie),
 * soins reçus, expérience, Lame bénie, et chaque demi-seconde : Souffle de vie et Bénédiction durable.
 */
public class ShrineGearEvents {

    /** Créatures des Corrompus : le Châtiment les frappe comme des morts-vivants. */
    private static final Set<String> CORRUPTED = Set.of("wavesurvivor:rootling", "wavesurvivor:stone_brute", "wavesurvivor:neantide");

    /** Durabilité vue au dernier passage, par joueur et par emplacement (pour « Bénédiction durable »). */
    private final Map<UUID, Map<String, Integer>> lastDamage = new HashMap<>();

    private static double t(Map<String, Double> totals, String key) { return totals.getOrDefault(key, 0.0); }

    // ─── Dégâts ───

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        DamageSource src = e.getSource();
        LivingEntity victim = e.getEntity();
        float amt = e.getAmount();

        // Le joueur frappe (corps à corps) : Châtiment, Bourreau, Marque de la Bête, vol de vie, Venin
        if (src.getEntity() instanceof ServerPlayer ap && src.getDirectEntity() == ap && victim != ap) {
            Map<String, Double> tt = ShrineGear.totals(ap);
            if (!tt.isEmpty()) {
                double mult = 1;
                ResourceLocation vid = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
                if (victim.getMobType() == MobType.UNDEAD || (vid != null && CORRUPTED.contains(vid.toString()))) mult += t(tt, "undead_dmg_pct");
                if (tt.containsKey("exec_bonus_pct")) {
                    boolean low = victim.getHealth() <= victim.getMaxHealth() * ShrineUpgrades.executionThreshold();
                    mult += low ? t(tt, "exec_bonus_pct") : t(tt, "exec_malus_pct");
                }
                if (tt.containsKey("night_dmg_pct") || tt.containsKey("day_dmg_pct")) {
                    mult += ap.level().isDay() ? t(tt, "day_dmg_pct") : t(tt, "night_dmg_pct");
                }
                amt *= (float) Math.max(0.1, mult);
                double steal = t(tt, "lifesteal_pct");
                if (steal > 0) ap.heal((float) (amt * steal));
                double poison = t(tt, "poison_seconds");
                if (poison > 0) victim.addEffect(new MobEffectInstance(MobEffects.POISON, (int) (poison * 20), 0));
                double hunger = t(tt, "hunger_per_hit");
                if (hunger > 0) ap.causeFoodExhaustion((float) hunger);
            }
        }

        // Le joueur est touché : Frénésie, Plume, Épines, Pacte de survie
        if (victim instanceof ServerPlayer vp) {
            vp.getPersistentData().putInt("ws_last_hurt", vp.tickCount);
            Map<String, Double> tt = ShrineGear.totals(vp);
            if (!tt.isEmpty()) {
                if (src.is(DamageTypes.FALL)) amt *= (float) (1 - Math.min(0.9, t(tt, "fall_reduction_pct")));
                amt *= (float) (1 + t(tt, "dmg_taken_pct"));
                double thorns = t(tt, "thorns_pct");
                if (thorns > 0 && src.getEntity() instanceof LivingEntity att && att != vp && !src.is(DamageTypes.THORNS)) {
                    att.hurt(vp.damageSources().thorns(vp), (float) (amt * thorns));
                }
                double pact = t(tt, "pact_minutes");
                if (pact > 0 && amt >= vp.getHealth()) {
                    long now = vp.level().getGameTime();
                    if (now >= vp.getPersistentData().getLong("ws_pact_until")) {
                        amt = Math.max(0, vp.getHealth() - 1);
                        vp.getPersistentData().putLong("ws_pact_until", now + (long) (pact * 60 * 20));
                        vp.level().playSound(null, vp.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 1.2f);
                        vp.displayClientMessage(Component.literal(WSLang.t("shrine.pact_used", (int) pact)), true);
                    }
                }
            }
        }
        e.setAmount(amt);
    }

    @SubscribeEvent
    public void onHeal(LivingHealEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        double h = t(ShrineGear.totals(p), "heal_received_pct");
        if (h != 0) e.setAmount((float) Math.max(0, e.getAmount() * (1 + h)));
    }

    @SubscribeEvent
    public void onXp(LivingExperienceDropEvent e) {
        if (!(e.getAttackingPlayer() instanceof ServerPlayer p)) return;
        double x = t(ShrineGear.totals(p), "xp_pct");
        if (x > 0) e.setDroppedExperience((int) Math.round(e.getDroppedExperience() * (1 + x)));
    }

    /** Lame bénie : tous les N ennemis tués, le porteur se soigne. */
    @SubscribeEvent
    public void onKill(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        Map<String, Double> tt = ShrineGear.totals(p);
        int need = (int) t(tt, "blade_kills");
        if (need <= 0) return;
        int n = p.getPersistentData().getInt("ws_blade_count") + 1;
        if (n >= need) {
            p.heal((float) t(tt, "blade_heal"));
            n = 0;
        }
        p.getPersistentData().putInt("ws_blade_count", n);
    }

    // ─── Équipement porté : attributs ───

    @SubscribeEvent
    public void onEquipment(LivingEquipmentChangeEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ShrineGear.refresh(p);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { ShrineGear.forget(p.getUUID()); ShrineGear.refresh(p); }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { ShrineGear.forget(p.getUUID()); ShrineGear.refresh(p); }
    }

    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { ShrineGear.forget(p.getUUID()); ShrineGear.refresh(p); }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ShrineGear.forget(e.getEntity().getUUID());
        lastDamage.remove(e.getEntity().getUUID());
    }

    // ─── Tous les 10 ticks : sécurité des attributs, Souffle de vie, Bénédiction durable ───

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        ShrineGear.refresh(p);
        Map<String, Double> tt = ShrineGear.totals(p);
        int tier = (int) t(tt, "regen_tier");
        if (tier > 0 && p.getHealth() < p.getMaxHealth() && p.tickCount - p.getPersistentData().getInt("ws_last_hurt") > ShrineUpgrades.outOfCombatTicks()) {
            p.heal(10f / ShrineUpgrades.regenInterval(tier)); // 1 PV par intervalle, réparti sur les passages de 10 ticks
        }
        durability(p);
    }

    /** Bénédiction durable : une part des points d'usure gagnés depuis le dernier passage est rendue (par objet). */
    private void durability(ServerPlayer p) {
        Map<String, Integer> prev = lastDamage.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        for (ItemStack st : ShrineGear.gearOf(p)) {
            if (!st.isDamageableItem()) continue;
            double pct = 0;
            for (ShrineGear.Applied a : ShrineGear.upgradesOf(st)) {
                ShrineUpgrades.Def d = ShrineUpgrades.get(a.id());
                if (d == null) continue;
                for (ShrineUpgrades.Fx fx : d.effects()) if (fx.key().equals("durability_pct")) pct += fx.at(a.tier());
            }
            if (pct <= 0) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
            String key = (id == null ? "?" : id.toString()) + "#" + (st == p.getMainHandItem() ? "main" : String.valueOf(p.getInventory().armor.indexOf(st)));
            int now = st.getDamageValue();
            Integer before = prev.get(key);
            if (before != null && now > before) {
                double gained = (now - before) * Math.min(0.9, pct);
                int refund = (int) gained + (p.getRandom().nextDouble() < gained - (int) gained ? 1 : 0);
                if (refund > 0) { now -= refund; st.setDamageValue(Math.max(0, now)); }
            }
            prev.put(key, now);
        }
    }
}
