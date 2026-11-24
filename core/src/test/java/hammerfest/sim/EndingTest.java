package hammerfest.sim;

import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EndingTest {

    @Test
    void elevatorEndsTheGameAsAVictory() {
        LevelSet set = DimensionTest.load("adventure");
        Game g = new Game(set, FrameSource.DEFAULT, 1L, Set.of(), Set.of());
        g.start(0, 1);
        g.forcedGoto(103);
        GameTest.run(g, 5, 0);
        Player p = g.players().get(0);
        p.shield(Const.SECOND * 1000);

        // L'ascenseur ne marche pas tant que la prison n'est pas ouverte
        g.script.codeTrigger(4);
        assertEquals(0, g.endModeTimer, 0);

        g.script.codeTrigger(3); // explosion de la prison : fruits libérés
        assertTrue(p.fl_lockControls);
        g.script.codeTrigger(4); // Igor prend l'ascenseur
        assertFalse(p.visible);
        assertTrue(g.endModeTimer > 0);

        GameTest.run(g, (int) (Const.SECOND * 14) + 2, 0);
        assertTrue(g.fl_gameOver);
        assertTrue(g.fl_victory, "partie gagnée");
    }
}
