package uy.edu.um.nexo.mnatural.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

// Idiomas de la aplicación: español e inglés (ESPECIFICACION.md, sección 1).
// - Los textos están en messages_es.properties y messages_en.properties y las pantallas los piden con #{clave}.
// - El idioma elegido se guarda en una cookie ("lang"), así que se mantiene al navegar y al volver.
// - Se cambia con ?lang=es o ?lang=en (el selector de la barra de navegación).
// - Un idioma que no esté soportado se trata como el español (idioma de la plataforma)
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    public static final String LANGUAGE_PARAM = "lang";
    public static final Locale SPANISH = Locale.forLanguageTag("es");
    public static final Locale ENGLISH = Locale.forLanguageTag("en");
    public static final List<Locale> SUPPORTED = List.of(SPANISH, ENGLISH);

    // ObjectProvider: en un test de controller (que no carga repositorios) simplemente no hay idioma guardado por usuario
    private final ObjectProvider<UserRepository> userRepository;

    public I18nConfig(ObjectProvider<UserRepository> userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        // Sin esto, el idioma de la computadora donde corre el servidor se colaría en las pantallas
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(SPANISH);
        return source;
    }

    // El nombre "localeResolver" es el que busca Spring MVC
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver(LANGUAGE_PARAM);
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setCookiePath("/");
        // Visitante sin cookie: inglés si su navegador lo pide, y español en cualquier otro caso
        resolver.setDefaultLocaleFunction(I18nConfig::browserLanguage);
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor change = new LocaleChangeInterceptor() {
            // Solo se acepta español o inglés: cualquier otro valor se ignora y queda el idioma que ya había
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws ServletException {
                String requested = request.getParameter(LANGUAGE_PARAM);
                if (requested != null && !isSupportedLanguage(requested)) {
                    return true;
                }
                return super.preHandle(request, response, handler);
            }
        };
        change.setParamName(LANGUAGE_PARAM);
        change.setIgnoreInvalidLocale(true);
        registry.addInterceptor(change);
        registry.addInterceptor(new UserLanguageInterceptor(userRepository::getIfAvailable));
    }

    // Solo mira el encabezado del navegador: el idioma de la computadora del servidor no cuenta
    static Locale browserLanguage(HttpServletRequest request) {
        if (request.getHeader("Accept-Language") != null
                && ENGLISH.getLanguage().equals(request.getLocale().getLanguage())) {
            return ENGLISH;
        }
        return SPANISH;
    }

    // "es" o "en", con o sin región (es-UY, en_US)
    static boolean isSupportedLanguage(String value) {
        String language = value.trim().toLowerCase(Locale.ROOT);
        return language.equals("es") || language.equals("en")
                || language.startsWith("es-") || language.startsWith("en-")
                || language.startsWith("es_") || language.startsWith("en_");
    }

    // Solo "es" o "en": cualquier otro valor (o ninguno) queda como español
    public static Locale supported(String language) {
        return language != null && language.trim().equalsIgnoreCase("en") ? ENGLISH : SPANISH;
    }
}
