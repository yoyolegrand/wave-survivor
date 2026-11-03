# Brief pour Claude chat (PixelLab) — textures de l'Artisanat sacré et profane

> Les textures ci-dessous sont des **versions provisoires générées par code** (Claude Code) : elles fonctionnent en jeu mais restent simples. À remplacer par de vraies textures PixelLab, **en gardant exactement les mêmes noms de fichiers** : rien à changer dans le code.
> Style : celui de la statue du Séraphin (grâce = ivoire / or / blanc ; souillure = pierre sombre violacée / fissures roses lumineuses). Pixel art net, pas de dégradés flous.

## 1. Textures de blocs (`src/main/resources/assets/wavesurvivor/textures/block/`, 16 × 16 ou 32 × 32)

### Marbre béni (blanc veiné d'or)
| Fichier | Usage |
|---|---|
| `blessed_marble.png` | bloc de base, escaliers, dalles, murets |
| `blessed_marble_polished.png` | marbre poli (surface lisse, liseré clair) |
| `blessed_marble_chiseled.png` | marbre sculpté (cadre doré + symbole de soleil / halo au centre) |
| `blessed_marble_column_side.png` / `_column_top.png` | colonne (cannelures verticales ; dessus : anneau d'or) |

### Pierre profanée (pierre sombre + fissures roses lumineuses)
Chaque variante a **2 fichiers** : le fond, et un **calque lumineux** `_glow` = **uniquement les fissures roses sur fond transparent** (le jeu l'affiche à pleine luminosité par-dessus le fond, comme les yeux des Corrompus). Le fond (`tainted_stone.png`…) contient la pierre sombre **avec les fissures en version mate** (elles se voient aussi sur les escaliers, dalles et murets, qui n'ont pas de calque) ; les `_glow` sont superposés par le modèle et font briller les mêmes fissures.
| Fond | Calque lumineux |
|---|---|
| `tainted_stone.png` | `tainted_stone_glow.png` |
| `tainted_stone_polished.png` | `tainted_stone_polished_glow.png` |
| `tainted_stone_chiseled.png` (cadre + œil) | `tainted_stone_chiseled_glow.png` |
| `tainted_stone_column_side.png` | `tainted_stone_column_side_glow.png` |
| `tainted_stone_column_top.png` | `tainted_stone_column_top_glow.png` |
> Remarque : escaliers, dalles et murets de pierre profanée n'ont pas (encore) de calque lumineux : ils utilisent `tainted_stone.png` tel quel ; prévoir que ce fichier reste joli seul.

### Blocs utilitaires
| Fichier | Usage / idée |
|---|---|
| `grace_lantern.png` | lanterne de grâce (feuille UV de la **lanterne vanilla** : garder la même disposition) — or pâle, flamme blanche |
| `votive_candle.png` / `votive_candle_lit.png` | bougie votive éteinte / allumée (disposition de la **bougie vanilla**) — cire ivoire, liseré or |
| `holy_fountain_side.png`, `holy_fountain_water.png` | fontaine sacrée : marbre + frise d'or ; eau bleu-blanc lumineuse |
| `bait_totem_side.png`, `bait_totem_face.png` | totem d'appât : os violacé ; la face = tête avec un **œil rose** (affichée sur les 4 côtés de la tête) |
| `profane_obelisk.png`, `profane_obelisk_glow.png` | obélisque : pierre sombre ; calque = **runes roses** verticales lumineuses |
| `blood_altar_side.png`, `blood_altar_top.png` | autel de sang : pierre rouge sombre ; dessus = bassin de **sang** |

## 2. Modèles 3D des blocs utilitaires (si tu peux les affiner : `assets/wavesurvivor/models/block/<nom>.json`)
Formes actuelles (en 1/16 de bloc) : **fontaine** = bassin 16×7×16 + eau + colonne centrale 4×6×4 + vasque ; **totem d'appât** = socle 10×3×10, fût 6×8×6, tête 12×5×12 ; **obélisque** = socle 12×3×12, fût 6×10×6, pointe 3×3×3 ; **autel de sang** = socle 16×9×16 + bassin 14×3×14. La lanterne et la bougie reprennent les modèles vanilla. Toute amélioration de forme est la bienvenue (garder la hauteur ≤ 16 et l'emprise dans le bloc ; les boîtes de collision sont dans `CraftUtilityBlock.getShape`).

## 3. Icônes d'objets (`textures/item/`, **32 × 32**, fond transparent)
| Fichier | Objet |
|---|---|
| `tainted_arrow.png` | Flèche souillée (flèche sombre, pointe rose, plumes noires) — **manquante** |
| `taint_totem.png` | Totem de souillure (totem d'immortalité version violette / noire, fissures roses) — **manquante** |
| `seraph_helmet.png`, `seraph_chestplate.png`, `seraph_leggings.png`, `seraph_boots.png` | Armure du Séraphin (blanc, argent et or, ailes) — provisoires actuelles : icônes de diamant recolorées |
| `blessed_bread.png` | déjà faite et branchée |

## 4. Décor porté (déjà fait)
`textures/models/armor/seraph_armor.png` (512 × 128) pour le modèle 3D de l'armure : ne pas toucher.

## 5. Comment livrer
Déposer les PNG sous **exactement ces noms** dans les dossiers indiqués (écrasent les provisoires), puis prévenir Claude Code pour qu'il recompile et vérifie en jeu.
