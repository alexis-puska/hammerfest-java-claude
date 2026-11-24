package hammerfest.screen;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import hammerfest.HammerfestGame;
import hammerfest.data.GameModes;
import hammerfest.data.QuestDef;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Jouer : choix du mode et des options ; ce qui n'est pas débloqué est grisé avec la quête correspondante. */
public class PlayScreen extends MenuScreen {

    private GameModes.Mode mode;
    private final Set<String> options = new LinkedHashSet<>();
    private final Table optionsTable = new Table();
    private final List<CheckBox> checkBoxes = new ArrayList<>();
    /** SoccerFest : terrain choisi (0-3), -1 = tiré au sort. */
    private int soccerMap = -1;

    public PlayScreen(HammerfestGame game) {
        super(game);
        Table panel = panel("Jouer");

        panel.add(label("Mode de jeu", "heading")).padBottom(6).row();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(1);
        GameModes.Mode last = GameModes.mode(game.save.lastMode);
        mode = isUnlocked(last) ? last : GameModes.MODES.get(0);
        for (GameModes.Mode m : GameModes.MODES) {
            boolean unlocked = isUnlocked(m);
            String text = m.label + (unlocked ? "" : "  - " + lockText(m.unlockMode));
            TextButton b = new TextButton(text, skin, "small");
            b.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
            b.getLabelCell().padLeft(10);
            b.setDisabled(!unlocked);
            group.add(b);
            if (m == mode) b.setChecked(true);
            b.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (b.isChecked() && mode != m) {
                        mode = m;
                        options.clear();
                        rebuildOptions();
                    }
                }
            });
            panel.add(b).width(560).height(30).padBottom(4).row();
        }

        for (String o : game.save.lastOptions) {
            if (game.save.unlockedOptions.contains(o)) options.add(o);
        }

        panel.add(label("Options", "heading")).padTop(12).padBottom(6).row();
        panel.add(optionsTable).left().row();
        if (game.save.unlockedOptions.contains(GameModes.PASSIVE_BOOST)) {
            panel.add(label("Tornade (boost de vitesse en début de partie) : toujours active.", "ok")).padTop(6).row();
        }
        rebuildOptions();

        panel.add().expandY().row();
        Table buttons = new Table();
        buttons.add(button("Lancer (Entrée)", this::launch)).width(200).padRight(16);
        buttons.add(button("Retour", this::back)).width(200);
        panel.add(buttons).center();

        stage.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean keyDown(com.badlogic.gdx.scenes.scene2d.InputEvent event, int keycode) {
                if (keycode == com.badlogic.gdx.Input.Keys.ENTER || keycode == com.badlogic.gdx.Input.Keys.NUMPAD_ENTER) {
                    launch();
                    return true;
                }
                return false;
            }
        });
    }

    private boolean isUnlocked(GameModes.Mode m) {
        return m.unlockMode == null || game.save.unlockedModes.contains(m.unlockMode);
    }

    private String lockText(String optionOrMode) {
        QuestDef q = game.quests.unlockingQuest(optionOrMode);
        return q == null ? "verrouillé" : "quête \"" + game.data.lang.get(q.nameKey) + "\"";
    }

    private void rebuildOptions() {
        optionsTable.clearChildren();
        checkBoxes.clear();
        options.removeIf(o -> mode.options.stream().noneMatch(opt -> opt.id.equals(o)));
        if ("soccer".equals(mode.id)) addSoccerMaps();
        if (mode.options.isEmpty()) {
            optionsTable.add(label("Aucune option pour ce mode.", "dim")).left();
            return;
        }
        for (GameModes.Option opt : mode.options) {
            boolean unlocked = game.save.unlockedOptions.contains(opt.id);
            CheckBox cb = new CheckBox(" " + opt.label + (unlocked ? "" : "  - " + lockText(opt.id)), skin);
            cb.getImageCell().padRight(6);
            cb.setChecked(unlocked && options.contains(opt.id));
            cb.setDisabled(!unlocked);
            cb.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (cb.isChecked()) options.add(opt.id); else options.remove(opt.id);
                }
            });
            checkBoxes.add(cb);
            optionsTable.add(cb).left().padBottom(4).row();
        }
    }

    /** Choix du terrain de SoccerFest (options set_soc_0..3 de l'original) : 4 terrains ou au hasard. */
    private void addSoccerMaps() {
        Table row = new Table();
        row.add(label("Terrain :", "default")).padRight(8);
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
        optionsTable.add(row).left().padBottom(8).row();
    }

    /** Start de la manette : lancer la partie. */
    @Override
    protected void onGamepadStart() {
        launch();
    }

    private void launch() {
        game.save.lastMode = mode.id;
        game.save.lastOptions.clear();
        game.save.lastOptions.addAll(options);
        game.persist();
        Set<String> chosen = new LinkedHashSet<>(options);
        if ("soccer".equals(mode.id) && soccerMap >= 0) chosen.add("set_soc_" + soccerMap);
        game.show(new GameScreen(game, mode, chosen));
    }
}
