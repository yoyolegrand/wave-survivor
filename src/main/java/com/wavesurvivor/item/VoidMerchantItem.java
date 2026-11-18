package com.wavesurvivor.item;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 2.1 — Objets du Marchand du Néant (fiche docs/faille/v2_1_ilots/FICHE_2_1_0.md § 3).
 * Fioles et élixirs se boivent ; les autres s'utilisent au clic droit (le Kit d'engin, sur un engin de siège : voir RiftEngines).
 */
public class VoidMerchantItem extends Item {

    public enum Kind { FIOLE, RESISTANCE, FORCE, RAPPEL, KIT_PONT, KIT_ENGIN, VIGIE, RELANCE }

    private final Kind kind;

    public VoidMerchantItem(Kind kind) {
        super(new Item.Properties().stacksTo(kind == Kind.RELANCE || kind == Kind.RAPPEL ? 16 : 64).rarity(kind == Kind.RELANCE ? Rarity.RARE : Rarity.UNCOMMON));
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    private boolean drink() { return kind == Kind.FIOLE || kind == Kind.RESISTANCE || kind == Kind.FORCE; }

    @Override
    public UseAnim getUseAnimation(ItemStack st) { return drink() ? UseAnim.DRINK : UseAnim.NONE; }

    @Override
    public int getUseDuration(ItemStack st) { return drink() ? 24 : 0; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (drink()) {
            p.startUsingItem(hand);
            return InteractionResultHolder.consume(st);
        }
        if (kind == Kind.KIT_ENGIN) {   // s'utilise sur un engin (événement d'interaction)
            if (!level.isClientSide) p.displayClientMessage(Component.literal(WSLang.t("item.kit_engin.how")), true);
            return InteractionResultHolder.pass(st);
        }
        if (level.isClientSide || !(p instanceof ServerPlayer sp)) return InteractionResultHolder.success(st);
        boolean used = switch (kind) {
            case RAPPEL -> recall(sp);
            case KIT_PONT -> com.wavesurvivor.horde.rift.RiftIslets.kitRepair(sp);
            case VIGIE -> watch(sp);
            case RELANCE -> reroll(sp);
            default -> false;
        };
        if (!used) return InteractionResultHolder.fail(st);
        if (!sp.isCreative()) st.shrink(1);
        sp.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack st, Level level, LivingEntity e) {
        if (!level.isClientSide) {
            switch (kind) {
                case FIOLE -> {
                    e.heal(16f);   // 8 cœurs d'un coup
                    ((ServerLevel) level).sendParticles(ParticleTypes.WITCH, e.getX(), e.getY() + 1, e.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
                }
                case RESISTANCE -> e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2400, 0));
                case FORCE -> e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2400, 0));
                default -> {}
            }
            level.playSound(null, e.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.4f);
        }
        if (!(e instanceof Player p) || !p.isCreative()) st.shrink(1);
        return st;
    }

    /** Pierre de rappel : téléporte à côté du Monolithe (même dimension). */
    private static boolean recall(ServerPlayer p) {
        BlockPos m = com.wavesurvivor.altar.AltarDefense.sessionPos();
        ServerLevel l = p.serverLevel();
        if (m == null || !com.wavesurvivor.altar.AltarDefense.isActiveAt(l.dimension().location().toString(), m)) {
            p.displayClientMessage(Component.literal(WSLang.t("item.pierre_rappel.fail")), true);
            return false;
        }
        BlockPos dest = null;
        for (int r = 3; r <= 6 && dest == null; r++) {
            for (int a = 0; a < 16 && dest == null; a++) {
                int x = m.getX() + (int) Math.round(Math.cos(a * Math.PI / 8) * r), z = m.getZ() + (int) Math.round(Math.sin(a * Math.PI / 8) * r);
                for (int y = m.getY() + 4; y >= m.getY() - 4; y--) {
                    BlockPos b = new BlockPos(x, y, z);
                    if (l.getBlockState(b).isAir() && l.getBlockState(b.above()).isAir() && l.getBlockState(b.below()).isSolidRender(l, b.below())) { dest = b; break; }
                }
            }
        }
        if (dest == null) dest = m.above(2);
        l.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.5);
        p.teleportTo(l, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
        l.playSound(null, dest, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.8f);
        return true;
    }

    /** Lanterne de vigie : les monstres proches brillent 30 s (visibles à travers les blocs, sous les ponts compris). */
    private static boolean watch(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        int n = 0;
        for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(96), m -> m.isAlive() && m instanceof Enemy)) {
            m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));
            n++;
        }
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.6f);
        l.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1.5, p.getZ(), 30, 1.5, 1, 1.5, 0.05);
        p.displayClientMessage(Component.literal(WSLang.t("item.lanterne_vigie.used", n)), true);
        return true;
    }

    /** Relance de bénédiction : +1 relance de cartes. */
    private static boolean reroll(ServerPlayer p) {
        com.wavesurvivor.horde.benediction.RogueUpgradeManager.addRerolls(p, 1);
        p.displayClientMessage(Component.literal(WSLang.t("item.relance_benediction.used")), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level lvl, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(WSLang.t("item." + id() + ".tip")).withStyle(ChatFormatting.GRAY));
    }

    public String id() {
        return switch (kind) {
            case FIOLE -> "fiole_neant";
            case RESISTANCE -> "elixir_resistance";
            case FORCE -> "elixir_force";
            case RAPPEL -> "pierre_rappel";
            case KIT_PONT -> "kit_pont";
            case KIT_ENGIN -> "kit_engin";
            case VIGIE -> "lanterne_vigie";
            case RELANCE -> "relance_benediction";
        };
    }
}
