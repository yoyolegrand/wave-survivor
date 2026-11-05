# Ruine « La Chapelle » — structure du monde (1.9) — version 2

> Rédigée par Claude chat avec Yoann. **v2** : corrige les trous vus en jeu (toit qui ne rejoignait pas les murs, triangle de ciel au-dessus du croisement, pignons du transept incomplets), **ouvre l'abside** (le mur nord du transept la bouchait), ajoute la **décoration intérieure**, **2 coffres latéraux** et les **points d'apparition** des monstres. C'est un **lieu marquant**, donc **rare**.

Fichiers (dans `docs/ruines/chapelle/`) :
- `blueprint_chapelle.json` — **version 2** (remplace la v1), plan bloc par bloc, même format que les arènes, JSON compact (~74 Ko), 37 × 33 × 55.
- `chapelle_v5.png` — vue d'ensemble corrigée ; `chapelle_interieur.png` — vue en coupe de l'intérieur.
- (anciens visuels v4 conservés pour comparaison)

## 1. Contenu
- Plan en croix : nef 13 × 16, transept 25 de large, abside arrondie (désormais **ouverte** par une grande arche), tour-clocher 7 × 7 (cloche, flèche en ardoise, croix en blocs d'or), grand portail, rosace, vitraux doubles, contreforts à pinacles, toit en tuiles de deepslate **entièrement fermé** (vérifié par programme : plus aucun vide entre murs et toit, aucune case du sol intérieur à ciel ouvert).
- **Décoration intérieure** : 6 rangées de bancs (escaliers de bouleau), allée de tapis jaune de l'entrée à l'autel, arcade de colonnes, 2 **bénitiers** (`holy_fountain`) à l'entrée, **pupitre** (lutrin) au croisement, **bannières jaunes** sur les murs de la nef, **lustres** (lanternes de grâce suspendues) dans la nef et les bras du transept, tapis blancs dans les bras, **bougies votives** au pied des colonnes et sur les marches, autel surélevé avec bougies, lanterne et **pots de muguet**.
- **Chapelles latérales** (une dans chaque bras du transept) : petit autel sculpté avec bougie, **bibliothèques**, **petit coffre**.
- Abords : chemin bordé de lanternes, cimetière clôturé (tombes, stèles, croix), haie, fleurs.
- Blocs du mod : `blessed_marble` (+ `_slab`, `_stairs`, `_wall`, `_polished`, `_chiseled`, `_column`), `grace_lantern` (posée / suspendue), `votive_candle` (allumée), `holy_fountain`. **Vérifier les noms d'états** (`hanging`, `lit`) et que `holy_fountain` se pose sans état particulier ; sinon adapter la palette.

## 2. Repères (`markers`)
- `chest_main` : grand coffre au pied de l'autel.
- `chests_side` : 2 petits coffres (chapelles latérales).
- `guardian_spawns_graveyard` : 7 points, devant les tombes du cimetière.
- `guardian_spawns_nave` : 2 points, entre les bancs près de l'entrée.
- `trigger_graveyard` : boîte (min / max) qui couvre le cimetière.
- `entrance` (portail, face au sud), `bell` (cloche).

## 3. Butin (tables réglables)
- **Grand coffre** : 6 à 10 Éclats de grâce, 1 à 2 Fragments, 15 % de chance d'un Cœur de grâce, + un peu de butin de « temple » (pain béni, bougies, émeraudes).
- **Petits coffres** (×2) : 2 à 4 Éclats de grâce, 30 % de chance d'un Fragment, + livres / papier / bougies.
- Pillables une seule fois (coffres vanilla avec table de butin).

## 4. Apparition des monstres (une seule fois par chapelle)
1. **Le cimetière se réveille** : quand un joueur entre dans `trigger_graveyard` **ou** passe le portail pour la première fois → la **cloche sonne**, puis 4 à 6 morts-vivants (zombies et squelettes, quelques-uns équipés d'armures en or ou en cuivre) sortent des tombes aux `guardian_spawns_graveyard` (particules de terre / âmes à l'apparition).
2. **Les gardiens de la nef** : quand un joueur ouvre le **grand coffre** (ou s'approche à moins de 6 blocs de l'autel) → 2 **gardiens** (squelettes avec épée et casque, plus résistants) apparaissent aux `guardian_spawns_nave`, entre le joueur et la sortie.
- Réglages : activer / désactiver, nombre, types de monstres, difficulté (suivre la difficulté du monde ou du mod).
- Pas d'apparition naturelle supplémentaire dans la chapelle : les lanternes et bougies l'éclairent.

## 5. Génération et pose
- **Rare** (réglable) : plaines, prairies, savanes, forêts, forêts de bouleaux — terrain assez plat (refuser au-delà de 3 blocs d'écart sur l'emprise).
- Pose comme les arènes : vider l'emprise au-dessus du plan, poser, combler dessous (fondations), raccorder les bords en pente douce ; rotation aléatoire 0 / 90 / 180 / 270° (tourner les repères, les états `facing` et les bannières).
- Page du Codex « Ruines » + entrée dans l'onglet Ressources du Carnet du Survivant (« Fragments de grâce : chapelles »).
