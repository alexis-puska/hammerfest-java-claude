package hammerfest.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.utils.Disposable;
import hammerfest.data.SpriteLibrary;
import hammerfest.json.Jsons;
import hammerfest.sim.FrameSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Clips Flash exportés (assets/sprites/&lt;lib&gt;.json + .atlas) et leur dessin.
 * Reproduit l'empilement Flash : clip de l'entité (image = état) → sous-clip "sub" (image = animation),
 * avec la matrice de placement, l'échelle/le retournement de l'entité et le cadre des PNG.
 */
public class Sprites implements Disposable, FrameSource {

    private final Map<String, SpriteLibrary> libs = new HashMap<>();
    private final Map<String, TextureAtlas> atlases = new HashMap<>();
    private final Map<String, TextureAtlas.AtlasRegion[]> regionCache = new HashMap<>();
    private final Affine2 affine = new Affine2();

    public Sprites(String... names) {
        for (String name : names) {
            FileHandle json = Gdx.files.internal("sprites/" + name + ".json");
            FileHandle atlas = Gdx.files.internal("sprites/" + name + ".atlas");
            if (!json.exists()) continue;
            libs.put(name, Jsons.read(json.readString("UTF-8"), SpriteLibrary.class));
            if (atlas.exists()) atlases.put(name, new TextureAtlas(atlas));
        }
    }

    /** Décalage vertical du sous-clip "sub" à l'image {@code frame} (1..n) du clip, 0 si aucun. */
    public float subOffsetY(String linkage, int frame) {
        String lib = libOf(linkage);
        if (lib == null) return 0;
        SpriteLibrary.Clip clip = libs.get(lib).symbol(linkage);
        if (frame < 1 || frame > clip.subs.size() || clip.subs.get(frame - 1) == null) return 0;
        return clip.subs.get(frame - 1).ty;
    }

    /** Le clip est-il exporté et dessinable ? */
    public boolean has(String linkage) {
        String lib = libOf(linkage);
        return lib != null && atlases.containsKey(lib);
    }

    public boolean available() {
        return !atlases.isEmpty();
    }

    /**
     * Ordre de recherche d'un nom exporté par plusieurs bibliothèques. Le jeu d'origine est un seul game.swf (swfmake :
     * _gfx, _editor, _anims, _sounds, _ending fusionnés) et, pour un nom exporté deux fois, le lecteur Flash garde le
     * dernier ExportAssets. Seul cas qui diffère : « statue », grande statue banane ninja dans anims (export n° 132
     * de game.swf) et petite statue de gfx (export n° 161, celle qui s'affiche) : décor des niveaux du temple et de
     * l'enfer (dimension du niveau 54). « partExplosion » est le même clip dans anims et ending.
     */
    static final String[] LOOKUP_ORDER = {"gfx", "ending", "anims"};

    /** Bibliothèque contenant le symbole, ou null. */
    private String libOf(String linkage) {
        for (String name : LOOKUP_ORDER) {
            SpriteLibrary l = libs.get(name);
            if (l != null && l.symbols.containsKey(linkage)) return name;
        }
        for (Map.Entry<String, SpriteLibrary> e : libs.entrySet()) { // variantes du joueur…
            if (e.getValue().symbols.containsKey(linkage)) return e.getKey();
        }
        return null;
    }

    /**
     * {images, image du removeMovieClip() (0 = absent), images portant un stop()...} du clip,
     * pour les décors animés.
     */
    @Override
    public boolean playsOnAttach(String linkage) {
        String lib = libOf(linkage);
        SpriteLibrary.Clip clip = lib == null ? null : libs.get(lib).symbol(linkage);
        return clip != null && clip.playOnAttach;
    }

    public int[] playInfo(String linkage) {
        String lib = libOf(linkage);
        if (lib == null) return new int[]{1, 0};
        SpriteLibrary.Clip clip = libs.get(lib).symbol(linkage);
        int[] out = new int[2 + clip.stopFrames.size()];
        out[0] = clip.frames;
        out[1] = clip.removeFrame;
        for (int i = 0; i < clip.stopFrames.size(); i++) out[2 + i] = clip.stopFrames.get(i);
        return out;
    }

