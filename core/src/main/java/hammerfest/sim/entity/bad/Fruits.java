package hammerfest.sim.entity.bad;

import hammerfest.sim.Game;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.shoot.Pepin;

import static hammerfest.sim.Const.*;

/** Ennemis fruits (entity/bad/walker/*.mt) et fabrique GameMode.attachBad. */
public final class Fruits {

    /** Cerise : marcheuse simple, animation ralentie. */
    public static class Cerise extends Walker {
        public Cerise() {
            super(BAD_LINKAGES[BAD_CERISE]);
            animFactor = 0.65;
        }
    }

    /** Orange : saute par-dessus le vide. */
    public static class Orange extends Jumper {
        public Orange() {
            super(BAD_LINKAGES[BAD_ORANGE]);
            setJumpH(100);
        }
    }

    /** Banane : saute dans tous les sens et escalade. */
    public static class Banane extends Jumper {
        public Banane() {
            super(BAD_LINKAGES[BAD_BANANE]);
            setJumpUp(5);
            setJumpDown(5);
            setJumpH(100);
            setClimb(100, IA_CLIMB);
            setFall(5);
        }
    }

    /** Pomme : sauteuse qui crache des pépins. */
    public static class Pomme extends Shooter {
        public Pomme() {
            super(BAD_LINKAGES[BAD_POMME]);
            setJumpUp(3);
            setJumpH(100);
            setClimb(100, 1);
            setFall(20);
            setShoot(2);
            initShooter(20, 12);
        }

        @Override
        public void onShoot() {
            Pepin s = Pepin.attach(game, x, y);
            if (dir < 0) s.moveLeft(s.shootSpeed);
            else s.moveRight(s.shootSpeed);
        }
    }

    /** Citron : tire des zestes vers le haut ou le bas selon la position du joueur. */
    public static class Citron extends Shooter {
        public Citron() {
            super(BAD_LINKAGES[BAD_CITRON]);
            setJumpH(50);
            setShoot(3);
            initShooter(50, 20);
        }

        @Override
        public void onShoot() {
            hammerfest.sim.entity.shoot.Zeste s = hammerfest.sim.entity.shoot.Zeste.attach(game, x, y);
            hammerfest.sim.entity.Entity target = game.getOne(PLAYER);
            if (target != null && target.y < y) s.moveUp(s.shootSpeed);
            else s.moveDown(s.shootSpeed);
        }
    }

    /** Abricot : le gros (« spawner ») libère deux petits abricots en tombant par le bas. */
    public static class Abricot extends Jumper {
        final boolean spawner;

        public Abricot(boolean spawner) {
            super(BAD_LINKAGES[BAD_ABRICOT]);
            this.spawner = spawner;
            animFactor = 0.65;
            setJumpUp(5);
            setJumpH(100);
            setClimb(100, 3);
            setFall(20);
        }

        @Override
        public void init(Game g) {
            super.init(g);
            if (!spawner) scale(75);
            dir = -1;
        }

        @Override
        public void onDeathLine() {
            if (spawner) {
                attach(game, BAD_ABRICOT2, x - CASE_WIDTH, -30);
                attach(game, BAD_ABRICOT2, x + CASE_WIDTH, -30);
            }
            super.onDeathLine();
        }
    }

    /** Poire : lance des bombes en cloche puis recule. */
    public static class Poire extends Shooter {
        public Poire() {
            super(BAD_LINKAGES[BAD_POIRE]);
            setJumpH(100);
            setClimb(100, 1);
            setShoot(4);
            initShooter(50, 8);
        }

        @Override
        public void onShoot() {
            hammerfest.sim.entity.PoireBomb s = hammerfest.sim.entity.PoireBomb.attach(game, x, y);
            s.owner = this;
            s.moveToAng(dir < 0 ? -135 : -45, 10);
            setNext(Double.NaN, Double.NaN, shootDuration, ACTION_FALLBACK);
        }
    }

