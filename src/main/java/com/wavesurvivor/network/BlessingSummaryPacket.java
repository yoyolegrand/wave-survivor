package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Packet S->C (1.8) : bénédictions du joueur (identifiant → niveau) pour afficher ses bonus cumulés (/ws benediction mine). */
public class BlessingSummaryPacket {

    private final Map<String, Integer> owned;
    private final float mult;

    public BlessingSummaryPacket(Map<String, Integer> owned, float mult) {
        this.owned = owned;
        this.mult = mult;
    }

    public static void writeMap(FriendlyByteBuf buf, Map<String, Integer> m) {
        buf.writeVarInt(m.size());
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            buf.writeUtf(e.getKey(), 128);
            buf.writeVarInt(e.getValue());
        }
    }

    public static Map<String, Integer> readMap(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) m.put(buf.readUtf(128), buf.readVarInt());
        return m;
    }

    public void encode(FriendlyByteBuf buf) {
        writeMap(buf, owned);
        buf.writeFloat(mult);
    }

    public static BlessingSummaryPacket decode(FriendlyByteBuf buf) {
        Map<String, Integer> m = readMap(buf);
        return new BlessingSummaryPacket(m, buf.readFloat());
    }

    public static void handle(BlessingSummaryPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.openBlessingSummary(pkt.owned, pkt.mult)));
        ctx.get().setPacketHandled(true);
    }
}
