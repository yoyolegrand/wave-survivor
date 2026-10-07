package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet S->C : demande au client d'ouvrir le BenedictionSelectionScreen.
 * Payload : liste d'IDs de bénédictions (3 en général) + wave actuelle (pour anti-farm client-side).
 */
public class OpenBenedictionScreenPacket {

    private final List<String> ids;
    private final int currentWave;

    public OpenBenedictionScreenPacket(List<String> ids, int currentWave) {
        this.ids = ids;
        this.currentWave = currentWave;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(ids.size());
        for (String id : ids) {
            buf.writeUtf(id, 128);
        }
        buf.writeInt(currentWave);
    }

    public static OpenBenedictionScreenPacket decode(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(buf.readUtf(128));
        int wave = buf.readInt();
        return new OpenBenedictionScreenPacket(ids, wave);
    }

    public static void handle(OpenBenedictionScreenPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientPacketHandler.openBenedictionScreen(pkt.ids, pkt.currentWave));
        });
        ctx.get().setPacketHandled(true);
    }
}
