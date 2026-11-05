package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S→C : résumé de plusieurs ouvertures d'un coup (une clé par ouverture) : tous les gains regroupés, le meilleur mis en avant. */
public class RouletteSummaryPacket {

    public record Entry(ItemStack icon, String name, int qty, int rarity) {}

    public final String chestName;
    public final int opens;
    public final List<Entry> entries;

    public RouletteSummaryPacket(String chestName, int opens, List<Entry> entries) {
        this.chestName = chestName;
        this.opens = opens;
        this.entries = entries;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(chestName);
        buf.writeVarInt(opens);
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeItem(e.icon());
            buf.writeUtf(e.name());
            buf.writeVarInt(e.qty());
            buf.writeVarInt(e.rarity());
        }
    }

    public static RouletteSummaryPacket decode(FriendlyByteBuf buf) {
        String chest = buf.readUtf();
        int opens = buf.readVarInt();
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(new Entry(buf.readItem(), buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
        return new RouletteSummaryPacket(chest, opens, list);
    }

    public static void handle(RouletteSummaryPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.wavesurvivor.client.RouletteSummaryScreen.open(pkt)));
        ctx.get().setPacketHandled(true);
    }
}
