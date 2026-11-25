package hammerfest.sim.entity;

import hammerfest.sim.Game;

/**
 * Portage de {@code entity/item/SpecialItem.mt} : objet spécial. Ramassé, il part au frigo ;
 * ses effets en jeu (SpecialManager) ne sont pas encore portés.
 */
public class SpecialItem extends Item {

    public SpecialItem() {
        super("hammer_item_special");
    }

    public static SpecialItem attach(Game g, double x, double y, int id, int subId) {
        if (g.fl_clear && id == 0) return null; // pas d'extend si niveau terminé
        SpecialItem it = new SpecialItem();
        it.initItem(g, x, y, id, subId);
        it.register(hammerfest.sim.Const.SPECIAL_ITEM);
        return it;
    }

    @Override
    public void execute(Player p) {
        game.pickUp(id);
        p.specialMan.execute(this);
        game.sound("sound_item_special");
        if (id > 0) game.attachItemName(id); // SpecialItem.execute
        super.execute(p);
    }

    @Override
    public void update() {
        super.update();
        if (id != 0 && game.random(4) == 0) {
            double s = game.visualRandom(70) + 30;
            game.fx("hammer_fx_star", x + game.random(15) * (game.random(2) * 2 - 1), y - game.random(10), 0, s, s, 100);
        }
    }
}
