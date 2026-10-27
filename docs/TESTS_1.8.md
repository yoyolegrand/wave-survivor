# Tests 1.8 — suivi

Cocher `[x]` quand c'est validé en jeu. Une ligne en échec : la laisser décochée et noter le souci à côté.
Branche de travail : `dev-1.8` (`mod_version=1.8.0`).

## Finitions bénédictions
- [x] Éditeur : un niveau avec effet spécial affiche son nom, sa description (survol) et ses paramètres modifiables ; changer une valeur, enregistrer, le menu de choix la reflète ; ✕ retire l'effet ; « Ajouter » (type ◀) en crée un
- [x] `/ws benediction mine` : totaux cumulés (stats + effets spéciaux) et liste des bénédictions ; bouton « Mes bonus » dans l'écran de choix ; vide après la fin de la horde
- [x] Plus de message « bénédictions dissipées » en se reconnectant ; après une partie perdue puis reconnexion, aucune bénédiction ne reste

- [x] Menu du Monolithe (horde ET Kingdom) : bouton « Bénédictions » à gauche de la fenêtre, ouvre le résumé de tes bénédictions ; Fermer revient au menu du Monolithe

## Lot A — réglages, aperçu, garder une carte
- [ ] Éditeur (op) → bouton « Réglages » : modifier le nombre de cartes (ex. 4), un poids de rareté, les relances au départ ; Enregistrer ; le prochain menu de choix le reflète. « Valeurs du mod » rétablit tout. Bouton grisé tant qu'une catégorie a des modifications non enregistrées
- [x] Écran de choix : sous chaque bonus, une ligne grise « total actuel → total après » (vide au premier choix, croît ensuite)
- [ ] Bouton « Garder » sous chaque carte : une carte gardée reste à sa place après « Relancer » (qui coûte 1 relance) ; impossible de tout garder [bouton vu en jeu, comportement à tester] ; les cartes gardées s'effacent à la vague suivante / après un choix

