# Bourse à clés (`key_pouch`) — 1.9

> Demandée par Yoann, rédigée par Claude chat. Icône **installée** : `textures/item/key_pouch.png` (PixelLab, 32 × 32 — bourse en cuir brun, cordon doré, clé dorée qui pend).

## 1. L'objet
- Une **petite bourse** (pas un sac à dos), non empilable (1 par case).
- **Ne contient que des clés** de coffres roulettes (toutes les clés `RouletteKeyItem`, y compris celles des coffres personnalisés). Les clés s'empilent par type (64).
- Contenu enregistré **dans l'objet** (comme une boîte de Shulker / un sac vanilla) : on peut la ranger, la jeter, la donner, elle garde ses clés.
- **Info-bulle** : liste des clés contenues (« Clé du Reliquaire du Boss ×3 », etc.) et rappel des commandes.

## 2. Ouvrir la bourse
- **Clic droit dans le vide** (bourse en main) → ouvre un **inventaire de coffre** (3 rangées, 27 cases) réservé aux clés : tout autre objet est refusé.
- On peut y déposer / retirer des clés comme dans un coffre ; les touches habituelles (Maj + clic, glisser) fonctionnent.

## 3. Utiliser les clés depuis la bourse
Valable pour **tous** les coffres roulettes : hordes, Kingdom, Invasions, Boss Rush, coffres personnalisés, arènes, ruines…
- Condition : **aucune clé en main** et au moins **une bourse dans l'inventaire** du joueur (barre rapide, inventaire principal ou seconde main).
- **Clic droit** sur un coffre roulette → la bourse fournit **une** clé du bon type, qui est **consommée**, et le coffre s'ouvre comme d'habitude (roulette, animation).
- **Accroupi + clic droit** → **toutes** les clés de ce type contenues dans la bourse sont consommées : autant de tirages que de clés.
  - Pour ne pas enchaîner 20 animations : une **seule** animation (accélérée), puis un **écran de résumé** avec tous les gains (« ×12 ouvertures »), le meilleur gain mis en avant ; le bonus de rareté de la révélation joue pour le gain le plus rare.
- Une clé **tenue en main** reste prioritaire sur la bourse.
- Si la bourse ne contient pas la bonne clé : petit message « Aucune clé pour ce coffre dans ta bourse ».
- Plusieurs bourses : on puise dans la première qui contient la bonne clé.

## 4. Options (réglables en config)
- **Ramassage automatique** : quand une bourse est dans l'inventaire et qu'elle a de la place, les clés ramassées y vont directement (activable / désactivable par joueur : accroupi + clic droit dans le vide avec la bourse en main, avec un message de confirmation).
- Taille de la bourse (27 cases par défaut).
- Autoriser ou non l'ouverture en série (accroupi).

## 5. Obtention
- **Fabrication** (établi) : 4 cuirs, 2 ficelles, 1 lingot d'or, 1 pépite d'or — ou une recette au choix de Yoann.
- Possible aussi : vendue par le **Marchand** des hordes, ou remise comme récompense d'un objectif du **Carnet du Survivant** (« Obtiens ta première clé »).

## 6. Textes
FR / EN : nom (« Bourse à clés » / « Key Pouch »), info-bulle, messages, page du Codex (onglet Objets) et entrée dans l'onglet Ressources du Carnet du Survivant.
