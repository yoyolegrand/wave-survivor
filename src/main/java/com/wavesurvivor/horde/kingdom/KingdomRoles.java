package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.KingdomRolesPacket;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RÔLES DU ROYAUME (mode Kingdom uniquement) : des mécaniques, pas de statistiques.
 *  - Un rôle par joueur, et chaque rôle n'est tenu que par un seul joueur connecté ;
 *  - Choix sur l'écran de l'autel (celui qui lance) ou dans l'onglet « Rôles » de la boutique du Monolithe (tout le monde) ;
 *  - Un joueur sans rôle peut en prendre un à tout moment ; changer de rôle n'est possible que pendant le Calme ;
 *  - Un rôle dont la mécanique est absente de la horde n'est pas proposé (Mineur sans gisements) ;
 *  - Le rôle d'un joueur déconnecté redevient libre.
 * Les effets de chaque rôle sont branchés ailleurs ({@link #has(ServerPlayer, Role)} / {@link #present(Role)}).
 */
public final class KingdomRoles {

    private KingdomRoles() {}

    public enum Role {
        ARCANIST("arcanist", Items.END_CRYSTAL),
        MINER("miner", Items.IRON_PICKAXE),
        BUILDER("builder", Items.BRICKS),
        COMMANDER("commander", Items.IRON_SWORD),
        QUARTERMASTER("quartermaster", Items.GOLD_INGOT),
        ALCHEMIST("alchemist", Items.BREWING_STAND),
        BOUNTY_HUNTER("bounty_hunter", Items.CROSSBOW),
        AUGUR("augur", Items.ENDER_EYE),
        SCAVENGER("scavenger", Items.CHAIN);

        public final String id;
        public final Item icon;

        Role(String id, Item icon) { this.id = id; this.icon = icon; }

        public static Role byId(String id) {
            if (id == null) return null;
            for (Role r : values()) if (r.id.equalsIgnoreCase(id.trim())) return r;
            return null;
        }
    }

    /** Rôle de chaque joueur pendant la partie. */
    private static final Map<UUID, Role> ROLES = new HashMap<>();

    /** Sauvegarde de partie : rôle de chaque joueur (UUID → nom du rôle). */
    static net.minecraft.nbt.CompoundTag saveRoles() {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        for (Map.Entry<UUID, Role> e : ROLES.entrySet()) t.putString(e.getKey().toString(), e.getValue().name());
        return t;
    }

    /** Reprise de partie : chaque joueur retrouve son rôle. */
    static void restoreRoles(net.minecraft.server.MinecraftServer server, net.minecraft.nbt.CompoundTag t) {
        for (String k : t.getAllKeys()) {
            try { ROLES.put(UUID.fromString(k), Role.valueOf(t.getString(k))); } catch (IllegalArgumentException ignored) {}
        }
        if (server != null) sync(server);
    }
    /** Rôle choisi sur l'écran de l'autel, appliqué au lancement. */
    private static final Map<UUID, Role> PENDING = new HashMap<>();
    /** Rôles proposés par la horde en cours. */
    private static final Set<Role> AVAILABLE = EnumSet.noneOf(Role.class);
    private static boolean on = false;

    // ─── Copie client (onglet Rôles de la boutique) ───
    public static boolean clientOn = false;
    public static boolean clientCalm = false;
    public static final Set<Role> CLIENT_AVAILABLE = EnumSet.noneOf(Role.class);
    /** Rôle → [UUID du joueur, nom affiché]. */
    public static final Map<Role, String[]> CLIENT_HOLDERS = new HashMap<>();
    /** Flèche du HUD : type de cible (« catalyst », « deposit », vide) et positions x, y, z à la suite. */
    public static String clientTrackKind = "";
    public static double[] clientTrack = new double[0];
    /** 2e flèche du HUD : objectif du Calme (« treasure », « foyer »), indépendante de celle du rôle. */
    public static String clientObjKind = "";
    public static double[] clientObjTrack = new double[0];

    // ─── Disponibilité ───

    /** Rôles proposés par une horde (le Mineur exige des gisements dans ses événements de chaos). */
    public static Set<Role> availableFor(HordeConfigMultiData h) {
        Set<Role> out = EnumSet.allOf(Role.class);
        if (!hasDeposits(h)) out.remove(Role.MINER);
        return out;
    }

    private static boolean hasDeposits(HordeConfigMultiData h) {
        if (h == null || h.configData == null || !h.configData.chaosEnabled || h.configData.chaosEvents == null) return false;
        for (var ev : h.configData.chaosEvents) if (com.wavesurvivor.horde.HordeManager.isResourceEvent(ev)) return true;
        return false;
    }

    /** « arcanist,builder,… » (pour l'écran de l'autel). */
    public static String encodeAvailable(HordeConfigMultiData h) {
        StringBuilder sb = new StringBuilder();
        for (Role r : availableFor(h)) sb.append(sb.length() > 0 ? "," : "").append(r.id);
        return sb.toString();
    }

    // ─── Cycle de vie ───

    public static void setPending(ServerPlayer p, String roleId) {
        Role r = Role.byId(roleId);
        if (r == null) PENDING.remove(p.getUUID());
        else PENDING.put(p.getUUID(), r);
    }

    /** Lancement de la partie : rôles disponibles, rôles choisis à l'autel appliqués. */
    public static void start(MinecraftServer server, HordeConfigMultiData h) {
        ROLES.clear();
        AVAILABLE.clear();
        AVAILABLE.addAll(availableFor(h));
        on = true;
        for (Map.Entry<UUID, Role> e : PENDING.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && AVAILABLE.contains(e.getValue()) && holder(server, e.getValue()) == null) {
                ROLES.put(p.getUUID(), e.getValue());
                announce(server, p, e.getValue());
                KingdomRoleExtras.onRoleGiven(p, e.getValue());
            }
        }
        PENDING.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!ROLES.containsKey(p.getUUID())) p.sendSystemMessage(WSLang.c("kingdom.role.hint"));
        }
        sync(server);
    }

    public static void stop(MinecraftServer server) {
        boolean was = on;
        on = false;
        ROLES.clear();
        AVAILABLE.clear();
        if (was) sync(server);
    }

    public static boolean active() {
        return on && KingdomManager.isActive();
    }

    // ─── Requêtes (effets des rôles) ───

    public static Role of(ServerPlayer p) {
        return active() && p != null ? ROLES.get(p.getUUID()) : null;
    }

    public static boolean has(ServerPlayer p, Role r) {
        return of(p) == r;
    }

    /** Joueur connecté qui tient ce rôle (null = libre). */
    public static ServerPlayer holder(MinecraftServer server, Role r) {
        if (server == null) return null;
        for (Map.Entry<UUID, Role> e : ROLES.entrySet()) {
            if (e.getValue() != r) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) return p;
        }
        return null;
    }

    /** Prix avec la remise du Bâtisseur (-20 %) s'il est là. */
    public static int builderPrice(int cost, boolean builder) {
        return builder ? Math.max(1, (int) Math.round(cost * 0.8)) : cost;
    }

    /** Vrai si un joueur connecté tient ce rôle. */
    public static boolean present(Role r) {
        var lvl = KingdomManager.level();
        return active() && lvl != null && holder(lvl.getServer(), r) != null;
    }

    // ─── Choix ───

    /** Choix d'un rôle dans la boutique du Monolithe (index dans {@link Role#values()}). */
    public static void choose(ServerPlayer p, int index) {
        if (!active()) {
            p.displayClientMessage(WSLang.c("kingdom.role.only_kingdom"), true);
            return;
        }
        Role[] all = Role.values();
        if (index < 0 || index >= all.length) return;
        Role r = all[index];
        Role current = ROLES.get(p.getUUID());
        if (current == r) return;
        if (!AVAILABLE.contains(r)) {
            p.displayClientMessage(WSLang.c("kingdom.role.unavailable"), true);
            return;
        }
        // Le rôle est définitif jusqu'à la fin de la horde (sauf joueur en créatif ou opérateur)
        if (current != null && !canSwitch(p)) {
            p.displayClientMessage(WSLang.c("kingdom.role.locked"), true);
            return;
        }
        ServerPlayer h = holder(p.getServer(), r);
        if (h != null && h != p) {
            p.displayClientMessage(WSLang.c("kingdom.role.taken", h.getGameProfile().getName()), true);
            return;
        }
        // Le rôle d'un joueur déconnecté est libéré
        ROLES.entrySet().removeIf(e -> e.getValue() == r);
        ROLES.put(p.getUUID(), r);
        announce(p.getServer(), p, r);
        KingdomRoleExtras.onRoleGiven(p, r);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.3f);
        sync(p.getServer());
    }

    /** Changer de rôle en cours de horde : réservé aux joueurs en créatif ou opérateurs (niveau 2). */
    public static boolean canSwitch(net.minecraft.world.entity.player.Player p) {
        return p != null && (p.isCreative() || p.hasPermissions(2));
    }

    private static void announce(MinecraftServer server, ServerPlayer p, Role r) {
        Component msg = WSLang.c("kingdom.role.chosen", p.getGameProfile().getName(), WSLang.t("kingdom.role." + r.id));
        for (ServerPlayer o : server.getPlayerList().getPlayers()) o.sendSystemMessage(msg);
    }

    // ─── Synchronisation ───

    public static void sync(MinecraftServer server) {
        if (server == null || NetworkHandler.CHANNEL == null) return;
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), packet(server));
        KingdomRoleState.syncAll(server);
    }

    private static KingdomRolesPacket packet(MinecraftServer server) {
        List<String> avail = new ArrayList<>();
        for (Role r : AVAILABLE) avail.add(r.id);
        List<String[]> holders = new ArrayList<>();
        for (Role r : Role.values()) {
            ServerPlayer h = holder(server, r);
            if (h != null) holders.add(new String[]{r.id, h.getUUID().toString(), h.getGameProfile().getName()});
        }
        return new KingdomRolesPacket(active(), KingdomManager.isActive() && KingdomManager.phase() == KingdomManager.Phase.CALM,
                avail, holders);
    }

    public static class Events {
        @SubscribeEvent
        public void rolesLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || p.getServer() == null || NetworkHandler.CHANNEL == null) return;
            sync(p.getServer());
            if (active() && !ROLES.containsKey(p.getUUID())) p.sendSystemMessage(WSLang.c("kingdom.role.hint"));
        }

        @SubscribeEvent
        public void rolesLogout(PlayerEvent.PlayerLoggedOutEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || p.getServer() == null || !active()) return;
            // Après la déconnexion : son rôle apparaît comme libre
            p.getServer().execute(() -> sync(p.getServer()));
        }
    }
}
