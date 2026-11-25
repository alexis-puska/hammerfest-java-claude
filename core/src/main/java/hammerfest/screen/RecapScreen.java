package hammerfest.screen;

import hammerfest.ui.Tr;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import hammerfest.HammerfestGame;
import hammerfest.data.QuestDef;
import hammerfest.game.GameResult;

import java.util.List;
import java.util.Map;

/** Récapitulatif de fin de partie : score, niveau, objets ajoutés au frigo, quêtes terminées. */
public class RecapScreen extends MenuScreen {

    public RecapScreen(HammerfestGame game, GameResult result, List<QuestDef> completed) {
        super(game);
        Table panel = panel(result.victory ? Tr.t("Victoire !") : Tr.t("Fin de partie"));
        if (result.victory && result.timeMs < 0) {
            panel.add(label(Tr.t("Igor a libéré les fruits et quitté les cavernes par l'ascenseur."), "ok")).padBottom(8).row();
        }
        if (result.scoreText != null) {
            panel.add(label(result.scoreText, "heading")).padBottom(4).row();
        } else if (result.timeMs >= 0) {
            panel.add(label(result.victory ? Tr.t("Temps : ") + hammerfest.sim.Game.formatTime(result.timeMs)
                    : Tr.t("Parcours non terminé"), "heading")).padBottom(4).row();
        } else {
            panel.add(label(Tr.t("Score : ") + result.score, "heading")).padBottom(4).row();
        }
        panel.add(label(Tr.t("Niveau atteint : ") + result.level, "default")).padBottom(12).row();

        panel.add(label(Tr.t("Objets ajoutés au frigo"), "heading")).padBottom(4).row();
        if (result.items.isEmpty()) panel.add(label(Tr.t("Aucun"), "dim")).row();
        for (Map.Entry<Integer, Integer> e : result.items.entrySet()) {
            panel.add(label(game.data.itemName(e.getKey()) + "  x" + e.getValue(), "default")).row();
        }

        if (!completed.isEmpty()) {
            panel.add(label(Tr.t("Quêtes terminées !"), "heading")).padTop(12).padBottom(4).row();
            for (QuestDef q : completed) panel.add(label(game.data.lang.get(q.nameKey), "ok")).row();
        }
        panel.add().expandY().row();
        addBackButton(panel);
    }
}
