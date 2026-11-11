package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.service.ActivityCatalogService;
import uy.edu.um.nexo.mnatural.weather.WeatherService;

import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El cambio de idioma de punta a punta en el portal público: selector, cookie, textos y formatos.
// Los services están simulados, no se toca la base de datos
@WebMvcTest(PublicActivityController.class)
class LanguageSwitchTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ActivityCatalogService catalogService;
    @MockitoBean
    private ActivityTypeRepository activityTypeRepository;
    @MockitoBean(name = "weatherService")
    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        Business business = new Business();
        business.setName("Emprendimiento");

        Activity activity = new Activity();
        activity.setId(1L);
        activity.setName("Cabalgata");
        activity.setDescription("Descripción");
        activity.setDepartment(Department.ROCHA);
        activity.setLocality("Castillos");
        activity.setAddress("Punto de encuentro");
        activity.setDurationMinutes(90);
        activity.setPricePerPerson(new BigDecimal("1234.50"));
        activity.setBusiness(business);

        when(catalogService.search(any())).thenReturn(List.of(activity));
        when(catalogService.findPublished(1L)).thenReturn(Optional.of(activity));
        when(catalogService.upcomingSchedules(activity)).thenReturn(List.of());
        when(activityTypeRepository.findAll(any(Sort.class))).thenReturn(List.of());
    }

    @Test
    void withoutAnyChoiceThePortalIsInSpanish() throws Exception {
        mockMvc.perform(get("/actividades"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html lang=\"es\"")))
                .andExpect(content().string(containsString("Iniciar sesión")))
                .andExpect(content().string(containsString("Limpiar filtros")))
                .andExpect(content().string(not(containsString("Clear filters"))));
    }

    @Test
    void theLanguageParameterSwitchesToEnglishAndRemembersItInACookie() throws Exception {
        mockMvc.perform(get("/actividades").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("lang", "en"))
                .andExpect(content().string(containsString("<html lang=\"en\"")))
                .andExpect(content().string(containsString("Log in")))
                .andExpect(content().string(containsString("Clear filters")))
                .andExpect(content().string(containsString("Nature experiences all over Uruguay")))
                .andExpect(content().string(not(containsString("Limpiar filtros"))))
                .andExpect(content().string(not(containsString("Iniciar sesión"))));
    }

    @Test
    void theChoiceIsKeptOnTheNextPagesThroughTheCookie() throws Exception {
        for (String page : new String[]{"/actividades", "/actividades/1"}) {
            mockMvc.perform(get(page).cookie(new Cookie("lang", "en")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<html lang=\"en\"")))
                    .andExpect(content().string(containsString("Log in")));
        }
    }

    @Test
    void theChoiceCanBeChangedBackToSpanish() throws Exception {
        mockMvc.perform(get("/actividades").param("lang", "es").cookie(new Cookie("lang", "en")))
                .andExpect(cookie().value("lang", "es"))
                .andExpect(content().string(containsString("Iniciar sesión")));
    }

    @Test
    void theDetailPageIsTranslatedWithItsOwnFormats() throws Exception {
        mockMvc.perform(get("/actividades/1").cookie(new Cookie("lang", "en")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Useful information")))
                .andExpect(content().string(containsString("Upcoming dates")))
                .andExpect(content().string(containsString("Back to the activities")))
                .andExpect(content().string(containsString("90 minutes")))
                // Precio: 1,234.50 en inglés y 1.234,50 en español
                .andExpect(content().string(containsString("1,234.50")))
                .andExpect(content().string(not(containsString("Datos útiles"))));

        mockMvc.perform(get("/actividades/1"))
                .andExpect(content().string(containsString("Datos útiles")))
                .andExpect(content().string(containsString("90 minutos")))
                .andExpect(content().string(containsString("1.234,50")));
    }

    @Test
    void aBrowserThatAsksForEnglishGetsEnglishUntilTheUserChooses() throws Exception {
        mockMvc.perform(get("/actividades").header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(content().string(containsString("Log in")));
        mockMvc.perform(get("/actividades").header("Accept-Language", "en").cookie(new Cookie("lang", "es")))
                .andExpect(content().string(containsString("Iniciar sesión")));
    }

    @Test
    void anyOtherBrowserLanguageFallsBackToSpanish() throws Exception {
        for (String header : new String[]{"fr-FR,fr;q=0.9", "pt-BR", "es-UY", "de"}) {
            mockMvc.perform(get("/actividades").header("Accept-Language", header))
                    .andExpect(content().string(containsString("Iniciar sesión")));
        }
    }

    @Test
    void anUnsupportedLanguageIsIgnoredAndNothingIsStored() throws Exception {
        mockMvc.perform(get("/actividades").param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("lang"))
                .andExpect(content().string(containsString("Iniciar sesión")));
        // Con un idioma ya elegido, un valor inválido no lo pisa
        mockMvc.perform(get("/actividades").param("lang", "xx-nope").cookie(new Cookie("lang", "en")))
                .andExpect(cookie().doesNotExist("lang"))
                .andExpect(content().string(containsString("Log in")));
    }

    @Test
    void theSelectorShowsBothLanguagesMarksTheCurrentOneAndKeepsTheFilters() throws Exception {
        mockMvc.perform(get("/actividades").param("locality", "Castillos"))
                .andExpect(content().string(containsString("href=\"/actividades?locality=Castillos&amp;lang=es\"")))
                .andExpect(content().string(containsString("href=\"/actividades?locality=Castillos&amp;lang=en\"")))
                .andExpect(content().string(containsString("class=\"active\"")));

        // Cambiar de idioma dos veces no acumula el parámetro lang
        mockMvc.perform(get("/actividades").param("lang", "en").param("locality", "Castillos"))
                .andExpect(content().string(containsString("href=\"/actividades?locality=Castillos&amp;lang=es\"")))
                .andExpect(content().string(not(containsString("lang=en&amp;lang"))));
    }

    @Test
    void theSelectorMarksTheCurrentLanguage() throws Exception {
        mockMvc.perform(get("/actividades"))
                .andExpect(content().string(containsString("lang=\"es\" hreflang=\"es\"")))
                .andExpect(content().string(containsString("aria-current=\"true\"")));
        mockMvc.perform(get("/actividades").cookie(new Cookie("lang", "en")))
                .andExpect(content().string(containsString("Switch to Spanish")));
    }
}
