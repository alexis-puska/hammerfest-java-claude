package hammerfest.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;

/**
 * Icône d'un objet (score ou spécial) dessinée avec son clip du jeu, centrée et réduite pour tenir dans le widget.
 * Un objet jamais trouvé est dessiné en silhouette sombre.
 */
public class ItemIcon extends Widget {

    /** Agrandissement maximal : les petits objets ne sont pas étirés au-delà, pour rester nets. */
    private static final float MAX_SCALE = 2.5f;

    private final Sprites sprites;
    private final String linkage;
    private final int animId;
    private final boolean silhouette;
    private final float size;
    private final float[] box = new float[4];
    private final boolean ok;

    /** @param itemId id de données : 1000+ objet à points, 0..999 objet spécial */
    public ItemIcon(Sprites sprites, int itemId, boolean silhouette, float size) {
        this.sprites = sprites;
        this.linkage = itemId >= 1000 ? "hammer_item_score" : "hammer_item_special";
        this.animId = itemId >= 1000 ? itemId - 1000 : itemId; // Item.initItem : gotoAndStop(id+1)
        this.silhouette = silhouette;
        this.size = size;
        this.ok = sprites != null && sprites.has(linkage) && sprites.bounds(linkage, animId, box);
    }

    @Override
    public float getPrefWidth() {
        return size;
    }

    @Override
    public float getPrefHeight() {
        return size;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!ok || !(batch instanceof SpriteBatch)) return;
        float scale = Math.min(MAX_SCALE, Math.min(getWidth() / box[2], getHeight() / box[3]));
        float cx = getX() + getWidth() / 2, cy = getY() + getHeight() / 2;
        // Centre du cadre Flash (y vers le bas) placé au centre du widget (y vers le haut) :
        // x gdx = x + scale·X', y gdx = worldHeight - scale·Y'
        float x = cx - scale * (box[0] + box[2] / 2);
        float worldHeight = cy + scale * (box[1] + box[3] / 2);
        Color old = batch.getColor().cpy();
        if (silhouette) batch.setColor(0.1f, 0.1f, 0.15f, 0.55f * parentAlpha);
        else batch.setColor(1, 1, 1, parentAlpha);
        sprites.draw((SpriteBatch) batch, linkage, animId, 1, x, 0, scale, scale, worldHeight);
        batch.setColor(old);
    }
}
