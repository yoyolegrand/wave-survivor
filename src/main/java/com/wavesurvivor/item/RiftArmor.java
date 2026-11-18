package com.wavesurvivor.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wavesurvivor.craft.CraftConfig;
import com.wavesurvivor.craft.CraftRecipes;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 2.1 — ARMURE DE LA FAILLE : état d'une pièce (NBT « RiftArmor » : Tier, Branch, Level), réglages
 * (crafting_fragments.json : « equipment.rift_set » et « riftArmorUpgrades »), bonus d'ensemble et améliorations
 * (paliers II-III à la Forge de l'écran Renaissance ; branches à l'onglet Artisanat du Sanctuaire).
 * Commun client / serveur (aucune classe du client ici).
 */
public final class RiftArmor {

    public static final String TAG = "RiftArmor";

    /** Branche : BASE (paliers I à III), BLESSED (Bénie), CORRUPTED (Corrompue). */
    public enum Branch {
        BASE(""), BLESSED("blessed"), CORRUPTED("corrupted");

        public final String id;

        Branch(String id) { this.id = id; }

        public static Branch of(String s) {
            if ("blessed".equals(s)) return BLESSED;
            if ("corrupted".equals(s)) return CORRUPTED;
            return BASE;
        }

        /** Alignement du Sanctuaire donné par la branche (null pour la base). */
        public String align() {
            return this == BLESSED ? com.wavesurvivor.shrine.ShrineGear.CONSECRATED : this == CORRUPTED ? com.wavesurvivor.shrine.ShrineGear.PROFANED : null;
        }
    }

    private RiftArmor() {}

    // ─── État (NBT) ───

    private static CompoundTag tag(ItemStack st) {
        return st.getTag() != null && st.getTag().contains(TAG) ? st.getTag().getCompound(TAG) : null;
    }

    public static Branch branch(ItemStack st) {
        CompoundTag t = tag(st);
        return t == null ? Branch.BASE : Branch.of(t.getString("Branch"));
    }

    /** Palier 1 à 3 (une pièce bénie ou corrompue est toujours au palier 3). */
    public static int tier(ItemStack st) {
        if (branch(st) != Branch.BASE) return 3;
        CompoundTag t = tag(st);
        return t == null || !t.contains("Tier") ? 1 : Math.max(1, Math.min(3, t.getInt("Tier")));
    }

    /** Niveau dans la branche (1 à 3). */
    public static int level(ItemStack st) {
        CompoundTag t = tag(st);
        return t == null || !t.contains("Level") ? 1 : Math.max(1, Math.min(3, t.getInt("Level")));
    }

    public static void setTier(ItemStack st, int tier) {
        CompoundTag t = st.getOrCreateTagElement(TAG);
        t.putInt("Tier", Math.max(1, Math.min(3, tier)));
    }

    public static void setBranch(ItemStack st, Branch b, int level) {
        CompoundTag t = st.getOrCreateTagElement(TAG);
        t.putInt("Tier", 3);
        t.putString("Branch", b.id);
        t.putInt("Level", Math.max(1, Math.min(3, level)));
    }

    /** Apparence de l'icône (propriété wavesurvivor:rift_look) : 0 / 0,25 / 0,5 palier I-II-III, 0,75 bénie, 1 corrompue. */
    public static float look(ItemStack st) {
        return switch (branch(st)) {
            case BLESSED -> 0.75f;
            case CORRUPTED -> 1.0f;
            default -> (tier(st) - 1) * 0.25f;
        };
    }

    public static boolean is(ItemStack st) { return st.getItem() instanceof RiftArmorItem; }

    // ─── Réglages ───

    private static JsonObject cfg() { return CraftConfig.equip("rift_set"); }

    private static JsonObject obj(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    private static double numOf(JsonElement e, double def) {
        try { return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def; } catch (Exception ex) { return def; }
    }

    public static double num(String key, double def) { return numOf(cfg().get(key), def); }

    /** Valeur d'un objet indexé par palier (« 1 », « 2 », « 3 »). */
    public static double byTier(String key, int tier, double def) { return numOf(obj(cfg(), key).get(String.valueOf(tier)), def); }

    /** Valeur d'un tableau de branche (niveaux I, II, III). */
    public static double byLevel(Branch b, String key, int level, double def) {
        if (b == Branch.BASE) return 0;
        JsonObject o = obj(cfg(), b.id);
        if (!o.has(key)) return def;
        JsonElement e = o.get(key);
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            return a.isEmpty() ? def : numOf(a.get(Math.max(0, Math.min(a.size() - 1, level - 1))), def);
        }
        return numOf(e, def);
    }

