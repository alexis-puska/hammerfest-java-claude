package hammerfest.sim.entity.shoot;

import hammerfest.sim.Game;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.Shoot;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/shoot/Hammer.mt} : marteau (objet 85) qui suit le joueur et assomme les ennemis proches. */
public class Hammer extends Shoot {

    public Player player;

    public Hammer() {
        super("hammer_shoot_hammer");
        shootSpeed = 0;
    }

    public static Hammer attach(Game g, Player p) {
        Hammer s = new Hammer();
        s.initShoot(g, p.x, p.y - 10);
        s.player = p;
        return s;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        animId = -1;
        playAnim(ANIM_LOOP);
    }

    @Override
    public void destroy() {
        if (fl_destroy) return;
        if (player != null) player.specialMan.interrupt(85);
        game.fx("hammer_fx_pop", x, y - CASE_HEIGHT / 2.0);
        super.destroy();
    }

    /**
     * Hammer.endUpdate : tourné du côté où regarde le joueur, APRÈS Shoot.endUpdate (qui l'orienterait selon dx,
     * nul pour le marteau : toujours à droite).
     */
    @Override
    public void endUpdate() {
        super.endUpdate();
        if (player != null) xscale = player.dir < 0 ? -Math.abs(xscale) : Math.abs(xscale);
    }

    @Override
    public void update() {
        double hx = Math.max(1, Math.min(GAME_WIDTH - 1, player.x + player.dir * CASE_WIDTH * 0.7));
        double hy = Math.max(20, Math.min(GAME_HEIGHT - 1, player.y - CASE_HEIGHT * 0.7));
        moveTo(hx, hy);
        super.update();
        for (Entity e : game.getClose(BAD, x, y, CASE_WIDTH * 2, false)) {
            ((Bad) e).killHit(player.dir * 9);
        }
        if (player.fl_kill || game.fl_clear || !player.specialMan.active(85)) destroy();
    }
}
