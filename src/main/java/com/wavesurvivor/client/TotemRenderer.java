package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.wavesurvivor.entity.TotemEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Rendu du TOTEM, uniquement avec des éléments de Minecraft :
 *   - pilier sculpté de 3 blocs de blackstone qui s'affinent ;
 *   - la TÊTE (item de la config : tête de joueur, crâne, citrouille...) au sommet ;
 *   - anneau de 6 RUNES lumineuses (couleur du rôle) qui tournent ; elles s'éteignent avec les PV ;
 *   - flash quand il est touché, effondrement à la mort.
 */
@OnlyIn(Dist.CLIENT)
public class TotemRenderer extends EntityRenderer<TotemEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final Map<String, ItemStack> HEADS = new HashMap<>();
    private final BlockRenderDispatcher blocks;

    public TotemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0.5f;
    }

    @Override
    public ResourceLocation getTextureLocation(TotemEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private static ItemStack head(String id) {
        return HEADS.computeIfAbsent(id, k -> {
            try {
                var item = BuiltInRegistries.ITEM.get(new ResourceLocation(k));
                return new ItemStack(item == Items.AIR ? Items.PLAYER_HEAD : item);
            } catch (Exception ex) {
                return new ItemStack(Items.PLAYER_HEAD);
            }
        });
    }

    @Override
    public void render(TotemEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float hp = Mth.clamp(e.getHealth() / e.getMaxHealth(), 0f, 1f);
        float death = e.deathTime > 0 ? Mth.clamp(1f - (e.deathTime + pt) / 20f, 0f, 1f) : 1f;
        int col = e.getTotemColor();
        float r = ((col >> 16) & 0xFF) / 255f, g = ((col >> 8) & 0xFF) / 255f, b = (col & 0xFF) / 255f;
        boolean flash = e.hurtTime > 0;

        pose.pushPose();
        pose.scale(death, death, death);

        // ─── Pilier ───
        pillar(pose, buffers, light, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 0.0f, 0.80f, 0.80f);
        pillar(pose, buffers, light, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 0.80f, 0.66f, 0.70f);
        pillar(pose, buffers, light, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 1.50f, 0.56f, 0.55f);

        // ─── Tête (par rôle si la config ne précise rien) ───
        String headId = e.getHeadItem();
        if (headId == null || headId.isBlank() || headId.equals("minecraft:player_head")) headId = roleHead(e.getRole());
        pose.pushPose();
        pose.translate(0, 2.27, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot() + Mth.sin(t * 0.03f) * 12f));
        pose.scale(0.95f, 0.95f, 0.95f);
        Minecraft.getInstance().getItemRenderer().renderStatic(head(headId), ItemDisplayContext.FIXED,
                flash ? FULL_BRIGHT : light, flash ? OverlayTexture.pack(0, 3) : OverlayTexture.NO_OVERLAY,
                pose, buffers, e.level(), e.getId());
        pose.popPose();

        // ─── Anneau de runes : petits cubes lumineux (béton de la couleur du rôle, gris si éteints) ───
        BlockState litState = runeBlock(e.getRole()).defaultBlockState();
        BlockState offState = Blocks.GRAY_CONCRETE.defaultBlockState();
        int lit = Mth.ceil(6 * hp);
        for (int i = 0; i < 6; i++) {
            pose.pushPose();
            float ang = t * 1.6f + i * 60f;
            double bob = Mth.sin(t * 0.08f + i) * 0.08;
            pose.mulPose(Axis.YP.rotationDegrees(ang));
            pose.translate(0.82, 1.25 + bob, 0);
            pose.mulPose(Axis.YP.rotationDegrees(t * 4f));
            pose.mulPose(Axis.XP.rotationDegrees(35f));
            float s = 0.16f;
            pose.scale(s, s, s);
            pose.translate(-0.5, -0.5, -0.5);
            boolean on = i < lit;
            blocks.renderSingleBlock(flash && on ? Blocks.WHITE_CONCRETE.defaultBlockState() : on ? litState : offState,
                    pose, buffers, on ? FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();

        super.render(e, yaw, pt, pose, buffers, light); // nom + PV
    }

    /** Tête par défaut selon le rôle. */
    private static String roleHead(String role) {
        return switch (role == null ? "" : role) {
            case "fureur" -> "minecraft:piglin_head";
            case "soin" -> "minecraft:creeper_head";
            case "malediction" -> "minecraft:wither_skeleton_skull";
            case "invocation" -> "minecraft:zombie_head";
            default -> "minecraft:skeleton_skull";
        };
    }

    /** Bloc des runes allumées selon le rôle. */
    private static net.minecraft.world.level.block.Block runeBlock(String role) {
        return switch (role == null ? "" : role) {
            case "fureur" -> Blocks.RED_CONCRETE;
            case "soin" -> Blocks.LIME_CONCRETE;
            case "malediction" -> Blocks.PURPLE_CONCRETE;
            case "invocation" -> Blocks.ORANGE_CONCRETE;
            default -> Blocks.YELLOW_CONCRETE;
        };
    }

    private void pillar(PoseStack pose, MultiBufferSource buffers, int light, BlockState st, float y, float w, float h) {
        pose.pushPose();
        pose.translate(-w / 2, y, -w / 2);
        pose.scale(w, h, w);
        blocks.renderSingleBlock(st, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Petit carré lumineux (recto + verso). */
    private static void rune(VertexConsumer vc, Matrix4f m, Matrix3f n, TextureAtlasSprite sp,
                             float r, float g, float b, float a, float s) {
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        v(vc, m, n, -s, -s, u0, v1, r, g, b, a, 1);
        v(vc, m, n, s, -s, u1, v1, r, g, b, a, 1);
        v(vc, m, n, s, s, u1, v0, r, g, b, a, 1);
        v(vc, m, n, -s, s, u0, v0, r, g, b, a, 1);
        v(vc, m, n, -s, s, u0, v0, r, g, b, a, -1);
        v(vc, m, n, s, s, u1, v0, r, g, b, a, -1);
        v(vc, m, n, s, -s, u1, v1, r, g, b, a, -1);
        v(vc, m, n, -s, -s, u0, v1, r, g, b, a, -1);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float vv,
                          float r, float g, float b, float a, int side) {
        vc.vertex(m, x, y, 0f).color(r, g, b, a).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT).normal(n, 0f, 0f, side).endVertex();
    }
}
