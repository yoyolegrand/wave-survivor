# Tests 2.1 — « La Faille s'étend »

## 1. Socle des îlots et du pont
- [ ] Fin de l'agrandissement de l'île (début du Siège) : message « Des îlots dérivent… », île minière visible au nord (~27 blocs du bord)
- [ ] Rien côté forteresse (est) ni côté Titan (sud)
- [ ] Boutique du Monolithe › Défenses › Engins : ligne « Pont vers l'île minière », bouton Construire
- [ ] Sans 20 bois + 20 pierre au trésor : refus avec message ; avec : le trésor baisse
- [ ] Le pont se construit tout seul (~10 s) : blocs fantômes lumineux qui descendent, lanternes violettes sur poteaux tous les 8 blocs, lanternes pendues sous le tablier
- [ ] `/ws rift islets` : état (côté, pont, PV des 5 segments, filons vivants)
- [ ] `/ws rift islets break 3` : le segment 3 s'effondre (blocs qui tombent), message d'alerte
- [ ] Clic droit avec 16 pierre en main près du trou : le segment se reconstruit (même animation), pierre consommée ; moins de 16 : refus
- [ ] Coincé sur l'îlot : on peut poser des blocs entre l'îlot et l'île (et casser ceux qu'on a posés), pas casser le terrain de l'îlot
- [ ] Chute dans le vide : règles habituelles de la Faille
- [ ] Calme prolongé : +60 s en solo, +30 s à 2, rien à 3+ (message)

## 2. Île minière
- [x] Terrain : plat côté pont, monte vers le fond, mine décorative au fond (butte, galerie étayée, lanternes, rails, wagonnet, tonneaux), géode d'améthyste à gauche, filons de minerai sous l'île
- [ ] 8 filons au début du Siège : 4 fer, 2 émeraude, géode d'améthyste, cristaux d'Essence (sans compte à rebours)
- [ ] Pioche minimum : pierre pour fer / améthyste, fer pour émeraude / Essence
- [ ] Butin : fer brut, émeraudes (→ trésor au ramassage), éclats d'améthyste, Essence au trésor (cristaux) ; pierre + fer au trésor à chaque filon épuisé
- [x] Début de chaque Calme du Siège : ~50 % des filons épuisés repoussent (75 % avec un Mineur), message
- [ ] Gardes au début du Siège et de chaque Calme (5 points), les anciens disparaissent
- [ ] Mineur : colonne de lumière / flèche sur les filons de l'îlot
- [ ] Île principale : moins de gisements (40 %), plus d'émeraude ni d'améthyste
- [ ] Monstres de la Faille : éclat d'améthyste de temps en temps (12 %)
- [ ] Après le Siège (Contre-offensive, Titan) : îlot toujours là, plus de repousse ni de gardes

## Correctifs après le 1er test
- [ ] Pont en diagonale (île du Marchand au nord-ouest, île au coffre) : plein, sans trou, bordures et lanternes régulières
- [ ] Île du Marchand en diagonale : échoppe droite (axes du monde), marchand dégagé derrière son comptoir, allée jusqu'au pont
- [ ] Prix ×3 : Clé du Titan 24, Poussière de Faille ×4 30, Éclat de Faille 120
- [ ] Capture de l'autel : écran à 3 cartes (Rempart / Arsenal / Prospérité) qui s'ouvre tout seul pour ceux qui sont dans le cercle, cartes qui arrivent l'une après l'autre ; clic droit sur l'autel pour le rouvrir
- [ ] Cristaux de l'autel : grand cristal blanc facetté (modèle du cristal du Monolithe) qui flotte et tourne, sombre tant que l'autel n'est pas pris, allumé + 4 mini-cristaux en orbite une fois capturé ; petit cristal sur chaque pilier, allumé selon la progression
- [ ] Montée de niveau de la Mairie : les Golems de fer n'essaient plus d'attaquer le cristal du Monolithe

