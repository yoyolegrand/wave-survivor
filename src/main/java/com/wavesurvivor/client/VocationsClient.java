package com.wavesurvivor.client;

import com.wavesurvivor.horde.kingdom.KingdomRoles;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** VOCATIONS — copie côté client de la Maîtrise et des talents du joueur (reçue du serveur). */
@OnlyIn(Dist.CLIENT)
public final class VocationsClient {

    private VocationsClient() {}

    private static CompoundTag data = new CompoundTag();
    private static boolean hasConfig = false;

    public static void apply(String config, CompoundTag d) {
        if (config != null && !config.isEmpty()) {
            VocationsConfig.loadFromJson(config);
            hasConfig = true;
        }
        if (d != null) data = d;
    }

    /** Vrai une fois la configuration des arbres reçue. */
    public static boolean ready() { return hasConfig; }

    private static CompoundTag role(KingdomRoles.Role r) { return data.getCompound("roles").getCompound(r.id); }

    public static int pr() { return data.getInt("pr"); }

    public static boolean inGame() { return data.getBoolean("inGame"); }

    public static long xp(KingdomRoles.Role r) { return role(r).getLong("xp"); }

    public static int level(KingdomRoles.Role r) { return VocationsConfig.levelForXp(xp(r)); }

    public static long xpIntoLevel(KingdomRoles.Role r) { return xp(r) - VocationsConfig.xpToReach(level(r)); }

    /** XP à gagner pour passer au niveau suivant (0 au niveau maximum). */
    public static int xpForNext(KingdomRoles.Role r) {
        int l = level(r);
        return l >= VocationsConfig.maxLevel() ? 0 : VocationsConfig.xpForLevel(l + 1);
    }

    /** Avancement dans le niveau en cours, de 0 à 1. */
    public static float progress(KingdomRoles.Role r) {
        int n = xpForNext(r);
        return n <= 0 ? 1f : Math.min(1f, xpIntoLevel(r) / (float) n);
    }

    public static int pending(KingdomRoles.Role r) { return role(r).getInt("pending"); }

    public static int rank(KingdomRoles.Role r, String talentId) { return role(r).getCompound("t").getInt(talentId); }

    public static String ultimate(KingdomRoles.Role r) { return role(r).getString("ult"); }

    public static boolean ultimatePaid(KingdomRoles.Role r) { return role(r).getBoolean("paid"); }

    public static int pointsTotal(KingdomRoles.Role r) { return level(r) * VocationsConfig.pointsPerLevel(); }

    public static int pointsSpent(KingdomRoles.Role r) {
        int n = 0;
        CompoundTag t = role(r).getCompound("t");
        for (String k : t.getAllKeys()) n += t.getInt(k);
        return n;
    }

    public static int pointsFree(KingdomRoles.Role r) { return Math.max(0, pointsTotal(r) - pointsSpent(r)); }

    /** Points dépensés dans une branche. */
    public static int branchPoints(KingdomRoles.Role r, VocationsConfig.Branch b) {
        int n = 0;
        for (VocationsConfig.Talent t : b.talents()) n += rank(r, t.id());
        return n;
    }

    public static boolean me(String uuid) {
        var p = Minecraft.getInstance().player;
        return p != null && p.getUUID().toString().equals(uuid);
    }
}
