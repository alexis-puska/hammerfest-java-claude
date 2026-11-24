package hammerfest.sim.entity;

/** Entité gelable par une bombe (les ennemis, cf. entity.Bad.freeze / setCombo). */
public interface Freezable {

    void setCombo(int bombId);

    void freeze(double duration);
}
