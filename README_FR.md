# 🌊 Wave Survivor

**Un mod de défense contre des vagues pour Minecraft Forge 1.20.1.** Liez un Autel runique à une horde, défendez le Monolithe contre des vagues de monstres, affrontez des boss aux mécaniques uniques et repartez avec des reliques, des clés et du butin.

> *English: see [README.md](README.md).*

---

## ✨ Fonctionnalités

- **9 hordes prêtes à jouer** : un tutoriel et 4 campagnes à thème (Morts-vivants, Nether, End, Profondeurs), la plupart en version **Vanilla** et **Moddée**, plus une horde Iron's Spells.
- **Des boss avec de vraies mécaniques** : phases, boucliers, totems protecteurs, cristaux, pluies de météores…
- **Mécaniques de horde** : Défense du Monolithe, Brèches infernales, Anomalies gravitationnelles, événements du chaos (gisements à miner, totems, marchands du chaos, éclairs…).
- **Butin et progression** : coffres Reliquaires ouverts avec des clés, **15 reliques** (compatibles Curios), marchands entre les vagues, récompenses du classement de fin de horde, autel de **Renaissance**.
- **Éditeur complet en jeu** (`/ws editor`) : créez et réglez hordes, monstres, boss, compétences et butin sans toucher à un fichier.
- **Anglais et français**, au choix en jeu (`/ws lang en` / `/ws lang fr`).

---

## 📦 Prérequis

| | |
|---|---|
| **Minecraft** | 1.20.1 |
| **Forge** | 47.x ou plus récent |
| **Mods obligatoires** | aucun — les hordes Vanilla fonctionnent seules |

**Mods optionnels** (détectés automatiquement) :

| Mod | Ce qu'il débloque |
|---|---|
| **Curios API** | les reliques s'équipent dans les emplacements Curios (bague, collier, charme, ceinture…) |
| **Iron's Spells 'n Spellbooks** | la horde *Le Conclave Arcanique* + la compétence « Sort Iron's » pour les monstres custom |
| **L_Ender's Cataclysm**, **Dungeons & Combat**, **Alex's Caves**, **Travel Optics**, **Variants & Ventures**… | les versions **Moddées** des hordes |

S'il manque un mod pour une version, son bouton **Lancer** est grisé et les mods manquants sont listés.

---

## 🚀 Pour commencer

1. Récupérez un **Autel runique** (onglet créatif *Wave Survivor*, ou `/ws altar give`) et posez-le.
2. **Clic droit** dessus avec les ingrédients de liaison d'une horde (affichés sur l'écran de liaison) :

| Horde | Ingrédients de liaison |
|---|---|
| Tuto Vanilla | 1 lingot de fer + 1 os |
| La Nécropole | 1 crâne de squelette + 4 blocs d'os |
| Les Terres Brûlées | 1 bâton de blaze + 4 blocs de magma |
| Le Néant Éternel | 1 perle de l'Ender + 4 pierres de l'End |
| Horde des profondeurs | 1 cœur de la mer |
| Le Conclave Arcanique | 1 livre enchanté + 4 blocs d'améthyste |

3. Une **arène** se forme autour de l'autel (sol à thème, coffres de roulette).
4. **Clic droit** à nouveau sur l'autel → choisissez la version **Vanilla** ou **Moddée** → **Lancer**.

Entre deux vagues, une courte pause permet de se soigner, de réparer le Monolithe et de passer chez les marchands.

---

## 🗺 Les hordes

