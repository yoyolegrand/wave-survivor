package com.wavesurvivor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * FIL D'ÉVÉNEMENTS (HUD) : petites notifications à gauche de l'écran au lieu de lignes dans le chat
 * (événements du chaos, arbres, gisements, spécialisations…). Une icône d'objet, une couleur, un texte.
 */
public class EventFeedPacket {

    /** Couleurs par catégorie. */
    public static final int CHAOS = 0xFFFF9A3C, RESOURCE = 0xFF6FE08A, ORE = 0xFFF0D860, ARCANE = 0xFFC99BFF,
            DANGER = 0xFFFF6A6A, KINGDOM = 0xFF6FB7FF;

    public final Component text;
    public final String icon;
    public final int color;
    public final boolean important;

    public EventFeedPacket(Component text, String icon, int color, boolean important) {
        this.text = text;
        this.icon = icon == null ? "" : icon;
        this.color = color;
        this.important = important;
    }

    public void encode(FriendlyByteBuf b) {
        b.writeComponent(text);
        b.writeUtf(icon, 128);
        b.writeInt(color);
        b.writeBoolean(important);
    }

    public static EventFeedPacket decode(FriendlyByteBuf b) {
        return new EventFeedPacket(b.readComponent(), b.readUtf(128), b.readInt(), b.readBoolean());
    }

    public static void handle(EventFeedPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.wavesurvivor.client.EventFeedOverlay.push(pkt)));
        ctx.get().setPacketHandled(true);
    }

    // ─── Envoi (côté serveur) ───

    public static void toAll(MinecraftServer server, Component text, String icon, int color, boolean important) {
        if (server == null || NetworkHandler.CHANNEL == null) return;
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new EventFeedPacket(text, icon, color, important));
    }

    public static void toPlayer(ServerPlayer p, Component text, String icon, int color, boolean important) {
        if (p == null || NetworkHandler.CHANNEL == null) return;
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new EventFeedPacket(text, icon, color, important));
    }
}
