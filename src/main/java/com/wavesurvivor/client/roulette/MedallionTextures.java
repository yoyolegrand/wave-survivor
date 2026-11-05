package com.wavesurvivor.client.roulette;

import com.mojang.blaze3d.platform.NativeImage;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.roulette.RouletteStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MÉDAILLONS DU COFFRE ROULETTE (1.9) : compose en texture dynamique 16 × 16 (une par combinaison, mise en cache) la plaque sombre, l'anneau teinté à la
 * couleur d'accent, puis chaque couche de symbole (masque blanc teinté de sa couleur) avec un léger relief. La première couche reçoit une ombre
 * portée d'un pixel (bas droite), les suivantes un contour sombre d'un pixel pour se détacher du fond.
 */
@OnlyIn(Dist.CLIENT)
public final class MedallionTextures {

    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final Map<String, NativeImage> SOURCES = new HashMap<>();
    private static int counter = 0;

    private static final int OUTLINE = 0xFF1E1824;
    public static final ResourceLocation BASE = new ResourceLocation(WaveSurvivorMod.MODID, "textures/block/roulette/medallion_base.png");

    private MedallionTextures() {}

    /** Libère les textures composées et les sources lues (rechargement des ressources). */
    public static void clear() {
        for (ResourceLocation rl : CACHE.values()) Minecraft.getInstance().getTextureManager().release(rl);
        CACHE.clear();
        for (NativeImage img : SOURCES.values()) if (img != null) img.close();
        SOURCES.clear();
    }

