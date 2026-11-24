package hammerfest.sim.entity.bad;

import hammerfest.sim.Game;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Bomb;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.shoot.EnemyFireBall;

import java.util.ArrayList;
import java.util.List;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/bad/WallWalker.mt} : ennemi qui longe les surfaces (sol, murs, plafond).
 * {@code cp} est la direction de la surface d'appui ; sans appui il tombe, puis repart en touchant le sol.
 */
public class WallWalker extends Bad {

    public boolean fl_wallWalk, fl_intercept, fl_lost;
    /** Point d'appui relatif (case de la surface longée). */
    public int[] cp;
    public double speed = 3;
    /** Excentrage du sous-clip vers la surface longée (WallWalker.subOffset). */
    double subOffset = 8;
    static final double SUB_RECAL = 0.2;
    double xSpeed, ySpeed;
    /** Dernière position sûre {x, y, xSpeed, ySpeed, cpx, cpy}. */
    double[] lastSafe;

    public WallWalker(String linkage) {
        super(linkage);
        angerFactor = 0.4;
        fl_largeTrigger = true;
    }

    @Override
    public void initBad(Game g, double x, double y) {
        super.initBad(g, x, y);
        wallWalk();
    }

    public void wallWalk() {
        if (!isHealthy() || !isReady()) return;
        fl_wallWalk = true;
        fl_gravity = false;
        fl_friction = false;
        fl_hitWall = false;
        fl_hitGround = false;
        fl_hitBorder = false;
        int[][] all = {{1, 0, 0, 1}, {-1, 0, 0, 1}, {1, 0, 0, -1}, {-1, 0, 0, -1},
                {0, 1, 1, 0}, {0, -1, 1, 0}, {0, 1, -1, 0}, {0, -1, -1, 0}};
        List<int[]> choices = new ArrayList<>();
        for (int[] ch : all) {
            if (world.getCase(cx + ch[0], cy + ch[1]) <= 0 && world.getCase(cx + ch[2], cy + ch[3]) > 0) choices.add(ch);
        }
        if (choices.isEmpty()) {
            suicide();
            return;
        }
        int[] c = choices.get(game.random(choices.size()));
        setDir(c[0], c[1]);
        setCP(c[2], c[3]);
        playAnim(WALK);
    }

    public void land() {
        fl_wallWalk = false;
        fl_gravity = true;
        fl_friction = true;
        fl_hitWall = true;
        fl_hitGround = true;
        fl_hitBorder = true;
    }

    void setDir(int xoff, int yoff) {
        xSpeed = speed * xoff;
        ySpeed = speed * yoff;
        updateSpeed();
        x = Entity.xCtr(cx);
        y = Entity.yCtr(cy);
        setNext(dx, dy, 0, ACTION_MOVE);
    }

    void setCP(int xoff, int yoff) {
        cp = new int[]{xoff, yoff};
    }

    @Override
    public void updateSpeed() {
        super.updateSpeed();
        if (fl_wallWalk && isReady()) {
            dx = xSpeed * speedFactor;
            dy = ySpeed * speedFactor;
        }
    }

    @Override
    public void infix() {
        super.infix();
        if (fl_wallWalk) wallWalkIA();
    }

    void wallWalkIA() {
        if (deathTimer > 0 || cp == null) return;
        int dirX = (int) Math.signum(dx), dirY = (int) Math.signum(dy);
        if (cy == 0) {
            if (dy < 0) {
                setDir(-cp[0], 0);
                setCP(0, -1);
            } else if (world.getCase(cx + dirX, cy) > 0) {
                setDir(0, 1);
                setCP(dirX, 0);
            }
        } else if (world.getCase(cx + cp[0], cy + cp[1]) <= 0) {
            setDir(cp[0], cp[1]);
            setCP(-dirX, -dirY);
        } else if (world.getCase(cx + dirX, cy + dirY) > 0) {
            setDir(-cp[0], -cp[1]);
            setCP(dirX, dirY);
        }
        boolean deadEnd = true;
        for (int tries = 0; deadEnd && tries < 4; tries++) {
            dirX = (int) Math.signum(dx);
            dirY = (int) Math.signum(dy);
            if (world.getCase(cx + dirX, cy + dirY) > 0 && world.getCase(cx + cp[0], cy + cp[1]) > 0) {
                setDir(-cp[0], -cp[1]);
                setCP(dirX, dirY);
            } else {
                deadEnd = false;
            }
        }
        if (deadEnd && !(deathTimer > 0)) suicide();
    }

