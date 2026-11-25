package hammerfest.sim.entity.shoot;

import hammerfest.sim.Game;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.Shoot;
import hammerfest.sim.entity.bad.Fruits;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/shoot/FramBall.mt} : fragment de framboise qui file en tournoyant vers
 * le point de reconstitution ; mortel pour le joueur. Arrivé (ou au bout de 4 s), il prévient la framboise.
 */
public class FramBall extends Shoot {

    public Fruits.Framboise owner;
    double turnSpeed;
    double ang;
    boolean fl_arrived;

    public FramBall() {
        super("hammer_shoot_framBall2");
        yOffset = -2;
        fl_checkBounds = false;
        fl_blink = false;
    }

    public static FramBall attach(Game g, double x, double y, int fragment) {
        FramBall s = new FramBall();
        s.initShoot(g, x, y);
        s.shootSpeed = 5 + g.random(3);
        s.turnSpeed = 0.03 + g.random(10) / 100.0 + s.shootSpeed * 0.02;
        s.setLifeTimer(SECOND * 4);
        s.ang = g.random(314);
        s.fl_anim = false;
        s.animId = -1;
        s.subFrame = fragment; // gotoAndStop(i+1) : morceau du fruit
        return s;
    }

    public void setOwner(Fruits.Framboise b) {
        owner = b;
        double tang = Math.atan2(owner.ty - y, owner.tx - x);
        ang = -tang + (game.random(168) / 100.0) * (game.random(2) * 2 - 1);
        if (owner.anger > 0) shootSpeed *= 1 + owner.anger * 0.5;
    }

    @Override
    public void hit(Entity e) {
        if ((e.types & PLAYER) > 0) ((Player) e).killHit(dx);
    }

    static double adjustAngRad(double a) {
        if (a < -Math.PI) return a + Math.PI * 2;
        if (a > Math.PI) return a - Math.PI * 2;
        return a;
    }

    @Override
    public void onLifeTimer() {
        onArrived();
        super.onLifeTimer();
    }

    void onArrived() {
        if (fl_arrived) return;
        fl_arrived = true;
        if (owner != null) owner.onArrived(this);
    }

    @Override
    public void infix() {
        super.infix();
        if (owner == null) return;
        double d = distance(owner.tx, owner.ty);
        if (d < 40) turnSpeed *= 1.1;
        if (d < 20) {
            shootSpeed *= 0.9;
            if (shootSpeed <= 5.8 || owner.anger > 0) onArrived();
        }
        turnSpeed = Math.min(1, turnSpeed);
        shootSpeed = Math.max(2, shootSpeed);
    }

    @Override
    public void update() {
        if (owner != null && !fl_arrived) {
            double tang = Math.atan2(owner.ty - y, owner.tx - x);
            if (adjustAngRad(tang - ang) > 0) ang += turnSpeed * game.tmod;
            if (adjustAngRad(tang - ang) < 0) ang -= turnSpeed * game.tmod;
            dx = Math.cos(ang) * shootSpeed * game.tmod;
            dy = Math.sin(ang) * shootSpeed * game.tmod;
        }
        super.update();
    }
}
