package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import com.wavesurvivor.horde.editor.HordeEditorManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Packets de l'ÉDITEUR DE HORDES (ops).
 *   OpenList  S→C : liste des hordes + entités custom disponibles
 *   Request   C→S : demande le JSON d'une horde (pour éditer ou copier)
 *   HordeData S→C : JSON complet d'une horde
 *   Save      C→S : enregistre une horde (JSON complet, ancien nom pour renommage)
 *   Delete    C→S : supprime une horde
 *   Result    S→C : retour (ok / erreur)
 * Le JSON est compressé (gzip) : une horde dépasse facilement 32 Ko.
 */
public final class HordeEditorPackets {

    private HordeEditorPackets() {}

    public static final int PURPOSE_EDIT = 0;
    public static final int PURPOSE_COPY = 1;
    public static final int PURPOSE_LIST = 2;

    // ─── Utilitaires JSON compressé ───

    static void writeJson(FriendlyByteBuf buf, String json) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) { gz.write(json.getBytes(StandardCharsets.UTF_8)); }
            buf.writeByteArray(bos.toByteArray());
        } catch (Exception e) {
            buf.writeByteArray(new byte[0]);
        }
    }

    static String readJson(FriendlyByteBuf buf) {
        byte[] data = buf.readByteArray();
        if (data.length == 0) return "";
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return new String(gz.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    // ─── S→C : liste ───

    public record Entry(String name, String source, int waves, int mobs, int specials, int bosses) {}

    public static class OpenList {
        public final List<Entry> entries;
        public final List<String> customEntities;
        /** Coffres roulette : "configKey|Nom du coffre" (exclusions de zone dans l'onglet Autel). */
        public final List<String> chests;
        /** Catalogue des compétences (JSON { skills, schema }) pour ajouter des compétences aux unités (onglet Mobs). */
        public final String skillCatalog;
        public OpenList(List<Entry> entries, List<String> customEntities, List<String> chests) {
            this(entries, customEntities, chests, "{}");
        }
        public OpenList(List<Entry> entries, List<String> customEntities, List<String> chests, String skillCatalog) {
            this.entries = entries; this.customEntities = customEntities; this.chests = chests;
            this.skillCatalog = skillCatalog != null ? skillCatalog : "{}";
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entries.size());
            for (Entry e : entries) {
                buf.writeUtf(e.name()); buf.writeUtf(e.source());
                buf.writeVarInt(e.waves()); buf.writeVarInt(e.mobs()); buf.writeVarInt(e.specials()); buf.writeVarInt(e.bosses());
            }
            buf.writeVarInt(customEntities.size());
            for (String s : customEntities) buf.writeUtf(s);
            buf.writeVarInt(chests.size());
            for (String s : chests) buf.writeUtf(s);
            buf.writeUtf(skillCatalog, 1 << 20);
        }

        public static OpenList decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<Entry> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new Entry(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            int m = buf.readVarInt();
            List<String> ce = new ArrayList<>(m);
            for (int i = 0; i < m; i++) ce.add(buf.readUtf());
            int c = buf.readVarInt();
            List<String> ch = new ArrayList<>(c);
            for (int i = 0; i < c; i++) ch.add(buf.readUtf());
            String cat = buf.readUtf(1 << 20);
            return new OpenList(l, ce, ch, cat);
        }

        public static void handle(OpenList pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openHordeEditorList(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ─── C→S : demande d'une horde ───

    public static class Request {
        public final String name;
        public final int purpose;
        public Request(String name, int purpose) { this.name = name; this.purpose = purpose; }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(name); buf.writeVarInt(purpose); }
        public static Request decode(FriendlyByteBuf buf) { return new Request(buf.readUtf(), buf.readVarInt()); }
        public static void handle(Request pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p == null) return;
                if (pkt.purpose == PURPOSE_LIST) HordeEditorManager.openList(p);
                else HordeEditorManager.sendHorde(p, pkt.name, pkt.purpose);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ─── S→C : JSON d'une horde ───

    public static class HordeData {
        public final String name;
        public final int purpose;
        public final String json;
        public final String altarJson;
        public HordeData(String name, int purpose, String json, String altarJson) {
            this.name = name; this.purpose = purpose; this.json = json; this.altarJson = altarJson != null ? altarJson : "";
        }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(name); buf.writeVarInt(purpose); writeJson(buf, json); writeJson(buf, altarJson); }
        public static HordeData decode(FriendlyByteBuf buf) { return new HordeData(buf.readUtf(), buf.readVarInt(), readJson(buf), readJson(buf)); }
        public static void handle(HordeData pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleHordeData(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ─── C→S : enregistrer ───

    public static class Save {
        public final String oldName;
        public final String json;
        /** Config d'autel : "" = inchangée, {"__delete":true} = supprimée. */
        public final String altarJson;
        /** Onglets à enregistrer (HordeSections.ALL = toute la horde, comme avant). */
        public final int mask;
        public Save(String oldName, String json, String altarJson) { this(oldName, json, altarJson, com.wavesurvivor.horde.editor.HordeSections.ALL); }
        public Save(String oldName, String json, String altarJson, int mask) {
            this.oldName = oldName != null ? oldName : ""; this.json = json; this.altarJson = altarJson != null ? altarJson : ""; this.mask = mask;
        }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(oldName); writeJson(buf, json); writeJson(buf, altarJson); buf.writeInt(mask); }
        public static Save decode(FriendlyByteBuf buf) { return new Save(buf.readUtf(), readJson(buf), readJson(buf), buf.readInt()); }
        public static void handle(Save pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) HordeEditorManager.save(p, pkt.oldName, pkt.json, pkt.altarJson, pkt.mask);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ─── C→S : supprimer ───

    /** C→S : donne le Bâton de tracé pour le chemin n° path de la horde (mode Kingdom). */
    public static class GiveWand {
        public final String horde;
        public final int path;
        public GiveWand(String horde, int path) { this.horde = horde != null ? horde : ""; this.path = path; }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(horde); buf.writeVarInt(path); }
        public static GiveWand decode(FriendlyByteBuf buf) { return new GiveWand(buf.readUtf(), buf.readVarInt()); }
        public static void handle(GiveWand pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) com.wavesurvivor.horde.kingdom.PathWandItem.give(p, pkt.horde, pkt.path);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Delete {
        public final String name;
        public Delete(String name) { this.name = name; }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(name); }
        public static Delete decode(FriendlyByteBuf buf) { return new Delete(buf.readUtf()); }
        public static void handle(Delete pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) HordeEditorManager.delete(p, pkt.name);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ─── S→C : résultat ───

    public static class Result {
        public final boolean ok;
        public final String message;
        public final String savedName;
        public Result(boolean ok, String message, String savedName) { this.ok = ok; this.message = message; this.savedName = savedName != null ? savedName : ""; }
        public void encode(FriendlyByteBuf buf) { buf.writeBoolean(ok); buf.writeUtf(message); buf.writeUtf(savedName); }
        public static Result decode(FriendlyByteBuf buf) { return new Result(buf.readBoolean(), buf.readUtf(), buf.readUtf()); }
        public static void handle(Result pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleHordeEditorResult(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }
}
