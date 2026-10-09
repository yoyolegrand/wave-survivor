# Wave Survivor — Fiches de conception : Mutateurs & Boss Rush

*Validées le 01/10/2026 — à développer après la 1.0.0. Ordre conseillé : mutateurs, puis Boss Rush.*

---

## 🎲 Mutateurs de horde

**Principe :** sur l'écran de confirmation de l'autel, le joueur coche des mutateurs avant de lancer la horde. Chacun rend la partie plus dure et augmente les récompenses.

| Catégorie | Mutateur | Effet | Bonus |
|---|---|---|---|
| Monstres | Frénésie | +30 % de vitesse | +15 % |
| | Blindés | +50 % de PV | +20 % |
| | Nuée | +50 % de monstres par vague | +25 % |
| | Instables | explosent à leur mort | +15 % |
| | Venimeux | chaque coup reçu empoisonne | +15 % |
| Joueurs | Fragiles | pas de régénération naturelle | +20 % |
| | Affamés | faim 2× plus rapide | +10 % |
| | Une seule vie | mort = spectateur jusqu'à la fin | +40 % |
| Boss | Enragés | +50 % de PV, mécaniques de fin de combat dès le début | +30 % |
| | Escorte | sbires d'élite avec chaque boss | +15 % |
| Arène | Nuit éternelle | obscurité permanente | +15 % |
| | Sol instable | Anomalies à chaque vague | +20 % |
| | Monolithe fragile | intégrité divisée par 2 | +25 % |
| Fun | Brutes | monstres ×1,5 PV et dégâts, plus lents | +10 % |
| | Avortons | monstres à la moitié des PV, très rapides | +10 % |

**Décisions du 02/10/2026 :**
- Les mutateurs s'appliquent aussi au **mode Kingdom**, **sauf « Une seule vie »** (trop punitif sur une longue partie).
- « Géants » et « Miniatures » sont remplacés par **« Brutes »** et **« Avortons »** : Minecraft 1.20.1 ne permet pas de changer la taille d'une créature sans astuce technique lourde.
- Ordre de développement prévu : 1. cœur + écran de sélection sur l'autel → 2. les 15 effets → 3. récompenses → 4. Défi du jour → 5. éditeur (mutateurs autorisés par horde).

- Les bonus s'additionnent (ex. « Butin ×1,8 ») : butin, chances de clés, PR de Renaissance, score du classement.
- Récompense exclusive au-delà d'un seuil (ex. ×2).
- **Défi du jour** : 3 mutateurs tirés chaque jour, identiques pour tout le serveur, récompense spéciale.
- **Éditeur** : mutateurs autorisés par horde ; plus tard, mutateurs personnalisés.

---

## 👑 Mode Boss Rush

**Principe :** uniquement les boss, à la suite, sans vagues de monstres.

1. Bouton « ⚔ Boss Rush » sur l'écran de l'autel, débloqué après avoir terminé la horde une fois.
2. Les boss de la campagne s'enchaînent (ex. Fossoyeur → Morvhal).
3. 20 s de pause entre deux boss + coffre de ravitaillement.
4. 3 vies partagées par l'équipe.

| Variante | Contenu |
|---|---|
| Boss Rush de campagne | les boss d'une horde (Vanilla ou Moddée) |
| Grand Boss Rush | tous les boss finaux à la suite |
| Gantelet | chaque boss +15 % plus fort que le précédent |

- Chronomètre + classement des meilleurs temps par serveur.
- Récompenses : clé du Reliquaire du Boss par boss + PR de Renaissance.
- **Décision du 09/10/2026 : pas de médailles, pas de trophées décoratifs, pas d'objet exclusif** (donc aucune nouvelle texture pour le Boss Rush).
- Compatible avec les mutateurs.
- Réutilise les boss et contrôleurs existants : surtout un nouveau déroulement dans le gestionnaire de hordes.

---

## ⚔ Refonte de la difficulté des boss — « Boss 2.0 » (validée, à faire AVANT le Boss Rush)

**Constat :** les boss (Vanilla et Moddés) sont trop faciles et trop peu complexes : peu de mécaniques, exploitables (pilier, arc à distance, coin de mur), aucune pression, PV identiques en solo et en groupe, rythme prévisible.

**Socle commun à tous les boss** (Vanilla, Moddés et futurs boss custom), réglable dans l'éditeur (sous-onglet « ⚔ Difficulté » de l'onglet Boss) :

1. **Phases (66 % et 33 % des PV)** : courte pause avec onde de choc, puis boss plus rapide, temps de recharge réduits, nouvelles compétences débloquées.
2. ~~Timer d'enrage~~ — **écarté**, non retenu.
3. **Anti-exploitation** :
   - joueur hors d'atteinte (pilier, > 20 blocs, derrière un mur trop longtemps) : attiré vers le boss, ou le boss se téléporte vers lui ;
   - phases de bouclier qui renvoient les projectiles ;
   - plafond de dégâts par coup (ex. 8 % des PV du boss).
4. **Mécaniques à gérer** :
   - attaques télégraphiées (zone rouge au sol 1,5 s, puis gros coup) ;
   - objectifs de protection (totems / cristaux / sbires : boss invulnérable ou qui se régénère tant qu'ils vivent) ;
   - pression sur le Monolithe (le boss envoie des Profanateurs pendant le combat).
5. **Combos de compétences** : enchainements (attirer → uppercut → frappe au sol) et choix de l'attaque selon la situation (distance, joueurs groupés, PV bas).
6. **Mise à l'échelle selon le nombre de joueurs** : +40 % de PV par joueur supplémentaire (réglable), plus de sbires.
7. **Affixes aléatoires** (1 ou 2 par apparition, affichés dans la barre du boss) : Vampirique, Blindé, Invocateur, Rapide, Réflecteur, Volatile.

**Ensuite, une mécanique signature par boss**, par exemple :
- Le Fossoyeur : enterre les joueurs immobiles et les tire sous terre ;
- Le Seigneur du Bastion : plante des bannières d'or qui renforcent les piglins, à détruire ;
- Le Gardien Shulker / Golem de l'End : balles de shulker téléguidées à détruire ;
- Le Roi Mort : maudit un joueur que les autres doivent protéger ;
- Le Monstre des Profondeurs : inonde une partie de l'arène.

**Ordre de développement conseillé :** bloc 1 = phases + anti-exploitation + plafond de dégâts + mise à l'échelle (rend tous les boss plus durs d'un coup) → affixes → mécaniques signatures → Boss Rush.
