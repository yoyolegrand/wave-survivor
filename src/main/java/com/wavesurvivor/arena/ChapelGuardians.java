package com.wavesurvivor.arena;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
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
 * GARDIENS DE LA CHAPELLE (ruine générée « wavesurvivor:chapelle »), en deux temps, une seule fois chacun par chapelle (mémorisé avec le monde) :
 * 1. le cimetière se réveille quand un joueur entre dans la zone « trigger_graveyard » ou passe le portail (« entrance ») : la cloche sonne et 4 à 6
 *    morts-vivants sortent des tombes (« guardian_spawns_graveyard ») ;
 * 2. les gardiens de la nef (2 squelettes en armure, plus résistants, « guardian_spawns_nave ») apparaissent quand un joueur s'approche du grand coffre
 *    (« chest_main », à 6 blocs : l'ouvrir suppose d'être plus près).
 * Seules les chapelles générées naturellement (structure) sont gérées : une chapelle posée à la commande reste calme (pratique pour la visiter).
 */
public class ChapelGuardians {

    public static final String ARENA_ID = "chapelle";

    /** Chapelles dont les gardiens sont déjà sortis (identifiées par le chunk de départ de la structure). */
    public static class Data extends SavedData {
        private final Set<Long> done = new HashSet<>();
        /** Chapelles dont les gardiens de la nef sont sortis. */
        private final Set<Long> nave = new HashSet<>();

        public static Data get(ServerLevel level) {
            return level.getServer().overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "wavesurvivor_chapels");
        }

        /** Cette ruine est-elle déjà marquée (sans la marquer) ? */
        public boolean has(long key) { return done.contains(key); }

        /** Marque une ruine comme déclenchée ; faux si elle l'était déjà. */
        public boolean claim(long key) {
            if (!done.add(key)) return false;
            setDirty();
            return true;
        }

        static Data load(CompoundTag tag) {
            Data d = new Data();
            for (Tag t : tag.getList("done", Tag.TAG_LONG)) d.done.add(((LongTag) t).getAsLong());
            for (Tag t : tag.getList("nave", Tag.TAG_LONG)) d.nave.add(((LongTag) t).getAsLong());
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag l = new ListTag();
            for (long v : done) l.add(LongTag.valueOf(v));
            tag.put("done", l);
            ListTag n = new ListTag();
            for (long v : nave) n.add(LongTag.valueOf(v));
            tag.put("nave", n);
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
        boolean graveyardDone = data.done.contains(key), naveDone = data.nave.contains(key);
        if (graveyardDone && naveDone) return;
        var markers = bp.worldMarkers(piece.ox(), piece.oz(), piece.y0(), rot);
        BlockPos pos = p.blockPosition();
        if (!graveyardDone && (inBox(markers.get("trigger_graveyard"), 0, pos) || inBox(markers.get("entrance"), 2, pos))) {
            data.done.add(key);
            data.setDirty();
            awakenGraveyard(level, p, markers);
        }
        List<BlockPos> chest = markers.getOrDefault("chest_main", List.of());
        if (!naveDone && !chest.isEmpty() && chest.get(0).closerThan(pos, 6.0)) {
            data.nave.add(key);
            data.setDirty();
            awakenNave(level, p, markers);
        }
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

    /** Le point est-il dans la boîte formée par les repères (leurs coins), élargie de {@code grow} blocs ? */
    private static boolean inBox(List<BlockPos> pts, int grow, BlockPos pos) {
        if (pts == null || pts.isEmpty()) return false;
        BoundingBox box = BoundingBox.fromCorners(pts.get(0), pts.get(pts.size() - 1));
        if (grow > 0) box = box.inflatedBy(grow);
        return box.isInside(pos);
    }

    /** Cimetière : la cloche sonne, 4 à 6 morts-vivants sortent des tombes. */
    private static void awakenGraveyard(ServerLevel level, ServerPlayer p, java.util.Map<String, List<BlockPos>> markers) {
        List<BlockPos> bells = markers.getOrDefault("bell", List.of());
        BlockPos bell = bells.isEmpty() ? p.blockPosition() : bells.get(0);
        level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4f, 0.7f);
        level.playSound(null, bell, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 3f, 0.6f);
        List<BlockPos> spawns = new ArrayList<>(markers.getOrDefault("guardian_spawns_graveyard", List.of()));
        Collections.shuffle(spawns, new java.util.Random(level.random.nextLong()));
        int count = Math.min(spawns.size(), 4 + level.random.nextInt(3));
        for (int i = 0; i < count; i++) {
            BlockPos at = spawns.get(i);
            Mob m = spawn(level, p, at, level.random.nextInt(3) != 0);
            if (m != null && level.random.nextInt(3) == 0) { // quelques-uns portent de l'or ou de la maille
                boolean gold = level.random.nextBoolean();
                m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(gold ? Items.GOLDEN_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
                m.setDropChance(EquipmentSlot.CHEST, 0.05f);
            }
            level.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.03);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, 20, 0.4, 0.1, 0.4, 0.1);
        }
        p.displayClientMessage(Component.literal(WSLang.t("ruin.chapel.guardians")), true);
    }

    /** Nef : 2 gardiens (squelettes en armure, plus résistants) entre le joueur et la sortie. */
    private static void awakenNave(ServerLevel level, ServerPlayer p, java.util.Map<String, List<BlockPos>> markers) {
        for (BlockPos at : markers.getOrDefault("guardian_spawns_nave", List.of())) {
            Mob m = spawn(level, p, at, false);
            if (m == null) continue;
            m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            m.setDropChance(EquipmentSlot.MAINHAND, 0.05f);
            m.setDropChance(EquipmentSlot.CHEST, 0.05f);
            var hp = m.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(36); m.setHealth(36f); }
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 2f, 0.6f);
        p.displayClientMessage(Component.literal(WSLang.t("ruin.chapel.nave")), true);
    }

    /** Un gardien : zombie (2 sur 3, sinon squelette), casque pour ne pas brûler au soleil, équipement variable ; il vise le joueur. */
    private static Mob spawn(ServerLevel level, ServerPlayer target, BlockPos at, boolean zombie) {
        Mob m = (Mob) (zombie ? EntityType.ZOMBIE : EntityType.SKELETON).create(level);
        if (m == null) return null;
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
        return m;
    }
}
