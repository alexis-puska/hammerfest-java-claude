package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.sim.entity.Entity;

import java.util.ArrayList;
import java.util.List;

import static hammerfest.sim.Const.*;

/**
 * Niveau en cours côté simulation : portage de {@code levels.SetManager} (getCase, forceCase,
 * getGround) et {@code levels.GameMechanics} (murs, drapeaux IA, triggers).
 */
public class World {

    public final LevelData data;
    /** Grille [x][y] après parseWalls (sols empilés convertis en murs). */
    final int[][] map = new int[LEVEL_WIDTH][LEVEL_HEIGHT];
    final int[][] flagMap = new int[LEVEL_WIDTH][LEVEL_HEIGHT];
    final int[][] fallMap = new int[LEVEL_WIDTH][LEVEL_HEIGHT];
    /** Entités présentes dans chaque case (Trigger.tAdd). */
    @SuppressWarnings("unchecked")
    public final List<Entity>[][] triggers = new List[LEVEL_WIDTH][LEVEL_HEIGHT];

    /** Masquage des dalles / bordures demandé par le script (e_hide). */
    public boolean hideTiles, hideBorders;
    /** Incrémenté à chaque modification de la grille (le rendu se met à jour). */
    public int version;

    /** Valeur brute de la case (rendu). */
    public int rawCase(int cx, int cy) {
        return inBound(cx, cy) ? map[cx][cy] : 0;
    }

    /** Copie de la grille courante pour le rendu, murs (2) rendus comme du sol. */
    public int[][] renderGrid() {
        int[][] g = new int[LEVEL_WIDTH][LEVEL_HEIGHT];
        for (int x = 0; x < LEVEL_WIDTH; x++)
            for (int y = 0; y < LEVEL_HEIGHT; y++) g[x][y] = map[x][y] == WALL ? GROUND : map[x][y];
        return g;
    }

    /** Portails du niveau, dans l'ordre de View.attachField : {cx, cy} de la première case ; index = pid. */
    public final List<int[]> portalList = new ArrayList<>();

    public World(LevelData data) {
        this.data = data;
        int[][] src = data.grid();
        for (int x = 0; x < LEVEL_WIDTH; x++) {
            System.arraycopy(src[x], 0, map[x], 0, LEVEL_HEIGHT);
            for (int y = 0; y < LEVEL_HEIGHT; y++) triggers[x][y] = new ArrayList<>();
        }
        parseWalls();
        parseIA();
        for (hammerfest.sim.level.TileLayout.Field f : hammerfest.sim.level.TileLayout.fields(map, LEVEL_WIDTH, LEVEL_HEIGHT)) {
            if (f.id == FIELD_PORTAL) portalList.add(new int[]{f.x, f.y});
            if (f.id == FIELD_TELEPORT) teleporterList.add(new Teleporter(f.x, f.y, f.length, f.vertical ? VERTICAL : HORIZONTAL));
        }
    }

    /** Téléporteur (levels.TeleporterData) : segment de cases FIELD_TELEPORT. */
    public static final class Teleporter {
        public final int cx, cy, length, dir;
        public final double centerX, centerY;

        Teleporter(int cx, int cy, int length, int dir) {
            this.cx = cx;
            this.cy = cy;
            this.length = length;
            this.dir = dir;
            double centerX = cx * CASE_WIDTH + CASE_WIDTH / 2.0;
            double centerY = cy * CASE_HEIGHT + CASE_HEIGHT;
            if (dir == HORIZONTAL) centerX += length / 2.0 * CASE_WIDTH;
            else centerY += length / 2.0 * CASE_HEIGHT;
            this.centerX = centerX;
            this.centerY = centerY;
        }

        boolean contains(int x, int y) {
            return dir == HORIZONTAL ? y == cy && x >= cx && x < cx + length : x == cx && y >= cy && y < cy + length;
        }
    }

    /** Téléporteurs du niveau, dans l'ordre de View.attachField. */
    public final List<Teleporter> teleporterList = new ArrayList<>();

    /** Téléporteur de la case, ou null (ou s'il s'agit de celui par lequel l'entité vient d'arriver). */
    public Teleporter getTeleporter(Teleporter last, int cx, int cy) {
        if (getCase(cx, cy) != FIELD_TELEPORT) return null;
        for (Teleporter t : teleporterList) {
            if (t.contains(cx, cy)) return t == last ? null : t;
        }
        return null;
    }

