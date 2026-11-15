package uy.edu.um.nexo.mnatural.config;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.nexo.mnatural.controllers.LoginController;
import uy.edu.um.nexo.mnatural.security.AppUserDetailsService;
import uy.edu.um.nexo.mnatural.security.Role;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// "Explorar sin iniciar sesión" y reservar: un visitante mira todo, al tocar "Reservar" se le pide entrar o crear la
// cuenta y, al iniciar sesión, vuelve a la reserva que quería. Prueba SecurityConfig y LoginController juntos con el
// filtro de seguridad real; no usa la base de datos (el usuario viene de un mock)
@WebMvcTest({LoginController.class, ReserveLoginFlowTest.AnyUrlController.class})
@Import({SecurityConfig.class, LoginController.class, ReserveLoginFlowTest.AnyUrlController.class,
        ReserveLoginFlowTest.WebSecurityForTest.class})
class ReserveLoginFlowTest {

    private static final String PASSWORD = "clave-de-prueba";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    // @WebMvcTest no activa Spring Security por su cuenta
    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurityForTest {
    }

    // Atiende cualquier URL que no sea /login (ahí responde LoginController, más específico)
    @RestController
    static class AnyUrlController {
        @RequestMapping("/**")
        String anyUrl() {
            return "contenido";
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private AppUserDetailsService userDetailsService;

    @BeforeEach
    void users() {
        // Un usuario nuevo en cada llamada: Spring le borra la contraseña al usuario después de iniciar sesión
        for (Role role : Role.values()) {
            String email = role.name().toLowerCase() + "@test.uy";
            when(userDetailsService.loadUserByUsername(email)).thenAnswer(call -> User
                    .withUsername(email)
                    .password(passwordEncoder.encode(PASSWORD))
                    .roles(role.name())
                    .build());
        }
    }

    // ===== Explorar sin sesión =====

    @Test
    void aVisitorSeesTheListAndTheDetailWithoutLoggingIn() throws Exception {
        for (String url : new String[]{"/", "/actividades", "/actividades/1", "/actividades?q=kayak"}) {
            mockMvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    // ===== Tocar "Reservar" sin sesión =====

    @Test
    void touchingReserveWithoutASessionGoesToLogin() throws Exception {
        mockMvc.perform(get("/reservar/5")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/payments/bookings/1")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void theLoginExplainsWhyAndOffersToCreateTheAccount() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/reservar/5").session(session)).andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/login").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Iniciá sesión o registrate para reservar")))
                .andExpect(content().string(containsString("href=\"/registro/turista\"")));
        // Ver la pantalla no gasta la página guardada: sigue sirviendo para volver después de entrar
        mockMvc.perform(get("/login").session(session))
                .andExpect(content().string(containsString("Iniciá sesión o registrate para reservar")));
    }

    @Test
    void theExplanationIsInEnglishWhenThePageIsInEnglish() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/reservar/5").session(session));

