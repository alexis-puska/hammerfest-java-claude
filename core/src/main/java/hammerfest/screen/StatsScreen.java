package hammerfest.screen;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import hammerfest.HammerfestGame;
import hammerfest.game.Profile;
import hammerfest.save.SaveData;

/** Statistiques : meilleur score, niveau max du puits, parties, objets, quêtes. */
public class StatsScreen extends MenuScreen {

    public StatsScreen(HammerfestGame game) {
        super(game);
        Table panel = panel("Statistiques");
        SaveData s = game.save;
        row(panel, "Meilleur score", String.valueOf(s.stats.bestScore));
        row(panel, "Niveau max atteint (puits)", s.stats.maxLevel + " / " + Profile.WELL_LEVELS);
        row(panel, "Parties jouées", String.valueOf(s.stats.gamesPlayed));
        row(panel, "Meilleur temps (Time Attack)", s.stats.bestTime > 0 ? hammerfest.sim.Game.formatTime(s.stats.bestTime) : "-");
        row(panel, "Objets ramassés", String.valueOf(s.stats.itemsCollected));
        row(panel, "Objets différents au frigo", s.fridge.size() + " / " + game.data.items.size());
        row(panel, "Quêtes terminées", s.completedQuests.size() + " / " + game.data.quests.size());
        panel.add().expandY().row();
        addBackButton(panel);
    }

    private void row(Table panel, String name, String value) {
        Table r = new Table();
        r.add(label(name, "default")).left().expandX();
        r.add(label(value, "accent")).right();
        panel.add(r).width(540).padBottom(8).row();
    }
}