    @Override
    public int frames(String linkage, int animId) {
        String lib = libOf(linkage);
        if (lib == null) return FrameSource.DEFAULT.frames(linkage, animId);
        SpriteLibrary l = libs.get(lib);
        SpriteLibrary.Clip clip = l.symbol(linkage);
        if (animId >= 0 && animId < clip.subs.size() && clip.subs.get(animId) != null && !clip.subs.get(animId).loop) {
            SpriteLibrary.Clip sub = l.clips.get(clip.subs.get(animId).id);
            return sub == null ? 1 : sub.frames;
        }
        return clip.frames;
    }

    /**
     * Dessine l'image {@code subFrame} (1..n) de l'état {@code animId} du clip {@code linkage}, à la position
     * Flash (x, y) (y vers le bas) dans un monde de hauteur {@code worldHeight}.
     *
     * @return false si le clip n'est pas disponible
     */
    public boolean draw(SpriteBatch batch, String linkage, int animId, int subFrame,
                        float x, float y, float scaleX, float scaleY, float worldHeight) {
        return draw(batch, linkage, animId, subFrame, x, y, scaleX, scaleY, 0, worldHeight);
    }

    /** Nombre d'images d'un clip qui boucle tout seul (sans l'image de saut gotoAndPlay). */
    static int loopFrames(SpriteLibrary.Clip c) {
        if (c == null) return 1;
        return Math.max(1, c.loopLength > 0 ? Math.min(c.loopLength, c.frames) : c.frames);
    }

    /** Image d'un clip qui joue tout seul depuis {@link #animClock} : en boucle, ou figé sur son premier stop(). */
    int loopFrame(SpriteLibrary.Clip c) {
        return clipFrame(c, hammerfest.sim.Const.clipAge(animClock)); // 40 images/s, comme le SWF d'origine
    }

    /** Image d'un clip joué depuis {@code age} cycles : figé sur son premier stop(), ou en boucle (reprise à loopStart). */
    public static int clipFrame(SpriteLibrary.Clip c, long age) {
        if (c != null && c.stopFrame > 1 && age + 1 >= c.stopFrame) return c.stopFrame;
        int n = loopFrames(c);
        int start = c == null ? 1 : Math.max(1, Math.min(c.loopStart, n));
        if (age < n) return (int) age + 1;
        return start + (int) ((age - n) % (n - start + 1));
    }

    /**
     * Rotation (degrés) du sous-clip « sub » de l'image dessinée (lame de la scie) : il est dessiné à part, tourné,
     * sous l'image (évidée à l'assemblage, voir PackSprites.CUT_INNER) ; NaN = aucune.
     */
    public float innerRotation = Float.NaN;

    /** Retourne horizontalement le clip en boucle emboîté (View.attachField : skin.sub._xscale *= -1). */
    public boolean flipInnerLoop;

    /** Boucle emboîtée sur son 2e état quand il existe (rayon de téléporteur allumé, {@link SpriteLibrary.Placement#alt}). */
    public boolean altInnerLoop;

    /** Teinte des couches en aplat (rayons d'arme), créée à la première utilisation (contexte GL). */
    private TintShader tintShader;

    /** Horloge (cycles de jeu) des clips qui jouent en boucle tout seuls ({@link SpriteLibrary.Placement#loop}). */
    public long animClock;

    /** Couches affichées à la demande que l'entité dessinée montre ({@link SpriteLibrary.Placement#part}). */
    public final java.util.Set<String> visibleParts = new java.util.HashSet<>();

    /** Variante avec _rotation (degrés, sens horaire comme Flash). */
    public boolean draw(SpriteBatch batch, String linkage, int animId, int subFrame,
                        float x, float y, float scaleX, float scaleY, float rotationDeg, float worldHeight) {
        return draw(batch, linkage, animId, subFrame, x, y, scaleX, scaleY, rotationDeg, worldHeight, null, 0);
    }

