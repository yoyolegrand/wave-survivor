package com.wavesurvivor.network;

import com.wavesurvivor.horde.renaissance.RenaissanceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet C→S : le joueur sacrifie une sélection d'items de son inventaire principal.
 * Chaque entrée = (slot 0..35, quantité, itemId attendu). Le serveur revalide TOUT.
 */
public class RenaissanceSacrificePacket {

    public record Entry(int slot, int count, String itemId) {}

    private static final int MAX_ENTRIES = 36;

    public final List<Entry> entries;

    public RenaissanceSacrificePacket(List<Entry> entries) {
        this.entries = entries;
    }

    public void encode(FriendlyByteBuf buf) {
        int n = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            buf.writeVarInt(e.slot());
            buf.writeVarInt(e.count());
            buf.writeUtf(e.itemId());
        }
    }

    public static RenaissanceSacrificePacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readUtf()));
        return new RenaissanceSacrificePacket(list);
    }

    public static void handle(RenaissanceSacrificePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) RenaissanceManager.sacrifice(sender, pkt.entries);
        });
        ctx.get().setPacketHandled(true);
    }
}
