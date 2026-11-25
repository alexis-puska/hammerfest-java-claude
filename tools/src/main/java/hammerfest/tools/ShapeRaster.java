package hammerfest.tools;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dessin Java2D d'une forme SWF (DefineShape de l'XML de FFDec, {@code -swf2xml}) : remplissages unis, dégradés
 * linéaires (type 16) et radiaux (18) ; ni traits ni bitmaps (non gérés : forme ignorée en partie). Sert aux formes
 * que le jeu masque ou découpe par script et que FFDec n'exporte qu'assemblées dans leur clip.
 *
 * <p>Coordonnées en twips. Chaque remplissage est reconstitué en chaînant ses bords orientés (fillStyle1 tel quel,
 * fillStyle0 inversé) ; un StyleChangeRecord à nouveaux styles ouvre un calque dessiné par-dessus le précédent.</p>
 */
final class ShapeRaster {

    private record Edge(int x0, int y0, int cx, int cy, int x1, int y1, boolean curve) {
        Edge reversed() {
            return new Edge(x1, y1, cx, cy, x0, y0, curve);
        }
    }

    private static final class Layer {
        final List<Paint> fills = new ArrayList<>();
        final Map<Integer, List<Edge>> edges = new HashMap<>(); // indice de remplissage (1..n) → bords orientés
    }

    private final List<Layer> layers = new ArrayList<>();

    /** {@code shape} : élément {@code <item type="DefineShape…Tag">} de l'XML de FFDec. */
    ShapeRaster(Element shape) {
        Element shapes = child(shape, "shapes");
        Layer layer = new Layer();
        readFills(child(shapes, "fillStyles"), layer);
        layers.add(layer);
        int x = 0, y = 0, f0 = 0, f1 = 0;
        for (Element r : items(child(shapes, "shapeRecords"))) {
            switch (r.getAttribute("type")) {
                case "StyleChangeRecord" -> {
                    if ("true".equals(r.getAttribute("stateNewStyles"))) {
                        layer = new Layer();
                        readFills(child(r, "fillStyles"), layer);
                        layers.add(layer);
                    }
                    if ("true".equals(r.getAttribute("stateMoveTo"))) {
                        x = intAttr(r, "moveDeltaX");
                        y = intAttr(r, "moveDeltaY");
                    }
                    if ("true".equals(r.getAttribute("stateFillStyle0"))) f0 = intAttr(r, "fillStyle0");
                    if ("true".equals(r.getAttribute("stateFillStyle1"))) f1 = intAttr(r, "fillStyle1");
                }
                case "StraightEdgeRecord" -> {
                    boolean general = "true".equals(r.getAttribute("generalLineFlag"));
                    boolean vert = "true".equals(r.getAttribute("vertLineFlag"));
                    int dx = general || !vert ? intAttr(r, "deltaX") : 0;
                    int dy = general || vert ? intAttr(r, "deltaY") : 0;
                    add(layer, new Edge(x, y, 0, 0, x + dx, y + dy, false), f0, f1);
                    x += dx;
                    y += dy;
                }
                case "CurvedEdgeRecord" -> {
                    int cx = x + intAttr(r, "controlDeltaX"), cy = y + intAttr(r, "controlDeltaY");
                    int ax = cx + intAttr(r, "anchorDeltaX"), ay = cy + intAttr(r, "anchorDeltaY");
                    add(layer, new Edge(x, y, cx, cy, ax, ay, true), f0, f1);
                    x = ax;
                    y = ay;
                }
                default -> {
                }
            }
        }
    }

    private static void add(Layer l, Edge e, int f0, int f1) {
        if (f1 > 0) l.edges.computeIfAbsent(f1, k -> new ArrayList<>()).add(e);
        if (f0 > 0) l.edges.computeIfAbsent(f0, k -> new ArrayList<>()).add(e.reversed());
    }

    /** Dessine la forme ; {@code g} doit déjà porter la transformation twips → pixels. */
    void draw(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        for (Layer l : layers) {
            for (int f = 1; f <= l.fills.size(); f++) {
                List<Edge> es = l.edges.get(f);
                if (es == null || l.fills.get(f - 1) == null) continue;
                g.setPaint(l.fills.get(f - 1));
                g.fill(path(es));
            }
        }
    }

