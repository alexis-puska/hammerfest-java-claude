package hammerfest.net;

/** Événement réseau, mis en file par le thread réseau et lu par le thread de rendu ({@code poll()}). */
public final class NetEvent {

    public enum Type {
        /** Hôte : un joueur demande à rejoindre ({@link #name}). */
        JOIN_ASKED,
        /** Client : l'hôte {@link #name} a accepté. */
        ACCEPTED,
        /** Client : l'hôte {@link #name} a refusé ({@link #text} = raison). */
        REFUSED,
        /** Client : pas de réponse de l'hôte dans le délai. */
        TIMED_OUT,
        /** Connexion perdue ou fermée par l'autre joueur ({@link #text} = raison). */
        DISCONNECTED,
        /** Message de jeu reçu ({@link #message}). */
        MESSAGE
    }

    public final Type type;
    public final String name;
    public final String text;
    public final Object message;

    NetEvent(Type type, String name, String text, Object message) {
        this.type = type;
        this.name = name;
        this.text = text;
        this.message = message;
    }

    @Override
    public String toString() {
        return type + (name != null ? " " + name : "") + (text != null ? " (" + text + ")" : "");
    }
}
