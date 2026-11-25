package hammerfest.tools;

import hammerfest.data.SpriteLibrary;
import hammerfest.json.Jsons;

import javax.imageio.ImageIO;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Lit l'export XML d'un SWF (JPEXS FFDec {@code -swf2xml}) et produit un {@link SpriteLibrary} :
 * nombre d'images, cadre (bornes) et placement du sous-clip "sub" de chaque clip atteignable
 * depuis les symboles exportés.
 *
 * <p>Usage : {@code SwfLayout <swf.xml> <dossier export PNG FFDec> <sortie.json>}.
 * Le dossier PNG sert uniquement à vérifier que les tailles calculées correspondent.</p>
 */
public final class SwfLayout {

    static final float TWIPS = 20f;

    /** Rectangle en twips. */
    static final class Rect {
        double xMin = Double.POSITIVE_INFINITY, yMin = Double.POSITIVE_INFINITY;
        double xMax = Double.NEGATIVE_INFINITY, yMax = Double.NEGATIVE_INFINITY;

        boolean isEmpty() {
            return xMin > xMax;
        }

        void add(double x, double y) {
            xMin = Math.min(xMin, x);
            yMin = Math.min(yMin, y);
            xMax = Math.max(xMax, x);
            yMax = Math.max(yMax, y);
        }

        void add(Rect r, Matrix m) {
            if (r.isEmpty()) return;
            double[][] pts = {{r.xMin, r.yMin}, {r.xMax, r.yMin}, {r.xMin, r.yMax}, {r.xMax, r.yMax}};
            for (double[] p : pts) add(m.a * p[0] + m.c * p[1] + m.tx, m.b * p[0] + m.d * p[1] + m.ty);
        }
    }

    /** Matrice Flash (translation en twips). */
    static final class Matrix {
        double a = 1, b, c, d = 1, tx, ty;

        Matrix copy() {
            Matrix m = new Matrix();
            m.a = a; m.b = b; m.c = c; m.d = d; m.tx = tx; m.ty = ty;
            return m;
        }
    }

    static final class Placed {
        int id;
        Matrix matrix = new Matrix();
        String name;
        /** Transformation de couleur (CXFORMWITHALPHA) : multiplicateurs r, g, b, a (/256) puis ajouts ; null = aucune. */
        int[] color;

        Placed copy() {
            Placed p = new Placed();
            p.id = id;
            p.matrix = matrix.copy();
            p.name = name;
            p.color = color;
            return p;
        }
    }

    static final class Sprite {
        int id;
        int frameCount;
        int stopFrame, removeFrame;
        /** Première image (> 1) dont le script fait gotoAndPlay : jamais affichée, la boucle s'arrête avant. */
        int jumpFrame;
        /** Image visée par ce saut (1 par défaut ; étiquette pour gotoAndPlay("loop")). */
        int jumpTarget = 1;
        String jumpLabel;
        final Map<String, Integer> labels = new HashMap<>();
        final List<Integer> stopFrames = new ArrayList<>();
        /** Image 1 arrêtée par script (stop() ou gotoAndStop(variable)) : clip figé par le jeu. */
        boolean stoppedAtStart;
        /** Bytecode de l'image 1 (variable lue par gotoAndStop : head, skin, currentWeapon…). */
        String frame1Actions;
        /** Image 1 avec play() (0x06) sans stop() : le clip repart même si le code l'arrête juste après l'avoir attaché. */
        boolean playOnAttach;
        /** Liste d'affichage de chaque image : profondeur → objet placé. */
        final List<TreeMap<Integer, Placed>> frames = new ArrayList<>();
    }

    final Map<Integer, Rect> shapeBounds = new HashMap<>();
    final Map<Integer, Sprite> sprites = new HashMap<>();
    final Map<String, Integer> exports = new LinkedHashMap<>();
    private final Map<Integer, Rect> spriteBounds = new HashMap<>();

