package hammerfest.sim.entity;

import java.util.List;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/Physics.mt} : gravité, frictions, découpage du déplacement en pas
 * (« stepping ») et collisions avec la grille (murs, sols, plafonds).
 *
 * <p>Non encore porté : téléporteurs et portails (nécessitent TeleporterData / dimensions),
 * vent, bulles en mode aquatique.</p>
 */
public abstract class Physics extends Animator {

    public double dx, dy;
    /** Hauteur de départ de chute ; NaN = null. */
    public double fallStart = Double.NaN;
    public double gravityFactor = 1.0;
    public double fallFactor = 1.0;
    /** NaN = friction de glisse du monde. */
    public double slideFriction = Double.NaN;
    public double shockResistance = 1.0;

    public boolean fl_stable;
    public boolean fl_physics = true;
    public boolean fl_friction = true;
    public boolean fl_gravity = true;
    public boolean fl_strictGravity = true;
    public boolean fl_hitGround = true;
    public boolean fl_hitCeil;
    public boolean fl_hitWall = true;
    public boolean fl_hitBorder = true;
    public boolean fl_slide = true;
    public boolean fl_teleport;
    public boolean fl_portal;
    public boolean fl_wind;
    public boolean fl_moveable = true;
    public boolean fl_bump;

    public boolean fl_stopStepping;
    public boolean fl_skipNextGravity = true;
    public boolean fl_skipNextGround;

    protected Physics(String linkage) {
        super(linkage);
    }

    @Override
    public void init(hammerfest.sim.Game g) {
        super.init(g);
        register(PHYSICS);
    }

    public void shockWave(Physics e, double radius, double power) {
        if (!e.fl_moveable) return;
        power /= e.shockResistance;
        double dist = e.distance(x, y);
        double ratio = 1 - dist / radius;
        double ang = Math.atan2(e.y - y, e.x - x);
        e.dx = Math.cos(ang) * ratio * power;
        if (e.fl_stable) {
            e.dy = -5;
        } else {
            if (e instanceof hammerfest.sim.entity.bad.Flyer && ((hammerfest.sim.entity.bad.Flyer) e).fl_intercept) {
                e.dy = 0; // volant gelé en vol : poussé à l'horizontale
                e.dx *= 2;
            } else {
                e.dy += Math.sin(ang) * ratio * power;
            }
            e.dy = Math.max(e.dy, -10);
        }
        e.fl_stable = false;
    }

    public void killHit(double dx) {
        if (fl_kill) return;
        this.dx = dx;
        this.dy = -10;
        fl_hitGround = false;
        fl_hitWall = false;
        fl_kill = true;
        fallFactor = FALL_FACTOR_DEAD;
        onKill();
    }

    public void resurrect() {
        fl_hitGround = true;
        fl_hitWall = true;
        fl_kill = false;
        fallFactor = 1.0;
        fl_stopStepping = true;
    }

    public void moveToAng(double angDeg, double speed) {
        double rad = Math.PI * angDeg / 180;
        dx = Math.cos(rad) * speed;
        dy = Math.sin(rad) * speed;
    }

    public void moveUp(double speed) {
        moveToAng(-90, speed);
    }

    public void moveDown(double speed) {
        moveToAng(90, speed);
    }

    public void moveLeft(double speed) {
        moveToAng(180, speed);
    }

    public void moveRight(double speed) {
        moveToAng(0, speed);
    }

    public void moveToTarget(Entity e, double speed) {
        double rad = Math.atan2(e.y - y, e.x - x);
        dx = Math.cos(rad) * speed;
        dy = Math.sin(rad) * speed;
    }

    /** Part d'une autre entité dans une direction au hasard vers le haut (Physics.moveFrom). */
    public void moveFrom(Entity e, double speed) {
        double ang = -game.random((int) Math.round(100 * Math.PI)) / 100.0;
        x = e.x + Math.cos(ang) * CASE_WIDTH * 2;
        y = e.y + Math.sin(ang) * CASE_HEIGHT * 2;
        moveToTarget(e, speed);
        dx = -dx;
        dy = -dy;
    }

    public void moveToGround() {
        if (fl_stable) return;
        int[] pt = world.getGround(cx, cy);
        moveToCase(pt[0], pt[1]);
    }

    // ------------------------------------------------------------------ stepping

    public void checkHits() {
        List<Entity> l = getByType(ENTITY);
        for (Entity e : l) {
            if (!e.fl_kill && !fl_kill && hitBound(e) && e.uniqId != uniqId) {
                hit(e);
                e.hit(this);
            }
        }
    }

    public void prefix() {
    }

