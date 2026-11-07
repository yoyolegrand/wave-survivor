# Arène Kingdom « L'Oasis des Pharaons » — Kingdom des Sables

> Rédigée par Claude chat avec Yoann (visuel validé). Deuxième arène Kingdom, sur le modèle du Val de la Couronne (`docs/arenes/val_de_la_couronne/ARENE_VAL_DE_LA_COURONNE.md`, à relire pour la méthode de pose). Le mode lui-même : `docs/DESIGN_KINGDOM_SABLES.md`.

Fichiers (dans ce dossier) :
- `blueprint_oasis_des_pharaons.json` — plan bloc par bloc, **version 2**, **181 × 27 × 181**, 36 blocs différents, JSON compact (~880 Ko). v2 : feuilles des palmiers **persistantes** (elles tombaient), Temple de Rê enrichi (pylône à hiéroglyphes et disque solaire, colonnade, obélisques, braseros, allée de sphinx, colonnes tombées) ; repères inchangés. Ancienne version : `blueprint_oasis_des_pharaons_v1.json`.
- `kingdom_desert_vue.png` (vue 3D), `kingdom_desert_plan.png` (plan annoté), `palmiers_oasis.png` (forme d'arbre « Oasis »), `palm.py` (algorithme du palmier).

## 1. Mesures (identiques au Val de la Couronne)
| Élément | Valeur |
|---|---|
| Monolithe | au centre, place libre de 7 blocs (rien au-dessus) |
| Royaume | 16 → 32 (niveaux 1 à 5) ; **rempart de grès** à 34 (créneaux, 4 ouvertures dans l'axe des routes, 4 tours d'angle) ; intérieur **vide** |
| Portes | **3** portes fixes à **68** blocs (nord, est, sud), **14 blocs libres** autour de chacune |
| Marché | à l'ouest, à 68 blocs, **pas de porte** ; place aplanie (rayon 20) |
| Sol | butte du royaume plate ; dunes ailleurs ; zones des portes et du marché aplanies (rayon 20, raccord jusqu'à 27) |

## 2. Contenu
- **Nord — Tombeau d'Anubis** : grande pyramide à degrés à l'est de la porte (entrée tournée vers la porte), petite pyramide sœur à l'ouest, 4 lanternes d'âme sur socles.
- **Est — Nid de Sekhmet** : crête rocheuse en arc (terre cuite), 3 terriers, ossements.
- **Sud — Temple de Rê** : demi-cercle de colonnes, grand disque solaire (or) sur pilier, 2 obélisques.
- **Ouest — Marché** : place dallée, **puits** couvert, **4 étals** (auvents colorés, tonneaux), 3 tentes de caravane, feu de camp, **6 emplacements** de coffres (grès sculpté).
- **Ressources** : grande **oasis** (lac + 10 palmiers), **petite oasis** (6 palmiers), **palmeraie** (8 palmiers), **carrière** rocheuse (pierre, andésite, pierre taillée, **minerai de fer et de charbon** en surface et en profondeur), quelques cactus.
- **4 obélisques en ruine** sur les routes (à 44 blocs).

## 3. Repères (`markers`)
- `monolith`, `ground_y_at_monolith` (= 6 dans le plan), `kingdom_radius_by_tier` `[16,20,24,28,32]`, `rampart_radius` 34.
- `fixed_gates` : 3 portes avec `id` (`anubis`, `sekhmet`, `re`), `pos`, `facing_toward_monolith` et `lord` (le seigneur qui mène les grandes marches de cette porte).
- `market` : `center`, `radius`, `merchant_spawns` (4, devant les étals), `roulette_chest_spawns` (6, sur les dalles de grès sculpté autour du puits).
- `resources` : `oasis`, `small_oasis`, `palm_grove`, `quarry`. `obelisks` : 4.

## 4. Pose et branchement
1. Pose comme le Val de la Couronne (§3 de sa fiche : hauteur de référence, vider, poser, combler, raccord extérieur en sable). Rotation : 0° au début.
2. Poser le **Monolithe** au repère `monolith`, lié à la horde Kingdom « Les Sables ».
3. `gatePlacement = "fixed"` avec les **3** `fixed_gates` (coordonnées absolues), pour cette arène seulement.
4. Marché : faire apparaître les **4 marchands** aux `merchant_spawns` pendant le Calme, et les **coffres roulettes** aux `roulette_chest_spawns` (voir `DESIGN_KINGDOM_SABLES.md` §3 et §4).
5. Vérifier que les marches des 3 portes atteignent le royaume sans rester bloquées (dunes, crête de Sekhmet, pyramide).

## 5. Génération
Comme le Val : **pas de génération naturelle par défaut** (pose par commande ou objet dédié) ; option très rare dans les déserts plats.
