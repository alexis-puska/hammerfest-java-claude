package hammerfest.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import hammerfest.HammerfestGame;
import hammerfest.data.GameModes;
import hammerfest.data.QuestDef;
import hammerfest.game.GameResult;
import hammerfest.net.HostSession;
import hammerfest.net.Net;
import hammerfest.net.NetEvent;
import hammerfest.net.RenderFrame;
import hammerfest.sim.Const;
import hammerfest.sim.Game;
import hammerfest.sim.InputFrame;
import hammerfest.sim.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Écran de partie : simulation {@link Game} à pas fixe (32 cycles/s, comme Timer.wantedFPS), entrées clavier
 * et manette converties en {@link InputFrame}, dessin par {@link GameView} à partir d'un {@link RenderFrame}.
 *
 * <p>En ligne ({@code host} non nul) : cet écran est l'hôte qui fait foi. Le joueur 2 est piloté par les
 * touches reçues du client, et un instantané lui est envoyé tous les {@link #SEND_EVERY} cycles.</p>
 */
public class GameScreen extends ScreenAdapter {

    private static final float STEP = 1f / Const.SECOND;
    private static final int MAX_STEPS_PER_FRAME = 5;
    /** Un instantané envoyé au client tous les 2 cycles (16 par seconde). */
    static final int SEND_EVERY = 2;

    private final HammerfestGame app;
    private final GameModes.Mode mode;
    private final Game game;
    private final GameView view;
    private final HostSession host;
    private RenderFrame frame;
    private float accumulator;
    private boolean finished;

    /** Grille déjà dessinée localement / envoyée au client (niveau et version). */
    private String localGridKey, sentGridKey;
    private final List<Game.Event> pendingRemote = new ArrayList<>();
    /** Touches du client : état tenu (dernier reçu) et appuis brefs accumulés. */
    private int remoteHeld, remoteLatched;
    private boolean remoteGone;

    public GameScreen(HammerfestGame app, GameModes.Mode mode, Set<String> options) {
        this(app, mode, options, System.nanoTime(), null);
    }

    public GameScreen(HammerfestGame app, GameModes.Mode mode, Set<String> options, long seed, HostSession host) {
        this.app = app;
        this.mode = mode;
        this.host = host;
        this.view = new GameView(app, mode);
        Set<String> opts = new java.util.HashSet<>(options);
        // Seule option passive : Tornade, active dès qu'elle est débloquée ; les autres sont cochées dans Jouer.
        if (app.save.unlockedOptions.contains(GameModes.PASSIVE_BOOST)) opts.add(GameModes.PASSIVE_BOOST);
        this.game = new Game(app.data.levels(mode.levelSet), view.sprites(), seed, app.save.unlockedFamilies, opts);
        game.setItemFamilies(app.data.families);
        if ("adventure".equals(mode.levelSet)) {
            game.setDimensions(hammerfest.data.GameData.ADVENTURE_DIMENSIONS, app.data::levels, app.data.portals);
        }
        game.start(startLevel(mode, opts, game), mode.players);
        if (host != null) {
            view.onlineWith = host.peerName();
            Net.StartGame start = new Net.StartGame();
            start.modeId = mode.id;
            start.options = opts.toArray(new String[0]);
            start.seed = seed;
            start.clientPid = 1;
            host.send(start);
        }
        frame = capture(new ArrayList<>());
        view.advance(frame, 0);
    }

    /** Réglages propres au mode (Apprentissage, Tubercourse, Time Attack, SoccerFest) et niveau de départ. */
    static int startLevel(GameModes.Mode mode, Set<String> opts, Game game) {
        if ("tutorial".equals(mode.id)) game.setTutorial();
        if ("bossrush".equals(mode.id)) game.setBossRush();
        if ("timeattackmulti".equals(mode.id)) game.setTimeAttack(0);
        if ("timeattack".equals(mode.id)) game.setTimeAttack(opts.contains("set_ta_1") ? 1 : 0);
        int start = game.fl_timeAttack ? game.taFirstLevel : 0;
        if ("soccer".equals(mode.id)) { // SoccerFest : terrain choisi (set_soc_0..3) ou tiré au sort
            game.setSoccer();
            start = new java.util.Random().nextInt(4);
            for (int i = 0; i < 4; i++) if (opts.contains("set_soc_" + i)) start = i;
        }
        return start;
    }

    private RenderFrame capture(List<Game.Event> events) {
        String key = game.set.name + "/" + game.currentDim + "/" + game.levelId + "/" + game.world.version;
        boolean withGrid = !key.equals(localGridKey);
        localGridKey = key;
        return RenderFrame.capture(game, events, withGrid);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                for (int pid = 0; pid < pendingBits.length; pid++) pendingBits[pid] |= bitFor(pid, keycode);
                if (keycode == Input.Keys.ESCAPE) {
                    finish();
                    return true;
                }
                if (keycode == Input.Keys.P) { // pause et aide (GameMode.onPause), P ou C pour reprendre
                    togglePause();
                    return true;
                }
                if (keycode == Input.Keys.C && view.hasMap()) { // carte (GameMode.onMap)
                    toggleMap();
                    return true;
                }
                // Déguisements (GameMode, touche D) : joueur 1, en partie à un joueur (D sert aussi à Sandy)
                if (keycode == Input.Keys.D && game.players().size() == 1 && host == null && !finished) {
                    game.disguise();
                    return true;
                }
                // TEMPORAIRE (tests manuels) : 1 = niveau suivant, comme le parapluie. À retirer.
                if (keycode == Input.Keys.NUM_1 || keycode == Input.Keys.NUMPAD_1) {
                    if (!finished) game.debugSkipLevel();
                    return true;
                }
                return false;
            }
        });
    }

    private final Rumble rumble = new Rumble();

    /** Manettes de jeu (Start = pause, ○/B = carte), en plus du clavier. */
    private final hammerfest.input.Gamepads gamepads = new hammerfest.input.Gamepads();

    private void togglePause() {
        if (host != null) { // partie partagée : pas de pause
            view.alert("Pas de pause en ligne");
            return;
        }
        if (view.paused) {
            view.paused = false;
            view.mapOpen = false;
        } else {
            view.paused = true;
            view.tipIndex++;
        }
    }

    private void toggleMap() {
        if (host != null) return;
        if (view.paused) {
            view.paused = false;
            view.mapOpen = false;
        } else {
            view.paused = true;
            view.mapOpen = true;
        }
    }

    /** Touches enfoncées depuis le dernier cycle, par joueur (un appui bref entre deux cycles n'est pas perdu). */
    private final int[] pendingBits = new int[2];

    /** Touches surveillées pour chaque joueur. */
    static final int[] KEYS = {Input.Keys.LEFT, Input.Keys.RIGHT, Input.Keys.UP, Input.Keys.DOWN,
            Input.Keys.SPACE, Input.Keys.CONTROL_LEFT, Input.Keys.ENTER,
            Input.Keys.D, Input.Keys.G, Input.Keys.R, Input.Keys.F, Input.Keys.A};

    /**
     * Touches comme dans le jeu d'origine. Joueur 1 : flèches + Espace, Ctrl ou Entrée (MultiCoop : Entrée).
     * Joueur 2 (Sandy, MultiCoop) : R haut, F bas, D gauche, G droite, A bombe.
     */
    static int bitFor(int pid, int keycode) {
        if (pid == 0) {
            switch (keycode) {
                case Input.Keys.LEFT: return InputFrame.LEFT;
                case Input.Keys.RIGHT: return InputFrame.RIGHT;
                case Input.Keys.UP: return InputFrame.UP;
                case Input.Keys.DOWN: return InputFrame.DOWN;
                case Input.Keys.SPACE:
                case Input.Keys.CONTROL_LEFT:
                case Input.Keys.ENTER: return InputFrame.BOMB;
                default: return 0;
            }
        }
        switch (keycode) {
            case Input.Keys.D: return InputFrame.LEFT;
            case Input.Keys.G: return InputFrame.RIGHT;
            case Input.Keys.R: return InputFrame.UP;
            case Input.Keys.F: return InputFrame.DOWN;
            case Input.Keys.A: return InputFrame.BOMB;
            default: return 0;
        }
    }

    private InputFrame[] readInput() {
        InputFrame[] out = new InputFrame[game.players().size()];
        for (int pid = 0; pid < out.length; pid++) {
            int bits;
            if (host != null && pid == 1) { // joueur distant
                bits = remoteHeld | remoteLatched;
                remoteLatched = 0;
            } else {
                int local = host != null ? 0 : pid; // en ligne, toutes les commandes locales pilotent le joueur 1
                bits = pendingBits[local];
                pendingBits[local] = 0;
                for (int key : KEYS) {
                    if (Gdx.input.isKeyPressed(key)) bits |= bitFor(local, key);
                }
                bits |= gamepads.bits(local, host != null ? 1 : out.length);
            }
            out[pid] = new InputFrame(game.cycle, bits);
        }
        return out;
    }

    private void pollNetwork() {
        if (host == null) return;
        for (NetEvent e = host.poll(); e != null; e = host.poll()) {
            if (e.type == NetEvent.Type.MESSAGE && e.message instanceof Net.InputMsg) {
                Net.InputMsg in = (Net.InputMsg) e.message;
                remoteHeld = in.bits;
                lastRemoteTick = Math.max(lastRemoteTick, in.tick);
                remoteLatched |= in.bits;
            } else if (e.type == NetEvent.Type.DISCONNECTED && !remoteGone) {
                remoteGone = true;
                remoteHeld = 0;
                view.alert((e.name != null ? e.name : "L'autre joueur") + " a quitté la partie");
            }
        }
    }

    @Override
    public void render(float delta) {
        pollNetwork();
        if (gamepads.consumePause() && !finished) togglePause();
        if (gamepads.consumeMap() && !finished && view.hasMap()) toggleMap();
        if (!view.paused && !finished) {
            accumulator += Math.min(delta, 0.25f);
            int steps = 0;
            while (accumulator >= STEP && steps < MAX_STEPS_PER_FRAME) {
                game.tick(readInput());
                if (game.script != null) game.script.tickDecors(view.sprites()::playInfo);
                List<Game.Event> events = game.drainEvents();
                if (host != null) pendingRemote.addAll(events);
                frame = capture(events);
                view.advance(frame, 1);
                // vibrations : en ligne seul le joueur 1 est sur ce poste ; en local, chacun sa manette
                int local = host != null ? 1 : game.players().size();
                rumble.update(frame, gamepads, local > 1 ? new int[]{0, 1} : new int[]{0}, local);
                if (host != null && (game.cycle % SEND_EVERY == 0 || game.fl_gameOver)) sendFrame();
                accumulator -= STEP;
                steps++;
            }
            if (game.fl_gameOver) finish();
        }
        view.render(frame);
    }

    /** Instantané pour le client : événements accumulés, grille si elle a changé depuis le dernier envoi. */
    /** Dernier tick de touches reçu du client. */
    private int lastRemoteTick = -1;

    private void sendFrame() {
        if (remoteGone) return;
        String key = game.set.name + "/" + game.currentDim + "/" + game.levelId + "/" + game.world.version;
        boolean withGrid = !key.equals(sentGridKey);
        sentGridKey = key;
        RenderFrame f = RenderFrame.capture(game, pendingRemote, withGrid);
        f.ackTick = lastRemoteTick; // prédiction du client : touches prises en compte jusqu'à ce tick
        pendingRemote.clear();
        host.send(f);
    }

    /** Fin de partie (game over, fin du set ou abandon) : frigo/stats/quêtes puis récapitulatif. */
    private void finish() {
        if (finished) return;
        finished = true;
        long score = 0;
        for (Player p : game.players()) score += p.score;
        GameResult result = new GameResult(mode.id, score, game.maxLevel, game.picks());
        result.victory = game.fl_victory;
        if (game.fl_timeAttack) result.timeMs = game.fl_gameOver ? game.totalTime : Game.TA_INFINITY;
        if (game.fl_bossRush) result.scoreText = "Score : " + score + "   Temps : " + Game.formatTime(game.chronoMs());
        if (game.fl_soccer) {
            result.victory = false;
            result.scoreText = "Igor " + game.soccerScores[0] + " - " + game.soccerScores[1] + " Sandy";
        }
        if (host != null) {
            if (!game.fl_gameOver) { // abandon de l'hôte : le client est prévenu
                game.onGameOver();
            }
            sendFrame();
        }
        List<QuestDef> completed = app.finishGame(result);
        app.show(new RecapScreen(app, result, completed));
    }

    @Override
    public void resize(int width, int height) {
        view.resize(width, height);
    }

    @Override
    public void dispose() {
        view.dispose();
        gamepads.dispose();
        if (host != null) {
            host.close();
            app.ports.close();
        }
    }
}
