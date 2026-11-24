package hammerfest.save;

import java.util.ArrayList;
import java.util.List;

/**
 * Réglages des manettes (save.json) : manette de chaque personnage, action de chacun des 11 boutons réglables
 * (4 de droite, gâchettes hautes et basses, clics des sticks, View/Create), vibrations et zone morte du stick.
 * Plusieurs boutons peuvent porter la même action. Sans dépendance libGDX : la résolution manette ↔ personnage se
 * fait sur les noms des manettes branchées.
 */
public class ControlSettings {

    /** Actions affectables aux boutons. */
    public static final int JUMP = 0, BOMB = 1, BOMB_UP = 2, MAP = 3, PAUSE = 4, NONE = 5;
    public static final String[] ACTION_NAMES = {"Sauter", "Bombe", "Bombe en haut", "Carte", "Pause", "Aucune"};

    /**
     * Boutons réglables, par position (la même sur Xbox et PlayStation) : les 4 de droite, gâchettes hautes (LB/RB,
     * L1/R1) et basses (LT/RT, L2/R2), clics des sticks (LS/RS, L3/R3) et View / Create. La croix, le stick gauche
     * et Menu / Options (pause) ne se règlent pas.
     */
    public static final int FACE_BOTTOM = 0, FACE_RIGHT = 1, FACE_LEFT = 2, FACE_TOP = 3, L1 = 4, R1 = 5, L2 = 6,
            R2 = 7, L3 = 8, R3 = 9, SELECT = 10, BUTTONS = 11;

    /**
     * Disposition d'origine : bas = saut, droite = carte, gauche = bombe, haut = bombe vers le haut ; en plus,
     * gâchette haute droite = bombe, gauche = bombe vers le haut ; les autres boutons ne font rien.
     */
    public static int[] defaultButtons() {
        return new int[]{JUMP, MAP, BOMB, BOMB_UP, BOMB_UP, BOMB, NONE, NONE, NONE, NONE, NONE};
    }

    /**
     * Manette d'Igor (0) et de Sandy (1) : « nom#n » (n-ième manette de ce nom), "" = automatique (ordre de
     * branchement), "-" = aucune (clavier).
     */
    public String[] assigned = {"", ""};
    /**
     * Action de chaque bouton (index FACE_*, L1…SELECT), par personnage. Nom JSON « faces » gardé : les anciennes
     * sauvegardes n'avaient que les 4 boutons de droite, complétés par {@link #sanitize()}.
     */
    public int[][] faces = {defaultButtons(), defaultButtons()};
    public boolean vibration = true;
    /** Intensité des vibrations, 0..1. */
    public float vibrationStrength = 0.7f;
    /** Zone morte du stick, fraction de la course (0.05..0.9). */
    public float deadZone = 0.45f;

    public ControlSettings sanitize() {
        if (assigned == null || assigned.length != 2) assigned = new String[]{"", ""};
        for (int i = 0; i < 2; i++) if (assigned[i] == null) assigned[i] = "";
        if (faces == null || faces.length != 2) faces = new int[][]{defaultButtons(), defaultButtons()};
        for (int i = 0; i < 2; i++) faces[i] = repair(faces[i]);
        vibrationStrength = Math.max(0, Math.min(1, vibrationStrength));
        deadZone = Math.max(0.05f, Math.min(0.9f, deadZone));
        return this;
    }

    /** Réglage valide d'un personnage : ancien réglage à 4 boutons complété, valeur hors limites = réglage d'origine. */
    static int[] repair(int[] a) {
        int[] def = defaultButtons();
        if (a == null || (a.length != 4 && a.length != BUTTONS)) return def;
        for (int v : a) if (v < 0 || v >= ACTION_NAMES.length) return def;
        if (a.length == BUTTONS) return a;
        System.arraycopy(a, 0, def, 0, 4);
        return def;
    }

    /** Donne l'action {@code action} au bouton {@code button} de {@code pid}. */
    public void setButton(int pid, int button, int action) {
        faces[pid][button] = action;
    }

    /** Aucun bouton de {@code pid} ne porte l'action {@code action}. */
    public boolean missing(int pid, int action) {
        for (int a : faces[pid]) if (a == action) return false;
        return true;
    }

    /** Clés « nom#n » des manettes branchées, dans l'ordre de branchement. */
    public static List<String> keys(List<String> names) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            int n = 0;
            for (int j = 0; j < i; j++) if (names.get(j).equals(names.get(i))) n++;
            out.add(names.get(i) + "#" + n);
        }
        return out;
    }

    /**
     * Manette (index dans {@code names}) de chaque personnage : celles choisies si elles sont branchées, puis les
     * personnages en automatique prennent les manettes restantes dans l'ordre ; -1 = aucune (clavier).
     */
    public int[] resolve(List<String> names) {
        List<String> keys = keys(names);
        int[] out = {-1, -1};
        boolean[] used = new boolean[names.size()];
        for (int pid = 0; pid < 2; pid++) {
            int i = keys.indexOf(assigned[pid]);
            if (i >= 0 && !used[i]) {
                out[pid] = i;
                used[i] = true;
            }
        }
        for (int pid = 0; pid < 2; pid++) {
            if (out[pid] >= 0 || "-".equals(assigned[pid])) continue;
            for (int i = 0; i < names.size(); i++) {
                if (!used[i]) {
                    out[pid] = i;
                    used[i] = true;
                    break;
                }
            }
        }
        return out;
    }

    /** Affecte la manette de clé {@code key} à {@code pid} ; si l'autre personnage l'avait, ils échangent. */
    public void assign(int pid, String key, List<String> names) {
        int[] now = resolve(names);
        List<String> keys = keys(names);
        int other = 1 - pid;
        int idx = keys.indexOf(key);
        if (idx >= 0 && now[other] == idx) {
            assigned[other] = now[pid] >= 0 ? keys.get(now[pid]) : "-";
        }
        assigned[pid] = key;
    }

    /** Manette PlayStation (libellés Croix / Rond / Carré / Triangle) d'après son nom. */
    public static boolean isPlayStation(String name) {
        String n = name == null ? "" : name.toLowerCase();
        return n.contains("dualsense") || n.contains("dualshock") || n.contains("playstation") || n.contains("ps4")
                || n.contains("ps5") || n.contains("ps3") || n.equals("wireless controller") || n.contains("sony");
    }
}
