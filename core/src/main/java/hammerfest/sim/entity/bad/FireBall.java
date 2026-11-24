package hammerfest.sim.entity.bad;

import hammerfest.sim.Anim;
import hammerfest.sim.Game;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Bomb;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/bad/FireBall.mt} : boule de feu du « hurry up ». Indestructible, elle
 * traverse le décor et pourchasse son joueur en tournant de plus en plus vite ; elle fait exploser
 * les bombes qu'elle touche. Elle disparaît quand son joueur meurt.
 */
public class FireBall extends Bad {

    public static final double AUTO_ANGER = SECOND * 4;

    public double ang = 270;
    double tang;
    double speed = 2.5;
    double angSpeed = 1.5;
    double angerTimer;
    double summonTimer = 85;
    public boolean fl_summon = true;

    public FireBall() {
        super(BAD_LINKAGES[3]);
        fl_anim = false;
        fl_hitGround = false;
        fl_hitWall = false;
        fl_gravity = false;
        fl_hitBorder = false;
        angerFactor = 0.05;
        maxAnger = 9999;
    }

    /** Apparaît sous l'écran, au centre, et prend le joueur pour cible. */
    public static FireBall attach(Game g, Player p) {
        FireBall f = new FireBall();
        int n = g.countList(PLAYER);
        double offs = n == 1 ? 0 : -30 + p.pid * 60;
        f.initBad(g, GAME_WIDTH / 2.0 + offs, GAME_HEIGHT + 10);
        f.hate(p);
        f.blink(BLINK_DURATION);
        return f;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        register(HU_BAD);
        unregister(BAD_CLEAR);
    }

    @Override
    public void playAnim(Anim a) {
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
    public void tAdd(int cx, int cy) {
        super.tAdd(cx, cy);
        tAddSingle(cx, cy + 1);
    }

    @Override
    public void tRem(int cx, int cy) {
        super.tRem(cx, cy);
        tRemSingle(cx, cy + 1);
    }

    @Override
    public void hit(Entity e) {
        if (fl_summon) return;
        if ((e.types & PLAYER) > 0) {
            Player p = (Player) e;
            if (p.animId != Anim.PLAYER_DIE.id) {
                p.fl_shield = false;
                p.killHit(dx);
            }
        }
        if ((e.types & BOMB) > 0) {
            Bomb b = (Bomb) e;
            if (!b.fl_kill && !b.fl_explode) b.onExplode();
        }
    }

    @Override
    public void angerMore() {
        super.angerMore();
        angSpeed *= 1.15;
    }

    @Override
    public void onHurryUp() {
    }

    @Override
    public void onDeathLine() {
    }

    /** FireBall.endUpdate : le corps est tourné vers sa direction, les yeux suivent l'angle (affichage). */
    @Override
    public void endUpdate() {
        super.endUpdate();
        subRotation = ang == 0 ? 360 : ang;
    }

    static double adjustAngle(double a) {
        while (a < 0) a += 360;
        while (a >= 360) a -= 360;
        return a;
    }

    @Override
    public void update() {
        if (fl_summon) {
            summonTimer -= game.tmod;
            if (summonTimer <= 0) {
                fl_summon = false;
                stopBlink();
            }
        }
        if (player == null || player.fl_kill || player.fl_destroy) {
            game.shine(x, y);
            game.explodeZone(x, y, 4 * CASE_WIDTH);
            game.shake(SECOND, 5);
            destroy();
            return;
        }
        angerTimer += game.tmod * game.diffFactor;
        if (angerTimer >= AUTO_ANGER) {
            angerTimer = 0;
            angerMore();
        }
        tang = adjustAngle(Math.toDegrees(Math.atan2(player.y - y, player.x - x)));
        if (ang - tang > 180) ang -= 360;
        if (tang - ang > 180) ang += 360;
        if (ang < tang) ang = Math.min(tang, ang + angSpeed * speedFactor * game.tmod);
        if (ang > tang) ang = Math.max(tang, ang - angSpeed * speedFactor * game.tmod);
        ang = adjustAngle(ang);
        moveToAng(ang, speed * speedFactor);
        super.update();
    }
}
