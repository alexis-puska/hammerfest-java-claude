package hammerfest.screen;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import hammerfest.HammerfestGame;

/** Écran titre : une touche ou un clic mène au menu principal. */
public class TitleScreen extends MenuScreen {

    private boolean leaving;

    public TitleScreen(HammerfestGame game) {
        super(game);
        Table t = new Table();
        t.setBackground(skin.getDrawable("panel"));
        t.pad(24);
        t.add(label("Les cavernes de", "heading")).row();
        t.add(label("HAMMERFEST", "title")).padBottom(10).row();
        t.add(label("Portage hors ligne", "dim")).row();
        root.add(t).expandY().bottom().padBottom(40).row();

        Label press = label("Appuyez sur une touche ou cliquez pour commencer", "accent");
        press.addAction(Actions.forever(Actions.sequence(Actions.alpha(0.3f, 0.8f), Actions.alpha(1f, 0.8f))));
        root.add(press).padBottom(16).row();
        root.add(label("Jeu original (c) Motion Twin 2006 - licence CC BY-NC-SA 4.0", "dim")).expandY().bottom().padBottom(8);

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                next();
                return true;
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                next();
                return true;
            }
        });
    }

    @Override
    protected void onGamepadConfirm() {
        next();
    }

    @Override
    protected void onGamepadStart() {
        next();
    }

    private void next() {
        if (leaving) return;
        leaving = true;
        game.show(new MainMenuScreen(game));
    }

    @Override
    protected void back() {
        // Échap sur l'écran titre : rien (le menu principal propose Quitter).
    }
}
