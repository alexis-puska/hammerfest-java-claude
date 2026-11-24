package hammerfest.sim.level;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TileLayoutTest {

    /** Grille à partir de lignes de texte ('#' plein, '.' vide, 'T' téléporteur -6). */
    static int[][] grid(String... rows) {
        int w = rows[0].length(), h = rows.length;
        int[][] g = new int[w][h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                g[x][y] = rows[y].charAt(x) == '#' ? 1 : rows[y].charAt(x) == 'T' ? -6 : 0;
        return g;
    }

    @Test
    void horizontalPlatformsUpToRightEdge() {
        List<TileLayout.Block> b = TileLayout.blocks(grid(
                "..###",
                "#...."), 5, 2);
        assertEquals(2, b.size());
        assertEquals("tile(2,0 x3)", b.get(0).toString());
        assertEquals("tile(0,1 x1)", b.get(1).toString());
    }

    @Test
    void columnDrawnOnceFromTop() {
        List<TileLayout.Block> b = TileLayout.blocks(grid(
                ".#..",
                ".#..",
                ".#..",
                "####"), 4, 4);
        assertEquals(2, b.size());
        assertEquals("col(1,0 x3)", b.get(0).toString());
        assertEquals("tile(0,3 x4)", b.get(1).toString());
    }

    @Test
    void singleIsolatedCellIsATile() {
        List<TileLayout.Block> b = TileLayout.blocks(grid(
                "...",
                ".#.",
                "..."), 3, 3);
        assertEquals("tile(1,1 x1)", b.get(0).toString());
        assertFalse(b.get(0).vertical);
    }

    @Test
    void fields() {
        List<TileLayout.Field> f = TileLayout.fields(grid(
                "TTT.",
                "...T",
                "...T"), 4, 3);
        assertEquals(2, f.size());
        assertFalse(f.get(0).vertical);
        assertEquals(3, f.get(0).length);
        assertTrue(f.get(1).vertical);
        assertEquals(2, f.get(1).length);
    }

    @Test
    void skins() {
        assertEquals(5, TileLayout.tileSkin(5));
        assertEquals(55, TileLayout.tileSkin(5655));
        assertEquals(56, TileLayout.columnSkin(5655));
        assertEquals(7, TileLayout.columnSkin(7));
    }
}
