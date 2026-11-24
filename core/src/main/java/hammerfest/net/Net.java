package hammerfest.net;

import com.esotericsoftware.kryo.Kryo;

/**
 * Constantes et messages du jeu en ligne (KryoNet). Les classes de messages sont partagées par l'hôte et
 * le client et enregistrées dans le même ordre des deux côtés ({@link #register}).
 */
public final class Net {

    /** Ports par défaut : connexion (TCP), entrées de jeu (UDP), découverte sur le réseau local (UDP). */
    public static final int TCP_PORT = 54555, UDP_PORT = 54557, DISCOVERY_PORT = 54556;
    /**
     * Version du protocole : deux versions différentes ne peuvent pas jouer ensemble (refus à la connexion). À
     * augmenter à chaque changement des messages, RenderFrame compris (2 : RenderFrame.backEntities, Tuberculoz).
     * Volontairement pas une constante de compilation (Integer.valueOf) : javac recopie les constantes dans les
     * classes qui les lisent, et la compilation incrémentale de Gradle avait laissé l'ancienne valeur dans
     * LanDiscovery (annonce « version 1 », liste « version différente » entre deux exe identiques).
     */
    public static final int PROTOCOL_VERSION = Integer.valueOf(2);
    /** Délai de réponse de l'hôte à une demande de connexion (ms). */
    public static final long JOIN_TIMEOUT_MS = 30_000;
    /** Tampons KryoNet : un instantané de dessin pèse quelques ko (le défaut, 2 ko, ne suffit pas). */
    public static final int WRITE_BUFFER = 512 * 1024, OBJECT_BUFFER = 128 * 1024;

    /** Le client se présente (nom saisi dans Profil, identifiant unique de la sauvegarde). */
    public static class JoinRequest {
        public String playerName;
        public String playerId;
        public int version;
    }

    /** Réponse de l'hôte : accepté ou refusé (raison facultative), avec le nom de l'hôte. */
    public static class JoinResponse {
        public boolean accepted;
        public String hostName;
        public String reason;
    }

    /** L'hôte lance la partie : mode, options, graine du hasard et niveau de départ, identiques des deux côtés. */
    public static class StartGame {
        public String modeId;
        public String[] options;
        public long seed;
        public int startLevel;
        /** Numéro de joueur attribué au client (0 = hôte). */
        public int clientPid = 1;
    }

    /** Entrées d'un joueur pour un cycle de jeu. */
    public static class InputMsg {
        public int pid;
        public int tick;
        public int bits;
    }

    /** Un joueur quitte la partie ou le lobby. */
    public static class Leave {
        public String reason;
    }

    public static void register(Kryo kryo) {
        kryo.register(JoinRequest.class);
        kryo.register(JoinResponse.class);
        kryo.register(StartGame.class);
        kryo.register(String[].class);
        kryo.register(InputMsg.class);
        kryo.register(Leave.class);
        kryo.register(RenderFrame.class);
        kryo.register(int[].class);
        kryo.register(short[].class);
        kryo.register(float[].class);
        kryo.register(long[].class);
        kryo.register(byte[].class);
        kryo.register(boolean[].class);
    }

    private Net() {
    }
}
