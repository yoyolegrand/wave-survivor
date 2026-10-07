package com.wavesurvivor.config.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * CustomSkill au format base44. Union de tous les paramètres possibles ;
 * les champs pertinents sont sélectionnés selon skillType.
 *
 * Phase 2b implémente : mortar, dash_charge, projectile_potion, totem_protection.
 * Phase 2c (Groupe 1) : minions, chill, venom.
 * Phase 2c (Groupe 2) : uppercut, gatling, web_trap.
 * Phase 2d (Groupe 3) : void_scream, solar, supernova, spectral_vanish, illusions, shadow_trap, force_field, execution.
 */
public class CustomSkillData {

    @SerializedName("skillName")
    public String skillName;

    /** Type du skill. Détermine quels champs sont utilisés. */
    @SerializedName("skillType")
    public String skillType;

    /** Boss 2.0 : la compétence ne s'active qu'à partir de cette phase du boss (1 = toujours, 2 = sous 66 % PV, 3 = sous 33 % PV). */
    @SerializedName("unlockPhase")
    public int unlockPhase = 1;

    // === mortar ===
    @SerializedName("mortarProjectileType")
    public String mortarProjectileType = "minecraft:fireball";

    @SerializedName("mortarArc")
    public double mortarArc = 0.8;

    @SerializedName("mortarCooldown")
    public int mortarCooldown = 100; // ticks

    @SerializedName("mortarExplosionPower")
    public double mortarExplosionPower = 2;

    @SerializedName("mortarSpeed")
    public double mortarSpeed = 0.08;

    @SerializedName("mortarSound")
    public String mortarSound;

    // === dash_charge ===
    @SerializedName("dashDelay")
    public int dashDelay = 100; // ticks entre dashs

    @SerializedName("dashMinDistance")
    public double dashMinDistance = 2;

    @SerializedName("dashPower")
    public double dashPower = 2;

    @SerializedName("dashDamageMultiplier")
    public double dashDamageMultiplier = 2;

    @SerializedName("dashSpeedThreshold")
    public double dashSpeedThreshold = 0.6;

    @SerializedName("dashResistanceDuration")
    public int dashResistanceDuration = 20;

    @SerializedName("dashWarningDelay")
    public int dashWarningDelay = 10;

    @SerializedName("dashSound")
    public String dashSound;

    @SerializedName("dashImpactSound")
    public String dashImpactSound;

    // === projectile_potion ===
    @SerializedName("projectileType")
    public String projectileType = "minecraft:arrow";

    @SerializedName("projectileSpeed")
    public double projectileSpeed = 1.5;

    @SerializedName("potionEffect")
    public String potionEffect;

    @SerializedName("potionEffectId")
    public int potionEffectId = -1;

    @SerializedName("potionDuration")
    public int potionDuration = 100;

    @SerializedName("potionAmplifier")
    public int potionAmplifier = 0;

    @SerializedName("potionColor")
    public int potionColor = 0x00FF00;

    /** Cooldown spécifique projectile_potion (pas dans base44 ; défaut 60 ticks = 3s). */
    @SerializedName("potionCooldown")
    public int potionCooldown = 60;

    /** Si true, la flèche est invisible (setInvisible). Défaut false. */
    @SerializedName("invisibleProjectile")
    public boolean invisibleProjectile = false;

    /** Type de particule qui suit le projectile pendant sa trajectoire (ex "minecraft:effect"). Null = pas de traînée. */
    @SerializedName("trailParticle")
    public String trailParticle;

    /** Nombre de particules spawnées par tick le long de la trajectoire. Défaut 3. */
    @SerializedName("trailParticleCount")
    public int trailParticleCount = 3;

    // === totem_protection ===
    @SerializedName("totemId")
    public String totemId;

    @SerializedName("totemCount")
    public int totemCount = 3;

    @SerializedName("totemPositions")
    public List<TotemPosition> totemPositions;

    @SerializedName("protectionRadius")
    public int protectionRadius = 30;

