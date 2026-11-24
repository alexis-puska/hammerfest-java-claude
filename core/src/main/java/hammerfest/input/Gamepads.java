package hammerfest.input;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import hammerfest.save.ControlSettings;
import hammerfest.sim.InputFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * Manettes de jeu (gdx-controllers), converties en bits d'{@link InputFrame} comme le clavier.
 *
 * <p>Croix ou stick gauche = gauche/droite, Start/Options = pause ; les 11 autres boutons (4 de droite, gâchettes
 * hautes et basses, clics des sticks, View/Create) se règlent par personnage dans l'écran Commandes
 * ({@link ControlSettings}). Dans les menus Start ou ✕/A valide, capture/Back ou ○/B revient.
 * Chaque personnage a sa manette (écran Commandes, sinon ordre de branchement) ; en solo, toutes les manettes
 * contrôlent Igor.</p>
 *
 * <p>Les appuis brefs sont mémorisés entre deux cycles de jeu (comme pour le clavier) pour ne pas être perdus.</p>
 */
public final class Gamepads extends ControllerAdapter implements Disposable {

    /** Bouton « capture » des manettes Xbox Series, hors disposition standard (numéro de l'énumération Jamepad). */
    static final int BUTTON_MISC1 = 15;
    /**
     * Gâchettes basses : la bibliothèque des manettes sur PC (Jamepad) les donne comme axes 0..1 (LEFTX, LEFTY,
     * RIGHTX, RIGHTY, TRIGGERLEFT, TRIGGERRIGHT) et non comme boutons ({@code buttonL2 = -1}).
     */
    static final int AXIS_TRIGGER_LEFT = 4, AXIS_TRIGGER_RIGHT = 5;
    /** Course à partir de laquelle une gâchette basse compte comme enfoncée. */
    static final float TRIGGER_DOWN = 0.5f;

    /** Réglages des manettes (save.json), partagés par tous les écrans. */
    public static ControlSettings settings = new ControlSettings();

    private static float deadZone() {
        return settings.deadZone;
    }

    private final int[] pending = new int[4];
    /** Gâchettes basses enfoncées, par manette (gauche, droite) : détection de l'appui. */
    private final boolean[][] triggers = new boolean[4][2];
    private boolean pausePressed, mapPressed, confirmPressed, backPressed, startPressed;
    private boolean available = true;

    public Gamepads() {
        try {
            Controllers.addListener(this);
        } catch (Throwable t) { // bibliothèque native absente : jeu au clavier seulement
            available = false;
        }
    }

    public boolean available() {
        return available;
    }

