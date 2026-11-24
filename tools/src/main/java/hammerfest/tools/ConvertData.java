package hammerfest.tools;

import hammerfest.data.ItemDef;
import hammerfest.data.ItemFamily;
import hammerfest.data.LevelSet;
import hammerfest.data.PortalData;
import hammerfest.data.QuestDef;
import hammerfest.json.Jsons;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversion one-shot des XML d'origine (original_sources/hammerfest-master/xml) en JSON (assets/data), via les
 * classes de données de core écrites par Jackson. Les XML ne sont jamais lus au runtime.
 *
 * <p>Usage : {@code ConvertData <dossier xml> <dossier sortie>} ou {@code ./gradlew tools:convertData}.</p>
 */
public final class ConvertData {

    static final String[] LANGS = {"fr", "en", "es"};

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: ConvertData <xmlDir> <outDir>");
            System.exit(2);
        }
        File xmlDir = new File(args[0]);
        File outDir = new File(args[1]);

        write(new File(outDir, "quests.json"), convertQuests(parse(new File(xmlDir, "quests.xml"))));
        write(new File(outDir, "items/scoreItems.json"), convertItems(parse(new File(xmlDir, "scoreItems.xml"))));
        write(new File(outDir, "items/specialItems.json"), convertItems(parse(new File(xmlDir, "specialItems.xml"))));
        write(new File(outDir, "portals.json"), convertPortals(parse(new File(xmlDir, "portalLinks.xml"))));
        for (String lang : LANGS) {
            write(new File(outDir, "lang/" + lang + ".json"), convertLang(parse(new File(xmlDir, "lang/" + lang + ".xml"))));
        }

        File[] levelFiles = new File(xmlDir, "levels").listFiles((d, n) -> n.endsWith(".xml"));
        if (levelFiles != null) {
            for (File f : levelFiles) {
                String name = f.getName().substring(0, f.getName().length() - 4);
                LevelSet set = LevelConverter.convert(name, readText(f));
                write(new File(outDir, "levels/" + name + ".json"), set);
                System.out.println("  levels/" + name + ".json : " + set.levels.size() + " niveau(x)");
            }
        }
        System.out.println("Données converties dans " + outDir.getAbsolutePath());
    }

    // ------------------------------------------------------------------ quêtes

    static List<QuestDef> convertQuests(Document doc) {
        List<QuestDef> quests = new ArrayList<>();
        for (Element q : children(doc.getDocumentElement(), "quest")) {
            QuestDef quest = new QuestDef(intAttr(q, "id"));
            for (Element r : children(q, "require")) {
                quest.require.add(new QuestDef.Require(intAttr(r, "item"), intAttr(r, "qty")));
            }
            for (Element g : children(q, "give")) {
                if (g.hasAttribute("family")) quest.give.add(intAttr(g, "family"));
                if (g.hasAttribute("option")) quest.options.add(g.getAttribute("option"));
                if (g.hasAttribute("mode")) quest.modes.add(g.getAttribute("mode"));
                if (g.hasAttribute("tokens")) quest.tokens = (quest.tokens == null ? 0 : quest.tokens) + intAttr(g, "tokens");
                if (g.hasAttribute("bank")) quest.bank = (quest.bank == null ? 0 : quest.bank) + intAttr(g, "bank");
                if (g.hasAttribute("log")) quest.log = true;
            }
            for (Element r : children(q, "remove")) quest.remove.add(intAttr(r, "family"));
            quests.add(quest);
        }
        return quests;
    }

    // ------------------------------------------------------------------ items

    /** La valeur "--" (inconnue) est laissée à null, donc omise du JSON. */
    static List<ItemFamily> convertItems(Document doc) {
        List<ItemFamily> families = new ArrayList<>();
        for (Element f : children(doc.getDocumentElement(), "family")) {
            ItemFamily family = new ItemFamily(intAttr(f, "id"));
            for (Element i : children(f, "item")) {
                String value = i.getAttribute("value").trim();
                family.items.add(new ItemDef(intAttr(i, "id"), intAttr(i, "rarity"),
                        value.isEmpty() || value.equals("--") ? null : Integer.valueOf(value),
                        i.hasAttribute("unlock") ? intAttr(i, "unlock") : null));
            }
            families.add(family);
        }
        return families;
    }

    // ------------------------------------------------------------------ portails

    static PortalData convertPortals(Document doc) {
        Element root = doc.getDocumentElement();
        PortalData out = new PortalData();
        for (Element t : descendants(root, "tag")) {
            out.tags.add(new PortalData.Tag(t.getAttribute("name"), intAttr(t, "did"), intAttr(t, "lid")));
        }
        for (Element w : descendants(root, null)) {
            String type = w.getTagName();
            if (type.equals("oneway") || type.equals("twoway")) {
                out.ways.add(new PortalData.Way(type, w.getAttribute("from").trim(), w.getAttribute("to").trim()));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ langues

    /**
     * Dictionnaire plat clé → texte : {@code static.<id>}, {@code family.<id>}, {@code item.<id>},
     * {@code quest.<id>.title}, {@code quest.<id>.desc}, {@code level.<did>.<lid>}, {@code key.<id>}.
     */
    static Map<String, String> convertLang(Document doc) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Element section : children(doc.getDocumentElement(), null)) {
            switch (section.getTagName()) {
                case "statics":
                    for (Element t : children(section, "t")) put(out, "static." + t.getAttribute("id"), t.getAttribute("v"));
                    break;
                case "families":
                    for (Element f : children(section, "family")) put(out, "family." + f.getAttribute("id"), f.getAttribute("name"));
                    break;
                case "items":
                    for (Element i : children(section, "item")) put(out, "item." + i.getAttribute("id"), i.getAttribute("name"));
                    break;
                case "quests":
                    for (Element q : children(section, "quest")) {
                        put(out, "quest." + q.getAttribute("id") + ".title", q.getAttribute("title"));
                        put(out, "quest." + q.getAttribute("id") + ".desc", q.getTextContent().replaceAll("\\s+", " ").trim());
                    }
                    break;
                case "dimensions":
                    for (Element d : children(section, "dimension")) {
                        for (Element l : children(d, "level")) {
                            put(out, "level." + d.getAttribute("id") + "." + l.getAttribute("id"), l.getAttribute("name"));
                        }
                    }
                    break;
                case "keys":
                    for (Element k : children(section, "key")) put(out, "key." + k.getAttribute("id"), k.getAttribute("name"));
                    break;
                default:
                    System.err.println("Section de langue ignorée : " + section.getTagName());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ utilitaires

    static String readText(File file) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    /**
     * Le parseur XML de Flash était tolérant : certains fichiers contiennent des commentaires
     * invalides (« -- » à l'intérieur). On retire donc les commentaires avant parsing.
     */
    static Document parse(File file) throws Exception {
        String text = readText(file).replaceAll("(?s)<!--.*?-->", "");
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setExpandEntityReferences(false);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(text)));
    }

    static List<Element> children(Element parent, String tag) {
        List<Element> list = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && (tag == null || ((Element) n).getTagName().equals(tag))) list.add((Element) n);
        }
        return list;
    }

    static List<Element> descendants(Element parent, String tag) {
        List<Element> list = new ArrayList<>();
        NodeList all = parent.getElementsByTagName(tag == null ? "*" : tag);
        for (int i = 0; i < all.getLength(); i++) list.add((Element) all.item(i));
        return list;
    }

    static int intAttr(Element e, String name) {
        return Integer.parseInt(e.getAttribute(name).trim());
    }

    static void put(Map<String, String> map, String key, String value) {
        if (map.put(key, value) != null) System.err.println("Clé de langue en double : " + key);
    }

    static void write(File file, Object value) throws IOException {
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), Jsons.write(value).getBytes(StandardCharsets.UTF_8));
    }

    private ConvertData() {
    }
}
