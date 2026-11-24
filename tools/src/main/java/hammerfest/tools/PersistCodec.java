package hammerfest.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Décodeur du format PersistCodec de MotionTypes ({@code original_sources/mtypes-master/lib/PersistCodec.mt}
 * et {@code BitCodec.mt}), utilisé par le jeu d'origine pour sérialiser les niveaux.
 *
 * <p>Le texte est un flux de bits en base 64 ({@code a-z A-Z 0-9 - _}), lu par paquets de 6 bits.
 * Valeurs produites : {@link Integer}, {@link Double}, {@link String}, {@link Boolean}, null,
 * {@link List} (les trous des tableaux valent null) et {@link Map} (ordre des clés conservé).</p>
 */
public final class PersistCodec {

    private final String data;
    private int pos;
    private int nbits;
    private long bits;
    private final List<String> fieldTable = new ArrayList<>();
    private int fieldBits;
    private int nextFieldBits = 1;

    private PersistCodec(String data) {
        this.data = data;
    }

    public static Object decode(String data) {
        PersistCodec c = new PersistCodec(data);
        return c.decodeValue();
    }

    // ------------------------------------------------------------------ BitCodec

    static int d64(char c) {
        if (c >= 'a' && c <= 'z') return c - 'a';
        if (c >= 'A' && c <= 'Z') return c - 'A' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '-') return 62;
        if (c == '_') return 63;
        return -1;
    }

    static char c64(int v) {
        if (v < 26) return (char) ('a' + v);
        if (v < 52) return (char) ('A' + v - 26);
        if (v < 62) return (char) ('0' + v - 52);
        return v == 62 ? '-' : '_';
    }

    private int read(int n) {
        while (nbits < n) {
            if (pos >= data.length()) throw new IllegalStateException("fin de données inattendue (pos " + pos + ")");
            int c = d64(data.charAt(pos++));
            if (c < 0) throw new IllegalStateException("caractère invalide '" + data.charAt(pos - 1) + "' (pos " + pos + ")");
            nbits += 6;
            bits = (bits << 6) | c;
        }
        nbits -= n;
        return (int) ((bits >> nbits) & ((1L << n) - 1));
    }

    // ------------------------------------------------------------------ PersistCodec

    private Object decodeValue() {
        if (read(1) == 0) {                 // nombre
            if (read(1) == 1) {             // flottant
                if (read(1) == 1) {         // spécial
                    if (read(1) == 1) return read(1) == 1 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                    return Double.NaN;
                }
                return decodeFloat();
            }
            return decodeInt();
        }
        if (read(1) == 0) {                 // tableau / objet
            return read(1) == 1 ? decodeObject() : decodeArray();
        }
        switch (read(2)) {
            case 0:
                return Boolean.FALSE;
            case 1:
                return Boolean.TRUE;
            case 2:
                return decodeString();
            default:
                return null;                // undefined
        }
    }

    private int decodeInt() {
        int nb = read(2);
        if (nb == 3) {
            if (read(1) == 1) return -decodeInt();
            if (read(1) == 1) {
                int lo = read(16);
                int hi = read(16);
                return lo | (hi << 16);
            }
            return read(16);
        }
        return read((nb + 1) * 2);
    }

    private double decodeFloat() {
        int len = read(5);
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int k = read(4);
            if (k < 10) s.append((char) ('0' + k));
            else if (k == 10) s.append('.');
            else if (k == 11) s.append('+');
            else if (k == 12) s.append('-');
            else s.append('e');
        }
        return Double.parseDouble(s.toString());
    }

    private String decodeString() {
        int len = decodeInt();
        StringBuilder s = new StringBuilder(len);
        if (read(1) == 0) {
            for (int i = 0; i < len; i++) s.append(c64(read(6)));
        } else {
            int width = read(1) == 0 ? 7 : 8;
            for (int i = 0; i < len; i++) s.append((char) read(width));
        }
        return s.toString();
    }

    private List<Object> decodeArray() {
        List<Object> a = new ArrayList<>();
        int index = 0;
        while (true) {
            if (read(1) == 0) {
                Object v = decodeValue();
                while (a.size() < index) a.add(null);
                a.add(v);
                index++;
            } else {
                if (read(1) == 1) break;
                index += decodeInt();
            }
        }
        while (a.size() < index) a.add(null);
        return a;
    }

    private Map<String, Object> decodeObject() {
        Map<String, Object> o = new LinkedHashMap<>();
        while (true) {
            String key;
            if (read(1) == 0) {
                key = fieldTable.get(read(fieldBits));
            } else {
                if (read(1) == 1) break;
                key = decodeString();
                fieldTable.add(key);
                if (fieldTable.size() >= nextFieldBits) {
                    fieldBits++;
                    nextFieldBits *= 2;
                }
            }
            o.put(key, decodeValue());
        }
        return o;
    }
}
