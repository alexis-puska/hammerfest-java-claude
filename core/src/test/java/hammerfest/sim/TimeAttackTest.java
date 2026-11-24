package hammerfest.sim;

import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.ScoreItem;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TimeAttackTest {

    static Game game() {
        LevelSet set = DimensionTest.load("timeAttack");
        Game g = new Game(set, FrameSource.DEFAULT, 2L, Set.of(), Set.of("mirror"));
        g.setTimeAttack(0);
        g.start(g.taFirstLevel, 1);
        return g;
    }

    @Test
    void countdownThenChronoWithoutScore() {
        Game g = game();
        Player p = g.players().get(0);
        assertFalse(g.fl_mirror, "pas de miroir en Time Attack");
        assertEquals(3, p.lives);
        assertTrue(p.fl_lockControls, "bloqué pendant le compte à rebours");
        GameTest.run(g, (int) (Const.SECOND * 3.5) + 1, 0);
        assertFalse(p.fl_lockControls);
        GameTest.run(g, (int) Const.SECOND * 10, 0);
        assertEquals(10_000, g.chronoMs(), 100);
        assertEquals(0, g.huState, "pas de hurry up");

        ScoreItem.attach(g, p.x, p.y, 0, 2).execute(p);
        assertEquals(5_000, g.chronoMs(), 100, "cristal de rang 2 : 5 s de moins");
        assertEquals(0, p.score, "pas de score");
    }

    @Test
    void finishingTheRunGivesATotalTime() {
        Game g = game();
        GameTest.run(g, (int) (Const.SECOND * 3.5) + 1, 0);
        for (int id = 1; id <= 6 && !g.fl_gameOver; id++) {
            g.players().get(0).shield(Const.SECOND * 100);
            GameTest.run(g, (int) Const.SECOND * 2, 0);
            g.goTo(id);
        }
        assertTrue(g.fl_gameOver);
        assertTrue(g.fl_victory, "niveau 6 vide : fin du parcours");
        assertEquals(6, g.times.size());
        assertEquals(12_000, g.totalTime, 300);
        assertEquals("00\" 12' 000", Game.formatTime(12_000));
    }

    @Test
    void alertEachNewMinute() {
        Game g = game();
        GameTest.run(g, (int) (Const.SECOND * 3.5) + 1, 0);
        g.players().get(0).shield(Const.SECOND * 1000);
        g.drainEvents();
        GameTest.run(g, (int) Const.SECOND * 61, 0);
        assertTrue(g.drainEvents().stream().anyMatch(e -> e.name.equals("alert:static.18||1 ")), "alerte « 1 MINUTE »");
    }

    @Test
    void losingGivesInfiniteTime() {
        Game g = game();
        Player p = g.players().get(0);
        p.lives = 0;
        p.killPlayer();
        assertTrue(g.fl_gameOver);
        assertEquals(Game.TA_INFINITY, g.totalTime);
    }

    @Test
    void timeAttackForTwoAndBossRush() {
        LevelSet set = DimensionTest.load("multiTime");
        Game g = new Game(set, FrameSource.DEFAULT, 2L, Set.of(), Set.of());
        g.setTimeAttack(0);
        g.start(0, 2);
        Player igor = g.players().get(0), sandy = g.players().get(1);
        assertEquals(3, igor.lives);
        assertEquals(3, sandy.lives);
        assertEquals(Const.HEAD_SANDY, sandy.head);
        assertEquals(Const.LEVEL_WIDTH - 1 - igor.cx, sandy.cx, 1, "départ symétrique");

        Game r = new Game(DimensionTest.load("adventure"), FrameSource.DEFAULT, 2L, Set.of(), Set.of());
        r.setBossRush();
        r.start(0, 1);
        Player p = r.players().get(0);
        assertEquals(1.5, p.speedFactor, 1e-9);
        assertEquals(2, p.maxBombs);
        assertTrue(p.ctrl.fl_upKick);
        GameTest.run(r, (int) Const.SECOND * 3, 0);
        assertEquals(3000, r.chronoMs(), 50, "chrono de la Tubercourse");
        r.forcedGoto(1);
        assertEquals(1, r.times.size(), "temps du niveau enregistré");
    }
}
