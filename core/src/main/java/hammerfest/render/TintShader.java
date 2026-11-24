package hammerfest.render;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/**
 * Filtre de couleur des entités (Entity.setColorHex) : chaque composante devient
 * {@code c × (100 - a)% + a% × couleur}, l'alpha est inchangé (flash orange du blob qui tire…).
 */
public final class TintShader implements Disposable {

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
            + "uniform vec4 u_tint;\n" // rgb, intensité 0..1
            + "void main() {\n"
            + "  vec4 c = texture2D(u_texture, v_texCoords);\n"
            + "  c.rgb = c.rgb * (1.0 - u_tint.a) + u_tint.rgb * u_tint.a;\n"
            + "  gl_FragColor = v_color * c;\n"
            + "}\n";

    private final ShaderProgram program;

    public TintShader() {
        ShaderProgram.pedantic = false;
        ShaderProgram p = new ShaderProgram(VERTEX, FRAGMENT);
        program = p.isCompiled() ? p : null;
        if (program == null) p.dispose();
    }

    /** Shader réglé sur {@code tint} (intensité 0..100 << 24 | 0xRRGGBB), ou null si indisponible. */
    public ShaderProgram forTint(int tint) {
        if (program == null) return null;
        program.bind();
        program.setUniformf("u_tint", ((tint >> 16) & 0xff) / 255f, ((tint >> 8) & 0xff) / 255f, (tint & 0xff) / 255f,
                Math.min(100, (tint >>> 24) & 0xff) / 100f);
        return program;
    }

    @Override
    public void dispose() {
        if (program != null) program.dispose();
    }
}
