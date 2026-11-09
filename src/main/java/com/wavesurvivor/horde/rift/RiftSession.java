package com.wavesurvivor.horde.rift;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 2.0 — SAUVEGARDE DE LA FAILLE : points de reprise enregistrés avec le monde (toutes les 30 s et à la fermeture du serveur).
 * <ul>
 *   <li>Horde : reprise à la vague atteinte ;</li>
 *   <li>Siège : reprise au dernier calme (assaut atteint, trésor, rôles, défenses) ;</li>
 *   <li>Contre-offensive : reprise à son début (elle recommence avec tous ses catalyseurs) ;</li>
 *   <li>Titan : pas de point de reprise (on reprend au début de la Contre-offensive).</li>
 * </ul>
 * Effacée à la fin de la Faille (victoire, défaite, arrêt). Au retour, un message propose « Reprendre » ou « Abandonner ».
 */
public class RiftSession extends SavedData {

    private static final String NAME = "wavesurvivor_rift_session";
    /** Vrai pendant l'arrêt de fermeture du serveur : la sauvegarde ne doit pas être effacée. */
    private static boolean keep = false;

    /** 0 = aucune ; 1 Horde, 2 Siège, 3 Contre-offensive. */
    int phase;
    String horde = "", dim = "", difficulty = "", mutators = "", fortress = "";
    int wave;
    BlockPos altar;
    float hp = -1, maxHp = -1;
    int[] levels = new int[3];
    long elapsed;
    final Map<UUID, Integer> kills = new HashMap<>();
    CompoundTag kingdom = new CompoundTag();
    /** Engins de siège posés (type, niveau, variante, position). */
    net.minecraft.nbt.ListTag engines = new net.minecraft.nbt.ListTag();

    public RiftSession() {}

    public static RiftSession get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RiftSession::load, RiftSession::new, NAME);
    }

    public boolean present() { return phase > 0 && !horde.isEmpty(); }

    static void setKeep(boolean k) { keep = k; }

    /** Copie détachée (la reprise remet tout à zéro, y compris la sauvegarde). */
    RiftSession copy() {
        RiftSession c = new RiftSession();
        c.phase = phase;
        c.horde = horde;
        c.dim = dim;
        c.difficulty = difficulty;
        c.mutators = mutators;
        c.fortress = fortress;
        c.wave = wave;
        c.altar = altar;
        c.hp = hp;
        c.maxHp = maxHp;
        c.levels = levels.clone();
        c.elapsed = elapsed;
        c.kills.putAll(kills);
        c.kingdom = kingdom.copy();
        c.engines = engines.copy();
        return c;
    }

    /** Fin de Faille (victoire, défaite, arrêt, nouvelle Faille) : sauvegarde effacée, sauf pendant la fermeture. */
    static void discard(MinecraftServer server) {
        if (keep || server == null) return;
        RiftSession s = get(server);
        if (!s.present()) return;
        s.phase = 0;
        s.horde = "";
        s.altar = null;
        s.kills.clear();
        s.kingdom = new CompoundTag();
        s.engines = new net.minecraft.nbt.ListTag();
        s.setDirty();
    }

    private String phaseText() {
        return WSLang.t("rift.session.phase." + Math.max(1, Math.min(3, phase)), wave);
    }

    /** Connexion : une Faille interrompue attend → message avec boutons « Reprendre » / « Abandonner ». */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null || RiftManager.isActive()) return;
        RiftSession s = get(p.getServer());
        if (!s.present()) return;
        MutableComponent resume = Component.literal(WSLang.t("session.btn_resume")).withStyle(st -> st.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wavesurvivor riftresume"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(s.phaseText()))));
        MutableComponent abandon = Component.literal(WSLang.t("session.btn_abandon")).withStyle(st -> st.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wavesurvivor riftabandon"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(WSLang.t("session.hover_abandon")))));
        p.sendSystemMessage(Component.literal(WSLang.t("rift.session.pending", WSLang.t(s.horde), s.phaseText())));
        p.sendSystemMessage(Component.literal("   ").append(resume).append(Component.literal("   ")).append(abandon));
    }

    // ─── Sauvegarde NBT ───

    private static RiftSession load(CompoundTag t) {
        RiftSession s = new RiftSession();
        s.phase = t.getInt("phase");
        s.horde = t.getString("horde");
        s.dim = t.getString("dim");
        s.difficulty = t.getString("difficulty");
        s.mutators = t.getString("mutators");
        s.fortress = t.getString("fortress");
        s.wave = t.getInt("wave");
        if (t.contains("altar")) s.altar = BlockPos.of(t.getLong("altar"));
        s.hp = t.contains("hp") ? t.getFloat("hp") : -1;
        s.maxHp = t.contains("maxHp") ? t.getFloat("maxHp") : -1;
        int[] lv = t.getIntArray("levels");
        for (int i = 0; i < Math.min(3, lv.length); i++) s.levels[i] = lv[i];
        s.elapsed = t.getLong("elapsed");
        CompoundTag k = t.getCompound("kills");
        for (String id : k.getAllKeys()) {
            try { s.kills.put(UUID.fromString(id), k.getInt(id)); } catch (IllegalArgumentException ignored) {}
        }
        s.kingdom = t.getCompound("kingdom");
        s.engines = t.getList("engines", net.minecraft.nbt.Tag.TAG_COMPOUND);
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.putInt("phase", phase);
        t.putString("horde", horde);
        t.putString("dim", dim);
        t.putString("difficulty", difficulty);
        t.putString("mutators", mutators);
        t.putString("fortress", fortress);
        t.putInt("wave", wave);
        if (altar != null) t.putLong("altar", altar.asLong());
        t.putFloat("hp", hp);
        t.putFloat("maxHp", maxHp);
        t.putIntArray("levels", levels);
        t.putLong("elapsed", elapsed);
        CompoundTag k = new CompoundTag();
        for (Map.Entry<UUID, Integer> e : kills.entrySet()) k.putInt(e.getKey().toString(), e.getValue());
        t.put("kills", k);
        if (kingdom != null && !kingdom.isEmpty()) t.put("kingdom", kingdom);
        t.put("engines", engines);
        return t;
    }
}
