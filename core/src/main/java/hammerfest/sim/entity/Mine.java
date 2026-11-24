package hammerfest.sim.entity;

import hammerfest.sim.Anim;
import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/bomb/bad/Mine.mt} : mine posée par le kiwi. Elle devient invisible une
 * fois au sol, se déclenche quand un joueur approche (ou qu'on la kicke) et explose peu après.
 * Non déclenchée, elle disparaît sans exploser au bout de 15 s.
 */
public class Mine extends BadBomb {

    static final double SUDDEN_DEATH = SECOND * 1.1;
    static final double HIDE_SPEED = 3;
    static final double DETECT_RADIUS = CASE_WIDTH * 2.5;

    public boolean fl_trigger;
    public boolean fl_defuse;
    public boolean fl_plant;

    public Mine() {
        super("hammer_bomb_mine");
        fl_blink = true;
        fl_alphaBlink = false;
        duration = SECOND * 15;
        power = 50;
        radius = CASE_WIDTH * 3;
    }

    public static Mine attach(Game g, double x, double y) {
        Mine m = new Mine();
        m.initBomb(g, x, y);
        return m;
    }

    @Override
    public void initBomb(Game g, double x, double y) {
        super.initBomb(g, x, y);
        if (game.fl_bombExpert) radius *= 1.3;
    }

    @Override
    public Bomb duplicate() {
        return attach(game, x, y);
    }

    @Override
    public void onHitGround(double h) {
        super.onHitGround(h);
        if (!fl_trigger) playAnim(LOOP);
        if (!fl_defuse) rotation = 0;
    }

    @Override
    public void onExplode() {
        if (!fl_trigger || fl_defuse) {
            game.fx("hammer_fx_pop", x, y - CASE_HEIGHT);
            destroy();
        } else {
            super.onExplode();
            game.explodeZone(x, y, radius);
            blastPlayers();
        }
    }

    @Override
    public void onKick(Player p) {
        super.onKick(p);
        triggerMine();
        updateLifeTimer(SECOND * 0.7);
        dx *= 0.8 + game.random(10) / 10.0;
    }

    public void triggerMine() {
        if (fl_trigger) return;
        fl_trigger = true;
        playAnim(DROP);
        dy = -7;
        show();
        alpha = 100;
        setLifeTimer(SUDDEN_DEATH * 3); // pour forcer le clignotement
        updateLifeTimer(SUDDEN_DEATH);
        blinkLife();
    }

    @Override
    public void playAnim(Anim a) {
        super.playAnim(a);
        if (a.id == DROP.id) fl_loop = true;
        if (a.id == LOOP.id) fl_loop = false;
    }

    @Override
    public void update() {
        super.update();
        if (fl_stable && !fl_plant) fl_plant = true;
        if (fl_plant && !fl_trigger && alpha > 0) {
            alpha -= game.tmod * HIDE_SPEED;
            if (alpha <= 0) hide();
        }
        if (fl_plant && !fl_trigger) {
            for (Entity e : game.getClose(PLAYER, x, y, DETECT_RADIUS, false)) {
                if (!e.fl_kill) triggerMine();
            }
        }
    }
}
