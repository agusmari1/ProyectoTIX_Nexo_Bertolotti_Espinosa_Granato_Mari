package uy.edu.um.nexo.mnatural.controllers;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// La página 403 (templates/error/403.html) con el "path" que le pasa Spring Boot. Sin seguridad ni base de datos
@WebMvcTest(ForbiddenPageTest.ShowForbidden.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ForbiddenPageTest.ShowForbidden.class)
class ForbiddenPageTest {

    // Muestra la vista de error con el modelo que le arma Spring Boot ("path" es la URL que se pidió)
    @Controller
    static class ShowForbidden {
        @GetMapping("/mostrar-403")
        String show(@RequestParam(required = false) String path, Model model) {
            if (path != null) {
                model.addAttribute("path", path);
            }
            return "error/403";
        }
    }


    @Autowired
    private MockMvc mockMvc;

    @Test
    void anAdminOrProviderWhoTriesToBookIsToldThatOnlyTouristsCanBook() throws Exception {
        for (String path : new String[]{"/reservar/5", "/payments/bookings/1/pay"}) {
            mockMvc.perform(get("/mostrar-403").param("path", path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Solo los turistas pueden reservar")))
                    .andExpect(content().string(containsString("Volver al inicio")));
        }
    }

    @Test
    void theOtherTouristSectionsSayTheyAreForTouristsOnly() throws Exception {
        for (String path : new String[]{"/turista/reservas", "/reviews/bookings/3"}) {
            mockMvc.perform(get("/mostrar-403").param("path", path))
                    .andExpect(content().string(containsString("Esta sección es solo para turistas")))
                    .andExpect(content().string(not(containsString("Solo los turistas pueden reservar"))));
        }
    }

    @Test
    void anyOtherForbiddenPageGetsAGeneralMessage() throws Exception {
        for (String path : new String[]{"/admin/prestadores", "/provider/activities", "/cualquier/cosa"}) {
            mockMvc.perform(get("/mostrar-403").param("path", path))
                    .andExpect(content().string(containsString("No tenés permiso para ver esta página")))
                    .andExpect(content().string(not(containsString("Solo los turistas"))));
        }
        // Sin ruta en el modelo (por ejemplo un 403 raro): también el mensaje general, sin romperse
        mockMvc.perform(get("/mostrar-403"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No tenés permiso para ver esta página")));
    }

    @Test
    void theMessageIsAlsoInEnglish() throws Exception {
        mockMvc.perform(get("/mostrar-403").param("path", "/reservar/5").cookie(new Cookie("lang", "en")))
                .andExpect(content().string(containsString("Only tourists can make bookings")))
                .andExpect(content().string(containsString("Back to the home page")))
                .andExpect(content().string(not(containsString("Solo los turistas"))));
    }

    @Test
    void thePathIsNeverPrintedInThePage() throws Exception {
        mockMvc.perform(get("/mostrar-403").param("path", "/reservar/<script>alert(1)</script>"))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }
}