    /** PV de chaque totem (0 = baseHP de la TotemConfig, mini 20). */
    @SerializedName("totemHealth")
    public double totemHealth = 40;

    /** Si tous les totems sont détruits : ils réapparaissent après N secondes (0 = jamais). */
    @SerializedName("totemRespawnSeconds")
    public int totemRespawnSeconds = 0;

    /** Rayon de placement autour du boss (si pas de positions explicites). */
    @SerializedName("totemRadius")
    public double totemRadius = 5;

    public static class TotemPosition {
        @SerializedName("x")
        public int x;
        @SerializedName("z")
        public int z;
    }

    // === minions ===
    /** Type d'entité à invoquer (ex "minecraft:cave_spider", ou nom d'une CustomEntity). */
    @SerializedName("minionType")
    public String minionType = "minecraft:zombie";

    /** Nombre de minions par activation. */
    @SerializedName("minionCount")
    public int minionCount = 2;

    /** HP appliqué à chaque minion (via Attributes.MAX_HEALTH). */
    @SerializedName("minionHealth")
    public double minionHealth = 10;

    /** Vitesse de déplacement du minion. */
    @SerializedName("minionSpeed")
    public double minionSpeed = 0.3;

    /** Chance [0..1] que l'activation spawn effectivement. 1.0 = toujours. Défaut 1.0. */
    @SerializedName("minionSpawnChance")
    public double minionSpawnChance = 1.0;

    /** Son joué au spawn (ex "minecraft:entity.silverfish.step"). */
    @SerializedName("minionSound")
    public String minionSound;

    /** Cooldown entre 2 activations (ticks). Défaut 200 = 10s. */
    @SerializedName("minionCooldown")
    public int minionCooldown = 200;

    // === chill ===
    @SerializedName("chillSlownessDuration")
    public int chillSlownessDuration = 40;

    @SerializedName("chillSlownessAmplifier")
    public int chillSlownessAmplifier = 2;

    @SerializedName("chillBlindnessDuration")
    public int chillBlindnessDuration = 40;

    @SerializedName("chillCooldown")
    public int chillCooldown = 300;

    @SerializedName("chillSound")
    public String chillSound;

    @SerializedName("chillMessage")
    public String chillMessage;

    /** Particule spawnée sur chaque joueur affecté (défaut snowflake). */
    @SerializedName("chillParticle")
    public String chillParticle = "minecraft:snowflake";

    /** Rayon en blocks. Défaut 12. */
    @SerializedName("chillRange")
    public double chillRange = 12;

    // === venom ===
    @SerializedName("venomPoisonDuration")
    public int venomPoisonDuration = 100;

    @SerializedName("venomPoisonAmplifier")
    public int venomPoisonAmplifier = 1;

    @SerializedName("venomNauseaDuration")
    public int venomNauseaDuration = 100;

    @SerializedName("venomNauseaAmplifier")
    public int venomNauseaAmplifier = 1;

    @SerializedName("venomBlindnessDuration")
    public int venomBlindnessDuration = 60;

    /** Cooldown ticks. Défaut 200 = 10s. */
    @SerializedName("venomCooldown")
    public int venomCooldown = 200;

    /** Rayon en blocks. Défaut 8. */
    @SerializedName("venomRange")
    public double venomRange = 8;

    @SerializedName("venomSound")
    public String venomSound;

    @SerializedName("venomMessage")
    public String venomMessage;

    /** Particule spawnée sur chaque joueur affecté (défaut squid_ink). */
    @SerializedName("venomParticle")
    public String venomParticle = "minecraft:squid_ink";

    // === uppercut ===
    /** Force verticale du knock-up (multiplié par 1.0 pour une vitesse Y en blocks/tick). */
    @SerializedName("uppercutPower")
    public double uppercutPower = 2.0;

    @SerializedName("uppercutCooldown")
    public int uppercutCooldown = 100;

    @SerializedName("uppercutSlownessDuration")
    public int uppercutSlownessDuration = 60;

