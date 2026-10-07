package com.wavesurvivor.network;

import com.wavesurvivor.config.HordeExchange;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Import / export de hordes : bouton « Exporter » de l'éditeur, écran /ws import. Réservé aux OP (niveau 2). */
public final class HordeExchangePackets {

    private HordeExchangePackets() {}

    private static boolean allowed(ServerPlayer p) {
        return p != null && p.hasPermissions(2);
    }

    // ─── C→S : exporter une horde enregistrée ───

    public static class Export {
        public final String name;
        public Export(String name) { this.name = name != null ? name : ""; }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(name); }
        public static Export decode(FriendlyByteBuf buf) { return new Export(buf.readUtf()); }
        public static void handle(Export pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (allowed(p)) HordeExchange.export(p, pkt.name);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ─── S→C : liste des fichiers à importer ───

    public static class OpenImport {
        public final List<HordeExchange.Entry> entries;
        public OpenImport(List<HordeExchange.Entry> entries) { this.entries = entries; }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entries.size());
            for (HordeExchange.Entry e : entries) {
                buf.writeUtf(e.file()); buf.writeUtf(e.name()); buf.writeUtf(e.type());
                buf.writeUtf(e.description() == null ? "" : e.description(), 4096);
                buf.writeVarInt(e.waves()); buf.writeVarInt(e.bosses());
                buf.writeVarInt(e.entities()); buf.writeVarInt(e.skills()); buf.writeVarInt(e.chests());
                buf.writeVarInt(e.missingMods().size());
                for (String m : e.missingMods()) buf.writeUtf(m);
                buf.writeBoolean(e.conflict());
                buf.writeUtf(e.author() == null ? "" : e.author());
            }
        }

        public static OpenImport decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<HordeExchange.Entry> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String file = buf.readUtf(), name = buf.readUtf(), type = buf.readUtf(), desc = buf.readUtf(4096);
                int waves = buf.readVarInt(), bosses = buf.readVarInt(), ent = buf.readVarInt(), sk = buf.readVarInt(), ch = buf.readVarInt();
                int m = buf.readVarInt();
                List<String> mods = new ArrayList<>(m);
                for (int j = 0; j < m; j++) mods.add(buf.readUtf());
                boolean conflict = buf.readBoolean();
                String author = buf.readUtf();
                l.add(new HordeExchange.Entry(file, name, type, desc, waves, bosses, ent, sk, ch, mods, conflict, author));
            }
            return new OpenImport(l);
        }

        public static void handle(OpenImport pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.wavesurvivor.client.HordeImportScreen.open(pkt.entries)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Ouvre l'écran d'import chez le joueur (commande /ws import). */
    public static void openFor(ServerPlayer p) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new OpenImport(HordeExchange.list()));
    }

    // ─── C→S : importer un fichier ───

    public static class DoImport {
        public final String file;
        public final int mode;
        public DoImport(String file, int mode) { this.file = file; this.mode = mode; }
        public void encode(FriendlyByteBuf buf) { buf.writeUtf(file); buf.writeVarInt(mode); }
        public static DoImport decode(FriendlyByteBuf buf) { return new DoImport(buf.readUtf(), buf.readVarInt()); }
        public static void handle(DoImport pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (!allowed(p)) return;
                HordeExchange.doImport(p, pkt.file, pkt.mode);
                openFor(p); // liste rafraîchie (la horde importée apparaît désormais « déjà présente »)
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
