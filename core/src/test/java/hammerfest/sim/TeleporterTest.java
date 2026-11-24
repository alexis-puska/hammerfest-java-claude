package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TeleporterTest {

    /** Deux téléporteurs horizontaux de 3 cases face à face (même x), rangées 10 et 20. */
    static Game game() {
        LevelData l = GameTest.level(-1, 5, 2);
        int[][] g = l.grid();
        for (int x = 0; x < LevelData.WIDTH; x++) {
            g[x][15] = 1;
            g[x][24] = 1;
        }
        for (int x = 4; x < 7; x++) {
            g[x][12] = Const.FIELD_TELEPORT;
            g[x][20] = Const.FIELD_TELEPORT;
        }
        l.setGrid(g);
        LevelSet set = new LevelSet("test");
        set.levels.add(l);
        Game game = new Game(set, FrameSource.DEFAULT, 6L, Set.of(), Set.of());
        game.start(0, 1);
        return game;
    }

    @Test
    void teleportersArePairedAndMoveThePlayer() {
        Game g = game();
        assertEquals(2, g.world.teleporterList.size());
        Player p = g.players().get(0);
        // Le joueur tombe à travers le téléporteur du haut (rangée 12) : il ressort par celui du bas
        for (int i = 0; i < 64 && p.cy < 19; i++) GameTest.run(g, 1, 0);
        assertTrue(p.cy >= 19, "téléporté sous le second téléporteur : cy=" + p.cy);
        assertNotNull(p.lastTeleporter);
        assertEquals(20, p.lastTeleporter.cy, "arrivé par le téléporteur de la rangée 20");
    }

    @Test
    void arrivalTeleporterIsNotTakenAgainImmediately() {
        Game g = game();
        World.Teleporter bottom = g.world.teleporterList.get(1);
        assertNull(g.world.getTeleporter(bottom, 5, 20), "pas de retour immédiat");
        assertNotNull(g.world.getTeleporter(null, 5, 20));
    }
}
