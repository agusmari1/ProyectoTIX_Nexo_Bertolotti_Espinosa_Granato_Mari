package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.RegistrationException;
import uy.edu.um.nexo.mnatural.service.TouristService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El idioma inicial del turista al registrarse: el que se calculó según su país queda elegido en el navegador.
// Sin filtros de seguridad y con TouristService simulado: no se toca la base de datos
@WebMvcTest(TouristRegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
class TouristRegistrationLanguageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TouristService touristService;

    private MockHttpServletRequestBuilder validRegistration(String country) {
        return post("/registro/turista")
                .param("name", "Ana").param("lastName", "Prueba").param("dateOfBirth", "1995-05-10")
                .param("documentType", "CI").param("documentNumber", "12345678").param("country", country)
                .param("email", "ana@ejemplo.test").param("password", "clave-de-prueba")
                .param("confirmPassword", "clave-de-prueba");
    }

    private Tourist registeredWith(String language) {
        Tourist tourist = new Tourist();
        tourist.setLanguage(language);
        return tourist;
    }

    @Test
    void aTouristFromASpanishSpeakingCountryStartsInSpanish() throws Exception {
        when(touristService.register(any())).thenReturn(registeredWith("es"));

        mockMvc.perform(validRegistration("Uruguay"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/registro/turista/exito"))
                .andExpect(cookie().value("lang", "es"));
    }

    @Test
    void aTouristFromAnyOtherCountryStartsInEnglish() throws Exception {
        when(touristService.register(any())).thenReturn(registeredWith("en"));

        mockMvc.perform(validRegistration("Brasil"))
                .andExpect(redirectedUrl("/registro/turista/exito"))
                .andExpect(cookie().value("lang", "en"));
    }

    @Test
    void aLanguageThatIsNotSupportedIsStoredAsSpanish() throws Exception {
        when(touristService.register(any())).thenReturn(registeredWith("pt"));

        mockMvc.perform(validRegistration("Portugal"))
                .andExpect(cookie().value("lang", "es"));
    }

    @Test
    void aFailedRegistrationDoesNotChangeTheLanguage() throws Exception {
        when(touristService.register(any())).thenThrow(new RegistrationException("Ya existe una cuenta con ese correo"));

        mockMvc.perform(validRegistration("Uruguay"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("lang"));
        // Formulario incompleto
        mockMvc.perform(post("/registro/turista").param("name", "Ana"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("lang"));
    }
}
