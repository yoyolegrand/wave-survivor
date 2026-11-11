package com.wavesurvivor.horde.rift;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.roulette.RouletteChestBlock;
import com.wavesurvivor.horde.roulette.RouletteChestBlockEntity;
import com.wavesurvivor.horde.roulette.RouletteChestConfig;
import com.wavesurvivor.horde.roulette.RouletteChestManager;
import com.wavesurvivor.horde.roulette.RouletteChestRegistry;
import com.wavesurvivor.horde.roulette.RouletteReward;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.item.RelicSets;
import com.wavesurvivor.item.RiftRelics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 2.0 — LE COFFRE DU TITAN : coffre roulette livré avec le mod (couche de base, modifiable en jeu comme les autres), deux fois plus grand
 * (champ « scale »). Il n'apparaît que par la mise en scène de la victoire, près du centre de l'île ; chaque joueur présent reçoit 1 Clé du Titan.
 * Quand toutes les clés distribuées ont servi (ou à la fermeture de la Faille), il implose. Une clé non utilisée reste valable pour un prochain coffre.
 * Jauge de la Faille (par joueur, sauvegardée) : chaque tirage sans relique de la Faille donne +1 % de chance Légendaire aux suivants ; retour à 0
 * dès qu'une relique de la Faille sort.
 * Le coffre agrandi déborde de son bloc : une entité d'interaction invisible, à sa taille, reçoit les clics (droit : ouvrir, gauche : aperçu).
 */
public class TitanChest {

