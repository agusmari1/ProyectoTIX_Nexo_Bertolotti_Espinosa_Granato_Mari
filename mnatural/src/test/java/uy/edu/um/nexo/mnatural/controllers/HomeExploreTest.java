package uy.edu.um.nexo.mnatural.controllers;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.security.Role;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// La opción "Explorar sin iniciar sesión" del inicio. No usa la base de datos
@WebMvcTest(HomeController.class)
class HomeExploreTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aVisitorSeesTheOptionToExploreWithoutLoggingInThatLeadsToTheActivities() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Explorar sin iniciar sesión")))
                .andExpect(content().string(containsString("id=\"explore-link\"")))
                .andExpect(content().string(containsString("href=\"/actividades\"")))
                .andExpect(content().string(containsString("Para reservar vas a necesitar iniciar sesión o crear una")));
    }

    @Test
    void theOptionIsAlsoInEnglish() throws Exception {
        mockMvc.perform(get("/").cookie(new Cookie("lang", "en")))
                .andExpect(content().string(containsString("Browse without logging in")))
                .andExpect(content().string(not(containsString("Explorar sin iniciar sesión"))));
    }

    @Test
    void whoAlreadyHasASessionDoesNotNeedTheOption() throws Exception {
        var tourist = UsernamePasswordAuthenticationToken.authenticated("turista@ejemplo.test", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + Role.TOURIST.name())));

        mockMvc.perform(get("/").principal(tourist))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Explorar sin iniciar sesión"))))
                .andExpect(content().string(containsString("Ver actividades")));
    }
}
