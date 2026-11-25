package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.SpecialItem;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpecialManagerTest {

    static Game game(int levels, boolean withBad) {
        LevelSet set = new LevelSet("test");
        for (int i = 0; i < levels; i++) {
            LevelData l = GameTest.level(15, 3, 14);
            if (withBad) l.badList.add(new LevelData.Bad(Const.BAD_CERISE, 15, 14));
            set.levels.add(l);
        }
        Game g = new Game(set, FrameSource.DEFAULT, 5L, Set.of(), Set.of());
        g.start(0, 1);
        GameTest.run(g, 10, 0);
        return g;
    }

    static void pick(Game g, int id, int subId) {
        Player p = g.players().get(0);
        SpecialItem it = SpecialItem.attach(g, p.x, p.y, id, subId);
        it.execute(p);
    }

    /** Bâton tonnerre (objet 85) : le marteau passe du côté où regarde le joueur et se retourne (Hammer.endUpdate). */
    @Test
    void thunderHammerFollowsPlayerDirection() {
        Game g = game(1, true);
        Player p = g.players().get(0);
        p.shield(Const.SECOND * 100);
        pick(g, 85, -1);
        hammerfest.sim.entity.shoot.Hammer h = null;
        for (hammerfest.sim.entity.Entity e : g.getList(Const.SHOOT)) {
            if (e instanceof hammerfest.sim.entity.shoot.Hammer) h = (hammerfest.sim.entity.shoot.Hammer) e;
        }
        assertNotNull(h);
        GameTest.run(g, 3, InputFrame.RIGHT);
        assertTrue(h.x > p.x && h.xscale > 0, "à droite, tourné vers la droite");
        GameTest.run(g, 3, InputFrame.LEFT);
        assertTrue(h.x < p.x, "passé à gauche du joueur");
        assertTrue(h.xscale < 0, "retourné vers la gauche");
    }

    @Test
    void shieldProtectsFromBads() {
        Game g = game(1, true);
        Player p = g.players().get(0);
        pick(g, 1, -1);
        assertTrue(p.fl_shield);
        p.killHit(1);
        assertFalse(p.fl_kill, "bouclier : invulnérable");
        GameTest.run(g, Const.SECOND * 10 + 2, 0);
        assertFalse(p.fl_shield, "bouclier de 10 s terminé");
    }

    @Test
    void specialItemsShowTheirBackground() {
        Game g = game(1, true);
        g.players().get(0).shield(Const.SECOND * 100);
        pick(g, 28, -1); // coupe en argent : fond étoilé 9 s
        GameTest.run(g, 1, 0);
        assertNotNull(g.specialBg);
        assertEquals(Const.BG_STAR, g.specialBg[0]);
        GameTest.run(g, (int) (Const.SECOND * 9) + 2, 0);
        assertNull(g.specialBg, "fond retiré après 9 s");
        pick(g, 42, -1); // signe du zodiaque : constellation n° 2
        GameTest.run(g, 1, 0);
        assertArrayEquals(new int[]{Const.BG_CONSTEL, 2}, g.specialBg);
    }

    @Test
    void extendLettersAppearEveryTenSeconds() {
        LevelSet set = new LevelSet("test");
        LevelData l = GameTest.level(15, 3, 14);
        l.badList.add(new LevelData.Bad(Const.BAD_CERISE, 15, 14));
        l.specialSlots.add(new LevelData.Slot(10, 14));
        set.levels.add(l);
        Game g = new Game(set, FrameSource.DEFAULT, 5L, Set.of(), Set.of());
        g.start(0, 1);
        g.players().get(0).shield(Const.SECOND * 100);
        assertEquals(1, g.extendList.size(), "sans combo : une seule lettre possible");
        int letters = 0;
        for (int i = 0; i < Const.SECOND * 10 + 5; i++) {
            GameTest.run(g, 1, 0);
            for (hammerfest.sim.entity.Entity e : g.getList(Const.ITEM)) {
                if (e instanceof SpecialItem && ((SpecialItem) e).id == 0) letters++;
            }
            if (letters > 0) break;
        }
        assertTrue(letters > 0, "une lettre EXTEND apparaît après 10 s");
    }

    @Test
    void fireRainAndHammerHitBads() {
        Game g = game(1, true);
        Player p = g.players().get(0);
        p.shield(Const.SECOND * 100);
        pick(g, 84, -1);
        assertTrue(g.countList(Const.SHOOT) > 0, "pluie de feu");
        GameTest.run(g, Const.SECOND * 3, 0);
        assertTrue(g.countList(Const.SHOOT) > 0, "la pluie continue");

        Game g2 = game(1, true);
        Player p2 = g2.players().get(0);
        p2.shield(Const.SECOND * 100);
        Bad b = (Bad) g2.getList(Const.BAD).get(0);
        b.moveTo(p2.x + 20, p2.y);
        pick(g2, 85, -1);
        GameTest.run(g2, 3, 0);
        assertTrue(b.fl_kill || g2.countList(Const.BAD) == 0, "le marteau assomme l'ennemi proche");
        assertTrue(p2.specialMan.active(85));
    }

    @Test
    void extendWordGivesALifeAndClearsBads() {
        Game g = game(1, true);
        Player p = g.players().get(0);
        int lives = p.lives;
        for (int letter = 0; letter < Const.EXTENDS; letter++) pick(g, 0, letter);
        // +1 pour le mot complet, +1 car le bonus parfait (150 000) franchit le palier de 100 000 points
        assertEquals(lives + 2, p.lives, "CRYSTAL complet : une vie (et une vie de palier)");
        GameTest.run(g, 1, 0);
        assertEquals(0, g.countList(Const.BAD), "ennemis détruits");
        assertTrue(p.score >= 150000, "lettres dans l'ordre : bonus parfait");
    }

    @Test
    void bombBonusesAndTemporaryCurseExpire() {
        Game g = game(1, false);
        Player p = g.players().get(0);
        pick(g, 4, -1);
        assertEquals(p.initialMaxBombs + 1, p.maxBombs);
        pick(g, 22, -1);
        assertEquals(0.6, p.speedFactor, 1e-9, "malédiction lente");
        GameTest.run(g, Const.SECOND * 40 + 2, 0);
        assertEquals(1.0, p.speedFactor, 1e-9, "fin après 40 s");
        assertTrue(p.specialMan.active(4), "bonus permanent conservé");
    }

    @Test
    void levelConversionGivesPerfectBonus() {
        Game g = game(1, true);
        Player p = g.players().get(0);
        pick(g, 81, -1);
        GameTest.run(g, 1, 0);
        assertEquals(0, g.countList(Const.BAD), "ennemis retirés");
        assertEquals(20, g.perfectItemCpt, "un diamant par case de sol");
        GameTest.run(g, 60, 0);
        int before = p.score;
        for (hammerfest.sim.entity.Entity e : new java.util.ArrayList<>(g.getList(Const.PERFECT_ITEM))) {
            ((hammerfest.sim.entity.Item) e).execute(p);
        }
        assertTrue(g.perfectItemCpt <= 0);
        assertTrue(p.score - before >= 50000, "bonus parfait");
    }

    @Test
    void badsToCrystalsAndWarpZone() {
        Game g = game(5, true);
        pick(g, 32, -1);
        GameTest.run(g, 1, 0);
        assertEquals(0, g.countList(Const.BAD_CLEAR));
        assertTrue(g.countList(Const.ITEM) >= 1, "cristal à la place de l'ennemi");
        pick(g, 12, -1);
        assertEquals(2, g.levelId, "warp +2");
        assertTrue(g.players().get(0).fl_shield, "protégé à l'arrivée");
    }

    @Test
    void supaItemsSweepTheBads() {
        // Tonneaux : tuent les ennemis qu'ils écrasent (le joueur est protégé par un bouclier)
        Game g = game(1, true);
        Player p = g.players().get(0);
        p.shield(Const.SECOND * 100);
        pick(g, 9, -1);
        assertEquals(1, g.countList(Const.SUPA));
        for (int i = 0; i < Const.SECOND * 60 && g.countList(Const.BAD_CLEAR) > 0; i++) {
            Bad b = (Bad) g.getOne(Const.BAD_CLEAR);
            hammerfest.sim.entity.Supa s = (hammerfest.sim.entity.Supa) g.getOne(Const.SUPA);
            if (b != null && !b.fl_kill) s.moveTo(b.x, b.y - 30); // force la rencontre
            GameTest.run(g, 1, 0);
        }
        assertTrue(g.countList(Const.BAD_CLEAR) == 0 || ((Bad) g.getOne(Const.BAD_CLEAR)).fl_kill, "écrasé");

        // Météore de glace : gèle
        Game g2 = game(1, true);
        pick(g2, 24, -1);
        Bad b2 = (Bad) g2.getOne(Const.BAD);
        hammerfest.sim.entity.Supa m = (hammerfest.sim.entity.Supa) g2.getOne(Const.SUPA);
        m.moveTo(b2.x, b2.y);
        GameTest.run(g2, 1, 0);
        assertTrue(b2.fl_freeze, "gelé par le météore");

        // Tous les super-objets tournent sans erreur
        for (int id : new int[]{3, 8, 19, 23, 25, 38, 71}) {
            Game g3 = game(1, true);
            pick(g3, id, -1);
            GameTest.run(g3, Const.SECOND * 10, 0);
        }
    }

    @Test
    void stunAndFreezeAllBads() {
        Game g = game(1, true);
        pick(g, 6, -1);
        Bad b = (Bad) g.getOne(Const.BAD);
        assertTrue(b.fl_freeze);
        assertEquals(Const.FREEZE_DURATION * 2, b.freezeTotal, 1e-9);
    }

    @Test
    void chourouHairShowsNecklaceUntilInterrupted() {
        Game g = game(1, true);
        Player p = g.players().get(0);
        assertFalse(p.fl_chourou, "collier caché par défaut");
        pick(g, 99, -1);
        assertTrue(p.fl_chourou, "Poils de Chourou ramassés : collier visible");
        p.specialMan.interrupt(99);
        assertFalse(p.fl_chourou);
    }
}
