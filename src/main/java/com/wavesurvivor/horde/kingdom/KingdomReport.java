package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BILAN DE FIN D'ASSAUT (Kingdom) — compté pendant l'Assaut, publié au début du Calme :
 * monstres vaincus (par joueur, par les soldats, par les défenses et le reste), ressources gagnées par le trésor,
 * dégâts subis par les constructions et éléments détruits, MVP de l'Assaut.
 */
public class KingdomReport {

    private static boolean on = false;
    private static int cycle = 0;
    private static final Map<UUID, Integer> PLAYER_KILLS = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();
    private static int soldierKills = 0, otherKills = 0;
    private static final int[] GAINED = new int[KingdomTreasury.Res.values().length];
    private static float structDamage = 0f;
    private static int structLost = 0;

    /** Début d'un Assaut : on remet les compteurs à zéro. */
    public static void start(int assault) {
        on = true;
        cycle = assault;
        PLAYER_KILLS.clear();
        NAMES.clear();
        soldierKills = 0;
        otherKills = 0;
        java.util.Arrays.fill(GAINED, 0);
        structDamage = 0f;
        structLost = 0;
    }

    public static void stop() { on = false; }

    static void onGain(KingdomTreasury.Res r, int n) {
        if (on && n > 0) GAINED[r.ordinal()] += n;
    }

    static void onStructureDamage(float d) {
        if (on && d > 0) structDamage += d;
    }

    static void onStructureLost() {
        if (on) structLost++;
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (!on || !(e.getEntity() instanceof Mob m) || m.level().isClientSide) return;
        // Même critère que les pièges, mais sans « encore vivant » (ici le monstre vient justement de mourir)
        if (m.getPersistentData().getBoolean("ws_kingdom_soldier") || m instanceof com.wavesurvivor.entity.KingdomSoldier
                || m instanceof com.wavesurvivor.entity.BrecheEntity) return;
        if (!(m instanceof net.minecraft.world.entity.monster.Enemy) && com.wavesurvivor.horde.spawn.MobRegistry.get(m.getUUID()) == null) return;
        Entity src = e.getSource().getEntity();
        if (src instanceof ServerPlayer p) {
            PLAYER_KILLS.merge(p.getUUID(), 1, Integer::sum);
            NAMES.put(p.getUUID(), p.getGameProfile().getName());
        } else if (src != null && src.getPersistentData().getBoolean("ws_kingdom_soldier")) {
            soldierKills++;
        } else {
            otherKills++;   // tours, pièges, magie, feu, chute…
        }
    }

    /** Fin de l'Assaut (début du Calme) : publie le bilan à tous les joueurs. */
    public static void finish(ServerLevel level) {
        if (!on || level == null) return;
        on = false;
        int playerKills = PLAYER_KILLS.values().stream().mapToInt(Integer::intValue).sum();
        int total = playerKills + soldierKills + otherKills;
        List<Component> lines = new ArrayList<>();
        lines.add(WSLang.c("kingdom.report.head", cycle, total));
        // Joueurs (détail par joueur, du meilleur au moins bon)
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(PLAYER_KILLS.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder det = new StringBuilder();
        for (int i = 0; i < Math.min(4, sorted.size()); i++) {
            if (i > 0) det.append(", ");
            det.append(NAMES.getOrDefault(sorted.get(i).getKey(), "?")).append(' ').append(sorted.get(i).getValue());
        }
        lines.add(WSLang.c("kingdom.report.kills", playerKills, det.length() > 0 ? " (" + det + ")" : "", soldierKills, otherKills));
        // Ressources gagnées
        StringBuilder res = new StringBuilder();
        for (KingdomTreasury.Res r : KingdomTreasury.Res.values()) {
            int n = GAINED[r.ordinal()];
            if (n <= 0) continue;
            res.append(" §a+").append(n).append(" §f").append(WSLang.t("workshop.res." + r.name().toLowerCase()));
        }
        lines.add(WSLang.c("kingdom.report.resources", res.length() > 0 ? res.toString() : " §7—"));
        // Constructions
        lines.add(WSLang.c("kingdom.report.damage", Math.round(structDamage), structLost));
        // MVP
        if (!sorted.isEmpty()) {
            lines.add(WSLang.c("kingdom.report.mvp", NAMES.getOrDefault(sorted.get(0).getKey(), "?"), sorted.get(0).getValue()));
        }
        for (ServerPlayer p : level.players()) for (Component c : lines) p.sendSystemMessage(c);
    }
}
