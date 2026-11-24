package hammerfest.sim.entity;

import hammerfest.sim.Game;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/bomb/PlayerBomb.mt} : propriétaire, amélioration au kick d'un autre joueur. */
public abstract class PlayerBomb extends Bomb {

    static final double UPGRADE_FACTOR = 1.5;
    static final int MAX_UPGRADES = 1;

    public Player owner;
    public int upgrades;
    public boolean fl_unstable;

    protected PlayerBomb(String linkage) {
        super(linkage);
        fl_airKick = true;
    }

    @Override
    public void init(Game g) {
        super.init(g);
        register(PLAYER_BOMB);
    }

    public void setOwner(Player p) {
        owner = p;
    }

    @Override
    public void hit(Entity e) {
        super.hit(e);
        if (fl_unstable && (e.types & BAD) > 0) onExplode();
    }

    @Override
    public void onHitGround(double h) {
        super.onHitGround(h);
        if (fl_unstable && fl_bounce) onExplode();
    }

    @Override
    public void onKick(Player p) {
        if (upgrades < MAX_UPGRADES && owner != null && p.pid != owner.pid) upgradeBomb(p);
        super.onKick(p);
        if (!fl_stable) fl_airKick = false;
    }

    void upgradeBomb(Player p) {
        game.fx("hammer_fx_pop", x, y);
        radius *= UPGRADE_FACTOR;
        power *= UPGRADE_FACTOR;
        setLifeTimer(duration * 0.7);
        dx *= 1.5;
        fl_blink = true;
        fl_alphaBlink = false;
        scale(scaleFactor * UPGRADE_FACTOR * 100);
        owner = p;
        upgrades++;
    }

    @Override
    public void onExplode() {
        super.onExplode();
        if (upgrades > 0) game.explosion(x, y, radius); // bombe améliorée
        if (game.fl_bombExpert) {
            for (Player p : game.players()) {
                double dist = distance(p.x, p.y);
                if (dist <= radius) {
                    double ratio = (radius - dist) / radius;
                    p.knock(SECOND + 2 * SECOND * ratio);
                    double ang = Math.atan2(p.y - y, p.x - x);
                    p.dx = Math.cos(ang) * power * 0.7 * ratio;
                    p.dy = Math.sin(ang) * power * 0.7 * ratio;
                }
            }
        }
        game.onExplode(x, y, radius);
        if (owner != null) { // champignons : un objet à points à chaque explosion
            for (int m = 14; m <= 17; m++) {
                if (owner.specialMan.active(m)) ScoreItem.attach(game, x, y, 47 + (m - 14), -1);
            }
        }
    }

    @Override
    public void update() {
        super.update();
        if (upgrades > 0 && !isBlinking()) blink(BLINK_DURATION_FAST);
    }
}
