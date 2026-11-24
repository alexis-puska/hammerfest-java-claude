package hammerfest.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import java.util.ArrayList;
import java.util.List;

/** Navigation clavier (haut/bas + Entrée/Espace) dans une liste verticale de boutons, synchronisée avec la souris. */
public class MenuNav extends InputListener {

    private final List<TextButton> buttons = new ArrayList<>();
    private final Skin skin;
    private int index = -1;

    public MenuNav(Stage stage, Skin skin) {
        this.skin = skin;
        stage.addListener(this);
    }

    public TextButton add(TextButton b) {
        int i = buttons.size();
        buttons.add(b);
        b.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor from) {
                if (pointer == -1) focus(i);
            }
        });
        if (index < 0) focus(0);
        return b;
    }

    public void focus(int i) {
        if (buttons.isEmpty()) return;
        if (index >= 0 && index < buttons.size()) {
            buttons.get(index).setStyle(skin.get("default", TextButton.TextButtonStyle.class));
        }
        index = Math.floorMod(i, buttons.size());
        buttons.get(index).setStyle(skin.get("focused", TextButton.TextButtonStyle.class));
    }

    @Override
    public boolean keyDown(InputEvent event, int keycode) {
        if (buttons.isEmpty()) return false;
        switch (keycode) {
            case Input.Keys.UP:
                move(-1);
                return true;
            case Input.Keys.DOWN:
                move(1);
                return true;
            case Input.Keys.ENTER:
            case Input.Keys.NUMPAD_ENTER:
            case Input.Keys.SPACE:
                TextButton b = buttons.get(index);
                if (!b.isDisabled()) b.fire(new ChangeListener.ChangeEvent());
                return true;
            default:
                return false;
        }
    }

    private void move(int dir) {
        int i = index;
        for (int n = 0; n < buttons.size(); n++) {
            i = Math.floorMod(i + dir, buttons.size());
            if (!buttons.get(i).isDisabled()) break;
        }
        focus(i);
    }
}
