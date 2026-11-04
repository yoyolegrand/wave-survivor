# Tests 1.9 — Vocations

## Étape 1 — Données et sauvegarde (commandes de test, opérateurs)
- [ ] `/ws vocation info` : liste les 9 rôles (Maîtrise 0/20, 0 point) ; `/ws vocation info builder` détaille les 3 branches et les 2 ultimes.
- [ ] `/ws vocation xp builder 100` : niveau 1 ; `xp builder 11500` au total : niveau 20 (plafond, pas plus).
- [ ] `/ws vocation buy builder discount` : refusé sans point, accepté avec un point ; 3 rangs max.
- [ ] Paliers : `upgrade_discount` refusé tant que la branche n'a pas 2 points, `wall_stone` à 5, `free_last_level` à 8.
- [ ] `/ws vocation ultimate builder masterpiece` : refusé sous 15 points dépensés ; à 15 points, prend 150 PR (comptent pour la Renaissance) ; un second ultime refusé.
- [ ] `/ws vocation reset builder` : 50 PR, rend les points, l'ultime est à re-choisir **sans** repayer les 150 PR.
- [ ] Refusé pendant une partie (horde ou Kingdom).
- [ ] Mort, déconnexion/reconnexion, redémarrage du serveur, Renaissance : XP, talents et ultime conservés.
- [ ] `config/wavesurvivor/vocations.json` (ex. `{"maxLevel": 10}`) puis `/ws reload` : la valeur est prise en compte.

## Étape 2 — XP de Maîtrise gagnée en partie Kingdom
- [ ] Pendant une partie Kingdom avec un rôle, rien n'est versé tout de suite (l'XP est en réserve) ; à la fin, message « +X XP de Maîtrise (Rôle) ».
- [ ] Victoire : XP ×2 (message « victoire ×2.0 »). Défaite (vies perdues) : XP ×0,5. `/ws stop` : aucune XP versée.
- [ ] Chaque Assaut survécu : +10 XP de base pour le rôle tenu (Calme n°1 exclu, on compte à partir du 1er Assaut survécu).
- [ ] Actions par rôle (valeurs dans `vocations.json`, `xp.actions`) :
  - Arcaniste : Catalyseur fermé (15), foyer purifié (10) ; Mineur : gisement détruit (10) ;
  - Bâtisseur : défense achetée (5), amélioration niv. 2 (5), réparation (2) ; Commandant : cor utilisé (5), soldat produit (1) ;
  - Intendant : 1 XP pour 20 pièces gagnées ; Alchimiste : fiole fabriquée (5), améliorée (15) ;
  - Chasseur : contrat réussi (30), élite abattue (5) ; Augure : présage appliqué à l'Assaut (10) ;
  - Récupérateur : débris ramassé (1), brèche du Calme refermée (10).
- [ ] Niveau gagné : ligne dans le fil d'événements (« Maîtrise … niveau N/20 ») + message des points à dépenser.
- [ ] Changer de rôle en cours de partie : l'XP reste attribuée au rôle tenu au moment de l'action.
- [ ] Un joueur déconnecté à la fin ne reçoit pas son XP (limite connue).
- [ ] `/ws vocation info <rôle>` confirme le niveau et l'XP après la partie.

