package hammerfest.screen;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import hammerfest.HammerfestGame;

/**
 * Partie multi en ligne : créer une partie (annoncée sous le nom du joueur, chaque demande acceptée ou refusée)
 * ou rejoindre celle d'un autre joueur. Un nom de joueur est obligatoire (écran Profil).
 */
public class MultiScreen extends MenuScreen {

    public MultiScreen(HammerfestGame game) {
        super(game);
        Table panel = panel("Partie multi");
        boolean named = game.save.playerName != null && !game.save.playerName.isBlank();
        panel.add(label(named ? "Vous jouez en tant que : " + game.save.playerName : "Choisissez d'abord un nom de joueur.",
                "heading")).padBottom(4).row();
        panel.add(button(named ? "Changer de nom" : "Choisir un nom", "small",
                () -> game.show(new ProfileScreen(game, null, () -> new MultiScreen(game))))).padBottom(16).row();

        panel.add(button("Créer une partie", () -> open(true))).width(320).center().padBottom(8).row();
        panel.add(button("Rejoindre une partie", () -> open(false))).width(320).center().padBottom(16).row();

        Label info = label("Votre partie est annoncée comme \"Partie de " + (named ? game.save.playerName : "<votre nom>")
                + "\" sur le réseau local, et vous acceptez ou refusez chaque joueur qui demande à vous rejoindre. "
                + "Pour jouer par Internet, le port " + hammerfest.net.Net.TCP_PORT
                + " est ouvert automatiquement sur votre box (UPnP) et l'autre joueur saisit votre adresse.", "dim");
        info.setWrap(true);
        panel.add(info).width(540).row();
        panel.add().expandY().row();
        addBackButton(panel);
    }

    private void open(boolean host) {
        if (game.save.playerName == null || game.save.playerName.isBlank()) {
            game.show(new ProfileScreen(game, "Un nom de joueur est nécessaire pour jouer en ligne.",
                    () -> host ? new HostLobbyScreen(game) : new JoinScreen(game)));
            return;
        }
        game.show(host ? new HostLobbyScreen(game) : new JoinScreen(game));
    }
}
