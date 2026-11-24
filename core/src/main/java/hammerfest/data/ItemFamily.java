package hammerfest.data;

import java.util.ArrayList;
import java.util.List;

/** Famille d'objets : ids &lt; 1000 spéciaux, ids &ge; 1000 objets à score. */
public class ItemFamily {
    public int id;
    public String nameKey;
    public List<ItemDef> items = new ArrayList<>();

    public ItemFamily() {
    }

    public ItemFamily(int id) {
        this.id = id;
        this.nameKey = "family." + id;
    }

    public boolean isScoreFamily() {
        return id >= 1000;
    }
}
