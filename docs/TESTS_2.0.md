# Tests 2.0 — La Faille (cases à cocher en jeu)

Conception : `docs/DESIGN_FAILLE_2_0.md`. Mod Pehkui requis pour la Faille (sans lui : message et onglet indisponible).

## Jalon 1a — socle (mode, dimension, île, aller-retour)
- [ ] Éditeur, onglet Général : le bouton de mode fait Vagues → Kingdom → Invasion → **La Faille** → Vagues ; en mode Faille l'onglet « Pauses » devient « 🌌 La Faille »
- [ ] Onglet « La Faille » avec Pehkui : durée et secousses de l'animation, titre, rayon de l'île (8-48), vagues de la Horde ; valeurs conservées après fermeture / réouverture
- [ ] Onglet « La Faille » sans Pehkui : message d'explication seulement
- [ ] `/ws rift status` : dit si une Faille est ouverte et si Pehkui est installé
- [ ] `/ws rift open` (op, avec Pehkui) : une île de deepslate (rayon 16, plus épaisse au centre, bord d'obsidienne) apparaît dans la dimension `wavesurvivor:rift` et les joueurs proches y sont envoyés (titre « La Faille ») ; sans Pehkui : message d'erreur
- [ ] Autel lié à une horde en mode Faille : clic droit puis « Lancer » → la Faille s'ouvre (pas de compte à rebours ni de horde) ; terrain non vérifié
- [ ] Clic droit sur l'autel pendant qu'une Faille est ouverte : le joueur est envoyé sur l'île
- [ ] `/ws rift leave` : retour exactement là où on était en entrant ; `/ws rift enter` : retour sur l'île
- [ ] Tomber de l'île : replacé sur l'île avec 1 cœur de dégâts (filet provisoire)
- [ ] `/ws rift stop` : tout le monde est renvoyé chez soi, l'île et les entités sont effacées
- [ ] Se déconnecter dans la Faille puis relancer le monde (Faille fermée) : on est renvoyé chez soi à la connexion

## Jalon 1b — Portail de la Faille et animation d'ouverture
- [ ] Lier l'autel à « La Faille » (1 obsidienne pleureuse + 4 éclats d'améthyste) puis Lancer : plus de téléportation directe, le portail s'ouvre à ~18 blocs de l'autel, façade tournée vers l'autel
- [ ] Animation : grondement + poussière (1,5 s), le rocher surgit couche par couche avec secousses, silence + fissures violettes, explosion (éclair, ovale qui vole en éclats), le portail s'ouvre du centre vers les bords, veines violettes + contour turquoise, vrilles une à une, titre « La Faille est ouverte »
- [ ] Les joueurs trop proches sont repoussés (ou sortis de l'emprise du rocher) sans dégâts
- [ ] Une fois ouvert : marcher dans l'ovale envoie sur l'île
- [ ] `/ws rift stop` : tout le monde rentre, l'ovale redevient de la pierre, le rocher reste en place
- [ ] Relancer depuis le même autel : pas de nouvelle animation complète, courte réouverture (~1,5 s)
- [ ] Redémarrer le jeu avec un portail resté ouvert : il est refermé (pierre) au chargement du monde
- [ ] Réglages de l'éditeur : durée × (0,5 = rapide, 2 = lent), secousses × (0 = aucune), titre on/off
- [ ] Terrain en pente / dans l'eau : le rocher se pose correctement (fondations), pas de joueur coincé
- [ ] Texture animée du portail visible des deux côtés, translucide

## Onglet de l'autel
- [ ] L'écran « Lier l'autel » a un onglet « 🌌 La Faille » (visible seulement s'il existe une horde Faille), la horde n'est plus dans « Hordes »

## Jalon 2 — Phase Horde sur l'île
- [ ] Portail ouvert + un joueur entre : message/actionbar « La Horde commence dans X s », la horde démarre au bout du délai (réglable)
- [ ] 5 vagues sur l'île (zombies, archers, araignées, gardiens), boss à la vague 5, apparitions sur l'île (pas dans le vide)
- [ ] Mort : on garde son inventaire, spectateur au-dessus de l'île, retour après X s (réglable) ; pas de mort réelle ni retour au monde normal
- [ ] Tous les joueurs à terre = défaite, message, pause de fin puis retour devant le portail
- [ ] Victoire : récompenses de la horde, pause de fin (4 min par défaut, compte à rebours en actionbar), puis fermeture et retour
- [ ] Joueur qui entre en cours de Horde : spectateur, il joue à la vague suivante
- [ ] `/ws rift stop` pendant la Horde : arrêt propre, tout le monde rentre, le rayon d'apparition de la horde est restauré
- [ ] Éditeur : onglet La Faille → vagues, début après, retour après mort, pause de fin

## Portail : veines, contour, Faille vivante
- [ ] Les vrilles sont de fins filaments (veines multiface turquoise / violet en alternance) qui rampent depuis le bord de l'ovale sur la roche, sans bloc turquoise plein
- [ ] Les veines sont lumineuses (lumière 7) et animées, sans collision, cassables à la main
- [ ] Liseré de particules turquoise continu le long du bord de l'ovale
- [ ] Particules aspirées vers le centre + étincelles qui s'échappent
- [ ] La lueur du portail varie lentement (respiration), de jour comme de nuit

## Jalons 3 à 7 — Siège, Contre-offensive, Titan, engins, éditeur (à tester d'un bloc)
Parcours complet conseillé : `/ws rift open` (ou autel) → entrer → Horde (5 vagues + boss) → Siège → Contre-offensive → Titan → pause de fin. Pour aller vite : réduire vagues / PV dans l'éditeur (onglet La Faille, 3 pages).
- [ ] Après la Horde : message « le Siège approche », pause, puis l'île s'agrandit par anneaux (secousses), un pont et une forteresse (plateforme, muraille à porte, 4 tours, donjon) apparaissent
- [ ] Siège : bouclier (particules turquoise/violet), impossible d'entrer dans la forteresse (refoulé vers le pont) ; assauts sortent de la porte de la forteresse et viennent vers les joueurs
- [ ] À chaque calme : arbres et minerais apparaissent sur l'île, message « Un calme s'installe »
- [ ] Engins : 2 catapultes et 2 balistes sur l'île côté -X. Catapulte : clic droit sur le pupitre avec de la cobblestone → boulet en cloche, explosion de zone, recharge 30 s. Baliste : clic droit avec des flèches → charge 16 (10 s), tirs toutes les 0,5 s (clics répétés)
- [ ] Les récompenses de la horde ne sont PAS versées entre les phases, seulement à la toute fin
- [ ] Fin du Siège : le bouclier tombe, 3 catalyseurs (cristaux avec PV affichés) apparaissent dans la forteresse ; on les détruit au corps à corps, à l'arc, à la baliste ou à la catapulte ; renforts régulièrement
- [ ] Catalyseurs détruits → le cœur de la forteresse apparaît (plus de PV) ; cœur détruit → Titan
- [ ] Titan : éveil (secousses, titre), il monte de l'abîme derrière la forteresse, très grand (Pehkui), barre de vie violette ; reste affiché de loin
- [ ] Attaques annoncées (zone rouge au sol qui se remplit + texte « ⚠ … » + lueur) : météores sous les joueurs, onde de choc sur un quart d'arène (fuir ou sauter), rayon (s'abriter derrière un mur), rugissement (faiblesse), invocation d'avatars
- [ ] Tuer un avatar retire 8 % des PV max du Titan ; plafond de dégâts par coup (6 % des PV max) ; phases à 75 / 50 / 25 % (message, nouveaux avatars, attaques plus rapides)
- [ ] Mort / spectateur / retour fonctionnent dans toutes les phases ; tous à terre = défaite ; joueur arrivé en cours : spectateur jusqu'au prochain point d'entrée
- [ ] Titan vaincu : butin (émeraudes, éclats d'améthyste, obsidienne pleureuse) + récompenses de la horde, pause de fin, fermeture et retour
- [ ] `/ws rift stop` à n'importe quel moment : tout est nettoyé (forteresse, île, Titan, avatars, engins, cristaux), les joueurs rentrent
- [ ] Éditeur : 3 pages dans l'onglet La Faille (Départ et Horde / Siège et Contre-offensive / Titan et engins), réglages pris en compte

## Retours de test n°1 (Monolithe, échelle, Titan, ressources)
- [ ] Un Monolithe (bloc d'autel) apparaît au centre de l'île au début de la Horde ; les ennemis viennent le frapper ; barre d'intégrité visible
- [ ] Clic droit sur le Monolithe : boutique (Renfort / Régénération / Blindage) ; réparation à la poudre d'os
- [ ] Le Monolithe reste le même du début de la Horde jusqu'au Siège ; il disparaît au début de la Contre-offensive (bonus si intact)
- [ ] Monolithe détruit : défaite de la Faille (pause de fin, retour)
- [ ] Arbres et gisements des calmes : ceux de l'onglet Chaos (garantis par calme), sur l'île, jamais dans le vide
- [ ] Pont 3 fois plus long et plus large, forteresse 3 fois plus grande ; les ennemis traversent le pont
- [ ] Contre-offensive : catalyseurs répartis sur le grand cercle, cœur dans le donjon
- [ ] Titan x110 (x5) : visible, au SUD de l'île ; engins de siège (flanc sud) orientés vers lui ; avatars arrivent par le sud

## Retours de test n°2 — Siège sur le moteur du Kingdom, engins achetables
- [ ] Horde terminée → Siège : la forteresse apparaît, **une seule Porte** s'ouvre devant la porte de la forteresse (face à l'île).
- [ ] Onglets **Défenses / Mairie / Rôles** visibles dans la boutique du Monolithe pendant le Siège (pas pendant la Horde).
- [ ] Tours d'archers / mages, sanctuaire, baraquement, remparts, portes, pièges : achat avec le trésor, pose sur l'île, ils tirent sur les ennemis de la Faille.
- [ ] Cycle **Assaut / Calme** : arbres et gisements (système du Chaos) apparaissent à chaque Calme ; le trésor se remplit.
- [ ] **Rôles** : choix possible ; Augure OK (pas de « Porte endormie » ni « Gardiens assoupis » proposés) ; Mineur OK (gisements).
- [ ] La Porte de la forteresse ne se détruit pas ; pas de Catalyseur, pas de gardiens, pas de boss final, pas d'objectif de Calme.
- [ ] Après le nombre d'assauts réglé (éditeur, page Siège) → Contre-offensive ; le royaume (trésor, rôles, défenses) s'arrête proprement.
- [ ] Éditeur, page Siège : **Unités/assaut, Hausse %, Calme (s)** pris en compte.
- [ ] Boutique, onglet **Défenses › Engins** (Faille seulement) : Catapulte de siège et Baliste céleste de siège achetables ; clic droit sur le sol de l'île = pose (face au sud) ; pupitre = pilotage comme avant.
- [ ] Message d'erreur si pas de place / hors île ; plus aucun engin pré-posé sur l'île.
- [ ] **Non-régression** : une partie Kingdom normale et une horde classique (hors Faille) fonctionnent comme avant.

## Titan — fenêtres de vulnérabilité
- [ ] Hors fenêtre : les dégâts au Titan sont réduits de moitié (et plafonnés par coup).
- [ ] Après une **Onde de choc** ou un **Rugissement** : le Titan brille, barre rouge, message « à découvert » ; dégâts ×1,5 pendant la fenêtre (8 s par défaut).
- [ ] Le total de la fenêtre est plafonné (8 % des PV max par défaut) ; la fenêtre se referme avec un message.
- [ ] Éditeur, page Titan : « Fenêtre (s) » (0 = désactivée) et « Plafond fenêtre % ».

## Ruines réparables (Siège)
- [ ] Une tour détruite par les monstres laisse des **vestiges** (briques de deepslate fissurées, fumée) à sa place.
- [ ] Clic droit sur les vestiges : la tour est rebâtie (niveau 1) pour **la moitié de son prix** ; message de confirmation.
- [ ] Démonter une tour à la pioche **ne laisse pas** de ruine.
- [ ] Fin du Siège : les vestiges disparaissent avec le royaume.
- [ ] Hors Faille (Kingdom normal) : aucune ruine, comportement inchangé.

## Bénédictions conservées Horde → Siège
- [ ] Les bénédictions obtenues pendant la Horde sont **toujours actives** au Siège, à la Contre-offensive et face au Titan.
- [ ] Elles se dissipent à la **fin de la Faille** (victoire, défaite ou arrêt).

## Améliorations des engins (statistiques ; visuels à venir de Claude Chat)
- [ ] Clic droit sur le pupitre **avec un lingot d'or** : niveau 2 (catapulte 60 ◆ « renforcée », baliste 50 ◆ « consacrée »). Catapulte : recharge 22,5 s, dégâts ×1,25, boulet plus rapide. Baliste : cadence 0,4 s, 7,6 dégâts par flèche.
- [ ] Nouveau clic avec un lingot d'or au niveau 2 : boutons cliquables dans le chat pour choisir la **variante** (100 ◆ catapulte, 80 ◆ baliste), définitive pour cet engin.
- [ ] Catapulte : **Infernale** (cibles enflammées), **À fragmentation** (5 éclats autour de l'impact), **Trébuchet de siège** (portée, dégâts ×2, recharge 37,5 s).
- [ ] Baliste : **Séraphique** (flèche perforante), **Solaire** (rayon de lumière, cadence 0,2 s), **Du Jugement** (rayon qui marque : +25 % de dégâts subis pendant 8 s, pour toute l'équipe).
- [ ] Les engins améliorés continuent de fonctionner à la Contre-offensive et face au Titan.
