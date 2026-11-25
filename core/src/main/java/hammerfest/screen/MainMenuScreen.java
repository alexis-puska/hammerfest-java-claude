package hammerfest.screen;

import hammerfest.ui.Tr;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import hammerfest.HammerfestGame;
import hammerfest.ui.MenuNav;

/** Menu principal : Jouer, Partie multi, Frigo, Quêtes, Statistiques, Profil, Commandes, Options, Quitter. */
public class MainMenuScreen extends MenuScreen {

    public MainMenuScreen(HammerfestGame game) {
        super(game);

        String name = game.save.hasPlayerName() ? game.save.playerName : Tr.t("(aucun nom, voir Profil)");
        root.add(label(Tr.t("Joueur : ") + name, game.save.hasPlayerName() ? "accent" : "dim"))
                .expandX().right().top().pad(12).row();

        Table menu = new Table();
        menu.setBackground(skin.getDrawable("panel"));
        menu.pad(20);
        menu.add(label("HAMMERFEST", "title")).padBottom(18).row();
        menu.defaults().width(300).height(42).padBottom(8);

        MenuNav nav = new MenuNav(stage, skin);
        menu.add(nav.add(button(Tr.t("Jouer"), () -> game.show(new PlayScreen(game))))).row();
        menu.add(nav.add(button(Tr.t("Partie multi"), this::openMulti))).row();
        menu.add(nav.add(button(Tr.t("Frigo"), () -> game.show(new FridgeScreen(game))))).row();
        menu.add(nav.add(button(Tr.t("Quêtes"), () -> game.show(new QuestsScreen(game))))).row();
        menu.add(nav.add(button(Tr.t("Statistiques"), () -> game.show(new StatsScreen(game))))).row();
        menu.add(nav.add(button(Tr.t("Profil / Nom du joueur"), () -> game.show(new ProfileScreen(game, null, null))))).row();
        menu.add(nav.add(button(Tr.t("Commandes"), () -> game.show(new ControlsScreen(game))))).row();
        menu.add(nav.add(button(Tr.t("Options (son, langue)"), () -> game.show(new OptionsScreen(game))))).row();
        menu.add(nav.add(button(Tr.t("Quitter"), () -> Gdx.app.exit()))).padBottom(0).row();

        root.add(menu).expand().top().padTop(10);
    }

    /** Le multi exige un nom de joueur : redirection vers Profil si absent, puis retour vers le multi. */
    private void openMulti() {
        if (game.save.hasPlayerName()) {
            game.show(new MultiScreen(game));
        } else {
            game.show(new ProfileScreen(game, Tr.t("Un nom de joueur est nécessaire pour jouer en ligne."),
                    () -> new MultiScreen(game)));
        }
    }

    @Override
    protected void back() {
        game.show(new TitleScreen(game));
    }
}
