package com.wavesurvivor.network;

import com.wavesurvivor.horde.benediction.RogueUpgradeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Actions du joueur sur son menu de bénédictions (1.8), client → serveur :
 *   REROLL : relancer les cartes (arg = famille voulue, "" = au hasard) ;
 *   BAN    : bannir une catégorie pour toute la partie (arg = identifiant de la catégorie de la carte).
 * Le serveur valide (menu en attente, relances / bannissements restants) et renvoie un nouveau menu.
 */
public class BenedictionActionPacket {

    public static final byte REROLL = 0, BAN = 1;

    private final byte type;
    private final String arg;

    public BenedictionActionPacket(byte type, String arg) {
        this.type = type;
        this.arg = arg == null ? "" : arg;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(type);
        buf.writeUtf(arg, 64);
    }

    public static BenedictionActionPacket decode(FriendlyByteBuf buf) {
        return new BenedictionActionPacket(buf.readByte(), buf.readUtf(64));
    }

    public static void handle(BenedictionActionPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            if (pkt.type == REROLL) RogueUpgradeManager.reroll(p, pkt.arg);
            else if (pkt.type == BAN) RogueUpgradeManager.ban(p, pkt.arg);
        });
        ctx.get().setPacketHandled(true);
    }
}
