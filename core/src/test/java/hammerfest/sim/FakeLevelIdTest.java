package hammerfest.sim;

import hammerfest.data.LevelSet;
import hammerfest.data.PortalData;
import hammerfest.json.Jsons;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** GameMode.fakeLevelId : numéro affiché 0, 1, 2… dans une contrée ; niveau réel de nouveau dans le puits. */
class FakeLevelIdTest {

    static <T> T read(String path, Class<T> type) {
        try {
            return Jsons.read(new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void dimensionLevelsAreCountedFromZero() {
        Game g = new Game(read("data/levels/adventure.json", LevelSet.class), FrameSource.DEFAULT, 5L, Set.of(), Set.of());
        g.setDimensions(new String[]{"adventure", "deepnight"}, n -> read("data/levels/" + n + ".json", LevelSet.class),
                read("data/portals.json", PortalData.class));
        g.start(54, 1);
        assertNull(g.fakeLevelId, "puits : niveau réel");
        g.fl_clear = true;
        g.switchDimension(1, 141, 0); // les enfers
        assertEquals(0, g.fakeLevelId, "entrée dans la contrée : 0");
        g.nextLevel();
        g.nextLevel();
        assertEquals(143, g.levelId);
        assertEquals(2, g.fakeLevelId, "deux niveaux plus bas : 2 (et non 143)");
        g.fl_clear = true;
        g.switchDimension(0, 55, -1);
        assertNull(g.fakeLevelId, "retour dans le puits : niveau réel");
        assertEquals(55, g.levelId);
    }
}
