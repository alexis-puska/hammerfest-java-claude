package hammerfest.sim.entity.bad;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/bad/Shooter.mt} : sauteur qui prépare puis lance un tir. */
public class Shooter extends Jumper {

    public boolean fl_shooter;
    public double shootCD = PEACE_COOLDOWN;
    public double shootDuration;
    public double shootPreparation;
    public double chanceShoot;

    public Shooter(String linkage) {
        super(linkage);
    }

    public void initShooter(double prepa, double duration) {
        shootPreparation = prepa;
        shootDuration = duration;
    }

    /** Chance sur 100 ; négatif = ne tire pas. */
    public void setShoot(double chance) {
        fl_shooter = chance >= 0;
        chanceShoot = chance * 10;
    }

    @Override
    public void onNext() {
        boolean shoot = next != null && next.action == ACTION_SHOOT;
        super.onNext();
        if (shoot && next != null && next.action == ACTION_SHOOT) {
            setNext(Double.NaN, Double.NaN, shootDuration, ACTION_WALK);
            halt();
            playAnim(SHOOT_END);
            onShoot();
        }
    }

    public void startShoot() {
        setNext(dx, dy, shootPreparation, ACTION_SHOOT);
        halt();
        playAnim(SHOOT_START);
    }

    @Override
    public void onEndAnim(int id) {
        super.onEndAnim(id);
        if (id == SHOOT_START.id) playAnim(SHOOT_LOOP);
    }

    public void onShoot() {
    }

    @Override
    public void update() {
        if (shootCD > 0) shootCD -= game.tmod;
        if (fl_shooter && shootCD <= 0 && isReady() && game.random(1000) < chanceShoot) startShoot();
        super.update();
    }
}
