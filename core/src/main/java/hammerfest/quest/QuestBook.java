package hammerfest.quest;

import hammerfest.data.QuestDef;
import hammerfest.save.SaveData;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation des quêtes à partir du frigo et calcul des déblocages
 * (logique tenue autrefois par le site : familles envoyées au jeu via le Loader).
 */
public class QuestBook {

    /**
     * Familles disponibles pour un nouveau joueur. Déduites des données : ce sont les familles
     * d'objets qu'aucune quête ne donne mais dont certaines sont retirées par une quête
     * (0 objets de base, 7 Cœur 1, 18 Kit d'appel, 1000 Petites gâteries, 1028 Sandy en kit).
     * À valider contre le jeu d'origine.
     */
    public static final int[] START_FAMILIES = {0, 7, 18, 1000, 1028};

    public enum Status { LOCKED, IN_PROGRESS, COMPLETED }

    private final List<QuestDef> quests;

    public QuestBook(List<QuestDef> quests) {
        this.quests = quests;
    }

    public boolean isSatisfied(QuestDef q, SaveData save) {
        for (QuestDef.Require r : q.require) {
            if (save.fridgeCount(r.item) < r.qty) return false;
        }
        return true;
    }

    public Status status(QuestDef q, SaveData save) {
        if (save.completedQuests.contains(q.id)) return Status.COMPLETED;
        for (QuestDef.Require r : q.require) {
            if (save.fridgeCount(r.item) > 0) return Status.IN_PROGRESS;
        }
        return Status.LOCKED;
    }

    /**
     * Valide toutes les quêtes satisfaites par le frigo puis recalcule familles, options et modes.
     *
     * @return les quêtes nouvellement terminées (pour affichage dans le récap)
     */
    public List<QuestDef> update(SaveData save) {
        List<QuestDef> newlyCompleted = new ArrayList<>();
        for (QuestDef q : quests) {
            if (!save.completedQuests.contains(q.id) && isSatisfied(q, save)) {
                save.completedQuests.add(q.id);
                newlyCompleted.add(q);
            }
        }
        recomputeUnlocks(save);
        return newlyCompleted;
    }

    /**
     * Familles, options et modes des quêtes terminées : familles de départ + données (give) − retirées (remove).
     * Un retrait l'emporte toujours, quel que soit l'ordre des quêtes : la quête qui fait apparaître une famille
     * (grimoire des étoiles : signes du zodiaque) est souvent plus loin dans la liste que celle qui la retire une
     * fois la collection faite (constellations) ; de même pour la clé avinée et la paperasse.
     */
    public void recomputeUnlocks(SaveData save) {
        save.unlockedFamilies.clear();
        save.unlockedOptions.clear();
        save.unlockedModes.clear();
        for (int f : START_FAMILIES) save.unlockedFamilies.add(f);
        java.util.Set<Integer> removed = new java.util.HashSet<>();
        for (QuestDef q : quests) {
            if (!save.completedQuests.contains(q.id)) continue;
            for (int f : q.give) save.unlockedFamilies.add(f);
            removed.addAll(q.remove);
            for (String o : q.options) save.unlockedOptions.add(o);
            for (String m : q.modes) save.unlockedModes.add(m);
        }
        save.unlockedFamilies.removeAll(removed);
    }

    /** Quête qui débloque l'option (ou le mode) donnée, pour l'afficher sur une option grisée. */
    public QuestDef unlockingQuest(String optionOrMode) {
        for (QuestDef q : quests) {
            for (String o : q.options) if (o.equals(optionOrMode)) return q;
            for (String m : q.modes) if (m.equals(optionOrMode)) return q;
        }
        return null;
    }
}
