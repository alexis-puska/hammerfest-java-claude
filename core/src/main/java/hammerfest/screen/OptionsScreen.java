package hammerfest.screen;

import hammerfest.ui.Tr;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import hammerfest.HammerfestGame;
import hammerfest.render.Audio;
import hammerfest.save.AudioSettings;

/**
 * Options : volume de la musique et des bruitages, musique coupée (aussi touche M, en jeu et dans les menus), langue des textes du jeu
 * (fr, en, es : messages, objets, quêtes et contrées d'origine). Réglages enregistrés dans save.json.
 */
public class OptionsScreen extends MenuScreen {

    static final String[] LANGS = {"fr", "en", "es"};
    static final String[] LANG_NAMES = {"Français", "English", "Español"};

    private final AudioSettings audio = game.save.audio;
    /** Lecteur de l'écran : bruitage d'essai quand on lâche le curseur. */
    private final Audio preview = new Audio();
    private final CheckBox mute;

    @Override
    protected void toggleMusic() {
        super.toggleMusic();
        mute.setChecked(audio.musicMuted); // case à jour après la touche M
    }

    public OptionsScreen(HammerfestGame game) {
        super(game);
        Table panel = panel(Tr.t("Options"));

        panel.add(label(Tr.t("Son"), "heading")).left().padBottom(8).row();
        Table sound = new Table();
        sound.defaults().left().padBottom(10);
        sound.add(label(Tr.t("Musique"), "default")).width(130);
        Label musicValue = label(percent(audio.musicVolume), "dim");
        Slider music = new Slider(0, 1, 0.05f, false, skin);
        music.setValue(audio.musicVolume);
        music.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.musicVolume = music.getValue();
                musicValue.setText(percent(audio.musicVolume));
                game.menuAudio.applySettings(); // volume appliqué tout de suite à la musique des menus
                if (!music.isDragging()) game.persist();
            }
        });
        sound.add(music).width(260).padRight(10);
        sound.add(musicValue).width(60).row();

        sound.add(label(Tr.t("Bruitages"), "default")).width(130);
        Label soundValue = label(percent(audio.soundVolume), "dim");
        Slider sfx = new Slider(0, 1, 0.05f, false, skin);
        sfx.setValue(audio.soundVolume);
        sfx.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.soundVolume = sfx.getValue();
                soundValue.setText(percent(audio.soundVolume));
                if (!sfx.isDragging()) {
                    game.persist();
                    preview.play("sound_jump"); // essai au volume choisi
                }
            }
        });
        sound.add(sfx).width(260).padRight(10);
        sound.add(soundValue).width(60).row();

        mute = new CheckBox(Tr.t(" Musique coupée (touche M)"), skin);
        mute.getImageCell().padRight(6);
        mute.setChecked(audio.musicMuted);
        mute.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.musicMuted = mute.isChecked();
                game.menuAudio.applySettings();
                game.persist();
            }
        });
        sound.add(mute).colspan(3).row();
        panel.add(sound).left().row();

        panel.add(label(Tr.t("Langue"), "heading")).left().padTop(14).padBottom(8).row();
        Table langs = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(1);
        for (int i = 0; i < LANGS.length; i++) {
            final String code = LANGS[i];
            TextButton b = new TextButton(LANG_NAMES[i], skin, "small");
            group.add(b);
            if (code.equals(game.save.lang)) b.setChecked(true);
            b.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (b.isChecked() && !code.equals(game.save.lang)) {
                        game.setLanguage(code);
                        game.show(new OptionsScreen(game)); // textes de l'écran dans la nouvelle langue
                    }
                }
            });
            langs.add(b).width(140).height(30).padRight(8);
        }
        panel.add(langs).left().row();
        Label note = label(Tr.t("Menus et textes du jeu (messages, objets, quêtes, contrées)."), "dim");
        note.setWrap(true);
        panel.add(note).width(560).left().padTop(6).row();

        panel.add().expandY().row();
        addBackButton(panel);
    }

    static String percent(float v) {
        return Math.round(v * 100) + " %";
    }

    @Override
    public void dispose() {
        super.dispose();
        preview.dispose();
    }
}