    @SerializedName("uppercutSlownessAmplifier")
    public int uppercutSlownessAmplifier = 2;

    @SerializedName("uppercutAddNausea")
    public boolean uppercutAddNausea = false;

    @SerializedName("uppercutNauseaDuration")
    public int uppercutNauseaDuration = 100;

    @SerializedName("uppercutImpactSound")
    public String uppercutImpactSound;

    @SerializedName("uppercutParticle")
    public String uppercutParticle = "minecraft:explosion";

    @SerializedName("uppercutMessage")
    public String uppercutMessage;

    /** Distance max en blocks pour déclencher l'uppercut (la cible doit être au corps-à-corps). Défaut 4. */
    @SerializedName("uppercutRange")
    public double uppercutRange = 4.0;

    // === gatling ===
    @SerializedName("gatlingCooldown")
    public int gatlingCooldown = 160;

    /** Nombre de projectiles dans la rafale. */
    @SerializedName("gatlingBulletCount")
    public int gatlingBulletCount = 10;

    /** Intervalle en ticks entre deux projectiles de la rafale. */
    @SerializedName("gatlingShotInterval")
    public int gatlingShotInterval = 3;

    @SerializedName("gatlingSpeed")
    public double gatlingSpeed = 2.5;

    /** Dispersion aléatoire (blocks). Plus la valeur est haute, plus les tirs sont éparpillés. */
    @SerializedName("gatlingDispersion")
    public double gatlingDispersion = 2.0;

    @SerializedName("gatlingProjectileType")
    public String gatlingProjectileType = "minecraft:small_fireball";

    @SerializedName("gatlingSound")
    public String gatlingSound;

    @SerializedName("gatlingMessage")
    public String gatlingMessage;

    // === web_trap ===
    /** Cooldown ticks entre 2 pièges. */
    @SerializedName("webTrapInterval")
    public int webTrapInterval = 300;

    /** Durée (ticks) après laquelle la cobweb est supprimée. */
    @SerializedName("webTrapDuration")
    public int webTrapDuration = 60;

    @SerializedName("webTrapSound")
    public String webTrapSound;

    @SerializedName("webTrapMessage")
    public String webTrapMessage;

    /** Rayon en blocks pour choisir les cibles. Défaut 16 (le boss n'a pas besoin d'être corps-à-corps). */
    @SerializedName("webTrapRange")
    public double webTrapRange = 16.0;

    // === void_scream ===
    @SerializedName("voidScreamCooldown")
    public int voidScreamCooldown = 360;

    @SerializedName("voidScreamDamage")
    public double voidScreamDamage = 10;

    @SerializedName("voidScreamKnockback")
    public double voidScreamKnockback = 2;

    @SerializedName("voidScreamRange")
    public double voidScreamRange = 16;

    @SerializedName("voidScreamSound")
    public String voidScreamSound;

    @SerializedName("voidScreamParticle")
    public String voidScreamParticle = "minecraft:sonic_boom";

    @SerializedName("voidScreamMessage")
    public String voidScreamMessage;

    // === solar ===
    @SerializedName("solarCooldown")
    public int solarCooldown = 400;

    /** Rayon en blocks pour chercher les cibles autour du boss. */
    @SerializedName("solarRadius")
    public double solarRadius = 30;

    /** Délai en ticks entre le warning et l'impact. */
    @SerializedName("solarWarningDelay")
    public int solarWarningDelay = 40;

    /** Type d'impact (entity ID). Défaut lightning_bolt. */
    @SerializedName("solarStrikeType")
    public String solarStrikeType = "minecraft:lightning_bolt";

    @SerializedName("solarWarningSound")
    public String solarWarningSound;

    @SerializedName("solarMessage")
    public String solarMessage;

    // === supernova ===
    @SerializedName("supernovaCooldown")
    public int supernovaCooldown = 300;

