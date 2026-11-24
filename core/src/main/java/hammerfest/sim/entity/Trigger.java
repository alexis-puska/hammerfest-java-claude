package hammerfest.sim.entity;

import java.util.ArrayList;
import java.util.List;

import static hammerfest.sim.Const.*;

/** Portage de {@code entity/Trigger.mt} : présence de l'entité dans les cases du monde. */
public abstract class Trigger extends Entity {

    public boolean fl_largeTrigger;

    protected void tAddSingle(int cx, int cy) {
        if (cx < 0 || cx >= LEVEL_WIDTH || cy < 0 || cy >= LEVEL_HEIGHT) return;
        world.triggers[cx][cy].add(this);
    }

    protected void tRemSingle(int cx, int cy) {
        if (cx < 0 || cx >= LEVEL_WIDTH || cy < 0 || cy >= LEVEL_HEIGHT) return;
        world.triggers[cx][cy].removeIf(e -> e == this);
    }

    public void tAdd(int cx, int cy) {
        tAddSingle(cx, cy);
        tAddSingle(cx - 1, cy);
        tAddSingle(cx + 1, cy);
        tAddSingle(cx, cy - 1);
        if (fl_largeTrigger) tAddSingle(cx, cy + 1);
    }

    public void tRem(int cx, int cy) {
        tRemSingle(cx, cy);
        tRemSingle(cx - 1, cy);
        tRemSingle(cx + 1, cy);
        tRemSingle(cx, cy - 1);
        if (fl_largeTrigger) tRemSingle(cx, cy + 1);
    }

    public void moveTo(double x, double y) {
        tRem(cx, cy);
        this.x = x;
        this.y = y - 1;
        updateCoords();
        tAdd(cx, cy);
    }

    public void moveToCase(int cx, int cy) {
        moveTo(xCtr(cx), yCtr(cy));
    }

    /** Entités du type donné présentes dans la case courante. */
    public List<Entity> getByType(int type) {
        List<Entity> out = new ArrayList<>();
        if (cx < 0 || cx >= LEVEL_WIDTH || cy < 0 || cy >= LEVEL_HEIGHT) return out;
        for (Entity e : world.triggers[cx][cy]) {
            if ((e.types & type) != 0) out.add(e);
        }
        return out;
    }
}