    /** Bombe ambulante : touchée ou gelée, elle chauffe puis explose en tuant les joueurs proches. */
    public static class Bombe extends Jumper {
        static final double RADIUS = CASE_WIDTH * 5;
        static final double EXPERT_RADIUS = CASE_WIDTH * 8;
        static final double POWER = 30;
        boolean fl_overheat;

        public Bombe() {
            super(BAD_LINKAGES[BAD_BOMBE]);
            setJumpUp(10);
            setJumpDown(6);
            setJumpH(100);
            setClimb(100, 3);
            fl_alphaBlink = false;
        }

        double radius() {
            return game.fl_bombExpert ? EXPERT_RADIUS : RADIUS;
        }

        @Override
        public void playAnim(hammerfest.sim.Anim a) {
            if (fl_overheat) return;
            super.playAnim(a);
            if (a.id == THINK.id) fl_loop = true;
            if (a.id == JUMP.id) fl_loop = false;
        }

        void trigger() {
            if (fl_overheat) return;
            halt();
            playAnim(DIE);
            fl_loop = false;
            fl_freeze = false;
            setLifeTimer(SECOND * 3);
            updateLifeTimer(SECOND);
            setNext(Double.NaN, Double.NaN, SECOND * 3, ACTION_WALK);
            fl_overheat = true;
        }

        void selfDestruct() {
            game.explodeZone(x, y, radius());
            game.particles(PARTICLE_METAL, x, y, 5, 4);
            game.particles(PARTICLE_SPARK, x, y, 0, 4);
            for (hammerfest.sim.entity.Entity e : game.getClose(PLAYER, x, y, radius(), false)) {
                hammerfest.sim.entity.Player p = (hammerfest.sim.entity.Player) e;
                p.killHit(0);
                shockWave(p, radius(), POWER);
                p.dy = -10 - game.random(20);
            }
            game.sound("sound_bomb_black");
            dropReward();
            onKill();
            destroy();
        }

        @Override
        public void killHit(double dx) {
            trigger();
        }

        @Override
        public void onFreeze() {
            trigger();
        }

        @Override
        public boolean isHealthy() {
            return !fl_overheat && super.isHealthy();
        }

        @Override
        public void onLifeTimer() {
            selfDestruct();
        }

        @Override
        public void prefix() {
            if (!fl_stable && fl_overheat) dx = 0;
            super.prefix();
        }
    }

    /**
     * Litchi : protégé par sa carapace. Tué ou gelé, il est seulement assommé ou perd sa carapace,
     * laissant place à un litchi faible (énervé) qui, lui, est vulnérable.
     */
    public static class Litchi extends Jumper {
        LitchiWeak child;

        public Litchi() {
            super(BAD_LINKAGES[BAD_LITCHI]);
            speed *= 0.8;
            setJumpH(100);
            setJumpUp(10);
            setJumpDown(6);
            setClimb(25, 3);
            setFall(25);
        }

        @Override
        public void freeze(double d) {
            weaken();
        }

        @Override
        public void killHit(double dx) {
            if (!fl_knock) knock(KNOCK_DURATION);
        }

        /** Mort instantanée (Litchi.forceKill). */
        @Override
        public void forceKill(double dx) {
            super.killHit(dx);
        }

        void weaken() {
            if (child != null) return;
            child = (LitchiWeak) attach(game, BAD_LITCHI_WEAK, x, y - CASE_HEIGHT);
            child.angerMore();
            child.updateSpeed();
            child.halt();
            child.dir = dir;
            child.fl_ninFoe = fl_ninFoe;
            child.fl_ninFriend = fl_ninFriend;
            game.particles(PARTICLE_LITCHI, x, y - CASE_HEIGHT, 5, 4); // la peau du litchi éclate
            child.playAnim(SHOOT_START);
        }

        @Override
        public void update() {
            super.update();
            if (!fl_kill && fl_knock && child == null && dy <= -BAD_VJUMP_Y * 0.6) dy = -BAD_VJUMP_Y * 0.6;
        }

