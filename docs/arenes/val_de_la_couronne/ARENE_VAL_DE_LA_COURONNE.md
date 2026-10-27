# Arène Kingdom « Le Val de la Couronne » — horde des pillards (mode Kingdom)

> Rédigée par Claude chat avec Yoann. Première arène pensée pour le **mode Kingdom**. Même format de plan que les autres arènes (`docs/arenes/necropole/ARENE_NECROPOLE.md` §2), mais **beaucoup plus grande** (181 × 181) : c'est un terrain complet.

Fichiers (dans `docs/arenes/val_de_la_couronne/`) :
- `blueprint_val_de_la_couronne.json` — le plan bloc par bloc (JSON compact, ~650 Ko).
- `arene_kingdom.png` — vue isométrique d'ensemble.
- `arene_kingdom_plan.png` — plan numéroté, avec les **cercles du royaume** (niveaux 1 → 5) et l'**emprise de la plus grande Porte** à chaque camp.
- `arene_kingdom_camp.png` — gros plan d'un camp de pillards.

---

## 1. Dimensions calées sur le code actuel du mode Kingdom (vérifiées)
| Élément | Valeur dans le mod | Dans l'arène |
|---|---|---|
| Portes | 4, aux points cardinaux, anneau `ringMin 64` – `ringMax 72` → **68 blocs** | Centres des 4 camps **à 68 blocs** exactement |
| Royaume (`KingdomTownHall`) | 16 blocs au niveau 1, **+4 par niveau** (`CLAIM_PER_TIER`), **32 au niveau 5** (`TOWN_MAX`) | Butte **plate sur 36 blocs** ; **palissade à 34** = limite du royaume niveau 5 |
| Décor de la mairie | jusqu'à **6 blocs** autour du Monolithe + cristal **5 blocs au-dessus** | **Place libre de 7 blocs** de rayon, rien au-dessus |
| Porte en arche (`GateStructure.build`) | ≈ 11 large × 13 haut × 5 profondeur | **Espace libre de 14 blocs** de rayon au centre de chaque camp |
| Porte en tanière (`buildDen`) | jusqu'à **19 large** (dôme + rocher annexe) × 12 haut × **9 profondeur** | idem (tient avec de la marge, voir rectangles rouges du plan) |

Si ces valeurs changent dans le code, adapter le plan (ou les repères) en conséquence.