    @Override
    public void killHit(double dx) {
        super.killHit(dx);
        land();
        fl_hitGround = false;
        fl_hitWall = false;
    }

    void suicide() {
        if (deathTimer > 0) return;
        dx = 0;
        dy = 0;
        land();
        fl_lost = true;
        deathTimer = SECOND * 3;
    }

    void moveToSafePos() {
        if (lastSafe == null) return;
        x = lastSafe[0];
        y = lastSafe[1];
        xSpeed = lastSafe[2];
        ySpeed = lastSafe[3];
        cp = new int[]{(int) lastSafe[4], (int) lastSafe[5]};
        updateCoords();
    }

    @Override
    public boolean isReady() {
        return isHealthy() && !fl_lost;
    }

    @Override
    public void onFreeze() {
        super.onFreeze();
        if (fl_wallWalk) fl_intercept = true;
        land();
    }

    @Override
    public void onKnock() {
        super.onKnock();
        land();
    }

    @Override
    public void onMelt() {
        super.onMelt();
        wallWalk();
    }

    @Override
    public void onWakeUp() {
        super.onWakeUp();
        wallWalk();
    }

    @Override
    public void onHitWall() {
        if (!fl_wallWalk && world.getCase(cx, cy) != WALL) dx = -dx;
    }

    @Override
    public void onHitGround(double h) {
        super.onHitGround(h);
        fl_intercept = false;
        if (fl_lost) {
            fl_lost = false;
            deathTimer = 0;
            wallWalk();
        }
    }

    /** Excentrage du sous-clip vers la surface longée (WallWalker.endUpdate). */
    @Override
    public void endUpdate() {
        super.endUpdate();
        int cpx = cp == null ? 0 : cp[0], cpy = cp == null ? 0 : cp[1];
        double xSub, ySub;
        if (isHealthy()) {
            xSub = cpx * subOffset;
            ySub = cpy > 0 ? subOffset * 0.5 : cpy * subOffset;
        } else {
            xSub = 0;
            ySub = fl_freeze ? 0 : subOffset * 0.5;
        }
        subX += SUB_RECAL * (xSub - subX) * speedFactor;
        subY += SUB_RECAL * (ySub - subY) * speedFactor;
    }

    @Override
    public void update() {
        Entity p = game.getOne(PLAYER);
        realRadius = p != null && p.y > y ? CASE_WIDTH * 1.4 : CASE_WIDTH;
        super.update();
        if (lastSafe != null && world.getCase(Entity.xRtc(lastSafe[0]) + (int) lastSafe[4],
                Entity.yRtc(lastSafe[1]) + (int) lastSafe[5]) <= 0) {
            lastSafe = null;
            fl_gravity = true;
            fl_wallWalk = false;
            fl_hitGround = true;
            fl_friction = true;
            fl_lost = true;
        }
        if (cp != null && world.getCase(cx + cp[0], cy + cp[1]) > 0) {
            lastSafe = new double[]{x, y, xSpeed, ySpeed, cp[0], cp[1]};
        }
    }

