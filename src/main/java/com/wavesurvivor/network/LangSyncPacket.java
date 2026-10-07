package com.wavesurvivor.network;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S→C : langue du mod (en / fr), envoyée à la connexion et à chaque /ws lang. */
public class LangSyncPacket {

    public final String lang;

    public LangSyncPacket(String lang) {
        this.lang = lang;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(lang);
    }

    public static LangSyncPacket decode(FriendlyByteBuf buf) {
        return new LangSyncPacket(buf.readUtf());
    }

    public static void handle(LangSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> WSLang.set(pkt.lang));
        ctx.get().setPacketHandled(true);
    }
}
