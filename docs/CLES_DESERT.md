# Clés des coffres du désert — nouvelles têtes (1.9)

> Claude chat, suite à `ITEMS_KINGDOM_SABLES_POUR_VISUEL.md`. Visuel validé par Yoann : `docs/coffres_roulette/cles_desert.png` (actuelle / nouvelle). But : que les 6 clés se reconnaissent d'un coup d'œil (les 3 Caravanes étaient identiques).

## 5 nouvelles têtes de clé (même format que les existantes)
Dans `textures/item/roulette_key/` (paquet `cles_desert_pack.zip`, 30 fichiers) :
- `head_<nom>.png` (tête 5 × 5 en 2 tons de gris, teintée par le matériau), `head_<nom>_gem.png` (gemme 1 px), `outline_<nom>_<panneton>.png` × 4 (contour 4-voisins, couleur `#1E1824`, comme les autres).
- Noms : **`ankh`**, **`scorpion`**, **`sun`**, **`leaf`**, **`sword`**.
- Les ajouter à la liste des têtes du système de clés composées (et à l'éditeur, onglet Clé).

## Nouvelles configurations des 6 clés
| Coffre | Tête | Panneton | Matériau | Gemme |
|---|---|---|---|---|
| Reliquaire d'Anubis | **ankh** | double | or | `#2F6FD8` |
| Reliquaire de Sekhmet | **scorpion** | crochet (hook) | cuivre | `#E04A32` |
| Reliquaire de Rê | **sun** | double | or | `#FF7A1E` |
| Caravane des Vivres | **leaf** | simple | fer | `#6DBE45` |
| Caravane des Armes | **sword** | escalier (stairs) | fer | `#E04A32` |
| Caravane des Remèdes | heart | crochet (hook) | fer | `#2BB3A8` |

## Objets utilitaires
`gourde_fraiche`, `fiole_mirage`, `tablette_reconnaissance` : nouvelles icônes **32 × 32** installées (PixelLab, style de la Voile du désert). Les anciennes (script, 16 × 16) sont gardées hors du projet. Rien à coder.
