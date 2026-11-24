package hammerfest.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import hammerfest.HammerfestGame;
import hammerfest.input.Gamepads;
import hammerfest.save.ControlSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Écran Commandes : onglet Clavier (touches, pour mémoire) et onglet Manette. Manette : affectation d'une manette à
 * Igor et à Sandy (menus déroulants ; toucher l'un d'eux affiche le réglage de ce personnage), dessin de la manette
 * branchée (Xbox ou PlayStation) entouré d'un menu déroulant par bouton réglable (4 boutons de droite, gâchettes
 * hautes et basses, clics des sticks, View / Create : Sauter, Bombe, Bombe en haut, Carte, Pause ou Aucune),
 * vibrations et zone morte du stick. Réglages enregistrés dans save.json.
 */
public class ControlsScreen extends MenuScreen {

    /** Lignes de réglage de part et d'autre du dessin, de haut en bas : boutons du côté gauche, puis du côté droit. */
    private static final int[] LEFT_ROWS = {ControlSettings.L2, ControlSettings.L1, ControlSettings.SELECT,
            ControlSettings.L3};
    private static final int[] RIGHT_ROWS = {ControlSettings.R2, ControlSettings.R1, ControlSettings.FACE_TOP,
            ControlSettings.FACE_RIGHT, ControlSettings.FACE_LEFT, ControlSettings.FACE_BOTTOM, ControlSettings.R3};
    /** Icône de chaque bouton (assets/ui/btn-&lt;type&gt;-&lt;nom&gt;.png). */
    private static final String[] BUTTON_FILE = {"bottom", "right", "left", "top", "l1", "r1", "l2", "r2", "l3", "r3",
            "select"};
    /** Nom de chaque bouton, Xbox puis PlayStation (bulle d'aide de la ligne). */
    private static final String[][] BUTTON_NAMES = {
            {"A", "B", "X", "Y", "LB", "RB", "LT", "RT", "LS (clic du stick gauche)", "RS (clic du stick droit)", "View"},
            {"Croix", "Rond", "Carré", "Triangle", "L1", "R1", "L2", "R2", "L3 (clic du stick gauche)",
                    "R3 (clic du stick droit)", "Create"}};
    private static final String[] PLAYER = {"Igor", "Sandy"};

    private final ControlSettings settings = game.save.controls;
    private final java.util.Map<String, Texture> textures = new java.util.HashMap<>();
    private boolean keyboardTab;
    private int editPid;
    private List<String> names = new ArrayList<>();
    /** Icônes des lignes, par bouton (surbrillance quand le bouton est enfoncé) et manette affichée. */
    private final Image[] rowIcons = new Image[ControlSettings.BUTTONS];
    /** Nom du dernier bouton survolé ou enfoncé, sous le dessin. */
    private Label buttonName;
    private int shownController = -1;
    private Table popup;
    private Actor popupCatcher;

    public ControlsScreen(HammerfestGame game) {
        super(game);
        names = gamepads.names();
        build();
    }

    private Texture tex(String path) {
        return textures.computeIfAbsent(path, k -> {
            Texture t = new Texture(Gdx.files.internal(k));
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return t;
        });
    }

