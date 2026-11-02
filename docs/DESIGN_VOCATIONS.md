# Vocations — progression des métiers du mode Kingdom (1.9)

> Rédigée par Claude chat avec Yoann (brainstorming validé). Les **valeurs** sont une première base d'équilibrage : tout doit être dans un fichier de config (`vocations.json`) pour être ajusté sans toucher au code.
> Les maquettes (`docs/maquettes_vocations/`) sont une **ligne directrice** visuelle, pas un plan au pixel près.

---

## 1. Principe

- Chaque **rôle du mode Kingdom** (les 9 actuels : `KingdomRoles.Role`) a une barre de progression : la **Maîtrise** (niveaux 1 → 20).
- Jouer un rôle dans une partie Kingdom lui donne de l'**XP de Maîtrise** (victoire ou défaite, la victoire rapporte plus).
- Chaque niveau donne **1 point de talent** pour ce rôle → 20 points au maximum.
- Chaque rôle a un **arbre de talents** : **3 branches de 4 talents** (certains à plusieurs rangs, ~30 points pour tout prendre : il faut choisir) + **un talent ultime** au choix parmi 2.
- Les **points de Renaissance (PR)** servent à débloquer l'ultime et à réinitialiser l'arbre.
- Les **effets de base** des rôles sont **réduits** (≈ moitié) pour laisser de la marge à la progression ; l'arbre permet de retrouver puis de dépasser les valeurs actuelles.
- Les talents ne s'appliquent que **pendant une partie Kingdom**, au joueur qui **tient ce rôle**.
- Pour l'instant : **mode Kingdom uniquement** (la feuille de route dit « pas de classes dans les hordes classiques » ; à revoir plus tard si on étend au mode Autel).

## 2. Maîtrise (XP)

- **Courbe** : niveau n → n+1 coûte `100 + 50 × (n − 1)` XP (≈ 11 500 XP pour le niveau 20). Configurable.
- **Gains pendant la partie** (pour le rôle tenu) :
  - **10 XP par vague / assaut survécu** (base commune) ;
  - **actions propres au rôle** (récompenser le fait de *jouer* la classe), par exemple : Arcaniste — Catalyseur fermé 15, foyer purifié 10 ; Exploitant — gisement ou arbre récolté 10 ; Bâtisseur — construction / amélioration 5, réparation 2 ; Commandant — cor utilisé 5, soldat produit 1 ; Intendant — 1 XP par 20 pièces d'intérêts ; Alchimiste — fiole préparée 5, amélioration 15 ; Chasseur — contrat rempli 30, élite tuée 5 ; Augure — présage choisi 10 ; Récupérateur — débris ramassé 1, brèche refermée 10.
- **Fin de partie** : **victoire ×2** sur l'XP gagnée dans la partie, **défaite ×0,5**.
- Affichage : XP gagnée pendant la partie visible dans la fiche du rôle (Monolithe) ; message de fin de partie « +X XP de Maîtrise (Bâtisseur) », et annonce de **montée de niveau** dans le fil d'événements.
- **Persistance** : par joueur, **même portée que les données de Renaissance** (PR, Héritage).

## 3. Arbres : règles communes

