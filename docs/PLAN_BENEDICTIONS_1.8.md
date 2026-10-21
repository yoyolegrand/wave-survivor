# Plan 1.8 — rework des bénédictions (brouillon à valider avec Yoann)

## État actuel (1.7)
- ~40 bénédictions codées en dur dans `horde/benediction/RogueUpgradeRegistry.java` : 10 catégories (Résistance, Longévité, Célérité,
  Chance, Vitesse d'attaque, Vitesse, Robustesse, Armure…) avec 1 à 4 raretés (Commun / Rare / Épique / Légendaire).
- Uniquement des modificateurs d'attributs (+ soin à l'application). Niveau cumulable par id (NBT joueur `wave_survivor_benedictions`).
- Tirage de 3 choix uniforme sur tous les ids : la rareté ne pèse pas, la Chance n'a aucun effet sur le tirage.
- Aucun éditeur : seulement le catalogue en lecture (`/ws benediction liste`) et un interrupteur « bénédictions » dans le Boss Rush.
- Le client lit le même registre Java.

## Demande de Yoann (2026-10-21)
1. Un éditeur en jeu qui reprend TOUT l'existant.
2. Affichage groupé par CATÉGORIE (ex. « Bénédiction PV ») : un seul élément qui contient ses niveaux de rareté ; flèches ◀ ▶ pour passer
   de Commun à Rare, Épique, Légendaire ; on édite chaque niveau ; chaque niveau peut être activé ou non (ex. Longévité : Épique et Légendaire
   seulement).
3. Plus tard : des bénédictions spéciales (liste à établir ensemble).
4. Chance : augmente la probabilité d'une meilleure rareté, ET un impact de gameplay en plus.
5. Pouvoir cibler les statistiques de Minecraft ET des autres mods (ex. Skill Tree).

## Faisabilité des statistiques d'autres mods
- Toute statistique enregistrée comme `Attribute` (registre `ForgeRegistries.ATTRIBUTES`) est atteignable : vanilla, Forge (portée de bloc / d'entité,
  vitesse de nage, hauteur de marche, gravité), Apothic Attributes (critique, vol de vie, esquive…), Iron's Spellbooks (puissance des sorts, mana…).
  L'éditeur proposera un sélecteur avec recherche alimenté EN DIRECT par ce registre (donc il suit les mods installés).
- Passive Skill Tree n'enregistre pas d'attributs (aucune clé `attribute.name` dans ses textes) : ses bonus passent par son propre système et ne
  sont pas atteignables par un modificateur d'attribut ; il s'appuie sur Apothic Attributes pour le reste. À revoir si Yoann cite des stats précises.

