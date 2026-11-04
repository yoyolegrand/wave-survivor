package com.wavesurvivor.arena;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GARDIENS DE LA CHAPELLE (ruine générée « wavesurvivor:chapelle ») : la première fois qu'un joueur entre dans la nef ou le cimetière, la cloche sonne et
 * 4 à 6 morts-vivants apparaissent aux repères « guardian_spawns » du plan. Une seule fois par chapelle (mémorisé avec le monde).
 * Seules les chapelles générées naturellement (structure) sont gérées : une chapelle posée à la commande reste calme (pratique pour la visiter).
 */
public class ChapelGuardians {

    public static final String ARENA_ID = "chapelle";
    /** Zones de déclenchement dans le repère du plan (x1, z1, x2, z2) : la nef avec le transept, et le cimetière. */
    private static final int[][] ZONES = {{10, 18, 22, 45}, {24, 27, 35, 41}};

    /** Chapelles dont les gardiens sont déjà sortis (identifiées par le chunk de départ de la structure). */
    public static class Data extends SavedData {
        private final Set<Long> done = new HashSet<>();

        public static Data get(ServerLevel level) {
            return level.getServer().overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "wavesurvivor_chapels");
        }

        /** Marque une ruine comme déclenchée ; faux si elle l'était déjà. */
        public boolean claim(long key) {
            if (!done.add(key)) return false;
            setDirty();
            return true;
        }

        static Data load(CompoundTag tag) {
            Data d = new Data();
            for (Tag t : tag.getList("done", Tag.TAG_LONG)) d.done.add(((LongTag) t).getAsLong());
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag l = new ListTag();
            for (long v : done) l.add(LongTag.valueOf(v));
            tag.put("done", l);
            return tag;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.isSpectator()) return;
        ServerLevel level = p.serverLevel();
        if (level.getGameTime() % 20 != 0 || level.getDifficulty() == Difficulty.PEACEFUL) return;
        Found f = locate(level, p.blockPosition(), ARENA_ID);
        if (f == null) return;
        StructureStart start = f.start();
        ArenaPiece piece = f.piece();
        ArenaBlueprint bp = f.bp();
        Rotation rot = piece.rotation();
        Data data = Data.get(level);
        long key = start.getChunkPos().toLong();
        if (data.done.contains(key) || !inZone(bp, piece, rot, p.blockPosition())) return;
        data.done.add(key);
        data.setDirty();
        awaken(level, p, bp, piece, rot);
    }

    /** Ruine générée sous un point : sa structure, sa pièce et son plan (null si le point n'est pas dans cette ruine). */
    public record Found(StructureStart start, ArenaPiece piece, ArenaBlueprint bp) {}

    public static Found locate(ServerLevel level, BlockPos pos, String id) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation("wavesurvivor", id));
        if (structure == null) return null;
        StructureStart start = level.structureManager().getStructureAt(pos, structure);
        if (!start.isValid()) return null;
        ArenaPiece piece = null;
        for (var sp : start.getPieces()) if (sp instanceof ArenaPiece ap) { piece = ap; break; }
        if (piece == null) return null;
        ArenaBlueprint bp = ArenaBlueprint.load(piece.arena());
        return bp == null ? null : new Found(start, piece, bp);
    }

    private static boolean inZone(ArenaBlueprint bp, ArenaPiece piece, Rotation rot, BlockPos pos) {
        for (int[] z : ZONES) {
            BlockPos a = bp.worldPos(piece.ox(), piece.oz(), piece.y0(), rot, z[0], 0, z[1]);
            BlockPos b = bp.worldPos(piece.ox(), piece.oz(), piece.y0(), rot, z[2], 12, z[3]);
            if (BoundingBox.fromCorners(a, b).isInside(pos)) return true;
        }
        return false;
    }

    private static void awaken(ServerLevel level, ServerPlayer p, ArenaBlueprint bp, ArenaPiece piece, Rotation rot) {
        var markers = bp.worldMarkers(piece.ox(), piece.oz(), piece.y0(), rot);
        List<BlockPos> bells = markers.getOrDefault("bell", List.of());
        BlockPos bell = bells.isEmpty() ? p.blockPosition() : bells.get(0);
        level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4f, 0.7f);
        level.playSound(null, bell, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 3f, 0.6f);
        List<BlockPos> spawns = new ArrayList<>(markers.getOrDefault("guardian_spawns", List.of()));
        Collections.shuffle(spawns, new java.util.Random(level.random.nextLong()));
        int count = Math.min(spawns.size(), 4 + level.random.nextInt(3));
        for (int i = 0; i < count; i++) spawn(level, p, spawns.get(i), level.random.nextInt(3) != 0);
        p.displayClientMessage(Component.literal(WSLang.t("ruin.chapel.guardians")), true);
    }

    /** Un gardien : zombie (2 sur 3, sinon squelette), casque pour ne pas brûler au soleil, équipement variable ; il vise le joueur. */
    private static void spawn(ServerLevel level, ServerPlayer target, BlockPos at, boolean zombie) {
        Mob m = (Mob) (zombie ? EntityType.ZOMBIE : EntityType.SKELETON).create(level);
        if (m == null) return;
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(level.random.nextBoolean() ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET));
        m.setDropChance(EquipmentSlot.HEAD, 0.05f);
        if (zombie && level.random.nextInt(3) == 0) {
            m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            m.setDropChance(EquipmentSlot.MAINHAND, 0.05f);
        }
        m.setPersistenceRequired();
        m.setTarget(target);
        level.addFreshEntity(m);
    }
}
