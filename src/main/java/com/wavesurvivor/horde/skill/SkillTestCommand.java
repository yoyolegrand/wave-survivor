package com.wavesurvivor.horde.skill;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * COMMANDE DE TEST DES COMPÉTENCES (opérateurs) : lance une compétence de boss DEPUIS LE JOUEUR, sans attendre une horde.
 *   /ws skill list                  liste les compétences disponibles
 *   /ws skill <nom>                 le joueur est le « boss », la cible est l'entité visée (sinon la plus proche, sinon lui-même)
 *   /ws skill <nom> self            le joueur est à la fois le boss et la cible (pour sentir l'effet sur un joueur)
 * La compétence est exécutée telle quelle (mêmes réglages que sur le boss, ceux de la fiche de la compétence), sans temps de
 * recharge ni phase. Certaines compétences supposent un monstre (déplacement, objectifs) : si l'une échoue, le message le dit.
 */
public final class SkillTestCommand {

    private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, builder) -> {
        String rem = builder.getRemaining().toLowerCase().replace("\"", "");
        var cfg = WaveSurvivorMod.getConfig();
        if (cfg != null && cfg.customSkill != null) {
            for (CustomSkillData s : cfg.customSkill) {
                if (s.skillName == null || !s.skillName.toLowerCase().startsWith(rem)) continue;
                builder.suggest(s.skillName.contains(" ") ? "\"" + s.skillName + "\"" : s.skillName);
            }
        }
        return builder.buildFuture();
    };

    private SkillTestCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("skill").requires(src -> src.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.argument("name", StringArgumentType.string()).suggests(NAMES)
                        .executes(ctx -> cast(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false))
                        .then(Commands.literal("self")
                                .executes(ctx -> cast(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true))));
    }

    private static int fail(CommandSourceStack src, String msg) {
        src.sendFailure(Component.literal(msg));
        return 0;
    }

    private static int list(CommandSourceStack src) {
        var cfg = WaveSurvivorMod.getConfig();
        List<String> names = new ArrayList<>();
        if (cfg != null && cfg.customSkill != null) {
            for (CustomSkillData s : cfg.customSkill) if (s.skillName != null) names.add(s.skillName + " §8(" + s.skillType + ")§r");
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        final String text = WSLang.t("skilltest.list", names.size()) + "\n" + String.join(", ", names);
        src.sendSuccess(() -> Component.literal(text), false);
        return names.size();
    }

    private static int cast(CommandSourceStack src, String name, boolean self) {
        ServerPlayer p;
        try { p = src.getPlayerOrException(); } catch (Exception e) { return fail(src, WSLang.t("skilltest.err.player")); }
        var cfg = WaveSurvivorMod.getConfig();
        CustomSkillData data = cfg == null ? null : cfg.findCustomSkill(name.trim());
        if (data == null) return fail(src, WSLang.t("skilltest.err.none", name));
        BossSkill skill = SkillFactory.create(data);
        if (skill == null) return fail(src, WSLang.t("skilltest.err.type", String.valueOf(data.skillType)));

        ServerLevel level = p.serverLevel();
        LivingEntity target = self ? p : pickTarget(p);
        try {
            skill.execute(p, target, level);
        } catch (Throwable t) {
            WaveSurvivorMod.LOGGER.warn("[SkillTest] '{}' a échoué : {}", name, t.toString());
            return fail(src, WSLang.t("skilltest.err.fail", data.skillName, t.getClass().getSimpleName() + ": " + t.getMessage()));
        }
        final String shownTarget = target == p ? WSLang.t("skilltest.self") : target.getName().getString();
        src.sendSuccess(() -> Component.literal(WSLang.t("skilltest.cast", data.skillName, String.valueOf(data.skillType), shownTarget)), true);
        return 1;
    }

    /** Entité visée (cône de 20°, 40 blocs), sinon la plus proche dans 24 blocs, sinon le joueur lui-même. */
    private static LivingEntity pickTarget(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        AABB box = p.getBoundingBox().inflate(40);
        LivingEntity aimed = null, nearest = null;
        double aimedDist = Double.MAX_VALUE, nearestDist = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isSpectator())) {
            double d = e.distanceTo(p);
            Vec3 to = e.getEyePosition().subtract(eye).normalize();
            if (to.dot(look) > 0.94 && d < aimedDist) { aimed = e; aimedDist = d; }
            if (d <= 24 && d < nearestDist) { nearest = e; nearestDist = d; }
        }
        return aimed != null ? aimed : nearest != null ? nearest : p;
    }
}
