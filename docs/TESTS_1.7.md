# Tests 1.7 — suivi

Cocher `[x]` quand c'est validé en jeu. Une ligne en échec : la laisser décochée et noter le souci à côté.
Déjà validés avant ce fichier : mode sans fin, écran de statistiques, 21 succès de base, ajouts de Claude Chat (à rejouer une fois dans la 1.7 fusionnée, voir plus bas).

## Invasions de village
- [ ] Les envahisseurs chassent les villageois, même cachés derrière des portes (pas d'attente à la cloche)
- [ ] Portes : les envahisseurs les ouvrent / les brisent selon les réglages
- [ ] Roulette du Mauvais présage : raid vanilla avec les ajouts
- [ ] Roulette du Mauvais présage : invasion préparée
- [ ] Poids vanilla à 0 : le Mauvais présage est différé, aucune raid vanilla pure
- [ ] Cloche + barre de boss (villageois, vagues)
- [ ] Délai de préparation entre les vagues
- [ ] Composition mixte vanilla/mod (PV et dégâts du multiplicateur appliqués)
- [ ] Victoire : Héros du village, butin, PR, statistiques
- [ ] Défaite : conséquence configurée appliquée
- [ ] Succès `invasion_1`, `invasion_10`, `invasion_flawless`
- [ ] Commande `/invasion`
- [ ] Les 3 invasions par défaut : Pillards, Razzia des Sables, Meute des Neiges
- [ ] Éditeur, mode « Invasion » : textes lisibles, boutons clairs, onglet Événements vide, template « invasion »
- [ ] Pas de horde normale pendant une invasion, ni d'invasion pendant une horde

## Horde des Sables ardents
- [ ] Débloquée après la Nécropole
- [ ] Tempête de sable : l'abri couvert protège, une source de chaleur n'arrête pas la tempête, particules de bloc visibles
- [ ] Séisme : secousse de caméra, fissures
- [ ] Relique Voile du Désert : immunité au séisme
- [ ] Relique Voile du Désert : effet utile, bonus de set à 4 pièces, emplacement Curios
- [ ] Boss : compétence Sandstorm
- [ ] Boss : compétence Quicksand
- [ ] Boss : compétence Dust Devil
- [ ] Boss : compétence Tremor
- [ ] Boss : compétence Earthquake
- [ ] (Plus tard) vraies entités de Claude Chat : scarabée, chacal, scorpion, djinn, golem de grès
- [ ] (Plus tard) icône définitive de la relique

## Correctifs du retour joueur
- [ ] Zombie de horde dans l'eau : reste un zombie, la vague se termine
- [ ] Husk, squelette, piglin, hoglin : pas de transformation
- [ ] Sorcières : plus de soin
- [ ] Bénédiction en solo : le jeu se met en pause pendant le choix
- [ ] Bénédiction en multi : monstres ignorent le joueur + invulnérable (30 s max)
- [ ] Bénédiction en multi : la protection tombe après le choix ou la déconnexion
- [ ] Bénédictions ×1 Facile/Normal, ×1,25 Difficile, ×1,5 Cauchemar, dans tous les modes
- [ ] Écran de choix et catalogue : valeurs affichées = valeurs réelles

## Jamais testé
- [ ] Multijoueur : statistiques
- [ ] Multijoueur : vies partagées
- [ ] Succès « Marathonien »
- [ ] Les 24 succès s'obtiennent et s'affichent
- [ ] Reprise d'une sauvegarde 1.6 en 1.7 : ajouts `invasions` et `sables_ardents` appliqués une seule fois, config existante intacte
- [ ] Ajouts de Claude Chat rejoués : Brasier d'offrande, Baraquement, coffres roulette

## Retours joueurs CurseForge (BLaoTea, 2026-10-18)
- [ ] Tuto Vanilla, vague 5 (Zombie puissant) : la charge (dash) ne fait des dégâts que si elle touche, plus de mort en un coup
- [ ] Anomalies / Terrain instable : plus aucune entre deux vagues (Calme, pause, coffres), celles en cours disparaissent
