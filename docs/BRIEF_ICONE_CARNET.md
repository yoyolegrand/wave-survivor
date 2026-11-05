# Brief pour Claude chat (PixelLab) — icône du Carnet du Survivant

Salut ! Peux-tu te connecter à PixelLab et refaire **l'icône du Carnet du Survivant** (objet de Wave Survivor, mod Minecraft Forge 1.20.1) ?

## Fichier à remplacer
`src/main/resources/assets/wavesurvivor/textures/item/survivor_notebook.png`
- **Même nom de fichier**, rien à changer dans le code (le modèle est `item/generated`, un calque unique `layer0`).
- Taille actuelle : **16 × 16** (RGBA, fond transparent). Si PixelLab produit du **32 × 32**, c'est accepté (le jeu réduit proprement) : dis-le-moi et on garde le même nom.

## Ce que c'est
Un carnet de bord offert au joueur à son arrivée : il contient le guide de démarrage, les objectifs, les ressources et la recherche dans le Codex. C'est le « livre d'aventurier » du mod : un petit **carnet de cuir** fermé, tenu par une sangle, avec une **marque dorée** (symbole du mod) sur la couverture.

## Style à respecter
- Pixel art net, contours sombres d'1 pixel, **pas de dégradés flous**, pas d'anti-aliasing.
- Même ambiance que les autres objets du mod (reliques, clés de coffre) : **cuir brun-rouge chaud + liseré or**, éclat doré discret ; lisible à 16 × 16 dans la barre d'objets, **même sur fond sombre**.
- Vu de **3/4 face**, légèrement incliné comme les livres vanilla (dos à gauche, tranche des pages visible à droite en ivoire).
- Une **sangle de fermeture** (cuir plus sombre) avec un petit fermoir doré.
- Sur la couverture : un **petit emblème doré** (une étoile à 4 branches, ou un monolithe stylisé, ou une flamme — au choix, le plus lisible).
- Éviter : le rouge vif du livre vanilla (il doit se distinguer d'un livre enchanté / du livre de recettes), le violet (réservé aux objets de souillure).

## Livraison
Dépose le PNG final à l'emplacement ci-dessus (en gardant le nom), et prévenez-moi : Claude Code vérifie la taille et le rendu au prochain build.
Si tu veux, propose-moi 2–3 variantes (emblème étoile / monolithe / flamme) et Yoann choisira.
