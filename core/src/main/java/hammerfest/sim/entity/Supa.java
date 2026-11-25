package hammerfest.sim.entity;

import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/Supa.mt} et {@code entity/supa/*.mt} : « super-objets » déclenchés par
 * des objets spéciaux, qui traversent l'écran en balayant les ennemis. Hors du système de cases
 * (pas de triggers), sans collision avec le décor.
 */
public abstract class Supa extends Mover {

    public double radius;
    public double speed;

    protected Supa(String linkage) {
        super(linkage);
        fl_hitGround = false;
        fl_hitWall = false;
        fl_gravity = false;
        fl_friction = false;
        fl_hitBorder = false;
        minAlpha = 0;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        register(SUPA);
        fl_anim = false;
    }

    public void initSupa(Game g, double x, double y) {
        init(g);
        moveTo(x, y);
        endUpdate();
    }

    @Override
    public double width() {
        return radius * 2 * scaleFactor;
    }

    @Override
    public double height() {
        return radius * 2 * scaleFactor;
    }

    @Override
    public void infix() {
    }

    @Override
    public void tAdd(int cx, int cy) {
    }

    @Override
    public void tRem(int cx, int cy) {
    }

    boolean inBound() {
        return x >= -width() && x < GAME_WIDTH && y >= -height() && y < GAME_HEIGHT;
    }

    static <S extends Supa> S start(S s, Game g, double x, double y) {
        s.initSupa(g, x, y);
        return s;
    }

    /**
     * Super-objet de niveau parfait (entity/supa/SupaItem.mt) : gros cristal qui tombe du haut de l'écran
     * quand les ennemis ont été tués dans leur ordre d'apparition. Il vaut 5 fois un cristal de même rang.
     */
    public static class SupaItem extends Supa {
        public int supaId;

        public SupaItem() {
            super("hammer_supa_item");
            radius = 50;
        }

        public static SupaItem attach(Game g, int id) {
            SupaItem s = new SupaItem();
            s.supaId = id;
            return start(s, g, GAME_WIDTH / 2.0, -50);
        }

        @Override
        public void initSupa(Game g, double x, double y) {
            super.initSupa(g, x, y);
            scale(200);
            moveDown(5);
            animId = -1;
            subFrame = supaId + 1;
        }

        void pick(Player pl) {
            game.particles(PARTICLE_ICE, x, y, 15, 0);
            game.explodeZone(x, y, 50);
            pl.getScore(this, ScoreItem.crystalValue(supaId) * 5);
            game.sound("sound_item_supa");
            for (Player o : game.players()) {
                if (o.uniqId != pl.uniqId) o.setBaseAnims(hammerfest.sim.Anim.PLAYER_WALK, hammerfest.sim.Anim.PLAYER_STOP_L);
            }
            destroy();
        }

        @Override
        public void prefix() {
            super.prefix();
            for (Entity e : game.getClose(PLAYER, x, y + CASE_HEIGHT * 1.5, radius, false)) {
                if (!e.fl_kill) {
                    pick((Player) e);
                    return;
                }
            }
            if (y >= GAME_HEIGHT + 50) { // loupé : tout le monde est déçu
                for (Player o : game.players()) o.setBaseAnims(hammerfest.sim.Anim.PLAYER_WALK, hammerfest.sim.Anim.PLAYER_STOP_L);
                destroy();
            }
        }
    }

    /** Boule géante qui rebondit en traversant l'écran et assomme les ennemis. */
    public static class Ball extends Supa {
        public Ball() {
            super("hammer_supa_ball");
            fl_gravity = true;
        }

        public static Ball attach(Game g) {
            return start(new Ball(), g, GAME_WIDTH, 0);
        }

        @Override
        public void initSupa(Game g, double x, double y) {
            super.initSupa(g, x, y);
            speed = 2;
            radius = 40;
            fallFactor = 0.8;
            gravityFactor = 0.8;
            dx = speed;
            moveTo(0, -50);
            scale(220);
        }

        @Override
        public void prefix() {
            super.prefix();
            for (Entity e : game.getClose(BAD, x, y, radius, false)) {
                Bad b = (Bad) e;
                if (!b.fl_knock) {
                    b.knock(KNOCK_DURATION * 3);
                    b.dx = dx * 2;
                    b.dy = -5;
                }
            }
        }

        @Override
        public void postfix() {
            super.postfix();
            if (dy > 0 && y >= GAME_HEIGHT) {
                dy = -Math.abs(dy);
                game.shake(SECOND * 0.5, 3);
            }
            if (x >= GAME_WIDTH + 50) destroy();
            // script du clip (image 1 : b._rotation += 3, image 2 : retour à 1) : le ballon tourne sur lui-même,
            // son reflet reste fixe ; angle porté par subRotation (jamais 0, sinon il ne serait pas transmis)
            subRotation = subRotation % 360 + 3 * CLIP_FRAMES_PER_CYCLE * game.tmod; // 3° par image Flash (40/s)
        }
    }

    /** Bulle qui rebondit sur les bords pendant 10 s et assomme les ennemis. */
    public static class Bubble extends Supa {
        public Bubble() {
            super("hammer_supa_bubble");
            blinkAlpha = 25;
        }

        public static Bubble attach(Game g) {
            return start(new Bubble(), g, GAME_WIDTH, 0);
        }

        @Override
        public void initSupa(Game g, double x, double y) {
            super.initSupa(g, x, y);
            speed = 8;
            radius = 80;
            moveTo(GAME_WIDTH / 2.0, GAME_HEIGHT / 2.0);
            moveToAng(45 + game.random(30) * (game.random(2) * 2 - 1) + (game.random(4) + 1) * 90, speed + game.random(5));
            setLifeTimer(SECOND * 10);
        }

        @Override
        public void prefix() {
            super.prefix();
            for (Entity e : game.getClose(BAD, x, y, radius, false)) {
                Bad b = (Bad) e;
                if (!b.fl_knock) {
                    b.knock(KNOCK_DURATION * 2);
                    game.particles(PARTICLE_ICE, b.x, b.y - CASE_HEIGHT, 0, 5);
                    b.dx = dx * 2;
                    b.dy = -5;
                }
            }
        }

        @Override
        public void postfix() {
            super.postfix();
            if (y <= 0) dy = Math.abs(dy);
            if (y >= GAME_HEIGHT) dy = -Math.abs(dy);
            if (x <= 0) dx = Math.abs(dx);
            if (x >= GAME_WIDTH) dx = -Math.abs(dx);
        }
    }

    /** Tonneaux qui tombent sans fin et écrasent tout, joueur compris. */
    public static class Tons extends Supa {
        public Tons() {
            super("hammer_supa_tons");
            fl_gravity = true;
        }

        public static Tons attach(Game g) {
            return start(new Tons(), g, GAME_WIDTH, 0);
        }

        @Override
        public void initSupa(Game g, double x, double y) {
            super.initSupa(g, x, y);
            radius = 40;
            moveTo(game.random(GAME_WIDTH), -40);
            fallFactor = 0.3;
        }

        @Override
        public void prefix() {
            super.prefix();
            for (Entity e : game.getClose(BAD, x, y, radius, false)) {
                if (!e.fl_kill) {
                    ((Bad) e).killHit((game.random(2) * 2 - 1) * game.random(5));
                    ((Bad) e).dy = -25;
                }
            }
            for (Entity e : game.getClose(PLAYER, x, y, radius, false)) {
                if (!e.fl_kill) {
                    ((Player) e).killHit((game.random(2) * 2 - 1) * game.random(5));
                    ((Player) e).dy = -25;
                }
            }
        }

        @Override
        public void postfix() {
            super.postfix();
            if (y >= GAME_HEIGHT * 2) {
                moveTo(game.random(GAME_WIDTH), -40);
                dy = 0;
            }
        }
    }

    /** Flèche géante qui traverse l'écran de droite à gauche, en boucle, en tuant les ennemis. */
    public static class Arrow extends Supa {
        public Arrow() {
            super("hammer_supa_arrow");
        }

        public static Arrow attach(Game g) {
            return start(new Arrow(), g, GAME_WIDTH, -50);
        }

        @Override
        public void initSupa(Game g, double x, double y) {
            super.initSupa(g, x, y);
            speed = 10;
            radius = 50;
            moveLeft(speed);
        }

        @Override
        public void prefix() {
            super.prefix();
            for (Entity e : game.getClose(BAD, x, y, radius, false)) {
                if (e.y >= y - CASE_HEIGHT && e.y <= y + CASE_HEIGHT * 2 && !e.fl_kill) {
                    ((Bad) e).killHit(-speed * 1.5);
                }
            }
        }

        @Override
        public void postfix() {
            super.postfix();
            if (!inBound()) moveTo(GAME_WIDTH, game.random((int) (GAME_HEIGHT * 0.7)) + 40);
        }
    }

    /** Météore de glace : traverse l'écran en diagonale et gèle les ennemis. */
    public static class IceMeteor extends Supa {
        public IceMeteor() {
            super("hammer_supa_icemeteor");
        }

        public static IceMeteor attach(Game g) {
            return start(new IceMeteor(), g, GAME_WIDTH, 0);
        }

        @Override
        public void initSupa(Game g, double x, double y) {
            super.initSupa(g, x, y);
            speed = 10;
            radius = 50;
            moveToAng(130, speed);
            setLifeTimer(SUPA_DURATION);
        }

        @Override
        public void prefix() {
            super.prefix();
            for (Entity e : game.getClose(BAD, x, y, radius, false)) {
                Bad b = (Bad) e;
                if (!b.fl_freeze) {
                    b.freeze(FREEZE_DURATION);
                    b.dx = dx * 2;
                    b.dy = -5;
                }
            }
        }

        @Override
        public void postfix() {
            super.postfix();
            rotation -= 7 * game.tmod;
            if (!inBound()) moveTo(GAME_WIDTH, 0);
        }
    }

    /** Nuage de fumée plein écran (purement visuel, 3 s). */
    public static class Smoke extends Supa {
        public Smoke() {
            super("hammer_supa_smoke");
            fl_blink = false;
        }

        public static Smoke attach(Game g) {
            return start(new Smoke(), g, GAME_WIDTH / 2.0, GAME_HEIGHT / 2.0);
        }

        @Override
        public void initSupa(Game g, double x, double y) {
            super.initSupa(g, x, y);
            scale(265);
            setLifeTimer(SECOND * 3);
        }
    }
}
