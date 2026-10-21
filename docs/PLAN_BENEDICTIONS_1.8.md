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

## Décisions de Yoann (2026-10-21, 2e tour)
- **4 raretés** (Commun, Rare, Épique, Légendaire) : on garde les 4 actuelles (« Uncommon » = Peu commun, oublié, abandonné).
- **La Chance** (attribut `Luck`) agit sur : le tirage des bénédictions, l'autel de Renaissance (sacrifice), le Brasier d'offrandes du Kingdom
  (les récompenses tirées des items offerts), les coffres roulette, et, côté gameplay, du **butin supplémentaire sur les monstres**.
- Une bénédiction peut **revenir plusieurs fois** pour améliorer sa statistique (niveaux cumulables, déjà le cas).
- **Relances et bannissements : oui.** **Pas de listes de bénédictions par horde.**
- Bénédictions spéciales : liste à valider (voir la proposition dans la conversation).

## À trancher
- Formule de la Chance (rendements décroissants, plafonds) et réglages : voir la proposition.
- Liste finale des bénédictions spéciales.
- Nombre de relances et de bannissements par partie (et comment en gagner).
