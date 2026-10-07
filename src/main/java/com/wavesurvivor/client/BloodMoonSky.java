package com.wavesurvivor.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.kingdom.KingdomRoleState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Présage « Lune de sang » : une grande LUNE ROUGE pleine dans le ciel (dessinée par-dessus la lune vanilla, à la même
 * position) et un ciel / brouillard teinté de rouge. Purement visuel, côté client.
 */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BloodMoonSky {

    private BloodMoonSky() {}

    private static final ResourceLocation MOON = new ResourceLocation("textures/environment/moon_phases.png");

    private static boolean on() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && "blood_moon".equals(KingdomRoleState.clientOmenActive);
    }

    /** Ciel et brouillard tirés vers le rouge sang. */
    @SubscribeEvent
    public static void bloodMoonFog(ViewportEvent.ComputeFogColor e) {
        if (!on()) return;
        float k = 0.55f;
        e.setRed(e.getRed() * (1 - k) + 0.45f * k);
        e.setGreen(e.getGreen() * (1 - k) + 0.03f * k);
        e.setBlue(e.getBlue() * (1 - k) + 0.03f * k);
    }

    /** Lune rouge pleine, 1,6× plus grande que la lune vanilla. */
    @SubscribeEvent
    public static void bloodMoonSky(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !on()) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-90.0F));
        ps.mulPose(Axis.XP.rotationDegrees(mc.level.getTimeOfDay(e.getPartialTick()) * 360.0F));
        Matrix4f m = ps.last().pose();
        float size = 32f;
        // Pleine lune : première case de la planche des phases (4 × 2)
        float u0 = 0f, v0 = 0f, u1 = 0.25f, v1 = 0.5f;
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MOON);
        RenderSystem.setShaderColor(1.0f, 0.12f, 0.08f, 1.0f);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, -size, -100.0F, size).uv(u1, v1).endVertex();
        bb.vertex(m, size, -100.0F, size).uv(u0, v1).endVertex();
        bb.vertex(m, size, -100.0F, -size).uv(u0, v0).endVertex();
        bb.vertex(m, -size, -100.0F, -size).uv(u1, v0).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        ps.popPose();
    }
}
