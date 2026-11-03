package com.wavesurvivor.craft;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * NOURRITURE SPÉCIALE de l'Artisanat sacré et profane. Faim, saturation et effets viennent de la config
 * (crafting_fragments.json), pas du FoodProperties (celui-ci sert seulement à rendre l'objet comestible).
 * Un seul effet de nourriture spéciale à la fois : voir {@link CraftBuffs}.
 */
public class CraftFoodItem extends Item {

    private final String id;
    private final boolean drink;

    public CraftFoodItem(String id, boolean drink, Properties props) {
        super(props.stacksTo(16).food(new FoodProperties.Builder().nutrition(1).alwaysEat().build()));
        this.id = id;
        this.drink = drink;
    }

    /** Faim et saturation lues dans la config (barre de faim, aperçu des mods d'affichage) et non les valeurs figées du registre. */
    @Override
    public FoodProperties getFoodProperties(ItemStack stack, LivingEntity entity) {
        CraftConfig.Food f = CraftConfig.food(id);
        return new FoodProperties.Builder().nutrition(f.nutrition()).saturationMod(f.saturation()).alwaysEat().build();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return drink ? UseAnim.DRINK : UseAnim.EAT; }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player p)) return stack;
        CraftConfig.Food f = CraftConfig.food(id);
        p.getFoodData().eat(f.nutrition(), f.saturation());
        level.playSound(null, p.getX(), p.getY(), p.getZ(), drink ? stack.getDrinkingSound() : stack.getEatingSound(),
                SoundSource.NEUTRAL, 1f, 1f + (level.random.nextFloat() - level.random.nextFloat()) * 0.4f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
        p.awardStat(Stats.ITEM_USED.get(this));
        if (p instanceof ServerPlayer sp) {
            CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
            CraftBuffs.apply(sp, id, f);
        }
        if (p.getAbilities().instabuild) return stack;
        stack.shrink(1);
        if (drink) {
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (stack.isEmpty()) return bottle;
            if (!p.getInventory().add(bottle)) p.drop(bottle, false);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        for (String line : WSLang.t("craft.food." + id).split("\n")) tip.add(Component.literal(line));
    }
}
