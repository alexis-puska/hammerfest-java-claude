package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.PlayerBombs;
import hammerfest.sim.entity.PlayerShots;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WeaponTest {

    /** Sol en rangée 15 ; champ d'arme -{@code field} en (6, 14) ; ennemi en (badX, 14) si badX >= 0. */
    static Game game(int field, int badX) {
        LevelData l = GameTest.level(15, 3, 14);
        int[][] g = l.grid();
        if (field > 0) g[6][14] = -field;
        l.setGrid(g);
        if (badX >= 0) l.badList.add(new LevelData.Bad(Const.BAD_CERISE, badX, 14));
        LevelSet set = new LevelSet("test");
        set.levels.add(l);
        Game game = new Game(set, FrameSource.DEFAULT, 9L, Set.of(), Set.of());
        game.start(0, 1);
        GameTest.run(game, 20, 0);
        return game;
    }

    @Test
    void weaponFieldChangesTheBomb() {
        Game g = game(Const.WEAPON_B_BLACK, -1);
        Player p = g.players().get(0);
        for (int i = 0; i < 30 && p.currentWeapon != Const.WEAPON_B_BLACK; i++) GameTest.run(g, 1, InputFrame.RIGHT);
        assertEquals(Const.WEAPON_B_BLACK, p.currentWeapon, "champ noir traversé");
        GameTest.run(g, 3, 0);
        GameTest.run(g, 1, InputFrame.BOMB);
        assertInstanceOf(PlayerBombs.Black.class, g.getOne(Const.BOMB));
    }

    @Test
    void blackBombKillsBads() {
        Game g = game(0, 12);
        Bad b = (Bad) g.getOne(Const.BAD);
        PlayerBombs.Black bomb = (PlayerBombs.Black) PlayerBombs.create(g, Const.WEAPON_B_BLACK, b.x - 10, b.y);
        bomb.lifeTimer = 1;
        GameTest.run(g, 2, 0);
        assertTrue(b.fl_kill, "tué par la bombe noire");
    }

    @Test
    void redBombPropelsThePlayer() {
        Game g = game(0, -1);
        Player p = g.players().get(0);
        Entity bomb = PlayerBombs.create(g, Const.WEAPON_B_RED, p.x, p.y);
        ((hammerfest.sim.entity.Bomb) bomb).lifeTimer = 1;
        GameTest.run(g, 2, 0);
        assertTrue(p.dy < -20, "saut à la bombe rouge : dy=" + p.dy);
    }

    @Test
    void fireShotBurnsBads() {
        Game g = game(0, 12);
        Player p = g.players().get(0);
        Bad b = (Bad) g.getOne(Const.BAD);
        p.changeWeapon(Const.WEAPON_S_FIRE);
        p.dir = 1;
        Entity shot = p.attack();
        assertInstanceOf(PlayerShots.FireBall.class, shot);
        assertTrue(p.coolDown > 0, "temps de recharge");
        for (int i = 0; i < 60 && !b.fl_destroy; i++) GameTest.run(g, 1, 0);
        assertTrue(b.fl_destroy, "brûlé");
        assertTrue(g.countList(Const.ITEM) >= 1, "récompense lâchée sur place");
    }

    @Test
    void fireWeaponFromSpecialItemExpires() {
        Game g = game(0, -1);
        Player p = g.players().get(0);
        hammerfest.sim.entity.SpecialItem.attach(g, p.x, p.y, 29, -1).execute(p);
        assertEquals(Const.WEAPON_S_FIRE, p.currentWeapon);
        GameTest.run(g, (int) Const.WEAPON_DURATION + 2, 0);
        assertEquals(Const.WEAPON_B_CLASSIC, p.currentWeapon, "retour aux bombes après 30 s");
    }
}