    /**
     * Variante avec déformation du sous-clip "sub" ({@code subT[off..off+4]} : décalage x, y, échelles x, y,
     * rotation en degrés), comme sub._x/_y/_xscale/_yscale/_rotation du code d'origine ; null = aucune.
     */
    public boolean draw(SpriteBatch batch, String linkage, int animId, int subFrame,
                        float x, float y, float scaleX, float scaleY, float rotationDeg, float worldHeight,
                        float[] subT, int off) {
        String lib = libOf(linkage);
        if (lib == null || !atlases.containsKey(lib)) return false;
        SpriteLibrary l = libs.get(lib);
        int symbolId = l.symbols.get(linkage);
        SpriteLibrary.Clip clip = l.clips.get(symbolId);

        // gotoAndStop au-delà de la dernière image : Flash reste sur la dernière (scie : un seul état)
        if (animId >= clip.subs.size() && !clip.subs.isEmpty()) animId = clip.subs.size() - 1;
        SpriteLibrary.Placement p = animId >= 0 && animId < clip.subs.size() ? clip.subs.get(animId) : null;
        int imageId;
        int frame;
        SpriteLibrary.Clip image;
        // Matrice clip → entité (identité si pas de sous-clip)
        float a = 1, b = 0, c = 0, d = 1, tx = 0, ty = 0;
        if (p != null) {
            imageId = p.id;
            image = l.clips.get(p.id);
            frame = p.loop ? loopFrame(image) : subFrame;
            a = p.a; b = p.b; c = p.c; d = p.d; tx = p.tx; ty = p.ty;
            if (subT != null) {
                double r = Math.toRadians(subT[off + 4]);
                float sx = subT[off + 2], sy = subT[off + 3];
                float ca = (float) Math.cos(r), sa = (float) Math.sin(r);
                // Flash : a = sx·cos, b = sx·sin, c = -sy·sin, d = sy·cos, composé à la position d'origine
                float A = sx * ca, B = sx * sa, C = -sy * sa, D = sy * ca;
                float na = a * A + c * B, nb = b * A + d * B, nc = a * C + c * D, nd = b * C + d * D;
                a = na; b = nb; c = nc; d = nd;
                tx += subT[off];
                ty += subT[off + 1];
            }
        } else {
            // Pas de sous-clip à cette image : si le clip a des états (image = état), on affiche l'état ;
            // sinon le clip est sa propre animation (tirs, effets…) et on suit subFrame.
            imageId = symbolId;
            image = clip;
            frame = !clip.subs.isEmpty() && animId >= 0 ? animId + 1 : subFrame;
        }
        if (image == null) return false;
        // Image dont le sous-clip joue tout seul en boucle (tourbillon des portails…) : on descend dans ce clip
        int fi = Math.min(Math.max(frame, 1), image.frames) - 1;
        SpriteLibrary.Placement inner = image.subs != null && fi < image.subs.size() ? image.subs.get(fi) : null;
        if (altInnerLoop && inner != null && inner.alt != null && l.clips.containsKey(inner.alt.id)) inner = inner.alt;
        if (inner != null && inner.loop && l.clips.containsKey(inner.id)) {
            SpriteLibrary.Clip in = l.clips.get(inner.id);
            float na = a * inner.a + c * inner.b, nb = b * inner.a + d * inner.b;
            float nc = a * inner.c + c * inner.d, nd = b * inner.c + d * inner.d;
            float ntx = a * inner.tx + c * inner.ty + tx, nty = b * inner.tx + d * inner.ty + ty;
            if (flipInnerLoop) { // sub._xscale *= -1 (portail contre un mur à droite)
                na = -na;
                nb = -nb;
            }
            a = na; b = nb; c = nc; d = nd; tx = ntx; ty = nty;
            imageId = inner.id;
            image = in;
            frame = loopFrame(in);
        }
        if (!Float.isNaN(innerRotation) && inner != null && !inner.loop && l.clips.containsKey(inner.id)) {
            // sous-clip tourné sur lui-même, dessiné d'abord (il est sous les autres couches de l'image)
            SpriteLibrary.Clip ic = l.clips.get(inner.id);
            TextureAtlas.AtlasRegion ir = region(lib, inner.id, 0);
            if (ir != null) {
                double r = Math.toRadians(innerRotation);
                float cr = (float) Math.cos(r), sr = (float) Math.sin(r);
                float ia = inner.a * cr + inner.c * sr, ib = inner.b * cr + inner.d * sr;
                float ic2 = -inner.a * sr + inner.c * cr, id = -inner.b * sr + inner.d * cr;
                emit(batch, ir, ic, a * ia + c * ib, b * ia + d * ib, a * ic2 + c * id, b * ic2 + d * id,
                        a * inner.tx + c * inner.ty + tx, b * inner.tx + d * inner.ty + ty,
                        x, y, scaleX, scaleY, rotationDeg, worldHeight);
            }
        }
        int li = Math.min(Math.max(frame, 1), image.frames) - 1;
        java.util.List<SpriteLibrary.Placement> layers = image.layers != null && li < image.layers.size() ? image.layers.get(li) : null;
        if (layers != null) drawLayers(batch, lib, l, layers, true, a, b, c, d, tx, ty, x, y, scaleX, scaleY, rotationDeg, worldHeight);
        TextureAtlas.AtlasRegion region = region(lib, imageId, li);
        if (region == null) {
            // image vidée à l'assemblage (Clip.blankFrames) : seules ses couches teintées la dessinent (rayons d'arme)
            if (layers == null) return false;
            drawLayers(batch, lib, l, layers, false, a, b, c, d, tx, ty, x, y, scaleX, scaleY, rotationDeg, worldHeight);
            return true;
        }
        emit(batch, region, image, a, b, c, d, tx, ty, x, y, scaleX, scaleY, rotationDeg, worldHeight);
        if (layers != null) drawLayers(batch, lib, l, layers, false, a, b, c, d, tx, ty, x, y, scaleX, scaleY, rotationDeg, worldHeight);
        return true;
    }