    /**
     * Téléporteur d'arrivée (GameMechanics.getNextTeleporter) : en face (même alignement et longueur),
     * sinon un de même direction et longueur, sinon n'importe lequel. {@code [0]} : arrivée, {@code [1]} : tirage au hasard.
     */
    public Object[] getNextTeleporter(Teleporter start, hammerfest.sim.Game game) {
        for (Teleporter t : teleporterList) {
            if (t == start || t.dir != start.dir || t.length != start.length) continue;
            if (start.dir == HORIZONTAL ? t.cx == start.cx : t.cy == start.cy) return new Object[]{t, false};
        }
        List<Teleporter> same = new ArrayList<>();
        for (Teleporter t : teleporterList) {
            if (t != start && t.dir == start.dir && t.length == start.length) same.add(t);
        }
        if (!same.isEmpty()) return new Object[]{same.get(game.random(same.size())), same.size() != 1};
        List<Teleporter> any = new ArrayList<>();
        for (Teleporter t : teleporterList) if (t != start) any.add(t);
        if (any.isEmpty()) return null;
        return new Object[]{any.get(game.random(any.size())), true};
    }

    /** Index du portail dont la case (cx, cy) fait partie, -1 si aucun (Physics.infix). */
    public int portalAt(int cx, int cy) {
        int px = cx, py = cy;
        while (getCase(px - 1, py) == FIELD_PORTAL) px--;
        while (getCase(px, py - 1) == FIELD_PORTAL) py--;
        for (int i = 0; i < portalList.size(); i++) {
            if (portalList.get(i)[0] == px && portalList.get(i)[1] == py) return i;
        }
        return -1;
    }

    /** GameMechanics.parseWalls : une case pleine sous une case pleine devient un mur. */
    /** Grille de l'hôte (RenderFrame.grid, murs rendus en sols) : remplace les cases, murs recalculés. */
    void loadRenderGrid(int[] grid) {
        for (int x = 0; x < LEVEL_WIDTH; x++)
            for (int y = 0; y < LEVEL_HEIGHT; y++) map[x][y] = grid[x * LEVEL_HEIGHT + y];
        parseWalls();
        version++;
    }

    private void parseWalls() {
        for (int cy = 0; cy < LEVEL_HEIGHT; cy++) {
            for (int cx = 0; cx < LEVEL_WIDTH; cx++) {
                if (map[cx][cy] > 0 && cy > 0 && map[cx][cy - 1] > 0) map[cx][cy] = WALL;
            }
        }
    }

    public boolean inBound(int cx, int cy) {
        return cx >= 0 && cx < LEVEL_WIDTH && cy >= 0 && cy < LEVEL_HEIGHT;
    }

    /** SetManager.getCase : la rangée du haut n'agit pas comme un sol ; hors écran bas/côtés = OUT_WALL. */
    public int getCase(int cx, int cy) {
        if (inBound(cx, cy)) {
            if (cy == 0) return map[cx][0] > 0 ? WALL : 0;
            return map[cx][cy];
        }
        if (cy < 0) {
            return cx >= 0 && cx < LEVEL_WIDTH && map[cx][0] > 0 ? WALL : 0;
        }
        return OUT_WALL;
    }

    public void forceCase(int cx, int cy, int t) {
        if (!inBound(cx, cy)) return;
        if (t <= 0 && getCase(cx, cy) > 0 && getCase(cx, cy + 1) == WALL) forceCase(cx, cy + 1, GROUND);
        map[cx][cy] = t;
        if (t == GROUND) flagMap[cx][cy] |= IA_TILE;
        else flagMap[cx][cy] &= ~IA_TILE;
        version++;
    }

    public boolean checkFlag(int x, int y, int flag) {
        return inBound(x, y) && (flagMap[x][y] & flag) > 0;
    }

    /** SetManager.getGround : premier sol sous la case (en rebouclant par le haut). */
    public int[] getGround(int cx, int cy) {
        for (int n = 0, ty = cy; n <= LEVEL_HEIGHT; n++, ty++) {
            if (ty > 0 && getCase(cx, ty) == GROUND) return new int[]{cx, ty - 1};
            if (ty >= LEVEL_HEIGHT) ty = 0;
        }
        return new int[]{cx, cy};
    }

    // ------------------------------------------------------------------ IA (GameMechanics.parseCurrentIA)

    private void parseIA() {
        for (int x = 0; x < LEVEL_WIDTH; x++) {
            for (int y = 0; y < LEVEL_HEIGHT; y++) fallMap[x][y] = -1;
        }
        for (int cy = 0; cy < LEVEL_HEIGHT; cy++) {
            for (int cx = 0; cx < LEVEL_WIDTH; cx++) flagMap[cx][cy] = computeFlags(cx, cy);
        }
    }

