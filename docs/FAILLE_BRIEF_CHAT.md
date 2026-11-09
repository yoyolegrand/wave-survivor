# La Faille 2.0 — brief pour Claude Chat (visuels et contenus à produire)

Référence complète : `docs/DESIGN_FAILLE_2_0.md`. Le code est en place avec des **formes provisoires en blocs vanilla** ; Chat doit fournir les visuels et les contenus qui les remplaceront.

## Cotes actuelles à respecter (en blocs)
- **Île de départ** : rayon 16, qui grandit jusqu'à 32 (réglable, max 48), centre (0, 100, 0). Le Monolithe (bloc d'autel) est au centre.
- **Forteresse ennemie** (à l'est, côté +X) : pont de **33 de long et 15 de large**, plateforme de **rayon 39**, muraille de rayon ~31 et de 15 de haut avec une **porte de 13 de large** côté base, 4 tours de **9×9 haut de 27**, donjon **21×21 haut de 30** avec ouverture côté base. Les catalyseurs sont posés sur un cercle de rayon ~22, le cœur est dans le donjon.
- **Titan** : au **sud** de l'île (+Z), à ~145 blocs du centre, face au nord, ×110 pour un golem de fer (≈ 300 blocs de haut), il surgit de l'abîme. N'importe quelle entité peut servir de Titan.
- **Engins de siège** : sur le flanc sud de l'île, tournés vers le sud. Catapulte 2 × 6 au sol, 3 de haut. Baliste 3 × 3 au sol, 3 de haut.

## À produire
1. **Formes de forteresse** (plusieurs préréglages, tirage possible) : silhouette, chemin vers la base, points d'apparition des ennemis, emplacement du bouclier, du portail et des catalyseurs. Elles remplaceront la forteresse provisoire. Prévoir le gabarit ci-dessus (le chemin est volontairement long et large : on doit avoir l'impression de donner l'assaut).
2. **Premier Titan** : visuel + **storyboard de sa cinématique** d'invocation (bandes noires, caméra vers l'horizon, tremblements, surgit de l'abîme).
3. **Catapulte et baliste céleste** : modèles finaux, niveaux 1 et 2 (Renforcée / Consacrée), animations de tir et de rechargement (voir fiche, section 6.3).
4. **Variantes de niveau 3** des engins : catapulte Infernale / À fragmentation / Trébuchet de siège ; baliste Séraphique / Solaire / Du Jugement.
5. **Récompenses de fin** : coffre du Titan, trophée, éventuel objet unique par Faille.
6. **Ruines réparables** (bâtiments détruits) : visuel des vestiges.
7. **Visuels déjà validés à ranger dans `docs/faille/`** : `portail_faille_antre.png`, `portail_faille_antre_3d*.png`, `faille_animation_storyboard.png`, `faille_vide_anime.gif`, `catapulte.png`, `baliste_celeste.png`, `catapulte_animation.png`, `baliste_animation.png`.

## Ajouts depuis (mise à jour)
- **Icônes d'objet 16×16** (boutique) : « Catapulte de siège » et « Baliste céleste de siège » (aujourd'hui des icônes vanilla provisoires : `models/item/siege_catapult.json` et `siege_ballista.json`).
- **Vestiges de tour** (ruine réparable) : aujourd'hui des briques de deepslate fissurées ; visuel / bloc dédié à fournir.
- **Porte de la forteresse** (la Porte unique du Siège, devant la porte de la forteresse) : forme et thème visuel à valider.
- **Titan à découvert** (fenêtre de vulnérabilité) : effet visuel / animation à proposer (aujourd'hui : lueur et barre rouge).
- **Textes anglais** pour CurseForge / Modrinth : description de La Faille, entrée du CHANGELOG 2.0, légendes des captures.

## Pas encore codé côté Claude Code
- Achat de tours et bâtiments du Kingdom dans la Faille (dépend du moteur du Kingdom : plan à valider avec Yoann).
- Sauvegarde et reprise, améliorations des engins, fenêtres de vulnérabilité du Titan au corps à corps.
