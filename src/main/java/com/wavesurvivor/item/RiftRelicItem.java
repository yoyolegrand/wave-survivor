package com.wavesurvivor.item;

import net.minecraft.world.item.Rarity;

import java.util.List;

/**
 * 2.0 — RELIQUE DE LA FAILLE (Dé de la Faille, Sceau du Titan, Trophée du Titan, Cœur du Titan). Comme les autres reliques (niveaux,
 * Curios, Forge, famille), mais ses chiffres ne suivent pas tous le multiplicateur commun : l'infobulle les prend dans {@link RiftRelics}.
 */
public class RiftRelicItem extends RelicItem {

    private final String id;

    public RiftRelicItem(String id, String slot) {
        super(Rarity.EPIC, "Relique de la Faille", slot, "relic." + id + ".quote");
        this.id = id;
    }

    @Override
    public List<String> effectTexts(int lvl) {
        return RiftRelics.effects(id, lvl);
    }
}
