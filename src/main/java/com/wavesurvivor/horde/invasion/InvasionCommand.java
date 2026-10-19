package com.wavesurvivor.horde.invasion;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.Random;

/**
 * Commande /ws invasion (opérateurs) :
 *   start                       invasion tirée au sort parmi celles du mod, sur le village où l'on se trouve ;
 *   start <nom> [présage 1-5]   une invasion précise (« vanilla » = raid vanilla classique) ;
 *   stop                        arrête l'invasion en cours (sans récompense) ;
 *   skip                        passe la vague en cours (la dernière termine l'invasion, avec récompenses).
 */
public final class InvasionCommand {

    private static final Random RNG = new Random();

    private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase().replace("\"", "");
        List<String> names = new java.util.ArrayList<>();
        names.add("vanilla");
        for (HordeConfigMultiData h : InvasionManager.pool()) names.add(h.hordeName);
        for (String n : names) {
            if (n.toLowerCase().startsWith(remaining)) builder.suggest(n.contains(" ") ? "\"" + n + "\"" : n);
        }
        return builder.buildFuture();
    };

    private InvasionCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("invasion").requires(src -> src.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx.getSource(), null, 1))
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(NAMES)
                                .executes(ctx -> start(ctx.getSource(), StringArgumentType.getString(ctx, "name"), 1))
                                .then(Commands.argument("omen", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> start(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "omen"))))))
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("skip").executes(ctx -> skip(ctx.getSource())));
    }

    private static int fail(CommandSourceStack src, String key) {
        src.sendFailure(Component.literal(WSLang.t(key)));
        return 0;
    }

    private static int start(CommandSourceStack src, String name, int omen) {
        ServerPlayer p;
        try { p = src.getPlayerOrException(); } catch (Exception e) { return fail(src, "invasion.err.player"); }
        if (!p.serverLevel().isVillage(p.blockPosition())) return fail(src, "invasion.err.not_village");

        // Raid vanilla classique : on donne le Mauvais présage, sans passer par la roulette
        if (name != null && name.equalsIgnoreCase("vanilla")) {
            InvasionEvents.forceVanilla(p);
            p.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 120000, Math.max(0, omen - 1), false, false, true));
            src.sendSuccess(() -> Component.literal(WSLang.t("invasion.cmd.vanilla")), false);
            return 1;
        }
        HordeConfigMultiData horde = null;
        List<HordeConfigMultiData> pool = InvasionManager.pool();
        if (name == null) {
            if (!pool.isEmpty()) horde = pool.get(RNG.nextInt(pool.size()));
        } else {
            for (HordeConfigMultiData h : pool) if (h.hordeName.equalsIgnoreCase(name.trim())) horde = h;
        }
        if (horde == null) return fail(src, "invasion.err.no_horde");
        String err = InvasionManager.startForPlayer(p, horde, omen);
        if (err != null) return fail(src, err);
        final String shown = WSLang.t(horde.hordeName);
        src.sendSuccess(() -> Component.literal(WSLang.t("invasion.cmd.started", shown)), true);
        return 1;
    }

    private static int skip(CommandSourceStack src) {
        String err = InvasionManager.skip(src.getServer());
        if (err != null) return fail(src, err);
        src.sendSuccess(() -> Component.literal(WSLang.t("invasion.cmd.skipped")), true);
        return 1;
    }

    private static int stop(CommandSourceStack src) {
        if (!InvasionManager.stop(src.getServer())) return fail(src, "invasion.err.none");
        src.sendSuccess(() -> Component.literal(WSLang.t("invasion.cmd.stopped")), true);
        return 1;
    }
}
