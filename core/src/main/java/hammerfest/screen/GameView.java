package hammerfest.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import hammerfest.HammerfestGame;
import hammerfest.data.GameModes;
import hammerfest.data.LevelData;
import hammerfest.net.RenderFrame;
import hammerfest.render.LevelRenderer;
import hammerfest.render.Sprites;
import hammerfest.sim.Const;
import hammerfest.sim.Game;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Dessin d'une partie à partir d'un {@link RenderFrame} seulement (aucun accès à la simulation) : niveau,
 * entités, décors, effets, particules, obscurité, interface, pause et carte. Sert en solo comme pour le
 * client d'une partie en ligne, qui ne reçoit que ces instantanés.
 */
public final class GameView implements Disposable {

    private final HammerfestGame app;
    private final GameModes.Mode mode;
    final SpriteBatch batch = new SpriteBatch();
    /** Second bandeau (sous celui d'origine) : niveau, joueurs, aide. Hauteur en px du document. */
    public static final int BAND_HEIGHT = 24;
    /** Vue : document 420 × 520 d'origine, prolongé de BAND_HEIGHT vers le bas (y monde de -BAND_HEIGHT à 520). */
    final FitViewport viewport = new FitViewport(LevelRenderer.DOC_WIDTH, LevelRenderer.DOC_HEIGHT + BAND_HEIGHT);
    private final LevelRenderer levelRenderer = new LevelRenderer();
    final Sprites sprites;
    private final hammerfest.render.FxPlayer fx;
    private final hammerfest.render.ParticleSystem particles;
    private final java.util.Random rng = new java.util.Random();
    private final hammerfest.render.Audio audio = new hammerfest.render.Audio();
    private final hammerfest.render.DarknessRenderer darknessRenderer = new hammerfest.render.DarknessRenderer();
    private final BitmapFont font = gameFont();
    /** Texte du parchemin (champ Verdana 12 px : police du système, comme le lecteur Flash), couleurs par balises. */
    private final BitmapFont popFont = loadFont(hammerfest.render.Fonts.verdana(), 12, true);
    /** En-tête « Apprentissage » du parchemin (police Satans 19 px du SWF). */
    private final BitmapFont headerFont = loadFont(Gdx.files.internal("fonts/satans.ttf"), 19, false);
    /** Libellé gris au-dessus du nom de la contrée (Franklin Gothic 12 px). */
    private final BitmapFont smallFont = loadFont(Gdx.files.internal("fonts/hammerfest.ttf"), 12, false);

    /**
     * Police du jeu : Franklin Gothic Heavy exportée du SWF (assets/fonts/hammerfest.ttf, voir export-gfx.sh),
     * 16 px comme les champs du bandeau ; police par défaut de libGDX si le fichier manque.
     */
    static BitmapFont gameFont() {
        return loadFont(Gdx.files.internal("fonts/hammerfest.ttf"), 16, false);
    }

    static BitmapFont loadFont(com.badlogic.gdx.files.FileHandle ttf, int size, boolean markup) {
        return hammerfest.render.Fonts.load(ttf, size, markup);
    }
    private final Texture white;

    /** Niveau affiché (nom du set, dimension, niveau) et grille reçue. */
    private String shownKey;
    private int[] grid;
    private int shownGridVersion = -1;

    /** Pause et carte (état local de l'écran, pas de la partie). */
    public boolean paused, mapOpen;
    public int tipIndex = -1;

    public GameView(HammerfestGame app, GameModes.Mode mode) {
        this.app = app;
        this.mode = mode;
        this.sprites = new Sprites("anims", "gfx", "ending", "sandy", "soccer1", "soccer2", "igor",
                "head2", "head3", "head4", "head5", "head6", "igor-chourou", "sandy-chourou",
                "head2-chourou", "head3-chourou", "head4-chourou", "head5-chourou", "head6-chourou",
                "igor-lock", "sandy-lock", "head2-lock", "head3-lock", "head4-lock", "head5-lock", "head6-lock",
                "soccer1-crown", "soccer1-lose", "soccer2-crown", "soccer2-lose");
        this.fx = new hammerfest.render.FxPlayer(sprites);
        this.particles = new hammerfest.render.ParticleSystem(sprites);
        // Particle.onHitGround : goutte d'eau à 10-59 %, retournée au hasard
        particles.onRainDrop = (x, y) -> fx.attach("hammer_fx_water_drop", x, y, rng.nextBoolean() ? 1 : -1, 1, 0,
                (rng.nextInt(50) + 10) / 100f);
        levelRenderer.setSprites(sprites);
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        white = new Texture(p);
        p.dispose();
    }

    /** Images des clips, pour l'animation des décors côté simulation (ScriptEngine.tickDecors). */
    public Sprites sprites() {
        return sprites;
    }

    /** GameMode.fl_map : carte disponible en Aventure et en coopératif. */
    public boolean hasMap() {
        return "adventure".equals(mode.id) || "multicoop".equals(mode.id);
    }

    // ------------------------------------------------------------------ cycles et événements

    /**
     * Applique un nouvel instantané : niveau et grille, puis {@code cycles} cycles d'animation des effets et
     * particules, puis les événements (sons, effets, particules, messages).
     */
    public void advance(RenderFrame f, int cycles) {
        String key = f.setName + "/" + f.currentDim + "/" + f.levelId;
        if (!key.equals(shownKey)) {
            fx.clear();
            particles.clear();
        }
        if (f.grid != null) grid = f.grid;
        if (!key.equals(shownKey) || f.gridVersion != shownGridVersion) {
            if (grid != null) {
                LevelData data = app.data.levels(f.setName).level(f.levelId);
                int[][] g2 = new int[Const.LEVEL_WIDTH][Const.LEVEL_HEIGHT];
                for (int x = 0; x < Const.LEVEL_WIDTH; x++)
                    for (int y = 0; y < Const.LEVEL_HEIGHT; y++) g2[x][y] = RenderFrame.caseAt(grid, x, y);
                levelRenderer.setLevel(data, g2);
                shownKey = key;
                shownGridVersion = f.gridVersion;
            }
        }
        levelRenderer.mirror = f.mirror;
        levelRenderer.hideTiles = f.hideTiles;
        levelRenderer.hideBorders = f.hideBorders;
        for (int i = 0; i < cycles; i++) {
            portalStars(f);
            exitAge = f.exitArrow ? exitAge + 1 : -1;
            pointerAge = f.pointer != null ? pointerAge + 1 : -1;
            tickKeyMessage();
            tickGuuClouds(f);
            fx.tick();
            particles.tick((cx, cy) -> RenderFrame.caseAt(grid, cx, cy));
            aquaBubbles(f);
        }
        events(f);
        audio.playMusic(MUSICS[Math.max(0, Math.min(MUSICS.length - 1, f.music))]);
    }

