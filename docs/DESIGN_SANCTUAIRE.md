# Sanctuaire du Séraphin — fiche de conception (1.8)

> Rédigée par Claude chat avec Yoann. **Le visuel est déjà fait et installé** (voir §1). Cette fiche décrit la **logique** à coder.
> Règle : ne pas recréer ni écraser les fichiers listés au §1 ; les étendre.

---

## 1. Déjà en place (ne pas refaire)

| Élément | Fichier |
|---|---|
| Bloc | `shrine/SeraphShrineBlock.java` (FACING, `RenderShape.ENTITYBLOCK_ANIMATED`, clic droit provisoire : or/fleurs/miel/perle = purifie, chair/œil d'araignée/os = corrompt, accroupi main vide = défile) |
| Bloc entité | `shrine/SeraphShrineBlockEntity.java` (`stage` 0..4, synchro client, `getRenderBoundingBox` agrandie) |
| Modèle 3D | `client/SeraphShrineModel.java` (HD ×2, 3 paires d'ailes, capuche + voile d'ombre, halo 20 segments, `setupAnim(time, stage)` : halo qui tourne, ailes qui respirent/retombent, plumes perdues et halo brisé aux étapes 3-4) |
| Rendu | `client/SeraphShrineRenderer.java` (texture par étape + calque lumineux) |
| Textures | `textures/block/seraph_shrine/stage0..4.png` + `_glow` (1024×512) ; icône `textures/item/seraph_shrine.png` |
| Cadres d'emplacement (PixelLab) | `textures/gui/slot_fx/consecrated_1..3.png`, `profaned_1..3.png` (32×32, fond transparent) |
| Enregistrements | `ModBlocks.SERAPH_SHRINE`, `ModBlockEntities.SERAPH_SHRINE`, `ModItems.SERAPH_SHRINE_ITEM`, couche + rendu dans `ClientModEvents`, blockstate, modèles, loot, lang |

Étapes : **0 ✦ Pure · 1 ✧ Apaisée · 2 ◇ Neutre · 3 ✕ Souillée · 4 ☠ Corrompue**.

---

## 2. Structure dans le monde

- **Génération naturelle** rare (≈ 1 pour 1 500 blocs), en surface, biomes tempérés et désertiques (configurable). Apparaît **en ruine** : étape **Neutre (2)**, entourée de quelques blocs de quartz/calcite cassés et de mousse.
- **Incassable** en Survie : dureté `-1` (comme la bedrock), résistance aux explosions maximale, `PushReaction.BLOCK` (pistons), non déplaçable. Cassable seulement en Créatif.
- L'objet `seraph_shrine` reste disponible en Créatif / commande pour les tests et les cartes personnalisées.
- **Une statue = un état partagé** (multijoueur : tous les joueurs agissent sur la même jauge).

## 3. La jauge (pure ↔ corrompue)

- Valeur entière `faith` de **-600 à +600** (négatif = corrompu). Grâce et souillure se **compensent**.
- Paliers → étape : `faith ≥ +400` Pure (0) · `+100..+399` Apaisée (1) · `-99..+99` Neutre (2) · `-100..-399` Souillée (3) · `≤ -400` Corrompue (4).
- Le modèle lit `stage` (déjà synchronisé) : changer `stage` met à jour le visuel automatiquement.

### Offrandes (via l'écran, emplacement « Offrande »)
**Grâce (+)** : fleur, baies lumineuses = 1 · fiole de miel, éclat d'améthyste, bougie blanche = 3 · lingot d'or, perle de l'Ender = 5 · bloc d'or, bloc d'améthyste = 30 · pomme dorée = 40 · pomme dorée enchantée, totem d'immortalité = 150.
**Souillure (−)** : chair putréfiée, os = 1 · œil d'araignée, poudre à canon = 2 · œil d'araignée fermenté, sable des âmes = 5 · bloc de sculk, crâne de squelette = 15 · rose de Wither, crâne de Wither squelette = 40.
**Événements à moins de 16 blocs** : monstre de horde tué = +1 · animal paisible tué par un joueur = −3 · **villageois tué par un joueur = −50**.
Valeurs dans un JSON de config (`shrine_offerings.json`), modifiables.

## 4. Production de fragments (générateur)

- La statue produit selon son étape, stockée **dans la statue** (limite 64 par type) :
  Pure = 1 Éclat de grâce / 2 min · Apaisée = 1 / 5 min · Neutre = rien · Souillée = 1 Éclat de souillure / 5 min · Corrompue = 1 / 2 min.
- Bonus : chaque monstre de horde tué à proximité avance la production (dans la direction de l'étape).
- **Prix de la corruption** : à l'étape 3, des **Rootlings** apparaissent autour (toutes les ~3 min, 2-3) ; à l'étape 4, Rootlings plus fréquents + parfois un **Stone Brute**. Pas d'apparition si aucun joueur à moins de 32 blocs.

### Niveaux de fragments (objets)
| Niveau | Grâce | Souillure |
|---|---|---|
| I | `grace_shard` (Éclat de grâce) | `taint_shard` (Éclat de souillure) |
| II | `grace_fragment` | `taint_fragment` |
| III | `grace_heart` (Cœur de grâce) | `taint_heart` (Cœur de souillure) |
Fusion dans l'écran : **9 → 1** du niveau supérieur. Les Cœurs (III) peuvent aussi tomber des **boss** (chance faible, config).
Textures des 6 fragments : **FAITES** (PixelLab, 32×32) dans `textures/item/`. **Objets CRÉÉS** par Claude chat : `ModItems.GRACE_SHARD / GRACE_FRAGMENT / GRACE_HEART / TAINT_SHARD / TAINT_FRAGMENT / TAINT_HEART` (rareté peu commune / rare / épique, cœurs empilables par 16 avec reflet scintillant), modèles `models/item/*.json`, noms FR/EN, onglet créatif. Il reste seulement à les brancher sur la logique (production, fusion, forge, butin des boss).

## 5. L'écran de la statue (clic droit)

Onglets :
1. **Offrandes** : emplacement d'offrande + jauge visuelle (dorée ↔ violette), étape actuelle, prochain palier.
2. **Récolte** : fragments stockés, bouton « Récolter ».
3. **Fusion** : 9 fragments → 1 niveau supérieur.
4. **Forge** : un emplacement **équipement** + un emplacement **fragment** → aperçu de l'amélioration tirée, bouton « Insérer ».
Le clic droit provisoire actuel (§1) est à **remplacer** par l'ouverture de cet écran.

## 6. Amélioration des équipements

- **3 emplacements** par arme / pièce d'armure. Coût croissant : emplacement 1 = 1 fragment, 2 = 2 fragments, 3 = 3 fragments (du niveau choisi).
- **Un seul alignement par objet** : le premier fragment décide (**Consacré** ou **Profané**) ; ensuite seuls les fragments du même camp sont acceptés.
- Insertion = **tirage d'une amélioration** de la liste du camp, valable pour le type d'équipement ; sa force dépend du **niveau** du fragment.
- **Plafond** : un même type de bonus ne dépasse pas **+30 %** cumulé sur tout l'équipement porté.
- **Retirer / relancer** une amélioration : coûte des fragments, ne les rend pas.
- Stockage : NBT de l'objet (`WS_Align`, `WS_Upgrades:[{id, tier}]`) ; attributs via `ItemAttributeModifierEvent`, effets spéciaux via événements (dégâts, soin, mort…).

### Bénédictions (Consacré, que du positif) — I / II / III
| Id | Équipement | Effet |
|---|---|---|
| `holy_might` Force sacrée | Arme | +3 / +6 / +10 % dégâts |
| `celerity` Célérité | Arme | +3 / +6 / +10 % vitesse d'attaque |
| `smite` Châtiment | Arme | +8 / +15 / +25 % dégâts contre morts-vivants et Corrompus |
| `blessed_blade` Lame bénie | Arme | Soigne 0,5 cœur / **7** victimes · 0,5 cœur / **5** · **1** cœur / **3** |
| `wisdom` Grâce du savoir | Arme | +5 / +10 / +15 % d'expérience |
| `aegis` Égide | Armure | +3 / +6 / +10 % armure |
| `vitality` Vitalité | Armure | +1 / +2 / +3 cœurs max |
| `anchor` Ancrage | Armure | +5 / +10 / +20 % résistance au recul |
| `light_step` Pas léger | Bottes | +3 / +5 / +8 % vitesse |
| `breath_of_life` Souffle de vie | Plastron | Régénération hors combat lente / moyenne / rapide |
| `feather` Plume | Bottes | −15 / −30 / −50 % dégâts de chute |
| `enduring` Bénédiction durable | Tout | +15 / +30 / +50 % solidité |

### Corruptions (Profané, fort + malus lié) — I / II / III
| Id | Équipement | Bonus | Malus |
|---|---|---|---|
| `blind_rage` Rage aveugle | Arme | +10 / +18 / +30 % dégâts | −5 / −10 / −15 % vitesse d'attaque |
| `frenzy` Frénésie | Arme | +12 / +20 / +35 % vitesse d'attaque | +5 / +10 / +15 % dégâts reçus |
| `bloodthirst` Soif de sang | Arme | 3 / 5 / 8 % vol de vie | −1 / −2 / −3 cœurs max |
| `executioner` Bourreau | Arme | +20 / +35 / +60 % dégâts sous 30 % de vie de la cible | −5 / −8 / −12 % dégâts sinon |
| `venom` Venin | Arme | Poison 2 / 3 / 4 s à chaque coup | Faim accélérée |
| `cursed_shell` Carapace maudite | Armure | +10 / +18 / +30 % armure | −5 / −10 / −15 % vitesse |
| `tainted_flesh` Chair corrompue | Armure | +2 / +4 / +6 cœurs max | −10 / −20 / −30 % soins reçus |
| `taint_thorns` Épines de souillure | Armure | Renvoie 10 / 20 / 30 % des dégâts | −5 / −10 / −15 % robustesse |
| `rampart` Rempart | Plastron | +15 / +30 / +60 % résistance au recul | −5 / −10 / −15 % vitesse d'attaque |
| `shadow` Ombre | Bottes | +8 / +12 / +20 % vitesse | −5 / −10 / −15 % armure |
| `survival_pact` Pacte de survie | Plastron | Survit à un coup mortel (recharge 10 / 7 / 5 min) | −1 / −2 / −3 cœurs max |
| `mark_of_the_beast` Marque de la Bête | Tout | +15 / +25 / +40 % dégâts la nuit | −10 / −15 / −25 % dégâts le jour |

Toutes les valeurs dans un JSON (`shrine_upgrades.json`) pour l'équilibrage.

## 7. Affichage des objets améliorés

- **Cadres d'emplacement** (`textures/gui/slot_fx/`), dessinés sur l'emplacement de 16 px (texture 32×32 réduite de moitié), dans **tous les écrans** (inventaire, barre d'objets, coffres, écran de la statue) :
  - niveau du cadre = **nombre d'améliorations** (1, 2, 3) ;
  - Consacré : `consecrated_N`, **scintillement** (luminosité 0,85 → 1,1) ; dès N=2 **lueur dorée** floutée derrière l'objet ; N=3 **reflet** diagonal qui balaie l'icône (≈ 1 fois / 2,5 s) ;
  - Profané : `profaned_N`, **pulsation** (alpha 0,6 → 1) ; dès N=2 **lueur violette** derrière ; N=3 **tressaillement** : l'icône se dédouble en violet 2-3 images sur 12 ;
  - **Cadres AFFINÉS** (version actuelle) : tout le dessin tient dans une bande de **5 px sur 32 contre le bord** (2,5 px à l'écran), l'intérieur est vide. Les lueurs dessinées par le code doivent rester **discrètes et sur les bords** : l'icône de l'objet doit rester entièrement lisible (retour de Yoann : les anciens cadres prenaient trop de place).
- Technique : effets par-dessus l'icône via `RegisterItemDecorationsEvent` (`IItemDecorator`) ; lueur derrière l'icône = petite injection dans le rendu des objets en GUI (mixin `GuiGraphics.renderItem` ou équivalent).
- Barre d'objets : version plus légère (pas de reflet ni de tressaillement).
- **Option client** pour désactiver / atténuer les effets.
- Nom de l'objet coloré (doré ✦ / violet ☠) ; info-bulle : améliorations, niveau, bonus, **malus en rouge**.

## 8. À faire dans l'ordre conseillé
1. Bloc incassable + génération de la ruine dans le monde.
2. Jauge `faith` + écran (onglet Offrandes) + événements à proximité (villageois, animaux, monstres).
3. Production et récolte des fragments + objets fragments + fusion.
4. Forge : NBT, alignement, tirage, attributs et effets spéciaux, plafonds.
5. Apparition des Corrompus aux étapes 3-4.
6. Cadres d'emplacement + nom et info-bulle.
7. Config JSON + Codex (page « Sanctuaire du Séraphin ») + CHANGELOG.

## 9. Écran : ligne directrice visuelle (proposée par Claude chat)

### Style retenu : celui de l'Atelier du Kingdom, décliné en doré / argenté / violet
Prendre **`client/WorkshopScreen.java`** comme modèle : tout dessiné en aplats avec `g.fill(...)` (pas de texture) — bord sombre, fond un peu plus clair, **liséré coloré de 2 px en haut**, cases à fin contour coloré et fond sombre, **case spéciale** plus vive (comme le creuset) pour l'offrande et le fragment, textes dans les tons du thème. Onglets en haut (actif = liséré du thème).

**Thème** : selon l'état de la statue (étapes 0-1 → doré, 2 → argenté, 3-4 → violet). Dans l'onglet **Forge**, selon l'**alignement de l'objet** placé (Consacré → doré, Profané → violet, sans alignement → thème de la statue).

| Couleur | Doré (grâce) | Argenté (neutre) | Violet (souillure) |
|---|---|---|---|
| Bord | `0xFF2A2318` | `0xFF26262A` | `0xFF221A28` |
| Fond | `0xFF3A3124` | `0xFF34343A` | `0xFF30243A` |
| Liséré / boutons / onglet actif | `0xFFF2C14E` | `0xFFC8CCD8` | `0xFFB54BFF` |
| Contour de case | `0xFF9C7A34` | `0xFF707482` | `0xFF6A3A88` |
| Fond de case | `0xFF1C160E` | `0xFF18181C` | `0xFF140E1A` |
| Case spéciale (contour / fond) | `0xFFFFD36A` / `0xFF3A2A08` | `0xFFDDE2F0` / `0xFF2A2C36` | `0xFFD06BFF` / `0xFF2A0A36` |
| Titre | `0xFFFFE6A0` | `0xFFE6EAF4` | `0xFFEBC2FF` |
| Texte d'aide | `0xFFB8A888` | `0xFFA0A4B0` | `0xFFA898B4` |
| Libellé inventaire | `0xFFC8B898` | `0xFFB8BCC8` | `0xFFBCA8C8` |
Bonus en vert `0xFF6ED26E`, malus en rouge `0xFFEB5A5A`. La jauge or → argent → violet reste identique dans les trois thèmes, avec un curseur blanc.
Maquettes de référence : `docs/maquettes_sanctuaire/style_atelier/`.

Les textures « marbre et or » (`textures/gui/seraph_shrine/`) et les maquettes `docs/maquettes_sanctuaire/ecran_*.png` restent une **première piste** pour la disposition des éléments ; le style visuel à suivre est celui de l'Atelier ci-dessus.

> ⚠ **C'est une LIGNE DIRECTRICE, pas un plan imposé.** Ces textures, maquettes et coordonnées montrent l'**esprit** voulu (marbre ivoire et or comme la statue, 4 onglets, jauge or ↔ violet, forge à 3 losanges, malus en rouge) et donnent une **base de travail**. Tu es libre d'adapter la disposition, les tailles, les textes, de réorganiser ou de retoucher/redessiner les textures si l'ergonomie ou le code le demandent. Garde seulement l'ambiance générale et la lisibilité.

Textures de départ dans `textures/gui/seraph_shrine/` (256×256) : `offrandes.png`, `recolte.png`, `fusion.png`, `forge.png` (une par onglet, onglets dessinés en haut, l'onglet actif déjà fondu dans le panneau) + `widgets.png` (éléments dessinés par le code). Maquettes remplies : `docs/maquettes_sanctuaire/ecran_*.png`.

Coordonnées **indicatives** (celles des textures fournies) :

- Fenêtre : **176 × 234** (`imageWidth=176`, `imageHeight=234`). Les **onglets** occupent y 0..26, le **panneau** commence à **y = 24**. Toutes les coordonnées ci-dessous sont **relatives au panneau** (ajouter 24 en y pour la texture). Un emplacement fait 18×18 ; l'objet se dessine à +1,+1.
- **Onglets** (clic) : x = 4, 34, 64, 94 (largeur 28), y 0..26. Ordre : Offrandes, Récolte, Fusion, Forge.
- **Inventaire du joueur** (tous les onglets) : 3 rangées à x = 7 + 18·c, y = 124 + 18·r ; barre d'objets y = 182. Libellé « Inventaire » en (8, 113).
- Texte : titre de l'onglet en (8, 6), couleur `0x40341E`.

| Onglet | Élément | Position (x, y, l, h) |
|---|---|---|
| Offrandes | Emplacement d'offrande (serti d'or) | (24, 40) |
| | Bouton « Offrir » | (11, 66, 44, 14) |
| | Jauge pure ↔ corrompue | (64, 32, 104, 10) ; libellés « ✦ Pure » (64, 22) et « Corrompue ☠ » aligné à droite (168, 22) ; 5 repères de palier sous la jauge |
| | Zone d'état (nom de l'étape + points, production, prochain palier) | (64, 52, 104, 30) ; lignes à y +3 / +13 / +21 |
| | Message (dernière offrande) | (7, 92, 162, 16) |
| Récolte | Réserve Grâce / Souillure | (7, 20, 80, 64) / (89, 20, 80, 64) |
| | Emplacements de sortie (prise seulement) | (16, 30) / (98, 30) |
| | Barres de production | (12, 72, 70, 6) / (94, 72, 70, 6) |
| | Bouton « Récolter » | (56, 90, 64, 14) |
| Fusion | Entrée (9 requis) / Sortie | (36, 40) / (108, 40) |
| | Flèche (se remplit pendant la fusion) | (62, 44, 35, 10) |
| | Bouton « Fusionner » | (56, 70, 64, 14) |
| | Résumé (« 27 Éclats → 3 Fragments ») | (7, 92, 162, 16) |
| Forge | Équipement / Fragment | (14, 28) / (14, 60) |
| | 3 emplacements d'amélioration (losanges 12×12) | (46, 40), (62, 40), (78, 40) ; alignement écrit au-dessus (centré x = 62, y = 28), « 2 / 3 » dessous (y = 56) |
| | Liste des améliorations (nom en couleur du camp, bonus en vert, **malus en rouge**, coût du prochain emplacement) | (96, 20, 73, 66) |
| | Boutons « Retirer » / « Insérer » | (36, 90, 48, 14) / (100, 90, 64, 14) |

`widgets.png` :
| Élément | Position dans la texture |
|---|---|
| Remplissage de la jauge (or → neutre → violet, à dessiner entièrement : c'est le curseur qui bouge) | (0, 0, 104, 10) |
| Curseur de la jauge (pointe + trait) | (0, 12, 7, 13) — centré sur x = 64 + t·103, t = (600 − faith) / 1200 |
| Losange rempli doré / violet | (8, 12, 12, 12) / (20, 12, 12, 12) |
| Barre de production remplie or / violet | (32, 12, 70, 6) / (32, 19, 70, 6) |
| Bouton normal / survol / désactivé | (0, 28 / 43 / 58, 64, 14) — étirer en largeur si besoin |
| Flèche de fusion pleine | (0, 76, 35, 10) |
| Onglet inactif / actif | (64, 28, 28, 28) / (96, 28, 28, 28) |
| Icônes des onglets | (64 + 16·i, 60, 16, 16) |

Couleurs de texte : brun `0x40341E`, gris `0x504634`, or `0x966E14`, violet `0x7828A0`, bonus `0x287828`, malus `0xAA2828`.
