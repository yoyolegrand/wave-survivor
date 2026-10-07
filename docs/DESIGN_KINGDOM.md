# Wave Survivor — Mode Kingdom (brainstorming)

*Idée notée le 02/10/2026 — expérimental, à reprendre plus tard. Rien n'est figé.*

## Concept
Un nouveau mode de horde qui transforme la défense de l'autel en **défense de royaume**.

## Zone claim
- La zone autour de l'autel (au lieu de simplement changer le sol) devient la **zone claim du joueur** — ex. 32×32.
- Construction **autorisée dans la zone**, **bloquée / limitée en dehors**.

## Défenses (boutique, forme à définir)
- **Tours d'archers**.
- **Baraquements** qui font apparaître de petits guerriers alliés.
- Autres structures défensives à imaginer.

## Apparition des monstres
- **Jamais dans la zone claim.**
- Sur un **anneau éloigné** : ex. entre **64 et 72 blocs** du Monolithe (pas de 0 à 64).
- Sur des **points fixes aux 4 points cardinaux** (N / S / E / O), via de **grandes Brèches**, beaucoup plus résistantes que les brèches actuelles.

## Rythme : Horde / Calme (au lieu de vagues + pauses)
- **Phase Horde** : assaut brutal — ex. ~100 unités par portail, longue portée de détection, elles foncent vers le **Monolithe** ou le **joueur** (avec les unités spécialisées destructrices de Monolithe).
- Une fois toutes les unités tuées → **phase Calme** : les portails produisent beaucoup moins (~5 % de la masse de la phase Horde).
- Pendant le calme : de **petites brèches** s'ouvrent ponctuellement et font apparaître un peu plus d'unités, même objectif.

---

## ✅ Décisions validées

### 1. Flux continu plafonné (performances)
- Budget d'unités par phase Horde (ex. 100 par portail), libéré progressivement.
- Plafond global d'unités vivantes (ex. 80 au total, 20 par portail) : une unité meurt → le portail envoie la suivante.

### 2. Comportement de « marche » dédié
- **Couloirs** : chaque portail a un chemin (points de passage) vers le Monolithe, calculé une fois en début de partie.
- **Escouades** de 5 à 10 : seul le chef calcule le chemin, les autres le suivent (×10 moins de calculs).
- **Décoinçage** : immobile 3 s → petit saut ou téléportation de 2-3 blocs vers l'avant.
- **Approche finale** : à 16 blocs du Monolithe ou du joueur → IA de combat normale.
- **IA allégée** pendant la marche : pas d'errance, pas de ramassage d'objets, chemin recalculé toutes les 0,5 s seulement.

## 🤔 Propositions à approfondir

### 3. Murs et siège
- **Durabilité des blocs** posés par le joueur dans le claim, selon le matériau (bois < pierre < deepslate < obsidienne) ; fissures progressives quand les monstres frappent.
- **Unités de siège** : Sapeur (fait sauter uniquement les blocs du joueur), Bélier (défonce un mur en quelques coups), Grimpeur (passe par-dessus).
- Le terrain naturel reste protégé.

### 4. Défenses alliées (boutique du Monolithe, uniquement dans le claim, plusieurs niveaux)
- **Tour d'archers** : bloc qui tire sur le monstre le plus proche (24 blocs) ; améliorations flèches enflammées, tir multiple. Effort faible.
- **Baraquement** : 3 à 5 soldats alliés qui réapparaissent après un délai. Effort moyen (nouvelle créature alliée).
- **Tour de mage** : aura de lenteur. Effort faible.
- **Sanctuaire de réparation** : soigne lentement le Monolithe. Effort faible.
- Perfs : les tours agissent toutes les 0,5 s ; les soldats ont leur propre plafond.

### Boucle de jeu proposée
- Phase Horde = défendre ; phase Calme = sortir attaquer un portail (les 4 grands portails ont des PV).
- Victoire = les 4 portails détruits (boss possible sur le dernier).

## ✅ Fin de partie (validée)
- **Victoire = les 4 grands portails détruits.** Premier jet : portails vulnérables uniquement pendant le Calme.
- **Défaite** si le Monolithe tombe.
- **Limite de sécurité** : la partie se termine toute seule si elle devient impossible (ex. nombre maximum de cycles).
- **`/ws stop`** arrête une partie Kingdom ; **`/ws skip`** passe à la phase suivante (Assaut ↔ Calme).

## 🔮 Idées pour plus tard (quand les mécaniques seront renforcées)
- **Gardiens** : unités qui gardent chaque portail ; tant qu'ils vivent, le portail est **invincible**.
- **Objectifs pendant le Calme** : par exemple attaquer un **catalyseur** qui « débrèche » un portail → il perd son invincibilité et peut être attaqué jusqu'à un certain seuil de PV.