    /** Seuil HP [0..1] en dessous duquel le supernova se déclenche. Défaut 0.3 = 30%. */
    @SerializedName("supernovaHealthThreshold")
    public double supernovaHealthThreshold = 0.3;

    /** Délai en ticks entre le déclenchement et l'explosion (phase de charge visible). */
    @SerializedName("supernovaChargeDelay")
    public int supernovaChargeDelay = 60;

    @SerializedName("supernovaChargeParticle")
    public String supernovaChargeParticle = "minecraft:lava";

    @SerializedName("supernovaChargeSound")
    public String supernovaChargeSound;

    @SerializedName("supernovaExplosionPower")
    public double supernovaExplosionPower = 6;

    /** "none" = ne détruit pas les blocs ; "break" = comme creeper ; "destroy" = comme TNT. */
    @SerializedName("supernovaDestroyBlocks")
    public String supernovaDestroyBlocks = "none";

    @SerializedName("supernovaMessage")
    public String supernovaMessage;

    // === spectral_vanish ===
    /** Chance [0..1] par activation. Défaut 0.3. */
    @SerializedName("vanishTriggerChance")
    public double vanishTriggerChance = 0.3;

    @SerializedName("vanishInvisibilityDuration")
    public int vanishInvisibilityDuration = 60;

    @SerializedName("vanishSpeedDuration")
    public int vanishSpeedDuration = 60;

    @SerializedName("vanishSpeedAmplifier")
    public int vanishSpeedAmplifier = 1;

    /** Rayon de téléportation en blocks. */
    @SerializedName("vanishTeleportRadius")
    public double vanishTeleportRadius = 10;

    @SerializedName("vanishSound")
    public String vanishSound;

    /** Cooldown ticks. Défaut 200. */
    @SerializedName("vanishCooldown")
    public int vanishCooldown = 200;

    // === illusions ===
    @SerializedName("illusionsCloneCount")
    public int illusionsCloneCount = 3;

    @SerializedName("illusionsCloneType")
    public String illusionsCloneType = "minecraft:enderman";

    @SerializedName("illusionsCloneHealth")
    public double illusionsCloneHealth = 1;

    @SerializedName("illusionsSpawnRadius")
    public double illusionsSpawnRadius = 6;

    @SerializedName("illusionsCooldown")
    public int illusionsCooldown = 500;

    @SerializedName("illusionsSound")
    public String illusionsSound;

    @SerializedName("illusionsMessage")
    public String illusionsMessage;

    // === shadow_trap ===
    @SerializedName("shadowTrapCooldown")
    public int shadowTrapCooldown = 300;

    /** Délai en ticks entre la pose du piège et son déclenchement. */
    @SerializedName("shadowTrapDelay")
    public int shadowTrapDelay = 40;

    @SerializedName("shadowTrapParticle")
    public String shadowTrapParticle = "minecraft:sculk_soul";

    @SerializedName("shadowTrapSound")
    public String shadowTrapSound;

    @SerializedName("shadowTrapMessage")
    public String shadowTrapMessage;

    /** Rayon de téléportation du joueur au déclenchement. */
    @SerializedName("shadowTrapTeleportRadius")
    public double shadowTrapTeleportRadius = 10;

    /** "none" = pas d'explosion ; "break" = comme creeper ; "destroy" = comme TNT. */
    @SerializedName("shadowTrapExplosionMode")
    public String shadowTrapExplosionMode = "none";

    @SerializedName("shadowTrapExplosionPower")
    public double shadowTrapExplosionPower = 3;

    // === force_field ===
    /** Rayon de la bulle autour du boss. */
    @SerializedName("forceFieldRadius")
    public double forceFieldRadius = 4;

    /** Intervalle de check en ticks. Aussi utilisé comme cooldown du skill. */
    @SerializedName("forceFieldCheckInterval")
    public int forceFieldCheckInterval = 5;

    @SerializedName("forceFieldDamage")
    public double forceFieldDamage = 2;

    @SerializedName("forceFieldKnockback")
    public double forceFieldKnockback = 1.8;

