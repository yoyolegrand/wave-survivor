# Statue du Séraphin — plumes plates organiques (1.9)

> Rédigée par Claude chat, visuel validé par Yoann (`statue_comparaison.png`, `statue_nouvelle_detail.png`, `statue_5_stades.png`). Méthode : chaque plume-cube devient un **plan d'épaisseur zéro** découpé par la transparence (plume effilée, pointe arrondie), chaque segment d'aile devient une **bande de couvertures** (arête dorée continue, écailles, bout arrondi à la pointe de l'aile). Composition, poses et hiérarchie **inchangées**.

## Fichiers
- `textures/block/seraph_shrine/stage0..4.png` et `stage0..4_glow.png` : **remplacent** les actuelles. Seule la bande **y 320 → 511** (libre jusqu'ici) est nouvelle : 132 plumes + 18 bandes de couvertures, chacune peinte pour les 5 stades (blanc → gris → violacé → presque noir, fissures roses lumineuses aux stades 3-4 dans les `_glow`, bouts effilochés et trous aux stades 3-4). Le reste des textures est identique.
- `docs/statue_seraphin/plumes_plates.json` : pour chacune des **150 pièces** concernées (132 `*_fN` + 18 segments `up/mid/low` `L/R` `r/m/t`) : `texOffs` et `addBox` (x, y, z, largeur, hauteur, **profondeur 0**).

## Modifications dans `SeraphShrineModel.createBodyLayer()`
1. Pour chaque pièce listée dans `plumes_plates.json` : remplacer son cube par `CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, 0)` (valeurs du JSON). **Ne pas toucher au `PartPose`** (offset et rotations identiques) ni à la hiérarchie (les plumes restent enfants de leurs segments).
   - Plumes : largeur 6 ou 7, longueur = longueur actuelle, centrées (`x = -w/2`, `z = 0`).
   - Segments : bande de 10 de haut qui part de l'arête (`y = -2.6`), `x = -w` côté gauche (L), `0` côté droit (R).
2. **Type de rendu** : passer de `RenderType::entityCutoutNoCull` à **`RenderType::entityCutout`** (avec élimination des faces arrière). Indispensable : un plan d'épaisseur 0 a ses faces avant et arrière au même endroit ; sans élimination, elles se superposent et scintillent. Avec, on voit la face avant d'un côté et la face arrière (peinte en miroir) de l'autre. Les autres cubes de la statue sont fermés, rien ne change pour eux.
3. Taille de texture inchangée (1024 × 512).
4. La logique existante des stades (plumes arrachées, halo brisé, mousse, lierre) reste **inchangée** : elle s'applique aux mêmes pièces.

## Vérifications en jeu
- Les 3 paires d'ailes : plumes fines en éventail, pointes arrondies, arête dorée continue avec bout arrondi.
- Aucune face qui scintille (si oui : vérifier le type de rendu du point 2).
- Les 5 stades : couleur des plumes cohérente avec le corps, fissures roses lumineuses aux stades 3 et 4.
- Vue de dos : les plumes doivent être pleines (face arrière peinte).
