# Arène « Nécropole des Rois Déchus » — fiche de construction et d'intégration

> Rédigée par Claude chat avec Yoann. Première arène « toute faite » (feuille de route 1.8 : *Ready-made arenas*).
> Elle doit servir de **modèle** pour les arènes des autres hordes : le format du plan et les repères seront les mêmes.

Fichiers :
- `docs/arenes/necropole/blueprint_necropole.json` : **le plan bloc par bloc** (source de vérité).
- `docs/arenes/necropole/arene_necropole.png` : vue en perspective (référence visuelle).
- `docs/arenes/necropole/arene_necropole_plan.png` : plan vu de dessus, numéroté (nord en haut).

---

## 1. Concept

Un **cimetière en ruine** de pierre des abîmes, éclairé au **feu des âmes**, autour de l'autel de la horde de la **Nécropole**.
- Cercle de **31 × 31 blocs**, **10 blocs** de haut au plus (toit de la crypte).
- Au centre : l'**autel / Monolithe** de la horde sur un piédestal, entouré de 4 braseros (feux de camp des âmes).
- Une **couronne de 14 tombes** (stèles + tertres) : abris pour le joueur et points d'où les morts « jaillissent ».
- Au **nord** : une **crypte** (mausolée 9 × 6) à portail en grille de fer, gardée par deux statues d'os : sortie principale des vagues et entrée du boss.
- Une **enceinte en ruine** (muret brisé, brèches) avec **8 colonnes** à lanternes des âmes et toiles d'araignée, **4 entrées** dans l'axe des allées en croix, **3 arbres morts**.

## 2. Lire le plan (`blueprint_necropole.json`)

- `size` : x = 31, y = 10, z = 31.
- **Origine** : coin **nord-ouest**, au niveau du sol de l'arène (x = 0, y = 0, z = 0).
- **Axes** (convention Minecraft) : x vers l'**est**, z vers le **sud**, y vers le **haut**.
- `layers[y][z]` est une chaîne de 31 caractères, un par bloc de x = 0 à 30. `layers[0]` est la couche du sol.
- `palette` : chaque caractère → un état de bloc Minecraft (avec ses propriétés, ex. `minecraft:soul_lantern[hanging=false]`). `.` = air.
- Les caractères : `T` dalles des abîmes, `t` dalles fissurées, `B` briques des abîmes, `K` pierre noire polie, `C` pierre noire polie sculptée, `m` pierre moussue, `s` briques de pierre (stèles, allées), `o` terre des âmes, `a` sable des âmes, `p` podzol, `d` terre stérile, `b` bloc d'os, `F` feu de camp des âmes, `L` lanterne des âmes, `I` barreaux de fer, `W` bûche de chêne noir, `w` toile d'araignée, `c` bougies.

### Repères (`markers`, coordonnées relatives à l'origine)
| Repère | Position | Rôle |
|---|---|---|
| `monolith` | (15, 3, 15) | L'autel/Monolithe de la horde se pose ici, sur le bloc sculpté du piédestal (y = 2). **5 blocs de haut réservés** (y 3..7) : aucun bloc du plan à cet endroit. |
| `crypt_gate` | (15, 1, 6) | Portail de la crypte (3 de large, grille y 1..3), tourné vers le centre (sud). |
| `crypt_spawn` | (15, 1, 4) | Intérieur de la crypte : apparition des monstres et du **boss**. |
| `entrances` | 4 positions | Les 4 entrées de l'enceinte (bout des allées). |
| `grave_spawns` | 14 positions (y = 2) | Tertres devant les stèles : apparitions « qui jaillissent du sol ». |
| `soul_braziers` | 4 positions | Les 4 feux de camp des âmes autour du Monolithe. |

## 3. Construire la structure

