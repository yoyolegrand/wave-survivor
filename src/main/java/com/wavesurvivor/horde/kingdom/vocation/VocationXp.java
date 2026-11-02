package com.wavesurvivor.horde.kingdom.vocation;

import com.wavesurvivor.horde.kingdom.KingdomRoles;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.EventFeedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * VOCATIONS — XP de Maîtrise gagnée PENDANT une partie Kingdom.
 * L'XP s'accumule en réserve (par joueur et par rôle tenu au moment de l'action), puis est versée à la fin de la partie :
 * ×2 en victoire, ×0,5 en défaite (valeurs dans vocations.json). Seuls les joueurs connectés à la fin reçoivent leur XP.
 * Les actions et leur XP viennent de vocations.json (xp.actions.<rôle>.<action>) ; chaque rôle gagne aussi un peu d'XP par
 * Assaut survécu (xp.perWave).
 */
public final class VocationXp {

    private VocationXp() {}

    private static final Map<UUID, EnumMap<KingdomRoles.Role, Double>> PENDING = new HashMap<>();
    /** Pièces gagnées par le royaume, pas encore converties en XP d'Intendant. */
    private static double coinXp = 0;

    private static void add(UUID id, KingdomRoles.Role r, double xp) {
        if (xp <= 0) return;
        PENDING.computeIfAbsent(id, k -> new EnumMap<>(KingdomRoles.Role.class)).merge(r, xp, Double::sum);
    }

    /** Action accomplie par ce joueur dans son rôle (ignorée s'il ne tient pas ce rôle). */
    public static void award(ServerPlayer p, KingdomRoles.Role r, String action) {
        if (p == null || !KingdomRoles.has(p, r)) return;
        add(p.getUUID(), r, VocationsConfig.xpAction(r.id, action));
    }

    /** Action accomplie pour le compte du rôle (pas de joueur précis) : créditée à celui qui le tient. */
    public static void awardHolder(MinecraftServer server, KingdomRoles.Role r, String action) {
        award(KingdomRoles.holder(server, r), r, action);
    }

    /** Pièces gagnées par le royaume : l'Intendant en tire de l'XP (xp.actions.quartermaster.coins_per_xp pièces = 1 XP). */
    public static void coins(MinecraftServer server, int n) {
        double per = VocationsConfig.xpAction(KingdomRoles.Role.QUARTERMASTER.id, "coins_per_xp");
        if (n <= 0 || per <= 0) return;
        ServerPlayer q = KingdomRoles.holder(server, KingdomRoles.Role.QUARTERMASTER);
        if (q == null || !KingdomRoles.has(q, KingdomRoles.Role.QUARTERMASTER)) return;
        coinXp += n / per;
        if (coinXp < 1) return;
        int whole = (int) coinXp;
        coinXp -= whole;
        add(q.getUUID(), KingdomRoles.Role.QUARTERMASTER, whole);
    }

    /** Assaut survécu : chaque joueur qui a un rôle gagne l'XP de base. */
    public static void waveSurvived(MinecraftServer server) {
        int xp = VocationsConfig.xpPerWave();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            KingdomRoles.Role r = KingdomRoles.of(p);
            if (r != null) add(p.getUUID(), r, xp);
        }
    }

    /** XP en réserve d'un joueur pour un rôle (avant multiplicateur de fin de partie). */
    public static int pending(UUID id, KingdomRoles.Role r) {
        EnumMap<KingdomRoles.Role, Double> m = PENDING.get(id);
        return m == null ? 0 : (int) Math.round(m.getOrDefault(r, 0.0));
    }

    /** Fin de partie : verse l'XP (×victoire ou ×défaite), annonce le gain et les niveaux gagnés, puis vide la réserve. */
    public static void finish(MinecraftServer server, boolean victory) {
        double mult = victory ? VocationsConfig.victoryMult() : VocationsConfig.defeatMult();
        for (Map.Entry<UUID, EnumMap<KingdomRoles.Role, Double>> e : PENDING.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            for (Map.Entry<KingdomRoles.Role, Double> en : e.getValue().entrySet()) {
                KingdomRoles.Role r = en.getKey();
                long gain = Math.round(en.getValue() * mult);
                if (gain <= 0) continue;
                int before = Vocations.level(p, r);
                Vocations.addXp(p, r, gain);
                int after = Vocations.level(p, r);
                String role = Vocations.roleName(r);
                p.sendSystemMessage(WSLang.c(victory ? "voc.xp.gain_win" : "voc.xp.gain_loss", gain, role, mult));
                if (after > before) {
                    EventFeedPacket.toPlayer(p, WSLang.c("voc.xp.level_up", role, after, VocationsConfig.maxLevel()),
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(r.icon).toString(), EventFeedPacket.BLESSING, true);
                    p.sendSystemMessage(WSLang.c("voc.xp.points", Vocations.pointsFree(p, r), role));
                }
            }
        }
        for (UUID id : new java.util.ArrayList<>(PENDING.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) VocationSync.send(p, false);
        }
        clear();
    }

    public static void clear() {
        PENDING.clear();
        coinXp = 0;
    }
}
