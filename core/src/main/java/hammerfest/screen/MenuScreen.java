package hammerfest.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import hammerfest.HammerfestGame;
import hammerfest.ui.Ui;

/** Base des écrans de menu : Stage Scene2D, fond d'origine, Échap = retour. */
public abstract class MenuScreen extends ScreenAdapter {

    protected final HammerfestGame game;
    protected final Skin skin;
    protected final Stage stage;
    /** Table racine plein écran, posée sur le fond. */
    protected final Table root = new Table();

    protected MenuScreen(HammerfestGame game) {
        this.game = game;
        this.skin = game.ui.skin;
        this.stage = new Stage(new FitViewport(Ui.WIDTH, Ui.HEIGHT));

        Image bg = new Image(game.menuBackground);
        bg.setFillParent(true);
        stage.addActor(bg);
        root.setFillParent(true);
        stage.addActor(root);

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                    back();
                    return true;
                }
                return false;
            }
        });
    }

    /** Action d'Échap : retour au menu principal par défaut. */
    protected void back() {
        game.show(new MainMenuScreen(game));
    }

    protected Label label(String text, String style) {
        return new Label(text, skin, style);
    }

    protected TextButton button(String text, Runnable action) {
        return button(text, "default", action);
    }

    protected TextButton button(String text, String style, Runnable action) {
        TextButton b = new TextButton(text, skin, style);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                action.run();
            }
        });
        return b;
    }

    /** Panneau semi-opaque standard avec titre, contenu à remplir et bouton Retour en bas. */
    protected Table panel(String title) {
        root.clearChildren();
        Table panel = new Table();
        panel.setBackground(skin.getDrawable("panel"));
        panel.pad(16);
        panel.defaults().left();
        root.add(panel).expand().fill().pad(18);
        panel.add(label(title, "title")).center().padBottom(14).row();
        return panel;
    }

    protected void addBackButton(Table panel) {
        panel.add(button("Retour", this::back)).center().width(200).padTop(12);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        updateGamepad(delta);
        stage.act(delta);
        stage.draw();
    }

    // ------------------------------------------------------------------ navigation à la manette

    /** Manettes : croix/stick = choisir un bouton, A/Start = valider, B/Back = retour. */
    protected final hammerfest.input.Gamepads gamepads = new hammerfest.input.Gamepads();
    private com.badlogic.gdx.scenes.scene2d.ui.Button focus;
    private int lastDir;
    private float repeat;

    /** Bouton mis en surbrillance à la manette (écran Commandes : choix du personnage réglé). */
    protected void onFocus(com.badlogic.gdx.scenes.scene2d.ui.Button b) {
    }

    /** Validation sans bouton en surbrillance (écran titre : continuer). */
    protected void onGamepadConfirm() {
    }

    /** Start : valide le bouton en surbrillance (écran Jouer et titre : lancer / continuer). */
    protected void onGamepadStart() {
        if (focus != null && focus.getStage() != null && !focus.isDisabled()) focus.toggle();
        else onGamepadConfirm();
    }

    private void updateGamepad(float delta) {
        if (gamepads.count() == 0) return;
        if (gamepads.consumeBack()) {
            gamepads.clear();
            back();
            return;
        }
        if (gamepads.consumeStart()) {
            onGamepadStart();
            return;
        }
        if (gamepads.consumeConfirm()) {
            if (focus != null && focus.getStage() != null && !focus.isDisabled()) focus.toggle();
            else onGamepadConfirm();
            return;
        }
        int dir = gamepads.menuBits();
        repeat -= delta;
        if (dir != 0 && (dir != lastDir || repeat <= 0)) {
            moveFocus(dir);
            repeat = dir != lastDir ? 0.35f : 0.12f; // première répétition plus lente
        }
        lastDir = dir;
    }

    private void collect(com.badlogic.gdx.scenes.scene2d.Actor a, java.util.List<com.badlogic.gdx.scenes.scene2d.ui.Button> out) {
        if (!a.isVisible()) return;
        if (a instanceof com.badlogic.gdx.scenes.scene2d.ui.Button) {
            com.badlogic.gdx.scenes.scene2d.ui.Button b = (com.badlogic.gdx.scenes.scene2d.ui.Button) a;
            if (!b.isDisabled()) out.add(b);
            return;
        }
        if (a instanceof com.badlogic.gdx.scenes.scene2d.Group) {
            for (com.badlogic.gdx.scenes.scene2d.Actor c : ((com.badlogic.gdx.scenes.scene2d.Group) a).getChildren()) collect(c, out);
        }
    }

    private com.badlogic.gdx.math.Vector2 center(com.badlogic.gdx.scenes.scene2d.Actor a) {
        return a.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(a.getWidth() / 2, a.getHeight() / 2));
    }

    /** Bouton le plus proche dans la direction demandée (le premier en haut à gauche s'il n'y en a pas encore). */
    private void moveFocus(int dir) {
        java.util.List<com.badlogic.gdx.scenes.scene2d.ui.Button> buttons = new java.util.ArrayList<>();
        collect(stage.getRoot(), buttons);
        if (buttons.isEmpty()) return;
        com.badlogic.gdx.scenes.scene2d.ui.Button next = null;
        if (focus == null || focus.getStage() == null || !buttons.contains(focus)) {
            float best = -Float.MAX_VALUE;
            for (com.badlogic.gdx.scenes.scene2d.ui.Button b : buttons) {
                com.badlogic.gdx.math.Vector2 c = center(b);
                if (c.y - c.x * 0.01f > best) {
                    best = c.y - c.x * 0.01f;
                    next = b;
                }
            }
        } else {
            com.badlogic.gdx.math.Vector2 from = center(focus);
            float bestScore = Float.MAX_VALUE;
            for (com.badlogic.gdx.scenes.scene2d.ui.Button b : buttons) {
                if (b == focus) continue;
                com.badlogic.gdx.math.Vector2 c = center(b);
                float dx = c.x - from.x, dy = c.y - from.y;
                float primary, secondary;
                if ((dir & hammerfest.sim.InputFrame.UP) != 0) { primary = dy; secondary = dx; }
                else if ((dir & hammerfest.sim.InputFrame.DOWN) != 0) { primary = -dy; secondary = dx; }
                else if ((dir & hammerfest.sim.InputFrame.RIGHT) != 0) { primary = dx; secondary = dy; }
                else { primary = -dx; secondary = dy; }
                if (primary <= 2) continue;
                float score = primary + 2 * Math.abs(secondary);
                if (score < bestScore) {
                    bestScore = score;
                    next = b;
                }
            }
        }
        if (next == null) return;
        if (focus != null) focus.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        focus = next;
        focus.setColor(1f, 0.85f, 0.35f, 1f); // surbrillance
        onFocus(focus);
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        gamepads.dispose();
    }
}
