package hammerfest.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Clips qui jouent seuls : 40 images par seconde comme le SWF d'origine, la simulation restant à 32 cycles/s. */
class ClipCadenceTest {

    @Test
    void decorsPlayFortyFramesPerSecond() {
        Game g = BossTest.batLevel();
        g.script.addDecor("test", 0, 0, true);
        ScriptEngine.Decor d = g.script.decors.get(g.script.decors.size() - 1);
        d.playing = true;
        for (int i = 0; i < Const.SECOND; i++) g.script.tickDecors(link -> new int[]{1000, 0});
        assertEquals(41, d.frame, "32 cycles : 40 images jouées");
    }

    @Test
    void clipAgeIsFortyFramesPerSecond() {
        assertEquals(40, Const.clipAge(Const.SECOND));
        assertEquals(5, Const.clipAge(4));
    }
}
