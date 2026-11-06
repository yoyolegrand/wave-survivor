package com.wavesurvivor.item;

import com.wavesurvivor.horde.chaos.DesertEvents;
import com.wavesurvivor.horde.kingdom.KingdomManager;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * OBJETS DU MARCHÉ DU DÉSERT (1.9, vendus par l'Oracle des sables) :
 *  • Gourde d'eau fraîche : se boit, immunise 3 minutes contre le Soleil de plomb ;
 *  • Fiole de mirage : annule le prochain événement défavorable du désert (tempête de sable, sables mouvants, soleil, nuée, mirage) ;
 *  • Tablette de reconnaissance : révèle, Porte par Porte, la composition de l'Assaut suivant (et le seigneur éventuel).
 */
public class DesertUtilityItem extends Item {

    public enum Kind { GOURDE, FIOLE, TABLETTE }

    private final Kind kind;

    public DesertUtilityItem(Kind kind, Properties props) {
        super(props.stacksTo(16));
        this.kind = kind;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return kind == Kind.GOURDE ? UseAnim.DRINK : UseAnim.NONE; }

    @Override
    public int getUseDuration(ItemStack stack) { return kind == Kind.GOURDE ? 32 : 0; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (kind == Kind.GOURDE) {
            p.startUsingItem(hand);
            return InteractionResultHolder.consume(st);
        }
        if (level.isClientSide || !(p instanceof ServerPlayer sp)) return InteractionResultHolder.success(st);
        if (kind == Kind.FIOLE) {
            if (DesertEvents.shieldArmed()) {
                sp.displayClientMessage(WSLang.c("desert.fiole_already"), true);
                return InteractionResultHolder.fail(st);
            }
            DesertEvents.armShield();
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
            com.wavesurvivor.network.EventFeedPacket.toPlayer(sp, WSLang.c("desert.fiole_used"), "minecraft:potion", com.wavesurvivor.network.EventFeedPacket.CHAOS, false);
        } else {
            if (!KingdomManager.isActive()) {
                sp.displayClientMessage(WSLang.c("tablet.none"), true);
                return InteractionResultHolder.fail(st);
            }
            sp.sendSystemMessage(WSLang.c("tablet.title", KingdomManager.cycle() + 1));
            for (Component c : KingdomManager.previewNextAssault()) sp.sendSystemMessage(c);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.8f);
        }
        if (!p.getAbilities().instabuild) st.shrink(1);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity e) {
        if (kind != Kind.GOURDE || !(e instanceof Player p)) return stack;
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1f, 1f);
        if (p instanceof ServerPlayer sp) {
            DesertEvents.drink(sp, 180);
            sp.displayClientMessage(WSLang.c("desert.gourde_used"), true);
        }
        if (!p.getAbilities().instabuild) stack.shrink(1);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(WSLang.c("desert.tip." + kind.name().toLowerCase()));
    }
}
