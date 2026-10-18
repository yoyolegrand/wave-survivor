# Couches de configuration (1.7)

## Principe
- **Base** : ce que le mod livre, dans `src/main/resources/defaults/wavesurvivor/`. Relue **à chaque démarrage** depuis le jar
  (`ConfigLayers.builtin()`). Corriger la base = corriger tous les joueurs.
- **Joueur** : `config/wavesurvivor/*.json`, uniquement ses ajouts, ses modifications et ses suppressions.

| Fichier du joueur | Contenu |
|---|---|
| `custom_hordes.json` | `hordes` (créées par le joueur, complètes) · `patches` (modifications d'une horde de base : champs changés seulement) · `deleted` |
| `custom_entities.json` | `entities` / `skills` (créées) · `entityPatches` / `skillPatches` · `deletedEntities` / `deletedSkills` |
| `altar_recipes.json` | autel créé (complet) · `{ "__patch": {…} }` (modification d'un autel de base) · `__deleted` |
| `custom_roulette_chests.json` | `chests` (créés ou modifiés, entiers) · `deleted` |
| `renaissance_shop_custom.json` | `items` (créés ou modifiés) · `disabled` (masqués en plus de la base) · `enabled` (réactivés) |
| `renaissance_rarities.json` | raretés qui diffèrent de la base ; `"__reset__"` annule une rareté de la base |

Patch (`Patch.java`) : objets fusionnés champ par champ ; listes et valeurs simples remplacées en bloc ; `null` = champ supprimé.
Un champ à `null` équivaut à un champ absent (`Canon.java`).

## Livrer du contenu ou un correctif d'équilibrage
- **Corriger une valeur d'un élément existant** : modifier directement les fichiers de `defaults/wavesurvivor/` (`config.json`,
  `custom_hordes.json`, `custom_entities.json`, `altar_recipes.json`…). Les joueurs le reçoivent à la mise à jour, sauf pour
  les champs qu'ils ont eux-mêmes modifiés.
- **Ajouter une horde / entité / compétence / autel** : créer un fichier `additions/<nom>.json` et l'ajouter dans
  `ConfigLayers.ADDITIONS` (même format qu'avant ; plus de marqueur « appliqué une fois », la base est reconstruite à chaque lancement).
- **Ne pas supprimer** un élément de base dont les joueurs peuvent dépendre sans le dire dans le changelog : il disparaîtrait chez eux.

## Migration depuis ≤ 1.6 (`ConfigMigration`)
Jusqu'en 1.6, le mod copiait sa base chez le joueur. Au premier lancement en 1.7 :
1. copie de sécurité du dossier dans `config/wavesurvivor_backup_avant_couches` ;
2. pour chaque élément du joueur : on retrouve la version livrée la plus proche (`legacy_base.json` + base actuelle) ;
   identique → retiré (suit le mod) ; modifié → seul le **patch** est gardé ; créé par le joueur → gardé entier ;
3. l'ancien `config.json` n'est plus lu (ce qu'il contenait de modifié est repris) ; marqueur `config_layers.txt`.

### `legacy_base.json` : à régénérer à chaque version publiée
Contient toutes les versions livrées des éléments de base, pour reconnaître ce qu'un joueur a reçu au départ. Après avoir
publié une version (tag `vX.Y.Z`), la régénérer en ajoutant ce tag à la liste :

```
java -cp <classes du mod + ressources + jar Forge mappé + gson> GenLegacyBase <racine du dépôt> \
     src/main/resources/defaults/wavesurvivor/legacy_base.json v1.5.0 v1.6.0 vX.Y.Z WORKTREE
```
(`tools/GenLegacyBase.java`.) Sans cette régénération, les joueurs qui migrent depuis la nouvelle version sont tout de même
traités sans perte (leurs éléments sont gardés comme modifications), mais les éléments non touchés ne suivraient pas la base.

## Outils de vérification (hors jeu, dans `tools/`)
- `MigrationCheck <copie d'un dossier wavesurvivor>` : joue la migration sur une **copie** et affiche le compte rendu ;
- `com.wavesurvivor.config.LayerCheck <dossier migré> <dossier de sauvegarde>` : compare la config effective avant / après
  (seules les mises à jour de la base doivent apparaître) ;
- `com.wavesurvivor.config.PatchRoundTrip` : `apply(base, diff(base, modifié)) = modifié` sur toute la base.
