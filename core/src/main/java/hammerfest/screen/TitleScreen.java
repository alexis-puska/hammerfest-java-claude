package hammerfest.screen;

import hammerfest.ui.Tr;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import hammerfest.HammerfestGame;
import hammerfest.sim.InputFrame;

/**
 * Écran titre : choix de la langue (flèches gauche / droite, croix ou stick de la manette, ou clic sur un bouton ;
 * français par défaut), puis Entrée / Espace, A / Start ou un clic ailleurs pour passer au menu principal.
 */
public class TitleScreen extends MenuScreen {

    private boolean leaving;
    /** Langue des textes du jeu et des menus (fr, en, es), aussi réglable dans Options. */
    private final Table langTable = new Table();

    public TitleScreen(HammerfestGame game) {
        super(game);
        Table t = new Table();
        t.setBackground(skin.getDrawable("panel"));
        t.pad(24);
        t.add(label(Tr.t("Les cavernes de"), "heading")).row();
        t.add(label("HAMMERFEST", "title")).padBottom(10).row();
        t.add(label(Tr.t("Portage hors ligne"), "dim")).row();
        root.add(t).expandY().bottom().padBottom(40).row();

        root.add(languages()).padBottom(8).row();
        root.add(label(Tr.t("Flèches gauche / droite : langue"), "dim")).padBottom(14).row();
        Label press = label(Tr.t("Entrée ou clic : commencer"), "accent");
        press.addAction(Actions.forever(Actions.sequence(Actions.alpha(0.3f, 0.8f), Actions.alpha(1f, 0.8f))));
        root.add(press).padBottom(16).row();
        root.add(label(Tr.t("Jeu original (c) Motion Twin 2006 - licence CC BY-NC-SA 4.0"), "dim")).expandY().bottom().padBottom(8);

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                switch (keycode) {
                    case Input.Keys.LEFT:
                        changeLanguage(-1);
                        return true;
                    case Input.Keys.RIGHT:
                        changeLanguage(1);
                        return true;
                    case Input.Keys.ENTER:
                    case Input.Keys.NUMPAD_ENTER:
                    case Input.Keys.SPACE:
                        next();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (event.getTarget().isDescendantOf(langTable)) return false; // choix de la langue : on reste ici
                next();
                return true;
            }
        });
    }

    private Table languages() {
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(1);
        for (int i = 0; i < OptionsScreen.LANGS.length; i++) {
            final String code = OptionsScreen.LANGS[i];
            TextButton b = new TextButton(OptionsScreen.LANG_NAMES[i], skin, "small");
            group.add(b);
            if (code.equals(game.save.lang)) b.setChecked(true);
            b.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    if (b.isChecked()) setLanguage(code);
                }
            });
            langTable.add(b).width(120).height(28).padRight(6);
        }
        return langTable;
    }

    /** Langue précédente (-1) ou suivante (+1) de la liste, sans boucler. */
    private void changeLanguage(int step) {
        int i = java.util.Arrays.asList(OptionsScreen.LANGS).indexOf(game.save.lang);
        int j = Math.max(0, Math.min(OptionsScreen.LANGS.length - 1, (i < 0 ? 0 : i) + step));
        setLanguage(OptionsScreen.LANGS[j]);
    }

    private void setLanguage(String code) {
        if (leaving || code.equals(game.save.lang)) return;
        game.setLanguage(code);
        leaving = true;
        game.show(new TitleScreen(game)); // textes de l'écran dans la nouvelle langue
    }

    /** Croix ou stick gauche / droite : langue ; les autres directions ne font rien ici. */
    @Override
    protected boolean onGamepadDirection(int dir) {
        if ((dir & InputFrame.LEFT) != 0) changeLanguage(-1);
        else if ((dir & InputFrame.RIGHT) != 0) changeLanguage(1);
        return true;
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
