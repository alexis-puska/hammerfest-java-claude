package hammerfest.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import hammerfest.data.GameModes;
import hammerfest.json.Jsons;
import hammerfest.save.ControlSettings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Chaque texte du portage passé à Tr.t a sa version anglaise et espagnole dans data/lang/ui.json. */
class TrTest {

    static Map<String, Map<String, String>> table() throws IOException {
        return Jsons.read(new String(Files.readAllBytes(Path.of("data/lang/ui.json")), StandardCharsets.UTF_8),
                new TypeReference<Map<String, Map<String, String>>>() { });
    }

    static List<String> usedKeys() throws IOException {
        List<String> keys = new ArrayList<>();
        Pattern p = Pattern.compile("Tr\\.t\\(\"((?:[^\"\\\\]|\\\\.)*)\"\\)");
        try (Stream<Path> files = Files.walk(Path.of("../core/src/main/java"))) {
            for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                Matcher m = p.matcher(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
                while (m.find()) keys.add(m.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
            }
        }
        for (GameModes.Mode m : GameModes.MODES) {
            keys.add(m.label);
            keys.add(m.label.replace(" (2 joueurs, même PC)", "").replace(" (même PC)", ""));
            for (GameModes.Option o : m.options) keys.add(o.label);
        }
        keys.addAll(List.of(ControlSettings.ACTION_NAMES));
        return keys;
    }

    @Test
    void everyUiTextIsTranslated() throws IOException {
        Map<String, Map<String, String>> t = table();
        List<String> missing = new ArrayList<>();
        for (String k : usedKeys()) {
            Map<String, String> e = t.get(k);
            if (e == null || e.get("en") == null || e.get("es") == null) missing.add(k);
        }
        assertTrue(missing.isEmpty(), "textes sans traduction : " + missing);
    }

    @Test
    void translatesAndFallsBackToFrench() throws IOException {
        Tr.load("en", new String(Files.readAllBytes(Path.of("data/lang/ui.json")), StandardCharsets.UTF_8));
        assertEquals("Play", Tr.t("Jouer"));
        assertEquals("texte inconnu", Tr.t("texte inconnu"), "absent de la table : français");
        Tr.setLanguage("es");
        assertEquals("Jugar", Tr.t("Jouer"));
        Tr.setLanguage("fr");
        assertEquals("Jouer", Tr.t("Jouer"));
    }
}
