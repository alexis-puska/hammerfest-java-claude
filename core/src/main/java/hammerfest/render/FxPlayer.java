package hammerfest.render;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Effets visuels ponctuels (portage de {@code Animation} / {@code FxManager.attachFx}) : un clip
 * Flash joué image par image, une image par cycle de jeu, puis retiré à la fin de sa timeline.
 */
public class FxPlayer {

    /** Nombre maximal d'effets simultanés (Data.MAX_FX). */
    static final int MAX_FX = 64;

    private static final class Fx {
        final String linkage;
        final float x, y, scale;
        /** Retouches du clip : _xscale / _yscale (facteurs, négatif = retourné), _rotation, _alpha. */
        float sx = 1, sy = 1, rotation, alpha = 1;
        /** Mode de fusion overlay (FxManager.attachExplosion) : approché par un éclaircissement. */
        final boolean overlay;
        final int total;
        int frame = 1;

        Fx(String linkage, float x, float y, int total, float scale, boolean overlay) {
            this.overlay = overlay;
            this.linkage = linkage;
            this.x = x;
            this.y = y;
            this.total = total;
            this.scale = scale;
        }
    }

    private final Sprites sprites;
    private final List<Fx> list = new ArrayList<>();

    public FxPlayer(Sprites sprites) {
        this.sprites = sprites;
    }

    /** Ajoute un effet en coordonnées de jeu (Flash, y vers le bas). Ignoré si le clip n'existe pas. */
    public void attach(String linkage, double x, double y) {
        attach(linkage, x, y, 1);
    }

    /** Variante à l'échelle {@code scale} (_xscale = _yscale). */
    public void attach(String linkage, double x, double y, float scale) {
        attach(linkage, x, y, scale, false);
    }

    /** Variante en mode overlay (éclair lumineux). */
    public void attach(String linkage, double x, double y, float scale, boolean overlay) {
        if (!sprites.has(linkage)) return;
        if (list.size() >= MAX_FX) list.remove(0);
        list.add(new Fx(linkage, (float) x, (float) y, Math.max(1, sprites.frames(linkage, -1)), scale, overlay));
    }

    /**
     * Effet retouché après coup (attachFx puis _rotation, _xscale, _yscale, _alpha) : échelles en facteurs
     * (négatif = retourné), rotation en degrés, alpha de 0 à 1.
     */
    public void attach(String linkage, double x, double y, float sx, float sy, float rotation, float alpha) {
        attach(linkage, x, y, sx, sy, rotation, alpha, 1);
    }

    /** Variante qui démarre à l'image {@code startFrame} (gotoAndStop puis lecture : grains de poussière). */
    public void attach(String linkage, double x, double y, float sx, float sy, float rotation, float alpha, int startFrame) {
        if (!sprites.has(linkage)) return;
        if (list.size() >= MAX_FX) list.remove(0);
        Fx f = new Fx(linkage, (float) x, (float) y, Math.max(1, sprites.frames(linkage, -1)), 1, false);
        f.sx = sx;
        f.sy = sy;
        f.rotation = rotation;
        f.alpha = alpha;
        f.frame = Math.max(1, Math.min(startFrame, f.total));
        list.add(f);
    }

    /** Un cycle de jeu : image suivante ; l'effet disparaît après sa dernière image. */
    public void tick() {
        for (Iterator<Fx> it = list.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            if (f.frame >= f.total) it.remove();
            else f.frame++;
        }
    }

    public void clear() {
        list.clear();
    }

    public void render(SpriteBatch batch, float xOffset, float worldHeight) {
        for (Fx f : list) {
            if (f.overlay) batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_DST_COLOR, com.badlogic.gdx.graphics.GL20.GL_ONE);
            if (f.alpha < 1) batch.setColor(1, 1, 1, f.alpha);
            sprites.draw(batch, f.linkage, -1, f.frame, xOffset + f.x, f.y, f.scale * f.sx, f.scale * f.sy, f.rotation, worldHeight);
            if (f.alpha < 1) batch.setColor(1, 1, 1, 1);
            if (f.overlay) batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }
}