    /** Couches animées d'une image figée (dessous ou dessus), chacune à son image (horloge de l'entité). */
    private void drawLayers(SpriteBatch batch, String lib, SpriteLibrary l, java.util.List<SpriteLibrary.Placement> layers,
                            boolean under, float a, float b, float c, float d, float tx, float ty,
                            float x, float y, float scaleX, float scaleY, float rotationDeg, float worldHeight) {
        for (SpriteLibrary.Placement p : layers) {
            if (p.under != under) continue;
            if (p.part != null && !visibleParts.contains(p.part)) continue; // affichée à la demande seulement
            SpriteLibrary.Clip lc = l.clips.get(p.id);
            if (lc == null) continue;
            TextureAtlas.AtlasRegion r = region(lib, p.id, Math.min(Math.max(loopFrame(lc), 1), lc.frames) - 1);
            if (r == null) continue;
            com.badlogic.gdx.graphics.glutils.ShaderProgram before = null;
            com.badlogic.gdx.graphics.Color color = null;
            if (p.tintRgb != null) { // aplat de couleur (CXFORM d'origine : multiplicateurs 0, couleur ajoutée)
                if (tintShader == null) tintShader = new TintShader();
                before = batch.getShader();
                color = batch.getColor().cpy();
                if (tintShader.forTint(batch, (100 << 24) | p.tintRgb) == null) before = null;
                batch.setColor(color.r, color.g, color.b, color.a * (p.tintAlpha == null ? 1 : p.tintAlpha));
            }
            emit(batch, r, lc, a * p.a + c * p.b, b * p.a + d * p.b, a * p.c + c * p.d, b * p.c + d * p.d,
                    a * p.tx + c * p.ty + tx, b * p.tx + d * p.ty + ty, x, y, scaleX, scaleY, rotationDeg, worldHeight);
            if (color != null) {
                if (before != null) batch.setShader(before);
                batch.setColor(color);
            }
        }
    }

