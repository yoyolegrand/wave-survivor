# Tests 1.8 — suivi

Cocher `[x]` quand c'est validé en jeu. Une ligne en échec : la laisser décochée et noter le souci à côté.
Branche de travail : `dev-1.8` (`mod_version=1.8.0`).

## Étape 3 — tirage par rareté, Chance, relances, bannissements, famille
- [ ] Fin de vague : 3 cartes de CATÉGORIES différentes ; sur une dizaine de menus, les raretés varient (beaucoup de Commun/Rare, peu d'Épique, rare Légendaire)
- [ ] Les vagues avancées proposent plus souvent du rare / épique / légendaire qu'au début
- [ ] Avec des bénédictions de Chance (ou une potion de Chance) : les raretés élevées sortent plus souvent
- [ ] Bouton « ↻ Relancer (2) » : 3 nouvelles cartes, le compteur baisse ; à 0 le bouton est grisé
- [ ] Bouton « Famille : … » : choisir une famille puis relancer → les cartes viennent de cette famille (celles qui existent)
- [ ] « ✖ Bannir (1) » sous une carte : elle est remplacée, et cette catégorie ne ressort plus de toute la partie
- [ ] +1 relance et +1 bannissement toutes les 5 vagues (message « +1 relance(s), +1 bannissement(s) » à la vague 5, 10…)
- [ ] Solo : le jeu reste en pause pendant les relances ; multijoueur : le joueur reste protégé (et invulnérable) tant qu'il relance / choisit
- [ ] Fin de partie : relances et bannissements remis à zéro à la partie suivante
- [ ] `/ws benediction choose <id>` refuse une bénédiction qui n'est pas proposée (sauf opérateur sans menu en attente)

## Étape 2 — éditeur des bénédictions
- [ ] Bouton « 🙏 Bénédictions » dans l'éditeur de hordes (réservé aux opérateurs) ; l'écran s'ouvre avec les 10 catégories de base et leurs niveaux (carrés de couleur)
- [ ] ◀ ▶ : passer de Commun à Légendaire ; la case « Disponible / Désactivé / N'existe pas » fonctionne (ex. Longévité : Commun et Rare « n'existent pas »)
- [ ] Modifier la valeur d'un effet, ajouter / retirer un effet, changer l'opération, changer le soin : enregistrer ; l'écran de choix en jeu reflète le changement (sans redémarrer)
- [ ] Sélecteur d'attribut « … » : liste avec recherche contenant les attributs de Minecraft ET des mods installés (Apothic, Iron's…) ; en choisir un d'un mod et le voir appliqué
- [ ] Badge `[modifiée · N]`, bouton ↺ (rétablit la base, confirmation au 2ᵉ clic), « ↺ ✖ » (rétablit les catégories supprimées)
- [ ] Créer une catégorie (+), la dupliquer, la supprimer ; le nom, l'icône et la description apparaissent dans l'écran de choix
- [ ] Impossible d'enregistrer pendant une horde (message clair) ; un joueur non opérateur n'a pas accès
- [ ] Multijoueur : les joueurs voient la modification sans se reconnecter

## Étape 1 — bénédictions en données (aucun changement de jeu attendu)
- [ ] Le jeu démarre sans erreur (aucun « Attribut inconnu » dans les logs, ligne « [Bénédictions] 34 bénédictions construites »)
- [ ] Fin de vague : l'écran de choix propose les mêmes bénédictions qu'avant (noms, icônes, couleurs de rareté, descriptions, valeurs)
- [ ] `/ws benediction liste` : le catalogue montre toujours les 34 bénédictions
- [ ] Choisir une bénédiction applique la même statistique qu'avant (ex. Vie : PV max et soin ; Force : dégâts)
- [ ] Une partie en cours (monde de la 1.7) garde ses bénédictions déjà obtenues
- [ ] Multijoueur / serveur dédié : un client qui se connecte voit le même catalogue (écran de choix et liste)
- [ ] Couche du joueur (test manuel) : créer `config/wavesurvivor/custom_blessings.json` avec
      `{"patches":{"LONGEVITE":{"tiers":{"EPIC":{"enabled":false}}}}}` puis `/ws reload` : la Longévité Épique n'est plus proposée, la Légendaire oui
