package com.wavesurvivor.horde.benediction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wavesurvivor.horde.editor.HordeJson;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SYNERGIES DE FAMILLE (1.8) : plus on cumule de bénédictions d'une même famille (attaque, défense…), plus on gagne un petit bonus de palier.
 * Réglages (blessings.json › settings › synergies) : enabled, thresholds (nombre de bénédictions pour chaque palier, ex. 3 / 5 / 7) et, par
 * famille, l'attribut, l'opération et le bonus de chaque palier. Chaque exemplaire pris compte (niveau). Les malédictions n'en donnent pas.
 * Le calcul sert aussi au client (cartes, « Mes bonus ») : il ne dépend que des réglages synchronisés et des bénédictions obtenues.
 */
public final class BlessingSynergy {

    /** Une famille : nombre de bénédictions, palier atteint, prochain palier (0 = au maximum) et bonus actuel. */
    public record Entry(String family, int count, int tier, int nextAt, Attribute attr, AttributeModifier.Operation op, double amount) {}

    private BlessingSynergy() {}

    private static JsonObject cfg() {
        JsonObject s = BlessingCatalog.settings();
        return s.has("synergies") && s.get("synergies").isJsonObject() ? s.getAsJsonObject("synergies") : new JsonObject();
    }

    public static boolean enabled() { return HordeJson.bool(cfg(), "enabled", true); }

    /** Paliers (nombre de bénédictions de la famille), croissants. */
    public static int[] thresholds() {
        JsonObject c = cfg();
        if (!c.has("thresholds") || !c.get("thresholds").isJsonArray()) return new int[]{3, 5, 7};
        JsonArray a = c.getAsJsonArray("thresholds");
        int[] t = new int[a.size()];
        for (int i = 0; i < t.length; i++) t[i] = a.get(i).getAsInt();
        return t;
    }

    /** Nombre de bénédictions d'une famille (un exemplaire par niveau). */
    public static int countFor(String family, Map<String, Integer> owned) {
        int n = 0;
        for (Map.Entry<String, Integer> e : owned.entrySet()) {
            RogueUpgrade u = RogueUpgradeRegistry.get(e.getKey());
            if (u != null && u.family().equals(family)) n += Math.max(0, e.getValue());
        }
        return n;
    }

    /** Prochain palier strictement au-dessus de ce nombre (0 = déjà au maximum). */
    public static int nextAt(int count) {
        for (int t : thresholds()) if (t > count) return t;
        return 0;
    }

    /** Une entrée par famille configurée (bonus 0 tant que le premier palier n'est pas atteint). */
    public static List<Entry> compute(Map<String, Integer> owned) {
        List<Entry> out = new ArrayList<>();
        if (!enabled()) return out;
        JsonObject c = cfg();
        if (!c.has("families") || !c.get("families").isJsonObject()) return out;
        int[] th = thresholds();
        for (Map.Entry<String, JsonElement> fe : c.getAsJsonObject("families").entrySet()) {
            if (!fe.getValue().isJsonObject()) continue;
            JsonObject f = fe.getValue().getAsJsonObject();
            Attribute attr = BlessingCatalog.attribute(HordeJson.str(f, "attribute", ""));
            if (attr == null) continue;
            int count = countFor(fe.getKey(), owned), tier = 0;
            for (int t : th) if (count >= t) tier++;
            double amount = 0;
            if (tier > 0 && f.has("amounts") && f.get("amounts").isJsonArray() && f.getAsJsonArray("amounts").size() > 0) {
                JsonArray am = f.getAsJsonArray("amounts");
                amount = am.get(Math.min(tier, am.size()) - 1).getAsDouble();
            }
            out.add(new Entry(fe.getKey(), count, tier, nextAt(count), attr, BlessingCatalog.operation(HordeJson.str(f, "operation", "addition")), amount));
        }
        return out;
    }

    private static UUID uuid(String family) {
        return UUID.nameUUIDFromBytes(("rogue_syn_" + family).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Recalcule les bonus de synergie du joueur à partir de ses bénédictions (retire les anciens, ajoute les nouveaux). */
    public static void apply(ServerPlayer p) {
        double mult = RogueUpgradeManager.difficultyMult();
        for (Entry e : compute(RogueUpgradeManager.levels(p))) {
            AttributeInstance inst = p.getAttribute(e.attr());
            if (inst == null) continue;
            UUID id = uuid(e.family());
            if (inst.getModifier(id) != null) inst.removeModifier(id);
            if (e.amount() != 0) inst.addTransientModifier(new AttributeModifier(id, "rogue_syn_" + e.family(), e.amount() * mult, e.op()));
        }
    }
}