    public static void main(String[] args) throws Exception {
        if (args.length != 3 && args.length != 5) {
            System.err.println("Usage: SwfLayout <swf.xml> <pngExportDir> <out.json> [<symbole> <nouveauNom>]");
            System.exit(2);
        }
        SwfLayout layout = new SwfLayout();
        if (args.length == 5) {
            layout.onlySymbol = args[3];
            layout.renameTo = args[4];
        }
        try (InputStream in = new FileInputStream(args[0])) {
            layout.parse(in);
        }
        File original = new File(args[0].replace("-frozen.xml", ".xml"));
        if (args[0].endsWith("-frozen.xml") && original.exists()) {
            layout.partsSource = new SwfLayout();
            try (InputStream in = new FileInputStream(original)) {
                layout.partsSource.parse(in);
            }
        }
        SpriteLibrary lib = layout.build();
        if (!layout.nestedLoops.isEmpty()) {
            System.out.println("  sous-clips à une image avec enfant en boucle (clip=sous-clip) : " + layout.nestedLoops);
        }
        if (layout.layerCount > 0) System.out.println("  " + layout.layerCount + " images (ennemis, objets) avec couches animées");
        int checked = layout.verify(lib, new File(args[1]));
        File out = new File(args[2]);
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), Jsons.write(lib).getBytes(StandardCharsets.UTF_8));
        System.out.println(lib.symbols.size() + " symboles, " + lib.clips.size() + " clips, "
                + checked + " cadres vérifiés -> " + out);
    }

    // ------------------------------------------------------------------ lecture XML

    /** « removeMovieClip » en ASCII hexadécimal (appel this.removeMovieClip() dans un DoAction). */
    static final String REMOVE_MOVIE_CLIP_HEX = "72656d6f76654d6f766965436c6970";

    /** Vrai si le bytecode contient ActionGotoFrame2 (0x9F) sans lecture : gotoAndStop(expression). */
    static boolean hasGotoAndStop(String hex) {
        int i = 0;
        while (i + 2 <= hex.length()) {
            int op = Integer.parseInt(hex.substring(i, i + 2), 16);
            if (op == 0) return false;
            i += 2;
            if (op >= 0x80) {
                if (i + 4 > hex.length()) return false;
                int len = Integer.parseInt(hex.substring(i, i + 2), 16) | Integer.parseInt(hex.substring(i + 2, i + 4), 16) << 8;
                if (op == 0x9F && len >= 1 && i + 6 <= hex.length()
                        && (Integer.parseInt(hex.substring(i + 4, i + 6), 16) & 1) == 0) return true;
                i += 4 + len * 2;
            }
        }
        return false;
    }

    /** Vrai si le bytecode AS1/2 contient l'action ActionStop (0x07) au niveau des instructions. */
    /** L'action {@code wanted} (code d'une action sans données, < 0x80) figure dans le bloc. */
    static boolean hasOp(String hex, int wanted) {
        int i = 0;
        while (i + 2 <= hex.length()) {
            int op = Integer.parseInt(hex.substring(i, i + 2), 16);
            if (op == 0) return false;
            if (op == wanted) return true;
            i += 2;
            if (op >= 0x80) {
                if (i + 4 > hex.length()) return false;
                int len = Integer.parseInt(hex.substring(i, i + 2), 16) | Integer.parseInt(hex.substring(i + 2, i + 4), 16) << 8;
                i += 4 + len * 2;
            }
        }
        return false;
    }

    static boolean hasStop(String hex) {
        int i = 0;
        while (i + 2 <= hex.length()) {
            int op = Integer.parseInt(hex.substring(i, i + 2), 16);
            if (op == 0) return false;
            if (op == 0x07) return true;
            i += 2;
            if (op >= 0x80) {
                if (i + 4 > hex.length()) return false;
                int len = Integer.parseInt(hex.substring(i, i + 2), 16) | Integer.parseInt(hex.substring(i + 2, i + 4), 16) << 8;
                i += 4 + len * 2;
            }
        }
        return false;
    }

    void parse(InputStream in) throws Exception {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader r = f.createXMLStreamReader(in, "UTF-8");

        Deque<Sprite> spriteStack = new ArrayDeque<>();
        Deque<TreeMap<Integer, Placed>> displayStack = new ArrayDeque<>();
        Deque<String> elementTypes = new ArrayDeque<>();
        Integer pendingShapeId = null;
        Placed pendingPlace = null;
        Integer pendingDepth = null;
        boolean inExport = false, inExportTags = false, inExportNames = false;
        List<Integer> exportIds = new ArrayList<>();
        List<String> exportNames = new ArrayList<>();
        StringBuilder text = new StringBuilder();

        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                String local = r.getLocalName();
                String type = r.getAttributeValue(null, "type");
                elementTypes.push(type == null ? "" : type);
                text.setLength(0);

                if (inExport) {
                    if (local.equals("tags")) inExportTags = true;
                    if (local.equals("names")) inExportNames = true;
                    continue;
                }
                if (type == null) continue;
                switch (type) {
                    case "DefineSpriteTag": {
                        Sprite s = new Sprite();
                        s.id = intAttr(r, "spriteId");
                        s.frameCount = intAttr(r, "frameCount");
                        spriteStack.push(s);
                        displayStack.push(new TreeMap<>());
                        break;
                    }
                    case "PlaceObject2Tag":
                    case "PlaceObject3Tag": {
                        if (displayStack.isEmpty()) break; // timeline principale : ignorée
                        int depth = intAttr(r, "depth");
                        TreeMap<Integer, Placed> dl = displayStack.peek();
                        boolean hasChar = "true".equals(r.getAttributeValue(null, "placeFlagHasCharacter"));
                        boolean move = "true".equals(r.getAttributeValue(null, "placeFlagMove"));
                        Placed p;
                        if (hasChar) {
                            p = new Placed();
                            p.id = intAttr(r, "characterId");
                            Placed old = dl.get(depth);
                            if (move && old != null) p.matrix = old.matrix.copy();
                        } else {
                            Placed old = dl.get(depth);
                            p = old == null ? new Placed() : old.copy();
                        }
                        String name = r.getAttributeValue(null, "name");
                        if (name != null) p.name = name;
                        pendingPlace = p;
                        pendingDepth = depth;
                        break;
                    }
                    case "MATRIX":
                        if (pendingPlace != null) pendingPlace.matrix = readMatrix(r);
                        break;
                    case "CXFORMWITHALPHA":
                    case "CXFORM":
                        if (pendingPlace != null) pendingPlace.color = readColor(r);
                        break;
                    case "RemoveObject2Tag":
                    case "RemoveObjectTag":
                        if (!displayStack.isEmpty()) displayStack.peek().remove(intAttr(r, "depth"));
                        break;
                    case "DoActionTag": {
                        if (spriteStack.isEmpty()) break;
                        Sprite s = spriteStack.peek();
                        int frame = s.frames.size() + 1;
                        String hex = r.getAttributeValue(null, "actionBytes");
                        if (hex == null) break;
                        if (s.removeFrame == 0 && hex.contains(REMOVE_MOVIE_CLIP_HEX)) s.removeFrame = frame;
                        if (s.stopFrame == 0 && hasStop(hex)) s.stopFrame = frame;
                        if (hasStop(hex) && !s.stopFrames.contains(frame)) s.stopFrames.add(frame);
                        if (frame == 1 && (hasStop(hex) || hasGotoAndStop(hex))) s.stoppedAtStart = true;
                        if (frame == 1 && s.frame1Actions == null) s.frame1Actions = hex;
                        if (frame == 1 && hasOp(hex, 0x06) && !hasStop(hex)) s.playOnAttach = true;
                        if (frame > 1 && s.jumpFrame == 0 && hex.contains("9f010001")) s.jumpFrame = frame; // GotoFrame2, play
                        // ActionGoToLabel (0x8C) suivi de Play (0x06) : gotoAndPlay("étiquette")
                        if (frame > 1 && s.jumpFrame == 0 && hex.startsWith("8c")) {
                            int len = Integer.parseInt(hex.substring(4, 6) + hex.substring(2, 4), 16);
                            String rest = hex.substring(6 + len * 2);
                            if (rest.startsWith("06")) {
                                StringBuilder lab = new StringBuilder();
                                for (int k = 6; k + 1 < 6 + len * 2; k += 2) {
                                    int c = Integer.parseInt(hex.substring(k, k + 2), 16);
                                    if (c == 0) break;
                                    lab.append((char) c);
                                }
                                s.jumpFrame = frame;
                                s.jumpLabel = lab.toString();
                            }
                        }
                        break;
                    }
                    case "FrameLabelTag": // étiquette de l'image en cours
                        if (!spriteStack.isEmpty()) spriteStack.peek().labels.put(r.getAttributeValue(null, "name"), spriteStack.peek().frames.size() + 1);
                        break;
                    case "ShowFrameTag":
                        if (!spriteStack.isEmpty()) {
                            TreeMap<Integer, Placed> snapshot = new TreeMap<>();
                            for (Map.Entry<Integer, Placed> e : displayStack.peek().entrySet()) {
                                snapshot.put(e.getKey(), e.getValue().copy());
                            }
                            spriteStack.peek().frames.add(snapshot);
                        }
                        break;
                    case "ExportAssetsTag":
                        inExport = true;
                        exportIds.clear();
                        exportNames.clear();
                        break;
                    case "RECT": {
                        if (pendingShapeId != null && (local.equals("shapeBounds") || local.equals("startBounds")
                                || local.equals("bounds") || local.equals("textBounds"))) {
                            Rect rect = new Rect();
                            rect.add(dblAttr(r, "Xmin"), dblAttr(r, "Ymin"));
                            rect.add(dblAttr(r, "Xmax"), dblAttr(r, "Ymax"));
                            shapeBounds.merge(pendingShapeId, rect, (a, b) -> {
                                a.add(b.xMin, b.yMin);
                                a.add(b.xMax, b.yMax);
                                return a;
                            });
                        }
                        break;
                    }
                    default:
                        if (type.startsWith("DefineShape") || type.startsWith("DefineMorphShape")) {
                            pendingShapeId = intAttrOr(r, "shapeId", intAttrOr(r, "characterId", -1));
                        } else if (type.equals("DefineTextTag") || type.equals("DefineText2Tag")
                                || type.equals("DefineEditTextTag") || type.equals("DefineButton2Tag")) {
                            pendingShapeId = intAttrOr(r, "characterID", intAttrOr(r, "characterId",
                                    intAttrOr(r, "buttonId", -1)));
                        }
                }
            } else if (ev == XMLStreamConstants.CHARACTERS) {
                text.append(r.getText());
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                String local = r.getLocalName();
                String type = elementTypes.pop();
                if (inExport) {
                    if (local.equals("item") && inExportTags) exportIds.add(Integer.parseInt(text.toString().trim()));
                    else if (local.equals("item") && inExportNames) exportNames.add(text.toString().trim());
                    else if (local.equals("tags")) inExportTags = false;
                    else if (local.equals("names")) inExportNames = false;
                    else if (type.equals("ExportAssetsTag")) {
                        for (int i = 0; i < Math.min(exportIds.size(), exportNames.size()); i++) {
                            exports.put(stripDollar(exportNames.get(i)), exportIds.get(i));
                        }
                        inExport = false;
                    }
                    text.setLength(0);
                    continue;
                }
                switch (type) {
                    case "DefineSpriteTag": {
                        Sprite s = spriteStack.pop();
                        displayStack.pop();
                        sprites.put(s.id, s);
                        break;
                    }
                    case "PlaceObject2Tag":
                    case "PlaceObject3Tag":
                        if (pendingPlace != null && !displayStack.isEmpty()) {
                            displayStack.peek().put(pendingDepth, pendingPlace);
                        }
                        pendingPlace = null;
                        pendingDepth = null;
                        break;
                    default:
                        if (type.startsWith("DefineShape") || type.startsWith("DefineMorphShape")
                                || type.startsWith("DefineText") || type.equals("DefineEditTextTag")
                                || type.equals("DefineButton2Tag")) {
                            pendingShapeId = null;
                        }
                }
                text.setLength(0);
            }
        }
        r.close();
    }

    private static Matrix readMatrix(XMLStreamReader r) {
        Matrix m = new Matrix();
        if ("true".equals(r.getAttributeValue(null, "hasScale"))) {
            m.a = dblAttr(r, "scaleX");
            m.d = dblAttr(r, "scaleY");
        }
        if ("true".equals(r.getAttributeValue(null, "hasRotate"))) {
            m.b = dblAttr(r, "rotateSkew0");
            m.c = dblAttr(r, "rotateSkew1");
        }
        m.tx = dblAttr(r, "translateX");
        m.ty = dblAttr(r, "translateY");
        return m;
    }

    /** Transformation de couleur d'un placement : {mult r, g, b, a (sur 256), ajout r, g, b, a}. */
    static int[] readColor(XMLStreamReader r) {
        boolean mult = "true".equals(r.getAttributeValue(null, "hasMultTerms"));
        boolean add = "true".equals(r.getAttributeValue(null, "hasAddTerms"));
        int[] c = {256, 256, 256, 256, 0, 0, 0, 0};
        String[] keys = {"red", "green", "blue", "alpha"};
        for (int i = 0; i < 4; i++) {
            if (mult) c[i] = intOr(r, keys[i] + "MultTerm", 256);
            if (add) c[4 + i] = intOr(r, keys[i] + "AddTerm", 0);
        }
        return c;
    }

    static int intOr(XMLStreamReader r, String name, int def) {
        String v = r.getAttributeValue(null, name);
        return v == null ? def : Integer.parseInt(v);
    }

    // ------------------------------------------------------------------ calcul

    /** Bornes d'un caractère (forme, texte ou clip : union de toutes ses images). */
    Rect bounds(int id) {
        Rect shape = shapeBounds.get(id);
        if (shape != null) return shape;
        Sprite s = sprites.get(id);
        if (s == null) return new Rect();
        Rect cached = spriteBounds.get(id);
        if (cached != null) return cached;
        spriteBounds.put(id, new Rect()); // garde anti-cycle
        Rect r = new Rect();
        for (TreeMap<Integer, Placed> frame : s.frames) {
            for (Placed p : frame.values()) r.add(bounds(p.id), p.matrix);
        }
        spriteBounds.put(id, r);
        return r;
    }

    /**
     * Clips dont le sous-clip porteur d'image ne s'appelle pas "sub" : nom d'export → nom de l'enfant.
     * Ex. « field » (rayons des niveaux) : l'enfant « skin » porte le type de rayon (image |id|).
     */
    static final Map<String, String> CHILD_NAMES = Map.of("field", "skin");

    /** Clips exportés et sous-clips porteurs d'animation ("sub"), dont on lit toutes les images. */
    Set<Integer> reachable() {
        Set<Integer> reachable = new HashSet<>();
        Deque<Integer> todo = new ArrayDeque<>();
        for (Integer id : exports.values()) if (sprites.containsKey(id)) todo.add(id);
        Map<Integer, String> childName = new HashMap<>();
        for (Map.Entry<String, String> e : CHILD_NAMES.entrySet()) {
            Integer id = exports.get(e.getKey());
            if (id != null) childName.put(id, e.getValue());
        }
        while (!todo.isEmpty()) {
            int id = todo.poll();
            if (!reachable.add(id)) continue;
            String name = childName.getOrDefault(id, "sub");
            for (TreeMap<Integer, Placed> frame : sprites.get(id).frames) {
                for (Placed p : frame.values()) {
                    if (name.equals(p.name) && sprites.containsKey(p.id)) todo.add(p.id);
                }
            }
        }
        return reachable;
    }

    /**
     * Clips imbriqués que le jeu fige par script dès leur 1re image (tête d'Igor : gotoAndStop(head),
     * arme tenue…) : FFDec les anime à l'export, on les réduit à leur 1re image (voir FreezeNested).
     */
    Set<Integer> frozenNested() {
        Set<Integer> reach = reachable();
        Set<Integer> parts = partClips();
        Set<Integer> out = new TreeSet<>();
        for (Sprite s : sprites.values()) {
            if (s.stoppedAtStart && s.frames.size() > 1 && !reach.contains(s.id) && !exports.containsValue(s.id)
                    && !parts.contains(s.id)) out.add(s.id);
        }
        return out;
    }

    /**
     * Clips des parties dessinées à part (PARTS) : le jeu choisit leur image (yeux de la boule de feu :
     * eyes.gotoAndStop(angle), jauge de la carte…), ils gardent donc toutes leurs images au lieu d'être figés.
     */
    Set<Integer> partClips() {
        Set<Integer> out = new TreeSet<>();
        for (Map.Entry<String, List<String>> e : PARTS.entrySet()) {
            Integer sid = exports.get(e.getKey());
            if (sid == null || !sprites.containsKey(sid)) continue;
            for (TreeMap<Integer, Placed> frame : sprites.get(sid).frames) {
                for (Map.Entry<Integer, Placed> d : frame.entrySet()) {
                    if (partKey(e.getValue(), d.getValue().name, d.getKey()) != null) out.add(d.getValue().id);
                }
            }
        }
        return out;
    }

    /** « head » et « skin » en ASCII hexadécimal (variables lues par les clips de tête et de costume du joueur). */
    static final String HEAD_HEX = "6865616400", SKIN_HEX = "736b696e00", WEAPON_HEX = "63757272656e74576561706f6e00";

    /**
     * Image sur laquelle figer chaque clip imbriqué piloté par script, pour un personnage donné : les clips de tête
     * (gotoAndStop(head)) sur {@code head}, ceux de costume (gotoAndStop(skin)) sur {@code skin}, les autres sur 1.
     */
    Map<Integer, Integer> frozenFrames(int head, int skin) {
        return frozenFrames(head, skin, 1);
    }

    /** Variante avec l'image des clips de l'écharpe (gotoAndStop(currentWeapon) : 11 = sans arme, cadenas). */
    Map<Integer, Integer> frozenFrames(int head, int skin, int weaponFrame) {
        Map<Integer, Integer> out = new TreeMap<>();
        for (int id : frozenNested()) {
            Sprite s = sprites.get(id);
            String hex = s.frame1Actions == null ? "" : s.frame1Actions;
            int frame = 1;
            if (hex.contains(HEAD_HEX)) frame = head;
            else if (hex.contains(SKIN_HEX)) frame = skin;
            else if (hex.contains(WEAPON_HEX)) frame = weaponFrame;
            out.put(id, Math.max(1, Math.min(frame, s.frames.size())));
        }
        return out;
    }

    /** Clips dont on exporte les images pour le symbole {@code symbol} (lui-même et ses sous-clips "sub"). */
    Set<Integer> clipsOf(String symbol) {
        Set<Integer> out = new TreeSet<>();
        Deque<Integer> todo = new ArrayDeque<>();
        Integer root = exports.get(symbol);
        if (root != null) todo.add(root);
        while (!todo.isEmpty()) {
            int id = todo.poll();
            if (!out.add(id)) continue;
            for (TreeMap<Integer, Placed> frame : sprites.get(id).frames) {
                for (Placed p : frame.values()) if ("sub".equals(p.name) && sprites.containsKey(p.id)) todo.add(p.id);
            }
        }
        return out;
    }

    /** Variante de bibliothèque : seul ce symbole est gardé, sous un autre nom (ex. hammer_player → hammer_player_sandy). */
    String onlySymbol, renameTo;

    /**
     * Clips d'objets dont une image peut contenir un seul clip animé non nommé, qui joue en boucle dans le jeu
     * (pièce d'or qui rebondit…) : FFDec n'en exporte qu'une position figée dans l'image du parent.
     */
    static final Set<String> LOOP_CLIPS = Set.of("hammer_item_score", "hammer_item_special",
            // flamme de la torche (décor), boules de feu (clip « skin » de 10 images qui tourne en boucle)
            "torch", "hammer_shoot_fireball", "hammer_shoot_boss_fireball", "hammer_shoot_firerain",
            "hammer_shoot_player_fireball");

    /** Nombre d'images figées qui ont reçu des couches animées (compte rendu). */
    int layerCount;

    /** Parties nommées pilotées par script, dessinées séparément (SpriteLibrary.parts). */
    static final Map<String, List<String>> PARTS = Map.of("hammer_bad_fireball", List.of("body", "eyes"),
            // carte : fond (profondeur 2), jauge, pointeur, Igor (profondeur 8) ; « @n » = enfant sans nom à la profondeur n
            "hammer_map", List.of("pit", "ptr"),
            // lettres EXTEND du bandeau (visibles une fois ramassées) ; « * » = préfixe
            "hammer_interf_game", List.of("letter*"),
            // ballon géant (objet 3) : ballon « b » tourné par script (b._rotation += 3) ; reflet resté dans l'image
            "hammer_supa_ball", List.of("b"));

    /** XML d'origine où lire les parties quand la mise en page vient de l'XML figé (enfants retirés). */
    SwfLayout partsSource;

    static String partKey(List<String> wanted, String name, int depth) {
        if (name != null) {
            for (String w : wanted) {
                if (w.equals(name) || (w.endsWith("*") && name.startsWith(w.substring(0, w.length() - 1)))) return name;
            }
        }
        return wanted.contains("@" + depth) ? "@" + depth : null;
    }

    /** Clips à une image dont l'unique enfant joue en boucle : leur placement devient celui de cet enfant. */
    final Map<Integer, Integer> nestedLoops = new java.util.TreeMap<>();

    /** Enfant en boucle d'un sous-clip à une seule image (tourbillon des portails dans le champ), sinon null. */
    Placed wrappedLoop(int id) {
        Sprite t = sprites.get(id);
        if (t == null || (t.frames.size() != 1 && !scriptStopped().contains(id))) return null;
        return loopChild(t.frames.get(0));
    }

    /** Boucle de la 2e image d'un clip arrêté par script (rayon de téléporteur allumé, SetManager.showField) ; null sinon. */
    Placed wrappedLoopOn(int id) {
        Sprite t = sprites.get(id);
        if (t == null || t.frames.size() < 2 || !scriptStopped().contains(id)) return null;
        return loopChild(t.frames.get(1));
    }

    private Set<Integer> scriptStopped;

    /**
     * Sous-clips arrêtés sur leur 1re image par le script du jeu : ceux des rayons (View.attachField :
     * skin.sub.stop()) ; leurs enfants animés jouent quand même (scintillement des téléporteurs).
     */
    Set<Integer> scriptStopped() {
        if (scriptStopped != null) return scriptStopped;
        scriptStopped = new HashSet<>();
        Integer field = exports.get("field");
        if (field == null || !sprites.containsKey(field)) return scriptStopped;
        for (TreeMap<Integer, Placed> fr : sprites.get(field).frames) {
            for (Placed skin : fr.values()) {
                if (!"skin".equals(skin.name) || !sprites.containsKey(skin.id)) continue;
                for (TreeMap<Integer, Placed> sf : sprites.get(skin.id).frames) {
                    for (Placed sub : sf.values()) if ("sub".equals(sub.name)) scriptStopped.add(sub.id);
                }
            }
        }
        return scriptStopped;
    }

    static Matrix compose(Matrix p, Matrix g) {
        Matrix m = new Matrix();
        m.a = p.a * g.a + p.c * g.b;
        m.b = p.b * g.a + p.d * g.b;
        m.c = p.a * g.c + p.c * g.d;
        m.d = p.b * g.c + p.d * g.d;
        m.tx = p.a * g.tx + p.c * g.ty + p.tx;
        m.ty = p.b * g.tx + p.d * g.ty + p.ty;
        return m;
    }

    Set<Integer> loopClipIds() {
        Set<Integer> out = new HashSet<>();
        for (String name : LOOP_CLIPS) {
            Integer id = exports.get(name);
            if (id != null) out.add(id);
        }
        for (Map.Entry<String, Integer> e : exports.entrySet()) {
            if (hasLayers(e.getKey())) out.add(e.getValue()); // ennemis, objets, décors : animations qui jouent seules
        }
        return out;
    }

    /** Symboles dont les images figées peuvent contenir des couches animées : ennemis, et objets (brioche dorée…). */
    static final String LAYER_PREFIX = "hammer_bad_";
    // hammer_pod : socles des téléporteurs, boucle 1629 (éteint) ou 1633 (allumé) par-dessus la partie fixe
    static final Set<String> LAYER_SYMBOLS = Set.of("hammer_item_score", "hammer_item_special", "hammer_pod");

    static boolean hasLayers(String symbol) {
        return symbol.startsWith(LAYER_PREFIX) || LAYER_SYMBOLS.contains(symbol) || decorNames().contains(symbol);
    }

    private static Set<String> decorNames;

    /**
     * Décors posés par les scripts de niveau (e_mc / e_pmc, attribut n) : leurs clips qui jouent seuls sont animés
     * comme ceux des ennemis. Niveaux lus dans le dossier de la propriété système hammerfest.levels.
     */
    static Set<String> decorNames() {
        if (decorNames != null) return decorNames;
        decorNames = new HashSet<>();
        String dir = System.getProperty("hammerfest.levels");
        File[] files = dir == null ? null : new File(dir).listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return decorNames;
        // dans le JSON, les guillemets du script sont échappés : n=\"explosion\"
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<e_p?mc\\b[^>]*?\\bn=\\\\?\"([^\"\\\\]+)");
        for (File f : files) {
            try {
                java.util.regex.Matcher m = p.matcher(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                while (m.find()) decorNames.add(m.group(1));
            } catch (java.io.IOException ignored) {
                // niveau illisible : ignoré
            }
        }
        return decorNames;
    }

    /** Couche animée trouvée dans une image figée. */
    static final class LayerSpec {
        int id;
        Matrix m;
        boolean under;
        /** Enfant affiché à la demande (balle de la fraise) : nom de la partie, null = couche toujours dessinée. */
        String part;
        /** Teinte en aplat (multiplicateurs de couleur à 0) : couleur 0xRRGGBB et opacité, -1 = aucune. */
        int tintRgb = -1;
        float tintAlpha = 1;
    }

    /**
     * Rayons d'arme (« field », habillages 1 à 5) : deux instances du trait animé 3148 (halo élargi à 50 %, cœur) en
     * aplat de la couleur de l'arme (CXFORM : multiplicateurs 0, ajouts = couleur). L'image d'origine n'a rien d'autre :
     * elle est vidée à l'assemblage (Clip.blankFrames) et entièrement redessinée par ces couches teintées, qui jouent
     * en boucle (le skin.sub.stop() d'origine ne les concerne pas, elles ne s'appellent pas « sub »).
     */
    static final List<String> COLOR_LAYER_SYMBOLS = List.of("field");

    /** Clip → images (1..n) vidées à l'assemblage car entièrement dessinées par des couches teintées. */
    final Map<Integer, Set<Integer>> blankFrames = new TreeMap<>();

    void colorLayers(Map<Integer, Map<Integer, List<LayerSpec>>> out) {
        for (String sym : COLOR_LAYER_SYMBOLS) {
            Integer sid = exports.get(sym);
            if (sid == null || !sprites.containsKey(sid)) continue;
            Set<Integer> skins = new TreeSet<>();
            for (TreeMap<Integer, Placed> fr : sprites.get(sid).frames) {
                for (Placed p : fr.values()) if ("skin".equals(p.name) && sprites.containsKey(p.id)) skins.add(p.id);
            }
            for (int skinId : skins) {
                List<TreeMap<Integer, Placed>> frames = sprites.get(skinId).frames;
                for (int f = 0; f < frames.size(); f++) {
                    TreeMap<Integer, Placed> fr = frames.get(f);
                    if (fr.isEmpty()) continue;
                    boolean flat = true;
                    for (Placed p : fr.values()) {
                        int[] c = p.color;
                        if (c == null || c[0] != 0 || c[1] != 0 || c[2] != 0 || !playsAlone(p.id)) flat = false;
                    }
                    if (!flat) continue;
                    List<LayerSpec> l = new ArrayList<>();
                    for (Placed p : fr.values()) {
                        LayerSpec s = new LayerSpec();
                        s.id = p.id;
                        s.m = p.matrix;
                        s.tintRgb = (clamp255(p.color[4]) << 16) | (clamp255(p.color[5]) << 8) | clamp255(p.color[6]);
                        s.tintAlpha = Math.max(0, Math.min(1, p.color[3] / 256f + p.color[7] / 255f));
                        l.add(s);
                    }
                    out.computeIfAbsent(skinId, k -> new TreeMap<>()).put(f, l);
                    blankFrames.computeIfAbsent(skinId, k -> new TreeSet<>()).add(f + 1);
                }
            }
        }
    }

    static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * Couches « à la demande » : enfants des sous-clips retirés des images par FreezeNested.HIDDEN_IN_SUBS (balle de la
     * fraise), placés image par image d'après l'XML d'origine ; le jeu ne les dessine que si l'entité le demande.
     */
    void toggleLayers(Map<Integer, Map<Integer, List<LayerSpec>>> out) {
        SwfLayout src = partsSource != null ? partsSource : this;
        for (Map.Entry<String, List<String>> e : FreezeNested.HIDDEN_IN_SUBS.entrySet()) {
            Integer sid = src.exports.get(e.getKey());
            if (sid == null || !src.sprites.containsKey(sid)) continue;
            for (int subId : FreezeNested.subClips(src, sid)) {
                List<TreeMap<Integer, Placed>> frames = src.sprites.get(subId).frames;
                for (int f = 0; f < frames.size(); f++) {
                    int minOther = Integer.MAX_VALUE;
                    for (Map.Entry<Integer, Placed> d : frames.get(f).entrySet()) {
                        String n = d.getValue().name;
                        if (n == null || !e.getValue().contains(n)) minOther = Math.min(minOther, d.getKey());
                    }
                    for (Map.Entry<Integer, Placed> d : frames.get(f).entrySet()) {
                        Placed p = d.getValue();
                        if (p.name == null || !e.getValue().contains(p.name) || !sprites.containsKey(p.id)) continue;
                        LayerSpec l = new LayerSpec();
                        l.id = p.id;
                        l.m = p.matrix;
                        l.under = d.getKey() < minOther;
                        l.part = p.name;
                        out.computeIfAbsent(subId, k -> new TreeMap<>()).computeIfAbsent(f, k -> new ArrayList<>()).add(l);
                    }
                }
            }
        }
    }

    /** Clip animé qui joue seul (plusieurs images, pas de stop() d'entrée). */
    boolean playsAlone(int id) {
        Sprite s = sprites.get(id);
        return s != null && s.frames.size() > 1 && !s.stoppedAtStart;
    }

    /** Couches animées d'une image (enfants directs, ou dans un enfant fixe à une image) ; vide s'il n'y en a pas. */
    List<LayerSpec> layersOf(TreeMap<Integer, Placed> frame) {
        return layersOf(frame, false);
    }

    /** @param skipSub le sous-clip « sub » est arrêté par le script : il reste dans l'image fixe */
    List<LayerSpec> layersOf(TreeMap<Integer, Placed> frame, boolean skipSub) {
        List<LayerSpec> out = new ArrayList<>();
        int minStatic = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Placed> d : frame.entrySet()) {
            Placed p = d.getValue();
            if (playsAlone(p.id) && !(skipSub && "sub".equals(p.name))) continue;
            minStatic = Math.min(minStatic, d.getKey());
        }
        for (Map.Entry<Integer, Placed> d : frame.entrySet()) {
            Placed p = d.getValue();
            if (skipSub && "sub".equals(p.name)) continue; // arrêté : reste dans l'image fixe
            if (playsAlone(p.id)) {
                LayerSpec l = new LayerSpec();
                l.id = p.id;
                l.m = p.matrix;
                l.under = d.getKey() < minStatic;
                out.add(l);
            } else {
                nestedLayers(p.id, p.matrix, d.getKey() < minStatic, 1, out);
            }
        }
        return out;
    }

    /** Couches animées dans un enfant fixe à une image, jusqu'à 4 niveaux (matrices composées). */
    private void nestedLayers(int id, Matrix m, boolean under, int depth, List<LayerSpec> out) {
        Sprite s = sprites.get(id);
        if (s == null || s.frames.size() != 1 || depth > 4) return;
        for (Placed q : s.frames.get(0).values()) {
            Matrix qm = compose(m, q.matrix);
            if (playsAlone(q.id)) {
                LayerSpec l = new LayerSpec();
                l.id = q.id;
                l.m = qm;
                l.under = under;
                out.add(l);
            } else {
                nestedLayers(q.id, qm, under, depth + 1, out);
            }
        }
    }

    /**
     * Couches animées des images figées des ennemis : clip → (image → couches). Image figée = état sans sous-clip
     * « sub » ni clip en boucle, ou sous-clip « sub » à une seule image.
     */
    Map<Integer, Map<Integer, List<LayerSpec>>> enemyLayers() {
        Map<Integer, Map<Integer, List<LayerSpec>>> out = new TreeMap<>();
        for (Map.Entry<String, Integer> e : exports.entrySet()) {
            if (!hasLayers(e.getKey()) || !sprites.containsKey(e.getValue())) continue;
            int sid = e.getValue();
            List<TreeMap<Integer, Placed>> frames = sprites.get(sid).frames;
            for (int f = 0; f < frames.size(); f++) {
                TreeMap<Integer, Placed> frame = frames.get(f);
                Placed sub = null;
                for (Placed p : frame.values()) if ("sub".equals(p.name) && sprites.containsKey(p.id)) sub = p;
                int target = sid, tf = f;
                if (decorNames().contains(e.getKey())) {
                    // décor : dessiné image par image (sans état) ; son sub est arrêté par le script et reste dans
                    // l'image fixe, seuls les autres enfants qui jouent seuls deviennent des couches
                    List<LayerSpec> l = layersOf(frame, true);
                    if (!l.isEmpty()) out.computeIfAbsent(sid, k -> new TreeMap<>()).put(f, l);
                    continue;
                }
                if (sub != null) {
                    if (sprites.get(sub.id).frames.size() != 1 || wrappedLoop(sub.id) != null) continue;
                    target = sub.id;
                    tf = 0;
                } else if (loopChild(frame) != null) {
                    continue;
                }
                List<LayerSpec> l = layersOf(sprites.get(target).frames.get(tf));
                if (!l.isEmpty()) out.computeIfAbsent(target, k -> new TreeMap<>()).put(tf, l);
            }
        }
        return out;
    }

    /** Enfant unique de l'image, clip de plusieurs images non arrêté par script ; null sinon. */
    Placed loopChild(TreeMap<Integer, Placed> frame) {
        if (frame.size() != 1) return null;
        Placed p = frame.values().iterator().next();
        Sprite child = sprites.get(p.id);
        if (child == null || child.frames.size() <= 1 || child.stoppedAtStart) return null;
        return p;
    }

    SpriteLibrary build() {
        SpriteLibrary lib = new SpriteLibrary();
        Set<Integer> reachable = new HashSet<>();
        Deque<Integer> todo = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : exports.entrySet()) {
            if (onlySymbol != null && !onlySymbol.equals(e.getKey())) continue;
            if (sprites.containsKey(e.getValue())) {
                lib.symbols.put(onlySymbol != null && renameTo != null ? renameTo : e.getKey(), e.getValue());
                todo.add(e.getValue());
            }
        }
        Map<Integer, String> childName = new HashMap<>();
        for (Map.Entry<String, String> e : CHILD_NAMES.entrySet()) {
            Integer id = exports.get(e.getKey());
            if (id != null) childName.put(id, e.getValue());
        }
        // Parties nommées (corps et yeux de la boule de feu…) : placement de la 1re image
        SwfLayout src = partsSource != null ? partsSource : this;
        for (Map.Entry<String, List<String>> e : PARTS.entrySet()) {
            Integer sid = src.exports.get(e.getKey());
            if (sid == null || !lib.symbols.containsKey(e.getKey()) || !src.sprites.containsKey(sid)) continue;
            List<TreeMap<Integer, Placed>> sframes = src.sprites.get(sid).frames;
            for (int fi = 0; fi < sframes.size(); fi++)
            for (Map.Entry<Integer, Placed> d : sframes.get(fi).entrySet()) {
                Placed p = d.getValue();
                String key = partKey(e.getValue(), p.name, d.getKey());
                if (key == null || !sprites.containsKey(p.id)) continue;
                if (fi > 0) key += "#" + (fi + 1); // placement à l'image fi + 1
                SpriteLibrary.Placement pl = new SpriteLibrary.Placement();
                pl.id = p.id;
                pl.a = (float) p.matrix.a;
                pl.b = (float) p.matrix.b;
                pl.c = (float) p.matrix.c;
                pl.d = (float) p.matrix.d;
                pl.tx = (float) (p.matrix.tx / TWIPS);
                pl.ty = (float) (p.matrix.ty / TWIPS);
                lib.parts.put(e.getKey() + "/" + key, pl);
                todo.add(p.id);
            }
        }
        // Clips atteignables par les sous-clips "sub" (ceux dont on lit l'animation).
        Set<Integer> loopers = loopClipIds();
        // Clips joués en boucle par un parent de LOOP_CLIPS : leurs propres enfants animés jouent aussi tout seuls
        // (pièce qui rebondit une fois puis tourne sur elle-même : 2905 s'arrête, son enfant 2904 continue)
        Set<Integer> loopTargets = new HashSet<>();
        Map<Integer, Map<Integer, List<LayerSpec>>> layers = onlySymbol == null ? enemyLayers() : Map.of();
        if (onlySymbol == null) {
            toggleLayers(layers);
            colorLayers(layers);
        }
        for (Map<Integer, List<LayerSpec>> m : layers.values()) for (List<LayerSpec> l : m.values()) for (LayerSpec s : l) todo.add(s.id);
        while (!todo.isEmpty()) {
            int id = todo.poll();
            if (!reachable.add(id)) continue;
            String name = childName.getOrDefault(id, "sub");
            for (TreeMap<Integer, Placed> frame : sprites.get(id).frames) {
                for (Placed p : frame.values()) {
                    if (name.equals(p.name) && sprites.containsKey(p.id)) {
                        todo.add(p.id);
                        Placed w = wrappedLoop(p.id);
                        if (w != null) todo.add(w.id);
                        Placed on = wrappedLoopOn(p.id);
                        if (on != null) todo.add(on.id);
                    }
                }
                if (loopers.contains(id) || loopTargets.contains(id)) {
                    Placed loop = loopChild(frame);
                    if (loop != null) {
                        todo.add(loop.id);
                        loopTargets.add(loop.id);
                    }
                }
            }
        }
        for (int id : reachable) {
            Sprite s = sprites.get(id);
            SpriteLibrary.Clip clip = new SpriteLibrary.Clip();
            clip.frames = s.frames.size();
            clip.stopFrame = s.stopFrame;
            clip.stopFrames = new ArrayList<>(s.stopFrames);
            clip.removeFrame = s.removeFrame;
            clip.playOnAttach = s.playOnAttach;
            if (blankFrames.containsKey(id)) clip.blankFrames = new ArrayList<>(blankFrames.get(id));
            clip.loopLength = s.jumpFrame > 0 ? s.jumpFrame - 1 : 0;
            if (s.jumpLabel != null && s.labels.containsKey(s.jumpLabel)) clip.loopStart = s.labels.get(s.jumpLabel);
            Rect r = bounds(id);
            if (!r.isEmpty()) {
                clip.x = (float) (r.xMin / TWIPS);
                clip.y = (float) (r.yMin / TWIPS);
                clip.w = (float) ((r.xMax - r.xMin) / TWIPS);
                clip.h = (float) ((r.yMax - r.yMin) / TWIPS);
            }
            boolean anySub = false;
            String subName = childName.getOrDefault(id, "sub");
            List<SpriteLibrary.Placement> subs = new ArrayList<>();
            for (TreeMap<Integer, Placed> frame : s.frames) {
                SpriteLibrary.Placement pl = null;
                for (Placed p : frame.values()) {
                    if (subName.equals(p.name) && sprites.containsKey(p.id)) {
                        pl = new SpriteLibrary.Placement();
                        Placed w = wrappedLoop(p.id);
                        Matrix m = w == null ? p.matrix : compose(p.matrix, w.matrix);
                        pl.id = w == null ? p.id : w.id;
                        pl.loop = w != null;
                        if (w != null) nestedLoops.put(id, p.id);
                        Placed on = wrappedLoopOn(p.id);
                        if (on != null) {
                            Matrix om = compose(p.matrix, on.matrix);
                            pl.alt = new SpriteLibrary.Placement();
                            pl.alt.id = on.id;
                            pl.alt.loop = true;
                            pl.alt.a = (float) om.a;
                            pl.alt.b = (float) om.b;
                            pl.alt.c = (float) om.c;
                            pl.alt.d = (float) om.d;
                            pl.alt.tx = (float) (om.tx / TWIPS);
                            pl.alt.ty = (float) (om.ty / TWIPS);
                        }
                        pl.a = (float) m.a;
                        pl.b = (float) m.b;
                        pl.c = (float) m.c;
                        pl.d = (float) m.d;
                        pl.tx = (float) (m.tx / TWIPS);
                        pl.ty = (float) (m.ty / TWIPS);
                        anySub = true;
                        break;
                    }
                }
                if (pl == null && (loopers.contains(id) || loopTargets.contains(id))) {
                    Placed loop = loopChild(frame);
                    if (loop != null) { // clip animé qui joue tout seul (pièce qui rebondit…)
                        pl = new SpriteLibrary.Placement();
                        pl.id = loop.id;
                        pl.a = (float) loop.matrix.a;
                        pl.b = (float) loop.matrix.b;
                        pl.c = (float) loop.matrix.c;
                        pl.d = (float) loop.matrix.d;
                        pl.tx = (float) (loop.matrix.tx / TWIPS);
                        pl.ty = (float) (loop.matrix.ty / TWIPS);
                        pl.loop = true;
                        anySub = true;
                    }
                }
                subs.add(pl);
            }
            if (anySub) clip.subs = subs;
            else clip.subs = new ArrayList<>();
            Map<Integer, List<LayerSpec>> ls = layers.get(id);
            if (ls != null) {
                clip.layers = new ArrayList<>();
                for (int f = 0; f < clip.frames; f++) {
                    List<LayerSpec> l = ls.get(f);
                    if (l == null) {
                        clip.layers.add(null);
                        continue;
                    }
                    List<SpriteLibrary.Placement> pl = new ArrayList<>();
                    for (LayerSpec ly : l) {
                        SpriteLibrary.Placement p = new SpriteLibrary.Placement();
                        p.id = ly.id;
                        p.a = (float) ly.m.a;
                        p.b = (float) ly.m.b;
                        p.c = (float) ly.m.c;
                        p.d = (float) ly.m.d;
                        p.tx = (float) (ly.m.tx / TWIPS);
                        p.ty = (float) (ly.m.ty / TWIPS);
                        p.loop = true;
                        p.under = ly.under;
                        p.part = ly.part;
                        if (ly.tintRgb >= 0) { // couche teintée (rayons d'arme)
                            p.tintRgb = ly.tintRgb;
                            p.tintAlpha = ly.tintAlpha;
                        }
                        pl.add(p);
                    }
                    clip.layers.add(pl);
                }
                layerCount += ls.size();
            }
            lib.clips.put(id, clip);
        }
        return lib;
    }

    /** Compare le cadre calculé à la taille des PNG exportés par FFDec ; renvoie le nombre de clips vérifiés. */
    int verify(SpriteLibrary lib, File pngDir) throws Exception {
        if (!pngDir.isDirectory()) return 0;
        Map<Integer, File> dirs = new HashMap<>();
        File[] all = pngDir.listFiles(File::isDirectory);
        if (all != null) {
            for (File d : all) {
                String[] parts = d.getName().split("_");
                if (parts.length >= 2 && parts[0].equals("DefineSprite")) dirs.put(Integer.parseInt(parts[1]), d);
            }
        }
        int checked = 0, bad = 0;
        for (Map.Entry<Integer, SpriteLibrary.Clip> e : lib.clips.entrySet()) {
            File dir = dirs.get(e.getKey());
            if (dir == null) continue;
            File png = new File(dir, "1.png");
            if (!png.exists()) continue;
            BufferedImage img = ImageIO.read(png);
            checked++;
            SpriteLibrary.Clip c = e.getValue();
            if (Math.abs(img.getWidth() - c.w) > 2 || Math.abs(img.getHeight() - c.h) > 2) {
                bad++;
                if (bad <= 15) {
                    System.out.printf("  écart clip %d : png %dx%d, calculé %.1fx%.1f (recalé)%n",
                            e.getKey(), img.getWidth(), img.getHeight(), c.w, c.h);
                }
            }
            // Le PNG fait foi : marge des filtres (glow/blur) répartie de part et d'autre du cadre calculé.
            c.x -= (img.getWidth() - c.w) / 2f;
            c.y -= (img.getHeight() - c.h) / 2f;
            c.w = img.getWidth();
            c.h = img.getHeight();
        }
        System.out.println("  vérification : " + (checked - bad) + "/" + checked + " cadres conformes (±2 px)");
        return checked;
    }

    // ------------------------------------------------------------------ utilitaires

    static String stripDollar(String s) {
        return s.startsWith("$") ? s.substring(1) : s;
    }

    static int intAttr(XMLStreamReader r, String name) {
        return Integer.parseInt(r.getAttributeValue(null, name));
    }

    static int intAttrOr(XMLStreamReader r, String name, int def) {
        String v = r.getAttributeValue(null, name);
        return v == null ? def : Integer.parseInt(v);
    }

    static double dblAttr(XMLStreamReader r, String name) {
        String v = r.getAttributeValue(null, name);
        return v == null ? 0 : Double.parseDouble(v);
    }
}
