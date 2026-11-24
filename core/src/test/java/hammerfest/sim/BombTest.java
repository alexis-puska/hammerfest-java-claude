package hammerfest.sim;

import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Bomb;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BombTest {

    static Game game(Set<Integer> families) {
        LevelSet set = new LevelSet("test");
        set.levels.add(GameTest.level(15, 5, 14));
        Game g = new Game(set, FrameSource.DEFAULT, 7L, families, Set.of());
        g.start(0, 1);
        GameTest.run(g, 32, 0);
        return g;
    }

    static List<Entity> bombs(Game g) {
        return g.getList(Const.BOMB);
    }

    @Test
    void dropsOneBombAtATime() {
        Game g = game(Set.of());
        Player p = g.players().get(0);
        GameTest.run(g, 1, InputFrame.BOMB);
        assertEquals(1, bombs(g).size(), "bombe posée");
        assertEquals(p, bombs(g).get(0).parent);
        // Relâcher puis réappuyer en s'éloignant : la limite d'une bombe s'applique
        GameTest.run(g, 10, InputFrame.LEFT);
        GameTest.run(g, 1, InputFrame.BOMB);
        assertEquals(1, bombs(g).size(), "maxBombs = 1 sans la quête multi-bombe");
    }

    @Test
    void multiBombFamilyAllowsTwoBombs() {
        Game g = game(Set.of(100));
        GameTest.run(g, 1, InputFrame.BOMB);
        GameTest.run(g, 10, InputFrame.LEFT);
        GameTest.run(g, 1, InputFrame.BOMB);
        assertEquals(2, bombs(g).size());
    }

    @Test
    void bombExplodesAfterItsDurationThenDisappears() {
        Game g = game(Set.of());
        GameTest.run(g, 1, InputFrame.BOMB);
        Bomb b = (Bomb) bombs(g).get(0);
        // Mise à jour dès le cycle de pose (liste parcourue en direct) : 45 cycles en comptant celui-ci
        GameTest.run(g, 43, 0);
        assertFalse(b.fl_explode, "durée de vie : 45 cycles");
        GameTest.run(g, 1, 0);
        assertTrue(b.fl_explode);
        GameTest.run(g, 20, 0); // animation d'explosion (10 images par défaut)
        assertTrue(bombs(g).isEmpty(), "détruite en fin d'animation");
    }

    @Test
    void kickSendsTheBombAway() {
        Game g = game(Set.of());
        Player p = g.players().get(0);
        GameTest.run(g, 1, InputFrame.BOMB);
        Bomb b = (Bomb) bombs(g).get(0);
        double x0 = b.x;
        GameTest.run(g, 1, 0);           // relâche
        GameTest.run(g, 1, InputFrame.BOMB); // kick (bombe à portée)
        // Friction de l'air appliquée dans le même cycle que le kick
        assertEquals(Const.PLAYER_HKICK_X * Const.FRICTION_X * p.dir, b.dx, 1e-9);
        assertTrue(b.dy < 0, "kick vers le haut");
        GameTest.run(g, 15, 0);
        assertTrue(Math.abs(b.x - x0) > 20, "la bombe a voyagé");
    }
}
