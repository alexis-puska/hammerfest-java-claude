package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GameTest {

    /** Niveau avec un sol plein à la rangée {@code floorY} (aucun sol si floorY < 0). */
    static LevelData level(int floorY, int playerX, int playerY) {
        LevelData l = new LevelData();
        int[][] g = new int[LevelData.WIDTH][LevelData.HEIGHT];
        if (floorY >= 0) for (int x = 0; x < LevelData.WIDTH; x++) g[x][floorY] = 1;
        l.setGrid(g);
        l.playerX = playerX;
        l.playerY = playerY;
        return l;
    }

    static Game game(LevelData... levels) {
        LevelSet set = new LevelSet("test");
        for (int i = 0; i < levels.length; i++) {
            levels[i].id = i;
            set.levels.add(levels[i]);
        }
        Game g = new Game(set, FrameSource.DEFAULT, 42L, Set.of(), Set.of());
        g.start(0, 1);
        return g;
    }

    static void run(Game g, int ticks, int bits) {
        for (int i = 0; i < ticks; i++) g.tick(new InputFrame(g.cycle, bits));
    }

    /** FxManager.attachExit / detachExit : flèche « niveau suivant » selon GameMode et Adventure. */
    @Test
    void exitArrowFollowsOriginalRules() {
        LevelData withBad = level(15, 5, 14);
        withBad.badList.add(new LevelData.Bad(Const.BAD_CERISE, 15, 14));
        Game g = game(level(15, 5, 14), level(15, 5, 14), withBad);
        run(g, 2, 0);
        assertFalse(g.exitArrow, "pas de flèche au niveau 0 (Adventure.startLevel)");
        g.goTo(1);
        run(g, 2, 0);
        assertTrue(g.exitArrow, "niveau sans ennemi (GameMode.startLevel)");
        g.goTo(2);
        run(g, 2, 0);
        assertFalse(g.exitArrow, "ennemis présents : flèche retirée au changement de niveau");
        g.destroyList(Const.BAD);
        run(g, 2, 0);
        assertTrue(g.exitArrow, "niveau vidé (GameMode.onLevelClear)");
    }

    /** ViewManager : défilement de 40 cycles (0,04 par cycle jusqu'à π/2), partie verrouillée, puis onLevelReady. */
    @Test
    void levelChangeScrollsThenStartsNextLevel() {
        LevelData withBad = level(15, 5, 14);
        withBad.badList.add(new LevelData.Bad(Const.BAD_CERISE, 15, 14));
        withBad.script = "<do><e_hide tiles=\"1\" borders=\"0\"/><e_mc n=\"hammer_level2_front\" xr=\"0\" yr=\"0\"/></do>";
        Game g = game(level(15, 5, 14), withBad);
        g.scrollTransitions = true;
        run(g, 2, 0);
        g.nextLevel(); // sortie par le bas de l'écran
        assertTrue(g.scrolling());
        assertEquals(1, g.levelId);
        // GameMechanics.onDataReady : script lu une 1re fois avant le défilement (dalles masquées, décor posé)
        assertTrue(g.world.hideTiles, "e_hide du nouveau niveau appliqué pendant le défilement");
        assertEquals(1, g.script.decors.size(), "décor du nouveau niveau posé pendant le défilement");
        assertTrue(g.getList(Const.BAD).isEmpty(), "ennemis insérés à la fin du défilement");
        Player p = g.players().get(0);
        double y = p.y;
        g.goTo(0);
        assertEquals(1, g.levelId, "pas de changement de niveau pendant le verrou");
        run(g, 39, InputFrame.LEFT);
        assertTrue(g.scrolling());
        assertEquals(y, p.y, "joueur figé pendant le défilement");
        run(g, 1, 0);
        assertFalse(g.scrolling());
        assertEquals(1, g.getList(Const.BAD).size(), "niveau lancé (startLevel)");
    }

    /** Téléporteur, porte, parapluie, dimension : le niveau s'affiche aussitôt, sans défilement. */
    @Test
    void warpsShowLevelImmediately() {
        Game g = game(level(15, 5, 14), level(15, 5, 14), level(15, 5, 14));
        g.scrollTransitions = true;
        run(g, 2, 0);
        g.forcedGoto(2);
        assertFalse(g.scrolling(), "parapluie, porte, e_goto");
        assertEquals(2, g.levelId);
        g.goTo(1);
        assertFalse(g.scrolling(), "portail, changement de dimension");
        assertEquals(1, g.levelId);
    }

    @Test
    void playerFallsAndLandsOnGround() {
        Game g = game(level(15, 5, 5));
        Player p = g.players().get(0);
        run(g, 64, 0);
        assertTrue(p.fl_stable, "posé au sol");
        assertEquals(14, p.cy, "debout sur la rangée 15");
        assertEquals(0, p.dy);
    }

    @Test
    void walksRightAtPlayerSpeed() {
        Game g = game(level(15, 2, 14));
        Player p = g.players().get(0);
        run(g, 32, 0);
        double x0 = p.x;
        run(g, 20, InputFrame.RIGHT);
        assertEquals(1, p.dir);
        assertEquals(20 * Const.PLAYER_SPEED, p.x - x0, 1e-6, "4,3 px par cycle");
        assertEquals(Anim.PLAYER_WALK.id, p.animId);
        run(g, 5, 0);
        assertEquals(0, p.dx, "freinage au sol");
    }

    @Test
    void jumpsAndLandsBack() {
        Game g = game(level(15, 5, 14));
        Player p = g.players().get(0);
        run(g, 32, 0);
        double groundY = p.y;
        run(g, 1, InputFrame.UP);
        assertFalse(p.fl_stable);
        assertTrue(p.dy < 0, "monte");
        double top = p.y;
        for (int i = 0; i < 60 && !p.fl_stable; i++) {
            run(g, 1, 0);
            top = Math.min(top, p.y);
        }
        assertTrue(p.fl_stable, "retombe");
        assertEquals(groundY, p.y, 1e-6);
        assertTrue(groundY - top > 3 * Const.CASE_HEIGHT, "saut d'au moins 3 cases : " + (groundY - top));
    }

    @Test
    void wallsBlockHorizontalMovement() {
        LevelData l = level(15, 5, 14);
        int[][] grid = l.grid();
        for (int y = 10; y < 15; y++) grid[8][y] = 1; // mur vertical en x=8
        l.setGrid(grid);
        Game g = game(l);
        Player p = g.players().get(0);
        run(g, 32, 0);
        run(g, 40, InputFrame.RIGHT);
        assertTrue(p.x < 8 * Const.CASE_WIDTH, "bloqué avant le mur : x=" + p.x);
    }

    @Test
    void fallingThroughBottomGoesToNextLevel() {
        Game g = game(level(-1, 5, 2), level(15, 5, 5));
        Player p = g.players().get(0);
        run(g, 200, 0);
        assertEquals(1, g.levelId, "puits : niveau suivant");
        assertEquals(1, g.maxLevel);
        assertTrue(p.fl_stable, "posé dans le nouveau niveau");
        assertFalse(g.fl_gameOver);
    }

    @Test
    void endOfSetEndsTheGame() {
        Game g = game(level(-1, 5, 2));
        run(g, 200, 0);
        assertTrue(g.fl_gameOver);
    }

    @Test
    void simulationIsDeterministic() {
        int[] script = {0, 0, InputFrame.RIGHT, InputFrame.RIGHT | InputFrame.UP, InputFrame.LEFT, 0, InputFrame.UP};
        Game a = game(level(15, 3, 5)), b = game(level(15, 3, 5));
        for (int i = 0; i < 400; i++) {
            int bits = script[(i / 13) % script.length];
            a.tick(new InputFrame(i, bits));
            b.tick(new InputFrame(i, bits));
        }
        Player pa = a.players().get(0), pb = b.players().get(0);
        assertEquals(pa.x, pb.x);
        assertEquals(pa.y, pb.y);
        assertEquals(pa.animId, pb.animId);
    }

    @Test
    void worldGetCaseRules() {
        LevelData l = level(15, 0, 0);
        int[][] grid = l.grid();
        grid[3][0] = 1;
        grid[4][14] = 1; // sol sur sol : mur
        l.setGrid(grid);
        World w = new World(l);
        assertEquals(Const.WALL, w.getCase(3, 0), "rangée du haut = mur");
        assertEquals(Const.WALL, w.getCase(3, -2), "au-dessus d'une case haute pleine");
        assertEquals(0, w.getCase(5, -2));
        assertEquals(Const.OUT_WALL, w.getCase(-1, 5));
        assertEquals(Const.OUT_WALL, w.getCase(5, 25));
        assertEquals(Const.GROUND, w.getCase(4, 14));
        assertEquals(Const.WALL, w.getCase(4, 15));
        assertTrue(w.checkFlag(5, 15, Const.IA_TILE));
        assertTrue(w.checkFlag(5, 14, Const.IA_TILE_TOP));
    }

    @Test
    void scriptsAndHurryUpChangeTheMusic() {
        hammerfest.data.LevelSet set = new hammerfest.data.LevelSet("test");
        LevelData l = level(15, 3, 14);
        l.badList.add(new LevelData.Bad(Const.BAD_CERISE, 15, 14));
        l.script = "<t_timer t=\"1\"><e_music id=\"1\"/></t_timer>";
        set.levels.add(l);
        Game g = new Game(set, FrameSource.DEFAULT, 1L, java.util.Set.of(), java.util.Set.of());
        g.start(0, 1);
        assertEquals(0, g.currentTrack);
        run(g, 3, 0);
        assertEquals(1, g.currentTrack, "e_music : musique du boss");
        g.playMusic(0);
        g.onHurryUp();
        assertEquals(2, g.currentTrack, "hurry up");
        g.resetHurry();
        assertEquals(0, g.currentTrack, "retour à la musique du jeu");
    }

    @Test
    void deathsAreRecordedOnTheMap() {
        hammerfest.data.LevelSet set = new hammerfest.data.LevelSet("test");
        set.levels.add(level(15, 3, 14));
        Game g = new Game(set, FrameSource.DEFAULT, 1L, java.util.Set.of(102), java.util.Set.of());
        g.start(0, 1);
        hammerfest.sim.entity.Player p = g.players().get(0);
        p.resurrect();
        assertEquals(1, g.mapEvents.size());
        assertEquals(Const.EVENT_DEATH, g.mapEvents.get(0).eid);
        assertEquals(0, g.mapEvents.get(0).lid);
    }

    @Test
    void tutorialHasInfiniteLivesAndNoHurryUp() {
        hammerfest.data.LevelSet set = DimensionTest.load("tutorial");
        Game g = new Game(set, FrameSource.DEFAULT, 1L, java.util.Set.of(), java.util.Set.of());
        g.setTutorial();
        g.start(0, 1);
        assertEquals(99999, g.players().get(0).lives);
        assertTrue(g.drainEvents().stream().anyMatch(e -> e.name.equals("levelpop:static.16|0")));
        g.players().get(0).shield(Const.SECOND * 1000);
        run(g, (int) Const.SECOND * 70, 0);
        assertEquals(0, g.huState, "pas de hurry up");
        assertEquals(0, g.darkness, 1e-9);
    }

    @Test
    void disguisesFollowUnlockedFamilies() {
        hammerfest.data.LevelSet set = new hammerfest.data.LevelSet("test");
        set.levels.add(level(15, 3, 14));
        // afro (109) et Mario (112) débloqués seulement
        Game g = new Game(set, FrameSource.DEFAULT, 1L, java.util.Set.of(109, 112), java.util.Set.of());
        g.start(0, 1);
        hammerfest.sim.entity.Player p = g.players().get(0);
        assertEquals(Const.HEAD_NORMAL, p.head);
        g.disguise();
        assertEquals(Const.HEAD_AFRO, p.head);
        g.disguise();
        assertEquals(Const.HEAD_MARIO, p.head, "Cerbère et Piou non débloqués : sautés");
        g.disguise();
        assertEquals(Const.HEAD_NORMAL, p.head, "après la dernière tête : retour à la tête par défaut");

        Game rush = new Game(set, FrameSource.DEFAULT, 1L, java.util.Set.of(109), java.util.Set.of());
        rush.setBossRush();
        rush.start(0, 1);
        rush.disguise();
        assertEquals(Const.HEAD_NORMAL, rush.players().get(0).head, "pas de déguisement en Tubercourse");
    }
}
