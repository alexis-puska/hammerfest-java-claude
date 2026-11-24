package hammerfest.tools;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;

import java.util.List;
import java.util.Map;

/**
 * Convertit un fichier xml/levels/&lt;set&gt;.xml (niveaux PersistCodec séparés par ":") en {@link LevelSet}.
 * Reprend SetManager.unserialize : les murs (2) sont convertis en sol (1) comme convertWalls.
 * Les identifiants préfixés par "$" (marqueurs anti-obfuscation) sont débarrassés du "$".
 */
public final class LevelConverter {

    static final int GROUND = 1;
    static final int WALL = 2;

    public static LevelSet convert(String name, String fileContent) {
        LevelSet set = new LevelSet(name);
        String[] raw = fileContent.trim().split(":");
        for (int id = 0; id < raw.length; id++) {
            set.levels.add(convertLevel(id, raw[id].trim()));
        }
        return set;
    }

    @SuppressWarnings("unchecked")
    static LevelData convertLevel(int id, String raw) {
        LevelData l = new LevelData();
        l.id = id;
        if (raw.isEmpty()) {
            l.setGrid(new int[LevelData.WIDTH][LevelData.HEIGHT]);
            return l;
        }
        Map<String, Object> o = (Map<String, Object>) PersistCodec.decode(raw);
        l.playerX = intField(o, "playerX");
        l.playerY = intField(o, "playerY");
        l.skinTiles = intField(o, "skinTiles");
        l.skinBg = intField(o, "skinBg");
        Object script = field(o, "script");
        l.script = script == null ? "" : stripDollars(script.toString());

        int[][] grid = new int[LevelData.WIDTH][LevelData.HEIGHT];
        List<Object> map = (List<Object>) field(o, "map");
        if (map != null) {
            for (int x = 0; x < Math.min(LevelData.WIDTH, map.size()); x++) {
                List<Object> col = (List<Object>) map.get(x);
                if (col == null) continue;
                for (int y = 0; y < Math.min(LevelData.HEIGHT, col.size()); y++) {
                    int v = toInt(col.get(y));
                    grid[x][y] = v == WALL ? GROUND : v;
                }
            }
        }
        l.setGrid(grid);

        for (Map<String, Object> b : objects(field(o, "badList"))) {
            l.badList.add(new LevelData.Bad(intField(b, "id"), intField(b, "x"), intField(b, "y")));
        }
        for (Map<String, Object> s : objects(field(o, "specialSlots"))) {
            l.specialSlots.add(new LevelData.Slot(intField(s, "x"), intField(s, "y")));
        }
        for (Map<String, Object> s : objects(field(o, "scoreSlots"))) {
            l.scoreSlots.add(new LevelData.Slot(intField(s, "x"), intField(s, "y")));
        }
        return l;
    }

    /**
     * Retire le "$" placé devant les identifiants (balises, attributs, noms de clips) du script
     * et normalise les fins de ligne (\r de Flash → \n).
     */
    static String stripDollars(String s) {
        return s.replaceAll("\\$(?=[A-Za-z_])", "").replace("\r\n", "\n").replace('\r', '\n');
    }

    private static Object field(Map<String, Object> o, String name) {
        return o.containsKey("$" + name) ? o.get("$" + name) : o.get(name);
    }

    private static int intField(Map<String, Object> o, String name) {
        return toInt(field(o, name));
    }

    private static int toInt(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String && !((String) v).isEmpty()) return Integer.parseInt((String) v);
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objects(Object list) {
        if (!(list instanceof List)) return List.of();
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object e : (List<Object>) list) if (e instanceof Map) out.add((Map<String, Object>) e);
        return out;
    }

    private LevelConverter() {
    }
}
