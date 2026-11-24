package hammerfest.sim;

import hammerfest.data.LevelSet;
import hammerfest.sim.entity.boss.Bat;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BossTest {

    static Game batLevel() {
        LevelSet set = new LevelSet("adventure");
        for (int i = 0; i <= Const.TUBERCULOZ_LEVEL; i++) {
            var l = GameTest.level(24, 3, 22);
            l.id = i;
            set.levels.add(l);
        }
        Game g = new Game(set, FrameSource.DEFAULT, 8L, Set.of(), Set.of());
        g.start(Const.BAT_LEVEL, 1);
        return g;
    }

    @Test
    void batNeedsShieldBreakThenHitAndHasThreeLives() {
        Game g = batLevel();
        Bat bat = (Bat) g.getOne(Const.BOSS);
        assertNotNull(bat, "chauve-souris au niveau 100");
        assertFalse(g.checkLevelClear(), "sortie bloquée tant que le boss vit");
        g.players().get(0).shield(Const.SECOND * 1000);
        GameTest.run(g, Const.SECOND * 3, 0); // intro terminée : bouclier
        assertTrue(bat.fl_shield);

        for (int life = 3; life > 0; life--) {
            bat.freeze(1); // bombe : brise le bouclier
            assertFalse(bat.fl_shield);
            bat.freeze(1); // seconde bombe : perte d'une vie
            assertEquals(life - 1, bat.lives);
            GameTest.run(g, Const.SECOND * 4, 0); // fin d'immunité
        }
        assertTrue(bat.fl_death, "trois vies perdues");
        for (int i = 0; i < Const.SECOND * 60 && !bat.fl_destroy; i++) GameTest.run(g, 1, 0);
        assertTrue(bat.fl_destroy, "s'envole puis plonge");
        assertTrue(g.fl_clear, "niveau terminé");
    }

    static Game tuberculozLevel() {
        Game g = batLevel();
        g.forcedGoto(Const.TUBERCULOZ_LEVEL);
        return g;
    }

    @Test
    void tuberculozLosesLivesToBombsAndOpensTheExit() {
        Game g = tuberculozLevel();
        var tub = (hammerfest.sim.entity.boss.Tuberculoz) g.getOne(Const.BOSS);
        assertNotNull(tub, "Tuberculoz au niveau 101");
        assertFalse(g.checkLevelClear());
        hammerfest.sim.entity.Player p = g.players().get(0);
        p.shield(Const.SECOND * 10000);
        GameTest.run(g, Const.SECOND * 2, 0);
        g.onExplode(tub.x, tub.y - 28, 60);
        assertEquals(hammerfest.sim.entity.boss.Tuberculoz.LIVES - 1, tub.lives, "bombe : -1 vie");
        g.onExplode(tub.x, tub.y - 28, 60);
        assertEquals(hammerfest.sim.entity.boss.Tuberculoz.LIVES - 1, tub.lives, "immunité de 3 s");
        // Combat accéléré : jusqu'à la mort
        for (int i = 0; i < 200 && !tub.fl_death; i++) {
            GameTest.run(g, Const.SECOND * 3 + 1, 0);
            p.shield(Const.SECOND * 10000);
            g.onExplode(tub.x, tub.y - 28, 60);
        }
        assertTrue(tub.fl_death, "vaincu");
        for (int i = 0; i < Const.SECOND * 30 && !tub.fl_defeated; i++) GameTest.run(g, 1, 0);
        assertTrue(tub.fl_defeated);
        assertTrue(g.fl_clear, "sortie ouverte");
        assertTrue(g.script.decors.stream().anyMatch(d -> d.linkage.equals("door")), "porte de sortie");
    }

    @Test
    void tuberculozFightRunsWithoutErrors() {
        Game g = tuberculozLevel();
        g.players().get(0).shield(Const.SECOND * 10000);
        GameTest.run(g, Const.SECOND * 60, 0); // embrasement, tornade, charge, bombes → oranges
        assertNotNull(g.getOne(Const.BOSS));
    }

    @Test
    void angryBatSummonsOrbitingFireBalls() {
        Game g = batLevel();
        Bat bat = (Bat) g.getOne(Const.BOSS);
        g.players().get(0).shield(Const.SECOND * 1000);
        GameTest.run(g, Const.SECOND * 3, 0);
        bat.freeze(1);
        assertTrue(bat.fl_anger);
        assertEquals(6, g.countList(Const.SHOOT), "six boules de feu");
        GameTest.run(g, Const.SECOND * 16, 0);
        assertEquals(0, g.countList(Const.SHOOT), "calmé après 15 s");
    }
}