## 3. Île-autel
- [ ] Île-autel visible à l'ouest (rayon 12) : dallage en damier, cercle violet lumineux, estrade dorée, cristal flottant sombre, 4 piliers sombres
- [ ] Si l'île minière et l'île-autel sont réglées sur le même côté : l'autel prend un autre côté libre
- [ ] Boutique : ligne « Pont vers l'île-autel » (4e ligne des Engins), le pont se construit
- [ ] Capture : rester dans le cercle → message de progression, les piliers s'allument un par un (~30 s seul, plus rapide à plusieurs)
- [ ] Un monstre dans le cercle fige la barre (message « Capture figée »)
- [ ] Capturé : cristal et piliers allumés, colonne lumineuse vers le ciel, son, message ; écran à 3 cartes pour ceux qui sont dans le cercle
- [ ] Le choix marche pour un joueur non-op ; un seul choix par capture (un 2e joueur : « déjà choisi ») ; clic droit sur l'autel sans bonus choisi → l'écran revient
- [ ] Rempart : le Monolithe regagne 1 % de ses PV toutes les 10 s
- [ ] Arsenal : catapultes / balistes font plus mal (+20 %)
- [ ] Prospérité : plus de pierre / bois / fer au trésor, plus d'émeraudes et d'améthyste sur les monstres ; démolir un bâtiment ne rend pas plus
- [ ] Début de chaque Assaut du Siège : 6 monstres apparaissent au fond de l'île-autel et marchent vers le cercle (message)
- [ ] Seuls dans le cercle ~20 s : alerte « reprennent l'autel », piliers qui s'éteignent, puis autel repris (bonus perdu, faisceau éteint)
- [ ] Revenir dans le cercle pendant la reprise : l'autel remonte à 100 %
- [ ] Recapturer : nouveau choix de bonus
- [ ] Après le Siège : plus de raid, bonus gardé jusqu'à la fin de la Faille
- [ ] `/ws rift islets capture` : capture immédiate (test) ; `/ws rift islets` affiche l'état de l'autel

## 4. Marchand du Néant
- [ ] Île du Marchand au nord-ouest (rayon 9) : mousse et roche, tapis violet, échoppe à auvent violet, étagères, comptoir, gros cristal d'améthyste flottant, lanternes flottantes
- [ ] Boutique du Monolithe : 5e ligne « Pont vers l'île du Marchand »
- [ ] 1er Calme du Siège : pas de marchand ; 2e Calme : il arrive (particules, message), tourné vers le pont, derrière son comptoir
- [ ] Clic droit : boutique du Marchand (jamais l'écran d'échange vanilla), 12 articles, solde d'améthyste affiché, infobulle au survol
- [ ] Achat : améthyste retirée de l'inventaire, objet reçu ; pas assez : bouton grisé
- [ ] Consommables : 4 max par joueur et par Calme (« reste N ») ; Clé du Titan 2 / Calme, Poussière ×4 1 / Calme, Relance 1 / Calme (partagés) ; Éclat de Faille 1 par partie
- [ ] Nouveau Calme : stock renouvelé
- [ ] Pendant un Assaut : bouclier de particules, clic → « boutique fermée »
- [ ] Contre-offensive : ouvert ; combat du Titan : fermé
- [ ] `/ws rift islets merchant` : arrivée immédiate (test) ; `/ws rift islets` montre son état

## 5. Objets du Marchand
- [ ] Textures et noms des 8 objets (onglet créatif)
- [ ] Fiole du Néant : se boit, +8 cœurs
- [ ] Élixirs : Résistance I / Force I pendant 2 min
- [ ] Pierre de rappel : téléporte à côté du Monolithe (pas dans un mur), consommée
- [ ] Kit de pont : près d'un segment effondré → reconstruit, consommé ; sinon message
- [ ] Kit d'engin : clic droit sur un engin abîmé → +50 % PV ; engin plein → message, pas consommé
- [ ] Lanterne de vigie : monstres proches en surbrillance 30 s (visibles à travers les blocs)
- [ ] Relance de bénédiction : +1 relance au prochain choix de cartes

