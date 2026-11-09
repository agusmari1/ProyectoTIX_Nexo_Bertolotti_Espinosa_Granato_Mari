package uy.edu.um.nexo.mnatural.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.nexo.mnatural.security.AppUserDetailsService;
import uy.edu.um.nexo.mnatural.security.Role;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Qué rol entra a qué ruta. Un controller de prueba responde 200 a cualquier URL, así que el
// resultado depende solo de SecurityConfig. No usa la base de datos
@WebMvcTest(SecurityConfigTest.AnyUrlController.class)
@Import({SecurityConfig.class, SecurityConfigTest.AnyUrlController.class, SecurityConfigTest.WebSecurityForTest.class})
class SecurityConfigTest {

    // @WebMvcTest no activa Spring Security por su cuenta
    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurityForTest {
    }

    private static final String[] PUBLIC_URLS = {
            "/", "/login", "/registro/turista", "/registro/prestador", "/actividades", "/actividades/1",
            "/css/styles.css", "/img/banner-inicio.jpg", "/api/businesses", "/api/activities/search"};
    private static final String[] TOURIST_URLS = {
            "/turista/reservas", "/reservar/1", "/payments/bookings/1", "/payments/return", "/reviews/bookings/1"};
    private static final String[] PROVIDER_URLS = {"/provider/activities", "/provider/reviews", "/prestador/panel"};
    private static final String[] ADMIN_URLS = {"/admin/prestadores", "/admin/tipos-actividad"};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private AppUserDetailsService userDetailsService;

    @RestController
    static class AnyUrlController {

        // Devuelve el token CSRF para poder enviar el formulario de login en el test
        @RequestMapping("/**")
        String anyUrl(HttpServletRequest request) {
            return ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
        }
    }

    @Test
    void anonymousEntersOnlyPublicUrlsAndTheRestGoesToLogin() throws Exception {
        expect(null, PUBLIC_URLS, status().isOk());
        for (String[] urls : List.of(TOURIST_URLS, PROVIDER_URLS, ADMIN_URLS)) {
            expect(null, urls, redirectedUrl("/login"));
        }
    }

    @Test
    void touristEntersOnlyTouristAndPublicUrls() throws Exception {
        expect(Role.TOURIST, PUBLIC_URLS, status().isOk());
        expect(Role.TOURIST, TOURIST_URLS, status().isOk());
        expect(Role.TOURIST, PROVIDER_URLS, status().isForbidden());
        expect(Role.TOURIST, ADMIN_URLS, status().isForbidden());
    }

    @Test
    void providerEntersOnlyProviderAndPublicUrls() throws Exception {
        expect(Role.PROVIDER, PUBLIC_URLS, status().isOk());
        expect(Role.PROVIDER, PROVIDER_URLS, status().isOk());
        expect(Role.PROVIDER, TOURIST_URLS, status().isForbidden());
        expect(Role.PROVIDER, ADMIN_URLS, status().isForbidden());
    }

    @Test
    void adminEntersOnlyAdminAndPublicUrls() throws Exception {
        expect(Role.ADMIN, PUBLIC_URLS, status().isOk());
        expect(Role.ADMIN, ADMIN_URLS, status().isOk());
        expect(Role.ADMIN, TOURIST_URLS, status().isForbidden());
        expect(Role.ADMIN, PROVIDER_URLS, status().isForbidden());
    }

    @Test
    void apiIsPublicOnlyForGet() throws Exception {
        mockMvc.perform(get("/api/businesses")).andExpect(status().isOk());
        mockMvc.perform(post("/api/businesses")).andExpect(status().isForbidden());
    }

    @Test
    void formsStillRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/payments/bookings/1/start").session(sessionAs(Role.TOURIST)))
                .andExpect(status().isForbidden());
    }

    @Test
    void afterLoginTouristReturnsToTheBookingPageItAskedFor() throws Exception {
        when(userDetailsService.loadUserByUsername("turista@test.uy")).thenReturn(User
                .withUsername("turista@test.uy")
                .password(passwordEncoder.encode("clave-de-prueba"))
                .roles(Role.TOURIST.name())
                .build());
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/reservar/7").session(session)).andExpect(redirectedUrl("/login"));
        String csrfToken = mockMvc.perform(get("/login").session(session))
                .andReturn().getResponse().getContentAsString();

        String target = mockMvc.perform(post("/login").session(session)
                        .param("email", "turista@test.uy")
                        .param("password", "clave-de-prueba")
                        .param("_csrf", csrfToken))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(target).contains("/reservar/7");
    }

    private void expect(Role role, String[] urls, ResultMatcher result) throws Exception {
        for (String url : urls) {
            MockHttpSession session = role == null ? new MockHttpSession() : sessionAs(role);
            mockMvc.perform(get(url).session(session)).andExpect(result);
        }
    }

    // Deja la sesión como queda después de iniciar sesión con ese rol
    private MockHttpSession sessionAs(Role role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new UsernamePasswordAuthenticationToken("usuario@test.uy", null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())))));
        return session;
    }
}
