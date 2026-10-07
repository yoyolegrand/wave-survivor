package com.wavesurvivor.altar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Définition "autel" de chaque horde : recette de liaison + zone (claim) autour de l'autel.
 * Fichier : config/wavesurvivor/altar_recipes.json (séparé du config.json base44 → pas écrasé par un export)
 *
 *   {
 *     "Horde des profondeurs": {
 *       "ingredients": [ { "item": "minecraft:heart_of_the_sea", "count": 1 } ],
 *       "zone": { "radius": 8, "floor": [ { "block": "minecraft:prismarine", "weight": 60 }, ... ] }
 *     }
 *   }
 *
 * Ancien format accepté en lecture : { "Horde": [ {item,count}, ... ] }
 */
public class AltarRecipes {

    public static class Ingredient {
        public String item;
        public int count = 1;

        public Ingredient() {}
        public Ingredient(String item, int count) { this.item = item; this.count = count; }

        public Item resolve() {
            try {
                Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(item));
                return it == Items.AIR ? null : it;
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static class FloorBlock {
        public String block;
        public int weight = 1;

        public FloorBlock() {}
        public FloorBlock(String block, int weight) { this.block = block; this.weight = weight; }

        public BlockState resolve() {
            try {
                Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(block));
                return b == null || b == Blocks.AIR ? null : b.defaultBlockState();
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static class Zone {
        public int radius = 8;
        public List<FloorBlock> floor = new ArrayList<>();
        /**
         * Sol DESSINÉ à la main (éditeur « 🎨 Dessiner le sol ») : palette de blocs + grille carrée centrée sur l'autel.
         * Chaque caractère de {@code pattern} : '.' = vide (tirage pondéré du sol classique), '0'-'9' puis 'a'-'z' = index dans la palette.
         */
        public List<String> palette = new ArrayList<>();
        public List<String> pattern = new ArrayList<>();
        private transient List<BlockState> paletteCache;

        // ─── Disposition des marchands / roulette chests (entre les vagues) ───
        /** Placement automatique en cercle (coffres ~70% du rayon, marchands ~45%). */
        public boolean autoLayout = true;
        /** Nombre max de coffres en auto (un par config de coffre). */
        public int maxChests = 8;
        /** Configs de coffre jamais posées en auto. */
        public List<String> excludedChests = new ArrayList<>(List.of("coffre_ender"));
        /** Emplacements manuels, relatifs à l'autel (prioritaires sur l'auto). */
        public List<Slot> slots = new ArrayList<>();

        /** Tirage pondéré d'un bloc de sol (null si aucun bloc valide). */
        public BlockState pick(RandomSource rnd) {
            int total = 0;
            for (FloorBlock f : floor) if (f.weight > 0 && f.resolve() != null) total += f.weight;
            if (total <= 0) return null;
            int r = rnd.nextInt(total);
            for (FloorBlock f : floor) {
                BlockState st = f.weight > 0 ? f.resolve() : null;
                if (st == null) continue;
                r -= f.weight;
                if (r < 0) return st;
            }
            return null;
        }

        public boolean isActive() {
            return radius > 0 && ((floor != null && !floor.isEmpty()) || hasPattern());
        }

        public boolean hasPattern() {
            return pattern != null && !pattern.isEmpty() && palette != null && !palette.isEmpty();
        }

        /** Demi-côté du dessin (0 si aucun dessin). */
        public int patternHalf() {
            return hasPattern() ? pattern.size() / 2 : 0;
        }

        /** Bloc dessiné à (dx, dz) par rapport à l'autel, ou null si la case est vide / hors du dessin. */
        public BlockState patternAt(int dx, int dz) {
            if (!hasPattern()) return null;
            int half = pattern.size() / 2;
            int row = dz + half;
            if (row < 0 || row >= pattern.size()) return null;
            String line = pattern.get(row);
            int col = dx + half;
            if (line == null || col < 0 || col >= line.length()) return null;
            int idx = Character.digit(line.charAt(col), 36);
            if (idx < 0) return null;
            if (paletteCache == null) {
                paletteCache = new ArrayList<>();
                for (String id : palette) paletteCache.add(new FloorBlock(id, 1).resolve());
            }
            return idx < paletteCache.size() ? paletteCache.get(idx) : null;
        }

        /** Bloc à poser à (dx, dz) : le dessin d'abord, sinon le tirage pondéré (seulement dans le cercle du rayon). */
        public BlockState targetAt(int dx, int dz, RandomSource rnd) {
            BlockState drawn = patternAt(dx, dz);
            if (drawn != null) return drawn;
            if (dx * dx + dz * dz > (radius + 0.5) * (radius + 0.5)) return null;
            return pick(rnd);
        }
    }

    /** Emplacement manuel dans la zone : "chest" (avec configKey) ou "merchant". Offset relatif à l'autel. */
    public static class Slot {
        public String type;        // "chest" | "merchant"
        public String configKey;   // pour type=chest
        /** Pour type=merchant : nom du marchand (écran « 📍 Disposition ») ; vide = premier marchand libre. */
        public String name;
        public int dx, dy, dz;

        public Slot() {}
        public Slot(String type, String configKey, int dx, int dy, int dz) {
            this.type = type; this.configKey = configKey; this.dx = dx; this.dy = dy; this.dz = dz;
        }
    }

    public static class HordeAltarDef {
        public List<Ingredient> ingredients = new ArrayList<>();
        public Zone zone;
        /** Style du monolithe appliqué à la liaison (couleur + particules). */
        public Style style;
        /** Défense du Monolithe (le monolithe devient une cible à protéger). */
        public Defense defense;
        /** Brèches infernales (portails de renforts à détruire). */
        public Breaches breaches;
        /** Anomalies gravitationnelles (puits de lévitation sous les joueurs). */
        public Anomalies anomalies;
        /** Variantes lançables depuis l'autel lié à cette horde (ex : Vanilla / Moddée). Vide = la horde seule. */
        public List<Variant> variants;
    }

    public static class Variant {
        public String label;  // "Vanilla", "Moddée"...
        public String horde;  // nom de la horde lancée (HordeConfigMulti)
        public Variant() {}
        public Variant(String label, String horde) { this.label = label; this.horde = horde; }
    }

    /** Variantes de la horde liée (au moins une : elle-même). */
    public static List<Variant> getVariants(String hordeName) {
        HordeAltarDef d = DEFS.get(hordeName);
        if (d != null && d.variants != null && !d.variants.isEmpty()) {
            List<Variant> out = new ArrayList<>();
            for (Variant v : d.variants) if (v != null && v.horde != null && !v.horde.isBlank()) out.add(v);
            if (!out.isEmpty()) return out;
        }
        return List.of(new Variant("Standard", hordeName));
    }

    /** true si cette horde n'est qu'une variante d'une autre (→ pas proposée seule à la liaison). */
    public static boolean isVariantOf(String hordeName) {
        for (Map.Entry<String, HordeAltarDef> e : DEFS.entrySet()) {
            if (e.getKey().equalsIgnoreCase(hordeName) || e.getValue().variants == null) continue;
            for (Variant v : e.getValue().variants) if (v != null && hordeName.equalsIgnoreCase(v.horde)) return true;
        }
        return false;
    }

    /** Horde principale dont {@code hordeName} est une variante (« La Nécropole » pour « La Nécropole Moddée »), sinon null. */
    public static String parentOf(String hordeName) {
        if (hordeName == null) return null;
        for (Map.Entry<String, HordeAltarDef> e : DEFS.entrySet()) {
            if (e.getKey().equalsIgnoreCase(hordeName) || e.getValue().variants == null) continue;
            for (Variant v : e.getValue().variants) if (v != null && hordeName.equalsIgnoreCase(v.horde)) return e.getKey();
        }
        return null;
    }

    /** La horde et toutes ses variantes (terminer l'une compte pour la famille entière). */
    public static List<String> familyOf(String hordeName) {
        List<String> out = new ArrayList<>();
        if (hordeName == null) return out;
        String root = parentOf(hordeName);
        if (root == null) root = hordeName;
        out.add(root);
        HordeAltarDef d = null;
        for (Map.Entry<String, HordeAltarDef> e : DEFS.entrySet()) if (e.getKey().equalsIgnoreCase(root)) d = e.getValue();
        if (d != null && d.variants != null) {
            for (Variant v : d.variants) if (v != null && v.horde != null && !v.horde.equalsIgnoreCase(root)) out.add(v.horde);
        }
        return out;
    }

    /**
     * BRÈCHES INFERNALES : toutes les everyNWaves vagues, `count` Brèches s'ouvrent autour du point de horde.
     * Tant qu'elles sont ouvertes, elles font sortir des renforts (non comptés dans la vague).
     * Détruite = récompense ; encore ouverte en fin de vague = +buffPerOpen de dégâts pour la horde (cumulable).
     */
    public static class Breaches {
        public boolean enabled = false;
        public int everyNWaves = 3;
        public int count = 3;
        /** Distance au centre (blocs). 0 = 80 % du rayon de la zone de l'autel (ou 9). */
        public double distance = 0;
        /** Bonus de dégâts des monstres par Brèche restée ouverte (0.10 = +10 %). */
        public double buffPerOpen = 0.10;
        /** TYPES de Brèches : chaque Brèche ouverte tire son type au hasard (selon le poids). */
        public List<BreachType> types = new ArrayList<>();

        // ─── Ancien format (une seule sorte de Brèche) : converti automatiquement en un type ───
        public double health = 60;
        public int spawnIntervalSeconds = 15;
        public int spawnPerPulse = 2;
        public List<String> spawnEntities = new ArrayList<>(List.of(
                "minecraft:zombified_piglin", "minecraft:zombified_piglin", "minecraft:hoglin", "minecraft:blaze"));
        public int maxAlivePerBreach = 6;
        public String rewardItem = "minecraft:emerald";
        public int rewardCount = 4;
        public String style = "infernale";
        public int color = -1;

        /** Types réellement utilisés (liste des types, ou un type construit depuis l'ancien format). */
        public List<BreachType> effectiveTypes() {
            if (types != null && !types.isEmpty()) return types;
            BreachType t = new BreachType();
            t.style = style;
            t.color = color;
            t.health = health;
            t.spawnIntervalSeconds = spawnIntervalSeconds;
            t.spawnPerPulse = spawnPerPulse;
            t.maxAlive = maxAlivePerBreach;
            t.mobs = new ArrayList<>();
            java.util.Map<String, Integer> w = new java.util.LinkedHashMap<>();
            if (spawnEntities != null) for (String id : spawnEntities) if (id != null && !id.isBlank()) w.merge(id.trim(), 1, Integer::sum);
            w.forEach((id, n) -> { BreachMob m = new BreachMob(); m.entity = id; m.weight = n; t.mobs.add(m); });
            t.rewards = new ArrayList<>();
            if (rewardItem != null && !rewardItem.isBlank() && rewardCount > 0) {
                BreachReward r = new BreachReward();
                r.item = rewardItem; r.minQty = rewardCount; r.maxQty = rewardCount; r.chance = 100;
                t.rewards.add(r);
            }
            return List.of(t);
        }

        public BreachType pickType(java.util.Random rng) {
            List<BreachType> list = effectiveTypes();
            double total = 0;
            for (BreachType t : list) total += Math.max(0, t.weight);
            if (total <= 0) return list.get(0);
            double r = rng.nextDouble() * total;
            for (BreachType t : list) { r -= Math.max(0, t.weight); if (r < 0) return t; }
            return list.get(list.size() - 1);
        }
    }

    /** Un type de Brèche : apparence, résistance, monstres (pondérés) et récompenses. */
    public static class BreachType {
        /** Nom affiché au-dessus de la faille (vide = nom du style). */
        public String name = "";
        /** Probabilité relative de ce type. */
        public double weight = 1;
        /** Apparence : infernale, ames, end, abysses. */
        public String style = "infernale";
        /** Couleur de la faille (RGB) ; -1 = couleur du style. */
        public int color = -1;
        public double health = 60;
        public int spawnIntervalSeconds = 15;
        public int spawnPerPulse = 2;
        public int maxAlive = 6;
        public List<BreachMob> mobs = new ArrayList<>();
        public List<BreachReward> rewards = new ArrayList<>();

        public BreachMob pickMob(java.util.Random rng) {
            if (mobs == null || mobs.isEmpty()) return null;
            double total = 0;
            for (BreachMob m : mobs) total += Math.max(0, m.weight);
            if (total <= 0) return mobs.get(rng.nextInt(mobs.size()));
            double r = rng.nextDouble() * total;
            for (BreachMob m : mobs) { r -= Math.max(0, m.weight); if (r < 0) return m; }
            return mobs.get(mobs.size() - 1);
        }
    }

    /** Monstre de renfort : identifiant (vanilla, moddé ou entité custom) + poids. */
    public static class BreachMob {
        public String entity = "minecraft:zombie";
        public double weight = 1;
    }

    /** Récompense à la destruction d'une Brèche. */
    public static class BreachReward {
        public String item = "minecraft:emerald";
        public int minQty = 1;
        public int maxQty = 1;
        public double chance = 100;
    }

    /**
     * ANOMALIES : toutes les intervalSeconds, `count` anomalies apparaissent (moitié sous des joueurs, reste au hasard
     * dans la zone). Chacune tire son TYPE au hasard (selon le poids) : effets libres, dégâts, attraction/répulsion,
     * explosion finale, couleur du cercle. Les monstres ne sont jamais affectés.
     */
    public static class Anomalies {
        public boolean enabled = false;
        public int fromWave = 1;
        public int intervalSeconds = 20;
        public int count = 2;
        /** Rayon de la zone où tombent les anomalies « au hasard ». */
        public double zoneRadius = 12;
        public List<AnomalyType> types = new ArrayList<>();

        // ─── Ancien format : anomalie gravitationnelle unique ───
        public int warningTicks = 40;
        public int durationTicks = 60;
        public double radius = 3.0;
        public int levitationAmplifier = 1;

        public List<AnomalyType> effectiveTypes() {
            if (types != null && !types.isEmpty()) return types;
            AnomalyType t = new AnomalyType();
            t.warningTicks = warningTicks;
            t.durationTicks = durationTicks;
            t.radius = radius;
            AnomalyEffect e = new AnomalyEffect();
            e.effect = "minecraft:levitation";
            e.level = Math.max(0, levitationAmplifier) + 1;
            e.duration = 10;
            t.effects = new ArrayList<>(List.of(e));
            return List.of(t);
        }

        public AnomalyType pickType(java.util.Random rng) {
            List<AnomalyType> list = effectiveTypes();
            double total = 0;
            for (AnomalyType t : list) total += Math.max(0, t.weight);
            if (total <= 0) return list.get(0);
            double r = rng.nextDouble() * total;
            for (AnomalyType t : list) { r -= Math.max(0, t.weight); if (r < 0) return t; }
            return list.get(list.size() - 1);
        }
    }

    /** Un type d'anomalie : ses effets et son comportement. */
    public static class AnomalyType {
        public String name = "";
        public double weight = 1;
        /** Couleur du cercle (RGB). */
        public int color = 0xD940FF;
        public int warningTicks = 40;
        public int durationTicks = 60;
        public double radius = 3.0;
        /** Effets appliqués en continu aux joueurs dans le cercle actif. */
        public List<AnomalyEffect> effects = new ArrayList<>();
        /** Dégâts magiques par seconde dans le cercle actif. */
        public double damagePerSecond = 0;
        /** > 0 : attire vers le centre ; < 0 : repousse (force par tick). */
        public double pull = 0;
        /** Dégâts de l'explosion à la fin (0 = aucune). Ne casse pas de blocs. */
        public double burstDamage = 0;
    }

    public static class AnomalyEffect {
        public String effect = "minecraft:levitation";
        /** Niveau (1 = I). */
        public int level = 1;
        /** Durée appliquée à chaque rafraîchissement (ticks) ; l'effet est rafraîchi tant qu'on reste dedans. */
        public int duration = 40;
    }

    public static Anomalies getAnomalies(String hordeName) {
        HordeAltarDef d = DEFS.get(hordeName);
        return d != null && d.anomalies != null && d.anomalies.enabled ? d.anomalies : null;
    }

    /** Brèches actives d'une horde, ou null. */
    public static Breaches getBreaches(String hordeName) {
        HordeAltarDef d = DEFS.get(hordeName);
        return d != null && d.breaches != null && d.breaches.enabled ? d.breaches : null;
    }

    public static class Defense {
        public boolean enabled = false;
        public int maxHealth = 500;
        public String repairItem = "minecraft:bone_meal";
        public int repairAmount = 10;
        /** % d'intégrité minimum en fin de horde pour le bonus. */
        public int bonusThreshold = 75;
        public String bonusItem = "roulettechest:key_coffre_mysterieux";
        public int bonusCount = 1;
        /** Multiplicateur des dégâts des Profanateurs sur le monolithe (0.5 = moitié). */
        public double profanerDamageMultiplier = 0.5;

        // ─── Boutique d'améliorations du monolithe (pendant la horde, en émeraudes) ───
        public int upgradeMaxLevel = 5;
        public String upgradeCurrency = "minecraft:emerald";
        /** Renfort : +PV max par niveau. */
        public int hpPerLevel = 100;
        public int hpCostBase = 8;
        /** Régénération : PV rendus par seconde et par niveau. */
        public double regenPerLevel = 1.0;
        public int regenCostBase = 10;
        /** Blindage : % de réduction des dégâts par niveau. */
        public int armorPerLevel = 10;
        public int armorCostBase = 12;

        /** Coût du niveau suivant = base × (niveau actuel + 1). */
        public int cost(int type, int currentLevel) {
            int base = type == 0 ? hpCostBase : type == 1 ? regenCostBase : armorCostBase;
            return Math.max(0, base * (currentLevel + 1));
        }
    }

    /** Défense active d'une horde, ou null. */
    public static Defense getDefense(String hordeName) {
        HordeAltarDef d = DEFS.get(hordeName);
        return d != null && d.defense != null && d.defense.enabled ? d.defense : null;
    }

    public static Defense editDefense(String hordeName) {
        HordeAltarDef d = DEFS.computeIfAbsent(hordeName, k -> new HordeAltarDef());
        if (d.defense == null) d.defense = new Defense();
        return d.defense;
    }

    public static class Style {
        public String color = "red";       // DyeColor (red, orange, cyan...)
        public String particles = "ames";  // AltarParticles id

        public Style() {}
        public Style(String color, String particles) { this.color = color; this.particles = particles; }

        public net.minecraft.world.item.DyeColor dye() {
            net.minecraft.world.item.DyeColor d = net.minecraft.world.item.DyeColor.byName(color, null);
            return d != null ? d : net.minecraft.world.item.DyeColor.RED;
        }
    }

    /** Style d'une horde (ou null si aucun défini). */
    public static Style getStyle(String hordeName) {
        HordeAltarDef d = DEFS.get(hordeName);
        return d != null ? d.style : null;
    }

    public static Style editStyle(String hordeName) {
        HordeAltarDef d = DEFS.computeIfAbsent(hordeName, k -> new HordeAltarDef());
        if (d.style == null) d.style = new Style();
        return d.style;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Map<String, HordeAltarDef> DEFS = new LinkedHashMap<>();

    // ─── API Éditeur de Hordes ───

    /** Config d'autel d'une horde en JSON, ou "" si aucune. */
    public static String toJson(String hordeName) {
        HordeAltarDef d = DEFS.get(hordeName);
        return d == null ? "" : GSON.toJson(d);
    }

    /**
     * Écrit la config d'autel d'une horde depuis l'éditeur.
     * @param json "" = ne rien changer ; "{\"__delete\":true}" = supprimer ; sinon remplace.
     * @param oldName ancien nom de la horde (renommage : la config suit la horde)
     */
    public static void putFromEditor(String hordeName, String oldName, String json) {
        if (oldName != null && !oldName.isBlank() && !oldName.equals(hordeName) && DEFS.containsKey(oldName)) {
            HordeAltarDef moved = DEFS.remove(oldName);
            if (!DEFS.containsKey(hordeName)) DEFS.put(hordeName, moved);
        }
        if (json != null && !json.isBlank()) {
            if (json.contains("\"__delete\"")) {
                DEFS.remove(hordeName);
            } else {
                HordeAltarDef d = GSON.fromJson(json, HordeAltarDef.class);
                if (d != null) DEFS.put(hordeName, d);
            }
        }
        save();
    }

    public static void removeFromEditor(String hordeName) {
        if (DEFS.remove(hordeName) != null) save();
    }

    // ─── Chargement / sauvegarde ───

    public static void load() {
        Path file = filePath();
        try {
            if (!Files.exists(file)) {
                DEFS = defaults();
                save();
                WaveSurvivorMod.LOGGER.info("[AltarRecipes] Fichier créé avec les définitions par défaut.");
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, HordeAltarDef> out = new LinkedHashMap<>();
            boolean legacy = false;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                HordeAltarDef def;
                if (e.getValue().isJsonArray()) {
                    // Ancien format : directement la liste d'ingrédients
                    def = new HordeAltarDef();
                    def.ingredients = GSON.fromJson(e.getValue(), new TypeToken<List<Ingredient>>() {}.getType());
                    legacy = true;
                } else {
                    def = GSON.fromJson(e.getValue(), HordeAltarDef.class);
                }
                if (def.ingredients == null) def.ingredients = new ArrayList<>();
                out.put(e.getKey(), def);
            }
            // Migration : les hordes par défaut récupèrent leur zone / style si absents
            Map<String, HordeAltarDef> d = defaults();
            boolean migrated = false;
            for (Map.Entry<String, HordeAltarDef> e : out.entrySet()) {
                HordeAltarDef def0 = d.get(e.getKey());
                if (def0 == null) continue;
                if (e.getValue().zone == null) { e.getValue().zone = def0.zone; migrated = true; }
                if (e.getValue().style == null) { e.getValue().style = def0.style; migrated = true; }
            }
            DEFS = out;
            if (legacy || migrated) {
                save();
                WaveSurvivorMod.LOGGER.info("[AltarRecipes] Ancien format migré vers ingrédients + zone.");
            }
            WaveSurvivorMod.LOGGER.info("[AltarRecipes] {} définition(s) d'autel chargée(s).", DEFS.size());
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[AltarRecipes] Erreur load : {}", e.getMessage());
            DEFS = defaults();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(filePath().getParent());
            Files.writeString(filePath(), GSON.toJson(DEFS), StandardCharsets.UTF_8);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[AltarRecipes] Erreur save : {}", e.getMessage());
        }
    }

    private static Map<String, HordeAltarDef> defaults() {
        Map<String, HordeAltarDef> m = new LinkedHashMap<>();

        HordeAltarDef deep = new HordeAltarDef();
        deep.ingredients.add(new Ingredient("minecraft:heart_of_the_sea", 1));
        deep.zone = new Zone();
        deep.zone.radius = 8;
        deep.zone.floor.add(new FloorBlock("minecraft:prismarine", 30));
        deep.zone.floor.add(new FloorBlock("minecraft:sand", 70));
        m.put("Horde des profondeurs", deep);
        deep.style = new Style("cyan", "abysses");

        HordeAltarDef tuto = new HordeAltarDef();
        tuto.ingredients.add(new Ingredient("minecraft:iron_ingot", 1));
        tuto.zone = new Zone();
        tuto.zone.radius = 8;
        tuto.zone.floor.add(new FloorBlock("minecraft:mossy_cobblestone", 35));
        tuto.zone.floor.add(new FloorBlock("minecraft:cobblestone", 35));
        tuto.zone.floor.add(new FloorBlock("minecraft:stone", 30));
        m.put("Tuto", tuto);
        tuto.style = new Style("orange", "flammes");
        return m;
    }

    // ─── Accès ───

    /** Recette d'une horde, ou null si aucune (horde non liable via l'écran). */
    public static List<Ingredient> get(String hordeName) {
        HordeAltarDef d = DEFS.get(hordeName);
        return d == null || d.ingredients == null || d.ingredients.isEmpty() ? null : d.ingredients;
    }

    /** Zone d'une horde, ou null si aucune / inactive. */
    public static Zone getZone(String hordeName) {
        HordeAltarDef d = DEFS.get(hordeName);
        return d != null && d.zone != null && d.zone.isActive() ? d.zone : null;
    }

    /** Zone brute (même vide) pour l'édition admin ; créée si absente. */
    public static Zone editZone(String hordeName) {
        HordeAltarDef d = DEFS.computeIfAbsent(hordeName, k -> new HordeAltarDef());
        if (d.zone == null) d.zone = new Zone();
        if (d.zone.floor == null) d.zone.floor = new ArrayList<>();
        if (d.zone.slots == null) d.zone.slots = new ArrayList<>();
        if (d.zone.excludedChests == null) d.zone.excludedChests = new ArrayList<>();
        return d.zone;
    }

    public static Zone peekZone(String hordeName) {
        HordeAltarDef d = DEFS.get(hordeName);
        return d != null ? d.zone : null;
    }

    // ─── Inventaire ───

    /** Combien le joueur possède de cet item (inventaire principal + main secondaire). */
    public static int countInInventory(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (st.is(item)) n += st.getCount();
        for (ItemStack st : p.getInventory().offhand) if (st.is(item)) n += st.getCount();
        return n;
    }

    public static boolean hasAll(ServerPlayer p, List<Ingredient> recipe) {
        if (p.isCreative()) return true;
        for (Ingredient ing : recipe) {
            Item it = ing.resolve();
            if (it == null || countInInventory(p, it) < ing.count) return false;
        }
        return true;
    }

    /** Retire les ingrédients (à appeler après hasAll). Rien en créatif. */
    public static void consume(ServerPlayer p, List<Ingredient> recipe) {
        if (p.isCreative()) return;
        for (Ingredient ing : recipe) {
            Item it = ing.resolve();
            if (it == null) continue;
            int left = ing.count;
            List<ItemStack> all = new ArrayList<>(p.getInventory().items);
            all.addAll(p.getInventory().offhand);
            for (ItemStack st : all) {
                if (left <= 0) break;
                if (!st.is(it)) continue;
                int take = Math.min(left, st.getCount());
                st.shrink(take);
                left -= take;
            }
        }
        p.getInventory().setChanged();
        p.inventoryMenu.broadcastChanges();
    }

    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("altar_recipes.json");
    }
}
