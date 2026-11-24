package hammerfest.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScriptParseTest {

    /** Std.parseInt de Flash : chiffres en tête (« 18² » du niveau 15 de l'aventure, « 4²0 » de SoccerFest). */
    @Test
    void numbersAreReadLikeFlash() {
        assertEquals(18, ScriptEngine.parseLeadingInt("18²"));
        assertEquals(4, ScriptEngine.parseLeadingInt("4²0"));
        assertEquals(-3, ScriptEngine.parseLeadingInt(" -3"));
        assertEquals(12, ScriptEngine.parseLeadingInt("12.7"));
        assertNull(ScriptEngine.parseLeadingInt("abc"));
        assertNull(ScriptEngine.parseLeadingInt(""));
    }
}
