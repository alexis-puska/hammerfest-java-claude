package hammerfest.data;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/**
 * Un niveau (ex classe levels.Data d'origine), décodé depuis xml/levels/&lt;set&gt;.xml.
 *
 * <p>La grille fait {@link #WIDTH}x{@link #HEIGHT} cases. Elle est stockée en JSON sous forme
 * de lignes lisibles ({@link #rows}, une chaîne par ligne y, valeurs séparées par des espaces) et
 * exposée au code par {@link #tile(int, int)}. Valeurs : 0 vide, &gt;0 dalle, &lt;0 champ spécial
 * (Data.FIELD_* : -6 téléporteur, -7 portail…).</p>
 */
public class LevelData {

    public static final int WIDTH = 20;
    public static final int HEIGHT = 25;

    /** Index du niveau dans son set. */
    public int id;
    public int playerX;
    public int playerY;
    public int skinTiles;
    public int skinBg;
    /** Grille : HEIGHT lignes de WIDTH valeurs. */
    public List<String> rows = new ArrayList<>();
    public List<Bad> badList = new ArrayList<>();
    public List<Slot> specialSlots = new ArrayList<>();
    public List<Slot> scoreSlots = new ArrayList<>();
    /** Script du niveau (pseudo-XML interprété par ScriptEngine), vide si aucun. */
    public String script = "";

    @JsonIgnore
    private int[][] grid;

    /** Ennemi placé dans le niveau. */
    public static class Bad {
        public int id;
        public int x;
        public int y;

        public Bad() {
        }

        public Bad(int id, int x, int y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    /** Emplacement d'apparition d'objet. */
    public static class Slot {
        public int x;
        public int y;

        public Slot() {
        }

        public Slot(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    /** Valeur de la case (x, y) ; hors grille : 0. */
    public int tile(int x, int y) {
        if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) return 0;
        return grid()[x][y];
    }

    /** Grille indexée [x][y] comme dans le code d'origine. */
    public int[][] grid() {
        if (grid == null) grid = parseRows(rows);
        return grid;
    }

    public void setGrid(int[][] map) {
        grid = map;
        rows = formatRows(map);
    }

    static int[][] parseRows(List<String> rows) {
        int[][] g = new int[WIDTH][HEIGHT];
        for (int y = 0; y < Math.min(HEIGHT, rows.size()); y++) {
            String[] cells = rows.get(y).trim().split("\\s+");
            for (int x = 0; x < Math.min(WIDTH, cells.length); x++) {
                if (!cells[x].isEmpty()) g[x][y] = Integer.parseInt(cells[x]);
            }
        }
        return g;
    }

    static List<String> formatRows(int[][] g) {
        List<String> out = new ArrayList<>(HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < WIDTH; x++) {
                String v = String.valueOf(g[x][y]);
                if (x > 0) sb.append(' ');
                for (int pad = v.length(); pad < 3; pad++) sb.append(' ');
                sb.append(v);
            }
            out.add(sb.toString());
        }
        return out;
    }
}
