package com.wavesurvivor.network;

import com.wavesurvivor.horde.kingdom.KingdomRoles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S→C : rôles du royaume (actifs, phase de Calme, rôles proposés, qui tient quel rôle). */
public class KingdomRolesPacket {

    public final boolean on, calm;
    public final List<String> available;
    /** [id du rôle, UUID du joueur, nom du joueur]. */
    public final List<String[]> holders;

    public KingdomRolesPacket(boolean on, boolean calm, List<String> available, List<String[]> holders) {
        this.on = on;
        this.calm = calm;
        this.available = available;
        this.holders = holders;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(on);
        buf.writeBoolean(calm);
        buf.writeVarInt(available.size());
        for (String s : available) buf.writeUtf(s);
        buf.writeVarInt(holders.size());
        for (String[] h : holders) { buf.writeUtf(h[0]); buf.writeUtf(h[1]); buf.writeUtf(h[2]); }
    }

    public static KingdomRolesPacket decode(FriendlyByteBuf buf) {
        boolean on = buf.readBoolean(), calm = buf.readBoolean();
        int n = buf.readVarInt();
        List<String> av = new ArrayList<>(n);
        for (int i = 0; i < n; i++) av.add(buf.readUtf());
        int m = buf.readVarInt();
        List<String[]> hs = new ArrayList<>(m);
        for (int i = 0; i < m; i++) hs.add(new String[]{buf.readUtf(), buf.readUtf(), buf.readUtf()});
        return new KingdomRolesPacket(on, calm, av, hs);
    }

    public static void handle(KingdomRolesPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            KingdomRoles.clientOn = pkt.on;
            KingdomRoles.clientCalm = pkt.calm;
            KingdomRoles.CLIENT_AVAILABLE.clear();
            for (String s : pkt.available) {
                KingdomRoles.Role r = KingdomRoles.Role.byId(s);
                if (r != null) KingdomRoles.CLIENT_AVAILABLE.add(r);
            }
            KingdomRoles.CLIENT_HOLDERS.clear();
            for (String[] h : pkt.holders) {
                KingdomRoles.Role r = KingdomRoles.Role.byId(h[0]);
                if (r != null) KingdomRoles.CLIENT_HOLDERS.put(r, new String[]{h[1], h[2]});
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
