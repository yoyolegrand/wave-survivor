# Horde des Sables Ardents — entités à créer (pour Claude Chat)

La horde est déjà jouable : `src/main/resources/defaults/wavesurvivor/additions/sables_ardents.json`.
Les 5 entités propres ci-dessous y sont **provisoires** (base vanilla). Pour chaque entité : classe dans `entity/`, rendu dans `client/`,
enregistrement dans `ModEntities` + `ClientModEvents`, œuf dans `ModItems`, noms dans `lang/fr_fr.json` + `en_us.json`,
texture en **recoloration** de la texture vanilla (même patron UV), comme pour le Golem de givre, le Sorcier des neiges et le Ravageur de givre.
Entités **hostiles** (implémentent `Enemy`) ; elles ne doivent pas s'attaquer entre elles (voir `FrostRavager` pour l'exemple).

| Id proposé | Remplace (provisoire) | Modèle à recolorer | Rôle / comportement | Stats actuelles |
|---|---|---|---|---|
| `wavesurvivor:scarab` | `minecraft:silverfish` | Artésien / Endermite | Petit, très rapide, par dizaines, mord. Peut s'enfouir (visuel) | PV 8, dégâts 3, vitesse 0,36 |
| `wavesurvivor:jackal` | `minecraft:wolf` | Loup (pelage sable, oreilles dressées) | Chasse en meute, rapide. Sert aussi au Chacal Alpha (plus grand) | PV 22, dégâts 5, vitesse 0,40 (Alpha : 90 / 9 / 0,42) |
| `wavesurvivor:scorpion` | `minecraft:spider` | Araignée (queue recourbée si possible) | Poison au contact ; porte la compétence « Dard du scorpion ». Sekhmet (boss) utilise la même base en grand | PV 34, dégâts 5, vitesse 0,30 |
| `wavesurvivor:djinn` | `minecraft:evoker` | Évocateur / Vex (turban, bas du corps en volutes de sable) | Lanceur de sorts, garde ses distances (10 blocs), flotte légèrement. Orbes de sable + disparition | PV 52, dégâts 4, vitesse 0,50 |
| `wavesurvivor:sandstone_golem` | `minecraft:ravager` | Golem de fer (grès, hiéroglyphes) | Gros bloc de grès lent et très résistant (recul 100 % bloqué). Sert aussi de base à Rê-Atoum (boss, très agrandi) | PV 150, dégâts 13, vitesse 0,21 (Rê-Atoum : 1000 / 24 / 0,30) |

## Pour remplacer les entités provisoires
Dans `sables_ardents.json` (et dans la config de test, via l'éditeur), changer `baseEntityType` des entités custom :
`scarabee`, `chacal`, `chacal_alpha`, `scorpion`, `sekhmet_reine`, `djinn`, `golem_gres`, `re_atoum_ancien`.
Ne pas régénérer ce fichier à la main si la config de test a été retouchée : régler dans l'éditeur puis exporter.

## Relique « Voile du Désert »
Icône 2D 16×16 à refaire : `src/main/resources/assets/wavesurvivor/textures/item/voile_desert.png` (provisoire : voile de tissu sable, fermoir doré).
Style des autres reliques (PixelLab) ; c'est une relique « Tête » de la famille Éléments.