- **Paliers** dans une branche : talent 1 libre, talent 2 = 2 points dans la branche, talent 3 = 5 points, talent 4 = 8 points.
- **Ultime** : 15 points dépensés dans l'arbre + **150 PR** pour le débloquer ; **un seul** des deux.
- **Réinitialiser** l'arbre : **50 PR** (rend tous les points ; l'ultime débloqué reste payé mais doit être re-choisi).
- Changer ses talents : **hors partie** uniquement (à l'autel de Renaissance).
- Chaque talent est défini par : nom, description, **valeur par rang**, nombre de rangs, palier → l'interface affiche « valeur par rang » **et le total appliqué** (ex. « Remise -5 %/rang — total -10 % »).

## 4. Les 9 arbres

> « Aujourd'hui » = effet actuel dans le code ; « Base réduite » = nouvel effet sans talents.
> La **carte du royaume** montre déjà gisements, arbres, Catalyseurs, Portes… : **aucun talent de vision / détection**.

### 🔮 Arcaniste
- **Aujourd'hui** : colonne + flèche vers les Catalyseurs ; 20 % de chance d'un 2e Catalyseur ; purification des foyers ×2.
- **Base réduite** : 10 % de 2e Catalyseur ; purification ×1,5.
- **Résonance** : (1) +7 % de chance de 2e Catalyseur ×3 rangs · (2) Catalyseur fermé → Essence · (3) bouclier pendant la fermeture · (4) fermer 2 Catalyseurs en même temps.
- **Purification** : (1) purification +15 % ×3 · (2) foyer purifié soigne les alliés proches · (3) foyer purifié laisse une zone sacrée qui ralentit les monstres · (4) foyer purifié rapporte des ressources au trésor.
- **Arcanes offensives** : (1) fermer un Catalyseur repousse les monstres proches · (2) dégâts magiques +5 % ×3 · (3) fermer un Catalyseur foudroie les élites proches · (4) orbe arcanique autour de lui.
- **Ultimes** : *Rupture* (fermer un Catalyseur referme aussi la brèche la plus proche) / *Nexus* (1×/assaut, Catalyseur allié qui affaiblit les monstres autour).

### ⛏ Exploitant (id `miner`, gisements **et** arbres)
- **Aujourd'hui** : colonne + flèche vers gisements et arbres ; plus fréquents, plus nombreux, plus gros ; Célérité II à côté ; 25 % de double butin.
- **Base réduite** : un peu plus fréquents (sans le bonus de taille) ; Célérité I ; 15 % de double butin.
- **Abondance** : (1) plus fréquents ×2 · (2) plus gros · (3) +1 gisement ou arbre par Calme · (4) restent plus longtemps avant de disparaître.
- **Rendement** : (1) +5 % de double butin ×3 (jusqu'à 30 %) · (2) minerais rares plus fréquents · (3) arbre abattu → un peu d'Essence · (4) récolte en zone (blocs voisins).
- **Endurance** : (1) Célérité II à côté · (2) résistance aux dégâts pendant la récolte · (3) monstres proches ralentis pendant la récolte · (4) ressources directement au trésor.
- **Ultimes** : *Filon-mère* (1er gisement de chaque Calme = filon rare et généreux) / *Cœur de la forêt* (abattre un arbre fantastique soigne et accélère l'équipe).

### 🔨 Bâtisseur
- **Aujourd'hui** : défenses, remparts, portes et pièges -20 % ; +10 % PV sur les constructions ; 1 charge de démolition / vague ; contrepartie : réparations plus chères.
- **Base réduite** : -10 % ; +5 % PV ; charge et contrepartie inchangées.
- **Architecte** : (1) remise -5 % ×3 (jusqu'à -25 %) · (2) améliorations -10 % · (3) murs et portes -15 % de pierre · (4) dernier niveau de chaque bâtiment offert au passage de niveau de la mairie.
- **Ingénieur** : (1) PV +5 % ×3 (jusqu'à +20 %) · (2) pièges +20 % de dégâts · (3) 2 charges de démolition / vague · (4) la charge blesse aussi les unités de siège autour de la Porte.
- **Restaurateur** : (1) réparations -15 % ×2 · (2) démontage rembourse 75 % · (3) bâtiment détruit rend 30 % de son coût · (4) réparation automatique légère pendant le Calme.
- **Ultimes** : *Chef-d'œuvre* (un bâtiment dépasse le niveau max d'un rang) / *Grand chantier* (+1 à la limite de chaque type de bâtiment).
- ⚠ Les constructions sont **déjà instantanées** : aucun talent de vitesse de construction.

### 🚩 Commandant
- **Aujourd'hui** : +1 soldat par baraquement ; Cor de ralliement (bannière 60 s).
- **Base réduite** : +1 soldat à partir du baraquement niveau 2 ; bannière 40 s.
- **Discipline** : (1) soldats +10 % PV ×3 · (2) soldats +10 % dégâts ×2 · (3) soldats tombés reviennent plus vite · (4) +1 soldat dès le baraquement niveau 1.
- **Ralliement** : (1) bannière +20 s ×2 (60 puis 80 s) · (2) cor recharge plus vite · (3) soldats ralliés plus rapides · (4) 2e bannière à la fois.
- **Champ de bataille** : (1) soldats proches de lui frappent plus fort · (2) ses soldats attaquent d'abord les unités de siège · (3) le cor donne Force I aux soldats ralliés · (4) ses soldats ne subissent plus de recul.
- **Ultimes** : *Garde d'élite* (capitaine invincible 30 s, 1×/assaut) / *Dernier carré* (Monolithe < 25 % → tous les soldats tombés reviennent).

### 💰 Intendant
- **Aujourd'hui** : +20 % de monnaie sur ses kills ; marchands -15 % pour lui ; intérêts +5 % du trésor à chaque fin d'Assaut (max 100).
- **Base réduite** : +10 % ; -8 % ; intérêts 3 % (max 60).
- **Trésorerie** : (1) +5 % de pièces ×3 · (2) intérêts +1 % ×2 (jusqu'à 5 %) · (3) plafond des intérêts +40 ×2 (jusqu'à 140) · (4) prime de fin d'assaut si le Monolithe est au-dessus de 75 %.
- **Commerce** : (1) marchands -4 % ×3 (jusqu'à -20 %) · (2) sa remise profite à l'équipe, à moitié · (3) +1 offre chez chaque marchand · (4) une offre rare garantie par Calme.
- **Intendance** : (1) ressources de départ supplémentaires · (2) conversion de ressources au Monolithe (bois / pierre / fer) · (3) 1×/Calme, Essence → pièces · (4) soldats tombés moins chers à remplacer.
- **Ultimes** : *Âge d'or* (intérêts et plafond doublés) / *Marché noir* (marchand spécial aux offres exceptionnelles à chaque Calme).

### ⚗ Alchimiste
- **Aujourd'hui** : 1 réactif par kill (3 par élite) ; 5 fioles de jet (givre, incendiaire, soin, orage, bastion) améliorables jusqu'au niveau 3 pour le royaume.
- **Base réduite** : améliorations jusqu'au niveau 2.
- **Remèdes** : (1) fiole de soin +15 % ×3 · (2) la fiole de soin retire les effets négatifs · (3) la fiole de bastion protège aussi les soldats · (4) soin et bastion au niveau 4.
- **Toxines** : (1) effets des fioles offensives +15 % ×3 · (2) la fiole de givre gèle les élites un instant · (3) la fiole incendiaire laisse du feu au sol · (4) givre, incendiaire et orage au niveau 4.
- **Distillation** : (1) +1 réactif par élite et chance de réactif double · (2) améliorations moins chères · (3) **Maître alchimiste** : améliorations jusqu'au niveau 3 · (4) +1 fiole préparée par Calme.
- **Ultimes** : *Panacée* (1×/assaut, soigne l'équipe et retire tous les effets négatifs) / *Réaction en chaîne* (une fiole offensive peut déclencher une 2e explosion).

### 🎯 Chasseur de primes
- **Aujourd'hui** : un contrat par Calme (élites, unités de siège ou monstres) payé en Monnaie + Essence ; contrepartie : 10 % d'élites (+60 % PV).
- **Base réduite** : contrats payés -30 % ; contrepartie inchangée.
- **Contrats** : (1) +15 % de paiement ×3 · (2) choix entre 2 contrats · (3) un contrat secondaire par Calme · (4) les contrats donnent aussi de l'XP de Maîtrise.
- **Traque** : (1) +10 % de dégâts sur les élites ×3 · (2) **Marque** : une cible subit plus de dégâts de toute l'équipe · (3) une élite tuée prolonge le contrat en cours · (4) vitesse après chaque élite tuée.
- **Trophées** : (1) les élites lâchent plus de butin · (2) chance de trophée (bonus temporaire) · (3) les Champions lâchent de l'Essence · (4) collection de trophées (bonus permanent pour la partie).
- **Ultimes** : *Tête mise à prix* (un boss rapporte une prime énorme) / *Grand veneur* (chaque élite tuée soigne l'équipe).

### 👁 Augure
- **Aujourd'hui** : pendant le Calme, choisit **1 présage parmi 3** (avantage + contrepartie).
- **Base réduite** : **1 présage parmi 2**.
- **Clairvoyance** : (1) +1 présage proposé ×2 (retour à 3, puis 4) · (2) relancer le tirage 1×/Calme · (3) **présage scellé** : garder un présage du tirage pour un assaut ultérieur · (4) voir le tirage du Calme suivant à l'avance.
- **Maîtrise** : (1) avantages des présages +10 % ×3 · (2) contreparties de **tous** les présages -10 % ×3 · (3) le présage dure encore 30 s au début du Calme suivant · (4) un présage choisi deux assauts de suite devient plus fort.
- **Convergence** : (1) **présage double** (deux présages actifs, avec leurs deux contreparties) · (2) avec deux présages, contreparties -25 % · (3) bonus d'harmonie pour les présages qui se combinent · (4) présage double dès le début de la partie.
- **Ultimes** : *Triple présage* (trois présages actifs) / *Présage vivant* (le présage choisi donne aussi un bonus personnel à chaque joueur pendant l'assaut).
- ⚠ Pas de « présage favorable » ni de vision de l'assaut : les présages ont tous un avantage et une contrepartie.

### ⚙ Récupérateur
- **Aujourd'hui** : unités de siège abattues et brèches refermées lâchent bois, fer, pierre (lui seul ramasse) ; 12 % de débris sur les autres monstres ; contrepartie : plus d'unités de siège.
- **Base réduite** : 8 % de débris ; les unités de siège lâchent un peu moins ; contrepartie inchangée.
- **Ferraille** : (1) +4 % de débris ×3 (jusqu'à 20 %) · (2) débris attirés vers lui · (3) brèches refermées lâchent plus · (4) chance de débris rare (Essence).
- **Recyclage** : (1) recycler les objets inutiles en ressources au Monolithe · (2) recycler un équipement → Essence · (3) une caisse de ferraille par Calme · (4) les débris ramassés réparent un peu le bâtiment le plus proche.
- **Pillage** : (1) les unités de siège lâchent plus · (2) les Champions lâchent plus · (3) chance de récupérer un bélier ennemi qui se bat pour vous un moment · (4) un coffre de pillard après chaque assaut réussi.
- **Ultimes** : *Arsenal de siège* (chaque unité de siège tuée a une chance de rejoindre vos rangs) / *Trésor de guerre* (chaque assaut réussi double les débris de l'assaut suivant).

## 5. Écrans (ligne directrice : `docs/maquettes_vocations/`)

1. **Autel de Renaissance → nouvel onglet « Vocations »** (5e onglet, même panneau que `RenaissanceScreen`, 260 de large) : grille des métiers (icône = **l'objet du rôle** déjà utilisé à la sélection : cristal de l'End, pioche en fer, briques, épée en fer, lingot d'or, alambic, arbalète, œil de l'Ender, chaîne), Maîtrise x/20, barre d'XP, points disponibles en doré. **Défilement à la molette + barre de défilement** (pour les futurs rôles). Clic → arbre du métier.
2. **Arbre d'un métier** (style des nœuds de l'Héritage : vert acquis, doré disponible / en cours, gris verrouillé avec les points requis) : en-tête (icône, Maîtrise, XP, points à dépenser / dépensés, effet de base), 3 colonnes de 4 talents reliés, chaque talent sur 2 lignes (**nom + total appliqué** ; **valeur par rang** + carrés de rang), ultimes en bas, boutons Retour et Réinitialiser (PR). Clic sur un talent disponible = +1 rang.
3. **Monolithe → onglet Rôles → fiche du rôle** : description avec les **valeurs réduites**, nouvelle section **Maîtrise** (niveau, barre, XP gagnée dans la partie), aperçu des talents actifs (3 colonnes, rangs et totaux), état de l'ultime, bouton **« Voir l'arbre complet » en lecture seule**. Dans la liste des rôles, afficher aussi « Maîtrise x » de chaque joueur.

## 6. À faire (ordre conseillé)
1. Données : `vocations.json` (arbres, talents, valeurs par rang, paliers, coûts, courbe d'XP, gains d'XP) + sauvegarde par joueur (Maîtrise, points, talents, ultime).
2. Gains d'XP pendant les parties Kingdom + fin de partie (×2 / ×0,5) + annonces.
3. Réduction des effets de base des 9 rôles + application des talents (brancher chaque talent là où l'effet du rôle se joue : `KingdomRoleEffects`, `KingdomRoleExtras`, `KingdomManager`, `KingdomAlchemy`, `KingdomOmens`…).
4. Écrans : onglet Vocations, arbre, section Maîtrise du Monolithe.
5. Textes FR / EN, page du Codex « Vocations », CHANGELOG.