        mockMvc.perform(get("/login").session(session).param("lang", "en"))
                .andExpect(content().string(containsString("Log in or sign up to book")))
                .andExpect(content().string(containsString("Create a tourist account")));
    }

    @Test
    void theLoginHasNoBookingMessageWhenTheVisitorDidNotComeFromABooking() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("registrate para reservar"))));

        // Venía de otra página protegida (sus reservas), no de reservar
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/turista/reservas").session(session)).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/login").session(session))
                .andExpect(content().string(not(containsString("registrate para reservar"))));
    }

    // ===== Volver después de entrar =====

    @Test
    void aTouristGoesBackToTheBookingTheyWantedAfterLoggingIn() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/reservar/5").session(session)).andExpect(redirectedUrl("/login"));

        String target = login(session, "tourist@test.uy", "");

        assertThat(target).contains("/reservar/5");
        // Y ahora sí entra a la reserva (con la sesión iniciada)
        mockMvc.perform(get("/reservar/5").session(session)).andExpect(status().isOk());
    }

    @Test
    void aTouristWhoStartedFromTheLoginPageGoesToTheHomePage() throws Exception {
        MockHttpSession session = new MockHttpSession();

        assertThat(login(session, "tourist@test.uy", "")).isEqualTo("/");
    }

    @Test
    void adminAndProviderGoToTheirPanelEvenIfTheyAskedForABooking() throws Exception {
        for (String[] user : new String[][]{{"admin@test.uy", "/admin/prestadores"},
                {"provider@test.uy", "/provider/activities"}}) {
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(get("/reservar/5").session(session));

            assertThat(login(session, user[0], "")).as(user[0]).isEqualTo(user[1]);
        }
    }

    // ===== Seguridad =====

    @Test
    void anExternalDestinationInTheRequestIsNeverFollowed() throws Exception {
        String[] attempts = {"?next=https://sitio-malo.com", "?continue=https://sitio-malo.com",
                "?redirect=//sitio-malo.com", "?next=//sitio-malo.com/reservar/5", "?returnUrl=https://sitio-malo.com",
                "?next=https://sitio-malo.com&continue=https://sitio-malo.com"};
        for (String attempt : attempts) {
            // Sin haber pedido ninguna página: va al inicio
            MockHttpSession plain = new MockHttpSession();
            mockMvc.perform(get("/login" + attempt).session(plain)).andExpect(status().isOk());
            assertThat(login(plain, "tourist@test.uy", attempt)).as(attempt).isEqualTo("/");

            // Habiendo pedido una reserva: vuelve a LA RESERVA, no a lo que diga el parámetro
            MockHttpSession booking = new MockHttpSession();
            mockMvc.perform(get("/reservar/5").session(booking));
            assertThat(login(booking, "tourist@test.uy", attempt)).as(attempt).contains("/reservar/5")
                    .doesNotContain("sitio-malo");
        }
    }

    @Test
    void aWrongPasswordDoesNotLetAnyoneIn() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/reservar/5").session(session));
        String token = csrfOf(session);

        mockMvc.perform(post("/login").session(session).param("email", "tourist@test.uy")
                        .param("password", "otra-clave").param("_csrf", token))
                .andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(get("/reservar/5").session(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void aLoggedInTouristEntersTheBooking() throws Exception {
        mockMvc.perform(get("/reservar/5").session(sessionAs(Role.TOURIST))).andExpect(status().isOk());
        mockMvc.perform(get("/payments/bookings/1").session(sessionAs(Role.TOURIST))).andExpect(status().isOk());
    }

    @Test
    void anAdminOrProviderCannotBookOrPay() throws Exception {
        for (Role role : new Role[]{Role.ADMIN, Role.PROVIDER}) {
            for (String url : new String[]{"/reservar/5", "/payments/bookings/1", "/turista/reservas",
                    "/reviews/bookings/1"}) {
                mockMvc.perform(get(url).session(sessionAs(role))).andExpect(status().isForbidden());
            }
        }
    }

    // ===== Ayudas =====

    // Inicia sesión como en el navegador y devuelve a dónde lo manda. "query" simula parámetros de la URL del POST
    private String login(MockHttpSession session, String email, String query) throws Exception {
        String token = csrfOf(session);
        var request = post("/login" + query).session(session).param("email", email).param("password", PASSWORD)
                .param("_csrf", token);
        String target = mockMvc.perform(request).andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(target).isNotNull();
        return target;
    }

    private String csrfOf(MockHttpSession session) throws Exception {
        String page = mockMvc.perform(get("/login").session(session)).andReturn().getResponse().getContentAsString();
        Matcher matcher = CSRF.matcher(page);
        assertThat(matcher.find()).as("el formulario de login trae el token CSRF").isTrue();
        return matcher.group(1);
    }

    private MockHttpSession sessionAs(Role role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new UsernamePasswordAuthenticationToken("usuario@test.uy", null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())))));
        return session;
    }
}
