package hammerfest.sim.entity;

import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/** Bombes du joueur autres que la classique (entity/bomb/player/*.mt), obtenues via les champs d'armes. */
public final class PlayerBombs {

    /** Noire : tue les ennemis dans le rayon et les projette. */
    public static class Black extends PlayerBomb {
        public Black() {
            super("hammer_bomb_black");
            duration = 100;
            power = 20;
            explodeSound = "sound_bomb_black";
        }

        @Override
        public Bomb duplicate() {
            return make(new Black(), game, x, y);
        }

        @Override
        public void onExplode() {
            super.onExplode();
            game.explodeZone(x, y, radius);
            game.shake(10, 4);
            for (Entity e : bombGetClose(BAD)) {
                Bad b = (Bad) e;
                b.setCombo(uniqId);
                b.killHit(0);
                shockWave(b, radius, power);
                b.dy = -10 - game.random(20);
            }
        }
    }

    /** Bleue : assomme les ennemis et les fait tomber d'un étage. */
    public static class Blue extends PlayerBomb {
        public Blue() {
            super("hammer_bomb_blue");
            duration = 45;
            power = 20;
            explodeSound = "sound_bomb_blue";
        }

        @Override
        public Bomb duplicate() {
            return make(new Blue(), game, x, y);
        }

        @Override
        public void onExplode() {
            super.onExplode();
            for (Entity e : bombGetClose(BAD)) {
                Bad b = (Bad) e;
                b.setCombo(uniqId);
                b.knock(KNOCK_DURATION * 0.75);
                shockWave(b, radius, power);
                b.dx *= 0.3;
                b.dy = -6;
                b.yTrigger = b.y + CASE_HEIGHT * 1.5;
                b.fl_hitGround = false;
            }
        }
    }

    /** Verte : mine qui explose au contact d'un ennemi (gel), longue durée. */
    public static class Green extends PlayerBomb {
        public Green() {
            super("hammer_bomb_green");
            duration = 200;
            power = 25;
            fl_blink = true;
            fl_alphaBlink = false;
            fl_unstable = true;
            explodeSound = "sound_bomb_green";
        }

        @Override
        public Bomb duplicate() {
            return make(new Green(), game, x, y);
        }

        @Override
        public void endUpdate() {
            super.endUpdate();
            rotation = 0;
        }

        @Override
        public void onExplode() {
            if (fl_explode) return;
            game.sound("sound_bomb");
            super.onExplode();
            game.particles(PARTICLE_ICE, x, y, 2, 2);
            for (Entity e : bombGetClose(BAD)) {
                Bad b = (Bad) e;
                b.setCombo(uniqId);
                b.freeze(FREEZE_DURATION);
                shockWave(b, radius, power);
            }
        }
    }

    /** Rouge : gèle et projette en l'air ; propulse le joueur vers le haut (saut à la bombe). */
    public static class Red extends PlayerBomb {
        static final double JUMP_POWER = 32;

        public Red() {
            super("hammer_bomb_red");
            duration = 38;
            power = 30;
        }

        @Override
        public Bomb duplicate() {
            return make(new Red(), game, x, y);
        }

        @Override
        public void onExplode() {
            super.onExplode();
            game.explodeZone(x, y, radius);
            game.particles(PARTICLE_ICE, x, y, 2, 2);
            for (Entity e : bombGetClose(BAD)) {
                Bad b = (Bad) e;
                b.setCombo(uniqId);
                b.freeze(FREEZE_DURATION);
                shockWave(b, radius, power);
                if (b.dy < 0) {
                    b.dy *= 3;
                    if (distance(b.x, b.y) <= radius * 0.5) {
                        b.dx *= 0.5;
                        b.dy *= 2;
                    }
                }
            }
            for (Player p : game.players()) {
                double distX = p.x - x, distY = p.y - y;
                if (fl_stable) {
                    distX *= 1.5;
                    distY *= 0.5;
                } else {
                    distX *= 0.9;
                    distY *= 0.35;
                }
                if (Math.sqrt(distX * distX + distY * distY) <= 40) {
                    if (p.dy > 0) p.dy = 0;
                    p.dy -= JUMP_POWER;
                    if (p.dy <= -35) game.shake(10, 3);
                } else if (p.distance(x, y) <= radius) {
                    shockWave(p, radius, power);
                }
            }
        }
    }

    /** Répulsive : repousse et assomme les joueurs. */
    public static class Repel extends PlayerBomb {
        public Repel() {
            super("hammer_bomb_repel");
            duration = 38;
            power = 20;
            radius = CASE_WIDTH * 3;
        }

        @Override
        public Bomb duplicate() {
            return make(new Repel(), game, x, y);
        }

        @Override
        public void onKick(Player p) {
            super.onKick(p);
            dx *= 2.5;
        }

        @Override
        public void onExplode() {
            super.onExplode();
            game.explodeZone(x, y, radius);
            game.particles(PARTICLE_ICE, x, y, 2, 2);
            for (Player p : game.players()) {
                double dist = distance(p.x, p.y);
                if (dist <= radius) {
                    double ratio = (radius - dist) / radius;
                    p.knock(SECOND + SECOND * ratio);
                    double ang = Math.atan2(p.y - y, p.x - x);
                    p.dx = Math.cos(ang) * power * ratio;
                    p.dy = Math.sin(ang) * power * ratio;
                }
            }
        }
    }

    static <B extends Bomb> B make(B b, Game g, double x, double y) {
        b.initBomb(g, x, y);
        return b;
    }

    /** Bombe correspondant à l'arme (Player.attack), classique par défaut. */
    public static PlayerBomb create(Game g, int weapon, double x, double y) {
        switch (weapon) {
            case WEAPON_B_BLACK: return make(new Black(), g, x, y);
            case WEAPON_B_BLUE: return make(new Blue(), g, x, y);
            case WEAPON_B_GREEN: return make(new Green(), g, x, y);
            case WEAPON_B_RED: return make(new Red(), g, x, y);
            case WEAPON_B_REPEL: return make(new Repel(), g, x, y);
            default: return ClassicBomb.attach(g, x, y);
        }
    }

    private PlayerBombs() {
    }
}
