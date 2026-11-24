package hammerfest.data;

import com.badlogic.gdx.files.FileHandle;
import com.fasterxml.jackson.core.type.TypeReference;
import hammerfest.json.Jsons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Données statiques du jeu, chargées depuis assets/data (JSON via Jackson). */
public class GameData {

    public final List<ItemFamily> families = new ArrayList<>();
    public final Map<Integer, ItemDef> items = new HashMap<>();
    public final Map<Integer, ItemFamily> familiesById = new HashMap<>();
    public final List<QuestDef> quests;
    public final Lang lang;
    /** Lecture paresseuse des sets de niveaux : nom du set → contenu JSON. */
    private final Function<String, String> levelSource;
    private final Map<String, LevelSet> levelSets = new HashMap<>();

    public GameData(String scoreItemsJson, String specialItemsJson, String questsJson, String langJson,
                    Function<String, String> levelSource) {
        addFamilies(Jsons.read(specialItemsJson, new TypeReference<List<ItemFamily>>() { }));
        addFamilies(Jsons.read(scoreItemsJson, new TypeReference<List<ItemFamily>>() { }));
        List<QuestDef> list = new ArrayList<>(Jsons.read(questsJson, new TypeReference<List<QuestDef>>() { }));
        list.sort(Comparator.comparingInt(q -> q.id));
        quests = Collections.unmodifiableList(list);
        lang = new Lang(Jsons.read(langJson, new TypeReference<Map<String, String>>() { }));
        this.levelSource = levelSource;
    }

    /** Liens entre portails (portals.json), null si non chargés. */
    public PortalData portals;

    /** Dimensions de l'Aventure dans l'ordre des ids (Adventure.initWorld). */
    public static final String[] ADVENTURE_DIMENSIONS = {"adventure", "deepnight", "hiko", "ayame", "hk"};

    /** Charge depuis un dossier data/ (Gdx.files.internal("data")). */
    public static GameData load(FileHandle dataDir, String langCode) {
        GameData d = create(dataDir, langCode);
        FileHandle portals = dataDir.child("portals.json");
        if (portals.exists()) d.portals = Jsons.read(portals.readString("UTF-8"), PortalData.class);
        return d;
    }

    private static GameData create(FileHandle dataDir, String langCode) {
        return new GameData(
                dataDir.child("items/scoreItems.json").readString("UTF-8"),
                dataDir.child("items/specialItems.json").readString("UTF-8"),
                dataDir.child("quests.json").readString("UTF-8"),
                dataDir.child("lang/" + langCode + ".json").readString("UTF-8"),
                set -> dataDir.child("levels/" + set + ".json").readString("UTF-8"));
    }

    /** Set de niveaux (adventure, tutorial, timeAttack…), chargé à la première demande. */
    public LevelSet levels(String setName) {
        return levelSets.computeIfAbsent(setName, n -> Jsons.read(levelSource.apply(n), LevelSet.class));
    }

    public QuestDef quest(int id) {
        for (QuestDef q : quests) if (q.id == id) return q;
        return null;
    }

    public String itemName(int id) {
        return lang.get("item." + id);
    }

    private void addFamilies(List<ItemFamily> fams) {
        for (ItemFamily f : fams) {
            families.add(f);
            familiesById.put(f.id, f);
            for (ItemDef i : f.items) {
                i.familyId = f.id;
                items.put(i.id, i);
            }
        }
    }
}
