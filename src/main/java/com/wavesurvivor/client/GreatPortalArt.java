package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wavesurvivor.entity.BrecheEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * PORTES DU MODE KINGDOM — rendu monumental des grands portails (≈ 14 blocs de haut, 12 de large) et du Catalyseur.
 * Les brèches classiques des hordes ne changent pas (elles gardent le rendu de BrecheRenderer).
 * 6 THÈMES (choisis selon la horde ou dans l'éditeur) : abîme, feu, os, End, océan, arcane.
 *  - Fondation, 2 piliers colossaux veinés de lumière, chapiteaux + pics, arche à gradins couronnée.
 *  - Faille d'énergie qui RÉTRÉCIT avec les PV, objet géant qui tourne au centre.
 *  - Chaînes + lanternes, feux au pied des piliers, 6 éclats en orbite. Sous 50 % de PV, la pierre se fissure.
 * CATALYSEUR : gros cristal flottant dans une coque de verre, éclats en orbite, rayon de lumière visible de loin.
 */
@OnlyIn(Dist.CLIENT)
public class GreatPortalArt {

    private static final int GLOW = LightTexture.FULL_BRIGHT;

    /** Matériaux d'un thème de Porte. */
    private record Theme(BlockState platform, BlockState cracked, BlockState pillar, BlockState vein, BlockState capital,
                         BlockState arch, BlockState spike, BlockState fire, BlockState lantern, BlockState rift,
                         Item crown, Item eye, BlockState orbit) {}

    private static Theme theme(String id) {
        BlockState soulFire = Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
        BlockState fire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
        BlockState soulLantern = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        BlockState drip = Blocks.POINTED_DRIPSTONE.defaultBlockState();
        return switch (id == null ? "abyss" : id) {
            case "fire" -> new Theme(Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.CRACKED_NETHER_BRICKS.defaultBlockState(),
                    Blocks.BLACKSTONE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState(),
                    Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.BASALT.defaultBlockState(), fire, lantern,
                    Blocks.ORANGE_STAINED_GLASS.defaultBlockState(), Items.FIRE_CHARGE, Items.BLAZE_POWDER, Blocks.MAGMA_BLOCK.defaultBlockState());
            case "bone" -> new Theme(Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(),
                    Blocks.BONE_BLOCK.defaultBlockState(), Blocks.SCULK.defaultBlockState(), Blocks.CHISELED_DEEPSLATE.defaultBlockState(),
                    Blocks.BONE_BLOCK.defaultBlockState(), drip, soulFire, soulLantern,
                    Blocks.CYAN_STAINED_GLASS.defaultBlockState(), Items.SKELETON_SKULL, Items.BONE, Blocks.BONE_BLOCK.defaultBlockState());
            case "end" -> new Theme(Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.END_STONE.defaultBlockState(),
                    Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.END_ROD.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(),
                    Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.CHORUS_FLOWER.defaultBlockState(), Blocks.END_ROD.defaultBlockState(), soulLantern,
                    Blocks.BLACK_STAINED_GLASS.defaultBlockState(), Items.DRAGON_HEAD, Items.ENDER_EYE, Blocks.PURPUR_BLOCK.defaultBlockState());
            case "ocean" -> new Theme(Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState(),
                    Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState(),
                    Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), soulLantern,
                    Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), Items.HEART_OF_THE_SEA, Items.NAUTILUS_SHELL, Blocks.PRISMARINE.defaultBlockState());
            case "arcane" -> new Theme(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
                    Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(),
                    Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.AMETHYST_CLUSTER.defaultBlockState(), soulFire, soulLantern,
                    Blocks.MAGENTA_STAINED_GLASS.defaultBlockState(), Items.ENCHANTED_BOOK, Items.ENDER_EYE, Blocks.AMETHYST_BLOCK.defaultBlockState());
            default -> new Theme(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                    Blocks.OBSIDIAN.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
                    Blocks.BLACKSTONE.defaultBlockState(), drip, soulFire, soulLantern,
                    Blocks.NETHER_PORTAL.defaultBlockState(), Items.WITHER_SKELETON_SKULL, Items.ENDER_EYE, Blocks.CRYING_OBSIDIAN.defaultBlockState());
        };
    }

    private final BlockRenderDispatcher blocks;
    private final ItemRenderer items;

    public GreatPortalArt(EntityRendererProvider.Context ctx) {
        this.blocks = ctx.getBlockRenderDispatcher();
        this.items = ctx.getItemRenderer();
    }

    private void cube(PoseStack pose, MultiBufferSource buf, int light, BlockState st,
                      float x, float y, float z, float sx, float sy, float sz) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        blocks.renderSingleBlock(st, pose, buf, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

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

    private void item(PoseStack pose, MultiBufferSource buf, int light, BrecheEntity e, Item it,
                      float x, float y, float z, float scale, float ry) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.scale(scale, scale, scale);
        items.renderStatic(new ItemStack(it), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buf, e.level(), 0);
        pose.popPose();
    }

    // ─── Grande Porte ───

    public void render(BrecheEntity e, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        // Forme « Antre » (amas rocheux + ovale tourbillonnant), en vrais blocs ou entièrement dessinée
        if (e.isDen()) {
            renderDen(e, pt, pose, buf, light);
            return;
        }
        // Style « blocks » : la structure est faite de vrais blocs, on ne dessine que la faille et ses effets
        if (e.isBuilt()) {
            renderRiftOnly(e, pt, pose, buf);
            return;
        }
        float t = e.tickCount + pt;
        float hp = Mth.clamp(e.getHealth() / e.getMaxHealth(), 0f, 1f);
        float death = e.deathTime > 0 ? Mth.clamp(1f - (e.deathTime + pt) / 20f, 0f, 1f) : 1f;
        float s = e.getRiftScale() / 3f * 2f; // ≈ 14 blocs de haut, 12 de large
        Theme th = themeFor(e);
        BlockState plat = hp < 0.5f ? th.cracked() : th.platform();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - e.getYRot())); // façade tournée vers le Monolithe
        pose.scale(s, s * death, s);

        // Fondation
        cube(pose, buf, light, plat, -3f, 0f, -1.5f, 6f, 0.3f, 3f);
        cube(pose, buf, light, plat, -2.5f, 0f, -2.1f, 5f, 0.15f, 0.6f);
        // Piliers colossaux
        for (float px : new float[]{-2.6f, 1.4f}) {
            cube(pose, buf, light, th.capital(), px - 0.1f, 0.3f, -0.7f, 1.4f, 0.4f, 1.4f);
            cube(pose, buf, light, th.pillar(), px, 0.3f, -0.6f, 1.2f, 5.4f, 1.2f);
            cube(pose, buf, GLOW, th.vein(), px + 0.45f, 0.8f, -0.64f, 0.3f, 4.4f, 0.05f);
            cube(pose, buf, light, hp < 0.5f ? plat : th.capital(), px - 0.15f, 5.7f, -0.75f, 1.5f, 0.45f, 1.5f);
            cube(pose, buf, light, th.spike(), px + 0.05f, 6.15f, -0.5f, 0.5f, 0.9f, 0.5f);
            cube(pose, buf, light, th.spike(), px + 0.65f, 6.15f, -0.05f, 0.45f, 0.7f, 0.45f);
            cube(pose, buf, GLOW, th.fire(), px + 0.25f, 0.3f, -1.45f, 0.7f, 0.7f, 0.7f);
        }
        // Arche à gradins + couronne
        cube(pose, buf, light, th.arch(), -1.4f, 5.3f, -0.5f, 2.8f, 0.45f, 1.0f);
        cube(pose, buf, light, th.arch(), -1.0f, 5.75f, -0.45f, 2.0f, 0.4f, 0.9f);
        cube(pose, buf, light, th.arch(), -0.55f, 6.15f, -0.4f, 1.1f, 0.35f, 0.8f);
        item(pose, buf, light, e, th.crown(), 0f, 6.0f, -0.6f, 1.4f, 180f);
        // Chaînes + lanternes
        for (float cx : new float[]{-0.95f, 0.65f}) {
            cube(pose, buf, light, Blocks.CHAIN.defaultBlockState(), cx, 4.0f, -0.75f, 0.3f, 1.3f, 0.3f);
            cube(pose, buf, GLOW, th.lantern(), cx - 0.05f, 3.55f, -0.8f, 0.4f, 0.45f, 0.4f);
        }
        // Faille : rétrécit avec les PV
        float h = 5.0f * (0.3f + 0.7f * hp);
        cube(pose, buf, GLOW, th.rift(), -1.4f, 0.3f, -0.5f, 2.8f, h, 1.0f);
        // Objet géant au centre
        float pulse = 1.5f + Mth.sin(t * 0.15f) * 0.15f;
        item(pose, buf, GLOW, e, th.eye(), 0f, 0.3f + h * 0.55f, -0.1f, pulse, t * 3f);
        // 6 éclats en orbite
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(t * 1.2f + i * 60f);
            spun(pose, buf, GLOW, th.orbit(), (float) Math.cos(a) * 3.4f, 3.0f + Mth.sin(t * 0.05f + i) * 0.5f,
                    (float) Math.sin(a) * 3.4f, 0.35f, t * 3f, 45f, 45f);
        }
        pose.popPose();
    }

    // ─── Porte en blocs réels : faille (5 × 7, rétrécit avec les PV), œil géant, éclats en orbite ───

    private void renderRiftOnly(BrecheEntity e, float pt, PoseStack pose, MultiBufferSource buf) {
        float t = e.tickCount + pt;
        float hp = Mth.clamp(e.getHealth() / e.getMaxHealth(), 0f, 1f);
        float death = e.deathTime > 0 ? Mth.clamp(1f - (e.deathTime + pt) / 20f, 0f, 1f) : 1f;
        Theme th = themeFor(e);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - e.getYRot()));
        pose.scale(1f, death, 1f);
        float h = 7f * (0.3f + 0.7f * hp);
        cube(pose, buf, GLOW, th.rift(), -2.5f, 0f, -0.5f, 5f, h, 1f);
        float pulse = 2.2f + Mth.sin(t * 0.15f) * 0.2f;
        item(pose, buf, GLOW, e, th.eye(), 0f, h * 0.55f, -0.1f, pulse, t * 3f);
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(t * 1.2f + i * 60f);
            spun(pose, buf, GLOW, th.orbit(), (float) Math.cos(a) * 7.5f, 5.0f + Mth.sin(t * 0.05f + i) * 0.8f,
                    (float) Math.sin(a) * 7.5f, 0.7f, t * 3f, 45f, 45f);
        }
        pose.popPose();
    }

    // ─── Faille au sol (brèches du Calme) ───

    /** Carré plat centré, tourné autour de Y (pour la fosse en étoile). */
    private void flat(PoseStack pose, MultiBufferSource buf, int light, BlockState st, float cy, float size, float h, float ry) {
        pose.pushPose();
        pose.translate(0, cy, 0);
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.scale(size, h, size);
        pose.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(st, pose, buf, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /**
     * FAILLE AU SOL : veines lumineuses en étoile qui s'étirent, fosse lumineuse aux bords déchiquetés, paires d'yeux
     * qui clignent (une par unité à venir) et éclats de pierre flottants. Grandit à l'ouverture, rétrécit à la fermeture.
     */
    // ─── Faille au sol « monde à l'envers » : bassin incandescent, tentacules organiques, fissures lumineuses ───

    /** Segment allongé centré en (cx, cy, cz), orienté selon {@code ang} (degrés), relevé de {@code tilt} degrés. */
    private void seg(PoseStack pose, MultiBufferSource buf, int light, BlockState st, float cx, float cy, float cz,
                     float ang, float len, float h, float w, float tilt) {
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.YP.rotationDegrees(-ang));
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        pose.scale(len, h, w);
        pose.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(st, pose, buf, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Matières de la faille selon le thème : tentacules, cœur du bassin, lueur, fissures. */
    private record RiftLook(BlockState tendril, BlockState core, BlockState glow, BlockState crack) {}

    private static RiftLook riftLook(String theme) {
        return switch (theme == null ? "abyss" : theme) {
            case "bone" -> new RiftLook(Blocks.SCULK.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState(),
                    Blocks.CYAN_STAINED_GLASS.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState());
            case "end" -> new RiftLook(Blocks.OBSIDIAN.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(),
                    Blocks.PURPLE_STAINED_GLASS.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState());
            case "ocean" -> new RiftLook(Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState(),
                    Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState());
            case "arcane" -> new RiftLook(Blocks.OBSIDIAN.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState(),
                    Blocks.MAGENTA_STAINED_GLASS.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState());
            default -> new RiftLook(Blocks.CRIMSON_HYPHAE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(),
                    Blocks.ORANGE_STAINED_GLASS.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState());
        };
    }

    /**
     * FAILLE AU SOL (brèches du Calme), inspirée du « monde à l'envers » : bassin incandescent qui pulse, 12 tentacules
     * organiques qui s'affinent, s'enroulent et dressent leur pointe, fissures lumineuses en zigzag dans le sol,
     * paires d'yeux au fond (une par unité à venir). Grandit à l'ouverture, rétrécit à la fermeture.
     */
    public void renderGroundRift(BrecheEntity e, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        float open = Mth.clamp(e.getRiftOpen(), 0f, 1f);
        if (open <= 0.01f) return;
        float t = e.tickCount + pt;
        int seed = Math.abs(e.getId());
        RiftLook lk = riftLook(e.getTheme());
        float r = 1.4f * open;

        pose.pushPose();
        pose.translate(0, 0.01, 0);
        // Fissures lumineuses en zigzag, au-delà des tentacules
        for (int i = 0; i < 8; i++) {
            float ang = i * 45f + ((seed * 17 + i * 31) % 24) - 12f;
            float len = 3.8f * open * (0.7f + ((seed + i * 7) % 10) * 0.03f);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-ang));
            cube(pose, buf, GLOW, lk.crack(), r * 0.6f, 0f, -0.03f, len * 0.55f, 0.02f, 0.06f);
            pose.translate(r * 0.6f + len * 0.55f, 0, 0);
            pose.mulPose(Axis.YP.rotationDegrees(i % 2 == 0 ? 24f : -24f));
            cube(pose, buf, GLOW, lk.crack(), 0f, 0f, -0.025f, len * 0.45f, 0.02f, 0.05f);
            pose.popPose();
        }
        // Bassin : bord sombre, cœur incandescent, lueur translucide qui pulse et tourne lentement
        for (int k = 0; k < 4; k++) flat(pose, buf, light, Blocks.BLACKSTONE.defaultBlockState(), 0.005f, r * 2.2f, 0.03f, k * 22.5f + (seed % 15));
        for (int k = 0; k < 4; k++) flat(pose, buf, GLOW, lk.core(), 0.02f, r * 1.75f, 0.03f, k * 22.5f + 11f + (seed % 15));
        float pulse = 1f + Mth.sin(t * 0.15f) * 0.06f;
        for (int k = 0; k < 2; k++) flat(pose, buf, GLOW, lk.glow(), 0.06f, r * 1.6f * pulse, 0.02f, k * 45f + t * 0.5f);
        // 12 tentacules organiques : 6 segments qui s'affinent, s'enroulent et dont la pointe se dresse
        for (int i = 0; i < 12; i++) {
            float ang = i * 30f + ((seed * 13 + i * 29) % 20) - 10f;
            float curl = (i % 2 == 0 ? 1f : -1f) * (6f + (seed + i) % 6);
            float tl = (1.8f + ((seed * 7 + i * 11) % 10) * 0.12f) * open;
            float segLen = tl / 6f;
            double a0 = Math.toRadians(ang);
            float x = (float) Math.cos(a0) * r * 0.85f, z = (float) Math.sin(a0) * r * 0.85f;
            for (int s = 0; s < 6; s++) {
                double a = Math.toRadians(ang);
                float w = 0.34f * (1f - s / 7f) * Math.max(0.3f, open);
                float lift = s >= 4 ? (s - 3) * 0.12f : 0f;
                float tilt = s >= 4 ? 12f * (s - 3) : 0f;
                float cx = x + (float) Math.cos(a) * segLen * 0.5f, cz = z + (float) Math.sin(a) * segLen * 0.5f;
                seg(pose, buf, light, lk.tendril(), cx, 0.02f + lift, cz, ang, segLen * 1.15f, w * 0.8f, w, tilt);
                x += (float) Math.cos(a) * segLen;
                z += (float) Math.sin(a) * segLen;
                ang += curl;
            }
        }
        // Paires d'yeux au fond du bassin (une par unité à venir), qui clignent
        BlockState eye = Blocks.WHITE_CONCRETE.defaultBlockState();
        int pairs = Math.min(6, e.getRiftEyes());
        for (int p = 0; p < pairs; p++) {
            if (((int) t + p * 23 + seed) % 70 < 5) continue;
            double a = Math.toRadians(p * 137.5 + seed % 360);
            pose.pushPose();
            pose.translate((float) Math.cos(a) * r * 0.45f, 0.08, (float) Math.sin(a) * r * 0.45f);
            pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(a)));
            cube(pose, buf, GLOW, eye, -0.17f, 0f, -0.04f, 0.1f, 0.04f, 0.07f);
            cube(pose, buf, GLOW, eye, 0.07f, 0f, -0.04f, 0.1f, 0.04f, 0.07f);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Ancienne faille (fosse violette + éclats flottants), conservée pour référence. */
    public void renderGroundRiftV1(BrecheEntity e, float pt, PoseStack pose, MultiBufferSource buf) {
        float open = Mth.clamp(e.getRiftOpen(), 0f, 1f);
        if (open <= 0.01f) return;
        float t = e.tickCount + pt;
        Theme th = theme(e.getTheme());
        int seed = e.getId();
        BlockState dark = Blocks.BLACKSTONE.defaultBlockState();
        BlockState eye = Blocks.WHITE_CONCRETE.defaultBlockState();

        pose.pushPose();
        pose.translate(0, 0.01, 0);
        // Veines en étoile (avec une branche chacune), qui s'étirent pendant l'ouverture
        float len = 3.4f * Math.min(1f, open * 1.5f);
        for (int i = 0; i < 6; i++) {
            float ang = i * 60f + ((seed * 31 + i * 17) % 25) - 12f;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(ang));
            cube(pose, buf, GLOW, th.vein(), 0.4f, 0f, -0.04f, len, 0.03f, 0.08f);
            pose.translate(0.4f + len * 0.55f, 0, 0);
            pose.mulPose(Axis.YP.rotationDegrees(i % 2 == 0 ? 38f : -38f));
            cube(pose, buf, GLOW, th.vein(), 0f, 0f, -0.03f, len * 0.38f, 0.03f, 0.06f);
            pose.popPose();
        }
        // Fosse : bord sombre déchiqueté (carrés tournés) puis cœur lumineux
        float r = 1.25f * open;
        for (int k = 0; k < 4; k++) flat(pose, buf, 0xF000F0, dark, 0.005f, r * 2.1f, 0.03f, k * 22.5f + (seed % 15));
        for (int k = 0; k < 4; k++) flat(pose, buf, GLOW, th.rift(), 0.02f, r * 1.7f, 0.03f, k * 22.5f + 11f + (seed % 15));
        // Paires d'yeux (une par unité à venir), qui clignent
        int pairs = Math.min(6, e.getRiftEyes());
        for (int p = 0; p < pairs; p++) {
            if (((int) t + p * 23 + seed) % 70 < 5) continue; // clignement
            double a = Math.toRadians(p * 137.5 + seed % 360);
            float ex = (float) Math.cos(a) * r * 0.45f, ez = (float) Math.sin(a) * r * 0.45f;
            pose.pushPose();
            pose.translate(ex, 0.06, ez);
            pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(a)));
            cube(pose, buf, GLOW, eye, -0.17f, 0f, -0.04f, 0.1f, 0.04f, 0.07f);
            cube(pose, buf, GLOW, eye, 0.07f, 0f, -0.04f, 0.1f, 0.04f, 0.07f);
            pose.popPose();
        }
        // Éclats de pierre flottants (monolithes miniatures), légèrement inclinés
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(i * 120 + t * 0.6 + seed);
            float sx = (float) Math.cos(a) * (r + 0.9f), sz = (float) Math.sin(a) * (r + 0.9f);
            pose.pushPose();
            pose.translate(sx, 0.7f + Mth.sin(t * 0.06f + i) * 0.15f, sz);
            pose.mulPose(Axis.YP.rotationDegrees(t * 0.8f + i * 40));
            pose.mulPose(Axis.ZP.rotationDegrees(14f));
            pose.scale(0.22f * open, 0.6f * open, 0.22f * open);
            pose.translate(-0.5, -0.5, -0.5);
            blocks.renderSingleBlock(th.pillar(), pose, buf, GLOW, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    // ─── Forme « Antre » ───

    /** Couleurs de l'ovale selon le thème : bord, anneaux intermédiaires, cœur. */
    private static BlockState[] denColors(String theme) {
        return switch (theme == null ? "abyss" : theme) {
            case "fire" -> new BlockState[]{Blocks.ORANGE_CONCRETE.defaultBlockState(), Blocks.RED_CONCRETE.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState()};
            case "bone" -> new BlockState[]{Blocks.CYAN_CONCRETE.defaultBlockState(), Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState()};
            case "end" -> new BlockState[]{Blocks.PURPLE_CONCRETE.defaultBlockState(), Blocks.MAGENTA_CONCRETE.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState()};
            case "ocean" -> new BlockState[]{Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(), Blocks.CYAN_CONCRETE.defaultBlockState(), Blocks.BLUE_CONCRETE.defaultBlockState()};
            case "arcane" -> new BlockState[]{Blocks.MAGENTA_CONCRETE.defaultBlockState(), Blocks.PINK_CONCRETE.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState()};
            default -> new BlockState[]{Blocks.MAGENTA_CONCRETE.defaultBlockState(), Blocks.PURPLE_CONCRETE.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState()};
        };
    }

    /**
     * ANTRE : en vrais blocs, seul l'ovale tourbillonnant est dessiné (dans l'ouverture) ; en « Visuel seul », tout est
     * dessiné : dôme de rochers penchés, cadre de pierre autour de l'ovale, rocher annexe et vrilles lumineuses qui pulsent.
     */
    private void renderDen(BrecheEntity e, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        float hp = Mth.clamp(e.getHealth() / e.getMaxHealth(), 0f, 1f);
        float death = e.deathTime > 0 ? Mth.clamp(1f - (e.deathTime + pt) / 20f, 0f, 1f) : 1f;
        Theme th = themeFor(e);
        BlockState[] oc = denColors(e.getTheme());
        // Fond de l'ovale : portail du Nether recoloré (couleur choisie dans l'éditeur, ou selon le thème)
        int portalRgb = denColor(e.getDenBackground(), e.getTheme());
        int seed = Math.abs(e.getId());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - e.getYRot())); // face tournée vers le Monolithe (-z local)
        if (!e.isBuilt()) {
            pose.pushPose();
            pose.scale(1f, death, 1f);
            java.util.Random r = new java.util.Random(seed * 31L);
            BlockState[] rock = {th.arch(), th.pillar(), hp < 0.5f ? th.cracked() : th.platform()};
            // Dôme de rochers penchés (derrière la face), sans boucher l'ovale
            for (int i = 0; i < 34; i++) {
                double v = r.nextDouble();
                float w = (float) (5.5 * Math.sqrt(1 - v * v * 0.85));
                float x = (float) ((r.nextDouble() * 2 - 1) * w + v * 0.8);
                float y = (float) (v * 9.5);
                float z = 0.6f + r.nextFloat() * 4.0f;
                if (sq(x / 2.6f) + sq((y - 4f) / 3.9f) < 1f && z < 2.2f) z += 2.2f;
                float size = 1.6f + r.nextFloat() * 1.8f * (1f - (float) v * 0.5f);
                spun(pose, buf, light, rock[r.nextInt(3)], x, y, z, size, r.nextFloat() * 90f, r.nextFloat() * 30f - 15f, r.nextFloat() * 30f - 15f);
            }
            // Cadre de pierre autour de l'ovale
            for (int j = 0; j < 22; j++) {
                double a = j * Math.PI * 2 / 22;
                spun(pose, buf, light, rock[j % 2], (float) Math.cos(a) * 2.9f, 4f + (float) Math.sin(a) * 4.2f, 0.3f, 1.2f, j * 40f, 10f, 10f);
            }
            // Rocher annexe (empilement sur le côté)
            for (int i = 0; i < 7; i++) {
                spun(pose, buf, light, rock[i % 3], 7.5f + r.nextFloat() * 1.6f - 0.8f, i * 0.9f + 0.5f, 1.5f, 1.6f - i * 0.12f,
                        r.nextFloat() * 90f, r.nextFloat() * 20f - 10f, r.nextFloat() * 20f - 10f);
            }
            // Vrilles lumineuses : 5 sur le dôme, 2 sur le rocher annexe, qui pulsent
            float pulse = 0.85f + 0.15f * Mth.sin(t * 0.1f);
            for (int k = 0; k < 7; k++) {
                boolean annex = k >= 5;
                float base = k * 72f + seed % 40;
                for (int sg = 0; sg < (annex ? 9 : 14); sg++) {
                    float f = sg / (annex ? 9f : 14f);
                    double ang = Math.toRadians(base + f * 260f + Mth.sin(t * 0.03f + k) * 6f);
                    float rr = annex ? 1.0f : 4.8f - f * 2.2f;
                    float x = (annex ? 7.5f : 0f) + (float) Math.cos(ang) * rr;
                    float z = (annex ? 1.5f : 2.5f) + (float) Math.sin(ang) * rr * 0.8f;
                    float y = 0.3f + f * (annex ? 5.5f : 8.5f);
                    seg(pose, buf, GLOW, oc[1], x, y, z, (float) Math.toDegrees(ang) + 90f, 0.9f, 0.22f * pulse, 0.22f * pulse, 20f);
                }
            }
            pose.popPose();
        }
        // Ovale tourbillonnant (dans l'ouverture des vrais blocs : centre plus bas d'un bloc)
        denOval(pose, buf, hp, death, portalRgb, e.isBuilt() ? 3.0f : 4.0f, e.isBuilt() ? 2.2f : 2.5f, e.isBuilt() ? 3.4f : 3.9f);
        pose.popPose();
    }

    private static float sq(float v) { return v * v; }

    /** Ovale vertical : disque sombre, 4 anneaux de points lumineux qui tournent en sens alternés, 3 bras de spirale aspirés. */
    // ─── Fond de l'antre : portail du Nether recoloré (13 teintes × foncé / normal / clair) ───

    public static final String[] DEN_HUES = {"red", "orange", "yellow", "lime", "green", "cyan", "light_blue", "blue",
            "purple", "magenta", "pink", "brown", "gray"};
    private static final int[][] DEN_RGB = {
            {0x6B0F0F, 0xC21E1E, 0xFF6B6B}, {0x7A3500, 0xE06A00, 0xFFAA55}, {0x7A6A00, 0xE0C800, 0xFFF27A},
            {0x2F6B00, 0x6FD400, 0xB8FF70}, {0x0B4D1E, 0x1E9E3E, 0x6EE08A}, {0x00585E, 0x00B3BD, 0x7AF2F7},
            {0x1D4F7A, 0x3E9BEA, 0xA6D6FF}, {0x0D1A6B, 0x2A44D4, 0x7F92FF}, {0x3A0F6B, 0x7B2FD6, 0xB98CFF},
            {0x6B0F5A, 0xC226A8, 0xFF7AE6}, {0x7A2E48, 0xE86A9A, 0xFFB8D2}, {0x3B2412, 0x7A4B26, 0xB88A5E},
            {0x202020, 0x6E6E6E, 0xD8D8D8}};

    public static int denRgb(int hue, int v) { return DEN_RGB[hue][v]; }

    /** Clé de config d'une couleur : « red_dark », « red », « red_light »… */
    public static String denKey(int hue, int v) { return DEN_HUES[hue] + (v == 0 ? "_dark" : v == 2 ? "_light" : ""); }

    /** {teinte, variante} d'une clé (anciens noms compris : black, white, light_gray) ; null si « auto » ou inconnue. */
    public static int[] denParse(String key) {
        if (key == null || key.isBlank() || "auto".equals(key)) return null;
        if ("black".equals(key)) return new int[]{12, 0};
        if ("white".equals(key) || "light_gray".equals(key)) return new int[]{12, 2};
        int v = 1;
        String hue = key;
        if (key.endsWith("_dark")) { v = 0; hue = key.substring(0, key.length() - 5); }
        else if (key.endsWith("_light")) { v = 2; hue = key.substring(0, key.length() - 6); }
        int i = java.util.Arrays.asList(DEN_HUES).indexOf(hue);
        return i < 0 ? null : new int[]{i, v};
    }

    /** Couleur RGB du fond : celle choisie, sinon (« Auto ») une teinte assortie au thème. */
    public static int denColor(String key, String theme) {
        int[] hv = denParse(key);
        if (hv != null) return DEN_RGB[hv[0]][hv[1]];
        int hue = switch (theme == null ? "" : theme) {
            case "fire" -> 0;
            case "bone" -> 5;
            case "ocean" -> 6;
            case "arcane" -> 10;
            case "abyss" -> 9;
            default -> 8;
        };
        return DEN_RGB[hue][1];
    }

    /**
     * Ovale vertical rempli du portail du Nether (texture animée en niveaux de gris « den_portal ») teinté de la couleur
     * choisie, en pleine lumière ; découpé en cellules d'un demi-bloc pour garder l'échelle du portail vanilla.
     * Sa taille suit les PV de la Porte.
     */
    private void denOval(PoseStack pose, MultiBufferSource buf, float hp, float death, int rgb, float cy, float w0, float h0) {
        float k0 = (0.4f + 0.6f * hp) * death;
        float w = w0 * k0, h = h0 * k0;
        if (w < 0.05f) return;
        var sprite = net.minecraft.client.Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                .apply(new net.minecraft.resources.ResourceLocation("wavesurvivor", "block/den_portal"));
        var vc = buf.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));
        org.joml.Matrix4f m = pose.last().pose();
        org.joml.Matrix3f n = pose.last().normal();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255, a = 255;
        float cs = 0.5f;
        // 2 couches superposées (légèrement décalées) : fond bien plus opaque, sans masquer lueurs ni particules
        for (int layer = 0; layer < 2; layer++) {
        float z = layer * 0.04f;
        for (float x = (float) Math.floor(-w / cs) * cs; x < w; x += cs) {
            for (float y = (float) Math.floor(-h / cs) * cs; y < h; y += cs) {
                float mx = x + cs / 2, my = y + cs / 2;
                if (sq(mx / w) + sq(my / h) > 1f) continue;
                float fx = (float) (x - Math.floor(x)), fy = (float) (y - Math.floor(y));
                float u0 = sprite.getU(fx * 16), u1 = sprite.getU((fx + cs) * 16);
                float v0 = sprite.getV((1 - fy - cs) * 16), v1 = sprite.getV((1 - fy) * 16);
                float y0 = cy + y, y1 = cy + y + cs;
                vc.vertex(m, x, y0, z).color(r, g, b, a).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(GLOW).normal(n, 0, 0, 1).endVertex();
                vc.vertex(m, x + cs, y0, z).color(r, g, b, a).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(GLOW).normal(n, 0, 0, 1).endVertex();
                vc.vertex(m, x + cs, y1, z).color(r, g, b, a).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(GLOW).normal(n, 0, 0, 1).endVertex();
                vc.vertex(m, x, y1, z).color(r, g, b, a).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(GLOW).normal(n, 0, 0, 1).endVertex();
            }
        }
        }
    }

    /** Ancien ovale (disque de béton + anneaux de points + bras de spirale), conservé pour référence. */
    private void denOvalDotsV1(PoseStack pose, MultiBufferSource buf, float t, float hp, float death, BlockState[] oc, float cy, float w0, float h0) {
        float k0 = (0.4f + 0.6f * hp) * death;
        float w = w0 * k0, h = h0 * k0;
        if (w < 0.05f) return;
        for (int i = -12; i <= 12; i++) {
            float x = i / 12f * w;
            float hh = h * (float) Math.sqrt(Math.max(0, 1 - sq(x / w)));
            cube(pose, buf, GLOW, oc[2], x - w / 24f, cy - hh, -0.05f, w / 12f + 0.02f, 2 * hh, 0.1f);
        }
        for (int k = 0; k < 4; k++) {
            float fr = 1f - k * 0.2f;
            BlockState col = k % 2 == 0 ? oc[0] : oc[1];
            float speed = (k % 2 == 0 ? 1f : -1f) * (2.5f + k);
            int n = 40 - k * 6;
            for (int j = 0; j < n; j++) {
                if (j % 4 == 3) continue;
                double a = Math.toRadians(j * 360f / n + t * speed);
                float px = (float) Math.cos(a) * w * fr, py = (float) Math.sin(a) * h * fr;
                cube(pose, buf, GLOW, col, px - 0.09f, cy + py - 0.09f, -0.12f - k * 0.01f, 0.18f, 0.18f, 0.06f);
            }
        }
        for (int arm = 0; arm < 3; arm++) {
            for (int s = 0; s < 16; s++) {
                float f = s / 16f;
                double a = Math.toRadians(arm * 120f + f * 400f - t * 6f);
                float rr = (1f - f) * 0.85f, size = 0.16f * (1f - f * 0.6f);
                cube(pose, buf, GLOW, oc[0], (float) Math.cos(a) * w * rr - size / 2, cy + (float) Math.sin(a) * h * rr - size / 2, -0.14f, size, size, 0.05f);
            }
        }
    }

    // ─── Cristal de la Mairie (au-dessus du Monolithe) ───

    /**
     * CRISTAL DE LA MAIRIE (modèle 3D facetté du mod) : un seul cristal plein qui flotte et tourne, entouré de
     * mini-cristaux du même modèle en orbite (2 / 4 / 6 / 8 selon le niveau). Sa taille grandit avec la Mairie.
     */
    public void renderCrystal(BrecheEntity e, float pt, PoseStack pose, MultiBufferSource buf) {
        // Bloc modé « town_crystal » (modèle facetté, texture émeraude) en pleine lumière, qui flotte et tourne ;
        // petits cristaux du même style en orbite (2 par niveau au-delà du 1er). Sa taille suit le niveau de la Mairie.
        int tier = Math.max(2, Math.min(5, e.getRiftEyes()));
        float t = e.tickCount + pt;
        float s = 1.4f + 0.45f * (tier - 2);                // 1,4 → 2,75 blocs de haut
        BlockState gem = com.wavesurvivor.registry.ModBlocks.TOWN_CRYSTAL.get().defaultBlockState();
        pose.pushPose();
        pose.translate(0, Mth.sin(t * 0.05f) * 0.15f, 0);
        // Grand cristal qui tourne lentement
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 1.2f));
        pose.scale(s, s, s);
        pose.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(gem, pose, buf, GLOW, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        // Petits cristaux en orbite : 2 / 4 / 6 / 8 selon le niveau
        int n = (tier - 1) * 2;
        for (int k = 0; k < n; k++) {
            double a = Math.toRadians(t * 1.6f + k * 360f / n);
            float r = 0.95f * s + (k % 2) * 0.2f * s;
            float m = 0.3f * s;
            pose.pushPose();
            pose.translate(Math.cos(a) * r, Mth.sin(t * 0.07f + k) * 0.3f * s, Math.sin(a) * r);
            pose.mulPose(Axis.YP.rotationDegrees(-t * 2f + k * 40f));
            pose.mulPose(Axis.ZP.rotationDegrees(15f));
            pose.scale(m, m, m);
            pose.translate(-0.5, -0.5, -0.5);
            blocks.renderSingleBlock(gem, pose, buf, GLOW, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Ancien cristal de verre (bipyramides), conservé pour référence. */
    public void renderCrystalGlassV1(BrecheEntity e, float pt, PoseStack pose, MultiBufferSource buf) {
        int tier = Math.max(2, Math.min(5, e.getRiftEyes()));
        float t = e.tickCount + pt;
        float s = 1.2f + 0.45f * (tier - 2);                 // ×1,2 → ×2,55
        BlockState cr = com.wavesurvivor.registry.ModBlocks.TOWN_CRYSTAL.get().defaultBlockState();
        float bob = Mth.sin(t * 0.05f) * 0.15f;
        // Cristal principal
        pose.pushPose();
        pose.translate(0, bob, 0);
        pose.mulPose(Axis.YP.rotationDegrees(t * 1.2f));
        pose.scale(s, s, s);
        pose.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(cr, pose, buf, GLOW, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        // Mini-cristaux en orbite, qui tournent sur eux-mêmes
        int n = (tier - 1) * 2;
        for (int k = 0; k < n; k++) {
            double a = Math.toRadians(t * 1.6f + k * 360f / n);
            float r = 0.95f * s + (k % 2) * 0.2f * s;
            float ms = 0.28f * s;
            pose.pushPose();
            pose.translate(Math.cos(a) * r, bob + Mth.sin(t * 0.07f + k) * 0.25f * s, Math.sin(a) * r);
            pose.mulPose(Axis.YP.rotationDegrees(-t * 3f + k * 40f));
            pose.mulPose(Axis.ZP.rotationDegrees(15f));
            pose.scale(ms, ms, ms);
            pose.translate(-0.5, -0.5, -0.5);
            blocks.renderSingleBlock(cr, pose, buf, GLOW, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    /** Ancien cristal (verre + cœur + anneau d'or), conservé pour référence. */
    public void renderCrystalV1(BrecheEntity e, float pt, PoseStack pose, MultiBufferSource buf) {
        int tier = Math.max(2, Math.min(5, e.getRiftEyes()));
        float t = e.tickCount + pt;
        float s = 0.7f + 0.3f * (tier - 2);                 // 0,7 → 1,6
        BlockState shell = (tier >= 5 ? Blocks.MAGENTA_STAINED_GLASS : tier >= 4 ? Blocks.PURPLE_STAINED_GLASS
                : Blocks.LIGHT_BLUE_STAINED_GLASS).defaultBlockState();
        BlockState core = Blocks.AMETHYST_BLOCK.defaultBlockState();
        float bob = Mth.sin(t * 0.05f) * 0.15f;

        pose.pushPose();
        pose.translate(0, bob, 0);
        // Bipyramide principale (enveloppe de verre + cœur lumineux), qui tourne lentement
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 1.2f));
        bipyramid(pose, buf, shell, 2.4f * s, 0.95f * s, 11);
        bipyramid(pose, buf, core, 1.5f * s, 0.42f * s, 7);
        pose.popPose();
        // Éclats en orbite : 2 / 4 / 6 / 8 selon le niveau
        int n = (tier - 1) * 2;
        for (int k = 0; k < n; k++) {
            double a = Math.toRadians(t * 1.6f + k * 360f / n);
            float r = 1.25f * s + (k % 2) * 0.25f * s;
            pose.pushPose();
            pose.translate(Math.cos(a) * r, Mth.sin(t * 0.07f + k) * 0.3f * s, Math.sin(a) * r);
            pose.mulPose(Axis.YP.rotationDegrees(-t * 2f + k * 40f));
            pose.mulPose(Axis.ZP.rotationDegrees(18f));
            bipyramid(pose, buf, k % 2 == 0 ? shell : core, 0.7f * s, 0.26f * s, 5);
            pose.popPose();
        }
        // Niveau 4+ : anneau d'or qui tourne à contresens sous le cristal
        if (tier >= 4) {
            BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
            for (int i = 0; i < 20; i++) {
                double a = Math.toRadians(-t * 0.8f + i * 18f);
                spun(pose, buf, GLOW, gold, (float) Math.cos(a) * 1.0f * s, -1.15f * s, (float) Math.sin(a) * 1.0f * s, 0.12f * s, i * 18f, 0f, 45f);
            }
        }
        // Niveau 5 : couronne de pointes de cristal autour du sommet
        if (tier >= 5) {
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(t * 0.6f + i * 60f);
                pose.pushPose();
                pose.translate(Math.cos(a) * 0.55f * s, 1.05f * s, Math.sin(a) * 0.55f * s);
                pose.mulPose(Axis.YP.rotationDegrees((float) -Math.toDegrees(a)));
                pose.mulPose(Axis.ZP.rotationDegrees(-25f));
                bipyramid(pose, buf, core, 0.6f * s, 0.16f * s, 5);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /** Bipyramide centrée (hauteur h, largeur max w) faite d'étages carrés tournés à 45°, en pleine lumière. */
    private void bipyramid(PoseStack pose, MultiBufferSource buf, BlockState st, float h, float w, int layers) {
        float lh = h / layers;
        for (int i = 0; i < layers; i++) {
            float f = (i + 0.5f) / layers;
            float size = w * (1f - Math.abs(f - 0.5f) * 2f) + 0.04f;
            flat(pose, buf, GLOW, st, -h / 2f + i * lh, size, lh, 45f);
        }
    }

    // ─── Thème « Terrain » pour la Porte dessinée (mode Visuel seul) ───

    private final java.util.Map<Integer, Theme> terrainCache = new java.util.HashMap<>();

    /** Thème de la Porte : « Terrain » = matériaux du sol autour d'elle (calculés une fois, côté client), sinon thème fixe. */
    private Theme themeFor(BrecheEntity e) {
        if (!"terrain".equals(e.getTheme())) return theme(e.getTheme());
        Theme cached = terrainCache.get(e.getId());
        if (cached != null) return cached;
        Theme ab = theme("abyss");
        net.minecraft.core.BlockPos o = e.blockPosition().below(e.isBuilt() ? 1 : 0);
        var smp = com.wavesurvivor.horde.kingdom.TerrainPalette.sample(e.level(), o,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING);
        if (smp == null) return ab; // terrain pas encore chargé : on réessaiera
        Theme th = new Theme(smp.top().cut(), smp.top().cracked(), smp.low().cut(), ab.vein(), smp.low().polished(),
                smp.low().cut(), ab.spike(), ab.fire(), ab.lantern(), ab.rift(), ab.crown(), ab.eye(), ab.orbit());
        if (terrainCache.size() > 64) terrainCache.clear();
        terrainCache.put(e.getId(), th);
        return th;
    }

    // ─── Catalyseur (objectif du Calme) ───

    public void renderCatalyst(BrecheEntity e, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        float hp = Mth.clamp(e.getHealth() / e.getMaxHealth(), 0f, 1f);
        float death = e.deathTime > 0 ? Mth.clamp(1f - (e.deathTime + pt) / 15f, 0f, 1f) : 1f;
        Theme th = themeFor(e);
        float bob = Mth.sin(t * 0.08f) * 0.15f;

        pose.pushPose();
        pose.scale(death, death, death);
        // Socle
        cube(pose, buf, light, th.platform(), -0.7f, 0f, -0.7f, 1.4f, 0.25f, 1.4f);
        cube(pose, buf, light, th.capital(), -0.45f, 0.25f, -0.45f, 0.9f, 0.2f, 0.9f);
        // Cristal central (rétrécit avec les PV) dans une coque de verre qui tourne en sens inverse
        float cs = 0.55f + 0.45f * hp;
        spun(pose, buf, GLOW, th.vein(), 0f, 1.6f + bob, 0f, cs, t * 2.5f, 45f, 35f);
        spun(pose, buf, GLOW, th.rift(), 0f, 1.6f + bob, 0f, 1.35f, -t * 1.5f, 20f, 20f);
        // Éclats en orbite
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(t * 4f + i * 120f);
            spun(pose, buf, GLOW, th.orbit(), (float) Math.cos(a) * 1.1f, 1.6f + Mth.sin(t * 0.1f + i) * 0.3f,
                    (float) Math.sin(a) * 1.1f, 0.25f, t * 5f, 45f, 45f);
        }
        // Rayon de lumière visible de loin
        cube(pose, buf, GLOW, th.rift(), -0.08f, 2.3f + bob, -0.08f, 0.16f, 14f, 0.16f);
        pose.popPose();
    }
}