    /** Changement de case : collisions entre entités, bumpers. */
    public void infix() {
        checkHits();
        int cid = world.getCase(cx, cy);

        // Téléporteurs
        if (fl_teleport && !fl_kill) {
            if (cid == FIELD_TELEPORT) {
                hammerfest.sim.World.Teleporter start = world.getTeleporter(lastTeleporter, cx, cy);
                Object[] next = start == null ? null : world.getNextTeleporter(start, game);
                if (next != null) {
                    hammerfest.sim.World.Teleporter target = (hammerfest.sim.World.Teleporter) next[0];
                    boolean rand = (Boolean) next[1];
                    game.fx("hammer_fx_pop", x, y - CASE_HEIGHT);
                    if (target.dir == HORIZONTAL) {
                        moveTo(rand ? target.centerX - CASE_WIDTH * 0.5 : target.centerX + xCtr(cx) - start.centerX, target.centerY);
                    } else {
                        moveTo(target.centerX, rand ? target.centerY : target.centerY + yCtr(cy) - start.centerY);
                    }
                    game.fx("hammer_fx_shine", x, y - CASE_HEIGHT);
                    game.sound("sound_teleport");
                    fl_stopStepping = true;
                    lastTeleporter = target;
                    onTeleport();
                }
            } else {
                lastTeleporter = null;
            }
        }

        // Portails : utilisables seulement une fois le niveau terminé
        if (fl_portal && !fl_kill && cid == FIELD_PORTAL) {
            if (game.fl_clear) onPortal(world.portalAt(cx, cy));
            else onPortalRefusal();
        }

        if (fl_bump && !fl_kill && cid == FIELD_BUMPER) {
            int fdir = VERTICAL;
            if (world.getCase(cx - 1, cy) == cid || world.getCase(cx + 1, cy) == cid) fdir = HORIZONTAL;
            if (fdir == HORIZONTAL && Math.abs(dy) < 20) {
                dx = 0;
                dy *= 5;
            }
            if (fdir == VERTICAL && Math.abs(dx) < 20) {
                dx *= 7;
                dy = 0;
            }
            onBump();
        }
    }

    public void postfix() {
    }

    public void recal() {
        y = yCtr(yRtc(y));
        updateCoords();
        while (world.getCase(cx, cy) == GROUND) {
            y -= CASE_HEIGHT;
            updateCoords();
        }
    }

    public boolean needsPatch() {
        return false;
    }

    /** y - fallStart, avec fallStart null valant 0 comme en ActionScript. */
    double fallHeight() {
        return Double.isNaN(fallStart) ? y : y - fallStart;
    }

    // ------------------------------------------------------------------ événements

    public void onKill() {
    }

    public void onDeathLine() {
    }

    public void onHitWall() {
        dx = 0;
    }

    public void onHitGround(double height) {
        fl_stable = true;
        dy = 0;
        recal();
    }

    public void onHitCeil() {
        dy = 0;
    }

    public void onBump() {
    }

    public void onPortal(int pid) {
    }

    /** Téléporteur par lequel l'entité vient d'arriver (on ne le reprend pas aussitôt). */
    public hammerfest.sim.World.Teleporter lastTeleporter;

    public void onTeleport() {
    }

    public void onPortalRefusal() {
    }

    // ------------------------------------------------------------------ mises à jour

    @Override
    public void endUpdate() {
        if (fl_hitBorder) {
            if (x < BORDER_MARGIN) x = BORDER_MARGIN;
            if (x >= GAME_WIDTH - BORDER_MARGIN) x = GAME_WIDTH - BORDER_MARGIN - 1;
        }
        super.endUpdate();
    }

