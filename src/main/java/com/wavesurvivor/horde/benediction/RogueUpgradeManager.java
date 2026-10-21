package com.wavesurvivor.horde.benediction;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.OpenBenedictionScreenPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Gestionnaire central des bénédictions rogue-like.
 *
 * Persistance : dans player.getPersistentData() sous la clé NBT_KEY (CompoundTag mapping id -> level).
 * L'anti-farm est stocké dans le NBT joueur sous NBT_LAST_WAVE_KEY (un choix par vague).
 *
 * Cycle de vie :
 *   - Fin de vague N   -> openMenu(player) pour chaque joueur en ligne (envoie packet -> GUI client)
 *   - Joueur choisit   -> addUpgrade(player, id) -> +1 level -> reapplyAll (multiplié par level)
 *   - Joueur respawn   -> reapplyAll (si horde en cours), sinon clearAll
 *   - Joueur login     -> reapplyAll (si horde en cours)
 *   - Fin de horde     -> clearAllPlayers (dissipe tout)
 */
public class RogueUpgradeManager {

    private static final String NBT_KEY = "wave_survivor_benedictions";
    private static final String NBT_LAST_WAVE_KEY = "wave_survivor_last_wave";
    private static final Random RNG = new Random();
    private static final int MENU_CHOICES = 3;

    // ================================================================
    // Persistance NBT (level par upgrade)
    // ================================================================

    /**
     * Données des bénédictions : dans la partie « PlayerPersisted » (conservée à la mort), pour qu'un joueur mort puis
     * réapparu (multi, Kingdom) garde ses bénédictions jusqu'à la fin de la horde.
     */
    private static CompoundTag store(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        CompoundTag persisted = root.getCompound("PlayerPersisted");
        root.put("PlayerPersisted", persisted);
        // Migration : anciennes données à la racine (partie en cours lors de la mise à jour)
        if (root.contains(NBT_KEY)) {
            persisted.put(NBT_KEY, root.getCompound(NBT_KEY));
            root.remove(NBT_KEY);
        }
        if (root.contains(NBT_LAST_WAVE_KEY)) {
            persisted.putInt(NBT_LAST_WAVE_KEY, root.getInt(NBT_LAST_WAVE_KEY));
            root.remove(NBT_LAST_WAVE_KEY);
        }
        return persisted;
    }

