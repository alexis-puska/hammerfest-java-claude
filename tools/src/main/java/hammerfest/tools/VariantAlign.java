package hammerfest.tools;

import hammerfest.data.SpriteLibrary;
import hammerfest.json.Jsons;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Recale les images d'une variante du joueur (Sandy, tenues de foot) sur celles d'Igor : seuls la tête et les
 * couleurs changent, le corps et les pieds sont au même endroit. Pour chaque clip, on cherche le décalage qui
 * superpose au mieux la moitié basse (silhouette) des images de la variante à celles d'Igor, dont l'ancrage est
 * juste, et on en déduit l'ancrage de la variante. Plus fiable que le cadre calculé, que les têtes faites de
 * plusieurs pièces faussent.
 *
 * <p>Usage : VariantAlign &lt;variante.json&gt; &lt;pngVariante&gt; &lt;base.json&gt; &lt;pngBase&gt;</p>
 */
public final class VariantAlign {

    static final int SEARCH = 24;

    public static void main(String[] args) throws Exception {
        File variantJson = new File(args[0]);
        SpriteLibrary variant = read(variantJson), base = read(new File(args[2]));
        Map<Integer, File> vDirs = dirs(new File(args[1])), bDirs = dirs(new File(args[3]));
        int done = 0;
        for (Map.Entry<Integer, SpriteLibrary.Clip> e : variant.clips.entrySet()) {
            SpriteLibrary.Clip vc = e.getValue(), bc = base.clips.get(e.getKey());
            File vd = vDirs.get(e.getKey()), bd = bDirs.get(e.getKey());
            if (bc == null || vd == null || bd == null) continue;
            List<Integer> dus = new ArrayList<>(), dvs = new ArrayList<>();
            int step = Math.max(1, vc.frames / 6);
            for (int f = 1; f <= vc.frames; f += step) {
                File vp = new File(vd, f + ".png"), bp = new File(bd, f + ".png");
                if (!vp.exists() || !bp.exists()) continue;
                int[] d = bestOffset(ImageIO.read(vp), ImageIO.read(bp));
                if (d != null) {
                    dus.add(d[0]);
                    dvs.add(d[1]);
                }
            }
            if (dus.isEmpty()) continue;
            Collections.sort(dus);
            Collections.sort(dvs);
            // pixel (u, v) de la variante = pixel (u + du, v + dv) d'Igor : vc.x + u = bc.x + u + du
            vc.x = bc.x + dus.get(dus.size() / 2);
            vc.y = bc.y + dvs.get(dvs.size() / 2);
            done++;
        }
        Files.write(variantJson.toPath(), Jsons.write(variant).getBytes(StandardCharsets.UTF_8));
        System.out.println("  " + done + " clips recalés sur ceux d'Igor -> " + variantJson.getName());
    }

    /**
     * Décalage (du, dv) qui superpose la moitié basse de la silhouette de {@code v} à celle de {@code b} :
     * maximise les pixels opaques communs moins les pixels opaques sans correspondant.
     */
    static int[] bestOffset(BufferedImage v, BufferedImage b) {
        boolean[][] vm = mask(v), bm = mask(b);
        int vh = v.getHeight(), vw = v.getWidth(), bh = b.getHeight(), bw = b.getWidth();
        int best = Integer.MIN_VALUE, bu = 0, bv = 0;
        for (int dv = -SEARCH; dv <= SEARCH; dv++) {
            for (int du = -SEARCH; du <= SEARCH; du++) {
                int score = 0;
                for (int y = vh / 2; y < vh; y++) { // moitié basse : corps et pieds, communs à tous les personnages
                    int by = y + dv;
                    for (int x = 0; x < vw; x++) {
                        if (!vm[y][x]) continue;
                        int bx = x + du;
                        boolean hit = by >= 0 && by < bh && bx >= 0 && bx < bw && bm[by][bx];
                        score += hit ? 1 : -1;
                    }
                }
                if (score > best) {
                    best = score;
                    bu = du;
                    bv = dv;
                }
            }
        }
        return best <= 0 ? null : new int[]{bu, bv};
    }

    static boolean[][] mask(BufferedImage img) {
        boolean[][] m = new boolean[img.getHeight()][img.getWidth()];
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) m[y][x] = ((img.getRGB(x, y) >>> 24) & 0xff) > 64;
        return m;
    }

    static Map<Integer, File> dirs(File root) {
        Map<Integer, File> out = new java.util.HashMap<>();
        File[] all = root.listFiles(File::isDirectory);
        if (all == null) return out;
        for (File d : all) {
            String[] parts = d.getName().split("_");
            if (parts.length >= 2 && parts[0].equals("DefineSprite")) out.put(Integer.parseInt(parts[1]), d);
        }
        return out;
    }

    static SpriteLibrary read(File f) throws Exception {
        return Jsons.read(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), SpriteLibrary.class);
    }

    private VariantAlign() {
    }
}
