package hammerfest.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Disposable;

/**
 * Skin Scene2D construit par code (aucun fichier de skin externe), police libGDX par défaut
 * (couvre Latin-1, donc les accents français et espagnols).
 */
public final class Ui implements Disposable {

    /** Taille logique des écrans de menu : fond d'origine 420x500 à l'échelle 1,5. */
    public static final float WIDTH = 630;
    public static final float HEIGHT = 750;

    public static final Color PANEL = new Color(0.05f, 0.09f, 0.2f, 0.78f);
    public static final Color ACCENT = new Color(1f, 0.82f, 0.25f, 1f);
    public static final Color TEXT = Color.WHITE;
    public static final Color DIM = new Color(0.62f, 0.68f, 0.8f, 1f);
    public static final Color OK = new Color(0.45f, 0.9f, 0.45f, 1f);
    public static final Color ERROR = new Color(1f, 0.45f, 0.4f, 1f);

    public final Skin skin = new Skin();
    private final Texture white;

    public Ui() {
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        white = new Texture(p);
        p.dispose();
        skin.add("white", white);

        // Verdana, comme le texte des parchemins du jeu ; tailles générées directement (texte net)
        BitmapFont font = hammerfest.render.Fonts.load(hammerfest.render.Fonts.verdana(), 15, false);
        BitmapFont big = hammerfest.render.Fonts.load(hammerfest.render.Fonts.verdana(), 30, false);
        BitmapFont mid = hammerfest.render.Fonts.load(hammerfest.render.Fonts.verdana(), 20, false);
        skin.add("default", font);
        skin.add("big", big);
        skin.add("mid", mid);

        skin.add("default", new Label.LabelStyle(font, TEXT));
        skin.add("dim", new Label.LabelStyle(font, DIM));
        skin.add("ok", new Label.LabelStyle(font, OK));
        skin.add("error", new Label.LabelStyle(font, ERROR));
        skin.add("accent", new Label.LabelStyle(font, ACCENT));
        skin.add("title", new Label.LabelStyle(big, ACCENT));
        skin.add("heading", new Label.LabelStyle(mid, ACCENT));

        skin.add("panel", tint(PANEL), Drawable.class);

        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
        button.font = mid;
        button.up = tint(new Color(0.12f, 0.22f, 0.45f, 0.9f));
        button.over = tint(new Color(0.2f, 0.35f, 0.65f, 0.95f));
        button.down = tint(new Color(0.08f, 0.15f, 0.3f, 1f));
        button.checked = tint(new Color(0.55f, 0.4f, 0.05f, 0.95f));
        button.disabled = tint(new Color(0.15f, 0.15f, 0.2f, 0.7f));
        button.fontColor = TEXT;
        button.overFontColor = ACCENT;
        button.disabledFontColor = DIM;
        skin.add("default", button);

        TextButton.TextButtonStyle focused = new TextButton.TextButtonStyle(button);
        focused.up = button.over;
        focused.fontColor = ACCENT;
        skin.add("focused", focused);

        TextButton.TextButtonStyle small = new TextButton.TextButtonStyle(button);
        small.font = font;
        skin.add("small", small);

        TextField.TextFieldStyle field = new TextField.TextFieldStyle();
        field.font = mid;
        field.fontColor = TEXT;
        field.messageFontColor = DIM;
        field.background = tint(new Color(0f, 0f, 0f, 0.6f));
        field.focusedBackground = tint(new Color(0.05f, 0.05f, 0.15f, 0.85f));
        field.cursor = tint(ACCENT);
        field.selection = tint(new Color(0.3f, 0.4f, 0.8f, 0.8f));
        ((com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable) field.background).setLeftWidth(8);
        ((com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable) field.focusedBackground).setLeftWidth(8);
        ((com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable) field.cursor).setMinWidth(2);
        skin.add("default", field);

        CheckBox.CheckBoxStyle check = new CheckBox.CheckBoxStyle();
        check.font = font;
        check.fontColor = TEXT;
        check.disabledFontColor = DIM;
        check.checkboxOff = box(new Color(0.15f, 0.2f, 0.35f, 1f));
        check.checkboxOn = box(ACCENT);
        check.checkboxOffDisabled = box(new Color(0.25f, 0.25f, 0.28f, 1f));
        skin.add("default", check);

        skin.add("default", new ScrollPane.ScrollPaneStyle(null, null, null, null, tint(new Color(1, 1, 1, 0.35f))));

        ListStyle list = new ListStyle(font, ACCENT, TEXT, tint(new Color(0.2f, 0.35f, 0.65f, 1f)));
        list.background = tint(new Color(0.05f, 0.09f, 0.2f, 0.97f));
        skin.add("default", list);
        SelectBox.SelectBoxStyle select = new SelectBox.SelectBoxStyle(font, TEXT,
                tint(new Color(0.12f, 0.22f, 0.45f, 0.9f)),
                new ScrollPane.ScrollPaneStyle(), list);
        ((com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable) select.background).setLeftWidth(8);
        select.background.setMinHeight(26);
        skin.add("default", select);

        // curseur (vibrations, zone morte) : barre sombre, partie remplie dorée, bouton doré
        com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle slider = new com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle();
        slider.background = tint(new Color(0.1f, 0.16f, 0.32f, 1f));
        slider.background.setMinHeight(6);
        slider.knobBefore = tint(ACCENT);
        slider.knobBefore.setMinHeight(6);
        slider.knob = box(ACCENT);
        slider.knob.setMinWidth(14);
        slider.knob.setMinHeight(14);
        slider.knob.setRightWidth(0);
        skin.add("default-horizontal", slider);
    }

    public Drawable tint(Color c) {
        return skin.newDrawable("white", c);
    }

    private Drawable box(Color c) {
        Drawable d = tint(c);
        d.setMinWidth(16);
        d.setMinHeight(16);
        d.setRightWidth(6);
        return d;
    }

    @Override
    public void dispose() {
        skin.dispose();
    }
}
