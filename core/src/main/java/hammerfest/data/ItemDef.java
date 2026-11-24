package hammerfest.data;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Objet (score ou spécial) de assets/data/items/*.json. */
public class ItemDef {
    public int id;
    public String nameKey;
    /** Indice de rareté d'origine (0..7, cf. Data.RARITY). */
    public int rarity;
    /** Valeur en points (objets à score) ; null si inconnue ("--" dans le XML). */
    public Integer value;
    public Integer unlock;

    /** Famille parente, renseignée au chargement (non sérialisée). */
    @JsonIgnore
    public int familyId;

    public ItemDef() {
    }

    public ItemDef(int id, int rarity, Integer value, Integer unlock) {
        this.id = id;
        this.nameKey = "item." + id;
        this.rarity = rarity;
        this.value = value;
        this.unlock = unlock;
    }
}
