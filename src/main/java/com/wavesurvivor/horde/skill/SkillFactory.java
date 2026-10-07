package com.wavesurvivor.horde.skill;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.impl.SkillChill;
import com.wavesurvivor.horde.skill.impl.SkillDashCharge;
import com.wavesurvivor.horde.skill.impl.SkillExecution;
import com.wavesurvivor.horde.skill.impl.SkillForceField;
import com.wavesurvivor.horde.skill.impl.SkillGatling;
import com.wavesurvivor.horde.skill.impl.SkillIllusions;
import com.wavesurvivor.horde.skill.impl.SkillMinions;
import com.wavesurvivor.horde.skill.impl.SkillMortar;
import com.wavesurvivor.horde.skill.impl.SkillPotionRain;
import com.wavesurvivor.horde.skill.impl.SkillProjectilePotion;
import com.wavesurvivor.horde.skill.impl.SkillShadowTrap;
import com.wavesurvivor.horde.skill.impl.SkillSolar;
import com.wavesurvivor.horde.skill.impl.SkillSpectralVanish;
import com.wavesurvivor.horde.skill.impl.SkillSupernova;
import com.wavesurvivor.horde.skill.impl.SkillTotemProtection;
import com.wavesurvivor.horde.skill.impl.SkillUppercut;
import com.wavesurvivor.horde.skill.impl.SkillVenom;
import com.wavesurvivor.horde.skill.impl.SkillVoidScream;
import com.wavesurvivor.horde.skill.impl.SkillWebTrap;

/**
 * Instancie un BossSkill selon skillType.
 * Types supportés :
 *   - Phase 2b : mortar, dash_charge, projectile_potion, totem_protection
 *   - Phase 2c Groupe 1 : minions, chill, venom
 *   - Phase 2c Groupe 2 : uppercut, gatling, web_trap
 *   - Phase 2d Groupe 3 : void_scream, solar, supernova, spectral_vanish, illusions, shadow_trap, force_field, execution
 */
public class SkillFactory {

    public static BossSkill create(CustomSkillData config) {
        if (config == null || config.skillType == null) return null;
        return switch (config.skillType.toLowerCase()) {
            case "mortar"            -> new SkillMortar(config);
            case "dash_charge"       -> new SkillDashCharge(config);
            case "projectile_potion" -> new SkillProjectilePotion(config);
            case "totem_protection"  -> new SkillTotemProtection(config);
            case "minions"           -> new SkillMinions(config);
            case "chill"             -> new SkillChill(config);
            case "venom"             -> new SkillVenom(config);
            case "uppercut"          -> new SkillUppercut(config);
            case "gatling"           -> new SkillGatling(config);
            case "web_trap"          -> new SkillWebTrap(config);
            case "void_scream"       -> new SkillVoidScream(config);
            case "solar"             -> new SkillSolar(config);
            case "supernova"         -> new SkillSupernova(config);
            case "spectral_vanish"   -> new SkillSpectralVanish(config);
            case "illusions"         -> new SkillIllusions(config);
            case "shadow_trap"       -> new SkillShadowTrap(config);
            case "force_field"       -> new SkillForceField(config);
            case "execution"         -> new SkillExecution(config);
            case "potion_rain"       -> new SkillPotionRain(config);
            // Nécropole (B2)
            case "life_drain"        -> new com.wavesurvivor.horde.skill.impl.SkillLifeDrain(config);
            case "death_mark"        -> new com.wavesurvivor.horde.skill.impl.SkillDeathMark(config);
            case "resurrection"      -> new com.wavesurvivor.horde.skill.impl.SkillResurrection(config);
            case "burial"            -> new com.wavesurvivor.horde.skill.impl.SkillBurial(config);
            // Terres Brûlées (N2)
            case "eruption"          -> new com.wavesurvivor.horde.skill.impl.SkillEruption(config);
            case "wither_curse"      -> new com.wavesurvivor.horde.skill.impl.SkillWitherCurse(config);
            case "burning_touch"     -> new com.wavesurvivor.horde.skill.impl.SkillBurningTouch(config);
            // Néant Éternel (E2)
            case "blink"             -> new com.wavesurvivor.horde.skill.impl.SkillBlink(config);
            case "gravity_well"      -> new com.wavesurvivor.horde.skill.impl.SkillGravityWell(config);
            // Iron's Spells (optionnel)
            case "irons_spell"       -> new com.wavesurvivor.horde.skill.impl.SkillIronsSpell(config);
            // Boss 2.0 : mécaniques signatures (réutilisables par n'importe quel monstre)
            case "grave_grip"        -> new com.wavesurvivor.horde.skill.impl.SkillGraveGrip(config);
            case "war_banner"        -> new com.wavesurvivor.horde.skill.impl.SkillWarBanner(config);
            case "homing_orbs"       -> new com.wavesurvivor.horde.skill.impl.SkillHomingOrbs(config);
            case "doom_mark"         -> new com.wavesurvivor.horde.skill.impl.SkillDoomMark(config);
            case "telegraph"         -> new com.wavesurvivor.horde.skill.impl.SkillTelegraph(config);
            default -> {
                WaveSurvivorMod.LOGGER.warn("[SkillFactory] Type non implémenté : {} (skill '{}')",
                        config.skillType, config.skillName);
                yield null;
            }
        };
    }
}
