package com.wavesurvivor.horde.spawn;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.model.HordeEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MobRegistry {

    private static final Map<UUID, HordeEntity> MAP = new ConcurrentHashMap<>();

    public static void register(Entity entity, HordeEntity template) {
        if (entity != null && template != null) {
            MAP.put(entity.getUUID(), template);
            // Marque persistante (sauvegardée avec l'entité) : permet de retirer les restes d'une horde après un crash
            entity.getPersistentData().putBoolean("ws_horde_unit", true);
            // Jamais de disparition naturelle (monstre loin des joueurs) : la horde gère elle-même ses unités
            if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setPersistenceRequired();
            // Mutateurs de la horde en cours (Frénésie, Blindés, Brutes, Avortons) : appliqués après les statistiques
            com.wavesurvivor.horde.mutator.MutatorEffects.applyToUnit(entity);
            // Niveau de difficulté de la horde (PV et dégâts des monstres)
            com.wavesurvivor.horde.difficulty.HordeDifficulty.applyToUnit(entity);
            // Monture / passager non demandés (jockeys vanilla ou d'autres mods) : contrôlés au tick suivant
            MOUNT_CHECK.add(entity.getUUID());
            // « Briseur de Monolithe » coché sur l'unité : ignore les joueurs, ne vise que le Monolithe (sans entité custom)
            if (template.targetsAltar) com.wavesurvivor.altar.AltarDefense.registerProfaner(entity);
            // Compétences ajoutées à l'unité dans le Horde Editor (onglet Mobs), avec ses réglages propres (une seule fois)
            if (template.skills != null && !template.skills.isEmpty() && !entity.getPersistentData().getBoolean("ws_unit_skills")) {
                entity.getPersistentData().putBoolean("ws_unit_skills", true);
                com.wavesurvivor.horde.skill.SkillManager.registerUnitSkills(entity.getUUID(), template.customName, template.skills, template.skillOverrides);
            }
            // Taille de l'unité (mod Pehkui, optionnel) × mutateurs « Brutes » / « Avortons » ; un boss garde la sienne
            if (!entity.getPersistentData().getBoolean("ws_boss_scaled")) {
                double sc = template.scale > 0 ? template.scale : 1.0;
                if (com.wavesurvivor.horde.mutator.HordeMutators.on(com.wavesurvivor.horde.mutator.Mutator.BRUTES)) sc *= 1.3;
                if (com.wavesurvivor.horde.mutator.HordeMutators.on(com.wavesurvivor.horde.mutator.Mutator.RUNTS)) sc *= 0.6;
                com.wavesurvivor.compat.PehkuiCompat.setScale(entity, sc);
            }
        }
    }

    public static HordeEntity get(UUID uuid) {
        return MAP.get(uuid);
    }

    public static HordeEntity remove(UUID uuid) {
        LAST_SEEN.remove(uuid);
        DEAD.remove(uuid);
        return MAP.remove(uuid);
    }

    public static int size() {
        return MAP.size();
    }

    public static void clear() {
        MAP.clear();
        LAST_SEEN.clear();
        DEAD.clear();
    }

    public static Set<Map.Entry<UUID, HordeEntity>> entries() {
        return MAP.entrySet();
    }

    /**
     * Retire les UUIDs sans entité vivante correspondante dans le serveur.
     * Nécessaire car LivingDropsEvent n'est pas toujours déclenché (despawn, /kill,
     * discard() manuel, unloaded chunks). Sans ce cleanup, size() reste bloqué et
     * les vagues ne se terminent jamais.
     *
     * @return nombre d'entrées retirées
     */
    public static int pruneDead(MinecraftServer server) {
        if (server == null || MAP.isEmpty()) return 0;
        int removed = 0;
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, HordeEntity>> it = MAP.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, HordeEntity> e = it.next();
            Entity ent = findInAllLevels(server, e.getKey());
            // Hors de vue (zone déchargée, loin des joueurs) ≠ mort : on garde l'unité. Les vraies morts sont retirées
            // par l'événement de mort (HordeSafety) ; filet : oubliée après 5 minutes sans être vue.
            if (ent == null) {
                if (DEAD.remove(e.getKey())) { it.remove(); LAST_SEEN.remove(e.getKey()); removed++; continue; } // vraiment morte
                Long seen = LAST_SEEN.putIfAbsent(e.getKey(), now);
                if (seen == null || now - seen < 6000) continue;
            } else if (ent instanceof LivingEntity living && living.isAlive()) {
                LAST_SEEN.put(e.getKey(), now);
                DEAD.remove(e.getKey()); // mort annulée (résurrection, totem…) : toujours en vie
                continue;
            }
            it.remove();
            LAST_SEEN.remove(e.getKey());
            DEAD.remove(e.getKey());
            removed++;
        }
        if (removed > 0) {
            WaveSurvivorMod.LOGGER.debug("[MobRegistry] {} orphelin(s) purgé(s), il reste {} mob(s).", removed, MAP.size());
        }
        return removed;
    }

    /** Dernière fois (tick serveur) où chaque unité a été vue chargée. */
    private static final Map<UUID, Long> LAST_SEEN = new ConcurrentHashMap<>();

    /** Unités à contrôler au tick suivant leur apparition (montures / passagers non demandés). */
    private static final java.util.Queue<UUID> MOUNT_CHECK = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /**
     * Retire les montures et passagers qui ne font pas partie de la horde : poulet du bébé zombie, cheval zombie,
     * squelette sur une araignée… (ajoutés automatiquement par Minecraft ou d'autres mods). Les montures configurées
     * dans l'éditeur sont elles-mêmes des unités de la horde : elles sont gardées.
     */
    public static void checkMounts(MinecraftServer server) {
        if (server == null) return;
        UUID id;
        int n = 0;
        while (n++ < 200 && (id = MOUNT_CHECK.poll()) != null) {
            Entity e = findInAllLevels(server, id);
            if (e == null) continue;
            Entity v = e.getVehicle();
            if (v != null && !MAP.containsKey(v.getUUID()) && !(v instanceof net.minecraft.world.entity.player.Player)) {
                e.stopRiding();
                v.discard();
            }
            for (Entity p : new java.util.ArrayList<>(e.getPassengers())) {
                if (!(p instanceof net.minecraft.world.entity.player.Player) && !MAP.containsKey(p.getUUID())) {
                    p.stopRiding();
                    p.discard();
                }
            }
        }
    }

    /** Unités réellement mortes (événement de mort) : retirées au prochain nettoyage, après le butin. */
    private static final java.util.Set<UUID> DEAD = ConcurrentHashMap.newKeySet();

    /** Événement de mort : l'unité sera retirée au prochain nettoyage, même si son entité a déjà disparu. */
    public static void markDead(UUID id) {
        if (MAP.containsKey(id)) DEAD.add(id);
    }

    private static Entity findInAllLevels(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }
}
