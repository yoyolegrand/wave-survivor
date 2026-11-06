# Kingdom des Sables — « L'Oasis des Pharaons » (1.9)

> Conçu par Yoann et Claude chat (structure, horde, marchands et coffres validés dans le principe). Reprend les **mécaniques et les entités de la horde « Les Sables Ardents »** (`defaults/wavesurvivor/additions/sables_ardents.json`) en **mode Kingdom**, sur une arène dédiée. Arène : `docs/arenes/oasis_des_pharaons/` (plan + fiche de pose).

---

## 1. L'arène (rappel — détails dans `ARENE_OASIS_DES_PHARAONS.md`)
- 181 × 181, mêmes mesures que le Val de la Couronne : Monolithe au centre (place libre de 7), royaume 16 → 32, **rempart de grès** à 34 (4 ouvertures, 4 tours d'angle), intérieur **vide** (les joueurs construisent).
- **3 portes fixes** à 68 blocs (14 blocs libres chacune), une par **seigneur** :
  - **nord — Tombeau d'Anubis** (pyramide) ; **est — Nid de Sekhmet** (crête rocheuse, terriers) ; **sud — Temple de Rê** (colonnes, disque solaire).
- **Ouest — le Marché** (pas de porte) : puits, 4 étals, tentes ; **apparition des marchands** pendant le Calme et des **coffres roulettes** (repères `market.merchant_spawns`, `market.roulette_chest_spawns`).
- **Ressources** : bois = palmiers (grande oasis, petite oasis, palmeraie) ; pierre / fer / charbon = carrière rocheuse ; eau = lacs des oasis.

## 2. La horde Kingdom « Les Sables »
Utiliser **les entités du mod** de la horde Sables Ardents. **Pas de zombies** : des **husks** (Momies). Archers : squelettes (« Archer des dunes ») — option : un **Archer momifié** (squelette aux bandages, nouvelle texture à faire par Claude chat si Yoann le souhaite).

### 2.1 Troupes par porte (marches)
| Porte | Troupes de base | Troupes d'élite (à partir du royaume niv. 3) | Seigneur |
|---|---|---|---|
| **Nord — Anubis** | Momies (`husk`), Pilleurs de tombes, Scarabées (`wavesurvivor:scarab`) | **Gardes du Pharaon** (`wavesurvivor:royal_guard`, garde royal momifié), Archers des dunes | **Anubis, Gardien des Tombes** (charge, frappe, momies, malédiction, pesée de l'âme, enterré vivant, fracture) |
| **Est — Sekhmet** | Scorpions (`wavesurvivor:scorpion`), Scarabées, Chacals des dunes (`wavesurvivor:jackal`) | **Chacals Alpha** (bond), Scorpions géants (Scorpion à PV ×2) | **Sekhmet, Reine des Scorpions** (aiguillon, épines, sables mouvants, nuée, enfouissement, coup de queue, soif de sang, toile de sable) |
| **Sud — Rê** | Archers des dunes, Cavaliers des dunes, Momies | **Golems de grès** (`wavesurvivor:sandstone_golem`), **Djinns** (`wavesurvivor:djinn`, orbes + disparition) | **Rê-Atoum, l'Ancien des Sables** (tempête, tourbillon, séisme, mirage, rayon solaire, geysers, puits, barrière, gardes de sable) |
- **Profanateurs des Sables** (`targetsAltar`) : à partir du royaume niv. 2, 1 à 3 par marche, depuis **n'importe quelle porte**, foncent sur le **Monolithe** (comme dans la horde).
- **Progression** : la taille et la composition des marches suivent le niveau du royaume et le numéro de la marche (même logique que les pillards) ; les **grandes marches** (toutes les N marches, réglable) sont **menées par le seigneur** de la porte, une porte à la fois, en tournant (Anubis → Sekhmet → Rê).
- Reprendre les PV / dégâts / compétences des entités du JSON Sables Ardents, avec le scaling Kingdom habituel.

### 2.2 Événements du désert (chaos Kingdom)
| Événement | Effet | Quand |
|---|---|---|
| **Tempête de sable** | brouillard de sable (vue réduite hors des murs), marches **+20 % de vitesse** pendant 60 s | pendant une marche |
| **Sables mouvants** | 3 à 5 zones de sable mouvant près des portes : ralentissement fort + enlisement (joueurs **et** monstres) | marche ou Calme |
| **Soleil de plomb** | en plein jour, hors de l'ombre (pas de bloc au-dessus), la **faim** baisse 2× plus vite : pousse à construire des abris | Calme, de jour |
| **Nuée de scarabées** | une nuée surgit du sable **près des réserves / coffres** du royaume | Calme |
| **Mirage** | de fausses troupes apparaissent au loin (se dissipent à l'approche, aucun dégât) | marche |
| **Crue de l'oasis** *(favorable)* | les oasis donnent un bonus : arbres qui repoussent, +récoltes, marchands plus généreux (-10 %) pendant un Calme | Calme |
Tous réglables (activé, fréquence, durée, intensité) dans l'éditeur du chaos.

### 2.3 Arbres et minerais du chaos — nouveaux styles
- **Nouveau réglage « Forme des arbres »** dans **tous** les éditeurs qui font apparaître des arbres (chaos, horde, Kingdom, arènes…) : **Classique** (actuel) ou **Oasis** (palmier). Le palmier : tronc courbé de 6 à 9 blocs en `jungle_log` avec anneaux de `stripped_jungle_log`, courbure aléatoire (s'incline à partir de la moitié), couronne de **8 palmes** (`jungle_leaves`) qui montent puis retombent, noix de coco (`brown_terracotta`, ou cacao) sous la couronne. Algorithme de référence : `docs/arenes/oasis_des_pharaons/palm.py` ; visuel : `palmiers_oasis.png`.
- **Nouveau réglage « Style des minerais »** pour les minerais qui apparaissent avec le chaos : **Classique** ou **Désert** (le minerai sort du sable dans un petit rocher de grès / pierre / andésite, comme la carrière).
- Pour ce Kingdom : **Oasis** et **Désert** par défaut.

## 3. Le Marché — 4 marchands (pendant le Calme)
Apparaissent aux 4 `merchant_spawns` (devant les étals). Monnaie : celle des marchands du mod (émeraudes par défaut). Stocks et prix réglables.
| Marchand | Ce qu'il vend |
|---|---|
| **L'Épicière de l'oasis** (nourriture) | pain, poisson cuit, pastèque, ragoût, miel, pommes ; **Gourde d'eau fraîche** (annule le Soleil de plomb pour 3 min) ; rarement Pain béni |
| **L'Armurier des dunes** (équipement) | armes et armures en fer / or (parfois enchantées), boucliers, arcs, flèches, Flèches bénies / souillées ; rarement le **Voile du désert** (relique) |
| **Le Négociant** (ressources) | bûches de palmier, pierre, fer, charbon, grès, verre, seaux d'eau, terre, graines |
| **L'Oracle des sables** (spécial) | **clés** des coffres du désert ; **Tablette de reconnaissance** (révèle la porte et la composition de la prochaine marche) ; **Fiole de mirage** (annule le prochain événement défavorable du désert) ; échange Éclats ↔ Fragments ; très rarement une **Clé scellée** |

## 4. Coffres roulettes du désert (nouveau système personnalisable)
Apparaissent aux 6 `roulette_chest_spawns` du marché : emplacements 1 à 3 = les **3 reliquaires des seigneurs**, 4 à 6 = **Coffres de la Caravane**. Visuel : `docs/coffres_roulette/coffres_desert.png`. Nouveaux symboles de la bibliothèque : **ankh**, **scarab**, **scorpion** (grand 10 × 10 + petit 6 × 6, fournis dans `textures/roulette_symbols/`).
| Coffre | Bois / ferrures | Accent / gemme | Médaillon avant | Clé (tête / panneton / matière) | Particules repos → roulette | Butin (idée) |
|---|---|---|---|---|---|---|
| **Reliquaire d'Anubis** | ébène / or | lapis `#2F6FD8` | grand **ankh** or + petit **eye** lapis | crâne / double / or | âmes en cercle → vortex d'âmes | or, lapis, bandages / Éclats de souillure, relique du désert rare |
| **Reliquaire de Sekhmet** | acacia / cuivre | rouge `#E04A32` | grand **scorpion** rouge | étoile / crochet / cuivre | spores cramoisies → double hélice rouge | yeux d'araignée, fils, potions de poison / force, émeraudes |
| **Reliquaire de Rê** | bouleau / or | orange solaire `#FFB52E` (gemme `#FF7A1E`) | grand **sun** or + petit **scarab** lapis | ailée / double / or | étincelles dorées (dust) → colonne de lumière | or, blocs d'or rares, Éclats / Fragments de grâce, Voile du désert |
| **Coffre de la Caravane** | épicéa / fer | turquoise `#2BB3A8` | grand **gem** turquoise | anneau / simple / fer | poussière turquoise → feu d'artifice | ressources, nourriture, émeraudes (coffre courant) |
- **Clés** : les troupes de chaque porte lâchent la clé de **leur** seigneur (taux réglable ; le seigneur en lâche à coup sûr) ; la clé de la Caravane : récompense de chaque Calme réussi + vendue par l'Oracle. Les clés vont dans la **Bourse à clés**.
- Animations : celles du nouveau système (cadran qui tourne, couvercle, faisceau de rareté) — rien de spécial à coder en plus.

## 5. Ordre conseillé
1. Pose de l'arène + Monolithe + 3 portes fixes + marché (repères).
2. Horde Kingdom « Les Sables » (troupes par porte, seigneurs, Profanateurs).
3. Marchands et coffres du marché.
4. Événements du désert.
5. Styles « Oasis » (arbres) et « Désert » (minerais) dans les éditeurs.
Build, captures et commit à chaque étape.
