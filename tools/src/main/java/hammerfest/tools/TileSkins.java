package hammerfest.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Écrit les textures de dalles ({@code images/tiles/tile_NN.png} et {@code end_NN.png}) depuis l'export FFDec du
 * symbole « tile » de gfx : clip « skin » (573, une image par habillage, 63 en tout) et « endTile » (699). Le jeu
 * choisit l'habillage par {@code skin.gotoAndStop("" + id)} : sans étiquette, c'est l'image n° id. Les PNG fournis
 * avec les sources d'origine sautent les images 20, 21, 41 et 42 et sont renumérotés à partir de là (tile_54 =
 * image 58) : on ne s'en sert pas. Cadrage identique aux textures d'origine :
 * dalle 395×20 à (5, 5), embout 5×20 à (7, 5) dans les images exportées (marge de 5 px de FFDec : la forme
 * d'origine occupe y = 0 à 20 ; un cadrage à y = 6 décalait les dalles d'1 px vers le haut avec une ligne vide en
 * bas, d'où des colonnes « décollées » des plateformes).
 *
 * <p>Usage : TileSkins &lt;dossier export gfx&gt; &lt;assets/images/tiles&gt;</p>
 */
public final class TileSkins {

    static final int SKIN_CLIP = 573, END_CLIP = 699;

    public static void main(String[] args) throws Exception {
        File gfx = new File(args[0]), out = new File(args[1]);
        File skins = new File(gfx, "DefineSprite_" + SKIN_CLIP), ends = new File(gfx, "DefineSprite_" + END_CLIP);
        int written = 0;
        for (int n = 1; ; n++) {
            File s = new File(skins, n + ".png"), e = new File(ends, n + ".png");
            if (!s.exists()) break;
            String nn = String.format("%02d", n);
            File tile = new File(out, "tile_" + nn + ".png"), end = new File(out, "end_" + nn + ".png");
            {
                ImageIO.write(crop(ImageIO.read(s), 5, 5, 395, 20), "png", tile);
                written++;
            }
            if (e.exists()) {
                ImageIO.write(crop(ImageIO.read(e), 7, 5, 5, 20), "png", end);
                written++;
            }
        }
        System.out.println("  " + written + " textures de dalles écrites dans " + out);
    }

    static BufferedImage crop(BufferedImage img, int x, int y, int w, int h) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++)
                if (x + i < img.getWidth() && y + j < img.getHeight()) o.setRGB(i, j, img.getRGB(x + i, y + j));
        return o;
    }

    private TileSkins() {
    }
}
