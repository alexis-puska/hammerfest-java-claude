package hammerfest.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.UncheckedIOException;

/**
 * ObjectMapper Jackson partagé : données du jeu (assets/data) et sauvegarde (save.json).
 *
 * <p>Convention des classes de données : champs publics sérialisés tels quels (les getters ne sont
 * pas pris en compte, ce qui permet d'ajouter librement des méthodes utilitaires), champs
 * inconnus ignorés à la lecture (compatibilité ascendante), valeurs null omises à l'écriture.</p>
 */
public final class Jsons {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .setVisibility(PropertyAccessor.ALL, Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, Visibility.PUBLIC_ONLY)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Indentation sur 2 espaces, tableaux un élément par ligne. */
    private static final ObjectWriter PRETTY = MAPPER.writer(new DefaultPrettyPrinter()
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n")));

    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T read(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String write(Object value) {
        try {
            return PRETTY.writeValueAsString(value) + "\n";
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Jsons() {
    }
}
