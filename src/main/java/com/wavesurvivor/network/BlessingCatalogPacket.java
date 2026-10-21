package com.wavesurvivor.network;

import com.wavesurvivor.horde.benediction.BlessingCatalog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Catalogue des bénédictions (1.8), serveur → client : le serveur envoie ses catégories effectives (base du mod + couche du joueur) à la connexion
 * et à chaque rechargement, pour que l'écran de choix et le catalogue affichent les mêmes bénédictions que celles qui s'appliquent.
 */
public class BlessingCatalogPacket {

    private final String json;

    public BlessingCatalogPacket(String json) { this.json = json; }

    public void encode(FriendlyByteBuf buf) { buf.writeUtf(json, 1 << 20); }

    public static BlessingCatalogPacket decode(FriendlyByteBuf buf) { return new BlessingCatalogPacket(buf.readUtf(1 << 20)); }

    public static void handle(BlessingCatalogPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> BlessingCatalog.loadFromJson(pkt.json));
        ctx.get().setPacketHandled(true);
    }
}
