package hammerfest.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import hammerfest.data.LevelData;
import hammerfest.sim.level.TileLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dessin d'un niveau à la manière de levels.View : fond répété, dalles et colonnes, champs,
 * colonnes de pierre latérales et barre du bas. Coordonnées Flash (y vers le bas) converties
 * en coordonnées libGDX dans un monde de {@link #DOC_WIDTH}x{@link #DOC_HEIGHT}.
 */
public class LevelRenderer implements Disposable {

    public static final int DOC_WIDTH = 420;
    public static final int DOC_HEIGHT = 520;
    public static final int GAME_WIDTH = 400;
    public static final int GAME_HEIGHT = 500;
    public static final int X_OFFSET = 10;
    public static final int CASE = 20;

    private final Map<String, Texture> textures = new HashMap<>();
    private final Texture white;

    private LevelData level;
    private List<TileLayout.Block> blocks;
    private List<TileLayout.Field> fields;
    private int[][] grid;

    public LevelRenderer() {
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        white = new Texture(p);
        p.dispose();
    }

    /** Option Miroir : le fond est retourné (View.attachBg, _xscale *= -1). */
    public boolean mirror;

    /** Fond d'ambiance d'un objet spécial (hammer_special_bg) : {image, sous-image 1..n}, ou null. */
    public int[] specialBg;

    /** Masquage demandé par le script du niveau (e_hide). */
    public boolean hideTiles, hideBorders;

    public void setLevel(LevelData level) {
        setLevel(level, level.grid());
    }

    /** Niveau avec sa grille courante (modifiable par les scripts : e_add / e_rem). */
    public void setLevel(LevelData level, int[][] grid) {
        this.level = level;
        blocks = TileLayout.blocks(grid, LevelData.WIDTH, LevelData.HEIGHT);
        fields = TileLayout.fields(grid, LevelData.WIDTH, LevelData.HEIGHT);
        this.grid = grid;
    }

    public LevelData level() {
        return level;
    }

    /** Champs portail du niveau affiché : {case x, case y} du haut / de la gauche de chaque champ. */
    public List<int[]> portalFields() {
        List<int[]> out = new java.util.ArrayList<>();
        if (fields != null) for (TileLayout.Field f : fields) if (f.id == -7) out.add(new int[]{f.x, f.y});
        return out;
    }

    /** Dessine le niveau ; le batch doit être projeté sur un monde DOC_WIDTH x DOC_HEIGHT. */
    public void render(SpriteBatch batch) {
        if (level == null) return;
        drawBackground(batch);
        if (!hideTiles) drawTiles(batch);
        drawFields(batch);
    }

    /** Colonnes latérales et barre du bas, dessinées au-dessus des décors et entités. */
    public void renderFront(SpriteBatch batch) {
        if (level == null) return;
        drawBorders(batch);
    }

    /** Clips Flash (fond d'origine « hammer_bg ») ; null = images de secours. */
    private Sprites sprites;

    public void setSprites(Sprites sprites) {
        this.sprites = sprites;
    }

    private void drawBackground(SpriteBatch batch) {
        // View.attachSpecialBg : remplace le fond du niveau tant qu'il est affiché
        if (specialBg != null && sprites != null
                && sprites.draw(batch, "hammer_special_bg", specialBg[0], specialBg[1], X_OFFSET, 0, 1, 1, DOC_HEIGHT)) {
            return;
        }
        // View.attachBg : clip hammer_bg à x = xOffset, image skinBg
        if (sprites != null && sprites.has("hammer_bg")
                && sprites.draw(batch, "hammer_bg", -1, Math.max(1, level.skinBg),
                mirror ? X_OFFSET + GAME_WIDTH : X_OFFSET, 0, mirror ? -1 : 1, 1, DOC_HEIGHT)) {
            return;
        }
        Texture bg = texture("images/bg" + two(level.skinBg), ".jpg", ".png");
        if (bg == null) {
            fill(batch, X_OFFSET, 0, GAME_WIDTH, GAME_HEIGHT, Color.DARK_GRAY);
            return;
        }
        bg.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        float u2 = GAME_WIDTH / (float) bg.getWidth();
        float v2 = GAME_HEIGHT / (float) bg.getHeight();
        batch.draw(bg, X_OFFSET, flipY(0, GAME_HEIGHT), GAME_WIDTH, GAME_HEIGHT, 0, v2, u2, 0);
    }

    private void drawTiles(SpriteBatch batch) {
        int tileSkin = TileLayout.tileSkin(level.skinTiles);
        int columnSkin = TileLayout.columnSkin(level.skinTiles);
        for (TileLayout.Block b : blocks) {
            int skin = b.vertical ? columnSkin : tileSkin;
            Texture tile = texture("images/tiles/tile_" + two(skin), ".png");
            Texture end = texture("images/tiles/end_" + two(skin), ".png");
            if (tile == null) tile = texture("images/tiles/tile_01", ".png");
            if (end == null) end = texture("images/tiles/end_01", ".png");
            float x = X_OFFSET + b.x * CASE;
            float y = b.y * CASE;
            float len = b.length * CASE;
            // L'embout (endTile, placé à x = longueur) a son dessin décalé de -5 px dans son clip
            // (export SWF : forme à translateX=-100 twips) : il recouvre les 5 derniers pixels.
            if (!b.vertical) {
                int w = (int) Math.min(len, tile.getWidth());
                batch.draw(tile, x, flipY(y, tile.getHeight()), 0, 0, w, tile.getHeight());
                batch.draw(end, x + len - end.getWidth(), flipY(y, end.getHeight()));
            } else {
                // Colonne : la texture de dalle transposée (_rotation=90, _yscale=-100 dans Flash).
                Texture col = transposed(tile, "col_" + two(skin));
                Texture colEnd = transposed(end, "colEnd_" + two(skin));
                int h = (int) Math.min(len, col.getHeight());
                batch.draw(col, x, flipY(y, h), 0, 0, col.getWidth(), h);
                batch.draw(colEnd, x, flipY(y + len - colEnd.getHeight(), colEnd.getHeight()));
            }
        }
    }

    /** Longueur du rayon dans le clip « skin » des champs (3 cases) : base de l'étirement (mc._width/_height). */
    static final float FIELD_ART_LENGTH = 60;

    private void drawFields(SpriteBatch batch) {
        if (sprites != null && sprites.has("field")) {
            for (TileLayout.Field f : fields) {
                float x = X_OFFSET + f.x * CASE;
                float y = f.y * CASE;
                int skin = Math.abs(f.id);
                float stretch = f.length * CASE / FIELD_ART_LENGTH;
                // GameMechanics : les champs portail flottent (3 px, sinus)
                if (f.id == -7) y += (float) (3 * Math.sin(sprites.animClock * 0.1));
                if (f.vertical) {
                    // View.attachField : portail vertical avec une case pleine à sa droite → tourbillon retourné
                    sprites.flipInnerLoop = f.id == -7 && f.x + 1 < LevelData.WIDTH && grid[f.x + 1][f.y] > 0;
                    sprites.draw(batch, "field", 0, skin, x, y, 1, stretch, DOC_HEIGHT);
                    sprites.flipInnerLoop = false;
                } else {
                    sprites.draw(batch, "field", 1, skin, x, y, stretch, 1, DOC_HEIGHT);
                }
                if (f.id == -6) drawPods(batch, f);
            }
            return;
        }
        for (TileLayout.Field f : fields) {
            float x = X_OFFSET + f.x * CASE;
            float y = f.y * CASE;
            float w = f.vertical ? CASE : f.length * CASE;
            float h = f.vertical ? f.length * CASE : CASE;
            Color c = fieldColor(f.id);
            if (f.vertical) fill(batch, x + CASE * 0.35f, y, CASE * 0.3f, h, c);
            else fill(batch, x, y + CASE * 0.35f, w, CASE * 0.3f, c);
        }
    }

    private void drawBorders(SpriteBatch batch) {
        Texture side = texture("images/colonnes", ".png");
        if (side != null && !hideBorders) {
            batch.draw(side, 5 - side.getWidth() / 2f, flipY(0, side.getHeight()));
            batch.draw(side, GAME_WIDTH + 15 - side.getWidth() / 2f, flipY(0, side.getHeight()));
        }
        Texture bar = texture("images/bottomBar", ".png");
        if (bar != null) batch.draw(bar, 0, flipY(GAME_HEIGHT, bar.getHeight()));
    }

    /** Embouts des téléporteurs (hammer_pod) aux deux extrémités (View.attachField). */
    private void drawPods(SpriteBatch batch, TileLayout.Field f) {
        float len = f.length * CASE;
        if (!f.vertical) {
            float sx = X_OFFSET + f.x * CASE, sy = f.y * CASE + CASE - CASE * 0.5f;
            sprites.draw(batch, "hammer_pod", -1, 1, sx, sy, 1, 1, 0, DOC_HEIGHT);
            sprites.draw(batch, "hammer_pod", -1, 1, sx + len, sy, 1, 1, 180, DOC_HEIGHT);
        } else {
            float sx = X_OFFSET + f.x * CASE + CASE * 0.5f, sy = f.y * CASE;
            sprites.draw(batch, "hammer_pod", -1, 1, sx, sy, 1, 1, 90, DOC_HEIGHT);
            sprites.draw(batch, "hammer_pod", -1, 1, sx, sy + len, 1, 1, 270, DOC_HEIGHT);
        }
    }

    /** Couleurs provisoires des champs (Data.FIELD_*) en attendant les sprites exportés. */
    static Color fieldColor(int id) {
        switch (id) {
            case -6: return new Color(0.4f, 0.9f, 1f, 0.75f);    // téléporteur
            case -7: return new Color(0.9f, 0.4f, 1f, 0.75f);    // portail
            case -8: case -9: return new Color(1f, 1f, 1f, 0.6f); // buts (soccer)
            case -10: return new Color(1f, 0.6f, 0.1f, 0.75f);   // bumper
            case -11: return new Color(0.5f, 1f, 0.5f, 0.6f);    // zone de paix
            default: return new Color(1f, 0.9f, 0.2f, 0.7f);     // champs de bombes (-1..-5)
        }
    }

    private void fill(SpriteBatch batch, float x, float y, float w, float h, Color c) {
        Color old = batch.getColor().cpy();
        batch.setColor(c);
        batch.draw(white, x, flipY(y, h), w, h);
        batch.setColor(old);
    }

    /** y Flash (bord haut, vers le bas) → y libGDX (bord bas, vers le haut). */
    static float flipY(float flashY, float height) {
        return DOC_HEIGHT - flashY - height;
    }

    static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    /** Texture chargée à la première demande ; les absences sont mémorisées (valeur null). */
    private Texture texture(String base, String... exts) {
        if (textures.containsKey(base)) return textures.get(base);
        Texture t = null;
        for (String ext : exts) {
            FileHandle f = Gdx.files.internal(base + ext);
            if (f.exists()) {
                t = new Texture(f);
                break;
            }
        }
        textures.put(base, t);
        return t;
    }

    private Texture transposed(Texture src, String key) {
        return textures.computeIfAbsent(key, k -> {
            if (!src.getTextureData().isPrepared()) src.getTextureData().prepare();
            Pixmap in = src.getTextureData().consumePixmap();
            Pixmap out = new Pixmap(in.getHeight(), in.getWidth(), Pixmap.Format.RGBA8888);
            for (int x = 0; x < in.getWidth(); x++)
                for (int y = 0; y < in.getHeight(); y++)
                    out.drawPixel(y, x, in.getPixel(x, y));
            if (src.getTextureData().disposePixmap()) in.dispose();
            Texture t = new Texture(out);
            out.dispose();
            return t;
        });
    }

    @Override
    public void dispose() {
        for (Texture t : textures.values()) if (t != null) t.dispose();
        textures.clear();
        white.dispose();
    }
}
