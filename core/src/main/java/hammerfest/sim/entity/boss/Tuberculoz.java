package hammerfest.sim.entity.boss;

import hammerfest.sim.Anim;
import hammerfest.sim.Game;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.BadBomb;
import hammerfest.sim.entity.Bomb;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Mover;
import hammerfest.sim.entity.Physics;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.PlayerBomb;
import hammerfest.sim.entity.ScoreItem;
import hammerfest.sim.entity.SpecialItem;
import hammerfest.sim.entity.bad.Fruits;
import hammerfest.sim.entity.shoot.EnemyFireBall;

import java.util.HashSet;
import java.util.Set;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/boss/Tuberculoz.mt} : boss final (niveau 101), 100 points de vie.
 * Il marche au sol et alterne embrasement (pluie de boules de feu), tornade et charge ; il fait
 * apparaître des bombes qui deviennent des oranges. Une explosion de bombe près de lui lui retire
 * 1 vie, un ennemi gelé projeté sur lui 10 vies. Vaincu : cape, clé et porte de sortie.
 */
public class Tuberculoz extends Mover {

    // Actions (indépendantes des actions de Mover)
    static final int WALK = 101, JUMP = 102, DASH = 103, BOMB_ACT = 104, HIT = 105, BURN = 106,
            TORNADO = 107, TORNADO_END = 108, DIE = 109;

    static final Anim A_WAIT = new Anim(0, true);
    static final Anim A_SWITCH = new Anim(1, false);
    static final Anim A_JUMP_UP = new Anim(2, false);
    static final Anim A_JUMP_DOWN = new Anim(3, false);
    static final Anim A_JUMP_LAND = new Anim(4, false);
    static final Anim A_TORNADO_START = new Anim(5, false);
    static final Anim A_TORNADO_END = new Anim(6, false);
    static final Anim A_BAT_FORM = new Anim(7, false);
    static final Anim A_BURN_START = new Anim(8, false);
    static final Anim A_DEATH = new Anim(9, false);
    static final Anim A_DASH_START = new Anim(10, false);
    static final Anim A_DASH = new Anim(11, false);
    static final Anim A_BOMB = new Anim(12, false);
    static final Anim A_HIT = new Anim(13, false);
    static final Anim A_DASH_BUILD = new Anim(14, true);
    static final Anim A_BURN_LOOP = new Anim(15, true);
    static final Anim A_TORNADO_LOOP = new Anim(16, true);
    static final Anim A_DASH_LOOP = new Anim(17, true);

    static final int SEQ_BURN = 0, SEQ_TORNADO = 1, SEQ_DASH = 2, LAST_SEQ = 2;
    static final double SEQ_DURATION = SECOND * 9;
    public static final int LIVES = 100;
    static final double GROUND_Y = 446;
    static final double CENTER_OFFSET = -28;
    static final double HEAD_OFFSET_X = 10, HEAD_OFFSET_Y = -54;
    static final double RADIUS = CASE_WIDTH * 1.7, HEAD_RADIUS = CASE_WIDTH * 0.9;
    static final int MAX_BADS = 3;
    static final double WALK_SPEED = 3, DASH_SPEED = 16, FIREBALL_SPEED = 3;
    static final double TORNADO_INTRO = SECOND * 2, TORNADO_DURATION = SECOND * 7;
    static final double JUMP_Y = 15, JUMP_EST_X = 80, JUMP_EST_Y = -145;
    static final int A_STEP = 5, B_STEP = 10, EXTRA_LIFE_STEP = 30;
    static final int CHANCE_PLAYER_JUMP = 22, CHANCE_BOMB_JUMP = 15, CHANCE_SPAWN = 25, CHANCE_FINAL_ANGER = 35;

    public int lives = LIVES;
    public int dir;
    public int action;
    public boolean fl_immune, fl_death, fl_defeated, fl_twister;
    double immuneTimer, seqTimer = SEQ_DURATION, fbCoolDown, defeatTimeOut;
    int seq, dashCount, badKills, totalKills;
    final Set<Integer> recents = new HashSet<>();
    SpecialItem itemA, itemB;

