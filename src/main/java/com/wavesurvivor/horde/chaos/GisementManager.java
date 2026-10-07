package com.wavesurvivor.horde.chaos;

import com.wavesurvivor.entity.GisementEntity;
import com.wavesurvivor.horde.loot.LootItems;
import com.wavesurvivor.horde.model.ChaosEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GISEMENTS (événement chaos bénéfique) : étincelles pour les repérer, disparition après leur durée,
 * fragment possible à chaque coup de pioche, butin complet à la destruction.
 */
public class GisementManager {

    private static final Random RNG = new Random();

    private record Deposit(UUID id, long expires, List<ChaosEvent.Drop> drops, String hitDropItem, double hitDropChance, String label,
                           double lootMult, List<ChaosEvent.Drop> hitDrops) {}

    private static final Map<UUID, Deposit> DEPOSITS = new ConcurrentHashMap<>();

    public static void register(GisementEntity g, ChaosEvent ev, String label) {
        register(g, ev, label, 1.0);
    }

    /** @param lootMult multiplicateur des quantités du butin (taille du gisement). */
    public static void register(GisementEntity g, ChaosEvent ev, String label, double lootMult) {
        long now = g.level().getServer() != null ? g.level().getServer().getTickCount() : 0;
        int life = ev.lifetime > 0 ? ev.lifetime : 120;
        // Contrepartie du Mineur (Kingdom) : les gisements disparaissent plus vite (-20 % de durée)
        if (com.wavesurvivor.horde.kingdom.KingdomRoles.present(com.wavesurvivor.horde.kingdom.KingdomRoles.Role.MINER)) {
            life = Math.max(20, (int) Math.round(life * 0.8));
        }
        DEPOSITS.put(g.getUUID(), new Deposit(g.getUUID(), now + life * 20L,
                ev.drops != null ? new ArrayList<>(ev.drops) : List.of(), ev.hitDropItem, ev.hitDropChance, label, Math.max(0.1, lootMult),
                ev.hitDrops != null ? new ArrayList<>(ev.hitDrops) : List.of()));
    }

    /** Gisements vivants (effets de rôle : colonne de lumière et flèche du Mineur). */
    public static List<GisementEntity> deposits(MinecraftServer server) {
        List<GisementEntity> out = new ArrayList<>();
        if (server == null) return out;
        for (Deposit d : DEPOSITS.values()) {
            for (ServerLevel lvl : server.getAllLevels()) {
                if (lvl.getEntity(d.id()) instanceof GisementEntity ge && ge.isAlive()) { out.add(ge); break; }
            }
        }
        return out;
    }

    public static void clearAll(MinecraftServer server) {
        if (server != null) {
            for (Deposit d : DEPOSITS.values()) {
                for (ServerLevel lvl : server.getAllLevels()) {
                    Entity e = lvl.getEntity(d.id());
                    if (e != null) e.discard();
                }
            }
        }
        DEPOSITS.clear();
    }

