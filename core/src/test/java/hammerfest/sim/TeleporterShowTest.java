package hammerfest.sim;

import hammerfest.data.LevelSet;
import hammerfest.net.RenderFrame;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static hammerfest.sim.Const.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Player.showTeleporters : le téléporteur proche du joueur et sa destination (non tirée au hasard) s'allument
 * (SetManager.showField), les autres restent éteints ; l'état part dans les instantanés (RenderFrame.teleportersOn).
 */
class TeleporterShowTest {

    @Test
    void nearbyTeleporterAndItsTargetLightUp() {
        Game g = new Game(FakeLevelIdTest.read("data/levels/adventure.json", LevelSet.class), FrameSource.DEFAULT, 5L,
                Set.of(), Set.of());
        g.start(12, 1);
        java.util.List<World.Teleporter> tl = g.world.teleporterList;
        assertTrue(tl.size() >= 2, "niveau 12 : des téléporteurs");
        Player p = g.players().get(0);

        p.x = -1000;
        p.y = -1000;
        p.showTeleporters();
        for (World.Teleporter t : tl) assertFalse(t.on, "joueur loin de tout : tous éteints");
        assertNull(RenderFrame.capture(g, java.util.List.of(), false).teleportersOn);

        World.Teleporter t = tl.get(0);
        p.x = t.dir == VERTICAL ? t.centerX + CASE_WIDTH * 2 : t.centerX;
        p.y = t.dir == VERTICAL ? t.centerY : t.startY - CASE_HEIGHT * 2;
        p.showTeleporters();
        assertTrue(t.on, "téléporteur à 2 cases : allumé");
        World.Teleporter next = g.world.fixedNextTeleporter(t);
        if (next != null) assertTrue(next.on, "sa destination aussi");
        Object[] drawn = g.world.getNextTeleporter(t, g);
        assertEquals(next == null, drawn == null || (Boolean) drawn[1], "destination fixe = getNextTeleporter sans fl_rand");
        if (next != null) assertSame(next, drawn[0]);
        int[] on = RenderFrame.capture(g, java.util.List.of(), false).teleportersOn;
        assertNotNull(on);
        boolean found = false;
        for (int i = 0; i < on.length; i += 2) found |= on[i] == t.cx && on[i + 1] == t.cy;
        assertTrue(found, "instantané : téléporteur allumé transmis");

        // au-delà de 4 cases : éteint
        p.x = t.dir == VERTICAL ? t.centerX + CASE_WIDTH * 4.5 : t.centerX;
        p.y = t.dir == VERTICAL ? t.centerY : t.centerY - CASE_HEIGHT * 4.5;
        p.showTeleporters();
        assertFalse(t.on && !(next != null && near(p, next)), "à 4,5 cases : éteint");
    }

    /** Joueur proche de l'autre téléporteur (qui rallumerait le premier comme destination). */
    static boolean near(Player p, World.Teleporter o) {
        return o.dir == VERTICAL ? Math.abs(o.centerX - p.x) <= CASE_WIDTH * 4 : Math.abs(o.centerY - p.y) <= CASE_WIDTH * 4;
    }
}
