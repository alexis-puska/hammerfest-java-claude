package hammerfest.sim.entity;

import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/bomb/player/SoccerBall.mt} : ballon de SoccerFest. Une « bombe » qui n'explose
 * jamais, rebondit, se frappe au pied (même en l'air) et marque en entrant dans une case de but.
 */
public class SoccerBall extends PlayerBomb {

    static final double TOP_SPEED = 4;

    /** Dernier joueur à avoir frappé le ballon (buteur). */
    public Player lastPlayer;
    double burnTimer;

    public SoccerBall() {
        super("hammer_bomb_soccer");
        duration = 999999;
        bounceFactor = 0.8;
        fl_bounce = true;
        slideFriction = FRICTION_SLIDE * 0.9;
    }

    public static SoccerBall attach(Game g, double x, double y) {
        SoccerBall b = new SoccerBall();
        b.initBomb(g, x, y);
        return b;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        register(SOCCERBALL);
        game.shine(x, y - CASE_HEIGHT * 0.5);
    }

    @Override
    public Bomb duplicate() {
        return null;
    }

    @Override
    void upgradeBomb(Player p) {
    }

    @Override
    public void onKick(Player p) {
        super.onKick(p);
        lastPlayer = p;
        if (Math.abs(dx) < 10) {
            dx *= 3;
            dy *= 1.1;
        }
    }

    /** Touché par une flamme : le ballon brûle une seconde (effet visuel). */
    public void burn() {
        burnTimer = SECOND;
    }

    @Override
    public void onExplode() {
    }

    @Override
    public void onLifeTimer() {
    }

    @Override
    public void infix() {
        super.infix();
        if (fl_destroy) return;
        int id = world.getCase(cx, cy);
        if (id == FIELD_GOAL_1) {
            game.goal(1, this);
            destroy();
        } else if (id == FIELD_GOAL_2) {
            game.goal(0, this);
            destroy();
        }
    }

    @Override
    public void onHitWall() {
        int ocx = xRtc(oldX), ocy = yRtc(oldY);
        if (world.getCase(ocx, ocy) != GROUND) {
            dx = -dx;
            if (Math.abs(dx) > 7) game.particlesDir(PARTICLE_DUST, x, y, 1, 5, dx);
        }
    }

    @Override
    public void endUpdate() {
        super.endUpdate();
        rotation += dx * 5;
    }

    @Override
    public void update() {
        double speed = Math.sqrt(dx * dx + dy * dy);
        animFactor = 0.5 * speed / TOP_SPEED;
        fl_airKick = true;
        if (burnTimer > 0) {
            // étincelles et flammes à chaque cycle, taille selon la vitesse
            game.particles(PARTICLE_SPARK, x, y, 0, 3);
            double s = 100 * Math.min(1, speed / TOP_SPEED);
            game.fx("hammer_fx_ballBurn", x + game.visualRandom(5) * (game.visualRandom(2) * 2 - 1), y - game.visualRandom(20), 0, s, s, 100);
            burnTimer -= game.tmod;
        }
        super.update();
    }
}
