package hammerfest.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

/** Polices TTF rendues par FreeType : police du jeu (SWF), Verdana du système (parchemins, menus)… */
public final class Fonts {

    /** Police du jeu : Franklin Gothic Heavy exportée du SWF (voir export-gfx.sh). */
    public static final String GAME = "fonts/hammerfest.ttf";

    /** Verdana du système, comme le lecteur Flash pour les champs du parchemin (police non intégrée au SWF). */
    public static FileHandle verdana() {
        return Gdx.files.absolute(System.getenv().getOrDefault("WINDIR", "C:/Windows") + "/Fonts/verdana.ttf");
    }

    /**
     * Police {@code ttf} à {@code size} px ; à défaut la police du jeu, puis celle de libGDX.
     *
     * @param markup balises de couleur [#RRGGBB]…[] autorisées
     */
    public static BitmapFont load(FileHandle ttf, int size, boolean markup) {
        if (!ttf.exists()) ttf = Gdx.files.internal(GAME);
        if (!ttf.exists()) return new BitmapFont();
        FreeTypeFontGenerator gen = null;
        try {
            gen = new FreeTypeFontGenerator(ttf);
            FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
            p.size = size;
            p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "àâäçéèêëîïôöùûüÿœæÀÂÄÇÉÈÊËÎÏÔÖÙÛÜŒÆ«»’…–—€▼•";
            p.minFilter = Texture.TextureFilter.Linear;
            p.magFilter = Texture.TextureFilter.Linear;
            BitmapFont font = gen.generateFont(p);
            font.setUseIntegerPositions(false);
            font.getData().markupEnabled = markup;
            return font;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            Gdx.app.error("Fonts", "police illisible : " + ttf + ", police par défaut", e);
            return new BitmapFont();
        } finally {
            if (gen != null) gen.dispose();
        }
    }

    private Fonts() {
    }
}
