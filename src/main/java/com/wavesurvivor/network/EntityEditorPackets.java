package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import com.wavesurvivor.horde.editor.EntityEditorManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packets de l'Éditeur d'entités custom et de compétences (ops).
 *   Open   C→S : demande les données
 *   Data   S→C : entités + compétences + schéma des compétences (JSON compressé)
 *   Save   C→S : enregistre une entité ou une compétence
 *   Delete C→S : supprime une entité ou une compétence
 * Le retour (ok / erreur) utilise HordeEditorPackets.Result.
 */
public final class EntityEditorPackets {

    private EntityEditorPackets() {}

    public static class Open {
        /** Entité à sélectionner à l'ouverture ("" = aucune). */
        public final String focus;
        public Open() { this(""); }
        public Open(String focus) { this.focus = focus != null ? focus : ""; }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(focus); }
        public static Open decode(FriendlyByteBuf buf) { return new Open(buf.readUtf()); }
        public static void handle(Open pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) EntityEditorManager.open(p, pkt.focus);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** C→S : crée une copie dédiée d'une entité custom pour une horde (l'original reste intact). */
    public static class Fork {
        public final String source;
        public final String hordeName;
        public Fork(String source, String hordeName) { this.source = source; this.hordeName = hordeName != null ? hordeName : ""; }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(source); buf.writeUtf(hordeName); }
        public static Fork decode(FriendlyByteBuf buf) { return new Fork(buf.readUtf(), buf.readUtf()); }
        public static void handle(Fork pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) EntityEditorManager.fork(p, pkt.source, pkt.hordeName);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** S→C : copie créée → l'entrée de horde pointe vers newName. */
    public static class Forked {
        public final String source;
        public final String newName;
        public Forked(String source, String newName) { this.source = source; this.newName = newName; }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(source); buf.writeUtf(newName); }
        public static Forked decode(FriendlyByteBuf buf) { return new Forked(buf.readUtf(), buf.readUtf()); }
        public static void handle(Forked pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleForked(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Data {
        public final String json;
        public Data(String json) { this.json = json; }
        public void encode(FriendlyByteBuf buf) { HordeEditorPackets.writeJson(buf, json); }
        public static Data decode(FriendlyByteBuf buf) { return new Data(HordeEditorPackets.readJson(buf)); }
        public static void handle(Data pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openEntityEditor(pkt)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Save {
        public final boolean skill;
        public final String oldName;
        public final String json;
        public Save(boolean skill, String oldName, String json) { this.skill = skill; this.oldName = oldName != null ? oldName : ""; this.json = json; }
        public void encode(FriendlyByteBuf buf) { buf.writeBoolean(skill); buf.writeUtf(oldName); HordeEditorPackets.writeJson(buf, json); }
        public static Save decode(FriendlyByteBuf buf) { return new Save(buf.readBoolean(), buf.readUtf(), HordeEditorPackets.readJson(buf)); }
        public static void handle(Save pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) EntityEditorManager.save(p, pkt.skill, pkt.oldName, pkt.json);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Delete {
        public final boolean skill;
        public final String name;
        public Delete(boolean skill, String name) { this.skill = skill; this.name = name; }
        public void encode(FriendlyByteBuf buf) { buf.writeBoolean(skill); buf.writeUtf(name); }
        public static Delete decode(FriendlyByteBuf buf) { return new Delete(buf.readBoolean(), buf.readUtf()); }
        public static void handle(Delete pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) EntityEditorManager.delete(p, pkt.skill, pkt.name);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