## 2. Contenu
1. **Monolithe** au centre d'une place libre (7 blocs).
2. **Palissade de pierre** à 34 blocs, 4 ouvertures dans l'axe des routes, 4 tours d'angle (diagonales : bons emplacements de tours d'archers), 4 maisons et un puits près de la palissade (déjà construits, les joueurs peuvent les démolir).
3. **4 camps de guerre de pillards** (un par porte), tournés vers le Monolithe : palissade de rondins pointus (rayon 19, grande ouverture face au Monolithe, petite à l'arrière), tour de guet à plateforme + bannière, 3 tentes rayées, feu de camp, enclos de ravageur, cage en fer, ravitaillement (tonneaux, foin), mannequins, mâts à bannières à l'entrée. **Centre libre (14 blocs)** pour la Porte du mod.
4. **4 tours de guet en ruine** à 44 blocs sur les routes (avant-postes à tenir).
5. **Rivière** à l'ouest entre la palissade et le camp ouest, **pont** sur la route ouest (passage étroit).
6. **Forêts** : chênes noirs au nord-est, bouleaux au sud-ouest (bois). 7. **Colline rocheuse + carrière** au sud-est (pierre, minerai de fer). 8. **Lac** au nord-ouest. 9. **Champs de blé** au sud, **hors** du royaume.

Chaque porte a son caractère : ouest = le pont, nord = le long du lac, est = à travers la forêt, sud = par les champs.

## 3. Lire le plan
- **Version 2** (retour de jeu de Yoann) : camps reconstruits bloc par bloc (palissade à pointes, tour de guet avec plancher, garde-corps, échelle et toit, tentes à deux pans fermées à l'arrière, vraies bannières sur mâts, feu de camp et bancs, cage couverte, enclos avec portillon, ravitaillement, mannequins) ; sol de chaque camp **aplani** avec un raccord en pente douce (rayon 20 → 26) ; **fondations de 10 blocs** sous tout le terrain. Taille : 181 × 28 × 181, ~900 Ko.
- Origine au **coin nord-ouest, au bas** de la structure ; x → est, z → sud, y → haut ; `layers[y][z]` = ligne de x = 0..180 (JSON compact, sans retours à la ligne).
- `ground_y_at_monolith` = 16 : le sol de la place du Monolithe dans le plan. Le terrain varie autour (relief, rivière, colline, camps aplanis).
- Les **bannières** (`minecraft:white_banner`) sont des **bannières de pillard** : à la pose, leur appliquer le motif de la bannière sinistre (NBT du bloc) et les tourner vers l'extérieur du camp.
- Le **blé** est déjà posé (`wheat[age=7]` sur `farmland`).

### ⚠ Pose sur le terrain (problème vu en jeu : grandes plaques de pierre à nu, camps à moitié enterrés)
L'arène est un **terrain complet** : elle doit **remplacer** le terrain naturel sur toute sa surface, pas se superposer à lui. À la pose :
1. **Hauteur de référence** : placer la structure pour que `ground_y_at_monolith` tombe au niveau moyen du terrain naturel de la zone (médiane des hauteurs).
2. **Vider** : sur toute l'emprise (181 × 181), retirer **tous** les blocs naturels au-dessus du plan (jusqu'à la hauteur maximale du terrain naturel + arbres), **y compris** là où le plan contient de l'air.
3. **Poser le plan entier**, y compris ses 10 couches de fondations (elles recouvrent la pierre naturelle : aucune pierre ne doit rester à nu en surface).
4. **Sous le plan** : si le sol naturel est plus bas que les fondations, combler en terre / pierre jusqu'au sol solide.
5. **Raccord extérieur** : sur une bande de 12 blocs autour de l'emprise, **interpoler** la hauteur entre le bord du plan et le terrain naturel (talus en herbe et terre, jamais en pierre nue), et retirer les arbres coupés.
6. Vérifier avec une capture vue du ciel : aucune falaise ni plaque grise au bord, camps à plat.

### Repères (`markers`)
- `monolith` : position du Monolithe (sur la place).
- `kingdom_radius_by_tier` : `[16, 20, 24, 28, 32]` (rappel).
- `fixed_gates` : les **4 portes**, avec `pos` (centre du camp, au sol) et `facing_toward_monolith`.
- `watchtowers` : les 4 tours de guet. `palisade_radius` : 34.

## 4. Brancher l'arène sur le mode Kingdom
Le mod gère déjà des **portes fixes** pour les cartes sur mesure (`gatePlacement: "fixed"`, `gatePoints`, voir `KingdomManager`). Utiliser ce mécanisme :
1. **Poser l'arène** : commande `/ws arena place val_de_la_couronne` (chargeur `ArenaBlueprint` commun), avec fondations et raccord au terrain (§3 de la fiche Nécropole). Rotation : **0° uniquement** au début (les portes sont nommées N/E/S/O) ; si rotation, tourner aussi les repères.
2. **Poser le Monolithe** au repère `monolith`, **lié à la horde des pillards en mode Kingdom**.
3. **Configurer les portes** de cette partie : `gatePlacement = "fixed"` et `gatePoints` = les 4 `fixed_gates` **convertis en coordonnées absolues** (origine de l'arène + position du repère), `facing` = vers le Monolithe (ou `gateFacing = "monolith"`). Ces réglages doivent s'appliquer **à cette arène seulement** (ne pas écraser la config par défaut des pillards).
4. Le reste (royaume, mairie, défenses, marches des troupes, événements du chaos) fonctionne comme d'habitude. Vérifier que les **marches** (`KingdomMarch`) empruntent bien les routes et le pont.

## 5. Génération
Le terrain est **trop grand** (181 × 181) pour une génération naturelle fréquente. Conseillé :
- **Pas de génération naturelle par défaut** : l'arène se pose par commande, ou par un objet dédié (ex. « Charte du Val », utilisable une fois sur un terrain dégagé), réglable en config.
- Option (désactivée par défaut) : génération **très rare** dans les plaines / prairies / savanes plates, uniquement si le terrain est assez plat sur toute la zone.

## 6. Pour plus tard
D'autres arènes Kingdom (autres hordes) pourront suivre ce modèle : même calage (portes à 68 blocs, royaume jusqu'à 32, place libre de 7, espace libre de 14 autour des portes).
