package hammerfest.sim.entity.shoot;

import hammerfest.sim.Game;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.Shoot;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/shoot/FireBall.mt} : boule de feu tirée par Tuberculoz, mortelle au contact. */
public class EnemyFireBall extends Shoot {

    public EnemyFireBall() {
        super("hammer_shoot_fireball");
        fl_largeTrigger = true;
        shootSpeed = 7;
    }

    public static EnemyFireBall attach(Game g, double x, double y) {
        EnemyFireBall s = new EnemyFireBall();
        s.initShoot(g, x, y - 10);
        return s;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        animId = -1;
        playAnim(ANIM_LOOP);
    }

    @Override
    public void destroy() {
        game.fx("hammer_fx_pop", x, y - CASE_HEIGHT / 2.0);
        super.destroy();
    }

    @Override
    public void hit(Entity e) {
        if ((e.types & PLAYER) > 0 && distance(e.x, e.y) <= CASE_WIDTH * 1.2) {
            game.explosion(x, y, 25);
            ((Player) e).killHit(dx);
        }
    }
}
