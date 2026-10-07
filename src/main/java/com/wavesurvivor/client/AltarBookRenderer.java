package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.wavesurvivor.altar.AltarBlockEntity;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Livre flottant au sommet du Monolithe (Autel Runique).
 * Même modèle / texture / animation que la table d'enchantement vanilla,
 * positionné 2 blocs plus haut (au-dessus de la moitié haute).
 */
@OnlyIn(Dist.CLIENT)
public class AltarBookRenderer implements BlockEntityRenderer<AltarBlockEntity> {

    /** Hauteur de flottaison au-dessus de la base (1 bloc + marge). */
    private static final float BOOK_Y = 1.35F;

    /** Livre d'enchant avec couverture recolorée en cuir noir-gris (textures/block → atlas des blocs). */
    private static final net.minecraft.client.resources.model.Material BOOK_TEXTURE =
            new net.minecraft.client.resources.model.Material(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                    new net.minecraft.resources.ResourceLocation("wavesurvivor", "block/altar_book"));

    private final BookModel bookModel;

    public AltarBookRenderer(BlockEntityRendererProvider.Context ctx) {
        this.bookModel = new BookModel(ctx.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(AltarBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        pose.pushPose();
        float t = be.time + partialTick;
        pose.translate(0.5F, BOOK_Y + Mth.sin(t * 0.1F) * 0.03F, 0.5F);

        float dRot = be.rot - be.oRot;
        while (dRot >= (float) Math.PI) dRot -= (float) (Math.PI * 2);
        while (dRot < -(float) Math.PI) dRot += (float) (Math.PI * 2);
        float rot = be.oRot + dRot * partialTick;
        pose.mulPose(Axis.YP.rotation(-rot));
        pose.mulPose(Axis.ZP.rotationDegrees(80.0F));

        float flip = Mth.lerp(partialTick, be.oFlip, be.flip);
        float page1 = Mth.frac(flip + 0.25F) * 1.6F - 0.3F;
        float page2 = Mth.frac(flip + 0.75F) * 1.6F - 0.3F;
        float open = Mth.lerp(partialTick, be.oOpen, be.open);
        bookModel.setupAnim(t, Mth.clamp(page1, 0.0F, 1.0F), Mth.clamp(page2, 0.0F, 1.0F), open);

        // Lumière prise au-dessus du monolithe (la base est un bloc plein → sinon livre tout noir)
        int light = be.getLevel() != null
                ? LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above())
                : packedLight;

        VertexConsumer vc = BOOK_TEXTURE.buffer(buffers, RenderType::entitySolid);
        bookModel.renderToBuffer(pose, vc, light, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(AltarBlockEntity be) {
        return true;
    }
}
