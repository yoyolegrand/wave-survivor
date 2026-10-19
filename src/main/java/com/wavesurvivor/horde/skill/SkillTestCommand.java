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
 *   /ws skill <nom>                 un monstre de test (invulnérable, sans IA) apparaît devant toi et lance la compétence SUR TOI,
 *                                   comme un vrai boss ; il disparaît au bout d'une minute ou au test suivant
 *   /ws skill <nom> me              c'est TOI qui lances la compétence, sur l'entité que tu vises (sinon la plus proche, sinon toi)
 * La compétence est exécutée telle quelle (réglages de sa fiche), sans temps de recharge ni phase. ATTENTION : la plupart des
 * compétences ignorent les joueurs en CRÉATIF ou SPECTATEUR (comme en partie) : passe en survie pour voir leurs effets.
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
                        .then(Commands.literal("me")
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

    /** Monstre de test actif par joueur (retiré au test suivant ou au bout d'une minute). */
    private static final java.util.Map<java.util.UUID, java.util.UUID> DUMMIES = new java.util.HashMap<>();

    private static int cast(CommandSourceStack src, String name, boolean asPlayer) {
        ServerPlayer p;
        try { p = src.getPlayerOrException(); } catch (Exception e) { return fail(src, WSLang.t("skilltest.err.player")); }
        var cfg = WaveSurvivorMod.getConfig();
        CustomSkillData data = cfg == null ? null : cfg.findCustomSkill(name.trim());
        if (data == null) return fail(src, WSLang.t("skilltest.err.none", name));
        BossSkill skill = SkillFactory.create(data);
        if (skill == null) return fail(src, WSLang.t("skilltest.err.type", String.valueOf(data.skillType)));

        ServerLevel level = p.serverLevel();
        LivingEntity caster, target;
        if (asPlayer) {
            caster = p;
            target = pickTarget(p);
        } else {
            caster = spawnDummy(p, level, data.skillName);
            if (caster == null) return fail(src, WSLang.t("skilltest.err.fail", data.skillName, "monstre de test"));
            target = p;
        }
        try {
            skill.execute(caster, target, level);
        } catch (Throwable t) {
            WaveSurvivorMod.LOGGER.warn("[SkillTest] '{}' a échoué : {}", name, t.toString());
            return fail(src, WSLang.t("skilltest.err.fail", data.skillName, t.getClass().getSimpleName() + ": " + t.getMessage()));
        }
        final String shownTarget = target == p ? WSLang.t("skilltest.self") : target.getName().getString();
        src.sendSuccess(() -> Component.literal(WSLang.t("skilltest.cast", data.skillName, String.valueOf(data.skillType), shownTarget)), true);
        if (p.isCreative() || p.isSpectator()) src.sendSuccess(() -> Component.literal(WSLang.t("skilltest.warn_creative")), false);
        return 1;
    }

    /** Monstre de test : invulnérable, sans IA, 7 blocs devant le joueur, tourné vers lui, qui le cible. */
    private static LivingEntity spawnDummy(ServerPlayer p, ServerLevel level, String skillName) {
        java.util.UUID old = DUMMIES.remove(p.getUUID());
        if (old != null) {
            net.minecraft.world.entity.Entity e = level.getEntity(old);
            if (e != null) e.discard();
        }
        Vec3 look = p.getLookAngle();
        double hx = look.x, hz = look.z, len = Math.hypot(hx, hz);
        if (len < 0.01) { hx = 0; hz = 1; len = 1; }
        double x = p.getX() + hx / len * 7, z = p.getZ() + hz / len * 7;
        double y = Math.max(p.getY(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z)));
        net.minecraft.world.entity.monster.Husk d = net.minecraft.world.entity.EntityType.HUSK.create(level);
        if (d == null) return null;
        d.moveTo(x, y, z, (float) (Math.toDegrees(Math.atan2(-(p.getX() - x), p.getZ() - z))), 0f);
        d.setNoAi(true);
        d.setInvulnerable(true);
        d.setSilent(true);
        d.setPersistenceRequired();
        d.setCustomName(Component.literal("§7[Test] " + skillName));
        d.setCustomNameVisible(true);
        var atk = d.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(8.0);
        d.setTarget(p);
        d.addTag("ws_skilltest");
        level.addFreshEntity(d);
        DUMMIES.put(p.getUUID(), d.getUUID());
        DelayedActionScheduler.schedule(level.getServer(), 1200, d::discard, "skilltest dummy");
        return d;
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
