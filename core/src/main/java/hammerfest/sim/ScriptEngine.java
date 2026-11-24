package hammerfest.sim;

import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Item;
import hammerfest.sim.entity.Player;
import hammerfest.sim.entity.ScoreItem;
import hammerfest.sim.entity.SpecialItem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code levels/ScriptEngine.mt} : script XML du niveau, fait de déclencheurs
 * (t_timer, t_pos, exp, enter, end, attach, do, birth, death…) contenant des actions
 * (e_bad, e_score, e_spec, e_mc, e_add/e_rem, e_msg, e_hide, e_goto…).
 *
 * <p>Non porté : portails et changement de dimension (e_portal, e_openPortal), obscurité
 * (e_darkness), musique scriptée (e_music), codes 3/4 (ascenseur de fin).</p>
 */
public class ScriptEngine {

    /** Décor attaché par un script (e_mc) : clip Flash posé dans le niveau. */
    public static final class Decor {
        public final int sid;
        public final String linkage;
        public final double x, y;
        /** Derrière les entités (_sprite_back) ou devant (_sprite_top). */
        public final boolean back;
        public boolean playing;
        public int frame = 1;
        /** Retourné horizontalement (option Miroir : mc._xscale *= -1). */
        public boolean mirrored;
        /** Tête du joueur affichée dans le clip (ascenseur de fin : mc.head). */
        public int head;

        Decor(int sid, String linkage, double x, double y, boolean back, boolean playing) {
            this.sid = sid;
            this.linkage = linkage;
            this.x = x;
            this.y = y;
            this.back = back;
            this.playing = playing;
        }
    }

    final Game game;
    private Element script;
    public double cycle;
    boolean fl_birth, fl_death, fl_safe, fl_onAttach = true;
    private final List<double[]> recentExp = new ArrayList<>();
    private final List<int[]> entries = new ArrayList<>();
    public final List<Decor> decors = new ArrayList<>();
    /** Fin du jeu : la prison des fruits est ouverte, l'ascenseur attend le joueur. */
    boolean fl_elevatorOpen;
    private double bossDoorTimer = SECOND * 1.2;

    public ScriptEngine(Game game, String baseScript) {
        this.game = game;
        this.script = parse(baseScript);
    }

