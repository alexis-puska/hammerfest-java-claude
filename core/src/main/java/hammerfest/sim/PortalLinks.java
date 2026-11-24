package hammerfest.sim;

import hammerfest.data.PortalData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Liens entre portails (Data.xml_readPortalLinks / Data.getLink). Une extrémité s'écrit
 * {@code tag[+décalage][(portail)]} : le tag désigne (dimension, niveau), le décalage s'ajoute au
 * niveau, le portail vaut 0 par défaut (-1 = arrivée au point de départ du niveau).
 */
public class PortalLinks {

    /** Lien orienté (from → to). */
    public static final class Link {
        public final int fromDid, fromLid, fromPid, toDid, toLid, toPid;

        Link(int fromDid, int fromLid, int fromPid, int toDid, int toLid, int toPid) {
            this.fromDid = fromDid;
            this.fromLid = fromLid;
            this.fromPid = fromPid;
            this.toDid = toDid;
            this.toLid = toLid;
            this.toPid = toPid;
        }
    }

    private final List<Link> links = new ArrayList<>();
    private final Map<String, int[]> tags = new HashMap<>();

    public PortalLinks(PortalData data) {
        if (data == null) return;
        for (PortalData.Tag t : data.tags) tags.put(t.name.toLowerCase(Locale.ROOT), new int[]{t.did, t.lid});
        for (PortalData.Way w : data.ways) {
            int[] from = endpoint(w.from), to = endpoint(w.to);
            if (from == null || to == null) continue;
            links.add(new Link(from[0], from[1], from[2], to[0], to[1], to[2]));
            if ("twoway".equals(w.type)) links.add(new Link(to[0], to[1], to[2], from[0], from[1], from[2]));
        }
    }

    /** {did, lid, pid} ou null si le tag est inconnu. */
    int[] endpoint(String s) {
        String code = s.replace(" ", "").replace(")", "").toLowerCase(Locale.ROOT);
        int pid = 0;
        int paren = code.indexOf('(');
        if (paren >= 0) {
            pid = parseOr(code.substring(paren + 1), 0);
            code = code.substring(0, paren);
        }
        String[] parts = code.split("\\+");
        int[] tag = tags.get(parts[0]);
        if (tag == null) return null;
        int inc = parts.length > 1 ? parseOr(parts[1], 0) : 0;
        return new int[]{tag[0], tag[1] + inc, pid};
    }

    private static int parseOr(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public Link get(int did, int lid, int pid) {
        for (Link l : links) if (l.fromDid == did && l.fromLid == lid && l.fromPid == pid) return l;
        return null;
    }

    public int size() {
        return links.size();
    }
}