## Étape 3a — effets de base réduits + talents chiffrés
Les talents ne comptent que pendant une partie Kingdom, pour le joueur qui tient le rôle. Pour tester vite : `/ws vocation xp <rôle> 11500` (niveau 20) puis `/ws vocation buy <rôle> <talent>` hors partie ; les rangs/valeurs sont dans `vocations.json` (≈ 21 à 27 points par arbre).
- [ ] **Arcaniste** — 2e Catalyseur : 10 % de base (+7 %/rang) ; purification ×1,5 (+15 %/rang `purify_speed`) ; `essence_on_close` : Essence à chaque Catalyseur fermé ; `purify_loot` : Bois + Pierre au trésor par foyer purifié.
- [ ] **Exploitant** — gisements/arbres : plus de +1 gratuit ni de taille automatique ; Célérité I (II avec `haste`) ; double butin 15 % (+5 %/rang) ; `frequency` : chance d'un gisement en plus ; `size` : chance de taille supérieure ; `extra_deposit` : +1 gisement ; `lifetime` : durée.
- [ ] **Bâtisseur** — remise 10 % (+5 %/rang `discount`) ; PV +5 % (+5 %/rang `hp`) ; réparations −X % (`repair_discount`) ; pièges à lames/explosifs +X % de dégâts ; charges de démolition 1/vague (+1 avec le talent).
- [ ] **Commandant** — +1 soldat seulement dès le baraquement niv. 2 (niv. 1 avec `early_soldier`) ; bannière 40 s (+15 s/rang) ; recharge du cor −10 %/rang ; soldats +PV / +dégâts ; renaissance plus rapide (`fast_respawn`).
- [ ] **Intendant** — +10 % de pièces sur ses kills (+5 %/rang) ; marchands −8 % (−4 %/rang) et `team_share` : l'équipe a une partie de la remise ; intérêts 3 % max 60 (+1 %/rang, +40/rang de plafond).
- [ ] **Alchimiste** — fioles jusqu'au niveau 2 (3 avec `master_alchemist`) ; l'écran du Monolithe affiche bien « max » et le bon prix (`cheaper_upgrades`) ; `reagent_elite` : +1 réactif/élite ; fioles de soin / offensives plus fortes.
- [ ] **Chasseur de primes** — contrat payé −30 % de base (+15 %/rang) ; +10 %/rang de dégâts sur les élites.
- [ ] **Augure** — 2 présages proposés (3 avec `extra_omen`).
- [ ] **Récupérateur** — débris 8 % (+4 %/rang) ; unités de siège lâchent ×0,8 (+10 %/rang `siege_loot`).
- [ ] Textes de description des rôles mis à jour (valeurs de base).

