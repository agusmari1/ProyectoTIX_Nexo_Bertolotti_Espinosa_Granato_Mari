package uy.edu.um.nexo.mnatural.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.util.UriComponentsBuilder;
import uy.edu.um.nexo.mnatural.config.I18nConfig;

import java.util.Locale;

// Pone en el modelo de todas las pantallas lo que necesita el selector de idioma de la barra de navegación:
// el idioma actual y el link para cambiar a cada uno, que vuelve a la misma página con los mismos filtros
@ControllerAdvice
public class LanguageAdvice {

    // Los links del selector. Los parámetros de la página actual se conservan; solo cambia "lang"
    public record LanguageLinks(String current, String spanishUrl, String englishUrl) {
        public boolean spanish() {
            return "es".equals(current);
        }

        public boolean english() {
            return "en".equals(current);
        }
    }

    @ModelAttribute("languageLinks")
    public LanguageLinks languageLinks(HttpServletRequest request, Locale locale) {
        String current = I18nConfig.ENGLISH.getLanguage().equals(locale.getLanguage()) ? "en" : "es";
        return new LanguageLinks(current, urlFor(request, "es"), urlFor(request, "en"));
    }

    private String urlFor(HttpServletRequest request, String language) {
        // Solo la ruta y los parámetros: nunca un host que venga del pedido
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance().path(request.getRequestURI());
        // Solo en GET: en un POST (por ejemplo un formulario que se vuelve a mostrar con errores) los parámetros son
        // los datos del formulario, como la contraseña, y no tienen que terminar escritos en un link
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            request.getParameterMap().forEach((name, values) -> {
                if (!I18nConfig.LANGUAGE_PARAM.equals(name)) {
                    builder.queryParam(name, (Object[]) values);
                }
            });
        }
        return builder.queryParam(I18nConfig.LANGUAGE_PARAM, language).build().encode().toUriString();
    }
}
