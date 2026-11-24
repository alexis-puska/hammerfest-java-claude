package hammerfest.net;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Joueur qui rejoint une partie : se connecte à l'hôte, envoie sa demande (nom, identifiant, version)
 * et attend la réponse ({@link NetEvent.Type#ACCEPTED} avec le nom de l'hôte, ou {@code REFUSED}).
 * Sans réponse sous {@link Net#JOIN_TIMEOUT_MS}, la demande est annulée ({@code TIMED_OUT}).
 */
public final class ClientSession implements Closeable {

    private final Client client = new Client(Net.WRITE_BUFFER, Net.OBJECT_BUFFER);
    private final ConcurrentLinkedQueue<NetEvent> events = new ConcurrentLinkedQueue<>();
    private volatile boolean waitingAnswer, accepted, closing;
    private volatile long askedAt;
    private volatile String hostName;

    public ClientSession() {
        Net.register(client.getKryo());
        client.addListener(new Listener() {
            @Override
            public void received(Connection c, Object o) {
                onReceived(o);
            }

            @Override
            public void disconnected(Connection c) {
                if (closing) return;
                if (waitingAnswer || accepted) {
                    waitingAnswer = false;
                    accepted = false;
                    events.add(new NetEvent(NetEvent.Type.DISCONNECTED, hostName, "Connexion perdue", null));
                }
            }
        });
    }

    /**
     * Se connecte et envoie la demande. Bloquant (quelques secondes au plus) : à appeler hors du thread de rendu.
     *
     * @throws IOException hôte injoignable
     */
    public void connect(InetAddress host, int tcpPort, int udpPort, String playerName, String playerId) throws IOException {
        client.start();
        client.connect(5000, host, tcpPort, udpPort);
        Net.JoinRequest r = new Net.JoinRequest();
        r.playerName = playerName;
        r.playerId = playerId;
        r.version = Net.PROTOCOL_VERSION;
        waitingAnswer = true;
        askedAt = System.currentTimeMillis();
        client.sendTCP(r);
    }

    private void onReceived(Object o) {
        if (o instanceof Net.JoinResponse) {
            Net.JoinResponse r = (Net.JoinResponse) o;
            if (!waitingAnswer) return;
            waitingAnswer = false;
            hostName = r.hostName;
            if (r.accepted) {
                accepted = true;
                events.add(new NetEvent(NetEvent.Type.ACCEPTED, r.hostName, null, null));
            } else {
                events.add(new NetEvent(NetEvent.Type.REFUSED, r.hostName, r.reason, null));
                closeQuietly();
            }
        } else if (o instanceof Net.Leave) {
            accepted = false;
            events.add(new NetEvent(NetEvent.Type.DISCONNECTED, hostName, ((Net.Leave) o).reason, null));
            closeQuietly();
        } else if (accepted && !(o instanceof com.esotericsoftware.kryonet.FrameworkMessage)) {
            events.add(new NetEvent(NetEvent.Type.MESSAGE, hostName, null, o));
        }
    }

    /** Prochain événement (et annulation de la demande si l'hôte n'a pas répondu à temps), ou null. */
    public NetEvent poll() {
        if (waitingAnswer && System.currentTimeMillis() - askedAt > Net.JOIN_TIMEOUT_MS) {
            waitingAnswer = false;
            events.add(new NetEvent(NetEvent.Type.TIMED_OUT, null, "Pas de réponse de l'hôte", null));
            closeQuietly();
        }
        return events.poll();
    }

    public String hostName() {
        return hostName;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void send(Object o) {
        if (client.isConnected()) client.sendTCP(o);
    }

    public void sendFast(Object o) {
        if (client.isConnected()) client.sendUDP(o);
    }

    private void closeQuietly() {
        closing = true;
        new Thread(this::stopOnce, "hammerfest-client-stop").start(); // pas depuis le thread réseau lui-même
    }

    private final Object stopLock = new Object();
    private boolean stopped;

    /**
     * Arrête le client une seule fois. KryoNet ne supporte pas deux arrêts simultanés : départ de l'hôte reçu
     * ({@link #closeQuietly()}, autre thread) pendant que l'écran ferme la session ({@link #close()}) →
     * NullPointerException dans UdpConnection.close (champ relu entre son test et son usage), qui faisait planter
     * le jeu quand l'hôte quittait la partie.
     */
    private void stopOnce() {
        synchronized (stopLock) {
            if (stopped) return;
            stopped = true;
            try {
                client.stop();
            } catch (RuntimeException e) {
                // connexion déjà fermée par le thread réseau de KryoNet : rien à faire
            }
        }
    }

    @Override
    public void close() {
        closing = true;
        synchronized (stopLock) {
            if (!stopped && accepted && client.isConnected()) {
                Net.Leave l = new Net.Leave();
                l.reason = "Le joueur a quitté la partie";
                try {
                    client.sendTCP(l);
                } catch (RuntimeException e) {
                    // connexion coupée entre-temps
                }
            }
        }
        stopOnce();
    }
}
