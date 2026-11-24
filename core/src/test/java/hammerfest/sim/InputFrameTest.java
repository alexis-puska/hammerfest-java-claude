package hammerfest.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputFrameTest {

    @Test
    void bits() {
        InputFrame f = new InputFrame(12, InputFrame.LEFT | InputFrame.BOMB);
        assertTrue(f.has(InputFrame.LEFT));
        assertTrue(f.has(InputFrame.BOMB));
        assertFalse(f.has(InputFrame.RIGHT));
        assertEquals(0b10001, f.bits());
        assertEquals(new InputFrame(12, 0b10001), f);
        assertThrows(IllegalArgumentException.class, () -> new InputFrame(0, 0x100));
    }

    @Test
    void highBitSurvivesByteStorage() {
        assertEquals(0x80, new InputFrame(0, 0x80).bits());
    }
}
