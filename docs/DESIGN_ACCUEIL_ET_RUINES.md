# Accueil des joueurs, ruines et confort de jeu (1.9)

> Rédigée par Claude chat avec Yoann (tout validé). Toutes les valeurs (récompenses, pourcentages, fréquences) doivent être **réglables** (config et/ou éditeur) : celles indiquées sont les valeurs **par défaut**.

---

## 1. Le Carnet du Survivant
Un livre (écran dédié, style des autres écrans du mod) remis **automatiquement à la première connexion** d'un joueur dans un monde. Récupérable à tout moment avec `/ws carnet`, et fabricable (livre + papier). 4 onglets :

1. **Démarrage** — 5 pages courtes, chacune avec un bouton « Voir dans le Codex » vers la page correspondante :
   - l'autel et les vagues ; les bénédictions ; la Renaissance et les reliques ; le Sanctuaire du Séraphin ; le mode Kingdom.
2. **Objectifs** — étapes à cocher automatiquement, chacune avec une petite récompense, annoncées dans le **fil d'événements** quand elles sont validées. Par défaut :
   - lier sa première horde ; survivre à 5 vagues ; choisir une bénédiction ; dépenser ses premiers PR ; équiper une relique ; finir une horde ; faire une offrande au Sanctuaire ; jouer une partie Kingdom.
   - Récompenses modestes (quelques PR, pièces ou fragments). Liste et récompenses dans un fichier de config.
3. **Ressources** — une fiche par monnaie / ressource du mod (pièces, Essence, PR, fragments de grâce et de souillure, clés, réactifs de l'Alchimiste, Maîtrise…) avec : icône, **où on la gagne**, **à quoi elle sert**. Le contenu doit rester à jour quand on ajoute une ressource (lire les textes depuis les fichiers de langue).
4. **Codex** — une **recherche** plein texte dans toutes les pages du Codex (titre et contenu), résultats cliquables.

## 2. Les ruines à explorer
Trois petites structures **rares**, générées naturellement (même système que les arènes : `ArenaBlueprint` ou structures du jeu), toutes réglables en config (activée, fréquence, biomes, butin) :

| Ruine | Biomes | Contenu | Gardiens |
|---|---|---|---|
| **Chapelle effondrée** (marbre béni en ruine) | plaines, forêts, prairies | coffre : fragments de grâce (Éclats surtout, parfois un Fragment) | quelques morts-vivants à proximité |
| **Autel souillé** (pierre profanée, obélisque) | marais, forêts sombres | coffre : fragments de souillure | une **mini-vague de Corrompus** (Rootlings, parfois un Stone Brute) se déclenche quand un joueur approche |
| **Reliquaire scellé** (plus rare) | montagnes, pics | porte scellée qui s'ouvre avec une **clé** gagnée en horde ; dedans : un Reliquaire scellé **ou** un Cœur de grâce / de souillure | aucun (la clé suffit) |

- Utiliser les **blocs de l'Artisanat** déjà faits (marbre béni, pierre profanée, colonnes, lanternes, obélisque…) pour la construction.
- Chaque ruine n'est pillable **qu'une fois** par joueur (ou une fois tout court, réglable).
- Plans des structures : à préparer par Claude chat si besoin (comme les arènes).

## 3. Le bouton « Prêt » pendant les pauses
- Pendant une pause entre deux vagues, chaque joueur peut se déclarer **prêt** (bouton dans l'affichage + touche configurable). Afficher « Prêts : 2 / 3 ». Quand **tous** les joueurs de la partie sont prêts, la vague suivante démarre.
- **Dans l'éditeur, sur chaque pause** (là où se règle déjà la durée de la pause) :
  - interrupteur **« Autoriser Prêt »** (par défaut : oui) — pour pouvoir l'interdire avant un boss, par exemple ;
  - **bonus d'enchaînement** : type (pièces, PR, Essence, ou un effet temporaire : Force, Vitesse, Résistance…), quantité (ou durée et niveau pour un effet) ;
  - option **« proportionnel au temps restant »** (par défaut : oui) : enchaîner tout de suite donne le bonus maximal, juste avant la fin de la pause presque rien.
- Valeurs par défaut quand rien n'est réglé : Prêt autorisé, bonus = quelques pièces proportionnelles au temps restant.
- Fonctionne aussi en mode Sans fin, en Boss Rush et en Invasion ; pas en Kingdom (le Calme a sa propre logique).

## 4. Le prix de consolation en cas de défaite
- Une partie **perdue** rapporte quand même des **PR**, proportionnellement aux vagues survécues : par défaut **30 %** de ce qu'aurait rapporté la victoire à ce stade.
- Réglable dans la config globale **et** par horde dans l'éditeur (0 % = désactivé).
- Annoncé dans le résumé de fin de partie (« Défaite — prix de consolation : +X PR »).
- La Maîtrise des Vocations garde sa propre règle (×0,5 en défaite).

## 5. Les paliers du mode Sans fin
- Tous les **10 vagues** (réglable) : un **palier**.
  - Le joueur reçoit toujours un **coffre de palier** (contenu réglable, plus généreux à chaque palier).
  - Puis un écran de choix (temps limité, ex. 20 s, pendant la pause) : **Continuer** ou **Encaisser et s'arrêter**.
- **Gains** :
  - **Continuer jusqu'à la défaite** : **100 %** des gains accumulés, **aucun malus** (le joueur est allé au bout).
  - **Encaisser et s'arrêter** à un palier : **90 %** des gains accumulés (pourcentage réglable, défaut 90 %), la partie se termine proprement.
- En multijoueur : l'encaissement se décide **à la majorité** (ou par l'hôte, réglable) ; les joueurs qui ne votent pas comptent comme « Continuer ».
- Le classement du mode Sans fin enregistre la vague atteinte dans les deux cas.

---

## Ordre conseillé
1. **Prix de consolation** (le plus simple, effet immédiat).
2. **Bouton Prêt** + réglages dans l'éditeur.
3. **Paliers du mode Sans fin**.
4. **Carnet du Survivant** (Démarrage, Objectifs, Ressources, recherche dans le Codex).
5. **Ruines** (avec les plans à préparer).

Textes FR / EN, pages du Codex, CHANGELOG. Vérifier que le projet compile et commiter à chaque étape.
