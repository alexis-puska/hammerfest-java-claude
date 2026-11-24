package hammerfest.data;

import java.util.ArrayList;
import java.util.List;

/** Quête de assets/data/quests.json (structure de quests.xml). */
public class QuestDef {
    public int id;
    public String nameKey;
    public String descKey;
    public List<Require> require = new ArrayList<>();
    /** Familles débloquées. &lt;100 / &ge;1000 : objets générés en jeu ; 100+ : bonus permanents ; 5000+ : clés. */
    public List<Integer> give = new ArrayList<>();
    public List<Integer> remove = new ArrayList<>();
    /** Options de jeu débloquées (mirror, nightmare, ninja…). */
    public List<String> options = new ArrayList<>();
    /** Modes de jeu débloqués (timeattack, multicoop). */
    public List<String> modes = new ArrayList<>();
    public Integer tokens;
    public Integer bank;
    public Boolean log;

    public QuestDef() {
    }

    public QuestDef(int id) {
        this.id = id;
        this.nameKey = "quest." + id + ".title";
        this.descKey = "quest." + id + ".desc";
    }

    public static class Require {
        public int item;
        public int qty;

        public Require() {
        }

        public Require(int item, int qty) {
            this.item = item;
            this.qty = qty;
        }
    }
}
