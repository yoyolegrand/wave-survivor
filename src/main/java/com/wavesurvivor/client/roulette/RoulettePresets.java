package com.wavesurvivor.client.roulette;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.roulette.CustomChestData;
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
 * Préréglages de particules de l'éditeur : les effets « repos » et « roulette » des 6 coffres du mod (assets/wavesurvivor/roulette/presets_coffres.json).
 * Un préréglage s'applique au moment choisi (repos, roulette, révélation) et reste modifiable ensuite.
 */
@OnlyIn(Dist.CLIENT)
public final class RoulettePresets {

    private static final Gson GSON = new Gson();
    /** Nom du coffre → ses réglages bruts. */
    private static Map<String, JsonObject> data = null;

    private RoulettePresets() {}

    private static Map<String, JsonObject> data() {
        if (data != null) return data;
        Map<String, JsonObject> out = new LinkedHashMap<>();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(WaveSurvivorMod.MODID, "roulette/presets_coffres.json"));
            if (res.isPresent()) {
                try (var in = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                    for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(in).getAsJsonObject().entrySet()) out.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            }
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[Roulette] Préréglages illisibles : {}", e.getMessage());
        }
        data = out;
        return out;
    }

    /** Noms courts (Boss, Nether, Abysses, Arcanique, Trésors, End), dans l'ordre du fichier. */
    public static List<String> names() {
        List<String> l = new ArrayList<>();
        for (String full : data().keySet()) l.add(shortName(full));
        return l;
    }

    /** « Reliquaire du Boss » → « Boss », « Reliquaire de l'End » → « End », « Reliquaire Arcanique » → « Arcanique ». */
    private static String shortName(String full) {
        String s = full.replaceFirst("^Reliquaire\\s+", "").replaceFirst("^(du|des|de l'|de la|de)\\s*", "");
        return s.isEmpty() ? full : s;
    }

    /** Effet du moment (« idle », « spin », « reveal ») du préréglage, copie modifiable ; null si ce préréglage n'en règle pas. */
    public static CustomChestData.Effect effect(String shortName, String moment) {
        for (Map.Entry<String, JsonObject> e : data().entrySet()) {
            if (!shortName(e.getKey()).equals(shortName)) continue;
            JsonElement fx = e.getValue().get(moment);
            return fx != null && fx.isJsonObject() ? GSON.fromJson(fx, CustomChestData.Effect.class) : null;
        }
        return null;
    }
}
