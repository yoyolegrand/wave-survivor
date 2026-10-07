package com.wavesurvivor.horde;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * FILET DE SÉCURITÉ DES HORDES (classique et Kingdom).
 *  1. Fermeture du monde (retour au menu, /stop) : la horde en cours est arrêtée PROPREMENT avant la sauvegarde,
 *     exactement comme le bouton « Arrêter » : Portes, corruption du sol, décor de la Mairie, bornes du claim et arène
 *     restaurés ; unités, Gardiens, escortes et soldats retirés. On retrouve un monde propre en se reconnectant.
 *  2. Chargement du monde (après un crash, ou des restes d'avant ce correctif) : tout reste de horde rechargé depuis la
 *     sauvegarde alors qu'aucune horde ne tourne (unité marquée, Porte, Gardien, escorte, soldat, faille, gisement, totem)
 *     est retiré automatiquement.
 */
public class HordeSafety {

    /** Marques persistantes posées par le mod sur ses entités de horde. */
    private static final String[] TAGS = {"ws_horde_unit", "ws_kingdom_portal", "ws_kingdom_guardian", "ws_kingdom_escort",
            "ws_kingdom_soldier"};

    @SubscribeEvent
    public void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        // Nouvelles unités : montures / passagers non demandés retirés (jockeys vanilla, chevaux zombies…)
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) com.wavesurvivor.horde.spawn.MobRegistry.checkMounts(event.getServer());
        // Unités « attaque les bâtiments » : un coup par seconde au Monolithe / aux murs et défenses à portée
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) BuildingAttack.tick(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        boolean falling = com.wavesurvivor.altar.MonolithFall.active(); // défaite en cours : rien à reprendre
        com.wavesurvivor.altar.MonolithFall.cancel(); // séquence de défaite interrompue : l'arrêt propre ci-dessous suffit
        HordeManager hm = HordeManager.get();
        if (hm == null || !hm.isRunning()) return;
        WaveSurvivorMod.LOGGER.info("[HordeSafety] Fermeture du monde : partie sauvegardée, puis arrêt propre de la horde (terrain restauré, unités retirées).");
        try {
            if (falling) hm.stop(); // Monolithe tombé : la partie est perdue (sauvegarde effacée)
            else HordeSession.saveAndStop(event.getServer()); // reprise proposée au retour des joueurs
        } catch (Exception ex) {
            WaveSurvivorMod.LOGGER.error("[HordeSafety] Échec de l'arrêt propre de la horde à la fermeture", ex);
        }
    }

    /**
     * Blocage en AMONT (le plus fiable) : quand Minecraft vérifie si une créature a le droit d'apparaître à un endroit
     * (apparitions naturelles et patrouilles), on refuse pendant une horde réglée ainsi.
     */
    @SubscribeEvent
    public void onSpawnPlacement(net.minecraftforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck event) {
        var type = event.getSpawnType();
        if (type != net.minecraft.world.entity.MobSpawnType.NATURAL && type != net.minecraft.world.entity.MobSpawnType.PATROL) return;
        HordeManager hm = HordeManager.get();
        if (hm != null && hm.blocksNaturalSpawns()) event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    /**
     * Début d'une horde qui bloque les apparitions naturelles : chasse les monstres hostiles SAUVAGES déjà présents.
     * Appelé avant l'arrivée de la horde. Ne touche jamais : monstres nommés ou conservés, unités de horde, Portes,
     * soldats, ni les animaux / villageois. @return nombre de créatures chassées.
     */
    public static int purgeHostiles(net.minecraft.server.MinecraftServer server) {
        if (server == null) return 0;
        int n = 0;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            java.util.List<Entity> found = new java.util.ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (!(e instanceof net.minecraft.world.entity.Mob m) || !(e instanceof net.minecraft.world.entity.monster.Enemy)) continue;
                if (!m.isAlive() || m.isPersistenceRequired() || m.requiresCustomPersistence() || m.hasCustomName()) continue;
                if (com.wavesurvivor.horde.spawn.MobRegistry.get(e.getUUID()) != null || isLeftover(e)) continue;
                found.add(e);
            }
            for (Entity e : found) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
                e.discard();
                n++;
            }
        }
        if (n > 0) {
            WaveSurvivorMod.LOGGER.info("[HordeSafety] {} créature(s) hostile(s) sauvage(s) chassée(s) au début de la horde.", n);
            for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("srv.hostiles_purged", n));
            }
        }
        return n;
    }

    @SubscribeEvent
    public void onNaturalSpawn(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn event) {
        // Pendant une horde réglée ainsi : pas d'apparitions naturelles (ni patrouilles) — moins d'entités à calculer,
        // et aucun monstre sauvage ne vient se mêler au combat. Les unités de la horde, œufs et spawners ne sont pas concernés.
        var type = event.getSpawnType();
        if (type != net.minecraft.world.entity.MobSpawnType.NATURAL && type != net.minecraft.world.entity.MobSpawnType.PATROL) return;
        HordeManager hm = HordeManager.get();
        if (hm != null && hm.blocksNaturalSpawns()) event.setSpawnCancelled(true);
    }

    @SubscribeEvent
    public void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        java.util.UUID id = event.getEntity().getUUID();
        // Vraie mort d'une unité : notée, puis retirée du registre au prochain nettoyage (après le butin)
        com.wavesurvivor.horde.spawn.MobRegistry.markDead(id);
        // Vraie mort d'une Porte du royaume : seule façon de la déclarer détruite
        if (event.getEntity().getPersistentData().getBoolean("ws_kingdom_portal")) {
            com.wavesurvivor.horde.kingdom.KingdomManager.portalKilled(id);
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.loadedFromDisk()) return;  // seulement les entités rechargées
        HordeManager hm = HordeManager.get();
        if (hm != null && hm.isRunning()) return;                                // une horde tourne : rien à nettoyer
        Entity e = event.getEntity();
        if (isLeftover(e)) {
            event.setCanceled(true);
            WaveSurvivorMod.LOGGER.info("[HordeSafety] Reste de horde retiré au chargement : {} ({})",
                    e.getType().getDescriptionId(), e.blockPosition().toShortString());
        }
    }

    private static boolean isLeftover(Entity e) {
        if (e instanceof com.wavesurvivor.entity.BrecheEntity || e instanceof com.wavesurvivor.entity.KingdomSoldier
                || e instanceof com.wavesurvivor.entity.GisementEntity || e instanceof com.wavesurvivor.entity.TotemEntity) return true;
        CompoundTag tag = e.getPersistentData();
        for (String t : TAGS) if (tag.getBoolean(t)) return true;
        return false;
    }
}
