package com.wavesurvivor.network;

import com.wavesurvivor.horde.kingdom.KingdomRoles;
import com.wavesurvivor.horde.kingdom.vocation.VocationSync;
import com.wavesurvivor.horde.kingdom.vocation.Vocations;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C→S : action sur les Vocations. 0 = demander la synchronisation, 1 = talent +1 rang, 2 = choisir l'ultime, 3 = réinitialiser. Tout est revalidé côté serveur. */
public class VocationActionPacket {

    public static final int SYNC = 0, BUY = 1, ULTIMATE = 2, RESET = 3;

    public final int action;
    public final String role, id;

    public VocationActionPacket(int action, String role, String id) {
        this.action = action;
        this.role = role;
        this.id = id;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUtf(role, 32);
        buf.writeUtf(id, 64);
    }

    public static VocationActionPacket decode(FriendlyByteBuf buf) {
        return new VocationActionPacket(buf.readVarInt(), buf.readUtf(32), buf.readUtf(64));
    }

    public static void handle(VocationActionPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            KingdomRoles.Role r = KingdomRoles.Role.byId(pkt.role);
            if (pkt.action != SYNC && r != null) {
                Vocations.Result res = switch (pkt.action) {
                    case BUY -> Vocations.buyTalent(p, r, pkt.id);
                    case ULTIMATE -> Vocations.chooseUltimate(p, r, pkt.id);
                    case RESET -> Vocations.reset(p, r);
                    default -> Vocations.Result.UNKNOWN;
                };
                if (res != Vocations.Result.OK) p.displayClientMessage(WSLang.c("voc.res." + res.name().toLowerCase()), true);
            }
            VocationSync.send(p, pkt.action == SYNC);
        });
        ctx.get().setPacketHandled(true);
    }
}
