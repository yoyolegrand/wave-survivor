# Coffres roulettes — nouveau design entièrement personnalisable (1.9)

> Rédigée par Claude chat avec Yoann. Visuels de référence : `docs/coffres_roulette/coffre_roulette_couleurs.png` et `coffre_roulette_ouverture.png` (à ranger). **Rien n'est encore codé** : cette fiche fixe les demandes avant les visuels détaillés.

## 0. Ce qui existe déjà (à garder)
`CustomChestData` / `ChestBuilderScreen` :
- `color` : couleur du coffre (runes, gemme, clé) — `auto` ou couleur de teinture ;
- `particles` : `none`, `flame`, `bubble`, `enchant`, `portal`, `soul_flame`, `dust_glow`, `crimson_spore` ;
- `pattern` : **forme d'émission des particules** (`ParticlePatterns.ALL`) — ce n'est PAS le symbole du coffre ;
- `keyMaterial` : matière de la clé (`or` par défaut) ;
- `tierMessages`, `rewards`.
Tout cela doit continuer à marcher (anciens coffres et configs sans les nouveaux champs = valeurs par défaut).

## 1. Le nouveau modèle
Un vrai coffre : caisse en bois, couvercle bombé, 4 cornières en fer avec petits pieds, **cadran de roulette** sur la face avant (porte le **symbole**), serrure-gemme sur le couvercle, gemme sur le dessus, **liseré lumineux** à la jointure du couvercle.
Le modèle est rendu par un **BlockEntityRenderer** (comme le coffre vanilla) pour les couleurs à la volée et les animations. Chaque partie a sa texture en **niveaux de gris**, **teintée** par le jeu selon la config.

## 2. Ce qu'on doit pouvoir choisir dans l'éditeur (par coffre)
| Réglage | Nouveau ? | Valeurs |
|---|---|---|
| **Couleur d'accent** (cadran, liseré, runes) | existe (`color`) | `auto`, couleurs de teinture, **+ couleur libre (hex)** |
| **Couleur du bois** | **nouveau** `woodColor` | préréglages (chêne noir, épicéa, bouleau, acacia, cerisier, mangrove, cramoisi, biscornu, ébène…) + couleur libre |
| **Couleur des ferrures** | **nouveau** `ironColor` | préréglages (fer, or, cuivre, cuivre oxydé, netherite, améthyste…) + couleur libre |
| **Symbole du cadran** | **nouveau** `symbol` | bibliothèque de 18 symboles (voir §2 bis) **+ symbole personnalisé** dessiné dans l'éditeur sur une grille 10 × 10 (enregistré comme masque) |
| **Couleur du symbole** | **nouveau** `symbolColor` | `accent` (par défaut) ou une couleur au choix |
| **Couleur des gemmes** | existe via `color` → **séparer** en `gemColor` | `accent` par défaut, sinon une couleur |
| **Clé** | existe (`keyMaterial`) | matière + **couleur** de la clé + **forme** de la clé (tête de la clé et panneton : quelques modèles au choix), pour que la clé s'accorde au coffre |
| **Particules** | existe → **à développer** (§3) | — |
Aperçu en direct du coffre dans l'éditeur quand on change un réglage.

## 2 bis. Symboles : bibliothèque, faces, couches (validé)
- **Bibliothèque (18)** : point (actuel), étoile, crâne, croix, couronne, flamme, goutte, œil, soleil, lune, éclair, cœur, épées croisées, rune, gemme, feuille, sablier, clé. Masques **10 × 10**, tous de **dimensions paires** (le médaillon fait 16 px : seul un symbole pair est parfaitement centré) et **centrés automatiquement** d'après leur forme réelle. Planche : `docs/coffres_roulette/symboles_coffres.png`.
- **Deux tailles** par symbole : **grand** (10 × 10, pour le fond) et **petit** (6 × 6, pour une couche du dessus).
- **Par face** : avant (cadran), arrière, gauche, droite, dessus (médaillon à la place de la gemme). Chaque face : **aucun symbole** ou un **médaillon**.
- **Couches** : chaque médaillon empile **plusieurs symboles** (ex. grand soleil en fond + petit crâne par-dessus), **chacun avec sa couleur**. Les couches du dessus ont un **contour sombre** d'un pixel pour se détacher. Ordre des couches modifiable dans l'éditeur.
- L'**anneau** du médaillon prend la couleur d'accent.
- Combinaisons de départ validées par Yoann : sacré = bouleau + or ; Nether = cramoisi + netherite ; marin = biscornu + cuivre oxydé ; profane = ébène + obsidienne.
- Symboles conseillés pour les 6 coffres actuels : Boss = crâne, Nether = flamme, Abysses = goutte, Arcanique = rune, Trésors = gemme, End = lune.