## 6. Île au coffre
- [ ] Une fois par partie, à un Calme du Siège entre le 2e et le 4e : annonce, l'île en ruines monte du vide sur le côté libre restant (sud-ouest par défaut)
- [ ] Puis un pont de cristal (verre violet + perlelampes) se forme petit à petit jusqu'à elle, message
- [ ] Décor : piédestal, coffre géant en bois cerclé d'or, 4 piliers coiffés d'un cristal, chaînes vers le coffre, colonnes cassées, éclats dorés flottants
- [ ] En posant le pied sur l'île : 3 à 5 gardiens d'élite (« Gardien des ruines », PV ×2) + Gardien du trésor (Ravageur, barre de vie jaune), message
- [ ] Chaque gardien tué brise une partie des chaînes (cristal éteint, son, message) ; le dernier tué ouvre le coffre
- [ ] Un gardien tombé dans le vide compte comme tué (pas de blocage)
- [ ] Coffre ouvert : le coffre géant éclate, un coffre roulette « Coffre des Ruines » apparaît ; chaque joueur de la Faille a 1 tirage gratuit sans clé ; un 2e clic demande une clé
- [ ] Butin : émeraudes, améthyste, Poussière de Faille, pomme dorée, Clé scellée, Reliquaire scellé, rare Éclat de Faille ; la Chance compte
- [ ] Début de l'Assaut suivant : l'île repart (blocs et pont retirés), joueurs dessus ramenés sur l'île principale ; coffre non ouvert → « coffre perdu »
- [ ] Une roulette en cours au moment du départ : l'île attend la fin du tirage
- [ ] Le pont de cristal ne retire aucun bloc de l'île principale en partant
- [ ] `/ws rift islets chest` : arrivée immédiate (pendant le Siège) ; `/ws rift islets` montre son état

## 7. Brise-pont
- [ ] Modèle et texture : carapace violet sombre en 3 plaques, veines cyan lumineuses, 5 épines de cristal, point faible cyan sur le dos, 2 grands yeux cyan + 2 petits violets, 6 pattes ; pieds posés au sol (ni enfoncés ni flottants)
- [ ] Animation de marche (trépied) ; pose « ronge » (corps plaqué, tête plongée, mandibles qui claquent)
- [ ] Œuf d'apparition dans l'onglet créatif, nom fr/en
- [ ] Début de chaque Assaut du Siège : 1 à 2 par pont construit (3 en même temps max) ; ~3 s avant : particules violettes et raclement sous le segment
- [ ] Il grimpe depuis le vide à travers le bord du pont, se pose sur le tablier et ronge : fissures (briques fendues, gouttes de lave), segment effondré en ~15 s, puis il saute sur le segment suivant (vers l'îlot d'abord)
- [ ] Plus aucun segment debout : il redescend dans le vide
- [ ] Mord les joueurs tout près pendant qu'il ronge
- [ ] Coup dans le dos ou depuis au-dessus (flèche plongeante) : dégâts ×2 (éclat cyan, son de cristal)
- [ ] Recul renforcé : un bon coup (Recul / sprint) le jette dans le vide → mort immédiate
- [ ] Lanterne de vigie : il brille, même sous le pont
- [ ] Début du Calme : les Brise-pont restants redescendent dans le vide ; après le Siège : plus aucun
- [ ] `/ws rift islets breaker` : un Brise-pont sous le premier pont construit (test)
- [ ] Éditeur : section Brise-pont (activé, nombre par pont, max, durée de rongement, PV)

## Sauvegarde et reprise (§ A)
- [ ] Quitter / relancer pendant le Siège : îlot reposé, pont acheté reposé avec ses segments effondrés, filons vivants reposés
- [ ] Reprise à la Contre-offensive : îlot et pont toujours là
- [ ] Île au coffre : venue / ouverte retrouvé à la reprise ; sauvegarde pendant l'événement sans ouverture → elle revient au Calme suivant
- [ ] Marchand : arrivé ou pas, stock du Calme, Éclat déjà acheté — retrouvés à la reprise
- [ ] Autel capturé + bonus : retrouvés à la reprise (faisceau allumé) ; capture en cours : repart de 0

## Éditeur
- [ ] Onglet Faille › Îlots : réglages enregistrés (îlots, distance, Calme, pont, île minière et son côté, repousse, part des gisements, gardes, améthyste, île-autel : côté, capture, reprise, raid, valeurs des bonus)
- [ ] Faille › Îlots : 5 sous-pages (Îlots et ponts, Île minière, Île-autel, Marchand, Île au coffre), chacune tient à l'écran
- [ ] Marchand : prix et stock des 12 articles modifiables, pris en compte en jeu
- [ ] Île au coffre : choix du Gardien (liste / œuf) pris en compte

## Codex
- [ ] Page « Îlots de la Faille » dans le sommaire (après Créatures de la Faille), texte complet fr / en
