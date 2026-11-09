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

## Titan plus proche
- [ ] Le Titan apparaît à environ **12 blocs** du bord de l'île (marge réglable : éditeur, page Titan, « Marge (blocs) »), hors de portée du corps à corps mais atteignable à distance (engins, arcs).
- [ ] Si le Titan est toujours petit à l'écran : vérifier que la taille enregistrée n'est pas restée à l'ancienne valeur (défaut 110).

## Monolithe et royaume gardés jusqu'à la fin
- [ ] Après les assauts du Siège : la Porte de la forteresse disparaît, les ennemis du royaume sont retirés, **mais le Monolithe garde son menu** (Améliorations, Défense, Mairie, Rôles) pendant la Contre-offensive, le Titan et la pause de fin. Plus de menu « Lier l'autel ».
- [ ] Tours, remparts, pièges, trésor et rôles restent actifs ; les tours tirent sur les renforts de la Contre-offensive.
- [ ] Pas de nouvel assaut ni de calme pendant la veille (pas de gisements qui réapparaissent).
- [ ] Monolithe détruit pendant la Contre-offensive ou le Titan → défaite de la Faille.
- [ ] Fin de Faille (victoire, défaite ou /ws rift stop) : le royaume (boutique, défenses, trésor) est retiré proprement, récompenses de fin versées une seule fois.
- [ ] Prévu plus tard : attaques du Titan contre le Monolithe (point d'entrée existant : `AltarDefense.externalHit(level, dégâts)`).

## Île définitive (plans de Claude Chat)
- [ ] Faille ouverte : l'île de départ (rayon 16) est le plan définitif (place aux runes, anneau doré, pylônes d'améthyste, rebord d'obsidienne, cône du dessous). Le Monolithe est bien au centre, au-dessus du bloc de surface.
- [ ] Horde : dessus plat et dégagé ; arbres, gisements, arrivée des joueurs (sud du Monolithe) corrects.
- [ ] Siège, agrandissement 16 → 32 (et 32 → 48 si le rayon final est 48) : anneaux du centre vers le bord, briques de deepslate, **8 veines de Faille** (teal / violet) plaquées au sol, roche brute et débris flottants au-delà de 32.
- [ ] Le pont de la forteresse se raccorde bien au bord de l'île ; les engins se posent sur le sol de l'île.
- [ ] Rayons non standard (ex. 24) : l'île est tronquée au rayon réglé (plan du rayon supérieur).
- [ ] Pas de gros à-coup au premier anneau (rayon 48 : ~430 Ko de plan).

## Citadelle d'obsidienne (forteresse définitive)
- [ ] Début du Siège : la Citadelle et le pont (72 de long) se posent par tranches (pas de gros à-coup), le pont se raccorde au bord de l'île, la porte regarde l'île.
- [ ] Dôme du bouclier (rayon 42) : on ne peut pas entrer ; les joueurs refoulés sont replacés sur le pont, hors du dôme.
- [ ] Au début de la horde du Siège : la **herse** au bout du pont s'ouvre ; les ennemis sortent de la forteresse et traversent le pont vers le Monolithe.
- [ ] Éditeur, page Siège : choix de la forteresse (citadelle / tirage), régénération, rayon, gardiens min / max, force par phase.
- [ ] Contre-offensive : **6 catalyseurs** sur le cercle du plan ; à chaque tiers de PV perdu une chaîne de l'autel disparaît (éclat violet, son) et 3 puis 4 puis 5 gardiens surgissent, plus forts.
- [ ] Personne à moins de 8 blocs : le catalyseur regagne 2 % de PV par seconde (les chaînes brisées restent brisées).
- [ ] Le **cœur** n'apparaît dans le donjon qu'après la destruction des 6 catalyseurs.
- [ ] Les murs et barreaux du plan sont bien raccordés (pas de poteaux isolés) ; pas de blocs flottants.

## Titan — Poing contre le Monolithe
- [ ] À partir de la phase 2 du Titan : attaque annoncée « Le Titan vise le Monolithe ! » (cercle rouge sur le Monolithe, 3,5 s), puis coup à l'intégrité du Monolithe (60 par défaut) ; joueurs au pied du Monolithe blessés.
- [ ] Le Monolithe se répare au clic droit ; s'il tombe, défaite de la Faille.
- [ ] Éditeur, page Titan : « Poing/Monolithe » (0 = désactivé).

## Sauvegarde et reprise de la Faille
- [ ] Horde en cours (vague 3 par ex.) : fermer le monde, le rouvrir → message « La Faille … est interrompue : Horde, vague 3 » avec boutons **Reprendre / Abandonner**. « Reprendre » : île reconstruite, joueurs envoyés dessus, la vague 3 repart (Monolithe aux mêmes PV et niveaux, même difficulté et mutateurs).
- [ ] Siège (après quelques assauts, pendant un calme) : fermer / rouvrir → reprise : île à son rayon final, Citadelle reposée (message « La forteresse se reforme… »), puis le Siège repart avec trésor, rôles, défenses et assaut atteint.
- [ ] Contre-offensive : fermer / rouvrir → reprise au **début** de la Contre-offensive (tous les catalyseurs, 3 chaînes chacun). Titan : pas de point de reprise (on reprend au début de la Contre-offensive).
- [ ] « Abandonner » : plus de message à la connexion suivante.
- [ ] Fin de la Faille (victoire, défaite, /ws rift stop) : la sauvegarde est effacée ; nouvelle Faille = ancienne sauvegarde effacée.
- [ ] À la fermeture du serveur, les joueurs sont renvoyés dans le monde normal (devant le portail) et l'île est vidée.
- [ ] Les engins de siège posés ne sont pas conservés par la reprise (à racheter) — à confirmer.

## Pluie de météores améliorée
- [ ] Annonce : cercle de rayon 6 par zone, disque de particules rouges qui se remplit du centre vers le bord pendant 2,5 s ; texte et lueur du Titan inchangés.
- [ ] ~1,2 s avant l'impact, un **météore en blocs** (amas arrondi ~3×3×3 : magma, pierre noire, obsidienne, obsidienne pleureuse) apparaît dans le ciel côté Titan (~40 blocs plus haut) et tombe en diagonale en tournant, avec traînée de flammes et de fumée.
- [ ] Impact : explosion, gerbe de débris aux couleurs du météore, secousse d'écran pour les joueurs proches, météore supprimé ; dégâts jusqu'à 6,4 blocs (12 par défaut).
- [ ] Cratère temporaire (magma / pierre noire fumants, ~10 s) : n'abîme jamais les blocs posés par les joueurs ; le terrain revient à l'identique.
- [ ] Vu de près et de loin (le Titan est à ~12 blocs de l'île) : le météore reste visible.
- [ ] Éditeur, nouvelle page « Météores » : blocs et proportions, taille, rayon, dégâts, nombre (3 + 1 par joueur), annonce (50 t), chute (24 t), traînée (flammes / âme / fumée), cratère oui/non.

## Correctif : /ws skip pendant le Siège
- [ ] Pendant le Siège, `/ws skip` (Assaut → Calme → Assaut…) : après le dernier assaut prévu (réglage « Assauts »), la Contre-offensive démarre ; plus d'assaut ni de calme en trop.

## Modèles finaux des engins (niveaux 1 et 2)
- [ ] Achat puis pose (clic droit sur le sol de l'île) : l'engin apparaît avec son **modèle final** (plus de blocs vanilla), tourné vers le sud ; il est solide (on ne le traverse pas) et on ne peut pas le casser.
- [ ] **Catapulte** : le clic droit tire ; la poutre bascule (+13° → −18°), le boulet disparaît, le châssis tressaute, puis rebond ; pendant le rechargement le treuil tourne et la poutre remonte cran par cran ; le boulet réapparaît à la fin. 30 s (niveau 1) / 24 s (niveau 2).
- [ ] **Baliste** : la tourelle suit le regard du tireur (cap) et l'arme son inclinaison ; chaque tir : corde qui claque, ailes qui frémissent, carreau qui disparaît puis glisse ; 1 tir / 0,5 s (niveau 1) / 0,4 s (niveau 2), chargeur de 16 ; rechargement : halo qui tourne, ailes repliées puis retour, carreau qui se matérialise (10 s / 8 s). Baliste vide : un clic la recharge (une seule attente).
- [ ] Amélioration au lingot d'or : le modèle change (Renforcée / Consacrée), les cadences aussi ; les variantes de niveau 3 gardent le modèle de niveau 2 (leurs modèles viendront ensuite).
- [ ] Éditeur, page Titan : cadences des engins (rechargement catapulte N1/N2, tir baliste N1/N2, rechargement baliste N1/N2, en ticks).
- [ ] Textures : tuile 16×16 répétée sur chaque face, pièces lumineuses (cristaux, corde, halo) à pleine luminosité, pas de faces manquantes.
- [ ] Rendu à distance (le Titan est à 12 blocs de l'île) et vu de près : pas de clignotement ni de coupure.

## Correctif : taille réelle du Titan
- [ ] Le Titan fait bien la taille réglée (×110 par défaut : ~300 blocs de haut) et non ~15 blocs ; son bord est à ~12 blocs de l'île.
- [ ] Dans `latest.log` : « Titan … (demandé x110, appliqué x110 …) » ; un avertissement apparaît si Pehkui limite la taille.

## Titan — charge sur l'île
- [ ] À chaque palier de PV (75 / 50 / 25 %) puis toutes les 60 s dès la phase 3 : « Le Titan prend son élan ! », la zone d'impact rouge se dessine et se remplit sur le bord sud de l'île (3 s).
- [ ] Élan (1 s) : le Titan avance, l'écran tremble ; son bord nord arrive ~14 blocs à l'intérieur de l'île.
- [ ] Impact : gros tremblement pour tous ; les joueurs dans la zone prennent 24 dégâts et sont repoussés vers le Monolithe ; les autres ne sont pas touchés.
- [ ] Stun (6 s) : Titan à terre (lueur, barre rouge), attaquable au corps à corps (×1,5, plafond 15 % des PV max pour le stun) ; il ne lance aucune autre attaque.
- [ ] Éveil puis corps à corps (10 s) : coups annoncés (cercle rouge de 5 blocs, 1,2 s) sur les joueurs, 18 dégâts et recul.
- [ ] Retour en 3 s à sa place (12 blocs du bord de l'île) ; la charge suivante dans 60 s ; les autres attaques reprennent.
- [ ] Éditeur, page Titan : charge activée, délai, stun, mêlée, impact, empiètement, coup, plafond du stun.
- [ ] Le Titan mort pendant une charge : tout s'arrête proprement.

## Correctifs : lag du Titan, taille, amélioration des engins
- [ ] Le Titan (×55 par défaut) apparaît sans les arrêts de ~22 s (« Can't keep up! … 22000ms ») du `latest.log` ; plus de blocage pendant son éveil ni ses attaques.
- [ ] Le Titan ne reste plus « bloqué » : taille divisée par deux (×55), sa distance au Monolithe diminue avec elle.
- [ ] Engins : **clic droit = tirer**, **Maj + clic droit = améliorer** (niveau 2, puis choix de la variante). Message à la pose et à l'amélioration.
- [ ] Catapulte : la portée dépend de l'inclinaison du regard (plus de tir court à Maj).

## Fiche d'amélioration des engins
- [ ] **Maj + clic droit** sur une catapulte / baliste : un menu s'ouvre (comme pour les tours) : nom, étoiles, description, munitions (baliste), prochain niveau et son prix, bouton « Améliorer » (grisé si pas assez de ◆).
- [ ] Niveau 2 : le modèle change ; le menu propose 3 variantes avec description et prix (survol = description complète), bouton « Choisir » : choix définitif ; niveau 3 : nom de la variante affiché.
- [ ] Clic droit simple : tirer (inchangé). Le menu se met à jour après chaque achat.

## Corde de la baliste et tour-catapulte automatique
- [ ] **Baliste** : la corde (2 fins rubans cyan lumineux) relie toujours les pointes des deux ailes à l'encoche, pendant le tir, le battement des ailes et le rechargement. Au tir l'encoche avance de 20 px en 2 ticks (la corde claque) puis revient ; à vide la corde reste détendue.
- [ ] **Tour-catapulte** (6 × 6 × 6, remplace la catapulte pilotable dans la boutique) : la tour se pose (36 blocs de sol, 6 de haut dégagés), puis **vise et tire seule** : le Titan en priorité, sinon l'ennemi le plus proche (≤ 70 blocs). La plateforme tourne vers la cible à 30°/s au plus ; la poutre bascule (+14° → −24°) au tir ; rechargement de 30 s ; le boulet réapparaît à la fin.
- [ ] Munitions (**à valider**) : par défaut 1 Pierre du trésor du royaume par tir (message si le trésor n'en a plus) ; option « Tour : munitions illimitées » dans l'éditeur (page Titan).
- [ ] Clic droit sur la tour : elle ne tire pas (message) ; Maj + clic droit : fiche (améliorations niveau 2 / variantes appliquées à ses tirs).

## Correctif : le Titan était quasi impossible à toucher (boîtes de contact)
- [ ] Les flèches de la baliste, les boulets de la tour-catapulte et les rayons touchent le Titan sur sa **face avant** (pas seulement près de son centre) ; sa barre de vie baisse.
- [ ] Au corps à corps (pendant le stun de la charge), le joueur peut frapper le Titan sur la face avant, à portée normale.
- [ ] Une explosion de boulet ne blesse le Titan qu'une fois (pas de dégâts multipliés par le nombre de boîtes).
- [ ] Les boîtes de contact suivent le Titan pendant la charge et le retour, sont invisibles, sans collision, et disparaissent à sa mort.

## Titan : points faibles, vagues, bâtiments, phases, charge, sauvegarde
- [ ] **Cristaux** : 4 cristaux « Point faible » apparaissent sur la face avant du Titan (bas du corps), atteignables aux engins, arcs et en mêlée ; chacun détruit retire **5 %** des PV max du Titan ; détruit pendant le stun : **+2 s** de stun ; pendant l'annonce ou l'élan : stun plus long à l'impact. Ils se reforment à chaque palier (75 / 50 / 25 %) et suivent le Titan pendant la charge.
- [ ] **Plafond pendant la charge** : le Titan ne perd pas plus de **20 %** de ses PV max entre le début de l'annonce et la fin du retour (×1,5 pendant le stun, ×0,5 sinon ; les cristaux comptent).
- [ ] **Invasion continue** : dès 75 % de PV, un portail s'ouvre **sur le pont** et lâche une vague toutes les 10 s : 5 unités (palier 1), 10 (palier 2), 20 (palier 3) ; plafond d'ennemis vivants (60).
- [ ] **Attaque des bâtiments** : le Titan annonce (cercle rouge) puis écrase une tour / un rempart (dégâts + ruines réparables). La **charge** endommage aussi les bâtiments pris dans la zone d'impact.
- [ ] **Phases visuelles** : écran « PHASE 2/3/4 » + secousse à chaque palier ; braises (phase 2), éclairs visuels (phase 3), fissures sur l'île et grondements (phase 4).
- [ ] **Charge** : la zone d'impact est réduite (coin sud de l'île, ~28 de large) et l'empiètement passe à 8 blocs : plus de demi-île inaccessible.
- [ ] Tour-catapulte : munitions illimitées par défaut.
- [ ] **Reprise** : les engins posés et les tours du Kingdom sont retrouvés après une reprise (Siège, Contre-offensive, Titan → début de la Contre-offensive), avec le trésor à jour.
