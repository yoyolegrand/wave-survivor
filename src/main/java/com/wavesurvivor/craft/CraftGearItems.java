package com.wavesurvivor.craft;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.shrine.ShrineGear;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * ÉQUIPEMENT de l'Artisanat sacré et profane : Lame de l'aube, Faux de la souillure, flèches bénies et souillées, totems.
 * Les effets (bonus, balayage, totems, flèches) sont dans {@link CraftEquipEvents} ; les valeurs dans crafting_fragments.json (« equipment »).
 */
public final class CraftGearItems {

    private CraftGearItems() {}

    /** Objet dont l'alignement est imposé : la Forge du Sanctuaire n'accepte que les fragments de ce camp. */
    public interface FixedAlign {
        String align();
    }

    private static void tooltip(String id, List<Component> tip) {
        for (String line : WSLang.t("craft.item." + id).split("\n")) tip.add(Component.literal(line));
    }

    /** Lame de l'aube (Consacrée) : épée en diamant, +50 % contre morts-vivants et Corrompus, brille la nuit. */
    public static class DawnBlade extends SwordItem implements FixedAlign {
        public DawnBlade(Properties props) { super(Tiers.DIAMOND, 3, -2.4f, props); }

        @Override public String align() { return ShrineGear.CONSECRATED; }

        @Override
        public boolean isFoil(ItemStack stack) {
            return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient() && com.wavesurvivor.client.CraftClient.isNight();
        }

        @Override
        public void appendHoverText(ItemStack st, Level lv, List<Component> tip, TooltipFlag fl) { tooltip("dawn_blade", tip); }
    }

    /** Faux de la souillure (Profanée) : balayage large, vol de vie, un coup manqué coûte un cœur. */
    public static class TaintScythe extends SwordItem implements FixedAlign {
        public TaintScythe(Properties props) { super(Tiers.DIAMOND, 2, -2.6f, props); }

        @Override public String align() { return ShrineGear.PROFANED; }

        @Override
        public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
            if (entity instanceof ServerPlayer sp) CraftEquipEvents.onScytheSwing(sp);
            return false;
        }

        @Override
        public void appendHoverText(ItemStack st, Level lv, List<Component> tip, TooltipFlag fl) { tooltip("taint_scythe", tip); }
    }

    /** Flèche marquée : l'effet à l'impact est appliqué par CraftEquipEvents d'après l'étiquette « ws_arrow ». */
    public static class MarkedArrow extends ArrowItem {
        private final String kind;

        public MarkedArrow(String kind, Properties props) {
            super(props);
            this.kind = kind;
        }

        @Override
        public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity shooter) {
            Arrow a = new Arrow(level, shooter);
            a.getPersistentData().putString(CraftEquipEvents.ARROW_TAG, kind);
            return a;
        }

        @Override
        public void appendHoverText(ItemStack st, Level lv, List<Component> tip, TooltipFlag fl) { tooltip(kind + "_arrow", tip); }
    }

    /** Totem de grâce ou de souillure : tenu en main, remplace la mort (voir CraftEquipEvents). */
    public static class CraftTotem extends Item {
        private final String id;

        public CraftTotem(String id, Properties props) {
            super(props.stacksTo(1));
            this.id = id;
        }

        @Override
        public void appendHoverText(ItemStack st, Level lv, List<Component> tip, TooltipFlag fl) { tooltip(id, tip); }
    }
}
