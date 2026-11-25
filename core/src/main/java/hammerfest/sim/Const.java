package hammerfest.sim;

/** Constantes de jeu reprises de {@code Data.mt} (valeurs d'origine, 32 cycles par seconde). */
public final class Const {

    public static final int GAME_WIDTH = 400;
    public static final int GAME_HEIGHT = 500;
    public static final int LEVEL_WIDTH = 20;
    public static final int LEVEL_HEIGHT = 25;
    public static final int CASE_WIDTH = 20;
    public static final int CASE_HEIGHT = 20;
    /** Durée d'une seconde en cycles de jeu. */
    public static final int SECOND = 32;

    /**
     * Cadence des clips Flash qui jouent tout seuls (effets, décors, boucles) : le SWF d'origine tourne à 40 images
     * par seconde, alors que la simulation avance à SECOND (32) cycles par seconde (Timer.tmod). Images de clip
     * jouées pendant un cycle de jeu : 40 / 32.
     */
    public static final int FLASH_FPS = 40;
    public static final double CLIP_FRAMES_PER_CYCLE = FLASH_FPS / (double) SECOND;

    /** Images de clip jouées depuis {@code cycles} cycles de jeu (arrondi inférieur). */
    public static long clipAge(long cycles) {
        return cycles * FLASH_FPS / SECOND;
    }

    // Types d'entités (Data.ENTITY...)
    public static final int ENTITY = 1;
    public static final int PHYSICS = 1 << 1;
    public static final int ITEM = 1 << 2;
    public static final int SPECIAL_ITEM = 1 << 3;
    public static final int PLAYER = 1 << 4;
    public static final int PLAYER_BOMB = 1 << 5;
    public static final int BAD = 1 << 6;
    public static final int SHOOT = 1 << 7;
    public static final int BOMB = 1 << 8;
    public static final int FX = 1 << 9;
    public static final int SUPA = 1 << 10;
    public static final int CATCHER = 1 << 11;
    public static final int BALL = 1 << 12;
    public static final int HU_BAD = 1 << 13;
    public static final int BOSS = 1 << 14;
    public static final int BAD_BOMB = 1 << 15;
    public static final int BAD_CLEAR = 1 << 16;
    public static final int SOCCERBALL = 1 << 17;
    public static final int PLAYER_SHOOT = 1 << 18;
    public static final int PERFECT_ITEM = 1 << 19;
    public static final int SPEAR = 1 << 20;

    // Objets (Data.ITEM_*)
    public static final double ITEM_LIFE_TIME = 8 * 32;
    public static final int DIAMANT = 8;
    public static final int CONVERT_DIAMANT = 17;
    public static final double SPECIAL_ITEM_TIMER = 8 * 32;
    public static final double EXTEND_TIMER = 10 * SECOND;
    /** Têtes du joueur (Data.HEAD_*) : image du clip de tête. */
    /** Particules décoratives (Data.PARTICLE_*). */
    public static final int PARTICLE_ICE = 1, PARTICLE_CLASSIC_BOMB = 2, PARTICLE_STONE = 3, PARTICLE_SPARK = 4,
            PARTICLE_DUST = 5, PARTICLE_METAL = 7, PARTICLE_TUBERCULOZ = 8, PARTICLE_LITCHI = 10, PARTICLE_BUBBLE = 11,
            PARTICLE_ICE_BAD = 12, PARTICLE_BLOB = 13, PARTICLE_FRAMB = 14, PARTICLE_PORTAL = PARTICLE_SPARK,
            PARTICLE_ORANGE = 6, PARTICLE_RAIN = 9, PARTICLE_FRAMB_SMALL = 14;
    /** Data.MAX_FX : nombre maximal de particules (Tuberculoz vaincu en lâche autant). */
    public static final int MAX_FX = 16;
    /** Événements de la carte (Data.EVENT_*). */
    public static final int EVENT_EXIT_RIGHT = 1, EVENT_BACK_RIGHT = 2, EVENT_EXIT_LEFT = 3, EVENT_BACK_LEFT = 4,
            EVENT_DEATH = 5, EVENT_EXTEND = 6, EVENT_TIME = 7;
    public static final int HEAD_AFRO = 2, HEAD_CERBERE = 3, HEAD_PIOU = 4, HEAD_MARIO = 5, HEAD_TUB = 6;
    public static final int HEAD_NORMAL = 1, HEAD_LOSE = 8, HEAD_CROWN = 9, HEAD_SANDY = 10, HEAD_SANDY_LOSE = 11,
            HEAD_SANDY_CROWN = 12;
    /** Fonds d'ambiance (Data.BG_*), images du clip hammer_special_bg (id + 1). */
    public static final int BG_STAR = 0, BG_ORANGE = 2, BG_CONSTEL = 5, BG_GHOSTS = 7, BG_PYRAMID = 9,
            BG_SINGER = 10, BG_STORM = 11, BG_GUU = 12;
    public static final double SCORE_ITEM_TIMER = 12 * 32;
    /** Data.RARITY : poids de tirage par indice de rareté. */
    public static final int[] RARITY = {0, 2000, 1000, 300, 100, 10, 1, 60};
    public static final int[] EXTRA_LIFE_STEPS = {100000, 500000, 1000000, 2000000, 3000000, 4000000};

