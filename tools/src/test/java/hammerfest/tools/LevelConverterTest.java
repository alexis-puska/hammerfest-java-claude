package hammerfest.tools;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.json.Jsons;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Non-régression du décodage des niveaux (PersistCodec) contre les fichiers d'origine. */
class LevelConverterTest {

    static final File LEVELS = new File(System.getProperty("hammerfest.xml", "../original_sources/hammerfest-master/xml"), "levels");

    static LevelSet load(String name) throws Exception {
        return LevelConverter.convert(name, ConvertData.readText(new File(LEVELS, name + ".xml")));
    }

    @Test
    void persistCodecPrimitives() {
        // Encodages construits à la main selon PersistCodec.mt / BitCodec.mt.
        // int 5 : 0 0 (int) 01 (4 bits) 0101 -> 00 01 0101 + padding -> "000101" "01...." -> f, q
        assertEquals(5, PersistCodec.decode("fq"));
        // true : 1101 + padding -> "110100" = 52 -> '0'
        assertEquals(Boolean.TRUE, PersistCodec.decode("0"));
        // tableau vide : 100 + fin 11 + padding -> "100110" = 38 -> 'M'
        assertEquals(List.of(), PersistCodec.decode("M"));
    }

    @Test
    void adventureLevel5() throws Exception {
        LevelSet set = load("adventure");
        assertEquals(107, set.levels.size());
        LevelData l = set.level(5);
        assertEquals(3, l.playerX);
        assertEquals(5, l.playerY);
        assertEquals(2, l.badList.size());
        assertEquals(9, l.badList.get(0).id);
        assertEquals(15, l.badList.get(0).x);
        assertEquals(11, l.badList.get(0).y);
        assertEquals(1, l.specialSlots.size());
        assertEquals(3, l.specialSlots.get(0).x);
        assertEquals(17, l.specialSlots.get(0).y);
        assertEquals(1, l.tile(0, 18));
        assertEquals(1, l.tile(2, 9));
        assertEquals(0, l.tile(0, 0));
    }

    @Test
    void tutorialScriptIsReadable() throws Exception {
        LevelData l = load("tutorial").level(0);
        assertTrue(l.script.startsWith("<t_pos x=\"2\" y=\"8\" d=\"2\">"), l.script.substring(0, 40));
        assertFalse(l.script.contains("$e_"));
    }

    @Test
    void allSetsDecodeWithFullGrids() throws Exception {
        File[] files = LEVELS.listFiles((d, n) -> n.endsWith(".xml"));
        assertNotNull(files);
        int total = 0;
        for (File f : files) {
            String name = f.getName().replace(".xml", "");
            LevelSet set = load(name);
            for (LevelData l : set.levels) {
                assertEquals(LevelData.HEIGHT, l.rows.size(), name + "#" + l.id);
                for (int x = 0; x < LevelData.WIDTH; x++) {
                    for (int y = 0; y < LevelData.HEIGHT; y++) {
                        assertNotEquals(LevelConverter.WALL, l.tile(x, y), "murs convertis en sol");
                    }
                }
                total++;
            }
        }
        assertTrue(total > 300, "niveaux décodés : " + total);
    }

    @Test
    void jsonRoundTrip() throws Exception {
        LevelSet set = load("tutorial");
        LevelSet back = Jsons.read(Jsons.write(set), LevelSet.class);
        assertEquals(set.levels.size(), back.levels.size());
        for (int i = 0; i < set.levels.size(); i++) {
            LevelData a = set.levels.get(i), b = back.levels.get(i);
            assertEquals(a.rows, b.rows);
            assertEquals(a.script, b.script);
            for (int x = 0; x < LevelData.WIDTH; x++)
                for (int y = 0; y < LevelData.HEIGHT; y++)
                    assertEquals(a.tile(x, y), b.tile(x, y));
        }
        assertFalse(Jsons.write(set).contains("\"grid\""));
    }
}
