package hammerfest.screen;

import hammerfest.input.Gamepads;
import hammerfest.net.RenderFrame;
import hammerfest.sim.MovePredictor;

/**
 * Vibrations des manettes, d'après les instantanés : explosion proche d'un joueur, vie perdue, boss touché.
 * Réglées dans l'écran Commandes (activation, intensité).
 */
final class Rumble {

    /** Distance (px) sous laquelle une explosion fait vibrer la manette du joueur. */
    static final float NEAR = 90;

    private int[] lastLives;
    private float lastBoss = -1;

    /**
     * @param pids    joueurs dont les manettes sont sur ce poste (index dans l'instantané)
     * @param players joueurs pilotés depuis ce poste (1 = toutes les manettes jouent le joueur 0)
     */
    void update(RenderFrame f, Gamepads gamepads, int[] pids, int players) {
        if (f == null || f.lives == null) return;
        for (int pid : pids) {
            if (pid >= f.lives.length) continue;
            // vie perdue
            if (lastLives != null && pid < lastLives.length && f.lives[pid] < lastLives[pid]) {
                gamepads.vibrate(pid, players, 450, 1f);
            }
            // explosion proche
            int o = pid * MovePredictor.STATE_SIZE;
            if (f.eventNames != null && f.predict != null && o + 1 < f.predict.length) {
                float px = f.predict[o], py = f.predict[o + 1];
                for (int i = 0; i < f.eventNames.length; i++) {
                    String n = f.eventNames[i];
                    if (f.eventSounds[i] || !("rumble".equals(n) || n.startsWith("explodeZone|"))) continue;
                    if (Math.hypot(f.eventXs[i] - px, f.eventYs[i] - py) < NEAR) {
                        gamepads.vibrate(pid, players, 160, 0.55f);
                        break;
                    }
                }
            }
        }
        // boss touché
        if (lastBoss >= 0 && f.bossRatio >= 0 && f.bossRatio < lastBoss) {
            for (int pid : pids) gamepads.vibrate(pid, players, 220, 0.8f);
        }
        lastLives = f.lives.clone();
        lastBoss = f.bossRatio;
    }
}
