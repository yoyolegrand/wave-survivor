package com.wavesurvivor.horde.rift;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commande /ws rift (opérateurs), pour tester la Faille :
 *   open     ouvre une Faille à l'endroit où l'on se tient (réglages par défaut, sans autel) et y envoie les joueurs proches ;
 *   status   dit si une Faille est ouverte (et si le mod Pehkui est présent) ;
 *   enter    envoie le joueur sur l'île de la Faille ouverte ;
 *   leave    le renvoie là où il était ;
 *   stop     ferme la Faille : tout le monde rentre, l'île est vidée.
 */
public final class RiftCommand {

    private RiftCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("rift").requires(src -> src.hasPermission(2))
                .then(Commands.literal("open").executes(ctx -> open(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("enter").executes(ctx -> enter(ctx.getSource())))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())));
    }

    private static int open(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        return RiftManager.open(p, p.serverLevel(), p.blockPosition(), null) ? 1 : 0;
    }

    private static int status(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal(WSLang.t(RiftManager.isActive() ? "rift.status_on" : "rift.status_off",
                WSLang.t(RiftManager.available() ? "rift.pehkui_yes" : "rift.pehkui_no"))), false);
        return 1;
    }

    private static int enter(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        if (!RiftManager.isActive()) {
            src.sendFailure(Component.literal(WSLang.t("rift.not_active")));
            return 0;
        }
        RiftManager.enter(p);
        return 1;
    }

    private static int leave(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        if (p.level().dimension() != RiftManager.RIFT_DIM) {
            src.sendFailure(Component.literal(WSLang.t("rift.not_inside")));
            return 0;
        }
        RiftManager.sendHome(p);
        return 1;
    }

    private static int stop(CommandSourceStack src) {
        if (!RiftManager.isActive()) {
            src.sendFailure(Component.literal(WSLang.t("rift.not_active")));
            return 0;
        }
        RiftManager.stop(src.getServer());
        src.sendSuccess(() -> Component.literal(WSLang.t("rift.stopped")), true);
        return 1;
    }
}
