# Artisanat sacré et profane — objets à base de fragments (1.9)

> Rédigée par Claude chat avec Yoann (idées validées). Prolonge le Sanctuaire du Séraphin (`docs/DESIGN_SANCTUAIRE.md`) : les fragments de **grâce** et de **souillure** servent aussi à fabriquer de la **nourriture**, des **blocs** et de l'**équipement**.
> Toutes les valeurs (effets, durées, recettes) sont une **première base** : les mettre en config (`crafting_fragments.json` ou datapack de recettes) pour pouvoir les ajuster.

---

## 1. Principes

- **Consacré** (grâce) = effets **positifs, modérés**. **Profané** (souillure) = effets **forts, avec un prix**.
- Le **niveau du fragment** fixe la puissance : **Éclat** → objets courants, **Fragment** → objets utiles, **Cœur** → objets rares.
- **Où fabriquer** :
  - nourriture et blocs de **décoration** → **table de fabrication** classique (recettes vanilla en JSON) ;
  - blocs **utilitaires** et **équipement** → nouvel onglet **« Artisanat »** dans l'écran du Sanctuaire (même style que les autres onglets, ligne directrice `docs/maquettes_sanctuaire/style_atelier/`).
- **Garde-fous** : **un seul effet de nourriture spéciale** actif à la fois (manger un 2e remplace le 1er) ; recettes assez chères en fragments pour rester en concurrence avec la forge.
- Identifiants : préfixe `blessed_` / `tainted_` quand c'est une paire (ex. `blessed_bread`, `tainted_meat`).

## 2. Nourriture

| Objet | Recette (table de fabrication) | Effet |
|---|---|---|
| ✨ **Pain béni** `blessed_bread` | 3 blé + 1 Éclat de grâce | 6 faim, saturation haute, Régénération I 5 s |
| ✨ **Pomme sanctifiée** `sanctified_apple` | 1 pomme + 4 Éclats de grâce | 4 faim, Absorption I 60 s, retire 1 effet négatif |
| ✨ **Hydromel céleste** `celestial_mead` | 1 fiole de miel + 1 Fragment de grâce | 4 faim, Résistance I 60 s |
| ✨ **Festin de grâce** `grace_feast` (bloc posable, 6 parts) | 1 Cœur de grâce + 3 pains bénis + 3 pommes dorées | chaque part : 8 faim + 4 cœurs ; toute l'équipe peut en manger |
| ☠ **Viande souillée** `tainted_meat` | 1 viande crue + 1 Éclat de souillure | 10 faim, Force I 30 s, puis Faim 10 s |
| ☠ **Fruit du néant** `void_fruit` | 1 fruit de chorus + 2 Éclats de souillure | 4 faim, Vitesse II 20 s, Nausée 3 s au début |
| ☠ **Vin noir** `black_wine` | 1 bouteille d'eau + 1 œil d'araignée fermenté + 1 Fragment de souillure | +20 % de dégâts 60 s, **-2 cœurs max** pendant ce temps |
| ☠ **Cœur cru** `raw_heart` | 1 Cœur de souillure + 1 chair putréfiée | +40 % de dégâts 120 s, **aucune régénération naturelle** pendant ce temps |

## 3. Blocs de décoration (table de fabrication, avec des Éclats)

- **Marbre béni** `blessed_marble` : 8 calcite + 1 Éclat de grâce → 8 blocs. Blanc veiné d'or, **luit faiblement** (niveau de lumière 4). Variantes : escaliers, dalles, muret, **colonne**, marbre poli, marbre sculpté.
- **Pierre profanée** `tainted_stone` : 8 pierre des abîmes + 1 Éclat de souillure → 8 blocs. Sombre, **fissures roses lumineuses** (lumière 4, calque lumineux comme les Corrompus). Mêmes variantes.
- Textures en accord avec la statue du Séraphin (pure / corrompue).

## 4. Blocs utilitaires (onglet Artisanat du Sanctuaire, avec des Fragments)

