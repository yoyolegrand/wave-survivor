package com.wavesurvivor.horde.pact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue des pactes de sang. Portage direct de pacte_blood.js (3 configs).
 */
public class BloodPactRegistry {

    private static final Map<String, BloodPactConfig> PACTS = new LinkedHashMap<>();

    // Sons partagés par les 3 pactes (identiques dans le JS)
    private static final BloodPactSounds STANDARD_SOUNDS = new BloodPactSounds(
            "minecraft:block.beacon.activate",
            "minecraft:block.note_block.hat",
            "minecraft:entity.player.levelup",
            "minecraft:entity.ghast.scream"
    );

    // Skin NBT du JS — copiés tels quels pour compat avec têtes déjà distribuées
    private static final String SKULL_DEMON =
            "SkullOwner:{Id:[I;-931882201,1852922,-1964216875,-2096013554]," +
            "Properties:{textures:[{Value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWM1MTZiZDllN2M1ZWRjYzM0OTRkNTQ1ZDlhNDdlZjM1NDk1ODdiM2I3YjY4ZDU0MzVjMGZmMjI2ZTlmIn19fQ==\"}]}}";
    private static final String SKULL_TEST =
            "SkullOwner:{Id:[I;873050575,-427930477,-1147135702,147569213]," +
            "Properties:{textures:[{Value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2Y2ODcyMGI5ZmE3ZjE0OWJmMzNlZWU1YTMzZWJiOGE5NWVjODhiOWI4MmVkMjQyYTI4MmUyNGMwMmIyZWUxMCJ9fX0=\"}]}}";
    private static final String SKULL_PACTE1 =
            "SkullOwner:{Id:[I;987093303,948129233,-2138981139,774123822]," +
            "Properties:{textures:[{Value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDkyZjA4YzEyOTQ4MjlkNDcxYThlMDEwOWEwNmZiNmFlNzE3ZTVmYWYzZTA4MDg0MDhhNjZkODg5MjI3ZGFjNyJ9fX0=\"}]}}";

    static {
        // =============== DEMON (§4§lCalice Démoniaque) ===============
        register(new BloodPactConfig(
                "demon", "Custom Head ID: 7103", "§4§lCalice Démoniaque", SKULL_DEMON,
                BloodPactConfig.PactType.RANDOM, true, true, 3,
                "minecraft:soul_fire_flame", STANDARD_SOUNDS,
                List.of(
                        BloodPactEffect.pack("⚡ Pacte Frénésie du Diablotin", true, 0.2, List.of(
                                new BloodPactSubEffect("minecraft:speed", 2, 6000, true),
                                new BloodPactSubEffect("minecraft:haste", 2, 6000, true),
                                new BloodPactSubEffect("minecraft:weakness", 1, 6000, false)
                        )),
                        BloodPactEffect.pack("💪 Pacte de Démence", true, 0.2, List.of(
                                new BloodPactSubEffect("minecraft:strength", 3, 6000, true),
                                new BloodPactSubEffect("minecraft:nausea", 0, 300, false),
                                new BloodPactSubEffect("minecraft:slowness", 2, 6000, false)
                        )),
                        BloodPactEffect.pack("🛡️ Pacte d'Obsidienne", true, 0.2, List.of(
                                new BloodPactSubEffect("minecraft:resistance", 2, 6000, true),
                                new BloodPactSubEffect("minecraft:absorption", 2, 6000, true),
                                new BloodPactSubEffect("minecraft:slowness", 2, 6000, false)
                        )),
                        BloodPactEffect.pack("💚 Pacte de Chair Insatiable", true, 0.2, List.of(
                                new BloodPactSubEffect("minecraft:regeneration", 2, 6000, true),
                                new BloodPactSubEffect("minecraft:hunger", 2, 6000, false)
                        )),
                        BloodPactEffect.pack("🌀 Pacte de Chair Viciée", true, 0.2, List.of(
                                new BloodPactSubEffect("minecraft:saturation", 3, 6000, true),
                                new BloodPactSubEffect("minecraft:unluck", 3, 6000, false),
                                new BloodPactSubEffect("minecraft:slowness", 0, 6000, false)
                        ))
                )
        ));

        // =============== TEST (§4§lCalice d'ange) ===============
        register(new BloodPactConfig(
                "test", "Custom Head ID: 62731", "§4§lCalice d'ange", SKULL_TEST,
                BloodPactConfig.PactType.RANDOM, true, true, 3,
                "minecraft:soul_fire_flame", STANDARD_SOUNDS,
                List.of(
                        BloodPactEffect.pack("⚡ Pack Célérité", true, 0.4, List.of(
                                new BloodPactSubEffect("minecraft:speed", 0, 600, true),
                                new BloodPactSubEffect("minecraft:haste", 0, 600, true),
                                new BloodPactSubEffect("minecraft:weakness", 0, 600, false)
                        )),
                        BloodPactEffect.pack("💪 Pack Force", true, 0.3, List.of(
                                new BloodPactSubEffect("minecraft:strength", 1, 600, true),
                                new BloodPactSubEffect("minecraft:nausea", 0, 600, false)
                        )),
                        BloodPactEffect.pack("💚 Pack Régénération", true, 0.1, List.of(
                                new BloodPactSubEffect("minecraft:regeneration", 1, 400, true),
                                new BloodPactSubEffect("minecraft:hunger", 0, 600, false)
                        )),
                        BloodPactEffect.pack("🌀 Pack Chaos", true, 0.2, List.of(
                                new BloodPactSubEffect("minecraft:speed", 1, 400, true),
                                new BloodPactSubEffect("minecraft:strength", 0, 400, true),
                                new BloodPactSubEffect("minecraft:poison", 0, 200, false),
                                new BloodPactSubEffect("minecraft:nausea", 0, 300, false)
                        ))
                )
        ));

        // =============== PACTE1 (§4§lCalice de Sang) ===============
        register(new BloodPactConfig(
                "Pacte1", "Custom Head ID: 33330", "§4§lCalice de Sang", SKULL_PACTE1,
                BloodPactConfig.PactType.RANDOM, true, true, 3,
                "minecraft:soul_fire_flame", STANDARD_SOUNDS,
                List.of(
                        BloodPactEffect.single("minecraft:speed", 0, 1200, true, 0.2, "Vitesse"),
                        BloodPactEffect.single("minecraft:strength", 0, 1200, true, 0.2, "Force"),
                        BloodPactEffect.single("minecraft:regeneration", 0, 1200, true, 0.15, "Régénération"),
                        BloodPactEffect.single("minecraft:slowness", 0, 1200, false, 0.2, "Lenteur"),
                        BloodPactEffect.single("minecraft:weakness", 0, 1200, false, 0.15, "Faiblesse"),
                        BloodPactEffect.single("minecraft:poison", 0, 1200, false, 0.1, "Poison")
                )
        ));
    }

    private static void register(BloodPactConfig cfg) {
        PACTS.put(cfg.pactName(), cfg);
    }

    public static BloodPactConfig get(String pactName) {
        return PACTS.get(pactName);
    }

    /** Cherche un pacte dont le headCustomId apparaît dans la string NBT donnée. */
    public static BloodPactConfig findByNbtString(String nbtString) {
        if (nbtString == null) return null;
        for (BloodPactConfig cfg : PACTS.values()) {
            if (nbtString.contains(cfg.headCustomId())) return cfg;
        }
        return null;
    }

    public static Collection<BloodPactConfig> all() { return PACTS.values(); }
    public static List<String> allNames() { return new ArrayList<>(PACTS.keySet()); }
    public static int count() { return PACTS.size(); }
}
