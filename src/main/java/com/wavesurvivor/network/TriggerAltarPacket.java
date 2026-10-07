package com.wavesurvivor.network;

import com.wavesurvivor.altar.AltarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet C→S : le joueur a cliqué "Lancer la Horde" dans l'AltarConfirmScreen, avec la VARIANTE choisie.
 * Le serveur revalide (variante de l'autel, mods installés) puis lance.
 */
public class TriggerAltarPacket {

    public final BlockPos altarPos;
    public final String variantHorde;
    /** Mutateurs cochés (« id1,id2,… », vide = aucun). */
    public final String mutators;
    /** Niveau de difficulté choisi (« easy », « normal »… ; vide = niveau par défaut de la horde). */
    public final String difficulty;
    /** Mode Kingdom : rôle choisi par celui qui lance (vide = aucun). */
    public final String role;

    public TriggerAltarPacket(BlockPos pos, String variantHorde) {
        this(pos, variantHorde, "", "");
    }

    public TriggerAltarPacket(BlockPos pos, String variantHorde, String mutators) {
        this(pos, variantHorde, mutators, "");
    }

    public TriggerAltarPacket(BlockPos pos, String variantHorde, String mutators, String difficulty) {
        this(pos, variantHorde, mutators, difficulty, "");
    }

    public TriggerAltarPacket(BlockPos pos, String variantHorde, String mutators, String difficulty, String role) {
        this.altarPos = pos;
        this.variantHorde = variantHorde != null ? variantHorde : "";
        this.mutators = mutators != null ? mutators : "";
        this.difficulty = difficulty != null ? difficulty : "";
        this.role = role != null ? role : "";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(altarPos);
        buf.writeUtf(variantHorde);
        buf.writeUtf(mutators);
        buf.writeUtf(difficulty);
        buf.writeUtf(role);
    }

    public static TriggerAltarPacket decode(FriendlyByteBuf buf) {
        return new TriggerAltarPacket(buf.readBlockPos(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(TriggerAltarPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (!(sender.level() instanceof ServerLevel level)) return;
            AltarManager.confirmTrigger(sender, level, pkt.altarPos, pkt.variantHorde, pkt.mutators, pkt.difficulty, pkt.role);
        });
        ctx.get().setPacketHandled(true);
    }
}
