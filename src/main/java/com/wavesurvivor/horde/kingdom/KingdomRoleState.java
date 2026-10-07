package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.RoleStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Synchronise l'état personnel des rôles vers les clients : réactifs (Alchimiste), présages (Augure),
 * contrat en cours (Chasseur de primes, envoyé à lui seul).
 */
public final class KingdomRoleState {

    private KingdomRoleState() {}

    // ─── Copie client ───
    public static int clientReagents = 0;
    public static String[] clientOmenOptions = new String[0];
    public static String clientOmenChosen = "";
    public static String clientOmenActive = "";
    public static String clientContract = "";
    public static int clientContractProgress = 0, clientContractTarget = 0, clientContractReward = 0;
    public static int[] clientFlaskLevels = {1, 1, 1, 1, 1};

    public static void syncTo(ServerPlayer p) {
        if (NetworkHandler.CHANNEL == null || p == null) return;
        StringBuilder opts = new StringBuilder();
        // Présages proposés : envoyés à l'Augure seul (lui seul choisit)
        if (KingdomRoles.has(p, KingdomRoles.Role.AUGUR)) {
            for (KingdomOmens.Omen o : KingdomOmens.options()) opts.append(opts.length() > 0 ? "," : "").append(o.id());
        }
        KingdomOmens.Omen c = KingdomOmens.chosen(), a = KingdomOmens.active();
        boolean hunter = p.getUUID().equals(KingdomRoleExtras.contractHunter());
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new RoleStatePacket(
                KingdomAlchemy.reagents(p.getUUID()), opts.toString(), c == null ? "" : c.id(), a == null ? "" : a.id(),
                hunter ? KingdomRoleExtras.contractId() : "",
                hunter ? KingdomRoleExtras.contractProgress() : 0,
                hunter ? KingdomRoleExtras.contractTarget() : 0,
                hunter ? KingdomRoleExtras.contractReward() : 0,
                KingdomAlchemy.levels(p.getUUID()).clone()));
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) syncTo(p);
    }
}