## Étapes
1. **Données** : catégories → niveaux (rareté, activé, modificateurs, soin) dans un fichier de base du mod (couches de config : ajouts, patchs,
   suppressions du joueur) ; les ids actuels (`BENEDICTION_DE_<CAT>_<RARETE>`) sont conservés pour les parties en cours ; synchronisation du
   catalogue vers les clients (aujourd'hui lu en dur côté client).
2. **Éditeur** : bouton dans la liste des hordes ; liste de catégories à gauche, détail à droite ; ◀ ▶ entre raretés + case « disponible » ;
   modificateurs (attribut via sélecteur à recherche, valeur, opération), soin ; créer / dupliquer / supprimer ; badge `[modifiée · N]` et ↺.
3. **Tirage par rareté** : poids par rareté réglables, la rareté monte avec les vagues, 3 choix de catégories distinctes ; la Chance déplace
   le tirage vers les hautes raretés.
4. **Spéciales + gameplay de la Chance** (liste à valider).

## Décisions de Yoann (2026-10-21, validées)
- **4 raretés** (Commun, Rare, Épique, Légendaire).
- **La Chance** (attribut `Luck`, rendements décroissants + plafonds, un interrupteur par usage, réglages dans un fichier à couches) agit sur :
  1. le tirage des bénédictions (poids des hautes raretés) ;
  2. le Brasier d'offrandes du Kingdom (récompenses tirées des items offerts) ;
  3. les coffres roulette (poids des lots de haute rareté) ;
  4. le butin supplémentaire des monstres (au plus un lot de plus par monstre, tiré dans leur propre table).
  **PAS l'autel de Renaissance** (trop fort, retiré).
- Une bénédiction revient plusieurs fois pour améliorer sa statistique (niveaux cumulables).
- **Pas de listes de bénédictions par horde.**

## Bénédictions spéciales retenues (code nécessaire ; les bénédictions de statistiques passent par l'éditeur)
- **Attaque** : *Exécution* (dégâts bonus contre les monstres sous un seuil de PV).
- **Défense** : *Bouclier de l'aube* (absorption rechargée au début de chaque vague).
- **Utilité** : *Aimant* (attire les objets et l'expérience au sol dans un rayon).
- **Équipe** : *Bannière* (alliés proches : bonus de dégâts) et *Lien de sang* (part des soins reçus partagée avec les alliés proches).
- **Pactes** (risque contre bénéfice) : *Verre brisé* (beaucoup plus de dégâts, moins de PV max), *Berserker* (dégâts et vitesse d'attaque qui montent
  quand les PV baissent), *Fardeau*.
- **Fardeau doit être un ensemble complet et UNIQUE**, pas « de l'armure en plus » (sinon il fait doublon avec Résistance). Rôle « tank » : vitesse
  réduite en échange de (a) armure et robustesse élevées, (b) insensibilité au recul, (c) **Bastion** : réduction de dégâts croissante tant qu'on
  reste immobile, (d) **Provocation** : les monstres proches ont tendance à te cibler (protège l'équipe), (e) projectiles reçus fortement réduits.
  Chaque niveau renforce l'ensemble ; le malus de vitesse augmente aussi.
- **Contrainte (Yoann) : la Provocation ne doit JAMAIS entrer en collision avec la protection du choix de bénédiction** (`BenedictionProtection` : en
  multijoueur, pendant son choix, un joueur est ignoré des monstres et invulnérable, 30 s au maximum). Règles : la protection a toujours la priorité
  (annulation de changement de cible au niveau HIGHEST, la Provocation passe en priorité basse) ; la Provocation ne cible jamais un joueur protégé,
  créatif ou spectateur ; elle ne s'applique qu'à intervalles réguliers et avec une probabilité (pas un forçage permanent) ; les boss ne sont pas
  provoqués par défaut ; la fin de la protection reprend la Provocation normalement.

## Relances, bannissements, choix de famille
- 2 relances et 1 bannissement au départ ; **+1 relance et +1 bannissement toutes les 5 vagues** (l'Héritage de Renaissance pourra en ajouter).
- *Relancer* : retire les 3 cartes et en tire 3 nouvelles. *Bannir* : retire une catégorie du tirage pour toute la partie du joueur.
- **Choix de la famille** (pour éviter d'être noyé par trop de bénédictions) : chaque catégorie a une famille (Attaque, Défense, Mobilité, Utilité, Équipe,
  Pacte, Statistiques) ; en relançant, le joueur peut choisir une famille (ou « au hasard ») pour que les 3 nouvelles cartes viennent de celle-ci.
  Proposition retenue pour la validation : pas de jeton supplémentaire, le choix est inclus dans la relance.

## Étapes (inchangées)
1. Données (catégories → niveaux, couches de config, synchronisation client, ids conservés).
2. Éditeur en jeu (liste de catégories, ◀ ▶ entre raretés, case « disponible », sélecteur d'attributs de tous les mods, familles).
3. Tirage par rareté (poids, Chance, 3 catégories distinctes), relances, bannissements, choix de famille.
4. Bénédictions spéciales + butin supplémentaire des monstres + Chance sur Brasier et coffres roulette.
