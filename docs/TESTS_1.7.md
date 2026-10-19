# Tests 1.7 — suivi

Cocher `[x]` quand c'est validé en jeu. Une ligne en échec : la laisser décochée et noter le souci à côté.
Déjà validés avant ce fichier : mode sans fin, écran de statistiques, 21 succès de base, ajouts de Claude Chat (à rejouer une fois dans la 1.7 fusionnée, voir plus bas).

## Invasions de village
- [x] Les envahisseurs chassent les villageois, même cachés derrière des portes (pas d'attente à la cloche)
- [x] Portes : les envahisseurs les ouvrent / les brisent selon les réglages
- [x] Roulette du Mauvais présage : raid vanilla avec les ajouts
- [x] Roulette du Mauvais présage : invasion préparée (Razzia des Sables déclenchée)
- [x] Poids vanilla à 0 : le Mauvais présage est différé, aucune raid vanilla pure
- [x] Cloche + barre de boss (villageois, vagues)
- [x] Délai de préparation entre les vagues
- [x] Composition mixte vanilla/mod (PV et dégâts du multiplicateur appliqués)
- [x] Victoire : Héros du village, butin, PR, statistiques
- [x] Défaite : conséquence configurée appliquée
- [x] Succès `invasion_1`, `invasion_10`, `invasion_flawless`
- [x] Commande `/invasion`
- [ ] `/ws invasion skip` : en préparation (la 1re vague démarre), en combat (envahisseurs retirés, pause puis vague suivante), en pause (vague suivante) ; sur la dernière vague : victoire avec récompenses
- [x] Les 3 invasions par défaut : Pillards, Razzia des Sables, Meute des Neiges
- [x] Éditeur, mode « Invasion » : textes lisibles, boutons clairs, onglet Événements vide, template « invasion »
- [x] Pas de horde normale pendant une invasion, ni d'invasion pendant une horde

- [ ] Razzia des Sables : le Djinn (vague 3+) et le Colosse de grès (dernière vague) apparaissent avec leurs modèles

## Horde des Sables ardents
- [x] Débloquée après la Nécropole
- [ ] Tempête de sable : l'abri couvert protège, une source de chaleur n'arrête pas la tempête, particules de bloc visibles
- [ ] Séisme : secousse de caméra, fissures
- [ ] Relique Voile du Désert : immunité au séisme
- [ ] Relique Voile du Désert : effet utile, bonus de set à 4 pièces, emplacement Curios
- [ ] Boss : compétence Sandstorm
- [ ] Boss : compétence Quicksand
- [ ] Boss : compétence Dust Devil
- [ ] Boss : compétence Tremor
- [ ] Boss : compétence Earthquake
- [x] Djinn : modèle 3D sur mesure (turban, queue de fumée, yeux lumineux), sorts (orbes de sable, disparition), animation bras levés
- [x] Djinn : les Vex invoqués sont des Esprits du Djinn (modèle propre)
- [x] Icône définitive du Voile du Désert (objet, inventaire, Curios)
- [x] Scarabée : modèle sur mesure (scarabée sacré lapis et or), ne s'enfouit pas
- [x] Scorpion : queue articulée, coup de dard animé, grimpe, poison ; Sekhmet (vague 7) a le même modèle
- [x] Chacal maudit : yeux rouges, attaque en meute ; Chacal Alpha a le même modèle
- [x] Colosse de grès : voûté, cœur-soleil, rochers en orbite, hostile ; Rê-Atoum (vague 10) a le même modèle
- [x] Crocs du Djinn (invoqués par le Djinn) : modèle turquoise et or
- [ ] Garde royal momifié : modèle sur mesure, coup de Wither ; Anubis (vague 4) a le même modèle
- [x] Les 6 œufs d'apparition du désert (Djinn, Esprit, Scarabée, Scorpion, Chacal, Colosse) donnent la bonne créature, avec le bon nom FR/EN

## Correctifs du retour joueur
- [x] Zombie de horde dans l'eau : reste un zombie (confirmé ; fin de vague non précisée)
- [x] Husk, squelette, piglin, hoglin : pas de transformation
- [x] Sorcières : plus de soin
- [x] Bénédiction en solo : le jeu se met en pause pendant le choix
- [x] Bénédiction en multi : monstres ignorent le joueur + invulnérable (30 s max)
- [x] Bénédiction en multi : la protection tombe après le choix ou la déconnexion
- [x] Bénédictions ×1 Facile/Normal, ×1,25 Difficile, ×1,5 Cauchemar, dans tous les modes
- [x] Écran de choix et catalogue : valeurs affichées = valeurs réelles

## Jamais testé
- [ ] Multijoueur : statistiques
- [ ] Multijoueur : vies partagées
- [ ] Succès « Marathonien »
- [ ] Les 24 succès s'obtiennent et s'affichent
- [ ] Reprise d'un monde 1.6 en 1.7 : hordes, Kingdom et sauvegarde/reprise de partie fonctionnent, `invasions` et `sables_ardents` apparaissent dans la liste des hordes
- [x] Ajouts de Claude Chat rejoués : Brasier d'offrande, Baraquement, coffres roulette

## Retours joueurs CurseForge (BLaoTea, 2026-10-18)
- [x] Tuto Vanilla, vague 5 (Zombie puissant) : la charge (dash) ne fait des dégâts que si elle touche, plus de mort en un coup
- [x] Anomalies / Terrain instable : plus aucune entre deux vagues (Calme, pause, coffres), celles en cours disparaissent

## Retours de test du désert (2026-10-18)
- [ ] Cavalier des dunes : le chameau ne s'assoit plus (surtout la nuit), le cavalier n'est plus immobilisé
- [ ] Séisme et Fissures (Tremor) : fissures en zigzag avec branches, plus de barre droite ; dégâts une seule fois par fissure

- [ ] Perdre ou arrêter une horde en plein séisme / sable mouvant / pics de glace : plus aucun bloc d'effet ne reste au sol (essayer aussi `/horde stop`)

- [ ] Sekhmet (vague 7) : deux fois plus grande (modèle et hitbox, pas bloquée dans les passages), plus de particules blanches : coup de queue et enfouissement lancent du sable

- [ ] Rê-Atoum (vague 10) : plus de Mirage ; puits de sable, geysers, rayon solaire, tourbillon, barrière : uniquement des particules de sable (ni violet ni blanc)
- [ ] Rayon solaire : le cercle et la colonne restent visibles jusqu'à l'impact, la foudre frappe bien l'endroit visé
- [ ] Éditeur de compétences : les champs « Particle » ont un bouton « Choisir » (liste avec recherche, sable en tête) ; la valeur choisie est bien appliquée en jeu

- [x] `/ws skill list`, `/ws skill sa_puits_sable`, `/ws skill sa_rayon_solaire`… (EN SURVIE) : un monstre de test lance la compétence sur toi ; `/ws skill <nom> me` : tu la lances toi-même ; complétion des noms

## Éditeur : liste triée en onglets
- [x] Onglets Hordes / Kingdom / Invasions avec leurs compteurs, chaque horde dans le bon onglet
- [x] Créer une horde de chaque type (modèle) puis enregistrer : la liste se rouvre sur le bon onglet
- [x] Changer le mode d'une horde (Général) puis enregistrer : elle change d'onglet
- [x] Défilement, ↺, 🗑, copier : fonctionnent dans chaque onglet ; onglet vide : message « Aucune horde »

## Couches de configuration (base du mod + couche du joueur)
- [x] Nouvelle installation (dossier `config/wavesurvivor` supprimé) : le jeu démarre, les hordes du mod sont là, aucun fichier copié en dehors des tiens
- [x] Migration d'une config 1.6 : dossier `wavesurvivor_backup_avant_couches` créé, rien de perdu (hordes perso, entités perso, coffres perso, boutique)
- [x] Après migration : tes hordes de base modifiées gardent leurs changements (point d'apparition…), le reste suit le mod
- [x] Dash à ×1,2 et Zombie puissant à 10 d'attaque visibles après migration (sans rien régler à la main)
- [x] Éditeur de hordes : badge `[modifiée · N]` sur une horde de base modifiée, `[mod]` sur les autres, `[en jeu]` sur les tiennes
- [x] Modifier un champ d'une horde de base puis enregistrer : seul ce champ est gardé (le reste suit les mises à jour)
- [x] Bouton ↺ d'une horde modifiée : rétablit la version du mod (confirmation au 2ᵉ clic)
- [x] Supprimer une horde du mod puis « ↺ Hordes du mod supprimées » : elle revient
- [x] Éditeur d'entités et de compétences : badge, bouton ↺, modification d'un seul champ
- [x] Autels : modifier la recette d'une horde de base, relancer le jeu : la modification est conservée
- [x] Coffres roulette : créer / modifier / supprimer un coffre, relancer : l'état est conservé
- [x] Boutique Renaissance et raretés : modifier, relancer : conservé
- [x] `/horde setspawn <horde>` : le point d'apparition est conservé après relance
- [x] Export / import d'une horde (zip) fonctionne toujours
- [x] Serveur dédié : démarre et migre sans erreur
