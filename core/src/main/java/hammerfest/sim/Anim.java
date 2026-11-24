package hammerfest.sim;

/** Animation d'entité : id (image du clip parent - 1) et bouclage (Data.ANIM_*). */
public final class Anim {
    public final int id;
    public final boolean loop;

    public Anim(int id, boolean loop) {
        this.id = id;
        this.loop = loop;
    }

    public static final Anim PLAYER_STOP = new Anim(0, true);
    public static final Anim PLAYER_WALK = new Anim(1, true);
    public static final Anim PLAYER_JUMP_UP = new Anim(2, false);
    public static final Anim PLAYER_JUMP_DOWN = new Anim(3, false);
    public static final Anim PLAYER_JUMP_LAND = new Anim(4, false);
    public static final Anim PLAYER_DIE = new Anim(5, true);
    public static final Anim PLAYER_KICK = new Anim(6, false);
    public static final Anim PLAYER_ATTACK = new Anim(7, false);
    public static final Anim PLAYER_EDGE = new Anim(8, true);
    public static final Anim PLAYER_WAIT1 = new Anim(9, false);
    public static final Anim PLAYER_WAIT2 = new Anim(10, false);
    public static final Anim PLAYER_KNOCK_IN = new Anim(12, false);
    public static final Anim PLAYER_KNOCK_OUT = new Anim(13, false);
    public static final Anim PLAYER_RESURRECT = new Anim(14, false);
    public static final Anim PLAYER_CARROT = new Anim(15, true);
    public static final Anim PLAYER_RUN = new Anim(16, true);
    public static final Anim PLAYER_AIRKICK = new Anim(18, false);
    /** Pose de victoire (« V ») et pose déçue, pour le super-objet du niveau parfait. */
    public static final Anim PLAYER_SOCCER = new Anim(17, true);
    public static final Anim PLAYER_STOP_V = new Anim(19, true);
    public static final Anim PLAYER_WALK_V = new Anim(20, true);
    public static final Anim PLAYER_STOP_L = new Anim(21, true);
    public static final Anim PLAYER_WALK_L = new Anim(22, true);
}
