package hammerfest.sim.entity;

import hammerfest.sim.Anim;
import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/Animator.mt} : lecture des animations (image du sous-clip "sub")
 * et clignotement. Le rendu lit {@link #animId} et {@link #subFrame}.
 */
public abstract class Animator extends Trigger {

    /** Nom d'export du clip Flash de l'entité (ex. "hammer_player"). */
    public final String linkage;

    public int animId = -1;
    /** Image courante du sous-clip (1..subTotal), comme sub._currentframe. */
    public int subFrame = 1;
    public double frame;
    public double animFactor = 1.0;
    public boolean fl_anim = true;
    public boolean fl_loop;
    public boolean fl_stickyAnim;
    public boolean fl_blink = true;
    public boolean fl_alphaBlink = true;
    public double blinkTimer;
    public double blinkAlpha = 20;
    private boolean fl_blinking;
    private boolean fl_blinked = true;

    protected Animator(String linkage) {
        this.linkage = linkage;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        animId = 0;
        subFrame = 1;
    }

    public void blink(double duration) {
        if (!fl_blink) return;
        fl_blinking = true;
        blinkTimer = duration;
    }

    public boolean isBlinking() {
        return fl_blinking;
    }

    /** Relance l'animation courante depuis le début (Animator.replayAnim). */
    public void replayAnim() {
        int id = animId;
        boolean loop = fl_loop;
        playAnim(new Anim(id == 1 ? 2 : 1, loop));
        playAnim(new Anim(id, loop));
    }

    public void stopBlink() {
        fl_blinking = false;
        alpha = 100;
    }

    void blinkLife() {
        if (lifeTimer / totalLife <= 0.1) blink(BLINK_DURATION_FAST);
        else if (lifeTimer / totalLife <= 0.3) blink(BLINK_DURATION);
    }

    public void onEndAnim(int id) {
        fl_stickyAnim = false;
    }

    public void playAnim(Anim a) {
        if (fl_stickyAnim || fl_kill || !fl_anim) return;
        if (animId == a.id && fl_loop == a.loop) return;
        animId = a.id;
        subFrame = 1;
        fl_loop = a.loop;
        frame = 0;
    }

    /** Images du sous-clip de l'animation courante. */
    public int subTotal() {
        return Math.max(1, game.frames.frames(linkage, animId));
    }

    @Override
    public void update() {
        super.update();

        if (fl_blink) {
            if (!fl_blinking && lifeTimer > 0) blinkLife();
            if (fl_blinking) {
                blinkTimer -= game.tmod;
                if (blinkTimer <= 0) {
                    if (fl_blinked) {
                        alpha = 100;
                        fl_blinked = false;
                    } else {
                        alpha = blinkAlpha;
                        fl_blinked = true;
                    }
                    blinkLife();
                }
            }
        }

        if (!fl_anim) return;

        // Lecture du sous-clip
        if (frame >= 0) {
            boolean fl_break = false;
            frame += animFactor * game.tmod;
            while (!fl_break && frame >= 1) {
                if (subFrame >= subTotal()) {
                    if (fl_loop) {
                        subFrame = 1;
                    } else {
                        frame = -1;
                        onEndAnim(animId);
                        fl_break = true;
                    }
                }
                if (!fl_break) {
                    subFrame = Math.min(subFrame + 1, subTotal());
                    frame--;
                }
            }
        }
    }
}
