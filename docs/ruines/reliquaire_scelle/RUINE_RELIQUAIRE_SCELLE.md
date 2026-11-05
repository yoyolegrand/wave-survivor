# Ruine « Le Reliquaire scellé » — structure du monde (1.9)

> Rédigée par Claude chat avec Yoann (visuel validé). Troisième des 3 ruines (`docs/DESIGN_ACCUEIL_ET_RUINES.md` §2), la plus **rare**. Un mausolée **neutre** en quartz dans les montagnes ; dedans, un rappel **discret** de la grâce et de la souillure.

Fichiers (dans `docs/ruines/reliquaire_scelle/`) :
- `blueprint_reliquaire_scelle.json` — plan bloc par bloc (même format que les autres ruines), 19 × 15 × 22, 40 blocs différents, ~9,5 Ko.
- `reliquaire_scelle.png` (extérieur), `reliquaire_interieur.png` (coupe).

## 1. Contenu
- **Socle rocheux** de montagne (pierre, andésite, pierres moussues, gravier), herbes, fougères, bleuets.
- **Mausolée en quartz** : soubassement en briques de pierre, murs en briques de quartz, **piliers** aux angles, **pilastres** à chapiteaux sculptés, **fentes de lumière** à barreaux de fer sur 3 côtés, **corniche** (escaliers de quartz retournés) tout autour.
- **Porte en retrait** : encadrement de piliers + linteau sculpté, **sceau** reculé d'un bloc (3 × 4) avec **serrure** en quartz sculpté au centre.
- **Parvis** en damier de quartz, **balustrade** en murets de briques de pierre, **jarres** (pots décorés) près de la porte, **2 grands piliers** surmontés de **lanternes**, marches d'accès.
- **Toit à gradins** en quartz lisse, **acrotères** aux 4 angles, **fleuron** (quartz sculpté + barre de l'End).
- Usure : lierre, mousse.
- **Intérieur sobre** : sol en quartz lisse avec croix de quartz sculpté, **piédestal** (pilier de quartz + marches) portant le **coffre**, jarres dans les angles, bougies blanches, toiles d'araignée. **Seuls rappels** : un `blessed_marble_chiseled` et un `tainted_stone_chiseled` **incrustés** au pied du piédestal, une **lanterne de grâce** et une **lanterne d'âme** suspendues de chaque côté.

## 2. Repères (`markers`)
- `seal.blocks` : les 12 blocs de la porte (11 de deepslate polie + la serrure) ; `seal.lock` : la serrure.
- `chest` : le coffre sur le piédestal ; `entrance` : l'entrée (au sud).

## 3. Le bloc « Sceau » (`reliquary_seal`) — à créer
- Bloc **incassable** (résistance de la bedrock, pas de drop), aspect : pierre sombre gravée de runes dorées et roses alternées qui pulsent faiblement ; la **serrure** (centre) a son propre aspect (anneau doré autour d'un trou de serrure).
- À la pose du plan : remplacer les 12 blocs de `seal.blocks` par le Sceau (la serrure au centre).
- **Clic droit sur le Sceau avec une Clé scellée** (en main ou depuis la Bourse à clés) : la clé est consommée, **tous les blocs du sceau disparaissent** l'un après l'autre (du centre vers l'extérieur, ~0,5 s) avec des particules dorées et roses et un son de verrou ; la porte reste ouverte.
- Sans clé : message « Ce sceau ne s'ouvre qu'avec une Clé scellée ».

## 4. Butin (table réglable)
- Coffre : **un Reliquaire scellé** (objet) **ou** un **Cœur** (de grâce ou de souillure, 50 / 50), + 4 à 8 Éclats (des deux types) et quelques objets précieux (émeraudes, lingots d'or).
- Pillable une seule fois.

## 5. Génération et pose
- **Très rare** (réglable) : montagnes, pentes enneigées, prairies d'altitude, pics — sur un replat (refuser au-delà de 4 blocs d'écart ; combler sous le socle rocheux, qui est fait pour ça).
- Pose comme les autres ruines ; rotation aléatoire 0 / 90 / 180 / 270° (tourner repères, escaliers, lierre).
- Pas de gardiens : le sceau suffit.
- Codex « Ruines » + onglet Ressources du Carnet (« Reliquaires scellés : Clé scellée »).
