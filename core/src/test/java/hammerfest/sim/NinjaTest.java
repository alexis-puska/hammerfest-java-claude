package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NinjaTest {

    static Game game() {
        LevelSet set = new LevelSet("test");
        for (int i = 0; i < 2; i++) {
            LevelData l = GameTest.level(15, 3, 14);
            for (int x : new int[]{8, 11, 14, 17}) l.badList.add(new LevelData.Bad(Const.BAD_CERISE, x, 14));
            set.levels.add(l);
        }
        Game g = new Game(set, FrameSource.DEFAULT, 5L, Set.of(), Set.of("ninja"));
        g.start(0, 1);
        GameTest.run(g, 2, 0);
        return g;
    }

    static Bad find(Game g, boolean foe) {
        for (hammerfest.sim.entity.Entity e : g.getList(Const.BAD_CLEAR)) {
            Bad b = (Bad) e;
            if (foe ? b.fl_ninFoe : b.fl_ninFriend) return b;
        }
        return null;
    }

    @Test
    void oneFoeAndOneFriendAreChosen() {
        Game g = game();
        long foes = g.getList(Const.BAD_CLEAR).stream().filter(e -> ((Bad) e).fl_ninFoe).count();
        long friends = g.getList(Const.BAD_CLEAR).stream().filter(e -> ((Bad) e).fl_ninFriend).count();
        assertEquals(1, foes);
        assertEquals(1, friends, "1 ami avant le niveau 20");
    }

    @Test
    void killingTheFoeTurnsOthersIntoCrystals() {
        Game g = game();
        find(g, true).killHit(1);
        GameTest.run(g, 2, 0);
        assertEquals(1, g.countList(Const.BAD_CLEAR), "il ne reste que la cible (en train de tomber)");
        assertTrue(g.countList(Const.ITEM) >= 3, "cristaux + objet bonus");
    }

    @Test
    void killingAFriendKillsThePlayer() {
        Game g = game();
        Player p = g.players().get(0);
        p.shield(Const.SECOND * 100);
        find(g, false).killHit(1);
        assertTrue(p.fl_kill, "ami tué : le joueur meurt, même protégé");
    }
}
