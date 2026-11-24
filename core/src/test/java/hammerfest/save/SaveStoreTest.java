package hammerfest.save;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SaveStoreTest {

    @Test
    void roundTrip() {
        SaveData s = new SaveData();
        s.playerName = "Igor";
        s.addToFridge(40, 2);
        s.addToFridge(1003, 5);
        s.completedQuests.add(0);
        s.unlockedFamilies.add(100);
        s.unlockedOptions.add("mirror");
        s.unlockedModes.add("timeattack");
        s.stats.bestScore = 123456;
        s.stats.maxLevel = 42;
        s.stats.gamesPlayed = 3;
        s.lastMode = "timeattack";
        s.lastOptions.add("mirror");

        String json = SaveStore.format(s);
        assertTrue(json.contains("\"playerName\" : \"Igor\""), json);
        assertFalse(json.contains("hasPlayerName"), "méthodes non sérialisées");

        SaveData r = SaveStore.parse(json);
        assertEquals("Igor", r.playerName);
        assertEquals(s.playerId, r.playerId);
        assertEquals(2, r.fridgeCount(40));
        assertEquals(5, r.fridgeCount(1003));
        assertEquals(7, r.stats.itemsCollected);
        assertTrue(r.completedQuests.contains(0));
        assertTrue(r.unlockedFamilies.contains(100));
        assertTrue(r.unlockedOptions.contains("mirror"));
        assertTrue(r.unlockedModes.contains("timeattack"));
        assertEquals(123456, r.stats.bestScore);
        assertEquals(42, r.stats.maxLevel);
        assertEquals(3, r.stats.gamesPlayed);
        assertEquals("timeattack", r.lastMode);
        assertTrue(r.lastOptions.contains("mirror"));
    }

    @Test
    void toleratesMissingAndUnknownFields() {
        SaveData r = SaveStore.parse("{ \"fridge\": { \"40\": 1, \"41\": 0 }, \"stats\": null, \"futureField\": 3 }");
        assertEquals("", r.playerName);
        assertFalse(r.hasPlayerName());
        assertFalse(r.playerId.isEmpty(), "un playerId est généré s'il manque");
        assertEquals(1, r.fridgeCount(40));
        assertFalse(r.fridge.containsKey(41), "quantités nulles retirées");
        assertNotNull(r.stats);
    }

    @Test
    void readsFirstVersionFile() {
        // Format écrit par la toute première version (sérialisation manuelle), identique en structure.
        String v1 = "{\"version\":1,\"playerName\":\"  Sandy  \",\"playerId\":\"abc\",\"lang\":\"fr\","
                + "\"fridge\":{\"1003\":2},\"completedQuests\":[0],\"unlockedFamilies\":[0,7],"
                + "\"unlockedOptions\":[],\"unlockedModes\":[],"
                + "\"stats\":{\"bestScore\":10,\"maxLevel\":2,\"gamesPlayed\":1,\"itemsCollected\":2},"
                + "\"lastMode\":\"adventure\",\"lastOptions\":[]}";
        SaveData r = SaveStore.parse(v1);
        assertEquals("Sandy", r.playerName);
        assertEquals("abc", r.playerId);
        assertEquals(2, r.fridgeCount(1003));
        assertEquals(10, r.stats.bestScore);
    }

    @Test
    void playerIdIsUnique() {
        assertNotEquals(new SaveData().playerId, new SaveData().playerId);
    }
}
