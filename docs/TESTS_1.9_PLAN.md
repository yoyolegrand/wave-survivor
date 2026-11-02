# Plan de test 1.9 — Vocations (ordre conseillé)

Détail case par case : `docs/TESTS_1.9.md` (une section par étape de développement). Ce plan-ci donne l'**ordre** et regroupe tout.
Rien n'a encore été lancé en jeu : les phases 0 à 3 sont les plus importantes (si elles échouent, le reste ne sert à rien).

Raccourcis utiles (opérateur) : `/ws vocation xp <rôle> 11500` (niveau 20), `/ws vocation info [rôle]`, `/ws vocation buy <rôle> <talent>`,
`/ws vocation ultimate <rôle> <id>`, `/ws vocation reset <rôle>`, `/ws reload`. Rôles : arcanist, miner, builder, commander, quartermaster, alchemist, bounty_hunter, augur, scavenger.
Les PR s'ajoutent avec les commandes habituelles de Renaissance (150 pour un ultime, 50 par réinitialisation).

## Phase 0 — Build et démarrage (bloquant)
- [ ] Build Gradle sans erreur (`build.log`) — le code n'a été vérifié qu'avec javac.
- [ ] Le jeu démarre ; dans `latest.log` : `[Vocations] 9 arbres chargés.` et aucune erreur Vocations / paquet réseau.
- [ ] Un monde existant en 1.8.0 s'ouvre normalement (rien n'est perdu : PR, Héritage, reliques, parties Kingdom sauvegardées).
- [ ] Une partie Kingdom et une horde classique se lancent comme avant (aucun plantage au démarrage de partie).