    /** Crawler : rampe sur les surfaces et crache une boule de feu droit devant son support. */
    public static class Crawler extends WallWalker {
        static final double SHOOT_SPEED = 6;
        static final int CHANCE_ATTACK = 10;
        static final double COOLDOWN = SECOND * 2, ATTACK_TIMER = SECOND * 0.5;
        static final double SCALE_RECAL = 0.2, CRAWL_STRETCH = 1.8;
        static final int COLOR = 0xFF9146, COLOR_ALPHA = 40;
        boolean fl_attack;
        double attackCD = PEACE_COOLDOWN, attackTimer;
        /** Déformation « blob » (cosinus) et échelles visées du sous-clip. */
        double blobCpt, xscaleTarget, yscaleTarget;

        public Crawler() {
            super(BAD_LINKAGES[14]);
            speed = 2;
            angerFactor = 0.5;
        }

        @Override
        public void initBad(Game g, double x, double y) {
            super.initBad(g, x, y);
            scale(90);
        }

        @Override
        public void killHit(double dx) {
            super.killHit(dx);
            fl_attack = false;
        }

        @Override
        public boolean isReady() {
            return super.isReady() && !fl_attack;
        }

        void prepareAttack() {
            dx = 0;
            dy = 0;
            fl_attack = true;
            fl_wallWalk = false;
            attackTimer = ATTACK_TIMER;
            playAnim(SHOOT_START);
        }

        void attack() {
            EnemyFireBall s = EnemyFireBall.attach(game, x, y);
            s.moveTo(x, y);
            s.dx = -cp[0] * SHOOT_SPEED;
            s.dy = -cp[1] * SHOOT_SPEED;
            s.scale(70);
            if (cp[0] != 0) game.particlesDir(PARTICLE_BLOB, x, y, 2, 3, -cp[0]);
            else game.particles(PARTICLE_BLOB, x, y, 2, 3);
            game.explosion(x, y, 20);
            subXscale = 150 + Math.abs(cp[0]) * 150;
            subYscale = 150 + Math.abs(cp[1]) * 150;
            setColorHex(COLOR_ALPHA, COLOR);
            attackCD = COOLDOWN;
            playAnim(SHOOT_END);
        }

        boolean decideAttack() {
            if (fl_attack || player == null || cp == null) return false;
            boolean inSight = false;
            double factor = 1;
            if (cp[1] != 0 && Math.abs(player.x - x) <= CASE_WIDTH * 2) {
                if (cp[1] > 0 && player.y < y) inSight = true;
                if (cp[1] < 0 && player.y > y) inSight = true;
            }
            if (cp[0] != 0 && Math.abs(player.y - y) <= CASE_HEIGHT * 2) {
                if (cp[0] > 0 && player.x < x) inSight = true;
                if (cp[0] < 0 && player.x > x) inSight = true;
            }
            if (inSight) {
                attackCD -= game.tmod * 4;
                factor = 8;
            }
            return isReady() && isHealthy() && attackCD <= 0 && game.random(1000) < CHANCE_ATTACK * factor;
        }

        @Override
        public void onEndAnim(int id) {
            super.onEndAnim(id);
            if (id == SHOOT_END.id) {
                fl_attack = false;
                fl_wallWalk = true;
                moveToSafePos();
                updateSpeed();
                if (dx == 0 && dy == 0) wallWalk();
            }
        }

        @Override
        public void onFreeze() {
            super.onFreeze();
            fl_attack = false;
        }

        @Override
        public void onKnock() {
            super.onKnock();
            fl_attack = false;
        }

        /** Chute de plus de 3 cases : le blob s'écrase au sol. */
        @Override
        public void onHitGround(double h) {
            super.onHitGround(h);
            if (Math.abs(h) >= CASE_HEIGHT * 3) {
                subXscale = 2 * 100 * scaleFactor;
                subYscale = 0.2 * 100 * scaleFactor;
                subY = 10;
                if (!fl_freeze) game.particles(PARTICLE_BLOB, x, y, 2, 3);
            }
        }

