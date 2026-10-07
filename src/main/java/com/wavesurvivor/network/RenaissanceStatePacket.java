package com.wavesurvivor.network;

import com.wavesurvivor.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Packet S→C : état Renaissance du joueur (PR, achats, config).
 * - open=true : ouvre l'écran s'il n'est pas déjà ouvert
 * - open=false : met juste à jour l'écran s'il est ouvert
 * - resetSelection : vide la sélection de sacrifice (après un sacrifice réussi)
 * - purchases : achats de reliques (id → nb) + niveaux de stats ("stat:<id>" → niveau)
 */
public class RenaissanceStatePacket {

    private static final int MAX_JSON = 262144;

    public final boolean open;
    public final int tab;
    public final boolean resetSelection;
    public final int points;
    public final Map<String, Integer> purchases;
    public final boolean skillTreeAvailable;
    public final boolean statsAvailable;
    public final boolean editMode;
    public final String configJson;

    public RenaissanceStatePacket(boolean open, int tab, boolean resetSelection, int points,
                                  Map<String, Integer> purchases, boolean skillTreeAvailable,
                                  boolean statsAvailable, boolean editMode, String configJson) {
        this.open = open;
        this.tab = tab;
        this.resetSelection = resetSelection;
        this.points = points;
        this.purchases = purchases != null ? purchases : new HashMap<>();
        this.skillTreeAvailable = skillTreeAvailable;
        this.statsAvailable = statsAvailable;
        this.editMode = editMode;
        this.configJson = configJson != null ? configJson : "{}";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeVarInt(tab);
        buf.writeBoolean(resetSelection);
        buf.writeVarInt(points);
        buf.writeVarInt(purchases.size());
        for (Map.Entry<String, Integer> e : purchases.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }
        buf.writeBoolean(skillTreeAvailable);
        buf.writeBoolean(statsAvailable);
        buf.writeBoolean(editMode);
        buf.writeUtf(configJson, MAX_JSON);
    }

    public static RenaissanceStatePacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        int tab = buf.readVarInt();
        boolean reset = buf.readBoolean();
        int points = buf.readVarInt();
        int n = buf.readVarInt();
        Map<String, Integer> pur = new HashMap<>();
        for (int i = 0; i < n; i++) pur.put(buf.readUtf(), buf.readVarInt());
        boolean skill = buf.readBoolean();
        boolean stats = buf.readBoolean();
        boolean edit = buf.readBoolean();
        String json = buf.readUtf(MAX_JSON);
        return new RenaissanceStatePacket(open, tab, reset, points, pur, skill, stats, edit, json);
    }

    public static void handle(RenaissanceStatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleRenaissanceState(pkt)));
        ctx.get().setPacketHandled(true);
    }
}
