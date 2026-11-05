package com.wavesurvivor.client.roulette;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modèle du coffre roulette (1.9), lu dans assets/wavesurvivor/roulette/chest_model.json : pavés en pixels (bloc = 16), partie « base » ou « lid »
 * (le couvercle pivote autour de la charnière arrière : axe X, y = 9, z = 15) et emplacements des médaillons.
 */
@OnlyIn(Dist.CLIENT)
public final class RouletteChestModel {

    /** Un pavé : coordonnées en pixels, partie (couvercle ou non) et clé de texture (wood, iron, trim, gem, medallion). */
    public record Element(String name, float[] from, float[] to, boolean lid, String texture) {}

    /** Emplacement d'un médaillon : pavé fin et face visible (north, south, west, east, up). */
    public record Slot(float[] from, float[] to, String face, boolean lid) {}

    public final List<Element> elements = new ArrayList<>();
    public final Map<String, Slot> slots = new LinkedHashMap<>();

    private static RouletteChestModel instance = null;
    private static boolean failed = false;

    private RouletteChestModel() {}

    /** Le modèle (lu une fois ; null si le fichier est illisible : rien ne s'affiche, l'erreur est dans le journal). */
    public static RouletteChestModel get() {
        if (instance != null || failed) return instance;
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(WaveSurvivorMod.MODID, "roulette/chest_model.json"));
            if (res.isEmpty()) throw new IllegalStateException("chest_model.json introuvable");
            try (var in = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                RouletteChestModel m = new RouletteChestModel();
                for (JsonElement e : o.getAsJsonArray("elements")) {
                    JsonObject eo = e.getAsJsonObject();
                    m.elements.add(new Element(eo.get("name").getAsString(), vec(eo.getAsJsonArray("from")), vec(eo.getAsJsonArray("to")),
                            "lid".equals(eo.get("part").getAsString()), eo.get("texture").getAsString()));
                }
                JsonObject sl = o.getAsJsonObject("medallion_slots");
                for (Map.Entry<String, JsonElement> e : sl.entrySet()) {
                    JsonObject so = e.getValue().getAsJsonObject();
                    m.slots.put(e.getKey(), new Slot(vec(so.getAsJsonArray("from")), vec(so.getAsJsonArray("to")), so.get("face").getAsString(),
                            so.has("part") && "lid".equals(so.get("part").getAsString())));
                }
                instance = m;
            }
        } catch (Exception e) {
            failed = true;
            WaveSurvivorMod.LOGGER.error("[Roulette] Modèle du coffre illisible : {}", e.getMessage());
        }
        return instance;
    }

    /** Oublie le modèle (rechargement des ressources). */
    public static void reset() {
        instance = null;
        failed = false;
    }

    private static float[] vec(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }
}
