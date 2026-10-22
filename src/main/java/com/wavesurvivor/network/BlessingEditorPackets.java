package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import com.wavesurvivor.horde.editor.BlessingEditorManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquets de l'Éditeur des bénédictions (1.8, opérateurs).
 *   Open   C→S : demande les données
 *   Data   S→C : catégories effectives + origine de chacune (JSON compressé)
 *   Save   C→S : enregistre une catégorie (ancien id, JSON)
 *   Delete C→S : supprime une catégorie
 *   Revert C→S : rétablit la version du mod d'une catégorie (id vide = toutes celles que le joueur a supprimées)
 * Le retour (ok / erreur) utilise HordeEditorPackets.Result.
 */
public final class BlessingEditorPackets {

    private BlessingEditorPackets() {}

    public static class Open {
        public Open() {}
        public void encode(FriendlyByteBuf buf) {}
        public static Open decode(FriendlyByteBuf buf) { return new Open(); }
        public static void handle(Open pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) BlessingEditorManager.open(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Data {
        public final String json;
        public Data(String json) { this.json = json; }
        public void encode(FriendlyByteBuf buf) { HordeEditorPackets.writeJson(buf, json); }
        public static Data decode(FriendlyByteBuf buf) { return new Data(HordeEditorPackets.readJson(buf)); }
        public static void handle(Data pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openBlessingEditor(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Save {
        public final String oldId;
        public final String json;
        public Save(String oldId, String json) { this.oldId = oldId != null ? oldId : ""; this.json = json; }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(oldId); HordeEditorPackets.writeJson(buf, json); }
        public static Save decode(FriendlyByteBuf buf) { return new Save(buf.readUtf(), HordeEditorPackets.readJson(buf)); }
        public static void handle(Save pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) BlessingEditorManager.save(p, pkt.oldId, pkt.json);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** C→S : enregistre les réglages du tirage (JSON complet ; vide = revenir aux valeurs du mod). */
    public static class SaveSettings {
        public final String json;
        public SaveSettings(String json) { this.json = json != null ? json : ""; }
        public void encode(FriendlyByteBuf buf) { HordeEditorPackets.writeJson(buf, json); }
        public static SaveSettings decode(FriendlyByteBuf buf) { return new SaveSettings(HordeEditorPackets.readJson(buf)); }
        public static void handle(SaveSettings pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) BlessingEditorManager.saveSettings(p, pkt.json);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** C→S : exporte ta couche dans un pack, ou importe un pack (0 export, 1 import, 2 import en remplaçant les conflits). */
    public static class PackAction {
        public static final byte EXPORT = 0, IMPORT = 1, IMPORT_OVERWRITE = 2;
        public final byte action;
        public final String name;
        /** Éléments à exporter (identifiants de catégories, « @settings », « @deleted ») ; vide = tout. */
        public final java.util.List<String> ids;
        public PackAction(byte action, String name, java.util.List<String> ids) { this.action = action; this.name = name != null ? name : ""; this.ids = ids; }
        public void encode(FriendlyByteBuf buf) {
            buf.writeByte(action);
            buf.writeUtf(name, 64);
            buf.writeVarInt(ids.size());
            for (String i : ids) buf.writeUtf(i, 64);
        }
        public static PackAction decode(FriendlyByteBuf buf) {
            byte a = buf.readByte();
            String n = buf.readUtf(64);
            int c = Math.min(buf.readVarInt(), 500);
            java.util.List<String> l = new java.util.ArrayList<>();
            for (int i = 0; i < c; i++) l.add(buf.readUtf(64));
            return new PackAction(a, n, l);
        }
        public static void handle(PackAction pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) BlessingEditorManager.pack(p, pkt.action, pkt.name, pkt.ids);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Delete {
        public final String id;
        public Delete(String id) { this.id = id != null ? id : ""; }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(id); }
        public static Delete decode(FriendlyByteBuf buf) { return new Delete(buf.readUtf()); }
        public static void handle(Delete pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) BlessingEditorManager.delete(p, pkt.id);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Revert {
        public final String id;
        public Revert(String id) { this.id = id != null ? id : ""; }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(id); }
        public static Revert decode(FriendlyByteBuf buf) { return new Revert(buf.readUtf()); }
        public static void handle(Revert pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) BlessingEditorManager.revert(p, pkt.id);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
