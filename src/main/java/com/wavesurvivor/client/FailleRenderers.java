package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 2.0 — Rendus des monstres de la Faille posés au sol. Les modèles de Claude chat (dessinés à l'échelle 2, réduits de moitié autour de y = 0)
 * ont le bas des pieds au-dessus de y = 48 : ils flottaient (Ancreur ~0,47 bloc, Géodon ~0,25, Bolide ~1 px). Plutôt que de retoucher leurs
 * fichiers (générés par script), on descend ici chaque modèle de l'écart mesuré.
 * À RETIRER (remettre GeodonRenderer::new… dans ClientModEvents) si Claude chat corrige la hauteur dans ses modèles.
 */
@OnlyIn(Dist.CLIENT)
public final class FailleRenderers {

    private FailleRenderers() {}

    /** Écart (en blocs) entre le bas des pieds du modèle et le sol : (48 − bas des pieds) / 2 / 16. */
    static final float GEODON_DROP = (48f - 40f) / 32f, ANCREUR_DROP = (48f - 33f) / 32f, BOLIDE_DROP = (48f - 46f) / 32f;

    public static class Geodon extends GeodonRenderer<com.wavesurvivor.entity.Geodon> {
        public Geodon(EntityRendererProvider.Context ctx) { super(ctx); }

        @Override
        protected void setupRotations(com.wavesurvivor.entity.Geodon e, PoseStack ps, float age, float yaw, float pt) {
            ps.translate(0, -GEODON_DROP, 0);
            super.setupRotations(e, ps, age, yaw, pt);
        }
    }

    public static class Ancreur extends AncreurRenderer<com.wavesurvivor.entity.Ancreur> {
        public Ancreur(EntityRendererProvider.Context ctx) { super(ctx); }

        @Override
        protected void setupRotations(com.wavesurvivor.entity.Ancreur e, PoseStack ps, float age, float yaw, float pt) {
            ps.translate(0, -ANCREUR_DROP, 0);
            super.setupRotations(e, ps, age, yaw, pt);
        }
    }

    public static class Bolide extends BolideRenderer<com.wavesurvivor.entity.Bolide> {
        public Bolide(EntityRendererProvider.Context ctx) { super(ctx); }

        @Override
        protected void setupRotations(com.wavesurvivor.entity.Bolide e, PoseStack ps, float age, float yaw, float pt) {
            ps.translate(0, -BOLIDE_DROP, 0);
            super.setupRotations(e, ps, age, yaw, pt);
        }
    }
}
