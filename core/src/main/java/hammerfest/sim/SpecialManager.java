package hammerfest.sim;

import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.ScoreItem;
import hammerfest.sim.entity.SpecialItem;

import java.util.ArrayList;
import java.util.List;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code SpecialManager.mt} : effets des 118 objets spéciaux (id 0..117), effets
 * permanents / temporaires / globaux, événements récurrents.
 *
 */
public class SpecialManager {

    final Game game;
    final Player player;
    /** actives[id] : effet en cours (consulté par Player, Bad, PlayerController…). */
    public final boolean[] actives = new boolean[130];
    private final List<Integer> permList = new ArrayList<>();
    /** {id, cycle de fin}. */
    private final List<int[]> tempList = new ArrayList<>();
    /** {timer, baseTimer, repeat (0/1)} + action. */
    private final List<Object[]> recurring = new ArrayList<>();

    public SpecialManager(Game game, Player player) {
        this.game = game;
        this.player = player;
    }

    public boolean active(int id) {
        return id >= 0 && id < actives.length && actives[id];
    }

    void permanent(int id) {
        if (!actives[id]) {
            actives[id] = true;
            permList.add(id);
        }
    }

    void temporary(int id, double duration) {
        if (Double.isNaN(duration)) duration = 99999;
        if (!actives[id]) {
            actives[id] = true;
            tempList.add(new int[]{id, (int) (game.cycle + duration)});
        }
    }

    void global(int id) {
        game.globalActives[id] = true;
    }

    public void clearTemp() {
        while (!tempList.isEmpty()) {
            interrupt(tempList.get(0)[0]);
            tempList.remove(0);
        }
    }

    public void clearPerm() {
        while (!permList.isEmpty()) {
            interrupt(permList.get(0));
            permList.remove(0);
        }
    }

    public void clearRec() {
        recurring.clear();
    }

    void registerRecurring(Runnable func, double t, boolean repeat) {
        recurring.add(new Object[]{new double[]{t, t}, repeat, func});
    }

    /** Un diamant de conversion au-dessus de chaque dalle, apparitions échelonnées (levelConversion). */
    void levelConversion(int id, int sid) {
        int n = 0;
        for (int y = 0; y < LEVEL_HEIGHT; y++) {
            for (int x = 0; x < LEVEL_WIDTH; x++) {
                if (game.world.checkFlag(x, y, IA_TILE_TOP)) {
                    int t = n < 4 ? 1 : n * 2;
                    game.scheduleItem(t, true, id, sid, x, y, true);
                    n++;
                }
            }
        }
        game.perfectItemCpt = n;
    }

    void getZodiac() {
        for (Entity b : game.getList(BAD_CLEAR)) ScoreItem.attach(game, b.x, b.y - CASE_HEIGHT * 2, 169, 0);
    }

    void onPerfect() {
        interrupt(81);
        interrupt(96);
        interrupt(97);
        interrupt(98);
        int bonus = 50000;
        game.fx("hammer_fx_perfect", GAME_WIDTH * 0.5, GAME_HEIGHT * 0.5);
        List<Player> pl = game.players();
        for (Player p : pl) p.getScoreHidden(bonus / pl.size());
    }

    public void onPickPerfectItem() {
        game.perfectItemCpt--;
        if (game.perfectItemCpt <= 0) onPerfect();
    }

    /** Mot EXTEND complété : vie en plus, ennemis détruits, bonus si lettres dans l'ordre. */
    public void executeExtend(boolean perfect) {
        game.registerMapEvent(EVENT_EXTEND, null);
        game.fx("extendSequence", GAME_WIDTH / 2.0, GAME_HEIGHT / 2.0);
        game.destroyList(BAD);
        game.destroyList(BAD_BOMB);
        game.destroyList(SHOOT);
        player.lives++;
        if (perfect) {
            game.fx("hammer_fx_perfect", GAME_WIDTH * 0.5, GAME_HEIGHT * 0.2);
            player.getScoreHidden(150000);
        }
    }

    /** Parapluies (rouge +1, bleu +2, vert +3) : saut arrêté sur un boss ou avant un niveau vide (fin de dimension). */
    void warpZone(int w) {
        int arrival = game.warpArrival(w);
        if (arrival == game.levelId) { // téléportation impossible
            game.alert("static.34", "");
            return;
        }
        game.forcedGoto(arrival);
    }

