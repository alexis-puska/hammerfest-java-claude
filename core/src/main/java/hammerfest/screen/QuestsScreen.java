package hammerfest.screen;

import hammerfest.ui.Tr;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import hammerfest.HammerfestGame;
import hammerfest.data.Lang;
import hammerfest.data.QuestDef;
import hammerfest.quest.QuestBook;

/** Quêtes : état, progression possédés/requis depuis le frigo, déblocages et retraits des quêtes terminées. */
public class QuestsScreen extends MenuScreen {

    private static final float WIDTH = 540;

    public QuestsScreen(HammerfestGame game) {
        super(game);
        Table panel = panel(Tr.t("Quêtes"));
        Lang lang = game.data.lang;

        int done = game.save.completedQuests.size();
        panel.add(label(done + " / " + game.data.quests.size() + Tr.t(" quêtes terminées"), "dim")).padBottom(8).row();

        Table list = new Table();
        list.top().left();
        list.defaults().left();
        for (QuestDef q : game.data.quests) {
            QuestBook.Status st = game.quests.status(q, game.save);
            String stText = st == QuestBook.Status.COMPLETED ? Tr.t("terminée") : st == QuestBook.Status.IN_PROGRESS ? Tr.t("en cours") : Tr.t("verrouillée");
            String stStyle = st == QuestBook.Status.COMPLETED ? "ok" : st == QuestBook.Status.IN_PROGRESS ? "accent" : "dim";

            Table head = new Table();
            head.add(label(lang.get(q.nameKey), "heading")).left().expandX();
            head.add(label(stText, stStyle)).right();
            list.add(head).width(WIDTH).padTop(10).row();

            StringBuilder req = new StringBuilder();
            for (QuestDef.Require r : q.require) {
                int have = Math.min(game.save.fridgeCount(r.item), r.qty);
                if (req.length() > 0) req.append("\n");
                req.append(have >= r.qty ? "+ " : "- ").append(game.data.itemName(r.item))
                        .append("  ").append(have).append("/").append(r.qty);
            }
            list.add(wrapped(req.toString(), "default")).width(WIDTH).padLeft(12).row();

            if (st == QuestBook.Status.COMPLETED) {
                list.add(wrapped(lang.plain(q.descKey), "ok")).width(WIDTH).padLeft(12).padTop(4).row();
                String unlocks = describeUnlocks(q, lang);
                if (!unlocks.isEmpty()) list.add(wrapped(unlocks, "dim")).width(WIDTH).padLeft(12).row();
            }
        }

        ScrollPane scroll = new ScrollPane(list, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        panel.add(scroll).expand().fill().row();
        addBackButton(panel);
        stage.setScrollFocus(scroll);
    }

    private Label wrapped(String text, String style) {
        Label l = label(text, style);
        l.setWrap(true);
        return l;
    }

    private String describeUnlocks(QuestDef q, Lang lang) {
        StringBuilder sb = new StringBuilder();
        for (int f : q.give) {
            String key = "family." + f;
            append(sb, Tr.t("Débloque : ") + (lang.has(key) ? lang.get(key) : familyLabel(f)));
        }
        for (String o : q.options) append(sb, Tr.t("Option débloquée : ") + o);
        for (String m : q.modes) append(sb, Tr.t("Mode débloqué : ") + m);
        for (int f : q.remove) append(sb, "Retire : " + lang.get("family." + f));
        return sb.toString();
    }

    private static String familyLabel(int f) {
        if (f >= 5000) return Tr.t("clé ") + (f - 5000);
        if (f >= 100 && f < 1000) return Tr.t("bonus permanent ") + f;
        return Tr.t("famille ") + f;
    }

    private static void append(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("\n");
        sb.append(s);
    }
}
