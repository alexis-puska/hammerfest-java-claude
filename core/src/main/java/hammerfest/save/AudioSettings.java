package hammerfest.save;

/** Réglages du son (save.json) : volumes de la musique et des bruitages, musique coupée (touche M en jeu). */
public class AudioSettings {

    /** Volume de la musique, 0..1. */
    public float musicVolume = 0.5f;
    /** Volume des bruitages, 0..1. */
    public float soundVolume = 0.7f;
    /** Musique coupée (touche M) ; les bruitages restent. */
    public boolean musicMuted;

    public AudioSettings sanitize() {
        musicVolume = clamp(musicVolume);
        soundVolume = clamp(soundVolume);
        return this;
    }

    /** Volume réellement appliqué à la musique. */
    public float effectiveMusic() {
        return musicMuted ? 0 : musicVolume;
    }

    static float clamp(float v) {
        return Float.isNaN(v) ? 0.5f : Math.max(0, Math.min(1, v));
    }
}
