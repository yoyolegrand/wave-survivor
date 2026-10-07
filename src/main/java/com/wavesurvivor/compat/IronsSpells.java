package com.wavesurvivor.compat;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PONT IRON'S SPELLS (optionnel, 100 % par réflexion : aucun lien de compilation).
 *
 * Fait lancer un sort d'Iron's Spells à N'IMPORTE QUELLE créature :
 *   - mage d'Iron's (AbstractSpellCastingMob) → sa propre méthode initiateCastSpell (animations comprises) ;
 *   - autre créature → reproduction de la séquence des mages d'Iron's :
 *       regarder la cible → checkPreCastConditions → MagicData.initiateCast(source MOB) → onServerPreCast
 *       → puis, tick par tick : sorts CANALISÉS (onCast toutes les 10 ticks), sorts CHARGÉS (onCast à la fin),
 *         et onServerCastComplete + resetCastingState pour terminer proprement.
 * Iron's greffe ses données magiques sur toutes les créatures vivantes (mixin) : MagicData.getPlayerMagicData(entity).
 */
public final class IronsSpells {

    private static final String MODID = "irons_spellbooks";
    private static Boolean available = null;

    // Classes / méthodes Iron's (résolues une fois)
    private static Method mGetSpellById, mGetEnabledSpells, mGetSpellId, mGetCastType, mGetEffectiveCastTime, mCheckPreCast,
            mOnCast, mOnServerPreCast, mOnServerCastComplete, mGetMagicData, mInitiateCast, mIsCasting, mResetCasting,
            mMobInitiateCast, mMobIsCasting, mMaxLevel, mMinLevel;
    private static Class<?> cCastingMob, cNoneSpell;
    private static Object CAST_SOURCE_MOB;
    private static String MAINHAND;

    /** Public : une instance est enregistrée sur le bus d'événements (tick des sorts en cours). */
    public IronsSpells() {}

    /** Iron's Spells est-il installé et son API compatible ? */
    public static boolean available() {
        if (available != null) return available;
        available = false;
        if (!ModList.get().isLoaded(MODID)) return false;
        try {
            Class<?> registry = Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
            Class<?> spell = Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell");
            Class<?> magicData = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            Class<?> castSource = Class.forName("io.redspace.ironsspellbooks.api.spells.CastSource");
            Class<?> selection = Class.forName("io.redspace.ironsspellbooks.api.magic.SpellSelectionManager");
            cCastingMob = Class.forName("io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob");
            cNoneSpell = Class.forName("io.redspace.ironsspellbooks.spells.NoneSpell");

            mGetSpellById = registry.getMethod("getSpell", String.class);
            mGetEnabledSpells = registry.getMethod("getEnabledSpells");
            mGetSpellId = spell.getMethod("getSpellId");
            mGetCastType = spell.getMethod("getCastType");
            mMaxLevel = spell.getMethod("getMaxLevel");
            mMinLevel = spell.getMethod("getMinLevel");
            mGetEffectiveCastTime = spell.getMethod("getEffectiveCastTime", int.class, LivingEntity.class);
            mCheckPreCast = spell.getMethod("checkPreCastConditions", Level.class, int.class, LivingEntity.class, magicData);
            mOnCast = spell.getMethod("onCast", Level.class, int.class, LivingEntity.class, castSource, magicData);
            mOnServerPreCast = spell.getMethod("onServerPreCast", Level.class, int.class, LivingEntity.class, magicData);
            mOnServerCastComplete = spell.getMethod("onServerCastComplete", Level.class, int.class, LivingEntity.class, magicData, boolean.class);
            mGetMagicData = magicData.getMethod("getPlayerMagicData", LivingEntity.class);
            mInitiateCast = magicData.getMethod("initiateCast", spell, int.class, int.class, castSource, String.class);
            mIsCasting = magicData.getMethod("isCasting");
            mResetCasting = magicData.getMethod("resetCastingState");
            mMobInitiateCast = cCastingMob.getMethod("initiateCastSpell", spell, int.class);
            mMobIsCasting = cCastingMob.getMethod("isCasting");

            for (Object c : castSource.getEnumConstants()) if ("MOB".equals(((Enum<?>) c).name())) CAST_SOURCE_MOB = c;
            MAINHAND = (String) selection.getField("MAINHAND").get(null);
            if (CAST_SOURCE_MOB == null) throw new IllegalStateException("CastSource.MOB introuvable");
            available = true;
            WaveSurvivorMod.LOGGER.info("[Iron's] Intégration Iron's Spells active.");
        } catch (Throwable t) {
            WaveSurvivorMod.LOGGER.warn("[Iron's] Iron's Spells détecté mais API incompatible : {}", t.toString());
        }
        return available;
    }

    /** Identifiants de tous les sorts activés (Iron's + addons), triés. Liste vide si Iron's est absent. */
    public static List<String> spellIds() {
        List<String> out = new ArrayList<>();
        if (!available()) return out;
        try {
            for (Object s : (List<?>) mGetEnabledSpells.invoke(null)) {
                if (cNoneSpell.isInstance(s)) continue;
                out.add((String) mGetSpellId.invoke(s));
            }
        } catch (Throwable ignored) {}
        out.sort(String::compareTo);
        return out;
    }

