package com.wavesurvivor.client;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.shrine.ShrineGear;
import com.wavesurvivor.shrine.ShrineUpgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Info-bulle des équipements améliorés à la Forge du Sanctuaire, dans TOUS les écrans (inventaire, barre d'objets, coffres, Forge…) :
 * nom coloré (doré ✦ pour Consacré, violet ☠ pour Profané), alignement et nombre d'emplacements, puis chaque amélioration avec son niveau,
 * ses bonus en vert et ses malus en rouge.
 */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShrineGearTooltips {

    private ShrineGearTooltips() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent e) {
        ItemStack st = e.getItemStack();
        String align = ShrineGear.alignOf(st);
        if (align == null) return;
        List<ShrineGear.Applied> ups = ShrineGear.upgradesOf(st);
        if (ups.isEmpty()) return;
        boolean cons = ShrineGear.CONSECRATED.equals(align);
        List<Component> tip = e.getToolTip();

        // Nom coloré
        if (!tip.isEmpty()) {
            tip.set(0, Component.literal(cons ? "✦ " : "☠ ").append(tip.get(0).copy()).withStyle(cons ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE));
        }
        // Alignement et améliorations, juste sous le nom
        int at = Math.min(1, tip.size());
        tip.add(at++, Component.literal((cons ? "§6" : "§5") + WSLang.t("shrine.align." + align) + " §7(" + ups.size() + "/" + ShrineUpgrades.maxSlots() + ")"));
        for (ShrineGear.Applied a : ups) {
            ShrineUpgrades.Def d = ShrineUpgrades.get(a.id());
            if (d == null) continue;
            tip.add(at++, Component.literal((cons ? "§e" : "§d") + " " + WSLang.t("shrine.up." + a.id()) + " §7" + ShrineGear.roman(a.tier())));
            for (String line : ShrineGear.effectLines(d, a.tier())) tip.add(at++, Component.literal("   " + line));
        }
    }
}