    @Override
    public void update() {
        super.update();
        if (!fl_physics) return;

        updateCoords();

        // Chute
        if (fl_stable && (dy != 0 || world.getCase(fcx, fcy) != GROUND)) fl_stable = false;

        if (dx != 0 || dy != 0 || !fl_stable) {
            // Gravité
            if (!fl_skipNextGravity && !fl_stable && fl_gravity) {
                double patchFactor = game.tmod >= 2 ? 1.1 : 1.0;
                if (dy < 0) {
                    dy += gravityFactor * GRAVITY * game.tmod * patchFactor;
                } else {
                    if (Double.isNaN(fallStart)) fallStart = y;
                    if (game.fl_aqua && !fl_strictGravity) {
                        dy += 0.3 * fallFactor * FALL_SPEED * game.tmod * patchFactor;
                    } else {
                        dy += fallFactor * FALL_SPEED * game.tmod * patchFactor;
                    }
                }
            }
            fl_skipNextGravity = false;

            prefix();

            double dxTotal = dx * game.tmod;
            double dyTotal = dy * game.tmod;
            int total = (int) Math.max(Math.ceil(Math.abs(dxTotal) / STEP_MAX), Math.ceil(Math.abs(dyTotal) / STEP_MAX));
            double stepDx = total == 0 ? 0 : dxTotal / total;
            double stepDy = total == 0 ? 0 : dyTotal / total;

            int step = 0;
            while (!fl_stopStepping && step < total) {
                int ocx = cx, ocy = cy;
                double ox = x, oy = y;
                oldX = x;
                oldY = y;

                x += stepDx;
                y += stepDy;
                updateCoords();

                // Pénétration dans les murs
                if (fl_hitWall) {
                    boolean hasHitWall = false;
                    if (dx > 0 && world.getCase(xRtc(ox + CASE_WIDTH * 0.5), yRtc(oy)) > 0) hasHitWall = true;
                    if (dx < 0 && world.getCase(xRtc(ox - CASE_WIDTH * 0.5), yRtc(oy)) > 0) hasHitWall = true;
                    if (hasHitWall) {
                        x = ox;
                        stepDx = 0;
                        updateCoords();
                        onHitWall();
                    }
                }

                // Collision horizontale
                if ((fl_hitBorder && (x < BORDER_MARGIN || x >= GAME_WIDTH - BORDER_MARGIN))
                        || (fl_hitWall && world.getCase(cx, yRtc(oy)) > 0)) {
                    x = ox;
                    stepDx = 0;
                    updateCoords();
                    onHitWall();
                }

                // Traversée de murs par le haut
                if (fl_hitWall && stepDy > 0 && !fl_kill) {
                    if (world.getCase(xRtc(ox), yRtc(oy + Math.floor(CASE_HEIGHT / 2.0))) != WALL
                            && world.getCase(fcx, fcy) == WALL) {
                        x = ox;
                        stepDx = 0;
                        updateCoords();
                        onHitWall();
                    }
                }

                // Atterrissage
                if (fl_hitGround && stepDy >= 0) {
                    if (world.getCase(xRtc(ox), yRtc(oy + Math.floor(CASE_HEIGHT / 2.0))) != GROUND
                            && world.getCase(fcx, fcy) == GROUND
                            && world.checkFlag(fcx, fcy, IA_TILE)) {
                        if (fl_skipNextGround) {
                            fl_skipNextGround = false;
                        } else {
                            stepDy = 0;
                            onHitGround(fallHeight());
                            fallStart = Double.NaN;
                            updateCoords();
                        }
                    }
                }

                // Plafond
                if (fl_hitCeil && stepDy <= 0) {
                    if (world.getCase(xRtc(ox), yRtc(oy - Math.floor(CASE_HEIGHT / 2.0))) <= 0
                            && world.getCase(xRtc(x), yRtc(y - Math.floor(CASE_HEIGHT / 2.0))) > 0) {
                        stepDy = 0;
                        onHitCeil();
                        updateCoords();
                    }
                }

                // Changement de case
                if (ocx != cx || ocy != cy) {
                    boolean patched = false;
                    if (fl_hitGround && ocy < cy && needsPatch()) {
                        if (world.getCase(ocx, ocy) <= 0 && dy > 0 && world.getCase(cx, cy) > 0 && cy < LEVEL_HEIGHT) {
                            x = xCtr(ocx);
                            y = yCtr(ocy);
                            stepDy = 0;
                            updateCoords();
                            onHitGround(fallHeight());
                            patched = true;
                        }
                    }
                    if (fl_hitGround && dy >= 0 && ocx != cx && ocy != cy) {
                        if (world.getCase(ocx, ocy) <= 0 && world.getCase(cx, cy) == GROUND) {
                            x = xCtr(ocx);
                            y = yCtr(ocy);
                            stepDx = 0;
                            updateCoords();
                            onHitWall();
                            patched = true;
                        }
                    }
                    if (!patched) {
                        tRem(ocx, ocy);
                        infix();
                        updateCoords();
                        tAdd(cx, cy);
                    }
                }
                step++;
            }
        }

        fl_stopStepping = false;
        postfix();

        // Frictions
        if (fl_friction) {
            if ((game.fl_ice || fl_slide) && fl_stable) {
                if (Double.isNaN(slideFriction)) dx *= game.sFriction;
                else dx *= Math.pow(slideFriction, game.tmod);
            } else {
                dx *= game.xFriction;
                dy *= game.yFriction;
            }
        }
        if (Math.abs(dx) <= 0.2) dx = 0;
        if (Math.abs(dy) <= 0.2) dy = 0;

        if (y >= DEATH_LINE) onDeathLine();
    }
}
