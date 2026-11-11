package com.wavesurvivor.horde.kingdom.vocation;

import com.wavesurvivor.horde.kingdom.KingdomClaim;
import com.wavesurvivor.horde.kingdom.KingdomManager;
import com.wavesurvivor.horde.kingdom.KingdomRoles;
import com.wavesurvivor.horde.kingdom.KingdomTreasury;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/** VOCATIONS — petits effets déclenchés par des talents (Catalyseur fermé, foyer purifié, fin d'Assaut, réparation du Calme…). */
public final class VocationFx {

    private VocationFx() {}

    private static final KingdomRoles.Role ARC = KingdomRoles.Role.ARCANIST;

    /** Un Catalyseur vient d'être fermé : talents de l'Arcaniste (Résonance et Arcanes offensives), centrés sur lui. */
    public static void onCatalystClosed(MinecraftServer server) {
        ServerPlayer a = Voc.holder(ARC);
        if (a == null || !(a.level() instanceof ServerLevel lvl)) return;
        if (Vocations.has(a, ARC, "arcanist.resonance.shield_on_close")) {
            com.wavesurvivor.horde.benediction.AbsorptionLayers.give(a, com.wavesurvivor.horde.benediction.AbsorptionLayers.VOCATION, 8f, 200); // 4 cœurs d'absorption, 10 s (couche d'absorption, plus l'effet vanilla)
        }
        boolean knock = Vocations.has(a, ARC, "arcanist.offense.close_knockback");
        boolean bolt = Vocations.has(a, ARC, "arcanist.offense.close_lightning");
        if (!knock && !bolt) return;
        for (LivingEntity m : lvl.getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(knock ? 9 : 14), e -> e instanceof Enemy && e.isAlive())) {
            if (knock && m.distanceToSqr(a) <= 81) {
                Vec3 push = m.position().subtract(a.position()).multiply(1, 0, 1).normalize().scale(1.4);
                m.push(push.x, 0.5, push.z);
                m.hurtMarked = true;
            }
            if (bolt && m.getPersistentData().getBoolean("ws_elite")) {
                var b = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(lvl);
                if (b != null) {
                    b.moveTo(m.position());
                    b.setVisualOnly(true);
                    lvl.addFreshEntity(b);
                }
                m.hurt(lvl.damageSources().lightningBolt(), 16f);
            }
        }
        if (knock) lvl.sendParticles(ParticleTypes.CLOUD, a.getX(), a.getY() + 0.5, a.getZ(), 40, 3, 0.4, 3, 0.1);
    }

    // ── Zones temporaires (fiole incendiaire laissant du feu, zone sacrée d'un foyer purifié) ──
    private record Zone(ServerLevel lvl, net.minecraft.core.BlockPos pos, double r, long until, boolean holy, boolean weak) {}

    private static final java.util.List<Zone> ZONES = new java.util.ArrayList<>();

    public static void addZone(ServerLevel lvl, net.minecraft.core.BlockPos pos, double r, int seconds, boolean holy) {
        ZONES.add(new Zone(lvl, pos.immutable(), r, lvl.getServer().getTickCount() + seconds * 20L, holy, false));
    }

    public static void clear() {
        ZONES.clear();
        TASKS.clear();
        despawnMarket(KingdomManager.level());
        market = null;
        ALLIES.clear();
        essenceConverted = false;
        warChest = false;
        motherLode = false;
        lastStandUsed = false;
        captain = null;
        VocationEvents.reset();
    }

    /** Toutes les 0,5 s : le feu au sol brûle les monstres, la zone sacrée les ralentit. */
    public static void tickZones(long tick) {
        if (ZONES.isEmpty()) return;
        ZONES.removeIf(z -> tick >= z.until());
        for (Zone z : ZONES) {
            var c = net.minecraft.world.phys.Vec3.atBottomCenterOf(z.pos());
            z.lvl().sendParticles(z.holy() ? ParticleTypes.END_ROD : ParticleTypes.FLAME, c.x, c.y + 0.3, c.z, 6, z.r() / 2, 0.2, z.r() / 2, 0.01);
            if (tick % 20 != 0) continue;
            for (LivingEntity m : z.lvl().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(z.pos()).inflate(z.r(), 2, z.r()), e -> e instanceof Enemy && e.isAlive())) {
                if (z.weak()) {
                    m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, true, false));
                    m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true, false));
                } else if (z.holy()) m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, true, false));
                else {
                    m.setSecondsOnFire(3);
                    m.hurt(z.lvl().damageSources().onFire(), 2f);
                }
            }
        }
    }

    /** Un foyer vient d'être purifié : ressources (talent Purification) et soin des alliés autour de l'Arcaniste. */
    public static void onFocusPurified(MinecraftServer server, net.minecraft.core.BlockPos at) {
        ServerPlayer a = Voc.holder(ARC);
        if (a == null) return;
        int loot = (int) Vocations.total(a, ARC, "arcanist.purification.purify_loot");
        if (loot > 0) {
            KingdomTreasury.add(KingdomTreasury.Res.WOOD, loot);
            KingdomTreasury.add(KingdomTreasury.Res.STONE, loot);
        }
        if (Vocations.has(a, ARC, "arcanist.purification.purify_zone") && a.level() instanceof ServerLevel zl && at != null) {
            addZone(zl, at, 8, 40, true); // zone sacrée : monstres ralentis 40 s
        }
        if (Vocations.has(a, ARC, "arcanist.purification.purify_heal") && a.level() instanceof ServerLevel lvl) {
            for (LivingEntity m : lvl.getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(14),
                    e -> e.isAlive() && (e instanceof ServerPlayer || e instanceof com.wavesurvivor.entity.KingdomSoldier))) {
                m.heal(8f);
                lvl.sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + 1.2, m.getZ(), 6, 0.4, 0.5, 0.4, 0.02);
            }
        }
    }

    /** Début du Calme (Assaut survécu) : prime de l'Intendant, fiole offerte à l'Alchimiste, caisse de ferraille du Récupérateur. */
    public static void onCalmStart() {
        ServerPlayer alch = Voc.holder(KingdomRoles.Role.ALCHEMIST);
        if (alch != null && Vocations.has(alch, KingdomRoles.Role.ALCHEMIST, "alchemist.distillation.extra_flask")) {
            com.wavesurvivor.horde.kingdom.KingdomAlchemy.giveFree(alch);
        }
        ServerPlayer scav = Voc.holder(KingdomRoles.Role.SCAVENGER);
        if (scav != null && Vocations.has(scav, KingdomRoles.Role.SCAVENGER, "scavenger.recycling.scrap_crate")) {
            com.wavesurvivor.horde.kingdom.KingdomRoleExtras.scrapCrate(scav);
        }
        if (scav != null && Vocations.has(scav, KingdomRoles.Role.SCAVENGER, "scavenger.plunder.raider_chest")) com.wavesurvivor.horde.kingdom.KingdomRoleExtras.raiderChest(scav);
        essenceConverted = false;
        ServerPlayer qm = Voc.holder(KingdomRoles.Role.QUARTERMASTER);
        if (qm != null && Vocations.has(qm, KingdomRoles.Role.QUARTERMASTER, "quartermaster.ult.black_market") && qm.level() instanceof ServerLevel ql) spawnMarket(ql);
        warChest = scav != null && Vocations.has(scav, KingdomRoles.Role.SCAVENGER, "scavenger.ult.war_chest"); // ultime : doublent les débris du prochain Assaut
        motherLode = Voc.has(KingdomRoles.Role.MINER, "miner.ult.mother_lode"); // ultime : le 1er gisement du Calme sera un filon-mère

        float[] snap = com.wavesurvivor.altar.AltarDefense.snapshot();
        int bonus = (int) Voc.total(KingdomRoles.Role.QUARTERMASTER, "quartermaster.treasury.wave_bonus");
        if (bonus > 0 && snap != null && snap[1] > 0 && snap[0] / snap[1] > 0.75f) {
            KingdomTreasury.reward(KingdomTreasury.Res.MONEY, bonus, "voc.reason.wave_bonus");
        }
    }

    // ── Tâches différées, orbe arcanique, ultimes ──
    private record Task(long at, Runnable run) {}

    private static final java.util.List<Task> TASKS = new java.util.ArrayList<>();
    private static boolean warChest = false, motherLode = false, lastStandUsed = false;
    private static java.util.UUID captain = null;
    private static long captainUntil = 0;

    /** Exécute une action dans {@code ticks} ticks (Réaction en chaîne…). */
    public static void later(MinecraftServer server, int ticks, Runnable r) {
        TASKS.add(new Task(server.getTickCount() + ticks, r));
    }

    /** Ultime Trésor de guerre : les débris de cet Assaut sont doublés. */
    public static boolean warChestActive() { return warChest && KingdomManager.phase() == KingdomManager.Phase.ASSAULT; }

    /** Ultime Filon-mère : consomme le « premier gisement du Calme ». */
    public static boolean takeMotherLode() {
        if (!motherLode) return false;
        motherLode = false;
        return true;
    }

    /** Début d'un Assaut : ultimes de l'Arcaniste (Nexus) et du Commandant (Garde d'élite), Panacée rechargée. */
    public static void onAssaultStart(MinecraftServer server) {
        VocationEvents.onAssaultStart();
        despawnMarket(KingdomManager.level()); // le Marché noir repart avec l'Assaut
        warChest = Voc.has(KingdomRoles.Role.SCAVENGER, "scavenger.ult.war_chest") && warChest; // actif seulement après un Assaut repoussé
        ServerPlayer arc = Voc.holder(ARC);
        if (arc != null && Vocations.has(arc, ARC, "arcanist.ult.nexus") && KingdomManager.center() != null && arc.level() instanceof ServerLevel lvl) {
            addNexus(lvl, KingdomManager.center(), 22, 30);
            arc.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("voc.nexus"));
        }
        ServerPlayer cmd = Voc.holder(KingdomRoles.Role.COMMANDER);
        if (cmd != null && Vocations.has(cmd, KingdomRoles.Role.COMMANDER, "commander.ult.elite_guard")) {
            net.minecraft.world.entity.Mob c = com.wavesurvivor.horde.kingdom.KingdomDefenses.randomSoldier();
            if (c != null) {
                captain = c.getUUID();
                captainUntil = server.getTickCount() + (long) Math.max(5, Vocations.total(cmd, KingdomRoles.Role.COMMANDER, "commander.ult.elite_guard")) * 20L;
                c.setInvulnerable(true);
                c.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, true, false));
                cmd.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("voc.captain"));
            }
        }
    }

    private static void addNexus(ServerLevel lvl, net.minecraft.core.BlockPos pos, double r, int seconds) {
        ZONES.add(new Zone(lvl, pos.immutable(), r, lvl.getServer().getTickCount() + seconds * 20L, false, true));
    }

    /** Orbe arcanique (talent), fin du capitaine invincible, Dernier carré : toutes les 0,5 s. */
    public static void tickAll(MinecraftServer server) {
        long tick = server.getTickCount();
        for (java.util.Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (tick >= t.at()) {
                it.remove();
                try { t.run().run(); } catch (Exception ignored) { }
            }
        }
        tickZones(tick);
        ServerPlayer a = Voc.holder(ARC);
        if (a != null && Vocations.has(a, ARC, "arcanist.offense.arcane_orb") && a.level() instanceof ServerLevel lvl && a.isAlive()) {
            double ang = tick * 0.35;
            for (int i = 0; i < 3; i++) {
                double an = ang + i * Math.PI * 2 / 3;
                lvl.sendParticles(ParticleTypes.END_ROD, a.getX() + Math.cos(an) * 2.2, a.getY() + 1.2, a.getZ() + Math.sin(an) * 2.2, 1, 0, 0, 0, 0);
            }
            for (LivingEntity m : lvl.getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(3), x -> x instanceof Enemy && x.isAlive())) {
                m.hurt(lvl.damageSources().indirectMagic(a, a), 3f);
            }
        }
        if (tick % 20 == 0 && KingdomManager.phase() == KingdomManager.Phase.ASSAULT && !com.wavesurvivor.horde.kingdom.KingdomOmens.activeList().isEmpty()
                && Voc.has(KingdomRoles.Role.AUGUR, "augur.ult.living_omen")) { // ultime Présage vivant : bonus personnel pendant l'Assaut
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!p.isSpectator()) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
            }
        }
        if (!ALLIES.isEmpty()) {
            ServerLevel al = KingdomManager.level();
            for (java.util.Iterator<java.util.Map.Entry<java.util.UUID, Long>> it = ALLIES.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                var ae = al == null ? null : al.getEntity(en.getKey());
                if (ae == null || !ae.isAlive()) { it.remove(); continue; }
                if (tick >= en.getValue()) { ae.discard(); it.remove(); }
            }
        }
        if (captain != null) {
            ServerLevel lvl = KingdomManager.level();
            var c = lvl == null ? null : lvl.getEntity(captain);
            if (c == null || !c.isAlive() || tick >= captainUntil) {
                if (c != null) c.setInvulnerable(false);
                captain = null;
            }
        }
        if (!lastStandUsed && KingdomManager.phase() == KingdomManager.Phase.ASSAULT && Voc.has(KingdomRoles.Role.COMMANDER, "commander.ult.last_stand")) {
            float[] snap = com.wavesurvivor.altar.AltarDefense.snapshot();
            if (snap != null && snap[1] > 0 && snap[0] / snap[1] < 0.25f) {
                lastStandUsed = true;
                com.wavesurvivor.horde.kingdom.KingdomDefenses.reviveSoldiers();
                ServerPlayer cmd = Voc.holder(KingdomRoles.Role.COMMANDER);
                if (cmd != null) cmd.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("voc.last_stand"));
            }
        }
    }

    // ── Intendant, Récupérateur : marchands, conversions, recyclage, unités ralliées, Marché noir ──
    private static final java.util.Map<java.util.UUID, Long> ALLIES = new java.util.HashMap<>();
    private static java.util.UUID market = null;
    private static boolean essenceConverted = false;

    /** Talents Commerce : une offre en plus et/ou une offre rare chez chaque marchand (ajoutées une seule fois par marchand). */
    public static void addVillagerOffers(net.minecraft.world.entity.npc.AbstractVillager v) {
        if (v.getPersistentData().getBoolean("ws_voc_offers") || !KingdomRoles.present(KingdomRoles.Role.QUARTERMASTER)) return;
        boolean ex = Voc.has(KingdomRoles.Role.QUARTERMASTER, "quartermaster.trade.extra_offer");
        boolean rare = Voc.has(KingdomRoles.Role.QUARTERMASTER, "quartermaster.trade.rare_offer");
        if (!ex && !rare) return;
        net.minecraft.world.item.Item cur = com.wavesurvivor.altar.AltarDefense.currencyItem();
        var offers = v.getOffers();
        if (ex) offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.ItemStack(cur, 12),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 2), 4, 0, 0f));
        if (rare) offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.ItemStack(cur, 48),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING), 1, 0, 0f));
        v.getPersistentData().putBoolean("ws_voc_offers", true);
    }

    /** Intendant › Intendance : 0 Bois→Pierre, 1 Pierre→Fer, 2 Fer→Bois (20 pour 10) ; 3 Essence→pièces (2 pour 40, 1 fois par Calme). */
    public static void convert(ServerPlayer p, int kind) {
        if (!KingdomRoles.has(p, KingdomRoles.Role.QUARTERMASTER) || !KingdomTreasury.active()) return;
        if (kind < 3) {
            if (!Vocations.has(p, KingdomRoles.Role.QUARTERMASTER, "quartermaster.stewardship.convert")) return;
            KingdomTreasury.Res from = new KingdomTreasury.Res[]{KingdomTreasury.Res.WOOD, KingdomTreasury.Res.STONE, KingdomTreasury.Res.IRON}[kind];
            KingdomTreasury.Res to = new KingdomTreasury.Res[]{KingdomTreasury.Res.STONE, KingdomTreasury.Res.IRON, KingdomTreasury.Res.WOOD}[kind];
            if (!KingdomTreasury.has(from, 20)) {
                p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("voc.convert.no"), true);
                return;
            }
            KingdomTreasury.spend(from, 20);
            KingdomTreasury.add(to, 10);
        } else {
            if (!Vocations.has(p, KingdomRoles.Role.QUARTERMASTER, "quartermaster.stewardship.essence_to_coins")
                    || essenceConverted || KingdomManager.phase() != KingdomManager.Phase.CALM || !KingdomTreasury.has(KingdomTreasury.Res.ESSENCE, 2)) {
                p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("voc.convert.no"), true);
                return;
            }
            essenceConverted = true;
            KingdomTreasury.spend(KingdomTreasury.Res.ESSENCE, 2);
            KingdomTreasury.add(KingdomTreasury.Res.MONEY, 40);
        }
        KingdomTreasury.sync(p.getServer());
        p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("voc.convert.ok"), true);
    }

    /** Récupérateur › Recyclage : os, flèches, ficelle, plumes, poudre → Pierre ; chair pourrie, yeux d'araignée, cuir → Bois (1 pour 1). */
    public static void recycleItems(ServerPlayer p) {
        if (!KingdomRoles.has(p, KingdomRoles.Role.SCAVENGER) || !KingdomTreasury.active()
                || !Vocations.has(p, KingdomRoles.Role.SCAVENGER, "scavenger.recycling.recycle_items")) return;
        int stone = 0, wood = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            var it = st.getItem();
            if (it == net.minecraft.world.item.Items.BONE || it == net.minecraft.world.item.Items.ARROW || it == net.minecraft.world.item.Items.STRING
                    || it == net.minecraft.world.item.Items.FEATHER || it == net.minecraft.world.item.Items.GUNPOWDER) { stone += st.getCount(); inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY); }
            else if (it == net.minecraft.world.item.Items.ROTTEN_FLESH || it == net.minecraft.world.item.Items.SPIDER_EYE || it == net.minecraft.world.item.Items.LEATHER) { wood += st.getCount(); inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY); }
        }
        if (stone + wood == 0) {
            p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("voc.recycle.none"), true);
            return;
        }
        if (stone > 0) KingdomTreasury.add(KingdomTreasury.Res.STONE, stone);
        if (wood > 0) KingdomTreasury.add(KingdomTreasury.Res.WOOD, wood);
        KingdomTreasury.sync(p.getServer());
        p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("voc.recycle.items", stone, wood), true);
    }

    /** Récupérateur › Recyclage : l'équipement tenu en main (armure, outil, arme) devient de l'Essence (1, +1 par enchantement). */
    public static void recycleGear(ServerPlayer p) {
        if (!KingdomRoles.has(p, KingdomRoles.Role.SCAVENGER) || !KingdomTreasury.active()
                || !Vocations.has(p, KingdomRoles.Role.SCAVENGER, "scavenger.recycling.recycle_gear")) return;
        var st = p.getMainHandItem();
        if (st.isEmpty() || !st.isDamageableItem()) {
            p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("voc.recycle.no_gear"), true);
            return;
        }
        int ess = 1 + st.getEnchantmentTags().size();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        KingdomTreasury.reward(KingdomTreasury.Res.ESSENCE, ess, "voc.reason.recycle");
        KingdomTreasury.sync(p.getServer());
    }

    /** Pillage › Bélier apprivoisé / ultime Arsenal de siège : une unité de siège abattue peut rejoindre le royaume 60 s. */
    public static void siegeCapture(ServerLevel lvl, LivingEntity dead, String role) {
        ServerPlayer s = Voc.holder(KingdomRoles.Role.SCAVENGER);
        if (s == null) return;
        double ch = ("ram".equals(role) ? Vocations.total(s, KingdomRoles.Role.SCAVENGER, "scavenger.plunder.tame_ram") / 100.0 : 0)
                + (Vocations.has(s, KingdomRoles.Role.SCAVENGER, "scavenger.ult.siege_arsenal") ? 0.15 : 0);
        if (ch <= 0 || lvl.random.nextDouble() >= ch) return;
        var a = com.wavesurvivor.registry.ModEntities.KINGDOM_SOLDIER.get().create(lvl);
        if (a == null) return;
        a.moveTo(dead.getX(), dead.getY(), dead.getZ(), 0f, 0f);
        var hp = a.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(70);
        var atk = a.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(9);
        a.setHealth(a.getMaxHealth());
        a.setCustomName(Component.literal(com.wavesurvivor.i18n.WSLang.t("voc.ally.name")));
        a.setCustomNameVisible(true);
        if (lvl.addFreshEntity(a)) {
            ALLIES.put(a.getUUID(), lvl.getServer().getTickCount() + 1200L);
            s.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("voc.ally.joined"));
        }
    }

    /** Ultime Marché noir : un marchand spécial aux offres exceptionnelles apparaît près du Monolithe à chaque Calme. */
    private static void spawnMarket(ServerLevel lvl) {
        despawnMarket(lvl);
        var c = KingdomManager.center();
        if (c == null) return;
        var v = net.minecraft.world.entity.EntityType.VILLAGER.create(lvl);
        if (v == null) return;
        var at = lvl.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.offset(4, 0, 3));
        v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setCustomName(Component.literal(com.wavesurvivor.i18n.WSLang.t("voc.market.name")));
        v.setCustomNameVisible(true);
        v.getPersistentData().putBoolean("ws_voc_offers", true);
        net.minecraft.world.item.Item cur = com.wavesurvivor.altar.AltarDefense.currencyItem();
        var offers = v.getOffers();
        offers.clear();
        offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.ItemStack(cur, 32), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3), 3, 0, 0f));
        offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.ItemStack(cur, 48), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING), 2, 0, 0f));
        offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.ItemStack(cur, 56), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE), 1, 0, 0f));
        offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.ItemStack(cur, 40), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP, 2), 2, 0, 0f));
        if (lvl.addFreshEntity(v)) market = v.getUUID();
    }

    private static void despawnMarket(ServerLevel lvl) {
        if (market == null || lvl == null) { market = null; return; }
        var m = lvl.getEntity(market);
        if (m != null) m.discard();
        market = null;
    }

    /** Toutes les 5 s pendant le Calme : le Restaurateur répare légèrement toutes les constructions. */
    public static void tickCalm(long tick) {
        if (tick % 100 != 0 || KingdomManager.phase() != KingdomManager.Phase.CALM || KingdomManager.center() == null) return;
        if (!Voc.has(KingdomRoles.Role.BUILDER, "builder.restorer.auto_repair")) return;
        KingdomClaim.healArea(KingdomManager.center(), 1000, 0.02f);
    }
}
