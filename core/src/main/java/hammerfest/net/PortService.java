package hammerfest.net;

import java.util.function.Consumer;

/**
 * Ouverture du port de jeu sur la box (UPnP) quand on héberge une partie. Implémentée par la plateforme
 * (desktop : {@code UpnpPortService}, bibliothèque weupnp) pour ne pas lier le cœur du jeu à une plateforme.
 */
public interface PortService {

    /**
     * Ouvre TCP {@code tcpPort} et UDP {@code udpPort} en arrière-plan ; {@code status} reçoit l'état
     * (« ouverture du port… », « port ouvert », « échec… »), depuis un autre thread.
     */
    void open(int tcpPort, int udpPort, Consumer<String> status);

    /** Referme les redirections ouvertes (fin de partie, fermeture du jeu). */
    void close();

    /** Pas d'UPnP (plateforme sans support) : jeu en réseau local ou par adresse seulement. */
    PortService NONE = new PortService() {
        @Override
        public void open(int tcpPort, int udpPort, Consumer<String> status) {
            status.accept("Ouverture automatique du port indisponible sur cette plateforme");
        }

        @Override
        public void close() {
        }
    };
}
