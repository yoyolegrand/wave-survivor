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

    public static final int RALLY_SECONDS = 60;
    private static BlockPos rally;
    private static long rallyUntil;
    /** Bannière posée pour le ralliement (retirée à la fin). */
    private static BlockPos bannerPos;

    /** Point de ralliement actif (null = aucun). */
    public static BlockPos rallyPoint() {
        ServerLevel lvl = KingdomManager.level();
        if (rally == null || lvl == null) return null;
        if (lvl.getServer().getTickCount() >= rallyUntil) {
            endRally(lvl);
            return null;
        }
        return rally;
    }

    public static void setRally(ServerLevel lvl, BlockPos pos, ServerPlayer by) {
        endRally(lvl);
        rally = pos.immutable();
        rallyUntil = lvl.getServer().getTickCount() + RALLY_SECONDS * 20L;
        if (lvl.getBlockState(pos).isAir() && lvl.getBlockState(pos.below()).isSolid()) {
            lvl.setBlock(pos, Blocks.BLUE_BANNER.defaultBlockState(), 3);
            bannerPos = pos.immutable();
        }
        lvl.playSound(null, by.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.PLAYERS, 3f, 1f);
        Component msg = WSLang.c("kingdom.rally.set", by.getGameProfile().getName(), RALLY_SECONDS);
        for (ServerPlayer o : lvl.getServer().getPlayerList().getPlayers()) o.sendSystemMessage(msg);
    }

    public static void endRally(ServerLevel lvl) {
        if (bannerPos != null && lvl != null && lvl.getBlockState(bannerPos).is(Blocks.BLUE_BANNER)) {
            lvl.removeBlock(bannerPos, false);
        }
        bannerPos = null;
        rally = null;
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
        scavengeDrop(lvl, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 2 + RNG.nextInt(2), 1, 1 + RNG.nextInt(2));
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
        reward = 25 + 8 * c;
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
        rally = null;
        bannerPos = null;
    }

    private static boolean isElite(Entity e) {
        return e.getPersistentData().getBoolean("ws_elite");
    }

    /** Chasseur présent (10 %) et/ou présage Marée basse (20 %) : des monstres de la horde deviennent des élites. */
    private static void promoteElites(ServerLevel lvl) {
        double chance = (KingdomRoles.present(KingdomRoles.Role.BOUNTY_HUNTER) ? 0.10 : 0)
                + (KingdomOmens.is(KingdomOmens.Omen.TIDE) ? 0.20 : 0);
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

    public static class Events {

        @SubscribeEvent
        public void extrasTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 10 != 0) return;
            ServerLevel lvl = KingdomManager.level();
            if (lvl == null || !KingdomRoles.active()) return;
            if (KingdomRoles.present(KingdomRoles.Role.BOUNTY_HUNTER) || KingdomOmens.is(KingdomOmens.Omen.TIDE)) promoteElites(lvl);
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
            BlockPos r = rallyPoint();
            if (r != null) {
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
            if (KingdomOmens.is(KingdomOmens.Omen.IRON_RAIN) && RNG.nextDouble() < 0.15
                    && (MobRegistry.get(dead.getUUID()) != null || isElite(dead) || siege)) {
                KingdomTreasury.add(KingdomTreasury.Res.IRON, 1);
                lvl.sendParticles(ParticleTypes.CRIT, dead.getX(), dead.getY() + 1, dead.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
            }
            // Récupérateur : une unité de siège tombée lâche Bois, Fer et Pierre pour lui ;
            // tout autre monstre de la horde : 12 % de chance de laisser un débris (Fer ou Pierre)
            if (KingdomRoles.present(KingdomRoles.Role.SCAVENGER)) {
                if (siege) {
                    scavengeDrop(lvl, dead.getX(), dead.getY(), dead.getZ(), 3 + RNG.nextInt(2), 2, 2);
                } else if ((MobRegistry.get(dead.getUUID()) != null || isElite(dead)) && RNG.nextDouble() < 0.12) {
                    boolean iron = RNG.nextBoolean();
                    scavengeDrop(lvl, dead.getX(), dead.getY(), dead.getZ(), 0, iron ? 1 : 0, iron ? 0 : 2);
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
                    progress++;
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
