package hammerfest.sim;

import hammerfest.data.LevelData;
import hammerfest.data.LevelSet;
import hammerfest.json.Jsons;
import hammerfest.sim.entity.Entity;
import hammerfest.sim.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Set;

import static hammerfest.sim.Const.*;

/**
 * Prédiction locale du joueur client en ligne : le client fait bouger tout de suite son personnage avec le vrai
 * code de déplacement ({@link Player}), dans une simulation « fantôme » réduite au niveau (sans ennemis, objets ni
 * script). L'hôte reste celui qui fait foi : à chaque instantané, l'état qu'il envoie (position, vitesse, appui) au
 * dernier tick de touches qu'il a reçu remplace celui du fantôme, puis les touches pas encore reçues par l'hôte
 * sont rejouées. Seul le déplacement est prédit : bombes, objets, morts restent décidés par l'hôte.
 */
public final class MovePredictor {

    /**
     * État d'un joueur envoyé par l'hôte (RenderFrame.predict) : x, y, dx, dy, appui, direction, prédictible, puis ce
     * qui change le déplacement et que le fantôme (partie sans options ni objets) ne connaît pas : vitesse (Cauchemar,
     * Tornade, objets), chute, gravité, rétrécissement (objet 88 : saut divisé par deux).
     */
    public static final int STATE_SIZE = Integer.valueOf(11); // lue à l'exécution : voir Net.PROTOCOL_VERSION
    /** Touches rejouées au plus (≈ 2 s de latence). */
    static final int MAX_PENDING = 64;

    private final FrameSource frames;
    private Game game;
    private Player player;
    private String levelKey;
    private int gridVersion = -1;
    private final ArrayDeque<int[]> pending = new ArrayDeque<>();
    private boolean valid;

    public MovePredictor(FrameSource frames) {
        this.frames = frames;
    }

    /**
     * Niveau affiché (clé set/dimension/niveau) : la simulation fantôme est refaite à chaque nouveau niveau, la
     * grille (cases ajoutées / retirées par script) est reprise de l'hôte quand elle change.
     */
    public void setLevel(String key, LevelData data, int[] grid, int version) {
        if (data == null) {
            game = null;
            valid = false;
            return;
        }
        if (!key.equals(levelKey) || game == null) {
            levelKey = key;
            gridVersion = -1;
            LevelData copy = Jsons.read(Jsons.write(data), LevelData.class);
            copy.badList.clear();
            copy.specialSlots.clear();
            copy.scoreSlots.clear();
            copy.script = "";
            LevelSet set = new LevelSet("prediction");
            set.levels.add(copy);
            try {
                game = new Game(set, frames, 1L, Set.of(), Set.of());
                game.start(0, 1);
                player = game.players().get(0);
                player.lives = 99;
            } catch (RuntimeException e) {
                game = null;
            }
            valid = false;
        }
        if (game != null && grid != null && version != gridVersion) {
            game.world.loadRenderGrid(grid);
            gridVersion = version;
        }
    }

    /** Variables du niveau qui changent le déplacement (ICE : glissade, AQUA : nage), reçues de l'hôte. */
    public void setEnvironment(boolean ice, boolean aqua) {
        if (game == null) return;
        game.fl_ice = ice;
        game.fl_aqua = aqua;
    }

    /** Touches du client pour le tick {@code tick} (envoyées à l'hôte) : le personnage bouge tout de suite. */
    public void step(int tick, int bits) {
        pending.addLast(new int[]{tick, bits});
        while (pending.size() > MAX_PENDING) pending.removeFirst();
        if (valid) advance(bits);
    }

    /**
     * Instantané de l'hôte : état du joueur {@code state} (STATE_SIZE valeurs) après les touches jusqu'au tick
     * {@code ack} ; le fantôme repart de cet état et rejoue les touches suivantes.
     */
    public void reconcile(int ack, float[] state, int offset) {
        while (!pending.isEmpty() && pending.peekFirst()[0] <= ack) pending.removeFirst();
        if (game == null || state == null || offset + STATE_SIZE > state.length || state[offset + 6] == 0) {
            valid = false;
            return;
        }
        player.x = state[offset];
        player.y = state[offset + 1];
        player.dx = state[offset + 2];
        player.dy = state[offset + 3];
        player.fl_stable = state[offset + 4] != 0;
        player.dir = (int) state[offset + 5];
        player.speedFactor = state[offset + 7];
        player.fallFactor = state[offset + 8];
        player.gravityFactor = state[offset + 9];
        player.specialMan.actives[88] = state[offset + 10] != 0;
        player.fl_entering = false; // prédictible = ni en entrée de niveau, ni bloqué, ni assommé (état de l'hôte)
        player.fl_lockControls = false;
        player.fl_knock = false;
        player.oldX = player.x;
        player.oldY = player.y;
        player.updateCoords();
        valid = true;
        for (int[] in : pending) {
            if (!valid) break;
            advance(in[1]);
        }
    }

    /** Un cycle du seul joueur (sans bombe ni pause), comme Game.tick pour lui. */
    private void advance(int bits) {
        if (player.fl_kill || player.y > GAME_HEIGHT - CASE_HEIGHT * 1.5 || player.y < -CASE_HEIGHT * 4) {
            valid = false; // sortie par le bas, mort… : l'hôte décide
            return;
        }
        try {
            game.predictInputs(new InputFrame(game.cycle, bits & ~(InputFrame.BOMB | InputFrame.PAUSE)));
            player.update();
            player.endUpdate();
            for (Entity e : new ArrayList<>(game.getList(ENTITY))) if (e != player) e.destroy();
            game.cleanKills();
        } catch (RuntimeException e) {
            valid = false;
        }
        if (player.fl_kill) valid = false;
    }

    public boolean valid() {
        return valid && game != null;
    }

    public Player player() {
        return player;
    }
}
