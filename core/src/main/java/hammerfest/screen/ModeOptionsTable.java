package hammerfest.screen;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import hammerfest.HammerfestGame;
import hammerfest.data.GameModes;
import hammerfest.data.QuestDef;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Options d'un mode de jeu (cases à cocher, grisées avec leur quête tant qu'elles ne sont pas débloquées) et terrain
 * de SoccerFest. Partagé par l'écran Jouer (partie locale) et le salon de l'hôte (partie en ligne).
 */
public class ModeOptionsTable extends Table {

    private final HammerfestGame game;
    private final Skin skin;
    private GameModes.Mode mode;
    private final Set<String> options = new LinkedHashSet<>();
    /** SoccerFest : terrain choisi (0-3), -1 = tiré au sort. */
    private int soccerMap = -1;

    public ModeOptionsTable(HammerfestGame game, Skin skin, Set<String> initialOptions) {
        this.game = game;
        this.skin = skin;
        for (String o : initialOptions) if (game.save.unlockedOptions.contains(o)) options.add(o);
    }

    /** Affiche les options de {@code m} ; celles d'un autre mode sont oubliées. */
    public void setMode(GameModes.Mode m) {
        mode = m;
        clearChildren();
        options.removeIf(o -> mode.options.stream().noneMatch(opt -> opt.id.equals(o)));
        if ("soccer".equals(mode.id)) addSoccerMaps();
        if (mode.options.isEmpty()) {
            add(new Label("Aucune option pour ce mode.", skin, "dim")).left();
            return;
        }
        for (GameModes.Option opt : mode.options) {
            boolean unlocked = game.save.unlockedOptions.contains(opt.id);
            CheckBox cb = new CheckBox(" " + opt.label + (unlocked ? "" : "  - " + lockText(game, opt.id)), skin);
            cb.getImageCell().padRight(6);
            cb.setChecked(unlocked && options.contains(opt.id));
            cb.setDisabled(!unlocked);
            cb.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (cb.isChecked()) options.add(opt.id); else options.remove(opt.id);
                }
            });
            add(cb).left().padBottom(4).row();
        }
    }

    /** Choix du terrain de SoccerFest (options set_soc_0..3 de l'original) : 4 terrains ou au hasard. */
    private void addSoccerMaps() {
        Table row = new Table();
        row.add(new Label("Terrain :", skin)).padRight(8);
        ButtonGroup<TextButton> maps = new ButtonGroup<>();
        maps.setMinCheckCount(1);
        for (int i = -1; i < 4; i++) {
            final int map = i;
            TextButton b = new TextButton(i < 0 ? "Au hasard" : String.valueOf(i + 1), skin, "small");
            maps.add(b);
            if (map == soccerMap) b.setChecked(true);
            b.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (b.isChecked()) soccerMap = map;
                }
            });
            row.add(b).width(i < 0 ? 110 : 40).height(26).padRight(4);
        }
        add(row).left().padBottom(8).row();
    }

    /** Options cochées (à mémoriser dans la sauvegarde). */
    public Set<String> checked() {
        return new LinkedHashSet<>(options);
    }

    /** Options à donner à la partie : cochées, plus le terrain de SoccerFest s'il est choisi. */
    public Set<String> chosen() {
        Set<String> out = checked();
        if (mode != null && "soccer".equals(mode.id) && soccerMap >= 0) out.add("set_soc_" + soccerMap);
        return out;
    }

    /** « quête "…" » qui débloque le mode ou l'option, sinon « verrouillé ». */
    static String lockText(HammerfestGame game, String optionOrMode) {
        QuestDef q = game.quests.unlockingQuest(optionOrMode);
        return q == null ? "verrouillé" : "quête \"" + game.data.lang.get(q.nameKey) + "\"";
    }
}
