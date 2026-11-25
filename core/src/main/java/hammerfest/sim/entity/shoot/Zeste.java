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
        shootSpeed = 6;
    }

    public static Zeste attach(Game g, double x, double y) {
        Zeste s = new Zeste();
        s.initShoot(g, x, y);
        return s;
    }

    /**
     * Zeste.moveDown : tiré vers le bas, le clip est retourné verticalement (_yscale = -_yscale) et dessiné une case
     * plus haut (_yOffset = -CASE_HEIGHT). Retournement vertical = rotation de 180° + retournement horizontal (même
     * point d'ancrage). Tiré vers le haut, le zeste garde son sens et son décalage nul.
     */
    @Override
    public void moveDown(double speed) {
        super.moveDown(speed);
        rotation = 180;
        xscale = -xscale;
        yOffset = -CASE_HEIGHT;
    }

    @Override
    public void hit(Entity e) {
        if ((e.types & PLAYER) > 0 && Math.abs(e.x - x) <= CASE_WIDTH * 0.65) {
            ((Player) e).killHit((e.x - x) * 1.5);
            game.explodeZone(x, y, 15);
        }
    }
}