    /** Petit triangle blanc pointe en bas (flèche des menus déroulants). */
    private Texture arrowTexture() {
        return textures.computeIfAbsent("#arrow", k -> {
            com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(20, 12,
                    com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
            p.setColor(Color.WHITE);
            p.fillTriangle(0, 0, 19, 0, 10, 11);
            Texture t = new Texture(p);
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            p.dispose();
            return t;
        });
    }

    private void build() {
        closePopup();
        Table panel = panel("Commandes");
        Table tabs = new Table();
        tabs.add(button("Clavier", keyboardTab ? "focused" : "default", () -> {
            keyboardTab = true;
            build();
        })).width(150).height(32).padRight(10);
        tabs.add(button("Manette", keyboardTab ? "default" : "focused", () -> {
            keyboardTab = false;
            build();
        })).width(150).height(32);
        panel.add(tabs).left().padBottom(14).row();
        if (keyboardTab) buildKeyboard(panel);
        else buildGamepad(panel);
        panel.add().expandY().row();
        Table bottom = new Table();
        if (!keyboardTab) {
            bottom.add(button("Réinitialiser", () -> {
                settings.faces[editPid] = ControlSettings.defaultButtons();
                settings.vibration = true;
                settings.vibrationStrength = 0.7f;
                settings.deadZone = 0.45f;
                save();
                build();
            })).width(200).padRight(14);
        }
        bottom.add(button("Retour", this::back)).width(200);
        panel.add(bottom).center().padTop(8);
    }

    private void buildKeyboard(Table panel) {
        String[][] keys = {
                {"", "Igor", "Sandy (2 joueurs)"},
                {"Gauche / droite", "Flèches", "D / G"},
                {"Sauter", "Flèche haut", "R"},
                {"Bombe", "Espace, Ctrl, Entrée", "A"},
                {"Bombe en haut", "Flèche bas", "F"},
                {"Pause", "P", "P"},
                {"Carte", "C", "C"},
                {"Déguisement (solo)", "D", ""},
                {"Quitter la partie", "Échap", "Échap"},
        };
        Table t = new Table();
        t.defaults().left().padBottom(8).padRight(24);
        for (int i = 0; i < keys.length; i++) {
            String style = i == 0 ? "heading" : "default";
            t.add(label(keys[i][0], i == 0 ? "dim" : "default"));
            t.add(label(keys[i][1], style));
            t.add(label(keys[i][2], style)).row();
        }
        panel.add(t).left().row();
        panel.add(label("Les touches du clavier ne se règlent pas.", "dim")).left().padTop(6).row();
    }

    private void buildGamepad(Table panel) {
        names = gamepads.names();
        int[] resolved = settings.resolve(names);
        panel.add(label("Manettes branchées : " + names.size(), "heading")).left().padBottom(8).row();

        // affectation : un menu déroulant par personnage
        List<String> keys = ControlSettings.keys(names);
        Table assign = new Table();
        assign.defaults().left().padBottom(6);
        for (int pid = 0; pid < 2; pid++) {
            final int p = pid;
            List<String> options = new ArrayList<>();
            for (String n : names) options.add(n);
            options.add("Aucune (clavier)");
            int current = resolved[pid] >= 0 ? resolved[pid] : names.size();
            assign.add(label(PLAYER[pid], pid == editPid ? "accent" : "default")).width(70);
            TextButton dd = dropdown(options, current, 330, i -> {
                if (i < names.size()) settings.assign(p, keys.get(i), names);
                else settings.assigned[p] = "-";
                editPid = p;
                save();
                build();
            }, () -> {
                if (editPid != p) {
                    editPid = p;
                    build();
                }
            });
            assign.add(dd).width(330).height(28).row();
        }
        panel.add(assign).left().row();

        // réglage de la manette du personnage choisi
        int ctrl = resolved[editPid];
        shownController = ctrl;
        String title = (editPid == 0 ? "Manette d'Igor" : "Manette de Sandy");
        Table head = new Table();
        head.add(label(title, "heading")).padRight(10);
        head.add(label(ctrl >= 0 ? "— " + names.get(ctrl) : "— aucune manette affectée", "dim"));
        panel.add(head).left().padTop(10).padBottom(8).row();

        int type = ctrl >= 0 && ControlSettings.isPlayStation(names.get(ctrl)) ? 1 : 0;
        String typeName = type == 1 ? "ps" : "xbox";
        // dessin au centre, boutons du côté gauche à gauche, du côté droit à droite
        java.util.Arrays.fill(rowIcons, null);
        Table pad = new Table();
        pad.add(buttonRows(LEFT_ROWS, typeName, type, ctrl)).top().padRight(4);
        Table middle = new Table();
        Image padImage = new Image(tex("ui/pad-" + typeName + ".png"));
        if (ctrl < 0) padImage.setColor(1, 1, 1, 0.35f);
        middle.add(padImage).size(200, 152).row();
        buttonName = label("", "accent");
        buttonName.setAlignment(Align.center);
        buttonName.setWrap(true);
        middle.add(buttonName).width(196).padTop(4);
        pad.add(middle).top().padTop(20);
        pad.add(buttonRows(RIGHT_ROWS, typeName, type, ctrl)).top().padLeft(4);
        panel.add(pad).center().row();
        String start = type == 1 ? "Options" : "Menu";
        panel.add(label("Croix / stick gauche : déplacement   •   " + start + " : pause", "dim")).left().padBottom(4).row();
        StringBuilder missing = new StringBuilder();
        for (int a = 0; a <= ControlSettings.BOMB; a++) {
            if (settings.missing(editPid, a)) missing.append(missing.length() > 0 ? ", " : "").append(ControlSettings.ACTION_NAMES[a]);
        }
        if (ctrl >= 0 && missing.length() > 0) {
            panel.add(label("Aucun bouton pour : " + missing, "accent")).left().padBottom(4).row();
        } else {
            panel.add(label("Plusieurs boutons peuvent faire la même action.", "dim")).left().padBottom(4).row();
        }
        if (editPid == 0) { // le joueur qui rejoint une partie en ligne joue Sandy, avec ce réglage
            Label online = label("Réglage utilisé en jeu local et quand vous rejoignez une partie en ligne (vous y jouez Sandy).", "accent");
            online.setWrap(true);
            panel.add(online).width(560).left().padBottom(10).row();
        } else {
            panel.add().padBottom(6).row();
        }

        // vibrations et zone morte (communes)
        Table opts = new Table();
        opts.defaults().left().padBottom(8);
        CheckBox vib = new CheckBox(" Vibrations", skin);
        vib.setChecked(settings.vibration);
        vib.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                settings.vibration = vib.isChecked();
                save();
            }
        });
        opts.add(vib).width(170);
        opts.add(label("intensité", "dim")).padRight(10);
        Slider strength = new Slider(0, 1, 0.05f, false, skin);
        strength.setValue(settings.vibrationStrength);
        strength.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                settings.vibrationStrength = strength.getValue();
                if (!strength.isDragging()) {
                    save();
                    gamepads.vibrate(editPid, 2, 200, 1f); // essai
                }
            }
        });
        opts.add(strength).width(200).row();
        opts.add(label("Zone morte du stick", "default"));
        Label dzValue = label(Math.round(settings.deadZone * 100) + " %", "dim");
        opts.add(dzValue);
        Slider dz = new Slider(0.05f, 0.9f, 0.05f, false, skin);
        dz.setValue(settings.deadZone);
        dz.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                settings.deadZone = dz.getValue();
                dzValue.setText(Math.round(settings.deadZone * 100) + " %");
                if (!dz.isDragging()) save();
            }
        });
        opts.add(dz).width(200).row();
        panel.add(opts).left().row();
        panel.add(label("Vibrations : explosion proche, vie perdue, boss touché.", "dim")).left().row();
    }

    /** Colonne de lignes « icône du bouton + action » ; survoler une ligne affiche le nom du bouton sous le dessin. */
    private Table buttonRows(int[] buttons, String typeName, int type, int ctrl) {
        Table rows = new Table();
        for (int button : buttons) {
            Image icon = new Image(tex("ui/btn-" + typeName + "-" + BUTTON_FILE[button] + ".png"));
            icon.setOrigin(13, 13);
            rowIcons[button] = icon;
            Table row = new Table();
            row.add(icon).size(26, 26).padRight(4);
            String name = BUTTON_NAMES[type][button];
            TextButton dd = dropdown(List.of(ControlSettings.ACTION_NAMES), settings.faces[editPid][button], 142, a -> {
                settings.setButton(editPid, button, a);
                save();
                build();
            }, () -> buttonName.setText(name));
            dd.setDisabled(ctrl < 0);
            row.add(dd).width(142).height(26);
            rows.add(row).height(31).left().row();
        }
        return rows;
    }

    /**
     * Menu déroulant : bouton « valeur ▾ » qui ouvre la liste des choix sous lui (souris, clavier ou manette).
     *
     * @param onTouch appelé quand on le survole ou le sélectionne à la manette (null = rien)
     */
    private TextButton dropdown(List<String> options, int selected, float width, IntConsumer onPick, Runnable onTouch) {
        String text = selected >= 0 && selected < options.size() ? options.get(selected) : "";
        TextButton b = new TextButton(text, skin, "small");
        b.getLabel().setAlignment(Align.left);
        b.getLabel().setEllipsis(true);
        b.getLabelCell().growX().minWidth(0).padLeft(6);
        Image arrow = new Image(arrowTexture()); // ▼ absent de Verdana : triangle dessiné
        arrow.setColor(Color.LIGHT_GRAY);
        b.add(arrow).size(10, 6).padRight(7).padLeft(4);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (onTouch != null) onTouch.run();
                openPopup(b, options, selected, width, onPick);
            }
        });
        if (onTouch != null) {
            b.addListener(new InputListener() {
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    if (pointer == -1 && popup == null) onTouch.run();
                }
            });
            b.setUserObject(onTouch);
        }
        return b;
    }

    private void openPopup(TextButton anchor, List<String> options, int selected, float width, IntConsumer onPick) {
        closePopup();
        popupCatcher = new Actor();
        popupCatcher.setBounds(0, 0, stage.getWidth(), stage.getHeight());
        popupCatcher.setTouchable(Touchable.enabled);
        popupCatcher.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                closePopup();
                return true;
            }
        });
        stage.addActor(popupCatcher);
        popup = new Table();
        popup.setBackground(skin.getDrawable("panel"));
        popup.pad(4);
        for (int i = 0; i < options.size(); i++) {
            final int choice = i;
            TextButton o = button(options.get(i), i == selected ? "focused" : "small", () -> {
                closePopup();
                onPick.accept(choice);
            });
            o.getLabel().setAlignment(Align.left);
            popup.add(o).width(width).height(28).row();
        }
        popup.pack();
        com.badlogic.gdx.math.Vector2 p = anchor.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(0, 0));
        float y = p.y - popup.getHeight();
        if (y < 0) y = p.y + anchor.getHeight();
        popup.setPosition(p.x, y);
        stage.addActor(popup);
    }

    private void closePopup() {
        if (popup != null) popup.remove();
        if (popupCatcher != null) popupCatcher.remove();
        popup = null;
        popupCatcher = null;
    }

    @Override
    protected void onFocus(Button b) {
        if (popup == null && b.getUserObject() instanceof Runnable) ((Runnable) b.getUserObject()).run();
    }

    @Override
    public void render(float delta) {
        if (!keyboardTab && popup == null && !gamepads.names().equals(names)) build(); // manette branchée / retirée
        // bouton enfoncé : son icône s'allume et son nom s'affiche sous le dessin
        for (int b = 0; b < rowIcons.length; b++) {
            if (rowIcons[b] == null) continue;
            boolean down = gamepads.isDown(shownController, b);
            rowIcons[b].setColor(down ? new Color(1f, 1f, 0.6f, 1f) : Color.WHITE);
            rowIcons[b].setScale(down ? 1.25f : 1f);
            if (down && buttonName != null) {
                int type = shownController >= 0 && ControlSettings.isPlayStation(names.get(shownController)) ? 1 : 0;
                buttonName.setText(BUTTON_NAMES[type][b]);
            }
        }
        super.render(delta);
    }

    @Override
    protected void back() {
        if (popup != null) {
            closePopup();
            return;
        }
        game.show(new MainMenuScreen(game));
    }

    private void save() {
        game.persist();
    }

    @Override
    public void dispose() {
        super.dispose();
        for (Texture t : textures.values()) t.dispose();
    }
}