        /** Crawler.endUpdate : vibration avant le tir, étirement en rampant, ondulation, flash orange. */
        @Override
        public void endUpdate() {
            super.endUpdate();
            java.util.Random r = game.visualRng;
            if (fl_attack) {
                drawDx = r.nextInt(15) / 10.0 * (r.nextInt(2) * 2 - 1);
                drawDy = r.nextInt(15) / 10.0 * (r.nextInt(2) * 2 - 1);
                xscaleTarget = scaleFactor * 100 + r.nextInt(20) * (r.nextInt(2) * 2 - 1);
                yscaleTarget = scaleFactor * 100 + r.nextInt(20) * (r.nextInt(2) * 2 - 1);
            } else {
                drawDx = 0;
                drawDy = 0;
                xscaleTarget = scaleFactor * 100;
                yscaleTarget = scaleFactor * 100;
            }
            if (fl_wallWalk) {
                if (dx != 0) xscaleTarget = 100 * scaleFactor * CRAWL_STRETCH;
                if (dy != 0) yscaleTarget = 100 * scaleFactor * CRAWL_STRETCH;
            }
            if (isHealthy()) {
                xscaleTarget += 10 * Math.sin(blobCpt);
                yscaleTarget += 10 * Math.cos(blobCpt);
                blobCpt += game.tmod * 0.1;
            }
            subXscale += SCALE_RECAL * (xscaleTarget - subXscale);
            subYscale += SCALE_RECAL * (yscaleTarget - subYscale);
            if (colorAlpha > 0) {
                colorAlpha -= game.tmod * 3;
                if (colorAlpha < 0) colorAlpha = 0;
            }
        }

        @Override
        public void update() {
            if (fl_attack) {
                dx = 0;
                dy = 0;
            }
            super.update();
            if (attackCD > 0) attackCD -= game.tmod;
            if (decideAttack() && world.getCase(cx + cp[0], cy + cp[1]) > 0) prepareAttack();
            if (fl_attack && attackTimer > 0) {
                attackTimer -= game.tmod;
                if (attackTimer <= 0) attack();
            }
        }
    }

    /**
     * Scie : longe les surfaces, indestructible (touchée elle est étourdie 3 s), renvoie les bombes,
     * ne compte pas pour finir le niveau. Vitesse réglable par la variable de script SAW_SPEED (0 = arrêt).
     */
    public static class Saw extends WallWalker {
        static final double STUN_DURATION = SECOND * 3, BASE_SPEED = 3;
        boolean fl_stun, fl_stop, fl_updateSpeed;
        double stunTimer;
        /** Lame : vitesse de rotation (Saw.rotSpeed), au plus ROTATION_SPEED, ralentit à l'arrêt ou étourdie. */
        static final double ROTATION_SPEED = 10;
        double rotSpeed;

        static final double ROTATION_RECAL = 0.3;

        public Saw() {
            super(BAD_LINKAGES[16]);
            speed = BASE_SPEED;
            angerFactor = 0;
            subOffset = 2;
        }

        /** Saw.endUpdate : la scie s'oriente vers la surface longée, tremble quand elle est étourdie. */
        @Override
        public void endUpdate() {
            super.endUpdate();
            java.util.Random r = game.visualRng;
            if (fl_stun) {
                double f = 1 - stunTimer / STUN_DURATION;
                drawDx = f * (r.nextInt(20) / 10.0) * (r.nextInt(2) * 2 - 1);
                drawDy = f * (r.nextInt(20) / 10.0) * (r.nextInt(2) * 2 - 1);
            } else {
                drawDx = 0;
                drawDy = 0;
            }
            if ((fl_wallWalk || fl_stop) && cp != null) {
                double angDeg = Math.toDegrees(Math.atan2(cp[1], cp[0])) - 90;
                double delta = angDeg - subRotation;
                if (delta < -180) delta += 360;
                if (delta > 180) delta -= 360;
                subRotation += delta * ROTATION_RECAL;
            } else if (fl_kill) {
                subRotation += game.tmod * 14.5;
            } else if (isHealthy()) {
                subRotation += -subRotation * (ROTATION_RECAL * 0.25);
            }
            // rotation comme Flash : ramenée dans ]-180, 180]
            subRotation = ((subRotation + 180) % 360 + 360) % 360 - 180;
            // la lame tourne sur elle-même (sub.sub._rotation += rotSpeed)
            if (fl_stop || fl_stun) rotSpeed *= 0.9;
            else rotSpeed = Math.min(ROTATION_SPEED, rotSpeed + game.tmod);
            innerRotation = ((Double.isNaN(innerRotation) ? 0 : innerRotation) + rotSpeed) % 360;
        }

