package hammerfest.screen;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import hammerfest.HammerfestGame;
import hammerfest.data.GameModes;

/** Jouer : choix du mode et des options ; ce qui n'est pas débloqué est grisé avec la quête correspondante. */
public class PlayScreen extends MenuScreen {

    private GameModes.Mode mode;
    private final ModeOptionsTable optionsTable;

    public PlayScreen(HammerfestGame game) {
        super(game);
        Table panel = panel("Jouer");
        optionsTable = new ModeOptionsTable(game, skin, game.save.lastOptions);

        panel.add(label("Mode de jeu", "heading")).padBottom(6).row();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(1);
        GameModes.Mode last = GameModes.mode(game.save.lastMode);
        mode = isUnlocked(game, last) ? last : GameModes.MODES.get(0);
        for (GameModes.Mode m : GameModes.MODES) {
            boolean unlocked = isUnlocked(game, m);
            String text = m.label + (unlocked ? "" : "  - " + ModeOptionsTable.lockText(game, m.unlockMode));
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
                        optionsTable.setMode(m);
                    }
                }
            });
            panel.add(b).width(560).height(30).padBottom(4).row();
        }

        panel.add(label("Options", "heading")).padTop(12).padBottom(6).row();
        panel.add(optionsTable).left().row();
        if (game.save.unlockedOptions.contains(GameModes.PASSIVE_BOOST)) {
            panel.add(label("Tornade (boost de vitesse en début de partie) : toujours active.", "ok")).padTop(6).row();
        }
        optionsTable.setMode(mode);

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

    static boolean isUnlocked(HammerfestGame game, GameModes.Mode m) {
        return m.unlockMode == null || game.save.unlockedModes.contains(m.unlockMode);
    }

    /** Start de la manette : lancer la partie. */
    @Override
    protected void onGamepadStart() {
        launch();
    }

    private void launch() {
        game.save.lastMode = mode.id;
        game.save.lastOptions.clear();
        game.save.lastOptions.addAll(optionsTable.checked());
        game.persist();
        game.show(new GameScreen(game, mode, optionsTable.chosen()));
    }
}
