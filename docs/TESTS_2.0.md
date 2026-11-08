# Tests 2.0 — La Faille (cases à cocher en jeu)

Conception : `docs/DESIGN_FAILLE_2_0.md`. Mod Pehkui requis pour la Faille (sans lui : message et onglet indisponible).

## Jalon 1a — socle (mode, dimension, île, aller-retour)
- [ ] Éditeur, onglet Général : le bouton de mode fait Vagues → Kingdom → Invasion → **La Faille** → Vagues ; en mode Faille l'onglet « Pauses » devient « 🌌 La Faille »
- [ ] Onglet « La Faille » avec Pehkui : durée et secousses de l'animation, titre, rayon de l'île (8-48), vagues de la Horde ; valeurs conservées après fermeture / réouverture
- [ ] Onglet « La Faille » sans Pehkui : message d'explication seulement
- [ ] `/ws rift status` : dit si une Faille est ouverte et si Pehkui est installé
- [ ] `/ws rift open` (op, avec Pehkui) : une île de deepslate (rayon 16, plus épaisse au centre, bord d'obsidienne) apparaît dans la dimension `wavesurvivor:rift` et les joueurs proches y sont envoyés (titre « La Faille ») ; sans Pehkui : message d'erreur
- [ ] Autel lié à une horde en mode Faille : clic droit puis « Lancer » → la Faille s'ouvre (pas de compte à rebours ni de horde) ; terrain non vérifié
- [ ] Clic droit sur l'autel pendant qu'une Faille est ouverte : le joueur est envoyé sur l'île
- [ ] `/ws rift leave` : retour exactement là où on était en entrant ; `/ws rift enter` : retour sur l'île
- [ ] Tomber de l'île : replacé sur l'île avec 1 cœur de dégâts (filet provisoire)
- [ ] `/ws rift stop` : tout le monde est renvoyé chez soi, l'île et les entités sont effacées
- [ ] Se déconnecter dans la Faille puis relancer le monde (Faille fermée) : on est renvoyé chez soi à la connexion

## Jalon 1b — Portail de la Faille et animation d'ouverture
- [ ] Lier l'autel à « La Faille » (1 obsidienne pleureuse + 4 éclats d'améthyste) puis Lancer : plus de téléportation directe, le portail s'ouvre à ~18 blocs de l'autel, façade tournée vers l'autel
- [ ] Animation : grondement + poussière (1,5 s), le rocher surgit couche par couche avec secousses, silence + fissures violettes, explosion (éclair, ovale qui vole en éclats), le portail s'ouvre du centre vers les bords, veines violettes + contour turquoise, vrilles une à une, titre « La Faille est ouverte »
- [ ] Les joueurs trop proches sont repoussés (ou sortis de l'emprise du rocher) sans dégâts
- [ ] Une fois ouvert : marcher dans l'ovale envoie sur l'île
- [ ] `/ws rift stop` : tout le monde rentre, l'ovale redevient de la pierre, le rocher reste en place
- [ ] Relancer depuis le même autel : pas de nouvelle animation complète, courte réouverture (~1,5 s)
- [ ] Redémarrer le jeu avec un portail resté ouvert : il est refermé (pierre) au chargement du monde
- [ ] Réglages de l'éditeur : durée × (0,5 = rapide, 2 = lent), secousses × (0 = aucune), titre on/off
- [ ] Terrain en pente / dans l'eau : le rocher se pose correctement (fondations), pas de joueur coincé
- [ ] Texture animée du portail visible des deux côtés, translucide

## Onglet de l'autel
- [ ] L'écran « Lier l'autel » a un onglet « 🌌 La Faille » (visible seulement s'il existe une horde Faille), la horde n'est plus dans « Hordes »

## Jalon 2 — Phase Horde sur l'île
- [ ] Portail ouvert + un joueur entre : message/actionbar « La Horde commence dans X s », la horde démarre au bout du délai (réglable)
- [ ] 5 vagues sur l'île (zombies, archers, araignées, gardiens), boss à la vague 5, apparitions sur l'île (pas dans le vide)
- [ ] Mort : on garde son inventaire, spectateur au-dessus de l'île, retour après X s (réglable) ; pas de mort réelle ni retour au monde normal
- [ ] Tous les joueurs à terre = défaite, message, pause de fin puis retour devant le portail
- [ ] Victoire : récompenses de la horde, pause de fin (4 min par défaut, compte à rebours en actionbar), puis fermeture et retour
- [ ] Joueur qui entre en cours de Horde : spectateur, il joue à la vague suivante
- [ ] `/ws rift stop` pendant la Horde : arrêt propre, tout le monde rentre, le rayon d'apparition de la horde est restauré
- [ ] Éditeur : onglet La Faille → vagues, début après, retour après mort, pause de fin