    void scatter(Entity from, int id, int count, java.util.function.IntSupplier subId) {
        for (int i = 0; i < count; i++) {
            ScoreItem s = ScoreItem.attach(game, from.x, from.y, id, subId.getAsInt());
            s.moveFrom(from, 8);
        }
    }

    /** Toutes les crêpes deviennent des cristaux (taille sid). */
    void badsToCrystals(int sid, boolean twice) {
        for (Entity b : new ArrayList<>(game.getList(BAD_CLEAR))) {
            b.destroy();
            if (twice) {
                ScoreItem.attach(game, b.x - CASE_WIDTH, b.y - CASE_HEIGHT, 0, sid);
                ScoreItem.attach(game, b.x + CASE_WIDTH, b.y - CASE_HEIGHT, 0, sid);
            } else {
                ScoreItem.attach(game, b.x, b.y - CASE_HEIGHT, 0, sid);
            }
        }
    }

    /** Effet de l'objet spécial ramassé (SpecialManager.execute). */
    public void execute(SpecialItem item) {
        int id = item.id;
        int subId = item.subId;
        switch (id) {
            case 0: // lettre EXTEND
                if (actives[27]) player.getScore(item, 25);
                player.getScoreHidden(5);
                player.getExtend(subId);
                break;
            case 1: player.shield(SECOND * 10); break;
            case 2: player.shield(SECOND * 60); break;
            case 4:
                if (!actives[5]) {
                    player.maxBombs = player.initialMaxBombs + 1;
                    permanent(id);
                }
                break;
            case 5:
                player.maxBombs = player.initialMaxBombs + 4;
                permanent(id);
                break;
            case 6:
                for (Entity b : game.getList(BAD_CLEAR)) ((Bad) b).freeze(FREEZE_DURATION * 2);
                break;
            case 7:
                player.speedFactor = 1.5;
                permanent(id);
                break;
            case 11: warpZone(1); break;
            case 12: warpZone(2); break;
            case 13: case 27: case 66: case 70: case 73: case 92: case 115:
                permanent(id);
                break;
            case 26: case 68: // ampoule / bougie : moins d'obscurité
                permanent(id);
                game.updateDarkness();
                break;
            case 30:
                game.flipX = true;
                temporary(id, SECOND * 30);
                break;
            case 31:
                game.flipY = true;
                temporary(id, SECOND * 30);
                break;
            case 14: case 15: case 16: case 17: // champignons : un seul actif
                permanent(id);
                for (int other = 14; other <= 17; other++) if (other != id) interrupt(other);
                break;
            case 18:
                permanent(id);
                player.fallFactor = 0.55;
                break;
            case 19:
                game.attachBg(BG_ORANGE, -1, SECOND * 3);
                hammerfest.sim.entity.Supa.Smoke.attach(game);
                for (Entity b : new ArrayList<>(game.getList(BAD_CLEAR))) ((Bad) b).forceKill(Double.NaN);
                break;
            case 3: hammerfest.sim.entity.Supa.Ball.attach(game); break;
            case 8:
                hammerfest.sim.entity.Supa.Bubble.attach(game);
                hammerfest.sim.entity.Supa.Bubble.attach(game);
                break;
            case 9: hammerfest.sim.entity.Supa.Tons.attach(game); break;
            case 24: hammerfest.sim.entity.Supa.IceMeteor.attach(game); break;
            case 38: hammerfest.sim.entity.Supa.Arrow.attach(game).setLifeTimer(SUPA_DURATION); break;
            case 23: // perles de glace lancées sur chaque ennemi
                for (Entity b : new ArrayList<>(game.getList(BAD_CLEAR))) {
                    hammerfest.sim.entity.Shoot s = hammerfest.sim.entity.PlayerShots.create(game, WEAPON_S_ICE, player.x, player.y - CASE_WIDTH);
                    s.moveToTarget(b, s.shootSpeed);
                    s.fl_borderBounce = true;
                    s.setLifeTimer(SECOND * 3 + game.random(400) / 10.0);
                    s.yOffset = 0; // lancées depuis la tête du joueur : pas de décalage (s._yOffset = 0)
                }
                break;
            case 25: // boules de feu : 4 descendent, 4 montent
                for (int i = 0; i < 4; i++) {
                    hammerfest.sim.entity.PlayerShots.create(game, WEAPON_S_FIRE, GAME_WIDTH * 0.125 + GAME_WIDTH * 0.25 * i, 10)
                            .moveDown(8);
                    hammerfest.sim.entity.PlayerShots.create(game, WEAPON_S_FIRE, GAME_WIDTH * 0.25 + GAME_WIDTH * 0.25 * i, GAME_HEIGHT - 10)
                            .moveUp(8);
                }
                break;
            case 71: // deux boules de feu de part et d'autre
                hammerfest.sim.entity.PlayerShots.create(game, WEAPON_S_FIRE, item.x, item.y).moveLeft(8);
                hammerfest.sim.entity.PlayerShots.create(game, WEAPON_S_FIRE, item.x, item.y).moveRight(8);
                break;
            case 20: scatter(item, 0, 5, () -> game.random(4)); break;
            case 21: {
                game.attachBg(BG_SINGER, -1, SECOND * 3);
                Entity b = game.getOne(BAD_CLEAR);
                if (b != null) {
                    game.fx("hammer_fx_pop", b.x, b.y - CASE_HEIGHT);
                    ((Bad) b).forceKill(Double.NaN);
                }
                break;
            }
            case 22:
                interrupt(7);
                player.speedFactor = 0.6;
                temporary(id, SECOND * 40);
                break;
            case 28:
                game.attachBg(BG_STAR, -1, SECOND * 9);
                for (Entity b : game.getList(BAD_CLEAR)) ((Bad) b).knock(SECOND * 10);
                break;
            case 29:
                player.changeWeapon(WEAPON_S_FIRE);
                temporary(id, WEAPON_DURATION);
                break;
            case 32: badsToCrystals(0, false); break;
            case 33: badsToCrystals(2, false); break;
            case 34: badsToCrystals(5, false); break;
            case 35: badsToCrystals(6, true); break;
            case 36: case 103: case 104: case 105:
                player.lives++;
                game.shine(item.x, item.y - CASE_HEIGHT * 0.5);
                if (id >= 103) game.removeRandomItem(id);
                break;
            case 37:
                player.changeWeapon(WEAPON_S_ICE);
                temporary(id, WEAPON_DURATION);
                break;
            case 39:
                player.fallFactor = 1.6;
                temporary(id, Double.NaN);
                break;
            case 64: // 5 lettres EXTEND au hasard
                for (int i = 0; i < 5; i++) {
                    int[] pt = game.world.getGround(game.random(LEVEL_WIDTH), game.random(LEVEL_HEIGHT));
                    SpecialItem.attach(game, pt[0] * CASE_WIDTH, pt[1] * CASE_HEIGHT, 0, game.random(7));
                }
                break;
            case 65:
                for (Entity b : new ArrayList<>(game.getList(BAD))) {
                    player.getScore(b, (b.types & BAD_CLEAR) > 0 ? 2500 : 600);
                }
                break;
            case 67:
                player.changeWeapon(WEAPON_S_ARROW);
                temporary(id, WEAPON_DURATION);
                break;
            case 69: case 80: { // tortue / escargot : ennemis ralentis
                global(id);
                temporary(id, SECOND * 30);
                double f = id == 69 ? 0.6 : 0.3;
                for (Entity b : game.getList(BAD_CLEAR)) {
                    ((Bad) b).updateSpeed();
                    ((Bad) b).animFactor *= f;
                }
                break;
            }
            case 72: {
                game.destroyList(ITEM);
                game.destroyList(BAD_BOMB);
                int n = 1;
                for (Entity b : new ArrayList<>(game.getList(BAD_CLEAR))) {
                    ScoreItem.attach(game, b.x, b.y, 0, n++);
                    b.destroy();
                }
                break;
            }
            case 74: case 75: case 76:
                for (int i = 0; i < 7; i++) {
                    ScoreItem it = ScoreItem.attach(game, game.random(GAME_WIDTH), GAME_HEIGHT, id - 71, -1);
                    it.moveToAng(-20 - game.random(160), game.random(15) + 10);
                }
                break;
            case 77: case 78: case 79: {
                int sid = id == 77 ? 0 : id == 78 ? 2 : 3;
                game.endLevelStack.add(() -> {
                    for (int i = 0; i < 5; i++) {
                        ScoreItem.attach(game, game.random(GAME_WIDTH), -30 - game.random(50), 0, sid);
                    }
                });
                break;
            }
            case 81: case 96: case 97: case 98: // conversion du niveau en diamants
                game.destroyList(BAD);
                game.destroyList(ITEM);
                game.destroyList(BAD_BOMB);
                levelConversion(CONVERT_DIAMANT, id == 81 ? 0 : id - 95);
                temporary(id, SECOND * 19);
                break;
            case 82: onStrike(); break;
            case 83:
                game.attachBg(BG_PYRAMID, -1, 9999);
                temporary(id, Double.NaN);
                registerRecurring(this::onStrike, SECOND, true);
                onStrike();
                break;
            case 84: // talisman pluie de feu
                game.attachBg(BG_STORM, -1, 9999);
                temporary(id, Double.NaN);
                registerRecurring(this::onFireRain, SECOND * 0.8, true);
                onFireRain();
                break;
            case 85: // marteau
                hammerfest.sim.entity.shoot.Hammer.attach(game, player);
                temporary(id, Double.NaN);
                break;
            case 86:
                temporary(id, SECOND * 60);
                game.attachBg(BG_GHOSTS, -1, SECOND * 57);
                break;
            case 87: {
                Entity bad = game.getOne(BAD_CLEAR);
                if (bad != null) {
                    if (game.countList(BAD_CLEAR) == 1) ScoreItem.attach(game, bad.x, bad.y, DIAMANT, -1);
                    else SpecialItem.attach(game, bad.x, bad.y, id, subId);
                    game.shine(bad.x, bad.y - CASE_HEIGHT * 0.5);
                    bad.destroy();
                }
                break;
            }
            case 88:
                game.shine(player.x, player.y);
                player.scale(50);
                temporary(id, SECOND * 30);
                break;
            case 89: { // un ennemi devient un tzongre (50 000 points à attraper)
                Entity e = game.getOne(BAD_CLEAR);
                if (e != null) {
                    hammerfest.sim.entity.bad.Fruits.attach(game, 15, e.x - CASE_WIDTH * 0.5, e.y - CASE_HEIGHT * 2);
                    e.destroy();
                }
                break;
            }
            case 90: temporary(id, SECOND * 40); break;
            case 91:
                game.shine(player.x, player.y);
                temporary(id, SECOND * 15);
                break;
            case 93: {
                game.destroyList(BAD);
                game.destroyList(ITEM);
                game.destroyList(BAD_BOMB);
                int n = 6;
                for (int guard = 0; n > 0 && guard < 1000; guard++) {
                    int x = game.random(GAME_WIDTH), y = game.random(GAME_HEIGHT);
                    if (player.distance(x, y) >= 100) {
                        SpecialItem e = SpecialItem.attach(game, x, y, 101, -1);
                        if (e != null) e.lifeTimer = Double.NaN;
                        n--;
                    }
                }
                break;
            }
            case 94:
                global(id);
                permanent(id);
                break;
            case 95: permanent(id); break;
            case 99:
                registerRecurring(() -> player.getScore(player, 250), SECOND * 2, true);
                player.fl_chourou = true;
                temporary(id, Double.NaN);
                break;
            case 100:
                temporary(id, SECOND * 30);
                game.attachBg(BG_GUU, -1, SECOND * 30);
                break;
            case 101:
                if (game.random(2) == 0) {
                    player.getScore(item, 5000);
                } else {
                    hammerfest.sim.entity.PoireBomb b = hammerfest.sim.entity.PoireBomb.attach(game, item.x, item.y);
                    b.moveUp(10);
                }
                break;
            case 102: // carotte
                player.getScore(item, 4 * 25000);
                player.fl_carot = true;
                break;
            case 106: scatter(item, 1047, 5, () -> -1); break;
            case 107: scatter(item, 0, 5, () -> 0); break;
            case 108: warpZone(3); break;
            case 109:
                game.shine(item.x, item.y - CASE_HEIGHT * 0.5);
                game.removeRandomItem(id);
                break;
            case 110: case 111: game.shine(item.x, item.y - CASE_HEIGHT * 0.5); break; // flocons 2 et 3
            case 112: player.head = 4; break;
            case 113:
                player.getScore(item, 75000);
                player.head = 6;
                break;
            case 114: temporary(id, Double.NaN); break;
            case 116:
                player.lives++;
                player.getScore(item, 70000);
                break;
            case 117:
                game.pickUp(5000 + 12); // clé de Gordon
                player.getScore(item, 10000);
                break;
            default:
                // 3, 8, 9, 10, 23, 24, 25, 30, 31, 38, 40-63 (zodiaques), 71, 89
                if (id >= 40 && id <= 51) game.attachBg(BG_CONSTEL, id - 40, SECOND * 4); // signes : constellation
                if (id >= 40 && id <= 63) getZodiac();
                break;
        }
    }