    /** Envoie au joueur le résumé de ses bénédictions et de leurs bonus cumulés (/ws benediction mine). */
    public static void sendSummary(ServerPlayer p) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new com.wavesurvivor.network.BlessingSummaryPacket(levels(p), (float) difficultyMult()));
    }

    /** Tous les niveaux de bénédiction du joueur (identifiant → niveau). */
    public static java.util.Map<String, Integer> levels(ServerPlayer p) {
        CompoundTag b = store(p).getCompound(NBT_KEY);
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        for (String k : b.getAllKeys()) if (b.getInt(k) > 0) out.put(k, b.getInt(k));
        return out;
    }

    public static int getLevel(ServerPlayer p, String id) {
        return store(p).getCompound(NBT_KEY).getInt(id);
    }

    public static void setLevel(ServerPlayer p, String id, int level) {
        CompoundTag data = store(p);
        CompoundTag benedictions = data.getCompound(NBT_KEY);
        if (level > 0) benedictions.putInt(id, level);
        else benedictions.remove(id);
        data.put(NBT_KEY, benedictions);
    }

    public static int getLastChosenWave(ServerPlayer p) {
        return store(p).getInt(NBT_LAST_WAVE_KEY);
    }

    public static void setLastChosenWave(ServerPlayer p, int wave) {
        store(p).putInt(NBT_LAST_WAVE_KEY, wave);
    }

    // ================================================================
    // Application des modifiers
    // ================================================================

    /** Ajoute +1 au level de l'upgrade, réapplique tous les modifiers, applique le heal. */
    public static void addUpgrade(ServerPlayer p, String id) {
        RogueUpgrade upg = RogueUpgradeRegistry.get(id);
        if (upg == null) {
            WaveSurvivorMod.LOGGER.warn("[Benediction] addUpgrade '{}' inconnu", id);
            return;
        }
        if (upg.maxLevel() > 0 && getLevel(p, id) >= upg.maxLevel()) return; // plafond atteint
        int newLevel = getLevel(p, id) + 1;
        setLevel(p, id, newLevel);
        reapplyAll(p);

        if (upg.healOnApply() > 0) {
            p.heal((float) (upg.healOnApply() * difficultyMult()));
        }

        WaveSurvivorMod.LOGGER.info("[Benediction] '{}' -> upgrade '{}' lvl {}",
                p.getName().getString(), id, newLevel);
    }

    /**
     * Recalcule tous les modifiers d'attribut pour ce joueur en fonction de son NBT.
     * Retire d'abord chaque modifier existant (par UUID), puis en ajoute un nouveau avec amount * level.
     */
    public static void reapplyAll(ServerPlayer p) {
        CompoundTag benedictions = store(p).getCompound(NBT_KEY);
        if (benedictions.isEmpty()) return;

        for (String id : new ArrayList<>(benedictions.getAllKeys())) {
            int lvl = benedictions.getInt(id);
            if (lvl <= 0) continue;
            RogueUpgrade upg = RogueUpgradeRegistry.get(id);
            if (upg == null) continue;

            for (RogueUpgrade.AttributeMod mod : upg.modifiers()) {
                AttributeInstance instance = p.getAttribute(mod.attribute());
                if (instance == null) continue;
                UUID modUuid = makeModifierUuid(id, mod);
                if (instance.getModifier(modUuid) != null) {
                    instance.removeModifier(modUuid);
                }
                double totalAmount = mod.amount() * lvl * difficultyMult();
                String name = "rogue_" + id + "_" + safeAttrName(mod);
                AttributeModifier newMod = new AttributeModifier(modUuid, name, totalAmount, mod.operation());
                instance.addTransientModifier(newMod);
            }
        }
    }

    /**
     * Retire tous les modifiers de bénédiction + purge le NBT du joueur.
     * Appelé à la fin de la horde (via clearAllPlayers) ou par un admin.
     */
    public static void clearAll(ServerPlayer p) {
        CompoundTag data = store(p);
        CompoundTag benedictions = data.getCompound(NBT_KEY);

        for (String id : new ArrayList<>(benedictions.getAllKeys())) {
            RogueUpgrade upg = RogueUpgradeRegistry.get(id);
            if (upg == null) continue;
            for (RogueUpgrade.AttributeMod mod : upg.modifiers()) {
                AttributeInstance instance = p.getAttribute(mod.attribute());
                if (instance == null) continue;
                UUID modUuid = makeModifierUuid(id, mod);
                if (instance.getModifier(modUuid) != null) {
                    instance.removeModifier(modUuid);
                }
            }
        }
        data.remove(NBT_KEY);
        data.remove(NBT_LAST_WAVE_KEY);
        data.remove(NBT_STATE);
        PENDING.remove(p.getUUID());
        // Plus de message au joueur (il réapparaissait à chaque connexion) : on nettoie en silence et on le note dans le journal
        if (!benedictions.isEmpty()) WaveSurvivorMod.LOGGER.info("[Benediction] '{}' benedictions clear (horde terminee)", p.getName().getString());
    }

    /** Clear tous les joueurs connectés (appelé à la fin de la horde). */
    public static void clearAllPlayers(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            clearAll(p);
        }
    }

    // ================================================================
    // Menu de sélection (envoie packet -> GUI client)
    // ================================================================

    // ───────── Relances, bannissements, menus en attente (1.8) ─────────

    private static final String NBT_STATE = "wave_survivor_bless_state";

    /** Menu en attente de choix d'un joueur : cartes proposées et vague. */
    private record Pending(List<String> ids, int wave) {}
    private static final java.util.Map<UUID, Pending> PENDING = new java.util.concurrent.ConcurrentHashMap<>();

    private static com.google.gson.JsonObject settings() { return BlessingCatalog.settings(); }

    private static int cfgInt(String section, String key, int def) {
        com.google.gson.JsonObject o = section.isEmpty() ? settings()
                : settings().has(section) && settings().get(section).isJsonObject() ? settings().getAsJsonObject(section) : new com.google.gson.JsonObject();
        return com.wavesurvivor.horde.editor.HordeJson.num(o, key, def);
    }

    /** État du joueur pour la partie (relances, bannissements, catégories bannies) : créé au premier menu, effacé en fin de horde. */
    private static CompoundTag blessState(ServerPlayer p, int wave) {
        CompoundTag root = store(p);
        CompoundTag st = root.getCompound(NBT_STATE);
        if (!st.contains("init")) {
            st.putBoolean("init", true);
            st.putInt("rerolls", Math.max(0, cfgInt("rerolls", "start", 2)));
            st.putInt("bans", Math.max(0, cfgInt("bans", "start", 1)));
            // pas de cadeau rétroactif si le joueur arrive en cours de partie
            st.putInt("rerollStep", wave / Math.max(1, cfgInt("rerolls", "every", 5)));
            st.putInt("banStep", wave / Math.max(1, cfgInt("bans", "every", 5)));
        }
        root.put(NBT_STATE, st);
        return st;
    }

    /** +1 relance / +1 bannissement toutes les N vagues (réglable). */
    private static void grant(ServerPlayer p, int wave) {
        CompoundTag st = blessState(p, wave);
        int rs = wave / Math.max(1, cfgInt("rerolls", "every", 5)), bs = wave / Math.max(1, cfgInt("bans", "every", 5));
        int addR = Math.max(0, rs - st.getInt("rerollStep")) * Math.max(0, cfgInt("rerolls", "grant", 1));
        int addB = Math.max(0, bs - st.getInt("banStep")) * Math.max(0, cfgInt("bans", "grant", 1));
        st.putInt("rerollStep", Math.max(rs, st.getInt("rerollStep")));
        st.putInt("banStep", Math.max(bs, st.getInt("banStep")));
        if (addR > 0 || addB > 0) {
            st.putInt("rerolls", st.getInt("rerolls") + addR);
            st.putInt("bans", st.getInt("bans") + addB);
            p.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("bless.granted", addR, addB)), false);
        }
    }

    public static int rerollsLeft(ServerPlayer p) { return blessState(p, com.wavesurvivor.horde.HordeManager.get().getCurrentWave()).getInt("rerolls"); }

    public static int bansLeft(ServerPlayer p) { return blessState(p, com.wavesurvivor.horde.HordeManager.get().getCurrentWave()).getInt("bans"); }

    /** Catégories bannies par ce joueur pour la partie. */
    public static java.util.Set<String> banned(ServerPlayer p) {
        java.util.Set<String> out = new java.util.HashSet<>();
        net.minecraft.nbt.ListTag l = store(p).getCompound(NBT_STATE).getList("banned", 8);
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    /** Peut-il choisir cette bénédiction ? Seulement parmi les cartes proposées ; les opérateurs peuvent tout choisir (commande). */
    public static boolean canChoose(ServerPlayer p, String id) {
        Pending pe = PENDING.get(p.getUUID());
        if (pe != null) return pe.ids().contains(id);
        return p.hasPermissions(2);
    }

    /** Choix fait : le menu en attente disparaît. */
    public static void onChosen(ServerPlayer p) { PENDING.remove(p.getUUID()); }

    public static void dropPending(UUID id) { PENDING.remove(id); }

    private static int menuSize(ServerPlayer p) {
        // Héritage — Seigneur 3 : +1 choix de bénédiction
        return Math.max(1, cfgInt("", "choices", MENU_CHOICES)) + (com.wavesurvivor.horde.renaissance.Heritage.has(p,
                com.wavesurvivor.horde.renaissance.Heritage.Branch.LORD, 3) ? 1 : 0);
    }

    private static void sendMenu(ServerPlayer p, List<String> ids, int wave) {
        PENDING.put(p.getUUID(), new Pending(new ArrayList<>(ids), wave));
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> p),
                new OpenBenedictionScreenPacket(ids, wave, (float) difficultyMult(), rerollsLeft(p), bansLeft(p), levels(p)));
        // Multijoueur : le jeu ne se met pas en pause, le joueur est donc protégé pendant son choix (solo : pause côté client)
        if (BenedictionProtection.needed(p.getServer())) BenedictionProtection.protect(p);
    }

    /**
     * Envoie un menu de sélection au joueur : des catégories distinctes, chacune à une rareté tirée au poids (vague, Chance), hors catégories
     * bannies. Le client reçoit un OpenBenedictionScreenPacket et affiche BenedictionSelectionScreen. Peut être ré-appelé via /ws benediction menu.
     */
    public static void openMenu(ServerPlayer p) {
        int wave = com.wavesurvivor.horde.HordeManager.get().getCurrentWave();
        grant(p, wave);
        List<String> choices = BlessingDraw.draw(p, menuSize(p), wave, java.util.List.of(), "");
        if (choices.isEmpty()) return;
        sendMenu(p, choices, wave);
        // Petit son pour attirer l'attention
        p.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Relance : retire les cartes et en tire de nouvelles (dans la famille demandée, "" = au hasard). */
    public static void reroll(ServerPlayer p, String family) {
        Pending pe = PENDING.get(p.getUUID());
        if (pe == null) return;
        CompoundTag st = blessState(p, pe.wave());
        if (st.getInt("rerolls") <= 0) {
            p.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("bless.no_reroll")), true);
            return;
        }
        st.putInt("rerolls", st.getInt("rerolls") - 1);
        List<String> choices = BlessingDraw.draw(p, menuSize(p), pe.wave(), java.util.List.of(), family);
        if (choices.isEmpty()) { st.putInt("rerolls", st.getInt("rerolls") + 1); return; }
        sendMenu(p, choices, pe.wave());
        p.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    /** Bannissement : la catégorie de la carte ne sera plus proposée à ce joueur pendant la partie ; la carte est remplacée. */
    public static void ban(ServerPlayer p, String category) {
        Pending pe = PENDING.get(p.getUUID());
        if (pe == null || category == null || category.isBlank()) return;
        int idx = -1;
        for (int i = 0; i < pe.ids().size(); i++) {
            RogueUpgrade u = RogueUpgradeRegistry.get(pe.ids().get(i));
            if (u != null && u.category().equals(category)) idx = i;
        }
        if (idx < 0) return;
        CompoundTag st = blessState(p, pe.wave());
        if (st.getInt("bans") <= 0) {
            p.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("bless.no_ban")), true);
            return;
        }
        st.putInt("bans", st.getInt("bans") - 1);
        net.minecraft.nbt.ListTag l = st.getList("banned", 8);
        l.add(net.minecraft.nbt.StringTag.valueOf(category));
        st.put("banned", l);
        // La carte bannie est remplacée par une catégorie qui n'est pas déjà sur la table
        List<String> next = new ArrayList<>(pe.ids());
        next.remove(idx);
        List<String> present = new ArrayList<>();
        for (String id : next) {
            RogueUpgrade u = RogueUpgradeRegistry.get(id);
            if (u != null) present.add(u.category());
        }
        List<String> fresh = BlessingDraw.draw(p, 1, pe.wave(), present, "");
        if (!fresh.isEmpty()) next.add(idx, fresh.get(0));
        if (next.isEmpty()) return;
        sendMenu(p, next, pe.wave());
        p.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    /** Ouvre le menu pour tous les joueurs connectés (appelé à la fin de chaque vague). */
    public static void openMenuForAll(MinecraftServer server, int currentWave) {
        if (server == null) return;
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // Anti-farm : skip si déjà choisi pour cette vague
            if (currentWave > 0 && getLastChosenWave(p) == currentWave) continue;
            openMenu(p);
            n++;
        }
        WaveSurvivorMod.LOGGER.info("[Benediction] Menu ouvert pour {} joueur(s) (vague {})", n, currentWave);
    }

    // ================================================================
    // Helpers
    // ================================================================

    /**
     * Multiplicateur des bonus de bénédiction selon la difficulté de la partie (les monstres montent avec elle) :
     * Facile et Normal ×1, Difficile ×1,25, Cauchemar ×1,5. Vaut pour tous les modes (hordes, Kingdom, Boss Rush, Défi du jour, sans fin).
     */
    public static double difficultyMult() {
        var d = com.wavesurvivor.horde.difficulty.HordeDifficulty.active();
        if (d == null) return 1.0;
        return switch (d.id) {
            case "hard" -> 1.25;
            case "nightmare" -> 1.5;
            default -> 1.0;
        };
    }

    private static UUID makeModifierUuid(String upgradeId, RogueUpgrade.AttributeMod mod) {
        String key = "rogue_up_" + upgradeId + "_" + safeAttrName(mod);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String safeAttrName(RogueUpgrade.AttributeMod mod) {
        return mod.attribute().getDescriptionId() + "_" + mod.operation().name();
    }
}