    /** Bords chaînés en boucles fermées (extrémités exactes en twips). */
    private static Path2D path(List<Edge> edges) {
        Map<Long, List<Edge>> byStart = new HashMap<>();
        for (Edge e : edges) byStart.computeIfAbsent(key(e.x0, e.y0), k -> new ArrayList<>()).add(e);
        Path2D.Double p = new Path2D.Double(Path2D.WIND_NON_ZERO);
        for (Edge first : edges) {
            List<Edge> bucket = byStart.get(key(first.x0, first.y0));
            if (!bucket.remove(first)) continue; // déjà pris dans une boucle
            p.moveTo(first.x0, first.y0);
            Edge e = first;
            while (true) {
                if (e.curve) p.quadTo(e.cx, e.cy, e.x1, e.y1);
                else p.lineTo(e.x1, e.y1);
                if (e.x1 == first.x0 && e.y1 == first.y0) break;
                List<Edge> next = byStart.get(key(e.x1, e.y1));
                if (next == null || next.isEmpty()) break;
                e = next.remove(0);
            }
            p.closePath();
        }
        return p;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    private static void readFills(Element wrapper, Layer l) {
        if (wrapper == null) return;
        Element arr = child(wrapper, "fillStyles");
        if (arr == null) return;
        for (Element fs : items(arr)) l.fills.add(paint(fs));
    }

    private static Paint paint(Element fs) {
        int type = intAttr(fs, "fillStyleType");
        if (type == 0) return color(child(fs, "color"));
        if (type != 16 && type != 18) return null; // bitmaps, dégradé à foyer : non gérés
        Element m = child(fs, "gradientMatrix");
        boolean scaled = "true".equals(m.getAttribute("hasScale"));
        AffineTransform t = new AffineTransform(scaled ? dblAttr(m, "scaleX") : 1, dblAttr(m, "rotateSkew0"),
                dblAttr(m, "rotateSkew1"), scaled ? dblAttr(m, "scaleY") : 1, intAttr(m, "translateX"),
                intAttr(m, "translateY"));
        List<Element> recs = items(child(child(fs, "gradient"), "gradientRecords"));
        float[] fr = new float[recs.size()];
        Color[] cs = new Color[recs.size()];
        for (int i = 0; i < fr.length; i++) {
            fr[i] = intAttr(recs.get(i), "ratio") / 255f;
            if (i > 0 && fr[i] <= fr[i - 1]) fr[i] = Math.min(1f, fr[i - 1] + 1e-4f);
            cs[i] = color(child(recs.get(i), "color"));
        }
        if (fr.length == 1) return cs[0];
        if (type == 16) {
            return new java.awt.LinearGradientPaint(new Point2D.Double(-16384, 0), new Point2D.Double(16384, 0), fr, cs,
                    MultipleGradientPaint.CycleMethod.NO_CYCLE, MultipleGradientPaint.ColorSpaceType.SRGB, t);
        }
        return new java.awt.RadialGradientPaint(new Point2D.Double(0, 0), 16384, new Point2D.Double(0, 0), fr, cs,
                MultipleGradientPaint.CycleMethod.NO_CYCLE, MultipleGradientPaint.ColorSpaceType.SRGB, t);
    }

    private static Color color(Element c) {
        int a = c.hasAttribute("alpha") ? intAttr(c, "alpha") : 255;
        return new Color(intAttr(c, "red"), intAttr(c, "green"), intAttr(c, "blue"), a);
    }

    static Element child(Element e, String name) {
        if (e == null) return null;
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && ((Element) n).getTagName().equals(name)) return (Element) n;
        }
        return null;
    }

    static List<Element> items(Element e) {
        List<Element> out = new ArrayList<>();
        if (e == null) return out;
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && ((Element) n).getTagName().equals("item")) out.add((Element) n);
        }
        return out;
    }

    static int intAttr(Element e, String name) {
        String v = e.getAttribute(name);
        return v.isEmpty() ? 0 : Integer.parseInt(v);
    }

    static double dblAttr(Element e, String name) {
        String v = e.getAttribute(name);
        return v.isEmpty() ? 0 : Double.parseDouble(v);
    }
}
