package com.wavesurvivor.horde.roulette;

import com.wavesurvivor.config.RulesConfig;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * CLÉ SCELLÉE (1.9) : clé spéciale qui n'ouvre que les reliquaires scellés des ruines (consommée à l'ouverture).
 * C'est une clé de coffre roulette composée (tête ailée, panneton à crochet, or, gemme blanche) : elle se range donc dans la Bourse à clés.
 * Obtention : butin rare des boss de horde ({@code sealedKeyBossChance} % dans rules.json) et récompense d'une horde terminée sans aucune mort
 * ({@code sealedKeyFlawlessCount} clés, 0 = désactivé).
 */
public final class SealedKey {

    public static final String NAME = "Clé scellée", CHEST = "Reliquaire scellé", TAG = "sealedKey";
    private static final Random RNG = new Random();
    /** Morts de joueurs depuis le début de la horde en cours (remis à zéro au lancement). */
    private static int deaths = 0;

    private SealedKey() {}

    public static ItemStack create(int count) {
        ItemStack s = new ItemStack(ModItems.ROULETTE_KEY.get(), Math.max(1, count));
        CompoundTag t = s.getOrCreateTag();
        t.putInt("rouletteKey", 1);
        t.putString("rouletteKeyName", NAME);
        t.putString("rouletteChestName", CHEST);
        t.putString("rouletteKeyHead", "winged");
        t.putString("rouletteKeyBit", "hook");
        t.putString("rouletteKeyMaterial", "or");
        t.putInt("rouletteKeyColor", 0xFFFFFF);
        t.putBoolean(TAG, true);
        return s;
    }

    public static boolean is(ItemStack s) {
        return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean(TAG);
    }

    // ─── Obtention ───

    public static void resetDeaths() { deaths = 0; }
    public static void onPlayerDeath() { deaths++; }

    /** Un boss de horde vient de mourir : petite chance de lâcher une Clé scellée. */
    public static void onBossKilled(Entity boss) {
        double chance = RulesConfig.sealedKeyBossChance();
        if (chance <= 0 || boss.level().isClientSide || RNG.nextDouble() * 100.0 >= chance) return;
        ItemEntity drop = new ItemEntity(boss.level(), boss.getX(), boss.getY() + 0.5, boss.getZ(), create(1));
        drop.setDefaultPickUpDelay();
        boss.level().addFreshEntity(drop);
    }

    /** Horde terminée : si personne n'est mort, chaque joueur présent reçoit des Clés scellées. */
    public static void onHordeWon(MinecraftServer srv) {
        int n = RulesConfig.sealedKeyFlawlessCount();
        if (n <= 0 || deaths > 0) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            ItemStack k = create(n);
            if (!p.getInventory().add(k)) p.drop(k, false);
            p.sendSystemMessage(Component.literal(WSLang.t("sealed_key.flawless", n)));
        }
    }
}