## Étape 3b — talents « interrupteurs » simples
Même méthode de test qu'en 3a (`/ws vocation xp <rôle> 11500`, `/ws vocation buy <rôle> <talent>` hors partie).
- [ ] **Arcaniste** : `shield_on_close` (absorption 10 s à la fermeture d'un Catalyseur) ; `close_knockback` (monstres à ≤ 9 blocs repoussés) ; `close_lightning` (élites à ≤ 14 blocs foudroyées) ; `magic_damage` (+5 %/rang sur dégâts magie / magie indirecte / foudre) ; `purify_heal` (alliés à ≤ 14 blocs soignés quand un foyer est purifié) ; `purify_loot` (Bois + Pierre).
- [ ] **Exploitant** : `tree_essence` (Essence quand il abat un arbre) ; `resist` (dégâts subis réduits à moins de 6 blocs d'un gisement/arbre).
- [ ] **Bâtisseur** : `upgrade_discount` (niveau 2 moins cher, visible sur la fiche de la défense) ; `dismantle_refund` (démontage rembourse jusqu'à 75 %) ; `auto_repair` (+2 % de PV toutes les 5 s pendant le Calme).
- [ ] **Commandant** : `no_knockback` (soldats sans recul) ; `near_damage` (Cri de guerre : +20 % de base, + le talent).
- [ ] **Intendant** : `wave_bonus` (pièces au début du Calme si le Monolithe est à plus de 75 % de PV).
- [ ] **Alchimiste** : `cleanse` (la fiole de soin retire les effets négatifs).
- [ ] **Chasseur** : `elite_speed` (Vitesse II 5 s après une élite abattue).
- [ ] **Récupérateur** : `pull` (débris attirés), `breach_loot` (brèches refermées plus généreuses), `debris_repair` (chaque débris ramassé soigne les bâtiments à ≤ 12 blocs).

## Étape 3c — lot A (17 talents prévus, 14 faits)
- [ ] **Exploitant** : `slow_aura` (monstres à ≤ 8 blocs ralentis près d'un gisement) ; `direct_treasury` (+50 % de trésor sur ce qu'il détruit).
- [ ] **Bâtisseur** : `charge_hurts_siege` (la charge blesse les unités de siège à ≤ 14 blocs de la Porte).
- [ ] **Commandant** : `horn_strength` (Force I) et `rally_speed` (Vitesse I) pour les soldats quand une bannière est active ; `double_banner` (2 bannières, les soldats vont à la plus proche).
- [ ] **Alchimiste** : `frost_elite` (élite figée 3 s) ; `fire_ground` (zone de feu 6 s +2 s/niveau) ; `extra_flask` (fiole offerte au début de chaque Calme).
- [ ] **Chasseur** : `elite_loot` (butin des élites qu'il abat) ; `champion_essence` (Essence sur un Champion de Porte).
- [ ] **Récupérateur** : `rare_debris` (débris d'Essence) ; `scrap_crate` (caisse de ferraille à chaque Calme).
- [ ] **Intendant** : `start_resources` (Bois/Pierre/Fer à la prise du rôle, une fois par partie).
- [ ] **Arcaniste** : `purify_zone` (zone sacrée de 40 s qui ralentit les monstres, à l'endroit du foyer purifié).
- [ ] Non faits (à décider) : `choice_of_two`, `contract_extend` (Chasseur), `cheaper_soldiers`, `essence_to_coins` (Intendant).

## Étape 4 — écrans
Pour avoir des points : `/ws vocation xp <rôle> 11500` (niveau 20). Les talents « Bientôt » (non branchés) ne peuvent pas être achetés.
- [ ] **Autel de Renaissance** : 5 onglets (Sacrifice, Boutique, Forge, Héritage, Vocations) bien alignés ; le 5e ouvre la grille des 9 métiers (icône du rôle, Maîtrise x / 20, barre d'XP, « ★ n points » en doré). Les 4 autres onglets de cette grille ramènent à l'écran habituel.
- [ ] Grille : survol = cadre doré ; clic = arbre du métier ; la molette fait défiler (et la barre de défilement apparaît) seulement s'il y a plus de 9 métiers (rien à faire défiler pour l'instant).
- [ ] **Arbre** : en-tête (icône, Maîtrise, barre, points à dépenser / dépensés), ligne « Base : … » avec les valeurs réduites, 3 colonnes de 4 talents reliés. Vert = tous les rangs pris, doré = en cours ou achetable, gris = verrouillé (« N pts » requis), sombre « Bientôt » = pas encore actif.
- [ ] Chaque talent affiche le total appliqué (en haut à droite), la valeur par rang (ligne 2) et les carrés de rang ; l'info-bulle détaille nom, description, valeur par rang, total et condition.
- [ ] Clic sur un talent achetable : +1 rang (le serveur refuse sinon, avec un message). Paliers 0 / 2 / 5 / 8 points par branche respectés.
- [ ] **Ultimes** : grisés sous 15 points ; refusés tant qu'ils sont « Bientôt ».
- [ ] **Réinitialiser** : 1er clic « Confirmer ? », 2e clic = 50 PR, points rendus ; grisé pendant une partie (message rouge).
- [ ] **Monolithe › Rôles** : liste = « Maîtrise N » à côté de chaque rôle ; fiche = section Maîtrise en bas (niveau, barre, « +X XP cette partie », points libres, ultime) + bouton « Voir l'arbre complet » (lecture seule : aucun achat possible, Retour ramène au Monolithe).
- [ ] Multijoueur / serveur dédié : l'écran se remplit à la connexion (configuration envoyée par le serveur), pas de crash si le client n'a pas encore reçu les données (« Chargement… »).

## Étape 5 — Codex et CHANGELOG
- [ ] **Codex** (bouton 📖) : nouvelle page « Vocations » juste après « Rôles » (★), texte lisible et défilant en FR et en EN ; la page « Renaissance » parle de 5 onglets ; la page « Présages » dit 2 présages (3 avec un talent).
- [ ] Les textes de description des rôles (Monolithe, Codex) reflètent les valeurs réduites.
- [ ] `CHANGELOG.md` : section `## 1.9.0 — Role Vocations` (à relire / ajuster avant la publication ; titre de version à vérifier avec `.github/scripts/check_title.py`).

## Talents restants — lot B1 (17 talents + 13 ultimes)
Talents : `double_close`, `arcane_orb` (Arcaniste) · `rare_ores` (Exploitant) · `wall_stone`, `free_last_level` (Bâtisseur : spécialisation de niveau 3 offerte) · `siege_priority` (Commandant) · `supplies` (Intendant, remplace « soldats moins chers » : réparations −X % pour toute l'équipe) · `bastion_soldiers`, `support_level4`, `offense_level4` (Alchimiste : fioles jusqu'au niveau 4) · `contract_xp`, `contract_extend` (une élite compte triple), `mark` (cible marquée 10 s, +25 % de dégâts de l'équipe), `trophy_chance` (Force I 60 s), `collection` (+1 %/élite, 15 % max) (Chasseur) · `champion_loot`, `raider_chest` (Récupérateur).
Ultimes : Rupture (+15 % de PV max de la Porte vulnérables à chaque Catalyseur fermé), Nexus (zone d'affaiblissement 30 s au Monolithe à chaque Assaut) · Filon-mère, Cœur de la forêt · Chef-d'œuvre (+25 % PV constructions) · Garde d'élite (capitaine invincible), Dernier carré (Monolithe < 25 % PV) · Âge d'or · Panacée, Réaction en chaîne · Tête mise à prix, Grand veneur · Trésor de guerre.
- [ ] Chaque talent/ultime ci-dessus s'achète (plus de « Bientôt ») et a son effet décrit.
- [ ] Alchimiste : l'écran du Monolithe montre « max » au bon niveau par fiole (4 avec le talent de la catégorie), prix du niveau 4 = 48.
- [ ] Ultimes : achat possible à 15 points + 150 PR (une fois), puis re-choix gratuit après réinitialisation.
- Encore « Bientôt » : Exploitant `area_harvest` · Bâtisseur `ruin_refund`, ultime Grand chantier · Intendant `extra_offer`, `rare_offer`, `convert`, `essence_to_coins`, ultime Marché noir · Chasseur `choice_of_two`, `secondary_contract` · Récupérateur `recycle_items`, `recycle_gear`, `tame_ram`, ultime Arsenal de siège · Augure : 11 talents + 2 ultimes.

## Augure — refonte des présages (11 talents + 2 ultimes)
Les présages passent par `boon()` / `curse()` : plusieurs présages actifs, valeurs modulables, scellé, relance, aperçu.
- [ ] **Sans talent** : comportement inchangé (2 présages proposés, 1 seul choisi, effets identiques à avant).
- [ ] `omen_boon` / `omen_curse` : avantages +10 %/rang, contreparties −10 %/rang sur TOUS les présages (effectifs −25 %/+20 %/+30 %/+40 %, butin +50 %, Essence de Frénésie, Pluie de fer 15 %, élites de la Marée basse, Chasse royale +3 Essence, Lune de sang ×2, coût de l'Offrande).
- [ ] `reroll` (bouton « Relancer », 1 fois par Calme) ; `sealed_omen` (« Sceller » garde le dernier présage choisi, « Libérer » le remet parmi les choisis) ; `preview` (message « Aperçu » au début de l'Assaut, le Calme suivant propose bien ce tirage).
- [ ] `carry_over` : le présage reste actif 30 s après le début du Calme ; `stacking` : même présage deux Assauts de suite → avantages ×1,25 (✦ dans l'annonce).
- [ ] `double_omen` : 2 présages choisis (recliquer un choisi le retire) et actifs ensemble ; `double_curse_cut` : contreparties −25 % avec 2 présages ; `harmony` : avantages +15 % avec 2 présages ; `double_from_start` : 2 choix dès le 1er Calme.
- [ ] Ultimes : Triple présage (3 choix) ; Présage vivant (Régénération I aux joueurs pendant l'Assaut tant qu'un présage est actif).
- [ ] Écran du Monolithe (Augure) : boutons Relancer/Sceller/Libérer visibles seulement avec les talents ; pas de chevauchement avec le bloc Maîtrise (à vérifier avec 3 présages proposés).

## Derniers talents (plus aucun « Bientôt »)
- [ ] **Exploitant** `area_harvest` : coups sur gisements/arbres ×1,5 (interprétation de la « récolte de zone »).
- [ ] **Bâtisseur** `ruin_refund` : un bâtiment DÉTRUIT (pas démonté) rend sa part du coût au trésor ; ultime Grand chantier : +1 à la limite de tours d'archers.
- [ ] **Intendant** `extra_offer` (12 pièces → 2 pommes dorées), `rare_offer` (48 → Totem) chez chaque marchand (une seule fois par marchand) ; `convert` (3 boutons dans le Monolithe : 20→10) ; `essence_to_coins` (2 Essence → 40 pièces, 1×/Calme) ; ultime Marché noir : villageois « Marché noir » près du Monolithe à chaque Calme, retiré au début de l'Assaut.
- [ ] **Chasseur** `choice_of_two` (bouton « Changer de contrat », 1×/Calme, seulement si le contrat n'a pas avancé) ; `secondary_contract` (2e contrat de monstres, payé au Calme).
- [ ] **Récupérateur** `recycle_items` (objets inutiles → Pierre/Bois), `recycle_gear` (équipement en main → Essence) ; `tame_ram` (bélier abattu → allié 60 s), ultime Arsenal de siège (toute unité de siège, 15 %).
- [ ] Boutons de talents du Monolithe visibles seulement avec le talent, sans chevaucher le bloc Maîtrise.

## Artisanat sacré et profane — étape 1 : nourriture
- [ ] Les 7 aliments + le Festin de grâce sont dans l'onglet créatif (icônes OK ; Pain béni = pain vanilla provisoire).
- [ ] Les 8 recettes fonctionnent à la table de fabrication (cf. docs/DESIGN_ARTISANAT.md §2) ; Vin noir avec une bouteille d'eau, Viande souillée avec n'importe quelle viande crue.
- [ ] Chaque aliment donne sa faim et ses effets (config : crafting_fragments.json) ; Hydromel et Vin noir rendent une fiole vide.
- [ ] Garde-fou : manger un 2e aliment spécial retire les effets du 1er (potions, dégâts, PV max, régénération).
- [ ] Pomme sanctifiée : retire 1 effet négatif. Viande souillée : Faim 30 s après le repas. Fruit du néant : Nausée 3 s.
- [ ] Vin noir : +20 % de dégâts, -2 cœurs max pendant 60 s puis retour à la normale. Cœur cru : +40 %, aucune régénération naturelle 120 s (soin de potion toujours permis).
- [ ] Festin de grâce : se pose sur un bloc plein, 6 parts (+8 faim, +4 cœurs), tout joueur peut manger, rendu en gâteau (provisoire), se récupère s'il est intact.
- [ ] Effets effacés à la mort et à la déconnexion. /ws reload recharge la config.

## Artisanat sacré et profane — étape 2 : blocs de décoration
- [ ] Marbre béni et Pierre profanée (+ escalier, dalle, muret, colonne, poli, sculpté) sont dans l'onglet créatif, 14 blocs, textures vanilla provisoires.
- [ ] Recettes : 8 calcite + 1 Éclat de grâce → 8 marbre béni ; 8 ardoise des abîmes + 1 Éclat de souillure → 8 pierre profanée.
- [ ] Variantes à la table de fabrication (escalier 6→4, dalle 3→6, muret 6→6, colonne 2→2, poli 4→4, sculpté = 2 dalles) et à la scie à pierre.
- [ ] Escaliers (tous les angles, haut/bas), dalles (haut/bas/double), murets (connexions), colonne (3 axes) s'affichent bien ; tous les blocs lâchent leur objet à la pioche ; dalle double = 2 objets.
- [ ] Lumière faible (niveau 4) sur tous les blocs. À faire plus tard : textures définitives (veines dorées / fissures roses lumineuses).

## Artisanat sacré et profane — étape 3 : onglet Artisanat + blocs utilitaires
- [ ] Le Sanctuaire a un 5e onglet « Artisanat » (icône : Cœur de grâce) ; thème doré/violet selon la fabrication choisie ; liste à gauche (molette si besoin), coût à droite (vert = assez, rouge = manque), bouton « Fabriquer » ; les ingrédients sont pris dans l'inventaire du joueur.
- [ ] Chaque fabrication consomme les bons ingrédients (config : shrineCrafts) et donne le bloc ; sans ingrédients : message et rien ne se passe.
- [ ] Lanterne de grâce : lumière 15 ; un mort-vivant / Corrompu à moins de 8 blocs inflige -20 % de dégâts.
- [ ] Fontaine sacrée : Régénération I aux joueurs à moins de 5 blocs, rien pendant un combat de boss.
- [ ] Bougie votive : clic droit allume/éteint ; allumée, plus d'apparition naturelle de monstres à moins de 12 blocs (les vagues de horde apparaissent toujours).
- [ ] Totem d'appât : clic droit → attire les monstres à moins de 16 blocs 30 s (sauf boss), recharge 60 s (message si en recharge).
- [ ] Obélisque profane : monstres à moins de 6 blocs -1 PV/s ; joueurs à moins de 6 blocs : Faim.
- [ ] Autel de sang : clic droit → -4 cœurs, +120 points d'expérience, recharge 30 s ; refusé si on a 4 cœurs ou moins.
- [ ] Les 6 blocs sont aussi dans l'onglet créatif ; textures vanilla provisoires ; se cassent et se récupèrent.

## Artisanat sacré et profane — étape 4 : équipement
- [ ] Les 6 objets sont dans l'onglet créatif et dans la liste de l'onglet Artisanat du Sanctuaire (12 entrées au total, molette) ; flèches par 8. Icônes provisoires vanilla : Flèche souillée, Totem de souillure.
- [ ] Lame de l'aube : dégâts d'une épée en diamant, +50 % contre morts-vivants et Corrompus, brille (reflet enchanté) la nuit.
- [ ] Faux de la souillure : balayage large (60 % des dégâts aux ennemis dans 3 blocs), vol de vie 5 %, un coup manqué en l'air = -1 cœur (pas de pénalité sur un bloc ni à 1 cœur de la mort).
- [ ] Forge du Sanctuaire : la Lame n'accepte que des fragments de grâce, la Faux que des fragments de souillure (message sinon) ; les améliorations s'appliquent bien et le thème de l'écran suit l'alignement.
- [ ] Flèche bénie : embrase et repousse un mort-vivant / Corrompu. Flèche souillée : Wither I 4 s + Lenteur I. Les deux se tirent à l'arc et à l'arbalète.
- [ ] Totem de grâce (en main) : remplace la mort (1 cœur, Régénération, Absorption, Résistance au feu) et soigne les alliés à moins de 6 blocs de 4 cœurs.
- [ ] Totem de souillure (en main) : remplace la mort, vie pleine, +50 % de dégâts 30 s, puis 4 cœurs max pendant 2 min (retour à la normale ensuite, aussi après mort ou déconnexion).

## Artisanat sacré et profane — étape 5 : textes, Codex, CHANGELOG
- [ ] Codex : page « Artisanat sacré et profane » (après Sanctuaire du Séraphin), texte FR et EN complet et lisible (défilement) ; la page du Sanctuaire mentionne l'onglet 5.
- [ ] Aucun texte brut / clé manquante dans les infobulles, messages et l'onglet Artisanat (FR et EN).

## Forge du Sanctuaire — attributs visibles sur l'arme
- [ ] Une arme avec Célérité (vitesse d'attaque) ou Force sacrée (dégâts) affiche maintenant « +X % Attack Speed / Attack Damage » dans son info-bulle (« When in Main Hand ») ; la vitesse réelle de frappe change bien (barre de recharge).
- [ ] Les malus (Rage aveugle : -vitesse d'attaque) s'affichent aussi ; pas de double application (comparer avec un objet sans amélioration).

## Vocations — nouvelle règle de palier
- [ ] Le 2e / 3e / 4e talent d'une colonne demande 2 / 5 / 8 points dépensés dans TOUT l'arbre du rôle (toutes branches) ET que les talents précédents de la colonne aient au moins 1 rang. Ex. Chasseur de primes : 6 points en Contrats + 4 en La chasse = 10 → « Contrats instructifs » (8) s'achète si les 3 premiers de la colonne sont pris.
- [ ] Infobulle d'un talent verrouillé : « Requiert N point(s) dépensés dans l'arbre du rôle » ou « Prends d'abord les talents précédents de cette colonne » ; le lien entre deux talents devient vert quand le suivant est débloqué.

## Artisanat — retours du 1er test (armes, festin)
- [ ] Onglet Artisanat : barre de défilement visible + « 1-6 / 12 (molette) » ; clic en haut/bas de la barre ou molette pour atteindre les 6 équipements (Lame, Flèches, Totems, Faux…).
- [ ] Lame de l'aube 8 de dégâts, Faux 9 ; Lame = plus grande en main (1re et 3e personne) ; Faux = plus grande ; dire si la lame de la Faux est toujours « retournée ».
- [ ] Faux : coup en l'air (même avec un bloc derrière) = -1 cœur ; casser un bloc ou toucher un monstre = aucune perte. En créatif la perte de vie ne s'applique pas.
- [ ] Lame : contre un zombie / cheval zombie, étincelles blanches (END_ROD) quand le bonus de +50 % s'applique ; la nuit, étincelles autour du porteur + reflet enchanté.
- [ ] Festin de grâce : chaque part donne aussi Régénération II 10 s et Absorption II 60 s (config feast.effects).

## Lame de l'aube — lumière et bonus (2e retour)
- [ ] La nuit, tenue en main : un bloc de lumière invisible (niveau 10) suit le porteur ; il disparaît quand on range la Lame, de jour, à la mort, à la déconnexion et à l'arrêt du serveur (vérifier qu'aucune lumière ne reste dans le monde).
- [ ] Frapper un zombie / squelette / cheval zombie avec la Lame : message « Dégâts bénis contre les morts-vivants : N » dans la barre d'action + étincelles ; N = dégâts × 1,5. Si le message n'apparaît pas, envoyer une capture avec le type de monstre.

## Armure du Séraphin
- [ ] Les 4 pièces sont dans l'onglet créatif (icônes de diamant provisoires) et dans l'onglet Artisanat du Sanctuaire (4 entrées de plus).
- [ ] Porter chaque pièce SEULE (casque, plastron, jambières, bottes) : rien ne flotte ni ne se décale, la visière est face à l'avant, les ailes dorsales dans le dos, le halo derrière la tête.
- [ ] Porter l'ensemble, de face, de dos et de profil ; en marchant / courant / accroupi : épaulières et genouillères suivent bras et jambes.
- [ ] Armure vanilla sur un emplacement + Séraphin sur un autre : les deux rendus cohabitent (et sur un zombie / joueur tiers).
- [ ] Valeurs : casque 3, plastron 8, jambières 6, bottes 3, robustesse 2,5 ; réparable avec un Fragment de grâce ; son d'équipement doré.
- [ ] Bonus d'ensemble : avec les 4 pièces, -15 % de dégâts d'un mort-vivant ou d'un Corrompu ; avec 3 pièces, rien.
- [ ] Forge du Sanctuaire : n'accepte que des fragments de grâce sur ces pièces.

## Artisanat — textures provisoires générées par code
- [ ] Marbre béni : blanc veiné d'or (base, poli, sculpté avec soleil, colonne) ; Pierre profanée : sombre avec fissures roses qui BRILLENT dans le noir sur les cubes (base, poli, sculpté, colonne) ; escaliers / dalles / murets sans lueur.
- [ ] Blocs utilitaires : lanterne et bougie dorées, fontaine (bassin + eau), totem d'appât (tête avec œil rose), obélisque (runes lumineuses), autel de sang (bassin rouge) ; collisions et ombres cohérentes avec la forme ; icônes d'inventaire correctes.
- [ ] Faux : un coup dans le vide / sur un bloc non cassé coûte 1 PV (magique, ignore l'armure) ; frapper un monstre ou casser un bloc ne coûte rien.
- [ ] Lame : bonus aussi sur les morts-vivants d'autres mods (tag wavesurvivor:undead, forge:undead, mots-clés de noms dans la config).
- Textures PixelLab à venir : voir docs/BRIEF_TEXTURES_ARTISANAT.md.

## Ruine « La Chapelle »
- [ ] `/ws arena place chapelle [0|90|180|270]` : la chapelle se pose, tourne bien (escaliers, portes, cloche), cimetière et abords raccordés
- [ ] Lanternes de grâce : 3 suspendues dans la nef, 4 posées sur piliers sur le chemin ; bougies votives allumées sur l'autel
- [ ] Un monde neuf (ou une zone non explorée) : `/locate structure wavesurvivor:chapelle` trouve une chapelle (plaines, prairies, forêts), rare, sur terrain plat
- [ ] Coffre de l'autel : 6–10 Éclats, 1–2 Fragments de grâce, parfois un Cœur ; pillable une seule fois
- [ ] Entrer dans la nef ou le cimetière d'une chapelle **générée** : la cloche sonne, 4–6 gardiens (zombies/squelettes casqués) apparaissent ; une seule fois par chapelle (pas pour une chapelle posée à la commande)
- [ ] Lanterne : se pose au plafond quand on vise le dessous d'un bloc, casse si le support disparaît

## Carnet du Survivant
- [ ] Nouveau monde : le carnet est remis à la première connexion (message dans le chat) ; monde existant : remis à la prochaine connexion, sans récompenses pour ce qui est déjà accompli
- [ ] Clic droit sur le carnet et `/ws carnet` ouvrent l'écran ; `/ws carnet` en redonne un si on l'a perdu ; craft : livre + papier
- [ ] Onglet Démarrage : 5 pages, le bouton « Voir dans le Codex » ouvre la bonne page (autel/start, bénédictions, Renaissance, Sanctuaire, Kingdom)
- [ ] Onglet Objectifs : lier une horde, survivre à 5 vagues, choisir une bénédiction, dépenser des PR, équiper une relique (patienter ~5 s), finir une horde, faire une offrande, jouer une partie Kingdom → case cochée, récompense reçue, annonce dans le fil d'événements, progression 0/5 → 5/5
- [ ] Onglet Ressources : 8 fiches (icône, où le gagner, à quoi ça sert), texte défilant si long
- [ ] Onglet Codex : taper « relique », « Kingdom », « Séraphin »… → résultats cliquables ouvrant la page du Codex ; accents et majuscules ignorés
- [ ] `/ws reload` après modification de `config/wavesurvivor/carnet.json` (objectifs, récompenses, `autoGive: false`)

