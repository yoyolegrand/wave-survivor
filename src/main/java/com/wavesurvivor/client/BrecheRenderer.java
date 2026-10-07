package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.wavesurvivor.entity.BreachStyle;
import com.wavesurvivor.entity.BrecheEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Rendu de la BRÈCHE, uniquement avec des éléments de Minecraft (aucune texture à fournir) :
 *   - faille : plans croisés avec la texture ANIMÉE du portail du Nether, teintée (couleur du style), lumineuse,
 *     qui « respire » et tourne lentement ; sa hauteur suit les PV (3 blocs → mince fissure) ;
 *   - anneau : 8 mini-blocs au sol (2 blocs du style en alternance) ;
 *   - éclats : 4 mini-blocs en orbite qui tanguent, tombent à 75 / 50 / 25 % de PV ;
 *   - flash quand elle est touchée, effondrement à la mort.
 */
@OnlyIn(Dist.CLIENT)
public class BrecheRenderer extends EntityRenderer<BrecheEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;
    private final BlockRenderDispatcher blocks;
    /** Mode Kingdom : rendu monumental des grands portails (« Portes de l'Abîme »). */
    private final GreatPortalArt great;

    public BrecheRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.great = new GreatPortalArt(ctx);
        this.shadowRadius = 0.6f;
    }

    @Override
    public ResourceLocation getTextureLocation(BrecheEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(BrecheEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        // Cristal de la Mairie au-dessus du Monolithe (mode Kingdom) : visuel seul, sans nom
        if (e.isCrystal()) {
            great.renderCrystal(e, pt, pose, buffers);
            return;
        }
        // Faille au sol des brèches du Calme (mode Kingdom) : visuel seul, sans nom
        if (e.isGroundRift()) {
            great.renderGroundRift(e, pt, pose, buffers, light);
            return;
        }
        // Grand portail du mode Kingdom : Porte de l'Abîme (les brèches classiques gardent leur rendu)
        if (e.isGreat()) {
            great.render(e, pt, pose, buffers, light);
            super.render(e, yaw, pt, pose, buffers, light); // nom + PV
            return;
        }
        // Catalyseur du mode Kingdom (objectif du Calme)
        if (e.isCatalyst()) {
            great.renderCatalyst(e, pt, pose, buffers, light);
            super.render(e, yaw, pt, pose, buffers, light);
            return;
        }
        // Taille du portail (grands portails du mode Kingdom) : tout le dessin est agrandi
        float riftScale = e.getRiftScale();
        pose.pushPose();
        pose.scale(riftScale, riftScale, riftScale);
        float t = e.tickCount + pt;
        float hp = Mth.clamp(e.getHealth() / e.getMaxHealth(), 0f, 1f);
        float death = e.deathTime > 0 ? Mth.clamp(1f - (e.deathTime + pt) / 20f, 0f, 1f) : 1f;
        BreachStyle style = e.getStyle();

        int col = e.getRiftColor();
        float r = ((col >> 16) & 0xFF) / 255f, g = ((col >> 8) & 0xFF) / 255f, b = (col & 0xFF) / 255f;
        if (e.hurtTime > 0) { r = 1f; g = Math.min(1f, g * 0.5f + 0.4f); b = Math.min(1f, b * 0.5f + 0.4f); } // flash

        // ─── Faille ───
        float height = 3.0f * (0.35f + 0.65f * hp) * death;
        float pulse = 1f + 0.08f * Mth.sin(t * 0.15f);
        float half = 0.5f * pulse * death;
        if (height > 0.02f) {
            TextureAtlasSprite sp = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper()
                    .getParticleIcon(Blocks.NETHER_PORTAL.defaultBlockState());
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * 0.8f));
            for (int plane = 0; plane < 2; plane++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(plane * 90f));
                Matrix4f m = pose.last().pose();
                Matrix3f n = pose.last().normal();
                int segs = Math.max(1, Math.round(height));
                float segH = height / segs;
                for (int s = 0; s < segs; s++) {
                    float y0 = 0.05f + s * segH, y1 = y0 + segH;
                    // effilée en haut et en bas (forme de déchirure)
                    float k0 = taper(s, segs), k1 = taper(s + 1, segs);
                    quad(vc, m, n, -half * k0, y0, half * k0, y1, -half * k1, half * k1, sp, r, g, b);
                }
                pose.popPose();
            }
            pose.popPose();
        }

        // ─── Anneau de base ───
        int ringLight = style.baseB == Blocks.MAGMA_BLOCK ? FULL_BRIGHT : light;
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            BlockState st = (i % 2 == 0 ? style.baseA : style.baseB).defaultBlockState();
            pose.pushPose();
            pose.translate(Math.cos(a) * 0.95, -0.02, Math.sin(a) * 0.95);
            pose.mulPose(Axis.YP.rotationDegrees(-i * 45f));
            pose.scale(0.42f * death, 0.22f * death, 0.42f * death);
            pose.translate(-0.5, 0, -0.5);
            blocks.renderSingleBlock(st, pose, buffers, i % 2 == 0 ? light : ringLight, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }

        // ─── Éclats en orbite (tombent avec les PV) ───
        int shards = death < 1f ? 0 : Mth.ceil(4 * hp);
        BlockState shard = style.shard.defaultBlockState();
        int shardLight = style.shard == Blocks.SEA_LANTERN || style.shard == Blocks.CRYING_OBSIDIAN ? FULL_BRIGHT : light;
        for (int i = 0; i < shards; i++) {
            float ang = t * 2.0f + i * 90f;
            double a = Math.toRadians(ang);
            double y = 0.9 + i * 0.5 + Mth.sin(t * 0.1f + i) * 0.15;
            pose.pushPose();
            pose.translate(Math.cos(a) * 1.15, y, Math.sin(a) * 1.15);
            pose.mulPose(Axis.YP.rotationDegrees(t * 3f + i * 40f));
            pose.mulPose(Axis.XP.rotationDegrees(25f + i * 15f));
            pose.scale(0.28f, 0.28f, 0.28f);
            pose.translate(-0.5, -0.5, -0.5);
            blocks.renderSingleBlock(shard, pose, buffers, shardLight, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }

        pose.popPose(); // fin de l'agrandissement
        super.render(e, yaw, pt, pose, buffers, light); // nom + PV
    }

    /** Largeur relative d'une section (0 en bas/haut, 1 au milieu) pour une forme de déchirure. */
    private static float taper(int i, int segs) {
        float x = (float) i / segs; // 0..1
        return Mth.clamp(0.25f + 1.5f * (0.5f - Math.abs(x - 0.5f)) * 1.3f, 0.2f, 1f);
    }

    /** Quadrilatère texturé (recto + verso) entre y0 et y1, largeurs (x0a..x0b) en bas et (x1a..x1b) en haut. */
    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0a, float y0, float x0b, float y1,
                             float x1a, float x1b, TextureAtlasSprite sp, float r, float g, float b) {
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        float a = 0.88f;
        // recto
        v(vc, m, n, x0a, y0, u0, v1, r, g, b, a, 1);
        v(vc, m, n, x0b, y0, u1, v1, r, g, b, a, 1);
        v(vc, m, n, x1b, y1, u1, v0, r, g, b, a, 1);
        v(vc, m, n, x1a, y1, u0, v0, r, g, b, a, 1);
        // verso
        v(vc, m, n, x1a, y1, u0, v0, r, g, b, a, -1);
        v(vc, m, n, x1b, y1, u1, v0, r, g, b, a, -1);
        v(vc, m, n, x0b, y0, u1, v1, r, g, b, a, -1);
        v(vc, m, n, x0a, y0, u0, v1, r, g, b, a, -1);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float vv,
                          float r, float g, float b, float a, int side) {
        vc.vertex(m, x, y, 0f).color(r, g, b, a).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT).normal(n, 0f, 0f, side).endVertex();
    }
}
