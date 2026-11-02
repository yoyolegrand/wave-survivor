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