    private int computeFlags(int cx, int cy) {
        int flags = 0;
        if (getCase(cx, cy + 1) == GROUND) {
            flags |= IA_TILE_TOP;
            if (getCase(cx, cy - IA_VJUMP) == GROUND && getCase(cx, cy - IA_VJUMP - 1) <= 0) flags |= IA_JUMP_UP;
        }
        if (getCase(cx, cy) == GROUND && getCase(cx, cy - 1) <= 0) flags |= IA_TILE;

        int fallHeight = checkSecureFall(cx, cy);
        fallMap[cx][cy] = fallHeight;
        if (fallHeight >= 0) flags |= IA_ALLOW_FALL;

        if (getCase(cx, cy) == 0 && getCase(cx, cy + 1) == GROUND && checkSecureFall(cx, cy + 2) >= 0) {
            flags |= IA_JUMP_DOWN;
        }
        if (getCase(cx, cy + 1) == GROUND && (getCase(cx - 1, cy + 1) <= 0 || getCase(cx + 1, cy + 1) <= 0)) {
            flags |= IA_BORDER;
        }
        if (getCase(cx, cy + 1) <= 0 && (getCase(cx - 1, cy + 1) == GROUND || getCase(cx + 1, cy + 1) == GROUND)) {
            flags |= IA_FALL_SPOT;
        }
        if ((flags & IA_BORDER) > 0 && getCase(cx - 1, cy + 1) != GROUND && getCase(cx + 1, cy + 1) != GROUND) {
            flags |= IA_SMALL_SPOT;
        }
        if ((flags & IA_TILE_TOP) > 0) {
            int maxHeight = 1;
            for (int d = 1; d <= 5; d++) {
                if (getCase(cx, cy - d) <= 0) maxHeight++;
                else break;
            }
            if (getCase(cx - 1, cy) > 0) {
                Integer h = wallHeight(cx - 1, cy, IA_CLIMB);
                if (h != null && h < maxHeight && cy - h >= 0) flags |= IA_CLIMB_LEFT;
            }
            if (getCase(cx + 1, cy) > 0) {
                Integer h = wallHeight(cx + 1, cy, IA_CLIMB);
                if (h != null && h < maxHeight && cy - h >= 0) flags |= IA_CLIMB_RIGHT;
            }
        }
        if ((flags & IA_FALL_SPOT) > 0) {
            int maxHeight = 1;
            for (int d = 1; d <= 5; d++) {
                if (getCase(cx, cy - d) <= 0) maxHeight++;
                else {
                    maxHeight += 2;
                    break;
                }
            }
            if (getCase(cx + 1, cy + 1) == GROUND) {
                Integer h = stepHeight(cx, cy, IA_CLIMB);
                if (h != null && h < maxHeight && checkFlag(cx, cy - h, IA_BORDER) && checkFlag(cx + 1, cy - h, IA_FALL_SPOT)) {
                    flags |= IA_CLIMB_LEFT;
                }
            }
            if (getCase(cx - 1, cy + 1) == GROUND) {
                Integer h = stepHeight(cx, cy, IA_CLIMB);
                if (h != null && h < maxHeight && checkFlag(cx, cy - h, IA_BORDER) && checkFlag(cx - 1, cy - h, IA_FALL_SPOT)) {
                    flags |= IA_CLIMB_RIGHT;
                }
            }
            if (getCase(cx + 1, cy + 1) == GROUND && getCase(cx - IA_HJUMP, cy + 1) == GROUND && getCase(cx - 1, cy) <= 0) {
                flags |= IA_JUMP_LEFT;
            }
            if (getCase(cx - 1, cy + 1) == GROUND && getCase(cx + IA_HJUMP, cy + 1) == GROUND && getCase(cx + 1, cy) <= 0) {
                flags |= IA_JUMP_RIGHT;
            }
        }
        return flags;
    }

    private int checkSecureFall(int cx, int cy) {
        if (!inBound(cx, cy)) {
            if (cx < 0 || cx >= LEVEL_WIDTH) return -1;
        } else {
            if (map[cx][cy] == GROUND || map[cx][cy] == WALL) return -1;
            if (cy > 0 && (flagMap[cx][cy - 1] & IA_ALLOW_FALL) > 0) return fallMap[cx][cy - 1] - 1;
        }
        int h = 0;
        for (int i = cy + 1; i < LEVEL_HEIGHT; i++, h++) {
            if (i >= 0 && map[cx][i] == GROUND) return h;
        }
        return -1;
    }

    /** Hauteur de chute sûre depuis la case (-1 si vide), GameMechanics.fallMap. */
    public int fallHeight(int cx, int cy) {
        return inBound(cx, cy) ? fallMap[cx][cy] : -1;
    }

    /** GameMechanics.getWallHeight : null si la hauteur atteint {@code max}. */
    public Integer wallHeight(int cx, int cy, int max) {
        int h = 0;
        while (getCase(cx, cy - h) > 0 && h < max) h++;
        return h >= max ? null : h;
    }

    /** GameMechanics.getStepHeight : hauteur d'une marche dans le vide, null si trop haute. */
    public Integer stepHeight(int cx, int cy, int max) {
        int h = 0;
        while (getCase(cx, cy - h) <= 0 && h < max) h++;
        h++;
        return h >= max ? null : h;
    }
}
