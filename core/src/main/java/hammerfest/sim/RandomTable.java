package hammerfest.sim;

import java.util.Map;
import java.util.TreeMap;

/**
 * Portage de {@code RandomManager} : tirage pondéré d'un id (objets), les poids venant de la
 * rareté des objets des familles débloquées (Data.getRandFromFamilies). Parcours par id croissant,
 * comme le tableau indexé d'origine.
 */
public class RandomTable {

    private final TreeMap<Integer, Integer> weights = new TreeMap<>();
    private int sum;

    public void put(int id, int weight) {
        Integer old = weights.put(id, Math.max(0, weight));
        sum += Math.max(0, weight) - (old == null ? 0 : old);
    }

    public void remove(int id) {
        put(id, 0);
    }

    public boolean isEmpty() {
        return sum <= 0;
    }

    /** Id tiré, ou -1 si aucun poids. */
    public int draw(Game g) {
        if (sum <= 0) return -1;
        int target = g.random(sum);
        int acc = 0;
        for (Map.Entry<Integer, Integer> e : weights.entrySet()) {
            acc += e.getValue();
            if (target < acc) return e.getKey();
        }
        return -1;
    }
}
