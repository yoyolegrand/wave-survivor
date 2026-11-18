# Tests 2.1 — « La Faille s'étend »

## 1. Socle des îlots et du pont
- [x] Fin de l'agrandissement de l'île (début du Siège) : message « Des îlots dérivent… », île minière visible au nord (~27 blocs du bord)
- [x] Rien côté forteresse (est) ni côté Titan (sud)
- [x] Boutique du Monolithe › Défenses › Engins : ligne « Pont vers l'île minière », bouton Construire
- [x] Sans 20 bois + 20 pierre au trésor : refus avec message ; avec : le trésor baisse
- [x] Le pont se construit tout seul (~10 s) : blocs fantômes lumineux qui descendent, lanternes violettes sur poteaux tous les 8 blocs, lanternes pendues sous le tablier
- [x] `/ws rift islets` : état (côté, pont, PV des 5 segments, filons vivants)
- [x] `/ws rift islets break 3` : le segment 3 s'effondre (blocs qui tombent), message d'alerte
- [x] Clic droit avec 16 pierre en main près du trou : le segment se reconstruit (même animation), pierre consommée ; moins de 16 : refus
- [x] Coincé sur l'îlot : on peut poser des blocs entre l'îlot et l'île (et casser ceux qu'on a posés), pas casser le terrain de l'îlot
- [x] Chute dans le vide : règles habituelles de la Faille
- [x] Calme prolongé : +60 s en solo, +30 s à 2, rien à 3+ (message)

## 2. Île minière
- [x] Terrain : plat côté pont, monte vers le fond, mine décorative au fond (butte, galerie étayée, lanternes, rails, wagonnet, tonneaux), géode d'améthyste à gauche, filons de minerai sous l'île
- [x] 8 filons au début du Siège : 4 fer, 2 émeraude, géode d'améthyste, cristaux d'Essence (sans compte à rebours)
- [x] Pioche minimum : pierre pour fer / améthyste, fer pour émeraude / Essence
- [x] Butin : fer brut, émeraudes (→ trésor au ramassage), éclats d'améthyste, Essence au trésor (cristaux) ; pierre + fer au trésor à chaque filon épuisé
- [x] Début de chaque Calme du Siège : ~50 % des filons épuisés repoussent (75 % avec un Mineur), message
- [x] Gardes au début du Siège et de chaque Calme (5 points), les anciens disparaissent
- [x] Mineur : colonne de lumière / flèche sur les filons de l'îlot
- [x] Île principale : moins de gisements (40 %), plus d'émeraude ni d'améthyste
- [x] Monstres de la Faille : éclat d'améthyste de temps en temps (12 %)
- [x] Après le Siège (Contre-offensive, Titan) : îlot toujours là, plus de repousse ni de gardes

