package com.wavesurvivor.horde.roulette;

import com.wavesurvivor.horde.loot.LootItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Clé de roulette chest (remplace l'ancien name_tag, qui se confondait avec les étiquettes).
 * Identité portée par le NBT : rouletteKey=1, rouletteChestName, rouletteKeyName.
 * La gemme (calque tintindex 1) prend une couleur selon le type de clé.
 */
public class RouletteKeyItem extends Item {

    public RouletteKeyItem(Properties props) {
        super(props);
    }

    /**
     * Nom affiché calculé à la volée dans la langue du mod, à partir de l'étiquette interne rouletteKeyName
     * (qui, elle, reste en français : c'est elle qui sert à vérifier la clé).
     */
    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("rouletteKeyName")) {
            return Component.literal(keyDisplayName(tag.getString("rouletteKeyName")));
        }
        return super.getName(stack);
    }

    /** « Clé Boss » → « Boss Key » ; « Clé Reliquaire du Boss » → « Boss Reliquary Key » (règle générale). */
    public static String keyDisplayName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String t = com.wavesurvivor.i18n.WSLang.t(raw);
        if (!t.equals(raw)) return t;
        if (raw.startsWith("Clé ")) return com.wavesurvivor.i18n.WSLang.t("item.key_named", com.wavesurvivor.i18n.WSLang.t(raw.substring(4)));
        return raw;
    }

    /**
     * Migration des anciennes clés : elles portaient un nom figé en français (« Clé Boss »). On le retire
     * pour que le nom traduit (getName) s'affiche. Ne touche jamais à un nom personnalisé différent.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !stack.hasCustomHoverName()) return;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("rouletteKeyName")) return;
        String shown = net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString());
        if (shown != null && shown.equals(net.minecraft.ChatFormatting.stripFormatting(tag.getString("rouletteKeyName")))) {
            stack.resetHoverName();
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("rouletteChestName")) {
            tooltip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.ouvre") + com.wavesurvivor.i18n.WSLang.t(tag.getString("rouletteChestName"))));
        }
        Material mat = materialOf(stack);
        if (mat != MATERIALS[0]) tooltip.add(Component.literal("§8" + mat.label()));
        tooltip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.clic_droit_sur_le_coffre_correspondant")));
    }

    // ─── Matériau du corps de la clé (calque 0, teinté) ───

    public record Material(String id, String nameFr, String label, int color) {
        /** Nom traduit (langue du mod), le texte français sert de clé. */
        @Override public String nameFr() { return com.wavesurvivor.i18n.WSLang.t(nameFr); }
        @Override public String label() { return com.wavesurvivor.i18n.WSLang.t(label); }
    }

    /** Le premier est le matériau par défaut (aucun NBT → clés existantes inchangées et empilables). */
    public static final Material[] MATERIALS = {
            new Material("or", "Or", "Clé en or", 0xF6C83E),
            new Material("bronze", "Bronze", "Clé en bronze", 0xC98A4B),
            new Material("fer", "Fer", "Clé en fer", 0xD6D9DE),
            new Material("cuivre", "Cuivre", "Clé en cuivre", 0xE37E52),
            new Material("argent", "Argent", "Clé en argent", 0xEEF4FF),
            new Material("netherite", "Netherite", "Clé en netherite", 0x6A5C5E),
            new Material("os", "Os", "Clé en os", 0xEFE8CF),
            new Material("obsidienne", "Obsidienne", "Clé en obsidienne", 0x6B4F9E),
            new Material("rouille", "Rouille", "Clé rouillée", 0xA85A32),
            new Material("or_rose", "Or rose", "Clé en or rose", 0xF2A69A)};

    public static Material material(String id) {
        if (id != null) for (Material m : MATERIALS) if (m.id().equalsIgnoreCase(id)) return m;
        return MATERIALS[0];
    }

    public static Material materialOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return material(tag != null ? tag.getString("rouletteKeyMaterial") : null);
    }

    /** Couleur du corps (calque 0). */
    public static int bodyColor(ItemStack stack) {
        return materialOf(stack).color();
    }

    /** Couleur de la gemme (RGB) selon les mots du nom de la clé / du coffre. */
    public static int gemColor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("rouletteKeyColor")) return tag.getInt("rouletteKeyColor"); // couleur choisie
        String name = tag == null ? "" : tag.getString("rouletteKeyName") + " " + tag.getString("rouletteChestName");
        return colorForName(name);
    }

    /** Même couleur pour une clé et son coffre (partagée avec le Coffre Roulette). */
    public static int colorForName(String name) {
        if (name == null) name = "";
        Set<String> t = LootItems.tokens(name);
        String flat = String.join("", t); // "CoffreEnder" → "coffreender" (contient "ender")
        if (flat.contains("nether")) return 0xFF5A1E;
        if (flat.contains("drowned") || flat.contains("profondeur") || t.contains("noye") || flat.contains("abysse")) return 0x2FD3E0;
        if (flat.contains("ender") || t.contains("end")) return 0x8A3CD6;
        if (flat.contains("boss")) return 0xC026D3;
        if (flat.contains("magic") || flat.contains("magique")) return 0xFF5CE1;
        if (flat.contains("tresor") || t.contains("or") || t.contains("gold")) return 0xFFD23F;
        if (flat.contains("mysterieu")) return 0x3DDC84;
        if (name.isBlank()) return 0x3DDC84;
        // Autres clés : teinte stable dérivée du nom
        float hue = (Math.abs(name.hashCode()) % 360) / 360f;
        return net.minecraft.util.Mth.hsvToRgb(hue, 0.65f, 1f) & 0xFFFFFF;
    }
}
