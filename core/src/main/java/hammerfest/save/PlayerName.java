package hammerfest.save;

/**
 * Règles du nom de joueur (menu Profil). Le nom identifie le joueur en multi :
 * annonce des parties, demande de connexion acceptée/refusée par l'hôte.
 */
public final class PlayerName {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 16;

    /** Retire les espaces de début/fin et réduit les espaces internes multiples. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    /** null si le nom (déjà normalisé ou non) est valide, sinon le message d'erreur à afficher. */
    public static String validate(String raw) {
        String name = normalize(raw);
        if (name.length() < MIN_LENGTH) return "Le nom doit faire au moins " + MIN_LENGTH + " caractères.";
        if (name.length() > MAX_LENGTH) return "Le nom doit faire au plus " + MAX_LENGTH + " caractères.";
        for (int i = 0; i < name.length(); i++) {
            if (!isAllowed(name.charAt(i))) {
                return "Caractère interdit : « " + name.charAt(i) + " » (lettres, chiffres, espace, - et _).";
            }
        }
        return null;
    }

    public static boolean isValid(String raw) {
        return validate(raw) == null;
    }

    /**
     * Caractères acceptés à la saisie : lettres (accents Latin-1 compris), chiffres, espace, - et _.
     * Limité à Latin-1 pour rester affichable par la police du jeu chez tous les joueurs.
     */
    public static boolean isAllowed(char c) {
        if (c > 0xFF) return false;
        return Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_';
    }

    private PlayerName() {
    }
}
