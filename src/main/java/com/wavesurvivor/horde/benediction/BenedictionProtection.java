package com.wavesurvivor.horde.benediction;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PROTECTION PENDANT LE CHOIX D'UNE BÉNÉDICTION (1.7, multijoueur) : le jeu ne se met pas en pause, alors le joueur qui
 * choisit est ignoré des monstres (ils perdent leur cible, ne peuvent plus le viser) et invulnérable, jusqu'à son choix,
 * 30 s au maximum, ou sa déconnexion. En solo, le jeu se met en pause à la place (BenedictionSelectionScreen).
 */
public class BenedictionProtection {

    /** Durée maximale de la protection (ticks). */
    private static final long MAX_TICKS = 600;
    private static final Map<UUID, Long> UNTIL = new ConcurrentHashMap<>();

    /** Faut-il protéger ? Faux en solo (le jeu est alors en pause pendant le choix). */
    public static boolean needed(MinecraftServer srv) {
        return srv != null && (srv.isDedicatedServer() || srv.getPlayerCount() > 1 || srv.isPublished() || !srv.isSingleplayer());
    }

    public static boolean isProtected(ServerPlayer p) {
        return p != null && UNTIL.containsKey(p.getUUID());
    }

    /** Début de la protection : les monstres qui visaient le joueur le lâchent. */
    public static void protect(ServerPlayer p) {
        if (p == null || p.getServer() == null) return;
        UNTIL.put(p.getUUID(), p.getServer().getTickCount() + MAX_TICKS);
        for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), mob -> mob.getTarget() == p)) {
            m.setTarget(null);
            m.getNavigation().stop();
        }
        p.sendSystemMessage(Component.literal(WSLang.t("benediction.protect")));
    }

    /** Fin de la protection (choix fait, délai écoulé, déconnexion). */
    public static void release(ServerPlayer p) {
        if (p != null) UNTIL.remove(p.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer p && isProtected(p)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isProtected(p) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UNTIL.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || UNTIL.isEmpty() || event.getServer().getTickCount() % 20 != 0) return;
        long now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, Long>> it = UNTIL.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue() <= now) it.remove();
    }
}
