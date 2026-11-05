package com.wavesurvivor.horde.roulette;

import com.wavesurvivor.config.RulesConfig;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BOURSE À CLÉS (1.9) : petite bourse qui ne contient que des clés de coffres roulette (contenu enregistré dans l'objet, comme une boîte de Shulker).
 * - clic droit dans le vide : ouvre l'inventaire de la bourse ; accroupi : active / coupe le ramassage automatique des clés ;
 * - clic droit sur un coffre roulette sans clé en main : une clé de la bourse est consommée (accroupi : toutes celles du bon type, en une animation) ;
 * - réglages : {@code keyPouchSize}, {@code keyPouchAutoPickup}, {@code keyPouchBulk} dans rules.json.
 */
public class KeyPouch extends Item {

    public static final String TAG_KEYS = "PouchKeys", TAG_AUTO = "PouchAuto";

    public KeyPouch(Properties props) { super(props.stacksTo(1).rarity(Rarity.UNCOMMON)); }

    // ─── Règles ───

    public static int rows() { return Math.max(1, Math.min(6, (RulesConfig.keyPouchSize() + 8) / 9)); }

    /** Une clé de coffre roulette (nouvelle clé ou ancienne étiquette marquée) ? */
    public static boolean isKey(ItemStack s) {
        if (s.isEmpty()) return false;
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        if (!"minecraft:name_tag".equals(id) && !"wavesurvivor:roulette_key".equals(id)) return false;
        CompoundTag t = s.getTag();
        return t != null && t.getInt("rouletteKey") == 1;
    }

    public static boolean isPouch(ItemStack s) { return !s.isEmpty() && s.getItem() instanceof KeyPouch; }

    public static boolean autoPickup(ItemStack pouch) {
        CompoundTag t = pouch.getTag();
        return t == null || !t.contains(TAG_AUTO) || t.getBoolean(TAG_AUTO);
    }

    // ─── Contenu ───

    /** Inventaire d'une bourse : chaque modification est réécrite dans l'objet. */
    public static class Contents extends SimpleContainer {
        private final ItemStack pouch;
        private boolean loading = true;

        public Contents(ItemStack pouch) {
            super(rows() * 9);
            this.pouch = pouch;
            CompoundTag t = pouch.getTag();
            if (t != null && t.contains(TAG_KEYS, Tag.TAG_LIST)) {
                ListTag l = t.getList(TAG_KEYS, Tag.TAG_COMPOUND);
                for (int i = 0; i < l.size(); i++) {
                    CompoundTag c = l.getCompound(i);
                    int slot = c.getByte("Slot") & 255;
                    if (slot < getContainerSize()) super.setItem(slot, ItemStack.of(c));
                }
            }
            loading = false;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) { return isKey(stack); }

        @Override
        public void setChanged() {
            super.setChanged();
            if (loading) return; // chargement : ne pas réécrire un contenu partiel
            ListTag l = new ListTag();
            for (int i = 0; i < getContainerSize(); i++) {
                ItemStack s = getItem(i);
                if (s.isEmpty()) continue;
                CompoundTag c = new CompoundTag();
                c.putByte("Slot", (byte) i);
                s.save(c);
                l.add(c);
            }
            if (l.isEmpty()) {
                if (pouch.hasTag()) pouch.getTag().remove(TAG_KEYS);
            } else pouch.getOrCreateTag().put(TAG_KEYS, l);
        }
    }

    /** Clés contenues, regroupées par nom (pour l'info-bulle). */
    public static Map<String, Integer> summary(ItemStack pouch) {
        Map<String, Integer> out = new LinkedHashMap<>();
        CompoundTag t = pouch.getTag();
        if (t == null || !t.contains(TAG_KEYS, Tag.TAG_LIST)) return out;
        ListTag l = t.getList(TAG_KEYS, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            ItemStack s = ItemStack.of(l.getCompound(i));
            if (!s.isEmpty()) out.merge(s.getHoverName().getString(), s.getCount(), Integer::sum);
        }
        return out;
    }

    // ─── Objet ───

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        if (player.isShiftKeyDown()) {
            if (!RulesConfig.keyPouchAutoPickup()) {
                sp.displayClientMessage(Component.literal(WSLang.t("pouch.auto_disabled")), true);
                return InteractionResultHolder.success(stack);
            }
            boolean on = !autoPickup(stack);
            stack.getOrCreateTag().putBoolean(TAG_AUTO, on);
            sp.displayClientMessage(Component.literal(WSLang.t(on ? "pouch.auto_on" : "pouch.auto_off")), true);
            return InteractionResultHolder.success(stack);
        }
        open(sp, stack);
        return InteractionResultHolder.success(stack);
    }

    private static MenuType<ChestMenu> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    public static void open(ServerPlayer sp, ItemStack pouch) {
        final int rows = rows();
        final Contents contents = new Contents(pouch);
        NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new ChestMenu(type(rows), id, inv, contents, rows) {
            {
                for (int i = 0; i < rows * 9; i++) { // cases réservées aux clés
                    Slot old = slots.get(i);
                    Slot ns = new Slot(contents, i, old.x, old.y) {
                        @Override public boolean mayPlace(ItemStack s) { return isKey(s); }
                    };
                    ns.index = i;
                    slots.set(i, ns);
                }
            }

            @Override
            public boolean stillValid(Player pl) { return contains(pl, pouch); }
        }, Component.literal(WSLang.t("pouch.title"))));
    }

    private static boolean contains(Player p, ItemStack pouch) {
        if (p.getOffhandItem() == pouch) return true;
        for (ItemStack s : p.getInventory().items) if (s == pouch) return true;
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        Map<String, Integer> sum = summary(stack);
        if (sum.isEmpty()) tip.add(Component.literal("§8" + WSLang.t("pouch.empty")));
        else for (var e : sum.entrySet()) tip.add(Component.literal("§7" + e.getKey() + " §fx" + e.getValue()));
        tip.add(Component.literal("§8" + WSLang.t("pouch.tip_open")));
        tip.add(Component.literal("§8" + WSLang.t("pouch.tip_chest")));
        if (RulesConfig.keyPouchBulk()) tip.add(Component.literal("§8" + WSLang.t("pouch.tip_bulk")));
        if (RulesConfig.keyPouchAutoPickup()) tip.add(Component.literal("§8" + WSLang.t(autoPickup(stack) ? "pouch.tip_auto_on" : "pouch.tip_auto_off")));
    }

    // ─── Utilisation sur un coffre roulette ───

    /** Bourses du joueur : seconde main, puis inventaire. */
    public static List<ItemStack> pouches(Player p) {
        List<ItemStack> out = new ArrayList<>();
        if (isPouch(p.getOffhandItem())) out.add(p.getOffhandItem());
        for (ItemStack s : p.getInventory().items) if (isPouch(s)) out.add(s);
        return out;
    }

    /**
     * Retire des clés de la première bourse qui en contient du bon type pour ce coffre : une seule, ou toutes ({@code all}). Si les clés ne sont pas
     * consommées, sert seulement à vérifier (renvoie 1). @return nombre de clés retirées (0 = aucune).
     */
    public static int take(Player p, RouletteChestConfig config, boolean all, boolean consume) {
        for (ItemStack pouch : pouches(p)) {
            Contents c = new Contents(pouch);
            int n = 0;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty() || !RouletteChestManager.keyMatches(s, config)) continue;
                if (!consume) return 1;
                int k = all ? s.getCount() : 1;
                s.shrink(k);
                if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
                n += k;
                if (!all) break;
            }
            if (n > 0) {
                c.setChanged();
                return n;
            }
        }
        return 0;
    }

    /** Retire une Clé scellée de la première bourse qui en contient une (ouverture d'un sceau de reliquaire). @return vrai si une clé a été prise. */
    public static boolean takeSealed(Player p) {
        for (ItemStack pouch : pouches(p)) {
            Contents c = new Contents(pouch);
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty() || !SealedKey.is(s)) continue;
                s.shrink(1);
                if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
                c.setChanged();
                return true;
            }
        }
        return false;
    }

    // ─── Ramassage automatique ───

    public static class Events {
    @SubscribeEvent
    public void onPickup(EntityItemPickupEvent e) {
        if (!RulesConfig.keyPouchAutoPickup() || e.getEntity().level().isClientSide) return;
        ItemStack in = e.getItem().getItem();
        if (!isKey(in)) return;
        Player p = e.getEntity();
        int before = in.getCount();
        for (ItemStack pouch : pouches(p)) {
            if (!autoPickup(pouch)) continue;
            Contents c = new Contents(pouch);
            ItemStack rest = c.addItem(in.copy());
            in.setCount(rest.getCount());
            if (rest.isEmpty()) break;
        }
        int moved = before - in.getCount();
        if (moved <= 0) return;
        p.take(e.getItem(), moved);
        p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.2f, 1.4f);
        if (in.isEmpty()) {
            e.getItem().discard();
            e.setCanceled(true);
        }
    }
    }
}