| Horde | Vagues | Mécaniques | Boss |
|---|---|---|---|
| **Tuto Vanilla** | 5 | vagues simples, événements du chaos | Zombie puissant |
| **La Nécropole** (Vanilla / Moddée) | 10 | Défense du Monolithe, résurrections, malédictions | Le Fossoyeur (v5) · **Morvhal, la Liche** (v10) |
| **Les Terres Brûlées** (Vanilla / Moddée) | 10 | Défense du Monolithe, **Brèches infernales** | Le Seigneur du Bastion (v5) · **Le Cavalier des Cendres** (v10) |
| **Le Néant Éternel** (Vanilla / Moddée) | 10 | Défense du Monolithe, Brèches, **Anomalies gravitationnelles** | Le Gardien Shulker / Le Golem de l'End (v5) · **Xâl'Tor, l'Œil du Néant** (v10) |
| **Horde des profondeurs** | 10 | monstres aquatiques (moddés) | Le Monstre des Profondeurs |
| **Le Conclave Arcanique** | 10 | mages d'Iron's Spells, Brèches arcaniques | L'Archévocateur Suprême (v5) · **Le Roi Mort** (v10) |

Des **vagues spéciales** remplacent parfois une vague normale (Charge des Hoglins, vagues Trésor pleines de clés, Cercle des Pyromanciens…).
**Classement** : en fin de horde, les 3 meilleurs joueurs et celui qui a tué le boss reçoivent des récompenses.

---

## ⚔ Mécaniques de horde

### 🗿 Défense du Monolithe
L'autel devient le **Monolithe**, avec une barre d'intégrité. S'il tombe, **la horde est perdue**.
- Les **Profanateurs / Briseurs / Dévoreurs** ignorent les joueurs et foncent sur lui : tuez-les en priorité.
- **Réparation** : clic droit avec l'objet de réparation de la horde (lingot d'or, perle de l'Ender, lapis…).
- **Boutique du Monolithe** (émeraudes) : Renfort, Régénération, Blindage.

### 🔥 Brèches
Des failles s'ouvrent autour du Monolithe et libèrent des **renforts** jusqu'à leur destruction. Détruire une Brèche rapporte une récompense ; une Brèche encore ouverte en fin de vague **renforce la horde**. Chaque horde peut avoir plusieurs **types de Brèches** (monstres, récompenses, apparence).

### ✦ Anomalies
Des cercles lumineux apparaissent sous les joueurs, deviennent **rouges** juste avant de s'activer, puis appliquent leurs effets (lévitation, lenteur, poison, attraction…). **Sortez du cercle !** Parades : chute lente, l'*Anneau d'Ancrage du Néant*…

### ⚡ Événements du chaos
Des événements aléatoires pendant la horde : apparitions de monstres, éclairs, **totems** à aura (Fureur, Régénération, Malédiction, Invocation), **gisements** à miner à la pioche (Fortune fonctionne !) et **marchands du chaos** qui vendent des objets rares et légendaires — dont la relique de la horde.

### 👥 Apparition des monstres
Les monstres arrivent **progressivement** (par lots) et uniquement sur des **positions sûres** : sol solide, assez de place pour leur taille, ni lave, ni eau, ni cactus, ni mur. Les admins peuvent vérifier une arène avec `/ws spawnzone`.

---

## 🎁 Butin et progression

### Reliquaires
Des **coffres de roulette** qui s'ouvrent avec la **clé** correspondante (clic droit avec la clé). **Clic gauche** sur un coffre pour voir son contenu.

| Reliquaire | Clés surtout obtenues dans |
|---|---|
| du Boss | les boss, le classement |
| des Trésors | La Nécropole, les vagues Trésor |
| du Nether | Les Terres Brûlées |
| de l'End | Le Néant Éternel |
| des Abysses | la Horde des profondeurs |
| Arcanique | Le Conclave Arcanique |

### Reliques (15)
Des objets uniques, actifs **dans l'inventaire** ou **équipés dans leur emplacement Curios**.

