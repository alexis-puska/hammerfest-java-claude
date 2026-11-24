package hammerfest.sim.level;

import java.util.ArrayList;
import java.util.List;

/**
 * Découpe la grille d'un niveau en plateformes horizontales et colonnes verticales,
 * portage fidèle de {@code levels.View.attach()} / {@code isWall()}.
 * Pas de dépendance libGDX : réutilisable par la simulation et les tests.
 */
public final class TileLayout {

    /** Bloc à dessiner, en cases : origine (x, y) et longueur (vers la droite, ou vers le bas si vertical). */
    public static final class Block {
        public final int x;
        public final int y;
        public final int length;
        public final boolean vertical;

        public Block(int x, int y, int length, boolean vertical) {
            this.x = x;
            this.y = y;
            this.length = length;
            this.vertical = vertical;
        }

        @Override
        public String toString() {
            return (vertical ? "col" : "tile") + "(" + x + "," + y + " x" + length + ")";
        }
    }

    /** Champ spécial (valeur négative de la grille) : segment horizontal ou vertical de même id. */
    public static final class Field {
        public final int id;
        public final int x;
        public final int y;
        public final int length;
        public final boolean vertical;

        public Field(int id, int x, int y, int length, boolean vertical) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.length = length;
            this.vertical = vertical;
        }
    }

    /** @param map grille [x][y] de taille width x height */
    public static List<Block> blocks(int[][] map, int width, int height) {
        List<Block> out = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            boolean tracing = false;
            int startX = 0;
            for (int x = 0; x <= width; x++) {
                if (!tracing && get(map, x, y, width, height) > 0) {
                    startX = x;
                    tracing = true;
                }
                if (tracing && (get(map, x, y, width, height) <= 0 || x == width)) {
                    int wid = x - startX;
                    if (wid == 1 && isWall(map, x - 1, y, width, height)) {
                        // Colonne : tracée depuis sa case la plus haute uniquement.
                        if (!isWall(map, x - 1, y - 1, width, height)) {
                            int hei = 0;
                            int vty = y;
                            while (isWall(map, x - 1, vty, width, height)) {
                                hei++;
                                vty++;
                            }
                            out.add(new Block(startX, y, hei, hei != 1));
                        }
                    } else {
                        out.add(new Block(startX, y, wid, false));
                    }
                    tracing = false;
                }
            }
        }
        return out;
    }

    /** Segments de champs spéciaux, comme View.attachField (horizontal prioritaire). */
    public static List<Field> fields(int[][] map, int width, int height) {
        List<Field> out = new ArrayList<>();
        boolean[][] done = new boolean[width][height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int id = map[x][y];
                if (id >= 0 || done[x][y]) continue;
                if (get(map, x + 1, y, width, height) == id) {
                    int i = x;
                    while (i < width && map[i][y] == id) done[i++][y] = true;
                    out.add(new Field(id, x, y, i - x, false));
                } else if (get(map, x, y + 1, width, height) == id) {
                    int i = y;
                    while (i < height && map[x][i] == id) done[x][i++] = true;
                    out.add(new Field(id, x, y, i - y, true));
                } else {
                    done[x][y] = true;
                    out.add(new Field(id, x, y, 1, false));
                }
            }
        }
        return out;
    }

    /** Case pleine sans voisin plein à gauche ni à droite (View.isWall). */
    static boolean isWall(int[][] map, int x, int y, int width, int height) {
        return get(map, x, y, width, height) > 0
                && get(map, x - 1, y, width, height) <= 0
                && get(map, x + 1, y, width, height) <= 0;
    }

    /** Hors grille = 0 (équivalent des undefined de Flash). */
    static int get(int[][] map, int x, int y, int width, int height) {
        return x < 0 || y < 0 || x >= width || y >= height ? 0 : map[x][y];
    }

    /** Skin des dalles horizontales (View.getTileSkinId). */
    public static int tileSkin(int skinTiles) {
        return skinTiles >= 100 ? skinTiles % 100 : skinTiles;
    }

    /** Skin des colonnes (View.getColumnSkinId). */
    public static int columnSkin(int skinTiles) {
        return skinTiles >= 100 ? skinTiles / 100 : skinTiles;
    }

    private TileLayout() {
    }
}
