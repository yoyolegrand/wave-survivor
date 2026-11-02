package com.wavesurvivor.horde.kingdom.vocation;

import com.wavesurvivor.horde.kingdom.KingdomRoles;
import com.wavesurvivor.horde.renaissance.RenaissanceStore;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.VocationSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** VOCATIONS — envoie au client la Maîtrise, les talents et la configuration (arbres) d'un joueur. */
public final class VocationSync {

    private VocationSync() {}

    /** Photographie des données du joueur : PR, partie en cours, et pour chaque rôle XP, talents, ultime et XP en réserve. */
    public static CompoundTag snapshot(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.putInt("pr", RenaissanceStore.getPoints(p));
        t.putBoolean("inGame", Vocations.inGame());
        CompoundTag roles = new CompoundTag();
        for (KingdomRoles.Role r : KingdomRoles.Role.values()) {
            CompoundTag src = Vocations.root(p).getCompound(r.id);
            CompoundTag o = new CompoundTag();
            o.putLong("xp", src.getLong("xp"));
            o.putString("ult", src.getString("ult"));
            o.putBoolean("paid", src.getBoolean("paid"));
            o.put("t", src.getCompound("t").copy());
            o.putInt("pending", VocationXp.pending(p.getUUID(), r));
            roles.put(r.id, o);
        }
        t.put("roles", roles);
        return t;
    }

    /** @param withConfig joindre la configuration des arbres (à l'ouverture d'un écran ; inutile après un simple achat). */
    public static void send(ServerPlayer p, boolean withConfig) {
        if (NetworkHandler.CHANNEL == null || p == null) return;
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new VocationSyncPacket(withConfig ? VocationsConfig.json() : "", snapshot(p)));
    }
}
