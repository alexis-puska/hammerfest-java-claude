package hammerfest.data;

import java.util.ArrayList;
import java.util.List;

/** Set de niveaux (assets/data/levels/&lt;name&gt;.json) : adventure, tutorial, timeAttack… */
public class LevelSet {
    public String name;
    public List<LevelData> levels = new ArrayList<>();

    public LevelSet() {
    }

    public LevelSet(String name) {
        this.name = name;
    }

    public LevelData level(int id) {
        return id >= 0 && id < levels.size() ? levels.get(id) : null;
    }
}
