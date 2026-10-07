package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S→C : aperçu du loot d'un coffre roulette (clic gauche en survie). */
public class RouletteLootPacket {

    public record Entry(ItemStack icon, String name, int minQty, int maxQty, double percent, int rarity) {}

    public final String chestName;
    public final String keyName;
    public final int bonusWave; // > 0 : chances ajustées par la vague en cours
    public final List<Entry> entries;

    public RouletteLootPacket(String chestName, String keyName, int bonusWave, List<Entry> entries) {
        this.chestName = chestName;
        this.keyName = keyName;
        this.bonusWave = bonusWave;
        this.entries = entries;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(chestName);
        buf.writeUtf(keyName);
        buf.writeVarInt(bonusWave);
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeItem(e.icon());
            buf.writeUtf(e.name());
            buf.writeVarInt(e.minQty());
            buf.writeVarInt(e.maxQty());
            buf.writeDouble(e.percent());
            buf.writeVarInt(e.rarity());
        }
    }

    public static RouletteLootPacket decode(FriendlyByteBuf buf) {
        String chest = buf.readUtf();
        String key = buf.readUtf();
        int wave = buf.readVarInt();
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readItem(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                    buf.readDouble(), buf.readVarInt()));
        }
        return new RouletteLootPacket(chest, key, wave, list);
    }

    public static void handle(RouletteLootPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openRouletteLoot(pkt)));
        ctx.get().setPacketHandled(true);
    }
}
