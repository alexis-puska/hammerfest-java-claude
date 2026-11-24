package hammerfest.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Liens entre niveaux (assets/data/portals.json, ex portalLinks.xml). La syntaxe des
 * extrémités est conservée telle quelle (ex. {@code d_forest2+1(0)} : tag + décalage de niveau
 * + n° de portail ; portail -1 = transition floue).
 */
public class PortalData {
    public List<Tag> tags = new ArrayList<>();
    public List<Way> ways = new ArrayList<>();

    /** Nom symbolique d'un niveau : dimension (did) + niveau (lid). */
    public static class Tag {
        public String name;
        public int did;
        public int lid;

        public Tag() {
        }

        public Tag(String name, int did, int lid) {
            this.name = name;
            this.did = did;
            this.lid = lid;
        }
    }

    public static class Way {
        /** "oneway" ou "twoway". */
        public String type;
        public String from;
        public String to;

        public Way() {
        }

        public Way(String type, String from, String to) {
            this.type = type;
            this.from = from;
            this.to = to;
        }
    }
}
