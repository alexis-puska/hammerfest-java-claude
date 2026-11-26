package hammerfest.screen;

import hammerfest.ui.Tr;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import hammerfest.HammerfestGame;
import hammerfest.data.GameModes;
import hammerfest.game.GameResult;
import hammerfest.net.ClientSession;
import hammerfest.net.Net;
import hammerfest.net.NetEvent;
import hammerfest.net.RenderFrame;
import hammerfest.sim.Const;
import hammerfest.sim.InputFrame;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Partie en ligne côté client : aucune simulation. Les touches (clavier et manette) sont envoyées à l'hôte
 * 32 fois par seconde ; les instantanés reçus ({@link RenderFrame}, 16 par seconde) sont dessinés en
 * interpolant les positions entre les deux derniers, pour un mouvement fluide.
 */
public class OnlineClientScreen extends ScreenAdapter {

    private static final float STEP = 1f / Const.SECOND;

    private final HammerfestGame app;
    private final ClientSession client;
    private final GameView view;
    private final hammerfest.input.Gamepads gamepads = new hammerfest.input.Gamepads();
    private RenderFrame previous, current;
    private float sinceCurrent, interval = 1f / 16;
    private float accumulator;
    private int pendingBits;
    private boolean finished;
    private int tick;
    private final String modeId;
    private final Rumble rumble = new Rumble();
    /** Prédiction locale du personnage du client (pid 1) ; écart de correction résorbé en douceur à l'écran. */
    private final hammerfest.sim.MovePredictor predictor;
    private int[] lastGrid;
    private float errX, errY;
    private boolean predicted;
    private static final int PID = 1;
    private static final java.util.Set<Integer> MOVE_ANIMS = java.util.Set.of(0, 1, 2, 3, 4, 8);

    public OnlineClientScreen(HammerfestGame app, ClientSession client, Net.StartGame start) {
        this.app = app;
        this.client = client;
        this.view = new GameView(app, GameModes.mode(start.modeId));
        view.onlineWith = client.hostName();
        this.modeId = start.modeId;
        predictor = new hammerfest.sim.MovePredictor(view.sprites());
    }

