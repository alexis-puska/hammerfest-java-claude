package hammerfest.game;

import java.util.Map;
import java.util.TreeMap;

/** Bilan d'une partie, transmis au menu pour alimenter frigo, statistiques et quêtes. */
public class GameResult {
    public final String mode;
    public final long score;
    /** Niveau le plus profond atteint pendant la partie. */
    public final int level;
    /** Objets ramassés : itemId → quantité. */
    public final Map<Integer, Integer> items;
    /** Partie terminée par la sortie de fin (ascenseur), et non par un game over. */
    public boolean victory;
    /** Time Attack : temps total (ms) ; Game.TA_INFINITY si le parcours n'est pas terminé ; -1 hors Time Attack. */
    public long timeMs = -1;
    /** Résultat à afficher à la place du score (SoccerFest : « Igor 2 - 1 Sandy »), ou null. */
    public String scoreText;

    public GameResult(String mode, long score, int level, Map<Integer, Integer> items) {
        this.mode = mode;
        this.score = score;
        this.level = level;
        this.items = new TreeMap<>(items);
    }
}
