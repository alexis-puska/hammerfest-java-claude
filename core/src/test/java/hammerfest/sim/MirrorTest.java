package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MirrorTest {

    static Game game(boolean mirror) {
        LevelData l = GameTest.level(15, 3, 14);
        int[][] g = l.grid();
        g[2][10] = 1; // dalle isolée à gauche
        l.setGrid(g);
        l.badList.add(new LevelData.Bad(Const.BAD_CERISE, 5, 14));
        l.script = "<t_timer t=\"1\"><e_score i=\"0\" si=\"1\" x=\"4\" y=\"14\" inf=\"1\"/></t_timer>";
        LevelSet set = new LevelSet("test");
        set.levels.add(l);
        set.levels.add(GameTest.level(15, 5, 5));
        Game game = new Game(set, FrameSource.DEFAULT, 3L, Set.of(), mirror ? Set.of("mirror") : Set.of());
        game.start(0, 1);
        return game;
    }

    @Test
    void mirrorFlipsTheLevelAndScriptCoordinates() {
        Game normal = game(false), mirror = game(true);
        assertTrue(mirror.fl_mirror);
        assertTrue(normal.world.getCase(2, 10) > 0);
        assertTrue(mirror.world.getCase(Const.LEVEL_WIDTH - 3, 10) > 0, "grille retournée");
        assertEquals(0, mirror.world.getCase(2, 10));

        Player pn = normal.players().get(0), pm = mirror.players().get(0);
        assertEquals(Const.LEVEL_WIDTH - 1 - pn.cx, pm.cx, "départ du joueur retourné");

        hammerfest.sim.entity.Entity bn = normal.getOne(Const.BAD), bm = mirror.getOne(Const.BAD);
        assertEquals(Const.LEVEL_WIDTH - 1 - bn.cx, bm.cx, "ennemi retourné");

        GameTest.run(normal, 3, 0);
        GameTest.run(mirror, 3, 0);
        hammerfest.sim.entity.Entity in = normal.getOne(Const.ITEM), im = mirror.getOne(Const.ITEM);
        assertNotNull(im);
        assertEquals(Const.LEVEL_WIDTH - 1 - in.cx, im.cx, "objet du script retourné");
    }

    @Test
    void mirrorIsOffWithoutTheOption() {
        assertFalse(game(false).fl_mirror);
    }
}
