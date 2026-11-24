package hammerfest.save;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerNameTest {

    @Test
    void validNames() {
        assertTrue(PlayerName.isValid("Igor"));
        assertTrue(PlayerName.isValid("Sandy_42"));
        assertTrue(PlayerName.isValid("Élodie-Chloé"));
        assertTrue(PlayerName.isValid("  Le Bonhomme  "), "espaces de bord retirés");
        assertTrue(PlayerName.isValid("abcdefghijklmnop"), "16 caractères");
    }

    @Test
    void invalidNames() {
        assertNotNull(PlayerName.validate(null));
        assertNotNull(PlayerName.validate("ab"));
        assertNotNull(PlayerName.validate("   ab   "));
        assertNotNull(PlayerName.validate("abcdefghijklmnopq"), "17 caractères");
        assertNotNull(PlayerName.validate("Igor!"));
        assertNotNull(PlayerName.validate("Ig/or"));
        assertNotNull(PlayerName.validate("名前です"), "hors Latin-1");
    }

    @Test
    void normalize() {
        assertEquals("Le Bonhomme", PlayerName.normalize("  Le    Bonhomme "));
        assertEquals("", PlayerName.normalize(null));
    }
}
