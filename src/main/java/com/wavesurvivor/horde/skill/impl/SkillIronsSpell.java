package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.compat.IronsSpells;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * irons_spell : l'entité lance un sort d'Iron's Spells (Iron's + addons).
 *   - vers sa cible (portée ironsSpellRange), ou sur elle-même (ironsSpellOnSelf : soin, bouclier, invocation…),
 *     éventuellement seulement sous ironsSpellBelowHealth % de PV ;
 *   - fonctionne sur N'IMPORTE QUELLE base (zombie, squelette, mob moddé…) ; les mages d'Iron's gardent leurs animations ;
 *   - sans Iron's Spells installé : la compétence ne fait simplement rien.
 */
public class SkillIronsSpell extends BossSkill {

    public SkillIronsSpell(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(20, config.ironsSpellCooldown);
    }

    @Override
    public boolean requiresTarget() {
        return !config.ironsSpellOnSelf; // un sort sur soi peut servir hors combat (soin)
    }

    @Override
    public void execute(LivingEntity caster, LivingEntity target, ServerLevel level) {
        if (!IronsSpells.available()) return;
        LivingEntity aim;
        if (config.ironsSpellOnSelf) {
            float pct = caster.getHealth() / Math.max(1f, caster.getMaxHealth()) * 100f;
            if (pct > Math.max(1, config.ironsSpellBelowHealth)) return;
            aim = null;
        } else {
            if (target == null || !target.isAlive()) return;
            double range = Math.max(2, config.ironsSpellRange);
            if (caster.distanceToSqr(target) > range * range) return;
            aim = target;
        }
        if (!IronsSpells.cast(caster, aim, config.ironsSpellId, config.ironsSpellLevel)) return;
        if (config.ironsSpellMessage != null && !config.ironsSpellMessage.isBlank()) {
            Component msg = Component.literal(config.ironsSpellMessage);
            for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(caster) < 48 * 48)) p.displayClientMessage(msg, true);
        }
    }
}
