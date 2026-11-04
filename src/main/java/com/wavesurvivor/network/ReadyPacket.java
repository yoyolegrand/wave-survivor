package com.wavesurvivor.network;

import com.wavesurvivor.horde.ready.ReadyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → serveur : le joueur appuie sur la touche « Prêt » pendant une pause. */
public class ReadyPacket {

    public ReadyPacket() {}

    public void encode(FriendlyByteBuf b) {}

    public static ReadyPacket decode(FriendlyByteBuf b) { return new ReadyPacket(); }

    public static void handle(ReadyPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && p.getServer() != null) ReadyManager.toggle(p, p.getServer().getTickCount());
        });
        ctx.get().setPacketHandled(true);
    }
}
