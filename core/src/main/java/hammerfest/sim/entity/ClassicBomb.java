package hammerfest.sim.entity;

import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/bomb/player/Classic.mt} : bombe de glace (gèle et repousse les ennemis). */
public class ClassicBomb extends PlayerBomb {

    public ClassicBomb() {
        super("hammer_bomb_classic");
        duration = 45;
        power = 30;
    }

    public static ClassicBomb attach(Game g, double x, double y) {
        ClassicBomb b = new ClassicBomb();
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
        game.particles(PARTICLE_ICE, x, y, 2, 2);
        game.shake(SECOND * 0.35, 1.5);
        for (Entity e : bombGetClose(BAD)) {
            if (e instanceof Freezable) {
                ((Freezable) e).setCombo(uniqId);
                ((Freezable) e).freeze(FREEZE_DURATION);
            }
            if (e instanceof Physics) shockWave((Physics) e, radius, power);
        }
    }
}
