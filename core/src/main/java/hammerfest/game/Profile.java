package hammerfest.game;

import hammerfest.data.QuestDef;
import hammerfest.quest.QuestBook;
import hammerfest.save.SaveData;

import java.util.List;
import java.util.Map;

/** Applique les résultats de partie à la sauvegarde (indépendant de libGDX, testable). */
public final class Profile {

    /** Le puits d'aventure compte 103 niveaux. */
    public static final int WELL_LEVELS = 103;

    /** @return les quêtes nouvellement terminées grâce aux objets ramassés */
    public static List<QuestDef> applyResult(SaveData save, QuestBook book, GameResult result) {
        if ("tutorial".equals(result.mode)) return book.update(save); // l'apprentissage ne compte pas (frigo, stats)
        save.stats.gamesPlayed++;
        save.stats.bestScore = Math.max(save.stats.bestScore, result.score);
        if (result.victory && result.timeMs > 0 && (save.stats.bestTime == 0 || result.timeMs < save.stats.bestTime)) {
            save.stats.bestTime = result.timeMs;
        }
        if ("adventure".equals(result.mode) || "multicoop".equals(result.mode)) {
            save.stats.maxLevel = Math.max(save.stats.maxLevel, Math.min(result.level, WELL_LEVELS));
        }
        for (Map.Entry<Integer, Integer> e : result.items.entrySet()) {
            save.addToFridge(e.getKey(), e.getValue());
        }
        return book.update(save);
    }

    private Profile() {
    }
}
