# Boussole des Vestiges (`vestige_compass`) — 1.9

> Demandée par Yoann, rédigée par Claude chat (fonctionnement validé). Ressources **installées** : 32 textures `textures/item/vestige_compass/vestige_compass_00..31.png` (32 × 32), modèle principal `models/item/vestige_compass.json` (avec `overrides` sur la propriété `angle`) et 32 modèles d'aiguille dans `models/item/vestige_compass/vestige_compass_00..31.json` (référencés comme `wavesurvivor:item/vestige_compass/vestige_compass_XX`).

## 1. Principe
Une boussole qui pointe vers la **structure du mod non réclamée la plus proche**, sans dire laquelle (garder le mystère) — un peu comme `/locate`.

Structures suivies : arènes de horde (toutes), Sanctuaires du Séraphin, ruines (chapelle, autel souillé, reliquaire scellé), et toute future structure du mod (prévoir un registre extensible).

## 2. « Réclamée »
Une structure n'est plus visée une fois réclamée **par ce joueur** :
- **arène de horde** : la horde de cette arène a été terminée (victoire) ;
- **Sanctuaire** : le joueur a cliqué une fois sur la statue ;
- **ruine** : son coffre principal a été ouvert (reliquaire : le sceau a été ouvert).
Puis la boussole vise automatiquement la suivante. Option de config : suivi partagé par l'équipe / tout le serveur.

## 3. Utilisation
- **Tenue en main / dans l'inventaire** : l'aiguille (pointe dorée) tourne vers la cible, comme une boussole vanilla. Implémentation : `ItemProperties.register(VESTIGE_COMPASS, "angle", …)` avec une fonction de type `CompassItemPropertyFunction` dont la cible est une position globale (dimension + bloc) envoyée par le serveur et stockée dans le NBT de l'objet (ou dans un cache client).
  - **Repère des images** : `vestige_compass_00` = aiguille vers le haut (cible droit devant), puis chaque image tourne de 11,25° dans le sens horaire (`_08` = droite, `_16` = bas, `_24` = gauche). Le modèle `vestige_compass.json` associe `angle` 0 → `_00`, 1/32 → `_01`, etc. ; adapter si la fonction d'angle utilise une autre origine (la boussole vanilla décale de 16 images).
- **Clic droit** : message dans la barre d'action — « Vestige inconnu à 1 240 blocs » (distance arrondie). Pas de nom ni de type.
- **Accroupi + clic droit** (option, réglable) : filtre le type visé — Tout (défaut) → Hordes → Sanctuaires → Ruines → Tout ; message « Boussole réglée sur : Ruines ».
- **Aucune cible** (rien de non réclamé dans le rayon, ou autre dimension) : l'aiguille tourne au hasard (comme une boussole dans le Nether) et le clic droit affiche « Aucun vestige à proximité ».

## 4. Recherche des structures (serveur)
- Rechercher parmi les structures **déjà connues du mod** (positions enregistrées à leur génération / pose dans une SavedData) et, si besoin, parmi les structures dont la génération est prévue (comme `/locate`, sans charger de chunks inutiles).
- **Rayon** réglable (défaut 2 000 blocs). Rafraîchir la cible toutes les ~2 s (ou quand le joueur s'est déplacé de plus de 32 blocs), pas à chaque tick.
- Seulement dans la dimension du joueur.

## 5. Obtention
Fabrication (établi) : 1 boussole + 4 lingots d'or + 2 Éclats de grâce + 2 Éclats de souillure (recette au choix de Yoann) ; possible aussi en récompense d'un objectif du Carnet du Survivant (« Trouve ta première ruine »).

## 6. Textes
FR / EN : nom (« Boussole des Vestiges » / « Vestige Compass »), info-bulle (« Pointe vers le vestige non réclamé le plus proche. Clic droit : distance. »), messages, page du Codex (Objets) et mention dans l'onglet Ressources du Carnet.
