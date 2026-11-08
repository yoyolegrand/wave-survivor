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
