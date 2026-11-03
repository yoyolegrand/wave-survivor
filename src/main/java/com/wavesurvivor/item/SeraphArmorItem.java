package com.wavesurvivor.item;

import com.wavesurvivor.craft.CraftGearItems;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.shrine.ShrineGear;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Pièce de l'ARMURE DU SÉRAPHIN (casque, plastron, jambières, bottes) : modèle 3D sur mesure (client/armor/SeraphArmorModel),
 * alignement Consacré imposé pour la Forge du Sanctuaire, bonus d'ensemble (voir CraftEquipEvents).
 */
public class SeraphArmorItem extends ArmorItem implements CraftGearItems.FixedAlign {

    /** Texture complète du modèle (écrite en texte ici pour ne pas charger les classes du client côté serveur). */
    public static final String TEXTURE = "wavesurvivor:textures/models/armor/seraph_armor.png";

    public SeraphArmorItem(ArmorItem.Type type, Properties props) {
        super(SeraphArmorMaterial.SERAPH, type, props.rarity(Rarity.EPIC));
    }

    @Override
    public String align() { return ShrineGear.CONSECRATED; }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) { return TEXTURE; }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                return com.wavesurvivor.client.armor.SeraphArmorClient.model(slot, original);
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        for (String line : WSLang.t("craft.item.seraph_armor").split("\n")) tip.add(Component.literal(line));
    }
}
