package com.wavesurvivor.item;

import net.minecraft.world.item.Rarity;

/**
 * ANNEAU D'ANCRAGE DU NÉANT — relique (non fabricable, vendue par le Marchand du Vide).
 * Immunité à la Lévitation, −25 % de dégâts de chute (effets dans RelicEffects).
 * 1.5 : rattachée à la classe commune RelicItem (famille Inébranlable, niveaux, Forge, équipement, infobulle).
 */
public class AnneauAncrageItem extends RelicItem {

    public AnneauAncrageItem() {
        super(Rarity.EPIC, "srv.relique_du_neant", "Bague", "relic.anchor.quote",
                "relic.anchor.levitation", "relic.anchor.fall");
    }
}
