package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.json.Jsons;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScriptEngineTest {

    static Game game(String script) {
        LevelData l = GameTest.level(15, 3, 14);
        l.script = script;
        LevelSet set = new LevelSet("test");
        set.levels.add(l);
        set.levels.add(GameTest.level(15, 3, 14));
        Game g = new Game(set, FrameSource.DEFAULT, 4L, Set.of(), Set.of());
        g.start(0, 1);
        return g;
    }

    @Test
    void timerTriggerAddsBadsAndRepeats() {
        Game g = game("<t_timer t=\"10\" repeat=\"2\"><e_bad i=\"1\" x=\"12\" y=\"14\"/></t_timer>");
        GameTest.run(g, 9, 0);
        assertEquals(0, g.countList(Const.BAD));
        GameTest.run(g, 2, 0);
        assertEquals(1, g.countList(Const.BAD), "déclenché à t=10");
        assertFalse(g.checkLevelClear(), "un ennemi scripté bloque la sortie");
        GameTest.run(g, 12, 0);
        assertEquals(2, g.countList(Const.BAD), "répété une seconde fois");
    }

    @Test
    void positionTriggerShowsMessageAndEditsTiles() {
        Game g = game("<t_pos x=\"3\" y=\"14\" d=\"2\"><e_msg id=\"100\"/><e_rem x1=\"5\" y1=\"15\" x2=\"7\" y2=\"15\"/></t_pos>");
        int v = g.world.version;
        GameTest.run(g, 5, 0);
        assertEquals("static.100", g.messageKey);
        assertEquals(0, g.world.getCase(6, 15), "dalles retirées");
        assertTrue(g.world.version > v, "le rendu doit être rafraîchi");
    }

    @Test
    void explosionTriggerAndScriptIds() {
        Game g = game("<do><e_mc n=\"menuDoor\" x=\"10\" y=\"10\" sid=\"5\"/></do>"
                + "<exp x=\"3\" y=\"14\"><e_kill sid=\"5\"/><e_score i=\"8\" x=\"3\" y=\"10\"/></exp>");
        GameTest.run(g, 2, 0);
        assertEquals(1, g.script.decors.size(), "décor posé");
        Player p = g.players().get(0);
        g.onExplode(p.x, p.y, 60);
        GameTest.run(g, 1, 0);
        assertEquals(0, g.script.decors.size(), "décor retiré par e_kill");
        assertEquals(1, g.countList(Const.ITEM), "objet donné par l'explosion");
    }

    @Test
    void keyRequiredTriggersOnlyWithTheKey() {
        Game g = game("<do key=\"3\"><e_score i=\"8\" x=\"3\" y=\"10\"/></do>");
        GameTest.run(g, 3, 0);
        assertEquals(0, g.countList(Const.ITEM), "clé 3 absente");
        g.pickUp(5003);
        GameTest.run(g, 1, 0);
        assertEquals(1, g.countList(Const.ITEM), "clé ramassée : déclenché");
    }

    @Test
    void villageScriptHidesTilesAndPlacesDecor() throws Exception {
        LevelSet set = Jsons.read(new String(Files.readAllBytes(Path.of("data/levels/adventure.json")),
                StandardCharsets.UTF_8), LevelSet.class);
        Game g = new Game(set, FrameSource.DEFAULT, 1L, Set.of(), Set.of());
        g.start(0, 1);
        GameTest.run(g, 3, 0);
        assertTrue(g.world.hideTiles, "village : dalles masquées (e_hide)");
        assertTrue(g.script.decors.stream().anyMatch(d -> d.linkage.equals("menu")), "décor du village");
        GameTest.run(g, 20, 0);
        assertEquals("static.100", g.messageKey, "message d'accueil à t=15");
    }

    @Test
    void allAdventureScriptsRunWithoutErrors() throws Exception {
        LevelSet set = Jsons.read(new String(Files.readAllBytes(Path.of("data/levels/adventure.json")),
                StandardCharsets.UTF_8), LevelSet.class);
        Game g = new Game(set, FrameSource.DEFAULT, 2L, Set.of(), Set.of());
        g.start(0, 1);
        for (int id = 1; id < set.levels.size(); id++) {
            g.players().get(0).shield(Const.SECOND * 100);
            g.forcedGoto(id);
            GameTest.run(g, Const.SECOND * 3, 0);
            if (g.fl_gameOver) break;
        }
        assertTrue(g.maxLevel > 50);
    }
}
