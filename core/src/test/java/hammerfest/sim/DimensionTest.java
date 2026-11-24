package hammerfest.sim;

import hammerfest.data.LevelSet;
import hammerfest.data.PortalData;
import hammerfest.json.Jsons;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DimensionTest {

    static final String[] DIMS = {"adventure", "deepnight", "hiko", "ayame", "hk"};
    static final Map<String, LevelSet> CACHE = new HashMap<>();

    static LevelSet load(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            try {
                return Jsons.read(new String(Files.readAllBytes(Path.of("data/levels/" + n + ".json")), StandardCharsets.UTF_8), LevelSet.class);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    static PortalData portals() throws Exception {
        return Jsons.read(new String(Files.readAllBytes(Path.of("data/portals.json")), StandardCharsets.UTF_8), PortalData.class);
    }

    static Game game(int level) throws Exception {
        Game g = new Game(load("adventure"), FrameSource.DEFAULT, 3L, Set.of(), Set.of());
        g.setDimensions(DIMS, DimensionTest::load, portals());
        g.start(level, 1);
        return g;
    }

    @Test
    void portalLinksResolveTags() throws Exception {
        PortalLinks links = new PortalLinks(portals());
        assertTrue(links.size() > 80);
        // "d_forest2(1)" <-> "d_forest2+1(1)" : deux sens
        int[] a = links.endpoint("d_forest2(1)");
        int[] b = links.endpoint("d_forest2+1(1)");
        assertNotNull(a);
        assertEquals(a[1] + 1, b[1], "décalage +1 sur le niveau");
        assertNotNull(links.get(a[0], a[1], 1));
        assertNotNull(links.get(b[0], b[1], 1), "lien retour d'un twoway");
    }

    @Test
    void enteringAPortalSwitchesDimension() throws Exception {
        PortalLinks links = new PortalLinks(portals());
        LevelSet adv = load("adventure");
        int level = -1, pid = -1;
        for (int i = 0; i < adv.levels.size() && level < 0; i++) {
            World w = new World(adv.level(i));
            for (int p = 0; p < w.portalList.size(); p++) {
                PortalLinks.Link l = links.get(0, i, p);
                if (l != null && l.toDid > 0) {
                    level = i;
                    pid = p;
                    break;
                }
            }
        }
        assertTrue(level >= 0, "un niveau de l'aventure mène à une autre dimension");

        Game g = game(level);
        Player pl = g.players().get(0);
        pl.shield(Const.SECOND * 1000);
        GameTest.run(g, 5, 0);
        int[] portal = g.world.portalList.get(pid);

        // Niveau non terminé : refusé
        g.destroyList(Const.BAD);
        GameTest.run(g, 1, 0);
        g.fl_clear = false;
        pl.moveToCase(portal[0], portal[1]);
        pl.onPortal(pid);
        assertEquals(0, g.currentDim);

        // Niveau terminé : passage
        g.fl_clear = true;
        assertTrue(g.usePortal(pid, pl));
        GameTest.run(g, 1, 0);
        PortalLinks.Link link = links.get(0, level, pid);
        assertEquals(link.toDid, g.currentDim, "nouvelle dimension");
        assertEquals(link.toLid, g.levelId);
        assertFalse(g.isMainWorld());
        assertTrue(pl.visible, "joueur replacé et visible");
    }

    @Test
    void darknessGrowsWithDepthAndIsReducedByTheBulb() throws Exception {
        Game shallow = game(5);
        GameTest.run(shallow, 20, 0);
        assertEquals(0, shallow.darkness, "pas d'obscurité avant le niveau 16");

        Game deep = game(40);
        deep.players().get(0).shield(Const.SECOND * 1000);
        GameTest.run(deep, 30, 0);
        assertEquals(40, deep.darkness, 1e-9, "opacité = numéro du niveau");

        Player p = deep.players().get(0);
        hammerfest.sim.entity.SpecialItem.attach(deep, p.x, p.y, 26, -1).execute(p);
        GameTest.run(deep, 30, 0);
        assertEquals(20, deep.darkness, 1e-9, "ampoule : moitié moins sombre");
    }

    @Test
    void allOtherDimensionsRunWithoutErrors() throws Exception {
        for (int did = 1; did < DIMS.length; did++) {
            LevelSet set = load(DIMS[did]);
            Game g = new Game(set, FrameSource.DEFAULT, did, Set.of(), Set.of());
            g.start(0, 1);
            for (int id = 0; id < set.levels.size(); id++) {
                g.players().get(0).shield(Const.SECOND * 100);
                g.forcedGoto(id);
                GameTest.run(g, Const.SECOND * 2, 0);
                if (g.fl_gameOver) break;
            }
            assertEquals(set.levels.size() - 1, g.levelId, DIMS[did] + " parcouru jusqu'au dernier niveau");
        }
    }

    @Test
    void screenFlipIsTemporary() throws Exception {
        Game g = game(1);
        Player p = g.players().get(0);
        p.shield(Const.SECOND * 1000);
        hammerfest.sim.entity.SpecialItem.attach(g, p.x, p.y, 30, -1).execute(p);
        assertTrue(g.flipX);
        GameTest.run(g, Const.SECOND * 30 + 2, 0);
        assertFalse(g.flipX, "écran remis à l'endroit après 30 s");
    }

    /** Régression : portail déclenché par le script du niveau 11 (e_portal, sans entité) → plus de NullPointerException. */
    @Test
    void scriptedPortalOfLevel11() throws Exception {
        Game g = game(11);
        g.fl_clear = true;
        assertTrue(g.usePortal(0, null), "le portail du niveau 11 mène à une dimension");
        GameTest.run(g, 2, 0);
        assertNotEquals(0, g.currentDim, "entré dans la dimension");
        assertEquals(1, g.mapTravels.size(), "sortie notée sur la carte");
    }

    @Test
    void torchesLightTheDarkness() throws Exception {
        for (int level : new int[]{54, 86}) {
            Game g = game(level);
            GameTest.run(g, 64, 0);
            System.out.println("TORCH adventure " + level + " darkness=" + g.darkness + " holes=" + g.extraHoles.size()
                    + (g.extraHoles.isEmpty() ? "" : " premier=" + java.util.Arrays.toString(g.extraHoles.get(0))));
            assertFalse(g.extraHoles.isEmpty(), "halo des torches du niveau " + level);
        }
    }

    @Test
    void unusedVortexIsRemovedWhenLeavingTheLevel() throws Exception {
        // niveau 11 : vortex 0 ouvert mais pas emprunté, on descend ; niveau 13 : vortex 0 à nouveau
        Game g = game(11);
        g.openPortal(2, 6, 0);
        assertEquals(1, g.floatingPortals.size());
        g.goTo(12);
        assertTrue(g.floatingPortals.isEmpty(), "vortex du niveau 11 retiré en le quittant");
        g.goTo(13);
        g.openPortal(17, 14, 0);
        assertEquals(1, g.floatingPortals.size(), "vortex du niveau 13 ouvert");
        assertEquals(hammerfest.sim.entity.Entity.xCtr(17), g.floatingPortals.get(0)[0], 0.01);
    }

    /** Niveau 15 : la clé ouvre la porte, puis la case (18, 8) mène à la dimension (x="18²" dans les données). */
    @Test
    void level15DoorLeadsToItsDimension() throws Exception {
        Game g = new Game(load("adventure"), FrameSource.DEFAULT, 3L, Set.of(5000), Set.of());
        g.setDimensions(DIMS, DimensionTest::load, portals());
        g.start(15, 1);
        Player p = g.players().get(0);
        for (hammerfest.sim.entity.Entity e : new java.util.ArrayList<>(g.getList(Const.BAD))) e.destroy();
        GameTest.run(g, 40, 0);
        p.moveTo(hammerfest.sim.entity.Entity.xCtr(1), hammerfest.sim.entity.Entity.yCtr(4)); // serrure
        GameTest.run(g, 10, 0);
        GameTest.run(g, 40, 0); // la porte s'ouvre ; niveau vidé : déclencheur de fin
        p.moveTo(hammerfest.sim.entity.Entity.xCtr(16), hammerfest.sim.entity.Entity.yCtr(8)); // devant la porte
        GameTest.run(g, 10, 0);
        for (int i = 0; i < 64 && g.isMainWorld(); i++) GameTest.run(g, 1, InputFrame.RIGHT); // entre dans (18, 8)
        assertFalse(g.isMainWorld(), "entrée dans la dimension du niveau 15");
    }

    /** Parapluies et touche 1 : jamais au-delà de la fin d'une dimension (niveaux vides entre les dimensions). */
    @Test
    void warpsStopAtTheEndOfADimension() {
        Game g = new Game(load("deepnight"), FrameSource.DEFAULT, 3L, Set.of(), Set.of());
        g.start(10, 1);
        assertTrue(g.isEmptyLevel(11), "séparateur entre deux dimensions");
        assertEquals(10, g.warpArrival(1), "dernier niveau de la dimension : téléportation impossible");
        assertEquals(10, g.warpArrival(3));
        g.debugSkipLevel();
        assertEquals(10, g.levelId, "la touche 1 ne sort pas de la dimension");

        Game h = new Game(load("deepnight"), FrameSource.DEFAULT, 3L, Set.of(), Set.of());
        h.start(14, 1);
        assertEquals(16, h.warpArrival(2), "saut normal à l'intérieur d'une dimension");
        assertEquals(22, h.warpArrival(9), "arrêt avant le niveau vide 23");
    }
}
