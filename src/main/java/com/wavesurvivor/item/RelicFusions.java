package com.wavesurvivor.item;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * FUSIONS DE RELIQUES (1.5) — deux reliques précises d'une même famille, niveau II minimum, + 50 PR → une relique
 * légendaire. Une légendaire agit comme ses deux reliques d'origine au niveau II (elle compte donc pour 2 pièces de
 * set) + un effet unique (géré dans RelicEffects). Code commun client / serveur.
 */
public final class RelicFusions {

    private RelicFusions() {}

    public static final int FUSE_COST = 50;
    public static final int MIN_LEVEL = 2;
    /** Niveau auquel une légendaire fait agir ses reliques d'origine. */
    public static final int PARENT_LEVEL = 2;

    /** Une recette : identifiant, les deux reliques à fusionner, la légendaire obtenue. */
    public record Fusion(String id, Supplier<Item> a, Supplier<Item> b, Supplier<Item> result) {}

    private static List<Fusion> ALL;

    /** Les 12 recettes (2 par famille). */
    public static List<Fusion> all() {
        if (ALL == null) {
            ALL = List.of(
                    new Fusion("cle_voute", ModItems.SCEAU_GARDIEN, ModItems.PIERRE_FONDATION, ModItems.CLE_VOUTE),
                    new Fusion("etendard_bastion", ModItems.SCEAU_GARDIEN, ModItems.BANNIERE_RALLIEMENT, ModItems.ETENDARD_BASTION),
                    new Fusion("trophee_veneur", ModItems.MARQUE_CHASSEUR, ModItems.CROC_ALPHA, ModItems.TROPHEE_VENEUR),
                    new Fusion("calice_sang", ModItems.OS_SACRE, ModItems.COEUR_ASSOIFFE, ModItems.CALICE_SANG),
                    new Fusion("oeil_faille", ModItems.ECLAT_BRECHE, ModItems.PRISME_CATALYSEUR, ModItems.OEIL_FAILLE),
                    new Fusion("cle_portes", ModItems.PRISME_CATALYSEUR, ModItems.FRAGMENT_PORTE, ModItems.CLE_PORTES),
                    new Fusion("egide_fleaux", ModItems.AMULETTE_BRASIER, ModItems.CHARME_PHYLACTERE, ModItems.EGIDE_FLEAUX),
                    new Fusion("coeur_saisons", ModItems.AMULETTE_BRASIER, ModItems.CRISTAL_GIVRE, ModItems.COEUR_SAISONS),
                    new Fusion("ancre_colosse", ModItems.ANNEAU_ANCRAGE, ModItems.CEINTURE_PLOMB, ModItems.ANCRE_COLOSSE),
                    new Fusion("regard_veilleur", ModItems.OEIL_VEILLEUR, ModItems.TALISMAN_SOUFFLE, ModItems.REGARD_VEILLEUR),
                    new Fusion("sceau_marchand_roi", ModItems.LANGUE_ARGENT, ModItems.BOURSE_COLPORTEUR, ModItems.SCEAU_MARCHAND_ROI),
                    new Fusion("trousseau_pilleur", ModItems.CLE_GARDIEN, ModItems.ANNEAU_PROSPECTEUR, ModItems.TROUSSEAU_PILLEUR));
        }
        return ALL;
    }

    public static Fusion byId(String id) {
        for (Fusion f : all()) if (f.id().equals(id)) return f;
        return null;
    }

    /** Recette dont cet objet est le résultat (légendaire), ou null. */
    public static Fusion ofResult(Item item) {
        for (Fusion f : all()) if (f.result().get() == item) return f;
        return null;
    }

    public static boolean isLegendary(Item item) {
        return item instanceof LegendaryRelicItem;
    }

    /** Emplacement (inventaire principal) d'une copie de {@code item} au niveau {@code min} ou plus, en évitant {@code skip} ; -1 sinon. */
    public static int findSlot(List<ItemStack> inv, Item item, int min, int skip) {
        int best = -1, bestLvl = 99;
        for (int i = 0; i < Math.min(36, inv.size()); i++) {
            if (i == skip) continue;
            ItemStack st = inv.get(i);
            if (!st.is(item)) continue;
            int l = RelicEffects.levelOf(st);
            if (l >= min && l < bestLvl) { best = i; bestLvl = l; } // on consomme la plus petite copie suffisante
        }
        return best;
    }

    /** La fusion est-elle possible avec cet inventaire (niveaux II min) ? Les PR sont vérifiés à part. */
    public static boolean canFuse(List<ItemStack> inv, Fusion f) {
        int a = findSlot(inv, f.a().get(), MIN_LEVEL, -1);
        return a >= 0 && findSlot(inv, f.b().get(), MIN_LEVEL, a) >= 0;
    }

    /** Fusion côté serveur : revalide (reliques niveau II, PR), consomme, donne la légendaire. */
    public static void fuse(ServerPlayer p, String id) {
        Fusion f = byId(id);
        if (f == null) return;
        var inv = p.getInventory().items;
        int a = findSlot(inv, f.a().get(), MIN_LEVEL, -1);
        int b = a < 0 ? -1 : findSlot(inv, f.b().get(), MIN_LEVEL, a);
        if (a < 0 || b < 0) {
            p.sendSystemMessage(Component.literal(WSLang.t("forge.fuse_missing")));
            return;
        }
        int pts = com.wavesurvivor.horde.renaissance.RenaissanceStore.getPoints(p);
        if (pts < FUSE_COST) {
            p.sendSystemMessage(Component.literal(WSLang.t("srv.pas_assez_de_pr") + FUSE_COST + WSLang.t("srv.requis_tu_en_as") + pts + "."));
            return;
        }
        com.wavesurvivor.horde.renaissance.RenaissanceStore.addPoints(p, -FUSE_COST);
        com.wavesurvivor.horde.renaissance.Heritage.addInvested(p, FUSE_COST); // compte pour la Renaissance
        inv.get(a).shrink(1);
        inv.get(b).shrink(1);
        ItemStack out = new ItemStack(f.result().get());
        if (!p.getInventory().add(out)) p.drop(out, false);
        p.getInventory().setChanged();
        p.inventoryMenu.broadcastChanges();
        p.sendSystemMessage(Component.literal(WSLang.t("forge.fused", out.getHoverName().getString(), FUSE_COST,
                com.wavesurvivor.horde.renaissance.RenaissanceStore.getPoints(p))));
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 80, 0.6, 1, 0.6, 0.4);
            sl.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 80, 0.8, 0.8, 0.8, 0.8);
            sl.playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5f, 1.6f);
            sl.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }
}
