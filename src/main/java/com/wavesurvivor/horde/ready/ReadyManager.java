package com.wavesurvivor.horde.ready;

import com.wavesurvivor.config.model.HordeConfigMultiData.ReadySettings;
import com.wavesurvivor.horde.renaissance.RenaissanceStore;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BOUTON « PRÊT » (1.9) : pendant une pause entre deux vagues, chaque joueur peut se déclarer prêt (touche « Prêt »). Quand tous les joueurs de la
 * partie le sont, la pause est écourtée et chacun reçoit le bonus d'enchaînement de la horde (proportionnel au temps restant si réglé).
 * Le moteur est commun : la horde classique (aussi Boss Rush et sans fin) et l'invasion lui confient leur pause avec {@link #begin}.
 */
public final class ReadyManager {

    /** Une pause confiée au moteur : joueurs concernés et moyen d'y mettre fin tout de suite. */
    public interface Pause {
        List<ServerPlayer> players();
        void skip();
    }

    private static final class Entry {
        Object key;
        Pause pause;
        ReadySettings s;
        long start, end;
        final Set<UUID> ready = new HashSet<>();
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    /** Effets proposables comme bonus. */
    public static final List<String> EFFECTS = List.of("strength", "speed", "resistance", "regeneration", "haste");

    private ReadyManager() {}

    /** Début d'une pause (ticks du serveur). Sans effet si « Prêt » est interdit pour cette horde ou si la pause est trop courte. */
    public static synchronized void begin(Object key, Pause pause, ReadySettings s, long start, long end) {
        end(key);
        if (s == null || !s.allow || end - start < 100) return;
        Entry e = new Entry();
        e.key = key;
        e.pause = pause;
        e.s = s;
        e.start = start;
        e.end = end;
        ENTRIES.add(e);
        Component msg = Component.literal(WSLang.t("ready.prompt", bonusText(s)));
        for (ServerPlayer p : pause.players()) p.displayClientMessage(msg, true);
    }

    /** Fin de la pause (la vague démarre, la partie s'arrête…) : on oublie les « prêts ». */
    public static synchronized void end(Object key) {
        ENTRIES.removeIf(e -> e.key == key);
    }

    public static synchronized void clearAll() { ENTRIES.clear(); }

    /** Suffixe pour la barre de la horde : « · Prêts 1/3 » (vide si aucune pause « Prêt » n'est en cours). */
    public static synchronized String hudSuffix(Object key) {
        for (Entry e : ENTRIES) {
            if (e.key != key) continue;
            List<ServerPlayer> ps = e.pause.players();
            int n = 0;
            for (ServerPlayer p : ps) if (e.ready.contains(p.getUUID())) n++;
            return WSLang.t("ready.hud", n, ps.size());
        }
        return "";
    }

    /** Le joueur appuie sur la touche « Prêt » : il passe de « pas prêt » à « prêt » (et inversement). */
    public static synchronized void toggle(ServerPlayer p, long now) {
        for (Entry e : ENTRIES) {
            List<ServerPlayer> ps = e.pause.players();
            if (!ps.contains(p)) continue;
            if (now >= e.end) return;
            if (!e.ready.remove(p.getUUID())) e.ready.add(p.getUUID());
            int n = 0;
            for (ServerPlayer q : ps) if (e.ready.contains(q.getUUID())) n++;
            Component msg = Component.literal(WSLang.t("ready.count", n, ps.size()));
            for (ServerPlayer q : ps) q.displayClientMessage(msg, true);
            if (n >= ps.size()) complete(e, ps, now);
            return;
        }
    }

    private static void complete(Entry e, List<ServerPlayer> ps, long now) {
        double scale = e.s.proportional ? Math.max(0.0, Math.min(1.0, (e.end - now) / (double) Math.max(1, e.end - e.start))) : 1.0;
        ENTRIES.remove(e);
        for (ServerPlayer p : ps) {
            pay(p, e.s, scale);
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.5f);
        }
        Component msg = Component.literal(WSLang.t("ready.go", bonusText(e.s, scale)));
        for (ServerPlayer p : ps) p.displayClientMessage(msg, true);
        e.pause.skip();
    }

    private static int scaled(int amount, double scale) { return (int) Math.round(amount * scale); }

    private static void pay(ServerPlayer p, ReadySettings s, double scale) {
        int n = scaled(Math.max(0, s.amount), scale);
        if (n <= 0) return;
        switch (s.bonusType == null ? "coins" : s.bonusType) {
            case "coins" -> {
                ItemStack st = new ItemStack(Items.EMERALD, n);
                if (!p.getInventory().add(st)) p.drop(st, false);
            }
            case "pr" -> RenaissanceStore.addPoints(p, n);
            default -> {
                MobEffect fx = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("minecraft", effectId(s.bonusType)));
                if (fx != null) p.addEffect(new MobEffectInstance(fx, n * 20, Math.max(0, s.level - 1)));
            }
        }
    }

    /** Identifiant vanilla d'un effet proposé (« haste » → minecraft:haste ; « speed » → minecraft:speed). */
    private static String effectId(String type) { return type; }

    public static String bonusText(ReadySettings s) { return bonusText(s, 1.0); }

    public static String bonusText(ReadySettings s, double scale) {
        int n = scaled(Math.max(0, s.amount), scale);
        String t = s.bonusType == null ? "coins" : s.bonusType;
        if (n <= 0) return WSLang.t("ready.bonus.none");
        return switch (t) {
            case "coins" -> WSLang.t("ready.bonus.coins", n);
            case "pr" -> WSLang.t("ready.bonus.pr", n);
            default -> WSLang.t("ready.bonus.effect", WSLang.t("ready.fx." + t), s.level, n);
        };
    }
}
