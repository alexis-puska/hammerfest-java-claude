package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CoopTest {

    static Game game(Set<String> options) {
        LevelSet set = new LevelSet("test");
        set.levels.add(GameTest.level(15, 3, 14));
        set.levels.add(GameTest.level(15, 3, 14));
        Game g = new Game(set, FrameSource.DEFAULT, 4L, Set.of(102, 103, 104), options);
        g.start(0, 2);
        GameTest.run(g, 40, 0);
        return g;
    }

    @Test
    void twoPlayersWithTheirOwnControls() {
        Game g = game(Set.of());
        assertTrue(g.fl_multi);
        Player igor = g.players().get(0), sandy = g.players().get(1);
        assertEquals(Const.HEAD_SANDY, sandy.head);
        assertEquals("Sandy", sandy.name);
        assertEquals(2, igor.lives, "vies divisées par deux (4 → 2)");
        assertTrue(sandy.x > igor.x, "Sandy part à droite");
        double ix = igor.x, sx = sandy.x;
        for (int i = 0; i < 20; i++) {
            g.tick(new InputFrame(g.cycle, 0), new InputFrame(g.cycle, InputFrame.LEFT));
        }
        assertEquals(ix, igor.x, 1e-6, "Igor immobile");
        assertTrue(sandy.x < sx, "Sandy va à gauche avec ses propres touches");
    }

    @Test
    void gameEndsWhenOnePlayerHasNoLivesUnlessLifeSharing() {
        Game g = game(Set.of());
        Player igor = g.players().get(0);
        igor.lives = 0;
        igor.killPlayer();
        assertTrue(g.fl_gameOver, "sans partage de vies : fin de partie");

        Game shared = game(Set.of("lifesharing"));
        Player i2 = shared.players().get(0), s2 = shared.players().get(1);
        i2.lives = 0;
        int sandyLives = s2.lives;
        i2.killPlayer();
        assertFalse(shared.fl_gameOver, "partage : Igor prend une vie à Sandy");
        assertEquals(sandyLives - 1, s2.lives);
    }
}