    private void events(RenderFrame f) {
        if (f.eventNames == null) return;
        for (int i = 0; i < f.eventNames.length; i++) {
            String name = f.eventNames[i];
            float ex = f.eventXs[i], ey = f.eventYs[i];
            if (f.eventSounds[i]) {
                audio.play(name);
            } else if (name.startsWith("score:")) {
                popups.add(new float[]{ex, ey, 0, Integer.parseInt(name.substring(6)), 0});
            } else if (name.startsWith("part:")) {
                String[] p = name.substring(5).split("\\|");
                int rand = Integer.parseInt(p[2]);
                int n = Integer.parseInt(p[1]) + (rand > 0 ? rng.nextInt(rand) : 0);
                particles.spawn(Integer.parseInt(p[0]), ex, ey, n, Double.parseDouble(p[3]));
            } else if (name.startsWith("levelpop:")) {
                String[] parts = name.substring(9).split("\\|");
                if (app.data.lang.has(parts[0])) {
                    levelPopName = app.data.lang.plain(parts[0]);
                    levelPopLabel = parts.length > 1 && parts[1].equals("1") ? app.data.lang.plain("static.13") : "";
                    levelPopTime = 0;
                }
            } else if (name.startsWith("alert:")) {
                String[] parts = name.substring(6).split("\\|", 3);
                alertText = (parts.length > 2 ? parts[2] : "") + app.data.lang.get(parts[0]) + (parts.length > 1 ? parts[1] : "");
                alertTime = 0;
            } else if (name.startsWith("explodeZone|")) { // attachExplosion / attachExplodeZone : taille et fusion d'origine
                String[] p = name.split("\\|");
                float w = sprites.clipRect("explodeZone", clipBox) ? clipBox[2] : 100;
                float s = 2 * Float.parseFloat(p[1]) / Math.max(1, w);
                float a = p.length > 3 ? Float.parseFloat(p[3]) / 100f : 1;
                if (a < 1) fx.attach("explodeZone", ex, ey, s, s, 0, a);
                else fx.attach("explodeZone", ex, ey, s, "1".equals(p[2]));
            } else if (name.startsWith("tf|")) { // attachFx retouché : nom|rotation|xscale|yscale|alpha (pourcentages)
                String[] p = name.split("\\|");
                fx.attach(p[1], ex, ey, Float.parseFloat(p[3]) / 100f, Float.parseFloat(p[4]) / 100f,
                        Float.parseFloat(p[2]), Float.parseFloat(p[5]) / 100f);
            } else if (name.startsWith("dust:")) { // FxManager.dust : 7 grains, largeur de la dalle
                int wid = Math.max(1, Integer.parseInt(name.substring(5)));
                for (int k = 0; k < 7; k++) {
                    float xs = (rng.nextInt(50) + 50 * (rng.nextInt(2) * 2 - 1)) / 100f; // priorité d'origine
                    fx.attach("hammer_fx_dust", ex + rng.nextInt(wid), ey, xs, (rng.nextInt(80) + 10) / 100f, 0,
                            (rng.nextInt(50) + 50) / 100f, rng.nextInt(5) + 5);
                }
            } else if (name.startsWith("keyMsg:")) { // FxManager.keyRequired / keyUsed
                String[] p = name.substring(7).split("\\|");
                keyMsgLabel = app.data.lang.plain(p[0]);
                keyMsgField = app.data.lang.has(p[1]) ? app.data.lang.plain(p[1]) : "";
                keyMsgTimer = Integer.parseInt(p[2]) * Const.SECOND;
                keyMsgAlpha = 1;
            } else if (name.startsWith("time:")) {
                popups.add(new float[]{ex, ey, 0, Integer.parseInt(name.substring(5)), 1});
            } else {
                if (name.startsWith("hurryUp")) { // FxManager.attachHurryUp : alerte « VITE ! » (Lang 4)
                    alert(app.data.lang.plain("static.4"));
                    continue;
                }
                fx.attach(name, ex, ey);
            }
        }
    }

    /** Nuages de la poupée Guu par joueur (identifiant) : x, y, âge. */
    private final java.util.Map<Integer, float[]> guuClouds = new java.util.HashMap<>();

    /**
     * SpecialManager (objet 100) : hammer_fx_cloud collé 80 px au-dessus du joueur avec retard élastique
     * (Entity.setElaStick(0.4) : pos += (cible - pos) × 0,4 - 32).
     */
    private void tickGuuClouds(RenderFrame f) {
        java.util.Set<Integer> alive = new java.util.HashSet<>();
        for (int i = 0; f.ids != null && i < f.ids.length; i++) {
            if ((f.extras[i] & (1 << 25)) == 0) continue;
            alive.add(f.ids[i]);
            float[] c = guuClouds.get(f.ids[i]);
            if (c == null) guuClouds.put(f.ids[i], new float[]{f.xs[i], f.ys[i] - 80, 0});
            else {
                c[0] += (f.xs[i] - c[0]) * 0.4f;
                c[1] += (f.ys[i] - c[1]) * 0.4f - 32;
                c[2]++;
            }
        }
        guuClouds.keySet().retainAll(alive);
    }

    private void drawGuuClouds(RenderFrame f) {
        for (float[] c : guuClouds.values()) {
            sprites.draw(batch, "hammer_fx_cloud", -1, sprites.playFrame("hammer_fx_cloud", (long) c[2]),
                    LevelRenderer.X_OFFSET + c[0], c[1], 1, 1, LevelRenderer.DOC_HEIGHT);
        }
    }

    /** Âge (cycles) de la flèche de sortie et du pointeur du tutoriel affichés, -1 = absents. */
    private long exitAge = -1, pointerAge = -1;

    /**
     * Flèche « niveau suivant » (hammer_fx_exit en bas au centre, DP_INTERF) : monte (images 1-6), puis rebondit en
     * boucle ; libellé Lang 3 (Verdana 12, blanc, centré) à partir de l'image 7. Pointeur du tutoriel
     * (hammer_fx_pointer) tourné vers le joueur.
     */
    private void drawExitAndPointer(RenderFrame f) {
        if (f.exitArrow && exitAge >= 0 && sprites.has("hammer_fx_exit")) {
            float x = LevelRenderer.X_OFFSET + Const.GAME_WIDTH / 2f, y = Const.GAME_HEIGHT;
            int fr = sprites.playFrame("hammer_fx_exit", exitAge);
            sprites.draw(batch, "hammer_fx_exit", -1, fr, x, y, 1, 1, LevelRenderer.DOC_HEIGHT);
            if (fr >= 7) fieldText(popFont, app.data.lang.plain("static.3"), x - 92, x + 92.05f, y - 18.5f, 2, Color.WHITE, 1, false);
        }
        if (f.pointer != null && pointerAge >= 0 && sprites.has("hammer_fx_pointer")) {
            sprites.draw(batch, "hammer_fx_pointer", -1, sprites.playFrame("hammer_fx_pointer", pointerAge),
                    LevelRenderer.X_OFFSET + f.pointer[0], f.pointer[1], 1, 1, f.pointer[2], LevelRenderer.DOC_HEIGHT);
        }
    }

    /** Message en jeu (hammer_interf_inGameMsg en haut à gauche) : clé requise ou utilisée. */
    private String keyMsgLabel, keyMsgField;
    private float keyMsgTimer, keyMsgAlpha;
    /** Champ « field » du message (Franklin Gothic 14 px). */
    private final BitmapFont msgFont = loadFont(Gdx.files.internal("fonts/hammerfest.ttf"), 14, false);

    /** FxManager.main : minuterie, puis _alpha -= 2 par cycle. */
    private void tickKeyMessage() {
        if (keyMsgLabel == null) return;
        keyMsgTimer--;
        if (keyMsgTimer <= 0) keyMsgAlpha -= 0.02f;
        if (keyMsgAlpha <= 0) keyMsgLabel = null;
    }

