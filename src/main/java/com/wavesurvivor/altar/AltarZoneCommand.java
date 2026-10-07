package com.wavesurvivor.altar;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/**
 * Sous-commandes admin de la zone (claim) d'autel, montées sous /ws altar :
 *
 *   /ws altar zone <horde>                          → affiche rayon + sol
 *   /ws altar zone <horde> radius <1-48>            → rayon du disque
 *   /ws altar zone <horde> floor add <bloc> <poids> → ajoute un bloc au sol (tirage pondéré)
 *   /ws altar zone <horde> floor clear              → vide le sol (zone désactivée)
 *
 * Sauvegardé dans altar_recipes.json. S'applique aux PROCHAINES liaisons.
 */
public class AltarZoneCommand {

    private static final SuggestionProvider<CommandSourceStack> BLOCK_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), builder);

    private static final SuggestionProvider<CommandSourceStack> CHEST_KEYS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    com.wavesurvivor.horde.roulette.RouletteChestRegistry.allKeys(), builder);

    public static LiteralArgumentBuilder<CommandSourceStack> build(SuggestionProvider<CommandSourceStack> hordeNames) {
        return Commands.literal("zone")
                .then(Commands.argument("hordeName", StringArgumentType.string()).suggests(hordeNames)
                        .executes(AltarZoneCommand::show)
                        .then(Commands.literal("radius")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 48))
                                        .executes(AltarZoneCommand::setRadius)))
                        .then(Commands.literal("floor")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("block", ResourceLocationArgument.id()).suggests(BLOCK_IDS)
                                                .executes(ctx -> addFloor(ctx, 1))
                                                .then(Commands.argument("weight", IntegerArgumentType.integer(1, 1000))
                                                        .executes(ctx -> addFloor(ctx, IntegerArgumentType.getInteger(ctx, "weight"))))))
                                .then(Commands.literal("clear").executes(AltarZoneCommand::clearFloor)))
                        // ─── Emplacements manuels (depuis ta position, relatifs à l'autel le plus proche de cette horde) ───
                        .then(Commands.literal("slot")
                                .then(Commands.literal("add")
                                        .then(Commands.literal("chest")
                                                .then(Commands.argument("configKey", StringArgumentType.string()).suggests(CHEST_KEYS)
                                                        .executes(ctx -> addSlot(ctx, "chest"))))
                                        .then(Commands.literal("merchant").executes(ctx -> addSlot(ctx, "merchant"))))
                                .then(Commands.literal("list").executes(AltarZoneCommand::listSlots))
                                .then(Commands.literal("clear").executes(AltarZoneCommand::clearSlots)))
                        // ─── Coffres automatiques ───
                        .then(Commands.literal("chests")
                                .then(Commands.literal("max")
                                        .then(Commands.argument("n", IntegerArgumentType.integer(0, 32))
                                                .executes(AltarZoneCommand::setMaxChests)))
                                .then(Commands.literal("exclude")
                                        .then(Commands.argument("configKey", StringArgumentType.string()).suggests(CHEST_KEYS)
                                                .executes(ctx -> toggleExclude(ctx, true))))
                                .then(Commands.literal("include")
                                        .then(Commands.argument("configKey", StringArgumentType.string()).suggests(CHEST_KEYS)
                                                .executes(ctx -> toggleExclude(ctx, false)))))
                        .then(Commands.literal("auto")
                                .then(Commands.literal("on").executes(ctx -> setAuto(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setAuto(ctx, false))))
                        // ─── Style du monolithe appliqué à la liaison ───
                        .then(Commands.literal("style")
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(net.minecraft.world.item.DyeColor.values())
                                                        .map(net.minecraft.world.item.DyeColor::getName), b))
                                        .then(Commands.argument("particles", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        java.util.Arrays.stream(AltarParticles.values()).map(p -> p.id), b))
                                                .executes(AltarZoneCommand::setStyle))))
                        // ─── Défense du Monolithe ───
                        .then(Commands.literal("defense")
                                .then(Commands.literal("on").executes(ctx -> setDefense(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setDefense(ctx, false)))
                                .then(Commands.literal("hp")
                                        .then(Commands.argument("hp", IntegerArgumentType.integer(10, 100000))
                                                .executes(AltarZoneCommand::setDefenseHp)))
                                .then(Commands.literal("spawn")
                                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 20))
                                                .executes(ctx -> {
                                                    int n = AltarDefense.spawnTestProfaners(ctx.getSource().getServer(),
                                                            IntegerArgumentType.getInteger(ctx, "n"));
                                                    if (n == 0) {
                                                        ctx.getSource().sendFailure(Component.literal(
                                                                com.wavesurvivor.i18n.WSLang.t("srv.aucune_defense_en_cours_lance_la_horde_d")));
                                                        return 0;
                                                    }
                                                    ctx.getSource().sendSuccess(() -> Component.literal("§4" + n + com.wavesurvivor.i18n.WSLang.t("srv.profanateur_s_de_test_invoque_s")), true);
                                                    return n;
                                                })))));
    }

    private static int setDefense(CommandContext<CommandSourceStack> ctx, boolean on) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        AltarRecipes.Defense d = AltarRecipes.editDefense(horde);
        d.enabled = on;
        AltarRecipes.save();
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.defense_du_monolithe") + (on ? com.wavesurvivor.i18n.WSLang.t("srv.activee") : com.wavesurvivor.i18n.WSLang.t("srv.desactivee"))
                + com.wavesurvivor.i18n.WSLang.t("srv.pour") + horde + (on ? " §7(" + d.maxHealth + com.wavesurvivor.i18n.WSLang.t("srv.pv_prochaine_horde_lancee_depuis_un_aute") : "")), true);
        return 1;
    }

    private static int setDefenseHp(CommandContext<CommandSourceStack> ctx) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        int hp = IntegerArgumentType.getInteger(ctx, "hp");
        AltarRecipes.editDefense(horde).maxHealth = hp;
        AltarRecipes.save();
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.integrite_du_monolithe_pour") + horde + " §f: §e" + hp + com.wavesurvivor.i18n.WSLang.t("srv.pv")), true);
        return 1;
    }

    private static int setStyle(CommandContext<CommandSourceStack> ctx) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        String color = StringArgumentType.getString(ctx, "color").toLowerCase();
        String particles = StringArgumentType.getString(ctx, "particles").toLowerCase();
        if (net.minecraft.world.item.DyeColor.byName(color, null) == null) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.couleur_inconnue") + color));
            return 0;
        }
        if (!AltarParticles.exists(particles)) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.particules_inconnues") + particles));
            return 0;
        }
        AltarRecipes.Style s = AltarRecipes.editStyle(horde);
        s.color = color;
        s.particles = particles;
        AltarRecipes.save();
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.style_de") + horde + " §f: §e" + color + " §7+ §e"
                + com.wavesurvivor.i18n.WSLang.t(AltarParticles.byId(particles).nameFr) + com.wavesurvivor.i18n.WSLang.t("srv.prochaines_liaisons")), true);
        return 1;
    }

    // ─── Disposition marchands / coffres ───

    /** Autel lié à cette horde le plus proche du joueur, dans sa zone (+2), même dimension. */
    private static AltarStore.AltarEntry nearestAltar(net.minecraft.server.level.ServerPlayer p, String horde) {
        String dim = p.level().dimension().location().toString();
        AltarStore.AltarEntry best = null;
        double bestD = Double.MAX_VALUE;
        for (AltarStore.AltarEntry e : AltarStore.all()) {
            if (!horde.equals(e.hordeName) || !dim.equals(e.dimension)) continue;
            double dx = p.getX() - (e.x + 0.5), dz = p.getZ() - (e.z + 0.5);
            double d = dx * dx + dz * dz;
            double lim = (e.zoneRadius + 2.0) * (e.zoneRadius + 2.0);
            if (d <= lim && d < bestD) { best = e; bestD = d; }
        }
        return best;
    }

    private static int addSlot(CommandContext<CommandSourceStack> ctx, String type) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        net.minecraft.server.level.ServerPlayer p;
        try { p = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) { ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.doit_etre_execute_en_jeu"))); return 0; }

        String key = null;
        if ("chest".equals(type)) {
            key = StringArgumentType.getString(ctx, "configKey");
            if (com.wavesurvivor.horde.roulette.RouletteChestRegistry.get(key) == null) {
                ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.config_de_coffre_inconnue") + key));
                return 0;
            }
        }
        AltarStore.AltarEntry altar = nearestAltar(p, horde);
        if (altar == null) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.place_toi_dans_la_zone_d_un_autel_lie_a") + horde + "."));
            return 0;
        }
        var bp = p.blockPosition();
        int dx = bp.getX() - altar.x, dy = bp.getY() - altar.y, dz = bp.getZ() - altar.z;
        AltarRecipes.editZone(horde).slots.add(new AltarRecipes.Slot(type, key, dx, dy, dz));
        AltarRecipes.save();
        String what = "chest".equals(type) ? com.wavesurvivor.i18n.WSLang.t("srv.coffre") + key : "marchand";
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.slot") + what + com.wavesurvivor.i18n.WSLang.t("srv.ajoute_offset") + dx + ", " + dy + ", " + dz
                + com.wavesurvivor.i18n.WSLang.t("srv.par_rapport_a_l_autel")), true);
        return 1;
    }

    private static int listSlots(CommandContext<CommandSourceStack> ctx) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        AltarRecipes.Zone z = AltarRecipes.peekZone(horde);
        var src = ctx.getSource();
        if (z == null || z.slots == null || z.slots.isEmpty()) {
            src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucun_slot_manuel_pour") + horde + com.wavesurvivor.i18n.WSLang.t("srv.placement_100_auto")), false);
            return 1;
        }
        src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.slots") + horde + " (" + z.slots.size() + ") ==="), false);
        int i = 1;
        for (AltarRecipes.Slot s : z.slots) {
            final int n = i++;
            String what = "chest".equals(s.type) ? com.wavesurvivor.i18n.WSLang.t("srv.coffre_2aba") + s.configKey : "§amarchand";
            src.sendSuccess(() -> Component.literal("§7" + n + ". " + what + " §8(" + s.dx + ", " + s.dy + ", " + s.dz + ")"), false);
        }
        return 1;
    }

    private static int clearSlots(CommandContext<CommandSourceStack> ctx) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        AltarRecipes.editZone(horde).slots.clear();
        AltarRecipes.save();
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.slots_manuels_de") + horde + com.wavesurvivor.i18n.WSLang.t("srv.vides_retour_au_placement_auto")), true);
        return 1;
    }

    private static int setMaxChests(CommandContext<CommandSourceStack> ctx) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        int n = IntegerArgumentType.getInteger(ctx, "n");
        AltarRecipes.editZone(horde).maxChests = n;
        AltarRecipes.save();
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.coffres_auto_max_pour") + horde + " §f: §e" + n), true);
        return 1;
    }

    private static int toggleExclude(CommandContext<CommandSourceStack> ctx, boolean exclude) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        String key = StringArgumentType.getString(ctx, "configKey");
        var list = AltarRecipes.editZone(horde).excludedChests;
        if (exclude) { if (!list.contains(key)) list.add(key); } else list.remove(key);
        AltarRecipes.save();
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.coffre_f325") + key + (exclude ? com.wavesurvivor.i18n.WSLang.t("srv.exclu") : com.wavesurvivor.i18n.WSLang.t("srv.reintegre"))
                + " §7(" + horde + ")"), true);
        return 1;
    }

    private static int setAuto(CommandContext<CommandSourceStack> ctx, boolean on) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        AltarRecipes.editZone(horde).autoLayout = on;
        AltarRecipes.save();
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.placement_auto") + (on ? com.wavesurvivor.i18n.WSLang.t("srv.active") : com.wavesurvivor.i18n.WSLang.t("srv.desactive"))
                + " §7(" + horde + ")"), true);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        AltarRecipes.Zone z = AltarRecipes.peekZone(horde);
        var src = ctx.getSource();
        if (z == null || z.floor == null || z.floor.isEmpty()) {
            src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.zone_9fe1") + horde + com.wavesurvivor.i18n.WSLang.t("srv.aucune_zone_sol_vide")), false);
            return 1;
        }
        int total = z.floor.stream().mapToInt(f -> Math.max(0, f.weight)).sum();
        src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.zone") + horde + com.wavesurvivor.i18n.WSLang.t("srv.rayon") + z.radius), false);
        for (AltarRecipes.FloorBlock f : z.floor) {
            int pct = total > 0 ? Math.round(100f * f.weight / total) : 0;
            String ok = f.resolve() != null ? "§a" : com.wavesurvivor.i18n.WSLang.t("srv.invalide");
            src.sendSuccess(() -> Component.literal("§7• " + ok + f.block + com.wavesurvivor.i18n.WSLang.t("srv.poids_c11f") + f.weight + " (" + pct + "%)"), false);
        }
        // Disposition marchands / coffres
        var chosen = AltarLayout.autoChestConfigs(z).stream().map(c -> c.configKey()).toList();
        int slots = z.slots != null ? z.slots.size() : 0;
        src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.disposition_auto") + (z.autoLayout ? com.wavesurvivor.i18n.WSLang.t("srv.on") : com.wavesurvivor.i18n.WSLang.t("srv.off"))
                + com.wavesurvivor.i18n.WSLang.t("srv.slots_manuels") + slots + com.wavesurvivor.i18n.WSLang.t("srv.max_coffres") + z.maxChests), false);
        src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.coffres_auto") + chosen.size() + ") : §f" + String.join(", ", chosen)), false);
        if (z.excludedChests != null && !z.excludedChests.isEmpty()) {
            src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.exclus") + String.join(", ", z.excludedChests)), false);
        }
        return 1;
    }

    private static int setRadius(CommandContext<CommandSourceStack> ctx) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        int r = IntegerArgumentType.getInteger(ctx, "radius");
        AltarRecipes.editZone(horde).radius = r;
        AltarRecipes.save();
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.zone_de") + horde + com.wavesurvivor.i18n.WSLang.t("srv.rayon_29e6") + r
                + com.wavesurvivor.i18n.WSLang.t("srv.prochaines_liaisons")), true);
        return 1;
    }

    private static int addFloor(CommandContext<CommandSourceStack> ctx, int weight) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "block");
        if (!BuiltInRegistries.BLOCK.containsKey(id) || BuiltInRegistries.BLOCK.get(id) == Blocks.AIR) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.bloc_inconnu") + id));
            return 0;
        }
        AltarRecipes.Zone z = AltarRecipes.editZone(horde);
        // Même bloc déjà présent → on met à jour son poids
        AltarRecipes.FloorBlock existing = null;
        for (AltarRecipes.FloorBlock f : z.floor) if (id.toString().equals(f.block)) existing = f;
        if (existing != null) existing.weight = weight;
        else z.floor.add(new AltarRecipes.FloorBlock(id.toString(), weight));
        AltarRecipes.save();
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.sol_de") + horde + " §f: §e" + id + com.wavesurvivor.i18n.WSLang.t("srv.poids") + weight + ")"), true);
        return 1;
    }

    private static int clearFloor(CommandContext<CommandSourceStack> ctx) {
        String horde = StringArgumentType.getString(ctx, "hordeName");
        AltarRecipes.editZone(horde).floor.clear();
        AltarRecipes.save();
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.sol_de") + horde + com.wavesurvivor.i18n.WSLang.t("srv.vide_zone_desactivee")), true);
        return 1;
    }
}
