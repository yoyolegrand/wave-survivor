package com.wavesurvivor.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Matrix4f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 2.0 — Ciel de la Faille (dimension wavesurvivor:rift), textures de Claude chat (docs/faille/ciel/CIEL_FAILLE.md).
 * Calques, dans l'ordre : fond en cube (sky_&lt;phase&gt;.png), tourbillon additif qui tourne lentement (rift_&lt;phase&gt;_glow.png),
 * cœur de la Faille fixe dans le ciel du côté du Titan (rift_&lt;phase&gt;.png, « fente », pulse à peine), puis l'anneau d'aurores
 * teinté et additif qui défile (aurora.png), dessiné après la Faille pour passer devant elle (2.1 : fiche docs/faille/v2_fente_cassure).
 * La phase vient du serveur ({@link com.wavesurvivor.network.RiftSkyPacket}) ; fondu de 100 ticks entre deux phases (fond, aurores,
 * Faille et brouillard). Option client : config/wavesurvivor/client_options.json › {"riftSky": false} = fond Horde seul, sans animation.
 */
@OnlyIn(Dist.CLIENT)
public class RiftSkyEffects extends DimensionSpecialEffects {

    public static final ResourceLocation ID = new ResourceLocation(WaveSurvivorMod.MODID, "rift");

    // Réglages par phase (fiche § 3) : horde, siege, contre, titan, victoire
    static final String[] PHASES = {"horde", "siege", "contre", "titan", "victoire"};
    private static final int[] AURORA_RGB = {0x3CDCC8, 0xAA50F0, 0x50F0DC, 0xD23C82, 0x78FAE6};
    private static final float[] AURORA_K = {0.35f, 0.60f, 0.80f, 0.55f, 0.55f};
    private static final float[] AURORA_SPEED = {0.0005f, 0.0010f, 0.0020f, 0.0015f, 0.0008f};
    private static final int[] FOG_RGB = {0x26123E, 0x1B0A34, 0x220E3C, 0x380A24, 0x323F6E};

    private static final ResourceLocation[] SKY = new ResourceLocation[5], RIFT = new ResourceLocation[5], SWIRL = new ResourceLocation[5];
    private static final ResourceLocation AURORA = tex("aurora");
    static {
        for (int i = 0; i < 5; i++) {
            SKY[i] = tex("sky_" + PHASES[i]);
            RIFT[i] = tex("rift_" + PHASES[i]);
            SWIRL[i] = tex("rift_" + PHASES[i] + "_glow");
        }
    }

    private static ResourceLocation tex(String n) {
        return new ResourceLocation(WaveSurvivorMod.MODID, "textures/environment/rift/" + n + ".png");
    }

    /** Durée des fondus entre deux phases (ticks). */
    private static final double FADE = 100.0;
    // Distances (blocs) : sous le plan lointain même à 2 chunks de distance de rendu
    private static final float CUBE = 50f, AURORA_R = 40f, RIFT_R = 45f;
    // La Faille : 44° de large, 60° de haut, centrée à +36° de tangage
    private static final float RIFT_W = 44f, RIFT_H = 60f, RIFT_PITCH = 36f;
    // Le tourbillon : zone carrée de 60° × 60° autour du même centre, 1 tour en 4 800 ticks (antihoraire), pulsation de 120 ticks
    private static final float SWIRL_SIZE = 60f;
    private static final double SWIRL_TURN = 4800.0, SWIRL_PULSE = 120.0;

    // État côté client
    private static int cur = 0, prev = 0;
    private static double changeAt = -1e9;   // temps de jeu (ticks) du dernier changement de phase
    private static float riftYaw = 0f;       // lacet de la Faille (0 = sud)
    private static double auroraU = 0, lastT = -1;
    private static Boolean animated = null;  // option client (relue à chaque changement de monde)
    private static ClientLevel lastLevel = null;

