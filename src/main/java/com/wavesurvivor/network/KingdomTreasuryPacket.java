package com.wavesurvivor.network;

import com.wavesurvivor.horde.kingdom.KingdomTreasury;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S→C : trésor commun du royaume (actif ou non + Monnaie, Pierre, Bois, Fer, Essence). */
public class KingdomTreasuryPacket {

    public final boolean on;
    public final int[] amounts;

    public KingdomTreasuryPacket(boolean on, int[] amounts) {
        this.on = on;
        this.amounts = amounts;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(on);
        buf.writeVarInt(amounts.length);
        for (int a : amounts) buf.writeVarInt(a);
    }

    public static KingdomTreasuryPacket decode(FriendlyByteBuf buf) {
        boolean on = buf.readBoolean();
        int n = buf.readVarInt();
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = buf.readVarInt();
        return new KingdomTreasuryPacket(on, a);
    }

    public static void handle(KingdomTreasuryPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            KingdomTreasury.clientOn = pkt.on;
            for (int i = 0; i < Math.min(KingdomTreasury.N, pkt.amounts.length); i++) KingdomTreasury.CLIENT[i] = pkt.amounts[i];
        });
        ctx.get().setPacketHandled(true);
    }
}
