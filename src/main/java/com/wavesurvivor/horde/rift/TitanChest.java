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
        Cinematic.cancel();
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

    /** Test (/ws rift chest) : la mise en scène complète devant le joueur, hors de la Faille (le « Titan » est 20 blocs plus loin). */
    public static void testVictory(ServerPlayer p) {
        var look = p.getLookAngle().multiply(1, 0, 1).normalize();
        BlockPos at = BlockPos.containing(p.getX() + look.x * 5, p.getY(), p.getZ() + look.z * 5);
        Cinematic.start(p.serverLevel(), new double[]{p.getX() + look.x * 20, p.getY(), p.getZ() + look.z * 20, 30}, at);
    }

    /** Test (/ws rift chest place) : le Coffre du Titan posé tout de suite devant le joueur, avec une clé. */
    public static boolean testPlace(ServerPlayer p) {
        var look = p.getLookAngle().multiply(1, 0, 1).normalize();
        BlockPos at = BlockPos.containing(p.getX() + look.x * 4, p.getY(), p.getZ() + look.z * 4);
        if (!place(p.serverLevel(), at, p.getDirection().getOpposite())) return false;
        giveKeys(List.of(p));
        return true;
    }

    /** Lance la mise en scène de la victoire (Titan vaincu) : le coffre descend de la Faille près du centre de l'île. */
    public static void startVictory(ServerLevel rd, double[] titan) {
        Cinematic.start(rd, titan);
    }

    /**
     * MISE EN SCÈNE DE LA VICTOIRE (~10 s, ticks depuis la mort du Titan) :
     *   0-40    le Titan se dissout en particules (portail, poussière violette / turquoise) qui tombent dans l'abîme ;
     *   40-60   la Faille (tourbillon dans le ciel) projette un faisceau turquoise sur l'emplacement du coffre ;
     *   60-140  le Coffre du Titan descend lentement dans le faisceau depuis 30 blocs, en tournant, dans une spirale de portail ;
     *   140     atterrissage : onde de choc, son grave et cristallin, petite secousse, titre « Le Titan est vaincu », 1 Clé du Titan par joueur.
     * La descente est un objet d'affichage (le coffre en objet, agrandi) dont la transformation est interpolée côté client par paliers de 10 ticks.
     */
    static final class Cinematic {
        static final int BEAM = 40, DROP = 60, LAND = 140, DROP_H = 30, SKY_H = 42;
        static ServerLevel l;
        static long t0 = -1;
        static double tx, ty, tz, th;
        static BlockPos at;
        static net.minecraft.world.entity.Display.ItemDisplay disp;

        /** Test (commande) : la mise en scène se joue hors de la Faille, à l'emplacement donné. */
        static boolean test = false;

        static void start(ServerLevel rd, double[] titan) {
            start(rd, titan, null);
        }

        static void start(ServerLevel rd, double[] titan, BlockPos forced) {
            cancel();
            test = forced != null;
            l = rd;
            t0 = rd.getServer().getTickCount();
            tx = titan[0];
            ty = titan[1];
            tz = titan[2];
            th = Math.max(10, titan[3]);
            at = forced != null ? forced : spot(rd);
        }

        /** Emplacement du coffre : près du Monolithe (centre de l'île), sur un sol encore debout. */
        static BlockPos spot(ServerLevel rd) {
            int[][] cand = {{4, 0}, {-4, 0}, {4, 4}, {-4, 4}, {0, 5}, {6, 0}, {-6, 0}};
            for (int[] c : cand) {
                BlockPos p = RiftManager.CENTER.offset(c[0], 1, c[1]);
                if (!rd.getBlockState(p.below()).isAir()) return p;
            }
            return RiftManager.CENTER.offset(4, 1, 0);
        }

        static void cancel() {
            if (disp != null && !disp.isRemoved()) disp.discard();
            disp = null;
            t0 = -1;
            l = null;
            at = null;
        }

        static void tick(MinecraftServer server, long now) {
            if (t0 < 0 || l == null || at == null) return;
            if (!test && !RiftManager.isActive()) { cancel(); return; }
            long t = now - t0;
            double cx = at.getX() + 0.5, cz = at.getZ() + 0.5, gy = at.getY();
            double s = scale();
            // 1. Le Titan se dissout et tombe dans l'abîme
            if (t < BEAM && t % 2 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = l.random.nextDouble() * Math.PI * 2, r = l.random.nextDouble() * 5, h = l.random.nextDouble() * th;
                    double x = tx + Math.cos(a) * r, y = ty + h - t * 0.6, z = tz + Math.sin(a) * r;
                    l.sendParticles(i % 2 == 0 ? VIOLET : TURQUOISE, x, y, z, 3, 0.4, 0.4, 0.4, 0);
                    l.sendParticles(ParticleTypes.PORTAL, x, y, z, 6, 0.6, 0.6, 0.6, 0.4);
                }
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, tx, ty - 10 - t, tz, 20, 4, 6, 4, 0.05);   // ce qui tombe dans l'abîme
                if (t == 0) l.playSound(null, BlockPos.containing(tx, ty, tz), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 6f, 0.4f);
            }
            // 2. La Faille s'ouvre dans le ciel et projette son faisceau (jusqu'à l'atterrissage)
            if (t >= BEAM - 10 && t <= LAND + 10 && t % 2 == 0) {
                double sky = gy + SKY_H;
                for (int i = 0; i < 10; i++) {
                    double a = (t * 0.25) + i * Math.PI / 5;
                    l.sendParticles(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * 3.5, sky, cz + Math.sin(a) * 3.5, 2, 0.2, 0.2, 0.2, 0.02);
                }
                l.sendParticles(VIOLET, cx, sky, cz, 8, 1.5, 0.3, 1.5, 0);
                if (t >= BEAM) {
                    for (double y = gy; y < sky; y += 1.5) {
                        l.sendParticles(TURQUOISE, cx, y, cz, 1, 0.12, 0.3, 0.12, 0);
                        if (((int) y + t) % 6 == 0) l.sendParticles(ParticleTypes.END_ROD, cx, y, cz, 1, 0.05, 0.3, 0.05, 0.01);
                    }
                }
                if (t == BEAM) l.playSound(null, at, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 4f, 0.6f);
            }
            // 3. Descente du coffre, en tournant, dans une spirale de portail
            if (t == DROP) spawnDisplay(s);
            if (t > DROP && t < LAND) {
                double u = (t - DROP) / (double) (LAND - DROP), h = DROP_H * (1 - u) * (1 - u);
                double y = gy + 0.45 * s + h;
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.35 + i * Math.PI * 2 / 3;
                    l.sendParticles(ParticleTypes.PORTAL, cx + Math.cos(a) * 1.4 * s, y - 0.5, cz + Math.sin(a) * 1.4 * s, 3, 0.05, 0.05, 0.05, 0.1);
                }
                if ((t - DROP) % 10 == 0) step((int) ((t - DROP) / 10) + 1, s);
            }
            // 4. Atterrissage : onde de choc, son, secousse, titre et clés
            if (t == LAND) land(s);
            if (t > LAND && t <= LAND + 12 && t % 3 == 0) {
                double r = 1 + (t - LAND) * 0.45;
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12;
                    l.sendParticles(TURQUOISE, cx + Math.cos(a) * r, gy + 0.1, cz + Math.sin(a) * r, 1, 0, 0.05, 0, 0);
                }
            }
            if (t > LAND + 12) t0 = -1;   // fin de la mise en scène
        }

        /** Le coffre (en objet) au sommet du faisceau, deux fois plus grand, toujours éclairé. */
        static void spawnDisplay(double s) {
            RouletteChestConfig cfg = config();
            if (cfg == null) return;
            var d = EntityType.ITEM_DISPLAY.create(l);
            if (d == null) return;
            d.moveTo(at.getX() + 0.5, at.getY() + 0.45 * s, at.getZ() + 0.5, 0f, 0f);
            CompoundTag tag = d.saveWithoutId(new CompoundTag());
            tag.put("item", RouletteChestManager.createChestItem(cfg).save(new CompoundTag()));
            tag.putString("item_display", "none");
            tag.put("transformation", transform(DROP_H, 0f, s));
            CompoundTag br = new CompoundTag();
            br.putInt("sky", 15);
            br.putInt("block", 15);
            tag.put("brightness", br);
            tag.putFloat("view_range", 4f);
            d.load(tag);
            d.addTag(TAG);
            if (l.addFreshEntity(d)) disp = d;
        }

        /** Palier k de la descente (8 paliers de 10 ticks) : plus bas, un quart de tour de plus. */
        static void step(int k, double s) {
            if (disp == null || disp.isRemoved() || k > 8) return;
            double u = Math.min(1, k / 8.0), h = DROP_H * (1 - u) * (1 - u);
            CompoundTag tag = disp.saveWithoutId(new CompoundTag());
            tag.put("transformation", transform(h, k * 90f, s));
            tag.putInt("interpolation_duration", 10);
            tag.putInt("start_interpolation", 0);
            disp.load(tag);
        }

        static CompoundTag transform(double h, float yawDeg, double s) {
            org.joml.Quaternionf q = new org.joml.Quaternionf().rotateY((float) Math.toRadians(yawDeg));
            CompoundTag t = new CompoundTag();
            t.put("translation", floats(0f, (float) h, 0f));
            t.put("left_rotation", floats(q.x, q.y, q.z, q.w));
            t.put("scale", floats((float) s, (float) s, (float) s));
            t.put("right_rotation", floats(0f, 0f, 0f, 1f));
            return t;
        }

        static net.minecraft.nbt.ListTag floats(float... v) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (float f : v) l.add(net.minecraft.nbt.FloatTag.valueOf(f));
            return l;
        }

        static void land(double s) {
            if (disp != null && !disp.isRemoved()) disp.discard();
            disp = null;
            if (!place(l, at, Direction.SOUTH)) return;
            double cx = at.getX() + 0.5, cz = at.getZ() + 0.5;
            l.sendParticles(ParticleTypes.EXPLOSION, cx, at.getY() + 0.3, cz, 3, 0.8, 0.1, 0.8, 0);
            l.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, at.getY() + 0.5, cz, 120, 1.5, 0.3, 1.5, 0.3);
            l.sendParticles(TURQUOISE, cx, at.getY() + 0.3, cz, 60, 2.0, 0.1, 2.0, 0);
            l.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 4f, 0.6f);
            l.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 4f, 0.5f);
            l.playSound(null, at, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.5f, 0.5f);
            List<ServerPlayer> present = new ArrayList<>();
            for (ServerPlayer p : l.players()) {
                if (p.isSpectator()) continue;
                present.add(p);
                com.wavesurvivor.network.ShakePacket.send(p, 20, 0.6f);
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 70, 20));
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                        Component.literal(WSLang.t("titan_chest.victory_title")).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.BOLD)));
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                        Component.literal(WSLang.t("titan_chest.victory_sub")).withStyle(net.minecraft.ChatFormatting.AQUA)));
            }
            giveKeys(present);
        }
    }
}