    /** Fin d'un effet (SpecialManager.interrupt). */
    public void interrupt(int id) {
        if (!active(id)) return;
        actives[id] = false;
        game.clearBg();
        switch (id) {
            case 4: case 5: player.maxBombs = player.initialMaxBombs; break;
            case 7: case 22: player.speedFactor = 1.0; break;
            case 18: case 39: player.fallFactor = 1.1; break;
            case 29: if (player.currentWeapon == WEAPON_S_FIRE) player.changeWeapon(player.lastBomb); break;
            case 37: if (player.currentWeapon == WEAPON_S_ICE) player.changeWeapon(player.lastBomb); break;
            case 67: if (player.currentWeapon == WEAPON_S_ARROW) player.changeWeapon(player.lastBomb); break;
            case 69: case 80: {
                game.globalActives[id] = false;
                double f = id == 69 ? 1 / 0.6 : 1 / 0.3;
                for (Entity b : game.getList(BAD_CLEAR)) {
                    ((Bad) b).updateSpeed();
                    ((Bad) b).animFactor *= f;
                }
                break;
            }
            case 81: case 96: case 97: case 98: game.destroyList(PERFECT_ITEM); break;
            case 30: game.flipX = false; break;
            case 31: game.flipY = false; break;
            case 88: player.scale(100); break;
            case 100: // poupée Guu : le nuage éclate
                game.fx("hammer_fx_pop", player.x, player.y - 80);
                player.scale(100);
                break;
            case 94: game.globalActives[id] = false; break;
            case 84: clearRec(); break;
            case 99: player.fl_chourou = false; clearRec(); break;
            default: break;
        }
    }

