package com.wavesurvivor.horde;

import com.wavesurvivor.altar.AltarDefense;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.spawn.MobRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * UNITÉS QUI ATTAQUENT LES BÂTIMENTS.
 *  - Option « Attaque les bâtiments » d'une unité : toutes les secondes, elle frappe le Monolithe s'il est à moins de
 *    3 blocs, sinon le mur / la défense le plus proche à moins de 2,5 blocs — quoi qu'elle fasse (sauter, grimper, se
 *    battre). Son IA n'est pas modifiée : elle attaque toujours les joueurs.
 *  - Explosions des unités de la horde (creepers, exploseurs custom) : dégâts au Monolithe (≤ 4,5 blocs) et aux murs /
 *    défenses (≤ 3 blocs). Le terrain naturel n'est jamais touché. Les Sapeurs gardent leur propre explosion.
 *  Dégâts : « dégâts par coup » de l'unité, sinon son attaque (coup) ou 40 (explosion).
 */
public class BuildingAttack {

    private static final double MONOLITH_REACH = 3.0, BUILDING_REACH = 2.5, BLAST_MONOLITH = 4.5;
    private static final int BLAST_RADIUS = 3;
    private static final float DEFAULT_BLAST = 40f;

    /** Appelé chaque tick serveur (fin de tick) : un coup par seconde et par unité concernée. */
    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 20 != 0) return;
        HordeManager hm = HordeManager.get();
        if (!hm.isRunning() || com.wavesurvivor.altar.MonolithFall.active()) return;
        for (Map.Entry<UUID, HordeEntity> en : new ArrayList<>(MobRegistry.entries())) {
            HordeEntity t = en.getValue();
            if (t == null || !t.attackBuildings) continue;
            Entity e = findEntity(server, en.getKey());
            if (!(e instanceof Mob m) || !m.isAlive() || !(m.level() instanceof ServerLevel lvl)) continue;
            if (AltarDefense.isProfaner(m.getUUID())) continue; // le Profanateur a d\u00e9j\u00e0 sa propre attaque du Monolithe
            float dmg = hitDamage(m, t);
            if (AltarDefense.inReach(lvl, m.position(), MONOLITH_REACH)) {
                m.swing(InteractionHand.MAIN_HAND);
                AltarDefense.externalHit(lvl, dmg);
                continue;
            }
            com.wavesurvivor.horde.kingdom.KingdomClaim.hitNear(m, BUILDING_REACH, dmg);
        }
    }

    private static float hitDamage(Mob m, HordeEntity t) {
        if (t.buildingDamage > 0) return (float) t.buildingDamage;
        var atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
        return atk != null ? (float) Math.max(1.0, atk.getValue()) : 2f;
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    /** Explosion d'une unit\u00e9 de la horde : d\u00e9g\u00e2ts au Monolithe et aux murs / d\u00e9fenses proches. */
    @SubscribeEvent
    public void onExplode(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel lvl) || !HordeManager.get().isRunning()) return;
        Entity src = event.getExplosion().getDirectSourceEntity();
        if (src == null) src = event.getExplosion().getIndirectSourceEntity();
        if (src == null) return;
        HordeEntity t = MobRegistry.get(src.getUUID());
        if (t == null) return;
        // Jamais de dégâts au terrain : les explosions des unités de la horde (creepers à l'IA vanilla compris) ne cassent
        // aucun bloc — les murs et défenses du royaume passent par leurs PV ci-dessous
        event.getAffectedBlocks().clear();
        // Les Sapeurs ont d\u00e9j\u00e0 leur explosion de si\u00e8ge (200 d\u00e9g\u00e2ts aux murs)
        if ("sapper".equals(src.getPersistentData().getString("ws_siege_role"))) return; // même clé que KingdomSiege.ROLE
        float dmg = t.buildingDamage > 0 ? (float) t.buildingDamage : DEFAULT_BLAST;
        if (AltarDefense.inReach(lvl, src.position(), BLAST_MONOLITH)) AltarDefense.externalHit(lvl, dmg);
        com.wavesurvivor.horde.kingdom.KingdomClaim.explosionDamage(BlockPos.containing(src.position()), BLAST_RADIUS, dmg);
    }
}
