package uy.edu.um.nexo.mnatural.config;

import org.junit.jupiter.api.Test;
import uy.edu.um.nexo.mnatural.entities.DocumentType;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// Los textos en español e inglés tienen que estar siempre completos y alineados. No toca la base de datos
class MessagesTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Pattern KEY_IN_TEMPLATE = Pattern.compile("#\\{([A-Za-z0-9_.]+)[(}]");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    private final Properties spanish = load("messages_es.properties");
    private final Properties english = load("messages_en.properties");

    @Test
    void bothLanguagesHaveExactlyTheSameKeys() {
        Set<String> onlySpanish = new TreeSet<>(spanish.stringPropertyNames());
        onlySpanish.removeAll(english.stringPropertyNames());
        Set<String> onlyEnglish = new TreeSet<>(english.stringPropertyNames());
        onlyEnglish.removeAll(spanish.stringPropertyNames());

        assertThat(onlySpanish).as("claves que faltan en inglés").isEmpty();
        assertThat(onlyEnglish).as("claves que faltan en español").isEmpty();
    }

    @Test
    void noTextIsEmpty() {
        for (Properties messages : new Properties[]{spanish, english}) {
            for (String key : messages.stringPropertyNames()) {
                assertThat(messages.getProperty(key)).as(key).isNotBlank();
            }
        }
    }

    @Test
    void everyTextUsesTheSameParametersInBothLanguages() {
        for (String key : spanish.stringPropertyNames()) {
            assertThat(placeholders(english.getProperty(key))).as("parámetros de " + key)
                    .isEqualTo(placeholders(spanish.getProperty(key)));
        }
    }

    @Test
    void theEnglishTextsAreReallyTranslated() {
        // Un texto igual en los dos idiomas casi siempre es una traducción que quedó sin hacer. Las excepciones
        // son nombres propios, siglas y símbolos que se escriben igual
        Set<String> sameOnPurpose = Set.of("common.brand", "home.title", "documentType.DNI", "pay.simulated.code",
                "paymentStatus.ERROR", "activities.card.minutes",
                "myBookings.total", "payment.total");
        Set<String> untranslated = new TreeSet<>();
        for (String key : spanish.stringPropertyNames()) {
            if (!sameOnPurpose.contains(key) && spanish.getProperty(key).equals(english.getProperty(key))) {
                untranslated.add(key);
            }
        }
        assertThat(untranslated).as("textos iguales en español e inglés (¿sin traducir?)").isEmpty();
    }

    @Test
    void everyKeyUsedByATemplateExists() throws IOException {
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(RESOURCES.resolve("templates"))) {
            for (Path template : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".html"))::iterator) {
                Matcher matcher = KEY_IN_TEMPLATE.matcher(Files.readString(template, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!spanish.containsKey(key) || !english.containsKey(key)) {
                        missing.add(template.getFileName() + " -> " + key);
                    }
                }
            }
        }
        assertThat(missing).as("claves usadas en plantillas que no existen").isEmpty();
    }

    @Test
    void theKeysBuiltDynamicallyExist() {
        // Las plantillas arman estas claves a partir de un dato (#{|documentType.${...}|} y weather.desc.<texto>)
        for (DocumentType type : DocumentType.values()) {
            assertThat(spanish).containsKey("documentType." + type.name());
            assertThat(english).containsKey("documentType." + type.name());
        }
        for (String description : new String[]{"despejado", "mayormente_despejado", "parcialmente_nublado", "nublado",
                "niebla", "llovizna", "lluvia", "nieve", "chubascos", "chubascos_de_nieve", "tormenta"}) {
            assertThat(spanish).containsKey("weather.desc." + description);
            assertThat(english).containsKey("weather.desc." + description);
        }
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static Properties load(String name) {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(RESOURCES.resolve(name)), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + name, e);
        }
        return properties;
    }
}