    public RiftSkyEffects() {
        super(Float.NaN, false, SkyType.NONE, true, false);   // comme l'End : pas de nuages, éclairage constant
    }

    // ─── Réception de la phase ───

    public static void receive(String phase, boolean instant, float yaw) {
        int idx = 0;
        for (int i = 0; i < PHASES.length; i++) if (PHASES[i].equalsIgnoreCase(phase)) idx = i;
        riftYaw = yaw;
        ClientLevel l = Minecraft.getInstance().level;
        if (instant || l == null) {
            cur = prev = idx;
            changeAt = -1e9;
            return;
        }
        if (idx == cur) return;
        double now = l.getGameTime();
        prev = fade(now) < 0.5 ? prev : cur;   // en plein fondu : on repart de la phase la plus visible
        cur = idx;
        changeAt = now;
    }

    /** Avancement du fondu 0 → 1 (smoothstep). */
    private static float fade(double now) {
        double x = Math.max(0, Math.min(1, (now - changeAt) / FADE));
        return (float) (x * x * (3 - 2 * x));
    }

    private static boolean animated() {
        Boolean a = animated;
        if (a != null) return a;
        a = true;
        Path f = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("client_options.json");
        try {
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("riftSky")) a = o.get("riftSky").getAsBoolean();
            }
        } catch (Exception ignored) { }
        animated = a;
        return a;
    }

    private static boolean inRift() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().location().equals(ID);
    }

    // ─── Effets de dimension ───

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return color;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        if (level != lastLevel) {   // nouveau monde : relit l'option, repart les aurores de zéro
            lastLevel = level;
            animated = null;
            lastT = -1;
        }
        double now = level.getGameTime() + partialTick;
        boolean anim = animated();
        Matrix4f m = poseStack.last().pose();

        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        if (!anim) {   // option désactivée : fond Horde seul
            cube(m, SKY[0], 1f);
        } else {
            float f = fade(now);
            // 1. Fond : l'ancien, puis le nouveau par-dessus
            if (f < 1f) cube(m, SKY[prev], 1f);
            cube(m, SKY[cur], f);

            // 2. Tourbillon (additif) : tourne lentement, luminosité ±10 % sur 120 ticks, fondu entre les deux textures
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            double turn = 2 * Math.PI * (now % SWIRL_TURN) / SWIRL_TURN;
            float sb = (float) (1 + 0.10 * Math.sin(2 * Math.PI * now / SWIRL_PULSE));
            if (f < 1f) swirl(m, SWIRL[prev], turn, sb, 1f - f);
            swirl(m, SWIRL[cur], turn, sb, f);
            RenderSystem.defaultBlendFunc();

            // 3. Cœur de la Faille (alpha normal) : pulsation discrète (échelle ±1,5 %, luminosité ±6 %, 80 ticks)
            double w = Math.sin(2 * Math.PI * now / 80.0);
            float scale = (float) (1 + 0.015 * w), bright = (float) (1 + 0.06 * w);
            if (f < 1f) rift(m, RIFT[prev], scale, bright, 1f - f);
            rift(m, RIFT[cur], scale, bright, f);

            // 4. Aurores (additives), APRÈS la Faille pour passer devant elle : teinte, intensité et vitesse mêlées entre les deux phases
            double dt = lastT < 0 ? 0 : Math.max(0, Math.min(10, now - lastT));
            lastT = now;
            float speed = lerp(AURORA_SPEED[prev], AURORA_SPEED[cur], f);
            auroraU = (auroraU + speed * dt) % 1.0;
            float k = lerp(AURORA_K[prev], AURORA_K[cur], f);
            float[] c = lerpRgb(AURORA_RGB[prev], AURORA_RGB[cur], f);
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            aurora(m, c[0] * k, c[1] * k, c[2] * k);
            RenderSystem.defaultBlendFunc();
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        return true;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float[] lerpRgb(int a, int b, float t) {
        return new float[]{
                lerp((a >> 16 & 255) / 255f, (b >> 16 & 255) / 255f, t),
                lerp((a >> 8 & 255) / 255f, (b >> 8 & 255) / 255f, t),
                lerp((a & 255) / 255f, (b & 255) / 255f, t)};
    }

    // Faces du cube (fiche § 2) : colonne, rangée de l'atlas 3 × 2, centre, direction de u (droite), direction de v (bas)
    private static final int[][] FACES = {
            {0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 1},     // haut
            {1, 0, 0, -1, 0, 1, 0, 0, 0, 0, -1},   // bas
            {2, 0, 0, 0, -1, -1, 0, 0, 0, -1, 0},  // nord
            {0, 1, 0, 0, 1, 1, 0, 0, 0, -1, 0},    // sud
            {1, 1, 1, 0, 0, 0, 0, -1, 0, -1, 0},   // est
            {2, 1, -1, 0, 0, 0, 0, 1, 0, -1, 0},   // ouest
    };

    /** Fond en cube centré sur la caméra : chaque point (u, v) de la case vise centre + u·droite + v·bas, comme à la génération. */
    private static void cube(Matrix4f m, ResourceLocation tex, float alpha) {
        if (alpha <= 0.002f) return;
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        float in = 0.5f / 768f, inV = 0.5f / 512f;   // demi-pixel : pas de couture entre les cases
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        int[][] corners = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        for (int[] fc : FACES) {
            for (int[] uv : corners) {
                int u = uv[0], v = uv[1];
                float x = (fc[2] + u * fc[5] + v * fc[8]) * CUBE;
                float y = (fc[3] + u * fc[6] + v * fc[9]) * CUBE;
                float z = (fc[4] + u * fc[7] + v * fc[10]) * CUBE;
                float tu = (fc[0] + (u + 1) / 2f) / 3f + (u < 0 ? in : -in);
                float tv = (fc[1] + (v + 1) / 2f) / 2f + (v < 0 ? inV : -inV);
                bb.vertex(m, x, y, z).uv(tu, tv).endVertex();
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    /** Direction (lacet 0 = sud +Z, 90 = est +X ; tangage vers le haut) × rayon. */
    private static float[] dir(double yawDeg, double pitchDeg, float r) {
        double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
        return new float[]{(float) (Math.cos(p) * Math.sin(y) * r), (float) (Math.sin(p) * r), (float) (Math.cos(p) * Math.cos(y) * r)};
    }

    /** Anneau d'aurores (cylindre ouvert) de +18° à +50° de tangage, texture enroulée une fois sur 360° et décalée de auroraU. */
    private static void aurora(Matrix4f m, float r, float g, float b) {
        RenderSystem.setShaderTexture(0, AURORA);
        RenderSystem.setShaderColor(r, g, b, 1f);
        float yTop = (float) (AURORA_R * Math.tan(Math.toRadians(50))), yBot = (float) (AURORA_R * Math.tan(Math.toRadians(18)));
        int n = 64;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            float x0 = (float) (Math.sin(a0) * AURORA_R), z0 = (float) (Math.cos(a0) * AURORA_R);
            float x1 = (float) (Math.sin(a1) * AURORA_R), z1 = (float) (Math.cos(a1) * AURORA_R);
            float u0 = (float) ((double) i / n + auroraU), u1 = (float) ((double) (i + 1) / n + auroraU);
            bb.vertex(m, x0, yTop, z0).uv(u0, 0f).endVertex();
            bb.vertex(m, x0, yBot, z0).uv(u0, 1f).endVertex();
            bb.vertex(m, x1, yBot, z1).uv(u1, 1f).endVertex();
            bb.vertex(m, x1, yTop, z1).uv(u1, 0f).endVertex();
        }
        BufferUploader.drawWithShader(bb.end());
    }

    /**
     * La Faille, fixe dans le ciel (pas un panneau qui suit le regard) : grille posée sur la sphère pour que ses angles
     * correspondent exactement à la texture (colonne gauche = lacet −22°, rangée du haut = tangage +66°).
     */
    private static void rift(Matrix4f m, ResourceLocation tex, float scale, float bright, float alpha) {
        if (alpha <= 0.002f) return;
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(bright, bright, bright, alpha);
        int nx = 8, ny = 12;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < nx; i++) for (int j = 0; j < ny; j++) {
            float ua = (float) i / nx, ub = (float) (i + 1) / nx, va = (float) j / ny, vb = (float) (j + 1) / ny;
            riftVertex(bb, m, ua, va, scale);
            riftVertex(bb, m, ua, vb, scale);
            riftVertex(bb, m, ub, vb, scale);
            riftVertex(bb, m, ub, va, scale);
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void riftVertex(BufferBuilder bb, Matrix4f m, float u, float v, float scale) {
        double yaw = riftYaw + (u - 0.5) * RIFT_W * scale;
        double pitch = RIFT_PITCH + (0.5 - v) * RIFT_H * scale;
        float[] p = dir(yaw, pitch, RIFT_R);
        bb.vertex(m, p[0], p[1], p[2]).uv(u, v).endVertex();
    }

    /**
     * Le tourbillon : grille carrée posée sur la sphère autour du centre de la Faille. C'est la GÉOMÉTRIE qui tourne (le décalage
     * angulaire de chaque sommet est tourné de « turn » avant le calcul du lacet et du tangage) ; les UV restent fixes, la texture
     * étant fondue en cercle, ses coins noirs ne se voient jamais.
     */
    private static void swirl(Matrix4f m, ResourceLocation tex, double turn, float bright, float alpha) {
        if (alpha <= 0.002f) return;
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(bright, bright, bright, alpha);
        int n = 12;
        double cs = Math.cos(turn), sn = Math.sin(turn);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            float ua = (float) i / n, ub = (float) (i + 1) / n, va = (float) j / n, vb = (float) (j + 1) / n;
            swirlVertex(bb, m, ua, va, cs, sn);
            swirlVertex(bb, m, ua, vb, cs, sn);
            swirlVertex(bb, m, ub, vb, cs, sn);
            swirlVertex(bb, m, ub, va, cs, sn);
        }
        BufferUploader.drawWithShader(bb.end());
    }

    /** Sommet du tourbillon. Le lacet grandit vers la gauche de l'écran : (dy, dp) tourné ainsi = sens antihoraire vu du joueur. */
    private static void swirlVertex(BufferBuilder bb, Matrix4f m, float u, float v, double cs, double sn) {
        double dy = (u - 0.5) * SWIRL_SIZE, dp = (0.5 - v) * SWIRL_SIZE;
        double ry = dy * cs + dp * sn, rp = -dy * sn + dp * cs;
        float[] p = dir(riftYaw + ry, RIFT_PITCH + rp, RIFT_R);
        bb.vertex(m, p[0], p[1], p[2]).uv(u, v).endVertex();
    }

    // ─── Enregistrement (bus du mod) ───

    @Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterDimensionSpecialEffectsEvent e) {
            e.register(ID, new RiftSkyEffects());
        }
    }

    // ─── Brouillard (bus Forge) ───

    @Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Fog {
        private Fog() {}

        /** Couleur du brouillard de la phase (fondu de 100 ticks). */
        @SubscribeEvent
        public static void fogColor(ViewportEvent.ComputeFogColor e) {
            if (!inRift()) return;
            ClientLevel l = Minecraft.getInstance().level;
            float[] c;
            if (!animated()) {
                c = lerpRgb(FOG_RGB[0], FOG_RGB[0], 0f);
            } else {
                c = lerpRgb(FOG_RGB[prev], FOG_RGB[cur], fade(l.getGameTime() + e.getPartialTick()));
            }
            e.setRed(c[0]);
            e.setGreen(c[1]);
            e.setBlue(c[2]);
        }
    }
}
