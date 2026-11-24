package hammerfest.sim.entity;

import hammerfest.sim.Anim;
import hammerfest.sim.InputFrame;

import static hammerfest.sim.Const.*;

/**
 * Portage de {@code entity/PlayerController.mt}. Les touches viennent d'un {@link InputFrame}
 * (même en solo) ; les « touches verrouillables » (keyIsDown) ne déclenchent qu'à l'appui.
 */
public class PlayerController {

    final Player player;
    /** Bits verrouillés jusqu'au relâchement (keyLocks / lastKeys). */
    private int locks;
    private InputFrame input = new InputFrame(0, 0);
    public boolean fl_upKick;
    /** Option « Contrôle du ballon » (SoccerFest) : la force du tir au sol dépend de la vitesse du joueur. */
    public boolean fl_powerControl;
    public int waterJump;
    double walkTimer;

    public PlayerController(Player p) {
        player = p;
        fl_upKick = p.game.hasFamily(101);
    }

    boolean isDown(int bit) {
        return input.has(bit);
    }

    /** Appui unique : vrai si enfoncée et non verrouillée, puis verrouille. */
    boolean keyIsDown(int bit) {
        boolean fl = isDown(bit) && (locks & bit) == 0;
        if (fl) lockKey(bit);
        return fl;
    }

    void lockKey(int bit) {
        locks |= bit;
    }

    public void update(InputFrame frame) {
        input = frame;
        getControls();
    }

    void getControls() {
        // Déverrouille les touches relâchées
        locks &= input.bits();

        if (player.fl_stable) waterJump = 3;

        double speed = PLAYER_SPEED * player.speedFactor;
        boolean ice = player.game.fl_ice || player.game.fl_aqua;

        if (isDown(InputFrame.LEFT)) {
            if (ice) {
                double frict = !player.fl_stable && player.game.fl_ice ? 0.35 : 0.1;
                player.dx -= frict * speed;
                player.dx = Math.max(player.dx, -speed);
            } else {
                player.dx = -speed;
            }
            player.dir = -1;
            if (player.fl_stable) player.playAnim(player.baseWalkAnim);
        }

        if (isDown(InputFrame.RIGHT)) {
            if (ice) {
                double frict = !player.fl_stable && player.game.fl_ice ? 0.35 : 0.1;
                player.dx += frict * speed;
                player.dx = Math.min(player.dx, speed);
            } else {
                player.dx = speed;
            }
            player.dir = 1;
            if (player.fl_stable) player.playAnim(player.baseWalkAnim);
        }

        // Feuille d'arbre : des points en marchant
        if (player.specialMan.active(73) && player.fl_stable && player.dx != 0) {
            walkTimer -= player.game.tmod;
            if (walkTimer <= 0) {
                walkTimer = SECOND;
                player.getScore(player, 10);
            }
        }

        // Freinage horizontal
        if (!isDown(InputFrame.LEFT) && !isDown(InputFrame.RIGHT)) {
            if (!player.game.fl_ice) player.dx *= player.game.gFriction * 0.8;
            if (player.animId == player.baseWalkAnim.id || player.animId == Anim.PLAYER_RUN.id) {
                player.playAnim(player.baseStopAnim);
            }
        }

        // Saut dans l'eau
        if (player.game.fl_aqua && waterJump > 0 && !player.fl_stable && keyIsDown(InputFrame.UP)) {
            player.airJump();
            waterJump--;
        }

        // Saut
        if (player.fl_stable && isDown(InputFrame.UP)) {
            player.dy = player.specialMan.active(88) ? -PLAYER_JUMP * 0.5 : -PLAYER_JUMP; // rétréci
            if (player.specialMan.active(66)) player.getScore(player, 10); // cactus
            player.game.sound("sound_jump");
            player.playAnim(Anim.PLAYER_JUMP_UP);
            player.game.fx("hammer_fx_jump", player.x, player.y, 0, 100, 100, 50); // _alpha = 50
            lockKey(InputFrame.UP);
        }

        // Attaque : kick des bombes proches, sinon pose d'une bombe
        if (keyIsDown(InputFrame.BOMB) && player.coolDown == 0) {
            java.util.List<Entity> bombs = player.game.getClose(BOMB, player.x, player.y, kickDistance(), false);
            if (bombs.isEmpty()) {
                if (player.currentWeapon > 0 && player.countBombs() < player.maxBombs) {
                    Entity e = player.attack();
                    if (e != null) {
                        e.setParent(player);
                        if (player.fl_stable) player.playAnim(Anim.PLAYER_ATTACK);
                    }
                }
            } else if (fl_powerControl && player.fl_stable) {
                player.kickBomb(bombs, Math.min(1.2, 0.5 + Math.abs(player.dx) / 4));
            } else {
                player.kickBomb(bombs, 1.0);
            }
        } else if (keyIsDown(InputFrame.DOWN) && fl_upKick) {
            java.util.List<Entity> bombs = player.game.getClose(BOMB, player.x, player.y, kickDistance(), false);
            if (!bombs.isEmpty()) player.upKickBomb(bombs);
        }
    }

    private double kickDistance() {
        double d = player.fl_stable ? KICK_DISTANCE : AIR_KICK_DISTANCE;
        return player.specialMan.active(115) ? d * 1.2 : d;
    }
}
