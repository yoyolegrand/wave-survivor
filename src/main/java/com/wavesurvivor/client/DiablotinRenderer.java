package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.VexModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Vex;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Rendu du Diablotin : modèle + animations + textures du vex, TEINTÉS rouge braise à la volée
 * (chaque couleur de sommet est multipliée par TINT) — aucune texture à fournir. Toujours lumineux.
 */
@OnlyIn(Dist.CLIENT)
public class DiablotinRenderer extends MobRenderer<Vex, VexModel> {

    private static final ResourceLocation VEX = new ResourceLocation("textures/entity/illager/vex.png");
    private static final ResourceLocation VEX_CHARGING = new ResourceLocation("textures/entity/illager/vex_charging.png");
    private final float R, G, B;

    /** Diablotin : rouge braise. */
    public DiablotinRenderer(EntityRendererProvider.Context ctx) {
        this(ctx, 1.0f, 0.32f, 0.18f);
    }

    /** Vex teinté de n'importe quelle couleur (ex : Éclat du Néant violet). */
    public DiablotinRenderer(EntityRendererProvider.Context ctx, float r, float g, float b) {
        super(ctx, new VexModel(ctx.bakeLayer(ModelLayers.VEX)), 0.3f);
        this.R = r;
        this.G = g;
        this.B = b;
    }

    @Override
    public ResourceLocation getTextureLocation(Vex e) {
        return e.isCharging() ? VEX_CHARGING : VEX;
    }

    @Override
    protected int getBlockLightLevel(Vex e, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(Vex e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        MultiBufferSource tinted = rt -> new Tinted(buffers.getBuffer(rt), R, G, B);
        super.render(e, yaw, partialTick, pose, tinted, light);
    }

    /** Délègue tout, en multipliant la couleur par la teinte. */
    private static final class Tinted implements VertexConsumer {
        private final VertexConsumer d;
        private final float R, G, B;
        Tinted(VertexConsumer d, float r, float g, float b) { this.d = d; this.R = r; this.G = g; this.B = b; }

        @Override public VertexConsumer vertex(double x, double y, double z) { d.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            d.color((int) (r * R), (int) (g * G), (int) (b * B), a);
            return this;
        }
        @Override public VertexConsumer uv(float u, float v) { d.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { d.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { d.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { d.normal(x, y, z); return this; }
        @Override public void endVertex() { d.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { d.defaultColor((int) (r * R), (int) (g * G), (int) (b * B), a); }
        @Override public void unsetDefaultColor() { d.unsetDefaultColor(); }
    }
}