    private static Object spell(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            Object s = mGetSpellById.invoke(null, id.trim());
            return s == null || cNoneSpell.isInstance(s) ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Niveau ramené dans les bornes du sort. */
    private static int clampLevel(Object spell, int level) {
        try {
            int min = (int) mMinLevel.invoke(spell), max = (int) mMaxLevel.invoke(spell);
            return Math.max(min, Math.min(max, level));
        } catch (Throwable t) {
            return Math.max(1, level);
        }
    }

    // ─── Lancements en cours (créatures non-mages) ───

    private static final class Cast {
        final LivingEntity caster;
        final LivingEntity target;
        final Object spell;
        final Object magicData;
        final int level;
        final String type;
        int remaining;

        Cast(LivingEntity caster, LivingEntity target, Object spell, Object magicData, int level, String type, int remaining) {
            this.caster = caster;
            this.target = target;
            this.spell = spell;
            this.magicData = magicData;
            this.level = level;
            this.type = type;
            this.remaining = remaining;
        }
    }

    private static final Map<UUID, Cast> CASTS = new ConcurrentHashMap<>();

    /**
     * Lance un sort. target peut être null (sort sur soi : soin, bouclier…). Renvoie false si Iron's est absent,
     * le sort inconnu, la créature déjà en train de lancer, ou si les conditions du sort ne sont pas remplies.
     */
    public static boolean cast(LivingEntity caster, LivingEntity target, String spellId, int level) {
        if (caster == null || !caster.isAlive() || caster.level().isClientSide || !available()) return false;
        Object spell = spell(spellId);
        if (spell == null) return false;
        int lvl = clampLevel(spell, level);
        Level world = caster.level();
        try {
            if (target != null) lookAt(caster, target);

            // Mage d'Iron's : sa propre séquence (animations comprises)
            if (cCastingMob.isInstance(caster)) {
                if ((boolean) mMobIsCasting.invoke(caster)) return false;
                mMobInitiateCast.invoke(caster, spell, lvl);
                return true;
            }

            // Autre créature : séquence reproduite
            if (CASTS.containsKey(caster.getUUID())) return false;
            Object md = mGetMagicData.invoke(null, caster);
            if (md == null || (boolean) mIsCasting.invoke(md)) return false;
            if (!(boolean) mCheckPreCast.invoke(spell, world, lvl, caster, md)) return false;
            int castTime = Math.max(0, (int) mGetEffectiveCastTime.invoke(spell, lvl, caster));
            String type = String.valueOf(mGetCastType.invoke(spell));
            mInitiateCast.invoke(md, spell, lvl, castTime, CAST_SOURCE_MOB, MAINHAND);
            mOnServerPreCast.invoke(spell, world, lvl, caster, md);
            if ("INSTANT".equals(type) || castTime <= 0) {
                mOnCast.invoke(spell, world, lvl, caster, CAST_SOURCE_MOB, md);
                finish(caster, spell, md, lvl, false);
                return true;
            }
            CASTS.put(caster.getUUID(), new Cast(caster, target, spell, md, lvl, type, castTime));
            return true;
        } catch (Throwable t) {
            WaveSurvivorMod.LOGGER.warn("[Iron's] Échec du lancement de {} : {}", spellId, t.toString());
            CASTS.remove(caster.getUUID());
            return false;
        }
    }

    private static void finish(LivingEntity caster, Object spell, Object md, int lvl, boolean cancelled) {
        try {
            mOnServerCastComplete.invoke(spell, caster.level(), lvl, caster, md, cancelled);
        } catch (Throwable ignored) {}
        try {
            mResetCasting.invoke(md);
        } catch (Throwable ignored) {}
    }

    private static void lookAt(LivingEntity caster, LivingEntity target) {
        if (caster instanceof Mob mob) {
            mob.getLookControl().setLookAt(target, 360f, 360f);
        }
        double dx = target.getX() - caster.getX();
        double dz = target.getZ() - caster.getZ();
        double dy = target.getEyeY() - caster.getEyeY();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        caster.setYRot(yaw);
        caster.setYHeadRot(yaw);
        caster.setYBodyRot(yaw);
        caster.setXRot(pitch);
        caster.yRotO = yaw;
        caster.xRotO = pitch;
    }

    /** Fait avancer les sorts chargés / canalisés des créatures non-mages. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CASTS.isEmpty()) return;
        Iterator<Cast> it = CASTS.values().iterator();
        while (it.hasNext()) {
            Cast c = it.next();
            try {
                if (!c.caster.isAlive() || c.caster.isRemoved()) {
                    finish(c.caster, c.spell, c.magicData, c.level, true);
                    it.remove();
                    continue;
                }
                if (c.target != null && c.target.isAlive()) lookAt(c.caster, c.target);
                if ("CONTINUOUS".equals(c.type) && c.remaining % 10 == 0) {
                    mOnCast.invoke(c.spell, c.caster.level(), c.level, c.caster, CAST_SOURCE_MOB, c.magicData);
                }
                if (--c.remaining <= 0) {
                    if ("LONG".equals(c.type)) {
                        mOnCast.invoke(c.spell, c.caster.level(), c.level, c.caster, CAST_SOURCE_MOB, c.magicData);
                    }
                    finish(c.caster, c.spell, c.magicData, c.level, false);
                    it.remove();
                }
            } catch (Throwable t) {
                finish(c.caster, c.spell, c.magicData, c.level, true);
                it.remove();
            }
        }
    }
}
