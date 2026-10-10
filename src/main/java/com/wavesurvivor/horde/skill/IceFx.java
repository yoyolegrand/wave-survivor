package com.wavesurvivor.horde.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EFFETS DE GLACE EN BLOCS (Pointes de glace, Fracture de glace…) — « block displays » de Minecraft :
 * des blocs purement visuels (aucune collision, le terrain n'est jamais modifié), qu'on peut pencher, étirer et
 * animer (montée, rétraction). Chaque bloc est étiqueté « ws_fx » et retiré tout seul ; ceux qui survivraient à un
 * arrêt du serveur sont supprimés dès que leur chunk se recharge.
 */
public final class IceFx {

    public static final String TAG = "ws_fx";
    private static final Map<UUID, Display.BlockDisplay> ACTIVE = new ConcurrentHashMap<>();

    public static final BlockState PACKED = Blocks.PACKED_ICE.defaultBlockState();
    public static final BlockState BLUE = Blocks.BLUE_ICE.defaultBlockState();
    public static final BlockState CLEAR = Blocks.ICE.defaultBlockState();
    public static final BlockState FROST_GLASS = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
    public static final BlockState CRACK = Blocks.CYAN_STAINED_GLASS.defaultBlockState();

    /** Instancié une seule fois, pour l'enregistrement des événements de nettoyage (WaveSurvivorMod). */
    public IceFx() {}

    // ─── Briques de base ───

    /** Transformation d'un bloc : pivot au centre de sa base, rotation, échelle, décalage le long de son axe. */
    private static CompoundTag transform(Quaternionf rot, float sx, float sy, float sz, float lift) {
        Vector3f t = rot.transform(new Vector3f(-sx / 2f, lift, -sz / 2f));
        CompoundTag tag = new CompoundTag();
        tag.put("translation", floats(t.x, t.y, t.z));
        tag.put("left_rotation", floats(rot.x, rot.y, rot.z, rot.w));
        tag.put("scale", floats(sx, sy, sz));
        tag.put("right_rotation", floats(0, 0, 0, 1));
        return tag;
    }

    private static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) l.add(FloatTag.valueOf(f));
        return l;
    }

    /** Fait apparaître un bloc visuel à la position donnée (lumineux, sans ombre). */
    public static Display.BlockDisplay spawn(ServerLevel level, double x, double y, double z, BlockState state,
                                             Quaternionf rot, float sx, float sy, float sz, float lift) {
        Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level);
        if (d == null) return null;
        d.moveTo(x, y, z, 0f, 0f);
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        tag.put("block_state", NbtUtils.writeBlockState(state));
        tag.put("transformation", transform(rot, sx, sy, sz, lift));
        CompoundTag light = new CompoundTag();
        light.putInt("sky", 15);
        light.putInt("block", 15);
        tag.put("brightness", light);
        tag.putFloat("view_range", 1.5f);
        d.load(tag);
        d.addTag(TAG);
        ACTIVE.put(d.getUUID(), d);
        level.addFreshEntity(d);
        return d;
    }

    /** Anime vers une nouvelle forme (interpolée côté client sur « duration » ticks). */
    public static void animate(Display.BlockDisplay d, Quaternionf rot, float sx, float sy, float sz, float lift, int duration) {
        if (d == null || d.isRemoved()) return;
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        tag.put("transformation", transform(rot, sx, sy, sz, lift));
        tag.putInt("interpolation_duration", Math.max(0, duration));
        tag.putInt("start_interpolation", 0);
        d.load(tag);
    }

    public static void remove(Display.BlockDisplay d) {
        if (d == null) return;
        ACTIVE.remove(d.getUUID());
        if (!d.isRemoved()) d.discard();
    }

    // ─── Formes composées ───

    /**
     * Un pic de glace qui jaillit du sol (3 étages qui s'affinent, tournés à 45° pour une pointe en losange),
     * reste « hold » ticks puis se rétracte et disparaît.
     * @param tilt inclinaison (radians, 0 = droit) ; dir direction vers laquelle il penche (radians, sur le plan XZ) ;
     *             height hauteur totale ; width largeur de la base
     */
    public static void spike(ServerLevel level, MinecraftServer server, double x, double y, double z,
                             float height, float width, float tilt, float dir, int delay, int hold) {
        // Penche vers « dir », puis tourne à 45° sur lui-même pour une pointe en losange
        Quaternionf rot = new Quaternionf().rotateY(-dir).rotateZ(-tilt).rotateY((float) Math.toRadians(45));
        // étages : [bloc, largeur relative, hauteur relative, décalage relatif]
        BlockState[] mats = {PACKED, BLUE, CLEAR};
        float[][] parts = {{1.0f, 0.45f, 0.0f}, {0.66f, 0.35f, 0.42f}, {0.34f, 0.32f, 0.74f}};
        DelayedActionScheduler.schedule(server, Math.max(0, delay), () -> {
            Display.BlockDisplay[] ds = new Display.BlockDisplay[parts.length];
            for (int i = 0; i < parts.length; i++) {
                float w = width * parts[i][0];
                ds[i] = spawn(level, x, y, z, mats[i], rot, w, 0.01f, w, 0f);
            }
            // Jaillissement (3 ticks)
            DelayedActionScheduler.schedule(server, 1, () -> {
                for (int i = 0; i < parts.length; i++) {
                    float w = width * parts[i][0];
                    animate(ds[i], rot, w, height * parts[i][1], w, height * parts[i][2], 3);
                }
            }, "ice_fx rise");
            // Rétraction puis disparition
            DelayedActionScheduler.schedule(server, 4 + hold, () -> {
                for (int i = 0; i < parts.length; i++) {
                    float w = width * parts[i][0];
                    animate(ds[i], rot, w * 0.6f, 0.01f, w * 0.6f, 0f, 6);
                }
            }, "ice_fx sink");
            DelayedActionScheduler.schedule(server, 11 + hold, () -> {
                for (Display.BlockDisplay d : ds) remove(d);
            }, "ice_fx remove");
        }, "ice_fx spike");
    }

    /** Une dalle de givre plate posée au sol (repère d'avertissement), retirée par l'appelant. */
    public static Display.BlockDisplay floorTile(ServerLevel level, double x, double y, double z, float size, BlockState state) {
        return spawn(level, x, y + 0.02, z, state, new Quaternionf(), size, 0.03f, size, 0f);
    }

    /** Une fissure : une fine bande lumineuse au sol, orientée selon l'angle (radians), qui part du point donné. */
    public static Display.BlockDisplay crack(ServerLevel level, double x, double y, double z, float angle, float length, float thickness) {
        Quaternionf rot = new Quaternionf().rotateY(-angle);
        Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level);
        if (d == null) return null;
        // La bande part du centre : pivot à son extrémité (et non à son milieu)
        d.moveTo(x, y + 0.03, z, 0f, 0f);
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        tag.put("block_state", NbtUtils.writeBlockState(CRACK));
        Vector3f t = rot.transform(new Vector3f(0f, 0f, -thickness / 2f));
        CompoundTag tr = new CompoundTag();
        tr.put("translation", floats(t.x, t.y, t.z));
        tr.put("left_rotation", floats(rot.x, rot.y, rot.z, rot.w));
        tr.put("scale", floats(Math.max(0.01f, length), 0.02f, thickness));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("transformation", tr);
        CompoundTag light = new CompoundTag();
        light.putInt("sky", 15);
        light.putInt("block", 15);
        tag.put("brightness", light);
        d.load(tag);
        d.addTag(TAG);
        ACTIVE.put(d.getUUID(), d);
        level.addFreshEntity(d);
        return d;
    }

    /** Allonge une fissure existante (interpolé). */
    public static void growCrack(Display.BlockDisplay d, float angle, float length, float thickness, int duration) {
        if (d == null || d.isRemoved()) return;
        Quaternionf rot = new Quaternionf().rotateY(-angle);
        Vector3f t = rot.transform(new Vector3f(0f, 0f, -thickness / 2f));
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        CompoundTag tr = new CompoundTag();
        tr.put("translation", floats(t.x, t.y, t.z));
        tr.put("left_rotation", floats(rot.x, rot.y, rot.z, rot.w));
        tr.put("scale", floats(Math.max(0.01f, length), 0.02f, thickness));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("transformation", tr);
        tag.putInt("interpolation_duration", Math.max(0, duration));
        tag.putInt("start_interpolation", 0);
        d.load(tag);
    }

    // ─── Nettoyage ───

    /** Supprime tout bloc d'effet qui réapparaîtrait (chunk rechargé après un arrêt du serveur). */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent e) {
        Entity en = e.getEntity();
        if (e.getLevel().isClientSide || !(en instanceof Display.BlockDisplay)) return;
        if (en.getTags().contains(TAG) && !ACTIVE.containsKey(en.getUUID())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onStopping(ServerStoppingEvent e) {
        for (Display.BlockDisplay d : ACTIVE.values()) if (!d.isRemoved()) d.discard();
        ACTIVE.clear();
    }
}
