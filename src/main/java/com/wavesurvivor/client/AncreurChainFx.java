package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.entity.Ancreur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 2.0 — Chaîne de l'ANCREUR, dessinée comme une laisse entre l'ancre plantée et le joueur enchaîné : maillons de la texture vanilla
 * {@code minecraft:block/chain} (deux plans en croix, comme le bloc de chaîne), teintés bleu acier, qui pendent quand la chaîne est lâche.
 * Le rendu de l'Ancreur (Claude chat) n'est pas modifié : tout se fait ici, à partir de l'ancre et du joueur synchronisés par l'entité.
 */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AncreurChainFx {

    private AncreurChainFx() {}

    private static final ResourceLocation CHAIN = new ResourceLocation("minecraft", "textures/block/chain.png");
    private static final int SEGMENTS = 16;

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        boolean any = false;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof Ancreur a) || a.failleState() != Ancreur.HOLD) continue;
            Entity p = a.chained();
            if (p == null) continue;
            Vec3 from = a.anchorPos().add(0, 1.1, 0);   // anneau de l'ancre plantée
            Vec3 to = p.getPosition(pt).add(0, p.getBbHeight() * 0.5, 0);   // à la taille du joueur
            if (from.distanceToSqr(to) > 40 * 40) continue;
            draw(e.getPoseStack(), buf.getBuffer(RenderType.entityCutoutNoCull(CHAIN)), cam, from, to, mc);
            any = true;
        }
        if (any) buf.endBatch(RenderType.entityCutoutNoCull(CHAIN));
    }

    /** Courbe de laisse : ligne droite + affaissement (plus fort quand la chaîne est lâche), maillons tous les ~0,6 bloc. */
    private static void draw(PoseStack ps, VertexConsumer vc, Vec3 cam, Vec3 a, Vec3 b, Minecraft mc) {
        double len = a.distanceTo(b);
        double sag = Math.max(0.05, (Ancreur.CHAIN_LEN + 0.5 - len) * 0.25);   // tendue au bout des 6 blocs
        Vec3[] pts = new Vec3[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            pts[i] = a.lerp(b, t).add(0, -sag * 4 * t * (1 - t), 0);
        }
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        double vAcc = 0;
        for (int i = 0; i < SEGMENTS; i++) {
            Vec3 p0 = pts[i], p1 = pts[i + 1];
            Vec3 dir = p1.subtract(p0);
            double segLen = dir.length();
            if (segLen < 1e-4) continue;
            Vec3 d = dir.scale(1 / segLen);
            // deux plans en croix autour du segment
            Vec3 side = d.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
            side = side.normalize().scale(0.09);
            Vec3 up = d.cross(side).normalize().scale(0.09);
            float v0 = (float) vAcc, v1 = (float) (vAcc + segLen / 0.9);   // la texture (16 px = 1 bloc) répétée ~ tous les 0,9 bloc
            vAcc += segLen / 0.9;
            Vec3 mid = p0.add(p1).scale(0.5);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(mid));
            quad(vc, m, n, p0, p1, side, 0f, 3f / 16f, v0, v1, light);
            quad(vc, m, n, p0, p1, up, 3f / 16f, 6f / 16f, v0, v1, light);
        }
        ps.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p0, Vec3 p1, Vec3 off, float u0, float u1, float v0, float v1, int light) {
        float r = 0.72f, g = 0.84f, b = 1.0f;   // fer bleuté
        vert(vc, m, n, p0.subtract(off), u0, v0, r, g, b, light);
        vert(vc, m, n, p0.add(off), u1, v0, r, g, b, light);
        vert(vc, m, n, p1.add(off), u1, v1, r, g, b, light);
        vert(vc, m, n, p1.subtract(off), u0, v1, r, g, b, light);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, float r, float g, float b, int light) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, 1f).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, 0f, 1f, 0f).endVertex();
    }
}
