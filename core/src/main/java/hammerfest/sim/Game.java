package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static hammerfest.sim.Const.*;

/**
 * Partie en cours : portage de la boucle de {@code mode.GameMode} (listes d'entités par type,
 * destruction différée, passage de niveau). Pas de dépendance libGDX : un cycle = {@link #tick},
 * 32 cycles par seconde, {@code tmod} = 1 (déterministe, rejouable à partir des InputFrame).
 *
 * <p>Non encore porté : ennemis, bombes, objets, ScriptEngine, portails/dimensions,
 * hurry up, effets d'ambiance. Sans ennemi, chaque niveau est « clear » dès son arrivée.</p>
 */
public class Game {

    /** Événement visuel ou sonore à jouer par le rendu (fx d'origine : attachFx / playSound). */
    public static final class Event {
        public final String name;
        public final double x, y;
        public final boolean sound;

        Event(String name, double x, double y, boolean sound) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.sound = sound;
        }
    }

    public LevelSet set;
    public final FrameSource frames;
    private final Random rng;
    /** Hasard des effets purement visuels (vibrations…) : ne décale pas celui de la simulation. */
    public final Random visualRng = new Random(7);
    private final Set<Integer> families;
    private final Set<String> options;

    public World world;
    public int levelId;
    public int cycle;
    public final double tmod = 1;
    public final double xFriction = FRICTION_X;
    public final double yFriction = FRICTION_Y;
    public final double gFriction = FRICTION_GROUND;
    public final double sFriction = FRICTION_SLIDE;
    public boolean fl_ice, fl_aqua, fl_nightmare, fl_bombExpert;
    /** Ennemis placés selon leur côté de l'écran plutôt qu'au hasard (GameMode.fl_static). */
    public boolean fl_static;
    /** Facteur de vitesse global des ennemis (GameMode.speedFactor). */
    public double badSpeedFactor = 1.0;
    /** Ennemis tués depuis le début de la partie. */
    public int badsKilled;
    private final Map<Integer, Integer> comboList = new HashMap<>();

    // Objets : tirage (RandomManager), valeurs, ramassage (pickUpScore / pickUpSpecial)
    private final RandomTable randItems = new RandomTable();
    /** Tirage des lettres EXTEND (Data.RAND_EXTENDS), poids par lettre C-R-Y-S-T-A-L. */
    private final RandomTable randExtends = new RandomTable();
    {
        int[] w = {10, 10, 6, 5, 5, 10, 4};
        for (int i = 0; i < w.length; i++) randExtends.put(i, w[i]);
    }
    /** Meilleur combo du niveau (StatsManager STAT_MAX_COMBO), remis à 0 à chaque niveau. */
    public int maxCombo;
    /** Lettres pouvant apparaître dans le niveau (StatsManager.extendList). */
    public final List<Integer> extendList = new ArrayList<>();
    private final RandomTable randScores = new RandomTable();
    private final Map<Integer, Integer> itemValues = new HashMap<>();
    /** Objets ramassés : id de données (spéciaux 0+, score 1000+) → quantité. Alimente le frigo. */
    private final Map<Integer, Integer> picks = new java.util.TreeMap<>();
    /**
     * Apparitions d'objets programmées dans le niveau :
     * {cycle, type (0 spécial / 1 score), id, x, y, subId (-1), infini (0/1), annulé si niveau fini (0/1)}.
     */
    private final List<int[]> itemTimers = new ArrayList<>();
    public int levelCycle;

    /** Effets globaux d'objets (GameMode.globalActives : 69 tortue, 80 escargot, 94…). */
    public final boolean[] globalActives = new boolean[130];
    // Hurry up (GameMode.huTimer / huState / diffFactor) : seuils de 35 s puis 25 s
    public static final double[] HU_STEPS = {35 * SECOND, 25 * SECOND};
    public double huTimer;
    public int huState;
    public double diffFactor = 1.0;

    // Dimensions (GameMode.dimensions) : 0 = set principal, puis les sets secondaires (Adventure.initWorld)
    private String[] dimensionNames = new String[0];
    private java.util.function.Function<String, LevelSet> setLoader;
    private PortalLinks links = new PortalLinks(null);
    public int currentDim;
    /** Niveau courant de chaque dimension quittée (on y revient au même endroit). */
    private final Map<Integer, Integer> dimLevels = new HashMap<>();
    /** Portails flottants ouverts par script (e_openPortal) : pid → {x, y}. */
    public final Map<Integer, double[]> floatingPortals = new HashMap<>();

    // Obscurité (GameMode.updateDarkness / holeUpdate) : opacité 0..100 du voile noir
    public static final int MIN_DARKNESS_LEVEL = 16;
    public Integer forcedDarkness;
    double targetDark;
    /** Opacité courante du voile (approche la cible de 2 points par cycle). */
    public double darkness;
    /** Taille des halos des joueurs (1.5 avec la bougie). */
    public double holeScale = 1;
    /** Halos de lumière fixes (torches) : {x, y, diamètre}. */
    public final List<double[]> extraHoles = new ArrayList<>();

    // Écran inversé (GameMode.flipX / flipY, objets spéciaux 30 et 31) : purement visuel
    public boolean flipX, flipY;

    /** Script du niveau courant. */
    public ScriptEngine script;
    public boolean fl_mirror, fl_ninja;
    /** Numéro de niveau affiché imposé par un script (e_fakelid), null sinon. */
    public Integer fakeLevelId;
    /** Variables dynamiques posées par les scripts (ICE, AQUA, SPEAR_SKIN…). */
    private final Map<String, String> dynamicVars = new HashMap<>();
    /** Message affiché (clé de langue ou texte brut), null si aucun. */
    public String messageKey, messageText;

    /** Diamants de conversion restant à ramasser pour le bonus « parfait ». */
    public int perfectItemCpt;
    /** Actions à exécuter à la fin du niveau (GameMode.endLevelStack). */
    public final List<Runnable> endLevelStack = new ArrayList<>();
    /** Tremblement d'écran en cours (durée en cycles, puissance en pixels), lu par le rendu. */
    public double shakeTimer, shakeTotal, shakePower;
    public boolean fl_clear;
    public boolean fl_gameOver;
    /** Partie à plusieurs joueurs (MultiCoop). */
    public boolean fl_multi;
    /** Partie gagnée : sortie par l'ascenseur de fin (endModeTimer). */
    public boolean fl_victory;
    /** GameMode.endModeTimer : fin automatique de la partie. */
    public double endModeTimer;
    /** Niveau le plus profond atteint (statistiques). */
    public int maxLevel;

    private final Map<Integer, List<Entity>> lists = new HashMap<>();
    private final List<Entity> killList = new ArrayList<>();
    private final List<Object[]> unregList = new ArrayList<>();
    private final List<Player> players = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private InputFrame[] inputs = new InputFrame[0];
    private int uniq;

    public Game(LevelSet set, FrameSource frames, long seed, Set<Integer> families, Set<String> options) {
        this.set = set;
        this.frames = frames;
        this.rng = new Random(seed);
        this.families = new HashSet<>(families);
        this.options = new HashSet<>(options);
        fl_nightmare = options.contains("nightmare") || options.contains("nightmaremulti");
        fl_mirror = options.contains("mirror") || options.contains("mirrormulti");
        fl_ninja = options.contains("ninja");
        fl_bombExpert = options.contains("bombexpert");
    }

    /**
     * Familles d'objets du jeu : seules les familles débloquées alimentent le tirage
     * (Data.getRandFromFamilies), toutes donnent les valeurs en points.
     */
    public void setItemFamilies(List<hammerfest.data.ItemFamily> all) {
        for (hammerfest.data.ItemFamily f : all) {
            for (hammerfest.data.ItemDef i : f.items) {
                if (i.value != null) itemValues.put(i.id, i.value);
                itemRarities.put(i.id, i.rarity);
                if (!families.contains(f.id)) continue;
                int weight = i.rarity >= 0 && i.rarity < RARITY.length ? RARITY[i.rarity] : 0;
                if (f.isScoreFamily()) randScores.put(i.id, weight);
                else randItems.put(i.id, weight);
            }
        }
    }

    /** Indice de rareté d'origine de l'objet (0 = jamais tiré au hasard), 0 si inconnu. */
    public int itemRarity(int dataId) {
        return itemRarities.getOrDefault(dataId, 0);
    }

    private final Map<Integer, Integer> itemRarities = new HashMap<>();

    public Integer itemValue(int dataId) {
        return itemValues.get(dataId);
    }

    public void pickUp(int dataId) {
        picks.merge(dataId, 1, Integer::sum);
    }

    /** Objets ramassés pendant la partie (id de données → quantité). */
    public Map<Integer, Integer> picks() {
        return Collections.unmodifiableMap(picks);
    }

    /** Démarre au niveau {@code startId} avec {@code playerCount} joueurs placés au point de départ. */
    public void start(int startId, int playerCount) {
        levelId = startId;
        world = new World(levelData(startId));
        mirrorPortals();
        fl_multi = playerCount > 1;
        for (int pid = 0; pid < playerCount; pid++) {
            Player p = new Player(pid);
            if (fl_multi && fl_timeAttack) {
                // TimeAttackMulti.initGame : départ symétrique, le joueur 2 est Sandy
                int px = world.data.playerX;
                int cx = pid == 0 ? px : (int) (LEVEL_WIDTH * 0.5 + (LEVEL_WIDTH * 0.5 - px));
                p.initPlayer(this, Entity.xCtr(cx), Entity.yCtr(world.data.playerY));
                if (pid == 1) {
                    p.name = "Sandy";
                    p.skin = 2;
                    p.defaultHead = HEAD_SANDY;
                    p.head = p.defaultHead;
                }
            } else if (fl_multi) {
                // MultiCoop.initGame : joueurs lâchés du haut de l'écran, colonnes 5 et 14
                p.initPlayer(this, Entity.xCtr(pid == 0 ? 5 : 14), Entity.yCtr(-2));
                // MultiCoop.initPlayer : moitié des vies (arrondie au-dessus) ; le joueur 2 est Sandy
                p.lives = (int) Math.ceil(p.lives * 0.5);
                if (pid == 1) {
                    p.name = "Sandy";
                    p.skin = 2;
                    p.defaultHead = HEAD_SANDY;
                    p.head = p.defaultHead;
                }
            } else {
                p.initPlayer(this, Entity.xCtr(world.data.playerX), Entity.yCtr(world.data.playerY));
            }
            upgradePlayer(p);
            players.add(p);
        }
        onLevelReady();
    }

    /** Un cycle de jeu. {@code inputs[pid]} : entrées du joueur pid pour ce cycle. */
    public void tick(InputFrame... inputs) {
        this.inputs = inputs;
        if (fl_gameOver) return;
        cycle++;
        levelCycle++;
        fl_ice = getDynamicVar("ICE") != null;
        fl_aqua = getDynamicVar("AQUA") != null;
        if (script != null) script.update();
        updateItemTimers();
        updateHurry();
        holeUpdate();
        updateBg();
        if (fl_timeAttack) updateTimeAttack();
        if (fl_bossRush && !fl_chronoStop) chrono += tmod;
        if (fl_soccer) updateSoccer();
        if (!fl_badsCallback && !getList(BAD).isEmpty()) { // GameMode.main
            onBadsReady();
            fl_badsCallback = true;
        }
        if (endModeTimer > 0) {
            endModeTimer -= tmod;
            if (endModeTimer <= 0) {
                fl_victory = true;
                onGameOver();
                return;
            }
        }
        // Parcours de la liste vivante, comme GameMode.main : une entité créée pendant le cycle
        // (bombe posée…) est mise à jour dans ce même cycle ; les détruites partent en cleanKills.
        List<Entity> l = getList(ENTITY);
        for (int i = 0; i < l.size(); i++) {
            Entity e = l.get(i);
            e.update();
            e.endUpdate();
        }
        cleanKills();
        if (pendingLink != null) {
            PortalLinks.Link link = pendingLink;
            pendingLink = null;
            switchDimension(link.toDid, link.toLid, link.toPid);
        }
        checkLevelClear();
        // GameMode.main : fausse indication de sortie (ennemis revenus), pas de flèche en apprentissage ni au SoccerFest
        if (exitArrow && (countList(BAD_CLEAR) > 0 || fl_tutorial || fl_soccer)) detachExit();
        // GameMode.main, « Enervement minimum » : en Cauchemar, tout ennemi à éliminer est au moins énervé d'un cran,
        // dès son apparition et à nouveau après chaque calmDown (VITE !, mort d'un joueur, niveau suivant…)
        if (fl_nightmare) {
            for (Entity e : getList(BAD)) {
                if (e instanceof hammerfest.sim.entity.Bad && e.isType(BAD_CLEAR) && ((hammerfest.sim.entity.Bad) e).anger == 0) {
                    ((hammerfest.sim.entity.Bad) e).angerMore();
                }
            }
        }
        if (shakeTimer > 0) {
            shakeTimer -= tmod;
            if (shakeTimer <= 0) {
                shakeTimer = 0;
                shakePower = 0;
            }
        }
    }

    /** Entités du type donné dans un rayon (GameMode.getClose). */
    public List<Entity> getClose(int type, double x, double y, double radius, boolean onGround) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : getList(type)) {
            double square = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y);
            if (square <= radius * radius && (!onGround || e.y <= y + CASE_HEIGHT)) out.add(e);
        }
        return out;
    }

    public void shake(double duration, double power) {
        shakeTimer = duration;
        shakeTotal = duration;
        shakePower = power;
    }

    /** Explosion d'une bombe du joueur : déclencheurs « exp » du script. */
    public void onExplode(double x, double y, double radius) {
        if (script != null) script.onExplode(x, y, radius);
        Entity boss = getOne(BOSS);
        if (boss instanceof hammerfest.sim.entity.boss.Tuberculoz) {
            ((hammerfest.sim.entity.boss.Tuberculoz) boss).onExplode(x, y, radius);
        }
    }

    /**
     * Dimensions accessibles par les portails : {@code names[0]} doit être le set principal,
     * chargées à la demande par {@code loader}.
     */
    public void setDimensions(String[] names, java.util.function.Function<String, LevelSet> loader,
                              hammerfest.data.PortalData portals) {
        dimensionNames = names;
        setLoader = loader;
        links = new PortalLinks(portals);
    }

    public boolean isMainWorld() {
        return currentDim == 0;
    }

    /** GameMode.usePortal : false si le portail ne mène nulle part. */
    public boolean usePortal(int pid, Entity e) {
        if (pendingLink != null) return true;
        PortalLinks.Link link = links.get(currentDim, levelId, pid);
        if (link == null || link.toDid >= dimensionNames.length || setLoader == null) return false;
        pendingLink = link; // appliqué en fin de cycle (pas pendant le déplacement des entités)
        if (isMainWorld()) { // GameMode.usePortal : sortie notée sur la carte, côté du portail
            // Portail ouvert par script (e_openPortal) : pas d'entité ; en Flash e.x vaut alors « undefined », la
            // comparaison est fausse et la sortie est toujours notée à GAUCHE du puits (le retour, lui, suit le point
            // d'arrivée : souvent à droite), comme dans le jeu d'origine.
            boolean right = e != null && flipCoordReal(e.x) >= GAME_WIDTH * 0.5;
            registerMapEvent(right ? EVENT_EXIT_RIGHT : EVENT_EXIT_LEFT,
                    (levelId + 1) + ". {level." + link.toDid + "." + link.toLid + "}");
        }
        for (Player p : players) p.hide();
        return true;
    }

    private PortalLinks.Link pendingLink;

    /** GameMode.switchDimensionById + onRestore : change de dimension et place les joueurs au portail d'arrivée. */
    public void switchDimension(int did, int lid, int pid) {
        if (!fl_clear) return;
        LevelSet target = setLoader.apply(dimensionNames[did]);
        if (target == null || lid < 0 || lid >= target.levels.size()) return;
        for (Player p : players) {
            p.specialMan.clearTemp();
            p.specialMan.clearRec();
            p.changeWeapon(WEAPON_B_CLASSIC);
        }
        if (fl_bossRush) registerLevelTime();
        dimLevels.put(currentDim, levelId);
        if (script != null && currentKey >= 0) savedScripts.put(currentKey, script.save()); // suspend
        currentDim = did;
        set = target;
        floatingPortals.clear();
        fakeLevelId = isMainWorld() ? null : 0;
        goTo(lid);
        if (!isMainWorld()) fakeLevelId = 0;
        // onRestore : arrivée à côté du portail pid (ou au départ si pid < 0)
        int px = world.data.playerX, py = world.data.playerY;
        boolean unstable = true;
        if (pid >= 0 && pid < world.portalList.size()) {
            px = world.portalList.get(pid)[0];
            py = world.portalList.get(pid)[1];
            if (world.getCase(px, py + 1) == FIELD_PORTAL) {
                while (world.getCase(px, py + 1) == FIELD_PORTAL) py++;
                if (world.getCase(px - 1, py) <= 0) px--;
                else if (world.getCase(px + 1, py) <= 0) px++;
                unstable = false;
            } else if (world.getCase(px + 1, py) == FIELD_PORTAL) {
                py += 2;
            }
        }
        if (isMainWorld()) registerMapEvent(px >= LEVEL_WIDTH * 0.5 ? EVENT_BACK_RIGHT : EVENT_BACK_LEFT, null); // onRestore
        for (Player p : players) {
            p.moveToCase(px, py);
            p.show();
            p.fl_entering = false;
            p.dx = 0;
            p.dy = 0;
            if (unstable) p.knock(SECOND * 0.6);
        }
    }

    /** Recalcule l'obscurité visée (niveau, script, bougie / ampoule). */
    public void updateDarkness() {
        if (fl_tutorial) { // Tutorial.darknessManager : pas d'obscurité
            targetDark = 0;
            darkness = 0;
            return;
        }
        if (forcedDarkness != null) {
            targetDark = forcedDarkness;
        } else if (isMainWorld()) {
            if (levelId < MIN_DARKNESS_LEVEL || levelId > TUBERCULOZ_LEVEL) {
                targetDark = 0;
                darkness = 0;
                return;
            }
            targetDark = levelId;
        } else {
            targetDark = darkness;
        }
        holeScale = 1;
        for (Player p : players) {
            if (p.fl_candle || p.specialMan.active(68)) holeScale = 1.5;
            if (p.fl_torch || p.specialMan.active(26)) targetDark *= forcedDarkness == null ? 0.5 : 0.75;
        }
    }

    public void addHole(double x, double y, double diameter) {
        extraHoles.add(new double[]{x, y, diameter});
    }

    public void clearExtraHoles() {
        extraHoles.clear();
    }

    private void holeUpdate() {
        darkness = Math.round(darkness);
        double t = Math.round(targetDark);
        if (darkness < t) darkness = Math.min(t, darkness + 2);
        if (darkness > t) darkness = Math.max(t, darkness - 2);
    }

    /** Portail flottant ouvert par script ; on l'emprunte en s'en approchant (niveau terminé). */
    public void openPortal(int cx, int cy, int pid) {
        if (floatingPortals.containsKey(pid)) return;
        double x = Entity.xCtr(flipCoordCase(cx)), y = Entity.yCtr(cy) - CASE_HEIGHT * 0.5;
        floatingPortals.put(pid, new double[]{x, y});
        explosion(x, y, 40);
        particles(PARTICLE_PORTAL, x, y, 5, 0);
        shine(x, y);
        if (script != null) {
            script.addScript("<t_pos x=\"" + cx + "\" y=\"" + cy + "\" d=\"1\" repeat=\"-1\"><e_portal pid=\"" + pid + "\"/></t_pos>");
        }
    }

    /** Clé possédée : débloquée par une quête (famille 5000+id) ou ramassée pendant la partie. */
    public boolean hasKey(int id) {
        return families.contains(5000 + id) || picks.containsKey(5000 + id);
    }

    /** GameMode.canAddItem. */
    public boolean canAddItem() {
        return !fl_clear || world.data.badList.isEmpty();
    }

    public String getDynamicVar(String name) {
        return dynamicVars.get(name);
    }

    public void setDynamicVar(String name, String value) {
        if (value == null || value.isEmpty()) dynamicVars.remove(name);
        else dynamicVars.put(name, value);
    }

    /** Message de tutoriel (parchemin « Apprentissage ») plutôt que message standard. */
    public boolean messageTuto;
    /** Incrémenté à chaque nouveau message (relance l'animation d'apparition). */
    public int messageVersion;

    /** Message à afficher (clé de langue ou texte brut) ; (null, null) l'efface. */
    public void message(String langKey, String text) {
        message(langKey, text, false);
    }

    public void message(String langKey, String text, boolean tuto) {
        messageKey = langKey;
        messageText = text == null ? null : text.trim();
        messageTuto = tuto;
        messageVersion++;
    }

    /** Prédiction locale (MovePredictor) : entrées du seul joueur pour un cycle. */
    void predictInputs(InputFrame in) {
        inputs = new InputFrame[]{in};
        cycle++;
        levelCycle++;
    }

    public InputFrame inputFor(int pid) {
        return pid < inputs.length && inputs[pid] != null ? inputs[pid] : new InputFrame(cycle, 0);
    }

    // ------------------------------------------------------------------ niveaux

    public boolean checkLevelClear() {
        if (fl_clear) return true;
        if (countList(BAD_CLEAR) > 0) return false;
        if (countList(BOSS) > 0) return false; // le boss gère lui-même la fin de son niveau
        onLevelClear();
        return true;
    }

    /** GameMode.onLevelClear : annule les apparitions d'objets « endClear », retire les lettres EXTEND. */
    void onLevelClear() {
        if (fl_clear) return;
        itemTimers.removeIf(t -> t[7] == 1);
        for (Entity e : new ArrayList<>(getList(SPECIAL_ITEM))) {
            if (((hammerfest.sim.entity.Item) e).id == 0) e.destroy();
        }
        fl_clear = true;
        if (script != null) script.clearEndTriggers();
        attachExit();
        List<Runnable> stack = new ArrayList<>(endLevelStack);
        endLevelStack.clear();
        for (Runnable r : stack) r.run();
        // Adventure.onLevelClear : ennemis tués dans l'ordre d'apparition → super-objet
        if (!isVisited() && !fl_timeAttack && !fl_tutorial && perfectCount > 0 && perfectOrder.isEmpty() && script != null && script.cycle >= 10) {
            for (Player p : players) p.setBaseAnims(Anim.PLAYER_WALK_V, Anim.PLAYER_STOP_V);
            hammerfest.sim.entity.Supa.SupaItem.attach(this, perfectCount - 1);
        }
    }

    /** Programme l'apparition d'un objet dans {@code delay} cycles (ScriptEngine.insertItem). */
    public void scheduleItem(int delay, boolean score, int id, int subId, int cx, int cy, boolean infinite) {
        itemTimers.add(new int[]{levelCycle + delay, score ? 1 : 0, id, cx, cy, subId, infinite ? 1 : 0, 0});
    }

    /** Retire un objet spécial du tirage (objets uniques). */
    public void removeRandomItem(int id) {
        randItems.remove(id);
    }

    public void destroyList(int type) {
        for (Entity e : new ArrayList<>(getList(type))) e.destroy();
    }

    /** Adventure.isBossLevel : monde principal, niveaux 30, 40… 90, chauve-souris (100) et Tuberculoz (101). */
    public boolean isBossLevel(int id) {
        return "adventure".equals(set.name) && isMainWorld()
                && ((id >= 30 && id % 10 == 0) || id == BAT_LEVEL || id == TUBERCULOZ_LEVEL);
    }

    /**
     * TEMPORAIRE (aide aux tests manuels, touche 2) : une vie de plus pour chaque joueur (niveaux difficiles des
     * contrées). À supprimer une fois le portage terminé.
     */
    public void debugAddLife() {
        for (Player p : players) if (!p.fl_kill) p.lives++;
        sound("sound_item_special");
    }

    /**
     * TEMPORAIRE (aide aux tests manuels, touche 1) : saute au niveau suivant comme le parapluie
     * (objet 11, warpZone(1)). À supprimer une fois le portage terminé.
     */
    public void debugSkipLevel() {
        // comme le parapluie rouge (warpZone(1)) : jamais au-delà de la fin d'une dimension
        if (warpArrival(1) == levelId) {
            alert("static.34", ""); // « Téléportation impossible »
            return;
        }
        // le script <end> du niveau de boss (e_music 0) ne joue pas si on le saute sans le finir
        if (currentTrack == 1) playMusic(0);
        forcedGoto(levelId + 1);
    }

    /**
     * SpecialManager.warpZone : arrivée d'un saut de {@code w} niveaux, arrêtée sur un niveau de boss et avant un
     * niveau vide (frontière d'une dimension, fin du parcours) ; vaut le niveau courant si le saut est impossible.
     */
    public int warpArrival(int w) {
        int arrival = levelId + w;
        for (int i = levelId + 1; i <= arrival; i++) {
            if (isBossLevel(i)) arrival = i;
            if (isEmptyLevel(i)) arrival = i - 1;
        }
        return arrival;
    }

    /** Saut direct à un niveau (warp) : joueurs replacés au départ, protégés par un bouclier. */
    public void forcedGoto(int id) {
        if (fakeLevelId != null) fakeLevelId += id - levelId; // GameMode.forcedGoto : numéro affiché dans une contrée
        goTo(id);
        for (Player p : players) {
            p.moveTo(Entity.xCtr(world.data.playerX), Entity.yCtr(world.data.playerY - 1));
            p.shield(SHIELD_DURATION * 1.3);
        }
    }

    /** Adventure.addLevelItems : un objet spécial à 8 s et un objet à score à 12 s, sur des emplacements tirés au sort. */
    void addLevelItems() {
        LevelData l = world.data;
        if (!l.specialSlots.isEmpty()) spreadExtend();
        if (!l.specialSlots.isEmpty() && !randItems.isEmpty()) {
            LevelData.Slot pt = l.specialSlots.get(random(l.specialSlots.size()));
            itemTimers.add(new int[]{levelCycle + (int) SPECIAL_ITEM_TIMER, 0, randItems.draw(this), pt.x, pt.y, -1, 0, 1});
        }
        if (!l.scoreSlots.isEmpty() && !randScores.isEmpty()) {
            LevelData.Slot pt = l.scoreSlots.get(random(l.scoreSlots.size()));
            itemTimers.add(new int[]{levelCycle + (int) SCORE_ITEM_TIMER, 1, randScores.draw(this), pt.x, pt.y, -1, 0, 1});
            if (globalActives[94]) { // objet à score supplémentaire
                int[] g = world.getGround(random(LEVEL_WIDTH), random(LEVEL_HEIGHT - 5));
                itemTimers.add(new int[]{levelCycle + (int) SCORE_ITEM_TIMER, 1, randScores.draw(this), g[0], g[1], -1, 0, 1});
            }
        }
    }

    /**
     * StatsManager.spreadExtend : 1 lettre possible, plus une par ennemi au-delà du premier dans le
     * meilleur combo du niveau précédent (7 au plus) ; une lettre au hasard parmi elles toutes les 10 s.
     */
    void spreadExtend() {
        int nb = Math.min(EXTENDS, 1 + (maxCombo >= 2 ? maxCombo - 1 : 0));
        extendList.clear();
        while (extendList.size() < nb) {
            int id = randExtends.draw(this);
            if (!extendList.contains(id)) extendList.add(id);
        }
        script.addScript("<t_timer t=\"" + (int) EXTEND_TIMER + "\" repeat=\"-1\" endClear=\"1\"><e_ext/></t_timer>");
    }

    private void updateItemTimers() {
        for (int i = 0; i < itemTimers.size(); i++) {
            int[] t = itemTimers.get(i);
            if (levelCycle < t[0]) continue;
            itemTimers.remove(i--);
            double x = flipCoordReal(Entity.xCtr(t[3]));
            double y = Entity.yCtr(t[4]);
            hammerfest.sim.entity.Item it = null;
            if (t[1] == 0) {
                if (!fl_clear || world.data.badList.isEmpty()) {
                    it = hammerfest.sim.entity.SpecialItem.attach(this, x, y, t[2], t[5]);
                }
            } else {
                it = hammerfest.sim.entity.ScoreItem.attach(this, x, y, t[2], t[5]);
            }
            if (it != null && t[6] == 1) it.lifeTimer = -1; // durée de vie infinie
        }
    }

    /** Adventure.fl_warpStart : le seau du village a été détruit, la chute mène directement au niveau 10. */
    public boolean fl_warpStart;

    public void nextLevel() {
        if (fl_warpStart && isMainWorld()) { // Adventure.nextLevel : raccourci du seau
            forcedGoto(10);
            return;
        }
        if (fakeLevelId != null) fakeLevelId++; // GameMode.nextLevel : numéro affiché dans une contrée
        goTo(levelId + 1);
    }

    /** GameMode.goto : change de niveau, le premier joueur arrivé en bas entre en premier. */
    public void goTo(int id) {
        if (fl_gameOver) return;
        if (id >= set.levels.size()) {
            onGameOver();
            return;
        }
        if (fl_timeAttack && world != null && !timeAttackGoTo(id)) return;
        if (fl_bossRush && world != null) { // BossRush.goto : temps du niveau, repère de temps tous les 10 niveaux
            if ((id - 1) % 10 == 0 && id > 1 && isMainWorld()) registerMapEvent(EVENT_TIME, formatTime(chronoMs()));
            registerLevelTime();
        }
        if (currentKey >= 0) visited.add(currentKey); // GameMechanics.goto : setVisited
        fl_clear = false;
        levelId = id;
        if (isMainWorld()) maxLevel = Math.max(maxLevel, id);
        floatingPortals.clear(); // GameMode.goto : killPortals (vortex non emprunté du niveau quitté)
        detachExit(); // FxManager.onNextLevel
        pointer = null;
        clearLevel();
        world = new World(levelData(id));
        mirrorPortals();
        for (Entity e : getList(ENTITY)) e.world = world;

        Player best = null;
        for (Player p : players) if (best == null || p.y > best.y) best = p;
        for (Player p : players) {
            p.moveTo(best.x, -600);
            p.hide();
            p.onNextLevel();
        }
        if (best != null) best.moveTo(best.x, -200);
        sound("sound_level_clear");
        onLevelReady();
    }

    void onLevelReady() {
        levelStartMs = chronoMs();
        fl_badsCallback = false;
        clearBg(); // fxMan.clear
        for (Player p : players) p.setBaseAnims(Anim.PLAYER_WALK, Anim.PLAYER_STOP); // Adventure.startLevel
        currentKey = currentDim * 10000 + levelId;
        fl_clear = false;
        perfectOrder.clear();
        perfectCount = 0;
        badCount = 0;
        comboList.clear();
        itemTimers.clear();
        endLevelStack.clear();
        levelCycle = 0;
        if (!isVisited()) resetHurry(); // GameMode.initLevel
        dynamicVars.clear();
        // fakeLevelId n'est PAS remis à zéro à chaque niveau (GameMode) : 0 en entrant dans une contrée, +1 par niveau,
        // fixé ou effacé par script (e_fakelid), effacé au retour dans le puits (switchDimension)
        message(null, null);
        forcedDarkness = null;
        clearExtraHoles();
        String saved = savedScripts.get(currentKey);
        script = new ScriptEngine(this, saved != null ? saved : world.data.script);
        if (fl_warpStart) { // Adventure.onLevelReady : porte secrète à l'arrivée du raccourci
            if (isMainWorld()) {
                // (le y d'origine utilise x_ctr sur playerY : reproduit tel quel)
                script.addDecor("door_secret", Entity.xCtr(world.data.playerX),
                        Entity.xCtr(world.data.playerY) + CASE_HEIGHT * 0.5, true);
            }
            fl_warpStart = false;
        }
        script.onPlayerBirth();
        if (isBossLevel(levelId)) alert("static.12", ""); // GameMode.startLevel : fxMan.attachWarning, « DANGER BOSS ! »
        updateDarkness();
        for (Player p : players) { // GameMode.initLevel : flèche d'entrée au-dessus du joueur qui arrive par le haut
            p.onStartLevel();
            if (p.y < 0) fx("hammer_fx_enter", p.x, 0);
        }
        // startLevel : ScriptEngine.insertBads (e_bad : x_ctr(cx) - CASE_WIDTH/2, y_ctr(cy - 1))
        // (ennemis « sys » : pas réinsérés dans un niveau déjà visité)
        boolean visitedLevel = isVisited();
        if (!visitedLevel) {
            for (LevelData.Bad b : world.data.badList) {
                hammerfest.sim.entity.bad.Fruits.attach(this, b.id,
                        Entity.xCtr(flipCoordCase(b.x)) - CASE_WIDTH * 0.5, Entity.yCtr(b.y - 1));
            }
        }
        if (visitedLevel || world.data.badList.isEmpty()) { // GameMode.startLevel : aucun ennemi → flèche de sortie
            fl_clear = true;
            attachExit();
        }
        if (isMainWorld() && levelId == 0) detachExit(); // Adventure.startLevel : pas de flèche au niveau 0
        if (!isVisited() && !fl_timeAttack && !fl_soccer && !fl_tutorial) addLevelItems(); // Time Attack, SoccerFest, Apprentissage : pas d'objets
        if (fl_tutorial && levelId == 0) { // Tutorial.onLevelReady : « Forêt d'Hammerfest »
            events.add(new Event("levelpop:static.16|0", 0, 0, false));
        } else if (!isVisited() && !fl_timeAttack && !fl_soccer && !fl_tutorial) { // Adventure.onLevelReady : nom de la zone
            events.add(new Event("levelpop:level." + currentDim + "." + levelId + "|" + (levelId > 0 ? 1 : 0), 0, 0, false));
        }
        if (fl_soccer) { // Soccer.onLevelReady : coup d'envoi 3 s après
            fl_match = false;
            offPlayTimer = START_OFFTIME;
            for (Player p : players) soccerInitPlayer(p);
        }
        if (fl_timeAttack && levelId == taFirstLevel) { // compte à rebours de départ
            starter = SECOND * 3.5;
            chrono = 0;
            fl_chronoStop = true;
            for (Player p : players) p.lockControls(starter);
        }
        maxCombo = 0; // statsMan.reset()
        // Adventure.startLevel : boss
        if (isBossLevel(levelId) && levelId == BAT_LEVEL) {
            hammerfest.sim.entity.boss.Bat.attach(this);
            fl_clear = false;
        }
        if (isBossLevel(levelId) && levelId == TUBERCULOZ_LEVEL) {
            hammerfest.sim.entity.boss.Tuberculoz.attach(this);
            fl_clear = false;
        }
    }

    /** Hurry up : 35 s → ennemis énervés ; +25 s → boule de feu ; relancée si elle disparaît. */
    private void updateHurry() {
        if (fl_tutorial) return; // Tutorial.onHurryUp : désactivé
        huTimer += tmod;
        if (huState < HU_STEPS.length && huTimer >= HU_STEPS[huState] / diffFactor) onHurryUp();
        if (huState >= HU_STEPS.length && countList(HU_BAD) == 0) {
            callEvilOne((int) Math.round(huTimer / hammerfest.sim.entity.bad.FireBall.AUTO_ANGER));
        }
    }

    /** Piste musicale en cours (Mode.currentTrack) : 0 jeu, 1 boss, 2 hurry up. */
    public int currentTrack;

    public void playMusic(int id) {
        currentTrack = id;
    }

    void onHurryUp() {
        if (currentTrack == 0) playMusic(2); // Adventure.onHurryUp
        huState++;
        huTimer = 0;
        for (Entity e : new ArrayList<>(getList(BAD))) ((hammerfest.sim.entity.Bad) e).onHurryUp();
        fx(huState == 1 ? "hurryUp" : "hurryUp2", GAME_WIDTH / 2.0, GAME_HEIGHT / 2.0);
        if (huState == 1) sound("sound_hurry");
        if (huState == 2) callEvilOne(0);
    }

    void callEvilOne(int baseAnger) {
        for (Player p : new ArrayList<>(players)) {
            if (p.fl_kill) continue;
            hammerfest.sim.entity.bad.FireBall f = hammerfest.sim.entity.bad.FireBall.attach(this, p);
            f.anger = baseAnger - 1;
            if (baseAnger > 0) {
                f.fl_summon = false;
                f.stopBlink();
            }
            f.angerMore();
        }
    }

    /** Remise à zéro du hurry up (nouveau niveau, résurrection) ; difficulté selon les vies. */
    public void resetHurry() {
        if (currentTrack == 2) playMusic(0); // Adventure.resetHurry
        huTimer = 0;
        huState = 0;
        int max = 0;
        for (Player p : players) max = Math.max(max, p.lives);
        diffFactor = max < 4 ? 1.0 : 1.0 + 0.05 * (max - 3);
        if (fl_nightmare) diffFactor *= 1.4;
        destroyList(HU_BAD);
        for (Entity e : getList(BAD)) ((hammerfest.sim.entity.Bad) e).calmDown();
        Entity boss = getOne(BOSS);
        if (boss instanceof hammerfest.sim.entity.boss.Bat) ((hammerfest.sim.entity.boss.Bat) boss).onPlayerDeath();
    }

    /** GameMode.clearLevel : retire tout sauf les joueurs avant de changer de niveau. */
    private void clearLevel() {
        itemNameId = -1; // GameMode.clearLevel : killItemName
        for (Entity e : new ArrayList<>(getList(ENTITY))) {
            if (!(e instanceof Player)) e.destroy();
        }
        cleanKills();
    }

    /** GameMode.getOne : une entité du type AU HASARD (l[Std.random(l.length)]), null si aucune. */
    public Entity getOne(int type) {
        List<Entity> l = getList(type);
        return l.isEmpty() ? null : l.get(random(l.size()));
    }

    /** Nombre d'ennemis touchés par la même bombe (pour la valeur des cristaux de combo). */
    public int countCombo(int id) {
        return comboList.merge(id, 1, Integer::sum);
    }

    /** Ordre d'apparition des ennemis restant à tuer pour le niveau parfait (Adventure.perfectOrder). */
    public final List<Integer> perfectOrder = new ArrayList<>();
    public int perfectCount;
    /** Ennemis insérés dans le niveau (GameMode.badCount, remis à 0 par initLevel). */
    public int badCount;

    /** Adventure.attachBad : mémorise l'ordre d'apparition pour le super-objet. */
    public void onAttachBad(hammerfest.sim.entity.Bad b) {
        if (b.isType(BAD_CLEAR)) {
            badCount++;
            perfectOrder.add(b.uniqId);
            perfectCount++;
        }
    }

    /** Ennemi retiré aussitôt inséré (e_bad « sys » d'un niveau déjà visité). */
    public void forgetBad(hammerfest.sim.entity.Bad b) {
        if (perfectOrder.remove((Integer) b.uniqId)) {
            perfectCount--;
            badCount--;
        }
    }

    // ------------------------------------------------------------------ niveaux visités (GameMechanics)

    /** Niveaux déjà parcourus, clé dimension * 10000 + niveau. */
    private final java.util.Set<Integer> visited = new java.util.HashSet<>();
    /** Scripts restants des niveaux quittés par un portail (GameMechanics.suspend → current.$script). */
    private final Map<Integer, String> savedScripts = new HashMap<>();
    private int currentKey = -1;

    // ------------------------------------------------------------------ Tubercourse (mode.BossRush)

    /** Tubercourse : l'Aventure chronométrée avec un joueur renforcé. */
    public boolean fl_bossRush;

    /** GameMode.fl_disguise : déguisements permis, sauf Tubercourse, coopératif et SoccerFest. */
    public boolean disguiseAllowed() {
        return !fl_bossRush && !fl_soccer && !(fl_multi && !fl_timeAttack);
    }

    /**
     * GameMode (touche D) : tête suivante du joueur 1, en sautant celles dont la famille n'est pas débloquée
     * (afro 109, Cerbère 110, Piou 111, Mario 112, cape de Tuberculoz 113) ; après la 6, tête par défaut.
     */
    public void disguise() {
        if (!disguiseAllowed() || players.isEmpty()) return;
        Player p = players.get(0);
        p.head++;
        if (p.head == HEAD_AFRO && !hasFamily(109)) p.head++;
        if (p.head == HEAD_CERBERE && !hasFamily(110)) p.head++;
        if (p.head == HEAD_PIOU && !hasFamily(111)) p.head++;
        if (p.head == HEAD_MARIO && !hasFamily(112)) p.head++;
        if (p.head == HEAD_TUB && !hasFamily(113)) p.head++;
        if (p.head > 6) p.head = p.defaultHead;
    }
    /** Temps moyen par niveau (ms), lissé comme dans l'original. */
    public double levelAvg;
    private long levelStartMs;

    public void setBossRush() {
        fl_bossRush = true;
        fl_mirror = false;
        fl_nightmare = false;
        fl_chronoStop = false;
    }

    /** BossRush.upgradePlayer : vitesse ×1,5, 2 bombes, coup de pied vers le haut. */
    public void upgradePlayer(Player p) {
        if (!fl_bossRush) return;
        p.speedFactor = 1.5;
        p.maxBombs = 2;
        p.initialMaxBombs = 2;
        p.ctrl.fl_upKick = true;
    }

    /** BossRush.registerLevelTime : temps du niveau qu'on quitte, et moyenne. */
    private void registerLevelTime() {
        long t = chronoMs() - levelStartMs;
        times.add(t);
        levelAvg = levelAvg == 0 ? t : 0.5 * (levelAvg + t);
    }

    // ------------------------------------------------------------------ Apprentissage (mode.Tutorial)

    /** Mode Apprentissage : vies illimitées, ni hurry up ni obscurité, ni objets de niveau ni super-objet. */
    public boolean fl_tutorial;

    public void setTutorial() {
        fl_tutorial = true;
    }

    // ------------------------------------------------------------------ SoccerFest (mode.Soccer)

    static final double MATCH_DURATION = 5 * 60 * SECOND, GOAL_OFFTIME = 5 * SECOND, START_OFFTIME = 3 * SECOND,
            SOCCER_END_TIMER = 13 * SECOND, BOOST_DISTANCE = 180, MAX_BOOST = 1.5;

    public boolean fl_soccer;
    /** Buts de chaque équipe (0 : Igor, 1 : Sandy). */
    public final int[] soccerScores = new int[2];
    public hammerfest.sim.entity.SoccerBall ball;
    /** Temps de match écoulé (cycles) ; le match dure 5 minutes. */
    public double matchChrono;
    public boolean fl_match, fl_matchEnd;
    double offPlayTimer, finalTimer;
    /** Équipe gagnante, -1 en cas de match nul. */
    public int winners = -1;

    /** Mode SoccerFest (set soccer : le niveau de départ est le terrain). */
    public void setSoccer() {
        fl_soccer = true;
        fl_nightmare = false;
    }

    /** Équipe du joueur : première moitié des joueurs à gauche (Igor), seconde à droite (Sandy). */
    public int team(Player p) {
        return p.pid < players.size() / 2.0 ? 0 : 1;
    }

    private int teamPos(Player p) {
        int pos = 0;
        for (Player o : players) if (team(o) == team(p) && o.pid < p.pid) pos++;
        return pos;
    }

    /** Soccer.initPlayer : tenue, pas de vies, pas d'arme (ou bombes répulsives), au coup d'envoi. */
    void soccerInitPlayer(Player p) {
        p.skin = p.pid + 3;
        if (p.skin == 4) {
            p.defaultHead = HEAD_SANDY;
            p.head = p.defaultHead;
        }
        p.baseWalkAnim = Anim.PLAYER_SOCCER;
        p.ctrl.fl_upKick = true;
        p.ctrl.fl_powerControl = hasOption("kickcontrol");
        p.lives = 0;
        p.dx = 0;
        p.dy = 0;
        if (p.fl_shield) p.unshield();
        int cx = team(p) == 0 ? world.data.playerX + teamPos(p) : LEVEL_WIDTH - world.data.playerX - 1 - teamPos(p);
        p.moveTo(Entity.xCtr(cx), Entity.yCtr(world.data.playerY));
        p.changeWeapon(hasOption("soccerbomb") ? WEAPON_B_REPEL : WEAPON_NONE);
    }

    /** Soccer.goal : l'équipe {@code tid} marque ; couronne pour le buteur, tête basse s'il a marqué contre son camp. */
    public void goal(int tid, hammerfest.sim.entity.SoccerBall b) {
        Player shooter = b.lastPlayer;
        soccerScores[tid]++;
        for (Player p : players) {
            p.head = p.defaultHead;
            p.speedFactor = 1;
            if (team(p) == tid) p.setBaseAnims(Anim.PLAYER_WALK_V, Anim.PLAYER_STOP_V);
            else p.setBaseAnims(Anim.PLAYER_SOCCER, Anim.PLAYER_STOP);
        }
        if (shooter != null && team(shooter) != tid) {
            sound("sound_player_death");
            alert("static.23", "");
            shooter.head = shooter.skin == 4 ? HEAD_SANDY_LOSE : HEAD_LOSE;
        } else {
            sound("sound_item_score");
            alert("static.22", " (" + soccerScores[0] + " - " + soccerScores[1] + ")");
            if (shooter != null) shooter.head = shooter.skin == 4 ? HEAD_SANDY_CROWN : HEAD_CROWN;
        }
        particlesDir(PARTICLE_CLASSIC_BOMB, b.x, b.y, 10, 0, -b.dx);
        fx("hammer_fx_shine", b.x, b.y - CASE_HEIGHT);
        ball = null;
        offPlayTimer = GOAL_OFFTIME;
    }

    private void insertBall() {
        List<LevelData.Slot> slots = world.data.specialSlots;
        LevelData.Slot s = slots.isEmpty() ? new LevelData.Slot(LEVEL_WIDTH / 2, 5) : slots.get(random(slots.size()));
        ball = hammerfest.sim.entity.SoccerBall.attach(this, Entity.xCtr(s.x), Entity.yCtr(s.y));
        particles(PARTICLE_CLASSIC_BOMB, ball.x, ball.y, 9, 0);
        particles(PARTICLE_ICE, ball.x, ball.y, 5, 0);
        for (Player p : players) p.setBaseAnims(Anim.PLAYER_SOCCER, Anim.PLAYER_STOP);
        sound("sound_pop");
    }

    private void endMatch() {
        if (ball != null) particles(PARTICLE_CLASSIC_BOMB, ball.x, ball.y, 10, 0);
        if (ball != null) ball.destroy();
        ball = null;
        alert("static.25", "");
        offPlayTimer = 99999;
        fl_matchEnd = true;
        winners = soccerScores[0] > soccerScores[1] ? 0 : soccerScores[0] < soccerScores[1] ? 1 : -1;
        for (Player p : players) {
            p.speedFactor = 1;
            p.setBaseAnims(Anim.PLAYER_SOCCER, Anim.PLAYER_STOP);
        }
    }

    private void showResults() {
        alert(winners == 0 ? "static.26" : winners == 1 ? "static.27" : "static.28", "");
        shake(SECOND, 5);
        for (Player p : players) {
            if (winners >= 0 && team(p) == winners) {
                p.speedFactor = 1;
                p.setBaseAnims(Anim.PLAYER_WALK_V, Anim.PLAYER_STOP_V);
            } else {
                p.speedFactor = 0.3;
                p.setBaseAnims(Anim.PLAYER_WALK_L, Anim.PLAYER_STOP_L);
            }
        }
    }

    private void updateSoccer() {
        huTimer = 0;
        if (fl_match && !fl_matchEnd) matchChrono += tmod;
        if (offPlayTimer > 0) offPlayTimer -= tmod;
        if (ball == null && offPlayTimer <= 0 && !fl_matchEnd) {
            if (!fl_match) alert("static.24", "");
            fl_match = true;
            insertBall();
        }
        if (ball != null && !ball.fl_destroy) {
            // les joueurs loin du ballon courent plus vite pour revenir dans le jeu
            for (Player p : players) {
                double dist = p.distance(ball.x, ball.y);
                p.speedFactor = dist > BOOST_DISTANCE ? Math.min(MAX_BOOST, dist / BOOST_DISTANCE) : 1;
            }
        } else if (ball != null) {
            ball = null; // ballon perdu (portail, bas de l'écran) : un nouveau est lancé
            offPlayTimer = GOAL_OFFTIME;
        }
        if (matchChrono >= MATCH_DURATION && !fl_matchEnd) {
            endMatch();
            finalTimer = SECOND * 3;
        }
        if (endModeTimer > 0 && winners >= 0) { // Soccer.main : étincelles autour des gagnants
            for (Player p : players) {
                if (team(p) != winners) continue;
                if (p.dx != 0) particlesDir(PARTICLE_SPARK, p.x, p.y - visualRandom(40), 0, 3, -p.dir);
                else particles(PARTICLE_SPARK, p.x, p.y - visualRandom(40), 0, 2);
            }
        }
        if (finalTimer > 0) {
            finalTimer -= tmod;
            if (finalTimer <= 0) {
                showResults();
                endModeTimer = SOCCER_END_TIMER;
            }
        }
    }

    /** Temps de match restant (ms). */
    public long matchRemainingMs() {
        return Math.max(0, Math.round((MATCH_DURATION - matchChrono) * 1000.0 / SECOND));
    }

    /** Message au centre de l'écran (FxManager.attachAlert) : texte de la langue {@code langKey} + suffixe. */
    public void alert(String langKey, String suffix) {
        alert("", langKey, suffix);
    }

    /** Variante avec un préfixe avant le texte (« 2 MINUTES »). */
    public void alert(String prefix, String langKey, String suffix) {
        events.add(new Event("alert:" + langKey + "|" + suffix + "|" + prefix, 0, 0, false));
    }

    // ------------------------------------------------------------------ Time Attack (mode.TimeAttack)

    /** Temps « infini » d'une partie perdue (59'59"999). */
    public static final long TA_INFINITY = 59 * 60 * 1000 + 59 * 1000 + 999;
    /** Secondes retirées du chrono par un cristal de rang sid (Data.getCrystalTime). */
    static final int[] CRYSTAL_TIME = {1, 3, 5, 7, 9, 10};

    public boolean fl_timeAttack;
    public int taFirstLevel;
    /** Chrono en cycles de jeu (32 par seconde), arrêté pendant le compte à rebours. */
    public double chrono;
    public boolean fl_chronoStop = true;
    /** Compte à rebours de départ (cycles). */
    public double starter;
    /** Temps de chaque niveau terminé (ms). */
    public final List<Long> times = new ArrayList<>();
    /** Temps total de la partie (ms) ; TA_INFINITY si elle est perdue. */
    public long totalTime;
    private final java.util.Set<Integer> alertedMinutes = new java.util.HashSet<>();

    /** Parcours de Time Attack {@code run} : niveaux run*9 et suivants, sans miroir ni cauchemar. */
    public void setTimeAttack(int run) {
        fl_timeAttack = true;
        taFirstLevel = run * 9;
        fl_mirror = false;
        fl_nightmare = false;
    }

    public long chronoMs() {
        return Math.round(chrono * 1000.0 / SECOND);
    }

    /** TimeAttack.formatTime : 01" 23' 456. */
    public static String formatTime(long ms) {
        long m = ms / 60000, s = (ms / 1000) % 60, r = ms % 1000;
        return String.format("%02d\" %02d' %03d", m, s, r);
    }

    /** Un cristal ramassé fait gagner du temps (TimeAttack.pickUpScore / timeShift). */
    public int crystalTimeBonus(int sid) {
        int bonus = CRYSTAL_TIME[Math.max(0, Math.min(sid, CRYSTAL_TIME.length - 1))];
        chrono = Math.max(0, chrono - bonus * SECOND);
        return bonus;
    }

    /**
     * SetManager.isEmptyLevel : niveau absent ou aux valeurs par défaut (départ 0,0, fond et dalles 1, ni ennemi ni
     * emplacement d'objet). Sépare les dimensions rangées bout à bout dans un même set, et marque la fin d'un parcours.
     */
    public boolean isEmptyLevel(int id) {
        if (id >= set.levels.size()) return true;
        LevelData l = set.level(id);
        return l.badList.isEmpty() && l.specialSlots.isEmpty() && l.scoreSlots.isEmpty()
                && l.playerX == 0 && l.playerY == 0 && l.skinBg == 1 && l.skinTiles == 1;
    }

    private void updateTimeAttack() {
        huTimer = 0;
        if (starter > 0) {
            starter -= tmod;
            if (starter <= 0) {
                for (Player p : players) p.fl_lockControls = false;
                chrono = 0;
                fl_chronoStop = false;
                alert("static.20", ""); // « TIME ATTACK ! »
            }
        }
        if (!fl_chronoStop) {
            long before = chronoMs() / 60000;
            chrono += tmod;
            long m = chronoMs() / 60000;
            // TimeAttack.main : alerte à chaque nouvelle minute (une seule fois)
            if (m != before && m > 0 && alertedMinutes.add((int) m)) alert(m + " ", m > 1 ? "static.19" : "static.18", "");
        }
    }

    /** TimeAttack.goto : temps du niveau terminé ; renvoie false si le parcours est fini. */
    private boolean timeAttackGoTo(int id) {
        long lt = chronoMs();
        for (long t : times) lt -= t;
        times.add(lt);
        if (isEmptyLevel(id)) { // fin du parcours : partie gagnée
            fl_victory = true;
            onGameOver();
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ fonds d'ambiance (FxManager.attachBg)

    /** File des fonds : {id, sous-image (-1 = animée), durée restante}. Le premier est affiché. */
    private final List<double[]> bgList = new ArrayList<>();
    /** Fond affiché à la place du fond du niveau : {id, sous-image ou -1}, ou null. */
    public int[] specialBg;

    public void attachBg(int id, int subId, double timer) {
        bgList.add(new double[]{id, subId, timer});
    }

    public void clearBg() {
        bgList.clear();
        specialBg = null;
    }

    private void updateBg() {
        if (bgList.isEmpty()) return;
        double[] b = bgList.get(0);
        if (specialBg == null) specialBg = new int[]{(int) b[0], (int) b[1]};
        b[2] -= tmod;
        if (b[2] <= 0) {
            bgList.remove(0);
            specialBg = null;
        }
    }

    // ------------------------------------------------------------------ option Miroir (SetManager.flip)

    /** GameMode.flipCoordReal : x retourné si l'option Miroir est active. */
    public double flipCoordReal(double x) {
        return fl_mirror ? GAME_WIDTH - x - 1 : x;
    }

    /** GameMode.flipCoordCase. */
    public int flipCoordCase(int cx) {
        return fl_mirror ? LEVEL_WIDTH - cx - 1 : cx;
    }

    /**
     * Données du niveau, grille et départ du joueur retournés horizontalement en mode Miroir
     * (SetManager.flip) ; ennemis, emplacements et script gardent leurs coordonnées, retournées à l'usage.
     */
    LevelData levelData(int id) {
        LevelData l = set.level(id);
        if (!fl_mirror) return l;
        LevelData f = hammerfest.json.Jsons.read(hammerfest.json.Jsons.write(l), LevelData.class);
        int[][] g = l.grid();
        int[][] out = new int[LEVEL_WIDTH][LEVEL_HEIGHT];
        for (int x = 0; x < LEVEL_WIDTH; x++) out[x] = g[LEVEL_WIDTH - x - 1].clone();
        f.setGrid(out);
        f.playerX = LEVEL_WIDTH - l.playerX - 1;
        return f;
    }

    /** SetManager.flipPortals : ordre des portails inversé dans chaque rangée, pour garder les mêmes numéros. */
    private void mirrorPortals() {
        if (!fl_mirror) return;
        java.util.TreeMap<Integer, List<int[]>> rows = new java.util.TreeMap<>();
        for (int[] p : world.portalList) rows.computeIfAbsent(p[1], k -> new ArrayList<>()).add(p);
        world.portalList.clear();
        for (List<int[]> row : rows.values()) {
            for (int i = row.size() - 1; i >= 0; i--) world.portalList.add(row.get(i));
        }
    }

    // ------------------------------------------------------------------ carte (GameMode.registerMapEvent)

    /** Événement de la carte : niveau du puits, type (EVENT_*), texte (« 12. {level.1.0} » : clé de langue entre accolades). */
    public static final class MapEvent {
        public final int lid, eid;
        public final String misc;

        MapEvent(int lid, int eid, String misc) {
            this.lid = lid;
            this.eid = eid;
            this.misc = misc;
        }
    }

    /** Sorties et retours de portails (mapTravels) et autres événements (mapEvents : morts, EXTEND). */
    public final List<MapEvent> mapTravels = new ArrayList<>(), mapEvents = new ArrayList<>();

    /** Niveau du puits (dimension 0) : courant, ou celui d'où l'on est parti vers une autre dimension. */
    public int mainLevelId() {
        return isMainWorld() ? levelId : dimLevels.getOrDefault(0, 0);
    }

    public void registerMapEvent(int eid, String misc) {
        if (!isMainWorld()) return;
        int lid = levelId;
        // GameMode.registerMapEvent filtre allers-retours et sorties répétées en parcourant mapEvents… où ne sont
        // jamais rangées les sorties (elles vont dans mapTravels) : le filtre ne retire rien, sortie ET retour au même
        // niveau sont tous deux affichés sur la carte. Reproduit tel quel.
        for (MapEvent e : mapEvents) {
            if (e.lid != lid) continue;
            if (e.eid == EVENT_EXIT_RIGHT && (eid == EVENT_BACK_RIGHT || eid == EVENT_EXIT_RIGHT)) return;
            if (e.eid == EVENT_EXIT_LEFT && (eid == EVENT_BACK_LEFT || eid == EVENT_EXIT_LEFT)) return;
        }
        MapEvent e = new MapEvent(lid, eid, misc);
        if (eid == EVENT_EXIT_LEFT || eid == EVENT_EXIT_RIGHT || eid == EVENT_BACK_LEFT || eid == EVENT_BACK_RIGHT || eid == EVENT_TIME) {
            mapTravels.add(e);
        } else {
            mapEvents.add(e);
        }
    }

    public boolean isVisited() {
        return visited.contains(currentKey);
    }

    /** GameMode.fl_badsCallback : désignation ninja faite pour ce niveau. */
    private boolean fl_badsCallback;

    /**
     * GameMode.onBadsReady (option Ninjutsu) : une cible au hasard, et des amis à épargner (1, ou plus en
     * profondeur, hors du puits et en Cauchemar), seulement s'il y a au moins deux ennemis.
     */
    void onBadsReady() {
        List<Entity> bads = getList(BAD_CLEAR);
        if (!fl_ninja || bads.size() <= 1) return;
        hammerfest.sim.entity.Bad foe = (hammerfest.sim.entity.Bad) bads.get(0);
        foe.fl_ninFoe = true;
        int limit;
        if (fl_nightmare || !isMainWorld() || levelId >= 20) {
            int lid = isMainWorld() ? levelId : dimLevels.getOrDefault(0, 0);
            limit = (int) Math.max(2, Math.floor((lid - 20) / 10.0));
        } else {
            limit = 1;
        }
        if (fl_nightmare) limit++;
        limit = Math.min(bads.size() - 1, limit);
        while (limit > 0) {
            hammerfest.sim.entity.Bad b = (hammerfest.sim.entity.Bad) bads.get(random(bads.size()));
            if (b != foe && !b.fl_ninFriend) {
                b.fl_ninFriend = true;
                limit--;
            }
        }
    }

    public void onKillBad(hammerfest.sim.entity.Bad b) {
        badsKilled++;
        if (badCount > 1 && !perfectOrder.isEmpty() && b.uniqId == perfectOrder.get(0)) perfectOrder.remove(0);
        Entity boss = getOne(BOSS);
        if (boss instanceof hammerfest.sim.entity.boss.Tuberculoz) ((hammerfest.sim.entity.boss.Tuberculoz) boss).onKillBad();
    }

    public LevelData level() {
        return world.data;
    }

    public void onGameOver() {
        if (fl_gameOver) return;
        fl_gameOver = true;
        if (fl_timeAttack) { // TimeAttack.onGameOver / onEndOfSet
            long t = 0;
            for (long x : times) t += x;
            totalTime = fl_victory ? t : TA_INFINITY;
        }
    }

    // ------------------------------------------------------------------ listes

    public int getUniqId() {
        return ++uniq;
    }

    public void addToList(int type, Entity e) {
        lists.computeIfAbsent(type, t -> new ArrayList<>()).add(e);
    }

    public void removeFromList(int type, Entity e) {
        List<Entity> l = lists.get(type);
        if (l != null) l.remove(e);
    }

    public List<Entity> getList(int type) {
        return lists.getOrDefault(type, Collections.emptyList());
    }

    public int countList(int type) {
        return getList(type).size();
    }

    public List<Player> players() {
        return Collections.unmodifiableList(players);
    }

    public void unregLater(int type, Entity e) {
        unregList.add(new Object[]{type, e});
    }

    public void killLater(Entity e) {
        killList.add(e);
    }

    void cleanKills() {
        for (Object[] u : unregList) removeFromList((Integer) u[0], (Entity) u[1]);
        unregList.clear();
        for (Entity e : killList) {
            if (e instanceof Player) players.remove(e);
        }
        killList.clear();
    }

    // ------------------------------------------------------------------ services

    /** Std.random(n) : entier dans [0, n). */
    public int random(int n) {
        return n <= 0 ? 0 : rng.nextInt(n);
    }

    public boolean hasFamily(int id) {
        return families.contains(id);
    }

    public boolean hasOption(String id) {
        return options.contains(id);
    }

    /**
     * FxManager.attachExplosion : clip « explodeZone » de diamètre 2 × {@code radius}, en mode de fusion overlay
     * (simple éclair lumineux).
     */
    public void explosion(double x, double y, double radius) {
        fx("explodeZone|" + Math.round(radius) + "|1", x, y);
    }

    /** FxManager.attachExplodeZone : clip « explodeZone » de diamètre 2 × {@code radius}, en mode normal. */
    public void explodeZone(double x, double y, double radius) {
        explodeZone(x, y, radius, 100);
    }

    /** Variante avec _alpha (pourcentage) : onde de choc de l'ananas à 20 %. */
    public void explodeZone(double x, double y, double radius, double alpha) {
        fx("explodeZone|" + Math.round(radius) + "|0|" + Math.round(alpha), x, y);
    }

    public void fx(String name, double x, double y) {
        events.add(new Event(name, x, y, false));
    }

    /**
     * attachFx suivi de retouches du clip : _rotation (degrés), _xscale et _yscale (pourcentages, négatif =
     * retourné), _alpha (pourcentage).
     */
    public void fx(String name, double x, double y, double rotation, double xscale, double yscale, double alpha) {
        fx("tf|" + name + "|" + Math.round(rotation) + "|" + Math.round(xscale) + "|" + Math.round(yscale) + "|"
                + Math.round(alpha), x, y);
    }

    /** FxManager.attachShine : clip « shine » à 150 %, retourné au hasard. */
    public void shine(double x, double y) {
        fx("shine", x, y, 0, 150 * (visualRng.nextInt(2) * 2 - 1), 150, 100);
    }

    /**
     * attachShine dont l'échelle est ensuite fixée à {@code scale} % (champs d'armes : 65 %) : _xscale réécrit,
     * donc plus retourné.
     */
    public void shine(double x, double y, double scale) {
        fx("shine", x, y, 0, scale, scale, 100);
    }

    /**
     * FxManager.dust : 7 grains de poussière tombant de la dalle (cx, cy), sur sa largeur élargie aux dalles
     * voisines (tirés au rendu).
     */
    public void dust(int cx, int cy) {
        double x = Entity.xCtr(cx), y = Entity.yCtr(cy);
        double xMin = x - CASE_WIDTH * 0.5, xMax = x + CASE_WIDTH * 0.5;
        if (world.getCase(cx - 1, cy) == GROUND) xMin -= CASE_WIDTH;
        if (world.getCase(cx + 1, cy) == GROUND) xMax += CASE_WIDTH;
        fx("dust:" + Math.round(xMax - xMin), xMin, y);
    }

    /** Hasard des seules retouches visuelles (échelles, rotations) : ne décale pas celui de la simulation. */
    public int visualRandom(int n) {
        return n <= 0 ? 0 : visualRng.nextInt(n);
    }

    /** Flèche « niveau suivant » en bas de l'écran (FxManager.attachExit / detachExit). */
    public boolean exitArrow;

    public void attachExit() {
        exitArrow = true;
    }

    public void detachExit() {
        exitArrow = false;
    }

    /** Pointeur du tutoriel (GameMode.attachPointer) : x, y, rotation ; null = aucun. */
    public double[] pointer;

    /**
     * Objet dont le nom est affiché en haut au centre (GameMode.attachItemName : objet à effet, ou objet à points
     * de rareté > 0), id d'objet (score : 1000 + id), -1 = aucun ; effacé au niveau suivant (clearLevel).
     */
    public int itemNameId = -1;

    public void attachItemName(int id) {
        itemNameId = id;
    }

    /**
     * Message en jeu (FxManager.keyRequired / keyUsed : hammer_interf_inGameMsg) : libellé Lang 40 ou 41 et nom de
     * la clé, 2 ou 3 s.
     */
    public void keyMessage(boolean used, int key) {
        fx("keyMsg:" + (used ? "static.41" : "static.40") + "|key." + key + "|" + (used ? 3 : 2), 0, 0);
    }

    /** Affichage des points gagnés (FxManager.attachScorePop) : nom "score:<valeur>". */
    /**
     * FxManager.inGameParticles : base + random(rand) particules décoratives (rendu seulement, le hasard
     * de leur nombre et de leur trajectoire n'est pas tiré dans celui de la simulation).
     */
    public void particles(int pid, double x, double y, int base, int rand) {
        particlesDir(pid, x, y, base, rand, Double.NaN);
    }

    /** FxManager.inGameParticlesDir : toutes projetées du côté de {@code dir} (signe), sinon alternées. */
    public void particlesDir(int pid, double x, double y, int base, int rand, double dir) {
        events.add(new Event("part:" + pid + "|" + base + "|" + rand + "|" + dir, x, y, false));
    }

    public void scorePop(double x, double y, int value) {
        events.add(new Event("score:" + value, x, y, false));
    }

    /** Bonus de temps affiché au-dessus du joueur (Time Attack : « -3 sec »). */
    public void timePop(double x, double y, int seconds) {
        events.add(new Event("time:" + seconds, x, y, false));
    }

    public void sound(String name) {
        events.add(new Event(name, 0, 0, true));
    }

    /** Événements produits depuis le dernier appel (vidés). */
    public List<Event> drainEvents() {
        List<Event> out = new ArrayList<>(events);
        events.clear();
        return out;
    }
}
