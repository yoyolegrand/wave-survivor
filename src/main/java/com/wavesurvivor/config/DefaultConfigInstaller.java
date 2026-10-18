package com.wavesurvivor.config;

import net.minecraftforge.fml.loading.FMLPaths;

/**
 * POINT D'ENTRÉE DE LA CONFIGURATION AU DÉMARRAGE.
 *
 * Depuis la 1.7, le mod ne COPIE plus sa configuration d'origine chez le joueur : la base est lue dans le jar à chaque
 * lancement (ConfigLayers) et config/wavesurvivor/ ne contient que les ajouts et modifications du joueur. Les mises à
 * jour corrigent ainsi la base chez tout le monde sans jamais toucher à son contenu.
 * Les dossiers des versions précédentes (qui contenaient une copie de la base) sont migrés une fois (ConfigMigration).
 *
 * Pour revenir à la base d'un élément : bouton « Rétablir » de l'éditeur (ou supprimer son fichier dans config/wavesurvivor/).
 */
public final class DefaultConfigInstaller {

    private DefaultConfigInstaller() {}

    /** Migre un ancien dossier de config si besoin. À appeler avant tout chargement de configuration. */
    public static void installMissing() {
        ConfigMigration.runIfNeeded(FMLPaths.CONFIGDIR.get().resolve("wavesurvivor"));
    }
}
