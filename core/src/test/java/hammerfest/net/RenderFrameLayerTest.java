package hammerfest.net;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.sim.Const;
import hammerfest.sim.FrameSource;
import hammerfest.sim.Game;
import hammerfest.sim.InputFrame;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Entity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Ordre de dessin : glaçon d'un ennemi gelé (DP_BADS) sous les bombes (DP_BOMBS). */
class RenderFrameLayerTest {

    @Test
    void iceIsDrawnBelowBombs() {
        LevelData l = new LevelData();
        int[][] grid = new int[LevelData.WIDTH][LevelData.HEIGHT];
        for (int x = 0; x < LevelData.WIDTH; x++) grid[x][15] = 1;
        l.setGrid(grid);
        l.playerX = 5;
        l.playerY = 14;
        l.badList.add(new LevelData.Bad(Const.BAD_CERISE, 15, 14));
        LevelSet set = new LevelSet("test");
        set.levels.add(l);
        Game g = new Game(set, FrameSource.DEFAULT, 3L, Set.of(), Set.of());
        g.start(0, 1);
        for (int i = 0; i < 32; i++) g.tick(new InputFrame(g.cycle, 0));
        ((Bad) g.getList(Const.BAD).get(0)).freeze(Const.FREEZE_DURATION);
        g.tick(new InputFrame(g.cycle, InputFrame.BOMB));
        List<Entity> bombs = g.getList(Const.BOMB);
        assertEquals(1, bombs.size());

        RenderFrame f = RenderFrame.capture(g, List.of(), false);
        assertEquals(4, f.ice.length, "un glaçon");
        int bomb = -1;
        for (int i = 0; i < f.ids.length; i++) if (f.ids[i] == bombs.get(0).uniqId) bomb = i;
        assertEquals(bomb, f.bombEntities, "glaçon dessiné juste avant la bombe");
        assertTrue(f.bombEntities > f.playerEntities, "après le joueur et l'ennemi");
    }
}
