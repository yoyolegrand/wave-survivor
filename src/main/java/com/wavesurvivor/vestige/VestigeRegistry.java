package com.wavesurvivor.vestige;

import com.wavesurvivor.config.RulesConfig;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.VestigeTargetPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * REGISTRE DES VESTIGES (1.9) : toutes les structures du mod connues du monde (arènes de horde, Sanctuaires du Séraphin, ruines), enregistrées à leur
 * génération (ou à leur pose par commande), et ce que chaque joueur a déjà « réclamé » (horde terminée, statue cliquée, coffre ou sceau ouvert).
 * La Boussole des Vestiges vise la plus proche non réclamée. Suivi par joueur, ou partagé par tout le serveur (rules.json : vestigeShared).
 * Les structures sont connues à leur génération, ou trouvées à l'avance par {@link VestigeScanner} (calcul des emplacements possibles autour d'un joueur qui porte la boussole).
 */
public class VestigeRegistry extends SavedData {

    public static final int HORDE = 0, SHRINE = 1, RUIN = 2;
    private static final Set<String> RUIN_IDS = Set.of("chapelle", "autel_souille", "reliquaire_scelle");
    private static final String NAME = "wavesurvivor_vestiges";

    public record Entry(String dim, int kind, String id, int x, int y, int z) {
        public String key() { return dim + "|" + x + "|" + z; }
        public BlockPos pos() { return new BlockPos(x, y, z); }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> claims = new HashMap<>();
    /** Enregistrements en attente : la génération du monde se fait hors du fil principal, les données sont modifiées ensuite. */
    private static final Queue<Entry> PENDING = new ConcurrentLinkedQueue<>();

    public static VestigeRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(VestigeRegistry::load, VestigeRegistry::new, NAME);
    }

    // ─── Enregistrement ───

    /** Une arène (horde ou ruine selon son identifiant) vient d'être générée ou posée. */
    public static void recordArena(ServerLevel level, String arenaId, BlockPos center) {
        record(level, RUIN_IDS.contains(arenaId) ? RUIN : HORDE, arenaId, center);
    }

    public static void record(ServerLevel level, int kind, String id, BlockPos center) {
        PENDING.add(new Entry(level.dimension().location().toString(), kind, id, center.getX(), center.getY(), center.getZ()));
    }

    private void drain() {
        Entry e;
        boolean dirty = false;
        while ((e = PENDING.poll()) != null) {
            if (entries.containsKey(e.key()) || hasNear(e)) continue; // déjà connue (génération + recherche de la boussole peuvent donner 1 bloc d'écart)
            entries.put(e.key(), e);
            dirty = true;
        }
        if (dirty) setDirty();
    }

    private boolean hasNear(Entry n) {
        for (Entry e : entries.values())
            if (e.kind == n.kind && e.dim.equals(n.dim) && Math.abs(e.x - n.x) <= 6 && Math.abs(e.z - n.z) <= 6) return true;
        return false;
    }

    /** La recherche active de la boussole n'a pas fini d'examiner les emplacements possibles autour du joueur. */
    public static boolean scanning(ServerPlayer p) { return VestigeScanner.busy(p); }

    /** Diagnostic de la commande /ws vestiges [scan]. */
    public static java.util.List<String> report(ServerPlayer p, boolean scanAll) {
        java.util.List<String> l = VestigeScanner.report(p, scanAll);
        VestigeRegistry r = get(p.getServer());
        r.drain();
        l.add("§7Registre : " + r.entries.size() + " structure(s) connue(s), " + r.claims.values().stream().mapToInt(Set::size).sum() + " réclamation(s).");
        Events.LAST.remove(p.getUUID());
        return l;
    }

    // ─── Réclamations ───

    private static UUID owner(Player p) { return RulesConfig.vestigeShared() ? new UUID(0, 0) : p.getUUID(); }

    private boolean claimed(Player p, Entry e) {
        Set<String> s = claims.get(owner(p));
        return s != null && s.contains(e.key());
    }

    /** Réclame la structure de ce type la plus proche de {@code pos} (dans le rayon, et de cet identifiant si donné). @return vrai si une structure a été réclamée. */
    public static boolean claimNear(ServerPlayer p, int kind, BlockPos pos, int radius, String id) {
        if (p.getServer() == null) return false;
        VestigeRegistry r = get(p.getServer());
        r.drain();
        String dim = p.level().dimension().location().toString();
        Entry best = null;
        double bestD = (double) radius * radius;
        for (Entry e : r.entries.values()) {
            if (e.kind != kind || !e.dim.equals(dim) || (id != null && !id.equals(e.id)) || r.claimed(p, e)) continue;
            double d = pos.distSqr(new BlockPos(e.x, pos.getY(), e.z));
            if (d <= bestD) { best = e; bestD = d; }
        }
        if (best == null) return false;
        r.claims.computeIfAbsent(owner(p), k -> new HashSet<>()).add(best.key());
        r.setDirty();
        Events.LAST.remove(p.getUUID()); // la cible est recalculée tout de suite
        return true;
    }

