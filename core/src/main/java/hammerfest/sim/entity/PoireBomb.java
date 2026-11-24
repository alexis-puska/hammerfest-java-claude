package hammerfest.sim.entity;

import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/bomb/bad/PoireBomb.mt} : bombe lancée en cloche par la poire. */
public class PoireBomb extends BadBomb {

    public PoireBomb() {
        super("hammer_bomb_poire");
        duration = 45;
        power = 30;
        radius = CASE_WIDTH * 4;
    }

    public static PoireBomb attach(Game g, double x, double y) {
        PoireBomb b = new PoireBomb();
        b.initBomb(g, x, y);
        return b;
    }

    @Override
    public Bomb duplicate() {
        return attach(game, x, y);
    }

    @Override
    public void onExplode() {
        super.onExplode();
        game.explodeZone(x, y, radius);
        blastPlayers();
    }

    /** Le kick retarde l'explosion. */
    @Override
    public void onKick(Player p) {
        super.onKick(p);
        setLifeTimer(lifeTimer + SECOND * 0.5);
    }
}
