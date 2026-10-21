package com.wavesurvivor.horde.benediction;

import com.wavesurvivor.horde.boss.BossManager;
import com.wavesurvivor.horde.spawn.HordeUnitGuard;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BÉNÉDICTIONS SPÉCIALES (1.8) : les effets qui ne sont pas de simples attributs. Un niveau de bénédiction peut porter une liste
 * « effects » (type + paramètres, dans blessings.json) ; chaque effet croît linéairement avec le niveau de la bénédiction chez le joueur.
 *
 *   execution            dégâts bonus contre les monstres sous un seuil de PV            (threshold, bonus)
 *   dawn_shield          absorption rechargée au début de chaque vague                    (absorption)
 *   magnet               attire objets et expérience au sol                               (radius)
 *   banner               les alliés proches infligent plus de dégâts                      (bonus, radius)
 *   blood_link           une part de vos soins est partagée avec les alliés proches        (share, radius)
 *   berserker            dégâts et vitesse d'attaque qui montent quand les PV baissent    (damage, attackSpeed)
 *   bastion              réduction de dégâts croissante tant qu'on reste immobile         (perSecond, maxReduction)
 *   projectile_reduction projectiles reçus réduits                                        (reduction)
 *   taunt                les monstres proches ont tendance à cibler le porteur             (radius, chance)
 *
 * La Provocation ne cible jamais un joueur protégé pendant son choix de bénédiction (BenedictionProtection garde toujours la priorité).
 */
public class BlessingEffects {

    /** Un effet possédé par un joueur, avec le niveau de sa bénédiction. */
    public record Owned(RogueUpgrade.Effect fx, int level) {}

    private static final UUID BERSERK_UUID = UUID.fromString("5b1e0f6a-2f3c-4a53-9c3a-7d9e1a4b7c11");
    /** Joueur → {x, y, z, ticks immobile}. */
    private static final Map<UUID, double[]> STILL = new HashMap<>();
    private static boolean sharing = false;

    // ─── Accès ───

    public static List<Owned> owned(ServerPlayer p, String type) {
        List<Owned> out = new ArrayList<>();
        Map<String, Integer> levels = RogueUpgradeManager.levels(p);
        if (levels.isEmpty()) return out;
        for (Map.Entry<String, Integer> e : levels.entrySet()) {
            RogueUpgrade u = RogueUpgradeRegistry.get(e.getKey());
            if (u == null || u.effects().isEmpty()) continue;
            for (RogueUpgrade.Effect fx : u.effects()) if (fx.type().equals(type)) out.add(new Owned(fx, e.getValue()));
        }
        return out;
    }

    private static double sum(List<Owned> l, String key) {
        double s = 0;
        for (Owned o : l) s += o.fx().p(key, 0) * o.level();
        return s;
    }

    private static double max(List<Owned> l, String key) {
        double m = 0;
        for (Owned o : l) m = Math.max(m, o.fx().p(key, 0));
        return m;
    }

    /** Texte d'un effet pour les cartes (écran de choix, catalogue). */
    public static String describe(RogueUpgrade.Effect fx) {
        return switch (fx.type()) {
            case "execution" -> WSLang.t("bless.fx.execution", pct(fx.p("bonus", 0)), pct(fx.p("threshold", 0)));
            case "dawn_shield" -> WSLang.t("bless.fx.dawn_shield", Math.round(fx.p("absorption", 0)));
            case "magnet" -> WSLang.t("bless.fx.magnet", Math.round(fx.p("radius", 0)));
            case "banner" -> WSLang.t("bless.fx.banner", pct(fx.p("bonus", 0)), Math.round(fx.p("radius", 12)));
            case "blood_link" -> WSLang.t("bless.fx.blood_link", pct(fx.p("share", 0)), Math.round(fx.p("radius", 16)));
            case "berserker" -> WSLang.t("bless.fx.berserker", pct(fx.p("damage", 0)), String.format(java.util.Locale.ROOT, "%.2f", fx.p("attackSpeed", 0)));
            case "bastion" -> WSLang.t("bless.fx.bastion", pct(fx.p("perSecond", 0)), pct(fx.p("maxReduction", 0)));
            case "projectile_reduction" -> WSLang.t("bless.fx.projectile_reduction", pct(fx.p("reduction", 0)));
            case "taunt" -> WSLang.t("bless.fx.taunt", Math.round(fx.p("radius", 16)));
            default -> fx.type();
        };
    }