| Bloc | Coût indicatif | Effet |
|---|---|---|
| ✨ **Lanterne de grâce** `grace_lantern` | 2 Fragments de grâce + 1 lanterne | lumière 15 ; morts-vivants et Corrompus à moins de 8 blocs : -20 % de dégâts |
| ✨ **Fontaine sacrée** `holy_fountain` | 4 Fragments de grâce + 4 marbre béni | joueurs à moins de 5 blocs : Régénération I (pas pendant un combat de boss) |
| ✨ **Bougie votive** `votive_candle` | 1 Fragment de grâce + 1 bougie | allumée : **aucune apparition naturelle** de monstres à moins de 12 blocs (n'empêche pas les vagues des hordes) |
| ☠ **Totem d'appât** `bait_totem` | 2 Fragments de souillure + 1 bloc d'os | **attire** les monstres à moins de 16 blocs (sauf boss) pendant 30 s, puis recharge 60 s ; utile pour les pièges en Kingdom |
| ☠ **Obélisque profane** `profane_obelisk` | 4 Fragments de souillure + 4 pierre profanée | monstres à moins de 6 blocs : 1 dégât / s ; joueurs à moins de 6 blocs : Faim |
| ☠ **Autel de sang** `blood_altar` | 3 Fragments de souillure + 1 table d'enchantement | clic droit : -4 cœurs (dégâts) contre de l'expérience ; recharge 30 s |

## 5. Équipement (onglet Artisanat, avec des Cœurs ou des Fragments)

| Objet | Coût indicatif | Effet |
|---|---|---|
| ✨ **Lame de l'aube** `dawn_blade` | 1 Cœur de grâce + 1 épée en diamant | dégâts d'une épée en diamant, +50 % contre morts-vivants et Corrompus, **brille la nuit** |
| ✨ **Flèches bénies** `blessed_arrow` ×8 | 1 Fragment de grâce + 8 flèches | embrasent et repoussent les morts-vivants |
| ✨ **Totem de grâce** `grace_totem` | 1 Cœur de grâce + 1 totem d'immortalité | comme un totem, et soigne aussi les alliés à moins de 6 blocs (4 cœurs) |
| ☠ **Faux de la souillure** `taint_scythe` | 1 Cœur de souillure + 1 houe en diamant | arme de zone (balayage large), vol de vie 5 %, **coup manqué = -1 cœur** |
| ☠ **Flèches souillées** `tainted_arrow` ×8 | 1 Fragment de souillure + 8 flèches | Wither I 4 s + Lenteur I |
| ☠ **Totem de souillure** `taint_totem` | 1 Cœur de souillure + 1 totem d'immortalité | vous ramène à la vie avec +50 % de dégâts 30 s, puis **4 cœurs max pendant 2 min** |

- Les objets d'équipement **peuvent recevoir les améliorations** de la forge, en respectant l'alignement (une Lame de l'aube ne peut être que Consacrée ; une Faux de la souillure que Profanée).
- **Plus tard** : deux armures complètes, *Armure du Séraphin* (blanche et dorée) et *Armure profanée* (noire, fissures roses), modèles originaux détaillés en couches (fiche à part).

## 6. Visuels
- **Icônes FAITES** (PixelLab, 32 × 32) dans `textures/item/` : `sanctified_apple`, `celestial_mead`, `grace_feast`, `tainted_meat`, `void_fruit`, `black_wine`, `raw_heart`, `dawn_blade`, `blessed_arrow`, `grace_totem`, `taint_scythe`.
- **Icônes MANQUANTES** (à venir, Claude chat) : `tainted_arrow`, `taint_totem` — en attendant, utiliser une texture provisoire (flèche, totem vanilla). `blessed_bread` est maintenant **FAITE**.
- Blocs (marbre béni, pierre profanée, blocs utilitaires) : textures à faire plus tard ; utiliser des textures provisoires (calcite, pierre des abîmes…) pour les tests.

## 7. À faire (ordre conseillé)
1. Nourriture (objets + recettes + effets + garde-fou « un seul effet spécial »).
2. Blocs de décoration (marbre béni, pierre profanée et leurs variantes).
3. Onglet **Artisanat** du Sanctuaire + blocs utilitaires.
4. Équipement (+ compatibilité avec la forge et les alignements).
5. Textes FR / EN, page du Codex « Artisanat sacré et profane », CHANGELOG.