    static Element parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader("<root>" + (xml == null ? "" : xml) + "</root>")));
            return d.getDocumentElement();
        } catch (Exception e) {
            System.err.println("Script de niveau illisible : " + e.getMessage());
            try {
                return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().createElement("root");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    /** Script restant (déclencheurs non encore consommés), pour GameMechanics.suspend. */
    public String save() {
        try {
            javax.xml.transform.Transformer t = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringBuilder out = new StringBuilder();
            for (org.w3c.dom.Node n = script.getFirstChild(); n != null; n = n.getNextSibling()) {
                java.io.StringWriter w = new java.io.StringWriter();
                t.transform(new javax.xml.transform.dom.DOMSource(n), new javax.xml.transform.stream.StreamResult(w));
                out.append(w);
            }
            return out.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ événements de jeu

    public void onPlayerBirth() {
        fl_birth = true;
    }

    public void onPlayerDeath() {
        fl_death = true;
    }

    public void onExplode(double x, double y, double radius) {
        recentExp.add(new double[]{x, y, radius});
    }

    public void onEnterCase(int cx, int cy) {
        entries.add(new int[]{cx, cy});
    }

    public void safeMode() {
        fl_safe = true;
    }

    // ------------------------------------------------------------------ script

    /** Ajoute du script (ex. insertion d'objets) à la fin du script courant. */
    public void addScript(String xml) {
        Element extra = parse(xml);
        for (Node n = extra.getFirstChild(); n != null; ) {
            Node next = n.getNextSibling();
            script.appendChild(script.getOwnerDocument().importNode(n, true));
            n = next;
        }
    }

    static Integer getInt(Element e, String name) {
        return parseLeadingInt(e.getAttribute(name));
    }

    /**
     * Std.parseInt de Flash : nombre en tête de chaîne, le reste est ignoré (« 18² » du niveau 15 de l'aventure
     * vaut 18) ; null s'il n'y a pas de chiffre au début.
     */
    static Integer parseLeadingInt(String v) {
        if (v == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*([-+]?\\d+(?:\\.\\d*)?)").matcher(v);
        if (!m.find()) return null;
        return (int) Math.floor(Double.parseDouble(m.group(1)));
    }

    static int getInt(Element e, String name, int def) {
        Integer v = getInt(e, name);
        return v == null ? def : v;
    }

    /** Un cycle de script (ScriptEngine.update / runScript). */
    private boolean firstTorch;

    public void update() {
        cycle += game.tmod;
        runScript();
        firstTorch = false;
    }

    /** ScriptEngine.VERBOSE_TRIGGERS : déclencheurs qui annoncent la clé manquante. */
    static final java.util.List<String> VERBOSE_TRIGGERS = java.util.List.of("t_pos", "exp", "enter");

    void runScript() {
        for (Node n = script.getFirstChild(); n != null; ) {
            Node next = n.getNextSibling();
            if (n instanceof Element && n.getParentNode() == script) {
                Element trigger = (Element) n;
                if (checkTrigger(trigger)) {
                    Integer key = getInt(trigger, "key");
                    if (key == null || game.hasKey(key)) {
                        if (key != null) game.keyMessage(true, key); // FxManager.keyUsed
                        executeTrigger(trigger);
                    } else if (VERBOSE_TRIGGERS.contains(trigger.getTagName())) {
                        game.keyMessage(false, key); // FxManager.keyRequired
                    }
                }
            }
            n = next;
        }
        fl_birth = false;
        fl_death = false;
        fl_onAttach = false;
        recentExp.clear();
        entries.clear();
    }

    boolean checkTrigger(Element t) {
        switch (t.getTagName()) {
            case "t_timer":
                return cycle >= getInt(t, "t", 0);
            case "t_pos": {
                int x = game.flipCoordCase(getInt(t, "x", 0)), y = getInt(t, "y", 0), dist = getInt(t, "d", 0);
                for (Player p : game.players()) {
                    if (!p.fl_kill && !p.fl_destroy && p.distanceCase(x, y) <= dist) return true;
                }
                return false;
            }
            case "attach":
                return fl_onAttach;
            case "do":
                return true;
            case "end":
                return game.fl_clear && cycle > 10;
            case "birth":
                return fl_birth;
            case "death":
                return fl_death;
            case "exp": {
                double x = game.flipCoordReal(Entity.xCtr(getInt(t, "x", 0))), y = Entity.yCtr(getInt(t, "y", 0));
                for (double[] e : recentExp) {
                    double sq = (x - e[0]) * (x - e[0]) + (y - e[1]) * (y - e[1]);
                    if (sq <= e[2] * e[2]) return true;
                }
                return false;
            }
            case "enter": {
                int cx = game.flipCoordCase(getInt(t, "x", 0)), cy = getInt(t, "y", 0);
                for (int[] e : entries) if (e[0] == cx && e[1] == cy) return true;
                return false;
            }
            case "night":
                return game.fl_nightmare;
            case "mirror":
                return game.fl_mirror;
            case "multi":
                return game.players().size() > 1;
            case "ninja":
                return game.fl_ninja;
            default:
                return false;
        }
    }

    void executeTrigger(Element trigger) {
        List<Element> events = new ArrayList<>();
        for (Node n = trigger.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) events.add((Element) n);
        }
        for (Element e : events) executeEvent(e);

        Integer total = getInt(trigger, "repeat");
        if (total != null) {
            total--;
            trigger.setAttribute("repeat", String.valueOf(total));
            if (total == 0) {
                remove(trigger);
            } else if ("t_timer".equals(trigger.getTagName())) {
                String base = trigger.getAttribute("base");
                if (base.isEmpty()) {
                    base = trigger.getAttribute("t");
                    trigger.setAttribute("base", base);
                }
                trigger.setAttribute("t", String.valueOf((int) (Double.parseDouble(base) + cycle)));
            }
        } else {
            remove(trigger);
        }
    }

    private void remove(Element e) {
        if (e.getParentNode() != null) e.getParentNode().removeChild(e);
    }

    static boolean isTrigger(String n) {
        switch (n) {
            case "t_timer": case "t_pos": case "attach": case "do": case "end": case "birth": case "death":
            case "exp": case "enter": case "night": case "mirror": case "multi": case "ninja":
                return true;
            default:
                return false;
        }
    }

    void executeEvent(Element ev) {
        switch (ev.getTagName()) {
            case "e_score": {
                Item it = ScoreItem.attach(game, game.flipCoordReal(Entity.xCtr(getInt(ev, "x", 0))), Entity.yCtr(getInt(ev, "y", 0)),
                        getInt(ev, "i", 0), getInt(ev, "si", -1));
                if (getInt(ev, "inf", 0) == 1) it.lifeTimer = -1;
                tagScript(ev, it);
                break;
            }
            case "e_spec": {
                if (game.canAddItem() && !fl_safe) {
                    Item it = SpecialItem.attach(game, game.flipCoordReal(Entity.xCtr(getInt(ev, "x", 0))), Entity.yCtr(getInt(ev, "y", 0)),
                            getInt(ev, "i", 0), getInt(ev, "si", -1));
                    if (it != null) {
                        if (getInt(ev, "inf", 0) == 1) it.lifeTimer = -1;
                        tagScript(ev, it);
                    }
                }
                break;
            }
            case "e_ext": // lettre EXTEND au hasard
                if (game.canAddItem() && !fl_safe && !game.fl_clear) {
                    int[] pt = game.world.getGround(game.random(LEVEL_WIDTH), game.random(LEVEL_HEIGHT));
                    int letter = game.extendList.isEmpty() ? game.random(EXTENDS) : game.extendList.get(game.random(game.extendList.size()));
                    SpecialItem.attach(game, Entity.xCtr(pt[0]), Entity.yCtr(pt[1]), 0, letter);
                }
                break;
            case "e_bad":
                if (!fl_safe) {
                    Entity b = hammerfest.sim.entity.bad.Fruits.attach(game, getInt(ev, "i", 0),
                            Entity.xCtr(game.flipCoordCase(getInt(ev, "x", 0))) - CASE_WIDTH * 0.5, Entity.yCtr(getInt(ev, "y", 0) - 1));
                    if ((b.types & BAD_CLEAR) > 0) {
                        Integer sys = getInt(ev, "sys");
                        if (sys != null && sys != 0 && game.isVisited()) { // ennemi « système » déjà vaincu
                            game.forgetBad((hammerfest.sim.entity.Bad) b);
                            b.destroy();
                            break;
                        }
                        game.fl_clear = false;
                    }
                    tagScript(ev, b);
                }
                break;
            case "e_kill":
                killById(getInt(ev, "sid"));
                break;
            case "e_tuto":
            case "e_msg": {
                Integer id = getInt(ev, "id");
                String inline = ev.getTextContent();
                game.message(id != null ? "static." + id : null, id == null ? inline : null,
                        ev.getTagName().equals("e_tuto"));
                break;
            }
            case "e_killMsg":
                game.message(null, null);
                break;
            case "e_pointer":
                { // GameMode.attachPointer : flèche sur la case, pointe tournée vers le joueur
                    double px = Entity.xCtr(game.flipCoordCase(getInt(ev, "x", 0))), py = Entity.yCtr(getInt(ev, "y", 0));
                    Entity p = game.getOne(PLAYER);
                    double ang = p == null ? 0 : Math.atan2(Entity.yCtr(p.cy) - py, Entity.xCtr(p.cx) - px) * 180 / Math.PI;
                    game.pointer = new double[]{px, py, ang - 90};
                }
                break;
            case "e_killPt":
                game.pointer = null;
                break;
            case "e_mc": {
                Integer sid = getInt(ev, "sid");
                killById(sid);
                Integer xr = getInt(ev, "xr");
                double x = game.flipCoordReal(xr != null ? xr : Entity.xCtr(getInt(ev, "x", 0)));
                if (game.fl_mirror) x += CASE_WIDTH;
                double y = xr != null ? getInt(ev, "yr", 0) : Entity.yCtr(getInt(ev, "y", 0));
                Decor decor = new Decor(sid == null ? -1 : sid, ev.getAttribute("n"), x, y,
                        getInt(ev, "back", 0) == 1, getInt(ev, "p", 0) > 0);
                decor.mirrored = game.fl_mirror;
                decors.add(decor);
                if ("torch".equals(ev.getAttribute("n"))) { // torche : halo de lumière
                    if (!firstTorch) game.clearExtraHoles();
                    game.addHole(x + CASE_WIDTH * 0.5, y - CASE_HEIGHT * 0.5, 180);
                    game.updateDarkness();
                    firstTorch = true;
                }
                break;
            }
            case "e_pmc":
                playById(getInt(ev, "sid"));
                break;
            case "e_add":
            case "e_rem": {
                int cx1 = game.flipCoordCase(getInt(ev, "x1", 0)), cy1 = getInt(ev, "y1", 0);
                int cx2 = game.flipCoordCase(getInt(ev, "x2", 0)), cy2 = getInt(ev, "y2", 0);
                int id = 0;
                if (ev.getTagName().equals("e_add")) {
                    int type = getInt(ev, "type", 0);
                    id = type > 0 ? -type : GROUND;
                }
                while (cx1 != cx2 || cy1 != cy2) {
                    game.world.forceCase(cx1, cy1, id);
                    if (cx1 < cx2) cx1++;
                    if (cx1 > cx2) cx1--;
                    if (cy1 < cy2) cy1++;
                    if (cy1 > cy2) cy1--;
                }
                game.world.forceCase(cx1, cy1, id);
                break;
            }
            case "e_itemLine": {
                int cx1 = getInt(ev, "x1", 0), cx2 = getInt(ev, "x2", 0), cy = getInt(ev, "y", 0);
                int id = getInt(ev, "i", 0), sid = getInt(ev, "si", -1), time = getInt(ev, "t", 0);
                for (int i = 0; ; i++) {
                    addScript("<t_timer t=\"" + (int) (cycle + i * time) + "\"><e_score i=\"" + id + "\" si=\""
                            + (sid < 0 ? "" : sid) + "\" x=\"" + cx1 + "\" y=\"" + cy + "\" inf=\"1\"/></t_timer>");
                    if (cx1 == cx2) break;
                    cx1 += cx1 < cx2 ? 1 : -1;
                }
                break;
            }
            case "e_goto":
                game.forcedGoto(getInt(ev, "id", game.levelId));
                break;
            case "e_hide":
                game.world.hideTiles = getInt(ev, "tiles", 0) == 1;
                game.world.hideBorders = getInt(ev, "borders", 0) == 1;
                break;
            case "e_hideBorders":
                game.world.hideTiles = true;
                break;
            case "e_ctrigger":
                codeTrigger(getInt(ev, "id", -1));
                break;
            case "e_setVar":
                game.setDynamicVar(ev.getAttribute("var"), ev.getAttribute("value"));
                break;
            case "e_fakelid":
                game.fakeLevelId = getInt(ev, "lid");
                break;
            case "e_portal":
                if (game.fl_clear && cycle > 10) game.usePortal(getInt(ev, "pid", 0), null);
                break;
            case "e_openPortal":
                game.openPortal(getInt(ev, "x", 0), getInt(ev, "y", 0), getInt(ev, "pid", 0));
                break;
            case "e_darkness":
                game.forcedDarkness = getInt(ev, "v");
                game.updateDarkness();
                break;
            case "e_music":
                game.playMusic(getInt(ev, "id", 0));
                break;
            default:
                if (isTrigger(ev.getTagName())) script.appendChild(ev.cloneNode(true));
                break;
        }
    }

    private void tagScript(Element ev, Entity e) {
        Integer sid = getInt(ev, "sid");
        if (sid == null) return;
        killById(sid);
        e.scriptId = sid;
    }

    /** Annule les déclencheurs « endClear » (fin de niveau). */
    public void clearEndTriggers() {
        for (Node n = script.getFirstChild(); n != null; ) {
            Node next = n.getNextSibling();
            if (n instanceof Element && "1".equals(((Element) n).getAttribute("endClear"))) script.removeChild(n);
            n = next;
        }
    }

    /** Ajoute un décor fixe par code (ex. porte de sortie après le boss). */
    public void addDecor(String linkage, double x, double y, boolean back) {
        decors.add(new Decor(-1, linkage, x, y, back, false));
    }

    public void killById(Integer id) {
        if (id == null) return;
        for (Entity e : new ArrayList<>(game.getList(ENTITY))) if (e.scriptId == id) e.destroy();
        decors.removeIf(d -> d.sid == id);
    }

    public void playById(Integer id) {
        if (id == null) return;
        for (Decor d : decors) if (d.sid == id) d.playing = true;
    }

    /**
     * Avance les décors animés d'une image, comme la tête de lecture Flash. {@code info} donne pour un clip
     * {nombre d'images, image du removeMovieClip() (0 = absent), images portant un stop()...} : en entrant
     * sur une image à stop() le décor s'arrête (un nouveau play() le relance), sur removeMovieClip() il disparaît.
     */
    public void tickDecors(java.util.function.Function<String, int[]> info) {
        for (java.util.Iterator<Decor> it = decors.iterator(); it.hasNext(); ) {
            Decor d = it.next();
            if (!d.playing) continue;
            int[] c = info.apply(d.linkage);
            int total = Math.max(1, c[0]);
            d.frame = d.frame >= total ? 1 : d.frame + 1;
            if (c.length > 1 && c[1] > 0 && d.frame == c[1]) {
                it.remove();
                continue;
            }
            for (int i = 2; i < c.length; i++) {
                if (c[i] == d.frame) d.playing = false;
            }
        }
    }

    /** Codes spécifiques non scriptables (ScriptEngine.codeTrigger). */
    void codeTrigger(int id) {
        switch (id) {
            case 0: // seau du 1er niveau : raccourci vers le niveau 10
                game.fl_warpStart = true;
                break;
            case 6: { // un ballon de foot sur un emplacement d'objet au hasard
                java.util.List<hammerfest.data.LevelData.Slot> slots = game.world.data.specialSlots;
                if (!slots.isEmpty()) {
                    hammerfest.data.LevelData.Slot s = slots.get(game.random(slots.size()));
                    hammerfest.sim.entity.SoccerBall.attach(game, Entity.xCtr(s.x), Entity.yCtr(s.y));
                }
                break;
            }
            case 10: // les ennemis ne sautent plus vers le bas
                for (Entity e : game.getList(BAD)) {
                    if (e instanceof hammerfest.sim.entity.bad.Jumper) ((hammerfest.sim.entity.bad.Jumper) e).setJumpDown(-1);
                }
                break;
            case 11: // tous les ennemis à abattre disparaissent
                for (Entity e : new ArrayList<>(game.getList(BAD_CLEAR))) {
                    game.fx("hammer_fx_pop", e.x, e.y - CASE_HEIGHT);
                    e.destroy();
                }
                break;
            case 12: // force le hurry up jusqu'à son 2e palier (arrivée de la boule de feu)
                while (game.huState < 2) game.onHurryUp();
                break;
            case 3: // libération des fruits : la prison s'ouvre
                playById(101);
                fl_elevatorOpen = true;
                for (Player p : game.players()) {
                    p.lockControls(SECOND * 12.5);
                    p.dx = 0;
                }
                game.huTimer = 0;
                break;
            case 4: // sortie par l'ascenseur : fin de la partie 14 s plus tard
                if (fl_elevatorOpen) {
                    for (Player p : game.players()) {
                        p.hide();
                        p.lockControls(99999);
                    }
                    game.huTimer = 0;
                    for (Decor d : decors) if (d.sid == 101) d.head = game.players().get(0).head;
                    playById(101);
                    game.endModeTimer = SECOND * 14;
                    fl_elevatorOpen = false;
                }
                break;
            case 5: { // sortie après Tuberculoz
                Entity boss = game.getOne(BOSS);
                if (boss instanceof hammerfest.sim.entity.boss.Tuberculoz && ((hammerfest.sim.entity.boss.Tuberculoz) boss).fl_defeated) {
                    bossDoorTimer -= game.tmod;
                    if (bossDoorTimer <= 0) {
                        game.destroyList(BOSS);
                        game.forcedGoto(102);
                    }
                }
                break;
            }
            case 7: // Igor est déçu
                for (Player p : game.players()) p.setBaseAnims(Anim.PLAYER_WALK, Anim.PLAYER_STOP_L);
                break;
            case 8: // Igor est content
                for (Player p : game.players()) p.setBaseAnims(Anim.PLAYER_WALK_V, Anim.PLAYER_STOP_V);
                break;
            case 9: // rire de Tuberculoz
                game.sound("sound_boss_laugh");
                break;
            case 1: game.huTimer -= game.tmod * 0.5; break; // hurry up allongé
            case 2: game.detachExit(); break; // anti flèche de sortie
            case 13:
                for (Entity e : new ArrayList<>(game.getList(ITEM))) {
                    game.fx("hammer_fx_pop", e.x, e.y - CASE_HEIGHT);
                    e.destroy();
                }
                break;
            case 14: game.clearExtraHoles(); break;
            case 15: game.resetHurry(); break;
            default: break;
        }
    }
}