    // Cases
    public static final int GROUND = 1;
    public static final int WALL = 2;
    public static final int OUT_WALL = 3;
    public static final int HORIZONTAL = 1;
    public static final int VERTICAL = 2;
    public static final int FIELD_TELEPORT = -6;
    public static final int FIELD_PORTAL = -7;
    public static final int FIELD_GOAL_1 = -8;
    public static final int FIELD_GOAL_2 = -9;
    public static final int FIELD_BUMPER = -10;
    public static final int FIELD_PEACE = -11;

    // Drapeaux IA (Data.IA_*)
    public static final int IA_TILE_TOP = 1;
    public static final int IA_ALLOW_FALL = 1 << 1;
    public static final int IA_BORDER = 1 << 2;
    public static final int IA_SMALL_SPOT = 1 << 3;
    public static final int IA_FALL_SPOT = 1 << 4;
    public static final int IA_JUMP_UP = 1 << 5;
    public static final int IA_JUMP_DOWN = 1 << 6;
    public static final int IA_JUMP_LEFT = 1 << 7;
    public static final int IA_JUMP_RIGHT = 1 << 8;
    public static final int IA_TILE = 1 << 9;
    public static final int IA_CLIMB_LEFT = 1 << 10;
    public static final int IA_CLIMB_RIGHT = 1 << 11;
    public static final int IA_HJUMP = 2;
    public static final int IA_VJUMP = 2;
    public static final int IA_CLIMB = 4;

    // Physique
    public static final double BORDER_MARGIN = CASE_WIDTH / 2.0;
    public static final double FRICTION_X = 0.93;
    public static final double FRICTION_Y = 0.86;
    public static final double FRICTION_GROUND = 0.70;
    public static final double FRICTION_SLIDE = 0.97;
    public static final double GRAVITY = 1.0;
    public static final double FALL_FACTOR_FROZEN = 2.3;
    public static final double FALL_FACTOR_KNOCK = 1.5;
    public static final double FALL_FACTOR_DEAD = 1.75;
    public static final double FALL_SPEED = 0.9;
    public static final double STEP_MAX = CASE_WIDTH;
    public static final double DEATH_LINE = GAME_HEIGHT + 50;
    public static final double DUST_FALL_HEIGHT = CASE_HEIGHT * 4;

    // Joueur
    public static final double PLAYER_SPEED = 4.3;
    public static final double PLAYER_JUMP = 18.7;
    public static final double PLAYER_AIR_JUMP = 7;
    public static final double PLAYER_HKICK_X = 3.5;
    public static final double PLAYER_HKICK_Y = 7.4;
    public static final double PLAYER_VKICK = 18;
    public static final double FREEZE_DURATION = SECOND * 5;
    public static final double SHIELD_DURATION = SECOND * 5;
    public static final double WEAPON_DURATION = SECOND * 30;
    public static final double SUPA_DURATION = SECOND * 30;
    public static final int BAT_LEVEL = 100;
    public static final int TUBERCULOZ_LEVEL = 101;
    /** Lettres EXTEND (Data.EXTENDS) : C R Y S T A L. */
    public static final int EXTENDS = 7;
    public static final double KNOCK_DURATION = SECOND * 3.75;

