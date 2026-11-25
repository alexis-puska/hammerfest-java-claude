package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.net.RenderFrame;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MovePredictorTest {

    /** Hôte : vraie partie ; client : fantôme recalé sur un état ancien de l'hôte puis rejouant ses touches. */
    @Test
    void predictionMatchesHostSimulation() {
        LevelData level = GameTest.level(15, 5, 12);
        Game host = GameTest.game(level);
        GameTest.run(host, 40, 0); // le joueur atterrit
        MovePredictor predictor = new MovePredictor(FrameSource.DEFAULT);
        RenderFrame f = RenderFrame.capture(host, List.of(), true);
        predictor.setLevel("test/0/0", level, f.grid, f.gridVersion);

        // instantané de l'hôte au tick 0, puis le client appuie à droite 10 ticks et saute
        predictor.reconcile(-1, f.predict, 0);
        assertTrue(predictor.valid(), "joueur prédictible");
        int[] bits = new int[24];
        for (int t = 0; t < bits.length; t++) bits[t] = InputFrame.RIGHT | (t == 10 ? InputFrame.UP : 0);
        for (int t = 0; t < bits.length; t++) predictor.step(t, bits[t]);
        for (int t = 0; t < bits.length; t++) host.tick(new InputFrame(host.cycle, bits[t]));
        Player h = host.players().get(0), p = predictor.player();
        assertEquals(h.x, p.x, 0.5, "même position horizontale que l'hôte");
        assertEquals(h.y, p.y, 0.5, "même position verticale (saut)");

        // recalage : l'hôte a reçu les touches jusqu'au tick 11 ; le fantôme rejoue les 12 suivantes
        RenderFrame later = RenderFrame.capture(host, List.of(), false);
        predictor.reconcile(bits.length - 1, later.predict, 0);
        assertEquals(h.x, predictor.player().x, 0.01);
        assertEquals(h.y, predictor.player().y, 0.01);
    }

    /** Niveau glacé : le fantôme glisse comme le joueur de l'hôte. */
    @Test
    void predictionSlidesOnIce() {
        LevelData level = GameTest.level(15, 5, 12);
        Game host = GameTest.game(level);
        host.setDynamicVar("ICE", "1");
        GameTest.run(host, 40, 0);
        MovePredictor predictor = new MovePredictor(FrameSource.DEFAULT);
        RenderFrame f = RenderFrame.capture(host, List.of(), true);
        assertTrue(f.iceLevel, "niveau glacé transmis");
        predictor.setLevel("ice/0/0", level, f.grid, f.gridVersion);
        predictor.setEnvironment(f.iceLevel, f.aqua);
        predictor.reconcile(-1, f.predict, 0);
        // 8 ticks à droite puis on lâche : sur la glace le joueur continue de glisser
        for (int t = 0; t < 20; t++) {
            int bits = t < 8 ? InputFrame.RIGHT : 0;
            predictor.step(t, bits);
            host.tick(new InputFrame(host.cycle, bits));
        }
        assertEquals(host.players().get(0).x, predictor.player().x, 0.5, "même glissade que l'hôte");
    }

    /** Cauchemar et Tornade (Igor plus rapide) : le fantôme, partie sans options, reprend la vitesse de l'hôte. */
    @Test
    void predictionUsesHostSpeedFactor() {
        LevelData level = GameTest.level(15, 5, 12);
        hammerfest.data.LevelSet set = new hammerfest.data.LevelSet("test");
        set.levels.add(level);
        Game host = new Game(set, FrameSource.DEFAULT, 1L, java.util.Set.of(), java.util.Set.of("nightmare", "boost"));
        host.start(0, 1);
        GameTest.run(host, 40, 0);
        assertEquals(1.6, host.players().get(0).speedFactor, 1e-9, "Cauchemar + Tornade");
        MovePredictor predictor = new MovePredictor(FrameSource.DEFAULT);
        RenderFrame f = RenderFrame.capture(host, List.of(), true);
        predictor.setLevel("test/0/0", level, f.grid, f.gridVersion);
        predictor.reconcile(-1, f.predict, 0);
        for (int t = 0; t < 16; t++) {
            predictor.step(t, InputFrame.RIGHT);
            host.tick(new InputFrame(host.cycle, InputFrame.RIGHT));
        }
        assertEquals(host.players().get(0).x, predictor.player().x, 0.5, "même vitesse que le joueur de l'hôte");
    }
}
