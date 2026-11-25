package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BadTest {

    /** Sol en rangée 15, sans sol sur les colonnes 0-1 (sortie), un ennemi de type {@code badId} en (badX, 14). */
    static Game game(int badId, int badX, int playerX) {
        LevelData l = GameTest.level(15, playerX, 14);
        int[][] g = l.grid();
        g[0][15] = 0;
        g[1][15] = 0;
        l.setGrid(g);
        l.badList.add(new LevelData.Bad(badId, badX, 14));
        LevelSet set = new LevelSet("test");
        set.levels.add(l);
        set.levels.add(GameTest.level(15, 5, 5));
        Game game = new Game(set, FrameSource.DEFAULT, 3L, Set.of(), Set.of());
        game.start(0, 1);
        return game;
    }

    static Bad bad(Game g) {
        return (Bad) g.getOne(Const.BAD);
    }

    @Test
    void badsAreInsertedAndBlockTheExit() {
        Game g = game(Const.BAD_CERISE, 15, 5);
        assertEquals(1, g.countList(Const.BAD_CLEAR));
        assertFalse(g.checkLevelClear(), "niveau non terminé tant qu'il reste un ennemi");
        Bad b = bad(g);
        assertEquals(Entity2.xCtr(15), b.x, 1e-9);
        GameTest.run(g, 64, 0);
        assertTrue(b.fl_stable, "posé au sol");
        assertNotEquals(Entity2.xCtr(15), b.x, "il marche");
    }

    @Test
    void touchingAHealthyBadKillsThePlayer() {
        Game g = game(Const.BAD_CERISE, 9, 5);
        Player p = g.players().get(0);
        ((hammerfest.sim.entity.bad.Walker) bad(g)).dir = -1; // marche vers le joueur
        for (int i = 0; i < 200 && !p.fl_kill; i++) GameTest.run(g, 1, 0);
        assertTrue(p.fl_kill, "le joueur meurt au contact");
    }

    @Test
    void classicBombFreezesTheBad() {
        Game g = game(Const.BAD_CERISE, 15, 5);
        // Pas de trou : un ennemi gelé qui glisse dans le vide meurt (comportement d'origine)
        g.world.forceCase(0, 15, Const.GROUND);
        g.world.forceCase(1, 15, Const.GROUND);
        GameTest.run(g, 40, 0);
        Bad b = bad(g);
        hammerfest.sim.entity.ClassicBomb bomb = hammerfest.sim.entity.ClassicBomb.attach(g, b.x - 20, b.y);
        bomb.lifeTimer = 1;
        GameTest.run(g, 2, 0);
        assertTrue(bomb.fl_explode);
        assertTrue(b.fl_freeze, "gelé par la bombe de glace");
        assertFalse(b.isHealthy());
        assertTrue(Math.abs(b.dx) > 0 || b.dy < 0, "repoussé par l'onde de choc");
        GameTest.run(g, (int) Const.FREEZE_DURATION + 5, 0);
        assertFalse(b.fl_freeze, "dégel après 5 s (freezeTimer=" + b.freezeTimer + " destroy=" + b.fl_destroy
                + " x=" + b.x + " y=" + b.y + ")");
        assertEquals(1, b.anger, "plus énervé après le dégel");
    }

    @Test
    void killedBadFallsOutAndLevelIsCleared() {
        Game g = game(Const.BAD_CERISE, 15, 5);
        Bad b = bad(g);
        b.killHit(5);
        for (int i = 0; i < 400 && g.countList(Const.BAD) > 0; i++) GameTest.run(g, 1, 0);
        assertEquals(0, g.countList(Const.BAD), "détruit sous la ligne du bas");
        assertTrue(g.checkLevelClear());
        assertEquals(1, g.badsKilled);
    }

    @Test
    void spearDoesNotBlockTheExitAndKillsBads() {
        Game g = game(Const.BAD_SPEAR, 10, 5);
        assertEquals(0, g.countList(Const.BAD_CLEAR), "la pique ne compte pas");
        assertTrue(g.checkLevelClear());
        hammerfest.sim.entity.bad.Spear spear = (hammerfest.sim.entity.bad.Spear) bad(g);
        spear.killHit(5);
        spear.freeze(100);
        assertFalse(spear.fl_kill, "indestructible");
        Bad cerise = hammerfest.sim.entity.bad.Fruits.attach(g, Const.BAD_CERISE, 9 * 20, 13 * 20);
        spear.hit(cerise);
        assertTrue(cerise.fl_kill, "un ennemi qui touche la pique meurt");
    }

    @Test
    void bigApricotSplitsInTwo() {
        Game g = game(Const.BAD_ABRICOT, 15, 5);
        Bad big = bad(g);
        big.killHit(5);
        for (int i = 0; i < 300 && !big.fl_destroy; i++) GameTest.run(g, 1, 0);
        assertTrue(big.fl_destroy);
        assertEquals(2, g.countList(Const.BAD_CLEAR), "deux petits abricots");
        assertEquals(0.75, ((Bad) g.getOne(Const.BAD)).scaleFactor, 1e-9);
    }

    @Test
    void walkingBombExplodesOneSecondAfterBeingHit() {
        Game g = game(Const.BAD_BOMBE, 6, 5);
        Player p = g.players().get(0);
        Bad bomb = bad(g);
        GameTest.run(g, 2, 0);
        bomb.killHit(0);
        assertFalse(bomb.fl_destroy);
        assertFalse(bomb.isHealthy(), "en surchauffe");
        GameTest.run(g, Const.SECOND + 2, 0);
        assertTrue(bomb.fl_destroy, "explosée");
        assertTrue(p.fl_kill, "le joueur à proximité est tué");
    }

    @Test
    void litchiLosesItsShellWhenFrozen() {
        Game g = game(Const.BAD_LITCHI, 15, 5);
        Bad litchi = bad(g);
        litchi.killHit(3);
        assertFalse(litchi.fl_kill, "carapace : seulement assommé");
        assertTrue(litchi.fl_knock);
        litchi.freeze(100);
        GameTest.run(g, 1, 0);
        assertTrue(litchi.fl_destroy, "carapace perdue");
        Bad weak = bad(g);
        assertInstanceOf(hammerfest.sim.entity.bad.Fruits.LitchiWeak.class, weak);
        assertEquals(1, weak.anger, "le litchi faible est énervé");
    }

    @Test
    void kiwiMineTriggersNearThePlayer() {
        Game g = game(Const.BAD_KIWI, 15, 3);
        Player p = g.players().get(0);
        GameTest.run(g, 20, 0);
        hammerfest.sim.entity.Mine m = hammerfest.sim.entity.Mine.attach(g, p.x + 30, p.y);
        GameTest.run(g, 3, 0);
        assertTrue(m.fl_trigger, "déclenchée à l'approche");
        GameTest.run(g, Const.SECOND * 2, 0);
        assertTrue(m.fl_explode || m.fl_destroy, "explosée");
        assertTrue(p.fl_kill, "joueur pris dans l'explosion");
    }

    @Test
    void hurryUpAngersBadsThenSummonsTheFireBall() {
        Game g = game(Const.BAD_CERISE, 15, 5);
        g.world.forceCase(0, 15, Const.GROUND);
        g.world.forceCase(1, 15, Const.GROUND);
        Player p = g.players().get(0);
        p.lives = 3;
        // Joueur protégé tant qu'on attend (sinon la cerise le tue)
        p.shield(Const.SECOND * 200);
        GameTest.run(g, (int) Game.HU_STEPS[0] + 1, 0);
        assertEquals(1, bad(g).anger, "hurry up : ennemis énervés à 35 s");
        GameTest.run(g, (int) Game.HU_STEPS[1] + 1, 0);
        assertEquals(1, g.countList(Const.HU_BAD), "boule de feu à 60 s");
        hammerfest.sim.entity.bad.FireBall f = (hammerfest.sim.entity.bad.FireBall) g.getOne(Const.HU_BAD);
        assertFalse(f.fl_kill, "indestructible");
        f.killHit(5);
        f.freeze(100);
        assertFalse(f.fl_kill);
        p.fl_shield = false;
        int lives = p.lives;
        for (int i = 0; i < Const.SECOND * 30 && p.lives == lives; i++) GameTest.run(g, 1, 0);
        assertEquals(lives - 1, p.lives, "rattrapé par la boule de feu");
        GameTest.run(g, 2, 0);
        assertEquals(0, g.countList(Const.HU_BAD), "remise à zéro à la résurrection");
        assertEquals(0, g.huState);
    }

    @Test
    void wallWalkersFollowSurfacesAndSawIsIndestructible() {
        // Crawler posé contre le sol : il longe une surface
        Game g = game(14, 10, 3);
        g.players().get(0).shield(Const.SECOND * 1000);
        Bad crawler = bad(g);
        assertInstanceOf(hammerfest.sim.entity.bad.WallWalker.Crawler.class, crawler);
        double x0 = crawler.x, y0 = crawler.y;
        GameTest.run(g, Const.SECOND * 2, 0);
        assertTrue(crawler.x != x0 || crawler.y != y0, "il se déplace");
        // Blob : déformé (ondulation, étirement en rampant) et excentré vers sa surface
        assertTrue(crawler.hasSubTransform(), "sous-clip déformé");
        assertTrue(Math.abs(crawler.subXscale - 90) > 1 || Math.abs(crawler.subYscale - 90) > 1, "ondulation / étirement");
        int[] cp = ((hammerfest.sim.entity.bad.WallWalker) crawler).cp;
        if (cp != null && cp[0] != 0) assertEquals(cp[0] * 8, crawler.subX, 2.5, "excentré vers la paroi");

        // Scie : ne compte pas, indestructible
        Game g2 = game(16, 10, 3);
        assertEquals(0, g2.countList(Const.BAD_CLEAR), "la scie ne bloque pas la sortie");
        Bad saw = bad(g2);
        saw.killHit(5);
        saw.freeze(100);
        assertFalse(saw.fl_kill, "indestructible");
        assertFalse(saw.isHealthy(), "étourdie");
        GameTest.run(g2, Const.SECOND * 3 + 2, 0);
        assertTrue(saw.isHealthy(), "repart après 3 s");
        // orientation vers la surface longée (rattrapée en quelques cycles après un coin)
        int aligned = 0;
        for (int k = 0; k < 40; k++) {
            GameTest.run(g2, 1, 0);
            int[] scp = ((hammerfest.sim.entity.bad.WallWalker) saw).cp;
            double want = Math.toDegrees(Math.atan2(scp[1], scp[0])) - 90;
            double diff = ((saw.subRotation - want) % 360 + 540) % 360 - 180;
            if (Math.abs(diff) < 10) aligned++;
        }
        assertTrue(aligned >= 25, "la scie s'oriente vers sa surface (" + aligned + "/40)");
        // la lame tourne sur elle-même (10° par cycle une fois lancée)
        double r0 = saw.innerRotation;
        GameTest.run(g2, 3, 0);
        double turned = ((saw.innerRotation - r0) % 360 + 360) % 360;
        assertEquals(30, turned, 0.01, "lame à pleine vitesse");
    }

    @Test
    void framboiseTeleportsAndReforms() {
        Game g = game(20, 15, 3);
        g.players().get(0).shield(Const.SECOND * 1000);
        GameTest.run(g, 20, 0);
        hammerfest.sim.entity.bad.Fruits.Framboise f = (hammerfest.sim.entity.bad.Fruits.Framboise) bad(g);
        f.onShoot();
        assertTrue(f.fl_phased, "éclatée en fragments");
        assertEquals(6, g.countList(Const.SHOOT), "6 fragments");
        for (int i = 0; i < Const.SECOND * 5 && f.fl_phased; i++) GameTest.run(g, 1, 0);
        assertFalse(f.fl_phased, "reconstituée");
        assertEquals(f.tx, f.x, 1e-6);
    }

    @Test
    void killingBadsInOrderDropsThePerfectItem() {
        for (boolean inOrder : new boolean[]{true, false}) {
            LevelData l = GameTest.level(15, 3, 14);
            l.badList.add(new LevelData.Bad(Const.BAD_CERISE, 10, 14));
            l.badList.add(new LevelData.Bad(Const.BAD_CERISE, 15, 14));
            LevelSet set = new LevelSet("test");
            set.levels.add(l);
            set.levels.add(GameTest.level(15, 5, 5));
            Game g = new Game(set, FrameSource.DEFAULT, 3L, Set.of(), Set.of());
            g.start(0, 1);
            GameTest.run(g, 20, 0);
            java.util.List<hammerfest.sim.entity.Entity> bads = new java.util.ArrayList<>(g.getList(Const.BAD));
            if (!inOrder) java.util.Collections.reverse(bads);
            for (hammerfest.sim.entity.Entity b : bads) {
                ((Bad) b).killHit(1);
                GameTest.run(g, 1, 0);
            }
            GameTest.run(g, Const.SECOND * 4, 0);
            assertTrue(g.fl_clear);
            assertEquals(inOrder ? 1 : 0, g.countList(Const.SUPA), inOrder ? "super-objet" : "pas de super-objet");
            assertEquals(inOrder ? Anim.PLAYER_STOP_V.id : Anim.PLAYER_STOP.id, g.players().get(0).baseStopAnim.id, "pose de victoire");
        }
    }

    @Test
    void visitedLevelsAreNotRepopulated() {
        LevelSet set = new LevelSet("test");
        for (int i = 0; i < 2; i++) {
            LevelData l = GameTest.level(15, 3, 14);
            l.badList.add(new LevelData.Bad(Const.BAD_CERISE, 12, 14));
            set.levels.add(l);
        }
        Game g = new Game(set, FrameSource.DEFAULT, 3L, Set.of(), Set.of());
        g.start(0, 1);
        assertFalse(g.isVisited());
        assertEquals(1, g.countList(Const.BAD_CLEAR));
        g.forcedGoto(1);
        GameTest.run(g, 2, 0);
        g.forcedGoto(0);
        GameTest.run(g, 2, 0);
        assertTrue(g.isVisited(), "niveau 0 déjà parcouru");
        assertEquals(0, g.countList(Const.BAD_CLEAR), "ennemis du niveau non réinsérés");
        assertTrue(g.fl_clear);
    }

    @Test
    void allBadTypesRunWithoutErrors() {
        for (int id = 0; id <= 20; id++) {
            Game g = game(id, 12, 3);
            GameTest.run(g, 32 * 20, 0); // 20 s de jeu
            assertNotNull(g.world);
        }
    }

    /** Accès aux conversions de coordonnées. */
    static final class Entity2 extends hammerfest.sim.entity.Entity {
        @Override
        public double width() {
            return 0;
        }

        @Override
        public double height() {
            return 0;
        }
    }

    @Test
    void onlyTheStrawberryHoldingTheBallShowsIt() {
        Game g = game(Const.BAD_FRAISE, 15, 5);
        hammerfest.sim.entity.bad.Fruits.attach(g, Const.BAD_FRAISE, Entity2.xCtr(20), Entity2.yCtr(14));
        hammerfest.sim.entity.bad.Fruits.attach(g, Const.BAD_FRAISE, Entity2.xCtr(10), Entity2.yCtr(14));
        GameTest.run(g, 2, 0);
        int holders = 0, shown = 0;
        for (var e : g.getList(Const.BAD)) {
            if (((hammerfest.sim.entity.bad.Fruits.Fraise) e).fl_ball) holders++;
        }
        hammerfest.net.RenderFrame f = hammerfest.net.RenderFrame.capture(g, java.util.List.of(), false);
        for (int i = 0; i < f.extras.length; i++) if ((f.extras[i] & hammerfest.net.RenderFrame.EXTRA_BALL) != 0) shown++;
        assertEquals(1, holders, "une seule balle pour trois fraises");
        assertEquals(1, shown, "seule la fraise qui la tient la montre");
    }

    @Test
    void nightmareKeepsEnemiesAngryFromTheStart() {
        LevelData l = GameTest.level(15, 5, 14);
        l.badList.add(new LevelData.Bad(Const.BAD_CERISE, 15, 14));
        LevelSet set = new LevelSet("test");
        set.levels.add(l);
        set.levels.add(GameTest.level(15, 5, 5));
        Game g = new Game(set, FrameSource.DEFAULT, 3L, Set.of(), Set.of("nightmare"));
        g.start(0, 1);
        GameTest.run(g, 2, 0);
        Bad b = bad(g);
        assertEquals(1, b.anger, "Cauchemar : énervé dès le début (GameMode.main, Enervement minimum)");
        b.calmDown();
        GameTest.run(g, 1, 0);
        assertEquals(1, b.anger, "jamais calme en Cauchemar");
        assertEquals(1.3, g.players().get(0).speedFactor, 1e-9, "Igor plus rapide");

        Game normal = game(Const.BAD_CERISE, 15, 5);
        GameTest.run(normal, 2, 0);
        assertEquals(0, bad(normal).anger, "sans Cauchemar : calme au début");
    }
}