        /** Après la perte de carapace : le litchi faible reprend l'élan, la carapace disparaît. */
        @Override
        public void endUpdate() {
            if (child != null) {
                child.dy = -7;
                destroy();
            } else {
                super.endUpdate();
            }
        }
    }

    /** Litchi sans carapace. */
    public static class LitchiWeak extends Jumper {
        public LitchiWeak() {
            super(BAD_LINKAGES[BAD_LITCHI_WEAK]);
            setJumpH(100);
            setJumpUp(10);
            setJumpDown(6);
            setClimb(25, 3);
            setFall(25);
        }

        @Override
        public void onEndAnim(int id) {
            super.onEndAnim(id);
            if (id == SHOOT_START.id) {
                playAnim(WALK);
                walk();
            }
        }

        @Override
        public void walk() {
            if (animId != SHOOT_START.id) super.walk();
        }
    }

    /** Kiwi : pose des mines invisibles devant lui. */
    public static class Kiwi extends Shooter {
        final java.util.List<hammerfest.sim.entity.Mine> mineList = new java.util.ArrayList<>();

        public Kiwi() {
            super(BAD_LINKAGES[BAD_KIWI]);
            setJumpUp(10);
            setJumpDown(20);
            setJumpH(50);
            setClimb(100, 3);
            setShoot(3);
            initShooter(SECOND, SECOND * 0.6);
            speed *= 0.7;
            angerFactor *= 2.5;
            shootCD = 0;
        }

        @Override
        public void onShoot() {
            mineList.add(hammerfest.sim.entity.Mine.attach(game, x + dir * CASE_WIDTH * 1.1, y));
        }

        /** Efface les mines non déclenchées de ce kiwi (elles sautent sans exploser). */
        public void clearMines() {
            for (hammerfest.sim.entity.Mine m : mineList) {
                if (!m.fl_trigger) m.setLifeTimer(game.random((int) Math.round(SECOND * 0.7)));
            }
            mineList.clear();
        }
    }

    /**
     * Fraise : les fraises se passent une balle mortelle. Seule une fraise tient la balle ; une fraise
     * seule la lance sur le joueur.
     */
    public static class Fraise extends Shooter {
        public boolean fl_ball;
        hammerfest.sim.entity.Entity ballTarget;
        double catchCD;

        public Fraise() {
            super(BAD_LINKAGES[BAD_FRAISE]);
            animFactor = 1.0;
            setShoot(4);
            initShooter(0, 10);
        }

        @Override
        public void init(Game g) {
            super.init(g);
            assignBall();
            for (hammerfest.sim.entity.Entity e : g.getList(CATCHER)) {
                if (((Fraise) e).fl_ball) fl_ball = false;
            }
            if (g.countList(BALL) > 0) fl_ball = false;
            register(CATCHER);
        }

        public void catchBall(hammerfest.sim.entity.shoot.Ball b) {
            if (!isHealthy()) return;
            next = null;
            walk();
            assignBall();
            b.destroy();
            ballTarget = null;
        }

        public void assignBall() {
            fl_ball = true;
            catchCD = SECOND * 1.5;
        }

        @Override
        public void onShoot() {
            super.onShoot();
            fl_ball = false;
            if (ballTarget == null) return;
            if ((ballTarget.types & BAD) > 0 && ballTarget instanceof Walker) {
                Walker w = (Walker) ballTarget;
                w.setNext(Double.NaN, Double.NaN, 2.3 * SECOND + 5, ACTION_WALK);
                w.halt();
                w.playAnim(THINK);
            }
            hammerfest.sim.entity.shoot.Ball s = hammerfest.sim.entity.shoot.Ball.attach(game, x, y);
            s.moveToTarget(ballTarget, s.shootSpeed);
            s.targetCatcher = ballTarget;
        }

        @Override
        public void onEndAnim(int id) {
            super.onEndAnim(id);
            ballTarget = null;
        }

