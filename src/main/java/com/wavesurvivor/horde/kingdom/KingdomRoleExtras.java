package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.horde.boss.BossManager;
import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * RÔLES « AVANCÉS » (phase 4a) :
 *  - COMMANDANT : +1 soldat par baraquement (KingdomDefenses) ; Cor de ralliement → bannière plantée là où il vise,
 *    les soldats sans cible s'y rendent pendant 60 s (sneak + clic droit : rappel) ;
 *  - RÉCUPÉRATEUR : les unités de siège tuées et les brèches du Calme qui se referment lâchent du Bois et du Fer que
 *    lui seul peut ramasser (crédités au trésor) ; contrepartie : plus d'unités de siège (KingdomManager) ;
 *  - CHASSEUR DE PRIMES : un contrat à chaque Calme (élites, unités de siège ou monstres à tuer avant le Calme suivant),
 *    payé en Monnaie + Essence ; contrepartie : 10 % des monstres deviennent des ÉLITES (+60 % PV, lueur).
 */
public final class KingdomRoleExtras {

    private KingdomRoleExtras() {}

    private static final Random RNG = new Random();

    // ═══ Commandant : ralliement ═══

    /** Durée de la bannière : base du rôle + talent Ralliement. */
    public static int rallySeconds() {
        return (int) (com.wavesurvivor.horde.kingdom.vocation.Voc.base(KingdomRoles.Role.COMMANDER, "bannerSeconds", 40)
                + com.wavesurvivor.horde.kingdom.vocation.Voc.total(KingdomRoles.Role.COMMANDER, "commander.rally.banner_time"));
    }
    /** Une bannière de ralliement : point, bannière posée (retirée à la fin) et fin de validité. */
    private static final class Flag {
        BlockPos pos, banner;
        long until;
    }

    /** Bannières actives (1 ; 2 avec le talent Ralliement › double bannière). */
    private static final java.util.List<Flag> FLAGS = new java.util.ArrayList<>();

    /** Point de ralliement actif (null = aucun). */
    public static BlockPos rallyPoint() {
        java.util.List<BlockPos> all = rallyPoints();
        return all.isEmpty() ? null : all.get(0);
    }

    /** Tous les points de ralliement actifs (les bannières expirées sont retirées au passage). */
    public static java.util.List<BlockPos> rallyPoints() {
        ServerLevel lvl = KingdomManager.level();
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        if (lvl == null) return out;
        long now = lvl.getServer().getTickCount();
        for (java.util.Iterator<Flag> it = FLAGS.iterator(); it.hasNext(); ) {
            Flag f = it.next();
            if (now >= f.until) { removeBanner(lvl, f); it.remove(); } else out.add(f.pos);
        }
        return out;
    }

    /** Point de ralliement le plus proche d'une position (null = aucun). */
    public static BlockPos nearestRally(BlockPos from) {
        BlockPos best = null;
        for (BlockPos p : rallyPoints()) if (best == null || p.distSqr(from) < best.distSqr(from)) best = p;
        return best;
    }

    private static void removeBanner(ServerLevel lvl, Flag f) {
        if (f.banner != null && lvl != null && lvl.getBlockState(f.banner).is(Blocks.BLUE_BANNER)) lvl.removeBlock(f.banner, false);
    }

    public static void setRally(ServerLevel lvl, BlockPos pos, ServerPlayer by) {
        rallyPoints(); // retire les bannières expirées
        int max = com.wavesurvivor.horde.kingdom.vocation.Vocations.has(by, KingdomRoles.Role.COMMANDER, "commander.rally.double_banner") ? 2 : 1;
        while (FLAGS.size() >= max) { removeBanner(lvl, FLAGS.get(0)); FLAGS.remove(0); }
        Flag f = new Flag();
        f.pos = pos.immutable();
        f.until = lvl.getServer().getTickCount() + rallySeconds() * 20L;
        if (lvl.getBlockState(pos).isAir() && lvl.getBlockState(pos.below()).isSolid()) {
            lvl.setBlock(pos, Blocks.BLUE_BANNER.defaultBlockState(), 3);
            f.banner = pos.immutable();
        }
        FLAGS.add(f);
        lvl.playSound(null, by.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.PLAYERS, 3f, 1f);
        Component msg = WSLang.c("kingdom.rally.set", by.getGameProfile().getName(), rallySeconds());
        for (ServerPlayer o : lvl.getServer().getPlayerList().getPlayers()) o.sendSystemMessage(msg);
    }

