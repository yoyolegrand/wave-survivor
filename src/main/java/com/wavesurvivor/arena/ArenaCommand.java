package com.wavesurvivor.arena;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Commandes d'arènes (opérateurs) :
 *   /ws arena list                          arènes disponibles ;
 *   /ws arena place &lt;arène&gt; [rotation]      pose l'arène devant le joueur (rotation 0 / 90 / 180 / 270, 0 par défaut) pour la tester.
 */
public final class ArenaCommand {

    /** Arènes livrées avec le mod (data/wavesurvivor/arenas/&lt;id&gt;.json) : ajouter ici chaque nouvelle arène. */
    public static final List<String> BUILTIN = List.of("necropole_rois_dechus", "breche_infernale", "ilot_du_neant", "recif_englouti", "temple_de_re");

    private ArenaCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("arena").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> {
                    List<String> ok = new java.util.ArrayList<>();
                    for (String id : BUILTIN) { ArenaBlueprint b = ArenaBlueprint.load(id); if (b != null && b.available()) ok.add(id); }
                    ctx.getSource().sendSuccess(() -> Component.literal("§6[Arènes] §f" + String.join(", ", ok)), false);
                    return 1;
                }))
                .then(Commands.literal("place")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(BUILTIN, b))
                                .executes(ctx -> place(ctx, "0"))
                                .then(Commands.argument("rotation", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("0", "90", "180", "270"), b))
                                        .executes(ctx -> place(ctx, StringArgumentType.getString(ctx, "rotation"))))));
    }

    static Rotation parseRotation(String s) {
        return switch (s) {
            case "90" -> Rotation.CLOCKWISE_90;
            case "180" -> Rotation.CLOCKWISE_180;
            case "270" -> Rotation.COUNTERCLOCKWISE_90;
            case "0" -> Rotation.NONE;
            default -> null;
        };
    }

    private static int place(CommandContext<CommandSourceStack> ctx, String rotArg) {
        CommandSourceStack src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        Rotation rot = parseRotation(rotArg);
        if (rot == null) {
            src.sendFailure(Component.literal("Rotation invalide : 0, 90, 180 ou 270."));
            return 0;
        }
        ArenaBlueprint bp = ArenaBlueprint.load(name);
        if (bp == null) {
            src.sendFailure(Component.literal("Arène introuvable ou illisible : « " + name + " » (voir le journal, /ws arena list)."));
            return 0;
        }
        if (!bp.available()) {
            src.sendFailure(Component.literal("Cette arène demande le mod « " + bp.requiresMod() + " » : il n'est pas installé."));
            return 0;
        }
        ServerPlayer p;
        try { p = src.getPlayerOrException(); } catch (Exception e) {
            src.sendFailure(Component.literal("Cette commande doit être lancée par un joueur."));
            return 0;
        }
        // Centre de l'arène : devant le joueur, à distance du bord du cercle + 4 blocs
        Vec3 look = p.getLookAngle();
        double lx = look.x, lz = look.z, len = Math.hypot(lx, lz);
        if (len < 1e-3) { lx = 0; lz = 1; len = 1; }
        double dist = Math.max(bp.sx, bp.sz) / 2.0 + 4;
        BlockPos center = BlockPos.containing(p.getX() + lx / len * dist, bp.floating() ? p.getY() + 2 : p.getY(), p.getZ() + lz / len * dist); // île flottante : le sol est à ta hauteur + 2
        long t0 = System.currentTimeMillis();
        ArenaBlueprint.Placement pl = bp.place(p.serverLevel(), center, rot);
        ArenaAltar.install(p.serverLevel(), bp, pl.origin().getX(), pl.origin().getZ(), pl.groundY(), rot, null);
        BlockPos altar = pl.marker("monolith");
        if (altar != null) ArenaAltar.adopt(p.serverLevel(), altar);
        BlockPos c = pl.center();
        src.sendSuccess(() -> Component.literal("§a[Arènes] « " + name + " » posée (rotation " + rotArg + "°) : centre " + c.getX() + " " + pl.groundY() + " " + c.getZ()
                + " · " + (System.currentTimeMillis() - t0) + " ms"), true);
        return 1;
    }
}
