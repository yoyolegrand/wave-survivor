# Objets du Kingdom des Sables — liste pour refaire le visuel

Les textures ci-dessous ont été **générées par script** (16 × 16, palette limitée) : ce sont des placeholders corrects mais à refaire proprement.
Chemins : `src/main/resources/assets/wavesurvivor/textures/item/<id>.png` (+ modèle `models/item/<id>.json`, type `item/generated`).
Thème : désert égyptien (grès, or, turquoise, lapis, cuivre), même style que la Voile du Désert (`voile_desert`).

## 1. Objets utilitaires du désert (vendus par l'Oracle des sables)

| Id | Nom | Utilité | Idée de visuel |
|---|---|---|---|
| `gourde_fraiche` | Gourde fraîche (Cool Waterskin) | Se boit : immunité au Soleil de plomb pendant 3 min (le Soleil fait perdre de la faim en continu). 3 émeraudes. | Gourde en cuir avec bouchon, quelques gouttes bleues, buée de fraîcheur. |
| `fiole_mirage` | Fiole de mirage (Mirage Vial) | Annule le prochain événement défavorable du désert (sables mouvants, soleil, nuée…). 18 émeraudes. | Fiole en verre au contenu violet/rose scintillant, léger halo « mirage ». |
| `tablette_reconnaissance` | Tablette de reconnaissance (Reconnaissance Tablet) | Révèle la composition du prochain Assaut, porte par porte. 12 émeraudes. | Tablette de grès gravée de hiéroglyphes, un œil/une flèche dorée au centre. |

## 2. Clés des coffres roulette du désert (une texture composée par coffre)

Les clés sont assemblées à partir de pièces (`textures/item/roulette_key/head_*`, `bit_*`, `outline_*`) : tête + panneton + matériau. À refaire si on veut des clés plus identifiables.

| Coffre (clé) | Tête / panneton / matériau | Couleur | Médaillon du coffre | Contenu (basique) |
|---|---|---|---|---|
| Reliquaire des Trésors / des Abysses / Arcanique | *existants (déjà dessinés pour l'autre Kingdom)* | | | contenu d'origine inchangé |
| Caravane des Vivres | anneau / simple / fer | vert `#6DBE45` | feuille | pain, mouton, melon, miel |
| Caravane des Armes | anneau / simple / fer | gris `#B8BCC4` | épées | fer, flèches, charbon, arc, bouclier |
| Caravane des Remèdes | anneau / simple / fer | turquoise `#2BB3A8` | cœur | émeraudes, potions de soin, fioles d'XP |

Au marché : les Reliquaires existants (Trésors, Abysses, Arcanique ; clés lâchées par les seigneurs Anubis, Sekhmet et Rê) et trois Caravanes simples (une clé au hasard à chaque Calme).

## 3. Symboles de coffre ajoutés (déjà installés)

`ankh`, `scarab`, `scorpion` dans `textures/roulette_symbols/large/` et `small/`.

## 4. Autres nouveautés 1.9 (hors Kingdom des Sables, pour mémoire)

Cahier du Survivant (`survivor_notebook`), Bourse à clés (`key_pouch`), Boussole des Vestiges (`vestige_compass`), nourritures sacrées/profanes
(`blessed_bread`, `sanctified_apple`, `celestial_mead`, `tainted_meat`, `void_fruit`, `black_wine`, `raw_heart`, `grace_feast`),
blocs (`grace_lantern`, `holy_fountain`, `votive_candle`, `bait_totem`, `profane_obelisk`, `reliquary_seal`, `blood_altar`),
armes (`dawn_blade`, `taint_scythe`, `blessed_arrow`, `tainted_arrow`), totems (`grace_totem`, `taint_totem`) et armure du Séraphin (`seraph_*`).
