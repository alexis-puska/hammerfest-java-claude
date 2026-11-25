package hammerfest.net;

import hammerfest.ui.Tr;

import java.io.Closeable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Découverte des parties sur le réseau local par diffusion UDP : le client envoie « HAMMERFEST? », chaque hôte
 * répond « HAMMERFEST|version|port|nom de l'hôte ». La liste affiche alors « Partie de &lt;nom&gt; ».
 */
public final class LanDiscovery {

    static final String QUERY = "HAMMERFEST?";
    static final String PREFIX = "HAMMERFEST|";

    /** Partie trouvée sur le réseau local. */
    public static final class HostInfo {
        public final InetAddress address;
        public final int tcpPort;
        public final int version;
        public final String hostName;

        HostInfo(InetAddress address, int tcpPort, int version, String hostName) {
            this.address = address;
            this.tcpPort = tcpPort;
            this.version = version;
            this.hostName = hostName;
        }

        @Override
        public String toString() {
            return Tr.t("Partie de ") + hostName + " (" + address.getHostAddress() + ")";
        }
    }

    /** Côté hôte : répond aux recherches tant qu'il est ouvert (thread en arrière-plan). */
    public static final class Responder implements Closeable {
        private final DatagramSocket socket;
        private volatile String hostName;
        private final int tcpPort;
        private final Thread thread;

        public Responder(int discoveryPort, int tcpPort, String hostName) throws java.io.IOException {
            this.tcpPort = tcpPort;
            this.hostName = hostName;
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress(discoveryPort));
            thread = new Thread(this::run, "hammerfest-discovery");
            thread.setDaemon(true);
            thread.start();
        }

        private void run() {
            byte[] buf = new byte[256];
            while (!socket.isClosed()) {
                try {
                    DatagramPacket in = new DatagramPacket(buf, buf.length);
                    socket.receive(in);
                    String q = new String(in.getData(), 0, in.getLength(), StandardCharsets.UTF_8);
                    if (!QUERY.equals(q)) continue;
                    byte[] out = (PREFIX + Net.PROTOCOL_VERSION + "|" + tcpPort + "|" + hostName).getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(out, out.length, in.getAddress(), in.getPort()));
                } catch (java.io.IOException e) {
                    if (socket.isClosed()) return;
                }
            }
        }

        @Override
        public void close() {
            socket.close();
        }
    }

    /**
     * Côté client : diffuse une recherche sur toutes les interfaces (et sur localhost) puis collecte les réponses
     * pendant {@code timeoutMs}. Bloquant : à appeler hors du thread de rendu.
     */
    public static List<HostInfo> discover(int discoveryPort, int timeoutMs) {
        Map<String, HostInfo> found = new LinkedHashMap<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            byte[] q = QUERY.getBytes(StandardCharsets.UTF_8);
            for (InetAddress target : targets()) {
                try {
                    socket.send(new DatagramPacket(q, q.length, target, discoveryPort));
                } catch (java.io.IOException ignored) {
                    // interface sans diffusion : on passe à la suivante
                }
            }
            long end = System.currentTimeMillis() + timeoutMs;
            byte[] buf = new byte[512];
            while (true) {
                int left = (int) (end - System.currentTimeMillis());
                if (left <= 0) break;
                socket.setSoTimeout(left);
                DatagramPacket in = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(in);
                } catch (SocketTimeoutException e) {
                    break;
                }
                String r = new String(in.getData(), 0, in.getLength(), StandardCharsets.UTF_8);
                if (!r.startsWith(PREFIX)) continue;
                String[] p = r.substring(PREFIX.length()).split("\\|", 3);
                if (p.length < 3) continue;
                try {
                    HostInfo h = new HostInfo(in.getAddress(), Integer.parseInt(p[1]), Integer.parseInt(p[0]), p[2]);
                    found.putIfAbsent(h.address.getHostAddress() + ":" + h.tcpPort, h);
                } catch (NumberFormatException ignored) {
                    // réponse malformée
                }
            }
        } catch (java.io.IOException e) {
            return Collections.emptyList();
        }
        return new ArrayList<>(found.values());
    }

    /** Adresses de diffusion des interfaces actives, plus 255.255.255.255 et localhost (deux instances sur un même PC). */
    static List<InetAddress> targets() {
        List<InetAddress> out = new ArrayList<>();
        try {
            out.add(InetAddress.getByName("255.255.255.255"));
            out.add(InetAddress.getLoopbackAddress());
            Enumeration<NetworkInterface> it = NetworkInterface.getNetworkInterfaces();
            while (it != null && it.hasMoreElements()) {
                NetworkInterface ni = it.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress a : ni.getInterfaceAddresses()) {
                    if (a.getBroadcast() != null) out.add(a.getBroadcast());
                }
            }
        } catch (java.io.IOException ignored) {
            // on garde ce qu'on a
        }
        return out;
    }

    private LanDiscovery() {
    }
}