    @Override
    public void show() {
        app.menuAudio.stopMusic(); // la musique des menus se coupe pendant la partie
        Gdx.input.setInputProcessor(new InputAdapter() {
            // Musique coupée / rétablie : caractère tapé (m), qui suit la disposition du clavier (AZERTY, QWERTY…),
            // et non la position physique de la touche (Input.Keys.M = touche « , » sur un AZERTY)
            @Override
            public boolean keyTyped(char character) {
                if (character != 'm' && character != 'M') return false;
                view.toggleMusic();
                app.persist();
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                pendingBits |= GameScreen.bitFor(0, keycode);
                if (keycode == Input.Keys.P) { // demande à l'hôte de mettre en pause ou de reprendre (pour les deux)
                    requestPause(false);
                    return true;
                }
                if (keycode == Input.Keys.C && view.hasMap()) { // carte du puits, pour les deux joueurs
                    requestPause(true);
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    leave(Tr.t("Vous avez quitté la partie"));
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        if (finished) return; // session fermée, écran suivant affiché à la prochaine image (HammerfestGame.show)
        for (NetEvent e = client.poll(); e != null; e = client.poll()) {
            if (e.type == NetEvent.Type.MESSAGE && e.message instanceof RenderFrame) {
                receive((RenderFrame) e.message);
            } else if (e.type == NetEvent.Type.MESSAGE && e.message instanceof Net.PauseMsg) { // pause décidée par l'hôte
                Net.PauseMsg pm = (Net.PauseMsg) e.message;
                if (pm.paused && !pm.map && !view.paused) view.tipIndex++;
                view.paused = pm.paused;
                view.mapOpen = pm.paused && pm.map;
                pendingBits = 0;
                accumulator = 0;
            } else if (e.type == NetEvent.Type.DISCONNECTED) {
                // l'hôte a quitté juste après son dernier instantané (Échap : fin de partie) : on termine
                // normalement (récapitulatif, objets rangés au frigo) au lieu de revenir au menu sans rien garder
                if (current != null && current.gameOver && !finished) {
                    finishGame();
                    return;
                }
                leave((e.name != null ? e.name : Tr.t("L'hôte")) + Tr.t(" a quitté la partie (") + e.text + ")");
                return;
            }
        }
        // touches envoyées à chaque cycle de 1/32 s (état tenu + appuis brefs) ; rien pendant la pause
        if (!view.paused) accumulator += Math.min(delta, 0.25f);
        while (accumulator >= STEP) {
            accumulator -= STEP;
            int bits = pendingBits;
            pendingBits = 0;
            for (int key : GameScreen.KEYS) if (Gdx.input.isKeyPressed(key)) bits |= GameScreen.bitFor(0, key);
            bits |= gamepads.bits(0, 1);
            Net.InputMsg in = new Net.InputMsg();
            in.pid = 1;
            in.tick = tick++;
            in.bits = bits;
            client.send(in);
            predictor.step(in.tick, bits);
        }
        if (gamepads.consumePause()) requestPause(false);
        if (gamepads.consumeMap() && view.hasMap()) requestPause(true);
        sinceCurrent += delta;
        if (current == null) {
            view.render(new RenderFrame()); // écran noir en attendant le premier instantané
            return;
        }
        view.render(interpolated());
        if (current.gameOver && !finished) finishGame();
    }

    private void receive(RenderFrame f) {
        int cycles = current == null ? 0 : Math.max(0, f.cycle - current.cycle);
        if (current != null && sinceCurrent > 0) interval = 0.8f * interval + 0.2f * Math.min(0.25f, sinceCurrent);
        previous = current;
        current = f;
        predict(f);
        rumble.update(f, gamepads, new int[]{PID}, 1); // toutes les manettes du poste jouent Sandy
        sinceCurrent = 0;
        view.advance(f, cycles);
    }

    /** Positions interpolées entre les deux derniers instantanés (mêmes entités repérées par leur identifiant). */
    private RenderFrame interpolated() {
        if (previous == null || previous.ids == null || current.ids == null) return current;
        float a = Math.min(1f, sinceCurrent / Math.max(0.01f, interval));
        Map<Integer, Integer> before = new HashMap<>();
        for (int i = 0; i < previous.ids.length; i++) before.put(previous.ids[i], i);
        float[] xs = current.xs.clone(), ys = current.ys.clone();
        for (int i = 0; i < current.ids.length; i++) {
            Integer j = before.get(current.ids[i]);
            if (j == null) continue;
            float dx = current.xs[i] - previous.xs[j], dy = current.ys[i] - previous.ys[j];
            if (Math.abs(dx) > 60 || Math.abs(dy) > 60) continue; // téléportation : pas d'interpolation
            xs[i] = previous.xs[j] + dx * a;
            ys[i] = previous.ys[j] + dy * a;
        }
        RenderFrame f = shallowCopy(current);
        f.xs = xs;
        f.ys = ys;
        if (previous.scroll >= 0 && current.scroll > previous.scroll) { // défilement entre deux niveaux
            f.scroll = previous.scroll + (current.scroll - previous.scroll) * a;
        }
        applyPrediction(f);
        f.grid = null;
        f.eventNames = null; // événements déjà traités à la réception
        return f;
    }

    /** Instantané reçu : niveau du fantôme à jour, puis recalage sur l'état de l'hôte et rejeu des touches. */
    private void predict(RenderFrame f) {
        if (f.grid != null) lastGrid = f.grid;
        hammerfest.data.LevelData data = null;
        try {
            data = app.data.levels(f.setName).level(f.levelId);
        } catch (RuntimeException ignored) {
            // set inconnu du client : pas de prédiction
        }
        predictor.setLevel(f.setName + "/" + f.currentDim + "/" + f.levelId, data, lastGrid, f.gridVersion);
        predictor.setEnvironment(f.iceLevel, f.aqua);
        float oldX = 0, oldY = 0;
        boolean had = predictor.valid();
        if (had) {
            oldX = (float) predictor.player().x;
            oldY = (float) predictor.player().y;
        }
        predictor.reconcile(f.ackTick, f.predict, PID * hammerfest.sim.MovePredictor.STATE_SIZE);
        if (had && predictor.valid()) { // écart de la correction : affiché puis résorbé
            errX += oldX - (float) predictor.player().x;
            errY += oldY - (float) predictor.player().y;
            if (Math.abs(errX) > 40 || Math.abs(errY) > 40) errX = errY = 0;
        } else {
            errX = errY = 0;
        }
    }

    /** Personnage du client dessiné à sa position prédite (direction et animation comprises). */
    private void applyPrediction(RenderFrame f) {
        predicted = predictor.valid() && f.playerIds != null && f.playerIds.length > PID && f.ids != null;
        if (!predicted) return;
        int id = f.playerIds[PID];
        for (int i = 0; i < f.ids.length; i++) {
            if (f.ids[i] != id) continue;
            hammerfest.sim.entity.Player p = predictor.player();
            errX *= 0.85f;
            errY *= 0.85f;
            f.xs[i] = (float) p.x + errX;
            f.ys[i] = (float) p.y + errY;
            f.xscales = f.xscales.clone();
            f.anims = f.anims.clone();
            f.subs = f.subs.clone();
            f.xscales[i] = (float) (p.xscale / 100.0);
            // animations de déplacement prédites (arrêt, marche, saut, réception, bord) ; les autres (attaque,
            // coup de pied, mort…) viennent de l'hôte
            if (MOVE_ANIMS.contains((int) f.anims[i]) && MOVE_ANIMS.contains(p.animId)) {
                f.anims[i] = (short) p.animId;
                f.subs[i] = (short) p.subFrame;
            }
            return;
        }
    }

    private static RenderFrame shallowCopy(RenderFrame c) {
        RenderFrame f = new RenderFrame();
        for (java.lang.reflect.Field fd : RenderFrame.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(fd.getModifiers())) continue;
            try {
                fd.set(f, fd.get(c));
            } catch (IllegalAccessException ignored) {
                // champs publics uniquement
            }
        }
        return f;
    }

    /**
     * Fin de partie décidée par l'hôte : le client range dans son frigo les objets ramassés pendant la partie
     * (frigo commun de la coopération, comme l'hôte), ses statistiques et quêtes sont mises à jour.
     */
    private void finishGame() {
        finished = true;
        long score = current.scores != null && current.scores.length > PID ? current.scores[PID] : 0;
        Map<Integer, Integer> items = new HashMap<>();
        for (int k = 0; current.picks != null && k + 1 < current.picks.length; k += 2) {
            items.merge(current.picks[k], current.picks[k + 1], Integer::sum);
        }
        GameResult result = new GameResult(modeId, score, current.mainLevelId, items);
        result.victory = current.victory;
        if (current.soccer) result.scoreText = Tr.t("Igor ") + current.soccer0 + " - " + current.soccer1 + Tr.t(" Sandy");
        client.close();
        List<hammerfest.data.QuestDef> completed = app.finishGame(result);
        app.show(new RecapScreen(app, result, completed));
    }

    /** P ou Start (carte : C ou bouton Carte) : demande à l'hôte de mettre en pause / d'ouvrir la carte, ou de reprendre. */
    private void requestPause(boolean map) {
        if (finished) return;
        Net.PauseMsg m = new Net.PauseMsg();
        m.map = map;
        client.send(m);
    }

    private void leave(String message) {
        if (finished) return;
        finished = true;
        client.close();
        app.show(new MultiScreen(app));
        Gdx.app.log("hammerfest", message);
    }

    @Override
    public void resize(int width, int height) {
        view.resize(width, height);
    }

    @Override
    public void dispose() {
        view.dispose();
        gamepads.dispose();
        if (!finished) client.close();
    }
}
