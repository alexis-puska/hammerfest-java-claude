package hammerfest.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import hammerfest.HammerfestGame;
import hammerfest.ui.Ui;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;

/**
 * Lanceur desktop. Lancé depuis le jar (double-clic sur Hammerfest.jar), la sauvegarde {@code save.json} et
 * l'éventuel journal d'erreur sont écrits à côté du jar ; lancé par Gradle ({@code lwjgl3:run}), dans le
 * dossier de travail ({@code assets/}).
 */
public class Lwjgl3Launcher {

    public static void main(String[] args) {
        File dir = jarDirectory();
        if (dir != null) System.setProperty(HammerfestGame.SAVE_DIR_PROPERTY, dir.getAbsolutePath());
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> logCrash(dir, e));
        try {
            Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
            config.setTitle("Hammerfest");
            config.setWindowedMode((int) Ui.WIDTH, (int) (Ui.WIDTH * (hammerfest.render.LevelRenderer.DOC_HEIGHT
                    + hammerfest.screen.GameView.BAND_HEIGHT) / hammerfest.render.LevelRenderer.DOC_WIDTH));
            config.setResizable(true);
            config.useVsync(true);
            config.setForegroundFPS(60);
            // icône de la fenêtre et de la barre des tâches : Igor (assets/icons)
            config.setWindowIcon(com.badlogic.gdx.Files.FileType.Internal,
                    "icons/igor-128.png", "icons/igor-64.png", "icons/igor-32.png", "icons/igor-16.png");
            new Lwjgl3Application(new HammerfestGame(new UpnpPortService()), config);
        } catch (Throwable e) {
            logCrash(dir, e);
            throw e;
        }
    }

    /** Dossier du jar exécutable, ou null hors d'un jar (lancement par Gradle ou l'IDE). */
    static File jarDirectory() {
        try {
            File f = new File(Lwjgl3Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return f.isFile() && f.getName().toLowerCase().endsWith(".jar") ? f.getParentFile() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Sans console (double-clic), les erreurs sont écrites dans hammerfest-erreur.log. */
    static void logCrash(File dir, Throwable e) {
        File log = new File(dir != null ? dir : new File("."), "hammerfest-erreur.log");
        try (PrintWriter w = new PrintWriter(new FileWriter(log, true))) {
            w.println("=== " + LocalDateTime.now() + " ===");
            e.printStackTrace(w);
        } catch (Exception ignored) {
            // rien d'autre à faire
        }
        e.printStackTrace();
    }
}