**Méthode conseillée (sans WorldEdit)** :
1. Copier `blueprint_necropole.json` dans `src/main/resources/data/wavesurvivor/arenas/necropole_rois_dechus.json`.
2. Écrire un **chargeur de plan** (`ArenaBlueprint`) : lit la palette (`BlockStateParser` ou équivalent), les couches et les repères ; méthode `place(ServerLevel, BlockPos origin, Rotation rot)` qui pose chaque bloc (y compris l'air, pour vider le volume) avec rotation des positions **et** des propriétés (`axis`, etc.).
3. Commande d'opérateur de développement : `/ws arena place necropole_rois_dechus [rotation]` → pose l'arène devant le joueur, pour la tester et l'ajuster.
4. *(Optionnel, pour la génération vanilla)* exporter ensuite en **`.nbt`** avec un bloc de structure, ou générer le `StructureTemplate` directement depuis le plan.

**À l'endroit choisi** :
- **Fondations** : sous chaque bloc de la couche 0, combler jusqu'au terrain solide (pierre des abîmes concassée / terre) pour que l'arène ne flotte jamais.
- **Dégagement** : vider l'air sur tout le cercle (rayon 15,5) jusqu'à y = 12, arbres et herbes compris.
- **Raccord** : anneau de 2 blocs autour (rayon 15,5 à 17,5) en podzol / terre stérile / mousse, pour fondre l'arène dans le paysage.
- **Contenu** : feux de camp allumés, bougies allumées ; aucun coffre ni générateur de monstres dans le plan.

## 4. Génération dans le monde

- **Biomes** : forêt sombre, marais, marais de mangrove, taïga ancienne, plaines (liste en config).
- **Rareté** : environ **1 pour 1 500 blocs** (espacement de structure ≈ 64 tronçons, séparation 24 ; en config).
- **Terrain** : refuser les zones trop pentues (écart de hauteur > 4 sur la zone), l'eau, et la proximité d'un village ou d'une autre arène.
- **Rotation aléatoire** (0 / 90 / 180 / 270°) : la crypte n'est pas toujours au nord.

## 5. Affiliation à la horde de la Nécropole

- Au repère `monolith`, poser l'**autel de horde du mod**, **verrouillé sur la horde de la Nécropole** (vérifier son identifiant dans la config des hordes ; « Burning Sands » s'y réfère comme « la Nécropole »). Le joueur ne peut pas le reconfigurer pour une autre horde.
- **L'autel est incassable** (comme la ruine du Sanctuaire) ; le reste de l'arène est cassable normalement.
- Une partie lancée sur cet autel :
  - **Apparitions** : de préférence dans la **crypte** (`crypt_spawn`) et sur les **tombes** (`grave_spawns`, avec particules de terre + son de pelle/de zombie qui sort du sol), plutôt que tout autour au hasard. Garder quelques apparitions aux `entrances` pour varier.
  - **Boss** : apparaît au `crypt_spawn`, la **grille du portail s'ouvre** (barreaux retirés pendant le combat, reposés à la fin).
  - **Ambiance** (facultatif) : les 4 braseros s'éteignent un par un au fil des vagues et se rallument à la victoire.
  - **Récompense** (facultatif) : un **coffre de la Nécropole** (coffre roulette) apparaît dans la crypte après la victoire, une fois par arène.
- L'arène est **réutilisable** : la horde peut être relancée ; le coffre de récompense, lui, n'est donné qu'à la première victoire.

## 6. Pour les arènes suivantes

Garder le même format : un `blueprint_<nom>.json` (palette + couches + repères `monolith`, `crypt_spawn`/`boss_spawn`, `spawns`, `entrances`) et une fiche comme celle-ci. Claude chat peut fournir les plans des autres hordes (Sables Ardents, Néant, Pics Gelés, Corrompus…).

## 7. Implémentation (Claude Code)

Code : `com.wavesurvivor.arena` (`ArenaBlueprint` = chargeur et pose du plan, `ArenaCommand` = `/ws arena list|place`, `ArenaStructure` + `ArenaPiece` = génération du monde, `ArenaAltar` = autel verrouillé, `ArenaSession` = apparitions, grille, braseros, coffre, `ArenaSettings` = config).
- Le plan est lu dans `data/wavesurvivor/arenas/<id>.json` (copie du blueprint) ou `config/wavesurvivor/arenas/<id>.json` (prioritaire). Champ ajouté : `"hordeName": "La Nécropole"` (nom exact de la horde dans la config).
- Une **nouvelle arène** = un plan JSON + son identifiant dans `ArenaCommand.BUILTIN` + trois fichiers de données (`worldgen/structure/arena_<nom>.json` avec `"arena": "<id>"`, `worldgen/structure_set/arena_<nom>.json`, `tags/worldgen/biome/has_structure/arena_<nom>.json`).
- Rareté : `structure_set` (spacing 64 / separation 24, à l'écart des villages) ; biomes : le tag ; options : `config/wavesurvivor/arenas.json` (`generation` : enabled, maxSlope, randomRotation, disabled ; `affinity` : cryptWeight, graveWeight, entranceWeight, gateOpens, braziersFade, rewardChest, rewardChestName).
- L'autel est marqué (identifiant, rotation, origine) à la pose ; à la première utilisation il est lié à la horde du plan et verrouillé (`AltarStore.AltarEntry.locked`).