    /** Texture du médaillon pour ces couches (du fond vers le dessus) et cette couleur d'accent. */
    public static ResourceLocation get(List<RouletteStyle.Layer> layers, int accent, Map<String, List<String>> custom) {
        StringBuilder key = new StringBuilder().append(accent);
        for (RouletteStyle.Layer l : layers) {
            key.append('|').append(l.symbol).append(l.small ? ":s:" : ":l:").append(l.color);
            if (l.symbol.startsWith("custom:") && custom != null) {
                List<String> rows = custom.get(l.symbol.substring(7));
                if (rows != null) key.append(String.join("/", rows));
            }
        }
        String k = key.toString();
        ResourceLocation rl = CACHE.get(k);
        if (rl != null) return rl;
        try {
            NativeImage img = compose(layers, accent, custom);
            rl = new ResourceLocation(WaveSurvivorMod.MODID, "roulette_medallion/m" + (counter++));
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Roulette] Médaillon illisible : {}", e.getMessage());
            rl = BASE;
        }
        CACHE.put(k, rl);
        return rl;
    }

    // ─── Lecture des textures sources ───

    private static NativeImage source(String path) {
        if (SOURCES.containsKey(path)) return SOURCES.get(path);
        NativeImage img = null;
        var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(WaveSurvivorMod.MODID, path));
        if (res.isPresent()) {
            try (InputStream in = res.get().open()) {
                img = NativeImage.read(in);
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.warn("[Roulette] Texture illisible {} : {}", path, e.getMessage());
            }
        }
        SOURCES.put(path, img);
        return img;
    }

    // Couleurs en ARGB (a, r, g, b) pour les calculs ; NativeImage stocke en ABGR.
    private static int argbAt(NativeImage img, int x, int y) {
        int c = img.getPixelRGBA(x, y);
        int a = c >>> 24, b = (c >> 16) & 255, g = (c >> 8) & 255, r = c & 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int toAbgr(int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /** « src » posé sur « dst » (mélange alpha classique). */
    private static int over(int dst, int src) {
        int sa = src >>> 24;
        if (sa == 255) return src;
        if (sa == 0) return dst;
        int da = dst >>> 24;
        float a = sa / 255f, d = da / 255f * (1 - a), out = a + d;
        if (out <= 0) return 0;
        int r = Math.round((((src >> 16) & 255) * a + ((dst >> 16) & 255) * d) / out);
        int g = Math.round((((src >> 8) & 255) * a + ((dst >> 8) & 255) * d) / out);
        int b = Math.round(((src & 255) * a + (dst & 255) * d) / out);
        return (Math.round(out * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int scale(int rgb, float f) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * f)), g = Math.min(255, Math.round(((rgb >> 8) & 255) * f)), b = Math.min(255, Math.round((rgb & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ─── Composition ───

    private static NativeImage compose(List<RouletteStyle.Layer> layers, int accent, Map<String, List<String>> custom) {
        int[][] px = new int[16][16];
        NativeImage base = source("textures/block/roulette/medallion_base.png");
        NativeImage ring = source("textures/block/roulette/medallion_ring.png");
        if (base != null) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px[x][y] = argbAt(base, x, y);
        if (ring != null) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int c = argbAt(ring, x, y);
                if ((c >>> 24) == 0) continue;
                float gray = ((c >> 16) & 255) / 255f; // anneau en niveaux de gris : multiplié par l'accent
                int tinted = (c & 0xFF000000) | (scale(accent, gray) & 0xFFFFFF);
                px[x][y] = over(px[x][y], tinted);
            }
        }
        for (int i = 0; i < layers.size(); i++) {
            RouletteStyle.Layer l = layers.get(i);
            boolean[][] mask = mask(l, custom);
            // première couche : ombre portée en bas à droite ; suivantes : contour sombre de 1 px (4 voisins)
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                if (mask[x][y]) continue;
                if (i == 0) {
                    if (x > 0 && y > 0 && mask[x - 1][y - 1]) px[x][y] = over(px[x][y], 0x80000000);
                } else if ((x > 0 && mask[x - 1][y]) || (x < 15 && mask[x + 1][y]) || (y > 0 && mask[x][y - 1]) || (y < 15 && mask[x][y + 1])) {
                    px[x][y] = OUTLINE;
                }
            }
            // remplissage avec un léger relief (bord haut gauche plus clair, bord bas droite plus sombre)
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                if (!mask[x][y]) continue;
                float f = 1f;
                if (x == 0 || y == 0 || !mask[x - 1][y - 1]) f = 1.15f;
                if (x == 15 || y == 15 || !mask[x + 1][y + 1]) f = 0.85f;
                px[x][y] = scale(l.color, f);
            }
        }
        NativeImage out = new NativeImage(16, 16, true);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) out.setPixelRGBA(x, y, toAbgr(px[x][y]));
        return out;
    }

    /** Masque 16 × 16 d'une couche : symbole de la bibliothèque (masque blanc déjà centré) ou dessin de joueur (grille 10 × 10). */
    private static boolean[][] mask(RouletteStyle.Layer l, Map<String, List<String>> custom) {
        boolean[][] m = new boolean[16][16];
        if (l.symbol.startsWith("custom:")) {
            List<String> rows = custom == null ? null : custom.get(l.symbol.substring(7));
            if (rows == null) return m;
            if (!l.small) { // grand : grille 10 × 10 centrée
                for (int y = 0; y < 10 && y < rows.size(); y++) for (int x = 0; x < 10 && x < rows.get(y).length(); x++)
                    if (rows.get(y).charAt(x) == '#') m[x + 3][y + 3] = true;
            } else {       // petit : même dessin réduit à 6 × 6
                for (int y = 0; y < 6; y++) for (int x = 0; x < 6; x++) {
                    int sx = x * 10 / 6, sy = y * 10 / 6;
                    if (sy < rows.size() && sx < rows.get(sy).length() && rows.get(sy).charAt(sx) == '#') m[x + 5][y + 5] = true;
                }
            }
            return m;
        }
        NativeImage img = source("textures/roulette_symbols/" + (l.small ? "small" : "large") + "/" + l.symbol + ".png");
        if (img == null) return m;
        for (int y = 0; y < 16 && y < img.getHeight(); y++) for (int x = 0; x < 16 && x < img.getWidth(); x++) m[x][y] = (argbAt(img, x, y) >>> 24) >= 128;
        return m;
    }
}
