# Ruine « La Chapelle » — structure du monde (1.9)

> Rédigée par Claude chat avec Yoann (visuel validé). Première des 3 ruines prévues dans `docs/DESIGN_ACCUEIL_ET_RUINES.md` §2. Elle a été agrandie à la demande de Yoann : c'est maintenant un **lieu marquant** (pas une petite ruine), donc **rare**, avec un butin un peu plus généreux.

Fichiers (dans `docs/ruines/chapelle/`) :
- `blueprint_chapelle.json` — plan bloc par bloc (même format que les arènes, voir `docs/arenes/necropole/ARENE_NECROPOLE.md` §2), JSON compact (~73 Ko).
- `chapelle_v4.png` — vue d'ensemble (2 angles) ; `chapelle_v4_facade.png` — gros plan de la façade.

## 1. Contenu
- Plan en croix : nef 13 × 16 (bancs en bouleau, allée de tapis jaune, arcade de colonnes, 3 lanternes de grâce suspendues), transept 25 de large (grandes verrières), abside arrondie (autel surélevé : bougies votives allumées, lanterne de grâce, **coffre**).
- Tour-clocher 7 × 7 sur 18 de haut (pinacles d'angle, beffroi ajouré, **cloche**, flèche en ardoise, croix en blocs d'or), grand portail en arc (portes en chêne noir ouvertes), grande rosace de vitrail.
- Murs : soubassement en marbre poli, corniche, vitraux doubles avec meneau (verre teinté jaune / blanc / bleu clair), contreforts à 2 niveaux avec pinacles (paratonnerres).
- Toit en tuiles de deepslate.
- Abords : chemin bordé de 4 lanternes sur piliers, **cimetière** clôturé (barreaux de fer, tombes, stèles, tertres de terre stérile, buissons morts), haie, fleurs.
- Blocs du mod utilisés : `blessed_marble` (+ `_slab`, `_stairs`, `_wall`, `_polished`, `_chiseled`, `_column`), `grace_lantern` (posée et suspendue), `votive_candle` (allumée). **Vérifier que les noms d'états** `hanging=true/false` (lanterne) et `lit=true` (bougie) correspondent à tes blocs ; sinon adapter la palette du plan.

## 2. Lire le plan
- Taille 37 × 33 × 55 ; `ground_y` = 0 (la couche y = 0 est le sol : herbe, dallage, chemin).
- x → est, z → sud, y → haut ; `layers[y][z]` = ligne de x. `.` = **ne rien poser** (garder l'air / le terrain).
- **Repères** (`markers`) :
  - `chest` : position du coffre de butin (au pied de l'autel) ;
  - `guardian_spawns` : 6 points d'apparition des gardiens, dans le cimetière ;
  - `entrance` : portail (face au sud) ; `bell` : la cloche.

## 3. Génération
- **Rare** (réglable) : plaines, prairies, forêts de bouleaux, forêts — terrain assez plat (refuser au-delà de 3 blocs d'écart sur l'emprise).
- Pose : comme les arènes — vider l'emprise au-dessus du plan, poser le plan, **combler dessous** (fondations en terre / pierre) et raccorder les bords en pente douce d'herbe ; rotation aléatoire 0 / 90 / 180 / 270° (tourner aussi les repères et les états `facing`).

## 4. Gameplay
- **Coffre** : fragments de grâce — 6 à 10 Éclats, 1 à 2 Fragments, 15 % de chance d'un Cœur de grâce (table de butin réglable).
- **Gardiens** : quand un joueur entre dans le cimetière ou la nef pour la première fois, 4 à 6 morts-vivants (zombies et squelettes, éventuellement équipés) apparaissent aux `guardian_spawns`. Une seule fois par chapelle.
- La **cloche** sonne quand les gardiens apparaissent (signal pour le joueur).
- Pillable une seule fois (coffre vanilla avec table de butin : il ne se remplit pas à nouveau).
- Page du Codex « Ruines » + entrée dans l'onglet Ressources du Carnet du Survivant (« Fragments de grâce : chapelles »).
