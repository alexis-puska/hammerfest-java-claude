package hammerfest.tools;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Couches de la barre de vie de Tuberculoz (symbole « hammer_interf_boss_bar », clip 350 de gfx), dessinées depuis
 * les formes de l'XML de FFDec : FFDec n'exporte que le clip assemblé, barre pleine, alors que le jeu découpe le
 * liquide rouge (masque « bar », {@code bar._xscale = vies / LIVES × 100}) et fait clignoter le tube (« barFade »).
 *
 * <ul>
 *   <li>{@code back.png} : socle de rochers (forme 341, profondeur 5) ;</li>
 *   <li>{@code tube.png} : intérieur du tube en blanc (forme 342, masque de barFade), pour l'éclair de la vie perdue ;</li>
 *   <li>{@code red.png} : liquide rouge et son reflet (forme 348, profondeur 13, masquée par « bar ») ;</li>
 *   <li>{@code front.png} : bulbe et reflets du verre (forme 349, profondeur 18).</li>
 * </ul>
 * Toutes cadrées sur le même rectangle du clip : origine ({@link #OX}, {@link #OY}) px, {@link #W} × {@link #H}.
 *
 * <p>Usage : BossBar &lt;gfx.xml&gt; &lt;assets/images/bossbar&gt; [aperçu.png]</p>
 */
public final class BossBar {

    /** Coin haut gauche du cadrage dans le repère du clip (px) et taille : bornes de la forme 341 (socle). */
    static final int OX = -143, OY = -9, W = 276, H = 27;
    static final int BACK = 341, TUBE = 342, RED = 348, FRONT = 349;

    public static void main(String[] args) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        Document doc = f.newDocumentBuilder().parse(new File(args[0]));
        Map<Integer, Element> shapes = new HashMap<>();
        NodeList items = doc.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Element e = (Element) items.item(i);
            if (e.getAttribute("type").startsWith("DefineShape") && e.hasAttribute("shapeId")) {
                shapes.put(Integer.parseInt(e.getAttribute("shapeId")), e);
            }
        }
        File out = new File(args[1]);
        out.mkdirs();
        BufferedImage back = render(shapes.get(BACK)), red = render(shapes.get(RED)), front = render(shapes.get(FRONT));
        BufferedImage tube = render(shapes.get(TUBE));
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) tube.setRGB(x, y, tube.getRGB(x, y) | 0x00ffffff);
        ImageIO.write(back, "png", new File(out, "back.png"));
        ImageIO.write(tube, "png", new File(out, "tube.png"));
        ImageIO.write(red, "png", new File(out, "red.png"));
        ImageIO.write(front, "png", new File(out, "front.png"));
        System.out.println("  barre de vie du boss : 4 couches écrites dans " + out);
        if (args.length > 2) { // aperçu barre pleine, à comparer à l'export FFDec du clip 350
            BufferedImage all = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = all.createGraphics();
            g.drawImage(back, 0, 0, null);
            int x0 = (int) Math.floor(-107.3 - OX), x1 = (int) Math.ceil(120.4 - OX); // masque « bar » à 100 %
            g.drawImage(red.getSubimage(x0, 0, x1 - x0, H), x0, 0, null);
            g.drawImage(front, 0, 0, null);
            g.dispose();
            File p = new File(args[2]);
            p.getParentFile().mkdirs();
            ImageIO.write(all, "png", p);
        }
    }

    static BufferedImage render(Element shape) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.translate(-OX, -OY);
        g.scale(1 / 20.0, 1 / 20.0);
        new ShapeRaster(shape).draw(g);
        g.dispose();
        return img;
    }

    private BossBar() {
    }
}
