package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur → client : clic droit sur l'autel pendant une pause « Prêt » → ouvre la demande « Passer la pause ? » (la réponse part par {@link ReadyPacket}). */
public class ReadyPromptPacket {

    public final boolean ready;
    public final int n, total;
    public final String bonus;

    public ReadyPromptPacket(boolean ready, int n, int total, String bonus) {
        this.ready = ready;
        this.n = n;
        this.total = total;
        this.bonus = bonus;
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(ready);
        b.writeVarInt(n);
        b.writeVarInt(total);
        b.writeUtf(bonus);
    }

    public static ReadyPromptPacket decode(FriendlyByteBuf b) {
        return new ReadyPromptPacket(b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readUtf());
    }

    public static void handle(ReadyPromptPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.openReadyPrompt(pkt)));
        ctx.get().setPacketHandled(true);
    }
}