    /** Dessine la région {@code region} du clip {@code image} : matrice clip → entité (a…ty), puis entité → monde. */
    private void emit(SpriteBatch batch, TextureAtlas.AtlasRegion region, SpriteLibrary.Clip image,
                      float a, float b, float c, float d, float tx, float ty,
                      float x, float y, float scaleX, float scaleY, float rotationDeg, float worldHeight) {

        // Point (u, v) de la région (v vers le haut, origine en bas à gauche de l'image complète) :
        //   clip :   X = image.x + u,  Y = image.y + image.h - v
        //   entité : X' = a·X + c·Y + tx,  Y' = b·X + d·Y + ty
        //   monde :  xw = x + scaleX·X',  yw = y + scaleY·Y',  puis gdx : (xw, H - yw)
        //   rotation Flash (y vers le bas) : xw = x + cos·sx·X' - sin·sy·Y',  yw = y + sin·sx·X' + cos·sy·Y'
        float ox = image.x + region.offsetX;
        float oy = image.y + image.h - region.offsetY;
        double rad = Math.toRadians(rotationDeg);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        // Matrice entité → monde Flash : [[cos·sx, -sin·sy], [sin·sx, cos·sy]]
        float e00 = cos * scaleX, e01 = -sin * scaleY, e10 = sin * scaleX, e11 = cos * scaleY;
        // Matrice clip → monde Flash = E · P
        float f00 = e00 * a + e01 * b, f01 = e00 * c + e01 * d, f02 = e00 * tx + e01 * ty;
        float f10 = e10 * a + e11 * b, f11 = e10 * c + e11 * d, f12 = e10 * tx + e11 * ty;
        // Région (u, v vers le haut) → clip : X = ox + u, Y = oy - v ; puis y gdx = H - y Flash
        affine.m00 = f00;
        affine.m01 = -f01;
        affine.m02 = x + f00 * ox + f01 * oy + f02;
        affine.m10 = -f10;
        affine.m11 = f11;
        affine.m12 = worldHeight - (y + f10 * ox + f11 * oy + f12);
        batch.draw(region, region.packedWidth, region.packedHeight, affine);
    }

    /**
     * Dessine la partie nommée {@code part} du symbole (SpriteLibrary.parts) à l'image {@code frame}, avec une
     * rotation propre en degrés ({@code partRotation}, NaN = celle du placement d'origine), comme body._rotation.
     */
    public boolean drawPart(SpriteBatch batch, String linkage, String part, int frame, float x, float y,
                            float scaleX, float scaleY, float partRotation, float worldHeight) {
        return drawPart(batch, linkage, part, frame, x, y, scaleX, scaleY, partRotation, 1, 1, Float.NaN, worldHeight);
    }

    /** Variante avec échelles propres de la partie (_xscale/_yscale) et position verticale imposée (_y, NaN = d'origine). */
    public boolean drawPart(SpriteBatch batch, String linkage, String part, int frame, float x, float y,
                            float scaleX, float scaleY, float partRotation, float partSx, float partSy, float partY,
                            float worldHeight) {
        String lib = libOf(linkage);
        if (lib == null || !atlases.containsKey(lib)) return false;
        SpriteLibrary l = libs.get(lib);
        SpriteLibrary.Placement p = l.parts.get(linkage + "/" + part);
        if (p == null) return false;
        SpriteLibrary.Clip image = l.clips.get(p.id);
        if (image == null) return false;
        TextureAtlas.AtlasRegion region = region(lib, p.id, Math.min(Math.max(frame, 1), image.frames) - 1);
        if (region == null) return false;
        float a = p.a, b = p.b, c = p.c, d = p.d;
        if (!Float.isNaN(partRotation)) {
            float sx = (float) Math.hypot(a, b), sy = (float) Math.hypot(c, d);
            double r = Math.toRadians(partRotation);
            float ca = (float) Math.cos(r), sa = (float) Math.sin(r);
            a = sx * ca; b = sx * sa; c = -sy * sa; d = sy * ca;
        }
        float ox = image.x + region.offsetX;
        float oy = image.y + image.h - region.offsetY;
        a *= partSx; b *= partSx; c *= partSy; d *= partSy;
        float pty = Float.isNaN(partY) ? p.ty : partY;
        float f00 = scaleX * a, f01 = scaleX * c, f02 = scaleX * p.tx;
        float f10 = scaleY * b, f11 = scaleY * d, f12 = scaleY * pty;
        affine.m00 = f00;
        affine.m01 = -f01;
        affine.m02 = x + f00 * ox + f01 * oy + f02;
        affine.m10 = -f10;
        affine.m11 = f11;
        affine.m12 = worldHeight - (y + f10 * ox + f11 * oy + f12);
        batch.draw(region, region.packedWidth, region.packedHeight, affine);
        return true;
    }

    /** Image du clip {@code linkage} joué depuis {@code age} cycles (voir clipFrame). */
    public int playFrame(String linkage, long age) {
        String lib = libOf(linkage);
        if (lib == null) return 1;
        return clipFrame(libs.get(lib).symbol(linkage), age);
    }

