package hammerfest.render;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static hammerfest.sim.Const.*;

/**
 * Particules décoratives (entity/fx/Particle.mt, FxManager.inGameParticlesDir) : éclats de glace, étincelles,
 * pierres, poussière, métal, bulles… Elles rebondissent sur le décor mais restent hors de la simulation
 * (leur hasard est propre au rendu) pour ne pas en modifier le déroulement.
 */
public final class ParticleSystem {

    /** Data.PARTICLE_* (image du clip hammer_fx_particle). */
    public static final int ICE = 1, CLASSIC_BOMB = 2, STONE = 3, SPARK = 4, DUST = 5, ORANGE = 6, METAL = 7,
            TUBERCULOZ = 8, RAIN = 9, LITCHI = 10, BUBBLE = 11, ICE_BAD = 12, BLOB = 13, FRAMB = 14;
    static final int MAX = 120;

    static final class P {
        int pid, subFrame;
        double x, y, dx, dy, scale, rotation, alpha = 1, life, totalLife, bounce = 0.6, gravity = 1, skipWallsY;
        boolean gravityOn = true, stuck;
    }

    private final List<P> list = new ArrayList<>();
    private final Random rng = new Random();
    private final Sprites sprites;
    /** Goutte de pluie qui touche le sol (Particle.onHitGround : hammer_fx_water_drop), coordonnées du jeu. */
    public java.util.function.BiConsumer<Double, Double> onRainDrop;

    public ParticleSystem(Sprites sprites) {
        this.sprites = sprites;
    }

    private int random(int n) {
        return n <= 0 ? 0 : rng.nextInt(n);
    }

    /** FxManager.inGameParticlesDir : {@code n} particules, alternées gauche/droite (ou toutes du côté de {@code dir}). */
    public void spawn(int pid, double x, double y, int n, double dir) {
        if (n <= 0) return;
        while (list.size() + n > MAX && !list.isEmpty()) list.remove(0);
        boolean left = rng.nextBoolean();
        int frames = Math.max(1, sprites.frames("hammer_fx_particle", pid - 1));
        for (int i = 0; i < n; i++) {
            P p = new P();
            p.pid = pid;
            p.x = x;
            p.y = y;
            p.skipWallsY = y;
            p.totalLife = p.life = SECOND * 2 + SECOND * 2 * random(100) / 100.0;
            if (pid != RAIN) {
                double bounce = random(65) / 10.0 + 1.5;
                p.dx = (1 + random(50) / 10.0) * (rng.nextBoolean() ? 1 : -1);
                p.dy = -bounce;
            }
            p.subFrame = random(frames) + 1;
            p.scale = (random(50) + 50) / 100.0;
            p.rotation = random(360);
            switch (pid) {
                case SPARK:
                    p.scale *= 2;
                    p.totalLife = p.life = SECOND;
                    break;
                case STONE:
                    p.scale = 1.5;
                    break;
                case METAL:
                    p.scale = (random(40) + 80) / 100.0;
                    p.bounce = 0.3;
                    break;
                case LITCHI:
                    p.bounce = 0.4;
                    break;
                case BUBBLE:
                    p.gravityOn = false;
                    p.alpha = (random(80) + 20) / 100.0;
                    p.dx = 0;
                    p.dy = -(0.3 + random(10) / 10.0);
                    p.scale = (random(40) + 30) / 100.0;
                    p.totalLife = p.life = SECOND + random((int) (SECOND * 20)) / 10.0;
                    break;
                case ICE_BAD:
                    p.bounce = 0.5;
                    p.dx *= 0.3;
                    p.dy = -Math.abs(p.dy);
                    break;
                case BLOB:
                    p.gravity = 0.6;
                    p.totalLife = p.life = SECOND * 3;
                    break;
                default:
                    break;
            }
            if (x <= CASE_WIDTH) left = false;
            if (x >= GAME_WIDTH - CASE_WIDTH) left = true;
            if (!Double.isNaN(dir) && dir != 0) left = dir < 0;
            if (pid != BUBBLE) p.dx = left ? -Math.abs(p.dx) : Math.abs(p.dx);
            left = !left;
            list.add(p);
        }
    }

    /** Un cycle de jeu (Particle.update) : gravité, rebonds sur le sol, les murs et la ligne du bas. */
    public void tick(java.util.function.IntBinaryOperator caseAt) {
        for (Iterator<P> it = list.iterator(); it.hasNext(); ) {
            P p = it.next();
            if (p.pid == BUBBLE || p.pid == SPARK || Math.abs(p.dx) <= 0.5 || p.pid == BLOB) p.life--;
            if (p.life <= 0 || p.y > GAME_HEIGHT + 20) {
                it.remove();
                continue;
            }
            if (p.stuck) continue;
            if (p.gravityOn) p.dy += GRAVITY * p.gravity;
            double nx = p.x + p.dx, ny = p.y + p.dy;
            if (caseAt != null) {
                int cx = (int) Math.floor(p.x / CASE_WIDTH), ncx = (int) Math.floor(nx / CASE_WIDTH);
                int cy = (int) Math.floor(p.y / CASE_HEIGHT), ncy = (int) Math.floor(ny / CASE_HEIGHT);
                if (p.pid == BUBBLE && p.dy < 0 && ncy != cy && caseAt.applyAsInt(cx, ncy) > 0) { // bulle : éclate au plafond
                    it.remove();
                    continue;
                }
                if (p.dy > 0 && ncy != cy && caseAt.applyAsInt(cx, ncy) > 0) { // sol
                    ny = ncy * CASE_HEIGHT - 0.1;
                    if (p.pid == RAIN) { // la goutte éclate
                        if (onRainDrop != null) onRainDrop.accept(nx, ny);
                        it.remove();
                        continue;
                    }
                    if (p.pid == BLOB) {
                        p.dx = 0;
                        p.dy = 0;
                        p.stuck = true;
                    } else {
                        p.dy = -p.dy * p.bounce;
                        if (Math.abs(p.dy) <= 1.5) p.dx *= 0.75;
                    }
                    p.skipWallsY = 0;
                }
                if (ncx != cx && caseAt.applyAsInt(ncx, (int) Math.floor(ny / CASE_HEIGHT)) > 0 && ny > p.skipWallsY) { // mur
                    p.dx = p.pid == BLOB ? 0 : -p.dx * 0.5;
                    nx = p.x;
                }
            }
            p.x = nx;
            p.y = ny;
            p.rotation += p.dx * 2;
            if (p.pid == BUBBLE) p.alpha = Math.min(1, 1.5 * p.life / p.totalLife);
            if (p.pid == BLOB) p.scale = Math.min(1, 1.5 * p.life / p.totalLife);
        }
    }

    public void clear() {
        list.clear();
    }

    public void render(SpriteBatch batch, float xOffset, float worldHeight) {
        for (P p : list) {
            batch.setColor(1, 1, 1, (float) p.alpha);
            sprites.draw(batch, "hammer_fx_particle", p.pid - 1, p.subFrame, xOffset + (float) p.x, (float) p.y,
                    (float) p.scale, (float) p.scale, (float) p.rotation, worldHeight);
        }
        batch.setColor(1, 1, 1, 1);
    }
}