    public Tuberculoz() {
        super("hammer_boss_human");
        fl_hitGround = false;
        fl_hitCeil = false;
        fl_hitWall = false;
        fl_hitBorder = true;
        fl_gravity = false;
        fl_friction = false;
        fl_moveable = false;
        fl_blink = true;
    }

    public static Tuberculoz attach(Game g) {
        Tuberculoz t = new Tuberculoz();
        t.init(g);
        t.dir = g.random(2) * 2 - 1;
        t.x = GAME_WIDTH * 0.5;
        t.y = GROUND_Y;
        t.updateCoords();
        t.animId = -1;
        t.playAnim(A_BAT_FORM);
        t.endUpdate();
        return t;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        register(BOSS);
    }

    @Override
    public double width() {
        return RADIUS * 2;
    }

    @Override
    public double height() {
        return 80;
    }

    // Hors du système de cases : contacts calculés à chaque cycle (checkHits)
    @Override
    public void tAdd(int cx, int cy) {
    }

    @Override
    public void tRem(int cx, int cy) {
    }

    java.util.List<Entity> getHitList(int type) {
        java.util.List<Entity> l = game.getClose(type, x, y + CENTER_OFFSET, RADIUS, false);
        if (action == WALK) l.addAll(game.getClose(type, x + HEAD_OFFSET_X * dir, y + HEAD_OFFSET_Y, HEAD_RADIUS, false));
        return l;
    }

    @Override
    public void checkHits() {
        if (fl_death) return;
        for (Entity e : getHitList(PLAYER_BOMB)) {
            PlayerBomb b = (PlayerBomb) e;
            if (!b.fl_explode && action == DASH && dx != 0) b.onExplode();
            if (!b.fl_explode && recents.add(b.uniqId)) kickBomb(b);
        }
        if (!fl_immune) {
            for (Entity e : getHitList(PLAYER)) {
                Player p = (Player) e;
                if (!p.fl_kill) {
                    p.killHit(dx);
                    if (action == DASH && dx != 0) game.explodeZone(p.x, p.y - CASE_HEIGHT * 0.5, CASE_WIDTH * 2);
                    game.shake(SECOND, 3);
                }
            }
        }
        for (Entity e : getHitList(BAD)) {
            Bad b = (Bad) e;
            if (b.uniqId == uniqId || b.fl_kill || !b.fl_trap) continue;
            if (!fl_immune && b.fl_freeze && b.evaluateSpeed() >= ICE_HIT_MIN_SPEED) {
                game.particles(PARTICLE_CLASSIC_BOMB, x, y, 4, 0);
                game.explodeZone(b.x, b.y - CASE_HEIGHT * 0.5, CASE_WIDTH * 2);
                loseLife(10);
                onKillBad();
                b.destroy();
            } else {
                b.killHit(dx);
            }
        }
    }

    void loseLife(int n) {
        if (fl_immune) return;
        lives -= n;
        if (lives <= 0) {
            die();
            return;
        }
        fl_immune = true;
        immuneTimer = SECOND * 3;
        blink(BLINK_DURATION_FAST);
        if (n > 1) {
            fl_gravity = true;
            fl_hitBorder = true;
            dy = -9;
            next = null;
            action = HIT;
            playAnim(A_HIT);
            game.shake(SECOND, 4);
            game.destroyList(SHOOT);
            for (Player p : game.players()) p.knock(SECOND);
            for (Entity e : new java.util.ArrayList<>(game.getList(BAD))) {
                Bad b = (Bad) e;
                if (fl_stable) b.dx = 0;
                b.knock(KNOCK_DURATION);
            }
        }
    }

