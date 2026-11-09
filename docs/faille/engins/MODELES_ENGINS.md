# Engins de siège — modèles finaux niveaux 1 et 2 (Claude chat)

Fichiers : `catapult_l1.json`, `catapult_l2.json` (Renforcée), `celestial_ballista_l1.json`, `celestial_ballista_l2.json` (Consacrée), atlas `textures/entity/siege_engines.png` (64 × 64, 16 tuiles de 16 × 16), aperçus `catapult_niveaux.png`, `celestial_ballista_niveaux.png`.

## Format
- Unités en **pixels** (16 = 1 bloc). Origine = coin nord-ouest **au sol** de l'emprise. Les engins **regardent vers le sud (+Z)**.
- `groups` : `{pivot, rotation [x,y,z] en degrés (pose de repos), parent}`. Rotation appliquée X puis Y puis Z autour du pivot ; un groupe enfant hérite de la transformation de son parent (comme des ModelPart imbriqués).
- `parts` : cubes `{group, from, to, texture, glow}`. `texture` = nom d'une tuile ; `tile_uv` donne son coin (u, v) dans l'atlas. **Chaque face est remplie en répétant la tuile 16 × 16** (une face de 40 px = 2,5 répétitions) : dessiner la face en sous-quads de 16 px max, avec les UV de la tuile. `glow` = pleine luminosité (couche émissive).
- Emprises : catapulte 32 × 48 × 96 px (2 × 3 × 6 blocs), baliste 48 × 48 × 48 px (3 × 3 × 3 blocs).

## Groupes animables
**Catapulte** : `chassis` (fixe) → `arm` (pivot sur l'axe ; rotation X : **+13° = chargée**, **−18° = après le tir**) → `ammo` (le boulet, enfant de `arm`, caché après le tir) ; `winch` (treuil, rotation Z en continu pendant le rechargement).
**Baliste** : `base` (fixe) → `turret` (rotation Y = visée horizontale) → `weapon` (rotation X = visée verticale, repos −18°) → `wing_r`, `wing_l` (rotation Y = battement / repli des ailes), `string` (la corde : translation vers l'avant au tir), `bolt` (le carreau : caché puis réapparition), `halo` (rotation Y en continu pendant le rechargement).

## Animations (voir aussi DESIGN_FAILLE_2_0.md §6.3) — bloc `animations` de chaque JSON
- **Catapulte** : tir 10 ticks (`arm` +13° → −18°, `ammo` caché, châssis qui tressaute) ; rebond 6 ticks ; rechargement **600 ticks (30 s) au niveau 1, 480 (24 s) au niveau 2** (`arm` −18° → +13° cran par cran, `winch` qui tourne) ; chargement 10 ticks (`ammo` visible).
- **Baliste** : chargeur de 16 ; un tir toutes les **10 ticks (niveau 1) / 8 ticks (niveau 2)** : `string` qui claque vers l'avant, ailes `±12°` puis retour, `bolt` caché puis glisse, cristal qui flashe ; rechargement complet **200 ticks (10 s) / 160 (8 s)** : `halo` qui tourne, ailes repliées puis retour, `bolt` qui se matérialise.

## Niveau 2
- **Catapulte Renforcée** : plaques de fer sur les flancs du châssis, poutre cerclée de fer, contrepoids plus gros (pierre cerclée), roues dentées sur le treuil.
- **Baliste Consacrée** : ailes à 5 segments (plus longues, plumes plus longues), 4 bagues dorées sur le fût, halo double, 4 cristaux aux angles du socle.
