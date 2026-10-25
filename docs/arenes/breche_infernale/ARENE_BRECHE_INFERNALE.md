# Arène « La Brèche Infernale » — fiche de construction et d'intégration

> Rédigée par Claude chat avec Yoann. Deuxième arène toute faite, pour la **horde du Nether**, dans l'**Overworld**.
> **Même format que la Nécropole** : lire d'abord `docs/arenes/necropole/ARENE_NECROPOLE.md` (§2 lecture du plan, §3 chargeur `ArenaBlueprint`, fondations, dégagement, raccord). Réutiliser le même chargeur.

Fichiers :
- `docs/arenes/breche_infernale/blueprint_breche_infernale.json` : **le plan bloc par bloc** (source de vérité).
- `docs/arenes/breche_infernale/arene_breche_infernale.png` : vue en perspective.
- `docs/arenes/breche_infernale/arene_breche_infernale_plan.png` : plan vu de dessus, numéroté (nord en haut).

---

## 1. Concept

Un portail du Nether a **déchiré** l'Overworld et le Nether **déborde** autour : un **cratère de netherrack** de **33 × 33 blocs** (14 de haut au plus, le portail) qui se fond peu à peu dans le paysage.
- Au centre : l'**autel** de la horde du Nether, sur un parvis de pierre noire polie au cœur de pierre noire dorée, avec 3 feux.
- Au **nord** : un **portail géant en ruine** (obsidienne + obsidienne pleureuse, cadre brisé, or incrusté, éclats au sol) sur une estrade de netherrack. Les vagues et le boss en sortent.
- Le **coffre** du portail en ruine (butin **vanilla**).
- **5 failles de magma** qui rayonnent depuis le portail : les monstres en jaillissent.
- **3 mares de lave** cerclées de pierre noire, **6 colonnes de basalte** penchées, une **forêt carmin** à l'ouest (nylium, racines, champignon carmin géant avec champilumières), des **feux** sur le netherrack.
- **Bord du cratère** : netherrack mêlé de terre stérile, gravier et herbe, rochers épars.

## 2. Le plan (`blueprint_breche_infernale.json`)

Même lecture que la Nécropole : origine au **coin nord-ouest** au niveau du sol, x vers l'**est**, z vers le **sud**, y vers le **haut**, `layers[y][z]` = ligne de x = 0..32, `size` = 33 × 14 × 33.

Caractères : `N` netherrack, `M` bloc de magma, `K` pierre noire, `P` pierre noire polie, `G` pierre noire dorée, `b` basalte, `B` basalte poli, `O` obsidienne, `Y` obsidienne pleureuse, `n` nylium carmin, `S` tige carmin, `w` bloc de verrue du Nether, `h` champilumière, `f` feu, `L` lave (sources), `g` bloc d'or, `d` terre stérile, `v` gravier, `r` bloc d'herbe, `C` coffre (face au sud), `R` racines carmin, `.` air.

### Repères (`markers`)
| Repère | Position | Rôle |
|---|---|---|
| `monolith` | (16, 3, 16) | L'autel de la horde du Nether se pose ici, sur la pierre noire dorée du piédestal (y = 2). **5 blocs de haut réservés** (y 3..7). |
| `portal_spawn` | (16, 2, 6) | Devant l'intérieur du portail, sur l'estrade : apparition principale des vagues. |
| `boss_spawn` | (16, 3, 5) | Au cœur du portail : le boss en sort. |
| `chest` | (21, 2, 6) | Coffre du portail en ruine, table de butin **`minecraft:chests/ruined_portal`** (à appliquer à la pose, avec une graine aléatoire). |
| `rift_spawns` | 5 positions (y = 1) | Bout des failles de magma : apparitions « qui jaillissent » (particules de lave + fumée + son). |
| `lava_pools` | 3 positions (y = 0) | Centres des mares de lave : ne jamais y faire apparaître de monstres. |

## 3. La greffe sur les portails en ruine

**But** : une partie des **portails en ruine vanilla sur la terre ferme** sont **remplacés** par la Brèche. Le joueur ne perd rien : la Brèche contient elle aussi un portail géant en ruine, de l'or et le coffre au butin vanilla, plus l'autel et l'environnement.

**Quels portails** : seulement la variante **standard posée en surface** (et désert), dans des biomes terrestres cohérents : plaines, plaines de tournesols, prairie, forêts (dont bouleaux), taïga, savanes, désert, badlands, plaines enneigées. **Jamais** : océan, jungle, marais, montagnes / pics, Nether, souterrains.

