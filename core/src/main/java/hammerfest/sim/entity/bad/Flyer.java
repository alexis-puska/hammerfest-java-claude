package hammerfest.sim.entity.bad;

import hammerfest.sim.Anim;
import hammerfest.sim.Game;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/bad/Flyer.mt} : ennemi volant qui rebondit en diagonale sur le décor.
 * Gelé ou assommé, il tombe ; il reprend son vol au réveil.
 */
public class Flyer extends Bad {

    public double xSpeed, ySpeed;
    public boolean fl_fly;
    /** Gelé en plein vol (l'onde de choc le pousse alors à l'horizontale). */
    public boolean fl_intercept;
    public double speed = 4;
    public int dir = 1;

    public Flyer(String linkage) {
        super(linkage);
        angerFactor = 0.5;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        xSpeed = Math.cos(Math.PI * 0.25) * speed;
        ySpeed = Math.sin(Math.PI * 0.25) * speed;
        dir = g.fl_static ? -1 : g.random(2) * 2 - 1;
        fly();
    }

    public void fly() {
        if (!isHealthy()) return;
        playAnim(anger > 0 ? ANGER : WALK);
        dx = dir * xSpeed * speedFactor;
        dy = -ySpeed * speedFactor;
        fl_fly = true;
        fl_intercept = false;
        fl_gravity = false;
        fl_friction = false;
        fl_hitCeil = true;
    }

    public void land() {
        fl_fly = false;
        fl_gravity = true;
        fl_friction = true;
        fl_hitCeil = false;
    }

    @Override
    public void killHit(double dx) {
        super.killHit(dx);
        land();
    }

    @Override
    public void infix() {
        super.infix();
        if (fl_fly && y >= GAME_HEIGHT) {
            y = GAME_HEIGHT;
            dy = -ySpeed * speedFactor;
        }
    }

    @Override
    public void postfix() {
        super.postfix();
        if (fl_fly) fl_friction = false;
    }

    @Override
    public void playAnim(Anim a) {
        if (a.id != JUMP.id) super.playAnim(a);
    }

    @Override
    public void updateSpeed() {
        super.updateSpeed();
        if (fl_fly) {
            dx = dir * xSpeed * speedFactor;
            dy = dy < 0 ? -ySpeed * speedFactor : ySpeed * speedFactor;
        }
    }

    @Override
    public void onNext() {
        if (!fl_fly) super.onNext();
    }

    @Override
    public void onHitGround(double h) {
        if (!fl_fly) {
            fl_intercept = false;
            super.onHitGround(h);
            return;
        }
        fl_stopStepping = true;
        dy = -ySpeed * speedFactor;
    }

    @Override
    public void onHitCeil() {
        if (!fl_fly) {
            super.onHitCeil();
            return;
        }
        fl_stopStepping = true;
        dy = ySpeed * speedFactor;
    }

    @Override
    public void onHitWall() {
        if (!fl_fly) {
            if (world.getCase(cx, cy) != WALL) dx = -dx;
            return;
        }
        fl_stopStepping = true;
        dir = -dir;
        dx = dir * xSpeed * speedFactor;
    }

    @Override
    public void onFreeze() {
        super.onFreeze();
        if (fl_fly) fl_intercept = true;
        land();
    }

    @Override
    public void onKnock() {
        super.onKnock();
        land();
    }

    @Override
    public void onMelt() {
        super.onMelt();
        fly();
    }

    @Override
    public void onWakeUp() {
        super.onWakeUp();
        fly();
    }

    @Override
    public void update() {
        super.update();
        if (fl_fly && dy < 0 && y <= CASE_HEIGHT) dy = Math.abs(dy);
    }

    @Override
    public void endUpdate() {
        super.endUpdate();
        xscale = dir * Math.abs(xscale);
    }

    /** Baleine : volant ordinaire. */
    public static class Baleine extends Flyer {
        public Baleine() {
            super(BAD_LINKAGES[12]);
        }
    }

    /**
     * Tzongre : volant inoffensif qui ne compte pas pour finir le niveau. L'attraper rapporte
     * 50 000 points ; il assomme les ennemis qu'il touche. Il repart au bout de 60 s.
     */
    public static class Tzongre extends Flyer {
        public Tzongre() {
            super(BAD_LINKAGES[15]);
        }

        @Override
        public void init(Game g) {
            super.init(g);
            unregister(BAD_CLEAR);
            setLifeTimer(SECOND * 60);
        }

        @Override
        public void freeze(double d) {
        }

        @Override
        public void knock(double d) {
        }

        @Override
        public void killHit(double dx) {
        }

        @Override
        public void hit(Entity e) {
            if ((e.types & PLAYER) > 0) {
                game.fx("hammer_fx_shine", x, y - CASE_HEIGHT);
                ((Player) e).getScore(this, 50000);
                destroy();
            }
            if ((e.types & BAD) > 0) ((Bad) e).knock(KNOCK_DURATION);
        }
    }
}
