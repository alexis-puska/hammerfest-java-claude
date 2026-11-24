package hammerfest.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import hammerfest.HammerfestGame;
import hammerfest.net.ClientSession;
import hammerfest.net.LanDiscovery;
import hammerfest.net.Net;
import hammerfest.net.NetEvent;

import java.net.InetAddress;
import java.util.List;

/**
 * Rejoindre une partie : recherche sur le réseau local (« Partie de &lt;nom&gt; ») ou adresse saisie, puis demande
 * de connexion envoyée sous le nom du joueur ; l'hôte accepte ou refuse.
 */
public class JoinScreen extends MenuScreen {

    private final Table list = new Table();
    private final Label status = label("", "dim");
    private final TextField address = new TextField("", skin);
    private ClientSession client;
    /** Session confiée à l'écran de partie en ligne. */
    private boolean handedOff;
    private boolean searching;

    public JoinScreen(HammerfestGame game) {
        super(game);
        Table panel = panel("Rejoindre une partie");
        panel.add(label("Parties sur le réseau local", "heading")).padBottom(6).row();
        panel.add(list).width(560).left().padBottom(6).row();
        panel.add(button("Rechercher", "small", this::search)).width(200).padBottom(14).row();

        panel.add(label("Ou adresse de l'hôte (IP ou nom)", "heading")).padBottom(6).row();
        Table row = new Table();
        row.add(address).width(330).height(30).padRight(10);
        row.add(button("Se connecter", "small", () -> {
            String a = address.getText().trim();
            if (!a.isEmpty()) connect(a, "l'hôte");
        })).width(180);
        panel.add(row).left().padBottom(14).row();

        status.setWrap(true);
        panel.add(status).width(560).row();
        panel.add().expandY().row();
        addBackButton(panel);
        search();
    }

    private void search() {
        if (searching) return;
        searching = true;
        list.clearChildren();
        list.add(label("Recherche…", "dim")).left();
        new Thread(() -> {
            List<LanDiscovery.HostInfo> found = LanDiscovery.discover(Net.DISCOVERY_PORT, 1500);
            Gdx.app.postRunnable(() -> showHosts(found));
        }, "hammerfest-search").start();
    }

    private void showHosts(List<LanDiscovery.HostInfo> found) {
        searching = false;
        list.clearChildren();
        if (found.isEmpty()) {
            list.add(label("Aucune partie trouvée.", "dim")).left();
            return;
        }
        for (LanDiscovery.HostInfo h : found) {
            boolean ok = h.version == Net.PROTOCOL_VERSION;
            String text = h + (ok ? "" : "  - version différente");
            com.badlogic.gdx.scenes.scene2d.ui.TextButton b = button(text, "small",
                    () -> connect(h.address.getHostAddress(), h.hostName));
            b.setDisabled(!ok);
            list.add(b).width(560).height(30).left().padBottom(4).row();
        }
    }

    private void connect(String host, String hostLabel) {
        if (client != null) client.close();
        ClientSession c = new ClientSession();
        client = c;
        status.setStyle(skin.get("dim", Label.LabelStyle.class));
        status.setText("Connexion à " + hostLabel + "…");
        new Thread(() -> {
            try {
                c.connect(InetAddress.getByName(host), Net.TCP_PORT, Net.UDP_PORT, game.save.playerName, game.save.playerId);
                Gdx.app.postRunnable(() -> status.setText("Demande envoyée à la partie de " + hostLabel
                        + ". En attente de sa réponse (30 s au plus)…"));
            } catch (Exception e) {
                Gdx.app.postRunnable(() -> error("Impossible de joindre " + hostLabel + " (" + host + ")."));
            }
        }, "hammerfest-connect").start();
    }

    private void error(String text) {
        status.setStyle(skin.get("error", Label.LabelStyle.class));
        status.setText(text);
    }

    @Override
    public void render(float delta) {
        if (client != null) {
            ClientSession c = client; // la boucle s'arrête si la connexion est abandonnée (refus, délai, départ)
            for (NetEvent e = c.poll(); e != null && client == c; e = c.poll()) {
                switch (e.type) {
                    case ACCEPTED:
                        status.setStyle(skin.get("ok", Label.LabelStyle.class));
                        status.setText("Connecté à la partie de " + e.name + ". En attente du lancement par l'hôte…");
                        break;
                    case REFUSED:
                        error(e.name + " a refusé votre demande" + (e.text != null ? " (" + e.text + ")." : "."));
                        client = null;
                        break;
                    case TIMED_OUT:
                        error("Pas de réponse de l'hôte : demande annulée.");
                        client = null;
                        break;
                    case DISCONNECTED:
                        error("Déconnecté de la partie de " + e.name + " (" + e.text + ").");
                        client = null;
                        break;
                    case MESSAGE:
                        if (e.message instanceof hammerfest.net.Net.StartGame) { // l'hôte lance la partie
                            handedOff = true;
                            client = null; // les instantanés suivants sont lus par l'écran de partie
                            game.show(new OnlineClientScreen(game, c, (hammerfest.net.Net.StartGame) e.message));
                            super.render(delta);
                            return;
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        super.render(delta);
    }

    @Override
    protected void back() {
        if (client != null) client.close();
        client = null;
        game.show(new MultiScreen(game));
    }

    @Override
    public void dispose() {
        if (client != null && !handedOff) client.close();
        super.dispose();
    }
}
