package com.wavesurvivor.network;

import com.wavesurvivor.horde.kingdom.KingdomRoleState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S→C : réactifs (Alchimiste), présages (Augure), contrat en cours (Chasseur de primes). */
public class RoleStatePacket {

    public final int reagents;
    public final String options, chosen, active;
    /** Contrat : type (« elites », « siege », « kills », vide = aucun), avancement, objectif, prime en monnaie. */
    public final String contract;
    public final int progress, target, reward;
    /** Niveaux des fioles de l'Alchimiste. */
    public final int[] flaskLevels;

    public RoleStatePacket(int reagents, String options, String chosen, String active,
                           String contract, int progress, int target, int reward, int[] flaskLevels) {
        this.reagents = reagents;
        this.options = options;
        this.chosen = chosen;
        this.active = active;
        this.contract = contract;
        this.progress = progress;
        this.target = target;
        this.reward = reward;
        this.flaskLevels = flaskLevels;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(reagents);
        buf.writeUtf(options);
        buf.writeUtf(chosen);
        buf.writeUtf(active);
        buf.writeUtf(contract);
        buf.writeVarInt(progress);
        buf.writeVarInt(target);
        buf.writeVarInt(reward);
        buf.writeVarIntArray(flaskLevels);
    }

    public static RoleStatePacket decode(FriendlyByteBuf buf) {
        return new RoleStatePacket(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarIntArray());
    }

    public static void handle(RoleStatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            KingdomRoleState.clientReagents = pkt.reagents;
            KingdomRoleState.clientOmenOptions = pkt.options.isEmpty() ? new String[0] : pkt.options.split(",");
            KingdomRoleState.clientOmenChosen = pkt.chosen;
            KingdomRoleState.clientOmenActive = pkt.active;
            KingdomRoleState.clientContract = pkt.contract;
            KingdomRoleState.clientContractProgress = pkt.progress;
            KingdomRoleState.clientContractTarget = pkt.target;
            KingdomRoleState.clientContractReward = pkt.reward;
            KingdomRoleState.clientFlaskLevels = pkt.flaskLevels;
        });
        ctx.get().setPacketHandled(true);
    }
}
