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
- [ ] Terrain : plat côté pont, monte vers le fond, mine décorative au fond (butte, galerie étayée, lanternes, rails, wagonnet, tonneaux), géode d'améthyste à gauche, filons de minerai sous l'île
- [ ] 8 filons au début du Siège : 4 fer, 2 émeraude, géode d'améthyste, cristaux d'Essence (sans compte à rebours)
- [ ] Pioche minimum : pierre pour fer / améthyste, fer pour émeraude / Essence
- [ ] Butin : fer brut, émeraudes (→ trésor au ramassage), éclats d'améthyste, Essence au trésor (cristaux) ; pierre + fer au trésor à chaque filon épuisé
- [ ] Début de chaque Calme du Siège : ~50 % des filons épuisés repoussent (75 % avec un Mineur), message
- [ ] Gardes au début du Siège et de chaque Calme (5 points), les anciens disparaissent
- [ ] Mineur : colonne de lumière / flèche sur les filons de l'îlot
- [ ] Île principale : moins de gisements (40 %), plus d'émeraude ni d'améthyste
- [ ] Monstres de la Faille : éclat d'améthyste de temps en temps (12 %)
- [ ] Après le Siège (Contre-offensive, Titan) : îlot toujours là, plus de repousse ni de gardes

## 3. Île-autel
- [ ] Île-autel visible à l'ouest (rayon 12) : dallage en damier, cercle violet lumineux, estrade dorée, cristal flottant sombre, 4 piliers sombres
- [ ] Si l'île minière et l'île-autel sont réglées sur le même côté : l'autel prend un autre côté libre
- [ ] Boutique : ligne « Pont vers l'île-autel » (4e ligne des Engins), le pont se construit
- [ ] Capture : rester dans le cercle → message de progression, les piliers s'allument un par un (~30 s seul, plus rapide à plusieurs)
- [ ] Un monstre dans le cercle fige la barre (message « Capture figée »)
- [ ] Capturé : cristal et piliers allumés, colonne lumineuse vers le ciel, son, message ; choix cliquable dans le chat [Rempart] [Arsenal] [Prospérité] (infobulle avec la valeur)
- [ ] Le choix marche pour un joueur non-op ; un seul choix par capture (2e clic : « déjà choisi ») ; clic droit sur l'autel sans bonus choisi → le choix revient
- [ ] Rempart : le Monolithe regagne 1 % de ses PV toutes les 10 s
- [ ] Arsenal : catapultes / balistes font plus mal (+20 %)
- [ ] Prospérité : plus de pierre / bois / fer au trésor, plus d'émeraudes et d'améthyste sur les monstres ; démolir un bâtiment ne rend pas plus
- [ ] Début de chaque Assaut du Siège : 6 monstres apparaissent au fond de l'île-autel et marchent vers le cercle (message)
- [ ] Seuls dans le cercle ~20 s : alerte « reprennent l'autel », piliers qui s'éteignent, puis autel repris (bonus perdu, faisceau éteint)
- [ ] Revenir dans le cercle pendant la reprise : l'autel remonte à 100 %
- [ ] Recapturer : nouveau choix de bonus
- [ ] Après le Siège : plus de raid, bonus gardé jusqu'à la fin de la Faille
- [ ] `/ws rift islets capture` : capture immédiate (test) ; `/ws rift islets` affiche l'état de l'autel

## Sauvegarde et reprise (§ A)
- [ ] Quitter / relancer pendant le Siège : îlot reposé, pont acheté reposé avec ses segments effondrés, filons vivants reposés
- [ ] Reprise à la Contre-offensive : îlot et pont toujours là
- [ ] Autel capturé + bonus : retrouvés à la reprise (faisceau allumé) ; capture en cours : repart de 0

## Éditeur
- [ ] Onglet Faille › Îlots : réglages enregistrés (îlots, distance, Calme, pont, île minière et son côté, repousse, part des gisements, gardes, améthyste, île-autel : côté, capture, reprise, raid, valeurs des bonus)
- [ ] La page Îlots tient à l'écran (sinon le dire : je la découperai)