## Lot B — synergies et malédictions
- [x] Synergies : prendre 3 bénédictions d'une même famille (ex. attaque) -> bonus de palier 1 appliqué (stat visible dans « Mes bonus », ligne « Synergie … ») ; la carte suivante de cette famille affiche « Synergie … : x/5 » puis « PALIER n ! » ; tout disparaît à la fin de la horde
- [ ] Malédictions : en réglant la chance à 1 (Réglages -> « Chance d'une malédiction par menu »), une carte rouge « ☠ MALÉDICTION » apparaît dans chaque menu (jamais deux) ; la choisir applique bonus ET malus ; elle peut être bannie ; remettre 0,1 ensuite
- [ ] Réglages : interrupteurs « Synergies de famille actives » et « Malédictions actives » fonctionnent (décochés = plus de synergie, plus de carte rouge)
- [ ] Éditeur : on peut créer une catégorie de famille « Malédiction » (négatif autorisé dans les montants)

## Lot C — packs et statistiques
- Packs de test prêts (copies dans `docs/packs_test/`, déjà posés dans `config/wavesurvivor/blessing_packs/`) : `test_malediction` (catégorie curse « Malédiction de la Lame », catégorie « Souffle du vent », réglage chance de malédiction = 1) et `test_conflit` (même id « TEST_VITESSE_PLUS » avec d'autres valeurs). Marche à suivre : `/ws benediction import test_malediction` -> une carte ☠ dans chaque menu + les 2 nouvelles catégories dans l'éditeur ; `/ws benediction import test_conflit` -> « TEST_VITESSE_PLUS » ignoré (conflit) ; avec `overwrite` -> remplacé ; Éditeur -> Packs -> exporter sous un autre nom -> le fichier contient bien tes catégories. NB : un export ne contient que ce que tu as créé ou modifié (d'où l'export « vide » tant que rien n'a été créé) ; si tu as déjà des réglages modifiés, le réglage du pack n'est importé qu'avec `overwrite`
- [ ] Éditeur -> « Packs » : saisir un nom, Exporter -> fichier créé dans `config/wavesurvivor/blessing_packs/<nom>.json` ; modifier ou supprimer une catégorie, Importer le pack -> elle revient (conflits ignorés sans « Remplacer », remplacés avec)
- [ ] `/ws benediction export test` puis `/ws benediction import test` (tab-complétion des noms) ; `overwrite` en dernier argument
- [ ] Jouer une partie (choisir, bannir) puis `/ws benediction stats` : top des choisies avec taux de choix et de victoire, catégories les plus bannies ; après une victoire / défaite, les « parties » et « victoires » montent (un arrêt manuel ne compte pas)
- [ ] Catalogue (`/ws benediction liste`) : une ligne dorée « Proposée … » sous les effets des bénédictions déjà vues ; `/ws benediction stats reset` remet à zéro

## Sanctuaire du Séraphin — étape 1 (bloc incassable + ruine)
- [ ] En Survie : le Sanctuaire ne se casse pas (au pic, à la TNT, au Creeper) ; un piston ne le pousse pas ; Dragon et Wither ne le détruisent pas ; en Créatif il se casse
- [ ] `/locate structure wavesurvivor:seraph_shrine` renvoie une position (région de ~1 500 blocs) ; s'y téléporter : plate-forme de quartz / calcite, 4 colonnes brisées, mousse, et le Sanctuaire à l'étape Neutre au centre
- [ ] Nouveau monde (la génération ne s'applique qu'aux chunks pas encore générés) ; la ruine n'est pas dans l'eau ni dans le vide
- [ ] Réglages : `spacing` / `separation` dans `data/wavesurvivor/worldgen/structure_set/seraph_shrine.json`, biomes dans `data/wavesurvivor/tags/worldgen/biome/has_structure/seraph_shrine.json`
- [ ] Ruine du Sanctuaire : jamais enfouie dans une colline (le volume autour de la statue est dégagé : terre, arbres) ni suspendue au bord d'une falaise (terrain refusé si l'écart de hauteur dépasse 3 blocs sous la ruine) ; plate-forme posée sur des fondations jusqu'au relief

## Sanctuaire du Séraphin — étape 2 (jauge de foi, offrandes, écran)
- [ ] Clic droit sur le Sanctuaire : l'écran s'ouvre (4 onglets, seul « Offrandes » actif ; les autres sont grisés) ; thème doré / argenté / violet selon l'état de la statue
- [ ] Poser une fleur (ou du miel, de l'or…) dans la case « Offrande » puis « Offrir » : la jauge monte (curseur vers l'or), l'objet est consommé ; la valeur s'affiche au-dessus du bouton et dans l'info-bulle de l'objet
- [ ] Chair putréfiée, os, œil d'araignée… : la jauge descend (curseur vers le violet) ; les paliers (foi ≥ 400 Pure, ≥ 100 Apaisée, -99..99 Neutre, -100..-399 Souillée, ≤ -400 Corrompue) font changer le modèle 3D et la texture, avec message « Le Sanctuaire devient : … »
- [ ] À la limite (±600) : l'offrande qui dépasserait est refusée / le surplus reste dans la case ; fermer l'écran rend l'objet restant
- [ ] Événements à moins de 16 blocs : tuer un monstre de horde = +1 ; un animal paisible = -3 ; un villageois = -50 (message au tueur) ; rien si la statue est plus loin
- [ ] Multijoueur : deux joueurs voient et modifient la même jauge ; l'écran se met à jour en direct
- [ ] Rééquilibrage : créer `config/wavesurvivor/shrine_offerings.json` avec p. ex. `{"events":{"villagerKill":-10}}` puis `/ws reload` : la valeur change

## Sanctuaire du Séraphin — étape 3 (production, récolte, fusion)
- [ ] Offrir de quoi passer la statue en Pure (foi ≥ 400) : après 2 minutes, 1 Éclat de grâce apparaît (particules), visible dans l'onglet « Récolte » avec la barre de production qui se remplit ; Apaisée : 1 toutes les 5 min ; Neutre : aucune production ; Souillée / Corrompue : Éclats de souillure (5 min / 2 min)
- [ ] Tuer des monstres de horde à moins de 16 blocs : la barre de production avance plus vite (+10 s par monstre, en plus de +1 de foi)
- [ ] « Récolter » : les Éclats vont dans l'inventaire (ou tombent au sol si plein), le stock revient à 0 ; le stock plafonne à 64 et la production attend
- [ ] Onglet « Fusion » : 9 Éclats de grâce → 1 Fragment de grâce (idem souillure, et Fragments → Cœurs) ; par lots (27 → 3) ; la case de sortie est en prise seulement ; les Cœurs ne sont pas acceptés ; sortie occupée par un autre objet = refus avec message
- [ ] Fermer l'écran rend les objets restés dans les cases (offrande, fusion)
- [ ] Un boss tué dans une horde lâche parfois un Cœur de grâce ou de souillure (5 %, `bossHeartChance`) ; la production et la foi se sauvegardent (quitter / recharger le monde)
- [ ] Rééquilibrage : `production.intervalSeconds`, `stockCap`, `killBonusSeconds`, `fusionCost`, `bossHeartChance` dans `config/wavesurvivor/shrine_offerings.json` puis `/ws reload`

## Sanctuaire du Séraphin — étape 4 (Forge)
- [x] Retouches de texte : jauge (« Pure » / « Corrompue » ne se chevauchent plus), onglet Récolte (stock, cycle « 1 / 2 min », « Dormant » lisibles), onglet Fusion (message sur deux lignes, plus coupé)
- [x] Onglet Forge : poser une arme ou une pièce d'armure + des Éclats de grâce, « Insérer » : une amélioration Consacré est tirée (message), le losange se remplit, le thème devient doré ; la liste à droite montre son nom, son niveau et ses lignes (bonus en vert, malus en rouge) ; survol de la ligne = info-bulle complète
- [x] Coût croissant : 1er emplacement = 1 fragment, 2e = 2, 3e = 3 (« Prochain : n frag. ») ; 4e insertion refusée (« emplacements pleins »)
- [x] Un seul alignement : un objet Consacré refuse les fragments de souillure (et inversement) ; avec des Éclats de souillure, une arme neuve devient Profanée (thème violet)
- [x] Le niveau du fragment fixe la force : Éclat (I) < Fragment (II) < Cœur (III) (comparer les valeurs affichées)
- [x] « Retirer » : coûte 1 fragment du même camp, enlève la dernière amélioration (les fragments ne sont pas rendus) ; plus aucune amélioration = plus d'alignement ; on peut alors retirer / réinsérer pour relancer
- [x] Effets en jeu (équipement porté / arme en main) : Force sacrée / Rage aveugle (dégâts), Célérité / Frénésie (vitesse d'attaque), Égide / Carapace maudite (armure), Vitalité / Chair corrompue (cœurs max), Ancrage / Rempart (résistance au recul), Pas léger / Ombre (vitesse), Plume (dégâts de chute), Souffle de vie (régénération hors combat), Bénédiction durable (l'usure monte moins vite)
- [x] Effets sur événement : Châtiment (morts-vivants + Corrompus), Lame bénie (soin toutes les N victimes), Grâce du savoir (+XP), Soif de sang (vol de vie), Bourreau (dégâts sous 30 % de vie), Venin (Poison + faim), Épines (renvoi), Pacte de survie (survit à un coup mortel, recharge), Marque de la Bête (nuit / jour)
- [x] Plafond : plusieurs pièces avec la même amélioration de pourcentage ne dépassent pas +30 % cumulés (sauf un objet seul plus fort) ; les malus, eux, s'additionnent
- [x] Les bonus disparaissent quand on retire l'équipement ou change d'arme en main (attributs), et au changement de monde / reconnexion tout est recalculé
- [x] Équilibrage : `config/wavesurvivor/shrine_upgrades.json` (coûts, plafond, valeurs par niveau) puis `/ws reload`

## Sanctuaire du Séraphin — étape 5 (Corrompus)
- [x] Statue Souillée (étape 3) avec un joueur à moins de 32 blocs : environ toutes les 3 minutes, 2-3 Rootlings apparaissent autour (5 à 12 blocs, particules d'âmes) ; aucun Stone Brute à cette étape
- [x] Statue Corrompue (étape 4) : groupes plus fréquents (~90 s), jusqu'à 4 créatures, et parfois un Stone Brute (25 %)
- [x] Aucun joueur à moins de 32 blocs : rien n'apparaît, le groupe est prêt et sort dès qu'un joueur approche ; étapes 0-2 : jamais de Corrompus
- [x] Plafond : pas plus de 8 Corrompus de la statue en même temps (`maxAlive`) ; remonter la statue à Neutre arrête les apparitions ; changer d'étape relance le délai
- [x] Réglages : bloc `corruption` de `config/wavesurvivor/shrine_offerings.json` (délais, nombre, `bruteChance`, rayons, plafond) puis `/ws reload`

## Arène « Nécropole des Rois Déchus » — étape 1 (chargeur de plan et pose)
- [ ] `/ws arena list` affiche `necropole_rois_dechus` ; `/ws arena place necropole_rois_dechus` pose l'arène devant toi (message avec le centre et la durée)
- [ ] Forme : cercle de 31 blocs, 10 de haut au plus ; piédestal central (sculpté), 4 braseros d'âmes allumés, couronne de 14 tombes, crypte au nord (portail en barreaux de fer, 2 statues d'os), enceinte en ruine avec 4 entrées, 8 colonnes à lanternes, 3 arbres morts — comparer avec `docs/arenes/necropole/arene_necropole.png`
- [ ] Terrain : sur une pente et sur un relief boisé : arbres et herbes dégagés dans le cercle, fondations comblées (rien ne flotte), anneau de 2 blocs de podzol / terre stérile / mousse qui fond l'arène dans le paysage
- [ ] Rotations `90`, `180`, `270` : la crypte se retrouve à l'est / au sud / à l'ouest, les colonnes, barreaux et bûches restent bien orientés
- [ ] Pas d'eau : poser l'arène sur un lac n'est pas prévu (à éviter pour l'instant)

## Arène « Nécropole des Rois Déchus » — étape 2 (génération dans le monde)
- [ ] Nouveau monde : `/locate structure wavesurvivor:arena_necropole` renvoie une position (forêt sombre, marais, mangrove, taïga ancienne ou plaines) ; s'y rendre : l'arène est complète, même à cheval sur plusieurs chunks (aucune coupure aux limites de chunk)
- [ ] Rotation au hasard : plusieurs arènes (ou `/locate` répété sur d'autres mondes) n'ont pas toutes la crypte au nord
- [ ] Jamais sur l'eau, jamais sur une pente de plus de 4 blocs, pas à moins de ~8 tronçons d'un village
- [ ] Réglages : `config/wavesurvivor/arenas.json` -> `{"generation":{"enabled":false}}` (ou `"disabled":["necropole_rois_dechus"]`, `"maxSlope"`, `"randomRotation"`) puis nouveau monde ; rareté = `spacing` / `separation` dans `data/wavesurvivor/worldgen/structure_set/arena_necropole.json`, biomes = tag `has_structure/arena_necropole`

## Arène « Nécropole des Rois Déchus » — étape 3 (affiliation à la horde)
- [ ] `/ws arena place necropole_rois_dechus` pose aussi l'autel de horde sur le piédestal ; clic droit : l'écran de lancement de « La Nécropole » s'ouvre directement (pas d'écran de liaison, aucun ingrédient)
- [ ] Autel verrouillé : shift + clic ne le délie pas (message « verrouillé ») ; en Survie impossible de le casser (pic, TNT, Creeper, piston, Wither / Dragon) ; en Créatif il se casse
- [ ] Lancer la horde depuis cet autel : les monstres apparaissent dans la crypte (la grille de fer s'ouvre à la première apparition), sur les tombes (terre qui jaillit + son de pelle / mort-vivant) et un peu aux 4 entrées — plus en cercle au hasard
- [ ] Boss : apparaît dans la crypte, grille ouverte ; en fin de partie (victoire, défaite ou arrêt) la grille est refermée
- [ ] Braseros : les 4 feux d'âmes s'éteignent un par un au fil des vagues, et se rallument à la fin
- [ ] Première victoire : environ 5 s après, un coffre roulette (« Reliquaire du Boss ») apparaît dans la crypte avec un message ; aux victoires suivantes, aucun nouveau coffre dans cette arène
- [ ] Arène générée dans un nouveau monde : même comportement (autel posé et verrouillé au premier clic) ; un autel ordinaire (non arène) se lie toujours par recette
- [ ] Réglages : `config/wavesurvivor/arenas.json` -> `"affinity": {"cryptWeight":5,"graveWeight":4,"entranceWeight":1,"gateOpens":true,"braziersFade":true,"rewardChest":true,"rewardChestName":"Reliquaire du Boss"}` puis `/ws reload`

## Sanctuaire du Séraphin — corps physique et info-bulles
- [ ] La statue est solide : impossible de la traverser (colonne de 5 blocs de haut + socle bas autour), ni pour les joueurs, ni pour les monstres, ni pour les flèches ; les ailes, elles, ne bloquent pas
- [ ] Clic droit sur le « corps » de la statue (au-dessus du bloc ou sur le socle) ouvre l'écran comme sur le bloc ; les blocs de collision sont invisibles et incassables en Survie
- [ ] Casser le Sanctuaire en Créatif retire aussi son corps ; une statue posée avant cette mise à jour récupère son corps au bout de quelques secondes ; une ruine générée en a un dès sa génération
- [ ] Info-bulle d'un équipement amélioré (inventaire, barre d'objets, coffre, Forge, n'importe où) : nom coloré doré « ✦ » (Consacré) ou violet « ☠ » (Profané), ligne « Consacré (2/3) », puis chaque amélioration avec son niveau, ses bonus en vert et ses malus en rouge — sans avoir à passer par la Forge
- [ ] Forge : la liste de droite ne coupe plus les noms longs (« … » à la place) ; le détail reste dans l'info-bulle

## Arène « La Brèche Infernale » — étape 1 (pose)
- [ ] `/ws arena list` affiche aussi `breche_infernale` ; `/ws arena place breche_infernale` (et `90`, `180`, `270`) : cratère de netherrack de 33 blocs, parvis de pierre noire avec l'autel au centre, portail géant en ruine au nord (obsidienne, obsidienne pleureuse, or), 5 failles de magma, 3 mares de lave cerclées de pierre noire, 6 colonnes de basalte, forêt carmin à l'ouest, feux ; comparer avec `docs/arenes/breche_infernale/arene_breche_infernale.png`
- [ ] Coffre du portail (au nord, face au sud avant rotation) rempli avec le butin vanilla `minecraft:chests/ruined_portal`, différent à chaque pose ; il suit la rotation
- [ ] Terrain : fondations (rien ne flotte), dégagement jusqu'à 15 blocs de haut (le portail ne touche aucun arbre), raccord qui fond le cratère dans le paysage ; la lave ne déborde pas du cercle

## Arène « La Brèche Infernale » — étape 2 (greffe sur les portails en ruine)
- [ ] Nouveau monde : en parcourant des plaines, forêts, taïgas, savanes, déserts, badlands (`/locate structure minecraft:ruined_portal` ou `minecraft:ruined_portal_desert`), environ un portail de surface sur quatre est une Brèche Infernale (cratère, portail géant, autel, coffre) ; les autres sont des portails vanilla inchangés
- [ ] Les portails souterrains, ceux des océans, jungles, marais, montagnes et du Nether restent vanilla (aucun n'a disparu) ; le portail vanilla n'est remplacé que si la Brèche se génère vraiment (terrain plat à 5 blocs près, pas d'eau)
- [ ] Rotation au hasard, plan centré sur le milieu du chunk du portail, terrain fondu dans le paysage
- [x] Chercher avec `/locate structure minecraft:ruined_portal` (et `minecraft:ruined_portal_desert`) : ce sont les portails du jeu, remplacés une fois sur quatre par la Brèche. NE PAS chercher `wavesurvivor:ruined_portal_*_original` (copies internes, jamais placées : « Could not find a structure » est normal). Dans `logs/latest.log`, chaque tirage retenu écrit une ligne `[Arènes] …` : « greffée à la place du portail en ruine, chunk x,z » ou la raison du refus (portail souterrain, biome non éligible, terrain refusé)
- [ ] Réglages : `config/wavesurvivor/arenas.json` -> `{"graft":{"breche_infernale":0.25}}` (0 = désactivé, 1 = tous les portails éligibles) ; `generation.enabled` / `disabled` coupent aussi la greffe ; biomes éligibles = tag `data/wavesurvivor/tags/worldgen/biome/has_structure/infernal_breach.json`

## Arène « La Brèche Infernale » — étape 3 (affiliation à la horde du Nether)
- [ ] `/ws arena place breche_infernale` pose l'autel au centre du parvis ; clic droit : l'écran de lancement de « Les Terres Brûlées » (horde du Nether, variantes Vanilla / Moddée) s'ouvre sans recette ; shift + clic ne le délie pas ; incassable en Survie (pic, TNT, piston, Wither / Dragon)
- [ ] Partie lancée depuis cet autel : apparitions surtout devant le portail (estrade, particules violettes), dans les 5 failles de magma (lave, fumée, son) et quelques-unes au bord du cratère ; jamais à moins de 3 blocs d'une mare de lave
- [ ] Boss : apparaît au cœur du portail, qui s'embrase de particules violettes pendant le combat (son de portail) puis s'éteint à la fin
- [ ] Les feux du plan reprennent à chaque vague quand ils se sont éteints ; à la victoire environ la moitié du magma refroidit en pierre noire
- [ ] Première victoire : environ 5 s après, un coffre roulette « Reliquaire du Nether » apparaît sur l'estrade du portail (une seule fois par arène) ; le coffre vanilla du portail est déjà là
- [ ] Réglages : `arenas.json` › `affinity` : `cryptWeight` (apparition principale : portail), `graveWeight` (failles), `entranceWeight` (bord), `firesRekindle`, `magmaCools`, `rewardChests` : `{"breche_infernale":"Reliquaire du Nether"}`

## Sanctuaire du Séraphin — étape 6 (cadres d'emplacement animés)
- [x] Un équipement amélioré (1, 2 ou 3 améliorations) a un cadre autour de son emplacement dans l'inventaire, les coffres et la Forge : doré scintillant (Consacré) ou violet qui pulse (Profané) ; le cadre change de modèle avec le nombre d'améliorations (`consecrated_1..3`, `profaned_1..3`) ; rien n'est dessiné au centre de l'icône
- [x] À 2 améliorations : lueur douce (dorée ou violette) sur l'emplacement ; à 3 : Consacré = reflet blanc diagonal qui balaie l'icône toutes les 2,5 s ; Profané = l'icône tressaille en violet (2-3 images sur 12)
- [x] Barre d'objets : cadre et scintillement / pulsation seulement (pas de lueur, de reflet ni de tressaillement)
- [x] Un objet sans amélioration n'a aucun cadre ; pas de ralentissement visible avec un inventaire plein d'objets améliorés
- [x] Option client : `config/wavesurvivor/client_options.json` -> `{"slotFx":"light"}` (version légère partout) ou `"off"` (aucun cadre animé), puis relancer le jeu
- Cadres affinés (bande de 5 px sur 32 contre le bord, centre vide) étirés sur tout l'emplacement 16×16 ; la lueur (2 px, opacité ~0,2), le reflet et le tressaillement restent sur le pourtour / très discrets : l'icône reste entièrement lisible (aperçu simulé : `docs/maquettes_sanctuaire/apercu_cadres_simulation.png`)
- Remarque : la lueur est dessinée par-dessus l'icône avec une opacité faible (pas derrière) ; l'info-bulle et le nom coloré des objets sont déjà en place

## Sanctuaire du Séraphin — étape 7 (Codex, CHANGELOG, config)
- [ ] `/ws codex` : les pages « Sanctuaire du Séraphin » et « Arènes toutes faites » apparaissent dans le sommaire (après « Reliques & Forge »), défilent et se lisent bien en français et en anglais
- [ ] Config : `shrine_offerings.json` (offrandes, paliers, événements, production, Corrompus) et `shrine_upgrades.json` (améliorations, coûts, plafond) dans `config/wavesurvivor/`, avec seulement les champs à changer, puis `/ws reload`

## Cinq nouvelles arènes — pose (`/ws arena place <arène> [rotation]`)
Comparer chaque pose avec les images de `docs/arenes/<arène>/` ; essayer aussi les rotations 90 / 180 / 270.
- [ ] `/ws arena list` : `ilot_du_neant`, `recif_englouti`, `temple_de_re`, `bastion_de_givre` ; `cercle_des_arcanes` seulement si Iron's Spells 'n Spellbooks est installé (sans le mod : « demande le mod » et aucune erreur)
- [x] **Îlot du Néant** : île flottante (dessous en cône, arche de cité de l'End au nord, 2 piliers d'obsidienne pleureuse en cage, chorus, barres de l'End), posée à ta hauteur + 2, sans dégagement ni fondations ni raccord ; le bord reste sans barrière
- [x] **Récif englouti** : île de sable dans un lagon, au niveau de la mer (y 62), fondations de sable jusqu'au fond, temple de prismarine au nord, épave, canaux, coraux dans l'eau ; coffre du temple avec `minecraft:chests/underwater_ruin_big` ; à tester près d'une plage ou d'un océan chaud
- [x] **Temple de Rê** : façade à colonnes, 4 obélisques à pointe d'or, 2 sarcophages, palmiers, cercle de terre cuite bleue, oasis au sud-est et 2 kiosques à toit plat
- [x] **Bastion de Givre** (nouveau plan : pics effilés et inclinés avec pic secondaire, sapins en cônes étagés avec couches de neige, arbre mort aux branches nues ; 14 blocs de haut) : bastion en ruine pris dans la glace, 2 tours creuses (porte au sud, toit à y = 5), surplomb de glace sur 2 piliers, crevasses de glace bleue ; les couches de neige (`snow[layers=2]`) et les branches horizontales (`spruce_log[axis=x/z]`) sont bien posées, et les branches restent bien orientées après rotation
- [ ] **Abris du Bastion** : lancer la horde des Pics Gelés depuis son autel, attendre la tempête : sous le toit d'une tour ou du surplomb on est « à l'abri » (message), dehors on est exposé ; même chose pour le Temple de Rê (oasis, kiosques) avec la tempête de sable
- [x] **Cercle des Arcanes** : grand cercle runique (améthyste, or, calcite), tour de mage en ruine au nord-ouest, 4 pylônes arcaniques
- [ ] Pour chacune : fondations (rien ne flotte, sauf l'Îlot), dégagement des arbres, autel de horde posé au centre sur son piédestal (clic droit : l'écran de lancement de la bonne horde)

## Cinq nouvelles arènes — génération dans le monde
Nouveau monde (ou zone inexplorée) ; chaque arène : rotation au hasard, pente tolérée de 5 blocs, réglages dans `arenas.json` (`generation`), rareté dans `worldgen/structure_set/arena_*.json`, biomes dans `tags/worldgen/biome/has_structure/arena_*.json`.
- [ ] **Îlot du Néant** : dans les îles extérieures de l'End (`/locate structure wavesurvivor:arena_ilot`), une île flottante au-dessus des îles (jamais dans l'île centrale, jamais sur une cité de l'End), sans fondations ; son autel (horde du Néant Éternel) est posé et verrouillé
- [ ] **Récif englouti** : sur les plages et dans les océans chauds / tièdes peu profonds (`/locate structure wavesurvivor:arena_recif`), sol au niveau de la mer, fondations de sable jusqu'au fond, pas de falaise ; île en relief + lagon (35 × 35), varech avec pointe, aucun corail hors de l'eau ; coffre du temple rempli (`underwater_ruin_big`) et coffre de l'épave rempli (`shipwreck_treasure`) ; apparitions dans les bassins (dans l'eau)
- [ ] **Temple de Rê** : dans le désert (`/locate structure wavesurvivor:arena_temple`), pas sur un temple vanilla ; autel de la horde des Sables Ardents ; oasis et kiosques protègent de la tempête de sable
- [ ] **Bastion de Givre** : dans les plaines enneigées, pics de glace et pentes enneigées (`/locate structure wavesurvivor:arena_bastion`) ; autel de la horde des Pics Gelés ; tours et surplomb protègent de la tempête
- [ ] **Cercle des Arcanes** : dans les forêts, bouleaux, forêts sombres, plaines et prairies (`/locate structure wavesurvivor:arena_cercle`) SEULEMENT si Iron's Spells 'n Spellbooks est installé ; sans le mod : aucune génération, aucune erreur

## Performance
- [x] `/ws perf` hors horde : affiche les mesures actuelles ; pendant une horde, après quelques vagues : le résumé de chaque vague apparaît (et dans `logs/latest.log` : lignes « [Perf] vague N … »)
- [x] Mesure réelle (grosse vague testée : OK) : lancer une grosse horde (ou le mode sans fin jusqu'à la vague 30+) en multijoueur ou avec beaucoup de monstres, noter le tick moyen / max (≤ 50 ms = 20 TPS) et dire ce qui est trop lourd
- [ ] `/ws perf verbose` : bascule l'affichage des résumés de vague dans le chat des opérateurs

## Étape 4 — bénédictions spéciales et Chance étendue
- [ ] Les 8 nouvelles catégories apparaissent dans l'éditeur (familles Attaque, Défense, Utilité, Équipe, Pacte) et dans l'écran de choix avec leurs lignes d'effet (en cyan)
- [ ] Exécution : dégâts nettement plus forts sur un monstre presque mort (comparer sans / avec)
- [ ] Aube : au début de la vague suivante, cœurs jaunes d'absorption + message
- [ ] Aimant : les objets et l'XP au sol viennent vers le joueur (pas ceux qu'on vient de lâcher)
- [ ] Bannière (2 joueurs) : l'allié proche inflige plus de dégâts ; Lien de sang : les soins du porteur soignent aussi l'allié proche
- [ ] Verre brisé : dégâts en hausse, PV max en baisse ; plafond de niveau 3 respecté (la carte n'est plus proposée au niveau max)
- [ ] Berserker : plus fort et plus rapide quand les PV sont bas ; armure réduite
- [ ] Fardeau : armure / robustesse élevées, pas de recul, plus lent ; immobile = dégâts reçus réduits de plus en plus ; flèches réduites ; les monstres proches te ciblent
- [ ] Provocation + choix de bénédiction en multijoueur : le joueur qui choisit reste ignoré des monstres (pas de collision)
- [ ] Chance : plus de lots rares aux coffres roulette et au Brasier (avec de la Chance contre sans) ; de temps en temps un monstre de horde lâche un lot en double
- [ ] `/ws benediction liste` : les nouvelles catégories s'affichent avec leurs effets

## Étape 3 — tirage par rareté, Chance, relances, bannissements, famille
- [ ] Fin de vague : 3 cartes de CATÉGORIES différentes ; sur une dizaine de menus, les raretés varient (beaucoup de Commun/Rare, peu d'Épique, rare Légendaire)
- [ ] Les vagues avancées proposent plus souvent du rare / épique / légendaire qu'au début
- [ ] Avec des bénédictions de Chance (ou une potion de Chance) : les raretés élevées sortent plus souvent
- [ ] Bouton « ↻ Relancer (2) » : 3 nouvelles cartes, le compteur baisse ; à 0 le bouton est grisé
- [ ] Bouton « Famille : … » : choisir une famille puis relancer → les cartes viennent de cette famille (celles qui existent)
- [ ] « ✖ Bannir (1) » sous une carte : elle est remplacée, et cette catégorie ne ressort plus de toute la partie
- [ ] +1 relance et +1 bannissement toutes les 5 vagues (message « +1 relance(s), +1 bannissement(s) » à la vague 5, 10…)
- [ ] Solo : le jeu reste en pause pendant les relances ; multijoueur : le joueur reste protégé (et invulnérable) tant qu'il relance / choisit
- [ ] Fin de partie : relances et bannissements remis à zéro à la partie suivante
- [ ] `/ws benediction choose <id>` refuse une bénédiction qui n'est pas proposée (sauf opérateur sans menu en attente)

## Étape 2 — éditeur des bénédictions
- [ ] Bouton « 🙏 Bénédictions » dans l'éditeur de hordes (réservé aux opérateurs) ; l'écran s'ouvre avec les 10 catégories de base et leurs niveaux (carrés de couleur)
- [ ] ◀ ▶ : passer de Commun à Légendaire ; la case « Disponible / Désactivé / N'existe pas » fonctionne (ex. Longévité : Commun et Rare « n'existent pas »)
- [ ] Modifier la valeur d'un effet, ajouter / retirer un effet, changer l'opération, changer le soin : enregistrer ; l'écran de choix en jeu reflète le changement (sans redémarrer)
- [ ] Sélecteur d'attribut « … » : liste avec recherche contenant les attributs de Minecraft ET des mods installés (Apothic, Iron's…) ; en choisir un d'un mod et le voir appliqué
- [ ] Badge `[modifiée · N]`, bouton ↺ (rétablit la base, confirmation au 2ᵉ clic), « ↺ ✖ » (rétablit les catégories supprimées)
- [ ] Créer une catégorie (+), la dupliquer, la supprimer ; le nom, l'icône et la description apparaissent dans l'écran de choix
- [ ] Impossible d'enregistrer pendant une horde (message clair) ; un joueur non opérateur n'a pas accès
- [ ] Multijoueur : les joueurs voient la modification sans se reconnecter

## Étape 1 — bénédictions en données (aucun changement de jeu attendu)
- [ ] Le jeu démarre sans erreur (aucun « Attribut inconnu » dans les logs, ligne « [Bénédictions] 34 bénédictions construites »)
- [ ] Fin de vague : l'écran de choix propose les mêmes bénédictions qu'avant (noms, icônes, couleurs de rareté, descriptions, valeurs)
- [ ] `/ws benediction liste` : le catalogue montre toujours les 34 bénédictions
- [ ] Choisir une bénédiction applique la même statistique qu'avant (ex. Vie : PV max et soin ; Force : dégâts)
- [ ] Une partie en cours (monde de la 1.7) garde ses bénédictions déjà obtenues
- [ ] Multijoueur / serveur dédié : un client qui se connecte voit le même catalogue (écran de choix et liste)
- [ ] Couche du joueur (test manuel) : créer `config/wavesurvivor/custom_blessings.json` avec
      `{"patches":{"LONGEVITE":{"tiers":{"EPIC":{"enabled":false}}}}}` puis `/ws reload` : la Longévité Épique n'est plus proposée, la Légendaire oui

## Arène Kingdom — Le Val de la Couronne
- [ ] `/ws arena place val_de_la_couronne` (sur terrain plat, rotation 0) : 181 × 181 posés, aucun bout flottant dans les coins, raccord autour
- [ ] Le Monolithe est au centre (place de 7 blocs), verrouillé et lié à « Le Siège des Pillards »
- [ ] Clic droit sur le Monolithe : le mode Kingdom démarre (pas de grille d'arène), 4 Portes construites au centre des 4 camps, tournées vers le Monolithe
- [ ] Config de la horde des pillards inchangée (gatePlacement toujours « auto » dans l'éditeur)
- [ ] Le royaume monte jusqu'au niveau 5 sans toucher la palissade (rayon 34)
- [ ] Les troupes empruntent les routes et le pont (ouest)
- [ ] Rotation 90° : les Portes suivent (repères tournés)
- [ ] Génération naturelle du Val de la Couronne : `/locate structure wavesurvivor:arena_val` (très rare : espacement 56 chunks, un peu moins rare qu un manoir à 80 ; plaines, prairies, savanes ; à 14 chunks d'un village au moins)
- [ ] Nouveau monde, s'y téléporter : l'arène se génère d'un coup sans trou ni coin flottant, talus d'herbe, camps à plat, Monolithe au centre
- [ ] Clic droit sur le Monolithe de l'arène naturelle : mode Kingdom, 4 Portes aux camps
- [ ] Val de la Couronne naturel : jamais par-dessus une autre structure (Nécropole, manoir, avant-poste, temples, village) ; vérifier sur plusieurs /locate
