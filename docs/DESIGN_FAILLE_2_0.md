# « La Faille » (The Rift) — conception, mise à jour 2.0

> Brainstorm Yoann × Claude chat (novembre 2026). Rien n'est encore codé : document de référence pour la 2.0. Décisions de Yoann intégrées.

## 0. Principe
Quatrième type d'événement, déclenché depuis l'**autel** (nouvel onglet « La Faille » dans l'éditeur). Un **portail** s'ouvre ; les joueurs qui le franchissent arrivent dans une **dimension dédiée** (vide à 99 %, île-arène flottante). Enchaînement : **Horde → Siège → Contre-offensive → Titan**. Prévu pour être **long** (plus d'une heure). Plusieurs **Failles** possibles (chacune avec son Titan, sa forteresse, ses hordes).

## 1. Le portail et la dimension
- Portail à concevoir (visuel à faire). Le franchir téléporte dans la Faille ; retour au monde normal à la victoire ou à la défaite.
- Dimension : vide, une **île principale** (arène de 16 de rayon au départ). Tout se passe sur cette île (pas d'îlots annexes).

## 2. Phase 1 — La Horde
- Horde classique sur l'île (nombre de vagues réglable, ex. 5), **boss de la dernière vague** choisi dans l'éditeur.

## 3. Phase 2 — Le Siège (variante Kingdom)
- À la fin de la Horde, **l'île s'agrandit** (rayon réglable, ex. 32+) et la **forteresse ennemie** se génère en face, reliée à la base par un **chemin**.
- Tous les ennemis viennent de la forteresse. Alternance **assaut / calme** comme le Kingdom, boss à certaines vagues.
- La forteresse est protégée par un **bouclier** : impossible d'y entrer ou de l'attaquer pendant le Siège. Les joueurs restent libres de leurs mouvements (pas d'enfermement dans la base).
- **Ressources** : pendant chaque calme, apparition **obligatoire** d'un nombre minimum (réglable) d'**arbres** et de **gisements de minerais** sur l'île (mêmes styles que le chaos : Classique / Oasis, Classique / Désert…).

## 4. Phase 3 — La Contre-offensive
- Le bouclier tombe. Les joueurs attaquent : détruire **plusieurs catalyseurs** répartis dans la forteresse, puis le **portail** de la forteresse.
- Les ennemis continuent d'apparaître mais **ne visent que les joueurs** (plus le Monolithe).

## 5. Phase 4 — Le Titan
- **Cinématique** d'invocation (bandes noires, caméra vers l'horizon, tremblements, le Titan surgit de l'abîme ou descend des nuages ; passable).
- Le Titan est **hors de l'arène**, derrière le décor, au-dessus du vide, **gigantesque** (au moins ×4 le Géant vanilla, soit ~50 blocs ; taille réglable). Il attaque de loin.
- **N'importe quelle entité** peut être un Titan (évocateur, golem de fer, entité moddée…) : agrandie au rendu, hitbox dédiée. Donc **aucune compétence ne doit dépendre d'une animation du modèle**.
- **Pas de corps à corps** en temps normal : armes à distance et **catapulte / baliste** (nouvelle défense pilotable, modèles à proposer par Claude chat).
- **Fenêtres de vulnérabilité** au corps à corps (ex. après une grosse attaque), avec **dégâts plafonnés** par fenêtre (ex. 5 à 10 % des PV max, réglable).
- **Avatars** = ses points faibles : il invoque des avatars (mini-boss puissants) ; en tuer un retire **X % de ses PV max** (réglable).
- **Phases** (ex. 75 / 50 / 25 % de PV) : changement d'attaques et de comportement.

### 5.1 Compétences de Titan (nouvel onglet de l'éditeur de compétences)
Compétences qui agissent **sur l'arène** :
- **Pluie de météores** : vise les joueurs et les **bâtiments**, qu'elle détruit.
- **Onde de choc** (au lieu de « coup de poing », pour ne pas dépendre d'un modèle) : impact sur une zone annoncée (un quart d'arène, un cercle…), visuel de l'impact.
- **Rayon** : balaie l'arène ; on s'abrite derrière des murs.
- **Rugissement** : affaiblit tout le monde.
- **Invocation d'avatars** (voir plus haut).

### 5.2 Annonce universelle des attaques (indépendante du modèle)
Chaque compétence de Titan est définie par une **forme de zone** (cercle, cône, ligne, quart d'arène, arène entière) et un **délai d'annonce** (1 à 3 s). Pendant le délai : zone tracée **au sol** (particules / marqueurs colorés qui se remplissent), **son** d'alerte, **texte** sous la barre de vie du boss, **lueur** sur le Titan (surcouche de couleur, valable pour n'importe quel modèle), tremblement d'écran à l'impact. Rien ne dépend d'une animation propre au modèle.

## 6. Points de reprise et mort
- **Sauvegarde** de la progression : reprise à la vague atteinte de la Horde, à la vague atteinte du Siège, au début de la Contre-offensive (catalyseurs déjà détruits conservés — à confirmer).
- **Titan : pas de point de reprise**, le combat recommence à zéro.
- **Mort** : le joueur passe en **spectateur** au-dessus du Monolithe (même système qu'en multijoueur), puis réapparaît à côté du Monolithe ; si tout le groupe tombe, c'est la **défaite** (retour au monde normal, récompense de consolation comme les hordes).

## 7. Forteresse ennemie configurable
- **Plusieurs formes** de forteresse (préréglages), choisies dans l'éditeur ; le joueur peut en proposer plusieurs pour une Faille (tirage).
- **Points d'apparition des catalyseurs** configurables (positions dans la forteresse).
- Chaque forme : chemin vers la base, points d'apparition des ennemis, emplacement du bouclier et du portail.

## 8. Éditeur — onglet « La Faille »
Phases (activables, vagues, boss), Siège (durées, vagues, boss, ressources minimales par calme, rayon de l'île agrandie), Contre-offensive (nombre et PV des catalyseurs, rythme des ennemis), Titan (entité, PV, taille, distance, compétences de Titan, avatars et % de PV, phases, plafond des fenêtres de vulnérabilité), forteresses (formes, catalyseurs), points de reprise, récompenses (coffre roulette du Titan, trophée).

## 9. À faire (visuels Claude chat)
- Modèles de la **catapulte** et de la **baliste céleste** (Yoann choisit).
- Design du **portail** de la Faille.
- Plusieurs **formes de forteresse**.
- Un premier **Titan** de démonstration et sa cinématique (storyboard).
