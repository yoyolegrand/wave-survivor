package com.wavesurvivor.horde;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
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
 * SAUVEGARDE DE PARTIE (hordes classiques) : enregistrée avec le monde à la fermeture et toutes les 30 s.
 * La horde est quand même arrêtée proprement à la fermeture (terrain restauré, monstres retirés) ; au retour, un message
 * propose « Reprendre » (la horde repart à l'autel, au début de la vague qui était en cours, avec la même difficulté,
 * les mêmes mutateurs, le Monolithe aux mêmes PV et niveaux, les mêmes compteurs) ou « Abandonner ».
 * Effacée quand la horde se termine (victoire, défaite, /ws stop) ou qu'une autre horde est lancée.
 */
public class HordeSession extends SavedData {

    private static final String NAME = "wavesurvivor_session";
    /** Vrai pendant l'arrêt de fermeture du serveur : la sauvegarde ne doit pas être effacée. */
    private static boolean keep = false;

    String horde = "", dim = "", difficulty = "", mutators = "";
    int wave;
    BlockPos altar;
    float hp = -1, maxHp = -1;
    int[] levels = new int[3];
    long elapsed;
    final Map<UUID, Integer> kills = new HashMap<>();
    /** Mode Kingdom : état du royaume au dernier Calme (vide pour une horde classique). */
    CompoundTag kingdom = new CompoundTag();
    /** Reprise automatique : tick serveur où la question a été posée (0 = pas de question en attente). */
    private static long askedAt = 0;

    public HordeSession() {}

