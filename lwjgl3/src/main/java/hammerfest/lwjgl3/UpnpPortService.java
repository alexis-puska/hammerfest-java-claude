package hammerfest.lwjgl3;

import hammerfest.net.PortService;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Ouverture du port de jeu par UPnP (bibliothèque weupnp), sur le principe du Bomberman de l'utilisateur :
 * découverte de la box, redirection TCP + UDP avec un bail d'une heure renouvelé tant que la partie dure
 * (une redirection oubliée expire d'elle-même), suppression à la fin, y compris sur fermeture brutale.
 */
public final class UpnpPortService implements PortService {

    static final int LEASE_SECONDS = 3600;
    static final String DESCRIPTION = "Hammerfest (partie en ligne)";

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hammerfest-upnp");
        t.setDaemon(true);
        return t;
    });
    private volatile GatewayDevice gateway;
    private final List<Object[]> mappings = new ArrayList<>(); // {port, protocole}

    public UpnpPortService() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::removeMappings, "hammerfest-upnp-cleanup"));
    }

    @Override
    public void open(int tcpPort, int udpPort, Consumer<String> status) {
        exec.execute(() -> {
            status.accept("Ouverture du port " + tcpPort + "…");
            try {
                GatewayDiscover discover = new GatewayDiscover();
                discover.discover();
                GatewayDevice d = discover.getValidGateway();
                if (d == null) {
                    status.accept("Pas de box UPnP trouvée : partie jouable en réseau local seulement");
                    return;
                }
                gateway = d;
                boolean ok = map(d, tcpPort, "TCP") & map(d, udpPort, "UDP");
                String ip = d.getExternalIPAddress();
                if (ok) {
                    status.accept("Port ouvert — adresse à donner à l'autre joueur : " + ip);
                    exec.scheduleAtFixedRate(() -> renew(tcpPort, udpPort), 50, 50, TimeUnit.MINUTES);
                } else {
                    status.accept("La box a refusé d'ouvrir le port (UPnP désactivé ?) : réseau local seulement");
                }
            } catch (Exception e) {
                status.accept("Ouverture du port impossible (" + e.getClass().getSimpleName() + ") : réseau local seulement");
            }
        });
    }

    /** AddPortMapping avec bail ; repli sur la commande simple (bail illimité) si la box refuse le bail. */
    private boolean map(GatewayDevice d, int port, String protocol) throws Exception {
        String local = d.getLocalAddress().getHostAddress();
        boolean ok;
        try {
            Map<String, String> args = new LinkedHashMap<>();
            args.put("NewRemoteHost", "");
            args.put("NewExternalPort", String.valueOf(port));
            args.put("NewProtocol", protocol);
            args.put("NewInternalPort", String.valueOf(port));
            args.put("NewInternalClient", local);
            args.put("NewEnabled", "1");
            args.put("NewPortMappingDescription", DESCRIPTION);
            args.put("NewLeaseDuration", String.valueOf(LEASE_SECONDS));
            Map<String, String> r = GatewayDevice.simpleUPnPcommand(d.getControlURL(), d.getServiceType(), "AddPortMapping", args);
            ok = r.get("errorCode") == null;
        } catch (Exception e) {
            ok = false;
        }
        if (!ok) ok = d.addPortMapping(port, port, local, protocol, DESCRIPTION);
        if (ok) {
            synchronized (mappings) {
                mappings.add(new Object[]{port, protocol});
            }
        }
        return ok;
    }

    private void renew(int tcpPort, int udpPort) {
        GatewayDevice d = gateway;
        if (d == null) return;
        try {
            map(d, tcpPort, "TCP");
            map(d, udpPort, "UDP");
        } catch (Exception ignored) {
            // la box ne répond plus : le bail expirera de lui-même
        }
    }

    private void removeMappings() {
        GatewayDevice d = gateway;
        if (d == null) return;
        synchronized (mappings) {
            for (Object[] m : mappings) {
                try {
                    d.deletePortMapping((Integer) m[0], (String) m[1]);
                } catch (Exception ignored) {
                    // déjà supprimée ou box injoignable
                }
            }
            mappings.clear();
        }
    }

    @Override
    public void close() {
        exec.execute(this::removeMappings);
    }
}
