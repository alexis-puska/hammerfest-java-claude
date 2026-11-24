package hammerfest.sim.entity.shoot;

import hammerfest.sim.Game;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.Shoot;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/shoot/Pepin.mt} : pépin craché par la pomme, mortel pour le joueur. */
public class Pepin extends Shoot {

    public Pepin() {
        super("hammer_shoot_pepin");
        shootSpeed = 5;
    }

    public static Pepin attach(Game g, double x, double y) {
        Pepin s = new Pepin();
        s.initShoot(g, x, y);
        return s;
    }

    @Override
    public void hit(Entity e) {
        if ((e.types & PLAYER) > 0) ((Player) e).killHit(dx);
    }
}
