package hammerfest.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

import java.util.List;

/**
 * Voile d'obscurité des niveaux profonds (hammer_fx_darkness) : noir d'opacité {@code darkness}%,
 * percé (mode ERASE de Flash) de halos autour des joueurs et des torches. Rendu dans un FrameBuffer :
 * remplissage noir, puis effacement de l'alpha par des disques dégradés.
 */
public class DarknessRenderer implements Disposable {

    /** Diamètre du halo d'un joueur (×1.5 avec la bougie). */
    static final float PLAYER_HOLE = 150;

    private final FrameBuffer fbo;
    private final TextureRegion fboRegion;
    private final Texture hole;
    private final Matrix4 projection = new Matrix4();

    public DarknessRenderer() {
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, LevelRenderer.DOC_WIDTH, LevelRenderer.DOC_HEIGHT, false);
        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);
        projection.setToOrtho2D(0, 0, LevelRenderer.DOC_WIDTH, LevelRenderer.DOC_HEIGHT);

        // Disque : alpha 1 au centre, dégradé vers 0 sur le dernier tiers
        int size = 128;
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double d = Math.hypot(x - size / 2.0 + 0.5, y - size / 2.0 + 0.5) / (size / 2.0);
                float a = d >= 1 ? 0 : d <= 0.65 ? 1 : (float) (1 - (d - 0.65) / 0.35);
                p.drawPixel(x, y, ((int) (a * 255) & 0xFF) | 0xFFFFFF00);
            }
        }
        hole = new Texture(p);
        hole.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
    }

    /**
     * Dessine le voile par-dessus la scène (batch hors begin/end).
     *
     * @param holes {x, y, diamètre} en coordonnées du document (y Flash vers le bas)
     */
    public void render(SpriteBatch batch, Matrix4 sceneProjection, com.badlogic.gdx.utils.viewport.Viewport viewport,
                       double darkness, List<float[]> holes) {
        if (darkness <= 0) return;
        Matrix4 old = batch.getProjectionMatrix().cpy();
        fbo.begin();
        Gdx.gl.glClearColor(0, 0, 0, (float) Math.min(1, darkness / 100.0));
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(projection);
        batch.begin();
        // dst = dst × (1 - alpha source) : efface le voile sous les halos
        batch.setBlendFunction(GL20.GL_ZERO, GL20.GL_ONE_MINUS_SRC_ALPHA);
        for (float[] h : holes) {
            float d = h[2];
            batch.draw(hole, h[0] - d / 2, LevelRenderer.DOC_HEIGHT - h[1] - d / 2, d, d);
        }
        batch.end();
        fbo.end();
        viewport.apply(); // fbo.end() remet le viewport sur toute la fenêtre
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setProjectionMatrix(sceneProjection);
        batch.begin();
        batch.draw(fboRegion, 0, 0, LevelRenderer.DOC_WIDTH, LevelRenderer.DOC_HEIGHT);
        batch.end();
        batch.setProjectionMatrix(old);
    }

    @Override
    public void dispose() {
        fbo.dispose();
        hole.dispose();
    }
}