    /** Réglage simple d'une branche (ex. waveAllyHeal). */
    public static double branchNum(Branch b, String key, double def) { return numOf(obj(cfg(), b.id).get(key), def); }

    private static final double[][] DEF_ARMOR = {{2, 6, 5, 2}, {3, 8, 6, 3}, {3, 8, 6, 3}};

    /** Protection d'une pièce selon son palier (tableau casque, plastron, jambières, bottes). */
    public static double tierArmor(int tier, ArmorItem.Type type) {
        int i = type.ordinal();
        JsonElement e = obj(cfg(), "tierArmor").get(String.valueOf(tier));
        if (e != null && e.isJsonArray() && e.getAsJsonArray().size() > i) return numOf(e.getAsJsonArray().get(i), DEF_ARMOR[tier - 1][i]);
        return DEF_ARMOR[Math.max(0, Math.min(2, tier - 1))][i];
    }

    // ─── Bonus d'ensemble ───

    /**
     * Ensemble porté : full = 4 pièces de la Faille ; tier = palier le plus bas ; branch = BLESSED / CORRUPTED si les 4 pièces
     * sont de cette branche (sinon BASE) ; level = niveau le plus bas dans cette branche.
     */
    public record SetInfo(boolean full, int tier, Branch branch, int level, int count) {
        public static final SetInfo NONE = new SetInfo(false, 0, Branch.BASE, 0, 0);
    }

    public static SetInfo set(Player p) {
        if (p == null) return SetInfo.NONE;
        int n = 0, tier = 3, level = 3;
        Branch common = null;
        boolean mixed = false;
        for (ItemStack a : p.getInventory().armor) {
            if (!is(a)) continue;
            n++;
            tier = Math.min(tier, tier(a));
            Branch b = branch(a);
            if (common == null) common = b; else if (common != b) mixed = true;
            level = Math.min(level, level(a));
        }
        if (n < 4) return new SetInfo(false, 0, Branch.BASE, 0, n);
        Branch b = mixed || common == null ? Branch.BASE : common;
        return new SetInfo(true, tier, b, b == Branch.BASE ? 0 : level, n);
    }

    /** Niveau maximal de l'Effet de Faille pour ce joueur (Ancre de la Faille), ou {@code max} sans l'ensemble. */
    public static int effectCap(Player p, int max) {
        SetInfo s = set(p);
        if (!s.full()) return max;
        return Math.max(1, Math.min(max, (int) byTier("riftEffectCap", s.tier(), 5 - s.tier())));
    }

    /** Grâce de la Faille : immunité à l'Effet de Faille. */
    public static boolean immune(Player p) {
        SetInfo s = set(p);
        return s.full() && s.branch() == Branch.BLESSED;
    }

    // ─── Coûts et améliorations ───

