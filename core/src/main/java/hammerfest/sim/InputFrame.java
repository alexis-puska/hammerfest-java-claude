package hammerfest.sim;

/**
 * Entrées d'un joueur pour un tick de simulation (40 Hz), sur 1 octet de bits.
 * Toute la simulation consomme des InputFrame, y compris en solo : c'est ce qui
 * permettra à l'hôte multi de rejouer les entrées reçues du réseau.
 *
 * <p>Aucune dépendance libGDX dans ce paquet.</p>
 */
public final class InputFrame {

    public static final int LEFT = 1;
    public static final int RIGHT = 1 << 1;
    public static final int UP = 1 << 2;
    public static final int DOWN = 1 << 3;
    public static final int BOMB = 1 << 4;
    public static final int PAUSE = 1 << 5;

    public final int tick;
    public final byte bits;

    public InputFrame(int tick, int bits) {
        if ((bits & ~0xFF) != 0) throw new IllegalArgumentException("bits hors octet : " + bits);
        this.tick = tick;
        this.bits = (byte) bits;
    }

    public boolean has(int flag) {
        return (bits & flag) != 0;
    }

    public int bits() {
        return bits & 0xFF;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof InputFrame && ((InputFrame) o).tick == tick && ((InputFrame) o).bits == bits;
    }

    @Override
    public int hashCode() {
        return tick * 31 + bits;
    }

    @Override
    public String toString() {
        return "InputFrame{tick=" + tick + ", bits=" + Integer.toBinaryString(bits()) + "}";
    }
}
