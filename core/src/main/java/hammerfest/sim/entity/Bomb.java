package hammerfest.sim.entity;

import hammerfest.sim.Anim;
import hammerfest.sim.Game;

import java.util.List;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/Bomb.mt} : bombe posée, kickable, qui explose en fin de vie. */
public abstract class Bomb extends Mover {

    public static final Anim DROP = new Anim(0, false);
    public static final Anim LOOP = new Anim(1, true);
    public static final Anim EXPLODE = new Anim(2, false);

    public double radius = CASE_WIDTH * 3;
    public double power;
    public double duration;
    public boolean fl_explode;
    public boolean fl_airKick;
    public boolean fl_bumped;
    public String explodeSound = "sound_bomb";

    protected Bomb(String linkage) {
        super(linkage);
        fl_slide = false;
        fl_bounce = false;
        fl_teleport = true;
        fl_wind = true;
        fl_blink = false;
        fl_portal = true;
        fl_bump = true;
        fl_strictGravity = false;
        slideFriction = 0.98;
    }

    public void initBomb(Game g, double x, double y) {
        init(g);
        register(BOMB);
        moveTo(x, y);
        setLifeTimer(duration);
        updateCoords();
        playAnim(DROP);
    }

    @Override
    public double width() {
        return 20 * scaleFactor;
    }

    @Override
    public double height() {
        return 20 * scaleFactor;
    }

    @Override
    public boolean needsPatch() {
        return true;
    }

    public List<Entity> bombGetClose(int type) {
        return game.getClose(type, x, y, radius, fl_stable);
    }

    public void onExplode() {
        playAnim(EXPLODE);
        if (explodeSound != null) game.sound(explodeSound);
        game.fx("rumble", x, y); // vibration des manettes proches (aucun clip : rien à dessiner)
        rotation = 0;
        fl_physics = false;
        fl_explode = true;
    }

    @Override
    public void onEndAnim(int id) {
        super.onEndAnim(id);
        if (id == DROP.id) playAnim(LOOP);
        if (id == EXPLODE.id) destroy();
    }

    @Override
    public void endUpdate() {
        if (!fl_stable) {
            double ang = 30;
            if (dx > 0) rotation += 0.02 * (ang - rotation);
            else rotation -= 0.02 * (ang - rotation);
            rotation = Math.max(-ang, Math.min(ang, rotation));
        }
        super.endUpdate();
    }

    @Override
    public void onLifeTimer() {
        stopBlink();
        onExplode();
    }

    @Override
    public void onDeathLine() {
        super.onDeathLine();
        destroy();
    }

    public void onKick(Player p) {
    }

    @Override
    public void onHitWall() {
        if (fl_bumped) dx = -dx * 0.7;
        else super.onHitWall();
    }

    @Override
    public void onPortal(int pid) {
        game.fx("hammer_fx_shine", x, y - CASE_HEIGHT * 0.5);
        destroy();
    }

    @Override
    public void onPortalRefusal() {
        game.particles(PARTICLE_PORTAL, x, y, 5, 0);
        dx = -dx * 3;
        dy = -5;
    }

    @Override
    public void onBump() {
        fl_bumped = true;
    }

    public Bomb duplicate() {
        return null;
    }
}
