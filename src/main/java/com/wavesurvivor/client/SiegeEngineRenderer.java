package com.wavesurvivor.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.entity.SiegeEngineEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2.0 — Rendu des engins de siège (catapulte, baliste céleste) à partir de modèles JSON (assets/wavesurvivor/models/siege/*.json) :
 * groupes imbriqués (pivot + rotation, comme des ModelPart), cubes dont chaque face répète la tuile 16 × 16 de l'atlas (sous-quads de 16 px max),
 * pièces « glow » en pleine luminosité. Unités en pixels, origine au coin nord-ouest au sol ; l'engin regarde vers le sud.
 * Les animations sont calculées à partir des dates de tir et de rechargement synchronisées par l'entité.
 */
public class SiegeEngineRenderer extends EntityRenderer<SiegeEngineEntity> {

    private static final ResourceLocation ATLAS = new ResourceLocation(WaveSurvivorMod.MODID, "textures/entity/siege_engines.png");
    private static final int ATLAS_SIZE = 64;
    private static final int FULL_BRIGHT = 0xF000F0;

    // ─── Modèle JSON ───

    private static final class Group {
        String name, parent;
        float[] pivot = new float[3], rot = new float[3];
        final List<Group> children = new ArrayList<>();
        final List<Part> parts = new ArrayList<>();
    }

    private static final class Part {
        float[] from = new float[3], to = new float[3];
        int tu, tv;
        boolean glow;
    }

    /** Point d'ancrage dans un groupe (px) ; corde dynamique entre deux ancres (largeur en px, tuile de texture, lumineuse ou non). */
    private static final class Anchor {
        String group;
        float[] pos = new float[3];
    }

    private static final class Str {
        String from, to;
        float width = 0.8f;
        int tu, tv;
    }

    private static final class Model {
        float sx, sz;
        /** Poutre de la tour : angle chargée / après le tir (lus dans « animations.fire »), groupes masqués au pilote. */
        float armLoaded = 14f, armFired = -24f;
        final Set<String> pilotHidden = new HashSet<>();
        final Map<String, Anchor> anchors = new HashMap<>();
        final List<Str> strings = new ArrayList<>();
        final Map<String, Group> groups = new LinkedHashMap<>();
        final List<Group> roots = new ArrayList<>();
    }

    private static final Map<String, Model> CACHE = new HashMap<>();

    private static float[] f3(JsonElement e, float def) {
        float[] a = {def, def, def};
        if (e != null && e.isJsonArray()) {
            JsonArray j = e.getAsJsonArray();
            for (int i = 0; i < 3 && i < j.size(); i++) a[i] = j.get(i).getAsFloat();
        }
        return a;
    }

    private static Model load(String name) {
        Model cached = CACHE.get(name);
        if (cached != null) return cached;
        Model m = new Model();
        try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(new ResourceLocation(WaveSurvivorMod.MODID, "models/siege/" + name + ".json"))) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            float[] size = f3(o.get("size_px"), 0);
            m.sx = size[0];
            m.sz = size[2];
            for (var e : o.getAsJsonObject("groups").entrySet()) {
                JsonObject g = e.getValue().getAsJsonObject();
                Group gr = new Group();
                gr.name = e.getKey();
                gr.pivot = f3(g.get("pivot"), 0);
                gr.rot = f3(g.get("rotation"), 0);
                gr.parent = g.has("parent") && !g.get("parent").isJsonNull() ? g.get("parent").getAsString() : null;
                m.groups.put(gr.name, gr);
            }
            for (Group g : m.groups.values()) {
                Group p = g.parent != null ? m.groups.get(g.parent) : null;
                if (p != null) p.children.add(g); else m.roots.add(g);
            }
            JsonObject tiles = o.getAsJsonObject("tile_uv");
            if (o.has("animations") && o.getAsJsonObject("animations").has("fire")) {
                JsonObject fire = o.getAsJsonObject("animations").getAsJsonObject("fire");
                if (fire.has("arm.rotation.x") && fire.get("arm.rotation.x").isJsonArray() && fire.getAsJsonArray("arm.rotation.x").size() >= 2) {
                    m.armLoaded = fire.getAsJsonArray("arm.rotation.x").get(0).getAsFloat();
                    m.armFired = fire.getAsJsonArray("arm.rotation.x").get(1).getAsFloat();
                }
            }
            if (o.has("pilot_hidden_groups")) for (JsonElement g : o.getAsJsonArray("pilot_hidden_groups")) m.pilotHidden.add(g.getAsString());
            if (o.has("anchors")) {
                for (var e : o.getAsJsonObject("anchors").entrySet()) {
                    JsonObject ao = e.getValue().getAsJsonObject();
                    Anchor an = new Anchor();
                    an.group = ao.get("group").getAsString();
                    an.pos = f3(ao.get("pos"), 0);
                    m.anchors.put(e.getKey(), an);
                }
            }
            if (o.has("dynamic_strings")) {
                for (JsonElement se : o.getAsJsonArray("dynamic_strings")) {
                    JsonObject so = se.getAsJsonObject();
                    Str st = new Str();
                    st.from = so.get("from").getAsString();
                    st.to = so.get("to").getAsString();
                    if (so.has("width_px")) st.width = so.get("width_px").getAsFloat();
                    JsonArray uv = tiles.getAsJsonArray(so.has("texture") ? so.get("texture").getAsString() : "cyan");
                    st.tu = uv != null ? uv.get(0).getAsInt() : 0;
                    st.tv = uv != null ? uv.get(1).getAsInt() : 0;
                    m.strings.add(st);
                }
            }
            for (JsonElement pe : o.getAsJsonArray("parts")) {
                JsonObject po = pe.getAsJsonObject();
                Group g = m.groups.get(po.get("group").getAsString());
                if (g == null) continue;
                Part p = new Part();
                p.from = f3(po.get("from"), 0);
                p.to = f3(po.get("to"), 0);
                JsonArray uv = tiles.getAsJsonArray(po.get("texture").getAsString());
                p.tu = uv != null ? uv.get(0).getAsInt() : 0;
                p.tv = uv != null ? uv.get(1).getAsInt() : 0;
                p.glow = po.has("glow") && po.get("glow").getAsBoolean();
                g.parts.add(p);
            }
        } catch (Exception ex) {
            WaveSurvivor.LOG("Modèle d'engin illisible : " + name + " (" + ex.getMessage() + ")");
            return null;
        }
        CACHE.put(name, m);
        return m;
    }

    /** Petit relais de journal (évite une dépendance circulaire dans les logs). */
    private static final class WaveSurvivor {
        static void LOG(String s) { WaveSurvivorMod.LOGGER.error("[Faille] {}", s); }
    }

    // ─── Animation (écarts appliqués aux groupes nommés) ───

    private static final class Anim {
        /** Rotations absolues (degrés, remplacent la pose de repos) et translations (pixels) par groupe ; groupes cachés. */
        final Map<String, float[]> rot = new HashMap<>(), tr = new HashMap<>();
        final Set<String> hidden = new HashSet<>();
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * Math.max(0f, Math.min(1f, t)); }

    private static Anim animate(SiegeEngineEntity e, float partial, Model model) {
        Anim a = new Anim();
        float now = e.level().getGameTime() + partial;
        float t = now - e.fireTick();
        int rt = Math.max(1, e.reloadTicks());
        if (!e.isBallista()) {
            // Tour-catapulte automatique : la plateforme tourne seule vers sa cible (cap synchronisé), la poutre bascule au tir
            a.rot.put("turret", new float[]{0, -e.aimYaw(), 0});
            float armX;
            float ld = model.armLoaded, fr = model.armFired;
            if (t < 0 || t >= rt) {                                       // armée, boulet en place
                armX = ld;
            } else if (t < 10f) {                                         // tir : la poutre bascule, le boulet part
                armX = lerp(ld, fr, t / 10f);
                if (t >= 2f) a.hidden.add("ammo");
            } else if (t < 16f) {                                         // rebond
                armX = fr + 4f * (float) Math.sin(Math.PI * (t - 10f) / 6f);
                a.hidden.add("ammo");
            } else if (t < rt - 10f) {                                    // rechargement : la poutre remonte cran par cran
                float p = (t - 16f) / Math.max(1f, rt - 26f);
                armX = fr + (ld - fr) * (float) (Math.floor(p * 16f) / 16f);
                a.hidden.add("ammo");
            } else {                                                      // chargement : le boulet tombe dans la cuillère
                armX = ld;
            }
            a.rot.put("arm", new float[]{armX, 0, 0});
            return a;
        }
        // Baliste céleste : la tourelle suit le cap, l'arme l'inclinaison
        a.rot.put("turret", new float[]{0, -e.aimYaw(), 0});
        a.rot.put("weapon", new float[]{e.aimPitch(), 0, 0});
        float rp = now - e.reloadStart();
        boolean inReload = e.reloadStart() > -1000 && rp >= 0 && rp < rt;
        float wr = 0f, wl = 0f;
        if (inReload) {                                                   // rechargement : halo qui tourne, ailes repliées puis retour, carreau qui se matérialise
            float fold = (float) Math.sin(Math.PI * rp / rt);
            wr = -35f * fold;
            wl = 35f * fold;
            a.rot.put("halo", new float[]{0, now * 12f, 0});
            if (rp < rt * 0.85f) a.hidden.add("bolt");
        } else if (t >= 0 && t < 10f) {                                   // tir : ailes qui frémissent, corde qui claque, carreau qui glisse
            float s = (float) Math.sin(Math.PI * t / 10f);
            wr = -12f * s;
            wl = 12f * s;
            if (t < 2f) a.hidden.add("bolt");
            else if (t < 8f) a.tr.put("bolt", new float[]{0, 0, -10f * (1f - (t - 2f) / 6f)});
        }
        a.rot.put("wing_r", new float[]{0, wr, 0});
        a.rot.put("wing_l", new float[]{0, wl, 0});
        // Encoche de la corde : au tir elle avance de 20 px en 2 ticks (la corde claque) puis revient (entre deux tirs, ou pendant le rechargement) ;
        // à vide elle reste détendue.
        float nock;
        if (inReload) nock = 20f * (1f - rp / rt);
        else if (e.ammo() <= 0) nock = 20f;
        else if (t >= 0 && t < 2f) nock = 20f * t / 2f;
        else if (t >= 2f && t < 12f) nock = 20f * (1f - (t - 2f) / 10f);
        else nock = 0f;
        a.tr.put("string", new float[]{0, 0, nock});
        if (e.ammo() <= 0 && !inReload) a.hidden.add("bolt");
        return a;
    }

    // ─── Rendu ───

    public SiegeEngineRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    /** Un modèle par niveau et par variante (niveau 3 : la variante choisie). */
    private static String modelName(SiegeEngineEntity e) {
        int lv = e.engineLevel(), v = e.variant;
        if (e.isBallista()) {
            if (lv >= 3 && v >= 1) return "celestial_ballista_l3_" + new String[]{"seraphic", "solar", "judgement"}[Math.min(2, v - 1)];
            return "celestial_ballista_l" + (lv >= 2 ? 2 : 1);
        }
        if (lv >= 3 && v >= 1) return "catapult_tower_l3_" + new String[]{"infernal", "fragmentation", "trebuchet"}[Math.min(2, v - 1)];
        return "catapult_tower_l" + (lv >= 2 ? 2 : 1);
    }

    @Override
    public ResourceLocation getTextureLocation(SiegeEngineEntity e) { return ATLAS; }

    @Override
    public boolean shouldRender(SiegeEngineEntity e, Frustum frustum, double x, double y, double z) { return true; }

    @Override
    public void render(SiegeEngineEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        Model m = load(modelName(e));
        if (m == null) return;
        Anim a = animate(e, partial, m);
        // Le joueur qui pilote la baliste ne voit pas les groupes « pilot_hidden_groups » (halo…)
        var me = Minecraft.getInstance().player;
        if (e.isBallista() && me != null && me.getVehicle() == e && Minecraft.getInstance().options.getCameraType().isFirstPerson()) a.hidden.addAll(m.pilotHidden);
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(ATLAS));
        ps.pushPose();
        // Entité = centre de l'emprise au sol ; le modèle part de son coin nord-ouest, en pixels
        ps.translate(-m.sx / 32.0, 0, -m.sz / 32.0);
        Matrix4f rootInv = new Matrix4f(ps.last().pose()).invert();
        Map<String, float[]> anchors = new HashMap<>();
        for (Group g : m.roots) renderGroup(g, ps, vc, light, a, m, rootInv, anchors);
        // Cordes dynamiques : deux fins rubans lumineux entre chaque ancre, en coordonnées monde après les transformations des groupes
        for (Str st : m.strings) {
            float[] p0 = anchors.get(st.from), p1 = anchors.get(st.to);
            if (p0 != null && p1 != null) ribbon(ps.last(), vc, p0, p1, st);
        }
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, light);
    }

    private static void renderGroup(Group g, PoseStack ps, VertexConsumer vc, int light, Anim a, Model m, Matrix4f rootInv, Map<String, float[]> anchors) {
        if (a.hidden.contains(g.name)) return;
        ps.pushPose();
        float[] t = a.tr.get(g.name);
        if (t != null) ps.translate(t[0] / 16.0, t[1] / 16.0, t[2] / 16.0);
        float[] r = a.rot.getOrDefault(g.name, g.rot);
        ps.translate(g.pivot[0] / 16.0, g.pivot[1] / 16.0, g.pivot[2] / 16.0);
        // Rotation X puis Y puis Z autour du pivot
        ps.mulPose(new Quaternionf().rotationZYX((float) Math.toRadians(r[2]), (float) Math.toRadians(r[1]), (float) Math.toRadians(r[0])));
        ps.translate(-g.pivot[0] / 16.0, -g.pivot[1] / 16.0, -g.pivot[2] / 16.0);
        PoseStack.Pose pose = ps.last();
        for (Part p : g.parts) cube(pose, vc, p, p.glow ? FULL_BRIGHT : light);
        for (Map.Entry<String, Anchor> en : m.anchors.entrySet()) {
            if (!en.getValue().group.equals(g.name)) continue;
            float[] ap = en.getValue().pos;
            Vector4f v = new Vector4f(ap[0] / 16f, ap[1] / 16f, ap[2] / 16f, 1f);
            pose.pose().transform(v);
            rootInv.transform(v);
            anchors.put(en.getKey(), new float[]{v.x * 16f, v.y * 16f, v.z * 16f});
        }
        for (Group c : g.children) renderGroup(c, ps, vc, light, a, m, rootInv, anchors);
        ps.popPose();
    }

    /** Un fin ruban lumineux entre deux points (px) : deux quads croisés, pour qu'il se voie de tous côtés. */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer vc, float[] a, float[] b, Str st) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01f) return;
        dx /= len; dy /= len; dz /= len;
        // premier axe : perpendiculaire à la corde et à la verticale ; second : perpendiculaire aux deux
        float n1x = dy * 0 - dz * 1, n1y = dz * 0 - dx * 0, n1z = dx * 1 - dy * 0;   // dir × (0,1,0)
        float nl = (float) Math.sqrt(n1x * n1x + n1y * n1y + n1z * n1z);
        if (nl < 1e-3f) { n1x = 1; n1y = 0; n1z = 0; nl = 1; }
        n1x /= nl; n1y /= nl; n1z /= nl;
        float n2x = dy * n1z - dz * n1y, n2y = dz * n1x - dx * n1z, n2z = dx * n1y - dy * n1x;
        float h = st.width / 2f;
        float u0 = (st.tu + 6) / (float) ATLAS_SIZE, u1 = (st.tu + 10) / (float) ATLAS_SIZE;
        float v0 = (st.tv + 6) / (float) ATLAS_SIZE, v1 = (st.tv + 10) / (float) ATLAS_SIZE;
        float[][] axes = {{n1x * h, n1y * h, n1z * h}, {n2x * h, n2y * h, n2z * h}};
        for (float[] ax : axes) {
            vertex(pose, vc, FULL_BRIGHT, a[0] - ax[0], a[1] - ax[1], a[2] - ax[2], u0, v0, 0, 1, 0);
            vertex(pose, vc, FULL_BRIGHT, a[0] + ax[0], a[1] + ax[1], a[2] + ax[2], u1, v0, 0, 1, 0);
            vertex(pose, vc, FULL_BRIGHT, b[0] + ax[0], b[1] + ax[1], b[2] + ax[2], u1, v1, 0, 1, 0);
            vertex(pose, vc, FULL_BRIGHT, b[0] - ax[0], b[1] - ax[1], b[2] - ax[2], u0, v1, 0, 1, 0);
        }
    }

    /** Un cube : six faces, chacune remplie en répétant la tuile 16 × 16 (sous-quads de 16 px au plus). */
    private static void cube(PoseStack.Pose pose, VertexConsumer vc, Part p, int light) {
        float x0 = Math.min(p.from[0], p.to[0]), x1 = Math.max(p.from[0], p.to[0]);
        float y0 = Math.min(p.from[1], p.to[1]), y1 = Math.max(p.from[1], p.to[1]);
        float z0 = Math.min(p.from[2], p.to[2]), z1 = Math.max(p.from[2], p.to[2]);
        float w = x1 - x0, h = y1 - y0, d = z1 - z0;
        // origine (coin haut-gauche vu de l'extérieur), axe u (vers la droite), axe v (vers le bas), taille, normale
        face(pose, vc, p, light, x1, y1, z1, -1, 0, 0, 0, -1, 0, w, h, 0, 0, 1);   // sud (+Z)
        face(pose, vc, p, light, x0, y1, z0, 1, 0, 0, 0, -1, 0, w, h, 0, 0, -1);   // nord (-Z)
        face(pose, vc, p, light, x1, y1, z1, 0, 0, -1, 0, -1, 0, d, h, 1, 0, 0);   // est (+X)
        face(pose, vc, p, light, x0, y1, z0, 0, 0, 1, 0, -1, 0, d, h, -1, 0, 0);   // ouest (-X)
        face(pose, vc, p, light, x0, y1, z0, 1, 0, 0, 0, 0, 1, w, d, 0, 1, 0);     // dessus
        face(pose, vc, p, light, x0, y0, z1, 1, 0, 0, 0, 0, -1, w, d, 0, -1, 0);   // dessous
    }

    private static void face(PoseStack.Pose pose, VertexConsumer vc, Part p, int light,
                             float ox, float oy, float oz, float ux, float uy, float uz, float vx, float vy, float vz,
                             float a, float b, float nx, float ny, float nz) {
        if (a <= 0 || b <= 0) return;
        for (float u0 = 0; u0 < a - 1e-4f; u0 += 16) {
            for (float v0 = 0; v0 < b - 1e-4f; v0 += 16) {
                float du = Math.min(16, a - u0), dv = Math.min(16, b - v0);
                float uA = p.tu / (float) ATLAS_SIZE, uB = (p.tu + du) / (float) ATLAS_SIZE;
                float vA = p.tv / (float) ATLAS_SIZE, vB = (p.tv + dv) / (float) ATLAS_SIZE;
                vertex(pose, vc, light, ox + ux * u0 + vx * v0, oy + uy * u0 + vy * v0, oz + uz * u0 + vz * v0, uA, vA, nx, ny, nz);
                vertex(pose, vc, light, ox + ux * (u0 + du) + vx * v0, oy + uy * (u0 + du) + vy * v0, oz + uz * (u0 + du) + vz * v0, uB, vA, nx, ny, nz);
                vertex(pose, vc, light, ox + ux * (u0 + du) + vx * (v0 + dv), oy + uy * (u0 + du) + vy * (v0 + dv), oz + uz * (u0 + du) + vz * (v0 + dv), uB, vB, nx, ny, nz);
                vertex(pose, vc, light, ox + ux * u0 + vx * (v0 + dv), oy + uy * u0 + vy * (v0 + dv), oz + uz * u0 + vz * (v0 + dv), uA, vB, nx, ny, nz);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, int light, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        vc.vertex(pose.pose(), x / 16f, y / 16f, z / 16f).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), nx, ny, nz).endVertex();
    }
}
