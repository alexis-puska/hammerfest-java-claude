package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScriptCodesTest {

    static Game game() {
        LevelSet set = new LevelSet("test");
        for (int i = 0; i < 12; i++) {
            LevelData l = GameTest.level(15, 3, 14);
            l.badList.add(new LevelData.Bad(Const.BAD_CERISE, 12, 14));
            l.badList.add(new LevelData.Bad(Const.BAD_CERISE, 15, 14));
            l.specialSlots.add(new LevelData.Slot(8, 10));
            set.levels.add(l);
        }
        Game g = new Game(set, FrameSource.DEFAULT, 9L, Set.of(), Set.of());
        g.start(0, 1);
        GameTest.run(g, 5, 0);
        return g;
    }

    @Test
    void bucketWarpsToLevelTenWithASecretDoor() {
        Game g = game();
        g.script.codeTrigger(0);
        assertTrue(g.fl_warpStart);
        g.nextLevel();
        assertEquals(10, g.levelId, "le seau mène au niveau 10");
        assertFalse(g.fl_warpStart);
        assertTrue(g.script.decors.stream().anyMatch(d -> d.linkage.equals("door_secret")), "porte secrète");
    }

    @Test
    void killAllBadsAndForceHurryUp() {
        Game g = game();
        assertEquals(2, g.countList(Const.BAD_CLEAR));
        g.script.codeTrigger(11);
        GameTest.run(g, 1, 0);
        assertEquals(0, g.countList(Const.BAD_CLEAR), "code 11 : ennemis retirés");

        Game h = game();
        h.script.codeTrigger(12);
        assertEquals(2, h.huState, "code 12 : hurry up forcé jusqu'au 2e palier");
    }

    @Test
    void soccerBallOnSlotAndNoJumpDown() {
        Game g = game();
        g.script.codeTrigger(6);
        assertEquals(1, g.countList(Const.SOCCERBALL), "code 6 : ballon sur un emplacement");
        g.script.codeTrigger(10);
        for (hammerfest.sim.entity.Entity e : g.getList(Const.BAD)) {
            if (e instanceof hammerfest.sim.entity.bad.Jumper) {
                assertFalse(((hammerfest.sim.entity.bad.Jumper) e).fl_jDown, "code 10 : plus de saut vers le bas");
            }
        }
    }
}
