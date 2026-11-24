package hammerfest.sim.entity.boss;

import hammerfest.sim.Anim;
import hammerfest.sim.Game;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.Shoot;

import java.util.ArrayList;
import java.util.List;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/boss/Bat.mt} : boss du niveau 100. Vole d'un point à l'autre, tue au
 * contact. Protégé par un bouclier : une bombe le brise (et l'énerve : boules de feu en orbite),
 * une seconde bombe lui fait perdre une vie. 3 vies ; à sa mort il s'envole puis plonge, et le
 * niveau est terminé.
 *
 * <p>Hérite de Bad pour être traité comme un ennemi par les bombes et collisions, sans compter
 * pour la fin de niveau ni réagir au hurry up.</p>
 */
public class Bat extends Bad {

    public static final Anim WAIT = new Anim(0, true);
    public static final Anim MOVE = new Anim(1, true);
    public static final Anim SWITCH = new Anim(2, false);
    public static final Anim INTRO = new Anim(4, false);
    public static final Anim BAT_KNOCK = new Anim(5, true);
    public static final Anim FINAL_DIVE = new Anim(6, true);
    public static final Anim BAT_ANGER = new Anim(7, true);

    static final double RADIUS = CASE_WIDTH * 1.5;
    static final double SPEED = 6.5;
    static final int LIVES = 3;
    static final double WAIT_TIME = SECOND * 3.5;
    static final double MAX_FALL_ROTATION = 80;
    static final double MIN_DIST = CASE_WIDTH * 7;
    static final double MIN_X_DIST = CASE_WIDTH * 3;

    public int dir = 1;
    public int lives = LIVES;
    public boolean fl_shield, fl_immune, fl_move, fl_wait, fl_death, fl_deathUp, fl_anger;
    double immuneTimer;
    double tx, ty;
    final List<FireBall> fbList = new ArrayList<>();

    public Bat() {
        super("hammer_boss_bat");
        fl_hitGround = false;
        fl_hitCeil = false;
        fl_hitWall = false;
        fl_hitBorder = true;
        fl_gravity = false;
        fl_friction = false;
        fl_moveable = false;
        fl_trap = false;
        realRadius = Double.NaN;
    }

    public static Bat attach(Game g) {
        Bat b = new Bat();
        b.init(g);
        b.moveTo(GAME_WIDTH * 0.5, 30);
        b.animId = -1;
        b.playAnim(INTRO);
        b.xscale = -100;
        b.endUpdate();
        return b;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        unregister(BAD_CLEAR);
        register(BOSS);
    }

    @Override
    public double width() {
        return 60;
    }

    @Override
    public double height() {
        return 50;
    }

    /** Fin du combat : niveau terminé. */
    @Override
    public void destroy() {
        super.destroy();
        game.fl_clear = true;
        game.attachExit();
    }

    void moveRandom() {
        if (fl_death) return;
        double limit = 0.8;
        for (int guard = 0; guard < 200; guard++) {
            tx = GAME_WIDTH * (1 - limit) * 0.5 + game.random((int) Math.round(GAME_WIDTH * limit));
            ty = GAME_HEIGHT * (1 - limit) * 0.5 + game.random((int) Math.round(GAME_HEIGHT * limit));
            if (distance(tx, ty) >= MIN_DIST && Math.abs(x - tx) >= MIN_X_DIST) break;
        }
        int sign = (int) Math.signum(tx - x);
        if (sign != dir) {
            flip();
        } else {
            playAnim(MOVE);
            fl_move = true;
        }
    }

    void flip() {
        halt();
        playAnim(SWITCH);
        dir = -dir;
        xscale = dir * 100;
    }

    void halt() {
        fl_stopStepping = true;
        fl_move = false;
        dx = 0;
        dy = 0;
    }

    void shield() {
        if (fl_shield || fl_death) return;
        fl_shield = true;
    }

    void removeImmunity() {
        fl_immune = false;
        stopBlink();
        shield();
    }

    /** Touché par une bombe : bouclier brisé (colère), sinon perte d'une vie. */
    @Override
    public void freeze(double d) {
        if (fl_immune || fl_death) return;
        if (fl_shield) {
            fl_shield = false;
            bossAnger();
        } else {
            fl_wait = false;
            fl_immune = true;
            immuneTimer = SECOND * 3;
            shield();
            bossCalmDown();
            loseLife();
        }
    }

    @Override
    public void knock(double d) {
    }

    @Override
    public void killHit(double dx) {
    }

    @Override
    public void hit(Entity e) {
    }

    @Override
    public boolean isHealthy() {
        return false;
    }

    @Override
    public void onHurryUp() {
    }

    @Override
    public void onDeathLine() {
    }

    @Override
    public void playAnim(Anim a) {
        // Pas d'ANIM_BAD_ANGER automatique : animations propres au boss
        if (fl_stickyAnim || !fl_anim) return;
        if (animId == a.id && fl_loop == a.loop) return;
        animId = a.id;
        subFrame = 1;
        fl_loop = a.loop;
        frame = 0;
    }

    /** Mort du joueur : le boss se calme et remet son bouclier. */
    public void onPlayerDeath() {
        bossCalmDown();
        shield();
    }

    @Override
    public void onEndAnim(int id) {
        super.onEndAnim(id);
        if (id == INTRO.id) {
            xscale = 100;
            shield();
            moveRandom();
        }
        if (id == SWITCH.id) {
            playAnim(MOVE);
            fl_move = true;
        }
    }

    @Override
    public void prefix() {
        super.prefix();
        if (!fl_immune && !fl_death) {
            for (Entity e : game.getClose(PLAYER, x, y + CASE_HEIGHT * 0.5, RADIUS, false)) ((Player) e).killHit(dx);
        }
    }

    @Override
    public void onNext() {
        if (next != null && next.action == ACTION_MOVE) {
            if (fl_anger) {
                bossCalmDown();
                shield();
                return;
            }
            moveRandom();
            fl_wait = false;
        }
        next = null;
    }

    void loseLife() {
        if (fl_death) return;
        blink(BLINK_DURATION);
        playAnim(BAT_KNOCK);
        game.explosion(x, y, 100);
        lives--;
        if (lives <= 0) kill();
        else game.shake(SECOND, 2);
    }

    void kill() {
        fl_shield = false;
        game.shake(SECOND * 5, 3);
        playAnim(MOVE);
        halt();
        fl_wait = false;
        fl_death = true;
        fl_deathUp = true;
        rotation = -30 * dir;
        dy = -1.5;
        if (y >= GAME_HEIGHT * 0.5) dy *= 2;
        setNext(Double.NaN, Double.NaN, SECOND * 9999, ACTION_MOVE);
    }

    void attachFireBall(double ang, double distFactor) {
        FireBall s = new FireBall();
        s.initShoot(game, x, y - 10);
        s.bat = this;
        s.ang = Math.toRadians(ang);
        s.maxDist *= distFactor;
        s.distSpeed *= distFactor;
        s.center();
        fbList.add(s);
    }

    void bossAnger() {
        fl_wait = false;
        halt();
        playAnim(BAT_ANGER);
        game.explosion(x, y, 60);
        fl_anger = true;
        attachFireBall(0, 1.0);
        attachFireBall(180, 1.0);
        attachFireBall(0, 0.5);
        attachFireBall(180, 0.5);
        attachFireBall(90, 1.0);
        attachFireBall(270, 1.0);
        setNext(Double.NaN, Double.NaN, SECOND * 15, ACTION_MOVE);
    }

    void bossCalmDown() {
        for (FireBall f : fbList) f.destroy();
        if (fl_anger) {
            game.explosion(x, y, 50);
            playAnim(WAIT);
        }
        fbList.clear();
        setNext(Double.NaN, Double.NaN, SECOND, ACTION_MOVE);
        fl_anger = false;
    }

    @Override
    public void update() {
        if (!fl_death) game.huTimer = 0;
        else game.huTimer += game.tmod * 3;
        super.update();
        if (fl_immune) {
            immuneTimer -= game.tmod;
            if (immuneTimer <= 0) removeImmunity();
        }
        if (fl_move) {
            moveToPoint(tx, ty, SPEED);
            if (distance(tx, ty) <= 10) {
                halt();
                fl_wait = true;
                playAnim(WAIT);
                setNext(Double.NaN, Double.NaN, WAIT_TIME, ACTION_MOVE);
            }
        }
        if (fl_death) {
            if (fl_deathUp && game.random(6) == 0) {
                game.particles(PARTICLE_STONE, game.visualRandom(GAME_WIDTH), 0, 1, 3);
                game.shake(SECOND, 1);
                game.explodeZone(x + game.random(20) * (game.random(2) * 2 - 1), y + game.random(20) * (game.random(2) * 2 - 1), game.random(40) + 10);
            }
            if (!fl_deathUp && game.visualRandom(2) == 0) { // chute : éclats tournés au hasard
                double s = game.visualRandom(50) + 50;
                game.fx("hammer_fx_pop", x + game.visualRandom(20) * (game.visualRandom(2) * 2 - 1), y - game.visualRandom(40),
                        game.visualRandom(360), s, s, 100);
            }
            if (y <= -50 && fl_deathUp) {
                xscale = 100;
                playAnim(FINAL_DIVE);
                dy = 5;
                fl_deathUp = false;
            }
            if (!fl_deathUp) {
                rotation = Math.min(MAX_FALL_ROTATION, rotation + 2.5 * game.tmod);
                dy += 0.1 * game.tmod;
                if (game.visualRandom(2) == 0) game.particles(PARTICLE_SPARK, x, y, 1, 2);
                if (y >= DEATH_LINE + GAME_HEIGHT * 1.5) {
                    game.shake(SECOND * 1.5, 5);
                    destroy();
                }
            }
        }
    }

    void moveToPoint(double px, double py, double speed) {
        double rad = Math.atan2(py - y, px - x);
        dx = Math.cos(rad) * speed;
        dy = Math.sin(rad) * speed;
    }

    /** Boule de feu en orbite autour du boss (shoot/BossFireBall.mt). */
    public static class FireBall extends Shoot {
        Bat bat;
        double ang;
        double dist = CASE_WIDTH * 0.2;
        double distSpeed = 2;
        double turnSpeed = 0.025;
        double maxDist = CASE_WIDTH * 5;

        public FireBall() {
            super("hammer_shoot_boss_fireball");
            fl_checkBounds = false;
            fl_largeTrigger = true;
        }

        @Override
        public void init(Game g) {
            super.init(g);
            fl_physics = false;
            animId = -1;
            playAnim(ANIM_LOOP);
        }

        void center() {
            moveTo(bat.x + Math.cos(ang) * dist, bat.y + Math.sin(ang) * dist);
        }

        @Override
        public void destroy() {
            game.explodeZone(x, y, CASE_WIDTH * 2);
            super.destroy();
        }

        @Override
        public void hit(Entity e) {
            if ((e.types & BOMB) > 0) {
                hammerfest.sim.entity.Bomb b = (hammerfest.sim.entity.Bomb) e;
                if (!b.fl_explode) {
                    game.fx("hammer_fx_pop", b.x, b.y - CASE_HEIGHT);
                    b.destroy();
                    game.particles(PARTICLE_CLASSIC_BOMB, x, y, 6, 0);
                    destroy();
                    return;
                }
            }
            if ((e.types & PLAYER) > 0) ((Player) e).killHit(dx);
        }

        @Override
        public void update() {
            super.update();
            int ocx = cx, ocy = cy;
            ang += game.tmod * turnSpeed;
            dist = Math.min(dist + game.tmod * distSpeed, maxDist);
            center();
            if (ocx != cx || ocy != cy) checkHits();
        }
    }
}
