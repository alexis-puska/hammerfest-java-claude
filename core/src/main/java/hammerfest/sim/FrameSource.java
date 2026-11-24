package hammerfest.sim;

/**
 * Nombre d'images des animations, lu depuis les clips Flash exportés (assets/sprites).
 * L'Animator d'origine s'appuie sur {@code sub._totalframes} : cette durée influe sur le gameplay
 * (fin d'attaque, d'atterrissage…), d'où sa présence côté simulation.
 */
public interface FrameSource {

    /** Images du sous-clip de l'animation {@code animId} du clip {@code linkage} (au moins 1). */
    int frames(String linkage, int animId);

    /** Valeur par défaut tant que les sprites ne sont pas disponibles. */
    FrameSource DEFAULT = (linkage, animId) -> 10;
}
