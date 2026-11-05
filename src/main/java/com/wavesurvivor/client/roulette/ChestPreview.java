package com.wavesurvivor.client.roulette;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.roulette.RouletteAnim;
import com.wavesurvivor.horde.roulette.RouletteStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Aperçu en direct du coffre dans l'éditeur : le même rendu que dans le monde (RouletteChestRenderer), dessiné dans l'interface, avec rotation
 * automatique ou fixe et un essai de l'animation d'ouverture (médaillon qui tourne et s'arrête, couvercle, faisceau, objet qui s'élève).
 * Dessine aussi l'icône composée d'une clé (onglet Clé).
 */
@OnlyIn(Dist.CLIENT)
public final class ChestPreview {

    /** Durée de l'essai d'ouverture (ticks) : instant du gain ; le couvercle se referme 40 ticks plus tard. */
    public static final int TEST_GIVE = 70;

    private ChestPreview() {}

    /**
     * Dessine le coffre centré en (cx, cy), {@code size} pixels de large environ. {@code yaw} : rotation autour de l'axe vertical (degrés).
     * {@code testTicks} : ticks écoulés depuis le début de l'essai d'ouverture (négatif = pas d'essai).
     */
    public static void draw(GuiGraphics g, RouletteStyle style, int cx, int cy, float size, float yaw, float time, float testTicks, int beamColor, ItemStack shown) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 200);
        float s = size * 0.78f;
        ps.scale(s, -s, s);
        ps.mulPose(Axis.XP.rotationDegrees(22));
        ps.mulPose(Axis.YP.rotationDegrees(180 + yaw)); // le médaillon avant fait face au spectateur à yaw = 0
        ps.translate(-0.5, -0.45, -0.5);

        float lid = 0f, spin = 0f;
        RouletteAnim.Timeline tl = null;
        boolean testing = testTicks >= 0 && testTicks <= TEST_GIVE + RouletteAnim.CLOSE_AFTER + 20;
        if (testing) {
            tl = RouletteAnim.of(TEST_GIVE);
            spin = RouletteAnim.spinAngle(tl, testTicks);
            float open = Math.min(1f, Math.max(0f, (testTicks - tl.stop()) / 8f));
            float close = Math.min(1f, Math.max(0f, (TEST_GIVE + RouletteAnim.CLOSE_AFTER + 8 - testTicks) / 8f));
            lid = RouletteChestRenderer.LID_OPEN * Math.min(open, close);
        }
        Lighting.setupForEntityInInventory();
        RenderSystem.enableDepthTest();
        var buf = g.bufferSource();
        RouletteChestRenderer.renderChest(style, ps, buf, 0xF000F0, OverlayTexture.NO_OVERLAY, lid, time, spin);
        if (testing) RouletteChestRenderer.renderReveal(beamColor, shown, null, 0L, tl, testTicks, time, ps, buf, OverlayTexture.NO_OVERLAY);
        g.flush();
        Lighting.setupFor3DItems();
        ps.popPose();
    }

    private static final ResourceLocation KEY_DIR = new ResourceLocation(WaveSurvivorMod.MODID, "textures/item/roulette_key/");

    private static ResourceLocation key(String name) {
        return new ResourceLocation(WaveSurvivorMod.MODID, "textures/item/roulette_key/" + name + ".png");
    }

    /** Icône de clé composée (contour, tige, panneton, tête teintés à la matière, gemme à la couleur des gemmes), {@code px} pixels de côté. */
    public static void drawKey(GuiGraphics g, String head, String bit, int body, int gem, int x, int y, int px) {
        tinted(g, key("outline_" + head + "_" + bit), 0xFFFFFF, x, y, px);
        tinted(g, key("shaft"), body, x, y, px);
        tinted(g, key("bit_" + bit), body, x, y, px);
        tinted(g, key("head_" + head), body, x, y, px);
        tinted(g, key("head_" + head + "_gem"), gem, x, y, px);
    }

    /** Dessine une texture 16 × 16 teintée (multiplication) à la taille voulue. */
    public static void tinted(GuiGraphics g, ResourceLocation tex, int rgb, int x, int y, int px) {
        g.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
        g.blit(tex, x, y, px, px, 0, 0, 16, 16, 16, 16);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Masque de symbole (blanc 16 × 16) teinté. */
    public static void drawSymbol(GuiGraphics g, String id, boolean small, int rgb, int x, int y, int px) {
        tinted(g, new ResourceLocation(WaveSurvivorMod.MODID, "textures/roulette_symbols/" + (small ? "small" : "large") + "/" + id + ".png"), rgb, x, y, px);
    }
}
