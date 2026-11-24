package hammerfest.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import hammerfest.HammerfestGame;
import hammerfest.net.HostSession;
import hammerfest.net.Net;
import hammerfest.net.NetEvent;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Lobby de l'hôte : la partie est annoncée comme « Partie de &lt;nom&gt; » sur le réseau local, le port est ouvert
 * sur la box (UPnP) et chaque demande de connexion est présentée avec Accepter / Refuser.
 */
public class HostLobbyScreen extends MenuScreen {

    private final HostSession host;
    private final Label status = label("", "dim");
    private final Label upnp = label("", "dim");
    private final Table request = new Table();
    private final Label player2 = label("Joueur 2 : en attente d'un joueur…", "default");
    private String askedName;
    private boolean leaving;
    /** Session confiée à l'écran de partie (ne pas la fermer en quittant le lobby). */
    private boolean handedOff;
    private final Table launch = new Table();

    public HostLobbyScreen(HammerfestGame game) {
        super(game);
        host = new HostSession(game.save.playerName);
        Table panel = panel("Partie de " + game.save.playerName);

        panel.add(label("Adresses de cette machine (réseau local) :", "heading")).padBottom(4).row();
        panel.add(label(String.join("   ", localAddresses()), "default")).padBottom(10).row();
        upnp.setWrap(true);
        panel.add(upnp).width(560).padBottom(10).row();
        panel.add(label("Joueur 1 : " + game.save.playerName + " (vous)", "default")).padBottom(4).row();
        panel.add(player2).padBottom(12).row();

        request.setVisible(false);
        panel.add(request).width(560).padBottom(12).row();
        launch.setVisible(false);
        panel.add(launch).padBottom(12).row();

        status.setWrap(true);
        panel.add(status).width(560).row();
        panel.add().expandY().row();
        addBackButton(panel);

        try {
            host.start(Net.TCP_PORT, Net.UDP_PORT, Net.DISCOVERY_PORT);
            status.setText("Partie annoncée sur le réseau local. Les autres joueurs la voient dans « Rejoindre une partie ».");
            game.ports.open(Net.TCP_PORT, Net.UDP_PORT, s -> Gdx.app.postRunnable(() -> upnp.setText(s)));
        } catch (Exception e) {
            status.setText("Impossible d'ouvrir la partie (port " + Net.TCP_PORT + " déjà utilisé ?) : " + e.getMessage());
            status.setStyle(skin.get("error", Label.LabelStyle.class));
        }
    }

    /** Adresses IPv4 des interfaces actives (à donner à un joueur qui se connecte par adresse). */
    static List<String> localAddresses() {
        List<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> it = NetworkInterface.getNetworkInterfaces();
            while (it != null && it.hasMoreElements()) {
                NetworkInterface ni = it.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                    InetAddress ad = a.nextElement();
                    if (ad instanceof Inet4Address) out.add(ad.getHostAddress());
                }
            }
        } catch (Exception ignored) {
            // pas d'adresse lisible
        }
        if (out.isEmpty()) out.add("inconnue");
        return out;
    }

    /** Encadré « &lt;nom&gt; veut rejoindre la partie » avec Accepter / Refuser. */
    private void showRequest(String name) {
        askedName = name;
        request.clearChildren();
        request.setBackground(skin.getDrawable("panel"));
        request.pad(10);
        request.add(label(name + " veut rejoindre la partie", "heading")).colspan(2).padBottom(8).row();
        TextButton yes = button("Accepter", () -> {
            host.accept(askedName);
            player2.setText("Joueur 2 : " + askedName);
            player2.setStyle(skin.get("ok", Label.LabelStyle.class));
            status.setText(askedName + " a rejoint la partie. Choisissez le mode de jeu.");
            request.setVisible(false);
            showLaunch();
        });
        TextButton no = button("Refuser", () -> {
            host.reject("Refusé par l'hôte");
            status.setText("Demande de " + askedName + " refusée.");
            request.setVisible(false);
        });
        request.add(yes).width(200).padRight(12);
        request.add(no).width(200);
        request.setVisible(true);
    }

    @Override
    public void render(float delta) {
        for (NetEvent e = host.poll(); e != null; e = host.poll()) {
            switch (e.type) {
                case JOIN_ASKED:
                    showRequest(e.name);
                    break;
                case DISCONNECTED:
                    launch.setVisible(false);
                    player2.setText("Joueur 2 : en attente d'un joueur…");
                    player2.setStyle(skin.get("default", Label.LabelStyle.class));
                    status.setText((e.name != null ? e.name : "Le joueur") + " est parti (" + e.text + ").");
                    break;
                default:
                    break;
            }
        }
        super.render(delta);
    }

    /** Modes à deux joueurs proposés en ligne. */
    private static final String[] ONLINE_MODES = {"multicoop", "soccer", "timeattackmulti"};

    private void showLaunch() {
        launch.clearChildren();
        launch.add(label("Lancer :", "heading")).padRight(10);
        for (String id : ONLINE_MODES) {
            hammerfest.data.GameModes.Mode m = hammerfest.data.GameModes.mode(id);
            launch.add(button(m.label.replace(" (2 joueurs, même PC)", "").replace(" (même PC)", ""), "small",
                    () -> start(m))).height(30).padRight(6);
        }
        launch.setVisible(true);
    }

    private void start(hammerfest.data.GameModes.Mode m) {
        if (!host.hasPeer()) return;
        handedOff = true;
        game.show(new GameScreen(game, m, new java.util.LinkedHashSet<>(), System.nanoTime(), host));
    }

    @Override
    protected void back() {
        close();
        game.show(new MultiScreen(game));
    }

    private void close() {
        if (leaving) return;
        leaving = true;
        host.close();
        game.ports.close();
    }

    @Override
    public void dispose() {
        if (!handedOff) close();
        super.dispose();
    }
}
