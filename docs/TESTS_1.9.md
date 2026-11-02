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
