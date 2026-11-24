package hammerfest.net;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Hôte d'une partie en ligne (2 joueurs) : annonce la partie sur le réseau local sous le nom du joueur,
 * reçoit les demandes de connexion et les présente une à une ({@link NetEvent.Type#JOIN_ASKED}) ;
 * l'interface appelle {@link #accept()} ou {@link #reject(String)}. Les demandes suivantes attendent leur tour,
 * et sont refusées automatiquement une fois la partie complète.
 */
public final class HostSession implements Closeable {

    private final Server server = new Server(Net.WRITE_BUFFER, Net.OBJECT_BUFFER);
    private final String hostName;
    private final ConcurrentLinkedQueue<NetEvent> events = new ConcurrentLinkedQueue<>();
    private LanDiscovery.Responder responder;

    /** Demande présentée à l'hôte, demandes en attente, joueur accepté. */
    private Connection pending;
    private final Deque<Object[]> waiting = new ArrayDeque<>();
    private volatile Connection peer;
    private volatile String peerName;

    public HostSession(String hostName) {
        this.hostName = hostName;
        Net.register(server.getKryo());
        server.addListener(new Listener() {
            @Override
            public void received(Connection c, Object o) {
                onReceived(c, o);
            }

            @Override
            public void disconnected(Connection c) {
                onDisconnected(c);
            }
        });
    }

    /** Ouvre les ports et l'annonce sur le réseau local. */
    public void start(int tcpPort, int udpPort, int discoveryPort) throws IOException {
        server.start();
        server.bind(tcpPort, udpPort);
        try {
            responder = new LanDiscovery.Responder(discoveryPort, tcpPort, hostName);
        } catch (IOException e) {
            responder = null; // découverte indisponible : la connexion par adresse reste possible
        }
    }

    public String hostName() {
        return hostName;
    }

    /** Nom du joueur accepté, ou null. */
    public String peerName() {
        return peerName;
    }

    public boolean hasPeer() {
        return peer != null && peer.isConnected();
    }

    /** Prochain événement, ou null. */
    public NetEvent poll() {
        return events.poll();
    }

    private synchronized void onReceived(Connection c, Object o) {
        if (o instanceof Net.JoinRequest) {
            Net.JoinRequest r = (Net.JoinRequest) o;
            if (r.version != Net.PROTOCOL_VERSION) {
                refuse(c, "Version du jeu différente");
                return;
            }
            if (peer != null) {
                refuse(c, "Partie complète");
                return;
            }
            String name = r.playerName == null || r.playerName.isBlank() ? "Joueur" : r.playerName;
            if (pending == null) {
                pending = c;
                events.add(new NetEvent(NetEvent.Type.JOIN_ASKED, name, null, null));
            } else {
                waiting.add(new Object[]{c, name});
            }
        } else if (c == peer && o instanceof Net.Leave) {
            String name = peerName;
            peer = null;
            peerName = null;
            events.add(new NetEvent(NetEvent.Type.DISCONNECTED, name, ((Net.Leave) o).reason, null));
        } else if (c == peer && !(o instanceof com.esotericsoftware.kryonet.FrameworkMessage)) {
            events.add(new NetEvent(NetEvent.Type.MESSAGE, peerName, null, o));
        }
    }

    private synchronized void onDisconnected(Connection c) {
        if (c == peer) {
            peer = null;
            String name = peerName;
            peerName = null;
            events.add(new NetEvent(NetEvent.Type.DISCONNECTED, name, "Connexion perdue", null));
        } else if (c == pending) {
            pending = null;
            nextRequest();
        } else {
            waiting.removeIf(w -> w[0] == c);
        }
    }

    private void nextRequest() {
        Object[] w = waiting.poll();
        if (w == null) return;
        pending = (Connection) w[0];
        events.add(new NetEvent(NetEvent.Type.JOIN_ASKED, (String) w[1], null, null));
    }

    private void refuse(Connection c, String reason) {
        Net.JoinResponse r = new Net.JoinResponse();
        r.accepted = false;
        r.hostName = hostName;
        r.reason = reason;
        c.sendTCP(r);
    }

    /** Accepte la demande en cours : le joueur rejoint la partie, les autres demandes sont refusées. */
    public synchronized void accept(String name) {
        if (pending == null) return;
        Net.JoinResponse r = new Net.JoinResponse();
        r.accepted = true;
        r.hostName = hostName;
        pending.sendTCP(r);
        peer = pending;
        peerName = name;
        pending = null;
        while (!waiting.isEmpty()) refuse((Connection) waiting.poll()[0], "Partie complète");
    }

    /** Refuse la demande en cours (la connexion est fermée par le client à la réception) ; passe à la suivante. */
    public synchronized void reject(String reason) {
        if (pending == null) return;
        refuse(pending, reason);
        pending = null;
        nextRequest();
    }

    /** Message fiable (TCP) au joueur accepté. */
    public void send(Object o) {
        Connection p = peer;
        if (p != null) p.sendTCP(o);
    }

    /** Message rapide (UDP) au joueur accepté. */
    public void sendFast(Object o) {
        Connection p = peer;
        if (p != null) p.sendUDP(o);
    }

    @Override
    public void close() {
        if (responder != null) responder.close();
        Connection p = peer;
        if (p != null) {
            Net.Leave l = new Net.Leave();
            l.reason = "L'hôte a quitté la partie";
            p.sendTCP(l);
        }
        server.stop();
    }
}