**Proportion** : **25 %** des portails éligibles (réglable en config, 0 = désactivé).

**Deux méthodes possibles** (choisir la plus fiable après essai) :
- **A. Structure jumelle, données seulement (conseillée en premier)** : déclarer une structure `wavesurvivor:infernal_breach` (biomes ci-dessus via un tag `wavesurvivor:has_structure/infernal_breach`) et un *structure set* `wavesurvivor:infernal_breaches` **avec le même placement que `minecraft:ruined_portals`** (mêmes `spacing`, `separation` et **même `salt`** que la version vanilla 1.20.1, à relever dans les données vanilla), plus `frequency: 0.25`. Puis **surcharger** `data/minecraft/worldgen/structure_set/ruined_portals.json` en y ajoutant une `exclusion_zone` vers `wavesurvivor:infernal_breaches` (`chunk_count: 1`). Résultat : là où la Brèche se place, le portail vanilla ne se génère pas ; ailleurs, rien ne change. Vérifier en jeu que la règle de fréquence est bien prise en compte par l'exclusion.
- **B. Remplacement direct (si A ne convient pas)** : intercepter la génération de `RuinedPortalStructure` (mixin ou équivalent) : quand la variante choisie est « standard en surface » dans un biome éligible, tirer 25 % avec un aléa **déterministe** (graine du monde + position du tronçon) et, si c'est le cas, remplacer la pièce vanilla par une `BreachPiece` qui pose le plan.

**Dans les deux cas** :
- Centrer le plan sur la position du portail ; rotation aléatoire (0 / 90 / 180 / 270°).
- **Terrain** : refuser si l'écart de hauteur dépasse 5 blocs sur la zone (le portail vanilla reste alors normal). Fondations, dégagement (jusqu'à y = 15) et raccord comme pour la Nécropole.
- La lave et le feu ne doivent **pas** se propager au-delà de l'arène à la génération (poser les mares en dernier, entourées de pierre noire comme dans le plan).

## 4. Affiliation à la horde du Nether

- Au repère `monolith`, poser l'**autel de horde du mod**, **verrouillé sur la horde du Nether** (vérifier son identifiant dans la config des hordes). Autel **incassable** ; le reste de l'arène se casse normalement.
- **Apparitions** : principalement au `portal_spawn` et aux `rift_spawns`, quelques-unes au bord du cratère. Jamais dans les `lava_pools`.
- **Boss** : au `boss_spawn`. Effet conseillé : l'intérieur du portail **s'illumine** (blocs de portail du Nether temporaires ou particules violettes denses) à l'arrivée du boss, puis s'éteint à la victoire.
- **Ambiance** (facultatif) : les feux reprennent à chaque vague ; à la victoire, une partie du magma refroidit en pierre noire.
- **Récompense** : le coffre vanilla est déjà là ; en option, un **coffre infernal** (coffre roulette) dans le portail à la **première** victoire.

## 5. Plus tard

Une version **Nether** plus rare et plus difficile, la **Citadelle de Basalte** (au-dessus d'un lac de lave), réutilisera le même format. Claude chat peut en fournir le plan.

## 6. Implémentation de la greffe (Claude Code) — méthode C

La méthode A (structure jumelle + `exclusion_zone`) a été écartée : l'exclusion se calcule sur les emplacements POSSIBLES de l'ensemble jumeau, que sa structure réussisse ou non. Avec une fréquence de 25 %, elle aurait supprimé un quart des portails vanilla dans les biomes non éligibles (océans, jungles, Nether…) et là où le terrain est refusé. La méthode B (mixin) n'est pas nécessaire :
- les fichiers vanilla `minecraft:ruined_portal` et `ruined_portal_desert` sont **remplacés** (`data/minecraft/worldgen/structure/`) par une structure `wavesurvivor:breach_graft` (`arena/BreachGraftStructure`) ;
- les originaux sont recopiés sous `wavesurvivor:ruined_portal_standard_original` / `ruined_portal_desert_original` (champ `delegate`) : la greffe génère d'abord le portail vanilla, puis le remplace par la Brèche seulement si il est en surface, dans un biome éligible, tiré à 25 % (déterministe : graine du monde + position) et si le terrain convient (`maxSlope` du plan = 5) ; sinon le portail vanilla reste tel quel ;
- réglages : `arenas.json` › `graft` (proportion), `generation` (activer / désactiver) ; biomes : tag `has_structure/infernal_breach`.
