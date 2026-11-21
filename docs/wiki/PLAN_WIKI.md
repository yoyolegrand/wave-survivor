# Wiki Wave Survivor sur moddedmc.wiki — plan et mise en ligne

## Ce qu'il faut pour publier (doc officielle : docs.moddedmc.wiki › Portal › Publishing)

1. **Le dossier `wiki/` dans le dépôt GitHub public** `yoyolegrand/wave-survivor`, sur la branche par défaut (`main`). Il est déjà prêt sur le disque : Claude Code doit seulement le committer et le pousser sur `main`.
2. **`wiki/sinytra-wiki.json`** (déjà écrit) :
   - id `wave-survivor`, modid `wavesurvivor` ;
   - plateforme CurseForge `wave-survivor` (Modrinth n'est pas mis : le projet est toujours en validation, l'inscription échouerait) ;
   - `owners: ["yoyolegrand"]`.
   - On ne met pas de champ licence : la « Custom License » de CurseForge reste telle quelle.
3. **CurseForge** : réglages du projet › onglet `Source` › `Repository URL` = `https://github.com/yoyolegrand/wave-survivor`. C'est le contrôle de propriété : l'URL doit correspondre au dépôt.
4. **Inscription** (Yoann, avec son compte) :
   - aller sur moddedmc.wiki, cliquer `Log in` et se connecter avec GitHub ;
   - autoriser l'application GitHub (permissions minimales) ;
   - cliquer `New Project` et remplir :
     - Repository URL : `https://github.com/yoyolegrand/wave-survivor`
     - Branch : `main`
     - Path : `/wiki`
   - cliquer `Submit`.
   - Le déploiement démarre tout seul. Ensuite, chaque push sur `main` met le wiki à jour.

## Structure (39 pages, anglais + français)

Pages générées depuis le **Codex du jeu** (textes officiels EN/FR de `wslang`), complétées depuis les données :
- **Accueil** (`_index.mdx`, écrit à la main).
- **Getting started.**
- **Hordes** : Classic hordes, Difficulty & Mutators (+ liste complète), Bosses, Breaches/Anomalies/Chaos, Ready-made arenas, Frozen Peaks, Burning Sands.
- **Game modes** :
  - Kingdom : vue d'ensemble, trésor, Monolithe et Mairie, défenses, atelier, rôles (9 détaillés), Vocations, alchimie (5 fioles), présages (10), objectifs du Calme, carte/boussole/éclaireur ;
  - Invasions, Boss Rush, Endless, Daily Challenge ;
  - The Rift : vue d'ensemble, créatures, îlots.
- **Progression** : Blessings & Pacts, Renaissance, Relics & Forge, Statistics.
- **World & items** : Seraph Shrine, Sacred & Profane Crafting, Ruins, Vestige Compass, Key Pouch.
- **Customization** : éditeur de hordes.
- **Interface & commands**, **Compatibility** (écrite à la main).

Hors wiki pour l'instant : Conquest et l'Astrolabe de fusion (pas encore sortis).

## Phase 2 (après la mise en ligne)

- **Images** : captures en jeu, visuels déjà faits (kit, HUD, ciel, forge…), icônes d'objets dans `wiki/assets/wavesurvivor/item/` (le wiki cherche `wavesurvivor:item/<id>`).
- **Pages « contenu »** (`wiki/content/`) avec infobox et recettes pour les objets et blocs : reliques, légendaires, clés, objets du désert, Armure de la Faille, armure du Séraphin… Le plugin Gradle du wiki peut exporter les rendus et les recettes automatiquement : c'est une tâche pour Claude Code.
- **Une page par horde**, avec les vagues, les boss et les butins.
- Une **FAQ**.
- Ajouter Modrinth dans `platforms` dès que le projet est validé.
- **Mise à jour** : le script `docs/wiki/build_wiki.py` régénère les pages depuis `wslang` à chaque version.
