package com.wavesurvivor.horde.renaissance;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 2.0 — HÉRITAGE DE LA FAILLE (onglet « Faille » de l'autel de Renaissance). Progression à part, conservée à la Renaissance :
 *   • Panthéon : Titans vaincus, meilleure difficulté battue (titre + aura : Brise-Titan / Briseur de Colosses / Fléau de l'Abîme), podiums ;
 *   • 5 nœuds payés en Marques du Titan (1 par victoire, +1 en Cauchemar, +1 pour le 1er du podium) :
 *     1 Cœur d'acier (engins +20 % PV et dégâts), 2 Œil du stratège (fenêtres du Titan +2 s), 3 Prospecteur de faille (+1 Éclat de Faille),
 *     4 Survivant de l'abîme (réapparition −5 s dans la Faille + un coup fatal évité par Faille),
 *     5 Vétéran des colosses (+10 % de dégâts contre les boss et le Titan, +10 % de PR dans tous les modes).
 * Les données vivent dans le même NBT persistant que l'Héritage (clés « rift_* », jamais effacées par la Renaissance).
 */
public final class RiftLegacy {

    public static final int NODES = 5;
    public static final int[] NODE_COST = {1, 1, 2, 2, 3};
    /** Client : {Titans vaincus, meilleure difficulté (0 à 3), podiums, Marques disponibles, nœuds achetés}. */
    public static int[] CLIENT = new int[5];

    /** Joueurs qui ont déjà évité un coup fatal pendant la Faille en cours (nœud 4). */
    private static final Set<UUID> LAST_STAND = new HashSet<>();

    private static final DustParticleOptions CRACK = new DustParticleOptions(new Vector3f(0.62f, 0.2f, 0.95f), 0.9f);
    private static final DustParticleOptions SHARD = new DustParticleOptions(new Vector3f(0.12f, 0.04f, 0.22f), 1.3f);

    // ─── Données ───

    public static int titans(Player p) { return Heritage.data(p).getInt("rift_titans"); }
    public static int best(Player p) { return Heritage.data(p).getInt("rift_best"); }
    public static int podiums(Player p) { return Heritage.data(p).getInt("rift_podiums"); }
    public static int marks(Player p) { return Heritage.data(p).getInt("rift_marks"); }
    public static int node(Player p) { return Math.min(NODES, Heritage.data(p).getInt("rift_node")); }

    public static boolean has(Player p, int n) { return p != null && node(p) >= n; }

    /** Un joueur connecté (hors spectateur) a-t-il ce nœud ? (effets d'équipe : nœuds 1 et 2) */
    public static boolean anyone(MinecraftServer s, int n) {
        if (s == null) return false;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (!p.isSpectator() && has(p, n)) return true;
        return false;
    }

    public static int[] state(Player p) {
        return new int[]{titans(p), best(p), podiums(p), marks(p), node(p)};
    }

    public static String titleKey(int best) { return "rift.legacy.title." + Math.max(1, Math.min(3, best)); }

    private static String color(int best) { return best >= 3 ? "§c" : best == 2 ? "§d" : "§5"; }

    /** Titre de la Faille devant le pseudo (vide si aucun Titan vaincu). */
    public static String titlePrefix(Player p) {
        int b = best(p);
        return b <= 0 ? "" : color(b) + "[" + WSLang.t(titleKey(b)) + "] §r";
    }

    // ─── Victoire et achat ───

    /**
     * Titan vaincu : le Panthéon avance, le joueur gagne des Marques du Titan. {@code podium} = 1 à 3 (0 = hors podium).
     * @return Marques gagnées
     */
    public static int onVictory(ServerPlayer p, String difficultyId, int podium) {
        CompoundTag d = Heritage.data(p);
        d.putInt("rift_titans", d.getInt("rift_titans") + 1);
        int lvl = "nightmare".equals(difficultyId) ? 3 : "hard".equals(difficultyId) ? 2 : 1;
        boolean newTitle = lvl > d.getInt("rift_best");
        if (newTitle) d.putInt("rift_best", lvl);
        if (podium >= 1 && podium <= 3) d.putInt("rift_podiums", d.getInt("rift_podiums") + 1);
        int marks = 1 + (lvl == 3 ? 1 : 0) + (podium == 1 ? 1 : 0);
        d.putInt("rift_marks", d.getInt("rift_marks") + marks);
        p.sendSystemMessage(Component.literal(WSLang.t("rift.legacy.marks", marks, d.getInt("rift_marks"))));
        if (newTitle) {
            Component msg = Component.literal(WSLang.t("rift.legacy.title_new", p.getGameProfile().getName(), color(lvl) + WSLang.t(titleKey(lvl))));
            for (ServerPlayer q : p.server.getPlayerList().getPlayers()) q.sendSystemMessage(msg);
            p.refreshDisplayName();
            p.refreshTabListName();
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 0.8f);
        }
        Heritage.sync(p);
        WaveSurvivorMod.LOGGER.info("[Faille] Panthéon : {} +{} Marque(s), meilleur titre {}", p.getGameProfile().getName(), marks, d.getInt("rift_best"));
        return marks;
    }

    /** Achat du nœud suivant (payé en Marques du Titan). */
    public static void buy(ServerPlayer p) {
        CompoundTag d = Heritage.data(p);
        int n = node(p);
        if (n >= NODES) return;
        int cost = NODE_COST[n];
        if (d.getInt("rift_marks") < cost) {
            p.sendSystemMessage(Component.literal(WSLang.t("rift.legacy.not_enough", cost, d.getInt("rift_marks"))));
            return;
        }
        d.putInt("rift_marks", d.getInt("rift_marks") - cost);
        d.putInt("rift_node", n + 1);
        p.sendSystemMessage(Component.literal(WSLang.t("rift.legacy.bought", WSLang.t("rift.legacy.node." + (n + 1)))));
        p.level().playSound(null, p.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1f, 0.8f);
        if (p.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 0.8, 0.6, 0.3);
        Heritage.sync(p);
    }

    /** Fin de la Faille : chacun pourra de nouveau éviter un coup fatal à la prochaine. */
    public static void resetRun() {
        LAST_STAND.clear();
    }

    // ─── Événements ───

    /** Nœud 4 : une fois par Faille, un coup fatal laisse à 1 PV (dans la dimension de la Faille). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLethal(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.isCreative() || e.getAmount() < p.getHealth()) return;
        if (!com.wavesurvivor.horde.rift.RiftManager.isActive() || p.level().dimension() != com.wavesurvivor.horde.rift.RiftManager.RIFT_DIM) return;
        if (!has(p, 4) || !LAST_STAND.add(p.getUUID())) return;
        e.setAmount(Math.max(0f, p.getHealth() - 1f));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, true));
        p.displayClientMessage(Component.literal(WSLang.t("rift.legacy.last_stand")), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** Nœud 5 : +10 % de dégâts contre les boss (tous les modes) et le Titan. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !has(p, 5)) return;
        var v = e.getEntity();
        if (com.wavesurvivor.horde.boss.BossManager.isBoss(v.getUUID()) || v.getPersistentData().getBoolean("ws_rift_titan")) {
            e.setAmount(e.getAmount() * 1.10f);
        }
    }

    /** Aura du titre : fissures violettes (Brise-Titan), éclats d'obsidienne en orbite (Briseur de Colosses), faille animée (Fléau de l'Abîme). */
    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 5) return;
        int b = best(p);
        if (b <= 0 || p.isSpectator() || p.isInvisible() || !(p.level() instanceof ServerLevel sl)) return;
        for (int i = 0; i < 4; i++) {
            double a = p.getRandom().nextDouble() * Math.PI * 2, r = 0.4 + p.getRandom().nextDouble() * 0.4;
            sl.sendParticles(CRACK, p.getX() + Math.cos(a) * r, p.getY() + 0.05, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (b >= 2) {
            for (int k = 0; k < 3; k++) {
                double a = p.tickCount * 0.12 + k * Math.PI * 2 / 3;
                sl.sendParticles(SHARD, p.getX() + Math.cos(a) * 0.9, p.getY() + 1.1, p.getZ() + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
            }
        }
        if (b >= 3) {
            for (int k = 0; k < 6; k++) {
                double a = -p.tickCount * 0.08 + k * Math.PI * 2 / 6;
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + Math.cos(a) * 0.7, p.getY() + 0.15, p.getZ() + Math.sin(a) * 0.7, 1, 0, 0.05, 0, 0.01);
            }
        }
    }
}