        /** Choisit un coéquipier prêt (ou le joueur si la fraise est seule) avant de tirer. */
        @Override
        public void startShoot() {
            if (!fl_ball || catchCD > 0) return;
            java.util.List<hammerfest.sim.entity.Entity> l = new java.util.ArrayList<>(game.getList(CATCHER));
            Walker mate = null;
            while (!l.isEmpty()) {
                Walker w = (Walker) l.remove(game.random(l.size()));
                if (w != this && w.isReady()) {
                    mate = w;
                    break;
                }
            }
            if (mate != null) {
                ballTarget = mate;
            } else if (game.countList(CATCHER) == 1) {
                ballTarget = game.getOne(PLAYER);
                if (ballTarget == null) return;
            } else {
                return;
            }
            super.startShoot();
        }

        @Override
        public void destroy() {
            if (fl_ball) {
                for (hammerfest.sim.entity.Entity e : game.getList(CATCHER)) {
                    if (e != this && !e.fl_destroy) {
                        ((Fraise) e).assignBall();
                        break;
                    }
                }
            }
            super.destroy();
        }

        @Override
        public void endUpdate() {
            super.endUpdate();
            if (dx == 0 && ballTarget != null && !ballTarget.fl_destroy) {
                if (ballTarget.x > x) xscale = Math.abs(xscale);
                if (ballTarget.x < x) xscale = -Math.abs(xscale);
            }
        }

        @Override
        public void update() {
            super.update();
            if (catchCD > 0) catchCD = Math.max(0, catchCD - game.tmod);
            if (game.countList(BALL) == 0) {
                boolean lost = true;
                for (hammerfest.sim.entity.Entity e : game.getList(BAD_CLEAR)) {
                    if (e instanceof Fraise && ((Fraise) e).fl_ball) lost = false;
                }
                if (lost) assignBall();
            }
        }
    }

    /** Ananas : bondit et retombe en onde de choc (assomme les joueurs au sol, repousse les bombes). */
    public static class Ananas extends Jumper {
        static final int CHANCE_DASH = 6;
        boolean fl_attack;
        double dashRadius = 100;
        double dashPower = 30;

        public Ananas() {
            super(BAD_LINKAGES[4]);
            setJumpH(100);
            speed *= 0.8;
            slideFriction = 0.9;
            shockResistance = 2.0;
        }

        @Override
        public void init(Game g) {
            super.init(g);
            if (g.fl_bombExpert) dashRadius *= 2;
        }

        @Override
        public void freeze(double d) {
            super.freeze(d);
            fallFactor *= 1.5;
            fl_attack = false;
        }

        @Override
        public void knock(double d) {
            super.knock(d);
            fallFactor *= 1.5;
            fl_attack = false;
        }

        @Override
        public void killHit(double dx) {
            super.killHit(dx);
            fl_attack = false;
        }

        @Override
        public boolean isReady() {
            return !fl_attack && super.isReady();
        }

        void repel(int type, double powerFactor) {
            for (hammerfest.sim.entity.Entity e : game.getClose(type, x, y, dashRadius, false)) {
                hammerfest.sim.entity.Physics p = (hammerfest.sim.entity.Physics) e;
                shockWave(p, dashRadius, dashPower * powerFactor);
                p.dy -= 8;
                if (p instanceof hammerfest.sim.entity.Player) ((hammerfest.sim.entity.Player) p).knock(SECOND * 1.5);
            }
        }

        void startAttack() {
            boolean allOut = true;
            for (hammerfest.sim.entity.Player p : game.players()) if (!p.fl_knock) allOut = false;
            if (allOut) return;
            halt();
            playAnim(THINK);
            fl_loop = true;
            setNext(0, -10, SECOND * 0.9, ACTION_MOVE);
            fl_attack = true;
        }

        void attack() {
            game.explodeZone(x, y, dashRadius, 20);
            game.shake(SECOND * 0.5, 5);
            for (hammerfest.sim.entity.Player p : game.players()) {
                if (p.fl_stable) {
                    if (p.fl_shield) p.dy = -8;
                    else p.knock(PLAYER_KNOCK_DURATION);
                }
            }
            repel(BOMB, 1);
            repel(PLAYER, 2);
            for (hammerfest.sim.entity.Entity e : game.getClose(PLAYER_SHOOT, x, y, dashRadius, false)) { // vaporize
                game.fx("hammer_fx_pop", e.x, e.y - CASE_HEIGHT);
                e.destroy();
            }
            fl_attack = false;
        }

