package com.wavesurvivor.horde.kingdom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.CustomHordeStore;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * BÂTON DE TRACÉ (éditeur du mode Kingdom) : trace un chemin que les escouades suivront de leur Porte au Monolithe.
 *  - clic droit au sol : ajoute un point en fin de chemin ;
 *  - clic droit sur un point existant (à 1,5 bloc) : le retire ;
 *  - Maj + clic droit : insère un point entre les deux points les plus proches ;
 *  - clic droit dans le vide : active / coupe l'enregistrement en marchant (un point tous les 8 blocs parcourus).
 * Les points sont rangés dans l'objet (pour l'aperçu côté client) ET enregistrés aussitôt dans la horde.
 */
public class PathWandItem extends Item {

    public PathWandItem(Properties p) { super(p); }

    @Override
    public boolean isFoil(ItemStack s) { return true; }

    // ─── Données rangées dans l'objet ───

    public static String horde(ItemStack s) { return s.getOrCreateTag().getString("Horde"); }

    public static int pathIndex(ItemStack s) { return s.getOrCreateTag().getInt("Path"); }

    public static boolean recording(ItemStack s) { return s.getOrCreateTag().getBoolean("Rec"); }

    public static List<BlockPos> points(ItemStack s) {
        List<BlockPos> out = new ArrayList<>();
        ListTag l = s.getOrCreateTag().getList("Points", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < l.size(); i++) {
            int[] a = l.getIntArray(i);
            if (a.length == 3) out.add(new BlockPos(a[0], a[1], a[2]));
        }
        return out;
    }

    private static void setPoints(ItemStack s, List<BlockPos> pts) {
        ListTag l = new ListTag();
        for (BlockPos b : pts) l.add(new IntArrayTag(new int[]{b.getX(), b.getY(), b.getZ()}));
        s.getOrCreateTag().put("Points", l);
    }

