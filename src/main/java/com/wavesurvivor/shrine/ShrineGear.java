package com.wavesurvivor.shrine;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ÉQUIPEMENTS AMÉLIORÉS : lecture / écriture des améliorations dans l'objet (NBT « WS_Align » et « WS_Upgrades »), tirage, calcul des totaux
 * de tout l'équipement porté (avec le plafond de +30 %) et application des bonus qui passent par les attributs du joueur.
 * Les effets spéciaux (vol de vie, épines, pacte de survie…) sont appliqués par ShrineGearEvents à partir de ces mêmes totaux.
 */
public final class ShrineGear {

    public static final String ALIGN = "WS_Align", UPGRADES = "WS_Upgrades";
    public static final String CONSECRATED = "consecrated", PROFANED = "profaned";

    /** Clés dont on garde la plus petite / la plus grande valeur au lieu de les additionner. */
    private static final Set<String> MIN_KEYS = Set.of("blade_kills", "pact_minutes");
    private static final Set<String> MAX_KEYS = Set.of("blade_heal", "poison_seconds", "regen_tier");

    private ShrineGear() {}

    // ─── Types d'équipement ───

    /** Types de cet objet : weapon, armor, chest, boots, all (vide = pas améliorable). */
    public static Set<String> typesOf(ItemStack st) {
        Set<String> t = new HashSet<>();
        if (st.isEmpty()) return t;
        if (st.getItem() instanceof ArmorItem armor) {
            t.add("armor");
            if (armor.getEquipmentSlot() == EquipmentSlot.CHEST) t.add("chest");
            if (armor.getEquipmentSlot() == EquipmentSlot.FEET) t.add("boots");
        } else if (st.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE)) {
            t.add("weapon");
        }
        if (!t.isEmpty()) t.add("all");
        return t;
    }

    /** Camp d'un fragment : « consecrated » (grâce), « profaned » (souillure), ou null. */
    public static String campOf(Item it) {
        if (it == ModItems.GRACE_SHARD.get() || it == ModItems.GRACE_FRAGMENT.get() || it == ModItems.GRACE_HEART.get()) return CONSECRATED;
        if (it == ModItems.TAINT_SHARD.get() || it == ModItems.TAINT_FRAGMENT.get() || it == ModItems.TAINT_HEART.get()) return PROFANED;
        return null;
    }

    /** Niveau d'un fragment : 1 Éclat, 2 Fragment, 3 Cœur (0 = pas un fragment). */
    public static int tierOf(Item it) {
        if (it == ModItems.GRACE_SHARD.get() || it == ModItems.TAINT_SHARD.get()) return 1;
        if (it == ModItems.GRACE_FRAGMENT.get() || it == ModItems.TAINT_FRAGMENT.get()) return 2;
        if (it == ModItems.GRACE_HEART.get() || it == ModItems.TAINT_HEART.get()) return 3;
        return 0;
    }

    // ─── NBT de l'objet ───

    public record Applied(String id, int tier) {}

    /** « consecrated », « profaned » ou null (pas encore d'amélioration). */
    public static String alignOf(ItemStack st) {
        return st.hasTag() && st.getTag().contains(ALIGN) ? st.getTag().getString(ALIGN) : null;
    }

    public static List<Applied> upgradesOf(ItemStack st) {
        List<Applied> out = new ArrayList<>();
        if (!st.hasTag() || !st.getTag().contains(UPGRADES, Tag.TAG_LIST)) return out;
        ListTag l = st.getTag().getList(UPGRADES, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            out.add(new Applied(c.getString("id"), Math.max(1, Math.min(3, c.getInt("tier")))));
        }
        return out;
    }

    public static void add(ItemStack st, String align, String id, int tier) {
        CompoundTag t = st.getOrCreateTag();
        t.putString(ALIGN, align);
        ListTag l = t.getList(UPGRADES, Tag.TAG_COMPOUND);
        CompoundTag c = new CompoundTag();
        c.putString("id", id);
        c.putInt("tier", tier);
        l.add(c);
        t.put(UPGRADES, l);
    }

    /** Retire la dernière amélioration ; sans plus aucune, l'objet n'a plus d'alignement. */
    public static void removeLast(ItemStack st) {
        if (!st.hasTag()) return;
        CompoundTag t = st.getTag();
        ListTag l = t.getList(UPGRADES, Tag.TAG_COMPOUND);
        if (!l.isEmpty()) l.remove(l.size() - 1);
        if (l.isEmpty()) {
            t.remove(UPGRADES);
            t.remove(ALIGN);
            if (t.isEmpty()) st.setTag(null);
        } else {
            t.put(UPGRADES, l);
        }
    }

    // ─── Tirage ───

    /** Tire une amélioration du camp, valable pour ces types d'équipement et pas déjà sur l'objet. Null si aucune. */
    public static ShrineUpgrades.Def draw(Set<String> types, String align, Collection<String> owned, RandomSource rnd) {
        List<ShrineUpgrades.Def> pool = new ArrayList<>();
        for (ShrineUpgrades.Def d : ShrineUpgrades.all()) {
            if (!d.align().equals(align) || owned.contains(d.id())) continue;
            boolean ok = false;
            for (String s : d.slots()) if (types.contains(s)) { ok = true; break; }
            if (ok) pool.add(d);
        }
        return pool.isEmpty() ? null : pool.get(rnd.nextInt(pool.size()));
    }

    // ─── Totaux de l'équipement porté ───

    /** Objets qui comptent : l'arme en main (si c'en est une) et les quatre pièces d'armure portées. */
    public static List<ItemStack> gearOf(Player p) {
        List<ItemStack> out = new ArrayList<>();
        ItemStack hand = p.getMainHandItem();
        if (typesOf(hand).contains("weapon") && hand.hasTag()) out.add(hand);
        for (ItemStack a : p.getInventory().armor) if (!a.isEmpty() && a.hasTag()) out.add(a);
        return out;
    }

    /**
     * Totaux par clé d'effet sur tout l'équipement porté. Les bonus positifs d'une clé « plafonnée » s'additionnent jusqu'à +30 %
     * (ou jusqu'à la valeur du plus fort objet seul, s'il dépasse le plafond) ; les malus s'additionnent sans limite.
     */
    public static Map<String, Double> totals(Player p) {
        Map<String, Double> pos = new HashMap<>(), neg = new HashMap<>(), single = new HashMap<>(), other = new HashMap<>();
        for (ItemStack st : gearOf(p)) {
            for (Applied a : upgradesOf(st)) {
                ShrineUpgrades.Def d = ShrineUpgrades.get(a.id());
                if (d == null) continue;
                for (ShrineUpgrades.Fx fx : d.effects()) {
                    double v = fx.at(a.tier());
                    String k = fx.key();
                    if (MIN_KEYS.contains(k)) other.merge(k, v, Math::min);
                    else if (MAX_KEYS.contains(k)) other.merge(k, v, Math::max);
                    else if (v >= 0) { pos.merge(k, v, Double::sum); single.merge(k, v, Math::max); }
                    else neg.merge(k, v, Double::sum);
                }
            }
        }
        Map<String, Double> out = new HashMap<>(other);
        Set<String> keys = new HashSet<>(pos.keySet());
        keys.addAll(neg.keySet());
        for (String k : keys) {
            double up = pos.getOrDefault(k, 0.0);
            if (ShrineUpgrades.isCapped(k)) up = Math.min(up, Math.max(ShrineUpgrades.cap(), single.getOrDefault(k, 0.0)));
            out.put(k, up + neg.getOrDefault(k, 0.0));
        }
        return out;
    }

    // ─── Bonus par attributs ───

    private record AttrFx(String key, Attribute attr, AttributeModifier.Operation op) {}

    private static final List<AttrFx> ATTRS = List.of(
            new AttrFx("damage_pct", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.MULTIPLY_TOTAL),
            new AttrFx("atkspeed_pct", Attributes.ATTACK_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL),
            new AttrFx("armor_pct", Attributes.ARMOR, AttributeModifier.Operation.MULTIPLY_TOTAL),
            new AttrFx("toughness_pct", Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.MULTIPLY_TOTAL),
            new AttrFx("speed_pct", Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL),
            new AttrFx("maxhp", Attributes.MAX_HEALTH, AttributeModifier.Operation.ADDITION),
            new AttrFx("kb_res", Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADDITION));

    private static final Map<UUID, Map<String, Double>> LAST = new HashMap<>();

    private static UUID uuid(String key) {
        return UUID.nameUUIDFromBytes(("ws_shrine_gear_" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Recalcule les bonus d'équipement du joueur et met à jour ses attributs (seulement si quelque chose a changé). */
    public static void refresh(Player p) {
        Map<String, Double> t = totals(p);
        Map<String, Double> prev = LAST.get(p.getUUID());
        if (prev != null && prev.equals(t)) return;
        for (AttrFx a : ATTRS) {
            AttributeInstance inst = p.getAttribute(a.attr());
            if (inst == null) continue;
            UUID id = uuid(a.key());
            if (inst.getModifier(id) != null) inst.removeModifier(id);
            double v = t.getOrDefault(a.key(), 0.0);
            if (v != 0) inst.addTransientModifier(new AttributeModifier(id, "ws_shrine_" + a.key(), v, a.op()));
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        LAST.put(p.getUUID(), t);
    }

    public static void forget(UUID player) { LAST.remove(player); }

    // ─── Textes ───

    /** Texte d'une valeur d'effet : « +10 % », « −5 % », « +2 », selon la clé. */
    public static String valueText(String key, double v) {
        if (key.equals("fall_reduction_pct")) return "−" + Math.round(Math.abs(v) * 100) + " %";
        if (key.endsWith("_pct") || key.equals("kb_res")) return (v >= 0 ? "+" : "−") + Math.round(Math.abs(v) * 100) + " %";
        if (key.equals("maxhp") || key.equals("blade_heal")) {
            double h = v / 2.0;
            String n = Math.abs(h - Math.round(h)) < 1e-9 ? String.valueOf(Math.round(Math.abs(h))) : String.format(java.util.Locale.ROOT, "%.1f", Math.abs(h)).replace('.', ',');
            return key.equals("maxhp") ? (h >= 0 ? "+" : "−") + n : n;
        }
        return Math.abs(v - Math.round(v)) < 1e-9 ? String.valueOf(Math.round(v)) : String.valueOf(v);
    }

    /** Une ligne par effet de l'amélioration au niveau donné, en vert (bonus) ou rouge (malus) : « §a+10 % dégâts ». */
    public static List<String> effectLines(ShrineUpgrades.Def d, int tier) {
        List<String> out = new ArrayList<>();
        for (ShrineUpgrades.Fx fx : d.effects()) {
            out.add((fx.bad() ? "§c" : "§a") + WSLang.t("shrine.fx." + fx.key(), valueText(fx.key(), fx.at(tier))));
        }
        return out;
    }

    public static String roman(int tier) { return tier == 1 ? "I" : tier == 2 ? "II" : "III"; }
}
