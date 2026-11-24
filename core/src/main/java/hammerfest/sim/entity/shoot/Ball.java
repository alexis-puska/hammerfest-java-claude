package hammerfest.sim.entity.shoot;

import hammerfest.sim.Game;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.Shoot;
import hammerfest.sim.entity.bad.Fruits;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/shoot/Ball.mt} : balle que les fraises se passent ; mortelle pour le joueur. */
public class Ball extends Shoot {

    static final double BALL_TIMEOUT = 2.3 * SECOND;

    public Entity targetCatcher;

    public Ball() {
        super("hammer_shoot_ball");
        shootSpeed = 8.5;
        fl_alphaBlink = false;
        fl_borderBounce = true;
    }

    public static Ball attach(Game g, double x, double y) {
        Ball b = new Ball();
        b.initShoot(g, x, y);
        return b;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        register(BALL);
        setLifeTimer(BALL_TIMEOUT);
    }

    /** Balle perdue : rendue à une fraise. */
    @Override
    public void onLifeTimer() {
        game.fx("hammer_fx_pop", x, y - CASE_HEIGHT / 2.0);
        Entity catcher = game.getOne(CATCHER);
        if (catcher instanceof Fruits.Fraise) ((Fruits.Fraise) catcher).assignBall();
        super.onLifeTimer();
    }

    @Override
    public void hit(Entity e) {
        if ((e.types & PLAYER) > 0) ((Player) e).killHit(dx);
        if ((e.types & CATCHER) > 0 && targetCatcher == e) ((Fruits.Fraise) e).catchBall(this);
    }
}
