package com.wavesurvivor.client.roulette;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.roulette.RouletteChestBlock;
import com.wavesurvivor.horde.roulette.RouletteAnim;
import com.wavesurvivor.horde.roulette.RouletteChestBlockEntity;
import com.wavesurvivor.horde.roulette.RouletteStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * RENDU DU COFFRE ROULETTE (1.9) : le coffre est dessiné à partir de chest_model.json (pavés), avec des textures en niveaux de gris teintées par
 * multiplication (bois, ferrures, liseré à la couleur d'accent, gemmes), les médaillons composés par {@link MedallionTextures}, et un couvercle qui pivote
 * autour de sa charnière arrière. Le liseré et les gemmes brillent et pulsent doucement (luminosité 0,8 à 1, période d'environ 2 s).
 * Sert aussi à l'objet (voir RouletteChestBewlr).
 */
@OnlyIn(Dist.CLIENT)
public class RouletteChestRenderer implements BlockEntityRenderer<RouletteChestBlockEntity> {

    /** Angle d'ouverture du couvercle (degrés) et vitesse (degrés par seconde : 70° en 0,4 s). */
    public static final float LID_OPEN = 70f;
    private static final float LID_SPEED = 175f;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final ResourceLocation WOOD = tex("wood"), IRON = tex("iron"), TRIM = tex("trim"), GEM = tex("gem");

    private static ResourceLocation tex(String n) { return new ResourceLocation(WaveSurvivorMod.MODID, "textures/block/roulette/" + n + ".png"); }

    public RouletteChestRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(RouletteChestBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        BlockState st = be.getBlockState();
        if (!st.hasProperty(RouletteChestBlock.FACING)) return;
        float yaw = 180f - st.getValue(RouletteChestBlock.FACING).toYRot(); // le médaillon avant regarde vers la direction du bloc
        float lid = be.animateLid(st.getValue(RouletteChestBlock.OPEN) ? LID_OPEN : 0f, LID_SPEED);
        float time = be.getLevel() == null ? 0 : be.getLevel().getGameTime() + partial;
        // Animation d'ouverture : t = ticks depuis le lancement (négatif ou trop grand = aucune animation)
        float t = be.animStart() < 0 ? -1 : time - be.animStart();
        boolean anim = t >= 0 && be.animGive() > 0 && t <= be.animGive() + 60;
        RouletteAnim.Timeline tl = anim ? RouletteAnim.of(be.animGive()) : null;
        float spin = anim ? RouletteAnim.spinAngle(tl, t) : 0f;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(-0.5, 0, -0.5);
        renderChest(be.style(), ps, buf, light, overlay, lid, time, spin);
        if (anim) renderReveal(be.animColor(), be.animStack(), be.getLevel(), be.getBlockPos().asLong(), tl, t, time, ps, buf, overlay);
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(RouletteChestBlockEntity be) { return false; }

    /** Faisceau à la couleur de la rareté du gain, et objet qui s'élève dedans avant d'être donné (aussi utilisé par l'aperçu de l'éditeur, avec level = null). */
    public static void renderReveal(int c, net.minecraft.world.item.ItemStack shown, net.minecraft.world.level.Level level, long seed,
                                    RouletteAnim.Timeline tl, float t, float time, PoseStack ps, MultiBufferSource buf, int overlay) {
        float strength = RouletteAnim.beamStrength(tl, t);
        if (strength > 0.01f) {
            float[] col = {((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
            ps.pushPose();
            ps.translate(0, 1.0, 0); // part du dessus du coffre
            BeaconRenderer.renderBeaconBeam(ps, buf, BeaconRenderer.BEAM_LOCATION, 0f, 1f, (long) time, 0, 6, col, 0.12f * strength, 0.18f * strength);
            ps.popPose();
        }
        float rise = RouletteAnim.rise(tl, t);
        if (rise >= 0 && rise <= 1.05f && shown != null && !shown.isEmpty()) {
            float e = 1f - (1f - Math.min(1f, rise)) * (1f - Math.min(1f, rise)); // décélère en haut du faisceau
            ps.pushPose();
            ps.translate(0.5, 1.15 + 1.35 * e, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(time * 8f));
            ps.scale(0.8f, 0.8f, 0.8f);
            Minecraft.getInstance().getItemRenderer().renderStatic(shown, ItemDisplayContext.GROUND, FULL_BRIGHT, overlay, ps, buf, level, (int) seed);
            ps.popPose();
        }
    }

    /** Dessine le coffre entier (repère du bloc : 0 à 1). {@code lidAngle} en degrés, {@code time} en ticks (pulsation). */
    public static void renderChest(RouletteStyle style, PoseStack ps, MultiBufferSource buf, int light, int overlay, float lidAngle, float time) {
        renderChest(style, ps, buf, light, overlay, lidAngle, time, 0f);
    }

    /** Idem, avec la rotation (degrés) du médaillon avant pendant la roulette. */
    public static void renderChest(RouletteStyle style, PoseStack ps, MultiBufferSource buf, int light, int overlay, float lidAngle, float time, float frontSpin) {
        RouletteChestModel model = RouletteChestModel.get();
        if (model == null) return;
        float pulse = 0.9f + 0.1f * Mth.sin(time * (float) Math.PI / 20f); // 0,8 à 1,0 sur 40 ticks
        boolean topMedallion = style.medallions.containsKey("top");
        for (RouletteChestModel.Element e : model.elements) {
            if (e.name().equals("front_medallion")) continue; // remplacé par le médaillon composé (face « front »)
            if (e.name().equals("gem") && e.from()[1] >= 14 && topMedallion) continue; // le médaillon du dessus remplace la gemme du couvercle
            ps.pushPose();
            if (e.lid()) rotateLid(ps, lidAngle);
            int tint;
            ResourceLocation texture;
            int lightNow = light;
            boolean full = false;
            switch (e.texture()) {
                case "wood" -> { tint = style.wood; texture = WOOD; }
                case "iron" -> { tint = style.iron; texture = IRON; }
                case "trim" -> { tint = scale(style.accent, pulse); texture = TRIM; lightNow = FULL_BRIGHT; }
                default -> { tint = scale(style.gem, pulse); texture = GEM; lightNow = FULL_BRIGHT; full = true; } // gemme : toute la texture sur chaque face
            }
            box(buf.getBuffer(RenderType.entityCutoutNoCull(texture)), ps.last(), e.from(), e.to(), tint, lightNow, overlay, full, null);
            ps.popPose();
        }
        // Médaillons : plaque + anneau + symboles composés en une texture
        for (String face : RouletteStyle.FACES) {
            List<RouletteStyle.Layer> layers = style.medallions.get(face);
            RouletteChestModel.Slot slot = model.slots.get(face);
            if (layers == null || layers.isEmpty() || slot == null) continue;
            ResourceLocation t = MedallionTextures.get(layers, style.accent, style.customSymbols);
            ps.pushPose();
            if (slot.lid()) rotateLid(ps, lidAngle);
            if (face.equals("front") && frontSpin != 0f) { // le cadran tourne sur lui-même (autour de la normale de la face)
                float cx = (slot.from()[0] + slot.to()[0]) / 32f, cy = (slot.from()[1] + slot.to()[1]) / 32f, cz = slot.from()[2] / 16f;
                ps.translate(cx, cy, cz);
                ps.mulPose(Axis.ZP.rotationDegrees(frontSpin));
                ps.translate(-cx, -cy, -cz);
            }
            box(buf.getBuffer(RenderType.entityCutoutNoCull(t)), ps.last(), slot.from(), slot.to(), 0xFFFFFF, light, overlay, true, slot.face());
            ps.popPose();
        }
    }

    /** Le couvercle pivote autour de la charnière arrière (axe X, y = 9, z = 15) ; l'avant se lève. */
    private static void rotateLid(PoseStack ps, float angle) {
        if (angle == 0) return;
        ps.translate(0, 9 / 16.0, 15 / 16.0);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        ps.translate(0, -9 / 16.0, -15 / 16.0);
    }

    private static int scale(int rgb, float f) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * f)), g = Math.min(255, Math.round(((rgb >> 8) & 255) * f)), b = Math.min(255, Math.round((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    // ─── Géométrie ───

    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    /**
     * Dessine un pavé (coordonnées en pixels). {@code only} = une seule face (médaillon). {@code full} : la texture entière sur chaque face ;
     * sinon les coordonnées du bloc servent de coordonnées de texture (le bois se répète, les cornières montrent leur bord).
     */
    private static void box(VertexConsumer vc, PoseStack.Pose pose, float[] f, float[] t, int rgb, int light, int overlay, boolean full, String only) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        for (String face : FACES) {
            if (only != null && !only.equals(face)) continue;
            quad(vc, pose, face, f, t, r, g, b, light, overlay, full);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, String face, float[] f, float[] t, float r, float g, float b, int light, int overlay, boolean full) {
        float x0 = f[0], y0 = f[1], z0 = f[2], x1 = t[0], y1 = t[1], z1 = t[2];
        float[][] c;
        float nx = 0, ny = 0, nz = 0;
        switch (face) {
            case "north" -> { c = new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}}; nz = -1; }
            case "south" -> { c = new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}}; nz = 1; }
            case "east" -> { c = new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}}; nx = 1; }
            case "west" -> { c = new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}}; nx = -1; }
            case "up" -> { c = new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}}; ny = 1; }
            default -> { c = new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}}; ny = -1; }
        }
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (float[] p : c) {
            float[] uv = full ? fullUv(face, p, f, t) : coordUv(face, p);
            vc.vertex(m, p[0] / 16f, p[1] / 16f, p[2] / 16f).color(r, g, b, 1f).uv(uv[0], uv[1]).overlayCoords(overlay).uv2(light)
                    .normal(n, nx, ny, nz).endVertex();
        }
    }

    /** Coordonnées de texture tirées des coordonnées du bloc (en pixels), vues de l'extérieur : u vers la droite, v vers le bas. */
    private static float[] coordUv(String face, float[] p) {
        float u, v;
        switch (face) {
            case "north" -> { u = 16 - p[0]; v = 16 - p[1]; }
            case "south" -> { u = p[0]; v = 16 - p[1]; }
            case "east" -> { u = 16 - p[2]; v = 16 - p[1]; }
            case "west" -> { u = p[2]; v = 16 - p[1]; }
            case "up" -> { u = p[0]; v = p[2]; }
            default -> { u = p[0]; v = 16 - p[2]; }
        }
        return new float[]{Mth.clamp(u, 0, 16) / 16f, Mth.clamp(v, 0, 16) / 16f};
    }

    /** Toute la texture sur la face : u de 0 à 1 vers la droite (vue de l'extérieur), v de 0 (haut) à 1 (bas). */
    private static float[] fullUv(String face, float[] p, float[] f, float[] t) {
        float dx = Math.max(1e-4f, t[0] - f[0]), dy = Math.max(1e-4f, t[1] - f[1]), dz = Math.max(1e-4f, t[2] - f[2]);
        float u, v;
        switch (face) {
            case "north" -> { u = (t[0] - p[0]) / dx; v = (t[1] - p[1]) / dy; }
            case "south" -> { u = (p[0] - f[0]) / dx; v = (t[1] - p[1]) / dy; }
            case "east" -> { u = (t[2] - p[2]) / dz; v = (t[1] - p[1]) / dy; }
            case "west" -> { u = (p[2] - f[2]) / dz; v = (t[1] - p[1]) / dy; }
            case "up" -> { u = (p[0] - f[0]) / dx; v = (p[2] - f[2]) / dz; }
            default -> { u = (p[0] - f[0]) / dx; v = (t[2] - p[2]) / dz; }
        }
        return new float[]{Mth.clamp(u, 0, 1), Mth.clamp(v, 0, 1)};
    }

    static final int NO_OVERLAY = OverlayTexture.NO_OVERLAY;
}
