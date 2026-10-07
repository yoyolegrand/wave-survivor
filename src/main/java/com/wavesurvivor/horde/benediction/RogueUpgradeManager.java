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
        int newLevel = getLevel(p, id) + 1;
        setLevel(p, id, newLevel);
        reapplyAll(p);

        if (upg.healOnApply() > 0) {
            p.heal(upg.healOnApply());
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
                double totalAmount = mod.amount() * lvl;
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
        p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.benedictions_dissipees")).withStyle(ChatFormatting.RED));
        WaveSurvivorMod.LOGGER.info("[Benediction] '{}' benedictions clear (horde terminee)",
                p.getName().getString());
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

    /**
     * Envoie un menu de sélection avec 3 upgrades aléatoires au joueur.
     * Le client reçoit un OpenBenedictionScreenPacket et affiche BenedictionSelectionScreen.
     * Peut être ré-appelé via /ws benediction menu.
     */
    public static void openMenu(ServerPlayer p) {
        List<String> ids = RogueUpgradeRegistry.allIds();
        Collections.shuffle(ids, RNG);
        // Héritage — Seigneur 3 : +1 choix de bénédiction
        int n = MENU_CHOICES + (com.wavesurvivor.horde.renaissance.Heritage.has(p, com.wavesurvivor.horde.renaissance.Heritage.Branch.LORD, 3) ? 1 : 0);
        List<String> choices = new ArrayList<>(ids.subList(0, Math.min(n, ids.size())));

        int currentWave = com.wavesurvivor.horde.HordeManager.get().getCurrentWave();
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> p),
                new OpenBenedictionScreenPacket(choices, currentWave));

        // Petit son pour attirer l'attention
        p.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
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

    private static UUID makeModifierUuid(String upgradeId, RogueUpgrade.AttributeMod mod) {
        String key = "rogue_up_" + upgradeId + "_" + safeAttrName(mod);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String safeAttrName(RogueUpgrade.AttributeMod mod) {
        return mod.attribute().getDescriptionId() + "_" + mod.operation().name();
    }
}
