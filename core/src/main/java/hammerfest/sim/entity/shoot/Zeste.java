package hammerfest.sim.entity.shoot;

import hammerfest.sim.Game;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.Shoot;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/shoot/Zeste.mt} : zeste du citron, tiré verticalement vers le joueur. */
public class Zeste extends Shoot {

    public Zeste() {
        super("hammer_shoot_zest");
        yOffset = -CASE_HEIGHT;
        shootSpeed = 6;
    }

    public static Zeste attach(Game g, double x, double y) {
        Zeste s = new Zeste();
        s.initShoot(g, x, y);
        return s;
    }

    @Override
    public void hit(Entity e) {
        if ((e.types & PLAYER) > 0 && Math.abs(e.x - x) <= CASE_WIDTH * 0.65) {
            ((Player) e).killHit((e.x - x) * 1.5);
            game.explodeZone(x, y, 15);
        }
    }
}
