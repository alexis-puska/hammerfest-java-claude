package hammerfest.data;

import java.util.HashMap;
import java.util.Map;

/** Dictionnaire clé → texte (assets/data/lang/&lt;lang&gt;.json). */
public class Lang {
    private final Map<String, String> texts;

    public Lang(Map<String, String> texts) {
        this.texts = new HashMap<>(texts);
    }

    /** Texte de la clé, ou la clé entre crochets si absente (repérage des manques). */
    public String get(String key) {
        String t = texts.get(key);
        return t != null ? t : "[" + key + "]";
    }

    public boolean has(String key) {
        return texts.containsKey(key);
    }

    /** Retire le balisage *gras* du texte d'origine. */
    public String plain(String key) {
        return get(key).replace("*", "");
    }
}
