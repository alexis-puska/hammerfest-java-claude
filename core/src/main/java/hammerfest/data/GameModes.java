package hammerfest.data;

import java.util.List;

/**
 * Modes et options proposés dans l'écran « Jouer ». Les identifiants sont ceux des
 * {@code <give mode=…/>} et {@code <give option=…/>} de quests.xml ; les libellés
 * reprennent les noms cités dans les descriptions de quêtes.
 */
public final class GameModes {

    public static final class Mode {
        public final String id;
        public final String label;
        /** Identifiant de mode à débloquer par une quête, ou null si toujours disponible. */
        public final String unlockMode;
        public final String levelSet;
        public final int players;
        public final List<Option> options;

        Mode(String id, String label, String unlockMode, String levelSet, int players, List<Option> options) {
            this.id = id;
            this.label = label;
            this.unlockMode = unlockMode;
            this.levelSet = levelSet;
            this.players = players;
            this.options = options;
        }
    }

    public static final class Option {
        public final String id;
        public final String label;

        Option(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    public static final List<Mode> MODES = List.of(
            new Mode("adventure", "Aventure", null, "adventure", 1, List.of(
                    new Option("mirror", "Miroir"),
                    new Option("nightmare", "Cauchemar"),
                    new Option("ninja", "Ninjutsu"),
                    new Option("bombexpert", "Explosifs instables"))),
            new Mode("tutorial", "Apprentissage", null, "tutorial", 1, List.of()),
            new Mode("timeattack", "Time Attack", "timeattack", "timeAttack", 1, List.of()),
            new Mode("timeattackmulti", "Time Attack à deux (même PC)", "timeattack", "multiTime", 2, List.of()),
            new Mode("bossrush", "Tubercourse (Boss Rush)", null, "adventure", 1, List.of()),
            new Mode("multicoop", "Multi coopératif (2 joueurs, même PC)", "multicoop", "adventure", 2, List.of(
                    new Option("mirrormulti", "Miroir"),
                    new Option("nightmaremulti", "Cauchemar"),
                    new Option("lifesharing", "Partage de vies"))),
            new Mode("soccer", "SoccerFest (2 joueurs, même PC)", null, "soccer", 2, List.of(
                    new Option("kickcontrol", "Contrôle du ballon"))));

    /** Option passive « Tornade » (boost de vitesse en début de partie), toujours active une fois débloquée. */
    public static final String PASSIVE_BOOST = "boost";

    public static Mode mode(String id) {
        for (Mode m : MODES) if (m.id.equals(id)) return m;
        return MODES.get(0);
    }

    private GameModes() {
    }
}
