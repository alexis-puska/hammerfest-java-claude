package hammerfest.quest;

import hammerfest.data.GameData;
import hammerfest.data.QuestDef;
import hammerfest.game.GameResult;
import hammerfest.game.Profile;
import hammerfest.save.SaveData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Utilise les vraies données converties (assets/data, dossier de travail des tests). */
class QuestBookTest {

    static GameData data;
    static QuestBook book;

    @BeforeAll
    static void load() throws IOException {
        data = new GameData(read("data/items/scoreItems.json"), read("data/items/specialItems.json"),
                read("data/quests.json"), read("data/lang/fr.json"), set -> {
                    try {
                        return read("data/levels/" + set + ".json");
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
        book = new QuestBook(data.quests);
    }

    static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    @Test
    void dataLoaded() {
        assertEquals(76, data.quests.size());
        assertEquals(51, data.families.size());
        assertEquals("Les constellations", data.lang.get(data.quest(0).nameKey));
        assertEquals(1000, data.items.get(1003).familyId);
        assertNull(data.items.get(1000).value, "valeur \"--\" inconnue");
    }

    @Test
    void levelSetsLoadLazily() {
        hammerfest.data.LevelSet adventure = data.levels("adventure");
        assertEquals(107, adventure.levels.size());
        assertEquals(3, adventure.level(5).playerX);
        assertSame(adventure, data.levels("adventure"), "mis en cache");
    }

    @Test
    void newPlayerHasStartFamiliesOnly() {
        SaveData s = new SaveData();
        assertTrue(book.update(s).isEmpty());
        assertTrue(s.unlockedFamilies.contains(0));
        assertTrue(s.unlockedFamilies.contains(1000));
        assertFalse(s.unlockedFamilies.contains(100));
        assertEquals(QuestBook.Status.LOCKED, book.status(data.quest(0), s));
    }

    /** Vérification du PLAN : 12 signes du zodiaque injectés → quête 0 validée → multi-bombe (famille 100). */
    @Test
    void zodiacQuestUnlocksMultiBomb() {
        SaveData s = new SaveData();
        s.addToFridge(40, 1);
        book.update(s);
        assertEquals(QuestBook.Status.IN_PROGRESS, book.status(data.quest(0), s));

        for (int item = 41; item <= 51; item++) s.addToFridge(item, 1);
        List<QuestDef> done = book.update(s);
        assertTrue(done.stream().anyMatch(q -> q.id == 0));
        assertEquals(QuestBook.Status.COMPLETED, book.status(data.quest(0), s));
        assertTrue(s.unlockedFamilies.contains(100), "multi-bombe 2");
        assertTrue(s.unlockedFamilies.contains(6), "potions du zodiaque");
        assertFalse(s.unlockedFamilies.contains(5), "signes retirés");
    }

    @Test
    void optionsAndModesUnlocked() {
        QuestDef mirror = book.unlockingQuest("mirror");
        assertEquals(63, mirror.id);
        assertEquals(73, book.unlockingQuest("timeattack").id);

        SaveData s = new SaveData();
        for (QuestDef.Require r : mirror.require) s.addToFridge(r.item, r.qty);
        book.update(s);
        assertTrue(s.unlockedOptions.contains("mirror"));
    }

    @Test
    void gameResultFeedsFridgeStatsAndQuests() {
        SaveData s = new SaveData();
        Map<Integer, Integer> items = new HashMap<>();
        for (int item = 40; item <= 51; item++) items.put(item, 1);
        items.put(1003, 3);
        List<QuestDef> done = Profile.applyResult(s, book, new GameResult("adventure", 5000, 12, items));

        assertEquals(1, s.stats.gamesPlayed);
        assertEquals(5000, s.stats.bestScore);
        assertEquals(12, s.stats.maxLevel);
        assertEquals(15, s.stats.itemsCollected);
        assertEquals(3, s.fridgeCount(1003));
        assertTrue(done.stream().anyMatch(q -> q.id == 0));

        Profile.applyResult(s, book, new GameResult("adventure", 100, 5, Map.of()));
        assertEquals(5000, s.stats.bestScore, "meilleur score conservé");
        assertEquals(12, s.stats.maxLevel, "niveau max conservé");
        assertEquals(2, s.stats.gamesPlayed);
    }

    /** Toutes les quêtes : une famille retirée (zodiaque, Coeur 2, clé avinée, paperasse) ne revient pas. */
    @Test
    void removedFamiliesStayRemovedWhateverTheQuestOrder() {
        hammerfest.save.SaveData save = new hammerfest.save.SaveData();
        for (hammerfest.data.QuestDef q : data.quests) save.completedQuests.add(q.id);
        book.recomputeUnlocks(save);
        for (int f : new int[]{5, 8, 1021, 1022}) {
            assertFalse(save.unlockedFamilies.contains(f), "famille " + f + " retirée par une quête");
        }
        assertTrue(save.unlockedFamilies.contains(1), "famille donnée (quête 10) et jamais retirée");
    }
}
