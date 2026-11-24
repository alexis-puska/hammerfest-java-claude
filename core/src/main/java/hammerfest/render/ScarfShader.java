package hammerfest.render;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/**
 * Couleur de l'écharpe d'Igor et de Sandy selon l'arme en cours (clips 436 et 477 du SWF : gotoAndStop(currentWeapon),
 * image 11 sans arme). Les images exportées portent l'écharpe violette de la bombe classique : ce shader remplace
 * le violet (clair et foncé, et les pixels d'anticrénelage entre les deux) par les couleurs de l'arme.
 */
public final class ScarfShader implements Disposable {

    /** Couleurs du SWF (clair, foncé) par arme : index = n° d'arme (1 à 9), 0 = aucune arme. */
    static final int[][] COLORS = {
            {0x999999, 0x3b3b3b}, // -1 aucune arme (image 11)
            {0xb965fe, 0x5b02b7}, // 1 bombe classique : violet
            {0x666666, 0x000000}, // 2 bombe noire
            {0x00ccff, 0x0066ff}, // 3 bombe bleue
            {0x00ff00, 0x009900}, // 4 bombe verte
            {0xff0000, 0x990000}, // 5 bombe rouge
            {0xd5bf00, 0x009900}, // 6 flèches
            {0xff9900, 0xf74900}, // 7 boules de feu
            {0xff9900, 0xf74900}, // 8 perles (même image que le feu dans le SWF)
            {0xb965fe, 0x5b02b7}, // 9 bombe répulsive (image 9 : violet)
    };

    private static final String VERTEX = ""
            + "attribute vec4 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
            + "attribute vec4 " + ShaderProgram.COLOR_ATTRIBUTE + ";\n"
            + "attribute vec2 " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n"
            + "uniform mat4 u_projTrans;\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "void main() {\n"
            + "  v_color = " + ShaderProgram.COLOR_ATTRIBUTE + ";\n"
            + "  v_color.a = v_color.a * (255.0/254.0);\n"
            + "  v_texCoords = " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n"
            + "  gl_Position = u_projTrans * " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
            + "}\n";

    private static final String FRAGMENT = ""
            + "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform vec3 u_light;\n"
            + "uniform vec3 u_dark;\n"
            + "const vec3 V_DARK = vec3(91.0, 2.0, 183.0) / 255.0;\n"
            + "const vec3 V_LIGHT = vec3(185.0, 101.0, 254.0) / 255.0;\n"
            + "void main() {\n"
            + "  vec4 c = texture2D(u_texture, v_texCoords);\n"
            + "  vec3 axis = V_LIGHT - V_DARK;\n"
            + "  float t = clamp(dot(c.rgb - V_DARK, axis) / dot(axis, axis), 0.0, 1.0);\n"
            + "  float dist = distance(c.rgb, V_DARK + axis * t);\n"
            // pixels violets de l'écharpe (et ceux mêlés au contour) : nouvelle teinte, même éclairage
            + "  float k = 1.0 - smoothstep(0.10, 0.22, dist);\n"
            + "  c.rgb = mix(c.rgb, mix(u_dark, u_light, t), k);\n"
            + "  gl_FragColor = v_color * c;\n"
            + "}\n";

    private final ShaderProgram program;

    public ScarfShader() {
        ShaderProgram.pedantic = false;
        ShaderProgram p = new ShaderProgram(VERTEX, FRAGMENT);
        program = p.isCompiled() ? p : null;
        if (program == null) p.dispose();
    }

    /** Shader prêt pour l'arme {@code weapon} (-1 = aucune), ou null si la carte graphique l'a refusé. */
    public ShaderProgram forWeapon(int weapon) {
        if (program == null) return null;
        int[] c = COLORS[weapon <= 0 || weapon >= COLORS.length ? 0 : weapon];
        program.bind();
        program.setUniformf("u_light", ((c[0] >> 16) & 0xff) / 255f, ((c[0] >> 8) & 0xff) / 255f, (c[0] & 0xff) / 255f);
        program.setUniformf("u_dark", ((c[1] >> 16) & 0xff) / 255f, ((c[1] >> 8) & 0xff) / 255f, (c[1] & 0xff) / 255f);
        return program;
    }

    @Override
    public void dispose() {
        if (program != null) program.dispose();
    }
}
