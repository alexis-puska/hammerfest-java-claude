package hammerfest.sim;

import hammerfest.data.LevelSet;
import hammerfest.json.Jsons;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Début de l'aventure avec les vrais niveaux (assets/data/levels/adventure.json). */
class AdventureStartTest {

    static Game adventure() throws Exception {
        LevelSet set = Jsons.read(new String(Files.readAllBytes(Path.of("data/levels/adventure.json")),
                StandardCharsets.UTF_8), LevelSet.class);
        Game g = new Game(set, FrameSource.DEFAULT, 1L, Set.of(), Set.of());
        g.start(0, 1);
        return g;
    }

    @Test
    void playerStandsInTheVillage() throws Exception {
        Game g = adventure();
        Player p = g.players().get(0);
        for (int i = 0; i < 64; i++) {
            g.tick(new InputFrame(i, 0));
        }
        assertEquals(0, g.levelId, "reste au village : y=" + p.y + " cy=" + p.cy + " stable=" + p.fl_stable);
        assertTrue(p.fl_stable);

        g.tick(new InputFrame(g.cycle, InputFrame.BOMB));
        assertEquals(1, g.countList(Const.BOMB),
                "bombe posée (arme=" + p.currentWeapon + ", entering=" + p.fl_entering + ")");
    }
}