    /** Durée du feu appliqué (en secondes). */
    @SerializedName("forceFieldFireDuration")
    public int forceFieldFireDuration = 5;

    @SerializedName("forceFieldParticle")
    public String forceFieldParticle = "minecraft:flame";

    @SerializedName("forceFieldSound")
    public String forceFieldSound;

    // === execution ===
    @SerializedName("executionCooldown")
    public int executionCooldown = 200;

    /** Distance en blocks derrière la cible où le boss se téléporte. */
    @SerializedName("executionBackDistance")
    public double executionBackDistance = 1.5;

    @SerializedName("executionStrengthDuration")
    public int executionStrengthDuration = 40;

    @SerializedName("executionStrengthAmplifier")
    public int executionStrengthAmplifier = 2;

    @SerializedName("executionSound")
    public String executionSound;

    @SerializedName("executionMessage")
    public String executionMessage;

    // === potion_rain ===
    @SerializedName("potionRainCooldown")
    public int potionRainCooldown = 300;

    /** Nombre de potions tirées par activation. */
    @SerializedName("potionRainPotionCount")
    public int potionRainPotionCount = 20;

    /** Intervalle en ticks entre 2 potions successives dans la même rafale. */
    @SerializedName("potionRainBurstInterval")
    public int potionRainBurstInterval = 3;

    /** Dispersion horizontale aléatoire autour de la cible (blocks). */
    @SerializedName("potionRainDispersion")
    public double potionRainDispersion = 4.0;

    /** Vitesse initiale horizontale des potions (contrôle la portée). */
    @SerializedName("potionRainProjectileSpeed")
    public double potionRainProjectileSpeed = 0.5;

    /** Hauteur de l'arc (contrôle la trajectoire lobbée vers le haut). */
    @SerializedName("potionRainArcHeight")
    public double potionRainArcHeight = 1.2;

    /** Liste d'IDs d'effets (ex "minecraft:poison"). Tirés aléatoirement pour chaque potion. */
    @SerializedName("potionRainEffects")
    public List<String> potionRainEffects;

    @SerializedName("potionRainEffectDuration")
    public int potionRainEffectDuration = 100;

    @SerializedName("potionRainEffectAmplifier")
    public int potionRainEffectAmplifier = 0;

    @SerializedName("potionRainSound")
    public String potionRainSound;

    // ══════════════════ NÉCROPOLE (B2) ══════════════════

    // ─── life_drain : rayon d'âmes qui blesse la cible et soigne le lanceur ───
    @SerializedName("lifeDrainCooldown")   public int lifeDrainCooldown = 200;
    @SerializedName("lifeDrainRange")      public double lifeDrainRange = 12;
    /** Durée du canal (ticks) ; une pulsation toutes les 10 ticks. */
    @SerializedName("lifeDrainDuration")   public int lifeDrainDuration = 60;
    @SerializedName("lifeDrainDamage")     public double lifeDrainDamage = 2.0;
    /** Soin du lanceur = dégâts × ratio. */
    @SerializedName("lifeDrainHealRatio")  public double lifeDrainHealRatio = 1.0;
    @SerializedName("lifeDrainSound")      public String lifeDrainSound = "minecraft:entity.vex.charge";
    @SerializedName("lifeDrainMessage")    public String lifeDrainMessage = "§5§lVotre vie est aspirée !";

    // ─── death_mark : la cible est marquée, tous les monstres proches la ciblent, elle prend +X% ───
    @SerializedName("deathMarkCooldown")    public int deathMarkCooldown = 300;
    @SerializedName("deathMarkDuration")    public int deathMarkDuration = 160;
    @SerializedName("deathMarkDamageBonus") public int deathMarkDamageBonus = 20;
    @SerializedName("deathMarkRadius")      public double deathMarkRadius = 32;
    @SerializedName("deathMarkSound")       public String deathMarkSound = "minecraft:entity.wither.ambient";
    @SerializedName("deathMarkMessage")     public String deathMarkMessage = "§8§l☠ Vous portez la Marque funèbre ! Les morts vous traquent...";