    public static HordeSession get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HordeSession::load, HordeSession::new, NAME);
    }

    public boolean present() { return !horde.isEmpty(); }

    /** Horde lancée depuis un autel (sinon : par commande, reprise au point de spawn habituel de la horde). */
    public boolean fromAltar() { return altar != null && !dim.isEmpty(); }

    // ─── Enregistrement ───

    /** Photographie la horde en cours (Kingdom : seulement pendant un Calme — sinon le dernier Calme reste le point de reprise). */
    public static void capture(MinecraftServer server) {
        HordeManager hm = HordeManager.get();
        if (server == null || !hm.isRunning() || hm.getActiveHorde() == null || hm.getActiveHorde().configData == null) return;
        if (com.wavesurvivor.horde.bossrush.BossRush.active()) return; // Boss Rush : pas de sauvegarde / reprise
        HordeConfigMultiData h = hm.getActiveHorde();
        boolean isKingdom = h.configData.isKingdom();
        if (isKingdom && !com.wavesurvivor.horde.kingdom.KingdomManager.isCalmPhase()) return;
        BlockPos pos = com.wavesurvivor.altar.AltarDefense.sessionPos();
        String d = com.wavesurvivor.altar.AltarDefense.sessionDim();
        HordeSession s = get(server);
        s.horde = h.hordeName;
        // Autel : reprise à l'autel ; sans autel (lancée par commande) : reprise au point de spawn habituel
        s.dim = d != null ? d : "";
        s.altar = pos;
        // Vague à rejouer : celle en cours, ou la suivante si l'on était entre deux vagues
        int w = hm.getCurrentWave();
        if (hm.getState() != HordeManager.State.RUNNING) w++;
        s.wave = Math.max(1, Math.min(w, Math.max(1, h.configData.totalWaves)));
        s.kingdom = new CompoundTag();
        if (isKingdom) {
            s.kingdom = com.wavesurvivor.horde.kingdom.KingdomManager.saveState();
            s.wave = Math.max(1, s.kingdom.getInt("cycle") + 1); // prochain Assaut
        }
        var diff = com.wavesurvivor.horde.difficulty.HordeDifficulty.active();
        s.difficulty = diff == null ? "" : diff.id;
        s.mutators = com.wavesurvivor.horde.mutator.Mutator.join(com.wavesurvivor.horde.mutator.HordeMutators.active());
        float[] snap = com.wavesurvivor.altar.AltarDefense.snapshot();
        if (snap != null) {
            s.hp = snap[0];
            s.maxHp = snap[1];
            for (int i = 0; i < 3; i++) s.levels[i] = (int) snap[2 + i];
        }
        s.elapsed = hm.getStartedAtTick() > 0 ? Math.max(0, server.getTickCount() - hm.getStartedAtTick()) : 0;
        s.kills.clear();
        s.kills.putAll(com.wavesurvivor.horde.rewards.KillTracker.snapshotKills());
        s.setDirty();
    }

    /** Fin de horde (victoire, défaite, arrêt, nouvelle horde) : sauvegarde effacée, sauf pendant la fermeture. */
    public static void discard(MinecraftServer server) {
        if (keep || server == null) return;
        HordeSession s = get(server);
        if (!s.present()) return;
        s.horde = "";
        s.altar = null;
        s.kills.clear();
        s.kingdom = new CompoundTag();
        askedAt = 0;
        s.setDirty();
    }

    /** Fermeture du serveur : sauvegarde, puis arrêt propre de la horde sans effacer la sauvegarde. */
    public static void saveAndStop(MinecraftServer server) {
        capture(server);
        keep = true;
        try {
            HordeManager.get().stopSilently();
        } finally {
            keep = false;
        }
    }

    // ─── Reprise / abandon ───

    public static int resume(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        HordeSession s = get(server);
        if (!s.present()) { src.sendFailure(Component.literal(WSLang.t("session.none"))); return 0; }
        if (HordeManager.get().isRunning()) { src.sendFailure(Component.literal(WSLang.t("session.running"))); return 0; }
        HordeConfigMultiData h = WaveSurvivorMod.getConfig().findHorde(s.horde);
        if (h == null) { src.sendFailure(Component.literal(WSLang.t("session.missing", s.horde))); return 0; }
        // Copie locale : le démarrage remet tout à zéro (et efface la sauvegarde)
        int wave = s.wave;
        long elapsed = s.elapsed;
        float hp = s.hp, maxHp = s.maxHp;
        int[] lv = s.levels.clone();
        Map<UUID, Integer> kills = new HashMap<>(s.kills);
        CompoundTag kingdomState = s.kingdom.copy();
        askedAt = 0;
        BlockPos altar = s.altar;
        String dim = s.dim;
        boolean fromAltar = s.fromAltar();
        com.wavesurvivor.horde.difficulty.HordeDifficulty.setPending(com.wavesurvivor.horde.difficulty.Difficulty.byId(s.difficulty));
        com.wavesurvivor.horde.mutator.HordeMutators.setPending(com.wavesurvivor.horde.mutator.Mutator.parse(s.mutators));
        boolean ok = fromAltar ? HordeManager.get().startAt(h, server, altar, dim) : HordeManager.get().start(h, server);
        if (!ok) { src.sendFailure(Component.literal(WSLang.t("session.failed"))); return 0; }
        if (!fromAltar) {
            // start() seul n'active ni la difficulté ni les mutateurs (c'est startAt qui le fait pour un autel)
            com.wavesurvivor.horde.difficulty.HordeDifficulty.begin(server);
            com.wavesurvivor.horde.mutator.HordeMutators.begin(server, false);
        }
        if (kingdomState.isEmpty()) HordeManager.get().resumeAt(wave, elapsed);
        if (fromAltar && maxHp > 0) com.wavesurvivor.altar.AltarDefense.restore(hp, maxHp, lv);
        // Kingdom : trésor, Mairie, Portes, boss, rôles, défenses ; reprise au début du Calme sauvegardé
        if (!kingdomState.isEmpty()) com.wavesurvivor.horde.kingdom.KingdomManager.restoreState(kingdomState, server);
        com.wavesurvivor.horde.rewards.KillTracker.restore(kills);
        capture(server); // nouvelle sauvegarde immédiate (plantage dans les premières secondes)
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(WSLang.t("session.resumed", WSLang.t(h.hordeName), wave)));
        }
        WaveSurvivorMod.LOGGER.info("[Session] Horde « {} » reprise à la vague {}.", h.hordeName, wave);
        return 1;
    }

    public static int abandon(CommandSourceStack src) {
        HordeSession s = get(src.getServer());
        if (!s.present()) { src.sendFailure(Component.literal(WSLang.t("session.none"))); return 0; }
        String name = s.horde;
        discard(src.getServer());
        src.sendSuccess(() -> Component.literal(WSLang.t("session.abandoned", WSLang.t(name))), true);
        return 1;
    }

    /** Connexion : une partie interrompue attend → message avec boutons « Reprendre » / « Abandonner ». */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null || HordeManager.get().isRunning()) return;
        HordeSession s = get(p.getServer());
        if (!s.present()) return;
        MutableComponent resume = Component.literal(WSLang.t("session.btn_resume")).withStyle(st -> st.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wavesurvivor resume"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(WSLang.t("session.hover_resume", s.wave)))));
        MutableComponent abandon = Component.literal(WSLang.t("session.btn_abandon")).withStyle(st -> st.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wavesurvivor abandon"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(WSLang.t("session.hover_abandon")))));
        p.sendSystemMessage(Component.literal(WSLang.t("session.pending", WSLang.t(s.horde), s.wave)));
        p.sendSystemMessage(Component.literal("   ").append(resume).append(Component.literal("   ")).append(abandon));
        if (askedAt == 0) {
            askedAt = p.getServer().getTickCount();
            p.sendSystemMessage(Component.literal(WSLang.t("session.auto", 60)));
        }
    }

    /** Sans réponse 60 s après la question, la partie reprend toute seule. */
    @SubscribeEvent
    public void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || askedAt == 0) return;
        MinecraftServer server = e.getServer();
        if (server.getTickCount() - askedAt < 1200) return;
        askedAt = 0;
        if (HordeManager.get().isRunning() || server.getPlayerList().getPlayerCount() == 0 || !get(server).present()) return;
        resume(server.createCommandSourceStack().withSuppressedOutput());
    }

    // ─── Sauvegarde NBT ───

    private static HordeSession load(CompoundTag t) {
        HordeSession s = new HordeSession();
        s.horde = t.getString("horde");
        s.dim = t.getString("dim");
        s.difficulty = t.getString("difficulty");
        s.mutators = t.getString("mutators");
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
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.putString("horde", horde);
        t.putString("dim", dim);
        t.putString("difficulty", difficulty);
        t.putString("mutators", mutators);
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
        return t;
    }
}