    public static void endRally(ServerLevel lvl) {
        for (Flag f : FLAGS) removeBanner(lvl, f);
        FLAGS.clear();
    }

    /** Onglet Rôles : le Commandant récupère son cor s'il l'a perdu (code 90). */
    public static void recoverHorn(ServerPlayer p) {
        if (!KingdomRoles.has(p, KingdomRoles.Role.COMMANDER)) {
            p.displayClientMessage(WSLang.c("kingdom.rally.commander_only"), true);
            return;
        }
        var horn = com.wavesurvivor.registry.ModItems.COMMANDER_HORN.get();
        if (p.getInventory().contains(new ItemStack(horn))) {
            p.displayClientMessage(WSLang.c("kingdom.rally.already"), true);
            return;
        }
        ItemStack st = new ItemStack(horn);
        if (!p.getInventory().add(st)) p.drop(st, false);
        p.displayClientMessage(WSLang.c("kingdom.rally.recovered"), true);
    }

    /** Rôle attribué : le Commandant reçoit son cor (s'il ne l'a pas déjà). */
    public static void onRoleGiven(ServerPlayer p, KingdomRoles.Role r) {
        if (r == KingdomRoles.Role.AUGUR && p.getServer() != null) {
            // holder() voit le nouveau rôle seulement après l'enregistrement : différé d'un tick
            p.getServer().execute(() -> KingdomOmens.offerIfNeeded(p.getServer()));
        }
        if (r == KingdomRoles.Role.BOUNTY_HUNTER) contractIfNeeded(p);
        if (r == KingdomRoles.Role.QUARTERMASTER && !startResourcesGiven) {
            int n = (int) com.wavesurvivor.horde.kingdom.vocation.Vocations.total(p, r, "quartermaster.stewardship.start_resources") * 2; // talent Intendance
            if (n > 0) {
                startResourcesGiven = true;
                KingdomTreasury.add(KingdomTreasury.Res.WOOD, n);
                KingdomTreasury.add(KingdomTreasury.Res.STONE, n);
                KingdomTreasury.add(KingdomTreasury.Res.IRON, n / 2);
                p.sendSystemMessage(WSLang.c("voc.start_resources", n, n, n / 2));
            }
        }
        if (r != KingdomRoles.Role.COMMANDER) return;
        var horn = com.wavesurvivor.registry.ModItems.COMMANDER_HORN.get();
        if (p.getInventory().contains(new ItemStack(horn))) return;
        ItemStack st = new ItemStack(horn);
        if (!p.getInventory().add(st)) p.drop(st, false);
    }

    // ═══ Récupérateur ═══

    /** Tag d'un objet réservé au Récupérateur : « logs » ou « iron » (crédité au trésor au ramassage). */
    public static final String SCAV_TAG = "ws_scav";

    private static void scavengeDrop(ServerLevel lvl, double x, double y, double z, int wood, int iron) {
        scavengeDrop(lvl, x, y, z, wood, iron, 0);
    }

    private static void scavengeDrop(ServerLevel lvl, double x, double y, double z, int wood, int iron, int stone) {
        ServerPlayer s = KingdomRoles.holder(lvl.getServer(), KingdomRoles.Role.SCAVENGER);
        if (s == null) return;
        if (com.wavesurvivor.horde.kingdom.vocation.VocationFx.warChestActive()) { wood *= 2; iron *= 2; stone *= 2; } // ultime Trésor de guerre
        if (wood > 0) spawnScav(lvl, x, y, z, new ItemStack(Items.OAK_LOG, wood), "logs", s.getUUID());
        if (iron > 0) spawnScav(lvl, x, y, z, new ItemStack(Items.IRON_INGOT, iron), "iron", s.getUUID());
        if (stone > 0) spawnScav(lvl, x, y, z, new ItemStack(Items.COBBLESTONE, stone), "stone", s.getUUID());
    }

