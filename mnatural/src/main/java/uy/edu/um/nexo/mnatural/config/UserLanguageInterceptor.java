package uy.edu.um.nexo.mnatural.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.support.RequestContextUtils;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.util.function.Supplier;

// Si alguien inicia sesión en un navegador donde todavía no eligió idioma, se le aplica el que tiene guardado en su
// usuario (el que se calculó según su país al registrarse). Si ya eligió uno (hay cookie), se respeta esa elección
public class UserLanguageInterceptor implements HandlerInterceptor {

    private final Supplier<UserRepository> userRepository;

    public UserLanguageInterceptor(Supplier<UserRepository> userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (hasLanguageCookie(request) || request.getParameter(I18nConfig.LANGUAGE_PARAM) != null) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return true;
        }
        LocaleResolver resolver = RequestContextUtils.getLocaleResolver(request);
        UserRepository users = userRepository.get();
        if (resolver == null || users == null) {
            return true;
        }
        users.findByEmail(authentication.getName())
                .map(user -> user.getLanguage())
                .filter(language -> !language.isBlank())
                .ifPresent(language -> resolver.setLocale(request, response, I18nConfig.supported(language)));
        return true;
    }

    private boolean hasLanguageCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        for (Cookie cookie : request.getCookies()) {
            if (I18nConfig.LANGUAGE_PARAM.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }
}
