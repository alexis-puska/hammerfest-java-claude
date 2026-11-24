package hammerfest.sim.entity;

import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/Item.mt} : objet ramassable, figé sur son image (id) et sa variante
 * (sous-clip à l'image subId), qui disparaît au bout de 8 s en clignotant.
 */
public abstract class Item extends Physics {

    /** Id dans le clip : objets spéciaux 0..n, objets à score 0..n (= id de données - 1000). */
    public int id;
    /** Variante (taille de cristal…) ; -1 = null. */
    public int subId = -1;

    protected Item(String linkage) {
        super(linkage);
        fl_anim = false;
        fl_alphaBlink = true;
        fl_largeTrigger = true;
        fl_strictGravity = false;
        minAlpha = 0;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        setLifeTimer(ITEM_LIFE_TIME);
        register(ITEM);
    }

    public void initItem(Game g, double x, double y, int id, int subId) {
        init(g);
        moveTo(x, y);
        this.id = id;
        this.subId = subId;
        animId = id;                          // gotoAndStop(id+1)
        subFrame = Math.max(1, subId + 1);    // sub.gotoAndStop(subId+1)
        game.fx("hammer_fx_shine", x, y - CASE_HEIGHT);
        endUpdate();
    }

    @Override
    public double width() {
        return 20;
    }

    @Override
    public double height() {
        return 20;
    }

    /** Ramassé par le joueur. */
    public void execute(Player p) {
        destroy();
    }

    @Override
    public void onDeathLine() {
        super.onDeathLine();
        moveTo(x, -30);
        dy = 0;
    }

    @Override
    public void onLifeTimer() {
        super.onLifeTimer();
        game.fx("hammer_fx_pop", x, y - CASE_HEIGHT / 2.0);
        game.sound("sound_pop");
    }
}
