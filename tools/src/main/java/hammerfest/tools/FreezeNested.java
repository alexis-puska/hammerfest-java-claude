package hammerfest.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Réduit à leur 1re image les clips imbriqués que le jeu fige par script (tête d'Igor : gotoAndStop(head),
 * arme tenue : gotoAndStop(currentWeapon)…), dans l'export XML d'un SWF (FFDec -swf2xml).
 *
 * <p>FFDec ignore l'ActionScript et anime ces clips à l'export : la tête d'Igor change à chaque image.
 * On réécrit l'XML puis on reconstruit un SWF (FFDec -xml2swf) dont l'export donne des images figées.
 * Les identifiants de caractères ne changent pas : la mise en page (SwfLayout) reste valable.</p>
 *
 * <p>Usage : FreezeNested &lt;in.xml&gt; &lt;out.xml&gt;</p>
 */
public final class FreezeNested {

    private static final Pattern SPRITE = Pattern.compile("^(\\s*)<item type=\"DefineSpriteTag\".*spriteId=\"(\\d+)\"");

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: FreezeNested <in.xml> <out.xml>");
            System.exit(2);
        }
        SwfLayout layout = new SwfLayout();
        try (InputStream in = new FileInputStream(args[0])) {
            layout.parse(in);
        }
        Map<Integer, Integer> frozen = new java.util.TreeMap<>();
        for (int id : layout.frozenNested()) frozen.put(id, 1);
        int done = rewrite(Path.of(args[0]), Path.of(args[1]), frozen, Set.of());
        System.out.println(done + " clips imbriqués figés sur leur 1re image -> " + args[1]);
        // Enfants affichés par script seulement (lettres EXTEND du bandeau) : retirés de l'image du clip
        Map<Integer, Set<Integer>> depths = new java.util.TreeMap<>();
        for (Map.Entry<String, java.util.List<String>> e : HIDDEN_CHILDREN.entrySet()) {
            Integer sid = layout.exports.get(e.getKey());
            if (sid == null || !layout.sprites.containsKey(sid)) continue;
            for (java.util.TreeMap<Integer, SwfLayout.Placed> frame : layout.sprites.get(sid).frames) {
                for (Map.Entry<Integer, SwfLayout.Placed> d : frame.entrySet()) {
                    String n = d.getValue().name;
                    if (n != null && e.getValue().stream().anyMatch(n::startsWith)) depths.computeIfAbsent(sid, k -> new java.util.TreeSet<>()).add(d.getKey());
                }
            }
        }
        // Même chose dans les sous-clips « sub » du symbole (balle de la fraise : sub.balle._visible)
        for (Map.Entry<String, java.util.List<String>> e : HIDDEN_IN_SUBS.entrySet()) {
            Integer sid = layout.exports.get(e.getKey());
            if (sid == null || !layout.sprites.containsKey(sid)) continue;
            for (int subId : subClips(layout, sid)) {
                for (java.util.TreeMap<Integer, SwfLayout.Placed> frame : layout.sprites.get(subId).frames) {
                    for (Map.Entry<Integer, SwfLayout.Placed> d : frame.entrySet()) {
                        String n = d.getValue().name;
                        if (n != null && e.getValue().contains(n)) depths.computeIfAbsent(subId, k -> new java.util.TreeSet<>()).add(d.getKey());
                    }
                }
            }
        }
        if (!depths.isEmpty()) {
            int removed = hideDepths(Path.of(args[1]), depths);
            System.out.println(removed + " placements d'enfants affichés par script retirés " + depths);
        }
    }

    /** Symbole → préfixe du nom des enfants que le jeu n'affiche qu'à la demande (dessinés à part par le jeu). */
    static final Map<String, java.util.List<String>> HIDDEN_CHILDREN = Map.of("hammer_interf_game", java.util.List.of("letter"),
            "hammer_map", java.util.List.of("pit", "ptr"));

    /**
     * Symbole → nom exact des enfants de ses sous-clips « sub » que le jeu n'affiche qu'à la demande, dessinés à part
     * (couche « part » de SwfLayout.TOGGLE_PARTS) : balle tenue par la fraise qui l'a (Fraise.mt : sub.balle._visible).
     */
    static final Map<String, java.util.List<String>> HIDDEN_IN_SUBS = Map.of("hammer_bad_fraise", java.util.List.of("balle"));

    /** Sous-clips « sub » placés dans les images du symbole {@code sid}. */
    static Set<Integer> subClips(SwfLayout layout, int sid) {
        Set<Integer> out = new java.util.TreeSet<>();
        for (java.util.TreeMap<Integer, SwfLayout.Placed> frame : layout.sprites.get(sid).frames) {
            for (SwfLayout.Placed p : frame.values()) if ("sub".equals(p.name) && layout.sprites.containsKey(p.id)) out.add(p.id);
        }
        return out;
    }

    /** Retire, dans les sprites donnés, les placements et retraits aux profondeurs données (réécrit le fichier). */
    static int hideDepths(Path file, Map<Integer, Set<Integer>> depths) throws Exception {
        java.util.List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        java.util.List<String> out = new java.util.ArrayList<>(lines.size());
        Set<Integer> cur = null;
        String endSub = null, skipUntil = null;
        int removed = 0;
        Pattern depth = Pattern.compile("depth=\"(\\d+)\"");
        for (String line : lines) {
            if (skipUntil != null) {
                if (line.equals(skipUntil)) skipUntil = null;
                continue;
            }
            if (cur == null) {
                Matcher m = SPRITE.matcher(line);
                if (m.find() && depths.containsKey(Integer.parseInt(m.group(2)))) {
                    cur = depths.get(Integer.parseInt(m.group(2)));
                    endSub = m.group(1) + "  </subTags>";
                }
            } else if (line.equals(endSub)) {
                cur = null;
            } else if (line.contains("type=\"PlaceObject") || line.contains("type=\"RemoveObject")) {
                Matcher d = depth.matcher(line);
                if (d.find() && cur.contains(Integer.parseInt(d.group(1)))) {
                    removed++;
                    if (!line.trim().endsWith("/>")) skipUntil = line.substring(0, line.indexOf('<')) + "</item>";
                    continue;
                }
            }
            out.add(line);
        }
        Files.write(file, out, StandardCharsets.UTF_8);
        return removed;
    }

    /**
     * Copie l'XML en réduisant chaque sprite de {@code frozen} (id → image N) à une seule image : les balises des
     * images 1..N sont gardées mais seule la N-ième est affichée (les ShowFrame précédents sont retirés, ce qui
     * rejoue les placements jusqu'à l'image N), tout ce qui suit est retiré. Renvoie le nombre de sprites modifiés.
     */
    static int rewrite(Path in, Path out, Map<Integer, Integer> frozen) throws Exception {
        return rewrite(in, out, frozen, Set.of());
    }

    /**
     * Comme {@link #rewrite(Path, Path, Map)}, et vide en plus les sprites de {@code hidden} (une image vide) :
     * clips que le jeu cache par script (_visible), comme le collier de Poils de Chourou.
     */
    static int rewrite(Path in, Path out, Map<Integer, Integer> frozen, Set<Integer> hidden) throws Exception {
        int done = 0;
        try (BufferedReader r = Files.newBufferedReader(in, StandardCharsets.UTF_8);
             BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            String line;
            String endSubTags = null; // fin des sous-balises du sprite en cours de réduction
            boolean skipping = false, emptying = false;
            int target = 1, shown = 0;
            while ((line = r.readLine()) != null) {
                if (endSubTags != null) {
                    if (line.equals(endSubTags)) {
                        endSubTags = null;
                        skipping = false;
                        emptying = false;
                    } else if (skipping) {
                        continue;
                    } else if (emptying && !line.contains("type=\"ShowFrameTag\"") && !line.trim().equals("<subTags>")) {
                        continue; // sprite caché : ni placements ni actions, seulement ses images vides
                    } else if (line.contains("type=\"ShowFrameTag\"")) {
                        shown++;
                        if (shown < target) continue; // images avant N : leurs placements s'accumulent
                        skipping = true; // tout ce qui suit l'image N est retiré
                    }
                } else {
                    Matcher m = SPRITE.matcher(line);
                    int sid = m.find() ? Integer.parseInt(m.group(2)) : -1;
                    if (sid >= 0 && hidden.contains(sid)) {
                        emptying = true;
                        target = 1;
                        shown = 0;
                        line = line.replaceFirst("frameCount=\"\\d+\"", "frameCount=\"1\"");
                        endSubTags = m.group(1) + "  </subTags>";
                        done++;
                    } else if (sid >= 0 && frozen.containsKey(sid)) {
                        target = frozen.get(sid);
                        shown = 0;
                        line = line.replaceFirst("frameCount=\"\\d+\"", "frameCount=\"1\"");
                        endSubTags = m.group(1) + "  </subTags>";
                        done++;
                    }
                }
                // Champs texte remplis par le jeu (titre de la carte, « VITE ! », icônes…) : FFDec dessine leur texte
                // par défaut (« pouet », « blabla », « Strates supérieures de Morgenfell ») ; on le vide, le jeu dessine le vrai.
                if (line.contains("type=\"DefineEditTextTag\"")) line = line.replaceFirst("initialText=\"[^\"]*\"", "initialText=\"\"");
                w.write(line);
                w.newLine();
            }
        }
        return done;
    }

    private FreezeNested() {
    }
}