    // ─── resurrection : aura passive, les morts-vivants tués près du lanceur peuvent se relever ───
    @SerializedName("resurrectionRadius")  public double resurrectionRadius = 12;
    @SerializedName("resurrectionChance")  public int resurrectionChance = 25;
    @SerializedName("resurrectionHealth")  public double resurrectionHealth = 10;
    @SerializedName("resurrectionEntity")  public String resurrectionEntity = "minecraft:skeleton";
    @SerializedName("resurrectionMessage") public String resurrectionMessage = "§7§oUn mort se relève...";

    // ─── burial : immobilise la cible, des mains sortent du sol et des morts surgissent ───
    @SerializedName("burialCooldown")        public int burialCooldown = 240;
    @SerializedName("burialRange")           public double burialRange = 16;
    @SerializedName("burialDuration")        public int burialDuration = 60;
    @SerializedName("burialSlowAmplifier")   public int burialSlowAmplifier = 4;
    @SerializedName("burialMinionCount")     public int burialMinionCount = 2;
    @SerializedName("burialMinionEntity")    public String burialMinionEntity = "minecraft:zombie";
    @SerializedName("burialMinionHealth")    public double burialMinionHealth = 16;
    @SerializedName("burialSound")           public String burialSound = "minecraft:block.rooted_dirt.break";
    @SerializedName("burialMessage")         public String burialMessage = "§6§lLa terre vous agrippe !";

    // ══════════════════ TERRES BRÛLÉES (N2) ══════════════════

    // ─── eruption : cercles de lave sous les joueurs (avertissement), puis geyser de flammes ───
    @SerializedName("eruptionCooldown")    public int eruptionCooldown = 200;
    @SerializedName("eruptionRange")       public double eruptionRange = 20;
    /** Nombre de joueurs visés (les plus proches). */
    @SerializedName("eruptionTargets")     public int eruptionTargets = 3;
    /** Délai d'avertissement avant le geyser (ticks). */
    @SerializedName("eruptionDelay")       public int eruptionDelay = 30;
    @SerializedName("eruptionRadius")      public double eruptionRadius = 2.0;
    @SerializedName("eruptionDamage")      public double eruptionDamage = 6;
    @SerializedName("eruptionFireSeconds") public int eruptionFireSeconds = 4;
    @SerializedName("eruptionKnockup")     public double eruptionKnockup = 0.9;
    @SerializedName("eruptionSound")       public String eruptionSound = "minecraft:entity.blaze.shoot";
    @SerializedName("eruptionMessage")     public String eruptionMessage = "§6§lLe sol se fissure sous vos pieds !";

    // ─── wither_curse : Wither sur la cible (ou en zone autour d'elle) ───
    @SerializedName("witherCurseCooldown")  public int witherCurseCooldown = 160;
    @SerializedName("witherCurseRange")     public double witherCurseRange = 16;
    @SerializedName("witherCurseDuration")  public int witherCurseDuration = 100;
    @SerializedName("witherCurseAmplifier") public int witherCurseAmplifier = 1;
    /** 0 = seulement la cible ; > 0 = tous les joueurs dans ce rayon autour d'elle. */
    @SerializedName("witherCurseRadius")    public double witherCurseRadius = 0;
    @SerializedName("witherCurseSound")     public String witherCurseSound = "minecraft:entity.wither.shoot";
    @SerializedName("witherCurseMessage")   public String witherCurseMessage = "§8§l☠ La Malédiction du Wither vous ronge !";

    // ─── burning_touch : aura brûlante passive (enflamme les joueurs au contact) ───
    /** Intervalle entre deux brûlures (ticks). */
    @SerializedName("burningTouchInterval")    public int burningTouchInterval = 20;
    @SerializedName("burningTouchRadius")      public double burningTouchRadius = 2.0;
    @SerializedName("burningTouchFireSeconds") public int burningTouchFireSeconds = 3;
    @SerializedName("burningTouchDamage")      public double burningTouchDamage = 1;

