package com.wavesurvivor.network;

import com.wavesurvivor.item.RelicEquip;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S→C : reliques équipées du joueur (mode sans Curios) — infobulles des sets et onglet Équipement de l'autel. */
public class RelicEquipSyncPacket {

    public final List<ItemStack> stacks;

    public RelicEquipSyncPacket(List<ItemStack> stacks) {
        this.stacks = stacks;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(stacks.size());
        for (ItemStack st : stacks) buf.writeItem(st);
    }

    public static RelicEquipSyncPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(16, buf.readVarInt());
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(buf.readItem());
        return new RelicEquipSyncPacket(l);
    }

    public static void handle(RelicEquipSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> RelicEquip.CLIENT = new ArrayList<>(pkt.stacks));
        ctx.get().setPacketHandled(true);
    }
}
