package hammerfest.sim;

import com.fasterxml.jackson.core.type.TypeReference;
import hammerfest.data.ItemFamily;
import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.json.Jsons;
import hammerfest.sim.entity.Item;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.ScoreItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ItemTest {

    static List<ItemFamily> families = new ArrayList<>();

    @BeforeAll
    static void load() throws Exception {
        for (String f : new String[]{"data/items/specialItems.json", "data/items/scoreItems.json"}) {
            families.addAll(Jsons.read(new String(Files.readAllBytes(Path.of(f)), StandardCharsets.UTF_8),
                    new TypeReference<List<ItemFamily>>() { }));
        }
    }

    static Game game(boolean withBad) {
        LevelData l = GameTest.level(15, 3, 14);
        l.specialSlots.add(new LevelData.Slot(10, 10));
        l.scoreSlots.add(new LevelData.Slot(12, 10));
        if (withBad) l.badList.add(new LevelData.Bad(Const.BAD_CERISE, 17, 14));
        LevelSet set = new LevelSet("test");
        set.levels.add(l);
        Game g = new Game(set, FrameSource.DEFAULT, 11L, Set.of(0, 1000, 1001), Set.of());
        g.setItemFamilies(families);
        g.start(0, 1);
        return g;
    }

    static List<hammerfest.sim.entity.Entity> items(Game g) {
        return g.getList(Const.ITEM);
    }

    @Test
    void levelItemsAppearAfterTheirTimers() {
        // Sans ennemi (sinon il peut tuer le joueur et finir la partie) : les minuteries tournent aussi
        Game g = game(false);
        GameTest.run(g, (int) Const.SPECIAL_ITEM_TIMER - 1, 0);
        assertEquals(0, g.countList(Const.SPECIAL_ITEM));
        GameTest.run(g, 2, 0);
        assertEquals(1, g.countList(Const.SPECIAL_ITEM), "objet spécial à 8 s");
        GameTest.run(g, (int) (Const.SCORE_ITEM_TIMER - Const.SPECIAL_ITEM_TIMER), 0);
        assertTrue(items(g).stream().anyMatch(e -> e instanceof ScoreItem), "objet à score à 12 s");
    }

    @Test
    void itemsVanishAfterTheirLifeTime() {
        Game g = game(false);
        GameTest.run(g, (int) Const.SPECIAL_ITEM_TIMER + 1, 0);
        Item it = (Item) g.getOne(Const.SPECIAL_ITEM);
        GameTest.run(g, (int) Const.ITEM_LIFE_TIME + 2, 0);
        assertTrue(it.fl_destroy, "disparu après 8 s");
    }

    @Test
    void clearingTheLevelCancelsPendingItems() {
        Game g = game(true);
        GameTest.run(g, 10, 0);
        ((hammerfest.sim.entity.Bad) g.getOne(Const.BAD)).destroy();
        GameTest.run(g, (int) Const.SCORE_ITEM_TIMER + 5, 0);
        assertEquals(0, g.countList(Const.ITEM), "endClear : plus d'apparition une fois le niveau terminé");
    }

    @Test
    void pickingUpAnItemFillsTheFridgeAndScore() {
        Game g = game(false);
        Player p = g.players().get(0);
        GameTest.run(g, 10, 0);
        // Un diamant (1008) tombe sur le joueur
        ScoreItem.attach(g, p.x, p.y - 60, 1000 + Const.DIAMANT, -1);
        GameTest.run(g, 30, 0);
        assertEquals(1, g.picks().get(1008));
        assertEquals(2000, p.score);
        assertEquals(0, g.countList(Const.ITEM));
    }

    @Test
    void crystalValuesGrowWithCombo() {
        assertEquals(500, ScoreItem.crystalValue(0));
        assertEquals(2000, ScoreItem.crystalValue(1));
        assertEquals(4500, ScoreItem.crystalValue(2));
        assertEquals(50000, ScoreItem.crystalValue(20), "plafond");
    }

    @Test
    void killedBadDropsACrystalOrDiamond() {
        Game g = game(true);
        hammerfest.sim.entity.Bad b = (hammerfest.sim.entity.Bad) g.getOne(Const.BAD);
        b.setCombo(99);
        b.killHit(3);
        for (int i = 0; i < 300 && g.countList(Const.BAD) > 0; i++) GameTest.run(g, 1, 0);
        ScoreItem crystal = (ScoreItem) items(g).stream().filter(e -> e instanceof ScoreItem).findFirst().orElse(null);
        assertNotNull(crystal, "récompense lâchée");
        assertEquals(0, crystal.id, "cristal (combo)");
        assertEquals(0, crystal.subId, "premier ennemi du combo");
    }

    @Test
    void extraLifeAtHundredThousandPoints() {
        Game g = game(false);
        Player p = g.players().get(0);
        int lives = p.lives;
        p.getScoreHidden(99_000);
        assertEquals(lives, p.lives);
        p.getScoreHidden(2_000);
        assertEquals(lives + 1, p.lives);
    }
}