    // ══════════════════ NÉANT ÉTERNEL (E2) ══════════════════

    // ─── blink : se téléporte dans le dos de la cible puis frappe ───
    @SerializedName("blinkCooldown")  public int blinkCooldown = 120;
    @SerializedName("blinkRange")     public double blinkRange = 24;
    /** Distance derrière la cible (blocs). */
    @SerializedName("blinkDistance")  public double blinkDistance = 1.8;
    /** Frappe juste après la téléportation. */
    @SerializedName("blinkStrike")    public boolean blinkStrike = true;
    /** Bonus de dégâts de la frappe (s'ajoute à l'attaque). */
    @SerializedName("blinkBonusDamage") public double blinkBonusDamage = 2;
    @SerializedName("blinkSound")     public String blinkSound = "minecraft:entity.enderman.teleport";
    @SerializedName("blinkMessage")   public String blinkMessage = "§5§lQuelque chose surgit dans votre dos !";

    // ─── irons_spell : lance un sort d'Iron's Spells (si le mod est installé) ───
    /** Identifiant du sort (ex : irons_spellbooks:fireball). Addons compris. */
    @SerializedName("ironsSpellId")       public String ironsSpellId = "irons_spellbooks:fireball";
    /** Niveau du sort (ramené dans les bornes du sort). */
    @SerializedName("ironsSpellLevel")    public int ironsSpellLevel = 3;
    /** Ticks entre deux lancements. */
    @SerializedName("ironsSpellCooldown") public int ironsSpellCooldown = 100;
    /** Portée : la cible doit être à moins de N blocs. */
    @SerializedName("ironsSpellRange")    public double ironsSpellRange = 24;
    /** Sort lancé sur soi (soin, bouclier, invocation…) plutôt que vers la cible. */
    @SerializedName("ironsSpellOnSelf")   public boolean ironsSpellOnSelf = false;
    /** Sort sur soi : ne le lance que sous ce % de PV (100 = toujours). */
    @SerializedName("ironsSpellBelowHealth") public int ironsSpellBelowHealth = 100;
    /** Message aux joueurs proches (vide = aucun). */
    @SerializedName("ironsSpellMessage")  public String ironsSpellMessage = "";

    // ─── grave_grip : Emprise de la tombe (un joueur immobile est tiré sous terre) ───
    @SerializedName("graveGripCooldown")     public int graveGripCooldown = 20;
    /** Secondes d'immobilité avant d'être agrippé. */
    @SerializedName("graveGripStillSeconds") public int graveGripStillSeconds = 3;
    @SerializedName("graveGripRadius")       public double graveGripRadius = 16;
    /** Durée de l'emprise (ticks). */
    @SerializedName("graveGripDuration")     public int graveGripDuration = 60;
    /** Dégâts par seconde pendant l'emprise. */
    @SerializedName("graveGripDamage")       public double graveGripDamage = 2;
    @SerializedName("graveGripMessage")      public String graveGripMessage = "§6§lLa tombe vous agrippe ! Bougez !";

    // ─── war_banner : Bannières de guerre (renforcent les monstres, le boss résiste tant qu'elles tiennent) ───
    @SerializedName("warBannerCooldown")  public int warBannerCooldown = 600;
    @SerializedName("warBannerCount")     public int warBannerCount = 2;
    /** Rayon de l'aura de chaque bannière. */
    @SerializedName("warBannerRadius")    public double warBannerRadius = 10;
    /** Distance de pose autour du boss. */
    @SerializedName("warBannerDistance")  public double warBannerDistance = 7;
    /** Durée de vie (secondes). */
    @SerializedName("warBannerLifetime")  public int warBannerLifetime = 45;
    /** Niveau de Résistance du boss tant qu'une bannière tient (1 = I). */
    @SerializedName("warBannerResistance") public int warBannerResistance = 2;
    @SerializedName("warBannerMessage")   public String warBannerMessage = "§6§lDes bannières de guerre se dressent ! Détruisez-les !";

