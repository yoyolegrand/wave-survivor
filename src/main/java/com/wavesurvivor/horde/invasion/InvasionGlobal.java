package com.wavesurvivor.horde.invasion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RÉGLAGES GÉNÉRAUX DES INVASIONS (1.7) : config/wavesurvivor/invasions.json (créé avec ces valeurs s'il manque,
 * relu par /ws reload). Les réglages propres à chaque invasion se font dans l'éditeur (horde de mode « invasion »).
 */
public final class InvasionGlobal {

    /** Interrupteur général : faux = le Mauvais présage reste 100 % vanilla et /ws invasion est désactivée. */
    public boolean enabled = true;
    /** Le Mauvais présage dans un village lance une roulette (raid vanilla ou invasion du mod). Faux = raid vanilla seul. */
    public boolean omenRoulette = true;
    /** Poids du raid vanilla PUR dans la roulette (les invasions ont le leur, dans l'éditeur). Par défaut 0 : le raid
     *  de Minecraft n'est jamais tiré, seules les invasions du mod le sont (« Invasion de Pillards » reprend la composition du raid vanilla
     *  et se modifie dans l'éditeur). Mettre une valeur > 0 pour rendre le raid classique possible. */
    public int vanillaWeight = 0;
    /** Raid vanilla : récompenses du mod (émeraudes, clés, points de Renaissance), statistiques et succès. */
    public boolean vanillaExtras = true;
    public int vanillaRewardEmeralds = 4;
    public int vanillaRewardKeys = 1;
    public int vanillaRewardPr = 2;
    /** Raid vanilla : les pillards ouvrent les portes (et les brisent). */
    public boolean vanillaOpenDoors = true;
    public boolean vanillaBreakDoors = true;
    /** Délai minimal (secondes) entre deux invasions du mod. */
    public int cooldownSeconds = 300;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static InvasionGlobal current;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("invasions.json");
    }

    public static synchronized InvasionGlobal get() {
        if (current == null) reload();
        return current;
    }

    public static synchronized void reload() {
        Path f = file();
        InvasionGlobal loaded = null;
        try {
            if (Files.exists(f)) {
                loaded = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), InvasionGlobal.class);
            }
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[Invasion] Lecture de {} impossible : {}", f, e.getMessage());
        }
        if (loaded == null) {
            loaded = new InvasionGlobal();
            try {
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(loaded), StandardCharsets.UTF_8);
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.warn("[Invasion] Écriture de {} impossible : {}", f, e.getMessage());
            }
        }
        current = loaded;
    }
}