    private static void spawnScav(ServerLevel lvl, double x, double y, double z, ItemStack st, String res, UUID owner) {
        st.getOrCreateTag().putString(SCAV_TAG, res);
        ItemEntity ie = new ItemEntity(lvl, x, y + 0.5, z, st);
        ie.setTarget(owner);
        ie.setPickUpDelay(10);
        ie.setGlowingTag(true);
        lvl.addFreshEntity(ie);
    }

    /** Brèche du Calme refermée : débris pour le Récupérateur (Bois, Fer, Pierre). */
    static void riftClosed(ServerLevel lvl, BlockPos pos) {
        if (!KingdomRoles.present(KingdomRoles.Role.SCAVENGER)) return;
        com.wavesurvivor.horde.kingdom.vocation.VocationXp.awardHolder(lvl.getServer(), KingdomRoles.Role.SCAVENGER, "breach_closed");
        double bm = 1.0 + com.wavesurvivor.horde.kingdom.vocation.Voc.pct(KingdomRoles.Role.SCAVENGER, "scavenger.scrap.breach_loot"); // talent Ferraille
        scavengeDrop(lvl, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, (int) Math.round((2 + RNG.nextInt(2)) * bm), (int) Math.round(1 * bm), (int) Math.round((1 + RNG.nextInt(2)) * bm));
    }

    // ═══ Chasseur de primes ═══

    private enum ContractType { ELITES, SIEGE, KILLS }

    private static ContractType contract;
    private static int target, progress, reward;
    private static UUID hunter;
    /** Monstres déjà examinés pour la promotion en élite. */
    private static final Set<UUID> SEEN = new HashSet<>();

    // Lecture (synchronisation vers le client du Chasseur)
    static UUID contractHunter() { return contract == null ? null : hunter; }
    static String contractId() { return contract == null ? "" : contract.name().toLowerCase(); }
    static int contractProgress() { return progress; }
    static int contractTarget() { return target; }
    static int contractReward() { return reward; }