    // ─── Utilisation ───

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        List<BlockPos> pts = points(stack);
        int near = -1;
        for (int i = 0; i < pts.size(); i++) if (pts.get(i).distSqr(at) <= 2.25 || pts.get(i).distSqr(ctx.getClickedPos()) <= 2.25) { near = i; break; }
        String msg;
        if (near >= 0 && !sp.isShiftKeyDown()) {
            pts.remove(near);
            msg = WSLang.t("kingdom.wand.removed", near + 1, pts.size());
        } else if (sp.isShiftKeyDown() && pts.size() >= 2) {
            int idx = insertIndex(pts, at);
            pts.add(idx, at);
            msg = WSLang.t("kingdom.wand.inserted", idx + 1, pts.size());
        } else {
            pts.add(at);
            msg = WSLang.t("kingdom.wand.added", pts.size());
        }
        commit(sp, stack, pts, msg);
        sp.level().playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
        // Anti double-clic : un clic droit maintenu répète l'action toutes les 4 ticks (retirer puis
        // aussitôt re-poser un point) → courte recharge de 0,4 s : un clic = une seule action
        sp.getCooldowns().addCooldown(this, 8);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            boolean rec = !recording(stack);
            stack.getOrCreateTag().putBoolean("Rec", rec);
            sp.displayClientMessage(Component.literal(WSLang.t(rec ? "kingdom.wand.rec_on" : "kingdom.wand.rec_off")), true);
            level.playSound(null, sp.blockPosition(), rec ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.5f);
            sp.getCooldowns().addCooldown(this, 8); // même anti double-clic (évite d'activer puis couper aussitôt)
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Enregistrement en marchant : un point tous les 8 blocs, au sol. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !selected || !(entity instanceof ServerPlayer sp) || !recording(stack)) return;
        if (level.getGameTime() % 5 != 0 || !sp.onGround()) return;
        List<BlockPos> pts = points(stack);
        BlockPos here = sp.blockPosition();
        if (!pts.isEmpty() && pts.get(pts.size() - 1).distSqr(here) < 64) return;
        pts.add(here);
        commit(sp, stack, pts, WSLang.t("kingdom.wand.added", pts.size()));
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("\u00a77" + horde(s) + " \u00b7 #" + (pathIndex(s) + 1) + " \u00b7 " + points(s).size() + " pts"));
        tip.add(Component.literal(WSLang.t("kingdom.wand.tip")));
        if (recording(s)) tip.add(Component.literal(WSLang.t("kingdom.wand.rec_on")));
    }

    /** Position d'insertion : entre les deux points cons\u00e9cutifs dont le segment passe le plus pr\u00e8s. */
    private static int insertIndex(List<BlockPos> pts, BlockPos at) {
        int best = pts.size();
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i + 1 < pts.size(); i++) {
            BlockPos a = pts.get(i), b = pts.get(i + 1);
            double d = Math.sqrt(a.distSqr(at)) + Math.sqrt(b.distSqr(at)) - Math.sqrt(a.distSqr(b));
            if (d < bestD) { bestD = d; best = i + 1; }
        }
        return best;
    }

    // ─── Enregistrement dans la horde ───

    private static JsonObject obj(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonObject()) o.add(k, new JsonObject());
        return o.getAsJsonObject(k);
    }

    private static JsonArray arr(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonArray()) o.add(k, new JsonArray());
        return o.getAsJsonArray(k);
    }

    /** Chemin n\u00b0 idx de la horde (cr\u00e9\u00e9 s'il manque). */
    private static JsonObject pathOf(JsonObject horde, int idx) {
        JsonArray paths = arr(obj(obj(horde, "configData"), "kingdom"), "paths");
        while (paths.size() <= idx) {
            JsonObject np = new JsonObject();
            np.addProperty("name", WSLang.t("ui.paths.default") + " " + (paths.size() + 1));
            np.add("points", new JsonArray());
            paths.add(np);
        }
        JsonElement el = paths.get(idx);
        if (!el.isJsonObject()) { paths.set(idx, new JsonObject()); el = paths.get(idx); }
        return el.getAsJsonObject();
    }

    private static void commit(ServerPlayer sp, ItemStack stack, List<BlockPos> pts, String msg) {
        setPoints(stack, pts);
        if (!sp.hasPermissions(2)) { sp.displayClientMessage(Component.literal(WSLang.t("kingdom.wand.no_perm")), true); return; }
        if (HordeManager.get().isRunning()) { sp.displayClientMessage(Component.literal(WSLang.t("kingdom.wand.running")), true); return; }
        JsonObject h = CustomHordeStore.get(horde(stack));
        if (h == null) { sp.displayClientMessage(Component.literal(WSLang.t("kingdom.wand.no_horde", horde(stack))), true); return; }
        JsonArray arr = new JsonArray();
        for (BlockPos b : pts) {
            JsonObject o = new JsonObject();
            o.addProperty("x", b.getX());
            o.addProperty("y", b.getY());
            o.addProperty("z", b.getZ());
            arr.add(o);
        }
        pathOf(h, pathIndex(stack)).add("points", arr);
        CustomHordeStore.put(h, horde(stack));
        WaveSurvivorMod.reloadConfig();
        sp.displayClientMessage(Component.literal(msg), true);
    }

    /** \u00c9diteur : donne le b\u00e2ton pour le chemin n\u00b0 idx de la horde (remplace un \u00e9ventuel autre b\u00e2ton). */
    public static void give(ServerPlayer sp, String hordeName, int idx) {
        if (!sp.hasPermissions(2)) { sp.displayClientMessage(Component.literal(WSLang.t("kingdom.wand.no_perm")), true); return; }
        JsonObject h = CustomHordeStore.get(hordeName);
        if (h == null) { sp.displayClientMessage(Component.literal(WSLang.t("kingdom.wand.no_horde", hordeName)), false); return; }
        JsonObject path = pathOf(h, Math.max(0, Math.min(7, idx)));
        List<BlockPos> pts = new ArrayList<>();
        for (JsonElement el : arr(path, "points")) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            pts.add(new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
        }
        ItemStack stack = new ItemStack(com.wavesurvivor.registry.ModItems.PATH_WAND.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("Horde", hordeName);
        tag.putInt("Path", Math.max(0, Math.min(7, idx)));
        setPoints(stack, pts);
        String name = path.has("name") ? path.get("name").getAsString() : WSLang.t("ui.paths.default");
        stack.setHoverName(Component.literal("\u00a7d\u00a7l\u2726 " + WSLang.t("kingdom.wand.name") + " \u00a77\u2014 \u00a7f" + name));
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            if (sp.getInventory().getItem(i).getItem() instanceof PathWandItem) sp.getInventory().setItem(i, ItemStack.EMPTY);
        }
        if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        sp.displayClientMessage(Component.literal(WSLang.t("kingdom.wand.given", name, pts.size())), false);
    }
}
