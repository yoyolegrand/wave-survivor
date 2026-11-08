# « La Faille » (The Rift) — conception, mise à jour 2.0

> Brainstorm Yoann × Claude chat (novembre 2026). Rien n'est encore codé : document de référence pour la 2.0, **toutes les décisions de Yoann intégrées** (fusion de la fiche initiale et du complément « portail, ouverture, engins de siège »).
> Visuels validés (à ranger dans `docs/faille/`) : `portail_faille_antre.png`, `portail_faille_antre_3d*.png`, `faille_animation_storyboard.png`, `faille_vide_anime.gif`, `catapulte.png`, `baliste_celeste.png`, `catapulte_animation.png`, `baliste_animation.png`.

## 0. Principe
Quatrième type d'événement, déclenché depuis l'**autel** (nouvel onglet « La Faille » dans l'éditeur). Un **portail** s'ouvre ; les joueurs qui le franchissent arrivent dans une **dimension dédiée** (vide à 99 %, île-arène flottante). Enchaînement : **Horde → Siège → Contre-offensive → Titan**. Volontairement **long** (plus d'une heure, c'est le but). Plusieurs **Failles** possibles (chacune avec son Titan, sa forteresse, ses hordes).
- **Dépendance** : la Faille n'est disponible **que si le mod Pehkui est installé** (dépendance **optionnelle**, limitée à la Faille ; sans Pehkui, l'onglet Faille est indisponible avec un message explicatif). Pehkui gère l'agrandissement du Titan (rendu + hitbox).
- **Difficulté** : pas pensée pour un joueur seul suréquipé ; tout **s'adapte au nombre de joueurs** (PV et nombre d'ennemis, PV du Titan et des avatars).

## 1. Le portail (forme « Antre » ultime) et son ouverture
### 1.1 Le portail
Même famille que la forme **Antre** (`gateShape: den`) du Kingdom (amas rocheux + ovale tourbillonnant + vrilles lumineuses), avec un **style « Faille »** :
- **Taille ~×2** d'un Antre normal (≈ 28 blocs de large, 16 de haut).
- **Roche** : pierre noire, basalte, obsidienne ; près de l'ovale, roche **veinée d'obsidienne pleureuse** (le violet s'estompe en s'éloignant) ; **cristaux d'améthyste**.
- **Ovale** en retrait (entrée de grotte), rempli du **vide violet-turquoise animé** (`textures/block/rift_portal.png`, 16 images, frametime 2, interpolé, raccord parfait entre blocs ; ou portail du Nether recoloré), contour turquoise lumineux.
- **Vrilles** turquoise et violettes en alternance, qui partent du bord de l'ovale et rampent sur la roche (filaments continus qui ondulent et pulsent).

### 1.2 Animation d'ouverture — la PREMIÈRE fois que l'autel est lié à la Faille (≈ 6,5 s, 130 ticks)
Rendu d'entité piloté par un compteur synchronisé (serveur → clients). **Aucune caméra imposée** : seulement des secousses d'écran pour les joueurs à moins de 48 blocs. Les joueurs trop proches sont **repoussés en douceur** (sans dégâts). Le portail n'est traversable qu'à la fin. **Uniquement des sons vanilla.**
| Temps | Ticks | Phase | Visuel | Son | Écran |
|---|---|---|---|---|---|
| 0,0 → 1,5 s | 0 → 30 | **1. Le grondement** | Fissures violettes en étoile au sol ; poussière qui s'élève | Grondement grave qui monte (`ambient.cave`, `entity.warden.dig`) | Secousse légère → forte |
| 1,5 → 3,5 s | 30 → 70 | **2. Le rocher surgit** | Le rocher **intact** (gris, sans veines, sans vrilles, ovale fermé) monte du sol (−16 → 0, ralenti en fin de course) ; terre et pierres projetées | Pierre qui se brise en boucle (`block.stone.break`, `block.deepslate.break`) | Secousse forte |
| 3,5 → 4,0 s | 70 → 80 | **3. Le silence** | Tremblement arrêté ; un **ovale de fissures violettes** s'allume sur la façade | `entity.warden.heartbeat`, puis silence | — |
| 4,0 → 4,3 s | 80 → 86 | **4. L'explosion** | **Éclair blanc-violet** ; la façade de l'ovale vole en éclats ; onde de choc au sol | `entity.generic.explode` | Flash + secousse brève |
| 4,3 → 5,5 s | 86 → 110 | **5. La Faille s'ouvre** | Fente verticale → **ovale complet** ; veines violettes qui se propagent dans la roche | **`block.portal.trigger`** | — |
| 5,5 → 6,5 s | 110 → 130 | **6. La Faille vit** | Les **vrilles jaillissent une à une** ; particules de portail | `block.portal.ambient` (boucle, bas) | Titre « La Faille est ouverte » |
- **Les fois suivantes** : le rocher est déjà invoqué et reste en place — **on ne rejoue pas l'animation** ; seule une courte réouverture (~2 s : son du portail, l'ovale se rouvre) si la faille s'était refermée.
- Réglages : multiplicateur de durée, intensité des secousses, titre on/off.

