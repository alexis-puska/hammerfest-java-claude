package hammerfest.screen;

import hammerfest.ui.Tr;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import hammerfest.HammerfestGame;
import hammerfest.game.Profile;
import hammerfest.save.SaveData;

/** Statistiques : meilleur score, niveau max du puits, parties, objets, quêtes. */
public class StatsScreen extends MenuScreen {

    public StatsScreen(HammerfestGame game) {
        super(game);
        Table panel = panel(Tr.t("Statistiques"));
        SaveData s = game.save;
        row(panel, Tr.t("Meilleur score"), String.valueOf(s.stats.bestScore));
        row(panel, Tr.t("Niveau max atteint (puits)"), s.stats.maxLevel + " / " + Profile.WELL_LEVELS);
        row(panel, Tr.t("Parties jouées"), String.valueOf(s.stats.gamesPlayed));
        row(panel, Tr.t("Meilleur temps (Time Attack)"), s.stats.bestTime > 0 ? hammerfest.sim.Game.formatTime(s.stats.bestTime) : "-");
        row(panel, Tr.t("Objets ramassés"), String.valueOf(s.stats.itemsCollected));
        row(panel, Tr.t("Objets différents au frigo"), s.fridge.size() + " / " + game.data.items.size());
        row(panel, Tr.t("Quêtes terminées"), s.completedQuests.size() + " / " + game.data.quests.size());
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
