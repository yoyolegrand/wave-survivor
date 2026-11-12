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
                // 2.0 — Récompenses du Titan : mise en scène + coffre, coffre seul, reliques de la Faille, Jauge
                .then(Commands.literal("chest").executes(ctx -> chest(ctx.getSource(), false))
                        .then(Commands.literal("place").executes(ctx -> chest(ctx.getSource(), true))))
                .then(Commands.literal("relics").executes(ctx -> relics(ctx.getSource())))
                .then(Commands.literal("sky").executes(ctx -> sky(ctx.getSource(), null))
                        .then(Commands.argument("phase", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        java.util.List.of("horde", "siege", "contre", "titan", "victoire", "auto"), b))
                                .executes(ctx -> sky(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "phase")))))
                .then(Commands.literal("monster").then(Commands.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("geodon", "ancreur", "bolide"), b))
                        .executes(ctx -> monster(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "type"), ""))
                        .then(Commands.argument("action", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        java.util.List.of("charge", "geode", "anchor", "fall", "cool"), b))
                                .executes(ctx -> monster(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "type"),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "action"))))))
                .then(Commands.literal("gauge").then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                        .executes(ctx -> gauge(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value")))))
                .then(Commands.literal("devoreur").executes(ctx -> titan(ctx.getSource(), 1.0, "devoreur"))
                        .then(Commands.argument("scale", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.2, 20))
                                .executes(ctx -> titan(ctx.getSource(), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "scale"), "devoreur"))))
                .then(Commands.literal("colosse").executes(ctx -> titan(ctx.getSource(), 1.0, "colosse"))
                        .then(Commands.argument("scale", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.2, 20))
                                .executes(ctx -> titan(ctx.getSource(), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "scale"), "colosse"))))
                .then(Commands.literal("archonte").executes(ctx -> titan(ctx.getSource(), 1.0, "archonte"))
                        .then(Commands.argument("scale", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.2, 20))
                                .executes(ctx -> titan(ctx.getSource(), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "scale"), "archonte"))))
                .then(Commands.literal("pose").then(Commands.argument("pose", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(com.wavesurvivor.entity.TitanAnimated.Pose.values()).map(p -> p.name().toLowerCase()), b))
                        .executes(ctx -> pose(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "pose"), 60))
                        .then(Commands.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1200))
                                .executes(ctx -> pose(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "pose"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks"))))))
                .then(Commands.literal("skill").then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((ctx, b) -> {   // 2.0 : attaques de test + compétences de la liste du Titan
                            java.util.List<String> all = new java.util.ArrayList<>(java.util.List.of(RiftTitan.DEBUG_SKILLS));
                            for (var en : TitanSkills.entries(RiftManager.settingsPublic())) if (en.skill != null && !all.contains(en.skill)) all.add(en.skill);
                            return net.minecraft.commands.SharedSuggestionProvider.suggest(all, b);
                        })
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

    /** Test : un Dévoreur immobile à 12 blocs devant le joueur (agrandi par Pehkui si l'échelle n'est pas 1). */
    /** Test : la mise en scène de la victoire et le Coffre du Titan ({@code direct} = coffre posé tout de suite, avec une clé). */
    private static int chest(CommandSourceStack src, boolean direct) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        if (direct) {
            if (!TitanChest.testPlace(p)) { src.sendFailure(Component.literal(WSLang.t("rift.test.chest_missing"))); return 0; }
            src.sendSuccess(() -> Component.literal(WSLang.t("rift.test.chest_placed")), false);
        } else {
            TitanChest.testVictory(p);
            src.sendSuccess(() -> Component.literal(WSLang.t("rift.test.chest_scene")), false);
        }
        return 1;
    }

    /** Test : les 6 reliques de la Faille (niveau I) et 48 Poussières de Faille. */
    private static int relics(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        for (net.minecraft.world.item.Item it : java.util.List.of(com.wavesurvivor.registry.ModItems.DE_FAILLE.get(), com.wavesurvivor.registry.ModItems.SCEAU_TITAN.get(),
                com.wavesurvivor.registry.ModItems.TROPHEE_TITAN.get(), com.wavesurvivor.registry.ModItems.COEUR_TITAN.get(),
                com.wavesurvivor.registry.ModItems.DESTIN_FAILLE.get(), com.wavesurvivor.registry.ModItems.COURONNE_TITAN.get())) {
            net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, new net.minecraft.world.item.ItemStack(it));
        }
        net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, new net.minecraft.world.item.ItemStack(com.wavesurvivor.registry.ModItems.POUSSIERE_FAILLE.get(), 48));
        src.sendSuccess(() -> Component.literal(WSLang.t("rift.test.relics_given")), false);
        return 1;
    }

    /**
     * Test (2.0) : monstres de la Faille. Sans action : en fait apparaître un à 6 blocs devant soi (le Bolide tombe du ciel).
     * Actions sur le plus proche (32 blocs) : geodon charge | geode, ancreur anchor (sur soi), bolide fall | cool.
     */
    private static int monster(CommandSourceStack src, String type, String action) {
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) return 0;
        net.minecraft.server.level.ServerLevel l = p.serverLevel();
        Class<? extends com.wavesurvivor.entity.FailleMonster> cls = switch (type) {
            case "geodon" -> com.wavesurvivor.entity.Geodon.class;
            case "ancreur" -> com.wavesurvivor.entity.Ancreur.class;
            case "bolide" -> com.wavesurvivor.entity.Bolide.class;
            default -> null;
        };
        if (cls == null) { src.sendFailure(Component.literal(WSLang.t("rift.test.monster_bad"))); return 0; }
        if (action.isEmpty()) {
            net.minecraft.world.entity.EntityType<?> et = switch (type) {
                case "geodon" -> com.wavesurvivor.registry.ModEntities.GEODON.get();
                case "ancreur" -> com.wavesurvivor.registry.ModEntities.ANCREUR.get();
                default -> com.wavesurvivor.registry.ModEntities.BOLIDE.get();
            };
            net.minecraft.world.phys.Vec3 look = p.getLookAngle();
            net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(p.getX() + look.x * 6, p.getY(), p.getZ() + look.z * 6);
            at = l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
            net.minecraft.world.entity.Entity e = et.spawn(l, at, net.minecraft.world.entity.MobSpawnType.COMMAND);
            if (e == null) return 0;
            src.sendSuccess(() -> Component.literal(WSLang.t("rift.test.monster_spawned", e.getName().getString())), false);
            return 1;
        }
        com.wavesurvivor.entity.FailleMonster m = null;
        for (var c : l.getEntitiesOfClass(cls, p.getBoundingBox().inflate(32))) if (m == null || c.distanceToSqr(p) < m.distanceToSqr(p)) m = c;
        if (m == null) { src.sendFailure(Component.literal(WSLang.t("rift.test.monster_none"))); return 0; }
        boolean ok;
        switch (type + ":" + action) {
            case "geodon:charge" -> ok = ((com.wavesurvivor.entity.Geodon) m).startCharge(p);
            case "geodon:geode" -> { ((com.wavesurvivor.entity.Geodon) m).startGeode(); ok = true; }
            case "ancreur:anchor" -> { m.setTarget(p); ok = ((com.wavesurvivor.entity.Ancreur) m).startAnchor(); }
            case "bolide:fall" -> { ((com.wavesurvivor.entity.Bolide) m).restartFall(); ok = true; }
            case "bolide:cool" -> { ((com.wavesurvivor.entity.Bolide) m).coolNow(); ok = true; }
            default -> { src.sendFailure(Component.literal(WSLang.t("rift.test.monster_bad"))); return 0; }
        }
        src.sendSuccess(() -> Component.literal(WSLang.t(ok ? "rift.test.monster_done" : "rift.test.monster_busy")), false);
        return ok ? 1 : 0;
    }

    /** Test (2.0) : force une phase du ciel de la Faille (« auto » = phase réelle ; sans argument : affiche la phase). */
    private static int sky(CommandSourceStack src, String ph) {
        if (ph == null) {
            src.sendSuccess(() -> Component.literal(WSLang.t("rift.test.sky_now", RiftManager.skyNow())), false);
            return 1;
        }
        String k = ph.toLowerCase(java.util.Locale.ROOT);
        if (!java.util.List.of("horde", "siege", "contre", "titan", "victoire", "auto").contains(k)) {
            src.sendFailure(Component.literal(WSLang.t("rift.test.sky_bad")));
            return 0;
        }
        RiftManager.forceSky(src.getServer(), k.equals("auto") ? null : k,
                src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
        String shown = RiftManager.skyNow();
        src.sendSuccess(() -> Component.literal(WSLang.t("rift.test.sky_set", shown)), false);
        return 1;
    }

    /** Test : règle la Jauge de la Faille du joueur. */
    private static int gauge(CommandSourceStack src, int v) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        com.wavesurvivor.item.RiftRelics.setGauge(p, v);
        src.sendSuccess(() -> Component.literal(WSLang.t("rift.test.gauge_set", v)), false);
        return 1;
    }

    /** Test : un Titan animé immobile devant le joueur ({@code id} = devoreur, colosse, archonte). */
    private static int titan(CommandSourceStack src, double scale, String id) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        boolean colosse = id.equals("colosse");
        com.wavesurvivor.entity.RiftTitanEntity d = switch (id) {
            case "colosse" -> com.wavesurvivor.registry.ModEntities.COLOSSE.get().create(p.serverLevel());
            case "archonte" -> com.wavesurvivor.registry.ModEntities.ARCHONTE.get().create(p.serverLevel());
            default -> com.wavesurvivor.registry.ModEntities.DEVOREUR.get().create(p.serverLevel());
        };
        if (d == null) return 0;
        net.minecraft.world.phys.Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        double dist = colosse ? 16 : 12;
        d.moveTo(p.getX() + look.x * dist, p.getY(), p.getZ() + look.z * dist, p.getYRot() + 180f, 0f);
        d.setYHeadRot(p.getYRot() + 180f);
        d.yBodyRot = p.getYRot() + 180f;
        d.setNoAi(true);
        d.setNoGravity(true);
        d.setPersistenceRequired();
        d.getPersistentData().putBoolean("ws_test_titan", true);
        p.serverLevel().addFreshEntity(d);
        if (scale != 1.0) com.wavesurvivor.compat.PehkuiCompat.setScale(d, scale);
        src.sendSuccess(() -> Component.literal(WSLang.t("rift.test." + id + "_ok")), false);
        return 1;
    }

    /** Test : pose jouée par le Titan de la Faille, sinon par les Dévoreurs à moins de 200 blocs. */
    private static int pose(CommandSourceStack src, String name, int ticks) {
        com.wavesurvivor.entity.TitanAnimated.Pose pose;
        try { pose = com.wavesurvivor.entity.TitanAnimated.Pose.valueOf(name.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { src.sendFailure(Component.literal(WSLang.t("rift.test.pose_unknown", name))); return 0; }
        int n = RiftTitan.debugPose(pose, ticks) ? 1 : 0;
        ServerPlayer p = src.getPlayer();
        if (p != null) {
            for (com.wavesurvivor.entity.RiftTitanEntity d : p.serverLevel().getEntitiesOfClass(com.wavesurvivor.entity.RiftTitanEntity.class, p.getBoundingBox().inflate(200))) {
                d.playPose(pose, ticks);
                n++;
            }
        }
        final int count = n;
        if (count == 0) { src.sendFailure(Component.literal(WSLang.t("rift.test.pose_none"))); return 0; }
        src.sendSuccess(() -> Component.literal(WSLang.t("rift.test.pose_ok", pose.name(), ticks, count)), false);
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
