package hammerfest.sim.entity;

import hammerfest.sim.Anim;
import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/Bad.mt} : ennemi (gel, assommage, colère, collisions avec le joueur
 * et entre ennemis gelés, mort par la ligne du bas).
 *
 * <p>Mode Ninjutsu : {@link #fl_ninFoe} (cible à abattre) et {@link #fl_ninFriend} (à épargner).</p>
 */
public abstract class Bad extends Mover implements Freezable {

    public static final Anim WALK = new Anim(0, true);
    public static final Anim ANGER = new Anim(1, true);
    public static final Anim FREEZE = new Anim(2, false);
    public static final Anim KNOCK = new Anim(3, true);
    public static final Anim DIE = new Anim(4, true);
    public static final Anim SHOOT_START = new Anim(5, false);
    public static final Anim SHOOT_END = new Anim(6, false);
    public static final Anim THINK = new Anim(7, false);
    public static final Anim JUMP = new Anim(8, true);
    public static final Anim SHOOT_LOOP = new Anim(9, true);

    public Player player;
    public boolean fl_playerClose;
    public int closeDistance = IA_CLOSE_DISTANCE;
    public double freezeTimer;
    public double freezeTotal;
    public boolean fl_freeze;
    public double knockTimer;
    public boolean fl_knock;
    public double deathTimer;
    public boolean fl_trap = true;
    /** Id de la bombe à l'origine du combo ; -1 = null. */
    public int comboId = -1;
    /** NaN = null. */
    public double yTrigger = Double.NaN;
    public int anger;
    public double angerFactor = 0.7;
    public double speedFactor = 1.0;
    public double chaseFactor = 5.0;
    public int maxAnger = MAX_ANGER;
    /** Rayon de contact réel avec le joueur (NaN = pas de test supplémentaire). */
    public double realRadius = CASE_WIDTH;

    protected Bad(String linkage) {
        super(linkage);
        fl_strictGravity = false;
        fl_largeTrigger = true;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        register(BAD);
        register(BAD_CLEAR);
        calcSpeed();
    }

    /** Place l'ennemi au sol de sa case (Bad.initBad). */
    public void initBad(Game g, double x, double y) {
        init(g);
        moveTo(x + CASE_WIDTH * 0.5, y + CASE_HEIGHT);
        endUpdate();
    }

    @Override
    public double width() {
        return 24 * scaleFactor;
    }

    @Override
    public double height() {
        return 26 * scaleFactor;
    }

    // ------------------------------------------------------------------ états

    @Override
    public void killHit(double dx) {
        if (fl_freeze) {
            game.particles(PARTICLE_ICE, x, y, 2, 3); // le bloc de glace éclate
            game.explosion(x, y - CASE_HEIGHT * 0.5, CASE_WIDTH * 2);
            melt();
        }
        if (fl_knock) wakeUp();
        if (Double.isNaN(dx)) dx = game.random(200) / 10.0 * (game.random(2) * 2 - 1);
        playAnim(DIE);
        super.killHit(dx);
    }

    public void forceKill(double dx) {
        killHit(dx);
    }

    /** Mort sur place (boule de feu) : récompense immédiate. */
    public void burn() {
        game.fx("hammer_fx_burning", x, y);
        dropReward();
        onKill();
        destroy();
    }

    public boolean isHealthy() {
        return !fl_kill && !fl_freeze && !fl_knock;
    }

    @Override
    public void hit(Entity e) {
        if ((e.types & PLAYER) > 0) {
            Player p = (Player) e;
            boolean fl_hit = Double.isNaN(realRadius) || distance(p.x, p.y) <= realRadius;
            if (fl_hit) {
                if (p.specialMan.active(86)) { // aura : l'ennemi touché est détruit
                    game.fx("hammer_fx_shine", x, y - CASE_HEIGHT);
                    p.getScore(this, 666);
                    destroy();
                } else if (isHealthy()) {
                    if (p.specialMan.active(114) && p.oldY <= y - CASE_HEIGHT * 0.5 && p.dy > 0) {
                        // malédiction Mario : on rebondit sur l'ennemi, qui gèle
                        p.dy = -PLAYER_AIR_JUMP * 2.5;
                        freeze(FREEZE_DURATION);
                        setCombo(-1);
                        game.explodeZone(x, y - 5, 30);
                        game.particles(PARTICLE_CLASSIC_BOMB, x, y - 5, 3, 3);
                    } else if (!p.fl_shield) {
                        p.killHit(p.x < x ? -1 : 1);
                    }
                }
            }
        }
        if ((e.types & BAD) > 0) {
            Bad b = (Bad) e;
            if (fl_freeze && !b.fl_freeze) {
                double spd = evaluateSpeed();
                if (spd >= ICE_HIT_MIN_SPEED) {
                    b.setCombo(comboId);
                    b.killHit(dx);
                } else if (spd >= ICE_KNOCK_MIN_SPEED) {
                    b.setCombo(comboId);
                    b.knock(KNOCK_DURATION);
                }
            }
        }
    }

    @Override
    public void setCombo(int id) {
        if (!fl_kill) comboId = id;
    }

    @Override
    public void playAnim(Anim a) {
        if (a.id == WALK.id && anger > 0) super.playAnim(ANGER);
        else super.playAnim(a);
    }

    public void calcSpeed() {
        speedFactor = 1.0 + angerFactor * anger;
        if (game != null && game.globalActives[69]) speedFactor *= 0.6; // tortue
        if (game != null && game.globalActives[80]) speedFactor *= 0.3; // escargot
    }

    public void updateSpeed() {
        calcSpeed();
    }

    public double evaluateSpeed() {
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Pas de récompense à la mort (ennemis nés des bombes du boss : dropReward = null). */
    public boolean noReward;

    /** Récompense : cristal de combo (plus gros à chaque ennemi de la même bombe) ou diamant. */
    public void dropReward() {
        if (noReward) return;
        double itY = world.inBound(cx, cy) ? y : -30;
        if (comboId >= 0) {
            int n = game.countCombo(comboId) - 1;
            game.maxCombo = Math.max(game.maxCombo, n + 1);
            ScoreItem.attach(game, x, itY, 0, n);
        } else {
            ScoreItem.attach(game, x, itY, DIAMANT, -1);
        }
    }

    @Override
    public boolean needsPatch() {
        return fl_freeze || fl_knock;
    }

    @Override
    public void freeze(double timer) {
        if (fl_kill) return;
        if (fl_knock) wakeUp();
        fallFactor = FALL_FACTOR_FROZEN;
        fl_slide = true;
        freezeTimer = timer;
        freezeTotal = timer;
        fl_freeze = true;
        onFreeze();
    }

    public void knock(double timer) {
        if (fl_freeze) melt();
        fallFactor = FALL_FACTOR_KNOCK;
        knockTimer = timer;
        fl_knock = true;
        onKnock();
    }

    public void melt() {
        next = null;
        angerMore();
        freezeTimer = 0;
        fl_freeze = false;
        fl_slide = false;
        fallFactor = 1.0;
        onMelt();
    }

    public void wakeUp() {
        next = null;
        knockTimer = 0;
        fl_knock = false;
        fallFactor = 1.0;
        onWakeUp();
    }

    public void calmDown() {
        if (fl_kill) return;
        anger = 0;
        updateSpeed();
    }

    public void angerMore() {
        anger = Math.min(anger + 1, maxAnger);
        updateSpeed();
    }

    public void hate(Player p) {
        player = p;
    }

    // ------------------------------------------------------------------ événements

    @Override
    public void onDeathLine() {
        game.fx("hammer_fx_death", x, GAME_HEIGHT * 0.5);
        game.shake(10, 3);
        game.sound("sound_bad_death");
        deathTimer = 0;
        super.onDeathLine();
        if (!fl_kill) onKill();
        dropReward();
        destroy();
    }

    public void onMelt() {
    }

    public void onWakeUp() {
    }

    public void onFreeze() {
        playAnim(FREEZE);
    }

    public void onKnock() {
        playAnim(KNOCK);
    }

    public void onHurryUp() {
        angerMore();
    }

    /** Ninjutsu : cible désignée (la tuer change les autres en cristaux) ou ami à épargner (le tuer tue le joueur). */
    public boolean fl_ninFoe, fl_ninFriend;

    @Override
    public void onKill() {
        super.onKill();
        if (fl_ninFriend) { // ami tué : les joueurs meurent
            for (Player p : game.players()) {
                if (!p.fl_kill) {
                    if (p.fl_shield) p.unshield();
                    p.killHit(game.random(20));
                    game.alert("static.44", ""); // « Cible interdite ! »
                }
            }
        }
        if (fl_ninFoe) { // cible tuée : les autres ennemis deviennent des cristaux
            int n = 1;
            for (Entity e : new java.util.ArrayList<>(game.getList(BAD_CLEAR))) {
                Bad b = (Bad) e;
                b.fl_ninFriend = false;
                if (b.uniqId != uniqId) {
                    ScoreItem.attach(game, b.x, b.y, 0, n);
                    b.destroy();
                    n++;
                }
            }
            ScoreItem.attach(game, GAME_WIDTH * 0.5, -20, 236, -1);
            game.alert("static.45", ""); // « Cible éliminée. »
        }
        deathTimer = SECOND * 5;
        game.onKillBad(this);
    }

    @Override
    public void onHitGround(double h) {
        super.onHitGround(h);
        if (fl_freeze) game.particles(PARTICLE_ICE_BAD, x, y, 2, 3);
        if (fl_freeze && h >= DUST_FALL_HEIGHT) game.dust(cx, cy + 1);
    }

    // ------------------------------------------------------------------ mises à jour

    @Override
    public void endUpdate() {
        double ox = oldX, oy = oldY;
        super.endUpdate();
        if (fl_freeze && fl_stable && Math.abs(dx) > 2) { // Bad.endUpdate : éclats de glace du bloc qui glisse
            int nb = game.visualRandom(5) + 2;
            for (int i = 0; i < nb; i++) {
                double s = game.visualRandom(80) + 20;
                game.fx("hammer_fx_partIce", ox + game.visualRandom(12) * (game.visualRandom(2) * 2 - 1),
                        oy - game.visualRandom(5) + 2, game.visualRandom(360), s, s, Math.min(100, (Math.abs(dx) - 2) / 5 * 100));
            }
        }
        if (!fl_stable && isHealthy() && (animId < 0 || animId == WALK.id || animId == ANGER.id)) {
            playAnim(JUMP);
        }
    }

    @Override
    public void update() {
        if (player == null || player.fl_destroy) {
            Entity p = game.getOne(PLAYER);
            hate(p instanceof Player ? (Player) p : null);
        }
        fl_playerClose = player != null && isHealthy()
                && distance(player.x, player.y) <= closeDistance * (anger + 1);

        if (freezeTimer > 0) {
            freezeTimer -= game.tmod;
            if (freezeTimer <= 0) melt();
        }
        if (deathTimer > 0) {
            deathTimer -= game.tmod;
            if (deathTimer <= 0) {
                game.explosion(x, y, 80);
                y = 1000;
            }
        }
        if (knockTimer > 0) {
            knockTimer -= game.tmod;
            if (knockTimer <= 0) wakeUp();
        }

        super.update();

        if (!Double.isNaN(yTrigger) && !fl_kill && y >= yTrigger) {
            fl_hitGround = true;
            yTrigger = Double.NaN;
        }
        if (comboId >= 0 && isHealthy()) setCombo(-1);
    }
}
