package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wavesurvivor.horde.kingdom.DefenseBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * RENDU 3D DES DÉFENSES du mode Kingdom (blocs vanilla mis à l'échelle et assemblés, comme le Gisement).
 * Le bloc posé sert de socle ; la structure est un décor (sans collision) qui évolue avec le niveau.
 *  - Tour d'archers : tour de guet médiévale (contreforts, créneaux, lanternes, bannières, grand arc qui pivote) ;
 *    niv.2 braseros ; niv.3 toit pointu et flèche dorée.
 *  - Tour de mage « Arcanum en lévitation » : dais runique, 3 blocs qui lévitent et tournent en sens alternés,
 *    2 cercles magiques au sol, livre enchanté flottant ; 1 à 3 anneaux de runes en orbite.
 *  - Sanctuaire « Autel du Renouveau » : sanctuaire de quartz, colonnes coiffées de lanternes marines et d'azalée,
 *    Totem d'immortalité géant flottant dans un rayon vert ; 1 à 3 pommes dorées en orbite.
 *  - Baraquement : garnison 3×3×3 (soubassement en pierre, colombages, double porte, fenêtres éclairées,
 *    toit à deux pans, cheminée, bouclier et épées) ; niv.2 bannières ; niv.3 garnitures dorées et drapeau.
 */
@OnlyIn(Dist.CLIENT)
public class DefenseRenderer implements BlockEntityRenderer<DefenseBlockEntity> {

    private static final int GLOW = LightTexture.FULL_BRIGHT;
    private final BlockRenderDispatcher blocks;
    private final ItemRenderer items;

    public DefenseRenderer(BlockEntityRendererProvider.Context ctx) {
        this.blocks = ctx.getBlockRenderDispatcher();
        this.items = ctx.getItemRenderer();
    }

    @Override
    public boolean shouldRenderOffScreen(DefenseBlockEntity be) { return true; }

    @Override
    public int getViewDistance() { return 96; }

    @Override
    public void render(DefenseBlockEntity be, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        Level lvl = be.getLevel();
        if (lvl == null) return;
        int lt = LevelRenderer.getLightColor(lvl, be.getBlockPos().above());
        float t = (lvl.getGameTime() % 24000L) + pt;
        int level = be.getDefLevel();
        switch (be.kind()) {
            case ARCHER -> archer(pose, buf, lt, level, t, lvl);
            case MAGE -> mage(pose, buf, lt, level, t, lvl);
            case SHRINE -> shrine(pose, buf, lt, level, t, lvl);
            case BARRACKS -> barracks(pose, buf, lt, level, lvl);
            case COLLECTOR -> collector(pose, buf, lt, level, t);
            case WORKSHOP -> workshop(pose, buf, lt, level, t);
        }
        // Barre de PV au-dessus de la structure
        float barY = switch (be.kind()) {
            case ARCHER -> 9.6f;
            case MAGE -> 4.3f;
            case SHRINE -> 3.2f;
            case BARRACKS -> 6.0f;
            case COLLECTOR -> 3.9f;
            case WORKSHOP -> 1.9f;
        };
        // Centre de la défense : tour d'archers 2×2 (1 ; 1), baraquement 6×4 (2 ; 1), atelier 2×1 (1 ; 0,5), autres (0,5 ; 0,5)
        float midX = be.kind() == com.wavesurvivor.horde.kingdom.DefenseBlock.Kind.ARCHER ? 1.0f
                : be.kind() == com.wavesurvivor.horde.kingdom.DefenseBlock.Kind.BARRACKS ? 2.0f
                : be.kind() == com.wavesurvivor.horde.kingdom.DefenseBlock.Kind.WORKSHOP ? 1.0f : 0.5f;
        float midZ = (be.kind() == com.wavesurvivor.horde.kingdom.DefenseBlock.Kind.ARCHER
                || be.kind() == com.wavesurvivor.horde.kingdom.DefenseBlock.Kind.BARRACKS) ? 1.0f : 0.5f;
        crest(be, pose, buf, barY, t, midX, midZ);
        hpBar(be, pose, buf, barY, midX, midZ);
    }

    /** Emblème de spécialisation (niveau 3) : bloc qui flotte et tourne sous la barre de PV, avec 4 éclats en orbite. */
    private void crest(DefenseBlockEntity be, PoseStack pose, MultiBufferSource buf, float y, float t, float midX, float midZ) {
        String v = be.getVariant();
        if (v == null || v.isEmpty()) return;
        BlockState st = switch (v) {
            case "fire", "smelter", "forge" -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case "frost", "glacier" -> Blocks.BLUE_ICE.defaultBlockState();
            case "explosive" -> Blocks.TNT.defaultBlockState();
            case "storm" -> Blocks.LIGHTNING_ROD.defaultBlockState();
            case "curse" -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
            case "life" -> Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState();
            case "blessing", "paladins" -> Blocks.GOLD_BLOCK.defaultBlockState();
            case "berserkers" -> Blocks.REDSTONE_BLOCK.defaultBlockState();
            case "treasury" -> Blocks.EMERALD_BLOCK.defaultBlockState();
            case "armory" -> Blocks.IRON_BLOCK.defaultBlockState();
            case "foundry" -> Blocks.BLAST_FURNACE.defaultBlockState();
            default -> Blocks.IRON_BLOCK.defaultBlockState(); // rempart, gardes, aimant
        };
        float cy = y - 0.6f + Mth.sin(t * 0.08f) * 0.08f;
        spun(pose, buf, GLOW, st, midX, cy, midZ, 0.32f, t * 3f, 45f, 45f);
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(-t * 4f + i * 90f);
            spun(pose, buf, GLOW, st, midX + (float) Math.cos(a) * 0.5f, cy, midZ + (float) Math.sin(a) * 0.5f, 0.1f, t * 6f, 45f, 45f);
        }
    }

    /** Barre de PV (face au joueur) : visible si la défense est abîmée ou si le joueur est à moins de 8 blocs. */
    private void hpBar(DefenseBlockEntity be, PoseStack pose, MultiBufferSource buf, float y, float midX, float midZ) {
        float max = be.getMaxHp();
        if (max <= 0) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        float hp = Math.max(0, be.getHp());
        boolean damaged = hp < max - 0.5f;
        if (!damaged && mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos())) > 64) return;
        float ratio = Mth.clamp(hp / max, 0f, 1f);
        int segs = 20, filled = Math.round(ratio * segs);
        String color = ratio > 0.6f ? "§a" : ratio > 0.3f ? "§6" : "§c";
        String bar = color + "|".repeat(filled) + "§8" + "|".repeat(segs - filled);
        String txt = "§c❤ §f" + Math.round(hp) + "§7/" + Math.round(max);
        pose.pushPose();
        pose.translate(midX, y, midZ);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025f, -0.025f, 0.025f);
        org.joml.Matrix4f m = pose.last().pose();
        net.minecraft.client.gui.Font f = mc.font;
        f.drawInBatch(bar, -f.width(bar) / 2f, 0, 0xFFFFFFFF, false, m, buf, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0x80000000, GLOW);
        f.drawInBatch(txt, -f.width(txt) / 2f, -10, 0xFFFFFFFF, false, m, buf, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0x80000000, GLOW);
        pose.popPose();
    }

    // ─── Outils ───

    /** Bloc mis à l'échelle : coin (x, y, z), tailles (sx, sy, sz), en fractions de bloc. */
    private void cube(PoseStack pose, MultiBufferSource buf, int light, BlockState st,
                      float x, float y, float z, float sx, float sy, float sz) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        blocks.renderSingleBlock(st, pose, buf, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Bloc centré en (cx, cy, cz), taille s, tourné (Y puis X puis Z). */
    private void spun(PoseStack pose, MultiBufferSource buf, int light, BlockState st,
                      float cx, float cy, float cz, float s, float ry, float rx, float rz) {
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.mulPose(Axis.XP.rotationDegrees(rx));
        pose.mulPose(Axis.ZP.rotationDegrees(rz));
        pose.scale(s, s, s);
        pose.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(st, pose, buf, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Bloc centré, aplati (sx, sy, sz) et tourné autour de Y — pour les disques et segments qui pivotent. */
    private void spinBox(PoseStack pose, MultiBufferSource buf, int light, BlockState st,
                         float cx, float cy, float cz, float sx, float sy, float sz, float ry) {
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.scale(sx, sy, sz);
        pose.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(st, pose, buf, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Pyramide en escalier centrée sur (cx, cz) : largeur de départ w0, rétrécie de step à chaque étage. */
    private void stepRoof(PoseStack pose, MultiBufferSource buf, int light, BlockState st,
                          float cx, float y, float cz, float w0, float step, float h, int floors) {
        for (int i = 0; i < floors; i++) {
            float w = w0 - step * i;
            if (w <= 0.05f) break;
            cube(pose, buf, light, st, cx - w / 2, y + h * i, cz - w / 2, w, h, w);
        }
    }

    private void item(PoseStack pose, MultiBufferSource buf, int light, Level lvl, ItemStack st,
                      float x, float y, float z, float scale, float ry, float rz) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.mulPose(Axis.ZP.rotationDegrees(rz));
        pose.scale(scale, scale, scale);
        items.renderStatic(st, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buf, lvl, 0);
        pose.popPose();
    }

    // ─── Tour d'archers : tour de guet médiévale ───

    // ─── Tour d'archers 2×2, 8 blocs de haut : tour de guet élancée (empreinte x/z 0 à 2) ───

    private void archer(PoseStack pose, MultiBufferSource buf, int lt, int level, float t, Level lvl) {
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState dark = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState slit = Blocks.COAL_BLOCK.defaultBlockState();
        BlockState red = Blocks.RED_WOOL.defaultBlockState();
        float top = 6.7f; // plateforme (le corps solide monte jusqu'à 8)

        // Socle 2×2, assise moussue, fût élancé
        cube(pose, buf, lt, mossy, -0.02f, 0f, -0.02f, 2.04f, 1.0f, 2.04f);
        cube(pose, buf, lt, cobble, 0.05f, 1.0f, 0.05f, 1.9f, 0.3f, 1.9f);
        cube(pose, buf, lt, bricks, 0.15f, 1.3f, 0.15f, 1.7f, top - 1.4f, 1.7f);
        // Contreforts au pied de chaque face
        float[][] buttress = {{0.85f, -0.2f, 0.3f, 0.3f}, {0.85f, 1.9f, 0.3f, 0.3f}, {-0.2f, 0.85f, 0.3f, 0.3f}, {1.9f, 0.85f, 0.3f, 0.3f}};
        for (float[] b : buttress) {
            cube(pose, buf, lt, cobble, b[0], 1.0f, b[1], b[2], 1.2f, b[3]);
            cube(pose, buf, lt, cobble, b[0] + 0.03f, 2.2f, b[1] + 0.03f, b[2] - 0.06f, 0.6f, b[3] - 0.06f);
        }
        // Deux étages de meurtrières + bandeau de bois à mi-hauteur
        for (float sy : new float[]{2.4f, 4.6f}) {
            for (float o : new float[]{0.55f, 1.35f}) {
                cube(pose, buf, lt, slit, o, sy, 0.12f, 0.1f, 0.5f, 0.04f);
                cube(pose, buf, lt, slit, o, sy, 1.84f, 0.1f, 0.5f, 0.04f);
                cube(pose, buf, lt, slit, 0.12f, sy, o, 0.04f, 0.5f, 0.1f);
                cube(pose, buf, lt, slit, 1.84f, sy, o, 0.04f, 0.5f, 0.1f);
            }
        }
        cube(pose, buf, lt, dark, 0.1f, 3.8f, 0.1f, 1.8f, 0.12f, 1.8f);
        // Bannières déroulées le long du fût
        cube(pose, buf, lt, red, 0.3f, 3.0f, 0.1f, 0.18f, 1.6f, 0.03f);
        cube(pose, buf, lt, red, 1.52f, 3.0f, 1.87f, 0.18f, 1.6f, 0.03f);
        // Bandeau haut et consoles d'angle
        cube(pose, buf, lt, dark, 0.1f, top - 0.1f, 0.1f, 1.8f, 0.1f, 1.8f);
        for (float[] c : new float[][]{{-0.15f, -0.15f}, {1.95f, -0.15f}, {-0.15f, 1.95f}, {1.95f, 1.95f}}) {
            cube(pose, buf, lt, dark, c[0], top - 0.2f, c[1], 0.2f, 0.25f, 0.2f);
        }
        // Plateforme en surplomb + plancher + créneaux (6 par face)
        cube(pose, buf, lt, bricks, -0.35f, top, -0.35f, 2.7f, 0.15f, 2.7f);
        cube(pose, buf, lt, planks, -0.3f, top + 0.15f, -0.3f, 2.6f, 0.02f, 2.6f);
        for (int i = 0; i < 6; i++) {
            float o = -0.35f + i * 0.488f;
            cube(pose, buf, lt, bricks, o, top + 0.15f, -0.35f, 0.22f, 0.34f, 0.22f);
            cube(pose, buf, lt, bricks, o, top + 0.15f, 2.13f, 0.22f, 0.34f, 0.22f);
            cube(pose, buf, lt, bricks, -0.35f, top + 0.15f, o, 0.22f, 0.34f, 0.22f);
            cube(pose, buf, lt, bricks, 2.13f, top + 0.15f, o, 0.22f, 0.34f, 0.22f);
        }
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        for (float[] c : new float[][]{{-0.33f, -0.33f}, {2.15f, -0.33f}, {-0.33f, 2.15f}, {2.15f, 2.15f}}) {
            cube(pose, buf, GLOW, lantern, c[0], top + 0.49f, c[1], 0.18f, 0.18f, 0.18f);
        }
        item(pose, buf, lt, lvl, new ItemStack(Items.BOW), 1.0f, top + 0.75f, 1.0f, 1.2f, t * 0.8f, -45f);
        if (level >= 2) {
            BlockState fire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
            for (float[] c : new float[][]{{0.0f, 0.0f}, {1.7f, 0.0f}, {0.0f, 1.7f}, {1.7f, 1.7f}}) {
                cube(pose, buf, GLOW, fire, c[0], top + 0.17f, c[1], 0.3f, 0.3f, 0.3f);
            }
        }
        if (level >= 3) {
            for (float[] c : new float[][]{{-0.25f, -0.25f}, {2.13f, -0.25f}, {-0.25f, 2.13f}, {2.13f, 2.13f}}) {
                cube(pose, buf, lt, dark, c[0], top + 0.49f, c[1], 0.12f, 0.6f, 0.12f);
            }
            stepRoof(pose, buf, lt, Blocks.RED_TERRACOTTA.defaultBlockState(), 1.0f, top + 1.07f, 1.0f, 2.9f, 0.35f, 0.14f, 8);
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), 0.97f, top + 2.15f, 0.97f, 0.06f, 0.5f, 0.06f);
        }
    }

    /** Tour d'archers 2×2 basse (version précédente, conservée pour référence). */
    private void archerV2(PoseStack pose, MultiBufferSource buf, int lt, int level, float t, Level lvl) {
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState dark = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState slit = Blocks.COAL_BLOCK.defaultBlockState();
        BlockState red = Blocks.RED_WOOL.defaultBlockState();

        // Socle sur toute l'empreinte 2×2 (cache le bloc-socle) puis assise moussue
        cube(pose, buf, lt, mossy, -0.02f, 0f, -0.02f, 2.04f, 1.0f, 2.04f);
        cube(pose, buf, lt, cobble, 0.05f, 1.0f, 0.05f, 1.9f, 0.3f, 1.9f);
        // Fût
        cube(pose, buf, lt, bricks, 0.15f, 1.3f, 0.15f, 1.7f, 1.35f, 1.7f);
        // Contreforts au milieu de chaque face
        float[][] buttress = {{0.85f, -0.2f, 0.3f, 0.3f}, {0.85f, 1.9f, 0.3f, 0.3f}, {-0.2f, 0.85f, 0.3f, 0.3f}, {1.9f, 0.85f, 0.3f, 0.3f}};
        for (float[] b : buttress) {
            cube(pose, buf, lt, cobble, b[0], 1.0f, b[1], b[2], 0.6f, b[3]);
            cube(pose, buf, lt, cobble, b[0] + 0.03f, 1.6f, b[1] + 0.03f, b[2] - 0.06f, 0.4f, b[3] - 0.06f);
        }
        // Meurtrières (2 par face) et bannières
        for (float o : new float[]{0.55f, 1.35f}) {
            cube(pose, buf, lt, slit, o, 1.9f, 0.12f, 0.1f, 0.45f, 0.04f);
            cube(pose, buf, lt, slit, o, 1.9f, 1.84f, 0.1f, 0.45f, 0.04f);
            cube(pose, buf, lt, slit, 0.12f, 1.9f, o, 0.04f, 0.45f, 0.1f);
            cube(pose, buf, lt, slit, 1.84f, 1.9f, o, 0.04f, 0.45f, 0.1f);
        }
        cube(pose, buf, lt, red, 0.3f, 1.4f, 0.1f, 0.16f, 0.55f, 0.03f);
        cube(pose, buf, lt, red, 1.54f, 1.4f, 1.87f, 0.16f, 0.55f, 0.03f);
        // Bandeau de bois et consoles d'angle
        cube(pose, buf, lt, dark, 0.1f, 2.6f, 0.1f, 1.8f, 0.1f, 1.8f);
        for (float[] c : new float[][]{{-0.15f, -0.15f}, {1.95f, -0.15f}, {-0.15f, 1.95f}, {1.95f, 1.95f}}) {
            cube(pose, buf, lt, dark, c[0], 2.5f, c[1], 0.2f, 0.25f, 0.2f);
        }
        // Plateforme en surplomb + plancher + créneaux (6 par face)
        cube(pose, buf, lt, bricks, -0.35f, 2.7f, -0.35f, 2.7f, 0.15f, 2.7f);
        cube(pose, buf, lt, planks, -0.3f, 2.85f, -0.3f, 2.6f, 0.02f, 2.6f);
        for (int i = 0; i < 6; i++) {
            float o = -0.35f + i * 0.488f;
            cube(pose, buf, lt, bricks, o, 2.85f, -0.35f, 0.22f, 0.32f, 0.22f);
            cube(pose, buf, lt, bricks, o, 2.85f, 2.13f, 0.22f, 0.32f, 0.22f);
            cube(pose, buf, lt, bricks, -0.35f, 2.85f, o, 0.22f, 0.32f, 0.22f);
            cube(pose, buf, lt, bricks, 2.13f, 2.85f, o, 0.22f, 0.32f, 0.22f);
        }
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        for (float[] c : new float[][]{{-0.33f, -0.33f}, {2.15f, -0.33f}, {-0.33f, 2.15f}, {2.15f, 2.15f}}) {
            cube(pose, buf, GLOW, lantern, c[0], 3.17f, c[1], 0.18f, 0.18f, 0.18f);
        }
        // Grand arc qui pivote au centre de la plateforme
        item(pose, buf, lt, lvl, new ItemStack(Items.BOW), 1.0f, 3.4f, 1.0f, 1.2f, t * 0.8f, -45f);
        if (level >= 2) {
            BlockState fire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
            for (float[] c : new float[][]{{0.0f, 0.0f}, {1.7f, 0.0f}, {0.0f, 1.7f}, {1.7f, 1.7f}}) {
                cube(pose, buf, GLOW, fire, c[0], 2.87f, c[1], 0.3f, 0.3f, 0.3f);
            }
        }
        if (level >= 3) {
            for (float[] c : new float[][]{{-0.25f, -0.25f}, {2.13f, -0.25f}, {-0.25f, 2.13f}, {2.13f, 2.13f}}) {
                cube(pose, buf, lt, dark, c[0], 3.17f, c[1], 0.12f, 0.6f, 0.12f);
            }
            stepRoof(pose, buf, lt, Blocks.RED_TERRACOTTA.defaultBlockState(), 1.0f, 3.77f, 1.0f, 2.9f, 0.35f, 0.14f, 8);
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), 0.97f, 4.85f, 0.97f, 0.06f, 0.5f, 0.06f);
        }
    }

    /** Ancienne tour d'archers 1×1 (conservée pour référence). */
    private void archerV1(PoseStack pose, MultiBufferSource buf, int lt, int level, float t, Level lvl) {
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState dark = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState slit = Blocks.COAL_BLOCK.defaultBlockState();

        cube(pose, buf, lt, mossy, 0.05f, 1.0f, 0.05f, 0.9f, 0.3f, 0.9f);
        cube(pose, buf, lt, bricks, 0.1f, 1.3f, 0.1f, 0.8f, 1.35f, 0.8f);
        float[][] buttress = {{0.38f, -0.18f, 0.24f, 0.28f}, {0.38f, 0.9f, 0.24f, 0.28f}, {-0.18f, 0.38f, 0.28f, 0.24f}, {0.9f, 0.38f, 0.28f, 0.24f}};
        for (float[] b : buttress) {
            cube(pose, buf, lt, cobble, b[0], 1.0f, b[1], b[2], 0.55f, b[3]);
            cube(pose, buf, lt, cobble, b[0] + 0.03f, 1.55f, b[1] + 0.03f, b[2] - 0.06f, 0.35f, b[3] - 0.06f);
        }
        cube(pose, buf, lt, slit, 0.45f, 1.9f, 0.07f, 0.1f, 0.4f, 0.04f);
        cube(pose, buf, lt, slit, 0.45f, 1.9f, 0.89f, 0.1f, 0.4f, 0.04f);
        cube(pose, buf, lt, slit, 0.07f, 1.9f, 0.45f, 0.04f, 0.4f, 0.1f);
        cube(pose, buf, lt, slit, 0.89f, 1.9f, 0.45f, 0.04f, 0.4f, 0.1f);
        BlockState red = Blocks.RED_WOOL.defaultBlockState();
        cube(pose, buf, lt, red, 0.28f, 1.35f, 0.05f, 0.14f, 0.5f, 0.03f);
        cube(pose, buf, lt, red, 0.58f, 1.35f, 0.92f, 0.14f, 0.5f, 0.03f);
        cube(pose, buf, lt, dark, 0.06f, 2.6f, 0.06f, 0.88f, 0.1f, 0.88f);
        float[][] corbels = {{-0.12f, -0.12f}, {0.92f, -0.12f}, {-0.12f, 0.92f}, {0.92f, 0.92f}};
        for (float[] c : corbels) cube(pose, buf, lt, dark, c[0], 2.5f, c[1], 0.2f, 0.25f, 0.2f);
        cube(pose, buf, lt, bricks, -0.3f, 2.7f, -0.3f, 1.6f, 0.15f, 1.6f);
        cube(pose, buf, lt, planks, -0.25f, 2.85f, -0.25f, 1.5f, 0.02f, 1.5f);
        for (int i = 0; i < 4; i++) {
            float o = -0.3f + i * 0.45f;
            cube(pose, buf, lt, bricks, o, 2.85f, -0.3f, 0.22f, 0.32f, 0.22f);
            cube(pose, buf, lt, bricks, o, 2.85f, 1.08f, 0.22f, 0.32f, 0.22f);
            cube(pose, buf, lt, bricks, -0.3f, 2.85f, o, 0.22f, 0.32f, 0.22f);
            cube(pose, buf, lt, bricks, 1.08f, 2.85f, o, 0.22f, 0.32f, 0.22f);
        }
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        for (float[] c : new float[][]{{-0.28f, -0.28f}, {1.1f, -0.28f}, {-0.28f, 1.1f}, {1.1f, 1.1f}}) {
            cube(pose, buf, GLOW, lantern, c[0], 3.17f, c[1], 0.18f, 0.18f, 0.18f);
        }
        item(pose, buf, lt, lvl, new ItemStack(Items.BOW), 0.5f, 3.35f, 0.5f, 0.95f, t * 0.8f, -45f);
        if (level >= 2) {
            BlockState fire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
            cube(pose, buf, GLOW, fire, 0.05f, 2.87f, 0.05f, 0.3f, 0.3f, 0.3f);
            cube(pose, buf, GLOW, fire, 0.65f, 2.87f, 0.65f, 0.3f, 0.3f, 0.3f);
        }
        if (level >= 3) {
            for (float[] c : new float[][]{{-0.2f, -0.2f}, {1.05f, -0.2f}, {-0.2f, 1.05f}, {1.05f, 1.05f}}) {
                cube(pose, buf, lt, dark, c[0], 3.17f, c[1], 0.12f, 0.55f, 0.12f);
            }
            stepRoof(pose, buf, lt, Blocks.RED_TERRACOTTA.defaultBlockState(), 0.5f, 3.72f, 0.5f, 1.7f, 0.3f, 0.14f, 6);
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), 0.47f, 4.5f, 0.47f, 0.06f, 0.45f, 0.06f);
        }
    }

    // ─── Tour de mage : « Arcanum en lévitation » ───

    private void mage(PoseStack pose, MultiBufferSource buf, int lt, int level, float t, Level lvl) {
        // Dais runique à 2 gradins
        cube(pose, buf, lt, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), -0.15f, 1.0f, -0.15f, 1.3f, 0.12f, 1.3f);
        cube(pose, buf, lt, Blocks.PURPUR_BLOCK.defaultBlockState(), 0.05f, 1.12f, 0.05f, 0.9f, 0.1f, 0.9f);
        // 4 petits obélisques d'angle avec une pointe lumineuse
        for (float[] c : new float[][]{{-0.15f, -0.15f}, {0.99f, -0.15f}, {-0.15f, 0.99f}, {0.99f, 0.99f}}) {
            cube(pose, buf, lt, Blocks.DEEPSLATE_TILES.defaultBlockState(), c[0], 1.12f, c[1], 0.16f, 0.55f, 0.16f);
            cube(pose, buf, GLOW, Blocks.AMETHYST_BLOCK.defaultBlockState(), c[0] + 0.03f, 1.67f, c[1] + 0.03f, 0.1f, 0.12f, 0.1f);
        }
        // 2 cercles magiques au sol qui tournent en sens inverse
        spinBox(pose, buf, GLOW, Blocks.PURPLE_STAINED_GLASS.defaultBlockState(), 0.5f, 1.23f, 0.5f, 1.25f, 0.02f, 1.25f, t * 1.2f);
        spinBox(pose, buf, GLOW, Blocks.MAGENTA_STAINED_GLASS.defaultBlockState(), 0.5f, 1.25f, 0.5f, 0.8f, 0.02f, 0.8f, -t * 2f);
        // 3 blocs en lévitation, tournant en sens alternés, avec un léger flottement
        float bob = Mth.sin(t * 0.08f) * 0.06f;
        spinBox(pose, buf, lt, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 0.5f, 1.45f + bob, 0.5f, 0.7f, 0.6f, 0.7f, t * 0.6f);
        spinBox(pose, buf, GLOW, Blocks.AMETHYST_BLOCK.defaultBlockState(), 0.5f, 2.2f - bob, 0.5f, 0.55f, 0.5f, 0.55f, -t * 1.0f);
        spinBox(pose, buf, lt, Blocks.PURPUR_PILLAR.defaultBlockState(), 0.5f, 2.85f + bob, 0.5f, 0.4f, 0.4f, 0.4f, t * 1.6f);
        // Livre enchanté flottant au sommet
        item(pose, buf, GLOW, lvl, new ItemStack(Items.ENCHANTED_BOOK), 0.5f, 3.65f + bob * 2, 0.5f, 0.9f, t * 2.5f, 0f);
        // Anneaux de runes (1 par niveau), chacun incliné différemment
        float[] tilt = {0f, 25f, -25f};
        float[] ringY = {2.3f, 2.6f, 1.95f};
        for (int r = 0; r < level; r++) {
            pose.pushPose();
            pose.translate(0.5, ringY[r], 0.5);
            pose.mulPose(Axis.XP.rotationDegrees(tilt[r]));
            pose.mulPose(Axis.YP.rotationDegrees((r % 2 == 0 ? 1 : -1) * t * (2.5f + r)));
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45f);
                float ox = (float) Math.cos(a) * 0.95f, oz = (float) Math.sin(a) * 0.95f;
                spun(pose, buf, GLOW, Blocks.AMETHYST_BLOCK.defaultBlockState(), ox, 0f, oz, 0.13f, t * 5f, 45f, 45f);
            }
            pose.popPose();
        }
    }

    // ─── Sanctuaire : « Autel du Renouveau » ───

    private void shrine(PoseStack pose, MultiBufferSource buf, int lt, int level, float t, Level lvl) {
        BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();
        BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
        // Socle moussu + marches de quartz
        cube(pose, buf, lt, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), -0.2f, 1.0f, -0.2f, 1.4f, 0.12f, 1.4f);
        cube(pose, buf, lt, quartz, -0.05f, 1.12f, -0.05f, 1.1f, 0.1f, 1.1f);
        cube(pose, buf, lt, Blocks.SMOOTH_QUARTZ.defaultBlockState(), 0.1f, 1.22f, 0.1f, 0.8f, 0.08f, 0.8f);
        // 4 colonnes de quartz coiffées d'une lanterne marine et d'azalée en fleurs
        for (float[] c : new float[][]{{-0.15f, -0.15f}, {0.97f, -0.15f}, {-0.15f, 0.97f}, {0.97f, 0.97f}}) {
            cube(pose, buf, lt, pillar, c[0], 1.12f, c[1], 0.18f, 1.25f, 0.18f);
            cube(pose, buf, lt, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), c[0] - 0.03f, 2.37f, c[1] - 0.03f, 0.24f, 0.1f, 0.24f);
            cube(pose, buf, GLOW, Blocks.SEA_LANTERN.defaultBlockState(), c[0] + 0.01f, 2.47f, c[1] + 0.01f, 0.16f, 0.16f, 0.16f);
            cube(pose, buf, lt, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(), c[0] - 0.06f, 2.63f, c[1] - 0.06f, 0.3f, 0.2f, 0.3f);
        }
        // Piédestal central
        cube(pose, buf, lt, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), 0.32f, 1.3f, 0.32f, 0.36f, 0.35f, 0.36f);
        cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), 0.28f, 1.65f, 0.28f, 0.44f, 0.06f, 0.44f);
        // Rayon de lumière vert
        cube(pose, buf, GLOW, Blocks.LIME_STAINED_GLASS.defaultBlockState(), 0.44f, 1.71f, 0.44f, 0.12f, 2.4f, 0.12f);
        // Totem d'immortalité géant qui flotte et tourne
        float bob = Mth.sin(t * 0.07f) * 0.1f;
        item(pose, buf, GLOW, lvl, new ItemStack(Items.TOTEM_OF_UNDYING), 0.5f, 2.35f + bob, 0.5f, 1.3f, t * 2f, 0f);
        // Pommes dorées en orbite (1 par niveau)
        for (int i = 0; i < level; i++) {
            double a = Math.toRadians(-t * 2.5f + i * 120f);
            float ox = 0.5f + (float) Math.cos(a) * 0.75f, oz = 0.5f + (float) Math.sin(a) * 0.75f;
            item(pose, buf, GLOW, lvl, new ItemStack(Items.GOLDEN_APPLE), ox, 2.3f + Mth.sin(t * 0.1f + i) * 0.12f, oz, 0.45f, t * 4f, 0f);
        }
    }

    // ─── Tour du Glaneur : tonneau, trémie, anneaux magnétiques en cuivre, magnétite flottante ───

    /** Atelier de Réparation (prototype) : table de forgeron (bloc principal, modèle du bloc) + enclume, meule et brasero sur le 2e bloc. */
    private void workshop(PoseStack pose, MultiBufferSource buf, int lt, int level, float t) {
        // 2e bloc (vers l'est) : socle de pierre, enclume, petite meule
        cube(pose, buf, lt, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 1.0f, 0.0f, 0.0f, 1.0f, 0.12f, 1.0f);
        cube(pose, buf, lt, Blocks.ANVIL.defaultBlockState(), 1.08f, 0.12f, 0.12f, 0.84f, 0.62f, 0.76f);
        cube(pose, buf, lt, Blocks.GRINDSTONE.defaultBlockState(), 1.62f, 0.12f, 0.62f, 0.34f, 0.34f, 0.34f);
        // Brasero sur la table : rougeoie et palpite
        float glow = 0.22f + Mth.sin(t * 0.25f) * 0.03f;
        cube(pose, buf, lt, Blocks.BLACKSTONE.defaultBlockState(), 0.32f, 1.0f, 0.32f, 0.36f, 0.08f, 0.36f);
        cube(pose, buf, 0xF000F0, Blocks.MAGMA_BLOCK.defaultBlockState(), 0.5f - glow / 2, 1.08f, 0.5f - glow / 2, glow, 0.1f, glow);
        // Niveau : rivets dorés sur l'enclume (1 par niveau)
        for (int i = 0; i < Math.min(3, level); i++) {
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), 1.2f + i * 0.22f, 0.75f, 0.45f, 0.1f, 0.06f, 0.1f);
        }
    }

    private void collector(PoseStack pose, MultiBufferSource buf, int lt, int level, float t) {
        cube(pose, buf, lt, Blocks.STONE_BRICKS.defaultBlockState(), 0.0f, 1.0f, 0.0f, 1.0f, 0.15f, 1.0f);
        cube(pose, buf, lt, Blocks.BARREL.defaultBlockState(), 0.12f, 1.15f, 0.12f, 0.76f, 0.75f, 0.76f);
        for (float[] c : new float[][]{{0.0f, 0.0f}, {0.88f, 0.0f}, {0.0f, 0.88f}, {0.88f, 0.88f}}) {
            cube(pose, buf, lt, Blocks.DARK_OAK_LOG.defaultBlockState(), c[0], 1.15f, c[1], 0.12f, 1.55f, 0.12f);
        }
        cube(pose, buf, lt, Blocks.HOPPER.defaultBlockState(), 0.1f, 1.9f, 0.1f, 0.8f, 0.8f, 0.8f);
        cube(pose, buf, lt, Blocks.CUT_COPPER.defaultBlockState(), -0.05f, 2.7f, -0.05f, 1.1f, 0.1f, 1.1f);
        // Anneaux magnétiques en cuivre (1 par niveau), sens alternés
        for (int r = 0; r < level; r++) {
            float ry = 2.05f + r * 0.32f;
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians((r % 2 == 0 ? 1 : -1) * t * (3f + r) + i * 45f);
                spun(pose, buf, lt, Blocks.WAXED_COPPER_BLOCK.defaultBlockState(), 0.5f + (float) Math.cos(a) * 0.85f, ry,
                        0.5f + (float) Math.sin(a) * 0.85f, 0.15f, t * 4f, 45f, 45f);
            }
        }
        // Magnétite flottante au sommet
        float bob = Mth.sin(t * 0.08f) * 0.08f;
        spun(pose, buf, lt, Blocks.LODESTONE.defaultBlockState(), 0.5f, 3.2f + bob, 0.5f, 0.42f, t * 2f, 0f, 0f);
    }

    // ─── Baraquement : garnison 3×3×3 (de -1 à +2 autour du socle) ───

    // ─── Baraquement 6×4, 5 blocs de haut (empreinte x de -1 à 5, z de -1 à 3) ───

    private void barracks(PoseStack pose, MultiBufferSource buf, int lt, int level, Level lvl) {
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState wall = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState roof = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState glowWin = Blocks.SHROOMLIGHT.defaultBlockState();
        float x0 = -1f, z0 = -1f, lx = 6f, lz = 4f, cx = 2f, cz = 1f, wallTop = 3.3f;

        // Fondation, soubassement, murs sur 2 étages (plancher à mi-hauteur)
        cube(pose, buf, lt, cobble, x0 - 0.05f, 0.0f, z0 - 0.05f, lx + 0.1f, 0.2f, lz + 0.1f);
        cube(pose, buf, lt, stone, x0 + 0.05f, 0.2f, z0 + 0.05f, lx - 0.1f, 0.6f, lz - 0.1f);
        cube(pose, buf, lt, wall, x0 + 0.1f, 0.8f, z0 + 0.1f, lx - 0.2f, wallTop - 0.8f, lz - 0.2f);
        cube(pose, buf, lt, log, x0, 2.0f, z0, lx, 0.12f, lz);
        // Poteaux d'angle et colombages
        for (float[] p : new float[][]{{x0, z0}, {x0 + lx - 0.22f, z0}, {x0, z0 + lz - 0.22f}, {x0 + lx - 0.22f, z0 + lz - 0.22f}}) {
            cube(pose, buf, lt, log, p[0], 0.2f, p[1], 0.22f, wallTop - 0.2f, 0.22f);
        }
        for (int i = 1; i < 6; i++) {
            float f = x0 + i;
            cube(pose, buf, lt, log, f - 0.07f, 0.8f, z0 + 0.05f, 0.14f, wallTop - 0.8f, 0.06f);
            cube(pose, buf, lt, log, f - 0.07f, 0.8f, z0 + lz - 0.11f, 0.14f, wallTop - 0.8f, 0.06f);
        }
        for (int i = 1; i < 4; i++) {
            float f = z0 + i;
            cube(pose, buf, lt, log, x0 + 0.05f, 0.8f, f - 0.07f, 0.06f, wallTop - 0.8f, 0.14f);
            cube(pose, buf, lt, log, x0 + lx - 0.11f, 0.8f, f - 0.07f, 0.06f, wallTop - 0.8f, 0.14f);
        }
        cube(pose, buf, lt, log, x0, wallTop, z0, lx, 0.15f, lz);
        // Grande double porte au centre de la façade
        cube(pose, buf, lt, log, cx - 0.65f, 0.2f, z0 + 0.02f, 1.3f, 1.7f, 0.06f);
        cube(pose, buf, lt, roof, cx - 0.57f, 0.2f, z0 - 0.01f, 0.55f, 1.6f, 0.05f);
        cube(pose, buf, lt, roof, cx + 0.02f, 0.2f, z0 - 0.01f, 0.55f, 1.6f, 0.05f);
        // Fenêtres éclairées sur 2 étages (façade, arrière, côtés)
        for (float wy : new float[]{1.1f, 2.4f}) {
            for (float wx : new float[]{x0 + 0.5f, x0 + lx - 0.95f}) {
                cube(pose, buf, GLOW, glowWin, wx, wy, z0 + 0.06f, 0.45f, 0.5f, 0.03f);
                cube(pose, buf, GLOW, glowWin, wx, wy, z0 + lz - 0.09f, 0.45f, 0.5f, 0.03f);
            }
            if (wy > 2f) cube(pose, buf, GLOW, glowWin, cx - 0.22f, wy, z0 + 0.06f, 0.45f, 0.5f, 0.03f);
            cube(pose, buf, GLOW, glowWin, x0 + 0.06f, wy, cz - 0.22f, 0.03f, 0.5f, 0.45f);
            cube(pose, buf, GLOW, glowWin, x0 + lx - 0.09f, wy, cz - 0.22f, 0.03f, 0.5f, 0.45f);
        }
        // Toit à deux pans (faîtage selon la longueur), pignons
        for (int i = 0; i < 8; i++) {
            float d = 2.15f - i * 0.3f;
            if (d <= 0.05f) break;
            cube(pose, buf, lt, roof, x0 - 0.15f, wallTop + 0.15f + i * 0.2f, cz - d, lx + 0.3f, 0.2f, d * 2);
            float g = d - 0.2f;
            if (g > 0.05f) {
                cube(pose, buf, lt, wall, x0 + 0.1f, wallTop + 0.15f + i * 0.2f, cz - g, 0.06f, 0.2f, g * 2);
                cube(pose, buf, lt, wall, x0 + lx - 0.16f, wallTop + 0.15f + i * 0.2f, cz - g, 0.06f, 0.2f, g * 2);
            }
        }
        // Cheminée haute, lanternes, bouclier et épées croisées au-dessus de la porte
        cube(pose, buf, lt, cobble, x0 + lx - 0.9f, 2.6f, z0 + lz - 0.8f, 0.45f, 3.2f, 0.45f);
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        cube(pose, buf, GLOW, lantern, cx - 1.0f, 1.6f, z0 - 0.25f, 0.3f, 0.3f, 0.3f);
        cube(pose, buf, GLOW, lantern, cx + 0.7f, 1.6f, z0 - 0.25f, 0.3f, 0.3f, 0.3f);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        item(pose, buf, lt, lvl, sword, cx, 2.25f, z0 - 0.06f, 0.85f, 0f, 45f);
        item(pose, buf, lt, lvl, sword, cx, 2.25f, z0 - 0.06f, 0.85f, 0f, -45f);
        item(pose, buf, lt, lvl, new ItemStack(Items.SHIELD), cx, 2.25f, z0 - 0.1f, 0.65f, 180f, 0f);
        if (level >= 2) {
            BlockState redW = Blocks.RED_WOOL.defaultBlockState();
            cube(pose, buf, lt, redW, x0 + 0.3f, 0.4f, z0 - 0.02f, 0.45f, 2.6f, 0.03f);
            cube(pose, buf, lt, redW, x0 + lx - 0.75f, 0.4f, z0 - 0.02f, 0.45f, 2.6f, 0.03f);
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), x0 + 0.46f, 1.3f, z0 - 0.04f, 0.13f, 0.13f, 0.02f);
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), x0 + lx - 0.59f, 1.3f, z0 - 0.04f, 0.13f, 0.13f, 0.02f);
        }
        if (level >= 3) {
            float ridge = wallTop + 0.15f + 7 * 0.2f + 0.2f;
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), x0 - 0.15f, ridge, cz - 0.08f, lx + 0.3f, 0.08f, 0.16f);
            cube(pose, buf, lt, log, x0 + 0.2f, ridge + 0.05f, cz - 0.04f, 0.08f, 1.1f, 0.08f);
            cube(pose, buf, lt, Blocks.RED_WOOL.defaultBlockState(), x0 + 0.28f, ridge + 0.75f, cz - 0.03f, 0.6f, 0.35f, 0.03f);
        }
    }

    /** Baraquement 4×4 (version précédente, conservée pour référence). */
    private void barracksV2(PoseStack pose, MultiBufferSource buf, int lt, int level, Level lvl) {
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState wall = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState roof = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState glowWin = Blocks.SHROOMLIGHT.defaultBlockState();
        float x0 = -1f, z0 = -1f, w = 4f, c = 1f;

        // Fondation, soubassement de pierre, murs de sapin (plus hauts)
        cube(pose, buf, lt, cobble, x0 - 0.05f, 0.0f, z0 - 0.05f, w + 0.1f, 0.2f, w + 0.1f);
        cube(pose, buf, lt, stone, x0 + 0.05f, 0.2f, z0 + 0.05f, w - 0.1f, 0.6f, w - 0.1f);
        cube(pose, buf, lt, wall, x0 + 0.1f, 0.8f, z0 + 0.1f, w - 0.2f, 1.3f, w - 0.2f);
        // Poteaux d'angle et colombages (3 par face)
        for (float[] p : new float[][]{{x0, z0}, {x0 + w - 0.22f, z0}, {x0, z0 + w - 0.22f}, {x0 + w - 0.22f, z0 + w - 0.22f}}) {
            cube(pose, buf, lt, log, p[0], 0.2f, p[1], 0.22f, 2.0f, 0.22f);
        }
        for (float f : new float[]{1.0f, 2.0f, 3.0f}) {
            cube(pose, buf, lt, log, x0 + f - 0.07f, 0.8f, z0 + 0.05f, 0.14f, 1.3f, 0.06f);
            cube(pose, buf, lt, log, x0 + f - 0.07f, 0.8f, z0 + w - 0.11f, 0.14f, 1.3f, 0.06f);
            cube(pose, buf, lt, log, x0 + 0.05f, 0.8f, z0 + f - 0.07f, 0.06f, 1.3f, 0.14f);
            cube(pose, buf, lt, log, x0 + w - 0.11f, 0.8f, z0 + f - 0.07f, 0.06f, 1.3f, 0.14f);
        }
        cube(pose, buf, lt, log, x0, 2.1f, z0, w, 0.15f, w);
        // Grande double porte (au centre de la façade)
        cube(pose, buf, lt, log, c - 0.6f, 0.2f, z0 + 0.02f, 1.2f, 1.6f, 0.06f);
        cube(pose, buf, lt, roof, c - 0.52f, 0.2f, z0 - 0.01f, 0.5f, 1.5f, 0.05f);
        cube(pose, buf, lt, roof, c + 0.02f, 0.2f, z0 - 0.01f, 0.5f, 1.5f, 0.05f);
        // 6 fenêtres éclairées
        cube(pose, buf, GLOW, glowWin, x0 + 0.35f, 1.2f, z0 + 0.06f, 0.45f, 0.5f, 0.03f);
        cube(pose, buf, GLOW, glowWin, x0 + w - 0.8f, 1.2f, z0 + 0.06f, 0.45f, 0.5f, 0.03f);
        cube(pose, buf, GLOW, glowWin, x0 + 0.06f, 1.2f, c - 0.22f, 0.03f, 0.5f, 0.45f);
        cube(pose, buf, GLOW, glowWin, x0 + w - 0.09f, 1.2f, c - 0.22f, 0.03f, 0.5f, 0.45f);
        cube(pose, buf, GLOW, glowWin, x0 + 0.6f, 1.2f, z0 + w - 0.09f, 0.45f, 0.5f, 0.03f);
        cube(pose, buf, GLOW, glowWin, x0 + w - 1.05f, 1.2f, z0 + w - 0.09f, 0.45f, 0.5f, 0.03f);
        // Toit à deux pans profond (faîtage selon X, centré en Z) et pignons
        for (int i = 0; i < 8; i++) {
            float d = 2.15f - i * 0.3f;
            if (d <= 0.05f) break;
            cube(pose, buf, lt, roof, x0 - 0.15f, 2.25f + i * 0.2f, c - d, w + 0.3f, 0.2f, d * 2);
            float g = d - 0.2f;
            if (g > 0.05f) {
                cube(pose, buf, lt, wall, x0 + 0.1f, 2.25f + i * 0.2f, c - g, 0.06f, 0.2f, g * 2);
                cube(pose, buf, lt, wall, x0 + w - 0.16f, 2.25f + i * 0.2f, c - g, 0.06f, 0.2f, g * 2);
            }
        }
        // Cheminée haute, lanternes, bouclier et épées croisées au-dessus de la porte
        cube(pose, buf, lt, cobble, x0 + w - 0.8f, 1.6f, z0 + w - 0.8f, 0.4f, 2.6f, 0.4f);
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        cube(pose, buf, GLOW, lantern, c - 0.95f, 1.5f, z0 - 0.25f, 0.3f, 0.3f, 0.3f);
        cube(pose, buf, GLOW, lantern, c + 0.65f, 1.5f, z0 - 0.25f, 0.3f, 0.3f, 0.3f);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        item(pose, buf, lt, lvl, sword, c, 2.15f, z0 - 0.06f, 0.8f, 0f, 45f);
        item(pose, buf, lt, lvl, sword, c, 2.15f, z0 - 0.06f, 0.8f, 0f, -45f);
        item(pose, buf, lt, lvl, new ItemStack(Items.SHIELD), c, 2.15f, z0 - 0.1f, 0.6f, 180f, 0f);
        if (level >= 2) {
            BlockState red = Blocks.RED_WOOL.defaultBlockState();
            cube(pose, buf, lt, red, x0 + 0.3f, 0.35f, z0 - 0.02f, 0.4f, 1.3f, 0.03f);
            cube(pose, buf, lt, red, x0 + w - 0.7f, 0.35f, z0 - 0.02f, 0.4f, 1.3f, 0.03f);
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), x0 + 0.44f, 0.85f, z0 - 0.04f, 0.12f, 0.12f, 0.02f);
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), x0 + w - 0.56f, 0.85f, z0 - 0.04f, 0.12f, 0.12f, 0.02f);
        }
        if (level >= 3) {
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), x0 - 0.15f, 3.75f, c - 0.08f, w + 0.3f, 0.08f, 0.16f);
            cube(pose, buf, lt, log, x0 + 0.2f, 3.8f, c - 0.04f, 0.08f, 1.1f, 0.08f);
            cube(pose, buf, lt, Blocks.RED_WOOL.defaultBlockState(), x0 + 0.28f, 4.5f, c - 0.03f, 0.6f, 0.35f, 0.03f);
        }
    }

    /** Ancien baraquement 3×3 (conservé pour référence). */
    private void barracksV1(PoseStack pose, MultiBufferSource buf, int lt, int level, Level lvl) {
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState wall = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState roof = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState glowWin = Blocks.SHROOMLIGHT.defaultBlockState();
        float x0 = -1f, z0 = -1f, w = 3f;

        cube(pose, buf, lt, cobble, x0 - 0.05f, 0.0f, z0 - 0.05f, w + 0.1f, 0.2f, w + 0.1f);
        cube(pose, buf, lt, stone, x0 + 0.05f, 0.2f, z0 + 0.05f, w - 0.1f, 0.6f, w - 0.1f);
        cube(pose, buf, lt, wall, x0 + 0.1f, 0.8f, z0 + 0.1f, w - 0.2f, 1.1f, w - 0.2f);
        for (float[] c : new float[][]{{x0, z0}, {x0 + w - 0.22f, z0}, {x0, z0 + w - 0.22f}, {x0 + w - 0.22f, z0 + w - 0.22f}}) {
            cube(pose, buf, lt, log, c[0], 0.2f, c[1], 0.22f, 1.75f, 0.22f);
        }
        for (float f : new float[]{0.95f, 2.05f}) {
            cube(pose, buf, lt, log, x0 + f - 0.07f, 0.8f, z0 + 0.05f, 0.14f, 1.1f, 0.06f);
            cube(pose, buf, lt, log, x0 + f - 0.07f, 0.8f, z0 + w - 0.11f, 0.14f, 1.1f, 0.06f);
            cube(pose, buf, lt, log, x0 + 0.05f, 0.8f, z0 + f - 0.07f, 0.06f, 1.1f, 0.14f);
            cube(pose, buf, lt, log, x0 + w - 0.11f, 0.8f, z0 + f - 0.07f, 0.06f, 1.1f, 0.14f);
        }
        cube(pose, buf, lt, log, x0, 1.85f, z0, w, 0.15f, w);
        cube(pose, buf, lt, log, 0.05f, 0.2f, z0 + 0.02f, 0.9f, 1.4f, 0.06f);
        cube(pose, buf, lt, roof, 0.12f, 0.2f, z0 - 0.01f, 0.36f, 1.28f, 0.05f);
        cube(pose, buf, lt, roof, 0.52f, 0.2f, z0 - 0.01f, 0.36f, 1.28f, 0.05f);
        cube(pose, buf, GLOW, glowWin, x0 + 0.35f, 1.05f, z0 + 0.06f, 0.4f, 0.45f, 0.03f);
        cube(pose, buf, GLOW, glowWin, x0 + 2.25f, 1.05f, z0 + 0.06f, 0.4f, 0.45f, 0.03f);
        cube(pose, buf, GLOW, glowWin, x0 + 0.06f, 1.05f, 0.3f, 0.03f, 0.45f, 0.4f);
        cube(pose, buf, GLOW, glowWin, x0 + w - 0.09f, 1.05f, 0.3f, 0.03f, 0.45f, 0.4f);
        cube(pose, buf, GLOW, glowWin, 0.3f, 1.05f, z0 + w - 0.09f, 0.4f, 0.45f, 0.03f);
        for (int i = 0; i < 6; i++) {
            float d = 1.65f - i * 0.27f;
            if (d <= 0.05f) break;
            cube(pose, buf, lt, roof, x0 - 0.15f, 2.0f + i * 0.2f, 0.5f - d, w + 0.3f, 0.2f, d * 2);
            float g = d - 0.2f;
            if (g > 0.05f) {
                cube(pose, buf, lt, wall, x0 + 0.1f, 2.0f + i * 0.2f, 0.5f - g, 0.06f, 0.2f, g * 2);
                cube(pose, buf, lt, wall, x0 + w - 0.16f, 2.0f + i * 0.2f, 0.5f - g, 0.06f, 0.2f, g * 2);
            }
        }
        cube(pose, buf, lt, cobble, x0 + 2.3f, 1.4f, z0 + 2.2f, 0.35f, 2.1f, 0.35f);
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        cube(pose, buf, GLOW, lantern, -0.32f, 1.25f, z0 - 0.25f, 0.3f, 0.3f, 0.3f);
        cube(pose, buf, GLOW, lantern, 1.02f, 1.25f, z0 - 0.25f, 0.3f, 0.3f, 0.3f);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        item(pose, buf, lt, lvl, sword, 0.5f, 1.85f, z0 - 0.06f, 0.7f, 0f, 45f);
        item(pose, buf, lt, lvl, sword, 0.5f, 1.85f, z0 - 0.06f, 0.7f, 0f, -45f);
        item(pose, buf, lt, lvl, new ItemStack(Items.SHIELD), 0.5f, 1.85f, z0 - 0.1f, 0.55f, 180f, 0f);
        if (level >= 2) {
            BlockState red = Blocks.RED_WOOL.defaultBlockState();
            cube(pose, buf, lt, red, x0 + 0.3f, 0.35f, z0 - 0.02f, 0.35f, 1.1f, 0.03f);
            cube(pose, buf, lt, red, x0 + 2.35f, 0.35f, z0 - 0.02f, 0.35f, 1.1f, 0.03f);
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), x0 + 0.42f, 0.75f, z0 - 0.04f, 0.11f, 0.11f, 0.02f);
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), x0 + 2.47f, 0.75f, z0 - 0.04f, 0.11f, 0.11f, 0.02f);
        }
        if (level >= 3) {
            cube(pose, buf, lt, Blocks.GOLD_BLOCK.defaultBlockState(), x0 - 0.15f, 3.2f, 0.42f, w + 0.3f, 0.08f, 0.16f);
            cube(pose, buf, lt, log, x0 + 0.2f, 3.25f, 0.46f, 0.08f, 1.0f, 0.08f);
            cube(pose, buf, lt, Blocks.RED_WOOL.defaultBlockState(), x0 + 0.28f, 3.9f, 0.47f, 0.5f, 0.3f, 0.03f);
        }
    }
}
