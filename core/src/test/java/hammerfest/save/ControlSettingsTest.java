package hammerfest.save;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ControlSettingsTest {

    static final List<String> TWO = List.of("Xbox Wireless Controller", "DualSense Wireless Controller");

    @Test
    void automaticAssignmentFollowsPlugOrder() {
        ControlSettings s = new ControlSettings();
        assertArrayEquals(new int[]{0, 1}, s.resolve(TWO));
        assertArrayEquals(new int[]{0, -1}, s.resolve(List.of("Xbox Wireless Controller")), "Sandy au clavier");
    }

    @Test
    void choosingTheOtherPadSwapsThem() {
        ControlSettings s = new ControlSettings();
        s.assign(0, "DualSense Wireless Controller#0", TWO);
        assertArrayEquals(new int[]{1, 0}, s.resolve(TWO), "Igor prend la DualSense, Sandy la Xbox");
        s.assigned[1] = "-";
        assertArrayEquals(new int[]{1, -1}, s.resolve(TWO), "Sandy sans manette");
    }

    @Test
    void identicalPadsAreDistinguished() {
        List<String> same = List.of("Xbox Wireless Controller", "Xbox Wireless Controller");
        assertEquals(List.of("Xbox Wireless Controller#0", "Xbox Wireless Controller#1"), ControlSettings.keys(same));
        ControlSettings s = new ControlSettings();
        s.assign(1, "Xbox Wireless Controller#0", same);
        assertArrayEquals(new int[]{1, 0}, s.resolve(same));
    }

    @Test
    void buttonsCanShareAnAction() {
        ControlSettings s = new ControlSettings();
        // bas = saut, droite = carte, gauche = bombe, haut = bombe en haut, RB = bombe, LB = bombe en haut
        assertEquals(ControlSettings.BUTTONS, s.faces[0].length);
        assertEquals(ControlSettings.BOMB, s.faces[0][ControlSettings.R1]);
        s.setButton(0, ControlSettings.R2, ControlSettings.JUMP);
        assertEquals(ControlSettings.JUMP, s.faces[0][ControlSettings.R2]);
        assertEquals(ControlSettings.JUMP, s.faces[0][ControlSettings.FACE_BOTTOM], "A garde le saut");
        s.setButton(0, ControlSettings.FACE_BOTTOM, ControlSettings.NONE);
        s.setButton(0, ControlSettings.R2, ControlSettings.NONE);
        assertTrue(s.missing(0, ControlSettings.JUMP));
        assertFalse(s.missing(1, ControlSettings.JUMP), "Sandy inchangée");
    }

    @Test
    void sanitizeUpgradesOldSavesAndRepairsBadValues() {
        ControlSettings s = new ControlSettings();
        s.faces = new int[][]{{ControlSettings.MAP, ControlSettings.JUMP, ControlSettings.BOMB, ControlSettings.BOMB_UP},
                {0, 9, 1, 2}};
        s.deadZone = 3;
        s.sanitize();
        int[] old = ControlSettings.defaultButtons();
        old[0] = ControlSettings.MAP;
        old[1] = ControlSettings.JUMP;
        assertArrayEquals(old, s.faces[0], "ancien réglage à 4 boutons conservé et complété");
        assertArrayEquals(ControlSettings.defaultButtons(), s.faces[1], "action inconnue : réglage d'origine");
        assertEquals(0.9f, s.deadZone, 1e-6);
        s.faces = new int[][]{null, new int[3]};
        s.sanitize();
        assertArrayEquals(ControlSettings.defaultButtons(), s.faces[0]);
        assertArrayEquals(ControlSettings.defaultButtons(), s.faces[1]);
    }

    @Test
    void detectsPlayStationPads() {
        assertTrue(ControlSettings.isPlayStation("DualSense Wireless Controller"));
        assertTrue(ControlSettings.isPlayStation("PS4 Controller"));
        assertFalse(ControlSettings.isPlayStation("Xbox Wireless Controller"));
    }
}
