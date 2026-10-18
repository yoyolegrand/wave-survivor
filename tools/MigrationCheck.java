import com.wavesurvivor.config.ConfigMigration;

import java.nio.file.Path;

/**
 * OUTIL (hors jeu) : joue la migration vers les couches sur une COPIE d'un dossier de config et affiche le compte rendu.
 *   java -cp <classes + ressources + Forge> MigrationCheck <dossier wavesurvivor à migrer (copie !)>
 */
public class MigrationCheck {
    public static void main(String[] a) {
        ConfigMigration.runIfNeeded(Path.of(a[0]));
    }
}
