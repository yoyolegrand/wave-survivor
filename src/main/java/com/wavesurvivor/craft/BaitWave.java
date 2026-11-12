package com.wavesurvivor.craft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.bossrush.BossRush;
import com.wavesurvivor.horde.editor.HordeJson;
import com.wavesurvivor.horde.kingdom.KingdomManager;
import com.wavesurvivor.horde.rift.RiftManager;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.EventFeedPacket;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 2.0 — VAGUE DU TOTEM D'APPÂT : à l'activation du totem, une chance (« waveChance », 25 % par défaut) d'invoquer une vague de monstres
 * (zombies, squelettes, squelettes wither, araignées… liste pondérée « waveMobs ») sur un anneau autour du totem.
 * Désactivée pendant une partie (horde, Kingdom, Faille, Boss Rush) sauf si « waveInGame » vaut true.
 * Les monstres invoqués portent l'étiquette TAG, peuvent lâcher un Fragment de souillure et disparaissent s'ils restent
 * trop longtemps sans joueur proche. Réglages : crafting_fragments.json › blocks › bait_totem.
 */
public class BaitWave {

    public static final String TAG = "ws_bait_wave";
    private static final String B = "bait_totem";

    /** Monstres invoqués encore suivis (UUID → dernier tick où un joueur était proche) ; état en mémoire seulement. */
    private static final Map<UUID, Long> SEEN = new HashMap<>();
    private static final Map<UUID, ServerLevel> LEVELS = new HashMap<>();

    private record Entry(EntityType<?> type, int weight) {}

    /** Partie en cours (horde, Kingdom, Faille, Boss Rush) ? */
    private static boolean inGame() {
        return HordeManager.get().isRunning() || KingdomManager.isActive() || RiftManager.isActive() || BossRush.active();
    }

    /** Tente la vague à l'activation du totem ; renvoie true si elle a été lancée. */
    public static boolean tryTrigger(ServerLevel level, BlockPos pos) {
        double chance = CraftConfig.blockNum(B, "waveChance", 25);
        if (chance <= 0 || level.random.nextDouble() * 100 >= chance) return false;
        if (inGame() && !HordeJson.bool(CraftConfig.block(B), "waveInGame", false)) return false;

        List<Entry> mobs = mobs();
        if (mobs.isEmpty()) return false;
        int total = mobs.stream().mapToInt(Entry::weight).sum();

        double r = CraftConfig.blockNum(B, "radius", 64);
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : level.players())
            if (!p.isSpectator() && p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= r * r) near.add(p);

        RandomSource rnd = level.random;
        int min = (int) Math.max(0, CraftConfig.blockNum(B, "waveMin", 6));
        int max = (int) Math.max(min, CraftConfig.blockNum(B, "waveMax", 10));
        int count = min + rnd.nextInt(max - min + 1) + (int) CraftConfig.blockNum(B, "wavePerExtraPlayer", 1) * Math.max(0, near.size() - 1);
        double rIn = Math.max(2, CraftConfig.blockNum(B, "waveRingMin", 16));
        double rOut = Math.max(rIn + 1, CraftConfig.blockNum(B, "waveRingMax", 28));