        @Override
        public void onHitGround(double h) {
            super.onHitGround(h);
            if (fl_attack && isHealthy()) {
                attack();
                playAnim(SHOOT_END);
                halt();
            } else {
                game.shake(SECOND * 0.2, 2);
            }
        }

        @Override
        public void onHitWall() {
            if (!isHealthy()) game.shake(5, 3);
            super.onHitWall();
        }

        @Override
        public void onEndAnim(int id) {
            super.onEndAnim(id);
            if (id == SHOOT_END.id) walk();
        }

        @Override
        public void prefix() {
            if (isReady()) {
                if (fl_playerClose && game.random(1000) <= CHANCE_DASH * 2) startAttack();
                if (!fl_playerClose && game.random(1000) <= CHANCE_DASH) startAttack();
            }
            super.prefix();
        }
    }

    /**
     * Framboise : quand une bombe approche (ou au hasard), elle éclate en 6 fragments qui filent
     * vers un autre point du niveau, où elle se reconstitue une fois tous arrivés.
     */
    public static class Framboise extends Shooter {
        static final int FRAGS = 6;
        static final int MAX_TRIES = 1000;
        public double tx, ty;
        public boolean fl_phased;
        int arrived;
        /** Morceaux o1 / o2 du tas (sub.o1._visible, sub.o2._visible), révélés par les fragments 5 et 6. */
        public boolean heapO1, heapO2;

        public Framboise() {
            super(BAD_LINKAGES[20]);
            setJumpUp(3);
            setJumpDown(6);
            setJumpH(100);
            setClimb(100, 3);
            setFall(20);
            setShoot(0.7);
            initShooter(20, 12);
        }

        double aroundX(double base) {
            return base + game.random(CASE_WIDTH) * (game.random(2) * 2 - 1);
        }

        double aroundY(double base) {
            return base - game.random(CASE_HEIGHT);
        }

        @Override
        public boolean isReady() {
            return super.isReady() && !fl_phased;
        }

        @Override
        public void freeze(double d) {
            phaseIn();
            super.freeze(d);
        }

        @Override
        public void knock(double d) {
            phaseIn();
            super.knock(d);
        }

        @Override
        public void killHit(double dx) {
            phaseIn();
            super.killHit(dx);
        }

        @Override
        public void destroy() {
            clearFrags();
            super.destroy();
        }

        /** Un fragment est arrivé : la framboise réapparaît au point visé ; reconstituée au 6e. */
        public void onArrived(hammerfest.sim.entity.shoot.FramBall fb) {
            show();
            moveTo(tx, ty);
            fb.destroy();
            // Framboise.onArrived : le tas grossit d'une image par fragment (sub.nextFrame), les morceaux 5 et 6
            // apparaissent par-dessus (sub.o1, sub.o2)
            if (fb.subFrame >= 5) {
                if (fb.subFrame == 5) heapO1 = true;
                else heapO2 = true;
            } else {
                subFrame++;
            }
            arrived++;
            if (arrived >= FRAGS) phaseIn();
        }

        void clearFrags() {
            for (hammerfest.sim.entity.Entity s : new java.util.ArrayList<>(game.getList(SHOOT))) {
                if (s instanceof hammerfest.sim.entity.shoot.FramBall && ((hammerfest.sim.entity.shoot.FramBall) s).owner == this) {
                    s.destroy();
                }
            }
        }

        @Override
        public void onHitGround(double h) {
            super.onHitGround(h);
            if (h >= CASE_HEIGHT * 2) game.particles(PARTICLE_FRAMB_SMALL, x, y, 2, 4);
        }