    private static List<CraftConfig.Cost> costs(JsonObject group, String key) {
        List<CraftConfig.Cost> out = new ArrayList<>();
        if (!group.has(key) || !group.get(key).isJsonArray()) return out;
        for (JsonElement e : group.getAsJsonArray(key)) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String it = o.has("item") ? o.get("item").getAsString() : "";
            if (!it.isEmpty()) out.add(new CraftConfig.Cost(it, Math.max(1, (int) numOf(o.get("count"), 1))));
        }
        return out;
    }

    /** Coût du palier suivant (Forge) ; null si la pièce est déjà au palier III ou dans une branche. */
    public static List<CraftConfig.Cost> tierCost(ItemStack st) {
        if (!is(st) || branch(st) != Branch.BASE || tier(st) >= 3) return null;
        int next = tier(st) + 1;
        JsonObject tiers = obj(CraftConfig.section("riftArmorUpgrades"), "tiers");
        List<CraftConfig.Cost> c = costs(tiers, String.valueOf(next));
        if (st.getItem() instanceof ArmorItem ai && ai.getType() == ArmorItem.Type.CHESTPLATE) c.addAll(costs(tiers, next + "_extraChestplate"));
        return c;
    }

    /** Coût d'un niveau de branche (Sanctuaire). */
    public static List<CraftConfig.Cost> branchCost(Branch b, int level) {
        return costs(obj(CraftConfig.section("riftArmorUpgrades"), b.id), String.valueOf(level));
    }

    public static boolean canPay(Player p, List<CraftConfig.Cost> cost) {
        if (cost == null || cost.isEmpty()) return false;
        for (CraftConfig.Cost k : cost) if (CraftRecipes.count(p, k.item()) < k.count()) return false;
        return true;
    }

    public static void pay(Player p, List<CraftConfig.Cost> cost) {
        for (CraftConfig.Cost k : cost) {
            int left = k.count();
            for (ItemStack st : p.getInventory().items) {
                if (left <= 0) break;
                if (!CraftRecipes.matches(st, k.item())) continue;
                int take = Math.min(left, st.getCount());
                st.shrink(take);
                left -= take;
            }
        }
    }

    /** Texte d'un coût : « 12 Poussière de Faille, 2 Diamant ». */
    public static String costText(List<CraftConfig.Cost> cost) {
        StringBuilder sb = new StringBuilder();
        for (CraftConfig.Cost k : cost) {
            ItemStack ic = CraftRecipes.icon(k.item());
            if (sb.length() > 0) sb.append(", ");
            sb.append(k.count()).append(' ').append(ic.isEmpty() ? k.item() : ic.getHoverName().getString());
        }
        return sb.toString();
    }

    /** Pièce désignée par la Forge : 0-35 = inventaire, 100-103 = emplacements d'armure. */
    public static ItemStack bySlot(Player p, int code) {
        if (code >= 100 && code < 104) return p.getInventory().armor.get(code - 100);
        if (code >= 0 && code < Math.min(36, p.getInventory().items.size())) return p.getInventory().items.get(code);
        return ItemStack.EMPTY;
    }

    /** Pièces de la Faille du joueur (emplacements d'armure puis inventaire), codées comme {@link #bySlot}. */
    public static List<Integer> slots(Player p) {
        List<Integer> out = new ArrayList<>();
        for (int i = 3; i >= 0; i--) if (is(p.getInventory().armor.get(i))) out.add(100 + i);
        for (int i = 0; i < Math.min(36, p.getInventory().items.size()); i++) if (is(p.getInventory().items.get(i))) out.add(i);
        return out;
    }

    /** Signature de l'état des pièces (l'écran de la Forge se reconstruit quand elle change). */
    public static String sig(Player p) {
        StringBuilder sb = new StringBuilder();
        for (int c : slots(p)) {
            ItemStack st = bySlot(p, c);
            sb.append(c).append(':').append(tier(st)).append(branch(st).id).append(level(st)).append(';');
        }
        return sb.toString();
    }

    /** Forge : la pièce passe au palier suivant (enchantements, durabilité et améliorations gardés). */
    public static void forgeUpgrade(ServerPlayer p, int code) {
        ItemStack st = bySlot(p, code);
        List<CraftConfig.Cost> cost = tierCost(st);
        if (cost == null) return;
        if (!canPay(p, cost)) {
            p.displayClientMessage(Component.literal(WSLang.t("shrine.craft_need")), true);
            return;
        }
        pay(p, cost);
        setTier(st, tier(st) + 1);
        p.getInventory().setChanged();
        p.inventoryMenu.broadcastChanges();
        p.sendSystemMessage(Component.literal(WSLang.t("rift_armor.upgraded", st.getHoverName().getString(), tierName(st))));
        p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Nom de l'état : « Palier II — Éveillée », « Bénie II »… */
    public static String tierName(ItemStack st) {
        Branch b = branch(st);
        if (b != Branch.BASE) return WSLang.t("rift_armor." + b.id, roman(level(st)));
        return WSLang.t("rift_armor.tier." + tier(st));
    }

    public static String roman(int n) { return n == 1 ? "I" : n == 2 ? "II" : "III"; }

    // ─── Sanctuaire : pièce à faire évoluer dans une branche ───

    /** La pièce peut-elle recevoir ce niveau de branche ? Niveau I : palier III sans branche, alignement compatible ; sinon le niveau précédent. */
    public static boolean eligible(ItemStack st, Branch b, int level) {
        if (!is(st) || b == Branch.BASE) return false;
        if (level <= 1) {
            if (branch(st) != Branch.BASE || tier(st) < 3) return false;
            String al = st.getTag() != null && st.getTag().contains(com.wavesurvivor.shrine.ShrineGear.ALIGN)
                    ? st.getTag().getString(com.wavesurvivor.shrine.ShrineGear.ALIGN) : null;
            return al == null || al.isEmpty() || al.equals(b.align());
        }
        return branch(st) == b && level(st) == level - 1;
    }

    /** Première pièce éligible : main, emplacements d'armure, puis inventaire. */
    public static ItemStack branchTarget(Player p, Branch b, int level) {
        if (p == null) return ItemStack.EMPTY;
        if (eligible(p.getMainHandItem(), b, level)) return p.getMainHandItem();
        for (int i = 3; i >= 0; i--) if (eligible(p.getInventory().armor.get(i), b, level)) return p.getInventory().armor.get(i);
        for (ItemStack st : p.getInventory().items) if (eligible(st, b, level)) return st;
        return ItemStack.EMPTY;
    }
}
