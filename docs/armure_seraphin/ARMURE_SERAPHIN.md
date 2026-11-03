# Armure du Séraphin — intégration dans Wave Survivor (1.9)

> Rédigée par Claude chat avec Yoann. **Géométrie et texture 100 % originales** (créées pour Wave Survivor) : rien ne vient de *Dungeons and Combat* (le modèle « model armor holy » qui servait de support d'essai NE doit PAS être intégré : sa géométrie appartient à Tohirogosu, licence « tous droits réservés »).

## 1. Fichiers déjà prêts
- `src/main/java/com/wavesurvivor/client/armor/SeraphArmorModel.java` — modèle 3D (74 pièces), généré automatiquement : **ne pas retoucher les boîtes à la main**.
  - `LAYER` (`wavesurvivor:seraph_armor#main`), `TEXTURE`, `createBodyLayer()` (UV déclarées en 256 × 64).
  - `forSlot(EquipmentSlot)` : n'affiche que les pièces de l'emplacement porté (HEAD = heaume, ailes, halo ; CHEST = plastron, tabard, écharpe, ailes dorsales, épaulières, brassards ; LEGS = cuissards + genouillères ailées ; FEET = solerets).
  - Les tailles non entières sont faites avec des boîtes entières + `CubeDeformation` pour que le patron UV tombe exactement sur la texture.
- `src/main/resources/assets/wavesurvivor/textures/models/armor/seraph_armor.png` — texture **512 × 128** (2 px par unité ; c'est voulu : Minecraft la lit avec les UV 256 × 64).
- `docs/armure_seraphin/apercu.png` — rendu de référence (face, 3/4, dos).

## 2. À faire
1. **Couche du modèle** : enregistrer `SeraphArmorModel.LAYER` avec `SeraphArmorModel::createBodyLayer` dans `EntityRenderersEvent.RegisterLayerDefinitions` (côté client, comme les autres modèles du mod).
2. **Matériau d'armure** `SERAPH` (proposition, à équilibrer) : durabilité ×35, protection casque 3 / plastron 8 / jambières 6 / bottes 3, robustesse 2.5, recul 0.05, enchantabilité 18, son d'équipement doré, réparation avec un **Fragment de grâce**.
3. **4 objets** `seraph_helmet`, `seraph_chestplate`, `seraph_leggings`, `seraph_boots` (`ArmorItem`) :
   - `initializeClient` → `IClientItemExtensions.getHumanoidArmorModel(...)` : renvoyer le modèle (créé une fois depuis `SeraphArmorModel.LAYER`) avec `forSlot(slot)`, en recopiant la pose du modèle d'origine (`original.copyPropertiesTo(model)`).
   - `getArmorTexture(...)` → `SeraphArmorModel.TEXTURE.toString()`.
   - Rareté épique, noms FR / EN (« Heaume du Séraphin », « Plastron du Séraphin », « Jambières du Séraphin », « Solerets du Séraphin »), onglet créatif.
4. **Obtention** : fabrication dans l'onglet **Artisanat** du Sanctuaire (voir `docs/DESIGN_ARTISANAT.md`), alignement **Consacré** uniquement. Proposition : la pièce de diamant correspondante + Fragments de grâce (casque 4, plastron 6, jambières 5, bottes 3) + 1 Cœur de grâce pour le plastron. Compatible avec la forge du Sanctuaire (améliorations Consacrées seulement).
5. **Bonus d'ensemble** (les 4 pièces, proposition) : *Bénédiction du Séraphin* — -15 % de dégâts subis des morts-vivants et des Corrompus, et le halo du heaume luit faiblement (calque lumineux à prévoir plus tard).
6. **Icônes** des 4 objets : à générer par Claude chat (PixelLab, 32 × 32) ; en attendant, utiliser les icônes du diamant.
7. Textes FR / EN, page du Codex, CHANGELOG.

## 3. Vérifications en jeu
- Porter chaque pièce seule puis l'ensemble : rien ne doit flotter ni se décaler, la visière dessinée doit être face à l'avant, les ailes dans le dos.
- En marchant et en courant, les épaulières et genouillères suivent bien les bras et les jambes.
- Avec une armure vanilla sur un emplacement et le Séraphin sur un autre, les deux rendus cohabitent.
