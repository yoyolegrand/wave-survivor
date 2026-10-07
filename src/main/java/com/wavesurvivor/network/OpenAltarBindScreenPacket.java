package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet S→C : ouvre l'écran "Lier l'autel" avec la liste des hordes liables,
 * leurs ingrédients, et ce que le joueur possède (calculé serveur).
 */
public class OpenAltarBindScreenPacket {

    public record Ing(String item, int need, int have) {}

    /**
     * @param kingdom  horde en mode Kingdom (onglet « ♛ Kingdoms »)
     * @param unlocked conditions de déblocage remplies (ou créatif)
     * @param lock     conditions encodées « 1|texte » / « 0|texte » séparées par des retours à la ligne (vide = aucune)
     */
    public record Option(String hordeName, int totalWaves, int totalBosses, boolean canBind, List<Ing> ingredients,
                         boolean kingdom, boolean unlocked, String lock) {}

    public final BlockPos pos;
    public final List<Option> options;

    public OpenAltarBindScreenPacket(BlockPos pos, List<Option> options) {
        this.pos = pos;
        this.options = options;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(options.size());
        for (Option o : options) {
            buf.writeUtf(o.hordeName());
            buf.writeVarInt(o.totalWaves());
            buf.writeVarInt(o.totalBosses());
            buf.writeBoolean(o.canBind());
            buf.writeVarInt(o.ingredients().size());
            for (Ing i : o.ingredients()) {
                buf.writeUtf(i.item());
                buf.writeVarInt(i.need());
                buf.writeVarInt(i.have());
            }
            buf.writeBoolean(o.kingdom());
            buf.writeBoolean(o.unlocked());
            buf.writeUtf(o.lock() == null ? "" : o.lock(), 4096);
        }
    }

    public static OpenAltarBindScreenPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int n = buf.readVarInt();
        List<Option> opts = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            String name = buf.readUtf();
            int waves = buf.readVarInt();
            int bosses = buf.readVarInt();
            boolean can = buf.readBoolean();
            int m = buf.readVarInt();
            List<Ing> ings = new ArrayList<>();
            for (int j = 0; j < m; j++) ings.add(new Ing(buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
            boolean kingdom = buf.readBoolean();
            boolean unlocked = buf.readBoolean();
            String lock = buf.readUtf(4096);
            opts.add(new Option(name, waves, bosses, can, ings, kingdom, unlocked, lock));
        }
        return new OpenAltarBindScreenPacket(pos, opts);
    }

    public static void handle(OpenAltarBindScreenPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openAltarBindScreen(pkt)));
        ctx.get().setPacketHandled(true);
    }
}
