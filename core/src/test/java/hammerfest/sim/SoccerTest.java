package hammerfest.sim;

import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SoccerTest {

    static Game game() {
        LevelSet set = DimensionTest.load("soccer");
        Game g = new Game(set, FrameSource.DEFAULT, 8L, Set.of(), Set.of());
        g.setSoccer();
        g.start(0, 2);
        return g;
    }

    static int[] findCase(Game g, int id) {
        for (int x = 0; x < Const.LEVEL_WIDTH; x++)
            for (int y = 0; y < Const.LEVEL_HEIGHT; y++)
                if (g.world.getCase(x, y) == id) return new int[]{x, y};
        return null;
    }

    @Test
    void kickOffGoalAndEndOfMatch() {
        Game g = game();
        Player igor = g.players().get(0), sandy = g.players().get(1);
        assertEquals(0, g.team(igor));
        assertEquals(1, g.team(sandy));
        assertEquals(Const.HEAD_SANDY, sandy.head);
        assertEquals(Const.WEAPON_NONE, igor.currentWeapon, "pas d'arme au foot");
        assertNull(g.ball);
        GameTest.run(g, (int) (Const.SECOND * 3) + 2, 0);
        assertNotNull(g.ball, "coup d'envoi après 3 s");
        assertTrue(g.fl_match);

        // Igor frappe le ballon dans le but 1 : l'équipe 1 (Sandy) marque... contre son camp pour Igor
        int[] goal = findCase(g, Const.FIELD_GOAL_1);
        assertNotNull(goal, "le terrain a des buts");
        g.ball.lastPlayer = igor;
        g.ball.moveTo(hammerfest.sim.entity.Entity.xCtr(goal[0]), hammerfest.sim.entity.Entity.yCtr(goal[1] - 2));
        g.ball.dx = 0;
        g.ball.dy = 0;
        for (int i = 0; i < 64 && g.soccerScores[1] == 0; i++) GameTest.run(g, 1, 0);
        assertEquals(1, g.soccerScores[1], "but pour l'équipe de Sandy");
        assertEquals(Const.HEAD_LOSE, igor.head, "contre son camp : tête basse");

        // Fin du match au bout de 5 minutes, résultats, puis fin de partie
        g.matchChrono = 5 * 60 * Const.SECOND - 1;
        GameTest.run(g, (int) (Const.SECOND * 17), 0);
        assertTrue(g.fl_matchEnd);
        assertEquals(1, g.winners, "Sandy gagne 1 - 0");
        assertTrue(g.fl_gameOver);
    }
}
