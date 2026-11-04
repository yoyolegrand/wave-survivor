# Brief pour Claude chat — plan de la ruine « L'Autel souillé » (1.9)

> Deuxième ruine après la Chapelle (`docs/ruines/chapelle/RUINE_CHAPELLE.md`, le modèle à suivre). Claude Code fait le code (génération, butin, mini-vague) **dès que le plan est livré** ; il ne dessine pas les structures.

## 1. Livrable
Dans `docs/ruines/autel_souille/` : `blueprint_autel_souille.json` (même format que la Chapelle : JSON compact, `layers[y][z]` = ligne de x, `.` = ne rien poser, `ground_y` = 0), une fiche `RUINE_AUTEL_SOUILLE.md` et des captures. Le fichier est lu tel quel par `ArenaBlueprint` : respecter exactement les clés de la Chapelle (`name`, `type`, `version`, `size`, `ground_y`, `palette`, `layers`, `markers`).

## 2. Idée (fiche de design validée)
Ruine **rare** en pierre profanée, plus sombre et plus menaçante que la Chapelle, pour **marais, forêts sombres** (et marais de mangrove si possible). Un autel, un obélisque, un coffre de **fragments de souillure** ; une **mini-vague de Corrompus** (Rootlings, parfois un Stone Brute) se déclenche quand un joueur approche. Plus petite que la Chapelle (≈ 25 × 25 ou moins), mais marquante : ruines noircies, racines et terre stérile qui mangent la pierre, fissures roses qui brillent.

## 3. Blocs du mod utilisables (noms EXACTS — une erreur de nom empêche tout le plan de se charger)
- Pierre profanée (fissures roses lumineuses) : `wavesurvivor:tainted_stone`, `wavesurvivor:tainted_stone_stairs`, `wavesurvivor:tainted_stone_slab`, `wavesurvivor:tainted_stone_wall`, `wavesurvivor:tainted_stone_column` (`axis=y`), `wavesurvivor:polished_tainted_stone`, `wavesurvivor:chiseled_tainted_stone`. **Attention à l'ordre des mots** : `polished_…` et `chiseled_…` s'écrivent en tête.
- Blocs utilitaires : `wavesurvivor:profane_obelisk` (**bloc double de 2 blocs de haut** : poser `wavesurvivor:profane_obelisk[half=lower]` ET, juste au-dessus, `wavesurvivor:profane_obelisk[half=upper]`), `wavesurvivor:blood_altar`, `wavesurvivor:bait_totem` (décor possible ; leurs effets sont actifs en jeu : l'obélisque et l'autel de sang ne gênent pas, le totem d'appât attire les monstres seulement quand un joueur l'active).
- Bougie votive : `wavesurvivor:votive_candle[lit=true]` ; on peut grouper 1 à 4 bougies sur un bloc avec `candles=2`, `candles=3` ou `candles=4` (ex. `wavesurvivor:votive_candle[candles=3,lit=false]`). Lanterne : `wavesurvivor:grace_lantern[hanging=true|false]`.
- Bloc de grâce à éviter ici (c'est la palette de la Chapelle) ; pour l'ambiance : deepslate, blackstone, mossy cobblestone, coarse dirt, podzol, mud, mangrove roots, dead bush, cobweb, soul fire / soul lantern, etc. (blocs vanilla : états valides 1.20.1).
- Les états `facing=` des escaliers, portes… sont tournés automatiquement avec la rotation aléatoire du plan.

## 4. Repères (`markers`) à fournir
Même syntaxe que la Chapelle (et pas d'autre : le lecteur ignore ce qu'il ne comprend pas) :
- `chest` : `{ "pos": [x,y,z] }` — le coffre de butin (un bloc `minecraft:chest[facing=…]` dans la palette, à cette position).
- `corrupted_spawns` : liste de **6 à 8** positions `[[x,y,z],…]` où les Corrompus surgissent (autour de l'autel, sur le sol de la ruine, jamais dans un mur).
- `trigger` : `{ "pos": [x,y,z] }` — le centre de la zone d'approche (l'autel) ; le rayon de déclenchement est réglé côté code (≈ 10 blocs).
- `altar` : `{ "pos": [x,y,z] }` — le bloc principal (autel / obélisque).
Positions en coordonnées du plan (`y` = 0 au sol).

## 5. Génération (réglée côté code)
Terrain à peu près plat (écart ≤ 3 blocs), pas d'eau profonde : le plan est posé sur le sol, les fondations sont comblées dessous et les bords raccordés par un talus d'herbe. Si la ruine doit « mordre » dans le marais, prévoir des blocs de terre / boue au niveau y = 0 plutôt que de l'eau.

## 6. Le gameplay (rappel, pour guider le dessin)
- Butin du coffre : fragments de souillure (Éclats surtout, parfois un Fragment, rarement un Cœur de souillure).
- Mini-vague : Rootlings, parfois un Stone Brute, qui surgissent des `corrupted_spawns` quand un joueur entre dans la zone ; une seule fois par ruine.
- Prévoir un **point de vue dégagé** devant l'autel pour que le combat soit lisible, et un ou deux **abris** (pans de mur) pour se replier.
