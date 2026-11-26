package hammerfest.net;

import hammerfest.sim.Const;
import hammerfest.sim.Game;
import hammerfest.sim.ScriptEngine;
import hammerfest.sim.entity.Animator;
import hammerfest.sim.entity.Bad;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Physics;
import hammerfest.sim.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Instantané de tout ce qu'il faut dessiner (« liste de dessin ») : construit par l'hôte depuis la simulation
 * ({@link #capture}), dessiné par l'écran de jeu. En solo il est pris localement ; en ligne l'hôte l'envoie
 * au client, qui ne fait que l'afficher (le client ne contient aucune règle du jeu).
 *
 * <p>Données simples uniquement (tableaux parallèles, chaînes, nombres) pour la sérialisation Kryo.</p>
 */
public class RenderFrame {

    // ------------------------------------------------------------------ partie et niveau
    public int cycle, levelCycle;
    public String setName;
    public int levelId, currentDim, mainLevelId, fakeLevelId = -1;
    public boolean mainWorld, mirror;
    /** Version de la grille ; {@link #grid} n'est rempli que si elle a changé (sinon null). */
    public int gridVersion;
    public int[] grid;
    public boolean hideTiles, hideBorders, flipX, flipY, aqua;
    public int specialBgId = -1, specialBgSub = -1;
    public float darkness;
    /** Flèche « niveau suivant » affichée (FxManager.attachExit). */
    public boolean exitArrow;
    /** Défilement vers le niveau suivant (Game.scrollCpt) : angle 0 à π/2, -1 sans défilement. */
    public float scroll = -1;
    /** Pointeur du tutoriel : x, y, rotation ; null = aucun. */
    public float[] pointer;
    /** Objet dont le nom est affiché en haut au centre (Game.itemNameId), -1 = aucun. */
    public int itemName = -1;
    /** Téléporteurs allumés (World.Teleporter.on) : case de départ cx, cy de chacun ; null = aucun. */
    public int[] teleportersOn;
    /** Halos de lumière : x, y, rayon (coordonnées du jeu, sans le décalage de 10 px). */
    public float[] holes;
    public float shakeTimer, shakeTotal, shakePower;
    public int music;
    public boolean multi, soccer, timeAttack, bossRush, tutorial, gameOver, victory;

    // ------------------------------------------------------------------ entités, dans l'ordre de dessin
    /** Bit de {@link #extras} d'une fraise qui tient la balle (couche « balle » de son sprite affichée). */
    public static final int EXTRA_BALL = 1 << 26;
    /** Bits de {@link #extras} d'une framboise en tas : morceaux o1 / o2 révélés (couches « o1 », « o2 »). */
    public static final int EXTRA_O1 = 1 << 27, EXTRA_O2 = 1 << 28;
    /** Bit de {@link #extras} d'un joueur qui porte la carotte d'Igor (Player.fl_carot : variante {@code <v>-carot}). */
    public static final int EXTRA_CAROT = 1 << 29;
    /** Nombre d'entités dessinées derrière les joueurs, en tête de liste (Tuberculoz vaincu : DP_SPRITE_BACK_LAYER). */
    public int backEntities;
    /** Nombre de joueurs dans la liste des entités, juste après les {@link #backEntities} (les joueurs visibles). */
    public int playerEntities;
    /**
     * Indice de la première entité de la couche DP_BOMBS (bombes puis supers) : les blocs de glace et icônes ninja
     * (Bad.freeze, DP_BADS) sont dessinés juste avant, au-dessus des ennemis mais sous les bombes.
     */
    public int bombEntities;
    public int[] ids;
    public String[] links;
    public short[] anims, subs;
    public float[] xs, ys, xscales, scales, rotations, alphas;
    /**
     * Joueurs : costume (bits 0-7), tête (bits 9-15), arme + 1 (bits 16-23, 0 = aucune), Poils de Chourou (bit 24) ;
     * bit 8 : entité en mouvement (bulles des niveaux aquatiques).
     */
    public int[] extras;
    /**
     * Déformations du sous-clip "sub" (5 par entité : x, y, échelle x, échelle y, rotation), null si aucune
     * entité n'est déformée ; filtres de couleur (intensité 0..100 << 24 | 0xRRGGBB), null si aucun.
     */
    public float[] subTransforms;
    /** Rotation du sous-clip intérieur par entité (lame de la scie), null si aucune entité n'en a. */
    public float[] innerRotations;
    public int[] tints;
    /** Bulles de bouclier visibles : x, y. */
    public float[] shields;
    /** Blocs de glace : x, y, échelle x, échelle y. */
    public float[] ice;
    /** Icônes ninja : x, y, image. */
    public float[] ninja;

    // ------------------------------------------------------------------ décors et portails
    public String[] decorLinks;
    public int[] decorFrames;
    public float[] decorXs, decorYs;
    /** Bit 1 : derrière les entités ; bit 2 : retourné (miroir). */
    public byte[] decorFlags;
    public float[] portals;

    // ------------------------------------------------------------------ interface
    public String[] names;
    public int[] lives;
    public long[] scores;
    /** Lettres EXTEND ramassées par joueur (bit i = lettre i). */
    public int[] extendMasks;
    /** Prédiction du client : uniqId de chaque joueur, état (MovePredictor.STATE_SIZE par joueur), dernier tick de touches reçu. */
    public int[] playerIds;
    public float[] predict;
    public int ackTick = -1;
    /** Fin de partie : objets ramassés (id, quantité, id, quantité…) pour le frigo du client ; null avant. */
    public int[] picks;
    /** Niveau glacé (variable ICE) : glissade, pour la prédiction du client. */
    public boolean iceLevel;
    public long chronoMs, lastLevelTime = -1, totalTime;
    public float starter, levelAvg;
    public int soccer0, soccer1;
    public long matchRemainingMs;
    /** Barre de vie du boss (0..1), -1 sans boss. */
    public float bossRatio = -1;
    public String messageKey, messageText;
    public boolean messageTuto;
    public int messageVersion;
    public int[] mapLids, mapEids;
    public String[] mapMiscs;
    public boolean[] mapTravel;

    // ------------------------------------------------------------------ événements (sons, effets, particules…)
    public String[] eventNames;
    public float[] eventXs, eventYs;
    public boolean[] eventSounds;

    /**
     * Construit l'instantané de la partie. {@code events} : événements accumulés depuis le précédent ;
     * {@code withGrid} : joindre la grille du niveau (premier envoi, changement de niveau ou de dalles).
     */
    public static RenderFrame capture(Game g, List<Game.Event> events, boolean withGrid) {
        RenderFrame f = new RenderFrame();
        f.cycle = g.cycle;
        f.levelCycle = g.levelCycle;
        f.setName = g.set.name;
        f.levelId = g.levelId;
        f.currentDim = g.currentDim;
        f.mainLevelId = g.mainLevelId();
        f.fakeLevelId = g.fakeLevelId == null ? -1 : g.fakeLevelId;
        f.mainWorld = g.isMainWorld();
        f.mirror = g.fl_mirror;
        f.gridVersion = g.world.version;
        if (withGrid) {
            int[][] grid = g.world.renderGrid();
            f.grid = new int[Const.LEVEL_WIDTH * Const.LEVEL_HEIGHT];
            for (int x = 0; x < Const.LEVEL_WIDTH; x++)
                for (int y = 0; y < Const.LEVEL_HEIGHT; y++) f.grid[x * Const.LEVEL_HEIGHT + y] = grid[x][y];
        }
        f.hideTiles = g.world.hideTiles;
        f.hideBorders = g.world.hideBorders;
        f.flipX = g.flipX;
        f.flipY = g.flipY;
        f.aqua = g.fl_aqua;
        f.iceLevel = g.fl_ice;
        if (g.specialBg != null) {
            f.specialBgId = g.specialBg[0];
            f.specialBgSub = g.specialBg[1];
        }
        f.darkness = (float) g.darkness;
        f.exitArrow = g.exitArrow;
        f.scroll = (float) g.scrollCpt;
        f.itemName = g.itemNameId;
        if (g.world != null) {
            List<Integer> on = new ArrayList<>();
            for (hammerfest.sim.World.Teleporter t : g.world.teleporterList) {
                if (!t.on) continue;
                on.add(t.cx);
                on.add(t.cy);
            }
            if (!on.isEmpty()) f.teleportersOn = on.stream().mapToInt(Integer::intValue).toArray();
        }
        if (g.pointer != null) f.pointer = new float[]{(float) g.pointer[0], (float) g.pointer[1], (float) g.pointer[2]};
        List<Float> holes = new ArrayList<>();
        double d = 150 * g.holeScale;
        for (Player p : g.players()) {
            if (!p.visible) continue;
            holes.add((float) p.x);
            holes.add((float) p.y - Const.CASE_HEIGHT);
            holes.add((float) d);
        }
        for (double[] h : g.extraHoles) {
            holes.add((float) h[0]);
            holes.add((float) h[1]);
            holes.add((float) h[2]);
        }
        f.holes = floats(holes);
        f.shakeTimer = (float) g.shakeTimer;
        f.shakeTotal = (float) g.shakeTotal;
        f.shakePower = (float) g.shakePower;
        f.music = g.currentTrack;
        f.multi = g.fl_multi;
        f.soccer = g.fl_soccer;
        f.timeAttack = g.fl_timeAttack;
        f.bossRush = g.fl_bossRush;
        f.tutorial = g.fl_tutorial;
        f.gameOver = g.fl_gameOver;
        if (g.fl_gameOver) {
            f.picks = new int[g.picks().size() * 2];
            int k = 0;
            for (java.util.Map.Entry<Integer, Integer> e : g.picks().entrySet()) {
                f.picks[k++] = e.getKey();
                f.picks[k++] = e.getValue();
            }
        }
        f.victory = g.fl_victory;

        // entités : profondeurs Flash DP_SPRITE_BACK_LAYER < DP_PLAYER < DP_ITEMS < DP_SHOTS < DP_BADS < DP_BOMBS
        // < DP_SUPA. Tuberculoz n'est pas un Bad (liste BOSS seulement, contrairement à la chauve-souris) : dessiné
        // avec les ennemis (DP_BADS), puis derrière tout une fois vaincu (openExit : DP_SPRITE_BACK_LAYER).
        List<Animator> back = new ArrayList<>(), bosses = new ArrayList<>();
        for (Entity e : g.getList(Const.BOSS)) {
            if (e instanceof Bad || !(e instanceof Animator)) continue;
            boolean defeated = e instanceof hammerfest.sim.entity.boss.Tuberculoz
                    && ((hammerfest.sim.entity.boss.Tuberculoz) e).fl_defeated;
            (defeated ? back : bosses).add((Animator) e);
        }
        List<Animator> order = new ArrayList<>(back);
        for (Player p : g.players()) order.add(p);
        int bombLayer = 0; // premier élément de DP_BOMBS dans order
        for (int type : new int[]{Const.ITEM, Const.SHOOT, Const.BAD, Const.BOMB, Const.SUPA}) {
            if (type == Const.BOMB) bombLayer = order.size();
            for (Entity e : g.getList(type)) if (e instanceof Animator) order.add((Animator) e);
            if (type == Const.BAD) order.addAll(bosses);
        }
        List<Animator> shown = new ArrayList<>();
        for (int k = 0; k < order.size(); k++) {
            if (k == bombLayer) f.bombEntities = shown.size();
            if (order.get(k).visible) shown.add(order.get(k));
        }
        if (bombLayer >= order.size()) f.bombEntities = shown.size();
        int n = shown.size();
        for (Animator a : shown) {
            if (a instanceof Player) f.playerEntities++;
            else if (back.contains(a)) f.backEntities++;
        }
        f.ids = new int[n];
        f.links = new String[n];
        f.anims = new short[n];
        f.subs = new short[n];
        f.xs = new float[n];
        f.ys = new float[n];
        f.xscales = new float[n];
        f.scales = new float[n];
        f.rotations = new float[n];
        f.alphas = new float[n];
        f.extras = new int[n];
        List<Float> shields = new ArrayList<>(), ice = new ArrayList<>(), ninja = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Animator a = shown.get(i);
            f.ids[i] = a.uniqId;
            f.links[i] = a.linkage;
            f.anims[i] = (short) a.animId;
            f.subs[i] = (short) a.subFrame;
            f.xs[i] = (float) (a.x + a.drawDx);
            f.ys[i] = (float) (a.y + a.yOffset + a.drawDy);
            if (a.hasSubTransform()) {
                if (f.subTransforms == null) {
                    f.subTransforms = new float[n * 5];
                    for (int k = 0; k < n; k++) {
                        f.subTransforms[k * 5 + 2] = 1;
                        f.subTransforms[k * 5 + 3] = 1;
                    }
                }
                f.subTransforms[i * 5] = (float) a.subX;
                f.subTransforms[i * 5 + 1] = (float) a.subY;
                f.subTransforms[i * 5 + 2] = (float) (a.subXscale / 100);
                f.subTransforms[i * 5 + 3] = (float) (a.subYscale / 100);
                f.subTransforms[i * 5 + 4] = (float) a.subRotation;
            }
            if (!Double.isNaN(a.innerRotation)) {
                if (f.innerRotations == null) {
                    f.innerRotations = new float[n];
                    java.util.Arrays.fill(f.innerRotations, Float.NaN);
                }
                f.innerRotations[i] = (float) a.innerRotation;
            }
            if (a.colorAlpha > 0) {
                if (f.tints == null) f.tints = new int[n];
                f.tints[i] = ((int) Math.round(Math.min(100, a.colorAlpha)) << 24) | (a.colorHex & 0xffffff);
            }
            f.xscales[i] = (float) (a.xscale / 100.0);
            f.scales[i] = (float) a.scaleFactor;
            f.rotations[i] = (float) a.rotation;
            f.alphas[i] = (float) (Math.max(a.minAlpha, a.alpha) / 100.0);
            int extra = 0;
            if (a instanceof Player) {
                Player p = (Player) a;
                extra = (p.skin & 0xff) | ((p.head & 0x7f) << 9) | (((p.currentWeapon + 1) & 0xff) << 16)
                        | (p.fl_chourou ? 1 << 24 : 0)
                        | (p.fl_carot ? EXTRA_CAROT : 0)
                        | (p.specialMan.active(100) ? 1 << 25 : 0); // nuage de la poupée Guu
                if (p.fl_shield && !p.fl_kill && !(p.shieldTimer <= Const.SECOND * 3 && (g.cycle / 2) % 2 == 1)) {
                    shields.add((float) p.shieldX);
                    shields.add((float) p.shieldY);
                    shields.add((float) (g.cycle - p.shieldStart));
                }
            }
            if ((a instanceof Player || a instanceof Bad) && a instanceof Physics) {
                Physics ph = (Physics) a;
                if (ph.dx != 0 || ph.dy != 0) extra |= 1 << 8;
            }
            if (a instanceof Bad) {
                Bad b = (Bad) a;
                if (b.fl_freeze && b.freezeTotal > 0) {
                    ice.add((float) b.x);
                    ice.add((float) b.y);
                    ice.add((float) (0.85 * b.scaleFactor));
                    ice.add((float) (0.85 * b.scaleFactor * b.freezeTimer / b.freezeTotal));
                }
                if ((b.fl_ninFoe || b.fl_ninFriend) && b.isHealthy()) {
                    ninja.add((float) b.x);
                    ninja.add((float) (b.y - Const.CASE_HEIGHT * 1.8));
                    ninja.add(b.fl_ninFoe ? 1f : 2f);
                }
                // fraise qui tient la balle (Fraise.mt : sub.balle._visible = fl_ball)
                if (b instanceof hammerfest.sim.entity.bad.Fruits.Fraise && ((hammerfest.sim.entity.bad.Fruits.Fraise) b).fl_ball) {
                    extra |= EXTRA_BALL;
                }
                if (b instanceof hammerfest.sim.entity.bad.Fruits.Framboise) { // tas : sub.o1 / sub.o2
                    hammerfest.sim.entity.bad.Fruits.Framboise fr = (hammerfest.sim.entity.bad.Fruits.Framboise) b;
                    if (fr.heapO1) extra |= EXTRA_O1;
                    if (fr.heapO2) extra |= EXTRA_O2;
                }
            }
            f.extras[i] = extra;
        }
        f.shields = floats(shields);
        f.ice = floats(ice);
        f.ninja = floats(ninja);

        // décors des scripts et portails flottants (pendant le défilement : ceux du nouveau niveau, posés par la
        // 1re lecture de son script ; ceux du niveau quitté sont gardés par le rendu)
        List<ScriptEngine.Decor> decors = g.script == null ? List.of() : g.script.decors;
        int m = decors.size();
        f.decorLinks = new String[m];
        f.decorFrames = new int[m];
        f.decorXs = new float[m];
        f.decorYs = new float[m];
        f.decorFlags = new byte[m];
        for (int i = 0; i < m; i++) {
            ScriptEngine.Decor dc = decors.get(i);
            f.decorLinks[i] = dc.linkage;
            f.decorFrames[i] = dc.frame;
            f.decorXs[i] = (float) dc.x;
            f.decorYs[i] = (float) dc.y;
            f.decorFlags[i] = (byte) ((dc.back ? 1 : 0) | (dc.mirrored ? 2 : 0));
        }
        List<Float> portals = new ArrayList<>();
        for (double[] p : g.floatingPortals.values()) {
            portals.add((float) p[0]);
            portals.add((float) p[1]);
        }
        f.portals = floats(portals);

        // interface
        int np = g.players().size();
        f.names = new String[np];
        f.lives = new int[np];
        f.scores = new long[np];
        f.extendMasks = new int[np];
        f.playerIds = new int[np];
        f.predict = new float[np * hammerfest.sim.MovePredictor.STATE_SIZE];
        for (int i = 0; i < np; i++) {
            Player p = g.players().get(i);
            f.names[i] = p.name;
            f.lives[i] = p.lives;
            f.scores[i] = p.score;
            f.extendMasks[i] = p.extendMask();
            f.playerIds[i] = p.uniqId;
            int o = i * hammerfest.sim.MovePredictor.STATE_SIZE;
            f.predict[o] = (float) p.x;
            f.predict[o + 1] = (float) p.y;
            f.predict[o + 2] = (float) p.dx;
            f.predict[o + 3] = (float) p.dy;
            f.predict[o + 4] = p.fl_stable ? 1 : 0;
            f.predict[o + 5] = p.dir;
            // états où le déplacement n'obéit plus aux touches : pas de prédiction
            f.predict[o + 6] = !p.fl_kill && !p.fl_knock && !p.fl_lockControls && !p.fl_entering && p.visible ? 1 : 0;
            f.predict[o + 7] = (float) p.speedFactor; // Cauchemar, Tornade, objets de vitesse
            f.predict[o + 8] = (float) p.fallFactor;
            f.predict[o + 9] = (float) p.gravityFactor;
            f.predict[o + 10] = p.specialMan.active(88) ? 1 : 0; // rétréci : saut divisé par deux
        }
        f.chronoMs = g.chronoMs();
        f.lastLevelTime = g.times.isEmpty() ? -1 : g.times.get(g.times.size() - 1);
        f.totalTime = g.totalTime;
        f.starter = (float) g.starter;
        f.levelAvg = (float) g.levelAvg;
        f.soccer0 = g.soccerScores[0];
        f.soccer1 = g.soccerScores[1];
        f.matchRemainingMs = g.matchRemainingMs();
        Entity boss = g.getOne(Const.BOSS);
        if (boss instanceof hammerfest.sim.entity.boss.Tuberculoz) {
            hammerfest.sim.entity.boss.Tuberculoz t = (hammerfest.sim.entity.boss.Tuberculoz) boss;
            if (!t.fl_death) f.bossRatio = Math.max(0, t.lives) / (float) hammerfest.sim.entity.boss.Tuberculoz.LIVES;
        }
        f.messageKey = g.messageKey;
        f.messageText = g.messageText;
        f.messageTuto = g.messageTuto;
        f.messageVersion = g.messageVersion;
        int nt = g.mapTravels.size(), ne = g.mapEvents.size();
        f.mapLids = new int[nt + ne];
        f.mapEids = new int[nt + ne];
        f.mapMiscs = new String[nt + ne];
        f.mapTravel = new boolean[nt + ne];
        for (int i = 0; i < nt + ne; i++) {
            Game.MapEvent e = i < nt ? g.mapTravels.get(i) : g.mapEvents.get(i - nt);
            f.mapLids[i] = e.lid;
            f.mapEids[i] = e.eid;
            f.mapMiscs[i] = e.misc;
            f.mapTravel[i] = i < nt;
        }

        int ev = events.size();
        f.eventNames = new String[ev];
        f.eventXs = new float[ev];
        f.eventYs = new float[ev];
        f.eventSounds = new boolean[ev];
        for (int i = 0; i < ev; i++) {
            Game.Event e = events.get(i);
            f.eventNames[i] = e.name;
            f.eventXs[i] = (float) e.x;
            f.eventYs[i] = (float) e.y;
            f.eventSounds[i] = e.sound;
        }
        return f;
    }

    private static float[] floats(List<Float> l) {
        float[] out = new float[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    /** Case de la grille (après {@link #grid} reçue), 0 hors du niveau. */
    public static int caseAt(int[] grid, int cx, int cy) {
        if (grid == null || cx < 0 || cx >= Const.LEVEL_WIDTH || cy < 0 || cy >= Const.LEVEL_HEIGHT) return 0;
        return grid[cx * Const.LEVEL_HEIGHT + cy];
    }
}