## Phase 1 — Données et commandes (sans partie)
- [ ] `/ws vocation info` / `info builder` ; `xp` jusqu'au niveau 20 (plafond) ; `buy` (rangs, paliers 0/2/5/8) ; ultime (15 points + 150 PR, un seul) ; `reset` (50 PR, ultime re-choisi gratuitement).
- [ ] Refusé pendant une partie ; conservé après mort, reconnexion, redémarrage, Renaissance.
- [ ] Les PR dépensés comptent pour la Renaissance (rang d'Héritage).
- [ ] Plus aucun talent « Bientôt » ; aucune clé de texte manquante (pas de `voc.talent...` brut à l'écran, FR et EN).

## Phase 2 — Écrans
- [ ] Autel de Renaissance : 5 onglets alignés ; « Vocations » ouvre la grille des 9 métiers (Maîtrise, barre, points) ; les autres onglets ramènent à l'écran habituel.
- [ ] Arbre d'un métier : en-tête, ligne « Base », 3 colonnes reliées, couleurs (vert / doré / gris), total appliqué + valeur par rang + carrés de rang, info-bulles lisibles, ultimes, Retour, Réinitialiser (clic de confirmation, grisé en partie).
- [ ] Clic sur un talent : +1 rang (message d'erreur sinon) ; le serveur refuse hors règles.
- [ ] Monolithe › Rôles : « Maîtrise N » dans la liste ; fiche = bloc Maîtrise + « Voir l'arbre complet » (lecture seule, Retour au Monolithe).
- [ ] Mises en page à plusieurs échelles d'interface (×2, ×3, ×4) : arbre entier visible, aucun chevauchement (fiche de l'Alchimiste, de l'Augure avec 3 présages, boutons de talents).
- [ ] Codex : page « Vocations » (FR et EN), page Renaissance (5 onglets), page Présages (2 présages).

## Phase 3 — XP de Maîtrise en partie Kingdom
- [ ] XP en réserve pendant la partie ; versée à la fin : ×2 victoire, ×0,5 défaite, rien avec `/ws stop` ; messages et fil d'événements (niveau gagné).
- [ ] 10 XP par Assaut survécu + actions de chaque rôle (voir `TESTS_1.9.md` étape 2) ; XP attribuée au rôle tenu au moment de l'action.
- [ ] Reprise d'une partie sauvegardée : l'XP en réserve repart de zéro (limite connue, à confirmer que rien ne plante).
- [ ] Joueur déconnecté à la fin : pas d'XP (limite connue).
- [ ] Boss Rush / Défi du jour / arènes en Kingdom : la fin de partie verse bien l'XP et ne plante pas.

## Phase 4 — Effets de base réduits (régression d'équilibrage), sans talent
Vérifier que chaque rôle fonctionne comme avant **avec les valeurs réduites** :
- [ ] Arcaniste : 2e Catalyseur 10 %, purification ×1,5. · Exploitant : Célérité I, double butin 15 %, plus de +1 gisement ni de taille automatique.
- [ ] Bâtisseur : -10 %, +5 % PV. · Commandant : soldat en plus dès le baraquement niveau 2, bannière 40 s. · Intendant : +10 % pièces, marchands -8 %, intérêts 3 % (max 60).
- [ ] Alchimiste : fioles jusqu'au niveau 2 (écran « max » juste, prix des améliorations). · Chasseur : contrats -30 %. · Augure : 2 présages. · Récupérateur : débris 8 %, siège ×0,8.
- [ ] Descriptions de rôle (Monolithe, Codex) cohérentes avec ces valeurs.

## Phase 5 — Talents, rôle par rôle (acheter, jouer une partie, constater l'effet)
Pour chaque talent : un point + une situation de jeu qui le déclenche. Liste complète et conditions dans `TESTS_1.9.md`.
- [ ] **Arcaniste** (12) : 2e Catalyseur, Essence à la fermeture, bouclier, double fermeture, purification rapide, foyer guérisseur, zone sacrée, butin sacré, repoussement, dégâts magiques, foudre sur élites, orbe arcanique.
- [ ] **Exploitant** (12) : fréquence, taille, gisement en plus, durée de vie, double butin, minerais rares, Essence des arbres, récolte de zone, Célérité, résistance, aura pesante, trésor direct.
- [ ] **Bâtisseur** (12) : remise, améliorations, murs/portes, dernier niveau offert, PV, pièges, charges de démolition, charge de siège, réparations, démontage, ruines, auto-réparation.
- [ ] **Commandant** (12) : PV et dégâts des soldats, relève rapide, soldat précoce, bannière, cor, ralliement rapide, double bannière, cri de guerre, priorité siège, force du cor, sans recul.
- [ ] **Intendant** (12) : pièces, intérêts, plafond, prime d'assaut, remise, remise d'équipe, offre en plus, offre rare, ressources de départ, conversions (3 boutons), Essence→pièces, Fournitures.
- [ ] **Alchimiste** (12) : fioles de soin, purge, bastion des soldats, niveau 4 soutien, fioles offensives, givre sur élites, sol en feu, niveau 4 offensif, réactifs d'élite, améliorations moins chères, Maître alchimiste, fiole offerte.
- [ ] **Chasseur** (12) : paiement, changement de contrat, contrat secondaire, XP des contrats, dégâts sur élites, marque, contrat prolongé, élan, butin d'élites, trophées, Essence de champion, collection.
- [ ] **Augure** (12) : présage en plus, relance, scellé/libérer, aperçu, avantages renforcés, contreparties adoucies, persistant, cumulé, double, contreparties doubles réduites, harmonie, double dès le début.
- [ ] **Récupérateur** (12) : débris, attraction, brèches généreuses, débris rare, recyclage objets, recyclage équipement, caisse de ferraille, débris réparateurs, butin de siège, butin de champion, bélier apprivoisé, coffre de pillard.
- [ ] Talents inactifs hors partie Kingdom et pour un joueur qui ne tient pas le rôle ; effets cumulés plausibles à 20 points (pas d'exploit évident).

## Phase 6 — Ultimes (18)
- [ ] Achat : refusé sous 15 points, 150 PR la première fois, re-choix gratuit après réinitialisation, un seul par rôle.
- [ ] Arcaniste : Rupture (brèche de Porte plus profonde), Nexus. · Exploitant : Filon-mère, Cœur de la forêt. · Bâtisseur : Chef-d'œuvre (+25 % PV), Grand chantier.
- [ ] Commandant : Garde d'élite, Dernier carré. · Intendant : Âge d'or, Marché noir. · Alchimiste : Panacée, Réaction en chaîne.
- [ ] Chasseur : Tête mise à prix, Grand veneur. · Augure : Triple présage, Présage vivant. · Récupérateur : Arsenal de siège, Trésor de guerre.

## Phase 7 — Présages (refonte de l'Augure) — le plus risqué à régresser
- [ ] Sans talent : comportement identique à avant (choix, effets, Offrande payée/remboursée, Frénésie, Chasse royale…).
- [ ] Avec talents : plusieurs présages actifs ensemble ; avantages/contreparties modulés ; relance 1×/Calme ; sceller/libérer ; aperçu fiable ; persistant 30 s ; cumulé ✦ ; présage double/triple et leurs réductions.
- [ ] Synchronisation client : les boutons de l'écran Augure reflètent l'état (choisis, désactivés, scellé).

## Phase 8 — Non-régression (le reste du mod)
- [ ] Hordes classiques et mode Autel : aucune différence (les talents ne s'appliquent pas).
- [ ] Kingdom sans aucun talent : parties complètes sur plusieurs Assauts, boss final, arène du Roi, vies partagées.
- [ ] Rôles : changement de rôle en cours de partie, rôle libéré à la déconnexion, Cor du Commandant, fioles, contrats, présages, débris.
- [ ] Sauvegarde/reprise d'une partie Kingdom (rôles, présages, soldats, bâtiments) ; fin de partie propre (bannières, marché noir, zones de feu, alliés retirés).
- [ ] Performances : une partie longue avec beaucoup de talents actifs (orbe, zones, attraction de débris) sans baisse de TPS notable.

## Phase 9 — Multijoueur et serveur dédié
- [ ] Deux joueurs avec deux rôles : chacun ne voit/applique que ses talents ; les effets « équipe » (marque, panacée, Grand veneur…) touchent bien tout le monde.
- [ ] Serveur dédié : le client reçoit les arbres à la connexion (aucun fichier côté client) ; changer `vocations.json` + `/ws reload` met à jour les écrans à la réouverture.
- [ ] Un client sans les données reçues affiche « Chargement… » sans planter.

## Phase 10 — Configuration et robustesse
- [ ] `config/wavesurvivor/vocations.json` : surcharge d'un champ (ex. `maxLevel`, `xpCurve`, `ultimate.costPR`, une valeur de talent) ; fichier invalide → erreur claire dans le log, valeurs par défaut conservées.
- [ ] Valeurs extrêmes (0, négatives, énormes) : pas de division par zéro ni de plantage.

## Phase 11 — Avant publication
- [ ] `mod_version` → 1.9.0 (nom du jar dans la commande de build), CHANGELOG `## 1.9.0 — Role Vocations` relu, `.github/scripts/check_title.py` OK (≤ 64 caractères).
- [ ] Description CurseForge / Modrinth et captures de l'arbre à préparer ; mise à jour de CLAUDE.md.

## Ajouts après premiers tests (retours de Yoann)
- [ ] Fenêtre de l'autel de Renaissance agrandie de 25 % (325×295) : tous les onglets lisibles (Sacrifice : grille d'inventaire plus grande, Boutique/Forge : 9 lignes visibles, Héritage : nœuds plus larges, Vocations : cartes et arbre plus larges).
- [ ] Créatif : aucune condition de déblocage de horde exigée (l'écran de l'autel les affiche comme remplies, le lancement et la liaison passent), y compris pour les hordes Kingdom.
- [ ] Ultime Rupture : à chaque Catalyseur fermé, la Porte voit +15 % de PV max rendus vulnérables en plus (plus de « brèche refermée »).
- [ ] Compétence `war_banner` (Porte-étendard, Seigneur du Bastion…) : un porteur ne repose pas de bannière tant que les siennes tiennent ; aucune nouvelle bannière à moins de 8 blocs d'une bannière d'un autre porteur.
- [ ] Arbre des Vocations (agrandi : 410×272) : noms de talents tronqués proprement (« … ») sans chevaucher « N pts », ligne « lecture seule » entière, ultimes lisibles.
- [ ] Forme du claim : éditeur de horde Kingdom › « Forme du claim » (▢ Carré / ◯ Cercle, même surface). Cercle : bornes (améthyste/obsidienne) et particules en cercle, construction autorisée seulement dans le cercle, carte du Monolithe avec contour circulaire, Mairie qui agrandit le cercle, soldats qui restent dedans.
- [ ] Le siège de base (legacy_base) démarre en cercle ; le Kingdom Pillager Siege aussi pour un NOUVEAU monde (un monde déjà existant garde sa config : activer « Cercle » dans l'éditeur).
