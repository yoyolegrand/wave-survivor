package com.wavesurvivor.item;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * RELIQUAIRE SCELLÉ (boutique de Renaissance) : clic droit → il se brise et donne une relique de BASE au hasard
 * (jamais une légendaire, qui ne s'obtient que par fusion). Sert surtout à trouver des doublons pour la Forge.
 */
public class RelicCacheItem extends Item {

    public RelicCacheItem(Properties props) {
        super(props.stacksTo(16).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(st);
        List<Item> pool = new ArrayList<>();
        for (RelicSets.Family f : RelicSets.Family.values()) pool.addAll(f.items());
        if (pool.isEmpty()) return InteractionResultHolder.fail(st);
        ItemStack relic = new ItemStack(pool.get(level.random.nextInt(pool.size())));
        String name = relic.getHoverName().getString(); // avant l'ajout : l'inventaire vide la pile d'origine
        if (!player.getAbilities().instabuild) st.shrink(1);
        if (!player.getInventory().add(relic)) player.drop(relic, false);
        player.sendSystemMessage(Component.literal(WSLang.t("relic_cache.opened", name)));
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.5, 0.6, 0.5, 0.6);
            sl.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void appendHoverText(ItemStack st, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(WSLang.t("relic_cache.tip")).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack st) {
        return true;
    }
}