    // ─── Recherche ───

    /** Vestige non réclamé le plus proche dans la dimension du joueur ; {@code filter} : 0 tout, 1 hordes, 2 Sanctuaires, 3 ruines. Null si aucun dans le rayon. */
    public static Entry nearest(ServerPlayer p, int filter) {
        if (p.getServer() == null) return null;
        VestigeRegistry r = get(p.getServer());
        r.drain();
        String dim = p.level().dimension().location().toString();
        double max = RulesConfig.vestigeRadius(), bestD = max * max;
        Entry best = null;
        for (Entry e : r.entries.values()) {
            if (!e.dim.equals(dim) || (filter > 0 && e.kind != filter - 1) || r.claimed(p, e)) continue;
            double dx = e.x + 0.5 - p.getX(), dz = e.z + 0.5 - p.getZ(), d = dx * dx + dz * dz;
            if (d <= bestD) { best = e; bestD = d; }
        }
        return best;
    }

    public static double distance(ServerPlayer p, Entry e) {
        return Math.sqrt((e.x + 0.5 - p.getX()) * (e.x + 0.5 - p.getX()) + (e.z + 0.5 - p.getZ()) * (e.z + 0.5 - p.getZ()));
    }

    /** Première Boussole des Vestiges du joueur (main, seconde main, puis inventaire). */
    public static ItemStack compassOf(Player p) {
        if (p.getMainHandItem().getItem() instanceof VestigeCompassItem) return p.getMainHandItem();
        if (p.getOffhandItem().getItem() instanceof VestigeCompassItem) return p.getOffhandItem();
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof VestigeCompassItem) return s;
        return ItemStack.EMPTY;
    }

    /** Envoie la cible au joueur (seulement si elle a changé, sauf {@code force}). */
    public static void sync(ServerPlayer p, boolean force) {
        ItemStack compass = compassOf(p);
        if (compass.isEmpty()) return;
        Entry e = nearest(p, VestigeCompassItem.filterOf(compass));
        String sig = e == null ? "" : e.key();
        if (!force && sig.equals(Events.LAST.get(p.getUUID()))) return;
        Events.LAST.put(p.getUUID(), sig);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                e == null ? new VestigeTargetPacket(false, new ResourceLocation("minecraft:overworld"), BlockPos.ZERO)
                        : new VestigeTargetPacket(true, new ResourceLocation(e.dim), e.pos()));
    }

    // ─── Sauvegarde ───

    static VestigeRegistry load(CompoundTag tag) {
        VestigeRegistry r = new VestigeRegistry();
        for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Entry e = new Entry(c.getString("dim"), c.getInt("kind"), c.getString("id"), c.getInt("x"), c.getInt("y"), c.getInt("z"));
            r.entries.put(e.key(), e);
        }
        CompoundTag cl = tag.getCompound("claims");
        for (String k : cl.getAllKeys()) {
            Set<String> s = new HashSet<>();
            for (Tag t : cl.getList(k, Tag.TAG_STRING)) s.add(t.getAsString());
            try { r.claims.put(UUID.fromString(k), s); } catch (IllegalArgumentException ignored) { }
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", e.dim);
            c.putInt("kind", e.kind);
            c.putString("id", e.id);
            c.putInt("x", e.x);
            c.putInt("y", e.y);
            c.putInt("z", e.z);
            l.add(c);
        }
        tag.put("entries", l);
        CompoundTag cl = new CompoundTag();
        for (Map.Entry<UUID, Set<String>> en : claims.entrySet()) {
            ListTag s = new ListTag();
            for (String k : en.getValue()) s.add(StringTag.valueOf(k));
            cl.put(en.getKey().toString(), s);
        }
        tag.put("claims", cl);
        return tag;
    }

    // ─── Événements ───

    public static class Events {
        /** Dernière cible envoyée à chaque joueur (clé de la structure, vide = aucune). */
        static final Map<UUID, String> LAST = new HashMap<>();

        /** Cible rafraîchie toutes les 2 secondes pour les joueurs qui ont une boussole. */
        @SubscribeEvent
        public void onTick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
            // Recherche active des structures pas encore générées (étalée, uniquement avec une boussole)
            if (p.tickCount % 4 == 0 && !compassOf(p).isEmpty()) VestigeScanner.tick(p);
            if (p.tickCount % 40 != 0) return;
            sync(p, false);
        }

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent e) { LAST.remove(e.getEntity().getUUID()); VestigeScanner.forget(e.getEntity().getUUID()); }

        /** Ruine : ouvrir un de ses coffres la réclame (le sceau du Reliquaire est géré à part). */
        @SubscribeEvent
        public void onChest(PlayerInteractEvent.RightClickBlock e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
            if (!(p.level().getBlockEntity(e.getPos()) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity)) return;
            claimNear(p, RUIN, e.getPos(), 40, null);
        }
    }
}
