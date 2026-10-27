package com.wavesurvivor.item;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RELIQUE générique : objet unique (stack 1), reflet enchanté, résistant au feu, non fabricable.
 * Ses effets sont gérés dans RelicEffects ; ici seulement l'affichage (titre, effets, emplacement Curios, citation).
 * Les textes passés au constructeur (en français) servent de clés de traduction : ils sont traduits à l'affichage.
 */
public class RelicItem extends Item {

    private final String title;
    private final List<String> effects;
    private final String slot;
    private final String quote;

    public RelicItem(Rarity rarity, String title, String slot, String quote, String... effects) {
        super(new Item.Properties().stacksTo(1).rarity(rarity).fireResistant());
        this.title = title;
        this.slot = slot;
        this.quote = quote;
        this.effects = List.of(effects);
    }

    /** Marqueurs de chiffres dans les textes d'effets : {25} = 25 × multiplicateur du niveau ; {1+1} = 1 + 1 × multiplicateur (ex. « ×2 » → ×2 / ×2,5 / ×3). */
    private static final Pattern MARK = Pattern.compile("[{]([0-9]+(?:[.][0-9]+)?)(?:[+]([0-9]+(?:[.][0-9]+)?))?[}]");

    /** Remplace les marqueurs par les valeurs réelles au multiplicateur {@code m}. */
    public static String scale(String text, float m) {
        Matcher mt = MARK.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (mt.find()) {
            double v = mt.group(2) == null ? Double.parseDouble(mt.group(1)) * m : Double.parseDouble(mt.group(1)) + Double.parseDouble(mt.group(2)) * m;
            v = Math.round(v * 10) / 10.0;
            String num = v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
            if ("fr".equals(WSLang.get())) num = num.replace('.', ',');
            mt.appendReplacement(sb, Matcher.quoteReplacement(num));
        }
        mt.appendTail(sb);
        return sb.toString();
    }

    /** Effets traduits, chiffres calculés pour ce niveau (I ×1, II ×1,5, III ×2). */
    public List<String> effectTexts(int lvl) {
        float m = RelicEffects.mult(lvl);
        List<String> out = new ArrayList<>();
        for (String e : effects) out.add(scale(WSLang.t(e), m));
        return out;
    }

    /** Maj enfoncée (côté client seulement). */
    protected static boolean shiftDown() {
        return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient() && com.wavesurvivor.client.RelicClient.shift();
    }

    /** Lignes en plus après les effets (la légendaire y détaille ses deux reliques d'origine). */
    protected void appendDetails(ItemStack stack, List<Component> tooltip) {}

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /** Niveau II / III : chiffre romain après le nom (« Os Sacré III »). */
    @Override
    public Component getName(ItemStack stack) {
        int lvl = RelicEffects.levelOf(stack);
        Component base = super.getName(stack);
        return lvl <= 1 ? base : Component.empty().append(base).append(lvl == 2 ? " II" : " III");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(WSLang.t(title)).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        int lvl = RelicEffects.levelOf(stack);
        if (lvl > 1) tooltip.add(Component.literal(WSLang.t("relic.level", lvl == 2 ? "II" : "III", lvl == 2 ? "×1,5" : "×2")).withStyle(ChatFormatting.AQUA));
        for (String e : effectTexts(lvl)) tooltip.add(Component.literal("✦ " + e).withStyle(ChatFormatting.LIGHT_PURPLE));
        appendDetails(stack, tooltip);
        tooltip.add(Component.literal(WSLang.t("relic.active_in", WSLang.t(slot))).withStyle(ChatFormatting.GRAY));
        // 1.5 : seules les reliques équipées agissent
        if (level != null && level.isClientSide && !com.wavesurvivor.client.RelicClient.equipped(this)) {
            tooltip.add(Component.literal(WSLang.t(RelicEffects.curiosAvailable() ? "relic.not_equipped" : "relic.not_equipped_altar"))
                    .withStyle(ChatFormatting.RED));
        }
        // Bonus de set (1.5) : famille, pièces portées, paliers (verts quand actifs)
        RelicSets.Family fam = RelicSets.familyOf(this);
        if (fam != null) {
            int have = level != null && level.isClientSide ? com.wavesurvivor.client.RelicClient.count(fam) : 0;
            tooltip.add(Component.literal(WSLang.t("set.tooltip", WSLang.t("set." + fam.id), have, fam.max)).withStyle(ChatFormatting.GOLD));
            for (int n = 2; n <= fam.max; n++) {
                boolean on = have >= n;
                tooltip.add(Component.literal(" (" + n + ") " + WSLang.t("set." + fam.id + "." + n))
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            }
        }
        if (quote != null && !quote.isBlank()) {
            tooltip.add(Component.literal("« " + WSLang.t(quote) + " »").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
