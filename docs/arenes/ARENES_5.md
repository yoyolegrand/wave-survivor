# Arènes des hordes — L'Îlot du Néant, le Récif englouti, le Temple de Rê, le Bastion de Givre, le Cercle des Arcanes

> Rédigée par Claude chat avec Yoann. Suite des arènes toutes faites (après la Nécropole et la Brèche Infernale).
> **Même format et même chargeur** (`ArenaBlueprint`) : lire d'abord `docs/arenes/necropole/ARENE_NECROPOLE.md` (§2 lecture du plan, §3 construction : fondations, dégagement, raccord, commande `/ws arena place`).

Chaque arène a son dossier `docs/arenes/<nom>/` avec : `blueprint_<nom>.json` (le plan, source de vérité), `arene_<x>.png` (perspective) et `arene_<x>_plan.png` (plan numéroté, nord en haut). Vue d'ensemble : `docs/arenes/apercu_5_arenes.png`.

## Différences de format avec la Nécropole (à gérer dans le chargeur)
- **`ground_y`** : le sol de l'arène n'est plus forcément à y = 0 (l'Îlot du Néant a un dessous en cône, le Récif un fond de lagon). L'origine est le **bas** de la structure ; le sol est à `y = ground_y`. Placer la structure pour que `ground_y` tombe au niveau du terrain.
- **Palette par arène** : les caractères (`a`, `b`, …) sont propres à chaque plan, toujours lire la palette du fichier.
- Repères communs : `monolith` (autel, 5 blocs réservés), `boss_spawn`, `spawns` (liste). Repères optionnels : `shelters` (abris, avec leur type), `chest` (coffre + table de butin).

---

## 1. L'Îlot du Néant — horde de l'End (`docs/arenes/ilot_du_neant/`)
- **Où** : îles extérieures de l'End (biomes `end_highlands`, `end_midlands`, `small_end_islands`), **en l'air** : l'arène est une **île flottante** posée telle quelle (son dessous en cône fait partie du plan), **sans fondations** jusqu'au sol. Ne pas générer dans l'île centrale (dragon).
- **Rareté** : comparable aux cités de l'End, sans jamais chevaucher une cité.
- **Contenu** : arche de cité de l'End en ruine (nord), 2 piliers d'obsidienne à « cristal » en cage (obsidienne pleureuse entourée de barreaux — un vrai cristal de l'End exploserait), chorus, barres de l'End autour de l'autel.
- **Repères** : `boss_spawn` sous l'arche ; `spawns` = 5 **failles du vide** (anneaux d'obsidienne au centre noir) : particules de portail + son d'Enderman.
- **Danger voulu** : le **bord de l'île** (chute dans le vide). Ne pas ajouter de barrière.

## 2. Le Récif englouti — horde aquatique (`docs/arenes/recif_englouti/`) — **VERSION 2** (35 × 35)
- **Où** : **plages** et bords d'**océans chauds / tièdes** (`beach`, `warm_ocean`, `lukewarm_ocean` peu profonds). L'eau du lagon est au niveau de la mer : `ground_y` = niveau de la mer (y = 62-63).
- **Contenu (v2)** : une vraie **île en relief** (dunes, plage en pente douce, rochers moussus, 4 palmiers penchés) entourée d'un **lagon de 2 blocs de profondeur** avec **récifs de corail, herbiers, varech et cornichons de mer, tous SOUS l'eau** (vivants) ; autel sur une **estrade à marches** de prismarine (4 lanternes aquatiques) ; **temple en ruine** (nord) sur une estrade à marches, colonnes inégales, arche effondrée, blocs tombés ; 3 **chenaux de marée** vers le lagon ; une **épave** (sud-est) au bord du lagon : coque creuse qui s'évase, château arrière, proue relevée et beaupré, brèche dans le flanc, pont crevé, mât avec vergue et voile déchirée, légère gîte. Gros plan : `epave_gros_plan.png`.
- **Repères** : `monolith` sur l'estrade (y plus haut que dans les autres arènes, lire la position) ; `boss_spawn` dans le temple ; `chest` dans le temple, table **`minecraft:chests/underwater_ruin_big`** ; `wreck_chest` dans la cale de l'épave, table **`minecraft:chests/shipwreck_treasure`** ; `spawns` = bout des 3 **chenaux de marée** (gerbe d'eau + son de noyé).
- **À vérifier à la pose** : varech (`kelp` sur `kelp` doit devenir `kelp_plant` en dessous), cornichons de mer immergés, aucun corail hors de l'eau.

## 3. Le Temple de Rê ensablé — Sables Ardents (`docs/arenes/temple_de_re/`)
- **Où** : **désert** (`desert`). Option : greffer sur une partie des **temples du désert** vanilla, même méthode que la Brèche sur les portails en ruine (voir `docs/arenes/breche_infernale/ARENE_BRECHE_INFERNALE.md` §3).
- **Contenu** : façade du temple à colonnes et frise orange / bleue (nord), 4 obélisques à pointe d'or, 2 sarcophages, palmiers (bûches + feuilles de jungle persistantes), dunes, cercle de terre cuite bleue.
- **Repères** : `boss_spawn` devant le temple ; `spawns` = 2 **sarcophages** (les Gardes royaux en sortent : couvercle qui s'ouvre / particules de sable).
- **ABRIS contre la TEMPÊTE DE SABLE** (`shelters`) — **obligatoires**, la horde a l'événement tempête de sable :
  - `oasis` : bassin d'eau au sud-est (être **dans l'eau** protège) ;
  - `kiosque` ×2 (ouest et est) : toit plat 5×5 à 4 blocs de haut (être **dessous** protège).
  - **Vérifier en jeu** que la règle d'abri de la tempête (toit au-dessus / eau) reconnaît bien ces trois abris.

## 4. Le Bastion de Givre — Pics Gelés (`docs/arenes/bastion_de_givre/`)
- **Où** : `snowy_plains`, `ice_spikes`, `snowy_slopes` (pas trop pentu).
- **Contenu** : bastion en ruine pris dans la glace (nord) avec porte centrale, pics de glace, cristaux de givre, sapins morts enneigés, sol de glace compacte et de neige, crevasses de glace bleue (glissantes).
- **Repères** : `boss_spawn` à la porte du bastion ; `spawns` = 5 **crevasses** (éclats de glace + son de verre).
- **ABRIS contre les TEMPÊTES** (`shelters`) : `tour` ×2 (tours creuses du bastion, porte au sud, toit à y = 5) et `surplomb` (toit de glace compacte sur deux piliers, au sud-est). Même vérification que pour la tempête de sable.

## 5. Le Cercle des Arcanes — horde de magie (`docs/arenes/cercle_des_arcanes/`)
- **⚠ Condition : n'existe QUE si le mod *Iron's Spells 'n Spellbooks* est installé** (`ModList.get().isLoaded("irons_spellbooks")`). Sans lui : ne pas enregistrer la structure (ou l'ajouter via un pack de données à condition `forge:mod_loaded`), et ne pas lier d'autel. Aucune erreur, aucun bloc manquant.
- **Où** : `forest`, `birch_forest`, `dark_forest`, `plains`, `meadow`.
- **Contenu** : grand cercle runique au sol (anneaux d'améthyste et d'or, rayons de calcite), tour de mage en ruine (nord-ouest) avec bibliothèques et pupitre, 4 pylônes arcaniques (obsidienne pleureuse, verre violet, pierre lumineuse, amas d'améthyste).
- **Repères** : `boss_spawn` dans la tour ; `spawns` = 4 **cercles d'invocation** (cercles d'obsidienne pleureuse : particules d'enchantement + son d'Évocateur).
- **Bonus si Iron's Spells est présent** (facultatif) : remplacer le pupitre par une **table d'inscription** et ajouter une **forge à parchemins** / une **enclume arcanique** dans la tour (vérifier les identifiants exacts dans le mod).

---

## Commun aux cinq
- **Autel** au repère `monolith`, **verrouillé** sur la horde de l'arène (vérifier les identifiants exacts des hordes dans la config) et **incassable** ; le reste de l'arène se casse normalement.
- **Apparitions** : de préférence aux `spawns` et au `boss_spawn`, quelques-unes au bord pour varier.
- **Rotation aléatoire** (0 / 90 / 180 / 270°), **terrain** : refuser au-delà de 5 blocs d'écart de hauteur (sauf l'Îlot du Néant, en l'air).
- **Réglages en config** : activer / désactiver chaque arène, rareté, biomes.
- **Récompense** (option, comme les autres arènes) : un coffre roulette à la première victoire.
