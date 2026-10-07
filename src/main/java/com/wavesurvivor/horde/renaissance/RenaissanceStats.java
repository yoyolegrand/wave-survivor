package com.wavesurvivor.horde.renaissance;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Stats permanentes Renaissance : modifiers d'attributs vanilla (même principe que les bénédictions),
 * mais PERMANENTS : pas de clear en fin de horde, réappliqués au login et au respawn.
 *
 * UUID stable par stat → un seul modifier par stat, dont l'amount = amount × niveau.
 */
public class RenaissanceStats {

    public static UUID modifierUuid(String statId) {
        return UUID.nameUUIDFromBytes(("ws_renaissance_stat_" + statId).getBytes(StandardCharsets.UTF_8));
    }

    /** Pose (ou retire si level=0) le modifier d'une stat. */
    public static void apply(ServerPlayer p, RenaissanceConfigData.StatUpgrade s, int level) {
        Attribute attr = s.resolveAttribute();
        if (attr == null) {
            WaveSurvivorMod.LOGGER.warn("[Renaissance] Attribut introuvable pour la stat '{}' : {}", s.id, s.attribute);
            return;
        }
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        UUID uuid = modifierUuid(s.id);
        if (inst.getModifier(uuid) != null) inst.removeModifier(uuid);
        if (level > 0) {
            inst.addPermanentModifier(new AttributeModifier(uuid, "ws_renaissance_" + s.id, s.amount * level, s.op()));
        }
    }

    public static void reapplyAll(ServerPlayer p) {
        RenaissanceConfigData cfg = RenaissanceConfigData.current();
        for (RenaissanceConfigData.StatUpgrade s : cfg.statUpgrades) {
            apply(p, s, RenaissanceStore.getStatLevel(p, s.id));
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    /** Retire tous les modifiers + purge les niveaux (admin). */
    public static void resetAll(ServerPlayer p) {
        RenaissanceConfigData cfg = RenaissanceConfigData.current();
        for (RenaissanceConfigData.StatUpgrade s : cfg.statUpgrades) apply(p, s, 0);
        RenaissanceStore.clearStats(p);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    // ─── Events (enregistré dans WaveSurvivorMod) ───

    public static class Events {
        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer sp) reapplyAll(sp);
        }

        @SubscribeEvent
        public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
            if (e.getEntity() instanceof ServerPlayer sp) {
                reapplyAll(sp);
                sp.setHealth(sp.getMaxHealth()); // respawn avec les PV bonus pleins
            }
        }
    }
}
