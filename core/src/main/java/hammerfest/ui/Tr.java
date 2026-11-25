package hammerfest.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import hammerfest.json.Jsons;

import java.util.HashMap;
import java.util.Map;

/**
 * Traduction des textes du portage (menus, écrans, messages) : la clé est le texte français tel qu'il est écrit dans le
 * code, la table {@code data/lang/ui.json} donne sa version anglaise (« en ») et espagnole (« es »). Un texte absent de
 * la table, ou la langue « fr », renvoie le texte français. Les textes du jeu d'origine (objets, quêtes, contrées,
 * messages) viennent, eux, de data/lang/&lt;langue&gt;.json.
 *
 * <p>Sans dépendance libGDX : la table est fournie par {@link #load(String, String)}.</p>
 */
public final class Tr {

    private static String lang = "fr";
    private static Map<String, Map<String, String>> table = new HashMap<>();

    /** Langue courante (fr, en, es) et contenu JSON de la table. */
    public static void load(String language, String json) {
        lang = language == null ? "fr" : language;
        if (json != null) table = Jsons.read(json, new TypeReference<Map<String, Map<String, String>>>() { });
    }

    public static void setLanguage(String language) {
        lang = language == null ? "fr" : language;
    }

    public static String language() {
        return lang;
    }

    /** Texte {@code fr} dans la langue courante (le texte français s'il n'est pas traduit). */
    public static String t(String fr) {
        if (fr == null || "fr".equals(lang)) return fr;
        Map<String, String> e = table.get(fr);
        if (e == null) return fr;
        String s = e.get(lang);
        return s == null || s.isEmpty() ? fr : s;
    }

    private Tr() {
    }
}
