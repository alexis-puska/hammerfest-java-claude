package hammerfest.sim.entity;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/Mover.mt} : décision différée (setNext) et rebond. */
public abstract class Mover extends Physics {

    /** Décision suivante : {dx, dy, delay, action} (dx/dy NaN = null dans l'original). */
    public static final class Next {
        public double dx, dy, delay;
        public final int action;

        Next(double dx, double dy, double delay, int action) {
            this.dx = dx;
            this.dy = dy;
            this.delay = delay;
            this.action = action;
        }
    }

    public Next next;
    public boolean fl_bounce;
    public double bounceFactor = 0.5;

    protected Mover(String linkage) {
        super(linkage);
    }

    public void setNext(double dx, double dy, double delay, int action) {
        next = new Next(dx, dy, delay, action);
    }

    public void onNext() {
        if (next != null && next.action == ACTION_MOVE) {
            dx = next.dx;
            dy = next.dy;
            if (dy != 0) fl_stable = false;
            next = null;
        }
    }

    @Override
    public void onKill() {
        super.onKill();
        next = null;
    }

    @Override
    public void onHitGround(double h) {
        if (fl_bounce) {
            double b = bounceFactor * Math.abs(dy);
            if (b >= 2) {
                setNext(dx, -b, 0, ACTION_MOVE);
                fl_skipNextGravity = true;
            }
        }
        super.onHitGround(h);
    }

    public boolean isReady() {
        return fl_stable && next == null;
    }

    @Override
    public void update() {
        if (next != null) {
            next.delay -= game.tmod;
            if (next.delay <= 0) onNext();
        }
        super.update();
    }
}