    /** Début du Calme : bilan du contrat précédent, puis nouveau contrat si le Chasseur est là. */
    static void onCalm(MinecraftServer server, int cycle) {
        if (contract != null) {
            ServerPlayer h = hunter != null ? server.getPlayerList().getPlayer(hunter) : null;
            if (progress >= target) {
                KingdomTreasury.reward(KingdomTreasury.Res.MONEY, reward, "kingdom.bounty.paid");
                KingdomTreasury.reward(KingdomTreasury.Res.ESSENCE, 4, "kingdom.bounty.paid");
                if (h != null) com.wavesurvivor.horde.kingdom.vocation.VocationXp.award(h, KingdomRoles.Role.BOUNTY_HUNTER, "contract_done");
                if (h != null && com.wavesurvivor.horde.kingdom.vocation.Vocations.has(h, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.contracts.contract_xp")) com.wavesurvivor.horde.kingdom.vocation.VocationXp.award(h, KingdomRoles.Role.BOUNTY_HUNTER, "contract_done"); // talent Contrats : XP doublée
            } else if (h != null) {
                h.sendSystemMessage(WSLang.c("kingdom.bounty.failed", progress, target));
            }
            contract = null;
            if (h != null) KingdomRoleState.syncTo(h);
        }
        ServerPlayer h = KingdomRoles.holder(server, KingdomRoles.Role.BOUNTY_HUNTER);
        if (h != null) issueContract(h, cycle);
    }

    /** Chasseur choisi en cours de partie : il reçoit tout de suite un contrat (s'il n'en a pas). */
    static void contractIfNeeded(ServerPlayer h) {
        if (contract != null && h.getUUID().equals(hunter)) return;
        issueContract(h, KingdomManager.cycle());
    }

    private static void issueContract(ServerPlayer h, int cycle) {
        java.util.List<ContractType> types = new java.util.ArrayList<>(java.util.List.of(ContractType.ELITES, ContractType.KILLS));
        if (KingdomManager.hasSiegeUnits()) types.add(ContractType.SIEGE);
        contract = types.get(RNG.nextInt(types.size()));
        int c = Math.max(1, cycle);
        target = switch (contract) {
            case ELITES -> Math.min(6, 2 + c / 3);
            case SIEGE -> Math.min(8, 2 + c / 2);
            case KILLS -> Math.min(60, 15 + 5 * c);
        };
        reward = (int) Math.round((25 + 8 * c) * com.wavesurvivor.horde.kingdom.vocation.Voc.base(KingdomRoles.Role.BOUNTY_HUNTER, "payoutMult", 0.70)
                * (1.0 + com.wavesurvivor.horde.kingdom.vocation.Vocations.total(h, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.contracts.pay") / 100.0));
        progress = 0;
        hunter = h.getUUID();
        h.sendSystemMessage(WSLang.c("kingdom.bounty.new", WSLang.t("kingdom.bounty.type." + contract.name().toLowerCase()), target));
        h.level().playSound(null, h.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1f, 1f);
        KingdomRoleState.syncTo(h);
    }

    static void clear() {
        contract = null;
        hunter = null;
        progress = target = 0;
        SEEN.clear();
        ServerLevel lvl = KingdomManager.level();
        endRally(lvl);
        startResourcesGiven = false;
    }

    private static boolean isElite(Entity e) {
        return e.getPersistentData().getBoolean("ws_elite");
    }

    /** Chasseur présent (10 %) et/ou présage Marée basse (20 %) : des monstres de la horde deviennent des élites. */
    private static void promoteElites(ServerLevel lvl) {
        double chance = (KingdomRoles.present(KingdomRoles.Role.BOUNTY_HUNTER) ? 0.10 : 0)
                + (KingdomOmens.is(KingdomOmens.Omen.TIDE) ? 0.20 * KingdomOmens.curse(KingdomOmens.Omen.TIDE) : 0);
        for (Map.Entry<UUID, com.wavesurvivor.horde.model.HordeEntity> en : MobRegistry.entries()) {
            UUID id = en.getKey();
            if (!SEEN.add(id)) continue;
            if (!(lvl.getEntity(id) instanceof Mob m) || !m.isAlive()) continue;
            var data = m.getPersistentData();
            if (BossManager.isBoss(id) || data.getBoolean("ws_kingdom_guardian") || data.getBoolean("ws_kingdom_escort")
                    || !KingdomSiege.roleOf(m).isEmpty()) continue;
            if (RNG.nextDouble() >= chance) continue;
            AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(hp.getBaseValue() * 1.6);
            m.setHealth(m.getMaxHealth());
            m.setGlowingTag(true);
            data.putBoolean("ws_elite", true);
            m.setCustomName(Component.literal("§6★ " + m.getName().getString()));
            m.setCustomNameVisible(true);
        }
        if (SEEN.size() > 4000) SEEN.clear();
    }

    /** Débris supplémentaires pour le Récupérateur (Champions abattus, coffre de pillard). */
    public static void bonusDrop(ServerLevel lvl, double x, double y, double z, int wood, int iron, int stone) {
        scavengeDrop(lvl, x, y, z, wood, iron, stone);
    }

    /** Talent Pillage : le coffre d'un pillard après chaque Assaut repoussé (débris + pièces). */
    public static void raiderChest(ServerPlayer s) {
        if (!(s.level() instanceof ServerLevel lvl)) return;
        scavengeDrop(lvl, s.getX(), s.getY(), s.getZ(), 6, 6, 6);
        KingdomTreasury.reward(KingdomTreasury.Res.MONEY, 15, "voc.reason.raider");
        s.displayClientMessage(WSLang.c("voc.raider_chest"), true);
    }

    /** Talent Recyclage : une caisse de ferraille (Bois, Fer, Pierre) pour le Récupérateur. */
    public static void scrapCrate(ServerPlayer s) {
        if (!(s.level() instanceof ServerLevel lvl)) return;
        scavengeDrop(lvl, s.getX(), s.getY(), s.getZ(), 8, 4, 8);
        s.displayClientMessage(WSLang.c("voc.scrap_crate"), true);
    }

    /** Talent Recyclage › Ressources de départ de l'Intendant : une seule fois par partie. */
    private static boolean startResourcesGiven = false;

    /** Talent Ferraille : les débris du Récupérateur sont attirés vers lui. */
    private static void pullDebris(ServerLevel lvl) {
        ServerPlayer s = KingdomRoles.holder(lvl.getServer(), KingdomRoles.Role.SCAVENGER);
        if (s == null || s.level() != lvl || !com.wavesurvivor.horde.kingdom.vocation.Vocations.has(s, KingdomRoles.Role.SCAVENGER, "scavenger.scrap.pull")) return;
        for (ItemEntity ie : lvl.getEntitiesOfClass(ItemEntity.class, s.getBoundingBox().inflate(14), i -> i.getItem().hasTag() && i.getItem().getTag().contains(SCAV_TAG))) {
            Vec3 to = s.position().add(0, 0.5, 0).subtract(ie.position());
            if (to.lengthSqr() < 1.5) continue;
            ie.setDeltaMovement(to.normalize().scale(0.35));
            ie.hurtMarked = true;
        }
    }

    public static class Events {

        @SubscribeEvent
        public void extrasTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 10 != 0) return;
            ServerLevel lvl = KingdomManager.level();
            if (lvl == null || !KingdomRoles.active()) return;
            if (KingdomRoles.present(KingdomRoles.Role.BOUNTY_HUNTER) || KingdomOmens.is(KingdomOmens.Omen.TIDE)) promoteElites(lvl);
            pullDebris(lvl);
            // Présages « Vents favorables » (Vitesse I pour tous) et « Lune de sang » (Force I aux monstres)
            boolean winds = KingdomOmens.is(KingdomOmens.Omen.FAIR_WINDS), blood = KingdomOmens.is(KingdomOmens.Omen.BLOOD_MOON);
            if (winds || blood) {
                if (winds) {
                    for (ServerPlayer pl : lvl.players()) {
                        if (!pl.isSpectator()) pl.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 40, 0, true, false, true));
                    }
                }
                for (Map.Entry<UUID, com.wavesurvivor.horde.model.HordeEntity> en : MobRegistry.entries()) {
                    if (!(lvl.getEntity(en.getKey()) instanceof Mob m) || !m.isAlive()) continue;
                    if (winds) m.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 40, 0, true, false));
                    if (blood) m.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 40, 0, true, false));
                }
            }
            // Bannière de ralliement : colonne bleue visible de tous
            for (BlockPos r : rallyPoints()) {
                for (int i = 0; i < 10; i++) {
                    lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, r.getX() + 0.5, r.getY() + 1 + i * 0.8, r.getZ() + 0.5, 1, 0.05, 0.1, 0.05, 0);
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void extrasDeath(LivingDeathEvent e) {
            LivingEntity dead = e.getEntity();
            if (dead.level().isClientSide || !(dead.level() instanceof ServerLevel lvl) || !KingdomRoles.active()) return;
            if (lvl != KingdomManager.level()) return;
            boolean siege = !KingdomSiege.roleOf(dead).isEmpty();
            // Présage « Pluie de fer » : 15 % de chance de +1 Fer par monstre de la horde abattu
            if (KingdomOmens.is(KingdomOmens.Omen.IRON_RAIN) && RNG.nextDouble() < 0.15 * KingdomOmens.boon(KingdomOmens.Omen.IRON_RAIN)
                    && (MobRegistry.get(dead.getUUID()) != null || isElite(dead) || siege)) {
                KingdomTreasury.add(KingdomTreasury.Res.IRON, 1);
                lvl.sendParticles(ParticleTypes.CRIT, dead.getX(), dead.getY() + 1, dead.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
            }
            // Récupérateur : une unité de siège tombée lâche Bois, Fer et Pierre pour lui ;
            // tout autre monstre de la horde : 12 % de chance de laisser un débris (Fer ou Pierre)
            if (KingdomRoles.present(KingdomRoles.Role.SCAVENGER)) {
                if (siege) {
                    double sm = com.wavesurvivor.horde.kingdom.vocation.Voc.base(KingdomRoles.Role.SCAVENGER, "siegeDropMult", 0.8)
                            * (1.0 + com.wavesurvivor.horde.kingdom.vocation.Voc.pct(KingdomRoles.Role.SCAVENGER, "scavenger.plunder.siege_loot"));
                    scavengeDrop(lvl, dead.getX(), dead.getY(), dead.getZ(), (int) Math.round((3 + RNG.nextInt(2)) * sm), (int) Math.round(2 * sm), (int) Math.round(2 * sm));
                } else if ((MobRegistry.get(dead.getUUID()) != null || isElite(dead))
                        && RNG.nextDouble() < com.wavesurvivor.horde.kingdom.vocation.Voc.base(KingdomRoles.Role.SCAVENGER, "debrisChance", 0.08)
                                + com.wavesurvivor.horde.kingdom.vocation.Voc.pct(KingdomRoles.Role.SCAVENGER, "scavenger.scrap.debris")) {
                    boolean iron = RNG.nextBoolean();
                    scavengeDrop(lvl, dead.getX(), dead.getY(), dead.getZ(), 0, iron ? 1 : 0, iron ? 0 : 2);
                    ServerPlayer sc = KingdomRoles.holder(lvl.getServer(), KingdomRoles.Role.SCAVENGER);
                    if (sc != null && RNG.nextDouble() < com.wavesurvivor.horde.kingdom.vocation.Vocations.total(sc, KingdomRoles.Role.SCAVENGER, "scavenger.scrap.rare_debris") / 100.0) {
                        spawnScav(lvl, dead.getX(), dead.getY(), dead.getZ(), new ItemStack(Items.AMETHYST_SHARD, 1), "essence", sc.getUUID()); // talent Ferraille : débris rare
                    }
                }
            }
            if (e.getSource().getEntity() instanceof ServerPlayer hp && isElite(dead)) {
                com.wavesurvivor.horde.kingdom.vocation.VocationXp.award(hp, KingdomRoles.Role.BOUNTY_HUNTER, "elite_killed");
                if (KingdomRoles.has(hp, KingdomRoles.Role.BOUNTY_HUNTER) && com.wavesurvivor.horde.kingdom.vocation.Vocations.has(hp, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.hunt.elite_speed")) {
                    hp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 100, 1)); // talent Traque
                }
            }
            // Chasseur de primes : avancement du contrat (ses propres kills)
            if (contract != null && e.getSource().getEntity() instanceof ServerPlayer p && p.getUUID().equals(hunter)
                    && progress < target) {
                boolean counts = switch (contract) {
                    case ELITES -> isElite(dead);
                    case SIEGE -> siege;
                    case KILLS -> MobRegistry.get(dead.getUUID()) != null || isElite(dead) || siege;
                };
                if (counts) {
                    progress += isElite(dead) && com.wavesurvivor.horde.kingdom.vocation.Vocations.has(p, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.hunt.contract_extend") ? 3 : 1; // talent Traque : une élite compte triple
                    p.displayClientMessage(WSLang.c("kingdom.bounty.progress", progress, target), true);
                    KingdomRoleState.syncTo(p);
                    if (progress >= target) {
                        p.sendSystemMessage(WSLang.c("kingdom.bounty.done"));
                        lvl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
                    }
                }
            }
        }
    }
}
