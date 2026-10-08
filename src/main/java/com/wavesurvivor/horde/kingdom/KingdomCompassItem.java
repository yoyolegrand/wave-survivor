package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * BOUSSOLE DU ROYAUME (Kingdom) — achetée 64 ◆ à la boutique du Monolithe. Sur la carte (Town Hall › Carte), un clic
 * sur un repère règle la boussole dessus ; clic droit sur la carte = effacer. Cible stockée au format des boussoles
 * liées vanilla (« LodestonePos » / « LodestoneDimension ») : l'aiguille fonctionne nativement. Rendu : cadran de la
 * boussole vanilla, teinté en or (ClientModEvents).
 */
public class KingdomCompassItem extends Item {

    public static final String TARGET_NAME = "ws_compass_target";
    /** Type de repère visé (gisement, arbre, Catalyseur…), pour se dérégler quand il disparaît. */
    public static final String TARGET_TYPE = "ws_compass_type";

    public KingdomCompassItem(Properties props) {
        super(props);
    }

    public static boolean hasTarget(ItemStack st) {
        return st.hasTag() && st.getTag().contains("LodestonePos");
    }

    /** Position visée (dimension du joueur), ou null. */
    public static GlobalPos target(ItemStack st) {
        if (!hasTarget(st)) return null;
        CompoundTag t = st.getTag();
        BlockPos p = NbtUtils.readBlockPos(t.getCompound("LodestonePos"));
        var dim = Level.OVERWORLD;
        if (t.contains("LodestoneDimension")) {
            dim = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    new net.minecraft.resources.ResourceLocation(t.getString("LodestoneDimension")));
        }
        return GlobalPos.of(dim, p);
    }

    public static void setTarget(ItemStack st, Level level, BlockPos pos, String name, int type) {
        CompoundTag t = st.getOrCreateTag();
        t.put("LodestonePos", NbtUtils.writeBlockPos(pos));
        t.putString("LodestoneDimension", level.dimension().location().toString());
        t.putBoolean("LodestoneTracked", false);
        t.putString(TARGET_NAME, name);
        t.putInt(TARGET_TYPE, type);
    }

    public static void clearTarget(ItemStack st) {
        if (!st.hasTag()) return;
        CompoundTag t = st.getTag();
        t.remove("LodestonePos");
        t.remove("LodestoneDimension");
        t.remove("LodestoneTracked");
        t.remove(TARGET_NAME);
        t.remove(TARGET_TYPE);
    }

    @Override
    public boolean isFoil(ItemStack st) {
        return hasTarget(st);
    }

    /** Cible disparue (gisement / arbre récolté, Catalyseur détruit) : la boussole se dèrègle et prévient. */
    @Override
    public void inventoryTick(ItemStack st, Level level, net.minecraft.world.entity.Entity holder, int slot, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 40 != 0 || !hasTarget(st)) return;
        int type = st.getTag().getInt(TARGET_TYPE);
        if (type != 5 && type != 6 && type != 7 && type != 10) return; // Catalyseur, gisement, arbre, foyer de corruption
        GlobalPos gp = target(st);
        if (gp == null || !gp.dimension().equals(level.dimension()) || !level.isLoaded(gp.pos())) return;
        var box = new net.minecraft.world.phys.AABB(gp.pos()).inflate(4, 12, 4);
        boolean alive = type == 5
                ? !level.getEntitiesOfClass(com.wavesurvivor.entity.BrecheEntity.class, box, b -> b.isAlive() && b.isCatalyst()).isEmpty()
                : !level.getEntitiesOfClass(com.wavesurvivor.entity.GisementEntity.class, box, g -> g.isAlive() && g.isFoyer() == (type == 10)).isEmpty();
        if (alive) return;
        clearTarget(st);
        if (holder instanceof net.minecraft.world.entity.player.Player p) p.displayClientMessage(WSLang.c("compass.gone"), true);
    }

    @Override
    public void appendHoverText(ItemStack st, Level level, List<Component> tip, TooltipFlag flag) {
        if (hasTarget(st)) {
            tip.add(Component.literal(WSLang.t("compass.target", st.getTag().getString(TARGET_NAME))).withStyle(ChatFormatting.GOLD));
        } else {
            tip.add(Component.literal(WSLang.t("compass.no_target")).withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.literal(WSLang.t("compass.hint")).withStyle(ChatFormatting.DARK_GRAY));
    }
}
