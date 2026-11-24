package hammerfest.sim.entity.bad;

import hammerfest.sim.Game;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Entity;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/bad/Spear.mt} : pique fixe, indestructible, qui ne compte pas pour
 * terminer le niveau. Mortelle pour le joueur et pour les ennemis qui tombent dessus.
 */
public class Spear extends Bad {

    public Spear() {
        super(BAD_LINKAGES[BAD_SPEAR]);
        fl_physics = false;
        fl_anim = false;
        realRadius = CASE_WIDTH * 0.7;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        unregister(BAD_CLEAR);
        register(SPEAR);
    }

    @Override
    public void initBad(Game g, double x, double y) {
        super.initBad(g, x, y);
        // Orientation selon le support : sol (1), mur gauche (2), plafond (3), mur droit (4)
        int frame = 1;
        if (world.getCase(cx, cy + 1) > 0) frame = 1;
        else if (world.getCase(cx, cy - 1) > 0) frame = 3;
        else if (world.getCase(cx - 1, cy) > 0) frame = 2;
        else if (world.getCase(cx + 1, cy) > 0) frame = 4;
        animId = frame - 1;
        subFrame = 1;
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
    public void burn() {
        game.fx("hammer_fx_pop", x, y);
    }

    @Override
    public void hit(Entity e) {
        super.hit(e);
        if ((e.types & BAD_CLEAR) > 0) {
            Bad b = (Bad) e;
            if (b.fl_physics && b.fl_trap) b.killHit(Double.NaN);
        }
    }
}