    // ─── homing_orbs : Orbes traqueurs (balles de shulker téléguidées, destructibles) ───
    @SerializedName("homingOrbsCooldown") public int homingOrbsCooldown = 140;
    @SerializedName("homingOrbsCount")    public int homingOrbsCount = 3;
    @SerializedName("homingOrbsRange")    public double homingOrbsRange = 24;
    @SerializedName("homingOrbsMessage")  public String homingOrbsMessage = "";

    // ─── doom_mark : Sentence (un joueur marqué, gros coup partagé entre les joueurs proches de lui) ───
    @SerializedName("doomMarkCooldown")      public int doomMarkCooldown = 500;
    /** Délai avant la sentence (ticks). */
    @SerializedName("doomMarkDelay")         public int doomMarkDelay = 160;
    /** Dégâts totaux, en % des PV max du joueur marqué (partagés entre les joueurs proches). */
    @SerializedName("doomMarkDamagePercent") public double doomMarkDamagePercent = 80;
    /** Rayon de partage autour du joueur marqué. */
    @SerializedName("doomMarkShareRadius")   public double doomMarkShareRadius = 4;
    @SerializedName("doomMarkMessage")       public String doomMarkMessage = "§4§l☠ {player} est condamné ! Rassemblez-vous autour de lui !";

    // ─── telegraph : attaque télégraphiée (zones au sol, avertissement, puis frappe) ───
    @SerializedName("telegraphCooldown")       public int telegraphCooldown = 160;
    /** Nombre de zones (sous des joueurs, puis au hasard autour du boss). */
    @SerializedName("telegraphCount")          public int telegraphCount = 3;
    @SerializedName("telegraphRadius")         public double telegraphRadius = 2.5;
    /** Avertissement avant la frappe (ticks). */
    @SerializedName("telegraphWarning")        public int telegraphWarning = 30;
    @SerializedName("telegraphDamage")         public double telegraphDamage = 8;
    /** Projection vers le haut (0 = aucune). */
    @SerializedName("telegraphKnockUp")        public double telegraphKnockUp = 0.8;
    /** Style : slam (frappe au sol), geyser (eau), fangs (crocs d'évocateur). */
    @SerializedName("telegraphStyle")          public String telegraphStyle = "slam";
    /** Effet appliqué aux joueurs touchés (vide = aucun). */
    @SerializedName("telegraphEffect")         public String telegraphEffect = "minecraft:slowness";
    @SerializedName("telegraphEffectLevel")    public int telegraphEffectLevel = 1;
    @SerializedName("telegraphEffectDuration") public int telegraphEffectDuration = 60;
    @SerializedName("telegraphMessage")        public String telegraphMessage = "";

    // ─── gravity_well : puits qui aspire les joueurs vers son centre, puis implose ───
    @SerializedName("gravityWellCooldown")  public int gravityWellCooldown = 240;
    @SerializedName("gravityWellRange")     public double gravityWellRange = 18;
    @SerializedName("gravityWellRadius")    public double gravityWellRadius = 5;
    @SerializedName("gravityWellDuration")  public int gravityWellDuration = 60;
    /** Force d'attraction par tick (0.08 = douce, 0.2 = forte). */
    @SerializedName("gravityWellStrength")  public double gravityWellStrength = 0.1;
    /** Dégâts par seconde au cœur du puits (≤ 1,5 bloc du centre). */
    @SerializedName("gravityWellDamage")    public double gravityWellDamage = 2;
    /** Implosion finale : projette en l'air les joueurs près du centre. */
    @SerializedName("gravityWellImplosion") public boolean gravityWellImplosion = true;
    @SerializedName("gravityWellSound")     public String gravityWellSound = "minecraft:block.portal.trigger";
    @SerializedName("gravityWellMessage")   public String gravityWellMessage = "§5§lUn puits de néant vous attire !";
}