## Correctifs après le 1er test
- [x] Île au coffre : elle apparaît et monte bien (minuterie corrigée)
- [x] Brise-pont : apparaissent en Siège (minuterie corrigée ; `/ws rift islets breaker` pour aller vite)
- [x] Export d'une horde : lien « [Ouvrir le dossier d'export] » dans le chat (solo / hôte LAN), ouvre l'explorateur sur config/wavesurvivor/export
- [x] Île au coffre : grand coffre roulette posé sur le piédestal dès que l'île est montée, contenu visible
- [x] Coffre des Ruines : tous les gardiens tués → s'ouvre au clic droit **ou** au clic gauche (1 tirage gratuit par joueur), puis le clic gauche redevient l'aperçu ; avant : « Le coffre est enchaîné »
- [x] Gardiens de l'île : restent sur l'île (celui qui s'éloigne est ramené au centre dans un éclat violet) et attaquent le joueur le plus proche ; un gardien poussé dans le vide compte toujours comme tué
- [x] Éditeur › Marchand : liste libre (nom cliquable pour changer d'objet, × quantité, ✦ prix, stock, ✖), « + Ajouter », « ↺ Articles de base », pages si plus de lignes ; plus de chevauchement « stock » dans la colonne de droite
- [x] Marchand en jeu : articles ajoutés vendus (quantité, prix, stock respectés), infobulle de l'objet ; nouvelle horde Faille = les 12 articles de base
- [x] Pont en diagonale (île du Marchand au nord-ouest, île au coffre) : plein, sans trou, bordures et lanternes régulières
- [x] Île du Marchand en diagonale : échoppe droite (axes du monde), marchand dégagé derrière son comptoir, allée jusqu'au pont
- [x] Prix ×3 : Clé du Titan 24, Poussière de Faille ×4 30, Éclat de Faille 120
- [x] Capture de l'autel : écran à 3 cartes (Rempart / Arsenal / Prospérité) qui s'ouvre tout seul pour ceux qui sont dans le cercle, cartes qui arrivent l'une après l'autre ; clic droit sur l'autel pour le rouvrir
- [x] Cristaux de l'autel : grand cristal blanc facetté (modèle du cristal du Monolithe) qui flotte et tourne, sombre tant que l'autel n'est pas pris, allumé + 4 mini-cristaux en orbite une fois capturé ; petit cristal sur chaque pilier, allumé selon la progression
- [x] Écran des bonus fermé par Échap : il revient en rentrant dans le cercle, par clic droit sur n'importe quel bloc du cercle (autel, estrade, dallage) ou par le lien [Ouvrir] dans le chat
- [x] Plus de colonne de barres de l'End au-dessus de l'autel (et l'ancienne disparaît d'une partie reprise)
- [x] Brise-pont pondu à côté du pont : il monte sur le tablier et se met à ronger (ne reste plus planté)
- [x] Kit de pont : répare un segment effondré
- [x] Structure de l'île minière intacte, pont attaqué par les Brise-pont
- [x] Montée de niveau de la Mairie : les Golems de fer n'essaient plus d'attaquer le cristal du Monolithe
- [x] Ancreur (n'importe où, même loin de 0,0) : l'ancre plantée enchaîne bien le joueur le plus proche (chaîne visible entre le joueur et l'ancre), **Lenteur V**, pas de message ; **un coup** sur la chaîne ou sur l'ancre la brise et retire la lenteur
- [x] Clés lâchées par les monstres (Faille, horde, Kingdom) : uniquement des clés de coffres qui apparaissent aux pauses de la partie (plus de clés de Reliquaires inutilisables dans la Faille)
- [x] Brasier des offrandes (Kingdom) : accepte maintenant bois, planches, pierre, fer, monnaie ; refuse toujours objets enchantés, objets du mod et clés
- [x] Fiche d'une défense (ex. Atelier de réparation niv. 3, Armurerie de campagne) : la ligne bleue de la spécialisation ne déborde plus sur le trait
- [x] Effet de Faille : ~1 coup sur 20 d'un ennemi de la Faille le pose (icône violette), +1 niveau et +5 s à chaque nouveau proc (5 niveaux, 30 s max) ; soins reçus −10 % par niveau (régénération, pommes, potions)
- [x] Brute de pierre : recule beaucoup moins (validé)
- [x] Brute de pierre de la Faille **dans la horde** : 130 PV (à l'œuf : 120, normal) ; robustesse d'armure 2
- [x] Ancreur : aucun recul pendant qu'il lève, plante ou tient l'ancre ; robustesse d'armure 2
- [x] Monstres de horde sans résistance au recul réglée (ex. Ravageur, Hoglin) : ils gardent leur résistance naturelle
- [x] Coffre du Titan : Poussière de Faille 10–14 (commun) et 20–24 (rare)
- [x] Balistes célestes de la Faille : plus besoin de flèches, 16 tirs puis courte recharge, en boucle
- [x] Sortie de la Faille (portail de fin, fermeture, reconnexion après fermeture) : toujours devant le Monolithe qui l'a ouverte, jamais au spawn ni au hasard ; la ligne « [Faille] Sortie de … (monolithe) » apparaît dans le log

## 3. Île-autel
- [x] Île-autel visible à l'ouest (rayon 12) : dallage en damier, cercle violet lumineux, estrade dorée, cristal flottant sombre, 4 piliers sombres
- [x] Si l'île minière et l'île-autel sont réglées sur le même côté : l'autel prend un autre côté libre
- [x] Boutique : ligne « Pont vers l'île-autel » (4e ligne des Engins), le pont se construit
- [x] Capture : rester dans le cercle → message de progression, les piliers s'allument un par un (~30 s seul, plus rapide à plusieurs)
- [x] Un monstre dans le cercle fige la barre (message « Capture figée »)
- [x] Capturé : cristal et piliers allumés, colonne lumineuse vers le ciel, son, message ; écran à 3 cartes pour ceux qui sont dans le cercle
- [x] Le choix marche pour un joueur non-op ; un seul choix par capture (un 2e joueur : « déjà choisi ») ; clic droit sur l'autel sans bonus choisi → l'écran revient
- [x] Rempart : le Monolithe regagne 1 % de ses PV toutes les 10 s
- [x] Arsenal : catapultes / balistes font plus mal (+20 %)
- [x] Prospérité : plus de pierre / bois / fer au trésor, plus d'émeraudes et d'améthyste sur les monstres ; démolir un bâtiment ne rend pas plus
- [x] Début de chaque Assaut du Siège : 6 monstres apparaissent au fond de l'île-autel et marchent vers le cercle (message)
- [x] Seuls dans le cercle ~20 s : alerte « reprennent l'autel », piliers qui s'éteignent, puis autel repris (bonus perdu, faisceau éteint)
- [x] Revenir dans le cercle pendant la reprise : l'autel remonte à 100 %
- [x] Recapturer : nouveau choix de bonus
- [x] Après le Siège : plus de raid, bonus gardé jusqu'à la fin de la Faille
- [x] `/ws rift islets capture` : capture immédiate (test) ; `/ws rift islets` affiche l'état de l'autel

## 4. Marchand du Néant
- [x] Île du Marchand au nord-ouest (rayon 9) : mousse et roche, tapis violet, échoppe à auvent violet, étagères, comptoir, gros cristal d'améthyste flottant, lanternes flottantes
- [x] Boutique du Monolithe : 5e ligne « Pont vers l'île du Marchand »
- [x] 1er Calme du Siège : pas de marchand ; 2e Calme : il arrive (particules, message), tourné vers le pont, derrière son comptoir
- [x] Clic droit : boutique du Marchand (jamais l'écran d'échange vanilla), 12 articles, solde d'améthyste affiché, infobulle au survol
- [x] Achat : améthyste retirée de l'inventaire, objet reçu ; pas assez : bouton grisé
- [x] Consommables : 4 max par joueur et par Calme (« reste N ») ; Clé du Titan 2 / Calme, Poussière ×4 1 / Calme, Relance 1 / Calme (partagés) ; Éclat de Faille 1 par partie
- [x] Nouveau Calme : stock renouvelé
- [x] Pendant un Assaut : bouclier de particules, clic → « boutique fermée »
- [x] Contre-offensive : ouvert ; combat du Titan : fermé
- [x] `/ws rift islets merchant` : arrivée immédiate (test) ; `/ws rift islets` montre son état

## 5. Objets du Marchand
- [x] Textures et noms des 8 objets (onglet créatif)
- [x] Fiole du Néant : se boit, +8 cœurs
- [x] Élixirs : Résistance I / Force I pendant 2 min
- [x] Pierre de rappel : téléporte à côté du Monolithe (pas dans un mur), consommée
- [x] Kit de pont : près d'un segment effondré → reconstruit, consommé ; sinon message
- [x] Kit d'engin : clic droit sur un engin abîmé → +50 % PV ; engin plein → message, pas consommé
- [x] Lanterne de vigie : monstres proches en surbrillance 30 s (visibles à travers les blocs)
- [x] Relance de bénédiction : +1 relance au prochain choix de cartes

## 6. Île au coffre
- [x] Une fois par partie, à un Calme du Siège entre le 2e et le 4e : annonce, l'île en ruines monte du vide sur le côté libre restant (sud-ouest par défaut)
- [x] Puis un pont de cristal (verre violet + perlelampes) se forme petit à petit jusqu'à elle, message
- [x] Décor : piédestal avec le Coffre des Ruines, 4 piliers coiffés d'un cristal, chaînes vers le coffre, colonnes cassées, éclats dorés flottants
- [x] En posant le pied sur l'île : 3 à 5 gardiens d'élite (Gardien / Lame / Archer des ruines) + Gardien du trésor (Ravageur 220 PV, barre de vie jaune), message
- [x] Chaque gardien tué brise une partie des chaînes (cristal éteint, son, message) ; le dernier tué ouvre le coffre
- [x] Un gardien tombé dans le vide compte comme tué (pas de blocage)
- [x] Coffre ouvert : chaque joueur de la Faille a 1 tirage gratuit sans clé ; un 2e clic demande une clé
- [x] Butin : émeraudes, améthyste, Poussière de Faille, pomme dorée, Clé scellée, Reliquaire scellé, rare Éclat de Faille ; la Chance compte
- [x] Début de l'Assaut suivant : l'île repart (blocs et pont retirés), joueurs dessus ramenés sur l'île principale ; coffre non ouvert → « coffre perdu »
- [x] Une roulette en cours au moment du départ : l'île attend la fin du tirage
- [x] Le pont de cristal ne retire aucun bloc de l'île principale en partant
- [x] `/ws rift islets chest` : arrivée immédiate (pendant le Siège) ; `/ws rift islets` montre son état

## 7. Brise-pont
- [x] Modèle et texture : carapace violet sombre en 3 plaques, veines cyan lumineuses, 5 épines de cristal, point faible cyan sur le dos, 2 grands yeux cyan + 2 petits violets, 6 pattes ; pieds posés au sol (ni enfoncés ni flottants)
- [x] Animation de marche (trépied) [marche validée] ; pose « ronge » (corps plaqué, tête plongée, mandibles qui claquent)
- [x] Œuf d'apparition dans l'onglet créatif, nom fr/en
- [x] Début de chaque Assaut du Siège : 1 à 2 par pont construit (3 en même temps max) ; ~3 s avant : particules violettes et raclement sous le segment
- [x] IA (corrigée) : pondu à l'œuf près d'un pont construit, il marche jusqu'au segment le plus proche (32 blocs) et le ronge ; pas de pont à portée → il chasse le joueur comme une araignée ; pont inaccessible ~20 s → il abandonne et chasse
- [x] Plus aucun segment debout : il redevient un chasseur de joueurs
- [x] Il grimpe depuis le vide à travers le bord du pont, se pose sur le tablier et ronge : fissures (briques fendues, gouttes de lave), segment effondré en ~15 s, puis il saute sur le segment suivant (vers l'îlot d'abord)
- [x] Mord les joueurs tout près pendant qu'il ronge
- [x] Coup dans le dos ou depuis au-dessus (flèche plongeante) : dégâts ×2 (éclat cyan, son de cristal)
- [x] Recul renforcé : un bon coup (Recul / sprint) le jette dans le vide → mort immédiate
- [x] Lanterne de vigie : il brille, même sous le pont
- [x] Début du Calme : les Brise-pont restants redescendent dans le vide ; après le Siège : plus aucun
- [x] `/ws rift islets breaker` : un Brise-pont sous le premier pont construit (test)
- [x] Éditeur : section Brise-pont (activé, nombre par pont, max, durée de rongement, PV)

## Sauvegarde et reprise (§ A)
- [x] Quitter / relancer pendant le Siège : îlot reposé, pont acheté reposé avec ses segments effondrés, filons vivants reposés
- [x] Reprise à la Contre-offensive : îlot et pont toujours là
- [x] Île au coffre : venue / ouverte retrouvé à la reprise ; sauvegarde pendant l'événement sans ouverture → elle revient au Calme suivant
- [x] Marchand : arrivé ou pas, stock du Calme, Éclat déjà acheté — retrouvés à la reprise
- [x] Autel capturé + bonus : retrouvés à la reprise (faisceau allumé) ; capture en cours : repart de 0

## Éditeur
- [x] Onglet Faille › Îlots : réglages enregistrés (îlots, distance, Calme, pont, île minière et son côté, repousse, part des gisements, gardes, améthyste, île-autel : côté, capture, reprise, raid, valeurs des bonus)
- [x] Faille › Îlots : 5 sous-pages (Îlots et ponts, Île minière, Île-autel, Marchand, Île au coffre), chacune tient à l'écran
- [x] Île au coffre : choix du Gardien (liste / œuf) pris en compte

## Codex
- [x] Page « Îlots de la Faille » dans le sommaire (après Créatures de la Faille), texte complet fr / en

## Import / export de hordes (sécurisé)
- [x] Tests JUnit `HordeArchiveTest` (20) : aller-retour des 4 modes, fichiers piégés refusés, ancien format accepté, garde-fous
- [x] Exporter une Faille et une Invasion, puis les déposer dans `config/wavesurvivor/import` : `/ws import` affiche **[Faille]** (violet) et **[Invasion]** (rouge)
- [x] Faille exportée sans Pehkui installé : l'écran signale « Mods manquants : pehkui »
- [x] Zip avec un fichier en plus (ex. `setup.exe`) : ligne grisée « ⚠ Fichier inattendu dedans : setup.exe », bouton Importer désactivé, rien d'importé
- [x] Ancien export (2.0) toujours importable
- [x] Horde aux valeurs extrêmes (ex. 100 vagues) : lignes jaunes d'avertissement, import quand même possible

## Correctifs et ajouts du 16/11 (après tests)
- [x] Sortie de la Faille par le portail (défaite / victoire) : arrivée devant le Monolithe, plus dans l'océan aux coordonnées de la Faille ; entrée par le portail toujours OK
- [x] Reprise d'une partie sauvegardée (Faille au Siège, horde, Kingdom) : bénédictions retrouvées ; le choix du Calme n'est pas reproposé s'il était déjà pris ; « Abandonner » efface les bénédictions
- [x] Brise-pont (`/ws rift islets breaker`) près de l'île : ne reste plus coincé dans les blocs, ne s'étouffe plus
- [x] Effet de Faille : infobulle sans description grise, seulement la ligne rouge « −10 % Soins reçus » par niveau
- [x] Île au coffre : gardiens restent sur l'île et attaquent ; clic gauche ouvre aussi le coffre une fois déverrouillé
- [x] Éditeur › Faille › Îlots › Île au coffre : boutons ★ Gardien du trésor / élites / + / ✖ / ↺, fiche de monstre (type, nom, PV, dégâts, vitesse, taille, compétences, équipement, butin), sans Briseur de Monolithe ni bâtiments ; réglages pris en compte en jeu
- [x] Chaos (toutes les hordes) : bouton Déclencheur (Minuteur du Chaos / Minuteur perso min–max / Par Calme tous les N / Par Assaut tous les N) + quantité × ; marche même Chaos désactivé ; horde classique (pause / vague), Kingdom, Faille
- [x] La Faille : arbre de cristal, éboulis, filon de fer (chaque Calme) et géode (un Calme sur deux) visibles dans l'onglet Chaos et modifiables ; plus d'ajout caché
- [x] Vagues spéciales : Marée d'éclats (Horde), Colonne des Brutes (Siège), Essaim de Néantides (Contre-offensive), Garde du Titan (Titan)
- [x] Boss du Siège : Colosse de la Faille au 4e assaut (950 PV, charge, frappe tellurique, cri, invoque des Rootlings)
- [x] Éditeur : le champ « Chance… » des vagues spéciales ne chevauche plus son texte ; plus de « vanilla » sur l'onglet Titan des Monstres

- [x] Île au coffre en partie normale (sans commande) : arrive toujours pendant un Calme du Siège (tirage borné aux Calmes existants : Assauts − 1)
- [x] Fin de la Contre-offensive (cœur détruit) : 10 s avant la cinématique du Titan (au lieu de 5)
- [x] Charge du Titan (séisme + bord de l'île arraché) : dégâts aux bâtiments divisés par 2

## Difficulté dans la Faille (17/11)
- [x] Faille lancée en Cauchemar : log « [Difficulté] nightmare » ; Zombie de la Faille / Archer plus costauds qu'en Normal (×2 PV), plus nombreux (×1,5)
- [x] Siège, Contre-offensive, Titan : toujours en Cauchemar (PV des monstres du Siège ×2, renforts de la Contre-offensive PV ×2)
- [x] Titan en Cauchemar : PV ×2,2, dégâts aux joueurs ×1,6 ; dégâts aux bâtiments inchangés
- [x] Mutateurs choisis à l'autel : actifs dans la Faille et gardés d'une phase à l'autre
- [x] Reprise d'une Faille en Cauchemar : toujours en Cauchemar
- [x] Fin de la Faille : plus de difficulté / mutateurs actifs ensuite (horde normale lancée après = Normal)

## Armure de la Faille (17/11)
- [x] Établi : pièce en fer + 8 Poussières de Faille → pièce de la Faille au palier I (icône « fissurée », cristaux éteints, pas de calque lumineux), stats de fer (2 / 6 / 5 / 2), durabilité de netherite, rareté peu commune
- [x] Forge (écran Renaissance › Forge › bouton « Armure ») : liste des pièces (inventaire + portées), coût affiché (vert / rouge), bouton « Améliorer (II) » puis « (III) » ; plastron au III : + 1 Éclat de Faille
- [x] Après chaque palier : cristaux allumés sur le joueur et dans l'icône, crête / épaulières / éclats dorsaux qui apparaissent (II puis III), enchantements et durabilité gardés, message « … passe en Palier II »
- [x] Infobulle : palier, stats (armure, robustesse 2 / 3, recul 0,1 au III), prochaine amélioration (lieu + coût), bonus d'ensemble (grisé si l'ensemble n'est pas porté), ligne Iron's Spells si le mod est là, « choix définitif » au palier III
- [x] Sanctuaire › Artisanat : entrées Bénie I-III / Corrompue I-III ; aperçu de la pièce après amélioration ; « Aucune pièce éligible » sans pièce au bon état ; Bénie I sur une pièce au palier III → nom « … béni(e) de la Faille », couronne d'éclats, enchantements gardés
- [x] Pièce Bénie : l'entrée Corrompue I la refuse (et inversement) ; Bénie II / III seulement après le niveau précédent ; Forge du Sanctuaire : seulement les fragments de grâce sur une pièce bénie
- [x] Corrompue : grandes cornes et épines dorsales, calque rose dans le noir ; Bénie : calque doré
- [x] Ensemble (4 pièces) : Effet de Faille plafonné au niveau 4 / 3 / 2 selon le palier le plus bas ; −10 % / −15 % de dégâts des ennemis de la Faille aux paliers II / III
- [x] Onde de faille (palier III) : un coup qui fait passer sous 35 % de vie → ennemis repoussés à 4 blocs + Lenteur II 3 s, particules turquoise, message « Onde de faille ! » ; pas de 2e Onde avant 45 s
- [x] Grâce (4 pièces bénies) : plus d'Effet de Faille du tout ; soins reçus +5 / 10 / 15 % ; l'Onde (dorée) soigne les alliés à 6 blocs
- [x] Faim (4 pièces corrompues) : l'Onde (rose) inflige 4 dégâts + Flétrissement, recharge 30 s ; vol de vie 3 % (niveau II) / 5 % (niveau III)
- [x] Pièces bénies et corrompues mélangées : seulement l'Ancre (palier III)
- [x] Iron's Spells : +6 % sorts d'Ender par pièce au palier III ; Bénie : + sorts Sacrés et +25 mana ; Corrompue : + sorts de Sang et +25 mana
- [x] Epic Fight en mode combat : le casque reste en place, les pièces masquées (couronne, cornes…) ne réapparaissent pas
- [x] Support d'armure, et armure de la Faille mélangée à une autre armure : rien ne flotte ni ne se décale
- [x] Codex : section « Armure de la Faille » dans « Artisanat sacré et profane » et dans « La Faille »

## Faille en coop (17/11)
- [x] À 2 joueurs ou plus : les coffres roulette (horde et Kingdom) disparaissent au début de chaque vague / Assaut, pas d'empilement
- [x] Pendant une Faille, rien n'apparaît au spawn de l'Overworld (particules des coffres)
- [x] Victoire à plusieurs : chaque joueur reçoit le message de PR, les Éclats, les reliquaires, le butin du Titan et une Clé du Titan (tester avec un joueur mort au moment de la victoire)
- [x] Bilan d'Assaut du Kingdom et podium de la Faille : les éliminations de chaque joueur sont comptées (invocations / animaux compris)
- [x] Difficulté (Nightmare) : gardes de l'île minière plus nombreux (×1,5) et plus costauds ; gardiens de l'île au coffre plus nombreux (12 max) ; renforts de la Contre-offensive plus nombreux. Catalyseurs inchangés

## Tuto du Kingdom (17/11)
- [x] Éditeur : sous-onglet « 🎓 Tutoriel » (cases Accompagner / Premier Calme sans chrono, sécurité en secondes) ; une horde neuve a le tuto décoché
- [x] Kingdom avec Tuto coché, joueur neuf : l'écran « Le cycle du Royaume » (v3) s'ouvre au lancement ; à l'échelle d'interface 3, comparé à docs/kingdom_tuto/apercu_cycle.png, les textes FR et EN tombent dans leurs cadres sans déborder (but, diorama, 3 cartes d'étapes, Victoire / Défaite, bouton « J'ai compris »)
- [x] Touche Prêt changée dans les Contrôles : la carte « Calme » affiche la nouvelle touche
- [x] Bandeau CALME / ASSAUT au centre (≈ 3 s), teinte dorée au Calme, rouge qui pulse au début de l'Assaut ; cloche grave au lancement ; pas d'alerte à 60 / 30 / 10 s
- [x] Liste « À faire » sous le trésor : lignes justes (réparations + devis, pièces à dépenser, catalyseur, gisement, présage pour l'Augure, Porte ouverte à portée, Prêt) avec flèche et distance ; les lignes faites se cochent ; « Victoire : détruire les N Portes » en bas
- [x] Liste « À faire » au Calme : objectif temporaire en tête avec son chrono et sa flèche (convoi, champion, trésor, foyers) ; Porte en 3 étapes : 1. ferme le catalyseur → 2. tue les Gardiens de la Porte X (nombre restant + flèche) → 3. frappe la Porte X
- [x] Pendant l'Assaut : PV du Monolithe, Portes restantes, ennemis en vie, menace la plus proche + flèche
- [x] Astuces (fil d'événements) une seule fois chacune : premier Calme, premier Assaut, Porte protégée, catalyseur, petite brèche, bâtiment abîmé, trésor, Porte détruite, mort, présage
- [x] Bilan d'Assaut : 1 ou 2 lignes « 💡 Conseils » (Porte qui perce, Monolithe à −30 %, bâtiments détruits, défenses qui travaillent peu, pièces en réserve, catalyseur pas fermé)
- [x] Premier Calme sans chrono (barre « Prends ton temps… · Prêts 0/1 ») : l'Assaut part quand tout le monde appuie sur Prêt (G), ou au bout de la sécurité (600 s par défaut)
- [x] Même joueur, deuxième partie de ce Kingdom : plus aucune aide, premier Calme normal (10 s)
- [x] Multijoueur, un nouveau et un ancien : seul le nouveau voit les aides ; le premier Calme attend quand même le Prêt de tous
- [x] Tuto décoché : comportement actuel inchangé (pas de « Prêt » dans les Calmes du Kingdom)
- [x] `/ws tuto reset` (soi-même), `/ws tuto reset <joueur> [horde]` et Codex › Mode Kingdom › « Revoir le tuto » : l'accompagnement revient ; « Le cycle » (Codex) et « Cycle » (Carnet) rouvrent l'écran
- [x] Option client `config/wavesurvivor/client_options.json` › `"kingdomTutorial": false` : aucune aide pour ce joueur
