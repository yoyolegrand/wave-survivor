package com.wavesurvivor.horde.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * ATELIER DE RÉPARATION (Kingdom) — bâtiment 2×1, 1 bloc de haut.
 * Clic droit : emplacements où déposer armures, armes, outils, arcs, boucliers (tout objet à durabilité),
 * réparés lentement. Chaque objet est réservé au joueur qui l'a déposé (voir WorkshopMenu).
 *   Niveau 1 : 2 emplacements, 0,5 %/s · Niveau 2 : 4 emplacements, 1 %/s · Niveau 3 : 6 emplacements, 2 %/s
 * Spécialisations (niveau 3) :
 *   « forge »    Forge Ardente        — réparation ×2
 *   « armory »   Armurerie de Campagne — répare aussi l'équipement PORTÉ des joueurs à ≤ 8 blocs (0,5 %/s) — lot C
 *   « mechanic » Atelier Mécanique     — répare aussi les constructions à ≤ 10 blocs — lot C
 */
public final class KingdomWorkshop {

    private KingdomWorkshop() {}

    /** Fonderie : secondes de fonte écoulées pour l'objet du creuset, par atelier. */
    private static final java.util.Map<BlockPos, Integer> MELT = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Fond un objet en ressources du trésor commun. Quantité = taille de l'objet × état (durabilité restante, 25 % min)
     * × valeur du matériau : bois → Bois · pierre → Pierre · cuir → Bois · maille, fer → Fer · or, diamant, netherite → ◆.
     */
    private static void melt(ServerLevel level, BlockPos pos, ItemStack st) {
        net.minecraft.world.item.Item it = st.getItem();
        double units = 2;                                   // arcs, boucliers, divers
        String mat = "wood";
        if (it instanceof net.minecraft.world.item.ArmorItem a) {
            units = switch (a.getType()) { case HELMET -> 5; case CHESTPLATE -> 8; case LEGGINGS -> 7; default -> 4; };
            var m = a.getMaterial();
            mat = m == net.minecraft.world.item.ArmorMaterials.LEATHER ? "wood"
                    : m == net.minecraft.world.item.ArmorMaterials.CHAIN || m == net.minecraft.world.item.ArmorMaterials.IRON
                    || m == net.minecraft.world.item.ArmorMaterials.TURTLE ? "iron"
                    : m == net.minecraft.world.item.ArmorMaterials.GOLD ? "gold"
                    : m == net.minecraft.world.item.ArmorMaterials.DIAMOND ? "diamond"
                    : m == net.minecraft.world.item.ArmorMaterials.NETHERITE ? "netherite" : "iron";
        } else if (it instanceof net.minecraft.world.item.TieredItem ti) {
            units = it instanceof net.minecraft.world.item.SwordItem ? 2 : it instanceof net.minecraft.world.item.ShovelItem ? 1
                    : it instanceof net.minecraft.world.item.HoeItem ? 2 : 3;
            var t = ti.getTier();
            mat = t == net.minecraft.world.item.Tiers.WOOD ? "wood" : t == net.minecraft.world.item.Tiers.STONE ? "stone"
                    : t == net.minecraft.world.item.Tiers.IRON ? "iron" : t == net.minecraft.world.item.Tiers.GOLD ? "gold"
                    : t == net.minecraft.world.item.Tiers.DIAMOND ? "diamond" : t == net.minecraft.world.item.Tiers.NETHERITE ? "netherite" : "iron";
        }
        double cond = st.getMaxDamage() > 0 ? Math.max(0.25, 1.0 - (double) st.getDamageValue() / st.getMaxDamage()) : 1.0;
        double rate = switch (mat) { case "wood", "stone" -> 1.0; case "iron" -> 0.5; case "gold" -> 1.0; case "diamond" -> 3.0; default -> 5.0; };
        int amount = Math.max(1, (int) Math.round(units * cond * rate));
        KingdomTreasury.Res res = switch (mat) {
            case "wood" -> KingdomTreasury.Res.WOOD;
            case "stone" -> KingdomTreasury.Res.STONE;
            case "iron" -> KingdomTreasury.Res.IRON;
            default -> KingdomTreasury.Res.MONEY;                 // or, diamant, netherite : uniquement des ◆
        };
        if (KingdomTreasury.active()) KingdomTreasury.add(res, amount);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 20, 0.25, 0.2, 0.25, 0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.01);
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.2f);
        String resName = com.wavesurvivor.i18n.WSLang.t("workshop.res." + res.name().toLowerCase());
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= 24 * 24) {
                p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("workshop.melted", st.getHoverName().getString(), amount, resName), true);
            }
        }
    }

    /** Emplacements selon le niveau (1 → 2, 2 → 4, 3 → 6). */
    public static int slots(int level) {
        return Math.max(2, Math.min(6, level * 2));
    }

    /** Pourcentage de durabilité rendu par seconde. */
    public static double percentPerSecond(int level, String variant) {
        double base = level >= 3 ? 2.0 : level == 2 ? 1.0 : 0.5;
        return "forge".equals(variant) ? base * 2.5 : base;
    }

    /** Texte « 1 %/s » pour l'écran (virgule décimale à la française). */
    public static String speedText(int level, String variant) {
        double p = percentPerSecond(level, variant);
        return (p == Math.rint(p) ? String.valueOf((int) p) : String.valueOf(p).replace('.', ',')) + " %/s";
    }

    /** Tick de l'atelier (appelé une fois par seconde par KingdomDefenses). */
    static void tick(ServerLevel level, BlockPos pos, int defLevel, String variant, long now) {
        if (now % 20 != 0) return;
        if (!(level.getBlockEntity(pos) instanceof DefenseBlockEntity be)) return;
        double pct = percentPerSecond(defLevel, variant);
        boolean worked = false;
        var storage = be.getStorage();
        for (int i = 0; i < slots(defLevel) && i < storage.getContainerSize(); i++) {
            if ("foundry".equals(variant) && i == slots(defLevel) - 1) continue; // le creuset fond, il ne répare pas
            ItemStack st = storage.getItem(i);
            if (st.isEmpty() || !st.isDamageableItem() || st.getDamageValue() <= 0) continue;
            int amount = Math.max(1, (int) Math.round(st.getMaxDamage() * pct / 100.0));
            st.setDamageValue(Math.max(0, st.getDamageValue() - amount));
            worked = true;
        }
        if (worked) be.setChanged();

        // Armurerie de Campagne : répare aussi l'équipement PORTÉ des joueurs à 8 blocs (0,5 %/s)
        if ("armory".equals(variant)) {
            for (net.minecraft.server.level.ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.distanceToSqr(pos.getX() + 1.0, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) continue;
                boolean fixed = false;
                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    ItemStack st = p.getItemBySlot(slot);
                    if (st.isEmpty() || !st.isDamageableItem() || st.getDamageValue() <= 0) continue;
                    int amount = Math.max(1, (int) Math.round(st.getMaxDamage() * 0.01));
                    st.setDamageValue(Math.max(0, st.getDamageValue() - amount));
                    fixed = true;
                }
                if (fixed && now % 60 == 0) {
                    level.sendParticles(ParticleTypes.WAX_OFF, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.3, 0.5, 0.3, 0.02);
                }
            }
            if (now % 40 == 0) {
                level.sendParticles(ParticleTypes.WAX_OFF, pos.getX() + 1.0, pos.getY() + 1.1, pos.getZ() + 0.5, 3, 0.5, 0.2, 0.3, 0.02);
            }
        }

        // Fonderie de Recyclage : la DERNIÈRE case est un creuset — l'objet y fond en 5 s et rapporte des ressources
        if ("foundry".equals(variant)) {
            int last = slots(defLevel) - 1;
            ItemStack cru = last >= 0 && last < storage.getContainerSize() ? storage.getItem(last) : ItemStack.EMPTY;
            if (cru.isEmpty() || !cru.isDamageableItem()) {
                MELT.remove(pos);
            } else {
                int t = MELT.merge(pos, 1, Integer::sum);
                level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.15, 0.05, 0.15, 0);
                if (t >= 5) {
                    MELT.remove(pos);
                    melt(level, pos, cru);
                    storage.setItem(last, ItemStack.EMPTY);
                    be.setChanged();
                }
            }
        }

        if (!worked) return;
        // Étincelles sur l'enclume (2e bloc, à l'est) ; un coup de marteau toutes les 4 s
        level.sendParticles(ParticleTypes.CRIT, pos.getX() + 1.5, pos.getY() + 0.85, pos.getZ() + 0.5, 4, 0.2, 0.05, 0.2, 0.15);
        level.sendParticles(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 1, 0.05, 0.05, 0.05, 0.01);
        if (now % 80 == 0) {
            level.playSound(null, pos.east(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.25f, 1.4f + level.random.nextFloat() * 0.2f);
        }
    }
}
