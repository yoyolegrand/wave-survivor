package com.wavesurvivor.horde.mutator;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * ÉTAT DES MUTATEURS (côté serveur) : choisis sur l'écran de l'autel (« en attente » pendant le compte à rebours),
 * activés au démarrage de la horde, effacés à sa fin. Les effets interrogent {@link #on(Mutator)}.
 */
public final class HordeMutators {

    private static final Set<Mutator> PENDING = EnumSet.noneOf(Mutator.class);
    private static final Set<Mutator> ACTIVE = EnumSet.noneOf(Mutator.class);

    private HordeMutators() {}

    /** Écran de l'autel : mutateurs cochés pour la prochaine horde. */
    public static synchronized void setPending(Collection<Mutator> ms) {
        PENDING.clear();
        if (ms != null) PENDING.addAll(ms);
    }

    /** Démarrage de la horde : les mutateurs en attente deviennent actifs (sans « Une seule vie » en Kingdom). */
    public static synchronized void begin(MinecraftServer server, boolean kingdom) {
        ACTIVE.clear();
        ACTIVE.addAll(PENDING);
        PENDING.clear();
        boolean removed = kingdom && ACTIVE.remove(Mutator.ONE_LIFE);
        SERVER = server;
        if (!ACTIVE.isEmpty()) MutatorEffects.onBegin(server, kingdom);
        if (ACTIVE.isEmpty() || server == null) return;
        StringBuilder names = new StringBuilder();
        for (Mutator m : ACTIVE) names.append(names.length() > 0 ? "\u00a77, " : "").append("\u00a7f").append(m.icon).append(" ").append(WSLang.t("mutator." + m.id));
        String mult = String.format(java.util.Locale.ROOT, "%.2f", multiplier()).replace('.', ',');
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(WSLang.t("mutator.announce", names.toString(), mult)));
            if (removed) p.sendSystemMessage(Component.literal(WSLang.t("mutator.one_life_kingdom")));
        }
        WaveSurvivorMod.LOGGER.info("[Mutateurs] Actifs : {} (×{})", Mutator.join(ACTIVE), mult);
    }

    /** Fin de horde : plus aucun mutateur actif (règles de jeu restaurées, spectateurs remis en Survie). */
    public static synchronized void clear() {
        if (!ACTIVE.isEmpty()) MutatorEffects.onEnd(SERVER);
        ACTIVE.clear();
    }

    private static MinecraftServer SERVER;

    public static synchronized boolean on(Mutator m) { return ACTIVE.contains(m); }

    public static synchronized boolean any() { return !ACTIVE.isEmpty(); }

    public static synchronized Set<Mutator> active() { return Collections.unmodifiableSet(EnumSet.copyOf(ACTIVE.isEmpty() ? EnumSet.noneOf(Mutator.class) : ACTIVE)); }

    /** Multiplicateur de récompenses de la horde en cours (1,0 sans mutateur). */
    public static synchronized double multiplier() { return Mutator.multiplier(ACTIVE); }
}