    private static long pct(double v) { return Math.round(v * 100); }

    // ─── Dégâts : Exécution, Berserker, Bannière (infligés) ; Bastion, Projectiles (reçus) ───

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        if (e.getSource().getEntity() instanceof ServerPlayer a && victim != a) {
            double add = 0;
            if (victim instanceof Mob) {
                float maxHp = Math.max(1f, victim.getMaxHealth());
                double ratio = victim.getHealth() / maxHp;
                for (Owned o : owned(a, "execution")) if (ratio <= o.fx().p("threshold", 0)) add += o.fx().p("bonus", 0) * o.level();
            }
            List<Owned> ber = owned(a, "berserker");
            if (!ber.isEmpty()) add += missing(a) * sum(ber, "damage");
            double banner = 0;
            for (ServerPlayer o : a.serverLevel().players()) {
                if (!o.isAlive() || o.isSpectator()) continue;
                for (Owned b : owned(o, "banner")) {
                    double r = b.fx().p("radius", 12);
                    if (o.distanceToSqr(a) <= r * r) banner += b.fx().p("bonus", 0) * b.level();
                }
            }
            add += Math.min(0.6, banner);
            if (add > 0) e.setAmount((float) (e.getAmount() * (1 + add)));
        }
        if (victim instanceof ServerPlayer v) {
            double red = 0;
            List<Owned> bast = owned(v, "bastion");
            if (!bast.isEmpty()) {
                double seconds = stillTicks(v) / 20.0;
                for (Owned o : bast) red += Math.min(o.fx().p("maxReduction", 0) * o.level(), o.fx().p("perSecond", 0) * o.level() * seconds);
            }
            if (e.getSource().is(DamageTypeTags.IS_PROJECTILE)) red += sum(owned(v, "projectile_reduction"), "reduction");
            if (red > 0) e.setAmount((float) (e.getAmount() * (1 - Math.min(0.75, red))));
        }
    }

    /** Part de PV manquante (0 = en pleine forme, 1 = presque mort). */
    private static double missing(LivingEntity p) {
        return Math.max(0, Math.min(1, 1 - p.getHealth() / Math.max(1f, p.getMaxHealth())));
    }

    // ─── Lien de sang ───

    @SubscribeEvent
    public void onHeal(LivingHealEvent e) {
        if (sharing || !(e.getEntity() instanceof ServerPlayer p) || e.getAmount() <= 0) return;
        List<Owned> link = owned(p, "blood_link");
        if (link.isEmpty()) return;
        double share = Math.min(0.8, sum(link, "share"));
        double radius = Math.max(4, max(link, "radius"));
        sharing = true;
        try {
            for (ServerPlayer o : p.serverLevel().players()) {
                if (o == p || !o.isAlive() || o.isSpectator() || o.distanceToSqr(p) > radius * radius) continue;
                o.heal((float) (e.getAmount() * share));
            }
        } finally {
            sharing = false;
        }
    }

    // ─── Aube : bouclier d'absorption au début de chaque vague ───

    public static void onWaveStart(MinecraftServer srv) {
        if (srv == null) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            double a = sum(owned(p, "dawn_shield"), "absorption");
            if (a <= 0) continue;
            p.setAbsorptionAmount((float) Math.max(p.getAbsorptionAmount(), a));
            p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.4, 0.6, 0.4, 0.02);
            p.displayClientMessage(Component.literal(WSLang.t("bless.dawn_up", Math.round(a / 2))), true);
        }
    }

    // ─── Tick : Aimant, Berserker (vitesse d'attaque), Bastion, Provocation ───

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        long now = e.getServer().getTickCount();
        if (now % 5 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator()) { STILL.remove(p.getUUID()); continue; }
            boolean any = !RogueUpgradeManager.levels(p).isEmpty();
            if (!any) {
                if (STILL.remove(p.getUUID()) != null || p.getAttribute(Attributes.ATTACK_SPEED).getModifier(BERSERK_UUID) != null) clearBerserk(p);
                continue;
            }
            trackStill(p);
            magnet(p);
            if (now % 20 == 0) berserk(p);
            if (now % 40 == 0) taunt(p);
        }
    }

    private static void trackStill(ServerPlayer p) {
        double[] s = STILL.computeIfAbsent(p.getUUID(), k -> new double[]{p.getX(), p.getY(), p.getZ(), 0});
        double dx = p.getX() - s[0], dy = p.getY() - s[1], dz = p.getZ() - s[2];
        if (dx * dx + dy * dy + dz * dz > 0.04) { s[0] = p.getX(); s[1] = p.getY(); s[2] = p.getZ(); s[3] = 0; }
        else s[3] += 5;
    }

    private static double stillTicks(ServerPlayer p) {
        double[] s = STILL.get(p.getUUID());
        return s == null ? 0 : s[3];
    }

    private static void magnet(ServerPlayer p) {
        List<Owned> m = owned(p, "magnet");
        if (m.isEmpty()) return;
        double r = Math.min(14, sum(m, "radius"));
        if (r <= 0) return;
        AABB box = p.getBoundingBox().inflate(r);
        Vec3 target = p.position().add(0, 0.6, 0);
        for (ItemEntity it : p.level().getEntitiesOfClass(ItemEntity.class, box, i -> !i.hasPickUpDelay() && i.getAge() > 10)) pull(it, target);
        for (ExperienceOrb xp : p.level().getEntitiesOfClass(ExperienceOrb.class, box)) pull(xp, target);
    }

    private static void pull(net.minecraft.world.entity.Entity en, Vec3 target) {
        Vec3 d = target.subtract(en.position());
        if (d.lengthSqr() < 0.25) return;
        en.setDeltaMovement(d.normalize().scale(0.3));
        en.hurtMarked = true;
    }

    private static void berserk(ServerPlayer p) {
        AttributeInstance as = p.getAttribute(Attributes.ATTACK_SPEED);
        if (as == null) return;
        if (as.getModifier(BERSERK_UUID) != null) as.removeModifier(BERSERK_UUID);
        List<Owned> ber = owned(p, "berserker");
        if (ber.isEmpty()) return;
        double bonus = missing(p) * sum(ber, "attackSpeed");
        if (bonus > 0.001) as.addTransientModifier(new AttributeModifier(BERSERK_UUID, "bless_berserker", bonus, AttributeModifier.Operation.ADDITION));
    }

    private static void clearBerserk(ServerPlayer p) {
        AttributeInstance as = p.getAttribute(Attributes.ATTACK_SPEED);
        if (as != null && as.getModifier(BERSERK_UUID) != null) as.removeModifier(BERSERK_UUID);
    }

    /** Provocation : les monstres de la horde proches ont une chance de cibler le porteur (jamais s'il est protégé ou hors jeu). */
    private static void taunt(ServerPlayer p) {
        List<Owned> t = owned(p, "taunt");
        if (t.isEmpty() || p.isCreative() || BenedictionProtection.isProtected(p)) return;
        double radius = max(t, "radius");
        double chance = Math.min(0.9, sum(t, "chance"));
        ServerLevel level = p.serverLevel();
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(radius), x -> x.isAlive())) {
            if (!HordeUnitGuard.isHordeEntity(m) || BossManager.isBoss(m.getUUID())) continue;
            LivingEntity cur = m.getTarget();
            if (cur == p) continue;
            if (cur instanceof ServerPlayer cp && BenedictionProtection.isProtected(cp)) continue; // il ne les voit pas : on n'y touche pas
            if (cur == null || level.random.nextDouble() < chance) m.setTarget(p);
        }
    }
}
