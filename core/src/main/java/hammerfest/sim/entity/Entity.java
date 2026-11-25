package hammerfest.sim.entity;

import hammerfest.sim.Game;
import hammerfest.sim.World;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code Entity.mt} : coordonnées réelles et de case, types, durée de vie.
 * La partie MovieClip (_x, _alpha…) est remplacée par des champs lus par le rendu.
 */
public abstract class Entity {

    public Game game;
    public World world;

    public double x, y;
    public double oldX, oldY;
    /** Case du bas de l'entité. */
    public int cx, cy;
    /** Case sous les pieds. */
    public int fcx, fcy;

    public double alpha = 100;
    public double minAlpha = 35;
    public double rotation;
    public double scaleFactor = 1;
    /** Échelle horizontale signée (-100 = retourné), comme _xscale. */
    public double xscale = 100;

    // Affichage seulement (sans effet sur la simulation) : déformations du clip "sub" et filtre de couleur,
    // comme sub._x/_y/_xscale/_yscale/_rotation et setColorHex du code d'origine.
    /** Décalage du sous-clip "sub" par rapport à sa position dans le clip (px). */
    public double subX, subY;
    /** Échelles (100 = normal) et rotation (degrés) du sous-clip "sub". */
    public double subXscale = 100, subYscale = 100, subRotation;
    /** Décalage du clip entier à l'affichage (_x = x + …, vibrations). */
    public double drawDx, drawDy;
    /**
     * Décalage vertical d'affichage propre au type (Entity._yOffset d'origine : _y = y + _yOffset) : la flèche et la
     * perle sont dessinées à hauteur du buste du joueur qui les tire (-15, -16), le zeste une case plus haut.
     */
    public double yOffset;
    /** Rotation (degrés) du sous-clip « sub » du sous-clip (sub.sub._rotation : lame de la scie) ; NaN = aucune. */
    public double innerRotation = Double.NaN;
    /** Filtre de couleur (setColorHex) : intensité 0..100 et couleur 0xRRGGBB. */
    public double colorAlpha;
    public int colorHex;

    /** Déformation du sous-clip en cours (sinon l'instantané ne l'envoie pas). */
    public boolean hasSubTransform() {
        return subX != 0 || subY != 0 || subXscale != 100 || subYscale != 100 || subRotation != 0;
    }

    /** setColorHex(a, col) : a = 0 annule le filtre (resetColor). */
    public void setColorHex(double a, int col) {
        colorAlpha = Math.max(0, a);
        colorHex = col;
    }
    public boolean visible = true;

    public int types;
    public int uniqId;
    /** Id donné par un script (sid), -1 sinon : permet e_kill / remplacement. */
    public int scriptId = -1;
    public Entity parent;

    /** NaN = pas de durée de vie (null dans le code d'origine). */
    public double lifeTimer = Double.NaN;
    public double totalLife = Double.NaN;

    public boolean fl_kill;
    public boolean fl_destroy;

    protected Entity() {
        updateCoords();
    }

    public void init(Game g) {
        game = g;
        uniqId = game.getUniqId();
        register(ENTITY);
        world = game.world;
        scale(100);
    }

    public void register(int type) {
        game.addToList(type, this);
        types |= type;
    }

    public void unregister(int type) {
        game.removeFromList(type, this);
        types ^= type;
    }

    public boolean isType(int t) {
        return (types & t) > 0;
    }

    public void setParent(Entity e) {
        parent = e;
    }

    public void setLifeTimer(double t) {
        lifeTimer = t;
        totalLife = t;
    }

    /** Change le temps de vie restant sans toucher au total initial (sauf s'il n'y en avait pas). */
    public void updateLifeTimer(double t) {
        if (Double.isNaN(totalLife)) setLifeTimer(t);
        else lifeTimer = t;
    }

    public void onLifeTimer() {
        destroy();
    }

    /** Largeur de la boîte de collision (le _width du clip d'origine). */
    public abstract double width();

    /** Hauteur de la boîte de collision (le _height du clip d'origine). */
    public abstract double height();

    public boolean hitBound(Entity e) {
        return x + width() / 2 > e.x - e.width() / 2
                && y > e.y - e.height()
                && x - width() / 2 < e.x + e.width() / 2
                && y - height() < e.y;
    }

    public void hit(Entity e) {
        // rien
    }

    public void destroy() {
        fl_kill = true;
        fl_destroy = true;
        for (int i = 0; i < 32; i++) {
            if ((types & (1 << i)) != 0) game.unregLater(1 << i, this);
        }
        game.killLater(this);
    }

    public void hide() {
        visible = false;
    }

    public void show() {
        visible = true;
    }

    public void scale(double n) {
        scaleFactor = n / 100;
        xscale = Math.signum(xscale == 0 ? 1 : xscale) * n;
    }

    // ------------------------------------------------------------------ coordonnées

    public void updateCoords() {
        cx = xRtc(x);
        cy = yRtc(y);
        fcx = xRtc(x);
        fcy = yRtc(y + Math.floor(CASE_HEIGHT / 2.0));
    }

    public static int xRtc(double n) {
        return (int) Math.floor(n / CASE_WIDTH);
    }

    public static int yRtc(double n) {
        return (int) Math.floor((n - CASE_HEIGHT / 2.0) / CASE_HEIGHT);
    }

    public static double xCtr(int n) {
        return n * CASE_WIDTH + CASE_WIDTH * 0.5;
    }

    public static double yCtr(int n) {
        return n * CASE_HEIGHT + CASE_HEIGHT;
    }

    public void adjustToLeft() {
        x = xCtr(cx);
        y = yCtr(cy);
        x -= CASE_WIDTH * 0.5 + 1;
    }

    public void adjustToRight() {
        x = xCtr(cx);
        y = yCtr(cy);
        x += CASE_WIDTH * 0.5 - 1;
    }

    public void centerInCase() {
        x = xCtr(cx);
        y = yCtr(cy);
    }

    public double distanceCase(int cx, int cy) {
        return Math.sqrt(Math.pow(cy - this.cy, 2) + Math.pow(cx - this.cx, 2));
    }

    public double distance(double x, double y) {
        return Math.sqrt(Math.pow(y - this.y, 2) + Math.pow(x - this.x, 2));
    }

    // ------------------------------------------------------------------ mises à jour

    public void update() {
        if (lifeTimer > 0) {
            lifeTimer -= game.tmod;
            if (lifeTimer <= 0) onLifeTimer();
        }
    }

    public void endUpdate() {
        updateCoords();
        oldX = x;
        oldY = y;
    }
}
