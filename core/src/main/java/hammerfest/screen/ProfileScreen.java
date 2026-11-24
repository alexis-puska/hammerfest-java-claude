package hammerfest.screen;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import hammerfest.HammerfestGame;
import hammerfest.save.PlayerName;

import java.util.function.Supplier;

/**
 * Profil : saisie du nom de joueur (3 à 16 caractères), enregistré dans save.json.
 * Il sert en multi à annoncer la partie, à présenter la demande de connexion à l'hôte
 * et à afficher le nom de l'hôte au joueur qui rejoint.
 */
public class ProfileScreen extends MenuScreen {

    private final Supplier<Screen> next;
    private final TextField field;
    private final Label feedback;

    /**
     * @param reason message affiché en tête (ex. redirection depuis le multi), ou null
     * @param next   écran ouvert après validation, ou null pour rester sur le Profil
     */
    public ProfileScreen(HammerfestGame game, String reason, Supplier<Screen> next) {
        super(game);
        this.next = next;

        Table panel = panel("Profil");
        if (reason != null) panel.add(label(reason, "accent")).padBottom(10).row();
        panel.add(label("Nom du joueur", "heading")).padBottom(6).row();
        panel.add(label("Utilisé en ligne pour vous identifier auprès des autres joueurs.", "dim")).padBottom(10).row();

        field = new TextField(game.save.playerName, skin);
        field.setMessageText("Votre nom");
        field.setMaxLength(PlayerName.MAX_LENGTH);
        field.setTextFieldFilter((f, c) -> PlayerName.isAllowed(c));
        field.setTextFieldListener((f, c) -> {
            if (c == '\r' || c == '\n') validate();
        });
        panel.add(field).width(360).height(40).padBottom(6).row();
        panel.add(label(PlayerName.MIN_LENGTH + " à " + PlayerName.MAX_LENGTH
                + " caractères : lettres, chiffres, espace, - et _", "dim")).padBottom(10).row();

        feedback = label("", "default");
        feedback.setWrap(true);
        panel.add(feedback).width(540).padBottom(10).row();

        Table buttons = new Table();
        buttons.add(button("Valider", this::validate)).width(200).padRight(16);
        buttons.add(button("Retour", this::back)).width(200);
        panel.add(buttons).center().row();
        panel.add().expandY();

        stage.setKeyboardFocus(field);
        field.setCursorPosition(field.getText().length());
    }

    private void validate() {
        String error = PlayerName.validate(field.getText());
        if (error != null) {
            feedback.setStyle(skin.get("error", Label.LabelStyle.class));
            feedback.setText(error);
            return;
        }
        String name = PlayerName.normalize(field.getText());
        game.save.playerName = name;
        game.persist();
        if (next != null) {
            game.show(next.get());
        } else {
            field.setText(name);
            feedback.setStyle(skin.get("ok", Label.LabelStyle.class));
            feedback.setText("Nom enregistré : " + name);
        }
    }
}