    // Armes (Data.WEAPON_*) : bombes 1..5 et 9, tirs 6..8 ; les champs de la grille -1..-5 donnent l'arme |id|
    public static final int WEAPON_NONE = -1;
    public static final int WEAPON_B_CLASSIC = 1;
    public static final int WEAPON_B_BLACK = 2;
    public static final int WEAPON_B_BLUE = 3;
    public static final int WEAPON_B_GREEN = 4;
    public static final int WEAPON_B_RED = 5;
    public static final int WEAPON_S_ARROW = 6;
    public static final int WEAPON_S_FIRE = 7;
    public static final int WEAPON_S_ICE = 8;
    public static final int WEAPON_B_REPEL = 9;
    public static final double KICK_DISTANCE = CASE_WIDTH;
    public static final double AIR_KICK_DISTANCE = CASE_WIDTH * 1.5;
    public static final double EDGE_TIMER = SECOND * 0.2;
    public static final double WAIT_TIMER = SECOND * 8;
    public static final double PLAYER_KNOCK_DURATION = SECOND * 2.5;
    public static final double BLINK_DURATION = 2.5;
    public static final double BLINK_DURATION_FAST = 1;

    public static final int ACTION_MOVE = 1;
    public static final int ACTION_WALK = 2;
    public static final int ACTION_SHOOT = 3;
    public static final int ACTION_FALLBACK = 4;

    // Ennemis (Data.BAD_* et constantes d'IA)
    public static final int IA_CLOSE_DISTANCE = 110;
    public static final int MAX_ANGER = 3;
    public static final double ICE_HIT_MIN_SPEED = 4;
    public static final double ICE_KNOCK_MIN_SPEED = 2;
    public static final double BAD_HJUMP_X = 5.5;
    public static final double BAD_HJUMP_Y = 8.5;
    public static final double BAD_VJUMP_X_CLIFF = 2.2;
    public static final double BAD_VJUMP_X = 1.3;
    public static final double BAD_VJUMP_Y = 19;
    public static final double[] BAD_VJUMP_Y_LIST = {11, 14, 19};
    public static final double BAD_VDJUMP_Y = 6.5;
    public static final double PEACE_COOLDOWN = SECOND * 3;

    public static final int BAD_POMME = 0;
    public static final int BAD_CERISE = 1;
    public static final int BAD_BANANE = 2;
    public static final int BAD_ABRICOT = 5;
    public static final int BAD_ABRICOT2 = 6;
    public static final int BAD_POIRE = 7;
    public static final int BAD_BOMBE = 8;
    public static final int BAD_ORANGE = 9;
    public static final int BAD_FRAISE = 10;
    public static final int BAD_CITRON = 11;
    public static final int BAD_SPEAR = 13;
    public static final int BAD_LITCHI = 17;
    public static final int BAD_KIWI = 18;
    public static final int BAD_LITCHI_WEAK = 19;

    /** Data.LINKAGES : clip Flash de chaque type d'ennemi. */
    public static final String[] BAD_LINKAGES = {
            "hammer_bad_pomme", "hammer_bad_cerise", "hammer_bad_banane", "hammer_bad_fireball",
            "hammer_bad_ananas", "hammer_bad_abricot", "hammer_bad_abricot", "hammer_bad_poire",
            "hammer_bad_bombe", "hammer_bad_orange", "hammer_bad_fraise", "hammer_bad_citron",
            "hammer_bad_baleine", "hammer_bad_spear", "hammer_bad_crawler", "hammer_bad_tzongre",
            "hammer_bad_saw", "hammer_bad_litchi", "hammer_bad_kiwi", "hammer_bad_litchi_weak",
            "hammer_bad_framboise"};

    private Const() {
    }
}
