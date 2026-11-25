package hammerfest.sim.entity;

import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/item/ScoreItem.mt} : objet à points (cristaux, diamants, nourriture…). */
public class ScoreItem extends Item {

    public ScoreItem() {
        super("hammer_item_score");
    }

    /** @param id id de données (1000+) ou id de clip (0+) ; @param subId -1 = null */
    public static ScoreItem attach(Game g, double x, double y, int id, int subId) {
        ScoreItem it = new ScoreItem();
        if (id >= 1000) id -= 1000;
        it.initItem(g, x, y, id, subId);
        if (it.id == CONVERT_DIAMANT) it.register(PERFECT_ITEM);
        return it;
    }

    /** Valeur en points (ScoreItem.execute). */
    public int value() {
        Integer v = game.itemValue(id + 1000);
        if (v != null && v != 0) return v;
        switch (id) {
            case 0: return crystalValue(subId);
            case DIAMANT: return 2000;
            case CONVERT_DIAMANT: return (int) Math.round(Math.min(10000, 75 * Math.pow(subId + 1, 4)));
            default: return 0;
        }
    }

    /** Data.getCrystalValue. */
    public static int crystalValue(int subId) {
        return (int) Math.round(Math.min(50000, 500 * Math.round(Math.pow(subId + 1, 2))));
    }

    @Override
    public void execute(Player p) {
        game.sound("sound_item_score");
        p.getScore(this, value());
        if (game.fl_timeAttack && id == 0) { // cristal : du temps en moins
            int bonus = game.crystalTimeBonus(subId);
            for (Player pl : game.players()) game.timePop(pl.x, pl.y, bonus);
        }
        game.pickUp(id + 1000);
        if (game.itemRarity(id + 1000) > 0) game.attachItemName(id + 1000); // ScoreItem.execute : rareté r > 0
        if (id == CONVERT_DIAMANT) p.specialMan.onPickPerfectItem();
        super.execute(p);
    }
}
