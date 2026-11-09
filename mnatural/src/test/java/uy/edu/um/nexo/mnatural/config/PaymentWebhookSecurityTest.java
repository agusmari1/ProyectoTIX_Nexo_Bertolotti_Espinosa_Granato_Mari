package uy.edu.um.nexo.mnatural.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.nexo.mnatural.security.AppUserDetailsService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El webhook de Mercado Pago tiene que entrar sin sesión y sin token CSRF, y solo él. Un controller de prueba
// responde 200 a cualquier URL, así que el resultado depende solo de SecurityConfig. No usa la base de datos
@WebMvcTest(PaymentWebhookSecurityTest.AnyUrlController.class)
@Import({SecurityConfig.class, PaymentWebhookSecurityTest.AnyUrlController.class,
        PaymentWebhookSecurityTest.WebSecurityForTest.class})
class PaymentWebhookSecurityTest {

    // @WebMvcTest no activa Spring Security por su cuenta
    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurityForTest {
    }

    @RestController
    static class AnyUrlController {
        @RequestMapping("/**")
        String anyUrl() {
            return "ok";
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AppUserDetailsService userDetailsService;

    @Test
    void mercadoPagoCanPostTheNotificationWithoutSessionNorCsrfToken() throws Exception {
        mockMvc.perform(post("/payments/webhook").param("type", "payment").param("data.id", "123"))
                .andExpect(status().isOk());
    }

    @Test
    void otherPaymentRoutesStayClosedToAnonymousUsers() throws Exception {
        // Sin sesión van al login
        mockMvc.perform(get("/payments/bookings/1")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/payments/return")).andExpect(status().is3xxRedirection());
        // Un POST sin sesión ni token CSRF no pasa
        mockMvc.perform(post("/payments/bookings/1/start")).andExpect(status().isForbidden());
        mockMvc.perform(post("/payments/bookings/1/cancel")).andExpect(status().isForbidden());
    }

    @Test
    void onlyThePostToTheWebhookIsOpenNotItsNeighbours() throws Exception {
        mockMvc.perform(get("/payments/webhook")).andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/payments/webhook/otra")).andExpect(status().isForbidden());
        mockMvc.perform(post("/payments/webhooks")).andExpect(status().isForbidden());
    }
}
