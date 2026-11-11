package com.wavesurvivor.item;

import java.util.List;

/**
 * 2.0 — LÉGENDAIRE DE LA FAILLE (Destin de la Faille, Couronne du Titan) : une légendaire ordinaire (fusion à la Forge, 2 pièces, reliques
 * d'origine au niveau II puis III puis IV), dont l'infobulle remplace aussi {U} par le nombre d'utilisations du Destin à ce niveau.
 */
public class RiftLegendaryItem extends LegendaryRelicItem {

    public RiftLegendaryItem(String slot, String quote, String... effects) {
        super("Relique légendaire", slot, quote, effects);
    }

    @Override
    public List<String> effectTexts(int lvl) {
        String uses = String.valueOf(RiftRelics.destinyUses(lvl));
        return super.effectTexts(lvl).stream().map(t -> t.replace("{U}", uses)).toList();
    }
}
