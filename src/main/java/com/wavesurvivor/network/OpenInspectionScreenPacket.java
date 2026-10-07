package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet S->C : demande au client d'ouvrir le WaveInspectionScreen.
 *
 * Envoyé quand un joueur exécute /ws info. Payload vide — le client sait déjà quelle
 * config afficher grâce à ClientConfigCache déjà rempli par SyncConfigPacket.
 */
public class OpenInspectionScreenPacket {

    public OpenInspectionScreenPacket() {}

    public void encode(FriendlyByteBuf buf) {
        // payload vide
    }

    public static OpenInspectionScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenInspectionScreenPacket();
    }

    public static void handle(OpenInspectionScreenPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openInspectionScreen());
        });
        ctx.get().setPacketHandled(true);
    }
}
