package hammerfest.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;

/**
 * Musique et bruitages (portage simplifié de SoundManager) :
 * <ul>
 *   <li>musiques : assets/sfx/music.mp3 (jeu), boss.mp3, hurryUp.mp3 ;</li>
 *   <li>bruitages : assets/sounds/&lt;nom&gt;.mp3|.wav (ex. sound_jump), extraits de _sounds.swf.
 *       Un bruitage absent est simplement ignoré.</li>
 * </ul>
 */
public class Audio implements Disposable {

    private final Map<String, Sound> sounds = new HashMap<>();
    private Music music;
    private String musicName;

    /** Réglages du son (save.json), partagés par tous les écrans (HammerfestGame). */
    public static hammerfest.save.AudioSettings settings = new hammerfest.save.AudioSettings();

    /** Volumes changés (écran Options, touche M) : appliqués à la musique en cours. */
    public void applySettings() {
        if (music != null) music.setVolume(settings.effectiveMusic());
    }

    /** Joue en boucle la musique {@code name} (sans extension) si ce n'est pas déjà elle. */
    public void playMusic(String name) {
        if (name.equals(musicName)) return;
        stopMusic();
        FileHandle f = Gdx.files.internal("sfx/" + name + ".mp3");
        if (!f.exists()) return;
        try {
            music = Gdx.audio.newMusic(f);
            music.setLooping(true);
            music.setVolume(settings.effectiveMusic());
            music.play();
            musicName = name;
        } catch (RuntimeException e) {
            System.err.println("Musique illisible " + f.path() + " : " + e.getMessage());
        }
    }

    public void stopMusic() {
        if (music != null) {
            music.stop();
            music.dispose();
        }
        music = null;
        musicName = null;
    }

    /** Joue le bruitage {@code name} s'il a été extrait. */
    public void play(String name) {
        Sound s = sounds.computeIfAbsent(name, n -> {
            for (String ext : new String[]{".mp3", ".wav"}) {
                FileHandle f = Gdx.files.internal("sounds/" + n + ext);
                if (f.exists()) {
                    try {
                        return Gdx.audio.newSound(f);
                    } catch (RuntimeException e) {
                        System.err.println("Son illisible " + f.path() + " : " + e.getMessage());
                    }
                }
            }
            return null;
        });
        if (s != null && settings.soundVolume > 0) s.play(settings.soundVolume);
    }

    @Override
    public void dispose() {
        stopMusic();
        for (Sound s : sounds.values()) if (s != null) s.dispose();
        sounds.clear();
    }
}
