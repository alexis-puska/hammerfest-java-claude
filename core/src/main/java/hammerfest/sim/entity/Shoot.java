package hammerfest.sim.entity;

import hammerfest.sim.Anim;
import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/Shoot.mt} : projectile sans gravité ni collision avec le décor.
 * Son animation est le clip lui-même (setSub(this)) : {@link #subFrame} parcourt les images du clip.
 */
public abstract class Shoot extends Physics {

    public static final Anim ANIM = new Anim(0, false);
    public static final Anim ANIM_LOOP = new Anim(0, true);

    public double shootSpeed;
    public double coolDown = 50;
    public boolean fl_borderBounce;
    public boolean fl_checkBounds = true;

    protected Shoot(String linkage) {
        super(linkage);
        fl_hitWall = false;
        fl_hitBorder = false;
        fl_hitGround = false;
        fl_gravity = false;
        fl_friction = false;
        fl_bump = false;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        register(SHOOT);
        animId = -1;
        playAnim(ANIM);
    }

    public void initShoot(Game g, double x, double y) {
        init(g);
        moveTo(x, y);
        endUpdate();
    }

    /** Le clip du tir est sa propre animation : toutes ses images, quel que soit l'état. */
    @Override
    public int subTotal() {
        return Math.max(1, game.frames.frames(linkage, -1));
    }

    @Override
    public double width() {
        return 10;
    }

    @Override
    public double height() {
        return 10;
    }

    @Override
    public void endUpdate() {
        if (dy == 0) xscale = dx < 0 ? -Math.abs(xscale) : Math.abs(xscale);
        super.endUpdate();
    }

    @Override
    public void update() {
        super.update();
        if (!fl_checkBounds) return;
        if (fl_borderBounce) {
            if (x < 0) dx = Math.abs(dx);
            if (x >= GAME_WIDTH) dx = -Math.abs(dx);
            if (y < 0) dy = Math.abs(dy);
            if (y >= GAME_HEIGHT) dy = -Math.abs(dy);
        } else if (!world.inBound(cx, cy)) {
            destroy();
        }
    }
}