    /** Coup de pioche valide : butin « par coup » (liste) + ancien fragment unique éventuel.
     *  Fortune : chaque niveau ajoute +50 % à la chance de base (I ×1,5 · II ×2 · III ×2,5), plafonné à 100 %. */
    public static void onHit(GisementEntity g, Player p) {
        Deposit d = DEPOSITS.get(g.getUUID());
        if (d == null) return;
        // Partie Kingdom : chaque coup de pioche a 10 % de chance de donner 1 Essence de brèche au trésor
        if (com.wavesurvivor.horde.kingdom.KingdomTreasury.active() && RNG.nextDouble() < 0.10) {
            com.wavesurvivor.horde.kingdom.KingdomTreasury.add(com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.ESSENCE, 1);
            if (g.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.WITCH, g.getX(), g.getY() + 1.0, g.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
            }
        }
        int fortune = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE, p.getMainHandItem());
        double fortuneMult = 1.0 + 0.5 * Math.max(0, fortune);
        for (ChaosEvent.Drop drop : d.hitDrops()) {
            if (drop == null || drop.item == null || drop.item.isBlank()) continue;
            if (RNG.nextDouble() * 100 >= Math.min(100, drop.chance * fortuneMult)) continue;
            int n = drop.minQty + (drop.maxQty > drop.minQty ? RNG.nextInt(drop.maxQty - drop.minQty + 1) : 0);
            if (n > 0) drop(g, LootItems.resolve(drop.item, n));
        }
        if (d.hitDropItem() == null || d.hitDropItem().isBlank()) return;
        if (RNG.nextDouble() * 100 < Math.min(100, d.hitDropChance() * fortuneMult)) drop(g, LootItems.resolve(d.hitDropItem(), 1));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DEPOSITS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % 10 != 0) return;
        for (Deposit d : DEPOSITS.values()) {
            GisementEntity g = null;
            for (ServerLevel lvl : server.getAllLevels()) {
                if (lvl.getEntity(d.id()) instanceof GisementEntity ge) { g = ge; break; }
            }
            if (g == null || !g.isAlive()) { DEPOSITS.remove(d.id()); continue; }
            ServerLevel level = (ServerLevel) g.level();
            if (now >= d.expires()) {
                level.sendParticles(ParticleTypes.POOF, g.getX(), g.getY() + 0.6, g.getZ(), 15, 0.4, 0.3, 0.4, 0.02);
                g.discard();
                DEPOSITS.remove(d.id());
                continue;
            }
            // Repères (visibles de loin) : étincelles pour un minerai, feuilles vertes pour un arbre
            boolean tree = g.isTree();
            if (tree) {
                // Arbre fantastique : spores qui tombent de la couronne, lueurs qui flottent autour
                double r = 2.0 * g.scale();
                double a = RNG.nextDouble() * Math.PI * 2, dd = RNG.nextDouble() * r;
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, g.getX() + Math.cos(a) * dd, g.getY() + (3.0 + RNG.nextDouble() * 2.2) * g.scale(),
                        g.getZ() + Math.sin(a) * dd, 1, 0, 0, 0, 0);
                if (now % 6 == 0) level.sendParticles(ParticleTypes.GLOW, g.getX() + Math.cos(a) * r * 0.8, g.getY() + (2.6 + RNG.nextDouble() * 2.5) * g.scale(),
                        g.getZ() + Math.sin(a) * r * 0.8, 1, 0.1, 0.1, 0.1, 0.01);
            }
            for (int i = 0; i < 3; i++) {
                level.sendParticles(tree ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.WAX_OFF,
                        g.getX() + (RNG.nextDouble() - 0.5) * 0.6, g.getY() + (tree ? 5.8 * g.scale() : 1.2) + RNG.nextDouble() * 3,
                        g.getZ() + (RNG.nextDouble() - 0.5) * 0.6, 1, 0, 0.05, 0, 0.01);
            }
            if (now % 40 == 0) level.sendParticles(ParticleTypes.END_ROD, g.getX(), g.getY() + (tree ? 6.2 * g.scale() : 1.5), g.getZ(), 2, 0.2, 1.2, 0.2, 0.02);
            g.setCustomName(Component.literal((tree ? "§a🪓 " : "§e⛏ ") + d.label() + " §7" + Math.round(g.getHealth()) + "/" + Math.round(g.getMaxHealth())
                    + " §8(" + Math.max(0, (d.expires() - now) / 20) + " s)"));
        }
    }

    /** Détruit : butin complet + message. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        Deposit d = DEPOSITS.remove(event.getEntity().getUUID());
        if (d == null || !(event.getEntity() instanceof GisementEntity g)) return;
        // Anneau du Prospecteur : +50 % de butin pour celui qui détruit le gisement
        double mult = d.lootMult();
        if (event.getSource().getEntity() instanceof Player killer) {
            mult *= 1.0 + com.wavesurvivor.item.RelicEffects.scaled(killer, com.wavesurvivor.registry.ModItems.ANNEAU_PROSPECTEUR.get(), 0.5f); // +50 / 75 / 100 %
        }
        // Mineur (Kingdom) : 25 % de chance de doubler le butin du gisement qu'il détruit
        if (event.getSource().getEntity() instanceof ServerPlayer minerP
                && com.wavesurvivor.horde.kingdom.KingdomRoles.has(minerP, com.wavesurvivor.horde.kingdom.KingdomRoles.Role.MINER)
                && RNG.nextDouble() < 0.25) {
            mult *= 2;
            minerP.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("kingdom.role.miner.double"), true);
        }
        for (ChaosEvent.Drop drop : d.drops()) {
            if (drop == null || drop.item == null || RNG.nextDouble() * 100 >= drop.chance) continue;
            int n = drop.minQty + (drop.maxQty > drop.minQty ? RNG.nextInt(drop.maxQty - drop.minQty + 1) : 0);
            n = (int) Math.round(n * mult);
            if (n > 0) drop(g, LootItems.resolve(drop.item, n));
        }
        // Partie Kingdom : trésor commun selon la taille — minerai : Pierre 8 + Fer 2 ; arbre : Bois 16
        if (com.wavesurvivor.horde.kingdom.KingdomTreasury.active()) {
            if (g.isTree()) {
                com.wavesurvivor.horde.kingdom.KingdomTreasury.add(com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.WOOD,
                        (int) Math.max(1, Math.round(16 * mult)));
            } else {
                com.wavesurvivor.horde.kingdom.KingdomTreasury.add(com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.STONE,
                        (int) Math.max(1, Math.round(8 * mult)));
                com.wavesurvivor.horde.kingdom.KingdomTreasury.add(com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.IRON,
                        (int) Math.max(1, Math.round(2 * mult)));
            }
        }
        if (g.level() instanceof ServerLevel level) {
            level.playSound(null, g.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
            String who = event.getSource().getEntity() instanceof ServerPlayer sp ? sp.getGameProfile().getName()
                    : com.wavesurvivor.i18n.WSLang.t("common.team");
            Component c = com.wavesurvivor.i18n.WSLang.c(g.isTree() ? "arbre.cut" : "gisement.mined", d.label(), who);
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) p.sendSystemMessage(c);
        }
    }

    private static void drop(LivingEntity g, ItemStack st) {
        if (st.isEmpty() || !(g.level() instanceof ServerLevel level)) return;
        ItemEntity it = new ItemEntity(level, g.getX(), g.getY() + 0.8, g.getZ(), st);
        it.setDeltaMovement((RNG.nextDouble() - 0.5) * 0.2, 0.25, (RNG.nextDouble() - 0.5) * 0.2);
        it.setDefaultPickUpDelay();
        level.addFreshEntity(it);
    }
}