    /** Une goutte de la pluie de feu tombe d'un point au hasard en haut de l'écran. */
    void onFireRain() {
        double x = game.random(GAME_WIDTH) + 50;
        hammerfest.sim.entity.shoot.FireRain s = hammerfest.sim.entity.shoot.FireRain.attach(game, x, -game.random(50));
        s.moveToAng(95 + game.random(30), s.shootSpeed);
    }

    /** Foudroie le premier ennemi vivant. */
    void onStrike() {
        for (Entity e : game.getList(BAD_CLEAR)) {
            if (!e.fl_kill) {
                int dir = game.random(2) * 2 - 1; // éclair et éjection du même côté
                game.fx("hammer_fx_strike", GAME_WIDTH / 2.0, e.y - CASE_HEIGHT * 0.5, 0, 100 * dir, game.visualRandom(50) + 50, 100);
                game.shine(e.x, e.y);
                ((Bad) e).forceKill(dir * (game.random(10) + 15));
                return;
            }
        }
    }

    /** Minuteries des effets temporaires et récurrents (SpecialManager.main). */
    public void main() {
        for (int n = 0; n < recurring.size(); n++) {
            Object[] r = recurring.get(n);
            double[] t = (double[]) r[0];
            t[0] -= game.tmod;
            if (t[0] <= 0) {
                ((Runnable) r[2]).run();
                if ((Boolean) r[1]) {
                    t[0] = t[1] + t[0];
                } else {
                    recurring.remove(n--);
                }
            }
        }
        for (int i = 0; i < tempList.size(); i++) {
            if (game.cycle >= tempList.get(i)[1]) {
                interrupt(tempList.get(i)[0]);
                tempList.remove(i--);
            }
        }
    }
}
