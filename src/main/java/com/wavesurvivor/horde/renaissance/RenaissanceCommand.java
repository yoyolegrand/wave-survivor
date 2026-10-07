package com.wavesurvivor.horde.renaissance;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

/**
 * /renaissance                         → ouvre l'écran (onglet Sacrifice)
 * /renaissance shop                    → ouvre l'écran (onglet Boutique)
 * /renaissance points                  → affiche ses PR
 * /renaissance points add|set <joueurs> <n>   (op)
 * /renaissance altar                   → donne un Autel de Renaissance (op)
 */
public class RenaissanceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("renaissance")
                .executes(ctx -> open(ctx, RenaissanceManager.TAB_SACRIFICE))
                .then(Commands.literal("shop")
                        .executes(ctx -> open(ctx, RenaissanceManager.TAB_SHOP))
                        .then(Commands.literal("edit").requires(s -> s.hasPermission(2))
                                .executes(ctx -> {
                                    RenaissanceManager.openEditor(ctx.getSource().getPlayerOrException());
                                    return 1;
                                })))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            RenaissanceManager.openEditor(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("points")
                        .executes(RenaissanceCommand::showPoints)
                        .then(Commands.literal("add").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> modify(ctx, false)))))
                        .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> modify(ctx, true))))))
                .then(Commands.literal("altar").requires(s -> s.hasPermission(2))
                        .executes(RenaissanceCommand::giveAltar))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(RenaissanceCommand::resetEverything)))
                .then(Commands.literal("stats")
                        .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(RenaissanceCommand::resetStats)))));
    }

    /** /renaissance reset : stats permanentes + compteurs d'achats de la boutique (PR non remboursés). */
    private static int resetEverything(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer p : targets) {
            RenaissanceStats.resetAll(p);
            RenaissanceStore.clearPurchases(p);
            RenaissanceManager.sendState(p, false, RenaissanceManager.TAB_SHOP, false);
        }
        int n = targets.size();
        ctx.getSource().sendSuccess(() -> com.wavesurvivor.i18n.WSLang.c("renaissance.reset_all", n), true);
        return n;
    }

    private static int resetStats(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer p : targets) {
            RenaissanceStats.resetAll(p);
            RenaissanceManager.sendState(p, false, RenaissanceManager.TAB_SHOP, false);
        }
        int n = targets.size();
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.renaissance_stats_permanentes_reset_pour")
                + n + com.wavesurvivor.i18n.WSLang.t("srv.joueur_s_pr_non_rembourses")), true);
        return n;
    }

    private static int open(CommandContext<CommandSourceStack> ctx, int tab) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        RenaissanceManager.open(p, tab);
        return 1;
    }

    private static int showPoints(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.points_de_renaissance") + RenaissanceStore.getPoints(p)
                + com.wavesurvivor.i18n.WSLang.t("srv.pr_gagnes_au_total") + RenaissanceStore.getTotalEarned(p) + ")"));
        return 1;
    }

    private static int modify(CommandContext<CommandSourceStack> ctx, boolean set) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        for (ServerPlayer p : targets) {
            if (set) RenaissanceStore.setPoints(p, amount);
            else RenaissanceStore.addPoints(p, amount);
            RenaissanceManager.sendState(p, false, RenaissanceManager.TAB_SACRIFICE, false);
        }
        int n = targets.size();
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.renaissance_162a") + (set ? com.wavesurvivor.i18n.WSLang.t("srv.pr_fixes_a") : "+")
                + amount + com.wavesurvivor.i18n.WSLang.t("srv.pr_pour") + n + com.wavesurvivor.i18n.WSLang.t("srv.joueur_s")), true);
        return n;
    }

    private static int giveAltar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ItemStack st = new ItemStack(ModItems.RENAISSANCE_ALTAR_ITEM.get());
        if (!p.getInventory().add(st)) p.drop(st, false);
        p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_de_renaissance_recu")));
        return 1;
    }
}
