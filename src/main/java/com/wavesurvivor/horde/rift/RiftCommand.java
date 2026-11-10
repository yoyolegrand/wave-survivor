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
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("open").executes(ctx -> open(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("enter").executes(ctx -> enter(ctx.getSource())))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("fortress").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(RiftFortress.FORM_IDS, b))
                        .executes(ctx -> fortress(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"), true))
                        .then(Commands.literal("fast").executes(ctx -> fortress(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"), false)))))
                .then(Commands.literal("catalysts").executes(ctx -> catalysts(ctx.getSource())))
                .then(Commands.literal("skill").then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(RiftTitan.DEBUG_SKILLS, b))
                        .executes(ctx -> skill(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name")))));
    }

    /**
     * Test : pose la forme {@code id} dans la Faille (dimension de la Faille), à la place de la forteresse actuelle — avec la cinématique
     * de construction, ou d'un coup avec « fast ».
     */
    private static int fortress(CommandSourceStack src, String id, boolean cine) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        if (p.level().dimension() != RiftManager.RIFT_DIM) {
            src.sendFailure(Component.literal(WSLang.t("rift.test.need_dim")));
            return 0;
        }
        if (!RiftFortress.testBuild(p.serverLevel(), id, RiftManager.testRadius(), cine)) {
            src.sendFailure(Component.literal(WSLang.t("rift.test.fortress_fail", id)));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(WSLang.t("rift.test.fortress_ok", WSLang.t("rift.fortress." + id))), false);
        return 1;
    }

    /** Test : cristaux de catalyseur aux 6 repères de la forme posée (casser leurs chaînes, voir les gardiens). */
    private static int catalysts(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        if (p.level().dimension() != RiftManager.RIFT_DIM) {
            src.sendFailure(Component.literal(WSLang.t("rift.test.need_dim")));
            return 0;
        }
        int n = RiftFortress.testCatalysts(p.serverLevel(), RiftManager.testRadius(), 300f);
        src.sendSuccess(() -> Component.literal(WSLang.t("rift.test.catalysts_ok", n)), false);
        return 1;
    }

    /** Test : le Titan lance tout de suite l'attaque demandée (météores, traqueurs, lave, géant, piétinement, rocher, regard, rugissement, charge). */
    private static int skill(CommandSourceStack src, String name) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        if (!RiftManager.isActive() || !RiftTitan.debugSkill(p.serverLevel(), name)) {
            src.sendFailure(Component.literal(WSLang.t("rift.titan.skill_fail", name)));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(WSLang.t("rift.titan.skill_ok", name)), false);
        return 1;
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