    void spawnBombs(int n) {
        java.util.List<Entity> placed = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            BossBomb b = BossBomb.attach(game, 0, 0);
            for (int guard = 0; guard < 100; guard++) {
                b.moveTo(game.random((int) Math.round(GAME_WIDTH * 0.8)) + GAME_WIDTH * 0.1, game.random(150) + 100);
                boolean tooClose = false;
                for (Entity o : placed) if (o.distance(b.x, b.y) <= CASE_WIDTH * 6) tooClose = true;
                if (!tooClose) break;
            }
            game.fx("hammer_fx_pop", b.x, b.y - CASE_HEIGHT * 0.5);
            placed.add(b);
        }
    }

    void kickBomb(Bomb b) {
        b.dx = dx == 0 ? dir * 15 : dx * 5;
        b.dy = -7;
        b.setLifeTimer(SECOND * 0.7);
        if ((b.x < x && dir > 0) || (b.x > x && dir < 0)) b.dx *= -1;
    }

    boolean isWindCompatible(Entity e) {
        if (!fl_twister && (e.types & FX) == 0) return false;
        if (e.fl_kill || e.uniqId == uniqId) return false;
        if ((e.types & SHOOT) > 0 || (e.types & ITEM) > 0) return false;
        if ((e.types & BAD) > 0 && ((Bad) e).fl_freeze) return false;
        return e.y >= 30;
    }

    void updateDash() {
        // particules d'entrée sur les bords de l'écran
        if ((oldX < 0 && x >= 0) || (oldX > 0 && x <= 0)) game.particles(PARTICLE_STONE, 10, y - 30, 6, 0);
        if ((oldX > GAME_WIDTH && x <= GAME_WIDTH) || (oldX < GAME_WIDTH && x >= GAME_WIDTH)) {
            game.particles(PARTICLE_STONE, GAME_WIDTH - 10, y - 30, 6, 0);
        }
        if ((dx < 0 && x <= -GAME_WIDTH) || (dx > 0 && x >= GAME_WIDTH * 2)) {
            Entity p = game.getOne(PLAYER);
            dx = -dx;
            if (p != null) y = p.y;
            dir = -dir;
            dashCount++;
            if (dashCount > 2) {
                x = dir < 0 ? GAME_WIDTH + 80 : -80;
                y = GROUND_Y;
                fl_hitBorder = true;
                jump(JUMP_Y * 0.6);
            }
        }
    }

    void updateTornado() {
        if (game.random(3) == 0) return;
        if (fl_twister || game.visualRandom(5) == 0) {
            game.particles(PARTICLE_DUST, game.visualRandom(GAME_WIDTH), game.visualRandom(GAME_HEIGHT), 0, 3);
        }
        for (Entity e : new java.util.ArrayList<>(game.getList(PHYSICS))) {
            if (!isWindCompatible(e)) continue;
            Physics p = (Physics) e;
            double wind = game.random(22) / 10.0 + 0.5;
            if (p.fl_stable) {
                if (wind > GRAVITY) {
                    p.dy -= wind + 3;
                    p.dx += game.random(2) * (game.random(2) * 2 - 1);
                }
            } else {
                p.dy -= wind;
                p.dx += game.random(2) * (game.random(2) * 2 - 1);
            }
        }
    }

    void updateDeath() {
        if ((dx != 0 || dy != 0 || (next != null && next.action == DIE)) && game.random(3) == 0) { // roches
            if (game.visualRandom(2) == 0) game.particles(PARTICLE_STONE, game.visualRandom(GAME_WIDTH), 0, 0, 2);
            else game.particles(PARTICLE_STONE, game.visualRandom(GAME_WIDTH), game.visualRandom((int) Math.round(GAME_HEIGHT * 0.6)), 0, 2);
            game.shake(SECOND, 1);
        }
        if (dy > 0 && y >= GROUND_Y - 8) {
            game.shake(SECOND, 5);
            land();
            y = GROUND_Y - 8;
        }
        if (next != null && next.action == DIE) {
            dx *= game.xFriction;
            if (game.random(3) == 0) {
                game.fx("hammer_fx_strike", GAME_WIDTH * 0.5, y - game.random(60));
                playAnim(A_HIT);
                replayAnim();
                dx = dx < 0 ? game.random(4) + 2 : -(game.random(4) + 2);
            }
        }
        if (animId != A_DEATH.id && game.random(3) == 0) {
            game.explodeZone(game.random(20) * (game.random(2) * 2 - 1) + x, y - game.random(70), game.random(20) + 8);
        }
    }

    void ia() {
        if (action != WALK) return;
        seqTimer -= game.tmod;
        if ((x >= GAME_WIDTH - RADIUS && dir > 0) || (x <= RADIUS && dir < 0)) {
            dir = -dir;
            walk();
            return;
        }
        if (game.random(1000) < CHANCE_PLAYER_JUMP
                && !game.getClose(PLAYER, x + JUMP_EST_X * dir, y + JUMP_EST_Y, RADIUS, false).isEmpty()) {
            jump(JUMP_Y);
            return;
        }
        if (game.random(1000) < CHANCE_BOMB_JUMP
                && !game.getClose(PLAYER_BOMB, x + JUMP_EST_X * dir, y + JUMP_EST_Y, RADIUS, false).isEmpty()) {
            jump(JUMP_Y);
            return;
        }
        if (seqTimer <= 0 && !fl_death) {
            seqTimer = SEQ_DURATION;
            switch (seq) {
                case SEQ_BURN: burn(); break;
                case SEQ_DASH: dash(); break;
                case SEQ_TORNADO: tornado(); break;
                default: break;
            }
            seq = seq >= LAST_SEQ ? 0 : seq + 1;
        }
        if (game.random(1000) < CHANCE_SPAWN && game.countList(BAD) + game.countList(BAD_BOMB) < MAX_BADS) {
            dropBombs();
        }
    }

    void halt() {
        dx = 0;
    }

    void walk() {
        if (fl_death) {
            setNext(Double.NaN, Double.NaN, SECOND * 3, DIE);
            return;
        }
        if (xscale * dir < 0) {
            playAnim(A_SWITCH);
            halt();
        } else {
            playAnim(A_WAIT);
            xscale = dir * Math.abs(xscale);
            dx = WALK_SPEED * dir;
            action = WALK;
        }
    }

    void jump(double jumpY) {
        action = JUMP;
        fl_gravity = true;
        dx = dir * WALK_SPEED * 1.6;
        dy = -jumpY;
        playAnim(A_JUMP_UP);
    }

    void land() {
        action = 0;
        fl_gravity = false;
        y = GROUND_Y;
        dy = 0;
        playAnim(A_JUMP_LAND);
    }

    void dash() {
        action = DASH;
        halt();
        dashCount = 0;
        playAnim(A_DASH_START);
    }

    void dropBombs() {
        action = BOMB_ACT;
        halt();
        playAnim(A_BOMB);
    }

    void burn() {
        halt();
        playAnim(A_BURN_START);
        setNext(Double.NaN, Double.NaN, SECOND * 3.5, BURN);
        action = BURN;
    }

    void tornado() {
        halt();
        playAnim(A_TORNADO_START);
        setNext(Double.NaN, Double.NaN, TORNADO_INTRO, TORNADO);
        fl_twister = false;
        action = TORNADO;
    }

    void die() {
        halt();
        action = 0;
        defeatTimeOut = SECOND * 15;
        fl_death = true;
        fl_gravity = true;
        dx = (GAME_WIDTH * 0.5 - x) * 0.025;
        dy = -13;
        dir = dx > 0 ? -1 : 1;
        xscale = 100 * dir;
        game.shake(SECOND, 5);
        playAnim(A_HIT);
        for (Entity e : new java.util.ArrayList<>(game.getList(BAD))) {
            Bad b = (Bad) e;
            b.noReward = true;
            b.killHit(game.random(50) * (game.random(2) * 2 - 1));
            b.dy -= game.random(10);
            game.particles(PARTICLE_SPARK, b.x, b.y, 1, 3);
        }
        for (Entity e : game.getList(BOMB)) game.particles(PARTICLE_CLASSIC_BOMB, e.x, e.y, 1, 3);
        game.destroyList(SHOOT);
        game.destroyList(BOMB);
    }

    /** Victoire : sortie ouverte, ennemis détruits. */
    void finalBlow() {
        if (fl_defeated) return;
        halt();
        openExit();
        game.explodeZone(x, y + CENTER_OFFSET, 150);
        game.particles(PARTICLE_TUBERCULOZ, x, y + CENTER_OFFSET, MAX_FX, 0);
        game.shake(SECOND, 5);
        playAnim(A_DEATH);
        game.destroyList(BAD);
        game.fl_clear = true;
        fl_defeated = true;
    }

    void openExit() {
        SpecialItem cloak = SpecialItem.attach(game, GAME_WIDTH * 0.5, GAME_HEIGHT - 40, 113, -1);
        if (cloak != null) cloak.dy = -20;
        ScoreItem key = ScoreItem.attach(game, GAME_WIDTH * 0.5 - 40, GAME_HEIGHT - 40, 199, -1);
        key.dy = -25;
        double doorX = game.flipCoordReal(Entity.xCtr(5)), doorY = Entity.yCtr(6);
        if (game.script != null) game.script.addDecor("door", Entity.xCtr(5), doorY, true);
        game.particles(PARTICLE_STONE, doorX + CASE_WIDTH, doorY, 3, 10);
        game.explodeZone(doorX + CASE_WIDTH, doorY - CASE_HEIGHT, 40);
        game.playMusic(0); // retour à la musique du jeu
        for (Player p : game.players()) { // bonus vies : « Bonus vies: 3 x 20.000 »
            p.getScore(p, p.lives * 20000);
            game.alert("static.35", p.lives + " x 20.000");
        }
    }

    @Override
    public void onEndAnim(int id) {
        super.onEndAnim(id);
        if (id == A_SWITCH.id || id == A_BAT_FORM.id || id == A_JUMP_LAND.id || id == A_TORNADO_END.id) walk();
        if (id == A_DASH_START.id) {
            playAnim(A_DASH_BUILD);
            setNext(Double.NaN, Double.NaN, SECOND * 2, DASH);
        }
        if (id == A_DASH.id) playAnim(A_DASH_LOOP);
        if (id == A_BOMB.id) {
            spawnBombs(2);
            walk();
        }
        if (id == A_HIT.id) playAnim(A_WAIT);
        if (id == A_BURN_START.id) playAnim(A_BURN_LOOP);
        if (id == A_TORNADO_START.id) playAnim(A_TORNADO_LOOP);
    }

    @Override
    public void onNext() {
        if (next == null) return;
        int a = next.action;
        if (a == DASH) {
            playAnim(A_DASH);
            dx = dir * DASH_SPEED;
            fl_hitBorder = false;
        }
        if (a == BURN) {
            playAnim(A_TORNADO_END);
            int n = 10;
            for (int i = 0; i < n; i++) {
                double bx = i * GAME_WIDTH / (double) n + CASE_WIDTH;
                if (Math.abs(bx - x) > 60) EnemyFireBall.attach(game, bx, GAME_HEIGHT).moveUp(FIREBALL_SPEED);
            }
        }
        if (a == TORNADO) {
            if (!fl_twister) {
                fl_twister = true;
                setNext(Double.NaN, Double.NaN, TORNADO_DURATION, TORNADO);
                return;
            }
            playAnim(A_TORNADO_END);
        }
        if (a == DIE) finalBlow();
        next = null;
    }

    /** Un ennemi tué : bombes bonus (objets 4 et 5) et vie (objet 36) à certains paliers. */
    public void onKillBad() {
        badKills++;
        totalKills++;
        if (badKills >= B_STEP) {
            if (itemB != null) itemB.destroy();
            itemB = SpecialItem.attach(game, GAME_WIDTH - 31, 0, 5, -1);
            if (itemB != null) itemB.lifeTimer = Double.NaN;
            badKills = 0;
        } else if (badKills == A_STEP) {
            if (itemA != null) itemA.destroy();
            itemA = SpecialItem.attach(game, 31, 0, 4, -1);
            if (itemA != null) itemA.lifeTimer = Double.NaN;
        }
        if (totalKills == EXTRA_LIFE_STEP) {
            SpecialItem it = SpecialItem.attach(game, 214, 340, 36, -1);
            if (it != null) it.lifeTimer = Double.NaN;
        }
    }

    /** Explosion d'une bombe du joueur : 1 vie si elle le touche (sauf pendant la charge). */
    public void onExplode(double ex, double ey, double radius) {
        if (fl_death) return;
        double d = Math.sqrt(Math.pow(x - ex, 2) + Math.pow(y + CENTER_OFFSET - ey, 2));
        if (d <= radius && action != DASH) loseLife(1);
    }

    @Override
    public void endUpdate() {
        super.endUpdate();
        xscale = dir < 0 ? -Math.abs(xscale) : Math.abs(xscale);
        if (action == TORNADO) updateTornado();
    }

    @Override
    public void update() {
        if (!fl_death) game.huTimer = 0;
        else game.huTimer += game.tmod * 3;
        ia();
        if (fl_immune) {
            immuneTimer -= game.tmod;
            if (immuneTimer <= 0) {
                fl_immune = false;
                stopBlink();
            }
        }
        if (action == JUMP) {
            if (dy >= 0 && animId == A_JUMP_UP.id) playAnim(A_JUMP_DOWN);
            if (dy > 0 && y >= GROUND_Y) land();
        }
        if (!fl_death && lives <= 50) {
            fbCoolDown -= game.tmod;
            Entity target = game.getOne(PLAYER);
            if (action == WALK && fbCoolDown <= 0 && game.countList(SHOOT) < 2 && target != null
                    && game.random(1000) <= CHANCE_FINAL_ANGER) {
                fbCoolDown = SECOND * 0.5;
                EnemyFireBall.attach(game, x, y).moveToTarget(target, FIREBALL_SPEED * 2);
            }
        }
        if (action == HIT && dy > 0 && y >= GROUND_Y) land();
        if (defeatTimeOut > 0 && !fl_defeated) {
            defeatTimeOut -= game.tmod;
            if (defeatTimeOut <= 0) finalBlow();
        }
        if (fl_death) updateDeath();
        super.update();
        if (action == DASH) updateDash();
        checkHits();
    }

    /** Bombe apparue par Tuberculoz : en explosant, elle libère une orange (plus énervée s'il est affaibli). */
    public static class BossBomb extends BadBomb {
        public BossBomb() {
            super("hammer_bomb_boss");
            fl_blink = true;
            explodeSound = null;
        }

        public static BossBomb attach(Game g, double x, double y) {
            BossBomb b = new BossBomb();
            b.duration = SECOND * 2 + g.random(50) / 10.0 * (g.random(2) * 2 - 1);
            b.initBomb(g, x, y);
            return b;
        }

        @Override
        public void initBomb(Game g, double x, double y) {
            super.initBomb(g, x, y);
            setLifeTimer(duration * 1.5);
            updateLifeTimer(duration);
        }

        @Override
        public Bomb duplicate() {
            return attach(game, x, y);
        }

        @Override
        public void onExplode() {
            super.onExplode();
            Bad b = Fruits.attach(game, BAD_ORANGE, x - CASE_WIDTH * 0.5, y - CASE_HEIGHT * 0.5);
            Entity boss = game.getOne(BOSS);
            if (boss instanceof Tuberculoz) {
                if (((Tuberculoz) boss).lives <= 70) b.angerMore();
                if (((Tuberculoz) boss).lives <= 50) b.angerMore();
            }
            b.moveUp(10);
            b.knock(SECOND);
            b.noReward = true;
        }

        @Override
        public void onKick(hammerfest.sim.entity.Player p) {
            super.onKick(p);
            setLifeTimer(lifeTimer + SECOND * 0.5);
        }
    }
}
