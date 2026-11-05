package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.craft.CraftUtilityBlock;
import com.wavesurvivor.craft.CraftUtilityBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Cristal rose de l'Obélisque profane : un modèle de bloc ne peut pas s'animer, il est donc dessiné ici (même texture, pleine luminosité),
 * tournant sur lui-même (un tour toutes les 4 secondes) avec un léger flottement. Ne dessine rien pour les autres blocs utilitaires.
 */
@OnlyIn(Dist.CLIENT)
public class ObeliskCrystalRenderer implements BlockEntityRenderer<CraftUtilityBlockEntity> {

    private static final ResourceLocation TEX = new ResourceLocation(WaveSurvivorMod.MODID, "textures/block/profane_obelisk_crystal.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public ObeliskCrystalRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(CraftUtilityBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (!(be.getBlockState().getBlock() instanceof CraftUtilityBlock b) || b.kind != CraftUtilityBlock.Kind.OBELISK || be.getLevel() == null) return;
        float t = be.getLevel().getGameTime() + partial;
        float angle = t * 4.5f; // 360° / 80 ticks
        float bob = (float) Math.sin(t * 0.1f) * (0.04f);
        ps.pushPose();
        ps.translate(0.5, 29.0 / 16.0 + bob, 0.5); // base du cristal : y = 29 / 16 au-dessus du bas de l'obélisque
        ps.mulPose(Axis.YP.rotationDegrees(angle));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        PoseStack.Pose pose = ps.last();
        float h = 3f / 16f, r = 1f / 16f; // 2 × 3 × 2 pixels
        // côtés : région de texture du modèle d'origine (4,0)–(12,12) ; dessus et dessous : (4,4)–(12,12)
        side(vc, pose, -r, 0, r, r, h, r, 0, 0, 1);   // sud
        side(vc, pose, r, 0, -r, -r, h, -r, 0, 0, -1); // nord
        side(vc, pose, r, 0, r, r, h, -r, 1, 0, 0);    // est
        side(vc, pose, -r, 0, -r, -r, h, r, -1, 0, 0); // ouest
        cap(vc, pose, h, r, 1);
        cap(vc, pose, 0, r, -1);
        ps.popPose();
    }

    /** Face verticale du point (xa, y0, za) au point (xb, y1, zb), normale (nx, 0, nz). */
    private static void side(VertexConsumer vc, PoseStack.Pose pose, float xa, float y0, float za, float xb, float y1, float zb, float nx, float ny, float nz) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float u0 = 4f / 16f, u1 = 12f / 16f, v0 = 0f, v1 = 12f / 16f;
        vertex(vc, m, n, xa, y1, za, u0, v0, nx, ny, nz);
        vertex(vc, m, n, xa, y0, za, u0, v1, nx, ny, nz);
        vertex(vc, m, n, xb, y0, zb, u1, v1, nx, ny, nz);
        vertex(vc, m, n, xb, y1, zb, u1, v0, nx, ny, nz);
    }

    /** Dessus (dir = 1) ou dessous (dir = −1). */
    private static void cap(VertexConsumer vc, PoseStack.Pose pose, float y, float r, int dir) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float u0 = 4f / 16f, u1 = 12f / 16f, v0 = 4f / 16f, v1 = 12f / 16f;
        if (dir > 0) {
            vertex(vc, m, n, -r, y, -r, u0, v0, 0, 1, 0);
            vertex(vc, m, n, -r, y, r, u0, v1, 0, 1, 0);
            vertex(vc, m, n, r, y, r, u1, v1, 0, 1, 0);
            vertex(vc, m, n, r, y, -r, u1, v0, 0, 1, 0);
        } else {
            vertex(vc, m, n, -r, y, r, u0, v0, 0, -1, 0);
            vertex(vc, m, n, -r, y, -r, u0, v1, 0, -1, 0);
            vertex(vc, m, n, r, y, -r, u1, v1, 0, -1, 0);
            vertex(vc, m, n, r, y, r, u1, v0, 0, -1, 0);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(1f, 1f, 1f, 1f).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n, nx, ny, nz).endVertex();
    }

    @Override
    public int getViewDistance() { return 64; }
}
