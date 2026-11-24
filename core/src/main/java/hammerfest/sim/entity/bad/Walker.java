package hammerfest.sim.entity.bad;

import hammerfest.sim.Game;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Entity;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/bad/Walker.mt} : ennemi qui marche, fait demi-tour ou se laisse tomber. */
public class Walker extends Bad {

    public double speed = 2;
    public int dir = 1;
    public boolean fl_fall;
    public boolean fl_willFallDown;
    public double chanceFall;
    public double recentParticles;

    public Walker(String linkage) {
        super(linkage);
        setFall(-1);
    }

    @Override
    public void init(Game g) {
        super.init(g);
        if (g.fl_static) dir = x <= GAME_WIDTH * 0.5 ? 1 : -1;
        else dir = g.random(2) * 2 - 1;
    }

    public void halt() {
        if (!fl_freeze && !fl_knock) dx = 0;
        dy = 0;
    }

    public void walk() {
        double base = speedFactor * speed * game.badSpeedFactor;
        if (!isReady()) return;
        if (dir == -1) base = -base;
        if (fl_stable) playAnim(WALK);
        setNext(base, 0, 0, ACTION_MOVE);
    }

    public void fallBack() {
        dx = -dx;
        if (!fl_freeze && !fl_knock) dir = -dir;
    }

    @Override
    public void infix() {
        if (fl_stable && world.checkFlag(cx, cy, IA_FALL_SPOT)) {
            onFall();
            fl_stopStepping = true;
            return;
        }
        super.infix();
    }

    @Override
    public void postfix() {
        super.postfix();
        fl_friction = !isReady();
    }

    @Override
    public boolean isReady() {
        return super.isReady() && isHealthy();
    }

    /** Chance (sur 100) de se laisser tomber ; négatif = jamais. */
    public void setFall(double chance) {
        if (chance < 0) {
            fl_fall = false;
        } else {
            fl_fall = true;
            chanceFall = chance * 10;
        }
    }

    @Override
    public void calmDown() {
        super.calmDown();
        if (animId == ANGER.id) playAnim(WALK);
        if (isReady()) {
            halt();
            walk();
        }
    }

    @Override
    public void angerMore() {
        super.angerMore();
        if (isReady()) {
            halt();
            walk();
        }
    }

    @Override
    public void updateSpeed() {
        super.updateSpeed();
        if (game != null) walk();
    }

    boolean decideFall() {
        if (fl_playerClose && player != null) {
            int d = player.cy - cy;
            int fall = world.fallHeight(cx, cy);
            return fall > 0 && d > 0 && fall <= d + 3;
        }
        return game.random(1000) < chanceFall;
    }

    @Override
    public void onNext() {
        if (next != null && next.action == ACTION_WALK) {
            next = null;
            walk();
        }
        if (next != null && next.action == ACTION_FALLBACK) {
            next = null;
            walk();
            if (next != null) next.dx = -next.dx;
        }
        if (next != null && next.action == ACTION_MOVE && next.dy == 0) playAnim(WALK);
        super.onNext();
    }

    @Override
    public void onHitWall() {
        if (!fl_stopStepping && world.getCase(cx, cy) != WALL && (!isHealthy() || fl_stable)) fallBack();
        if (fl_freeze && Math.abs(dx) >= 4) {
            if (recentParticles <= 0) {
                game.shake(SECOND * 0.2, 2);
                game.particles(PARTICLE_ICE_BAD, x, y, 1, 4);
                recentParticles = 10;
            }
            if (fl_stable) game.dust(cx, cy + 1);
        }
    }

    @Override
    public void onHitGround(double h) {
        super.onHitGround(h);
        halt();
        walk();
    }

    public void onFall() {
        if (!isReady()) return;
        if (world.checkFlag(Entity.xRtc(oldX), Entity.yRtc(oldY), IA_SMALL_SPOT)
                && world.checkFlag(cx, cy, IA_ALLOW_FALL)) {
            halt();
            return;
        }
        if (fl_fall) {
            if (decideFall() && world.checkFlag(cx, cy, IA_ALLOW_FALL)) fl_willFallDown = true;
            if (fl_willFallDown) {
                fl_willFallDown = false;
                halt();
                return;
            }
        }
        x = oldX;
        y = oldY;
        fallBack();
    }

    @Override
    public void onFreeze() {
        super.onFreeze();
        next = null;
    }

    @Override
    public void onMelt() {
        super.onMelt();
        walk();
        if (!fl_stable) playAnim(JUMP);
    }

    @Override
    public void onKnock() {
        super.onKnock();
        next = null;
    }

    @Override
    public void onWakeUp() {
        super.onWakeUp();
        walk();
        if (!fl_stable) playAnim(JUMP);
    }

    @Override
    public void endUpdate() {
        super.endUpdate();
        if (!fl_freeze && !fl_knock) {
            if (dx < 0) dir = -1;
            if (dx > 0) dir = 1;
            xscale = dir * Math.abs(xscale);
        }
    }

    @Override
    public void update() {
        fl_willFallDown = false;
        if (recentParticles > 0) recentParticles -= game.tmod;
        super.update();
    }
}
