package hammerfest.sim.entity;

import hammerfest.sim.Anim;
import hammerfest.sim.Game;
import hammerfest.sim.InputFrame;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/Player.mt} (déplacements, animations, mort, passage de niveau).
 *
 * <p>Non encore porté : bombes et armes (attack/kick), SpecialManager (effets d'objets),
 * bouclier, oxygène, malédictions, score détaillé.</p>
 */
public class Player extends Physics {

    /** Boîte de collision approximative d'Igor à l'échelle de jeu (sous-clip à 70 %). */
    static final double WIDTH = 22;
    static final double HEIGHT = 34;

    public String name = "Igor";
    public final int pid;
    public PlayerController ctrl;
    public Anim baseWalkAnim = Anim.PLAYER_WALK;
    public Anim baseStopAnim = Anim.PLAYER_STOP;

    /** Player.setBaseAnims : change les animations de marche et d'arrêt, et les applique aussitôt si besoin. */
    public void setBaseAnims(Anim walk, Anim stop) {
        boolean wasWalking = animId == baseWalkAnim.id;
        boolean wasStopped = animId == baseStopAnim.id || animId == Anim.PLAYER_WAIT1.id || animId == Anim.PLAYER_WAIT2.id;
        if (walk != null) baseWalkAnim = walk;
        if (stop != null) baseStopAnim = stop;
        if (wasWalking) playAnim(baseWalkAnim);
        if (wasStopped) playAnim(baseStopAnim);
    }
    public double speedFactor = 1.0;
    public int dir = 1;
    public int score;
    public int lives = 1;
    public int maxBombs;
    public double coolDown;
    public boolean fl_lockControls;
    public boolean fl_entering;
    public boolean fl_knock;
    public double knockTimer;
    public double lockTimer;
    public double edgeTimer;
    public double waitTimer;
    public double startX;

    public Player(int pid) {
        super("hammer_player");
        this.pid = pid;
        fallFactor = 1.1;
        fl_teleport = true;
        fl_portal = true;
        fl_wind = true;
        fl_strictGravity = false;
        fl_bump = true;
    }

    public hammerfest.sim.SpecialManager specialMan;
    public int initialMaxBombs;
    public boolean fl_carot;
    /** Bougie (famille 106) : halo plus grand dans l'obscurité ; torche (107) : obscurité réduite. */
    public boolean fl_candle, fl_torch;
    /** Poils de Chourou (objet 99) en cours : collier visible (clip 483 : _visible = fl_chourou). */
    public boolean fl_chourou;
    public int head = 1;
    /** Tête par défaut (Igor : normale, Sandy en coopératif) et apparence du corps (Data skin). */
    public int defaultHead = HEAD_NORMAL;
    public int skin = 1;
    public boolean fl_shield;
    public double shieldTimer;
    final boolean[] extendList = new boolean[EXTENDS];
    final java.util.List<Integer> extendOrder = new java.util.ArrayList<>();

    /** Lettres EXTEND ramassées (bit i = lettre i), pour le bandeau. */
    public int extendMask() {
        int m = 0;
        for (int i = 0; i < extendList.length; i++) if (extendList[i]) m |= 1 << i;
        return m;
    }

    public void initPlayer(Game g, double x, double y) {
        init(g);
        register(PLAYER);
        ctrl = new PlayerController(this);
        specialMan = new hammerfest.sim.SpecialManager(g, this);

        // Bonus permanents des quêtes (familles 100+)
        initialMaxBombs = 1 + (g.hasFamily(100) ? 1 : 0);
        maxBombs = initialMaxBombs;
        fl_carot = g.hasFamily(108);
        fl_candle = g.hasFamily(106);
        fl_torch = g.hasFamily(107);
        for (int f : new int[]{102, 103, 104, 105, 108}) if (g.hasFamily(f)) lives++;
        if (g.fl_timeAttack) lives = 3; // TimeAttack.initializePlayers
        if (g.fl_tutorial) lives = 99999; // Tutorial.initGame

        moveTo(x, y);
        if (g.fl_nightmare) speedFactor = 1.3;
        if (boosted(g)) speedFactor = g.fl_nightmare ? 1.6 : 1.3;
        endUpdate();
    }

    @Override
    public double width() {
        return WIDTH;
    }

    @Override
    public double height() {
        return HEIGHT;
    }

    // ------------------------------------------------------------------ armes et bombes

    public int currentWeapon = WEAPON_B_CLASSIC;
    public int lastBomb = WEAPON_B_CLASSIC;
    /** Bombes récemment kickées : {cycle, uniqId} (évite de kicker deux fois la même bombe). */
    final java.util.List<int[]> recentKicks = new java.util.ArrayList<>();

    static boolean isBombWeapon(int id) {
        return id == WEAPON_B_CLASSIC || id == WEAPON_B_BLACK || id == WEAPON_B_BLUE
                || id == WEAPON_B_GREEN || id == WEAPON_B_RED || id == WEAPON_B_REPEL;
    }

    static boolean isShootWeapon(int id) {
        return id == WEAPON_S_ARROW || id == WEAPON_S_FIRE || id == WEAPON_S_ICE;
    }

    public void changeWeapon(int id) {
        if (id > 0 && !isBombWeapon(id) && !isShootWeapon(id)) return;
        if (isBombWeapon(currentWeapon) && !isBombWeapon(id)) lastBomb = currentWeapon;
        if (isBombWeapon(id)) lastBomb = id;
        currentWeapon = id;
        replayAnim();
    }

    /** Player.attack : pose la bombe de l'arme courante, ou tire. */
    public Entity attack() {
        if (specialMan.active(91) || specialMan.active(85)) return null; // malédiction de paix
        if (currentWeapon <= 0) return null;
        if (isShootWeapon(currentWeapon)) return shoot(PlayerShots.create(game, currentWeapon, x, y));
        return drop(PlayerBombs.create(game, currentWeapon, x, y));
    }

    /** Lance le tir dans la direction du joueur, avec un temps de recharge (Player.shoot). */
    Shoot shoot(Shoot s) {
        coolDown = s.coolDown;
        if (dir < 0) s.moveLeft(s.shootSpeed);
        else s.moveRight(s.shootSpeed);
        return s;
    }

    PlayerBomb drop(PlayerBomb b) {
        if (!fl_stable) airJump();
        b.setOwner(this);
        game.sound("sound_bomb_drop");
        if (!fl_stable) kickBomb(java.util.List.of(b), 1.0);
        return b;
    }

    boolean isRecentKick(Bomb b) {
        for (int[] k : recentKicks) if (k[1] == b.uniqId) return true;
        return false;
    }

    public int countBombs() {
        int n = 0;
        for (Entity e : game.getList(PLAYER_BOMB)) {
            if (!((Bomb) e).fl_explode && e.parent == this) n++;
        }
        return n;
    }

    public void kickBomb(java.util.List<? extends Entity> l, double powerFactor) {
        for (Entity e : l) {
            Bomb b = (Bomb) e;
            if (isRecentKick(b)) continue;
            if ((b.fl_airKick || b.fl_stable) && !b.fl_explode) {
                b.dx = dir * PLAYER_HKICK_X;
                if (dir < 0 && world.checkFlag(cx, cy, IA_CLIMB_LEFT)) {
                    Integer h = wallHeight(cx - 1, cy);
                    if (h != null && h <= 1) b.moveTo(b.x, b.y - CASE_HEIGHT * 0.5);
                }
                if (dir > 0 && world.checkFlag(cx, cy, IA_CLIMB_RIGHT)) {
                    Integer h = wallHeight(cx + 1, cy);
                    if (h != null && h <= 1) b.moveTo(b.x, b.y - CASE_HEIGHT * 0.5);
                }
                if (specialMan.active(13)) b.dx *= 2; // casque de moto
                if (game.fl_bombExpert && dx / b.dx > 0) b.dx *= 2.5;
                if (specialMan.active(115)) b.dx *= 1.5; // casque de volley
                b.dy = -PLAYER_HKICK_Y;
                b.onKick(this);
                b.next = null;
                b.fl_bounce = true;
                recentKicks.add(new int[]{game.cycle, b.uniqId});
                playAnim(Anim.PLAYER_KICK);
                game.sound("sound_kick");
                if (specialMan.active(92) && b.lifeTimer > 0) { // chapeau rose : bombe dupliquée
                    Bomb b2 = b.duplicate();
                    if (b2 != null) {
                        if (b2 instanceof PlayerBomb) ((PlayerBomb) b2).owner = this;
                        b2.lifeTimer = b.lifeTimer;
                        b2.dx = -b.dx;
                        b2.dy = b.dy;
                        b2.fl_bounce = true;
                    }
                }
                if (specialMan.active(70)) getScore(this, 10); // trèfle
            }
        }
    }

    public void upKickBomb(java.util.List<? extends Entity> l) {
        for (Entity e : l) {
            Bomb b = (Bomb) e;
            if (isRecentKick(b)) continue;
            if ((b.fl_airKick || b.fl_stable) && !b.fl_explode) {
                b.dx *= 2;
                if (Math.abs(b.dx) <= 1.5) b.dx = 0.5 * dir;
                b.dy = -PLAYER_VKICK;
                if (specialMan.active(13)) b.dy *= 2;
                if (specialMan.active(115)) {
                    b.dy *= 2;
                    b.dx *= 1.3;
                }
                if (specialMan.active(70)) getScore(this, 10);
                b.next = null;
                b.onKick(this);
                b.fl_stable = false;
                b.fl_bounce = true;
                recentKicks.add(new int[]{game.cycle, b.uniqId});
                playAnim(Anim.PLAYER_KICK);
                game.sound("sound_kick");
            }
        }
    }

    /** GameMechanics.getWallHeight (null si la hauteur atteint IA_CLIMB). */
    private Integer wallHeight(int cx, int cy) {
        int h = 0;
        while (world.getCase(cx, cy - h) > 0 && h < IA_CLIMB) h++;
        return h >= IA_CLIMB ? null : h;
    }

    // ------------------------------------------------------------------ score et objets

    public int extraLifeCurrent;

    /** Ramasse les objets touchés (Player.hit). */
    @Override
    public void hit(Entity e) {
        if ((e.types & ITEM) > 0) ((Item) e).execute(this);
    }

    /** Points gagnés, avec affichage au-dessus de l'origine (Player.getScore). */
    public void getScore(Entity origin, int value) {
        if (game.fl_timeAttack) return; // Time Attack : pas de score
        if (origin != null) game.scorePop(origin.x, origin.y, specialMan.active(95) ? value * 2 : value);
        getScoreHidden(value);
    }

    /** Lettre EXTEND ramassée ; le mot complet donne une vie (bonus si dans l'ordre). */
    public void getExtend(int id) {
        if (id < 0 || id >= EXTENDS) return;
        if (!extendList[id]) extendOrder.add(id);
        extendList[id] = true;
        for (boolean b : extendList) if (!b) return;
        boolean perfect = true;
        for (int i = 0; i < extendOrder.size(); i++) if (extendOrder.get(i) != i) perfect = false;
        java.util.Arrays.fill(extendList, false);
        extendOrder.clear();
        specialMan.executeExtend(perfect);
    }

    public boolean hasExtend(int id) {
        return extendList[id];
    }

    /** Cycle de création de la bulle (attachFx), pour son animation. */
    public long shieldStart;

    public void shield(double duration) {
        if (Double.isNaN(duration)) duration = SHIELD_DURATION;
        shieldTimer = duration; // bulle dessinée par GameScreen tant que fl_shield
        shieldStart = game.cycle; // shieldMC recréé : la bulle se reforme
        shieldX = x;
        shieldY = y - 20;
        fl_shield = true;
    }

    /** Position de la bulle du bouclier (shieldMC), qui rattrape le joueur à 75 % par cycle. */
    public double shieldX, shieldY;

    public void unshield() {
        fl_shield = false;
        shieldTimer = 0;
        game.fx("popShield", x, y);
        checkHits();
    }

    /** Points sans affichage ; vie supplémentaire à chaque palier (Data.EXTRA_LIFE_STEPS). */
    public void getScoreHidden(int value) {
        if (game.fl_timeAttack) return;
        if (specialMan.active(95)) value *= 2; // sac à thunes
        if (extraLifeCurrent < EXTRA_LIFE_STEPS.length) {
            int step = EXTRA_LIFE_STEPS[extraLifeCurrent];
            if (score < step && score + value >= step) {
                lives++;
                extraLifeCurrent++;
            }
        }
        score += value;
    }

    // ------------------------------------------------------------------ actions

    public void airJump() {
        playAnim(Anim.PLAYER_JUMP_UP);
        if (dy > 0) {
            dy = -PLAYER_AIR_JUMP;
        } else if (Math.abs(dy) < PLAYER_AIR_JUMP) {
            dy = -PLAYER_AIR_JUMP;
        }
    }

    public void knock(double d) {
        if (fl_knock) return;
        fl_lockControls = true;
        fl_knock = true;
        knockTimer = d;
    }

    public void lockControls(double d) {
        lockTimer = d;
        playAnim(baseStopAnim);
        fl_lockControls = true;
    }

    public void forceKill(double dx) {
        fl_shield = false;
        killHit(dx);
    }

    /** Fin de chute après la mort : une vie de moins, résurrection ou fin de partie. */
    public void killPlayer() {
        game.fx("hammer_fx_death_player", x, GAME_HEIGHT);
        lives--;
        if (lives >= 0) {
            resurrect();
            return;
        }
        // Plus de vies : avec le partage de vies, on en prend une à un autre joueur encore en jeu
        if (game.hasOption("lifesharing")) {
            for (Player p : game.players()) {
                if (p.uniqId != uniqId && p.lives > 0) {
                    p.lives--;
                    lives = 0;
                    resurrect();
                    return;
                }
            }
        }
        game.onGameOver();
        destroy();
    }

    @Override
    public void killHit(double dx) {
        if (fl_kill || fl_shield) return;
        fl_knock = false;
        game.sound("sound_player_death");
        double sign = dx == 0 || Double.isNaN(dx) ? game.random(2) * 2 - 1 : Math.signum(dx);
        if (x >= 0.85 * GAME_WIDTH) sign = -1;
        if (x <= 0.15 * GAME_WIDTH) sign = 1;
        playAnim(Anim.PLAYER_DIE);
        super.killHit(sign * (game.tmod <= 0.6 ? 40 : 20));
    }

    @Override
    public void resurrect() {
        game.registerMapEvent(EVENT_DEATH, null); // GameMode.onResurrect
        super.resurrect();
        moveTo(xCtr(world.data.playerX), yCtr(world.data.playerY));
        dx = 0;
        dy = 0;
        shield(Double.NaN);
        changeWeapon(WEAPON_B_CLASSIC);
        fl_knock = false;
        specialMan.clearTemp();
        specialMan.clearPerm();
        specialMan.clearRec();
        game.resetHurry(); // GameMode.onResurrect
        if (game.script != null) {
            game.script.onPlayerBirth();
            game.script.onPlayerDeath();
        }
        playAnim(Anim.PLAYER_RESURRECT);
        fl_stickyAnim = true;
        fl_lockControls = true;
        if (game.fl_nightmare) speedFactor = 1.3;
        if (boosted(game)) speedFactor = game.fl_nightmare ? 1.6 : 1.3;
        game.upgradePlayer(this); // BossRush.onResurrect
    }

    /**
     * Tornade (option « boost », cadeau de la quête des manettes) : Player.initPlayer / resurrect d'origine, sauf en
     * Time Attack solo (game._name != "$time" ; le Time Attack à deux, « $timeMulti », l'a bien).
     */
    static boolean boosted(Game g) {
        return g.hasOption("boost") && !(g.fl_timeAttack && g.set != null && "timeAttack".equals(g.set.name));
    }

    @Override
    public void playAnim(Anim a) {
        if (a.id == baseWalkAnim.id && speedFactor > 1) a = Anim.PLAYER_RUN;
        if (a.id == Anim.PLAYER_JUMP_DOWN.id && animId == Anim.PLAYER_AIRKICK.id) return;
        if (fl_knock && a.id != Anim.PLAYER_DIE.id && a.id != Anim.PLAYER_KNOCK_IN.id) return;
        if (animId == Anim.PLAYER_KICK.id && a.id == Anim.PLAYER_JUMP_DOWN.id) return;
        if (animId == Anim.PLAYER_CARROT.id) return;
        super.playAnim(a);
    }

    @Override
    public boolean needsPatch() {
        return true;
    }

    // ------------------------------------------------------------------ événements

    @Override
    public void onEndAnim(int id) {
        super.onEndAnim(id);
        if (id == Anim.PLAYER_KNOCK_OUT.id) {
            fl_lockControls = false;
            playAnim(baseStopAnim);
        }
        if (id == Anim.PLAYER_AIRKICK.id) {
            animId = -1;
            playAnim(Anim.PLAYER_JUMP_DOWN);
        }
        if (id == Anim.PLAYER_RESURRECT.id) {
            fl_lockControls = false;
            playAnim(baseStopAnim);
        }
        if (id == Anim.PLAYER_KICK.id && !fl_stable) {
            animId = -1;
            playAnim(Anim.PLAYER_JUMP_DOWN);
            return;
        }
        if (id == Anim.PLAYER_CARROT.id) playAnim(baseStopAnim);
        if (id == Anim.PLAYER_KICK.id || id == Anim.PLAYER_ATTACK.id || id == Anim.PLAYER_WAIT1.id
                || id == Anim.PLAYER_WAIT2.id || id == Anim.PLAYER_JUMP_LAND.id) {
            playAnim(baseStopAnim);
        }
    }

    @Override
    public void onDeathLine() {
        super.onDeathLine();
        if (fl_kill) {
            killPlayer();
        } else if (game.checkLevelClear()) {
            dy = 0;
            game.nextLevel();
        } else {
            y = LEVEL_HEIGHT * CASE_HEIGHT - CASE_HEIGHT - 1;
            dx = 0;
            forceKill(0);
        }
    }

    public void onWakeUp() {
        fl_knock = false;
        if (fl_stable) {
            dx = 0;
            playAnim(Anim.PLAYER_KNOCK_OUT);
        } else {
            fl_lockControls = false;
            playAnim(Anim.PLAYER_JUMP_DOWN);
        }
    }

    public void onNextLevel() {
        changeWeapon(WEAPON_B_CLASSIC);
        if (fl_shield) shieldTimer = 1;
        specialMan.clearTemp();
        specialMan.clearRec();
    }

    @Override
    public void scale(double n) {
        super.scale(n);
        xscale = dir * Math.abs(xscale);
    }

    /** Changement de case : champs d'armes (-1..-5) et de désarmement (Player.infix). */
    @Override
    public void infix() {
        super.infix();
        int id = world.getCase(cx, cy);
        if (id > FIELD_TELEPORT && id < 0) {
            if (currentWeapon != Math.abs(id)) {
                game.shine(x, y - CASE_HEIGHT * 0.5, 65);
                game.sound("sound_field");
            }
            changeWeapon(Math.abs(id));
        }
        if (id == FIELD_PEACE) {
            if (currentWeapon != Math.abs(id)) {
                game.shine(x, y - CASE_HEIGHT * 0.5, 65);
                game.sound("sound_field");
            }
            changeWeapon(WEAPON_NONE);
        }
        if (!fl_kill && !fl_destroy && game.script != null) game.script.onEnterCase(cx, cy);
        showTeleporters();
    }

    /**
     * Player.showTeleporters : éteint tous les téléporteurs, puis allume ceux dont le joueur est proche (moins de
     * TELEPORTER_DISTANCE = 4 cases du centre, à leur hauteur pour un vertical, au-dessus pour un horizontal) et leur
     * destination quand elle n'est pas tirée au hasard. Allumé : rayon et socles sur leur 2e état (SetManager.showField).
     */
    public void showTeleporters() {
        java.util.List<hammerfest.sim.World.Teleporter> tl = world.teleporterList;
        for (hammerfest.sim.World.Teleporter t : tl) t.on = false;
        double dist = CASE_WIDTH * 4;
        for (hammerfest.sim.World.Teleporter t : tl) {
            boolean close = t.dir == VERTICAL
                    ? Math.abs(t.centerX - x) <= dist && y >= t.startY - CASE_HEIGHT * 0.5 && y <= t.endY + CASE_HEIGHT * 0.5
                    : Math.abs(t.centerY - y) <= dist && x >= t.startX - CASE_WIDTH && x <= t.endX + CASE_WIDTH;
            if (!close) continue;
            t.on = true;
            hammerfest.sim.World.Teleporter next = world.fixedNextTeleporter(t); // getNextTeleporter sans fl_rand
            if (next != null) next.on = true;
        }
    }

    public void onStartLevel() {
        show();
        startX = x;
        fl_entering = true;
    }

    @Override
    public void onHitGround(double h) {
        if (specialMan.active(90) && !fl_knock && h >= CASE_HEIGHT * 2) knock(SECOND); // effet goldorak
        super.onHitGround(h);
        if (specialMan.active(39)) { // tête de pierre : secousse, ennemis assommés
            game.shake(10, 2);
            for (Entity b : game.getList(BAD_CLEAR)) ((Bad) b).knock(SECOND);
        }
        showTeleporters(); // Player.onHitGround
        if (h >= DUST_FALL_HEIGHT) game.dust(cx, cy + 1);
        game.fx("hammer_fx_fall", x, y);
        game.sound("sound_land");
    }

    @Override
    public void onHitWall() {
        if (fl_knock) {
            dx = -dx * 0.5;
            if (Math.abs(dx) >= 10 && world.getCase(cx, cy) <= 0) { // gros choc
                game.shake(SECOND * 0.7, 5);
                game.particlesDir(PARTICLE_STONE, x, y, 1, 3, dx);
                game.particlesDir(PARTICLE_CLASSIC_BOMB, x, y, 3, 5, dx);
            }
        } else {
            super.onHitWall();
        }
    }

    @Override
    public void onTeleport() {
        dx = 0;
        dy = 0;
    }

    @Override
    public void onPortal(int pid) {
        if (!game.usePortal(pid, this)) onPortalRefusal();
    }

    /** Portail fermé (niveau non terminé ou sans destination) : repoussé et sonné. */
    @Override
    public void onPortalRefusal() {
        game.particles(PARTICLE_PORTAL, x, y, 5, 5);
        x = oldX;
        y = oldY;
        dx = -dx;
        knock(SECOND * 0.5);
        game.shake(SECOND, 3);
        fl_stopStepping = true;
    }

    @Override
    public void onBump() {
        super.onBump();
        fl_knock = false;
        knock(SECOND * 0.7);
    }

    // ------------------------------------------------------------------ mises à jour

    @Override
    public void endUpdate() {
        super.endUpdate();
        xscale = dir * Math.abs(xscale);
    }

    /** Mise à jour avec les entrées du cycle (Player.update). */
    public void update(InputFrame input) {
        recentKicks.removeIf(k -> game.cycle - k[0] > 2);
        specialMan.main();
        if (fl_shield) {
            shieldTimer -= game.tmod;
            if (shieldTimer <= 0) unshield();
        }
        shieldX += (x - shieldX) * 0.75;
        shieldY += (y - 20 - shieldY) * 0.75;
        if (!fl_kill && !fl_lockControls && !fl_entering && visible) ctrl.update(input);
        if (fl_entering && cy >= 0) fl_entering = false;

        if (coolDown > 0) {
            coolDown -= game.tmod;
            if (coolDown <= 0) coolDown = 0;
        }

        if (fl_knock) {
            knockTimer -= game.tmod;
            if (knockTimer <= 0) {
                onWakeUp();
            } else {
                if (fl_stable && animId != Anim.PLAYER_KNOCK_IN.id) playAnim(Anim.PLAYER_KNOCK_IN);
                if (!fl_stable && animId != Anim.PLAYER_DIE.id) playAnim(Anim.PLAYER_DIE);
            }
        }

        if (fl_lockControls && fl_stable && animId == baseStopAnim.id) {
            fl_lockControls = false;
        }
        if (lockTimer > 0) {
            lockTimer -= game.tmod;
            fl_lockControls = lockTimer > 0;
        }

        super.update();
        updateCoords();

        if (dx != 0 || dy != 0) {
            edgeTimer = 0;
            waitTimer = 0;
        }

        if (!fl_kill) {
            if (!fl_stable && dy >= 0 && animId != Anim.PLAYER_JUMP_DOWN.id && !fl_lockControls) {
                playAnim(Anim.PLAYER_JUMP_DOWN);
            }
            if (animId == Anim.PLAYER_JUMP_DOWN.id && fl_stable) playAnim(Anim.PLAYER_JUMP_LAND);

            // Anim d'attente
            if (animId == Anim.PLAYER_STOP.id) {
                if (waitTimer <= 0) waitTimer = WAIT_TIMER;
                waitTimer -= game.tmod;
                if (waitTimer <= 0) playAnim(game.random(20) == 0 ? Anim.PLAYER_WAIT1 : Anim.PLAYER_WAIT2);
            }

            // Anim au bord du vide
            if (fl_stable && dx == 0 && animId == baseStopAnim.id) {
                int px = xRtc(x + dir * CASE_WIDTH * 0.3);
                int py = yRtc(y + CASE_HEIGHT);
                if (world.getCase(px, py) <= 0 && world.getCase(cx + dir, cy) == 0) {
                    if (edgeTimer <= 0) edgeTimer = EDGE_TIMER;
                    edgeTimer -= game.tmod;
                    if (edgeTimer <= 0) playAnim(Anim.PLAYER_EDGE);
                }
            }
        }
    }

    @Override
    public void update() {
        update(game.inputFor(pid));
        if (specialMan.active(100)) { // poupée Guu : pluie du nuage, le joueur reprend sa taille ; trop petit, il meurt
            game.particles(PARTICLE_RAIN, x + game.visualRandom(20) * (game.visualRandom(2) * 2 - 1), y - 60 - game.visualRandom(5), 1, 2);
            scale(Math.min(100, (scaleFactor + 0.002 * game.tmod) * 100));
            if (scaleFactor <= 0.60) {
                killHit(Double.NaN);
                specialMan.interrupt(100);
            }
        }
    }
}
