package hammerfest.sim.entity.bad;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/bad/Jumper.mt} : marcheur qui saute (haut, bas, par-dessus le vide) et escalade. */
public class Jumper extends Walker {

    public boolean fl_jUp, fl_jDown, fl_jH, fl_jumper, fl_climb;
    double chanceJumpH, chanceJumpUp, chanceJumpDown, chanceClimb;
    public int maxClimb;

    public Jumper(String linkage) {
        super(linkage);
    }

    /** Chances sur 100 ; négatif = désactivé. */
    public void setJumpUp(double chance) {
        fl_jUp = chance >= 0;
        chanceJumpUp = chance * 10;
        setJumper();
    }

    public void setJumpDown(double chance) {
        fl_jDown = chance >= 0;
        chanceJumpDown = chance * 10;
        setJumper();
    }

    public void setJumpH(double chance) {
        fl_jH = chance >= 0;
        chanceJumpH = chance * 10;
        setJumper();
    }

    public void setClimb(double chance, int max) {
        fl_climb = chance >= 0;
        maxClimb = fl_climb ? max : 0;
        chanceClimb = chance * 10;
        setJumper();
    }

    void setJumper() {
        fl_jumper = fl_jUp || fl_jDown || fl_jH;
    }

    boolean decideJumpUp() {
        if (fl_playerClose) return player.cy < cy && game.random(1000) < chanceJumpUp * chaseFactor && isReady();
        return game.random(1000) < chanceJumpUp && isReady();
    }

    boolean decideJumpDown() {
        if (fl_playerClose) return player.cy > cy && game.random(1000) < chanceJumpDown * chaseFactor && isReady();
        return game.random(1000) < chanceJumpDown && isReady();
    }

    boolean decideClimb() {
        boolean stairway = (world.checkFlag(cx, cy, IA_CLIMB_LEFT) || world.checkFlag(cx, cy, IA_CLIMB_RIGHT))
                && world.checkFlag(cx, cy, IA_SMALL_SPOT);
        if (fl_playerClose && player != null) {
            int d = Math.abs(player.cy - cy);
            boolean good = d >= 3 || ((player.cx < cx && dx < 0) || (player.cx > cx && dx > 0));
            return isReady() && (stairway || (good && game.random(1000) < chanceClimb * chaseFactor));
        }
        return isReady() && (stairway || game.random(1000) < chanceClimb);
    }

    public void jump(double dx, double dy, double delay) {
        halt();
        setNext(dx, dy, delay, ACTION_MOVE);
        if (delay > 0) playAnim(THINK);
    }

    void checkClimb() {
        if (!decideClimb()) return;
        if (dx < 0 && world.checkFlag(cx, cy, IA_CLIMB_LEFT)) climb(-1, cx - 1);
        if (dx > 0 && world.checkFlag(cx, cy, IA_CLIMB_RIGHT)) climb(1, cx + 1);
    }

    private void climb(int side, int wallX) {
        boolean cliff = world.checkFlag(cx, cy, IA_TILE_TOP);
        Integer h = cliff ? world.wallHeight(wallX, cy, IA_CLIMB) : world.stepHeight(cx, cy, IA_CLIMB);
        if (h == null || h < 1 || h > maxClimb) return;
        double wait = h > 1 ? SECOND : 0;
        if (cliff) {
            jump(side * BAD_VJUMP_X_CLIFF, -BAD_VJUMP_Y_LIST[h - 1], wait);
        } else {
            jump(side * BAD_VJUMP_X, -BAD_VJUMP_Y_LIST[h - 1], wait);
            x = oldX;
        }
        fl_stopStepping = true;
    }

    @Override
    public void onNext() {
        if (next != null && next.action == ACTION_MOVE && next.dy != 0) playAnim(JUMP);
        super.onNext();
    }

    @Override
    public void onFall() {
        if (fl_jumper && isReady() && fl_jH) {
            if (fl_fall && decideFall() && world.checkFlag(cx, cy, IA_ALLOW_FALL)) fl_willFallDown = true;
            if (fl_fall && fl_climb
                    && (world.checkFlag(cx, cy + 1, IA_CLIMB_LEFT) || world.checkFlag(cx, cy + 1, IA_CLIMB_RIGHT))) {
                fl_willFallDown = true;
            }
            if (!fl_willFallDown) {
                if (dx < 0 && world.checkFlag(cx, cy, IA_JUMP_LEFT)) {
                    jump(-BAD_HJUMP_X, -BAD_HJUMP_Y, 0);
                    adjustToRight();
                }
                if (dx > 0 && world.checkFlag(cx, cy, IA_JUMP_RIGHT)) {
                    jump(BAD_HJUMP_X, -BAD_HJUMP_Y, 0);
                    adjustToLeft();
                }
                if (fl_climb) checkClimb();
            }
        }
        super.onFall();
    }

    @Override
    public void onHitWall() {
        if (fl_climb) checkClimb();
        super.onHitWall();
    }

    @Override
    public void onFreeze() {
        super.onFreeze();
        fl_skipNextGround = false;
    }

    @Override
    public void onKnock() {
        super.onKnock();
        fl_skipNextGround = false;
    }

    @Override
    public void infix() {
        super.infix();
        if (!fl_jumper) return;
        updateCoords();
        if (fl_jUp && decideJumpUp() && world.checkFlag(cx, cy, IA_JUMP_UP)) {
            jump(0, -BAD_VJUMP_Y, SECOND);
            fl_stopStepping = true;
        }
        if (fl_jDown && decideJumpDown() && world.checkFlag(cx, cy, IA_JUMP_DOWN)) {
            jump(0, -BAD_VDJUMP_Y, SECOND);
            fl_skipNextGround = true;
            fl_stopStepping = true;
        }
    }
}