    /** Nombre de manettes branchées. */
    public int count() {
        if (!available) return 0;
        try {
            return Controllers.getControllers().size;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Noms des manettes branchées, dans l'ordre de branchement. */
    public List<String> names() {
        List<String> out = new ArrayList<>();
        if (!available) return out;
        try {
            for (Controller c : Controllers.getControllers()) out.add(c.getName() == null ? "Manette" : c.getName());
        } catch (Throwable t) {
            available = false;
        }
        return out;
    }

    /** Index de la manette du personnage {@code pid} (0 Igor, 1 Sandy), -1 = aucune. */
    public int controllerOf(int pid) {
        return settings.resolve(names())[pid];
    }

    /** Personnage de la manette {@code index} (0 si elle n'est affectée à personne). */
    private int pidOf(int index) {
        int[] r = settings.resolve(names());
        return r[1] == index ? 1 : 0;
    }

    /** Bouton réglable (ControlSettings.FACE_*, L1…SELECT) enfoncé sur la manette {@code index} (écran Commandes). */
    public boolean isDown(int index, int button) {
        if (!available || index < 0) return false;
        try {
            Array<Controller> list = Controllers.getControllers();
            if (index >= list.size) return false;
            return isDown(list.get(index), button);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Vibration des manettes de {@code pid} (en solo, de toutes), si activées : {@code strength} 0..1 multipliée
     * par l'intensité réglée.
     */
    public void vibrate(int pid, int players, int durationMs, float strength) {
        if (!available || !settings.vibration || settings.vibrationStrength <= 0) return;
        try {
            Array<Controller> list = Controllers.getControllers();
            int mine = players <= 1 ? -2 : controllerOf(pid);
            for (int i = 0; i < list.size; i++) {
                if (mine != -2 && i != mine) continue;
                Controller c = list.get(i);
                if (c.canVibrate()) c.startVibration(durationMs, Math.min(1f, strength * settings.vibrationStrength));
            }
        } catch (Throwable ignored) {
            // vibration non gérée par la manette
        }
    }

    private int indexOf(Controller c) {
        Array<Controller> list = Controllers.getControllers();
        for (int i = 0; i < list.size; i++) if (list.get(i) == c) return i;
        return -1;
    }

    /** Bits de la manette à cet instant (croix, stick, boutons réglables selon les réglages de {@code pid}). */
    private int poll(Controller c, int pid) {
        ControllerMapping m = c.getMapping();
        int bits = 0;
        if (c.getButton(m.buttonDpadLeft)) bits |= InputFrame.LEFT;
        if (c.getButton(m.buttonDpadRight)) bits |= InputFrame.RIGHT;
        float ax = c.getAxis(m.axisLeftX);
        if (ax < -deadZone()) bits |= InputFrame.LEFT;
        if (ax > deadZone()) bits |= InputFrame.RIGHT;
        int[] actions = settings.faces[pid];
        for (int b = 0; b < ControlSettings.BUTTONS; b++) if (isDown(c, b)) bits |= actionBit(actions[b]);
        return bits;
    }

    /**
     * Code du bouton réglable {@code button} (gdx-controllers nomme les boutons à la Xbox : A en bas, B à droite,
     * X à gauche, Y en haut ; ✕ ○ ☐ △ sur PlayStation) ; -1 = gâchette basse lue comme axe.
     */
    static int buttonCode(ControllerMapping m, int button) {
        switch (button) {
            case ControlSettings.FACE_BOTTOM: return m.buttonA;
            case ControlSettings.FACE_RIGHT: return m.buttonB;
            case ControlSettings.FACE_LEFT: return m.buttonX;
            case ControlSettings.FACE_TOP: return m.buttonY;
            case ControlSettings.L1: return m.buttonL1;
            case ControlSettings.R1: return m.buttonR1;
            case ControlSettings.L2: return m.buttonL2;
            case ControlSettings.R2: return m.buttonR2;
            case ControlSettings.L3: return m.buttonLeftStick;
            case ControlSettings.R3: return m.buttonRightStick;
            default: return m.buttonBack;
        }
    }

    /** Axe de la gâchette basse {@code button} (L2 / R2), -1 pour les autres boutons. */
    static int triggerAxis(int button) {
        return button == ControlSettings.L2 ? AXIS_TRIGGER_LEFT : button == ControlSettings.R2 ? AXIS_TRIGGER_RIGHT : -1;
    }

    private static boolean isDown(Controller c, int button) {
        int code = buttonCode(c.getMapping(), button);
        if (code >= 0) return c.getButton(code);
        int axis = triggerAxis(button);
        return axis >= 0 && axis < c.getAxisCount() && c.getAxis(axis) > TRIGGER_DOWN;
    }

    /** Bit d'entrée d'une action (bombe vers le haut = touche bas au clavier ; carte et pause ne sont pas des entrées). */
    static int actionBit(int action) {
        switch (action) {
            case ControlSettings.JUMP: return InputFrame.UP;
            case ControlSettings.BOMB: return InputFrame.BOMB;
            case ControlSettings.BOMB_UP: return InputFrame.DOWN;
            default: return 0;
        }
    }

    /** Directions pour les menus : croix et stick dans les quatre directions. */
    private int pollDirections(Controller c) {
        ControllerMapping m = c.getMapping();
        int bits = 0;
        if (c.getButton(m.buttonDpadLeft)) bits |= InputFrame.LEFT;
        if (c.getButton(m.buttonDpadRight)) bits |= InputFrame.RIGHT;
        if (c.getButton(m.buttonDpadUp)) bits |= InputFrame.UP;
        if (c.getButton(m.buttonDpadDown)) bits |= InputFrame.DOWN;
        float ax = c.getAxis(m.axisLeftX), ay = c.getAxis(m.axisLeftY);
        if (ax < -deadZone()) bits |= InputFrame.LEFT;
        if (ax > deadZone()) bits |= InputFrame.RIGHT;
        if (ay < -deadZone()) bits |= InputFrame.UP; // stick vers le haut : valeurs négatives
        if (ay > deadZone()) bits |= InputFrame.DOWN;
        return bits;
    }

    /** Bits des manettes pour le joueur {@code pid} ({@code players} joueurs dans la partie) ; vide les appuis mémorisés. */
    public int bits(int pid, int players) {
        if (!available) return 0;
        int bits = 0;
        try {
            Array<Controller> list = Controllers.getControllers();
            int mine = players <= 1 ? -2 : controllerOf(pid);
            for (int i = 0; i < list.size && i < pending.length; i++) {
                if (players <= 1 ? pid != 0 : i != mine) continue;
                bits |= poll(list.get(i), players <= 1 ? 0 : pid) | pending[i];
                pending[i] = 0;
            }
        } catch (Throwable t) {
            available = false;
        }
        return bits;
    }

    /** Appui sur le bouton réglable {@code button} de la manette {@code i} : action mémorisée jusqu'au cycle suivant. */
    private void press(int i, int button) {
        int action = settings.faces[i >= 0 ? pidOf(i) : 0][button];
        if (i >= 0 && i < pending.length) pending[i] |= actionBit(action);
        if (action == ControlSettings.MAP) mapPressed = true;
        if (action == ControlSettings.PAUSE) pausePressed = true;
    }

    @Override
    public boolean buttonDown(Controller c, int code) {
        ControllerMapping m = c.getMapping();
        int i = indexOf(c);
        if (i >= 0 && i < pending.length) {
            if (code == m.buttonDpadLeft) pending[i] |= InputFrame.LEFT;
            else if (code == m.buttonDpadRight) pending[i] |= InputFrame.RIGHT;
        }
        for (int b = 0; b < ControlSettings.BUTTONS; b++) if (code == buttonCode(m, b)) press(i, b);
        if (code == m.buttonStart) {
            pausePressed = true;
            startPressed = true;
        }
        if (code == m.buttonA) confirmPressed = true;
        if (code == m.buttonB || code == m.buttonBack || code == BUTTON_MISC1) backPressed = true;
        return false;
    }

    @Override
    public boolean axisMoved(Controller c, int axisCode, float value) {
        int i = indexOf(c);
        if (i < 0 || i >= triggers.length) return false;
        for (int b = ControlSettings.L2; b <= ControlSettings.R2; b++) {
            if (triggerAxis(b) != axisCode || buttonCode(c.getMapping(), b) >= 0) continue;
            int side = b - ControlSettings.L2;
            boolean down = value > TRIGGER_DOWN;
            if (down && !triggers[i][side]) press(i, b);
            triggers[i][side] = down;
        }
        return false;
    }

    /** Pause demandée (Start, ou bouton réglé sur Pause) depuis le dernier appel. */
    public boolean consumePause() {
        boolean p = pausePressed;
        pausePressed = false;
        return p;
    }

    /** Bouton réglé sur Carte pressé depuis le dernier appel. */
    public boolean consumeMap() {
        boolean p = mapPressed;
        mapPressed = false;
        return p;
    }

    /** Directions de toutes les manettes à cet instant (navigation dans les menus). */
    public int menuBits() {
        if (!available) return 0;
        int bits = 0;
        try {
            for (Controller c : Controllers.getControllers()) bits |= pollDirections(c);
        } catch (Throwable t) {
            available = false;
        }
        return bits & (InputFrame.LEFT | InputFrame.RIGHT | InputFrame.UP | InputFrame.DOWN);
    }

    /** A ou Start pressé (valider dans les menus). */
    public boolean consumeConfirm() {
        boolean p = confirmPressed;
        confirmPressed = false;
        return p;
    }

    /** Start pressé (menus : lancer / continuer). */
    public boolean consumeStart() {
        boolean p = startPressed;
        startPressed = false;
        return p;
    }

    /** B ou Back pressé (retour dans les menus). */
    public boolean consumeBack() {
        boolean p = backPressed;
        backPressed = false;
        return p;
    }

    /** Oublie les appuis en attente (changement d'écran). */
    public void clear() {
        java.util.Arrays.fill(pending, 0);
        pausePressed = mapPressed = confirmPressed = backPressed = startPressed = false;
    }

    @Override
    public void dispose() {
        if (available) {
            try {
                Controllers.removeListener(this);
            } catch (Throwable ignored) {
                // rien
            }
        }
    }
}
