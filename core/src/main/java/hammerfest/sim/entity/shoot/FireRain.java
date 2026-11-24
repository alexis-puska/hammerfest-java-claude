package hammerfest.sim.entity.shoot;

import hammerfest.sim.Game;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Shoot;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/shoot/FireRain.mt} : goutte de la pluie de feu (objet 84), brûle les ennemis. */
public class FireRain extends Shoot {

    public FireRain() {
        super("hammer_shoot_firerain");
        fl_checkBounds = false;
    }

    public static FireRain attach(Game g, double x, double y) {
        FireRain s = new FireRain();
        s.shootSpeed = 10 + g.random(5);
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
    public void onDeathLine() {
        destroy();
    }

    @Override
    public void hit(Entity e) {
        if ((e.types & BAD_CLEAR) > 0) {
            game.particles(PARTICLE_SPARK, x, y, 1, 5);
            ((Bad) e).burn();
            destroy();
        }
    }

    @Override
    public void onHitWall() {
        hitLevel();
    }

    @Override
    public void onHitGround(double h) {
        hitLevel();
    }

    void hitLevel() {
        game.explodeZone(x, y, 30);
        game.particles(PARTICLE_STONE, x, y, 0, 4);
        destroy();
    }

    @Override
    public void update() {
        super.update();
        if (x < 0 && !fl_destroy) hitLevel();
    }
}
