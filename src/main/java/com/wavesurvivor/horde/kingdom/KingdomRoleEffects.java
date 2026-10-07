package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.entity.GisementEntity;
import com.wavesurvivor.horde.chaos.GisementManager;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.RoleTrackerPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * EFFETS PERMANENTS DES RÔLES (toutes les 0,5 s) :
 *  - Arcaniste : colonne de lumière violette au-dessus de chaque Catalyseur (visible de loin, par lui seul)
 *    + flèche du HUD vers le plus proche ;
 *  - Mineur : colonne de lumière dorée au-dessus de chaque gisement (par lui seul) + flèche du HUD,
 *    Célérité II à moins de 6 blocs d'un gisement.
 * Les autres effets sont branchés là où la mécanique se joue (KingdomManager, GisementManager, ChaosApplier…).
 */
public final class KingdomRoleEffects {

    private KingdomRoleEffects() {}

    private static final DustParticleOptions ARCANE = new DustParticleOptions(new Vector3f(0.75f, 0.3f, 1.0f), 2.0f);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.25f), 2.0f);
    /** Joueurs qui ont reçu une flèche (pour l'effacer quand ils n'en ont plus besoin). */
    private static final Set<UUID> TRACKED = new HashSet<>();

    public static class Events {

        /** Intendant (+20 % sur ses kills) et présage Butin abondant (+50 %) : plus de monnaie lâchée (arrondi au hasard). */
        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public void effectsDrops(net.minecraftforge.event.entity.living.LivingDropsEvent e) {
            if (!KingdomRoles.active()) return;
            boolean qm = e.getSource().getEntity() instanceof ServerPlayer kp && KingdomRoles.has(kp, KingdomRoles.Role.QUARTERMASTER);
            double bonus = (qm ? 1.2 : 1.0) * (KingdomOmens.is(KingdomOmens.Omen.BOUNTY) ? 1.5 : 1.0) - 1.0;
            if (bonus <= 0) return;
            var rnd = e.getEntity().getRandom();
            for (net.minecraft.world.entity.item.ItemEntity ie : e.getDrops()) {
                var st = ie.getItem();
                if (!com.wavesurvivor.altar.AltarDefense.isCurrency(st)) continue;
                double extra = st.getCount() * bonus;
                int add = (int) Math.floor(extra) + (rnd.nextDouble() < extra - Math.floor(extra) ? 1 : 0);
                if (add > 0) {
                    var copy = st.copy();
                    copy.grow(add);
                    ie.setItem(copy);
                }
            }
        }

        /** Intendant : -15 % chez les marchands (prix remis à la normale pour les autres joueurs). */
        @SubscribeEvent
        public void effectsInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract e) {
            if (e.getLevel().isClientSide || !KingdomRoles.active()) return;
            if (!(e.getTarget() instanceof net.minecraft.world.entity.npc.AbstractVillager v) || !(e.getEntity() instanceof ServerPlayer p)) return;
            boolean qm = KingdomRoles.has(p, KingdomRoles.Role.QUARTERMASTER);
            for (var offer : v.getOffers()) {
                int base = offer.getBaseCostA().getCount();
                offer.setSpecialPriceDiff(qm ? -Math.max(1, (int) Math.round(base * 0.15)) : 0);
            }
        }
        @SubscribeEvent
        public void effectsTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 10 != 0) return;
            MinecraftServer server = e.getServer();
            if (!KingdomRoles.active()) {
                if (!TRACKED.isEmpty()) {
                    for (UUID id : TRACKED) {
                        ServerPlayer p = server.getPlayerList().getPlayer(id);
                        if (p != null) send(p, "", List.of());
                    }
                    TRACKED.clear();
                }
                return;
            }
            Set<UUID> now = new HashSet<>();
            // Bâtisseur : +10 % de PV sur toutes les constructions tant qu'il est connecté
            KingdomClaim.setBuilderBonus(KingdomRoles.present(KingdomRoles.Role.BUILDER));
            // Pierre de Fondation (relique) : +15 % de PV tant qu'un porteur est connecté
            KingdomClaim.setFoundationLevel(com.wavesurvivor.item.RelicEffects.bestLevel(server, com.wavesurvivor.registry.ModItems.PIERRE_FONDATION.get()));
            boolean teamRift = false;
            for (ServerPlayer q : server.getPlayerList().getPlayers()) {
                if (com.wavesurvivor.item.RelicEffects.hasDirect(q, com.wavesurvivor.registry.ModItems.OEIL_FAILLE.get())) { teamRift = true; break; }
            }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                KingdomRoles.Role r = KingdomRoles.of(p);
                // Prisme du Catalyseur (relique) : colonne de lumière sur chaque Catalyseur (l'Arcaniste l'a déjà) ;
                // Œil de la Faille (légendaire) : toute l'équipe les voit
                if (r != KingdomRoles.Role.ARCANIST && (com.wavesurvivor.item.RelicEffects.hasRelic(p, com.wavesurvivor.registry.ModItems.PRISME_CATALYSEUR.get())
                        || teamRift)) {
                    for (Entity c : KingdomManager.catalysts()) beam(p, c, ARCANE);
                }
                if (r == KingdomRoles.Role.ARCANIST) {
                    List<Entity> cats = KingdomManager.catalysts();
                    for (Entity c : cats) beam(p, c, ARCANE);
                    send(p, "catalyst", cats);
                    now.add(p.getUUID());
                } else if (r == KingdomRoles.Role.MINER) {
                    List<GisementEntity> deps = GisementManager.deposits(server);
                    boolean near = false;
                    for (GisementEntity g : deps) {
                        beam(p, g, GOLD);
                        if (g.level() == p.level() && g.distanceToSqr(p) < 36) near = true;
                    }
                    if (near) p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 1, true, false, true));
                    send(p, "deposit", new ArrayList<>(deps));
                    now.add(p.getUUID());
                }
            }
            // Joueurs qui n'ont plus de cible à suivre : flèche effacée
            for (UUID id : TRACKED) {
                if (now.contains(id)) continue;
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) send(p, "", List.of());
            }
            TRACKED.clear();
            TRACKED.addAll(now);
        }
    }

    /** Colonne de particules au-dessus d'une cible, envoyée à un seul joueur (longue portée). */
    private static void beam(ServerPlayer p, Entity target, DustParticleOptions dust) {
        if (target.level() != p.level() || !(p.level() instanceof ServerLevel lvl)) return;
        double x = target.getX(), z = target.getZ(), y0 = target.getY() + target.getBbHeight();
        for (int i = 0; i < 24; i++) {
            lvl.sendParticles(p, dust, true, x, y0 + i * 1.5, z, 1, 0.05, 0.2, 0.05, 0);
        }
    }

    private static void send(ServerPlayer p, String kind, List<? extends Entity> targets) {
        if (NetworkHandler.CHANNEL == null) return;
        List<Entity> same = new ArrayList<>();
        for (Entity t : targets) if (t.level() == p.level()) same.add(t);
        double[] pts = new double[same.size() * 3];
        for (int i = 0; i < same.size(); i++) {
            Entity t = same.get(i);
            pts[i * 3] = t.getX();
            pts[i * 3 + 1] = t.getY();
            pts[i * 3 + 2] = t.getZ();
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new RoleTrackerPacket(pts.length == 0 ? "" : kind, pts));
    }
}