| Relique | Effet |
|---|---|
| Anneau d'Ancrage du Néant | immunité à la Lévitation, −25 % de dégâts de chute |
| Amulette du Brasier | immunité au feu et à la lave |
| Charme de Phylactère | immunité au Wither et au Poison |
| Œil du Veilleur | immunité Cécité/Ténèbres, monstres proches en surbrillance |
| Ceinture de Plomb | aucun recul |
| Os Sacré | +25 % de dégâts contre les morts-vivants |
| Éclat de Brèche | dégâts ×2 contre les Brèches, soin quand une Brèche est détruite |
| Marque du Chasseur | vos cibles sont en surbrillance |
| Cœur Assoiffé | +1 PV par monstre tué |
| Talisman du Dernier Souffle | survivre à un coup fatal par vague |
| Sceau du Gardien | le Monolithe subit −30 % de dégâts quand vous êtes proche |
| Bannière de Ralliement | alliés proches : +2 armure et régénération |
| Anneau du Prospecteur | gisements : coups doublés, +50 % de butin |
| Clé du Gardien | les monstres peuvent lâcher des clés de Reliquaire |
| Langue d'Argent | −25 % sur les prix des marchands |

### Renaissance
Un autel dédié où l'on dépense des **Points de Renaissance (PR)** en statistiques permanentes et en objets (ou dans un arbre de compétences si un mod compatible est présent).

---

## 🛠 Éditeur en jeu (`/ws editor`)

Tout se règle en jeu, sans éditer de fichier :
- **Hordes** : vagues, monstres, vagues spéciales, boss (et leurs **sbires** — stats, équipement, butin), pauses, marchands, événements du chaos, autel (liaison, arène, Défense du Monolithe, types de Brèches et d'Anomalies).
- **Entités et compétences** : monstres custom avec leurs propres stats, équipement, butin et **compétences illimitées** (charge, uppercut, éruption, puits d'éther, illusions… 28 types), chaque compétence étant **réglable par monstre**.
- **Iron's Spells** : donnez à n'importe quel monstre custom un **sort d'Iron's** (boule de feu, soin, invocation…) choisi dans une liste avec recherche.
- **Sélecteurs rapides** : choisissez une entité dans une **liste avec recherche** ou en cliquant sur son **œuf d'apparition** ; prenez les objets directement dans votre inventaire — **enchantements compris**.

Pensez à cliquer sur **💾 Enregistrer**.

---

## ⌨ Commandes

**Joueurs**

| Commande | |
|---|---|
| `/ws info` | horde en cours, vague et monstres restants |
| `/renaissance` | ouvrir le menu Renaissance |

**Opérateurs**

| Commande | |
|---|---|
| `/ws editor` | ouvrir l'éditeur en jeu |
| `/ws start <horde>` · `/ws stop` · `/ws skip` | lancer / arrêter une horde, passer à la vague suivante |
| `/ws lang <en\|fr>` | langue du mod (synchronisée pour tous les joueurs) |
| `/ws spawnzone [rayon]` | afficher les positions de spawn valides (particules vertes) |
| `/ws altar give` · `/ws altar bind <horde>` | outils de l'Autel runique |
| `/ws breach open [n] [style]` · `/ws breach close` | tester les Brèches |
| `/ws anomaly [n]` · `/ws totem [aura]` · `/ws gisement [taille]` | tester les Anomalies, totems et gisements |
| `/ws chaos [list\|n]` | lister / déclencher les événements du chaos |
| `/ws roulettechest …` | créateur de coffres de roulette |
| `/ws reload` | recharger la configuration |
| `/renaissance points add/set <joueur> <n>` | gérer les Points de Renaissance |
| `/renaissance reset <joueur>` | réinitialiser les stats permanentes et les achats de la boutique |

---

## ⚙ Configuration

Toute la configuration se trouve dans `config/wavesurvivor/`.
- Au **premier lancement**, le mod installe tout son contenu d'origine (hordes, entités, compétences, autels, Reliquaires, boutique Renaissance).
- Lors d'une mise à jour, les fichiers existants ne sont **jamais écrasés** : vos modifications sont conservées.
- Pour retrouver un fichier d'origine, **supprimez-le** puis relancez le jeu.
- Langue : `config/wavesurvivor/settings.json` (anglais par défaut, ou `/ws lang`).

---

## 📜 Licence et crédits

- **Licence** : code source sous [MIT](LICENSE) ; les ressources graphiques (textures, logo) sont **tous droits réservés**, voir [LICENSE-ASSETS](LICENSE-ASSETS)
- **Auteur** : yoyolegrand
