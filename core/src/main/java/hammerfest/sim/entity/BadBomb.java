package hammerfest.sim.entity;

import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/bomb/BadBomb.mt} : bombe d'ennemi, facile à repousser au kick. */
public abstract class BadBomb extends Bomb {

    public Bad owner;

    protected BadBomb(String linkage) {
        super(linkage);
        fl_airKick = true;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        register(BAD_BOMB);
    }

    @Override
    public void initBomb(Game g, double x, double y) {
        super.initBomb(g, x, y);
        if (game.fl_bombExpert) radius *= 1.4;
    }

    @Override
    public void onKick(Player p) {
        super.onKick(p);
        dx *= 3;
    }

    /** Tue et projette les joueurs dans le rayon (explosion des bombes d'ennemis). */
    void blastPlayers() {
        game.explodeZone(x, y, radius);
        for (Entity e : game.getClose(PLAYER, x, y, radius, false)) {
            Player p = (Player) e;
            p.killHit(0);
            shockWave(p, radius, power);
            p.dy = -10 - game.random(20);
        }
    }
}
