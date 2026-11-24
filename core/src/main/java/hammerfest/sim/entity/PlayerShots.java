package hammerfest.sim.entity;

import hammerfest.sim.Game;

import java.util.ArrayList;
import java.util.List;

import static hammerfest.sim.Const.*;

/** Tirs du joueur (entity/shoot/Player*.mt), armes temporaires données par des objets spéciaux. */
public final class PlayerShots {

    /** Boule de feu : brûle les ennemis touchés. */
    public static class FireBall extends Shoot {
        public FireBall() {
            super("hammer_shoot_player_fireball");
            shootSpeed = 8;
            coolDown = SECOND * 2;
        }

        @Override
        public void init(Game g) {
            super.init(g);
            register(PLAYER_SHOOT);
        }

        @Override
        public void hit(Entity e) {
            if ((e.types & BAD_CLEAR) > 0) {
                Bad b = (Bad) e;
                b.setCombo(uniqId);
                b.burn();
            }
        }

        @Override
        public void update() {
            super.update();
            if (game.visualRandom(3) == 0) game.particles(PARTICLE_SPARK, x, y, 0, 3);
        }

        @Override
        public void destroy() {
            if (!fl_destroy) {
                game.particles(PARTICLE_SPARK, x, y, 3, 0);
                game.particles(PARTICLE_STONE, x, y, 4, 0);
            }
            super.destroy();
        }
    }

    /** Flèche : tue le premier ennemi touché, se brise contre les murs. */
    public static class Arrow extends Shoot {
        public Arrow() {
            super("hammer_shoot_arrow");
            shootSpeed = 4;
            coolDown = SECOND * 2;
            fl_hitWall = true;
            fl_teleport = true;
        }

        @Override
        public void init(Game g) {
            super.init(g);
            register(PLAYER_SHOOT);
            animId = -1;
            playAnim(ANIM_LOOP);
        }

        @Override
        public void onHitWall() {
            if (livedOneTurn) game.fx("hammer_fx_arrowPouf", x, y - 15, 0, dx < 0 ? -100 : 100, 100, 100);
            else game.fx("hammer_fx_pop", x, y - CASE_HEIGHT * 0.5);
            destroy();
        }

        /** Vrai après la première mise à jour : une flèche tirée contre un mur fait un simple « pop ». */
        boolean livedOneTurn;

        @Override
        public void update() {
            super.update();
            livedOneTurn = true;
        }

        @Override
        public void hit(Entity e) {
            if ((e.types & BAD) > 0) {
                Bad b = (Bad) e;
                b.setCombo(uniqId);
                b.killHit(dx);
                game.fx("hammer_fx_pop", x, y - CASE_HEIGHT * 0.5);
                destroy();
            }
        }
    }

    /** Perle de glace : gèle chaque ennemi traversé (une fois chacun) et le pousse. */
    public static class Pearl extends Shoot {
        final List<Integer> shotList = new ArrayList<>();

        public Pearl() {
            super("hammer_shoot_player_pearl");
            shootSpeed = 8.5;
            coolDown = SECOND * 2;
        }

        @Override
        public void init(Game g) {
            super.init(g);
            register(PLAYER_SHOOT);
        }

        /** Éclats contre un bord (PlayerPearl.hitWallAnim). */
        void hitWallAnim() {
            game.particles(PARTICLE_ICE, x, y, 2, 0);
            game.particles(PARTICLE_STONE, x, y, 3, 0);
            game.fx("hammer_fx_icePouf", dx < 0 ? x + CASE_WIDTH * 0.5 : x - CASE_WIDTH * 0.5, y - 16);
        }

        @Override
        public void destroy() {
            if (!fl_destroy && !fl_borderBounce) hitWallAnim();
            super.destroy();
        }

        @Override
        public void onLifeTimer() {
            game.fx("hammer_fx_pop", x, y);
            super.onLifeTimer();
        }

        @Override
        public void endUpdate() {
            if (dy != 0) rotation = Math.atan2(dy, dx) * 180 / Math.PI;
            super.endUpdate();
        }

        @Override
        public void hit(Entity e) {
            if ((e.types & BAD) > 0 && !shotList.contains(e.uniqId)) {
                Bad b = (Bad) e;
                b.setCombo(uniqId);
                b.freeze(FREEZE_DURATION);
                b.dx = dx * 2;
                b.dy -= 3;
                shotList.add(b.uniqId);
            }
        }
    }

    /** Tir correspondant à l'arme, ou null si ce n'est pas une arme de tir. */
    public static Shoot create(Game g, int weapon, double x, double y) {
        Shoot s;
        switch (weapon) {
            case WEAPON_S_FIRE: s = new FireBall(); break;
            case WEAPON_S_ARROW: s = new Arrow(); break;
            case WEAPON_S_ICE: s = new Pearl(); break;
            default: return null;
        }
        s.initShoot(g, x, y);
        return s;
    }

    private PlayerShots() {
    }
}
