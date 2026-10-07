package com.wavesurvivor.horde;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.ModConfig;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.altar.AltarStore;
import com.wavesurvivor.horde.benediction.RogueUpgrade;
import com.wavesurvivor.horde.benediction.RogueUpgradeManager;
import com.wavesurvivor.horde.benediction.RogueUpgradeRegistry;
import com.wavesurvivor.horde.boss.BossManager;
import com.wavesurvivor.horde.pact.BloodPactConfig;
import com.wavesurvivor.horde.pact.BloodPactManager;
import com.wavesurvivor.horde.pact.BloodPactRegistry;
import com.wavesurvivor.horde.roulette.CustomChestStore;
import com.wavesurvivor.horde.roulette.RouletteChestConfig;
import com.wavesurvivor.horde.roulette.RouletteChestManager;
import com.wavesurvivor.horde.roulette.RouletteChestRegistry;
import com.wavesurvivor.horde.roulette.RouletteChestSpawnStore;
import com.wavesurvivor.horde.boss.BossMinionTracker;
import com.wavesurvivor.horde.model.ChaosEvent;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.OpenChestBuilderPacket;
import com.wavesurvivor.network.OpenInspectionScreenPacket;
import com.wavesurvivor.network.PlayerLoginHandler;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class HordeCommand {

    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final SuggestionProvider<CommandSourceStack> HORDE_NAMES =
            (ctx, builder) -> suggestHordeNames(builder);

    private static final SuggestionProvider<CommandSourceStack> BENEDICTION_IDS =
            (ctx, builder) -> {
                String remaining = builder.getRemaining().toLowerCase();
                for (String id : RogueUpgradeRegistry.allIds()) {
                    if (id.toLowerCase().startsWith(remaining)) builder.suggest(id);
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> ROULETTE_KEYS =
            (ctx, builder) -> {
                String remaining = builder.getRemaining().toLowerCase();
                for (String id : RouletteChestRegistry.allKeys()) {
                    if (id.toLowerCase().startsWith(remaining)) builder.suggest(id);
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> PACT_NAMES =
            (ctx, builder) -> {
                String remaining = builder.getRemaining().toLowerCase();
                for (String id : BloodPactRegistry.allNames()) {
                    if (id.toLowerCase().startsWith(remaining)) builder.suggest(id);
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> CUSTOM_CHEST_NAMES =
            (ctx, builder) -> {
                String remaining = builder.getRemaining().toLowerCase();
                for (var c : CustomChestStore.all()) {
                    if (c.name != null && c.name.toLowerCase().startsWith(remaining)) {
                        String s = c.name.contains(" ") ? "\"" + c.name + "\"" : c.name;
                        builder.suggest(s);
                    }
                }
                return builder.buildFuture();
            };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Racine ouverte à tous. Chaque sous-commande admin porte son propre requires(hasPermission(2)).
        // Seule /ws info est accessible aux joueurs sans permission.
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("wavesurvivor")
                .then(Commands.literal("info").executes(HordeCommand::info))
                // Codex : guide des mécaniques, ouvert à tous
                .then(Commands.literal("codex").executes(ctx -> {
                    com.wavesurvivor.network.CodexPacket.openFor(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("list").requires(src -> src.hasPermission(2)).executes(HordeCommand::list))
                .then(Commands.literal("reload").requires(src -> src.hasPermission(2)).executes(HordeCommand::reload))
                // Import de hordes : liste des .zip du dossier config/wavesurvivor/import
                .then(Commands.literal("import").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    com.wavesurvivor.network.HordeExchangePackets.openFor(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("stop").requires(src -> src.hasPermission(2)).executes(HordeCommand::stop))
                // Kingdom : restes orphelins (défenses, blocs invisibles, portes, pièges) — scan = particules, sinon suppression
                .then(Commands.literal("kclean").requires(src -> src.hasPermission(2))
                        .executes(ctx -> com.wavesurvivor.horde.kingdom.KingdomLeftovers.scan(ctx.getSource().getPlayerOrException(), 64, true))
                        .then(Commands.argument("rayon", IntegerArgumentType.integer(8, 256))
                                .executes(ctx -> com.wavesurvivor.horde.kingdom.KingdomLeftovers.scan(ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "rayon"), true)))
                        .then(Commands.literal("scan")
                                .executes(ctx -> com.wavesurvivor.horde.kingdom.KingdomLeftovers.scan(ctx.getSource().getPlayerOrException(), 64, false))
                                .then(Commands.argument("rayon", IntegerArgumentType.integer(8, 256))
                                        .executes(ctx -> com.wavesurvivor.horde.kingdom.KingdomLeftovers.scan(ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "rayon"), false)))))
                .then(Commands.literal("skip").requires(src -> src.hasPermission(2)).executes(HordeCommand::skip))
                // Kingdom (test) : saut direct à l'étape finale (Porte du Roi, arène ou boss final)
                .then(Commands.literal("kfinal").requires(src -> src.hasPermission(2))
                        .executes(ctx -> com.wavesurvivor.horde.kingdom.KingdomManager.debugFinal(ctx.getSource())))
                .then(Commands.literal("status").requires(src -> src.hasPermission(2)).executes(HordeCommand::status))
                .then(Commands.literal("debug").requires(src -> src.hasPermission(2)).executes(HordeCommand::debug)
                        .then(Commands.literal("soldier").executes(HordeCommand::debugSoldier)))
                // Sauvegarde de partie : boutons « Reprendre » / « Abandonner » du message de connexion (ouverts à tous)
                .then(Commands.literal("resume").executes(ctx -> HordeSession.resume(ctx.getSource())))
                .then(Commands.literal("abandon").executes(ctx -> HordeSession.abandon(ctx.getSource())))
                // « La Chute du Monolithe » : bouton « ↻ Réessayer » du bilan (ouvert à tous) → reconsacrer l'autel tombé
                .then(Commands.literal("retry").executes(ctx -> com.wavesurvivor.altar.MonolithFall.retry(ctx.getSource())))
                // Progression : débloquer une horde à la main, consulter ou réinitialiser la progression d'un joueur
                .then(Commands.literal("unlock").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(Commands.argument("hordeName", StringArgumentType.string()).suggests(HORDE_NAMES)
                                        .executes(ctx -> {
                                            net.minecraft.server.level.ServerPlayer p = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                            String h = StringArgumentType.getString(ctx, "hordeName");
                                            com.wavesurvivor.horde.difficulty.HordeProgress.get(ctx.getSource().getServer())
                                                    .record(p.getUUID(), h, com.wavesurvivor.horde.difficulty.Difficulty.NIGHTMARE);
                                            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("unlock.cmd_done",
                                                    com.wavesurvivor.i18n.WSLang.t(h), p.getGameProfile().getName())), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("progress").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> {
                                            net.minecraft.server.level.ServerPlayer p = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                            com.wavesurvivor.horde.difficulty.HordeProgress.get(ctx.getSource().getServer()).reset(p.getUUID());
                                            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("unlock.cmd_reset",
                                                    p.getGameProfile().getName())), true);
                                            return 1;
                                        })))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> {
                                    net.minecraft.server.level.ServerPlayer p = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                    var prog = com.wavesurvivor.horde.difficulty.HordeProgress.get(ctx.getSource().getServer()).of(p.getUUID());
                                    ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("unlock.cmd_list",
                                            p.getGameProfile().getName(), prog.size())), false);
                                    var diffs = com.wavesurvivor.horde.difficulty.Difficulty.values();
                                    for (var en : prog.entrySet()) {
                                        var d = diffs[Math.max(0, Math.min(diffs.length - 1, en.getValue()))];
                                        ctx.getSource().sendSuccess(() -> Component.literal(" §7• §f" + en.getKey() + " §8— "
                                                + d.color + com.wavesurvivor.i18n.WSLang.t("difficulty." + d.id)), false);
                                    }
                                    return 1;
                                })))
                // Mode Kingdom : choix de la spécialisation d'une défense (boutons cliquables du chat)
                .then(Commands.literal("kvariant")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                        .then(Commands.argument("variant", StringArgumentType.word())
                                .executes(ctx -> {
                                    com.wavesurvivor.horde.kingdom.KingdomDefenses.chooseVariant(ctx.getSource().getPlayerOrException(),
                                            new net.minecraft.core.BlockPos(IntegerArgumentType.getInteger(ctx, "x"),
                                                    IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z")),
                                            StringArgumentType.getString(ctx, "variant"));
                                    return 1;
                                }))))))
                // Mode Kingdom : choix d'un présage de l'Augure (boutons cliquables du chat)
                .then(Commands.literal("komen")
                        .then(Commands.argument("i", IntegerArgumentType.integer(0, 2))
                                .executes(ctx -> {
                                    com.wavesurvivor.horde.kingdom.KingdomOmens.choose(ctx.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(ctx, "i"));
                                    return 1;
                                })))
                .then(Commands.literal("lang")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> com.wavesurvivor.i18n.WSLang.c("lang.current",
                                    com.wavesurvivor.i18n.WSLang.get()), false);
                            return 1;
                        })
                        .then(Commands.argument("language", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .requires(src -> src.hasPermission(2))
                                .suggests((c, b) -> {
                                    for (String l : com.wavesurvivor.i18n.WSLang.SUPPORTED) b.suggest(l);
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    String l = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "language");
                                    if (!com.wavesurvivor.i18n.WSLang.isSupported(l)) {
                                        ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.en_fr")));
                                        return 0;
                                    }
                                    com.wavesurvivor.i18n.WSLang.set(l);
                                    com.wavesurvivor.i18n.WSLang.saveSettings();
                                    var pkt = new com.wavesurvivor.network.LangSyncPacket(com.wavesurvivor.i18n.WSLang.get());
                                    for (var sp : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                        com.wavesurvivor.network.NetworkHandler.CHANNEL.send(
                                                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp), pkt);
                                    }
                                    ctx.getSource().sendSuccess(() -> com.wavesurvivor.i18n.WSLang.c("lang.changed"), true);
                                    return 1;
                                })))
                .then(Commands.literal("spawnzone").requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return 0;
                            return com.wavesurvivor.horde.spawn.SpawnZone.debugShow(sp, -1);
                        })
                        .then(Commands.argument("rayon", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return 0;
                                    return com.wavesurvivor.horde.spawn.SpawnZone.debugShow(sp,
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "rayon"));
                                })))
                .then(Commands.literal("gisement").requires(src -> src.hasPermission(2))
                        .executes(ctx -> gisementHere(ctx.getSource(), "petit"))
                        .then(Commands.argument("taille", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String s : new String[]{"petit", "moyen", "grand", "aleatoire"}) b.suggest(s);
                                    return b.buildFuture();
                                })
                                .executes(ctx -> gisementHere(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "taille")))))
                .then(Commands.literal("totem").requires(src -> src.hasPermission(2))
                        .executes(ctx -> totemHere(ctx.getSource(), "protection"))
                        .then(Commands.argument("role", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String r : new String[]{"protection", "fureur", "soin", "malediction", "invocation"}) b.suggest(r);
                                    return b.buildFuture();
                                })
                                .executes(ctx -> totemHere(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "role")))))
                .then(Commands.literal("anomaly").requires(src -> src.hasPermission(2))
                        .executes(ctx -> anomaliesHere(ctx.getSource(), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 6))
                                .executes(ctx -> anomaliesHere(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("breach").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("open")
                                .executes(ctx -> openBreachesHere(ctx.getSource(), 3, null))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 8))
                                        .executes(ctx -> openBreachesHere(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"), null))
                                        .then(Commands.argument("style", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests((c, b) -> {
                                                    for (var st : com.wavesurvivor.entity.BreachStyle.values()) b.suggest(st.id);
                                                    return b.buildFuture();
                                                })
                                                .executes(ctx -> openBreachesHere(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "style"))))))
                        .then(Commands.literal("close").executes(ctx -> {
                            com.wavesurvivor.horde.breach.BreachManager.clearAll(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.breches_fermees_renforts_retires")), true);
                            return 1;
                        })))
                .then(Commands.literal("editor").requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                                com.wavesurvivor.horde.editor.HordeEditorManager.openList(sp);
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.commande_reservee_aux_joueurs")));
                            return 0;
                        }))
                .then(Commands.literal("special").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("waveName", StringArgumentType.greedyString())
                                .suggests((ctx, b) -> {
                                    var h = HordeManager.get().getActiveHorde();
                                    if (h != null && h.configData != null && h.configData.specialWaves != null) {
                                        for (var sw : h.configData.specialWaves) if (sw.name != null) b.suggest(sw.name);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    String n = StringArgumentType.getString(ctx, "waveName");
                                    String r = HordeManager.get().forceSpecialWave(n);
                                    if (r == null) {
                                        ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.vague_speciale_introuvable_ou_aucune_hor") + n));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.la_prochaine_vague_sera") + r), true);
                                    return 1;
                                })))
                .then(Commands.literal("chaos").requires(src -> src.hasPermission(2))
                        .executes(HordeCommand::chaosRandom)
                        .then(Commands.literal("list").executes(HordeCommand::chaosList))
                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .executes(HordeCommand::chaosByIndex)))
                .then(Commands.literal("start").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("hordeName", StringArgumentType.string())
                                .suggests(HORDE_NAMES)
                                .executes(HordeCommand::start)))
                .then(Commands.literal("setspawn").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("hordeName", StringArgumentType.string())
                                .suggests(HORDE_NAMES)
                                .executes(HordeCommand::setSpawn)))
                .then(Commands.literal("benediction")
                        .then(Commands.literal("menu").executes(HordeCommand::benedictionMenu))
                        .then(Commands.literal("liste").executes(HordeCommand::benedictionListe))
                        .then(Commands.literal("choose")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .suggests(BENEDICTION_IDS)
                                        .executes(HordeCommand::benedictionChoose))))
                .then(Commands.literal("roulettechest").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("list").executes(HordeCommand::rouletteList))
                        .then(Commands.literal("give")
                                .then(Commands.argument("configKey", StringArgumentType.string())
                                        .suggests(ROULETTE_KEYS)
                                        .executes(HordeCommand::rouletteGive)))
                        .then(Commands.literal("key")
                                .then(Commands.argument("configKey", StringArgumentType.string())
                                        .suggests(ROULETTE_KEYS)
                                        .executes(HordeCommand::rouletteKey)))
                        .then(Commands.literal("spawn")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("hordeName", StringArgumentType.string())
                                                .suggests(HORDE_NAMES)
                                                .then(Commands.argument("configKey", StringArgumentType.string())
                                                        .suggests(ROULETTE_KEYS)
                                                        .executes(HordeCommand::rouletteSpawnAdd))))
                                .then(Commands.literal("list")
                                        .then(Commands.argument("hordeName", StringArgumentType.string())
                                                .suggests(HORDE_NAMES)
                                                .executes(HordeCommand::rouletteSpawnList)))
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("hordeName", StringArgumentType.string())
                                                .suggests(HORDE_NAMES)
                                                .executes(HordeCommand::rouletteSpawnClear))))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(HordeCommand::rouletteCreate)))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(CUSTOM_CHEST_NAMES)
                                        .executes(HordeCommand::rouletteEdit)))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(CUSTOM_CHEST_NAMES)
                                        .executes(HordeCommand::rouletteDelete))))
                .then(Commands.literal("pacte")
                        .then(Commands.literal("list").executes(HordeCommand::pacteList))
                        .then(Commands.literal("give").requires(src -> src.hasPermission(2))
                                .then(Commands.argument("pactName", StringArgumentType.string())
                                        .suggests(PACT_NAMES)
                                        .executes(HordeCommand::pacteGive)))
                        .then(Commands.literal("resetcd").executes(HordeCommand::pacteResetCd)))
                .then(Commands.literal("altar").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("give")
                                .then(Commands.argument("hordeName", StringArgumentType.string())
                                        .suggests(HORDE_NAMES)
                                        .executes(HordeCommand::altarGive))
                                .executes(HordeCommand::altarGiveBlank))
                        .then(Commands.literal("bind")
                                .then(Commands.argument("hordeName", StringArgumentType.string())
                                        .suggests(HORDE_NAMES)
                                        .executes(HordeCommand::altarBind)))
                        .then(com.wavesurvivor.altar.AltarZoneCommand.build(HORDE_NAMES))
                        .then(Commands.literal("list").executes(HordeCommand::altarList)));

        dispatcher.register(root);
        dispatcher.register(Commands.literal("ws").redirect(dispatcher.register(root)));
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        try {
            String summary = WaveSurvivorMod.reloadConfig();
            // Broadcast la nouvelle config à tous les clients pour que /ws info affiche à jour.
            PlayerLoginHandler.broadcastToAll(ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.config_rechargee") + summary).withStyle(ChatFormatting.GREEN), true);
            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.note_la_horde_en_cours_garde_ses_templat")).withStyle(ChatFormatting.GRAY), false);
            return 1;
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[reload] Erreur : {}", e.getMessage(), e);
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.reload_echoue") + e.getMessage()));
            return 0;
        }
    }

    /**
     * /ws info : ouvre le WaveInspectionScreen côté client.
     * Accessible à tous les joueurs (permission 0). Doit être exécutée en jeu par un joueur.
     */
    private static int info(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenInspectionScreenPacket());
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cette_commande_doit_etre_executee_par_un")));
            return 0;
        }
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ModConfig cfg = WaveSurvivorMod.getConfig();
        if (cfg.hordeConfigMulti == null || cfg.hordeConfigMulti.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucune_horde_configuree")).withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.hordes_disponibles") + cfg.hordeConfigMulti.size() + ") ═══").withStyle(ChatFormatting.GOLD), false);
        for (HordeConfigMultiData h : cfg.hordeConfigMulti) {
            int waves = h.configData != null ? h.configData.totalWaves : 0;
            String coords = "";
            if (h.configData != null && h.configData.spawnCoords != null) {
                coords = " @ " + h.configData.spawnCoords.x + "/" + h.configData.spawnCoords.y + "/" + h.configData.spawnCoords.z;
            }
            String desc = h.hordeDescription != null && !h.hordeDescription.isEmpty()
                    ? " § " + h.hordeDescription : "";
            final String cFinal = coords, descFinal = desc;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  • " + h.hordeName + " (" + waves + com.wavesurvivor.i18n.WSLang.t("srv.vagues") + cFinal + ")" + descFinal
            ).withStyle(ChatFormatting.WHITE), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cmds_start_stop_skip_status_debug_chaos")).withStyle(ChatFormatting.GRAY), false);
        return cfg.hordeConfigMulti.size();
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "hordeName");
        HordeConfigMultiData horde = WaveSurvivorMod.getConfig().findHorde(name);
        if (horde == null) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.horde_introuvable_1934") + name + com.wavesurvivor.i18n.WSLang.t("srv.ws_list_pour_voir_les_noms")));
            return 0;
        }
        boolean ok = HordeManager.get().start(horde, ctx.getSource().getServer());
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.une_horde_est_deja_en_cours_ws_stop_d_ab")));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.horde_9a2c") + horde.hordeName + com.wavesurvivor.i18n.WSLang.t("srv.lancee")).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        boolean stopped = HordeManager.get().stop();
        if (!stopped) {
            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucune_horde_active")).withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.horde_arretee")).withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int skip(CommandContext<CommandSourceStack> ctx) {
        HordeManager mgr = HordeManager.get();
        if (mgr.getState() == HordeManager.State.IDLE || mgr.getState() == HordeManager.State.FINISHED) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucune_horde_active_a_skipper")));
            return 0;
        }
        boolean ok = mgr.skip();
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.skip_impossible_dans_l_etat_courant")));
            return 0;
        }
        return 1;
    }

    /** /ws gisement [taille] : gisement de fer de test à quelques blocs (petit, moyen, grand, aleatoire). */
    private static int gisementHere(CommandSourceStack src, String size) {
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return 0;
        var ev = new com.wavesurvivor.horde.model.ChaosEvent();
        ev.type = "gisement";
        ev.blocks = java.util.List.of("minecraft:iron_ore", "minecraft:stone", "minecraft:deepslate_iron_ore");
        ev.minTier = 1;
        ev.hits = 8;
        ev.size = size;
        ev.distanceMin = 5;
        ev.distanceMax = 7;
        ev.lifetime = 120;
        ev.hitDropItem = "minecraft:iron_nugget";
        ev.hitDropChance = 30;
        var d1 = new com.wavesurvivor.horde.model.ChaosEvent.Drop();
        d1.item = "minecraft:raw_iron"; d1.minQty = 3; d1.maxQty = 6; d1.chance = 100;
        var d2 = new com.wavesurvivor.horde.model.ChaosEvent.Drop();
        d2.item = "minecraft:iron_ingot"; d2.minQty = 1; d2.maxQty = 2; d2.chance = 40;
        ev.drops = java.util.List.of(d1, d2);
        com.wavesurvivor.horde.chaos.ChaosApplier.apply(ev, sp.serverLevel(), sp.blockPosition(), src.getServer());
        return 1;
    }

    /** /ws totem [rôle] : pose un totem de test devant le joueur (aperçu visuel). */
    private static int totemHere(CommandSourceStack src, String role) {
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
            src.sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.commande_reservee_aux_joueurs")));
            return 0;
        }
        var level = sp.serverLevel();
        var look = sp.getLookAngle();
        int x = (int) Math.floor(sp.getX() + look.x * 3), z = (int) Math.floor(sp.getZ() + look.z * 3);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        var t = com.wavesurvivor.registry.ModEntities.TOTEM.get().create(level);
        if (t == null) return 0;
        t.moveTo(x + 0.5, y, z + 0.5, sp.getYRot() + 180f, 0);
        t.setup(role, "minecraft:player_head", -1);
        t.setCustomName(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.totem_de_test") + role + ")"));
        t.setCustomNameVisible(true);
        level.addFreshEntity(t);
        if (!"protection".equals(role)) {
            // Aura active 60 s (test du chaos « totem »)
            com.wavesurvivor.horde.chaos.TotemAuraManager.register(t, role, 8, 60, "minecraft:emerald", 3, "minecraft:zombie");
        }
        src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.totem") + role + com.wavesurvivor.i18n.WSLang.t("srv.pose_frappe_le_pour_voir_ses_reactions")), false);
        return 1;
    }

    /** /ws anomaly [n] : anomalies gravitationnelles de test (la 1re sous le joueur, les autres autour). */
    private static int anomaliesHere(CommandSourceStack src, int count) {
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
            src.sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.commande_reservee_aux_joueurs")));
            return 0;
        }
        var level = sp.serverLevel();
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count, r = i == 0 ? 0 : 5;
            int x = sp.getBlockX() + (int) Math.round(Math.cos(a) * r), z = sp.getBlockZ() + (int) Math.round(Math.sin(a) * r);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            com.wavesurvivor.horde.anomaly.AnomalyManager.spawnAt(level, new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 0.5), (com.wavesurvivor.altar.AltarRecipes.Anomalies) null);
        }
        src.sendSuccess(() -> Component.literal("§5" + count + com.wavesurvivor.i18n.WSLang.t("srv.anomalie_s_gravitationnelle_s_creee_s")), true);
        return count;
    }

    /** /ws breach open [n] [style] : ouvre des Brèches autour du joueur (test). */
    private static int openBreachesHere(CommandSourceStack src, int count, String style) {
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
            src.sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.commande_reservee_aux_joueurs")));
            return 0;
        }
        com.wavesurvivor.altar.AltarRecipes.Breaches cfg = new com.wavesurvivor.altar.AltarRecipes.Breaches();
        if (style != null) cfg.style = com.wavesurvivor.entity.BreachStyle.byId(style).id;
        com.wavesurvivor.horde.breach.BreachManager.open(sp.serverLevel(), sp.blockPosition(), count, 8, cfg);
        src.sendSuccess(() -> Component.literal("§6" + count + com.wavesurvivor.i18n.WSLang.t("srv.breche_s_ouverte_s_ws_breach_close_pour")), true);
        return count;
    }

    private static int chaosRandom(CommandContext<CommandSourceStack> ctx) {
        String err = HordeManager.get().forceChaos(-1);
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.chaos_impossible") + err));
            return 0;
        }
        return 1;
    }

    private static int chaosByIndex(CommandContext<CommandSourceStack> ctx) {
        int idx = IntegerArgumentType.getInteger(ctx, "index");
        String err = HordeManager.get().forceChaos(idx);
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.chaos_impossible") + err));
            return 0;
        }
        return 1;
    }

    private static int chaosList(CommandContext<CommandSourceStack> ctx) {
        List<ChaosEvent> events = HordeManager.get().listChaosEvents();
        if (events == null) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucune_horde_active_lance_en_une_d_abord")));
            return 0;
        }
        if (events.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cette_horde_n_a_aucun_chaos_event")).withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.chaos_events") + events.size() + ") ═══").withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < events.size(); i++) {
            ChaosEvent e = events.get(i);
            String label = e.type + (e.entityType != null ? " → " + e.entityType : "");
            final String s = "  [" + i + "] " + label;
            ctx.getSource().sendSuccess(() -> Component.literal(s).withStyle(ChatFormatting.WHITE), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.force_via_ws_chaos_index")).withStyle(ChatFormatting.GRAY), false);
        return events.size();
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        HordeManager mgr = HordeManager.get();
        if (mgr.getState() == HordeManager.State.IDLE) {
            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.etat_idle")).withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        HordeConfigMultiData h = mgr.getActiveHorde();
        MinecraftServer srv = ctx.getSource().getServer();
        String name = h != null ? h.hordeName : "?";
        int total = h != null && h.configData != null ? h.configData.totalWaves : 0;
        long tick = srv.getTickCount();
        long elapsed = (tick - mgr.getStartedAtTick()) / 20;

        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.etat_horde")).withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.nom") + name).withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.etat") + mgr.getState()).withStyle(ChatFormatting.YELLOW), false);
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.vague") + mgr.getCurrentWave() + " / " + total).withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.ecoule") + elapsed + "s").withStyle(ChatFormatting.GRAY), false);

        Map<String, List<Entity>> byType = new HashMap<>();
        for (Map.Entry<UUID, HordeEntity> e : MobRegistry.entries()) {
            HordeEntity template = e.getValue();
            Entity ent = findEntity(srv, e.getKey());
            if (ent == null || !ent.isAlive()) continue;
            String key = template.entityType != null ? template.entityType : "?";
            byType.computeIfAbsent(key, k -> new ArrayList<>()).add(ent);
        }

        int totalMobs = byType.values().stream().mapToInt(List::size).sum();
        int bossCount = BossManager.activeBossCount();

        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.mobs_vivants") + totalMobs + com.wavesurvivor.i18n.WSLang.t("srv.dont") + bossCount + com.wavesurvivor.i18n.WSLang.t("srv.boss")).withStyle(ChatFormatting.LIGHT_PURPLE), false);

        if (totalMobs == 0 && bossCount == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucun_mob_restant_vague_clear")).withStyle(ChatFormatting.GRAY), false);
        }

        for (Map.Entry<String, List<Entity>> e : byType.entrySet()) {
            String type = e.getKey();
            List<Entity> ents = e.getValue();
            ctx.getSource().sendSuccess(() -> Component.literal("  • " + type + " × " + ents.size()).withStyle(ChatFormatting.WHITE), false);
            int shown = Math.min(3, ents.size());
            for (int i = 0; i < shown; i++) {
                Entity ent = ents.get(i);
                String coord = "     " + ((int) ent.getX()) + " / " + ((int) ent.getY()) + " / " + ((int) ent.getZ());
                ctx.getSource().sendSuccess(() -> Component.literal(coord).withStyle(ChatFormatting.GRAY), false);
            }
            if (ents.size() > 3) {
                int more = ents.size() - 3;
                ctx.getSource().sendSuccess(() -> Component.literal("     ... + " + more + com.wavesurvivor.i18n.WSLang.t("srv.autres")).withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
        return 1;
    }

    /** /ws debug soldier : état détaillé du soldat du royaume visé (ou le plus proche dans un rayon de 8 blocs). */
    private static int debugSoldier(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer p = ctx.getSource().getPlayerOrException();
        net.minecraft.world.phys.Vec3 eye = p.getEyePosition();
        net.minecraft.world.phys.Vec3 look = p.getLookAngle();
        com.wavesurvivor.entity.KingdomSoldier best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (var s : p.level().getEntitiesOfClass(com.wavesurvivor.entity.KingdomSoldier.class, p.getBoundingBox().inflate(8))) {
            net.minecraft.world.phys.Vec3 to = s.position().add(0, s.getBbHeight() * 0.5, 0).subtract(eye);
            double dot = to.normalize().dot(look);           // 1 = pile dans le viseur
            double score = dot * 10 - to.length() * 0.1;
            if (score > bestScore) { bestScore = score; best = s; }
        }
        if (best == null) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.debug_soldier_none")));
            return 0;
        }
        final com.wavesurvivor.entity.KingdomSoldier target = best;
        ctx.getSource().sendSuccess(() -> Component.literal("§b§l[Debug] §r").append(target.getName()), false);
        for (String line : target.debugLines()) ctx.getSource().sendSuccess(() -> Component.literal("  " + line), false);
        return 1;
    }

    private static int debug(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer srv = ctx.getSource().getServer();
        long now = srv.getTickCount();

        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.debug")).withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.tick_serveur") + now).withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.boss_actifs") + BossManager.activeBossCount()).withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.trackers_minions") + BossMinionTracker.trackerCount()).withStyle(ChatFormatting.WHITE), false);

        Map<UUID, BossMinionTracker.TrackerState> states = BossMinionTracker.statesSnapshot();
        if (states.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucun_tracker_minion_actif")).withStyle(ChatFormatting.GRAY), false);
        } else {
            for (Map.Entry<UUID, BossMinionTracker.TrackerState> e : states.entrySet()) {
                BossMinionTracker.TrackerState s = e.getValue();
                LivingEntity boss = findLiving(srv, e.getKey());
                String bossName = s.boss != null ? s.boss.bossName : "?";
                String status;
                if (boss == null) {
                    status = com.wavesurvivor.i18n.WSLang.t("srv.mort_nettoyage_au_prochain_tick");
                } else if (s.boss.minionConfig != null && "interval".equalsIgnoreCase(s.boss.minionConfig.triggerType)) {
                    long remaining = Math.max(0, (s.nextTriggerTick - now) / 20);
                    status = com.wavesurvivor.i18n.WSLang.t("srv.hp") + (int) boss.getHealth() + "/" + (int) boss.getMaxHealth()
                            + com.wavesurvivor.i18n.WSLang.t("srv.prochaine_invocation_dans") + remaining + "s";
                } else if (s.boss.minionConfig != null) {
                    float pct = boss.getHealth() / boss.getMaxHealth() * 100f;
                    status = com.wavesurvivor.i18n.WSLang.t("srv.hp") + (int) boss.getHealth() + "/" + (int) boss.getMaxHealth()
                            + " (" + (int) pct + com.wavesurvivor.i18n.WSLang.t("srv.seuil") + (int) s.boss.minionConfig.triggerValue + "% "
                            + (s.healthTriggered ? com.wavesurvivor.i18n.WSLang.t("srv.deja_declenche") : com.wavesurvivor.i18n.WSLang.t("srv.en_attente"));
                } else {
                    status = com.wavesurvivor.i18n.WSLang.t("srv.config_invalide");
                }
                final String bn = bossName, st = status;
                ctx.getSource().sendSuccess(() -> Component.literal("  • " + bn + " : " + st).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
        }
        return 1;
    }

    private static Entity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }

    private static LivingEntity findLiving(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e instanceof LivingEntity living) return living;
        }
        return null;
    }

    private static int setSpawn(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "hordeName");
        HordeConfigMultiData horde = WaveSurvivorMod.getConfig().findHorde(name);
        if (horde == null) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.horde_introuvable_1934") + name + "'."));
            return 0;
        }
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cette_commande_doit_etre_tapee_en_jeu_pa")));
            return 0;
        }
        int x = (int) Math.round(player.getX());
        int y = (int) Math.round(player.getY());
        int z = (int) Math.round(player.getZ());
        String dim = player.level().dimension().location().toString();

        if (horde.configData == null) horde.configData = new HordeConfigMultiData.ConfigDataInner();
        if (horde.configData.spawnCoords == null) horde.configData.spawnCoords = new HordeConfigMultiData.Coords();
        horde.configData.spawnCoords.x = x;
        horde.configData.spawnCoords.y = y;
        horde.configData.spawnCoords.z = z;
        horde.configData.spawnCoords.dimension = dim;

        try {
            Path cfgFile = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("config.json");
            if (!Files.exists(cfgFile)) {
                ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.config_json_introuvable_la_modification")));
                return 0;
            }
            String content = new String(Files.readAllBytes(cfgFile), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            JsonObject donnees = root.has("donnees") ? root.getAsJsonObject("donnees") : root;
            if (!donnees.has("HordeConfigMulti")) throw new RuntimeException("HordeConfigMulti absent");

            for (var el : donnees.getAsJsonArray("HordeConfigMulti")) {
                JsonObject h = el.getAsJsonObject();
                if (h.has("hordeName") && h.get("hordeName").getAsString().equals(name)) {
                    JsonObject configData = h.has("configData") ? h.getAsJsonObject("configData") : new JsonObject();
                    JsonObject coords = new JsonObject();
                    coords.addProperty("x", x);
                    coords.addProperty("y", y);
                    coords.addProperty("z", z);
                    coords.addProperty("dimension", dim);
                    configData.add("spawnCoords", coords);
                    h.add("configData", configData);
                    break;
                }
            }
            Files.write(cfgFile, PRETTY_GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            WaveSurvivorMod.LOGGER.info("[setSpawn] Horde '{}' spawnCoords → ({},{},{}) dim={}", name, x, y, z, dim);

            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.spawncoords_de") + name + com.wavesurvivor.i18n.WSLang.t("srv.mis_a") + x + "," + y + "," + z + com.wavesurvivor.i18n.WSLang.t("srv.dim") + dim).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[setSpawn] Erreur sauvegarde : {}", e.getMessage(), e);
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.erreur_sauvegarde") + e.getMessage()));
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> suggestHordeNames(SuggestionsBuilder builder) {
        ModConfig cfg = WaveSurvivorMod.getConfig();
        if (cfg.hordeConfigMulti != null) {
            String remaining = builder.getRemaining().toLowerCase();
            for (HordeConfigMultiData h : cfg.hordeConfigMulti) {
                if (h.hordeName != null && h.hordeName.toLowerCase().startsWith(remaining)) {
                    String s = h.hordeName.contains(" ") ? "\"" + h.hordeName + "\"" : h.hordeName;
                    builder.suggest(s);
                }
            }
        }
        return builder.buildFuture();
    }

    /** /ws benediction menu : ouvre le menu de sélection (utile si le joueur a manqué le message auto). */
    private static int benedictionMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cette_commande_doit_etre_executee_par_un")));
            return 0;
        }
        if (!HordeManager.get().isRunning()) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucune_horde_en_cours_pas_de_benediction")));
            return 0;
        }
        int currentWave = HordeManager.get().getCurrentWave();
        if (currentWave > 0 && RogueUpgradeManager.getLastChosenWave(player) == currentWave) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.vous_avez_deja_choisi_un_don_pour_cette")).withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        RogueUpgradeManager.openMenu(player);
        return 1;
    }

    /** /ws benediction liste : ouvre le catalogue de toutes les bénédictions du serveur. */
    private static int benedictionListe(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cette_commande_doit_etre_executee_par_un")));
            return 0;
        }
        com.wavesurvivor.network.NetworkHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.wavesurvivor.network.OpenBenedictionListScreenPacket());
        return 1;
    }

    /** /ws benediction choose <id> : le joueur choisit un don depuis le menu (via clic tellraw). */
    private static int benedictionChoose(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cette_commande_doit_etre_executee_par_un")));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        RogueUpgrade upg = RogueUpgradeRegistry.get(id);
        if (upg == null) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.benediction_inconnue") + id).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!HordeManager.get().isRunning()) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucune_horde_en_cours")).withStyle(ChatFormatting.RED));
            return 0;
        }
        int currentWave = HordeManager.get().getCurrentWave();
        if (currentWave > 0 && RogueUpgradeManager.getLastChosenWave(player) == currentWave) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.vous_avez_deja_choisi_un_don_pour_cette")).withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        RogueUpgradeManager.addUpgrade(player, id);
        RogueUpgradeManager.setLastChosenWave(player, currentWave);
        com.wavesurvivor.network.EventFeedPacket.toPlayer(player, Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.vous_avez_choisi") + upg.name())
                .withStyle(upg.color(), ChatFormatting.BOLD), "minecraft:totem_of_undying", 0xFFFFE27A, false);
        return 1;
    }

    /** /ws roulettechest list : liste toutes les configs disponibles. */
    private static int rouletteList(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.roulette_chests") + RouletteChestRegistry.count() + ") ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (RouletteChestConfig cfg : RouletteChestRegistry.all()) {
            src.sendSuccess(() -> Component.literal("§e• §f" + cfg.chestName()
                    + com.wavesurvivor.i18n.WSLang.t("srv.key") + cfg.configKey() + ", " + cfg.rewards().size() + com.wavesurvivor.i18n.WSLang.t("srv.rewards")), false);
        }
        src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.utilisez_ws_roulettechest_give_key_pour")), false);
        return RouletteChestRegistry.count();
    }

    /** /ws roulettechest give <configKey> : donne un coffre nommé + une clé correspondante. */
    private static int rouletteGive(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cette_commande_doit_etre_executee_par_un")));
            return 0;
        }
        String configKey = StringArgumentType.getString(ctx, "configKey");
        RouletteChestConfig cfg = RouletteChestRegistry.get(configKey);
        if (cfg == null) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.config_introuvable") + configKey
                    + com.wavesurvivor.i18n.WSLang.t("srv.utilisez_ws_roulettechest_list")).withStyle(ChatFormatting.RED));
            return 0;
        }

        var chestItem = RouletteChestManager.createChestItem(cfg);
        var keyItem = RouletteChestManager.createKeyItem(cfg);

        if (!player.getInventory().add(chestItem)) player.drop(chestItem, false);
        if (!player.getInventory().add(keyItem)) player.drop(keyItem, false);

        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.recu_35e2") + cfg.chestName() + "\" + \"" + cfg.keyName() + "\"").withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.pose_le_coffre_puis_clic_droit_avec_la_c")));
        return 1;
    }

    /** /ws roulettechest key <configKey> : donne uniquement la clé associée à un config, avec info sur ce qu'elle peut ouvrir. */
    private static int rouletteKey(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.doit_etre_execute_par_un_joueur_en_jeu")));
            return 0;
        }
        String configKey = StringArgumentType.getString(ctx, "configKey");
        RouletteChestConfig cfg = RouletteChestRegistry.get(configKey);
        if (cfg == null) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.config_introuvable") + configKey
                    + com.wavesurvivor.i18n.WSLang.t("srv.utilisez_ws_roulettechest_list")).withStyle(ChatFormatting.RED));
            return 0;
        }

        var keyItem = RouletteChestManager.createKeyItem(cfg);
        if (!player.getInventory().add(keyItem)) player.drop(keyItem, false);

        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.recu_35e2") + cfg.keyName() + "\"").withStyle(ChatFormatting.GREEN));

        // Info : quels coffres cette clé peut ouvrir
        //  - Le coffre "source" de la config (chestName match direct)
        //  - Tout coffre dont allowedChests contient ce chestName
        java.util.List<String> canOpen = new java.util.ArrayList<>();
        canOpen.add(cfg.chestName());
        for (RouletteChestConfig other : RouletteChestRegistry.all()) {
            if (other == cfg) continue;
            if (other.allowedChests() != null && other.allowedChests().contains(cfg.chestName())) {
                canOpen.add(other.chestName());
            }
        }
        if (canOpen.size() == 1) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.ouvre") + canOpen.get(0)));
        } else {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.ouvre") + String.join("§7, §f", canOpen)));
        }
        return 1;
    }

    /** /ws roulettechest spawn add <hordeName> <configKey> : ajoute un spawn à la position du joueur. */
    private static int rouletteSpawnAdd(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.doit_etre_execute_par_un_joueur_en_jeu")));
            return 0;
        }
        String hordeName = StringArgumentType.getString(ctx, "hordeName");
        String configKey = StringArgumentType.getString(ctx, "configKey");
        RouletteChestConfig cfg = RouletteChestRegistry.get(configKey);
        if (cfg == null) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.config_introuvable") + configKey));
            return 0;
        }
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());
        String dim = player.level().dimension().location().toString();
        RouletteChestSpawnStore.SpawnPoint sp = new RouletteChestSpawnStore.SpawnPoint(configKey, x, y, z, dim);
        RouletteChestSpawnStore.addSpawn(hordeName, sp);
        int total = RouletteChestSpawnStore.getSpawns(hordeName).size();
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.spawn_ajoute_a_la_horde") + hordeName
                + "§a : §f" + cfg.chestName() + " §7@ (" + x + ", " + y + ", " + z + ") §a[" + total + com.wavesurvivor.i18n.WSLang.t("srv.spawn_s_total")));
        return 1;
    }

    /** /ws roulettechest spawn list <hordeName> : liste les spawns configurés. */
    private static int rouletteSpawnList(CommandContext<CommandSourceStack> ctx) {
        String hordeName = StringArgumentType.getString(ctx, "hordeName");
        var src = ctx.getSource();
        var spawns = RouletteChestSpawnStore.getSpawns(hordeName);
        if (spawns.isEmpty()) {
            src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucun_spawn_configure_pour_la_horde") + hordeName), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.spawns_pour") + hordeName + " §6(" + spawns.size() + ") ==="), false);
        for (int i = 0; i < spawns.size(); i++) {
            var sp = spawns.get(i);
            RouletteChestConfig cfg = RouletteChestRegistry.get(sp.configKey);
            String name = cfg != null ? cfg.chestName() : sp.configKey;
            int idx = i + 1;
            src.sendSuccess(() -> Component.literal("§e[" + idx + "] §f" + name
                    + " §7@ (" + sp.x + ", " + sp.y + ", " + sp.z + ") " + sp.dimension), false);
        }
        return spawns.size();
    }

    /** /ws roulettechest spawn clear <hordeName> : reset tous les spawns d'une horde. */
    private static int rouletteSpawnClear(CommandContext<CommandSourceStack> ctx) {
        String hordeName = StringArgumentType.getString(ctx, "hordeName");
        int removed = RouletteChestSpawnStore.clearSpawns(hordeName);
        ctx.getSource().sendSuccess(() -> Component.literal("§a✓ " + removed + com.wavesurvivor.i18n.WSLang.t("srv.spawn_s_retire_s_de_la_horde") + hordeName), false);
        return removed;
    }

    /** /ws roulettechest create <name> : ouvre le ChestBuilderScreen pour créer un roulette chest custom. */
    private static int rouletteCreate(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.doit_etre_execute_par_un_joueur_en_jeu")));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        if (name == null || name.isBlank()) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.nom_vide_usage_ws_roulettechest_create_n")));
            return 0;
        }
        // Warn si un chest custom du même nom existe déjà (Phase 1 : juste un warn, l'édition future viendra en Phase 5)
        if (CustomChestStore.get(name) != null) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.un_coffre_custom_nomme") + name + com.wavesurvivor.i18n.WSLang.t("srv.existe_deja_il_sera_ecrase_si_tu_valides")).withStyle(ChatFormatting.YELLOW));
        }
        NetworkHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new OpenChestBuilderPacket(name));
        return 1;
    }

    /** /ws roulettechest edit <name> : rouvre le ChestBuilderScreen sur un chest custom existant. */
    private static int rouletteEdit(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.doit_etre_execute_par_un_joueur_en_jeu")));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        var data = CustomChestStore.get(name);
        if (data == null) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.coffre_custom_introuvable") + name + com.wavesurvivor.i18n.WSLang.t("srv.utilise_ws_roulettechest_list_pour_voir")));
            return 0;
        }
        String json = new com.google.gson.Gson().toJson(data);
        NetworkHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new OpenChestBuilderPacket(name, json));
        return 1;
    }

    /** /ws roulettechest delete <name> : supprime un chest custom (fichier + registry). */
    private static int rouletteDelete(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        var data = CustomChestStore.get(name);
        if (data == null) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.coffre_custom_introuvable") + name + "\"."));
            return 0;
        }
        boolean ok = CustomChestStore.delete(name);
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.coffre_d0f6") + name + com.wavesurvivor.i18n.WSLang.t("srv.supprime"))
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.echec_de_suppression")));
        return 0;
    }

    /** /ws pacte list : liste les 3 pactes disponibles. */
    private static int pacteList(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.pactes_de_sang") + BloodPactRegistry.count() + ") ==="), false);
        for (BloodPactConfig cfg : BloodPactRegistry.all()) {
            String typeLabel = cfg.pactType() == BloodPactConfig.PactType.SEALED ? com.wavesurvivor.i18n.WSLang.t("srv.scelle") : "§erandom";
            src.sendSuccess(() -> Component.literal("§c• §f" + cfg.pactName()
                    + " §7— " + cfg.displayName()
                    + " §7(" + cfg.effects().size() + com.wavesurvivor.i18n.WSLang.t("srv.effets") + typeLabel + "§7)"), false);
        }
        return BloodPactRegistry.count();
    }

    /** /ws pacte give <pactName> : donne la tête custom du pacte. */
    private static int pacteGive(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cette_commande_doit_etre_executee_par_un")));
            return 0;
        }
        String pactName = StringArgumentType.getString(ctx, "pactName");
        BloodPactConfig cfg = BloodPactRegistry.get(pactName);
        if (cfg == null) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.pacte_introuvable") + pactName
                    + com.wavesurvivor.i18n.WSLang.t("srv.utilisez_ws_pacte_list")).withStyle(ChatFormatting.RED));
            return 0;
        }
        var head = BloodPactManager.createHeadItem(cfg);
        if (!player.getInventory().add(head)) player.drop(head, false);
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.recu_70c1") + cfg.displayName()));
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.clic_droit_avec_la_tete_en_main_pour_sce")));
        return 1;
    }

    /** /ws pacte resetcd : reset son propre cooldown (en cas de bug). */
    private static int pacteResetCd(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cette_commande_doit_etre_executee_par_un")));
            return 0;
        }
        BloodPactManager.resetCooldown(player);
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.cooldown_des_pactes_reinitialise")));
        return 1;
    }

    // ================================================================
    // ALTARS
    // ================================================================

    /** /ws altar give <hordeName> : donne un Autel Runique pré-lié à une horde. */
    private static int altarGive(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.doit_etre_execute_par_un_joueur_en_jeu")));
            return 0;
        }
        String hordeName = StringArgumentType.getString(ctx, "hordeName");
        var stack = new net.minecraft.world.item.ItemStack(ModItems.ALTAR_RUNIC_ITEM.get());
        stack.getOrCreateTag().putString(com.wavesurvivor.altar.AltarEventHandler.NBT_BOUND_HORDE, hordeName);
        stack.setHoverName(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_runique") + hordeName));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_runique_lie_a_la_horde") + hordeName + com.wavesurvivor.i18n.WSLang.t("srv.recu")));
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.pose_le_puis_clic_droit_dessus_pour_l_ac")));
        return 1;
    }

    /** /ws altar give : donne un Autel Runique vierge (à lier via /ws altar bind ensuite). */
    private static int altarGiveBlank(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) { return 0; }
        var stack = new net.minecraft.world.item.ItemStack(ModItems.ALTAR_RUNIC_ITEM.get());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_runique_vierge_recu")));
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.pose_le_puis_vise_le_et_fais_ws_altar_bi")));
        return 1;
    }

    /** /ws altar bind <hordeName> : lie l'autel visé par le joueur à une horde. */
    private static int altarBind(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.doit_etre_execute_par_un_joueur_en_jeu")));
            return 0;
        }
        String hordeName = StringArgumentType.getString(ctx, "hordeName");

        // Raycast pour trouver le block visé
        var hit = player.pick(6.0, 0.0f, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bhr) || bhr.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.vise_un_autel_runique_pose_puis_relance")));
            return 0;
        }
        var pos = bhr.getBlockPos();
        var state = player.level().getBlockState(pos);
        if (state.getBlock() != com.wavesurvivor.registry.ModBlocks.ALTAR_RUNIC.get()) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.ce_block_n_est_pas_un_autel_runique")));
            return 0;
        }

        String dim = player.level().dimension().location().toString();
        var existing = AltarStore.get(dim, pos);
        if (existing == null) {
            // Ne devrait pas arriver si le placement handler a marqué l'entrée, mais safety net
            existing = new AltarStore.AltarEntry(hordeName, dim, pos.getX(), pos.getY(), pos.getZ(),
                    com.wavesurvivor.altar.AltarBlock.AltarType.HORDE_TRIGGER.name());
        } else {
            existing.hordeName = hordeName;
        }
        var zone = com.wavesurvivor.altar.AltarRecipes.getZone(hordeName);
        existing.zoneRadius = zone != null ? zone.radius : 0;
        var style = com.wavesurvivor.altar.AltarRecipes.getStyle(hordeName);
        if (style != null && player.level() instanceof net.minecraft.server.level.ServerLevel sl0) {
            com.wavesurvivor.altar.AltarManager.applyStyle(sl0, pos, existing, style.dye(), style.particles);
        }
        AltarStore.register(existing);
        if (zone != null && player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            com.wavesurvivor.altar.AltarZone.transform(sl, pos, zone);
        }
        player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autel_lie_a_la_horde") + hordeName));
        return 1;
    }

    /** /ws altar list : liste tous les autels enregistrés. */
    private static int altarList(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        var all = AltarStore.all();
        if (all.isEmpty()) {
            src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucun_autel_enregistre")), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.autels") + all.size() + ") ==="), false);
        for (var e : all) {
            String h = e.hordeName != null ? e.hordeName : com.wavesurvivor.i18n.WSLang.t("srv.non_lie_9b38");
            src.sendSuccess(() -> Component.literal("§c• §f" + h
                    + " §7@ (" + e.x + ", " + e.y + ", " + e.z + ") " + e.dimension), false);
        }
        return all.size();
    }
}
