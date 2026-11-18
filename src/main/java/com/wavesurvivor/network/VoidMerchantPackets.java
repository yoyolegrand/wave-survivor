package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 2.1 — Boutique du Marchand du Néant : ouverture / rafraîchissement (serveur → client) et achat (client → serveur). */
public final class VoidMerchantPackets {

    private VoidMerchantPackets() {}

    /** Un article : son numéro chez le marchand, objet, prix en améthyste, restant (−1 = illimité), description (clé, vide = infobulle de l'objet). */
    public record Entry(int index, ItemStack stack, int price, int left, String desc) {}

    public static class Open {
        public final List<Entry> entries;
        public final int amethyst;

        public Open(List<Entry> entries, int amethyst) { this.entries = entries; this.amethyst = amethyst; }

        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(entries.size());
            for (Entry e : entries) {
                b.writeVarInt(e.index());
                b.writeItem(e.stack());
                b.writeVarInt(e.price());
                b.writeVarInt(e.left() + 1);
                b.writeUtf(e.desc() == null ? "" : e.desc(), 128);
            }
            b.writeVarInt(amethyst);
        }

        public static Open decode(FriendlyByteBuf b) {
            int n = Math.min(64, b.readVarInt());
            List<Entry> l = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int idx = b.readVarInt();
                ItemStack st = b.readItem();
                int price = b.readVarInt(), left = b.readVarInt() - 1;
                l.add(new Entry(idx, st, price, left, b.readUtf(128)));
            }
            return new Open(l, b.readVarInt());
        }

        public static void handle(Open pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.wavesurvivor.client.VoidMerchantScreen.receive(pkt.entries, pkt.amethyst)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Buy {
        public final int index;

        public Buy(int index) { this.index = index; }

        public void encode(FriendlyByteBuf b) { b.writeVarInt(index); }

        public static Buy decode(FriendlyByteBuf b) { return new Buy(b.readVarInt()); }

        public static void handle(Buy pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) com.wavesurvivor.horde.rift.VoidMerchant.buy(p, pkt.index);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
