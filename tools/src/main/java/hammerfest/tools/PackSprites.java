package hammerfest.tools;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import hammerfest.data.SpriteLibrary;
import hammerfest.json.Jsons;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

/**
 * Assemble en atlas libGDX les images utiles d'une bibliothèque de sprites :
 * les sous-clips "sub" (animations) et les clips sans sous-clip. Région {@code c<id>},
 * index = n° d'image - 1.
 *
 * <p>Usage : {@code PackSprites <lib.json> <dossier PNG FFDec> <dossier assets/sprites> <nom>}</p>
 */
public final class PackSprites {

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: PackSprites <lib.json> <pngDir> <outDir> <name>");
            System.exit(2);
        }
        SpriteLibrary lib = Jsons.read(new String(Files.readAllBytes(new File(args[0]).toPath()),
                StandardCharsets.UTF_8), SpriteLibrary.class);
        File pngDir = new File(args[1]);
        File outDir = new File(args[2]);
        String name = args[3];

        Set<Integer> needed = new HashSet<>();
        for (var e : lib.clips.entrySet()) {
            // Le clip lui-même est nécessaire dès qu'une de ses images n'a pas de sous-clip "sub"
            // (ex. objets : la plupart des images sont dessinées directement dans le clip).
            boolean missingSub = e.getValue().subs.isEmpty();
            for (SpriteLibrary.Placement p : e.getValue().subs) {
                if (p != null) needed.add(p.id);
                else missingSub = true;
            }
            if (missingSub) needed.add(e.getKey());
            if (e.getValue().layers != null) {
                for (java.util.List<SpriteLibrary.Placement> l : e.getValue().layers) if (l != null) for (SpriteLibrary.Placement p : l) needed.add(p.id);
            }
        }

        File staging = new File(pngDir.getParentFile(), name + "-staging");
        deleteRecursively(staging);
        staging.mkdirs();
        File[] dirs = pngDir.listFiles(File::isDirectory);
        int copied = 0;
        if (dirs != null) {
            for (File d : dirs) {
                String[] parts = d.getName().split("_");
                if (parts.length < 2 || !parts[0].equals("DefineSprite")) continue;
                int id = Integer.parseInt(parts[1]);
                if (!needed.contains(id)) continue;
                File[] frames = d.listFiles((dd, n) -> n.endsWith(".png"));
                if (frames == null) continue;
                for (File f : frames) {
                    int frame = Integer.parseInt(f.getName().replace(".png", ""));
                    File dest = new File(staging, "c" + id + "_" + (frame - 1) + ".png");
                    int[] cut = CUT_INNER.get(name + "/" + id);
                    SpriteLibrary.Clip owner = lib.clips.get(id);
                    java.util.List<SpriteLibrary.Placement> layerList = owner != null && owner.layers != null
                            && frame - 1 < owner.layers.size() ? owner.layers.get(frame - 1) : null;
                    if (layerList != null || cut != null) {
                        File src = f;
                        if (cut != null) { // lame de la scie, puis couches animées éventuelles
                            cutInner(f, new File(new File(pngDir, dirName(dirs, cut[0])), "1.png"), lib, id, cut[0], dest);
                            src = dest;
                        }
                        if (layerList != null) cutLayers(src, pngDir, dirs, lib, id, layerList, dest);
                    } else if (OVERLAY_GLOW.getOrDefault(name, Set.of()).contains(id)) {
                        removeOverlayGlow(f, dest);
                    } else {
                        Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    copied++;
                }
            }
        }
        System.out.println("  " + copied + " images (" + needed.size() + " clips) à assembler");

        TexturePacker.Settings s = new TexturePacker.Settings();
        s.maxWidth = 2048;
        s.maxHeight = 2048;
        s.paddingX = 2;
        s.paddingY = 2;
        s.duplicatePadding = true;
        s.edgePadding = true;
        s.stripWhitespaceX = true;
        s.stripWhitespaceY = true;
        s.filterMin = Texture.TextureFilter.Linear;
        s.filterMag = Texture.TextureFilter.Linear;
        s.combineSubdirectories = false;
        s.useIndexes = true;
        s.silent = true;
        outDir.mkdirs();
        for (File old : outDir.listFiles((d, n) -> n.startsWith(name) && (n.endsWith(".png") || n.endsWith(".atlas")))) {
            old.delete();
        }
        TexturePacker.process(s, staging.getAbsolutePath(), outDir.getAbsolutePath(), name);
        deleteRecursively(staging);
        System.out.println("  atlas écrit : " + new File(outDir, name + ".atlas"));
    }

    /**
     * Clips contenant un halo en mode de fusion « overlay » que FFDec rend en disque noir : torche (73) et sa
     * flamme (72), halo = clip 66 (cercles blancs à 10, 15 et 30 %). Le jeu dessine ce halo lui-même.
     */
    static final java.util.Map<String, Set<Integer>> OVERLAY_GLOW = java.util.Map.of("gfx", Set.of(72, 73));

    /** Retire le halo noir : pixels noirs purs peu opaques (au plus 31 %) rendus transparents. */
    static void removeOverlayGlow(File src, File dest) throws java.io.IOException {
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(src);
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(img.getWidth(), img.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int a = argb >>> 24;
                boolean glow = (argb & 0xffffff) == 0 && a > 0 && a <= 80;
                out.setRGB(x, y, glow ? 0 : argb);
            }
        }
        javax.imageio.ImageIO.write(out, "png", dest);
    }

    /**
     * Sous-clips tournés par le jeu sous d'autres couches (lame de la scie : 268 dans 279, sub.sub._rotation) :
     * « lib/clip » → {sous-clip}. Leurs pixels sont retirés de l'image du clip, le jeu dessine le sous-clip tourné.
     */
    static final java.util.Map<String, int[]> CUT_INNER = java.util.Map.of("anims/279", new int[]{268});

    static String dirName(File[] dirs, int id) {
        for (File d : dirs) {
            String[] parts = d.getName().split("_");
            if (parts.length >= 2 && parts[0].equals("DefineSprite") && parts[1].equals(String.valueOf(id))) return d.getName();
        }
        return "DefineSprite_" + id;
    }

    /** Retire de l'image du clip les pixels identiques à ceux du sous-clip (là où aucune couche ne le recouvre). */
    static void cutInner(File src, File innerPng, SpriteLibrary lib, int outerId, int innerId, File dest) throws java.io.IOException {
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(src), in = javax.imageio.ImageIO.read(innerPng);
        SpriteLibrary.Clip o = lib.clips.get(outerId), c = lib.clips.get(innerId);
        SpriteLibrary.Placement p = o.subs.isEmpty() ? null : o.subs.get(0);
        int ox = Math.round(c.x + (p == null ? 0 : p.tx) - o.x), oy = Math.round(c.y + (p == null ? 0 : p.ty) - o.y);
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(img.getWidth(), img.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int cut = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int v = img.getRGB(x, y), ix = x - ox, iy = y - oy;
                if (ix >= 0 && iy >= 0 && ix < in.getWidth() && iy < in.getHeight()) {
                    int w = in.getRGB(ix, iy);
                    // bord anticrénelé de la lame : couleurs mêlées au fond, tolérance plus large
                    if ((w >>> 24) > 0 && close(v, w, (w >>> 24) < 250 || (v >>> 24) < 250 ? 80 : 28)) {
                        v = 0;
                        cut++;
                    }
                }
                out.setRGB(x, y, v);
            }
        }
        javax.imageio.ImageIO.write(out, "png", dest);
        System.out.println("  clip " + outerId + " : " + cut + " pixels du sous-clip " + innerId + " retirés");
    }

    /**
     * Retire de l'image d'un clip les pixels de ses couches animées (SpriteLibrary.Clip.layers) : pour chaque couche,
     * l'image de la couche qui correspond le mieux (le clip a pu être placé à n'importe quelle image) est projetée par
     * sa matrice et les pixels identiques sont effacés ; le jeu redessine la couche à son image courante.
     */
    static void cutLayers(File src, File pngDir, File[] dirs, SpriteLibrary lib, int ownerId,
                          java.util.List<SpriteLibrary.Placement> layers, File dest) throws java.io.IOException {
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(src);
        SpriteLibrary.Clip o = lib.clips.get(ownerId);
        boolean[][] erase = new boolean[img.getHeight()][img.getWidth()];
        // objets : la couche recouvre une partie fixe simple (ombre, socle) → toute sa surface est effacée ;
        // ennemis : seulement ses pixels visibles, pour ne pas trouer le corps sous les ailes ou les étoiles
        Integer score = lib.symbols.get("hammer_item_score"), special = lib.symbols.get("hammer_item_special");
        // (une seule couche : remplissage animé sur un contour fixe, hippo-flocon, arc-en-miel ; plusieurs petites couches
        // posées sur le corps, comme les éclats de la brioche dorée : découpe par couleur, sinon le corps serait troué)
        boolean fullErase = layers.size() == 1
                && (Integer.valueOf(ownerId).equals(score) || Integer.valueOf(ownerId).equals(special));
        for (SpriteLibrary.Placement p : layers) {
            SpriteLibrary.Clip c = lib.clips.get(p.id);
            if (c == null) continue;
            File dir = new File(pngDir, dirName(dirs, p.id));
            boolean[][] best = null;
            int bestCount = 0;
            for (int fr = 1; fr <= c.frames; fr++) {
                File png = new File(dir, fr + ".png");
                if (!png.exists()) continue;
                java.awt.image.BufferedImage in = javax.imageio.ImageIO.read(png);
                boolean[][] mask = new boolean[img.getHeight()][img.getWidth()];
                int count = 0;
                double det = p.a * p.d - p.b * p.c;
                if (Math.abs(det) < 1e-6) continue;
                for (int y = 0; y < img.getHeight(); y++) {
                    for (int x = 0; x < img.getWidth(); x++) {
                        double X = o.x + x + 0.5 - p.tx, Y = o.y + y + 0.5 - p.ty;
                        double cx = (p.d * X - p.c * Y) / det, cy = (-p.b * X + p.a * Y) / det;
                        int ix = (int) Math.floor(cx - c.x), iy = (int) Math.floor(cy - c.y);
                        if (ix < 0 || iy < 0 || ix >= in.getWidth() || iy >= in.getHeight()) continue;
                        int w = in.getRGB(ix, iy), v = img.getRGB(x, y);
                        if ((w >>> 24) == 0) continue;
                        boolean same = close(v, w, (w >>> 24) < 250 || (v >>> 24) < 250 ? 80 : 36);
                        if (same) count++;
                        if (same || (fullErase && !p.under)) mask[y][x] = true;
                    }
                }
                if (count > bestCount) {
                    bestCount = count;
                    best = mask;
                }
            }
            if (best != null) {
                for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) erase[y][x] |= best[y][x];
            }
        }
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(img.getWidth(), img.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) out.setRGB(x, y, erase[y][x] ? 0 : img.getRGB(x, y));
        javax.imageio.ImageIO.write(out, "png", dest);
    }

    static boolean close(int v, int w, int tol) {
        for (int sh = 0; sh <= 24; sh += 8) if (Math.abs(((v >>> sh) & 0xff) - ((w >>> sh) & 0xff)) > tol) return false;
        return true;
    }

    static void deleteRecursively(File f) {
        if (!f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteRecursively(c);
        f.delete();
    }

    private PackSprites() {
    }
}