## 2. Phase 1 — La Horde
- Horde classique sur l'île de départ (**16 de rayon**). Nombre de vagues **réglable** (ex. 5), **boss de la dernière vague** choisi dans l'éditeur.

## 3. Phase 2 — Le Siège (variante Kingdom)
- À la fin de la Horde, **l'île s'agrandit par anneaux successifs** (rayon final réglable, ex. 32+) et la **forteresse ennemie** se génère en face, reliée à la base par un **chemin**.
- Tous les ennemis viennent de la forteresse. Alternance **assaut / calme** comme le Kingdom, boss à certaines vagues. **Nombre d'assauts avant la Contre-offensive réglable.**
- La forteresse est protégée par un **bouclier** : impossible d'y entrer ou de l'attaquer pendant le Siège. Les joueurs circulent librement (pas d'enfermement dans la base).
- **Ressources** : pendant chaque calme, apparition **obligatoire** d'un nombre minimum (réglable) d'**arbres** et de **gisements de minerais** sur l'île (styles du chaos : Classique / Oasis, Classique / Désert…).
- **Bâtiments** : ceux du Kingdom. Un bâtiment détruit devient des **vestiges / ruines réparables** (à tester dans le Siège avant de l'étendre éventuellement au Kingdom).

## 4. Phase 3 — La Contre-offensive
- Le bouclier tombe. Les joueurs attaquent : détruire **plusieurs catalyseurs** répartis dans la forteresse, puis le **portail** de la forteresse.
- Les catalyseurs **n'apparaissent qu'au lancement de cette dernière vague** (jamais pendant les calmes du Siège).
- Les ennemis continuent d'apparaître mais **ne visent que les joueurs** (plus le Monolithe).

## 5. Phase 4 — Le Titan
- **Cinématique** d'invocation (bandes noires, caméra vers l'horizon, tremblements, le Titan surgit de l'abîme ou descend des nuages ; passable).
- Le Titan est **hors de l'arène**, derrière le décor, au-dessus du vide, **gigantesque** (au moins ×4 le Géant vanilla, ~50 blocs ; taille réglable). Il attaque de loin. Il doit **rester affiché de très loin** (au-delà de la distance de rendu normale des entités).
- **N'importe quelle entité** peut être un Titan (évocateur, golem de fer, entité moddée…), agrandie par Pehkui : **aucune compétence ne dépend d'une animation du modèle**.
- **Pas de corps à corps** en temps normal : armes à distance et **engins de siège** (§6).
- **Fenêtres de vulnérabilité** au corps à corps (ex. après une grosse attaque), avec **dégâts plafonnés** par fenêtre (5 à 10 % des PV max, réglable).
- **Avatars** = ses points faibles : il invoque des avatars (mini-boss puissants) ; en tuer un retire **X % de ses PV max** (réglable).
- **Phases** (ex. 75 / 50 / 25 % de PV) : changement d'attaques et de comportement.

### 5.1 Compétences de Titan (nouvel onglet de l'éditeur de compétences)
- **Pluie de météores** : vise les joueurs et les **bâtiments** (qui deviennent des ruines réparables).
- **Onde de choc** : impact sur une zone annoncée (quart d'arène, cercle…), visuel de l'impact.
- **Rayon** : balaie l'arène ; on s'abrite derrière des murs.
- **Rugissement** : affaiblit tout le monde.
- **Invocation d'avatars**.

### 5.2 Annonce universelle des attaques (indépendante du modèle)
Chaque compétence est définie par une **forme de zone** (cercle, cône, ligne, quart d'arène, arène entière) et un **délai d'annonce** (1 à 3 s). Pendant le délai : zone tracée **au sol** qui se remplit, **son** d'alerte, **texte** sous la barre de vie, **lueur** colorée sur le Titan (valable pour n'importe quel modèle), tremblement d'écran à l'impact.

## 6. Engins de siège pilotables (Siège et Titan de la Faille uniquement)
- **Catapulte** : **2 × 6 au sol, 3 de haut** (châssis de bois sombre cerclé de fer sur 4 roues, montants en A, grande poutre, cuillère, contrepoids de pierre, treuil).
- **Baliste céleste** : **3 × 3 au sol, 3 de haut** (piédestal octogonal de marbre à cadres dorés, tourelle pivotante, monture à tourillons, fût de marbre, arc en **ailes d'ange** dorées à plumes blanches, cristaux, corde lumineuse, halo doré).
- **Pilotage** : clic droit pour s'installer ; on vise avec la caméra ; maintenir pour charger la puissance ; relâcher pour tirer. Ils peuvent viser le Titan directement.

### 6.1 Munitions, cadence et équilibre (valeurs de départ, réglables)
- **Catapulte** : munition = **pierre taillée (cobblestone)** chargée dans la cuillère. **1 boulet** par chargement, puis **30 s** de rechargement. Tir en cloche, dégâts de **zone** : **80** au centre, décroissants jusqu'à 30 % au bord, rayon 4 ; **×1,5 contre le Titan et les structures ennemies**.
- **Baliste céleste** : munitions = **flèches** (prises dans l'inventaire au rechargement). Chargeur de **16**, **un tir toutes les 0,5 s**, puis **10 s** de rechargement complet. Tir tendu sur une cible : **6** dégâts par carreau ; **flèches bénies** : +50 % (dégâts sacrés), **flèches souillées** : effet de souillure.
- Repères : la Baliste fait ~96 dégâts par cycle de 18 s (≈ 5,3 / s, idéale contre les avatars) ; la Catapulte ~80 à 120 en zone toutes les ~31 s (idéale contre les groupes et le Titan).
- **Indicateurs** : barre de rechargement au-dessus de l'engin (et dans l'interface en pilotage) ; Baliste : compteur « 12 / 16 » et **cristaux des ailes qui pâlissent** à mesure que le chargeur se vide.

### 6.2 Améliorations — 3 niveaux (le 3e est un CHOIX de variante)
On **pose** l'engin au niveau 1 ; on l'**améliore une fois** (niveau 2) ; au **niveau 3**, on **choisit sa variante** (définitif pour cet engin ; plusieurs engins possibles). Amélioration pendant les calmes (ressources de l'île + Essence de brèche). Visuel différent à chaque niveau, dimensions inchangées. Tout réglable.
| Niveau | Catapulte | Baliste céleste |
|---|---|---|
| 1 | Catapulte (base) | Baliste céleste (base) |
| 2 | **Renforcée** : châssis blindé de fer, treuil à engrenages, contrepoids plus gros → recharge plus rapide, dégâts et portée + | **Consacrée** : ailes plus grandes, plus de cristaux, halo plus brillant → cadence et dégâts sacrés + |
| 3 (choix) | **Infernale** (boulets enflammés, zone de feu) / **À fragmentation** (le boulet éclate en 5 projectiles) / **Trébuchet de siège** (portée et dégâts max, recharge plus lente — anti-Titan) | **Séraphique** (carreau perforant qui traverse) / **Solaire** (tir maintenu → rayon de lumière continu) / **Du Jugement** (marque la cible : +dégâts subis de toute l'équipe — soutien) |
*(Variantes proposées par Claude chat, à valider par Yoann.)*

### 6.3 Animations de tir et de rechargement
**Catapulte** — 1. **Tir (0,5 s)** : la poutre bascule (jusqu'à +18°), le contrepoids chute juste au-dessus du châssis, la cuillère libère le boulet ; le châssis tressaute. 2. **Rebond (0,3 s)**. 3. **Rechargement (30 s)** : le treuil tourne lentement, la poutre redescend **cran par cran** jusqu'à −13°. 4. **Chargement (0,5 s)** : le boulet tombe dans la cuillère. Sons : bois qui claque + `entity.player.attack.sweep`, cliquetis de treuil, grincements, `block.stone.place`.
**Baliste** — 1. **Tir rapide (0,5 s, jusqu'à 16 fois)** : la corde claque, les ailes frémissent, le cristal flashe, sillage doré ; le carreau suivant glisse aussitôt sur le fût (`item.crossbow.shoot` + `block.amethyst_block.chime`). 2. **Rechargement complet (10 s)** : le halo tourne, les ailes se replient en tendant la corde, un **carquois doré** sur le flanc se remplit carreau par carreau (0 → 16), les cristaux se rallument (`block.beacon.power_select`, `item.crossbow.loading_middle`, `block.amethyst_cluster.place`).
**Variantes** : même squelette, avec leur effet (braises, éclats métalliques, fronde ; rayon chargé, chaînes, double rangée d'ailes).

## 7. Points de reprise, mort, arrivée en cours de partie, fin
- **Sauvegarde** : reprise à la vague atteinte de la Horde, à la vague atteinte du Siège, et au **début de la Contre-offensive** (elle recommence avec tous ses catalyseurs).
- **Titan : pas de point de reprise**, le combat recommence à zéro.
- **Mort** : on **garde son inventaire** dans la Faille (aucun souci si on tombe dans le vide) ; le joueur passe en **spectateur** au-dessus du Monolithe, puis réapparaît à côté du Monolithe ; si tout le groupe tombe, c'est la **défaite** (retour au monde normal, récompense de consolation comme les hordes).
- **Joueur qui arrive en cours de Faille** (non enregistré au départ) : il entre en **spectateur** et devient joueur au **prochain point d'entrée**, où il est alors **compté dans le calcul de difficulté** :
  - pendant la **Horde** : à la vague suivante (arrivé pendant la vague 3 → joue à la vague 4) ;
  - pendant le **Siège** : au prochain **calme** ;
  - pendant la **Contre-offensive** : à l'**apparition du Titan** ;
  - pendant le **Titan** : il reste **spectateur** jusqu'à la fin.
- **Fin** (victoire ou défaite) : **4 minutes de pause**, puis la dimension se **réinitialise** et les joueurs sont **renvoyés** là où ils étaient en entrant (devant le portail, dans le monde normal).

## 8. Forteresse ennemie configurable
- **Plusieurs formes** de forteresse (préréglages), choisies dans l'éditeur ; plusieurs peuvent être proposées pour une Faille (tirage). Visuels à proposer par Claude chat.
- **Points d'apparition des catalyseurs** configurables.
- Chaque forme : chemin vers la base, points d'apparition des ennemis, emplacement du bouclier et du portail.

## 9. Éditeur — onglet « La Faille »
Phases (activables, nombre de vagues de Horde, boss), Siège (nombre d'assauts avant la Contre-offensive, durées, boss, ressources minimales par calme, rayon final de l'île), Contre-offensive (nombre et PV des catalyseurs, rythme des ennemis), Titan (entité, PV, taille, distance, compétences de Titan, avatars et % de PV, phases, plafond des fenêtres de vulnérabilité), forteresses (formes, catalyseurs), engins de siège (munitions, cadence, dégâts, coûts d'amélioration, variantes), adaptation au nombre de joueurs, points de reprise, pause de fin (4 min), récompenses (coffre roulette du Titan, trophée).

## 10. Reste à faire
- **Visuels** : formes de forteresse ; premier **Titan** et storyboard de sa cinématique ; variantes de niveau 3 des engins.
- **Récompenses** de fin (coffre du Titan, trophée, objet unique par Faille ?).
- **Ruines réparables** : à tester dans le Siège avant de décider pour le Kingdom.
