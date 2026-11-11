package uy.edu.um.nexo.mnatural.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;
import uy.edu.um.nexo.mnatural.controllers.LanguageAdvice;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Idioma guardado en el usuario y links del selector. Sin base de datos: el repositorio es un mock
class UserLanguageInterceptorTest {

    private final UserRepository users = mock(UserRepository.class);
    private final LocaleResolver resolver = new I18nConfig(null).localeResolver();
    private final UserLanguageInterceptor interceptor = new UserLanguageInterceptor(() -> users);

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/actividades");
        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, resolver);
        response = new MockHttpServletResponse();
        Tourist tourist = new Tourist();
        tourist.setLanguage("en");
        when(users.findByEmail("turista@ejemplo.test")).thenReturn(Optional.of(tourist));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void loggedInAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken
                .authenticated(email, null, AuthorityUtils.createAuthorityList("ROLE_TOURIST")));
    }

    @Test
    void aUserWithoutACookieGetsTheLanguageSavedInTheirAccount() {
        loggedInAs("turista@ejemplo.test");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        assertThat(response.getCookie("lang")).isNotNull();
        assertThat(response.getCookie("lang").getValue()).isEqualTo("en");
    }

    @Test
    void aLanguageAlreadyChosenInTheBrowserIsRespected() {
        loggedInAs("turista@ejemplo.test");
        request.setCookies(new Cookie("lang", "es"));

        interceptor.preHandle(request, response, new Object());

        assertThat(response.getCookie("lang")).isNull();
        verify(users, never()).findByEmail("turista@ejemplo.test");
    }

    @Test
    void explicitlyChoosingALanguageWinsOverTheSavedOne() {
        loggedInAs("turista@ejemplo.test");
        request.setParameter("lang", "es");

        interceptor.preHandle(request, response, new Object());

        assertThat(response.getCookie("lang")).isNull();
    }

    @Test
    void visitorsAndAnonymousUsersAreLeftAlone() {
        interceptor.preHandle(request, response, new Object());
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        interceptor.preHandle(request, response, new Object());

        assertThat(response.getCookie("lang")).isNull();
    }

    @Test
    void aUserWithoutASavedLanguageOrNotFoundChangesNothing() {
        Tourist withoutLanguage = new Tourist();
        when(users.findByEmail("sinidioma@ejemplo.test")).thenReturn(Optional.of(withoutLanguage));
        loggedInAs("sinidioma@ejemplo.test");
        interceptor.preHandle(request, response, new Object());
        loggedInAs("noexiste@ejemplo.test");
        interceptor.preHandle(request, response, new Object());

        assertThat(response.getCookie("lang")).isNull();
    }

    @Test
    void aSavedLanguageThatIsNotSupportedFallsBackToSpanish() {
        Tourist old = new Tourist();
        old.setLanguage("pt");
        when(users.findByEmail("antiguo@ejemplo.test")).thenReturn(Optional.of(old));
        loggedInAs("antiguo@ejemplo.test");

        interceptor.preHandle(request, response, new Object());

        assertThat(response.getCookie("lang").getValue()).isEqualTo("es");
    }

    @Test
    void withoutARepositoryItDoesNothing() {
        loggedInAs("turista@ejemplo.test");

        assertThat(new UserLanguageInterceptor(() -> null).preHandle(request, response, new Object())).isTrue();
        assertThat(response.getCookie("lang")).isNull();
    }

    // ===== Selector: los links no filtran datos de un formulario =====

    @Test
    void theSelectorLinksOfAPostNeverCarryTheFormData() {
        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/registro/turista");
        post.setParameter("password", "secreto123");
        post.setParameter("email", "ana@ejemplo.test");

        LanguageAdvice.LanguageLinks links = new LanguageAdvice().languageLinks(post, Locale.forLanguageTag("es"));

        assertThat(links.englishUrl()).isEqualTo("/registro/turista?lang=en");
        assertThat(links.spanishUrl()).isEqualTo("/registro/turista?lang=es");
        assertThat(links.englishUrl() + links.spanishUrl()).doesNotContain("secreto123").doesNotContain("ana@");
    }

    @Test
    void theSelectorLinksOfAGetKeepTheQueryAndReplaceTheLanguage() {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/actividades");
        get.setParameter("department", "ROCHA");
        get.setParameter("lang", "en");
        get.setParameter("locality", "Punta del Diablo");

        LanguageAdvice.LanguageLinks links = new LanguageAdvice().languageLinks(get, Locale.forLanguageTag("en"));

        assertThat(links.current()).isEqualTo("en");
        assertThat(links.english()).isTrue();
        assertThat(links.spanish()).isFalse();
        assertThat(links.spanishUrl()).contains("department=ROCHA", "locality=Punta%20del%20Diablo")
                .endsWith("lang=es").containsOnlyOnce("lang=");
        assertThat(List.of(links.spanishUrl(), links.englishUrl())).allMatch(url -> url.startsWith("/actividades?"));
    }

    @Test
    void theSelectorConsidersAnyOtherLanguageSpanish() {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/");

        assertThat(new LanguageAdvice().languageLinks(get, Locale.forLanguageTag("fr")).current()).isEqualTo("es");
        assertThat(new LanguageAdvice().languageLinks(get, Locale.forLanguageTag("es-UY")).current()).isEqualTo("es");
        assertThat(new LanguageAdvice().languageLinks(get, Locale.forLanguageTag("en-US")).current()).isEqualTo("en");
    }
}
