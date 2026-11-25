package hammerfest.screen;

import hammerfest.ui.Tr;

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
        Table panel = panel(Tr.t("Partie multi"));
        boolean named = game.save.playerName != null && !game.save.playerName.isBlank();
        panel.add(label(named ? Tr.t("Vous jouez en tant que : ") + game.save.playerName : Tr.t("Choisissez d'abord un nom de joueur."),
                "heading")).padBottom(4).row();
        panel.add(button(named ? Tr.t("Changer de nom") : Tr.t("Choisir un nom"), "small",
                () -> game.show(new ProfileScreen(game, null, () -> new MultiScreen(game))))).padBottom(16).row();

        panel.add(button(Tr.t("Créer une partie"), () -> open(true))).width(320).center().padBottom(8).row();
        panel.add(button(Tr.t("Rejoindre une partie"), () -> open(false))).width(320).center().padBottom(16).row();

        Label info = label(Tr.t("Votre partie est annoncée comme \"Partie de ") + (named ? game.save.playerName : Tr.t("<votre nom>"))
                + Tr.t("\" sur le réseau local, et vous acceptez ou refusez chaque joueur qui demande à vous rejoindre. ")
                + Tr.t("Pour jouer par Internet, le port ") + hammerfest.net.Net.TCP_PORT
                + Tr.t(" est ouvert automatiquement sur votre box (UPnP) et l'autre joueur saisit votre adresse."), "dim");
        info.setWrap(true);
        panel.add(info).width(540).row();
        panel.add().expandY().row();
        addBackButton(panel);
    }

    private void open(boolean host) {
        if (game.save.playerName == null || game.save.playerName.isBlank()) {
            game.show(new ProfileScreen(game, Tr.t("Un nom de joueur est nécessaire pour jouer en ligne."),
                    () -> host ? new HostLobbyScreen(game) : new JoinScreen(game)));
            return;
        }
        game.show(host ? new HostLobbyScreen(game) : new JoinScreen(game));
    }
}
