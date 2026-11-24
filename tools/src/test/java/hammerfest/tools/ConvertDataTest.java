package hammerfest.tools;

import hammerfest.data.ItemFamily;
import hammerfest.data.PortalData;
import hammerfest.data.QuestDef;
import hammerfest.json.Jsons;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConvertDataTest {

    static final File XML = new File(System.getProperty("hammerfest.xml", "../original_sources/hammerfest-master/xml"));

    @Test
    void quests() throws Exception {
        List<QuestDef> quests = ConvertData.convertQuests(ConvertData.parse(new File(XML, "quests.xml")));
        assertEquals(76, quests.size());

        QuestDef zodiac = quests.get(0);
        assertEquals(0, zodiac.id);
        assertEquals("quest.0.title", zodiac.nameKey);
        assertEquals(12, zodiac.require.size());
        assertEquals(40, zodiac.require.get(0).item);
        assertEquals(List.of(100, 6), zodiac.give);
        assertEquals(List.of(5), zodiac.remove);
        assertEquals(List.of("mirror"), quests.get(63).options);
        assertEquals(List.of("timeattack"), quests.get(73).modes);
    }

    @Test
    void items() throws Exception {
        List<ItemFamily> score = ConvertData.convertItems(ConvertData.parse(new File(XML, "scoreItems.xml")));
        List<ItemFamily> special = ConvertData.convertItems(ConvertData.parse(new File(XML, "specialItems.xml")));
        assertEquals(31, score.size());
        assertEquals(20, special.size());
        assertEquals(1000, score.get(0).items.get(0).id);
        assertNull(score.get(0).items.get(0).value, "value \"--\" inconnue");
        assertEquals(500, score.get(0).items.get(1).value);
    }

    @Test
    void itemsJsonRoundTrip() throws Exception {
        List<ItemFamily> score = ConvertData.convertItems(ConvertData.parse(new File(XML, "scoreItems.xml")));
        String json = Jsons.write(score);
        assertFalse(json.contains("familyId"), "champ technique non sérialisé");
        assertFalse(json.contains("\"value\" : null"), "valeurs null omises");
        ItemFamily[] back = Jsons.read(json, ItemFamily[].class);
        assertEquals(score.size(), back.length);
        assertEquals(500, back[0].items.get(1).value);
    }

    @Test
    void lang() throws Exception {
        Map<String, String> fr = ConvertData.convertLang(ConvertData.parse(new File(XML, "lang/fr.xml")));
        assertEquals("Niveau", fr.get("static.1"));
        assertEquals("Alphabet Cristallin", fr.get("item.0"));
        assertEquals("Les constellations", fr.get("quest.0.title"));
        assertTrue(fr.get("quest.0.desc").startsWith("Igor peut maintenant poser 2 bombes"));
        assertEquals("Village de Hammerfest", fr.get("level.0.0"));
        assertEquals("Passe-partout en bois", fr.get("key.0"));
    }

    @Test
    void portals() throws Exception {
        PortalData p = ConvertData.convertPortals(ConvertData.parse(new File(XML, "portalLinks.xml")));
        assertEquals("village", p.tags.get(0).name);
        PortalData.Way way = p.ways.get(0);
        assertEquals("oneway", way.type);
        assertEquals("village", way.from);
        assertEquals("d_forest1(-1)", way.to);
    }
}
