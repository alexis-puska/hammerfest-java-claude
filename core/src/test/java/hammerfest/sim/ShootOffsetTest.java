package hammerfest.sim;

import hammerfest.net.RenderFrame;
import hammerfest.sim.entity.PlayerShots;
import hammerfest.sim.entity.Shoot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Entity._yOffset d'origine : flèche et perle dessinées à hauteur du buste, pas au niveau des pieds. */
class ShootOffsetTest {

    static float drawnY(Game g, Shoot s) {
        RenderFrame f = RenderFrame.capture(g, java.util.List.of(), false);
        for (int i = 0; i < f.ids.length; i++) if (f.ids[i] == s.uniqId) return f.ys[i];
        throw new AssertionError("tir absent de l'instantané");
    }

    @Test
    void arrowAndPearlAreDrawnAtChestHeight() {
        Game g = BossTest.batLevel();
        var p = g.players().get(0);
        Shoot arrow = PlayerShots.create(g, Const.WEAPON_S_ARROW, p.x, p.y);
        assertEquals(arrow.y - 15, drawnY(g, arrow), 1e-3, "flèche : _yOffset = -15");
        Shoot pearl = PlayerShots.create(g, Const.WEAPON_S_ICE, p.x, p.y);
        assertEquals(pearl.y - 16, drawnY(g, pearl), 1e-3, "perle : _yOffset = -16");
    }

    /** Zeste.moveDown : retourné verticalement et décalé d'une case ; vers le haut, ni l'un ni l'autre. */
    @Test
    void zestShotDownwardsIsFlipped() {
        Game g = BossTest.batLevel();
        var p = g.players().get(0);
        var down = hammerfest.sim.entity.shoot.Zeste.attach(g, p.x, p.y);
        down.moveDown(down.shootSpeed);
        var up = hammerfest.sim.entity.shoot.Zeste.attach(g, p.x, p.y);
        up.moveUp(up.shootSpeed);
        RenderFrame f = RenderFrame.capture(g, java.util.List.of(), false);
        for (int i = 0; i < f.ids.length; i++) {
            if (f.ids[i] == down.uniqId) {
                assertEquals(180, f.rotations[i], 1e-3);
                assertEquals(-1, f.xscales[i], 1e-3, "180° + miroir horizontal = _yscale négatif");
                assertEquals(down.y - Const.CASE_HEIGHT, f.ys[i], 1e-3);
            }
            if (f.ids[i] == up.uniqId) {
                assertEquals(0, f.rotations[i], 1e-3);
                assertEquals(up.y, f.ys[i], 1e-3, "vers le haut : pas de décalage");
            }
        }
    }
}