    /** Libellé (12 px, gris 219) puis nom de la clé (14 px, blanc), contour noir (addGlow 0x0, 2). */
    private void drawKeyMessage() {
        if (keyMsgLabel == null) return;
        float x = LevelRenderer.X_OFFSET;
        fieldText(smallFont, keyMsgLabel, x, x + 235.45f, 0, 0, new Color(219 / 255f, 219 / 255f, 219 / 255f, 1), keyMsgAlpha, true);
        fieldText(msgFont, keyMsgField, x, x + 236.9f, 13.1f, 0, Color.WHITE, keyMsgAlpha, true);
    }

    /**
     * Champ texte d'un clip, de x0 à x1 (document), haut du champ à {@code top} ; aligné 0 gauche, 1 droite,
     * 2 centre ; contour noir facultatif.
     */
    private void fieldText(BitmapFont bf, String text, float x0, float x1, float top, int align, Color color, float alpha,
                           boolean glow) {
        if (text == null || text.isEmpty()) return;
        GlyphLayout l = new GlyphLayout(bf, text);
        float x = align == 0 ? x0 + 2 : align == 1 ? x1 - 2 - l.width : (x0 + x1 - l.width) / 2f;
        float y = LevelRenderer.DOC_HEIGHT - top - 2;
        if (glow) {
            bf.setColor(0, 0, 0, 0.85f * alpha);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) bf.draw(batch, text, x + dx, y + dy);
        }
        bf.setColor(color.r, color.g, color.b, alpha);
        bf.draw(batch, text, x, y);
        bf.setColor(Color.WHITE);
    }

    /** Niveaux aquatiques : bulles derrière les joueurs et ennemis qui bougent (Physics.endUpdate, 10 % par cycle). */
    private void aquaBubbles(RenderFrame f) {
        if (!f.aqua || f.ids == null) return;
        for (int i = 0; i < f.ids.length; i++) {
            if ((f.extras[i] & (1 << 8)) == 0 || rng.nextInt(100) > 10) continue;
            particles.spawn(hammerfest.render.ParticleSystem.BUBBLE,
                    f.xs[i] + rng.nextInt(Const.CASE_WIDTH) * (rng.nextBoolean() ? 1 : -1),
                    f.ys[i] - rng.nextInt(Const.CASE_HEIGHT), 1, Double.NaN);
        }
    }

    /**
     * Étoiles autour des portails (GameMechanics : champs portail, GameMode : portails flottants) : une chance
     * sur 5 par cycle et par portail, taille 30 à 99 %.
     */
    private void portalStars(RenderFrame f) {
        for (int[] p : levelRenderer.portalFields()) {
            if (rng.nextInt(5) != 0) continue;
            float s = (rng.nextInt(70) + 30) / 100f;
            fx.attach("hammer_fx_star", p[0] * Const.CASE_WIDTH + Const.CASE_WIDTH * 0.5 + rng.nextInt(15) * (rng.nextInt(2) * 2 - 1),
                    p[1] * Const.CASE_HEIGHT + Const.CASE_WIDTH * 0.5 + rng.nextInt(15) * (rng.nextInt(2) * 2 - 1), s);
        }
        for (int i = 0; f.portals != null && i + 1 < f.portals.length; i += 2) {
            if (rng.nextInt(5) != 0) continue;
            float s = (rng.nextInt(70) + 30) / 100f;
            fx.attach("hammer_fx_star", f.portals[i] + rng.nextInt(25) * (rng.nextInt(2) * 2 - 1),
                    f.portals[i + 1] + rng.nextInt(25) * (rng.nextInt(2) * 2 - 1), s);
        }
    }

    /** Pistes du Loader d'origine : music.mp3, boss.mp3, hurryUp.mp3. */
    private static final String[] MUSICS = {"music", "boss", "hurryUp"};

    // ------------------------------------------------------------------ dessin

    public void render(RenderFrame f) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (shownKey == null) return; // niveau pas encore reçu
        sprites.animClock = f.cycle;
        viewport.apply();
        float shakeX = 0, shakeY = 0;
        if (f.shakeTimer > 0 && f.shakeTotal > 0) {
            float amp = f.shakePower * f.shakeTimer / f.shakeTotal;
            shakeX = (float) ((Math.random() * 2 - 1) * amp);
            shakeY = (float) ((Math.random() * 2 - 1) * amp);
        }
        viewport.getCamera().position.add(-shakeX, shakeY, 0);
        viewport.getCamera().update();
        // zone de jeu, éventuellement en miroir (objets spéciaux 30 / 31) ; l'interface reste droite
        com.badlogic.gdx.math.Matrix4 worldMatrix = viewport.getCamera().combined.cpy();
        if (f.flipX) worldMatrix.translate(LevelRenderer.DOC_WIDTH, 0, 0).scale(-1, 1, 1);
        if (f.flipY) worldMatrix.translate(0, LevelRenderer.DOC_HEIGHT, 0).scale(1, -1, 1);
        batch.setProjectionMatrix(worldMatrix);
        batch.begin();
        if (f.specialBgId >= 0) {
            int subFrames = Math.max(1, sprites.frames("hammer_special_bg", f.specialBgId));
            int sub = f.specialBgSub >= 0 ? f.specialBgSub + 1 : (f.cycle % subFrames) + 1;
            levelRenderer.specialBg = new int[]{f.specialBgId, sub};
        } else {
            levelRenderer.specialBg = null;
        }
        levelRenderer.render(batch);
        drawDecors(f, true); // _sprite_back
        drawFloatingPortals(f);
        drawEntities(f);
        drawGuuClouds(f);
        particles.render(batch, LevelRenderer.X_OFFSET, LevelRenderer.DOC_HEIGHT);
        fx.render(batch, LevelRenderer.X_OFFSET, LevelRenderer.DOC_HEIGHT);
        // colonnes (View._top, DP_TOP_LAYER) puis décors du dessus (_sprite_top, DP_SPRITE_TOP_LAYER) : les rochers
        // du niveau 1 recouvrent le haut des colonnes
        levelRenderer.renderFront(batch);
        drawDecors(f, false);
        drawExitAndPointer(f);
        batch.end();

        List<float[]> holes = new ArrayList<>();
        for (int i = 0; f.holes != null && i + 2 < f.holes.length; i += 3) {
            holes.add(new float[]{LevelRenderer.X_OFFSET + f.holes[i], f.holes[i + 1], f.holes[i + 2]});
        }
        darknessRenderer.render(batch, worldMatrix, viewport, f.darkness, holes);

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        drawMessage(f);
        drawKeyMessage();
        drawHud(f);
        batch.end();
        viewport.getCamera().position.add(shakeX, -shakeY, 0);
        viewport.getCamera().update();
    }

    /** Costume du joueur (skin) → variante exportée du clip (PlayerVariants), si ses images sont présentes. */
    private static final String[] PLAYER_VARIANTS = {null, null, "hammer_player_sandy", "hammer_player_soccer1", "hammer_player_soccer2"};

    /** Écharpe aux couleurs de l'arme en cours. */
    private final hammerfest.render.ScarfShader scarf = new hammerfest.render.ScarfShader();
    /** Cycle d'apparition de chaque entité (identifiant) : horloge de ses clips qui jouent tout seuls. */
    private final java.util.Map<Integer, Long> births = new java.util.HashMap<>();
    /** Filtre de couleur des entités (setColorHex). */
    private final hammerfest.render.TintShader tint = new hammerfest.render.TintShader();
    /**
     * Boule de feu du hurry up (FireBall.endUpdate) : corps animé tourné de l'angle de déplacement,
     * yeux à l'image round(angle / 360 × nombre d'images) + 1.
     */
    private boolean drawFireBall(RenderFrame f, int i, String link, float x) {
        if (!"hammer_bad_fireball".equals(link)) return false;
        int bodyFrames = sprites.partFrames(link, "body"), eyeFrames = sprites.partFrames(link, "eyes");
        if (bodyFrames == 0 || eyeFrames == 0) return false;
        float ang = f.subTransforms != null ? f.subTransforms[i * 5 + 4] % 360 : 270;
        float sx = f.xscales[i] * f.scales[i], sy = f.scales[i];
        sprites.drawPart(batch, link, "body", (int) (sprites.animClock % bodyFrames) + 1, x, f.ys[i], sx, sy, ang,
                LevelRenderer.DOC_HEIGHT);
        sprites.drawPart(batch, link, "eyes", Math.round(ang / 360f * eyeFrames) + 1, x, f.ys[i], sx, sy, Float.NaN,
                LevelRenderer.DOC_HEIGHT);
        return true;
    }

    private final float[] clipBox = new float[4];

    /** Halo des torches (clip 66, cercles blancs en mode « overlay ») : éclaircit ce qu'il recouvre. */
    private final com.badlogic.gdx.graphics.Texture torchGlow = torchGlowTexture();
    private final float[] torchBounds = new float[4];

    /**
     * Cercles du clip 66 (diamètre 118 px) : opacités 30 % (rayon 37), 15 % (52) et 10 % (59). En mode overlay
     * du blanc sur une couleur sombre la multiplie par (1 + opacité) : la texture porte cette opacité en couleur.
     */
    private static com.badlogic.gdx.graphics.Texture torchGlowTexture() {
        int size = 118;
        com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(size, size,
                com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double r = Math.hypot(x + 0.5 - size / 2.0, y + 0.5 - size / 2.0);
                float k = r <= 37 ? 0.30f : r <= 52 ? 0.15f : r <= 59 ? 0.10f : 0;
                p.setColor(k, k, k, 1);
                p.drawPixel(x, y);
            }
        }
        com.badlogic.gdx.graphics.Texture t = new com.badlogic.gdx.graphics.Texture(p);
        p.dispose();
        return t;
    }

    /**
     * Variante du clip du joueur : déguisement d'Igor (têtes 2 à 6), sinon selon le costume ; collier de Poils de
     * Chourou (suffixe -chourou) seulement si l'objet est actif. Les variantes exportées cachent le collier.
     */
    private String playerLinkage(String link, int extra) {
        int skin = extra & 0xff, head = (extra >> 9) & 0x7f;
        boolean chourou = (extra & (1 << 24)) != 0;
        boolean noWeapon = ((extra >> 16) & 0xff) == 0; // arme -1 : écharpe grise et cadenas
        String v;
        if (skin <= 1 && head >= 2 && head <= 6) v = "hammer_player_head" + head;
        else if (skin < PLAYER_VARIANTS.length && PLAYER_VARIANTS[skin] != null) v = PLAYER_VARIANTS[skin];
        else v = "hammer_player_igor";
        if (skin == 3 || skin == 4) { // SoccerFest : couronne du buteur, tête basse contre son camp
            String mood = head == 9 || head == 12 ? "-crown" : head == 8 || head == 11 ? "-lose" : "";
            if (!mood.isEmpty() && sprites.has(v + mood)) return v + mood;
        }
        if (noWeapon && sprites.has(v + "-lock")) return v + "-lock";
        if (chourou && sprites.has(v + "-chourou")) return v + "-chourou";
        if (sprites.has(v)) return v;
        return link;
    }

    private void drawEntities(RenderFrame f) {
        if (f.ids == null) return;
        int playerCount = f.playerEntities;
        if (playerCount == 0) drawShields(f);
        for (int i = 0; i < f.ids.length; i++) {
            String link = f.links[i];
            com.badlogic.gdx.graphics.glutils.ShaderProgram shader = null;
            if (i < playerCount) {
                link = playerLinkage(link, f.extras[i]);
                int weapon = ((f.extras[i] >> 16) & 0xff) - 1;
                if (weapon != 1) shader = scarf.forWeapon(weapon); // bombe classique : l'écharpe est déjà violette
            }
            float x = LevelRenderer.X_OFFSET + f.xs[i];
            // clips qui jouent tout seuls : depuis l'apparition de l'entité (pièce qui rebondit puis tourne)
            Long born = births.get(f.ids[i]);
            if (born == null) births.put(f.ids[i], born = (long) f.cycle);
            sprites.animClock = Math.max(0, f.cycle - born);
            if (shader == null && f.tints != null && f.tints[i] != 0) shader = tint.forTint(f.tints[i]);
            if (shader != null) batch.setShader(shader);
            batch.setColor(1, 1, 1, f.alphas[i]);
            sprites.innerRotation = f.innerRotations != null ? f.innerRotations[i] : Float.NaN;
            boolean drawn = drawFireBall(f, i, link, x)
                    || sprites.draw(batch, link, f.anims[i], f.subs[i], x, f.ys[i],
                    f.xscales[i], f.scales[i], f.rotations[i], LevelRenderer.DOC_HEIGHT, f.subTransforms, i * 5);
            sprites.innerRotation = Float.NaN;
            if (shader != null) batch.setShader(null);
            if (!drawn) { // sprites non exportés : silhouette provisoire
                batch.setColor(0.95f, 0.95f, 1f, 1f);
                batch.draw(white, x - 10, LevelRenderer.DOC_HEIGHT - f.ys[i], 20, 20);
            }
            batch.setColor(Color.WHITE);
            if (i == playerCount - 1) drawShields(f); // bulles juste au-dessus des joueurs
        }
        sprites.animClock = f.cycle;
        if (births.size() > 512) { // oubli des entités disparues
            java.util.Set<Integer> alive = new java.util.HashSet<>();
            for (int id : f.ids) alive.add(id);
            births.keySet().retainAll(alive);
        }
        // blocs de glace et icônes ninja par-dessus les ennemis
        for (int i = 0; f.ice != null && i + 3 < f.ice.length; i += 4) {
            batch.setColor(1, 1, 1, 0.85f);
            sprites.draw(batch, "hammer_bad_ice", -1, 1, LevelRenderer.X_OFFSET + f.ice[i], f.ice[i + 1],
                    f.ice[i + 2], f.ice[i + 3], LevelRenderer.DOC_HEIGHT);
            batch.setColor(Color.WHITE);
        }
        for (int i = 0; f.ninja != null && i + 2 < f.ninja.length; i += 3) {
            sprites.draw(batch, "hammer_interf_ninjaIcon", -1, (int) f.ninja[i + 2], LevelRenderer.X_OFFSET + f.ninja[i],
                    f.ninja[i + 1], 1, 1, LevelRenderer.DOC_HEIGHT);
        }
    }

    /** Bulles de bouclier (hammer_player_shield en boucle). */
    private void drawShields(RenderFrame f) {
        // bulle : formation (images 1 à 18) une fois, puis boucle à partir de l'étiquette « loop »
        for (int i = 0; f.shields != null && i + 2 < f.shields.length; i += 3) {
            sprites.draw(batch, "hammer_player_shield", -1, sprites.playFrame("hammer_player_shield", (long) f.shields[i + 2]),
                    LevelRenderer.X_OFFSET + f.shields[i], f.shields[i + 1], 1, 1, LevelRenderer.DOC_HEIGHT);
        }
    }

    /** Portails flottants ouverts par script (hammer_portal), avec leur flottement. */
    private void drawFloatingPortals(RenderFrame f) {
        int frames = Math.max(1, sprites.frames("hammer_portal", -1));
        for (int i = 0; f.portals != null && i + 1 < f.portals.length; i += 2) {
            float bob = (float) (2 * Math.sin(f.cycle * 0.1));
            sprites.draw(batch, "hammer_portal", -1, 1 + f.cycle % frames,
                    LevelRenderer.X_OFFSET + f.portals[i], f.portals[i + 1] + bob, 1, 1, LevelRenderer.DOC_HEIGHT);
        }
    }

    /** levels.View.xOffset. */
    private static final float VIEW_X_OFFSET = 10;

    /** Décors posés par le script du niveau (e_mc), calques _sprite_back/_sprite_top décalés de -xOffset. */
    private void drawDecors(RenderFrame f, boolean back) {
        if (f.decorLinks == null) return;
        for (int i = 0; i < f.decorLinks.length; i++) {
            if (((f.decorFlags[i] & 1) != 0) != back) continue;
            sprites.draw(batch, f.decorLinks[i], -1, f.decorFrames[i], LevelRenderer.X_OFFSET - VIEW_X_OFFSET + f.decorXs[i],
                    f.decorYs[i], (f.decorFlags[i] & 2) != 0 ? -1 : 1, 1, LevelRenderer.DOC_HEIGHT);
            if ("torch".equals(f.decorLinks[i])) drawTorchGlow(f, i);
        }
    }

    /**
     * Halo de la torche : présent sur toutes les images jouées de la flamme (clip 72 : l'image 8, qui le retire,
     * saute aussitôt à l'image 1 ou 2).
     */
    private void drawTorchGlow(RenderFrame f, int i) {
        if (!sprites.clipRect("torch", torchBounds)) return;
        float flip = (f.decorFlags[i] & 2) != 0 ? -1 : 1;
        float cx = LevelRenderer.X_OFFSET - VIEW_X_OFFSET + f.decorXs[i] + flip * (torchBounds[0] + torchBounds[2] / 2);
        float cy = f.decorYs[i] + torchBounds[1] + torchBounds[3] / 2;
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ONE);
        batch.draw(torchGlow, cx - 59, LevelRenderer.DOC_HEIGHT - cy - 59, 118, 118);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private int popVersion = -1;
    private int popFrame = 1;
    private static final Color POP_TEXT = new Color(82 / 255f, 60 / 255f, 37 / 255f, 1f);
    /** Couleur du champ du parchemin (216, 190, 169). */
    private static final Color POP_FIELD = new Color(216 / 255f, 190 / 255f, 169 / 255f, 1f);

    /** Message du script (GameMode.attachPop) : parchemin « hammer_interf_pop » qui glisse en place (10 images). */
    private void drawMessage(RenderFrame f) {
        String text = f.messageText;
        if (f.messageKey != null) text = app.data.lang.plain(f.messageKey);
        if (text == null || text.isEmpty()) return;
        if (popVersion != f.messageVersion) {
            popVersion = f.messageVersion;
            popFrame = 1;
        } else if (popFrame < 10 && !paused) {
            popFrame++;
        }
        float cx = LevelRenderer.X_OFFSET + Const.GAME_WIDTH * 0.5f;
        boolean drawn = sprites.draw(batch, "hammer_interf_pop", popFrame - 1, f.messageTuto ? 2 : 1,
                cx, 0, 1, 1, LevelRenderer.DOC_HEIGHT);
        float centerY = drawn ? sprites.subOffsetY("hammer_interf_pop", popFrame) : 40;
        // GameMode.attachPop : champ de 363 px centré, texte entre * en #f7e8d5, centré verticalement sur le sous-clip
        text = text.replaceAll("^[\\r\\n]+", "").replace("[", "[[");
        StringBuilder marked = new StringBuilder();
        boolean open = false;
        for (char c : text.toCharArray()) {
            if (c == '*') {
                marked.append(open ? "[]" : "[#F7E8D5]");
                open = !open;
            } else {
                marked.append(c);
            }
        }
        if (open) marked.append("[]");
        GlyphLayout layout = new GlyphLayout(popFont, marked, drawn ? POP_FIELD : Color.WHITE, 363,
                com.badlogic.gdx.utils.Align.center, true);
        if (!drawn) {
            batch.setColor(0.05f, 0.08f, 0.2f, 0.85f);
            batch.draw(white, 20, LevelRenderer.DOC_HEIGHT - 20 - layout.height - 16, 380, layout.height + 16);
            batch.setColor(Color.WHITE);
        }
        popFont.draw(batch, layout, cx - 179.5f, LevelRenderer.DOC_HEIGHT - centerY + layout.height * 0.5f + 2);
        if (f.messageTuto && drawn) { // en-tête : Lang 2, centré à y = sous-clip - 37
            GlyphLayout h = new GlyphLayout(headerFont, app.data.lang.plain("static.2"));
            headerFont.setColor(POP_TEXT);
            headerFont.draw(batch, h, cx - h.width / 2f, LevelRenderer.DOC_HEIGHT - (centerY - 37.3f));
            headerFont.setColor(Color.WHITE);
        }
    }

    /** Points gagnés qui montent puis s'effacent : {x, y, âge en s, valeur, 1 = bonus de temps}. */
    private final List<float[]> popups = new ArrayList<>();

    private void drawPopups() {
        float dt = Gdx.graphics.getDeltaTime();
        for (Iterator<float[]> it = popups.iterator(); it.hasNext(); ) {
            float[] p = it.next();
            p[2] += dt;
            if (p[2] > 1.2f) {
                it.remove();
                continue;
            }
            font.setColor(1, 1, 0.6f, 1 - p[2] / 1.2f);
            String s = p.length > 4 && p[4] == 1 ? "-" + (int) p[3] + " sec" : String.valueOf((int) p[3]);
            font.draw(batch, s, LevelRenderer.X_OFFSET + p[0] - s.length() * 3.5f, LevelRenderer.DOC_HEIGHT - p[1] + 30 + p[2] * 25);
        }
        font.setColor(Color.WHITE);
    }

    /** Barre de vie de Tuberculoz (verticale à gauche). */
    private void drawBossBar(RenderFrame f) {
        if (f.bossRatio < 0) return;
        float h = 300, bx = 2, by = LevelRenderer.DOC_HEIGHT / 2f - h / 2;
        batch.setColor(0, 0, 0, 0.6f);
        batch.draw(white, bx, by, 8, h);
        batch.setColor(1f, 0.35f, 0.1f, 1f);
        batch.draw(white, bx + 1, by + 1, 6, (h - 2) * f.bossRatio);
        batch.setColor(Color.WHITE);
    }

    /** Texte en bas à droite : « Echap : quitter » en solo, message de l'appelant en ligne. */
    public String footer = "Echap : quitter";
    /** Partie en ligne : nom du joueur connecté (affiché à gauche du second bandeau), null hors ligne. */
    public String onlineWith;

    /** gui.GameInterface : vies (BASE_X, BASE_X_RIGHT, BASE_WIDTH), au plus 8 chapeaux puis « + ». */
    static final int MAX_LIVES = 8;
    private static final Color LEVEL_COLOR = new Color(231 / 255f, 224 / 255f, 235 / 255f, 1);
    private static final Color GLOW = new Color(0x70 / 255f, 0x65 / 255f, 0x8d / 255f, 1);

    private void drawHud(RenderFrame f) {
        drawBossBar(f);
        drawPopups();
        drawLevelPop(); // DP_INTERF : sous le bandeau (DP_TOP)
        if (!f.soccer) drawInterface(f);
        if (f.timeAttack) drawTimeAttack(f);
        if (f.bossRush && f.lastLevelTime >= 0 && f.levelCycle < Const.SECOND * 4) {
            font.draw(batch, app.data.lang.plain("static.39") + " +" + Game.formatTime(f.lastLevelTime), 20, 50);
        }
        if (f.soccer) {
            long t = f.matchRemainingMs / 1000;
            font.draw(batch, f.soccer0 + " - " + f.soccer1 + "    " + String.format("%d:%02d", t / 60, t % 60),
                    LevelRenderer.DOC_WIDTH / 2f - 40, 15);
        }
        drawAlert();
        if (paused) {
            if (mapOpen) drawMap(f);
            else drawPauseHelp(f);
        }
        drawSecondBand(f);
    }

    /**
     * Bandeau d'origine (gui.GameInterface) : clip hammer_interf_game en bas (image 1 solo, 2 à deux joueurs,
     * 3 Time Attack), lettres EXTEND ramassées, chapeaux des vies (8 au plus, puis « + »), score et niveau.
     */
    private void drawInterface(RenderFrame f) {
        int np = f.names == null ? 0 : f.names.length;
        if (np == 0) return;
        int frame = f.timeAttack ? 3 : np > 1 ? 2 : 1;
        float base = LevelRenderer.DOC_HEIGHT; // mc._y = DOC_HEIGHT, mc._x = -xOffset
        boolean clip = sprites.draw(batch, "hammer_interf_game", -1, frame, 0, base, 1, 1, LevelRenderer.DOC_HEIGHT);
        if (!clip) fillRect(0, base - 33, LevelRenderer.DOC_WIDTH, 33, 0.18f, 0.15f, 0.25f, 1);
        // lettres EXTEND
        if (frame != 3) {
            for (int pid = 0; pid < Math.min(np, 2); pid++) {
                int mask = f.extendMasks == null || pid >= f.extendMasks.length ? 0 : f.extendMasks[pid];
                for (int i = 0; i < 7; i++) {
                    if ((mask & (1 << i)) == 0) continue;
                    String part = "letter" + pid + "_" + i + (frame > 1 ? "#" + frame : "");
                    sprites.drawPart(batch, "hammer_interf_game", part, 1, 0, base, 1, 1, Float.NaN, LevelRenderer.DOC_HEIGHT);
                }
            }
        }
        // vies : chapeaux d'Igor
        if (!f.tutorial) {
            float baseX = frame == 3 ? 8 : 92, baseXRight = frame == 3 ? 386 : 300, baseW = frame == 3 ? 15 : 20;
            for (int pid = 0; pid < Math.min(np, 2); pid++) {
                float w = np > 1 && frame != 3 ? 0.6f * baseW : baseW;
                float bx = baseX;
                if (pid == 1) {
                    w = -w;
                    bx = baseXRight;
                }
                int v = Math.max(0, f.lives[pid]);
                for (int i = 0; i < Math.min(v, MAX_LIVES); i++) {
                    sprites.draw(batch, "hammer_interf_life", -1, 23, bx + i * w, base - 19, 1, 1, LevelRenderer.DOC_HEIGHT);
                }
                if (v > MAX_LIVES) {
                    float mx = bx + w * MAX_LIVES - 4 - (pid > 0 ? w : 0);
                    sprites.draw(batch, "hammer_interf_more", -1, 1, mx, base - 25, 1, 1, LevelRenderer.DOC_HEIGHT);
                }
            }
        }
        // textes aux champs du clip : score (et niveau en solo), temps en Time Attack
        if (frame == 1) {
            hudText(scoreText(f.scores[0]), 324.7f, 418.9f, base - 19.45f, 1, 1.15f, Color.WHITE);
            String lvl = f.fakeLevelId >= 0 ? String.valueOf(f.fakeLevelId) : String.valueOf(f.levelId);
            hudText(lvl, 274.8f, 317.3f, base - 19.2f, 2, 1.15f, LEVEL_COLOR);
        } else if (frame == 2) {
            hudText(scoreText(f.scores[0]), 0.1f, 94.3f, base - 21.7f, 0, 0.95f, Color.WHITE);
            hudText(scoreText(f.scores[1]), 325.9f, 420.1f, base - 21.7f, 1, 0.95f, Color.WHITE);
        } else {
            hudText(Game.formatTime(f.chronoMs), 141.1f, 279.7f, base - 23.3f, 2, 1.45f, Color.WHITE);
        }
    }

    /** GameInterface.getScoreTxt : chiffres groupés par trois. */
    static String scoreText(long v) {
        String s = String.valueOf(v);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    /** Texte d'un champ du clip, de x0 à x1, haut à {@code top} (document) ; aligné 0 gauche, 1 droite, 2 centre. */
    private void hudText(String text, float x0, float x1, float top, int align, float scale, Color color) {
        font.getData().setScale(scale);
        GlyphLayout l = new GlyphLayout(font, text);
        float x = align == 0 ? x0 + 2 : align == 1 ? x1 - 2 - l.width : (x0 + x1 - l.width) / 2f;
        float y = LevelRenderer.DOC_HEIGHT - top - 2;
        font.setColor(GLOW); // FxManager.addGlow
        font.draw(batch, text, x + 1, y - 1);
        font.draw(batch, text, x - 1, y + 1);
        font.setColor(color);
        font.draw(batch, text, x, y);
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    /** Second bandeau, sous celui d'origine : niveau (et chrono), joueurs, aide. */
    private void drawSecondBand(RenderFrame f) {
        batch.setColor(0.08f, 0.06f, 0.12f, 1);
        batch.draw(white, 0, -BAND_HEIGHT, LevelRenderer.DOC_WIDTH, BAND_HEIGHT);
        batch.setColor(Color.WHITE);
        float y = -BAND_HEIGHT / 2f + 5;
        if (onlineWith != null) { // en ligne : joueur connecté à gauche, comment quitter à droite
            font.setColor(LEVEL_COLOR);
            font.draw(batch, "En ligne avec " + onlineWith, 6, y);
            GlyphLayout q = new GlyphLayout(font, footer);
            font.setColor(0.7f, 0.7f, 0.75f, 1);
            font.draw(batch, footer, LevelRenderer.DOC_WIDTH - 6 - q.width, y);
            font.setColor(Color.WHITE);
            return;
        }
        String level = f.mainWorld ? "Niveau " + (f.fakeLevelId >= 0 ? f.fakeLevelId : f.levelId)
                : sectorName(f.currentDim, f.levelId) + " " + f.levelId;
        if (f.bossRush || f.timeAttack) level += "   " + Game.formatTime(f.chronoMs);
        font.setColor(LEVEL_COLOR);
        font.draw(batch, level, 6, y);
        int np = f.names == null ? 0 : f.names.length;
        if (np > 1 || f.multi) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < np; i++) {
                if (i > 0) b.append("  /  ");
                b.append(f.names[i] == null || f.names[i].isEmpty() ? "J" + (i + 1) : f.names[i]);
            }
            GlyphLayout l = new GlyphLayout(font, b);
            font.setColor(Color.WHITE);
            font.draw(batch, b, (LevelRenderer.DOC_WIDTH - l.width) / 2f, y);
        }
        GlyphLayout fl = new GlyphLayout(font, footer);
        font.setColor(0.7f, 0.7f, 0.75f, 1);
        font.draw(batch, footer, LevelRenderer.DOC_WIDTH - 6 - fl.width, y);
        font.setColor(Color.WHITE);
    }

    // ------------------------------------------------------------------ pause et carte

    private void fillRect(float x, float yTop, float w, float h, float r, float g, float b, float a) {
        batch.setColor(r, g, b, a);
        batch.draw(white, x, LevelRenderer.DOC_HEIGHT - yTop - h, w, h);
        batch.setColor(Color.WHITE);
    }

    /** Lang.getSectorName : nom de la dernière zone nommée à ce niveau ou avant. */
    private String sectorName(int did, int lid) {
        for (int i = lid; i >= 0; i--) {
            String key = "level." + did + "." + i;
            if (app.data.lang.has(key)) return app.data.lang.plain(key);
        }
        return "";
    }

    private void centered(String text, float yTop) {
        GlyphLayout l = new GlyphLayout(font, text);
        font.draw(batch, text, (LevelRenderer.DOC_WIDTH - l.width) / 2f, LevelRenderer.DOC_HEIGHT - yTop);
    }

    /** Aide de la pause (hammer_interf_instructions) : touches, zone en cours et un conseil. */
    private void drawPauseHelp(RenderFrame f) {
        fillRect(0, 0, LevelRenderer.DOC_WIDTH, LevelRenderer.DOC_HEIGHT, 0, 0, 0, 0.45f);
        float x = 60, w = LevelRenderer.DOC_WIDTH - 120, top = 120;
        fillRect(x, top, w, 260, 0.36f, 0.25f, 0.16f, 0.95f);
        fillRect(x + 4, top + 4, w - 8, 252, 0.55f, 0.40f, 0.26f, 0.95f);
        font.getData().setScale(1.4f);
        font.setColor(0.98f, 0.9f, 0.7f, 1f);
        centered(app.data.lang.plain("static.5"), top + 18);
        font.getData().setScale(1f);
        String[][] rows = {
                {"Flèches", app.data.lang.plain("static.7")},
                {app.data.lang.plain("static.10"), app.data.lang.plain("static.8")},
                {"P", app.data.lang.plain("static.9")},
                {"C", "Carte"},
        };
        float y = top + 60;
        for (String[] r : rows) {
            if (r[0].equals("C") && !hasMap()) continue;
            fillRect(x + 20, y - 5, 110, 21, 0.85f, 0.82f, 0.75f, 1f);
            font.setColor(0.2f, 0.15f, 0.1f, 1f);
            font.draw(batch, r[0], x + 28, LevelRenderer.DOC_HEIGHT - y + 2);
            font.setColor(0.98f, 0.92f, 0.75f, 1f);
            font.draw(batch, r[1], x + 145, LevelRenderer.DOC_HEIGHT - y + 2);
            y += 30;
        }
        if (f.multi && !f.soccer) {
            font.draw(batch, "Sandy : R F D G + A", x + 20, LevelRenderer.DOC_HEIGHT - y + 2);
        }
        int n = 0;
        while (app.data.lang.has("static." + (301 + n))) n++;
        if (n > 0) {
            String tip = app.data.lang.plain("static.300") + app.data.lang.plain("static." + (301 + Math.floorMod(tipIndex, n)));
            font.setColor(1f, 0.95f, 0.85f, 1f);
            font.draw(batch, tip, x + 16, LevelRenderer.DOC_HEIGHT - (top + 200), w - 32, com.badlogic.gdx.utils.Align.left, true);
        }
        font.setColor(Color.WHITE);
        String sector = f.tutorial ? app.data.lang.plain("static.16") : sectorName(f.currentDim, f.mainWorld ? f.levelId : 0);
        if (!sector.isEmpty()) centered(app.data.lang.plain("static.14") + "\"" + sector + "\"", top + 290);
        if (f.bossRush) {
            centered(app.data.lang.plain("static.37") + " " + Game.formatTime(f.chronoMs) + "   "
                    + app.data.lang.plain("static.38") + " " + (f.levelAvg > 0 ? Game.formatTime((long) f.levelAvg) : "--"), top + 270);
        }
        centered("P : reprendre", top + 312);
    }

    /** GameMode.getMapY : hauteur d'un niveau du puits sur la carte. */
    private static float mapY(int lid) {
        return 84 + Math.min(350, lid * 3.5f);
    }

    /** Texte d'un événement : les clés de langue entre accolades sont remplacées (« 12. {level.1.0} »). */
    private String mapText(String misc) {
        if (misc == null) return "?";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([^}]+)\\}").matcher(misc);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(app.data.lang.has(key) ? app.data.lang.plain(key) : "?"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Carte du puits (hammer_map) : profondeur atteinte, portails empruntés, morts et mots EXTEND. */
    /**
     * Carte (GameMode.onMap) : clip hammer_map à x = -xOffset ; titre = contrée du niveau courant de la dimension
     * courante (Lang.getSectorName) ; jauge « pit » étirée selon la profondeur atteinte dans le monde principal
     * (yscale = lid %, alpha 75 %), pointeur « ptr » à getMapY(lid) (70 au niveau 0) ; icônes des événements.
     */
    private void drawMap(RenderFrame f) {
        int lid = f.mainLevelId;
        if (sprites.draw(batch, "hammer_map", -1, 1, 0, 0, 1, 1, LevelRenderer.DOC_HEIGHT)) { // jauge et pointeur retirés du clip exporté
            if (lid > 0) {
                batch.setColor(1, 1, 1, 0.75f);
                sprites.drawPart(batch, "hammer_map", "pit", 1, 0, 0, 1, 1, Float.NaN, 1, Math.min(1f, lid / 100f),
                        Float.NaN, LevelRenderer.DOC_HEIGHT);
                batch.setColor(Color.WHITE);
            }
        } else {
            fillRect(0, 0, LevelRenderer.DOC_WIDTH, LevelRenderer.GAME_HEIGHT, 0.32f, 0.17f, 0.08f, 0.96f);
        }
        // titre : champ centré de 343 px à x = 40, y = 25, police 14 px
        font.getData().setScale(14f / 16f); // champ 188 : Franklin Gothic 14 px, centré sur x = 38..382
        GlyphLayout title = new GlyphLayout(font, sectorName(f.currentDim, f.levelId));
        font.setColor(Color.WHITE);
        font.draw(batch, title, 210 - title.width / 2f, LevelRenderer.DOC_HEIGHT - 27);
        font.getData().setScale(1f);

        float cx = LevelRenderer.X_OFFSET + Const.GAME_WIDTH * 0.5f;
        int count = f.mapLids == null ? 0 : f.mapLids.length;
        for (int k = 0; k < count; k++) {
            int eid = f.mapEids[k], elid = f.mapLids[k];
            float x = cx;
            if (!f.mapTravel[k]) { // icônes côte à côte sur un même niveau (8 px)
                int same = 0, idx = 0;
                for (int o = 0; o < count; o++) {
                    if (f.mapTravel[o] || f.mapLids[o] != elid) continue;
                    if (o == k) idx = same;
                    same++;
                }
                x += idx * 8 - 0.5f * (same - 1) * 8;
            }
            if (eid == Const.EVENT_EXIT_LEFT || eid == Const.EVENT_BACK_LEFT) x = cx - 5;
            if (eid == Const.EVENT_EXIT_RIGHT || eid == Const.EVENT_BACK_RIGHT) x = cx + 5;
            x = (float) Math.floor(x);
            float y = (float) Math.floor(mapY(elid));
            sprites.draw(batch, "hammer_interf_mapIcon", -1, eid, x, y, 1, 1, LevelRenderer.DOC_HEIGHT);
            // champ « field » des images 1 (sortie droite), 3 (sortie gauche, aligné à droite) et 7 (temps)
            if (eid == Const.EVENT_EXIT_RIGHT || eid == Const.EVENT_EXIT_LEFT || eid == Const.EVENT_TIME) {
                String t = eid == Const.EVENT_TIME ? (f.mapMiscs[k] == null ? "?" : f.mapMiscs[k]) : mapText(f.mapMiscs[k]);
                font.getData().setScale(9f / 16f); // champs des icônes : 9 px
                GlyphLayout l = new GlyphLayout(font, t);
                float tx = eid == Const.EVENT_EXIT_LEFT ? x - 31.4f - l.width : x + (eid == Const.EVENT_TIME ? 34.4f : 32.7f);
                font.draw(batch, t, tx, LevelRenderer.DOC_HEIGHT - y + 4);
                font.getData().setScale(1f);
            }
        }
        if (sprites.partFrames("hammer_map", "ptr") > 0) {
            sprites.drawPart(batch, "hammer_map", "ptr", 1, 0, 0, 1, 1, Float.NaN, 1, 1,
                    lid == 0 ? 70 : mapY(lid), LevelRenderer.DOC_HEIGHT);
        }
    }

    /** Nom de la zone (FxManager.attachLevelPop : hammer_interf_zone), 5 s puis glisse vers le bas. */
    private String levelPopName, levelPopLabel;
    private float levelPopTime;

    private void drawLevelPop() {
        if (levelPopName == null) return;
        levelPopTime += Gdx.graphics.getDeltaTime();
        float slide = Math.max(0, levelPopTime - 5f) * 0.7f * Const.SECOND;
        // hammer_interf_zone à x = -10, y = GAME_HEIGHT - 1 (racine du jeu à xOffset) : champs alignés à droite
        float y = LevelRenderer.GAME_HEIGHT - 1 + slide;
        if (y >= LevelRenderer.GAME_HEIGHT + 40) {
            levelPopName = null;
            return;
        }
        float right = 413;
        if (!levelPopLabel.isEmpty()) { // label : 12 px, gris (219), haut à y - 33
            glowText(smallFont, levelPopLabel, right, y - 33.25f, new Color(219 / 255f, 219 / 255f, 219 / 255f, 1));
        }
        glowText(font, levelPopName, right, y - 20.15f, Color.WHITE); // field : 16 px, blanc, haut à y - 20
    }

    /** Texte aligné à droite sur {@code right}, haut à {@code top} (document), avec contour noir (addGlow 0x0, 2). */
    private void glowText(BitmapFont bf, String text, float right, float top, Color color) {
        GlyphLayout l = new GlyphLayout(bf, text);
        float x = right - 2 - l.width, yy = LevelRenderer.DOC_HEIGHT - top - 2;
        bf.setColor(0, 0, 0, 0.85f);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) bf.draw(batch, text, x + dx, yy + dy);
        bf.setColor(color);
        bf.draw(batch, text, x, yy);
        bf.setColor(Color.WHITE);
    }

    /** Message d'alerte au centre de l'écran (FxManager.attachAlert), 2,5 s. */
    private String alertText;
    private float alertTime;

    /** Affiche une alerte (aussi utilisé pour les messages réseau). */
    public void alert(String text) {
        alertText = text;
        alertTime = 0;
    }

    /**
     * Clip « hurryUp » (FxManager.attachAlert) : le texte arrive de la droite (images 1 à 8), reste au centre
     * (images 9-10 rejouées tant que timer = 50 cycles n'est pas écoulé), puis repart vers la gauche (11 à 18).
     * Le clip exporté contient le texte par défaut du champ (« pouet ») : on dessine le vrai texte à sa place.
     * Décalage horizontal du centre du texte par image (mesuré sur l'export), y = centre de l'écran + 28.
     */
    private static final float[] ALERT_X = {317, 212, 108, 55, 3, -1, -5, -1, 3, 3, 3, 7, 11, -53, -117, -181, -245, -310};

    private void drawAlert() {
        if (alertText == null) return;
        if (!paused) alertTime += Gdx.graphics.getDeltaTime();
        float c = alertTime * 32; // cycles écoulés
        float frame; // 0-based, fractionnaire
        if (c < 9) frame = c;
        else if (c < 59) frame = 9;
        else frame = 10 + (c - 59);
        if (frame >= ALERT_X.length - 1) {
            alertText = null;
            return;
        }
        int i = (int) frame;
        float t = frame - i;
        float dx = ALERT_X[i] + (ALERT_X[Math.min(i + 1, ALERT_X.length - 1)] - ALERT_X[i]) * t;
        font.getData().setScale(2f);
        GlyphLayout layout = new GlyphLayout(font, alertText);
        float cx = LevelRenderer.X_OFFSET + Const.GAME_WIDTH / 2f + dx;
        float cy = LevelRenderer.DOC_HEIGHT - (Const.GAME_HEIGHT / 2f + 28);
        font.setColor(0, 0, 0, 0.6f);
        font.draw(batch, alertText, cx - layout.width / 2f + 2, cy + layout.height / 2f - 2);
        font.setColor(Color.WHITE);
        font.draw(batch, alertText, cx - layout.width / 2f, cy + layout.height / 2f);
        font.getData().setScale(1f);
    }

    /** Compte à rebours de départ (hammer_interf_starter) et temps du dernier niveau (hammer_interf_time). */
    private void drawTimeAttack(RenderFrame f) {
        if (f.starter > 0) {
            String n = String.valueOf((int) Math.ceil(f.starter / Const.SECOND));
            font.getData().setScale(3f);
            font.draw(batch, n, LevelRenderer.DOC_WIDTH / 2f - 10, LevelRenderer.DOC_HEIGHT / 2f + 20);
            font.getData().setScale(1f);
        }
        if (f.lastLevelTime >= 0 && f.levelCycle < Const.SECOND * 4) {
            font.draw(batch, "+ " + Game.formatTime(f.lastLevelTime), 20, 50);
        }
    }

    public void resize(int width, int height) {
        viewport.update(width, height, false);
        viewport.getCamera().position.set(LevelRenderer.DOC_WIDTH / 2f, (LevelRenderer.DOC_HEIGHT - BAND_HEIGHT) / 2f, 0);
        viewport.getCamera().update();
    }

    @Override
    public void dispose() {
        batch.dispose();
        popFont.dispose();
        headerFont.dispose();
        smallFont.dispose();
        levelRenderer.dispose();
        sprites.dispose();
        audio.dispose();
        darknessRenderer.dispose();
        scarf.dispose();
        tint.dispose();
        torchGlow.dispose();
        font.dispose();
        white.dispose();
    }
}
