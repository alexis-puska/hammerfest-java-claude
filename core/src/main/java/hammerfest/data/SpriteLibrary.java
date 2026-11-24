package hammerfest.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Description des clips Flash exportés (assets/sprites/&lt;lib&gt;.json), produite par
 * {@code tools/SwfLayout} depuis le XML de JPEXS FFDec.
 *
 * <p>Chaque clip ({@link Clip}) a un nombre d'images et un cadre (origine du clip dans ses PNG,
 * en pixels). Les clips d'entité (ex. {@code hammer_player}) placent à chaque image un sous-clip
 * nommé {@code sub} : c'est lui qui porte l'animation, l'image du clip parent étant l'état
 * (cf. Animator : {@code gotoAndStop(animId+1)} puis lecture de {@code sub}).</p>
 */
public class SpriteLibrary {

    /** Nom d'export (linkage) → id du clip. */
    public Map<String, Integer> symbols = new TreeMap<>();
    /** Id → clip. */
    public Map<Integer, Clip> clips = new TreeMap<>();
    /**
     * Parties nommées d'un symbole pilotées par script ("symbole/nom" → placement dans la 1re image) : corps
     * tourné et yeux de la boule de feu du hurry up…
     */
    public Map<String, Placement> parts = new TreeMap<>();

    public static class Clip {
        public int frames;
        /** Coin haut-gauche du cadre des PNG, relatif à l'origine du clip (pixels, y vers le bas). */
        public float x;
        public float y;
        public float w;
        public float h;
        /** Placement du sous-clip "sub" pour chaque image (null si l'image n'en a pas). */
        public List<Placement> subs = new ArrayList<>();
        /** Première image (1..n) portant un stop() ; 0 = l'animation boucle. */
        public int stopFrame;
        /** Toutes les images (1..n) portant un stop() : un clip relancé par play() s'arrête à la suivante. */
        public List<Integer> stopFrames = new ArrayList<>();
        /** Image (1..n) portant this.removeMovieClip() : le clip disparaît ; 0 = jamais. */
        public int removeFrame;
        /**
         * Images réellement jouées en boucle quand une image saute ailleurs par gotoAndPlay avant d'être affichée
         * (flamme de la torche : image 8 → gotoAndPlay(1 ou 2)) ; 0 = toutes les images.
         */
        public int loopLength;
        /** Image où reprend la boucle après le saut (gotoAndPlay("loop") du bouclier : 19) ; 0 ou 1 = début. */
        public int loopStart;
        /**
         * Couches animées par image (index = image - 1, null = aucune) : clips enfants qui jouent tout seuls dans une
         * image figée (étoiles d'un ennemi assommé, ailes du tzongre…), retirés de l'image à l'assemblage et
         * redessinés à leur image courante, dessous ({@link Placement#under}) ou dessus.
         */
        public List<List<Placement>> layers;
    }

    /** Clip enfant placé avec une matrice affine Flash (x' = a·x + c·y + tx ; y' = b·x + d·y + ty). */
    public static class Placement {
        public int id;
        /** Clip animé qui joue tout seul en boucle (l'image suit le temps, pas l'entité). */
        public boolean loop;
        /** Couche animée dessinée sous l'image (sinon par-dessus). */
        public boolean under;
        /**
         * Couche affichée à la demande seulement (absente de l'image : balle de la fraise) : nom de la partie, que
         * l'entité doit demander ({@code Sprites.visiblePart}) ; null = couche toujours dessinée.
         */
        public String part;
        public float a = 1;
        public float b;
        public float c;
        public float d = 1;
        public float tx;
        public float ty;
    }

    public Clip symbol(String linkage) {
        Integer id = symbols.get(linkage);
        return id == null ? null : clips.get(id);
    }
}
