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