        @Override
        public void init(Game g) {
            super.init(g);
            unregister(BAD_CLEAR);
        }

        @Override
        public void initBad(Game g, double x, double y) {
            super.initBad(g, x, y);
            scale(80);
        }

        void stun() {
            if (fl_stop) return;
            game.explosion(x, y - CASE_HEIGHT * 0.5, 30);
            if (!fl_stun) {
                game.particlesDir(PARTICLE_METAL, x, y, 5, 0, dx);
                game.particlesDir(PARTICLE_STONE, x, y, 0, 4, dx);
            }
            fl_stun = true;
            fl_wallWalk = false;
            stunTimer = STUN_DURATION;
            dx = 0;
            dy = 0;
        }

        void haltSaw() {
            if (fl_stop) return;
            fl_stop = true;
            fl_wallWalk = false;
            dx = 0;
            dy = 0;
        }

        void run() {
            if (!fl_stop) return;
            fl_stop = false;
            fl_wallWalk = true;
            updateSpeed();
        }

        @Override
        public boolean isHealthy() {
            return !fl_kill && !fl_stun && !fl_stop;
        }

        @Override
        public void hit(Entity e) {
            if (isHealthy() && e.isType(PLAYER)) game.particles(PARTICLE_CLASSIC_BOMB, x, y, 3, 5);
            super.hit(e);
            if (isHealthy() && e.isType(BOMB) && !e.isType(BAD_BOMB)) {
                Bomb b = (Bomb) e;
                b.setLifeTimer(SECOND * 0.6);
                b.dx = dx != 0 ? -dx : (cp != null ? -cp[0] * 4 : 0);
                b.dy = cp != null && cp[1] != 0 ? -cp[1] * 13 : -8;
                game.particlesDir(PARTICLE_SPARK, b.x, b.y, 2, 0, b.dx);
            }
        }

        @Override
        public void land() {
        }

        @Override
        public void killHit(double dx) {
            stun();
        }

        @Override
        public void knock(double d) {
            stun();
        }

        @Override
        public void freeze(double d) {
            stun();
        }

        @Override
        public void onWakeUp() {
            fl_wallWalk = true;
            moveToSafePos();
            updateSpeed();
            game.particles(PARTICLE_STONE, x, y, 3, 0);
        }

        @Override
        public void update() {
            String dyn = game.getDynamicVar("SAW_SPEED");
            double old = speed;
            Integer dynSpeed = null;
            try {
                if (dyn != null) dynSpeed = Integer.parseInt(dyn.trim());
            } catch (NumberFormatException ignored) {
                dynSpeed = null;
            }
            if (dynSpeed == null || dynSpeed < 0) {
                speed = BASE_SPEED;
                run();
            } else if (dynSpeed == 0) {
                haltSaw();
            } else {
                speed = dynSpeed;
                run();
            }
            if (old != speed) fl_updateSpeed = true;
            if (fl_updateSpeed && isHealthy()) {
                fl_updateSpeed = false;
                updateSpeed();
            }
            if (fl_stop || fl_stun) {
                dx = 0;
                dy = 0;
            }
            if (fl_stun) {
                if (game.visualRandom(10) == 0) game.particles(PARTICLE_SPARK, x, y, 1, 0);
                stunTimer -= game.tmod;
                if (stunTimer <= 0) {
                    fl_stun = false;
                    onWakeUp();
                }
            }
            super.update();
        }
    }
}