    /** Nombre d'images de la partie nommée, 0 si absente. */
    public int partFrames(String linkage, String part) {
        String lib = libOf(linkage);
        if (lib == null) return 0;
        SpriteLibrary.Placement p = libs.get(lib).parts.get(linkage + "/" + part);
        SpriteLibrary.Clip c = p == null ? null : libs.get(lib).clips.get(p.id);
        return c == null ? 0 : c.frames;
    }

    /**
     * Rectangle englobant {x, y, largeur, hauteur} (repère Flash de l'entité, y vers le bas, échelle 1) de l'état
     * {@code animId} du clip : sert à centrer une image hors du jeu (icônes du frigo).
     *
     * @return false si le clip n'est pas disponible
     */
    /** Cadre {x, y, largeur, hauteur} du clip exporté (repère Flash du symbole, toutes images confondues). */
    public boolean clipRect(String linkage, float[] out) {
        String lib = libOf(linkage);
        if (lib == null) return false;
        SpriteLibrary.Clip c = libs.get(lib).clips.get(libs.get(lib).symbols.get(linkage));
        if (c == null) return false;
        out[0] = c.x;
        out[1] = c.y;
        out[2] = c.w;
        out[3] = c.h;
        return true;
    }

    public boolean bounds(String linkage, int animId, float[] out) {
        String lib = libOf(linkage);
        if (lib == null || !atlases.containsKey(lib)) return false;
        SpriteLibrary l = libs.get(lib);
        int symbolId = l.symbols.get(linkage);
        SpriteLibrary.Clip clip = l.clips.get(symbolId);
        // gotoAndStop au-delà de la dernière image : Flash reste sur la dernière (scie : un seul état)
        if (animId >= clip.subs.size() && !clip.subs.isEmpty()) animId = clip.subs.size() - 1;
        SpriteLibrary.Placement p = animId >= 0 && animId < clip.subs.size() ? clip.subs.get(animId) : null;
        // Même choix d'image que draw() avec subFrame = 1
        int imageId = p != null ? p.id : symbolId;
        SpriteLibrary.Clip image = l.clips.get(imageId);
        if (image == null) return false;
        int frame = p != null || clip.subs.isEmpty() || animId < 0 ? 1 : animId + 1;
        TextureAtlas.AtlasRegion region = region(lib, imageId, Math.min(Math.max(frame, 1), image.frames) - 1);
        if (region == null || region.packedWidth <= 0 || region.packedHeight <= 0) return false;
        float a = 1, b = 0, c = 0, d = 1, tx = 0, ty = 0;
        if (p != null) {
            a = p.a; b = p.b; c = p.c; d = p.d; tx = p.tx; ty = p.ty;
        }
        // Partie non vide de l'image (PNG recadré par l'atlas), dans le repère du clip (y vers le bas)
        float left = image.x + region.offsetX;
        float bottom = image.y + image.h - region.offsetY;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float X = left + ((i & 1) == 0 ? 0 : region.packedWidth);
            float Y = bottom - ((i & 2) == 0 ? 0 : region.packedHeight);
            float px = a * X + c * Y + tx, py = b * X + d * Y + ty;
            minX = Math.min(minX, px); maxX = Math.max(maxX, px);
            minY = Math.min(minY, py); maxY = Math.max(maxY, py);
        }
        out[0] = minX;
        out[1] = minY;
        out[2] = maxX - minX;
        out[3] = maxY - minY;
        return true;
    }

    private TextureAtlas.AtlasRegion region(String lib, int clipId, int index) {
        String key = lib + "/c" + clipId;
        TextureAtlas.AtlasRegion[] frames = regionCache.computeIfAbsent(key, k -> {
            com.badlogic.gdx.utils.Array<TextureAtlas.AtlasRegion> all = atlases.get(lib).findRegions("c" + clipId);
            TextureAtlas.AtlasRegion[] arr = new TextureAtlas.AtlasRegion[0];
            for (TextureAtlas.AtlasRegion r : all) {
                if (r.index >= arr.length) arr = java.util.Arrays.copyOf(arr, r.index + 1);
                arr[r.index] = r;
            }
            return arr;
        });
        return index >= 0 && index < frames.length ? frames[index] : null;
    }

    @Override
    public void dispose() {
        for (TextureAtlas a : atlases.values()) a.dispose();
        atlases.clear();
        if (tintShader != null) tintShader.dispose();
    }
}
