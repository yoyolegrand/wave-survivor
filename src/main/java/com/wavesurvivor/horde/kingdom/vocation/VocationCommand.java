package com.wavesurvivor.horde.kingdom.vocation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wavesurvivor.horde.kingdom.KingdomRoles;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig.Branch;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig.RoleTree;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig.Talent;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig.Ultimate;
import com.wavesurvivor.horde.renaissance.RenaissanceStore;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /ws vocation … (opérateurs) : outil de test de l'étape « données » des Vocations, avant les écrans.
 *   info [rôle]            Maîtrise, points et talents du joueur (tous les rôles si aucun n'est donné)
 *   xp <rôle> <n>          ajoute de l'XP de Maîtrise
 *   buy <rôle> <talent>    +1 rang dans un talent
 *   ultimate <rôle> <id>   choisit le talent ultime
 *   reset <rôle>           réinitialise l'arbre (PR)
 */
public final class VocationCommand {

    private VocationCommand() {}

    public static ArgumentBuilder<CommandSourceStack, ?> build() {
        return Commands.literal("vocation").requires(src -> src.hasPermission(2))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, null))
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(roleIds(), b))
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "role")))))
                .then(Commands.literal("xp")
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(roleIds(), b))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                        .executes(VocationCommand::xp))))
                .then(Commands.literal("buy")
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(roleIds(), b))
                                .then(Commands.argument("talent", StringArgumentType.word())
                                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(talentIds(c), b))
                                        .executes(VocationCommand::buy))))
                .then(Commands.literal("ultimate")
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(roleIds(), b))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(VocationCommand::ultimate))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(roleIds(), b))
                                .executes(VocationCommand::reset)));
    }

    private static java.util.List<String> roleIds() {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (KingdomRoles.Role r : KingdomRoles.Role.values()) l.add(r.id);
        return l;
    }

    private static java.util.List<String> talentIds(CommandContext<CommandSourceStack> ctx) {
        java.util.List<String> l = new java.util.ArrayList<>();
        KingdomRoles.Role r = KingdomRoles.Role.byId(StringArgumentType.getString(ctx, "role"));
        RoleTree t = r == null ? null : VocationsConfig.tree(r.id);
        if (t != null) for (Branch b : t.branches()) for (Talent tl : b.talents()) l.add(tl.id());
        return l;
    }

    private static KingdomRoles.Role role(CommandContext<CommandSourceStack> ctx) {
        KingdomRoles.Role r = KingdomRoles.Role.byId(StringArgumentType.getString(ctx, "role"));
        if (r == null) ctx.getSource().sendFailure(Component.literal(WSLang.t("voc.cmd.unknown_role")));
        return r;
    }

    private static int info(CommandContext<CommandSourceStack> ctx, String roleId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        for (KingdomRoles.Role r : KingdomRoles.Role.values()) {
            if (roleId != null && !r.id.equalsIgnoreCase(roleId)) continue;
            ctx.getSource().sendSuccess(() -> Component.literal(WSLang.t("voc.cmd.line", r.id, Vocations.level(p, r), VocationsConfig.maxLevel(),
                    Vocations.xpIntoLevel(p, r), Vocations.xpForNext(p, r), Vocations.pointsFree(p, r), Vocations.pointsSpent(p, r),
                    Vocations.ultimate(p, r).isEmpty() ? "-" : Vocations.ultimate(p, r))), false);
            if (roleId == null) continue;
            RoleTree tree = VocationsConfig.tree(r.id);
            if (tree == null) continue;
            for (Branch b : tree.branches()) {
                StringBuilder sb = new StringBuilder(b.id()).append(" (").append(Vocations.branchPoints(p, r, b.id())).append(") : ");
                for (Talent t : b.talents()) sb.append(t.id()).append(' ').append(Vocations.rank(p, r, t.id())).append('/').append(t.ranks()).append("  ");
                ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
            }
            StringBuilder u = new StringBuilder(WSLang.t("voc.cmd.ultimates")).append(' ');
            for (Ultimate x : tree.ultimates()) u.append(x.id()).append("  ");
            ctx.getSource().sendSuccess(() -> Component.literal(u.toString()), false);
        }
        return 1;
    }

    private static int xp(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        KingdomRoles.Role r = role(ctx);
        if (r == null) return 0;
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int n = IntegerArgumentType.getInteger(ctx, "n");
        int gained = Vocations.addXp(p, r, n);
        ctx.getSource().sendSuccess(() -> Component.literal(WSLang.t("voc.cmd.xp", n, r.id, Vocations.level(p, r), gained)), false);
        return 1;
    }

    private static int buy(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        KingdomRoles.Role r = role(ctx);
        if (r == null) return 0;
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        return report(ctx, Vocations.buyTalent(p, r, StringArgumentType.getString(ctx, "talent")));
    }

    private static int ultimate(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        KingdomRoles.Role r = role(ctx);
        if (r == null) return 0;
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        return report(ctx, Vocations.chooseUltimate(p, r, StringArgumentType.getString(ctx, "id")));
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        KingdomRoles.Role r = role(ctx);
        if (r == null) return 0;
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        return report(ctx, Vocations.reset(p, r));
    }

    private static int report(CommandContext<CommandSourceStack> ctx, Vocations.Result res) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (res == Vocations.Result.OK) {
            ctx.getSource().sendSuccess(() -> Component.literal(WSLang.t("voc.cmd.ok", RenaissanceStore.getPoints(p))), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal(WSLang.t("voc.res." + res.name().toLowerCase())));
        return 0;
    }
}
