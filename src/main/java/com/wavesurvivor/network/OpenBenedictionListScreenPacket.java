package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet S->C : demande au client d'ouvrir le BenedictionListScreen (catalogue de toutes les bénédictions).
 * Payload vide — le client utilise son propre RogueUpgradeRegistry.
 */
public class OpenBenedictionListScreenPacket {

    /** Multiplicateur de difficulté des bonus (1.7), pour afficher les valeurs réelles. */
    private final float mult;

    public OpenBenedictionListScreenPacket(float mult) { this.mult = mult; }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(mult);
    }

    public static OpenBenedictionListScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenBenedictionListScreenPacket(buf.readFloat());
    }

    public static void handle(OpenBenedictionListScreenPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientPacketHandler.openBenedictionListScreen(pkt.mult));
        });
        ctx.get().setPacketHandled(true);
    }
}
