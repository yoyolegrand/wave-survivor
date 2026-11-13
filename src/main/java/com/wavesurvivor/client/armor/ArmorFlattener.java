package com.wavesurvivor.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 2.0 — « APLATIT » un modèle d'armure 3D pour la compatibilité Epic Fight.
 * Epic Fight convertit les armures à modèle personnalisé en maillage animé : les cubes posés directement sur les 7 grandes parties
 * (tête, corps, bras, jambes) sont fixés une fois pour toutes, mais ceux des sous-parties (notre « armor », les ailes, l'auréole…)
 * sont recalculés à chaque image à partir de la pose ACTUELLE de la grande partie. Si cette pose n'est pas exactement remise à zéro
 * à ce moment-là (animations d'arme en mode combat, Player Animator…), tout le casque se décale de la tête, d'un écart variable.
 * Ici, toutes les sous-parties visibles sont fusionnées dans leur grande partie (sommets et normales transformés une fois) :
 * le modèle a la même structure qu'une armure vanilla et Epic Fight n'a plus rien à recalculer. Utilisé SEULEMENT pour Epic Fight (calledByEpicFight).
 * Les cubes sont modifiés EN PLACE : le modèle source doit être une instance à part (set.bakeLayer neuf), déjà réglée (forSlot, pièces déplacées).
 */
@OnlyIn(Dist.CLIENT)
public final class ArmorFlattener {

    private static final String[] PARTS = {"head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    private static Field cubePolygons, polyVertices, polyNormal, vertexPos, partCubes, partChildren;
    private static boolean failed;

    private ArmorFlattener() {}

    private static Boolean epicFight;

    /**
     * Vrai si l'appel vient d'Epic Fight (rendu en mode combat ou conversion de l'armure en maillage).
     * Le modèle aplati n'est donné qu'à Epic Fight : en rendu vanilla, Embeddium / Sodium redessinent les cubes à partir de leurs
     * dimensions d'origine (et non des sommets modifiés), ce qui ferait disparaître auréole, ailes et épaulières dans la tête et les bras.
     */
    public static boolean calledByEpicFight() {
        if (epicFight == null) epicFight = net.minecraftforge.fml.ModList.get().isLoaded("epicfight");
        if (!epicFight) return false;
        return StackWalker.getInstance().walk(s -> s.limit(20).anyMatch(f -> f.getClassName().startsWith("yesman.epicfight")));
    }

    /** Modèle aplati équivalent à « src » ; en cas d'échec (réflexion), renvoie « src » tel quel. */
    public static HumanoidModel<LivingEntity> flatten(HumanoidModel<LivingEntity> src) {
        if (failed || !init()) return src;
        try {
            Map<String, ModelPart> top = new HashMap<>();
            ModelPart[] parts = {src.head, src.hat, src.body, src.rightArm, src.leftArm, src.rightLeg, src.leftLeg};
            for (int i = 0; i < PARTS.length; i++) top.put(PARTS[i], flatPart(parts[i]));
            return new HumanoidModel<>(new ModelPart(List.of(), top));
        } catch (Exception e) {
            failed = true;
            WaveSurvivorMod.LOGGER.warn("[Armure] Aplatissement du modèle impossible ({}), modèle d'origine utilisé.", e.toString());
            return src;
        }
    }

    /** Grande partie aplatie : ses cubes + ceux de toutes ses sous-parties visibles, exprimés dans son propre repère. */
    @SuppressWarnings("unchecked")
    private static ModelPart flatPart(ModelPart p) throws Exception {
        List<ModelPart.Cube> cubes = new ArrayList<>((List<ModelPart.Cube>) partCubes.get(p));
        PoseStack ps = new PoseStack();
        collectChildren(p, ps, cubes);
        ModelPart out = new ModelPart(cubes, Map.of());
        out.setInitialPose(p.getInitialPose());
        out.loadPose(p.storePose());
        out.visible = p.visible;
        out.skipDraw = p.skipDraw;
        return out;
    }

    /** Parcourt les sous-parties visibles (récursif) et transforme leurs cubes dans le repère de la grande partie. */
    @SuppressWarnings("unchecked")
    private static void collectChildren(ModelPart parent, PoseStack ps, List<ModelPart.Cube> out) throws Exception {
        for (String name : childNames(parent)) {
            ModelPart c = parent.getChild(name);
            if (!c.visible) continue;
            ps.pushPose();
            c.translateAndRotate(ps);
            if (!c.skipDraw) {
                Matrix4f m = ps.last().pose();
                Matrix3f n = ps.last().normal();
                for (ModelPart.Cube cube : (List<ModelPart.Cube>) partCubes.get(c)) {
                    transform(cube, m, n);
                    out.add(cube);
                }
            }
            collectChildren(c, ps, out);
            ps.popPose();
        }
    }

    /** Noms des sous-parties (champ « children » privé, lu par réflexion). */
    private static List<String> childNames(ModelPart p) throws Exception {
        @SuppressWarnings("unchecked") Map<String, ModelPart> ch = (Map<String, ModelPart>) partChildren.get(p);
        return new ArrayList<>(ch.keySet());
    }

    /** Sommets en pixels : p' = M·(p/16)·16 ; normales tournées puis « recalées » sur l'axe le plus proche.
     *  Epic Fight (VanillaModelTransformer.getDirectionFromVector) ne reconnaît une face que si sa normale vaut EXACTEMENT
     *  l'un des 6 axes : une normale penchée (sous-partie tournée) ou approchée (0.99999994, -0.0) donnait « null » et faisait
     *  planter le rendu (NullPointerException dans AnimatedPolygon). La forme du modèle ne change pas, seule l'orientation
     *  de la face (éclairage) est arrondie à l'axe le plus proche. */
    private static void transform(ModelPart.Cube cube, Matrix4f m, Matrix3f n) throws Exception {
        Object polys = cubePolygons.get(cube);
        Vector3f tmp = new Vector3f();
        // les coins d'un cube sont PARTAGÉS entre ses faces (Vertex.remap garde le même Vector3f) : chacun n'est transformé qu'une fois,
        // sinon un coin commun à 3 faces est déplacé 3 fois et les morceaux tournés (auréole, ailes) s'envolent
        java.util.Set<Vector3f> done = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int i = 0, len = Array.getLength(polys); i < len; i++) {
            Object poly = Array.get(polys, i);
            Object verts = polyVertices.get(poly);
            for (int j = 0, vl = Array.getLength(verts); j < vl; j++) {
                Vector3f pos = (Vector3f) vertexPos.get(Array.get(verts, j));
                if (!done.add(pos)) continue;
                m.transformPosition(tmp.set(pos.x / 16f, pos.y / 16f, pos.z / 16f));
                pos.set(tmp.x * 16f, tmp.y * 16f, tmp.z * 16f);
            }
            Vector3f normal = (Vector3f) polyNormal.get(poly);
            n.transform(normal);
            Direction d = Direction.getNearest(normal.x(), normal.y(), normal.z());
            normal.set(d.getStepX(), d.getStepY(), d.getStepZ()); // valeurs entières exactes (0.0f positif, jamais -0.0f)
        }
    }

    private static boolean init() {
        if (cubePolygons != null) return true;
        try {
            partCubes = ObfuscationReflectionHelper.findField(ModelPart.class, "f_104212_");
            partChildren = ObfuscationReflectionHelper.findField(ModelPart.class, "f_104213_");
            cubePolygons = ObfuscationReflectionHelper.findField(ModelPart.Cube.class, "f_104341_");
            Class<?> poly = cubePolygons.getType().getComponentType();
            polyVertices = ObfuscationReflectionHelper.findField(poly, "f_104359_");
            polyNormal = ObfuscationReflectionHelper.findField(poly, "f_104360_");
            vertexPos = ObfuscationReflectionHelper.findField(polyVertices.getType().getComponentType(), "f_104371_");
            return true;
        } catch (Exception e) {
            failed = true;
            cubePolygons = null;
            WaveSurvivorMod.LOGGER.warn("[Armure] Réflexion des modèles impossible ({}), modèles d'armure non aplatis.", e.toString());
            return false;
        }
    }
}
