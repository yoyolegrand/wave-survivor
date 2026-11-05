# Ruine « L'Autel souillé » — structure du monde (1.9)

> Rédigée par Claude chat avec Yoann (visuel validé). Deuxième des 3 ruines (`docs/DESIGN_ACCUEIL_ET_RUINES.md` §2). Pendant **profane** de la chapelle : un lieu de rituel corrompu, sombre et brisé.

Fichiers (dans `docs/ruines/autel_souille/`) :
- `blueprint_autel_souille.json` — plan bloc par bloc (même format que les arènes et la chapelle), 35 × 8 × 35, 31 blocs différents, ~12 Ko.
- `autel_souille.png` — vue d'ensemble (2 angles).

## 1. Contenu
- **Plateforme circulaire** de 25 blocs de diamètre : briques de pierre noire polie (en partie fissurées) mêlées de deepslate polie, anneau extérieur en briques de deepslate abîmées, quelques dalles arrachées (sol des âmes).
- **Accents profanes seulement** (≈ 7 % des blocs) : un **cercle gravé** en `tainted_stone_chiseled` et **6 fins rayons** en `tainted_stone` qui vont vers les obélisques.
- **6 obélisques profanes** en cercle (`profane_obelisk` posé en y = 1 : c'est la **moitié basse** ; la moitié haute du bloc double se pose automatiquement).
- **Estrade centrale** à gradins (escaliers de briques de pierre noire polie) : **autel de sang** au sommet, encadré de 2 **totems d'appât** ; le **coffre** est **caché sous l'autel** (dans l'estrade).
- **Colonnes brisées** en basalte (certaines debout avec chapiteau, deux effondrées), **2 arches en ruine** en deepslate avec **clé de voûte profane** et **chaînes** (l'entrée au nord, l'autre à moitié écroulée).
- **Terrain corrompu** autour : boue, sol des âmes, un peu de sculk, podzol, racines cramoisies, buissons morts, **arbres morts** (troncs de chêne noir).

## 2. Repères (`markers`)
- `chest` : coffre caché sous l'autel (il faut casser une partie de l'estrade pour l'atteindre — c'est voulu, petite récompense pour les curieux ; option : le révéler quand le rituel est vaincu, voir §4).
- `blood_altar`, `obelisks` (6 positions), `corrupted_spawns` (8 points sur l'anneau, entre les obélisques), `trigger` (centre + rayon 16), `entrance` (au nord).

## 3. Butin (table réglable)
- Coffre : 6 à 10 Éclats de souillure, 1 à 2 Fragments de souillure, 15 % de chance d'un Cœur de souillure, + quelques réactifs sombres (os, poudre d'os, larmes de Ghast rares).
- Pillable une seule fois.

## 4. Le rituel (mini-vague de Corrompus)
- Quand un joueur entre dans le rayon de `trigger` pour la première fois : les **obélisques s'illuminent**, un son grave, puis une **mini-vague de Corrompus** apparaît aux `corrupted_spawns` (Rootlings surtout, parfois un Stone Brute ; nombre et types réglables, suivant la difficulté).
- Optionnel (réglable) : quand la mini-vague est vaincue, l'**estrade s'ouvre** (le bloc au-dessus du coffre devient de l'air) pour révéler le coffre.
- Les **totems d'appât** de l'estrade attirent les Corrompus vers l'autel pendant le rituel.
- Une seule fois par autel.

## 5. Génération et pose
- **Rare** (réglable) : marais, marais de mangroves, forêts sombres — terrain assez plat (refuser au-delà de 3 blocs d'écart).
- Pose comme les arènes : vider l'emprise au-dessus, poser le plan, combler dessous, raccorder les bords ; rotation aléatoire 0 / 90 / 180 / 270° (tourner repères, escaliers, colonnes couchées).
- Codex « Ruines » + onglet Ressources du Carnet (« Fragments de souillure : autels souillés »).