        long now = level.getGameTime();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos at = findSpot(level, pos, rIn, rOut, rnd);
            if (at == null) continue;
            EntityType<?> type = pick(mobs, total, rnd);
            Entity e = type.create(level);
            if (!(e instanceof Mob m)) { if (e != null) e.discard(); continue; }
            m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, rnd.nextFloat() * 360f, 0f);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
            m.addTag(TAG);
            ServerPlayer target = nearest(near, at);
            if (target != null) m.setTarget(target);
            level.addFreshEntity(m);
            level.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.5, 0.3, 0.02);
            SEEN.put(m.getUUID(), now);
            LEVELS.put(m.getUUID(), level);
            spawned++;
        }
        if (spawned == 0) return false;

        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7f, 0.8f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.05);
        for (ServerPlayer p : near)
            EventFeedPacket.toPlayer(p, Component.literal(WSLang.t("craft.block.bait_wave", spawned)), "☠", EventFeedPacket.DANGER, true);
        return true;
    }

    /** Liste pondérée « waveMobs » (id + weight) ; entrées inconnues ignorées. */
    private static List<Entry> mobs() {
        List<Entry> list = new ArrayList<>();
        JsonObject o = CraftConfig.block(B);
        if (!o.has("waveMobs") || !o.get("waveMobs").isJsonArray()) return list;
        for (JsonElement el : o.getAsJsonArray("waveMobs")) {
            if (!el.isJsonObject()) continue;
            JsonObject eo = el.getAsJsonObject();
            ResourceLocation id = ResourceLocation.tryParse(HordeJson.str(eo, "id", ""));
            int w = HordeJson.num(eo, "weight", 1);
            if (id == null || w <= 0 || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) continue;
            list.add(new Entry(ForgeRegistries.ENTITY_TYPES.getValue(id), w));
        }
        return list;
    }

    private static EntityType<?> pick(List<Entry> mobs, int total, RandomSource rnd) {
        int k = rnd.nextInt(total);
        for (Entry e : mobs) if ((k -= e.weight()) < 0) return e.type();
        return mobs.get(0).type();
    }

    private static ServerPlayer nearest(List<ServerPlayer> players, BlockPos at) {
        ServerPlayer best = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            double d = p.distanceToSqr(at.getX(), at.getY(), at.getZ());
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    /** Point au sol sur l'anneau (rIn..rOut) proche de la hauteur du totem : sol solide, deux blocs libres, jamais dans un fluide. */
    private static BlockPos findSpot(ServerLevel level, BlockPos c, double rIn, double rOut, RandomSource rnd) {
        for (int tries = 0; tries < 12; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = rIn + rnd.nextDouble() * (rOut - rIn);
            int x = c.getX() + (int) Math.round(Math.cos(a) * d), z = c.getZ() + (int) Math.round(Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, c.getY(), z))) continue;
            for (int dy = 8; dy >= -8; dy--) {
                BlockPos p = new BlockPos(x, c.getY() + dy, z);
                BlockState below = level.getBlockState(p.below());
                if (!below.isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP) || !below.getFluidState().isEmpty()) continue;
                if (!free(level, p) || !free(level, p.above())) continue;
                return p;
            }
        }
        return null;
    }

    private static boolean free(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    /** Toutes les 5 s : oublie les monstres morts, fait disparaître ceux restés seuls trop longtemps (« waveDespawnSeconds »). */
    @SubscribeEvent
    public void onBaitTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || SEEN.isEmpty()) return;
        if (e.getServer().getTickCount() % 100 != 0) return;
        long limit = (long) CraftConfig.blockNum(B, "waveDespawnSeconds", 180) * 20;
        Iterator<Map.Entry<UUID, Long>> it = SEEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> en = it.next();
            ServerLevel level = LEVELS.get(en.getKey());
            Entity m = level == null ? null : level.getEntity(en.getKey());
            if (m == null || !m.isAlive()) { it.remove(); LEVELS.remove(en.getKey()); continue; }
            long now = level.getGameTime();
            if (level.getNearestPlayer(m, 32) != null) en.setValue(now);
            else if (now - en.getValue() > limit) {
                m.discard();
                it.remove();
                LEVELS.remove(en.getKey());
            }
        }
    }

    /** Butin en plus : chance (« waveFragmentChance » %) de lâcher un Fragment de souillure. */
    @SubscribeEvent
    public void onBaitDrops(LivingDropsEvent e) {
        Entity v = e.getEntity();
        if (!v.getTags().contains(TAG) || v.level().isClientSide) return;
        double pct = CraftConfig.blockNum(B, "waveFragmentChance", 5);
        if (pct > 0 && v.level().random.nextDouble() * 100 < pct)
            e.getDrops().add(new ItemEntity(v.level(), v.getX(), v.getY(), v.getZ(), new ItemStack(ModItems.TAINT_FRAGMENT.get())));
    }

    /** Oublie le suivi à l'arrêt du serveur (pas de référence vers un monde fermé). */
    @SubscribeEvent
    public void onBaitStop(net.minecraftforge.event.server.ServerStoppedEvent e) {
        SEEN.clear();
        LEVELS.clear();
    }
}