        void phaseOut() {
            game.particles(PARTICLE_FRAMB_SMALL, x, y, 2, 3);
            game.explosion(x, y, 40);
            fl_phased = true;
            dx = 0;
            dy = 0;
            fl_shooter = false;
            fl_anim = false;
            // gotoAndStop("15") : le tas, sous-clip arrêté sur sa 1re image, morceaux o1 / o2 cachés
            animId = 14;
            subFrame = 1;
            heapO1 = false;
            heapO2 = false;
            hide();
        }

        void phaseIn() {
            clearFrags();
            fl_phased = false;
            fl_anim = true;
            fl_shooter = true;
            game.explosion(x, y, 40);
        }

        @Override
        public void walk() {
            if (!fl_phased) super.walk();
        }

        @Override
        public void onShoot() {
            int ctx = 0, cty = 0;
            int tries = 0;
            boolean invalid;
            do {
                invalid = false;
                ctx = game.random(LEVEL_WIDTH);
                cty = game.random(LEVEL_HEIGHT);
                tries++;
                if (distanceCase(ctx, cty) <= 7) invalid = true;
                if (!world.checkFlag(ctx, cty, IA_TILE_TOP)) invalid = true;
                if (world.checkFlag(ctx, cty, IA_SMALL_SPOT)) invalid = true;
                for (hammerfest.sim.entity.Entity sp : game.getList(SPEAR)) {
                    if (sp.cx == ctx && sp.cy == cty) invalid = true;
                }
            } while (tries < MAX_TRIES && invalid);
            if (tries >= MAX_TRIES) return;
            tx = hammerfest.sim.entity.Entity.xCtr(ctx);
            ty = hammerfest.sim.entity.Entity.yCtr(cty);
            arrived = 0;
            phaseOut();
            for (int i = 0; i < FRAGS; i++) {
                hammerfest.sim.entity.shoot.FramBall.attach(game, aroundX(x), aroundY(y), i + 1).setOwner(this);
            }
        }

        @Override
        public void update() {
            if (!visible) {
                if (!isHealthy()) {
                    show();
                    moveTo(tx, ty);
                    phaseIn();
                }
            } else if (isReady() && !game.getClose(PLAYER_BOMB, x, y, 90, false).isEmpty()) {
                startShoot();
            }
            super.update();
        }
    }

    /**
     * Crée l'ennemi {@code id} à la position (x, y) (GameMode.attachBad). Les types non encore portés
     * sont remplacés par un marcheur générique avec leur propre apparence.
     */
    public static Bad attach(Game g, int id, double x, double y) {
        Bad b;
        switch (id) {
            case BAD_POMME: b = new Pomme(); break;
            case BAD_CERISE: b = new Cerise(); break;
            case BAD_BANANE: b = new Banane(); break;
            case BAD_ORANGE: b = new Orange(); break;
            case BAD_CITRON: b = new Citron(); break;
            case BAD_ABRICOT: b = new Abricot(true); break;
            case BAD_ABRICOT2: b = new Abricot(false); break;
            case BAD_POIRE: b = new Poire(); break;
            case BAD_BOMBE: b = new Bombe(); break;
            case BAD_SPEAR: b = new Spear(); break;
            case BAD_LITCHI: b = new Litchi(); break;
            case BAD_LITCHI_WEAK: b = new LitchiWeak(); break;
            case BAD_KIWI: b = new Kiwi(); break;
            case 3: b = new FireBall(); break;
            case 4: b = new Ananas(); break;
            case BAD_FRAISE: b = new Fraise(); break;
            case 20: b = new Framboise(); break;
            case 14: b = new WallWalker.Crawler(); break;
            case 16: b = new WallWalker.Saw(); break;
            case 12: b = new Flyer.Baleine(); break;
            case 15: b = new Flyer.Tzongre(); break;
            default:
                String linkage = id >= 0 && id < BAD_LINKAGES.length ? BAD_LINKAGES[id] : BAD_LINKAGES[BAD_CERISE];
                b = new Walker(linkage);
        }
        b.initBad(g, x, y);
        g.onAttachBad(b);
        return b;
    }

    private Fruits() {
    }
}
