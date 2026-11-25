package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sortie par le bas pendant un cycle de jeu, avec défilement : le niveau suivant ne doit pas être déclaré terminé
 * (fl_clear) avant l'arrivée de ses fruits, sinon on peut en ressortir par le bas sans mourir, ou prendre une porte.
 */
class ScrollRegressionTest {

    /** Niveau avec un trou de 3 cases sous le joueur (colonne 5). */
    static LevelData withHole() {
        LevelData l = GameTest.level(15, 5, 14);
        int[][] grid = l.grid();
        for (int x = 4; x <= 6; x++) grid[x][15] = 0;
        l.setGrid(grid);
        return l;
    }

    @Test
    void levelReachedByFallingIsNotClearWhileFruitsRemain() {
        LevelData next = withHole();
        next.badList.add(new LevelData.Bad(Const.BAD_CERISE, 15, 14));
        Game g = GameTest.game(withHole(), next, GameTest.level(15, 5, 14));
        g.scrollTransitions = true;
        Player p = g.players().get(0);
        for (int i = 0; i < 200 && g.levelId == 0; i++) GameTest.run(g, 1, 0); // chute par le trou du niveau 0
        assertEquals(1, g.levelId);
        assertTrue(g.scrolling());
        assertFalse(g.fl_clear, "pas de fin de niveau déclarée pendant le défilement");
        GameTest.run(g, 41, 0);
        assertFalse(g.scrolling());
        assertEquals(1, g.countList(Const.BAD_CLEAR));
        assertFalse(g.fl_clear, "fruit vivant : niveau non terminé");
        int lives = p.lives;
        for (int i = 0; i < 200 && p.lives == lives && g.levelId == 1; i++) GameTest.run(g, 1, 0);
        assertEquals(1, g.levelId, "chute par le trou sans avoir tué le fruit : pas de niveau suivant");
        assertEquals(lives - 1, p.lives, "Igor meurt");
    }

    /** Niveau 86 de l'aventure : fruits posés par le script (attach), liste d'ennemis du niveau vide. */
    @Test
    void scriptedFruitsKeepLevelOpenAfterScroll() {
        LevelData next = GameTest.level(15, 5, 14);
        next.script = "<attach repeat=\"-1\"><e_bad i=\"" + Const.BAD_CERISE + "\" x=\"15\" y=\"14\"/></attach>";
        Game g = GameTest.game(withHole(), next, GameTest.level(15, 5, 14));
        g.scrollTransitions = true;
        for (int i = 0; i < 200 && g.levelId == 0; i++) GameTest.run(g, 1, 0);
        GameTest.run(g, 41, 0);
        assertFalse(g.scrolling());
        assertEquals(1, g.countList(Const.BAD_CLEAR));
        assertFalse(g.fl_clear, "fruit posé par script : niveau non terminé");
    }
}
