# Coffres roulettes — fiche technique pour Claude Code (1.9)

> Conception validée par Yoann avec Claude chat. Conception détaillée : `docs/DESIGN_COFFRES_ROULETTE.md`. Visuels de référence : ce dossier (`coffre_roulette_couleurs.png`, `coffre_roulette_ouverture.png`, `symboles_coffres.png`, `symboles_petits.png`, `coffre_bois.png`, `coffre_ferrures.png`, `coffre_symboles_couches.png`, `cles_formes.png`, `editeur_coffre_symboles.png`, `editeur_coffre_particules.png`).

## 1. Ressources livrées
- `textures/block/roulette/` : `wood.png`, `iron.png`, `trim.png`, `gem.png` (**niveaux de gris**, à teinter par multiplication), `medallion_base.png` (plaque sombre, **non teintée**), `medallion_ring.png` (anneau en niveaux de gris, teinté avec l'accent).
- `textures/roulette_symbols/large/<id>.png` et `small/<id>.png` : 18 symboles × 2 tailles, **masques blancs 16 × 16 déjà centrés** dans le médaillon. Ids : `dot, star, skull, cross, crown, flame, drop, eye, sun, moon, lightning, heart, swords, rune, gem, leaf, hourglass, key` (noms FR dans `symboles_noms.json`).
- `textures/item/roulette_key/` : `shaft.png`, `head_<id>.png` (8 : `ring, clover, crown, skull, heart, star, diamond, winged`), `head_<id>_gem.png` (masque de la gemme), `bit_<id>.png` (4 : `simple, double, stairs, hook`). Toutes en 16 × 16, même repère, niveaux de gris.
- `docs/coffres_roulette/chest_model.json` : pavés du coffre (pixels), partie `lid`, et emplacements des 5 médaillons (`medallion_slots`).
- `docs/coffres_roulette/presets_coffres.json` : réglages complets des 6 coffres actuels.

## 2. Données (`CustomChestData`) — nouveaux champs, tous optionnels
Rétrocompatibilité obligatoire : un coffre sans ces champs garde son comportement et prend les valeurs par défaut.
```
accentColor   : "#RRGGBB" | "auto"            (remplace l'usage de `color`, qui reste lu : couleur de teinture -> hex)
woodColor     : id de préréglage | "#RRGGBB"   (défaut "dark_oak")
ironColor     : id de préréglage | "#RRGGBB"   (défaut "iron")
gemColor      : "accent" | "#RRGGBB"           (défaut "accent")
medallions    : { "front"|"back"|"left"|"right"|"top" : [ { "symbol": id|"custom:<nom>", "size": "large"|"small", "color": "accent"|"#RRGGBB" }, ... ] }
                (liste = couches du fond vers le dessus ; face absente = pas de médaillon ; défaut : front = [dot, large, accent])
customSymbols : { "<nom>": ["10 lignes de 10 caractères '#'/'.'"] }   (dessinés dans l'éditeur ; version petite = même dessin réduit à 6×6, ou dessin séparé)
keyHead, keyBit : ids ci-dessus (défaut "ring", "simple") ; keyMaterial (existant) ; keyColor : "material" | "#RRGGBB"
particles     : { "idle": Effet, "spin": Effet, "reveal": Effet }
                Effet = { "a": type, "b": type|null, "pattern": forme, "density": 1-10, "radius": blocs, "height": blocs, "speed": 1-10, "color": "#RRGGBB", "color2": "#RRGGBB", "size": 0.5-3 }
                Ancien `particles` (string) + `pattern` => particles.idle (et spin si absent).
```
**Préréglages de couleur** (ids → hex) :
- Bois : `dark_oak` #462E1A, `spruce` #745432, `birch` #C8B47C, `acacia` #AC5C34, `cherry` #E2B0AA, `mangrove` #783630, `crimson` #6C324A, `warped` #2E6E68, `ebony` #2C2428.
- Ferrures : `iron` #C4C6CC, `gold` #FACE50, `copper` #DA8052, `oxidized_copper` #54A688, `netherite` #544E54, `amethyst` #A060CC, `quartz` #EEE8E0, `obsidian` #362850.

## 3. Rendu du coffre (BlockEntityRenderer)
- Construire le coffre à partir de `chest_model.json` (quads par face, texture = clé `wood`/`iron`/`trim`/`gem`) ; **teinte** = couleur résolue (bois, ferrures, accent pour `trim`, gemColor pour `gem`) multipliée sur la texture grise.
- **Médaillon** (par face présente) : `medallion_base` (sans teinte) → `medallion_ring` teinté accent → chaque couche : masque du symbole teinté de sa couleur, avec un léger relief (pixels haut-gauche ×1,15, bas-droite ×0,85) ; pour toutes les couches **sauf la première**, dessiner d'abord un **contour sombre** de 1 px (4 voisins) autour du masque ; pour la première, une ombre portée de 1 px en bas à droite. Composer en texture dynamique (cache par combinaison) plutôt que pixel par pixel à chaque image.
- Le médaillon du **dessus** remplace la gemme du couvercle quand il existe.
- Supprimer l'ancien rendu cube + anneau ; garder les noms flottants et les particules existantes.

## 4. Animations
1. **Repos** : `trim` et gemmes pulsent (luminosité 0,8 → 1,0, période ~2 s) ; particules `idle`.
2. **Lancement** (à l'utilisation de la clé) : le médaillon avant **tourne** sur lui-même, accélération sur ~1,5 s, étincelles ; particules `spin`.
3. **Arrêt + ouverture** : ralentissement ~1 s, arrêt ; couvercle ouvert à **70°** (charnière arrière : axe X, y = 9, z = 15) en ~0,4 s ; **faisceau** vertical à la couleur de la **rareté** du gain.
4. **Gain** : l'objet s'élève dans le faisceau (~1 s) puis est donné / tombe vers le joueur ; couvercle refermé après ~2 s.
- **Bonus de révélation automatique** (en plus de `reveal`) : commun rien ; peu commun petite gerbe de `dust` verte ; rare gerbe bleue + `electric_spark` ; épique onde de choc violette (`shockwave`) ; légendaire `firework` doré + `beam` + éclair visuel sans dégâts.
- L'écran de roulette existant (`RouletteLootScreen`) reste ; l'animation du bloc se joue en parallèle, synchronisée.

## 5. Clé (`RouletteKeyItem`)
Icône composée de calques teintés : `shaft` + `bit_<keyBit>` + `head_<keyHead>` (couleur de la matière ou `keyColor`) + `head_<keyHead>_gem` (couleur `gemColor` du coffre), puis contour sombre 1 px. Au choix : modèle d'objet généré à la volée (BakedModel) ou 32 modèles JSON (8 têtes × 4 pannetons) sélectionnés par propriété d'objet, avec `ItemColor` pour les teintes.

## 6. Particules
- Nouveaux types (vanilla 1.20.1) : voir la liste du DESIGN §3 (feu, magie, nature, eau, ténèbres, fête, poussières colorées `dust` / `dust_color_transition`).
- Formes : les 19 de `ParticlePatterns.ALL`, inchangées ; ajouter les paramètres densité / rayon / hauteur / vitesse et le type B (les deux types émis par la même forme, en alternance).

## 7. Éditeur (`ChestBuilderScreen`)
Onglets **Apparence** (accent, bois, ferrures, gemmes), **Symboles** (face, médaillon oui/non, liste des couches avec taille / couleur / monter / descendre / supprimer, bibliothèque des 18 symboles, « Dessiner un symbole » sur grille 10 × 10), **Clé** (tête, panneton, matière, couleur), **Particules** (3 moments, type A, type B, forme, curseurs, couleur, préréglages), **Butin** et **Messages** (existants). Aperçu en direct à gauche avec « Tourner » et « Tester l'ouverture ». Maquettes : `editeur_coffre_symboles.png`, `editeur_coffre_particules.png`.

## 8. Ordre conseillé
1. Données + rétrocompatibilité + préréglages des 6 coffres (`presets_coffres.json`).
2. Rendu du coffre teinté + médaillons en couches (sans animation).
3. Clé composée.
4. Animations + bonus de rareté.
5. Particules étendues.
6. Éditeur (onglets + aperçu en direct + dessin de symbole).
Build, captures en jeu et commit à chaque étape.