    public static final String NAME = "Coffre du Titan", KEY = "Clé du Titan";
    /** Étiquette de l'entité d'interaction (zone cliquable du coffre agrandi). */
    private static final String TAG = "ws_titan_chest";
    private static final DustParticleOptions TURQUOISE = new DustParticleOptions(new Vector3f(0.27f, 0.90f, 0.86f), 1.6f);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.69f, 0.44f, 1.0f), 1.6f);

    /** Coffre posé (un seul à la fois). */
    private static ServerLevel level;
    private static BlockPos pos;
    private static UUID hitbox;
    /** Clés distribuées pour ce coffre et pas encore utilisées. */
    private static int keysOut = 0;
    /** Implosion prévue (tick du serveur), -1 = aucune. */
    private static long implodeAt = -1;

    public static boolean isTitanChest(RouletteChestConfig cfg) {
        return cfg != null && NAME.equals(cfg.chestName());
    }

    public static RouletteChestConfig config() {
        return RouletteChestRegistry.getByChestName(NAME);
    }

    public static BlockPos pos() { return pos; }

    // ─── Jauge de la Faille et contenu ───

    /** Une relique de la famille Faille ? */
    public static boolean isRiftRelic(String itemId) {
        if (itemId == null) return false;
        Item it = BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(itemId.trim()));
        for (Item r : RelicSets.Family.RIFT.items()) if (r == it) return true;
        return false;
    }

    /** Chances du tirage avec la Jauge : +X points de pourcentage sur les lots Légendaires, pris aux autres (total inchangé). */
    public static List<RouletteReward> adjust(RouletteChestConfig cfg, List<RouletteReward> in, ServerPlayer p) {
        int g = RiftRelics.gauge(p);
        if (!isTitanChest(cfg) || g <= 0 || in.isEmpty()) return in;
        double leg = 0, other = 0;
        int nLeg = 0;
        for (RouletteReward r : in) {
            if (r.rarity() == RouletteReward.Rarity.LEGENDARY) { leg += r.chance(); nLeg++; } else other += r.chance();
        }
        if (nLeg == 0 || other <= 0) return in;
        double total = leg + other, add = total * g / 100.0;
        add = Math.min(add, other * 0.9);
        double f = (other - add) / other;
        List<RouletteReward> out = new ArrayList<>();
        for (RouletteReward r : in) {
            double c = r.rarity() == RouletteReward.Rarity.LEGENDARY ? r.chance() + add / nLeg : r.chance() * f;
            out.add(new RouletteReward(r.item(), r.minQty(), r.maxQty(), c, r.rarity(), r.displayName()));
        }
        return out;
    }

    /** Tirages faits avec des Clés du Titan : Jauge mise à jour, clés comptées (le coffre implose quand toutes ont servi). */
    public static void onDraw(ServerPlayer p, RouletteChestConfig cfg, List<RouletteReward> rolled, int keys, BlockPos at) {
        if (!isTitanChest(cfg)) return;
        boolean relic = false;
        for (RouletteReward r : rolled) if (isRiftRelic(r.item())) relic = true;
        int before = RiftRelics.gauge(p);
        if (relic) {
            RiftRelics.setGauge(p, 0);
            if (before > 0) p.sendSystemMessage(Component.literal(WSLang.t("titan_chest.gauge_reset")));
        } else {
            RiftRelics.setGauge(p, before + Math.max(1, rolled.size()));
            p.sendSystemMessage(Component.literal(WSLang.t("titan_chest.gauge_up", RiftRelics.gauge(p))));
        }
        if (pos != null && pos.equals(at) && keysOut > 0) {
            keysOut = Math.max(0, keysOut - Math.max(1, keys));
            if (keysOut == 0) {
                // Le dernier tirage se joue d'abord (animation de la roulette), puis le coffre implose
                implodeAt = p.getServer().getTickCount() + Math.max(5, cfg.animationDurationSeconds()) * 20L + 80L;
            }
        }
    }

    /** Une Clé du Titan. */
    public static ItemStack key() {
        RouletteChestConfig cfg = config();
        return cfg == null ? ItemStack.EMPTY : RouletteChestManager.createKeyItem(cfg);
    }

    /** Une Clé du Titan à chaque joueur, avec la valeur de sa Jauge de la Faille. */
    public static void giveKeys(List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            ItemStack k = key();
            if (k.isEmpty()) continue;
            net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, k);
            keysOut++;
            int g = RiftRelics.gauge(p);
            p.sendSystemMessage(Component.literal(WSLang.t("titan_chest.key_received") + (g > 0 ? " " + WSLang.t("titan_chest.gauge", g) : "")));
        }
    }

    // ─── Pose, zone cliquable, implosion ───

    /** Taille du coffre (champ « scale » de sa configuration). */
    public static double scale() {
        RouletteChestConfig cfg = config();
        return cfg == null ? 1.0 : RouletteChestManager.styleFor(cfg).scale;
    }

    /** Pose le Coffre du Titan à {@code at} (le terrain autour est dégagé), avec sa zone cliquable. */
    public static boolean place(ServerLevel l, BlockPos at, Direction facing) {
        RouletteChestConfig cfg = config();
        if (cfg == null) {
            WaveSurvivorMod.LOGGER.warn("[Faille] Coffre du Titan introuvable dans les coffres roulette (supprimé ?).");
            return false;
        }
        remove(false);
        double s = scale();
        int r = (int) Math.ceil((s - 1) / 2.0 + 0.01);
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-r, 0, -r), at.offset(r, (int) Math.ceil(s), r))) {
            if (!l.getBlockState(q).isAir()) l.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
        }
        if (l.getBlockState(at.below()).isAir()) l.setBlock(at.below(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        l.setBlock(at, com.wavesurvivor.registry.ModBlocks.ROULETTE_CHEST.get().defaultBlockState().setValue(RouletteChestBlock.FACING, facing), 3);
        if (l.getBlockEntity(at) instanceof RouletteChestBlockEntity be) {
            be.setCustomName(Component.literal(cfg.chestName()));
            be.setColor(RouletteChestManager.colorFor(cfg));
            be.setStyle(RouletteChestManager.styleFor(cfg).toJson());
        }
        RouletteChestManager.registerIdle(at, cfg);
        level = l;
        pos = at.immutable();
        implodeAt = -1;
        // Zone cliquable à la taille du coffre agrandi (un coffre fait 14/16 de bloc)
        Interaction hb = EntityType.INTERACTION.create(l);
        if (hb != null) {
            hb.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
            CompoundTag t = hb.saveWithoutId(new CompoundTag());
            t.putFloat("width", (float) (0.9 * s + 0.1));
            t.putFloat("height", (float) (0.9 * s + 0.1));
            t.putBoolean("response", false);
            hb.load(t);
            hb.addTag(TAG);
            if (l.addFreshEntity(hb)) hitbox = hb.getUUID();
        }
        return true;
    }

    /** Retire le coffre posé ; {@code implode} = avec l'éclat turquoise. */
    public static void remove(boolean implode) {
        ServerLevel l = level;
        BlockPos at = pos;
        if (l != null && at != null) {
            if (implode) implodeFx(l, at);
            RouletteChestManager.unregister(at);
            if (l.getBlockState(at).getBlock() instanceof RouletteChestBlock) l.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            if (hitbox != null) {
                Entity e = l.getEntity(hitbox);
                if (e != null) e.discard();
            }
            // Zones cliquables orphelines (rechargement du monde)
            for (Interaction i : l.getEntitiesOfClass(Interaction.class, new net.minecraft.world.phys.AABB(at).inflate(8), i -> i.getTags().contains(TAG))) i.discard();
        }
        level = null;
        pos = null;
        hitbox = null;
        keysOut = 0;
        implodeAt = -1;
    }

    private static void implodeFx(ServerLevel l, BlockPos at) {
        double s = scale(), x = at.getX() + 0.5, y = at.getY() + 0.45 * s, z = at.getZ() + 0.5;
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 160, 1.2 * s, 1.0 * s, 1.2 * s, 0.6);
        l.sendParticles(TURQUOISE, x, y, z, 90, 0.6 * s, 0.6 * s, 0.6 * s, 0.05);
        l.sendParticles(ParticleTypes.END_ROD, x, y, z, 60, 0.2, 0.2, 0.2, 0.35);
        l.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
        l.playSound(null, at, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2f, 0.6f);
        l.playSound(null, at, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 2f, 0.5f);
    }

    /** Fermeture de la Faille : le coffre implose (les clés non utilisées restent valables). */
    public static void clear(ServerLevel l) {
        if (pos != null) remove(true);
    }

    // ─── Événements : zone cliquable, implosion programmée ───

    private static boolean ours(Entity e) {
        return e instanceof Interaction && e.getTags().contains(TAG);
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!ours(e.getTarget())) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (e.getLevel().isClientSide || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getLevel() instanceof ServerLevel l)) return;
        BlockPos at = BlockPos.containing(e.getTarget().getX(), e.getTarget().getY(), e.getTarget().getZ());
        RouletteChestConfig cfg = RouletteChestManager.getConfigAt(at);
        if (cfg == null) cfg = config();
        if (cfg != null) RouletteChestManager.tryOpenChest(sp, l, at, cfg, e.getItemStack());
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        if (!ours(e.getTarget())) return;
        e.setCanceled(true);
        RouletteChestConfig cfg = config();
        if (e.getEntity() instanceof ServerPlayer sp && cfg != null) RouletteChestManager.showLoot(sp, cfg);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        long now = e.getServer().getTickCount();
        Cinematic.tick(e.getServer(), now);
        if (implodeAt > 0 && now >= implodeAt && pos != null) {
            if (RouletteChestManager.isBusy(pos)) { implodeAt = now + 20; return; }   // une roulette tourne encore
            remove(true);
        }
    }

    /** Mise en scène de la victoire (étape suivante). */
    static final class Cinematic {
        static void tick(MinecraftServer server, long now) { }
    }
}
