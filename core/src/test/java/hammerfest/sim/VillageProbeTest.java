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

/** Le joueur doit rester au village tant qu'il ne bouge pas, quelles que soient la graine et les animations. */
class VillageProbeTest {

    @Test
    void playerStaysInVillageWithAppLikeSetup() throws Exception {
        LevelSet set = Jsons.read(new String(Files.readAllBytes(Path.of("data/levels/adventure.json")),
                StandardCharsets.UTF_8), LevelSet.class);
        FrameSource realFrames = (linkage, animId) -> 28; // animations longues comme dans l'atlas
        for (long seed = 0; seed < 20; seed++) {
            Game g = new Game(set, realFrames, seed, Set.of(0, 7, 18, 1000, 1028), Set.of());
            g.start(0, 1);
            Player p = g.players().get(0);
            for (int i = 0; i < 96; i++) {
                g.tick(new InputFrame(i, 0));
                assertEquals(0, g.levelId, "seed " + seed + " tick " + i + " : y=" + p.y + " x=" + p.x
                        + " stable=" + p.fl_stable + " world(1,20)=" + g.world.getCase(1, 20));
            }
        }
    }
}