## 2 ter. Bois et ferrures (validé)
- Textures **en niveaux de gris**, **teintées** par le jeu. Préréglages bois : chêne noir, épicéa, bouleau, acacia, cerisier, mangrove, cramoisi, biscornu, ébène. Ferrures : fer, or, cuivre, cuivre oxydé, netherite, améthyste, quartz, obsidienne. + couleur libre pour les deux. Planches : `coffre_bois.png`, `coffre_ferrures.png`.

## 2 quater. Clés (en validation)
Icône 16 × 16, clé en diagonale (tête en haut à gauche), contour sombre style objet Minecraft, composée de pièces :
- **Tête** (8) : anneau, trèfle, couronne, crâne, cœur, étoile, losange, ailée — avec une **gemme** au centre (couleur de la gemme du coffre, réglable).
- **Panneton** (4) : simple, double, escalier, crochet.
- **Matière** : `keyMaterial` existant (or, fer, cuivre, netherite, améthyste, quartz…) + couleur libre ; métal en niveaux de gris teinté.
- Planche : `cles_formes.png`. Exemples : Boss = crâne + double + netherite ; Trésors = couronne + escalier + or ; Abysses = étoile + simple + quartz ; Arcanique = losange + crochet + améthyste ; Nether = trèfle + simple + cuivre ; Sacré = ailée + double + or.
- Nouveaux champs : `keyHead`, `keyBit`, `keyColor` (+ `keyMaterial` existant). L'icône est composée à la volée (couches teintées) ou par un modèle d'objet avec plusieurs calques teintés.

## 3. Particules (validé)
- **Formes d'émission** : les 19 de `ParticlePatterns.ALL` (circle, square, triangle, pentagram, hexagram, cross, double_ring, spiral_up, double_helix, vortex_down, cone_up, portal_vertical, shockwave, firework, aura_pulse, beam, ashes, fireflies, cloud) — inchangées.
- **Types** (vanilla 1.20.1), par thème :
  - Feu : flame*, soul_fire_flame*, small_flame, lava, campfire_cosy_smoke ;
  - Magie : enchant*, portal*, reverse_portal, witch, dragon_breath, glow, end_rod, totem_of_undying ;
  - Nature : cherry_leaves, spore_blossom_air, snowflake, composter ;
  - Eau : bubble*, dripping_water, splash, glow_squid_ink, nautilus ;
  - Ténèbres : crimson_spore*, warped_spore, soul, sculk_soul, ash, white_ash, smoke, dripping_obsidian_tear ;
  - Fête : heart, note, firework, crit, electric_spark ;
  - Couleur libre : dust* (`dust_glow`, couleur + taille), dust_color_transition (2 couleurs).
  (* = déjà proposés aujourd'hui.)
- **Réglages par effet** : type A, **type B optionnel** (combinaison), forme d'émission, densité (1–10), rayon, hauteur, vitesse, couleur(s) et taille (poussières).
- **Trois moments**, chacun avec son effet : **au repos**, **pendant la roulette**, **à la révélation**.
- **Bonus automatique de révélation selon la rareté** (ajouté à l'effet choisi) : commun = rien ; peu commun = petite gerbe de poussière verte ; rare = gerbe bleue + étincelles ; épique = onde de choc violette ; légendaire = feu d'artifice doré + colonne de lumière + éclair sans dégâts.
- **Préréglages des 6 coffres** (modifiables) : Boss = flammes d'âme en cercle / vortex d'âmes ; Nether = flammes + cendres qui montent / spirale de lave ; Abysses = bulles en lucioles / double hélice bulles + encre luisante ; Arcanique = enchantement en pentagramme / colonne d'étincelles de l'End ; Trésors = poussière dorée en scintillement / feu d'artifice doré ; End = portail en double anneau / portail inversé en vortex.
- Compatibilité : l'ancien champ `particles` + `pattern` devient l'effet **au repos** (et sert aussi pour la roulette si rien n'est réglé).

## 4. Animations
1. **Au repos** : le liseré et les gemmes pulsent doucement.
2. **Lancement** : le cadran tourne de plus en plus vite, étincelles.
3. **Ouverture** : le cadran ralentit et s'arrête, le couvercle s'ouvre, **faisceau** à la couleur de la **rareté** du gain.
4. **Gain** : l'objet s'élève dans le faisceau puis tombe vers le joueur ; effet plus fort selon la rareté (gerbe de particules, éclair pour le légendaire).

## 5. Prochaines étapes (Claude chat)
- Visuels : palettes bois / ferrures, planche des symboles, formes de clé, aperçu de l'éditeur.
- Puis textures en niveaux de gris + modèle + fiche technique pour Claude Code.
