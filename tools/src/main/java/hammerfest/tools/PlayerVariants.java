package hammerfest.tools;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Variantes du joueur pour les personnages autres qu'Igor : le clip hammer_player est le même, seuls ses clips
 * imbriqués de tête (gotoAndStop(head)) et de costume (gotoAndStop(skin)) changent d'image.
 *
 * <p>Pour chaque variante, écrit {@code <lib>-<variante>.xml} (clips figés sur la tête et le costume du personnage)
 * et affiche les commandes FFDec qui en tirent les images du joueur seulement ({@code -selectid}).</p>
 *
 * <p>Usage : PlayerVariants &lt;anims.xml&gt; &lt;dossierExport&gt;</p>
 */
public final class PlayerVariants {

    /** Nom, tête (Data.HEAD_*), costume (skin) : Sandy (MultiCoop), Igor et Sandy en tenue de foot (Soccer). */
    static final Object[][] VARIANTS = {
            {"igor", 1, 1},
            {"sandy", 10, 2},
            {"soccer1", 1, 3},
            {"soccer2", 10, 4},
            // déguisements d'Igor (touche D) : afro, Cerbère, Piou, Mario, cape de Tuberculoz
            {"head2", 2, 1},
            {"head3", 3, 1},
            {"head4", 4, 1},
            {"head5", 5, 1},
            {"head6", 6, 1},
            // avec le collier de Poils de Chourou (objet 99) ; les autres variantes le cachent
            {"igor-chourou", 1, 1},
            {"sandy-chourou", 10, 2},
            {"head2-chourou", 2, 1},
            {"head3-chourou", 3, 1},
            {"head4-chourou", 4, 1},
            {"head5-chourou", 5, 1},
            {"head6-chourou", 6, 1},
            // sans arme (rayon qui interdit les bombes) : écharpe grise et cadenas (image 11 des clips 436 / 477)
            {"igor-lock", 1, 1, 11},
            {"sandy-lock", 10, 2, 11},
            {"head2-lock", 2, 1, 11},
            {"head3-lock", 3, 1, 11},
            {"head4-lock", 4, 1, 11},
            {"head5-lock", 5, 1, 11},
            {"head6-lock", 6, 1, 11},
            // SoccerFest : couronne du buteur (9 Igor, 12 Sandy), tête basse contre son camp (8, 11)
            {"soccer1-crown", 9, 3},
            {"soccer1-lose", 8, 3},
            {"soccer2-crown", 12, 4},
            {"soccer2-lose", 11, 4},
    };

    /** Collier de Poils de Chourou : clip dont le script fait _visible = _parent._parent.fl_chourou. */
    static final int CHOUROU_CLIP = 483;
    /**
     * Carotte d'Igor (famille 108 ou objet 102) : clip dont le script fait _visible = _parent._parent.fl_carot,
     * gardée seulement dans les variantes {@code <v>-carot} (une par entrée de {@link #VARIANTS}).
     */
    static final int CAROT_CLIP = 479;
    static final String CAROT = "-carot";

    static final String SYMBOL = "hammer_player";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: PlayerVariants <anims.xml> <dossierExport>");
            System.exit(2);
        }
        Path xml = Path.of(args[0]);
        Path dir = Path.of(args[1]);
        SwfLayout layout = new SwfLayout();
        try (InputStream in = new FileInputStream(xml.toFile())) {
            layout.parse(in);
        }
        Set<Integer> ids = layout.clipsOf(SYMBOL);
        StringJoiner sel = new StringJoiner(",");
        for (int id : ids) sel.add(String.valueOf(id));

        System.out.println("Commandes FFDec (depuis la racine du projet) :");
        for (Object[] v : VARIANTS) {
            Map<Integer, Integer> frames = layout.frozenFrames((Integer) v[1], (Integer) v[2], v.length > 3 ? (Integer) v[3] : 1);
            for (String name : new String[] {(String) v[0], v[0] + CAROT}) {
                Set<Integer> hidden = new java.util.TreeSet<>();
                if (!name.contains("-chourou")) hidden.add(CHOUROU_CLIP);
                if (!name.endsWith(CAROT)) hidden.add(CAROT_CLIP);
                Path out = dir.resolve("anims-" + name + ".xml");
                FreezeNested.rewrite(xml, out, frames, hidden);
                String swf = "build/swf-export/_anims-" + name + ".swf";
                System.out.println("java -jar .tools/ffdec/ffdec-cli.jar -xml2swf build/swf-export/anims-" + name + ".xml " + swf
                        + " && java -jar .tools/ffdec/ffdec-cli.jar -selectid " + sel
                        + " -format sprite:png -export sprite build/swf-export/anims-" + name + " " + swf);
            }
        }
    }

    private PlayerVariants() {
    }
}
